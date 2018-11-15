package org.broadinstitute.dsde.workbench.google
package util

import java.util.concurrent.{Executor, TimeUnit}

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import org.broadinstitute.dsde.workbench.util.Retry.retry
import com.google.api.core.ApiFutures
import com.google.cloud.firestore.{DocumentReference, DocumentSnapshot, Transaction}
import org.broadinstitute.dsde.workbench.google.GoogleFirestoreOpsInterpreters.callBack
import org.broadinstitute.dsde.workbench.model.WorkbenchException
import org.broadinstitute.dsde.workbench.google.util.DistributedLock._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class DistributedLock[F[_]](lockPathPrefix: String, config: DistributedLockConfig, val googleFirestoreOps: GoogleFirestoreOps[F])(implicit ec: ExecutionContext, timer: Timer[F], af: Async[F]){

  // This is executor for java client's call back to return to. Hence it should be main implicit execution context
  val executor = new Executor {
    override def execute(command: Runnable): Unit = ec.execute(command)
  }

  def withLock(lock: LockPath): Resource[F, Unit] = Resource.make{
    retry(acquireLock(lock), config.retryInterval, config.maxRetry)
  }(_ => releaseLock(lock))

  private[dsde] def acquireLock[A](lock: LockPath): F[Unit] =
    googleFirestoreOps.transaction { (db, tx) =>
      val docRef = db.collection(s"$lockPathPrefix-${lock.collectionName.asString}").document(lock.document.asString)
      for {
        lockStatus <- getLockStatus(tx, docRef)
        _ <- lockStatus match {
          case Available => setLock(tx, docRef, lock.expiresIn)
          case Locked => af.raiseError[Unit](new WorkbenchException(s"can't get lock $lock"))
        }
      } yield ()
    }

  private[dsde] def getLockStatus(tx: Transaction, documentReference: DocumentReference): F[LockStatus] = {
    val res = for {
      documentSnapshot <- OptionT(af.async[DocumentSnapshot] { cb =>
        ApiFutures.addCallback(
          tx.get(documentReference),
          callBack(cb),
          executor
        )
      }.map(Option(_)))

      expiresAt <- OptionT.fromOption[F](Option(documentSnapshot.getLong(EXPIRESAT)))

      statusF = timer.clock.realTime(EXPIRESINTIMEUNIT).map[LockStatus] { currentTime =>
        if (expiresAt < currentTime)
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
      currentTime <- timer.clock.realTime(EXPIRESINTIMEUNIT)
      data = Map(
        EXPIRESAT -> (currentTime + expiresIn.toMillis)
      )
      _ <- af.delay(tx.set(documentReference, data.asJava))
    } yield ()

  private[dsde] def releaseLock(lockPath: LockPath)(implicit sf: Sync[F]): F[Unit] = {
    googleFirestoreOps.transaction{
      (db, tx) =>
        val docRef = db.collection(lockPath.collectionName.asString).document(lockPath.document.asString)
        sf.delay(tx.delete(docRef))
    }
  }
}

object DistributedLock {
  private[dsde] val EXPIRESAT = "expiresAt"
  private[dsde] val EXPIRESINTIMEUNIT = TimeUnit.MILLISECONDS

  def apply[F[_]](lockPathPrefix: String, config: DistributedLockConfig, googleFirestoreOps: GoogleFirestoreOps[F])(implicit ec: ExecutionContext, timer: Timer[F], af: Async[F]): DistributedLock[F] = new DistributedLock(lockPathPrefix: String, config, googleFirestoreOps)
}

final case class LockPath(collectionName: CollectionName, document: Document, expiresIn: FiniteDuration)
final case class DistributedLockConfig(retryInterval: FiniteDuration, maxRetry: Int)

sealed trait LockStatus extends Serializable with Product
final case object Available extends LockStatus
final case object Locked extends LockStatus