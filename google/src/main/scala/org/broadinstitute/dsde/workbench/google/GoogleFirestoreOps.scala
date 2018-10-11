package org.broadinstitute.dsde.workbench.google
import java.time.Instant

import com.google.cloud.firestore.{DocumentSnapshot, Firestore, Transaction}

import scala.language.higherKinds

trait GoogleFirestoreOps[F[_]] {
  def set(collectionName: CollectionName, document: Document, dataMap: Map[String, Any]): F[Instant]
  def get(collectionName: CollectionName, document: Document, fieldKey: FieldKey): F[DocumentSnapshot]
  def transaction[A](ops: (Firestore, Transaction) => A): F[A]
}


final case class CollectionName(asString: String) extends AnyVal
final case class Document(asString: String) extends AnyVal
final case class FieldKey(asString: String) extends AnyVal
