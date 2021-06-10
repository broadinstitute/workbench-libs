package org.broadinstitute.dsde.workbench.google2

import java.time.Instant
import java.util.concurrent.Executor

import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import com.google.api.core.ApiFutures
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.firestore._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

private[google2] class GoogleFirestoreInterpreter[F[_]](db: Firestore)(implicit effect: Effect[F], ec: ExecutionContext)
    extends GoogleFirestoreService[F] {
  override def set(collectionName: CollectionName, document: Document, dataMap: Map[String, Any]): F[Instant] = {
    val docRef =
      db.collection(collectionName.asString).document(document.asString)
    effect
      .async_[WriteResult] { cb =>
        ApiFutures
          .addCallback(docRef.set(dataMap.asJava), callBack(cb), executor)
      }
      .map(x => Instant.ofEpochSecond(x.getUpdateTime.getSeconds))
  }

  override def get(collectionName: CollectionName, document: Document): F[DocumentSnapshot] =
    effect.async_[DocumentSnapshot] { cb =>
      ApiFutures.addCallback(
        db.collection(collectionName.asString)
          .document(document.asString)
          .get(),
        callBack(cb),
        executor
      )
    }

  def transaction[A](ops: (Firestore, Transaction) => F[A]): F[A] =
    effect.async_[A] { cb =>
      ApiFutures.addCallback(
        db.runTransaction((transaction: Transaction) => ops.apply(db, transaction).toIO.unsafeRunSync()),
        callBack(cb),
        executor
      )
    }

  private val executor = new Executor {
    override def execute(command: Runnable): Unit = ec.execute(command)
  }
}

object GoogleFirestoreInterpreter {
  def apply[F[_]](db: Firestore)(implicit
    effect: Effect[F],
    ec: ExecutionContext
  ): GoogleFirestoreInterpreter[F] = new GoogleFirestoreInterpreter[F](db)

  def firestore[F[_]](
    pathToJson: String
  )(implicit sf: Sync[F]): Resource[F, Firestore] =
    for {
      credential <- org.broadinstitute.dsde.workbench.util2.readFile(pathToJson)
      db <- Resource.make[F, Firestore](
        sf.delay(
          FirestoreOptions
            .newBuilder()
            .setCredentials(ServiceAccountCredentials.fromStream(credential))
            .build()
            .getService
        )
      )(db => sf.delay(db.close()))
    } yield db
}
