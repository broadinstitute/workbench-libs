package org.broadinstitute.dsde.workbench.google
package util

import java.util.concurrent.{Executor, TimeUnit}

import cats.effect._
import cats.implicits._
import com.google.api.core.ApiFutures
import com.google.cloud.firestore.{DocumentReference, DocumentSnapshot, Transaction}
import org.broadinstitute.dsde.workbench.google.GoogleFirestoreOpsInterpreters.callBack
import org.broadinstitute.dsde.workbench.model.WorkbenchException
import org.broadinstitute.dsde.workbench.google.util.DistributedLock._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class DistributedLock[F[_]](lockPathPrefix: String, config: DistributedLockConfig, val googleFirestoreOps: GoogleFirestoreOps[F])(implicit ec: ExecutionContext, timer: Timer[F]){

  // This is executor for java client's call back to return to. Hence it should be main implicit execution context
  val executor = new Executor {
    override def execute(command: Runnable): Unit = ec.execute(command)
  }

  def withLock(lock: LockPath)(implicit af: Async[F]): Resource[F, Unit] = Resource.make{
    retry(acquireLock(lock), config.retryInterval, config.maxRetry)
  }(_ => releaseLock(lock))

  private[dsde] def acquireLock[A](lock: LockPath)(implicit af: Async[F]): F[Unit] =
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

  private[dsde] def getLockStatus(tx: Transaction, documentReference: DocumentReference)(implicit af: Async[F]): F[LockStatus] =
    for {
      nullableDocumentSnapshot <- af.async[DocumentSnapshot] { cb =>
        ApiFutures.addCallback(
          tx.get(documentReference),
          callBack(cb),
          executor
        )
      }
      status <- Option(nullableDocumentSnapshot).fold[F[LockStatus]](af.pure(Available)) { documentSnapshot =>
        val expiresIn = Option(documentSnapshot.getLong(EXPIRESIN))
        expiresIn match {
          case Some(e) =>
            timer.clock.realTime(EXPIRESINTIMEUNIT).map { currentTime =>
              if (e < currentTime)
                Available
              else
                Locked
            }
          case None => af.pure(Available)
        }
      }
    } yield status

  private[dsde] def releaseLock(lockPath: LockPath)(implicit sf: Sync[F]): F[Unit] = {
    googleFirestoreOps.transaction{
      (db, tx) =>
        val docRef = db.collection(lockPath.collectionName.asString).document(lockPath.document.asString)
        sf.delay(tx.delete(docRef))
    }
  }
}

object DistributedLock {
  private[dsde] val EXPIRESIN = "expiresIn"
  private[dsde] val EXPIRESINTIMEUNIT = TimeUnit.MILLISECONDS

  def apply[F[_]](lockPathPrefix: String, config: DistributedLockConfig, googleFirestoreOps: GoogleFirestoreOps[F])(implicit ec: ExecutionContext, timer: Timer[F]): DistributedLock[F] = new DistributedLock(lockPathPrefix: String, config, googleFirestoreOps)

  private[dsde] def setLock[F[_]](tx: Transaction, documentReference: DocumentReference, expiresIn: FiniteDuration)(implicit sf: Sync[F], timer: Timer[F]): F[Unit] =
    for {
      currentTime <- timer.clock.realTime(EXPIRESINTIMEUNIT)
      data = Map(
        EXPIRESIN -> (currentTime + expiresIn.toMillis)
      )
      _ <- sf.delay(tx.set(documentReference, data.asJava))
    } yield ()

  def retry[F[_], A](fa: F[A], interval: FiniteDuration, maxRetries: Int)
                         (implicit sf: Sync[F], timer: Timer[F]): F[A] = {
    fa.handleErrorWith {
      case e =>
        if (maxRetries > 0)
          timer.sleep(interval) *> retry(fa, interval, maxRetries - 1)
        else
          sf.raiseError(new WorkbenchException(s"can't get lock: ${e}"))
    }
  }
}

final case class LockPath(collectionName: CollectionName, document: Document, expiresIn: FiniteDuration)
final case class DistributedLockConfig(retryInterval: FiniteDuration, maxRetry: Int)

sealed trait LockStatus extends Serializable with Product
final case object Available extends LockStatus
final case object Locked extends LockStatus