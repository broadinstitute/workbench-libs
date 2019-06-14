package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import cats.implicits._
import fs2.Stream
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper
import io.chrisdavenport.linebacker.Linebacker
import org.broadinstitute.dsde.workbench.google2.Generators._
import org.broadinstitute.dsde.workbench.google2.GoogleStorageInterpreter._
import org.broadinstitute.dsde.workbench.google2.GoogleStorageInterpreterSpec._
import org.broadinstitute.dsde.workbench.util.WorkbenchTest
import org.scalacheck.Gen
import org.scalatest.{AsyncFlatSpec, Matchers}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext

// AsyncFlatSpec currently doesn't work with scalacheck's forAll. It'll be supported in scalatest 3
class GoogleStorageInterpreterSpec extends AsyncFlatSpec with Matchers with WorkbenchTest {
  "ioStorage storeObject" should "be able to upload an object" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val objectName = genGcsBlobName.sample.get
    val objectBody = genGcsObjectBody.sample.get
    localStorage.storeObject(bucketName, objectName, objectBody, objectType).compile.drain.attempt.map(x => x.isRight shouldBe(true))
  }

  "ioStorage unsafeGetObject" should "be able to retrieve an object" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    val objectBody = genGcsObjectBody.sample.get
    for {
      _ <- localStorage.storeObject(bucketName, blobName, objectBody, objectType).compile.drain
      r <- localStorage.unsafeGetObjectBody(bucketName, blobName)
    } yield {
      r.get.getBytes(Generators.utf8Charset) shouldBe(objectBody)
    }
  }

  it should "return None if object doesn't exist" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    for {
      r <- localStorage.unsafeGetObjectBody(bucketName, blobName)
    } yield {
      r shouldBe(None)
    }
  }

  "ioStorage getBlob" should "be able to get blob" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    for {
      _ <- localStorage.storeObject(bucketName, blobName, "test".getBytes("UTF-8")).compile.drain
      r <- localStorage.getBlob(bucketName, blobName, None).compile.lastOrError
    } yield {
      r.getBucket shouldBe bucketName.value
      r.getBlobId.getName shouldBe(blobName.value)
    }
  }

  "ioStorage getObjectMetadata" should "return GetMetadataResponse.NotFound if object doesn't exist" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    for {
      r <- localStorage.getObjectMetadata(bucketName, blobName, None).compile.lastOrError
    } yield {
      r shouldBe(GetMetadataResponse.NotFound)
    }
  }

  "ioStorage removeObject" should "remove an object" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    val objectBody = genGcsObjectBody.sample.get
    for {
      _ <- localStorage.storeObject(bucketName, blobName, objectBody, objectType).compile.drain
      getBeforeDelete <- localStorage.unsafeGetObjectBody(bucketName, blobName)
      _ <- localStorage.removeObject(bucketName, blobName).compile.drain
      getAfterDelete <- localStorage.unsafeGetObjectBody(bucketName, blobName)
    } yield {
      getBeforeDelete.get.getBytes(Generators.utf8Charset) shouldBe(objectBody)
      getAfterDelete shouldBe(None)
    }
  }

  "ioStorage listObjectsWithPrefix" should "list all objects with a given prefix" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val prefix = "samePrefix"
    val blobNameWithPrefix = Gen.listOfN(4, genGcsBlobName).sample.get.map(x => GcsBlobName(s"$prefix${x.value}"))
    val blobNames = Gen.listOfN(5, genGcsBlobName).sample.get
    val allObjects = blobNameWithPrefix ++ blobNames
    val objectBody = genGcsObjectBody.sample.get
    for {
      _ <- allObjects.parTraverse(obj => localStorage.storeObject(bucketName, obj, objectBody, objectType).compile.drain)
      allObjectsWithPrefix <- localStorage.unsafeListObjectsWithPrefix(bucketName, prefix)
    } yield {
      allObjectsWithPrefix.map(_.value) should contain theSameElementsAs (blobNameWithPrefix.map(_.value))
    }
  }

  it should "list objects with / properly" in ioAssertion {
    val objectBody = genGcsObjectBody.sample.get
    val blobName = GcsBlobName("pet-254290011538078c723da@testproject.iam.gserviceaccount.com/806ad9bb-a9b7-4706-a29b-0d06a1a3519")
    val bucketName = genGcsBucketName.sample.get
    for {
      _ <- localStorage.storeObject(bucketName, blobName, objectBody, objectType).compile.drain
      allObjectsWithPrefix <- localStorage.unsafeListObjectsWithPrefix(bucketName, "pet-254290011538078c723da@testproject.iam.gserviceaccount.com/")
    } yield {
      allObjectsWithPrefix.map(_.value) should contain theSameElementsAs List("pet-254290011538078c723da@testproject.iam.gserviceaccount.com/806ad9bb-a9b7-4706-a29b-0d06a1a3519")
    }
  }

  it should "retrieve multiple pages" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val prefix = Gen.uuid.sample.get.toString
    val blobNameWithPrefix = Gen.listOfN(4, genGcsBlobName).sample.get.map(x => GcsBlobName(s"$prefix${x.value}"))
    val objectBody = genGcsObjectBody.sample.get
    for {
      _ <- blobNameWithPrefix.parTraverse(obj => localStorage.storeObject(bucketName, obj, objectBody, objectType).compile.drain)
      allObjectsWithPrefix <- localStorage.unsafeListObjectsWithPrefix(bucketName, prefix, 1)
      _ <- allObjectsWithPrefix.traverse(obj => localStorage.removeObject(bucketName, GcsBlobName(obj.value), None).compile.drain) //clean up test objects
    } yield {
      allObjectsWithPrefix.map(_.value) should contain theSameElementsAs (blobNameWithPrefix.map(_.value))
    }
  }

  it should "do not list duplicate entries" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val prefix = "yetAnotherPrefix"
    val blobNameWithPrefix = genGcsBlobName.map(x => GcsBlobName(s"$prefix$x")).sample.get
    val duplicateBlobs = Stream(blobNameWithPrefix).repeat.take(3).toList
    val objectBody = genGcsObjectBody.sample.get
    for {
      _ <- duplicateBlobs.parTraverse(obj => localStorage.storeObject(bucketName, obj, objectBody, objectType).compile.drain)
      allObjectsWithPrefix <- localStorage.listObjectsWithPrefix(bucketName, prefix, 1).compile.toList
    } yield {
      allObjectsWithPrefix.map(_.value) should contain theSameElementsAs List(blobNameWithPrefix.value)
    }
  }
}

object GoogleStorageInterpreterSpec {
  implicit val cs = IO.contextShift(ExecutionContext.global)
  implicit val timer = IO.timer(ExecutionContext.global)
  implicit val logger = Slf4jLogger.getLogger[IO]
  implicit val lineBacker = Linebacker.fromExecutionContext[IO](ExecutionContext.global)

  val db = LocalStorageHelper.getOptions().getService()
  val localStorage = GoogleStorageInterpreter[IO](db, defaultRetryConfig)
  val objectType = "text/plain"
}
