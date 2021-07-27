package org.broadinstitute.dsde.workbench.google2

import cats.effect._
import cats.effect.std.Dispatcher
import cats.syntax.all._
import com.google.api.core.ApiFutures
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.firestore._

import java.time.Instant
import java.util.concurrent.Executor
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

private[google2] class GoogleFirestoreInterpreter[F[_]](db: Firestore)(implicit
  F: Async[F],
  ec: ExecutionContext
) extends GoogleFirestoreService[F] {
  override def set(collectionName: CollectionName, document: Document, dataMap: Map[String, Any]): F[Instant] = {
    val docRef =
      db.collection(collectionName.asString).document(document.asString)
    F
      .async[WriteResult] { cb =>
        F.delay(
          ApiFutures
            .addCallback(docRef.set(dataMap.asJava), callBack(cb), executor)
        ).as(None)
      }
      .map(x => Instant.ofEpochSecond(x.getUpdateTime.getSeconds))
  }

  override def get(collectionName: CollectionName, document: Document): F[DocumentSnapshot] =
    F.async[DocumentSnapshot] { cb =>
      F.delay(
        ApiFutures.addCallback(
          db.collection(collectionName.asString)
            .document(document.asString)
            .get(),
          callBack(cb),
          executor
        )
      ).as(None)
    }

  def transaction[A](ops: (Firestore, Transaction) => F[A]): F[A] = Dispatcher[F].use {
d =>
    F.async[A] { cb =>
      F.delay(
        ApiFutures.addCallback(
          db.runTransaction { (transaction: Transaction) =>
            d.unsafeRunSync(ops.apply(db, transaction))
          },
          callBack(cb),
          executor
        )
      ).as(None)
    }
  }

  private val executor = new Executor {
    override def execute(command: Runnable): Unit = ec.execute(command)
  }
}

object GoogleFirestoreInterpreter {
  def apply[F[_]](db: Firestore)(implicit
    F: Async[F],
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
