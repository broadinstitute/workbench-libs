package org.broadinstitute.dsde.workbench.google2

import java.nio.charset.Charset
import java.time.Instant

import org.broadinstitute.dsde.workbench.model.google._
import org.scalacheck.Gen

object Generators {
  val utf8Charset = Charset.forName("UTF-8")
  val genGoogleProject: Gen[GoogleProject] = Gen.uuid.map(x => GoogleProject(s"bucket${x.toString}"))
  val genGcsBucketName: Gen[GcsBucketName] = Gen.uuid.map(x => GcsBucketName(s"bucket${x.toString}"))
  val genGcsBlobName: Gen[GcsBlobName] = Gen.uuid.map(x => GcsBlobName(s"object${x.toString}"))
  val genGcsObjectName: Gen[GcsObjectName] = for{
    blobName <- genGcsBlobName
    createdTime <- Gen.calendar.map(x => Instant.ofEpochMilli(x.getTimeInMillis))
  } yield GcsObjectName(blobName.value, createdTime)
  val genGcsObjectBody: Gen[Array[Byte]] = Gen.alphaStr.map(x => x.getBytes(utf8Charset))
}
