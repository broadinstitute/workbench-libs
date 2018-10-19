package org.broadinstitute.dsde.workbench.google

import java.io.InputStream
import java.time.Instant
import java.util.concurrent.Executor

import cats.effect.{IO, Resource}
import com.google.api.core.{ApiFutureCallback, ApiFutures}
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.firestore._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


object GoogleFirestoreOpsInterpreters{
  def ioFirestore(db: Firestore)(implicit ec: ExecutionContext): GoogleFirestoreOps[IO] = new GoogleFirestoreOps[IO] {
    override def set(collectionName: CollectionName, document: Document, dataMap: Map[String, Any]): IO[Instant] = {
      val docRef = db.collection(collectionName.asString).document(document.asString)
      IO.async[WriteResult]{
        cb =>
          ApiFutures.addCallback(
            docRef.set(dataMap.asJava),
            callBack(cb),
            executor
          )
      }.map(x => Instant.ofEpochSecond(x.getUpdateTime.getSeconds))
    }

    override def get(collectionName: CollectionName, document: Document): IO[DocumentSnapshot] = {
      IO.async[DocumentSnapshot]{
        cb =>
          ApiFutures.addCallback(
            db.collection(collectionName.asString).document(document.asString).get(),
            callBack(cb),
            executor
          )
      }
    }

    def transaction[A](ops: (Firestore, Transaction) => A): IO[A] = {
      IO.async[A]{
        cb =>
          ApiFutures.addCallback(
            db.runTransaction(new Transaction.Function[A]{
              override def updateCallback(transaction: Transaction): A = ops(db, transaction)
            }),
            callBack(cb),
            executor
          )
      }
    }

    private val executor = new Executor {
      override def execute(command: Runnable): Unit = ec.execute(command)
    }
  }

  def futureFirestore(db: Firestore)(implicit ec: ExecutionContext): GoogleFirestoreOps[Future] = new GoogleFirestoreOps[Future] {
    val dao = ioFirestore(db)
    override def set(collectionName: CollectionName,
                     document: Document,
                     dataMap: Map[String, Any]): Future[Instant] = dao.set(collectionName, document, dataMap).unsafeToFuture()
    override def get(collectionName: CollectionName,
                     document: Document): Future[DocumentSnapshot] = dao.get(collectionName, document).unsafeToFuture()
    override def transaction[A](ops: (Firestore, Transaction) => A): Future[A] = {
      dao.transaction(ops).unsafeToFuture()
    }
  }

  def firestore(credential: InputStream): Resource[IO, Firestore] = Resource.make[IO, Firestore](IO(FirestoreOptions
    .newBuilder()
    .setTimestampsInSnapshotsEnabled(true)
    .setCredentials(ServiceAccountCredentials.fromStream(credential))
    .build()
    .getService
  ))(db => IO(db.close()))

  def callBack[A](cb: Either[Throwable, A] => Unit): ApiFutureCallback[A] = new ApiFutureCallback[A]{
    @Override def onFailure(t: Throwable): Unit = cb(Left(t))
    @Override def onSuccess(result: A): Unit = cb(Right(result))
  }
}