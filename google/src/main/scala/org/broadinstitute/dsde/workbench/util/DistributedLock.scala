package org.broadinstitute.dsde.workbench.google
package util

import java.util.concurrent.{Executor, TimeUnit}

import cats.effect.{IO, Resource, Timer}
import cats.implicits._
import com.google.api.core.ApiFutures
import com.google.cloud.firestore.{DocumentReference, DocumentSnapshot, Transaction}
import org.broadinstitute.dsde.workbench.google.GoogleFirestoreOpsInterpreters.callBack
import org.broadinstitute.dsde.workbench.model.WorkbenchException
import org.broadinstitute.dsde.workbench.google.util.DistributedLock._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class DistributedLock(config: DistributedLockConfig, val googleFirestoreOps: GoogleFirestoreOps[IO])(implicit ec: ExecutionContext, timer: Timer[IO]){

  // This is executor for java client's call back to return to. Hence it should be main implicit execution context
  val executor = new Executor {
    override def execute(command: Runnable): Unit = ec.execute(command)
  }

  def withLock(lock: LockPath): Resource[IO, Unit] = Resource.make{
    retry(acquireLock(lock), config.retryInterval, config.maxRetry)
  }(_ => releaseLock(lock))

  private[dsde] def acquireLock[A](lock: LockPath): IO[Unit] =
    googleFirestoreOps.transaction { (db, tx) =>
      val docRef = db.collection(lock.collectionName.asString).document(lock.document.asString)
      val res = for {
        lockStatus <- getLockStatus(tx, docRef)
        _ <- lockStatus match {
          case Available => setLock(tx, docRef, lock.expiresIn)
          case Locked => IO.raiseError(new WorkbenchException(s"can't get lock $lock"))
        }
      } yield ()
      res.unsafeRunSync()
    }

  private[dsde] def getLockStatus(tx: Transaction, documentReference: DocumentReference): IO[LockStatus] =
    for {
      nullableDocumentSnapshot <- IO.async[DocumentSnapshot] { cb =>
        ApiFutures.addCallback(
          tx.get(documentReference),
          callBack(cb),
          executor
        )
      }
      status <- Option(nullableDocumentSnapshot).fold[IO[LockStatus]](IO.pure(Available)) { documentSnapshot =>
        val expiresIn = Option(documentSnapshot.getLong(EXPIRESIN))
        expiresIn match {
          case Some(e) =>
            timer.clock.realTime(EXPIRESINTIMEUNIT).map { currentTime =>
              if (e < currentTime)
                Available
              else
                Locked
            }
          case None => Available.pure[IO]
        }
      }
    } yield status

  private[dsde] def releaseLock(lockPath: LockPath): IO[Unit] = {
    googleFirestoreOps.transaction{
      (db, tx) =>
        val docRef = db.collection(lockPath.collectionName.asString).document(lockPath.document.asString)
        tx.delete(docRef)
    }
  }
}

object DistributedLock {
  private[dsde] val EXPIRESIN = "expiresIn"
  private[dsde] val EXPIRESINTIMEUNIT = TimeUnit.MILLISECONDS

  def apply(config: DistributedLockConfig, googleFirestoreOps: GoogleFirestoreOps[IO])(implicit ec: ExecutionContext, timer: Timer[IO]): DistributedLock = new DistributedLock(config, googleFirestoreOps)

  private[dsde] def setLock(tx: Transaction, documentReference: DocumentReference, expiresIn: FiniteDuration)(implicit timer: Timer[IO]): IO[Unit] =
    for {
      currentTime <- timer.clock.realTime(EXPIRESINTIMEUNIT)
      data = Map(
        EXPIRESIN -> (currentTime + expiresIn.toMillis)
      )
      _ <- IO(tx.set(documentReference, data.asJava))
    } yield ()

  def retry[A](ioa: IO[A], interval: FiniteDuration, maxRetries: Int)
                         (implicit timer: Timer[IO]): IO[A] = {
    ioa.handleErrorWith {
      case e =>
        if (maxRetries > 0)
          IO.sleep(interval) *> retry(ioa, interval, maxRetries - 1)
        else
          IO.raiseError(new WorkbenchException(s"can't get lock: ${e}"))
    }
  }
}

final case class LockPath(collectionName: CollectionName, document: Document, expiresIn: FiniteDuration)
final case class DistributedLockConfig(retryInterval: FiniteDuration, maxRetry: Int)

sealed trait LockStatus extends Serializable with Product
final case object Available extends LockStatus
final case object Locked extends LockStatus