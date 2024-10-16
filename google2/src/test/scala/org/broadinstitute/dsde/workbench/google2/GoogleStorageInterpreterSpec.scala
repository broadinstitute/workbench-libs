package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import cats.effect.std.Semaphore
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper
import fs2.Stream
import org.broadinstitute.dsde.workbench.google2.Generators._
import org.broadinstitute.dsde.workbench.google2.GoogleStorageInterpreterSpec._
import org.broadinstitute.dsde.workbench.util2.WorkbenchTestSuite
import org.scalacheck.Gen
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.SelfAwareStructuredLogger

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator

// AsyncFlatSpec currently doesn't work with scalacheck's forAll. It'll be supported in scalatest 3
class GoogleStorageInterpreterSpec extends AsyncFlatSpec with Matchers with WorkbenchTestSuite {
  "ioStorage storeObject" should "be able to upload an object" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val objectName = genGcsBlobName.sample.get
    val objectBody = genGcsObjectBody.sample.get
    localStorage
      .createBlob(bucketName, objectName, objectBody, objectType)
      .compile
      .drain
      .attempt
      .map(x => x.isRight shouldBe true)
  }

  "ioStorage streamUploadBlob" should "be able to streamUpload an object" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val objectName = genGcsBlobName.sample.get
    val objectBody = genGcsObjectBody.sample.get
    localStorage
      .createBlob(bucketName, objectName, objectBody, objectType)
      .compile
      .drain
      .attempt
      .map(x => x.isRight shouldBe true)

    for {
      _ <- (Stream
        .emits(objectBody)
        .covary[IO] through localStorage.streamUploadBlob(bucketName, objectName)).compile.drain
      blob <- localStorage.getBlobBody(bucketName, objectName).compile.to(Array)
    } yield blob shouldBe objectBody
  }

  "ioStorage unsafeGetBlobBody" should "be able to retrieve an object" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    val objectBody = genGcsObjectBody.sample.get
    for {
      _ <- localStorage.createBlob(bucketName, blobName, objectBody, objectType).compile.drain
      r <- localStorage.unsafeGetBlobBody(bucketName, blobName)
    } yield r.get.getBytes(Generators.utf8Charset) shouldBe objectBody
  }

  "ioStorage getBlobBody" should "be able to retrieve an object's stream" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    val objectBody = genGcsObjectBody.sample.get
    for {
      _ <- localStorage.createBlob(bucketName, blobName, objectBody, objectType).compile.drain
      stream <- localStorage.getBlobBody(bucketName, blobName).compile.toList
    } yield stream.take(5) shouldBe (objectBody.take(5))
  }

  it should "return None if object doesn't exist" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    for {
      r <- localStorage.unsafeGetBlobBody(bucketName, blobName)
    } yield r shouldBe None
  }

  "ioStorage getBlob" should "be able to get blob" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    for {
      _ <- localStorage.createBlob(bucketName, blobName, "test".getBytes("UTF-8")).compile.drain
      r <- localStorage.getBlob(bucketName, blobName, None).compile.lastOrError
    } yield {
      r.getBucket shouldBe bucketName.value
      r.getBlobId.getName shouldBe (blobName.value)
    }
  }

  "ioStorage getSignedBlobUrl" should "be able to get a signed blob URL" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    val person = genPerson.sample.get

    val keyGen = KeyPairGenerator.getInstance("RSA")
    keyGen.initialize(2048)
    val pair = keyGen.genKeyPair()
    val serviceAccountCredentials = ServiceAccountCredentials
      .newBuilder()
      .setServiceAccountUser(person.name)
      .setClientEmail(person.email)
      .setPrivateKey(pair.getPrivate)
      .build()

    for {
      _ <- localStorage.createBlob(bucketName, blobName, "test".getBytes("UTF-8")).compile.drain
      url <- localStorage.getSignedBlobUrl(bucketName, blobName, serviceAccountCredentials).compile.lastOrError
    } yield url.getFile should startWith(s"/${bucketName.value}/${blobName.value}")
  }

  it should "attach provided query params to a signed URL for a blob" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    val person = genPerson.sample.get

    val keyGen = KeyPairGenerator.getInstance("RSA")
    keyGen.initialize(2048)
    val pair = keyGen.genKeyPair()
    val serviceAccountCredentials = ServiceAccountCredentials
      .newBuilder()
      .setServiceAccountUser(person.name)
      .setClientEmail(person.email)
      .setPrivateKey(pair.getPrivate)
      .build()

    val queryParams = Map("aParam" -> "a-param-value-1", "anEmail" -> "an-email@email.com")

    for {
      _ <- localStorage.createBlob(bucketName, blobName, "test".getBytes("UTF-8")).compile.drain
      url <- localStorage
        .getSignedBlobUrl(bucketName, blobName, serviceAccountCredentials, queryParams = queryParams)
        .compile
        .lastOrError
    } yield {
      url.getFile should startWith(s"/${bucketName.value}/${blobName.value}")
      url.toString should include("aParam=a-param-value-1")
      url.toString should include(s"anEmail=${URLEncoder.encode("an-email@email.com", StandardCharsets.UTF_8)}")
    }
  }

  it should "create a signed URL for a blob that doesn't exist" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    val person = genPerson.sample.get

    val keyGen = KeyPairGenerator.getInstance("RSA")
    keyGen.initialize(2048)
    val pair = keyGen.genKeyPair()
    val serviceAccountCredentials = ServiceAccountCredentials
      .newBuilder()
      .setServiceAccountUser(person.name)
      .setClientEmail(person.email)
      .setPrivateKey(pair.getPrivate)
      .build()

    for {
      url <- localStorage.getSignedBlobUrl(bucketName, blobName, serviceAccountCredentials).compile.lastOrError
      r <- localStorage.unsafeGetBlobBody(bucketName, blobName)
    } yield {
      url.getFile should startWith(s"/${bucketName.value}/${blobName.value}")
      r should be(None)
    }
  }

  "ioStorage getObjectMetadata" should "return GetMetadataResponse.NotFound if object doesn't exist" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    for {
      r <- localStorage.getObjectMetadata(bucketName, blobName, None).compile.lastOrError
    } yield r shouldBe (GetMetadataResponse.NotFound)
  }

  "ioStorage removeObject" should "remove an object" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val blobName = genGcsBlobName.sample.get
    val objectBody = genGcsObjectBody.sample.get
    for {
      _ <- localStorage.createBlob(bucketName, blobName, objectBody, objectType).compile.drain
      getBeforeDelete <- localStorage.unsafeGetBlobBody(bucketName, blobName)
      _ <- localStorage.removeObject(bucketName, blobName).compile.drain
      getAfterDelete <- localStorage.unsafeGetBlobBody(bucketName, blobName)
    } yield {
      getBeforeDelete.get.getBytes(Generators.utf8Charset) shouldBe objectBody
      getAfterDelete shouldBe None
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
      _ <- allObjects.parTraverse(obj => localStorage.createBlob(bucketName, obj, objectBody, objectType).compile.drain)
      allObjectsWithPrefix <- localStorage.unsafeListObjectsWithPrefix(bucketName, prefix)
    } yield allObjectsWithPrefix.map(_.value) should contain theSameElementsAs (blobNameWithPrefix.map(_.value))
  }

  it should "list objects with / properly" in ioAssertion {
    val objectBody = genGcsObjectBody.sample.get
    val blobName =
      GcsBlobName("pet-254290011538078c723da@testproject.iam.gserviceaccount.com/806ad9bb-a9b7-4706-a29b-0d06a1a3519")
    val bucketName = genGcsBucketName.sample.get
    for {
      _ <- localStorage.createBlob(bucketName, blobName, objectBody, objectType).compile.drain
      allObjectsWithPrefix <- localStorage.unsafeListObjectsWithPrefix(
        bucketName,
        "pet-254290011538078c723da@testproject.iam.gserviceaccount.com/"
      )
    } yield allObjectsWithPrefix.map(_.value) should contain theSameElementsAs List(
      "pet-254290011538078c723da@testproject.iam.gserviceaccount.com/806ad9bb-a9b7-4706-a29b-0d06a1a3519"
    )
  }

  // disabled because the LocalStorageHelper does not seem to support pagination of results
  ignore should "retrieve multiple pages" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val prefix = Gen.uuid.sample.get.toString
    val blobNameWithPrefix = Gen.listOfN(4, genGcsBlobName).sample.get.map(x => GcsBlobName(s"$prefix${x.value}"))
    val objectBody = genGcsObjectBody.sample.get
    for {
      _ <- blobNameWithPrefix.parTraverse(obj =>
        localStorage.createBlob(bucketName, obj, objectBody, objectType).compile.drain
      )
      allObjectsWithPrefix <- localStorage.unsafeListObjectsWithPrefix(bucketName, prefix, 1)
      _ <- allObjectsWithPrefix.traverse(obj =>
        localStorage.removeObject(bucketName, GcsBlobName(obj.value), None).compile.drain
      ) // clean up test objects
    } yield allObjectsWithPrefix.map(_.value) should contain theSameElementsAs (blobNameWithPrefix.map(_.value))
  }

  it should "do not list duplicate entries" in ioAssertion {
    val bucketName = genGcsBucketName.sample.get
    val prefix = "yetAnotherPrefix"
    val blobNameWithPrefix = genGcsBlobName.map(x => GcsBlobName(s"$prefix$x")).sample.get
    val duplicateBlobs = Stream(blobNameWithPrefix).repeat.take(3).toList
    val objectBody = genGcsObjectBody.sample.get
    for {
      _ <- duplicateBlobs.parTraverse(obj =>
        localStorage.createBlob(bucketName, obj, objectBody, objectType).compile.drain
      )
      allObjectsWithPrefix <- localStorage.listObjectsWithPrefix(bucketName, prefix, false, 1).compile.toList
    } yield allObjectsWithPrefix.map(_.value) should contain theSameElementsAs List(blobNameWithPrefix.value)
  }
}

object GoogleStorageInterpreterSpec {
  implicit val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  val db = LocalStorageHelper.getOptions().getService()
  val semaphore = Semaphore[IO](1).unsafeRunSync
  val localStorage = GoogleStorageInterpreter[IO](db, Some(semaphore))
  val objectType = "text/plain"
}
