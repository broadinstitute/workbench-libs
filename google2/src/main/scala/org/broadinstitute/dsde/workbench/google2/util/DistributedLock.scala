package org.broadinstitute.dsde.workbench.google2
package util

import java.util.concurrent.{Executor, TimeUnit}

import cats.data.OptionT
import cats.effect._
import cats.syntax.all._
import fs2.Stream
import com.google.api.core.ApiFutures
import com.google.cloud.firestore.{DocumentReference, DocumentSnapshot, Transaction}
import org.broadinstitute.dsde.workbench.google2.util.DistributedLock._
import org.broadinstitute.dsde.workbench.model.WorkbenchException

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class DistributedLock[F[_]](
  lockPathPrefix: String,
  config: DistributedLockConfig,
  val googleFirestoreOps: GoogleFirestoreService[F]
)(implicit ec: ExecutionContext, F: Async[F])
    extends DistributedLockAlgebra[F] {
  val retryable: Function1[Throwable, Boolean] = {
    case _: FailToObtainLock => true
    case _                   => false
  }
  // This is executor for java client's call back to return to. Hence it should be main implicit execution context
  val executor = new Executor {
    override def execute(command: Runnable): Unit = ec.execute(command)
  }

  def withLock(lock: LockPath): Resource[F, Unit] = {
    val lockPathWithPrefix =
      lock.copy(collectionName = CollectionName(s"$lockPathPrefix-${lock.collectionName.asString}"))
    Resource.make {
      Stream
        .retry[F, Unit](acquireLock(lockPathWithPrefix), config.retryInterval, identity, config.maxRetry, retryable)
        .adaptError { case e => new WorkbenchException(s"Reached max retry: $e") }
        .compile
        .drain
    }(_ => releaseLock(lockPathWithPrefix))
  }

  private[dsde] def acquireLock[A](lock: LockPath): F[Unit] =
    googleFirestoreOps.transaction { (db, tx) =>
      val docRef = db.collection(lock.collectionName.asString).document(lock.document.asString)
      for {
        lockStatus <- getLockStatus(tx, docRef)
        _ <- lockStatus match {
          case Available => setLock(tx, docRef, lock.expiresIn)
          case Locked    => F.raiseError[Unit](FailToObtainLock(lock))
        }
      } yield ()
    }

  private[dsde] def getLockStatus(tx: Transaction, documentReference: DocumentReference): F[LockStatus] = {
    val res = for {
      documentSnapshot <- OptionT(
        F
          .async[DocumentSnapshot] { cb =>
            F.delay(
              ApiFutures.addCallback(
                tx.get(documentReference),
                callBack(cb),
                executor
              )
            ).as(None)
          }
          .map(Option(_))
      )

      expiresAt <- OptionT.fromOption[F](Option(documentSnapshot.getLong(EXPIRESAT)))
      statusF = F.realTimeInstant.map[LockStatus] { currentTime =>
        if (expiresAt.longValue() < currentTime.toEpochMilli)
          Available
        else
          Locked
      }
      status <- OptionT.liftF[F, LockStatus](statusF)
    } yield status

    res.fold[LockStatus](Available)(identity)
  }

  private[dsde] def setLock(tx: Transaction, documentReference: DocumentReference, expiresIn: FiniteDuration): F[Unit] =
    for {
      currentTime <- F.realTimeInstant
      data = Map(
        EXPIRESAT -> (currentTime.toEpochMilli + expiresIn.toMillis)
      )
      _ <- F.delay(tx.set(documentReference, data.asJava))
    } yield ()

  private[dsde] def releaseLock(lockPath: LockPath): F[Unit] =
    googleFirestoreOps.transaction { (db, tx) =>
      val docRef = db.collection(lockPath.collectionName.asString).document(lockPath.document.asString)
      F.delay(tx.delete(docRef))
    }
}

object DistributedLock {
  private[dsde] val EXPIRESAT = "expiresAt"
  private[dsde] val EXPIRESATTIMEUNIT = TimeUnit.MILLISECONDS

  def apply[F[_]: Async](
    lockPathPrefix: String,
    config: DistributedLockConfig,
    googleFirestoreOps: GoogleFirestoreService[F]
  )(implicit ec: ExecutionContext): DistributedLock[F] =
    new DistributedLock(lockPathPrefix: String, config, googleFirestoreOps)
}

final case class LockPath(collectionName: CollectionName, document: Document, expiresIn: FiniteDuration)
final case class DistributedLockConfig(retryInterval: FiniteDuration, maxRetry: Int)
final case class FailToObtainLock(lockPath: LockPath) extends RuntimeException {
  override def getMessage: String = s"can't get lock: $lockPath"
}

sealed trait LockStatus extends Serializable with Product
final case object Available extends LockStatus
final case object Locked extends LockStatus
