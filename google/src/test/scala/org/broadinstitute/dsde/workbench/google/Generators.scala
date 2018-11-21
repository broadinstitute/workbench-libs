package org.broadinstitute.dsde.workbench.google

import java.nio.charset.Charset
import java.time.Instant

import org.broadinstitute.dsde.workbench.model.google._
import org.scalacheck.Gen

object Generators {
  val utf8Charset = Charset.forName("UTF-8")
  val genGoogleProject: Gen[GoogleProject] = Gen.alphaStr.map(x => GoogleProject(s"bucket$x"))
  val genGcsBucketName: Gen[GcsBucketName] = Gen.alphaStr.map(x => GcsBucketName(s"bucket$x"))
  val genGcsBlobName: Gen[GcsBlobName] = Gen.alphaStr.map(x => GcsBlobName(s"object$x"))
  val genGcsObjectName: Gen[GcsObjectName] = for{
    blobName <- genGcsBlobName
    createdTime <- Gen.calendar.map(x => Instant.ofEpochMilli(x.getTimeInMillis))
  } yield GcsObjectName(blobName.value, createdTime)
  val genGcsObjectBody: Gen[Array[Byte]] = Gen.alphaStr.map(x => x.getBytes(utf8Charset))
}
