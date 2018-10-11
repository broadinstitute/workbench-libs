package org.broadinstitute.dsde.workbench.google

import java.time.Instant
import java.util.concurrent.Executor

//import cats.data.State
import cats.effect.{IO, Resource}
import com.google.api.core.{ApiFutureCallback, ApiFutures}
import com.google.cloud.firestore._
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

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

    override def get(collectionName: CollectionName, document: Document, fieldKey: FieldKey): IO[DocumentSnapshot] = {
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
//
//  def futureFirestore(db: Firestore)(implicit ec: ExecutionContext): GoogleFirestoreOps[Future] = new GoogleFirestoreOps[Future] {
//    val dao = ioFirestore(db)
//    override def set(collectionName: CollectionName,
//                     document: Document,
//                     dataMap: Map[String, Any]): Future[Instant] = dao.set(collectionName, document, dataMap).unsafeToFuture()
//    override def getString(collectionName: CollectionName,
//                     document: Document, fieldKey: FieldKey): Future[Option[String]] = dao.getString(collectionName, document, fieldKey).unsafeToFuture()
//    override def transaction[A](ops: (Firestore, Transaction) => Future[A]): Future[A] = {
//      dao.transaction((db, tx) => IO.fromFuture(IO(ops(db, tx)))).unsafeToFuture()
//    }
//  }
//  // This interpreter should only be used for testing
//  type TestDB = Map[String, Map[String, Any]]
//  type TestState[A] = State[TestDB, A]
//  val stateFireStore: GoogleFirestoreOps[TestState] = new GoogleFirestoreOps[TestState] {
//    def key(collectionName: CollectionName, document: Document): String = collectionName.asString + "-" + document.asString
//
//    override def set(collectionName: CollectionName,
//                     document: Document,
//                     dataMap: Map[String, Any]): TestState[Instant] = State {
//      mp =>
//        val data = key(collectionName, document) -> dataMap
//        (mp + data, Instant.now())
//    }
//
//    override def getString(collectionName: CollectionName,
//                           document: Document,
//                           fieldKey: FieldKey): TestState[Option[String]] = State.inspect[TestDB, Option[String]]{
//      mp =>
//        for{
//          d <- mp.get(key(collectionName, document))
//          r <- d.get(fieldKey.asString)
//          s <- Either.catchNonFatal(r.asInstanceOf[String]).toOption
//        } yield s
//    }
//
//    override def transaction[A](
//      ops: (Firestore, Transaction) => TestState[A]): TestState[A] = ops(null, null)
//  }
//
//  def testIOFirestore[A](initialState: TestDB): GoogleFirestoreOps[IO] = stateFireStore.mapK{
//    new ~>[TestState, IO] {
//      override def apply[A](fa: TestState[A]): IO[A] = IO(fa.runA(initialState).value)
//    }
//  }

  def firestore(projectId: GoogleProject): Resource[IO, Firestore] = Resource.make[IO, Firestore](IO(FirestoreOptions
    .getDefaultInstance()
    .toBuilder()
    .setTimestampsInSnapshotsEnabled(true)
    .setProjectId(projectId.value)
    .build()
    .getService
  ))(db => IO(db.close()))

  def callBack[A](cb: Either[Throwable, A] => Unit): ApiFutureCallback[A] = new ApiFutureCallback[A]{
    @Override def onFailure(t: Throwable): Unit = cb(Left(t))
    @Override def onSuccess(result: A): Unit = cb(Right(result))
  }
}