package org.broadinstitute.dsde.workbench.google

import cats.effect.IO
import cats.implicits._
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper
import org.broadinstitute.dsde.workbench.google.Generators._
import org.broadinstitute.dsde.workbench.google.GoogleStorageInterpreterSpec._
import org.broadinstitute.dsde.workbench.google.GoogleStorageInterpreters._
import org.scalacheck.Gen
import org.scalatest.{AsyncFlatSpec, Matchers}

import scala.concurrent.ExecutionContext

// AsyncFlatSpec currently doesn't work with scalacheck's forAll. It'll be supported in scalatest 3
class GoogleStorageInterpreterSpec extends AsyncFlatSpec with Matchers {
  "ioStorage storeObject" should "be able to upload an object" in {
    val bucketName = genGcsBucketName.sample.get
    val objectName = genGcsBlobName.sample.get
    val objectBody = genGcsObjectBody.sample.get
    val res = localStorage.storeObject(bucketName, objectName, objectBody, objectType).attempt.map(x => x.isRight shouldBe(true))
    res.unsafeToFuture()
  }

  "ioStorage getObject" should "be able to retrieve an object" in {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    val objectBody = genGcsObjectBody.sample.get
    val res = for {
      createdTime <- localStorage.storeObject(bucketName, blobName, objectBody, objectType)
      r <- localStorage.unsafeGetObject(bucketName, blobName)
    } yield {
      r.get.getBytes(Generators.utf8Charset) shouldBe(objectBody)
    }
    res.unsafeToFuture()
  }

  it should "return None if object doesn't exist" in {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    val res = for {
      r <- localStorage.unsafeGetObject(bucketName, blobName)
    } yield {
      r shouldBe(None)
    }
    res.unsafeToFuture()
  }

  "ioStorage removeObject" should "remove an object" in {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    val objectBody = genGcsObjectBody.sample.get
    val res = for {
      _ <- localStorage.storeObject(bucketName, blobName, objectBody, objectType)
      getBeforeDelete <- localStorage.unsafeGetObject(bucketName, blobName)
      _ <- localStorage.removeObject(bucketName, blobName)
      getAfterDelete <- localStorage.unsafeGetObject(bucketName, blobName)
    } yield {
      getBeforeDelete.get.getBytes(Generators.utf8Charset) shouldBe(objectBody)
      getAfterDelete shouldBe(None)
    }
    res.unsafeToFuture()
  }

  "ioStorage listObjectsWithPrefix" should "list all objects with a given prefix" in {
    val bucketName = genGcsBucketName.sample.get
    val prefix = "samePrefix"
    val blobNameWithPrefix = Gen.listOfN(4, genGcsBlobName).sample.get.map(x => GcsBlobName(s"$prefix${x.value}"))
    val blobNames = Gen.listOfN(5, genGcsBlobName).sample.get
    val allObjects = blobNameWithPrefix ++ blobNames
    val objectBody = genGcsObjectBody.sample.get
    val res = for {
      _ <- allObjects.parTraverse(obj => localStorage.storeObject(bucketName, obj, objectBody, objectType))
      allObjectsWithPrefix <- localStorage.unsafeListObjectsWithPrefix(bucketName, prefix)
    } yield {
      allObjectsWithPrefix.map(_.value) should contain theSameElementsAs (blobNameWithPrefix.map(_.value))
    }
    res.unsafeToFuture()
  }

  it should "list objects with / properly" in {
    val objectBody = genGcsObjectBody.sample.get
    val blobName = GcsBlobName("pet-254290011538078c723da@testproject.iam.gserviceaccount.com/806ad9bb-a9b7-4706-a29b-0d06a1a3519")
    val bucketName = genGcsBucketName.sample.get
    val res = for {
      _ <- localStorage.storeObject(bucketName, blobName, objectBody, objectType)
      allObjectsWithPrefix <- localStorage.unsafeListObjectsWithPrefix(bucketName, "pet-254290011538078c723da@testproject.iam.gserviceaccount.com/")
    } yield {
      allObjectsWithPrefix.map(_.value) should contain theSameElementsAs List("pet-254290011538078c723da@testproject.iam.gserviceaccount.com/806ad9bb-a9b7-4706-a29b-0d06a1a3519")
    }
    res.unsafeToFuture()
  }
}

object GoogleStorageInterpreterSpec {
  implicit val cs = IO.contextShift(ExecutionContext.global)
  val db = LocalStorageHelper.getOptions().getService()
  val localStorage = ioStorage(db, ExecutionContext.global)
  val objectType = "text/plain"
}