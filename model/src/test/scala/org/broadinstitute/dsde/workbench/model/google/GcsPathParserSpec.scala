package org.broadinstitute.dsde.workbench.model.google

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpecLike

class GcsPathParserSpec extends AnyFlatSpecLike with Matchers with EitherValues {

  "gcs" should "generate valid bucket names" in {
    generateUniqueBucketName("myCluster").value should startWith("mycluster-")
    generateUniqueBucketName("MyCluster").value should startWith("mycluster-")
    generateUniqueBucketName("My_Cluster").value should startWith("my_cluster-")
    generateUniqueBucketName("MY_CLUSTER").value should startWith("my_cluster-")
    generateUniqueBucketName("MYCLUSTER").value should startWith("mycluster-")
    generateUniqueBucketName("_myCluster_").value should startWith("0mycluster_-")
    generateUniqueBucketName("my.cluster").value should startWith("my.cluster-")
    generateUniqueBucketName("my+cluster").value should startWith("mycluster-")
    generateUniqueBucketName("mY-?^&@%#@&^#cLuStEr.foo_bar").value should startWith("my-cluster.foo_bar-")
    generateUniqueBucketName("googMyCluster").value should startWith("oomycluster-")
    generateUniqueBucketName("my_Google_clUsTer").value should startWith("my_oole_cluster-")
    generateUniqueBucketName("googoogle").value should startWith("oooole-")
    generateUniqueBucketName("myClusterWhichHasAVeryLongNameBecauseIAmExtremelyVerboseInMyDescriptions").value should startWith(
      "myclusterwhichhasaverylon-"
    )
    generateUniqueBucketName("myClusterWhichHasAVeryLongNameBecauseIAmExtremelyVerboseInMyDescriptions").value.length shouldBe 62
  }

  "gcs" should "generate valid bucket names when trimming the uuid" in {
    //trim the uuid
    generateUniqueBucketName("myClusterWhichHasAModeratelyLongName", false).value should startWith(
      "myclusterwhichhasamoderatelylonname-"
    )

    //trim the uuid but the prefix is also too long so trim that too
    generateUniqueBucketName("myClusterWhichHasAVeryLongNameBecauseIAmExtremelyVerboseInMyDescriptions", false).value should startWith(
      "myclusterwhichhasaverylonnamebecauseiamextremelyverboseinmyde-"
    )
    generateUniqueBucketName("myClusterWhichHasAVeryLongNameBecauseIAmExtremelyVerboseInMyDescriptions", false).value.length shouldBe 62
  }

  "GcsPath" should "parse valid paths" in {
    parseGcsPath("gs://a-bucket/a/relative/path").right.value shouldBe GcsPath(GcsBucketName("a-bucket"),
                                                                               GcsObjectName("a/relative/path"))
    parseGcsPath("gs://a-123-bucket/foo/").right.value shouldBe GcsPath(GcsBucketName("a-123-bucket"),
                                                                        GcsObjectName("foo/"))
    parseGcsPath("gs://a-bucket-123/").right.value shouldBe GcsPath(GcsBucketName("a-bucket-123"), GcsObjectName(""))
    parseGcsPath("gs://a-bucket-123").right.value shouldBe GcsPath(GcsBucketName("a-bucket-123"), GcsObjectName(""))
    parseGcsPath("//a-bucket-123/a/relative/path").right.value shouldBe GcsPath(GcsBucketName("a-bucket-123"),
                                                                                GcsObjectName("a/relative/path"))
    parseGcsPath("gs://a_123_bucket/a/relative/path").right.value shouldBe GcsPath(GcsBucketName("a_123_bucket"),
                                                                                   GcsObjectName("a/relative/path"))
    parseGcsPath("gs://a.123.bucket.123/a/relative/path").right.value shouldBe GcsPath(
      GcsBucketName("a.123.bucket.123"),
      GcsObjectName("a/relative/path")
    )
    parseGcsPath("gs://a-bucket_123.456/a/relative/path").right.value shouldBe GcsPath(
      GcsBucketName("a-bucket_123.456"),
      GcsObjectName("a/relative/path")
    )
    parseGcsPath("gs://a-bucket-with-a-slightly-longer-name-42/a/relative/path").right.value shouldBe GcsPath(
      GcsBucketName("a-bucket-with-a-slightly-longer-name-42"),
      GcsObjectName("a/relative/path")
    )
  }

  it should "fail to parse invalid paths" in {
    parseGcsPath("foo").left.value shouldBe a[GcsParseError]
    parseGcsPath("Foo/Bar").left.value shouldBe a[GcsParseError]
    parseGcsPath("C:\\Windows\\System32").left.value shouldBe a[GcsParseError]
    parseGcsPath("gs://aCamelCaseBucket/a/relative/path").left.value shouldBe a[GcsParseError]
    parseGcsPath("gs://a+bucket/a/relative/path").left.value shouldBe a[GcsParseError]
    parseGcsPath("gs://_abucket/a/relative/path").left.value shouldBe a[GcsParseError]
    parseGcsPath("gs://abucket-/a/relative/path").left.value shouldBe a[GcsParseError]
    parseGcsPath("gs://.abucket./a/relative/path").left.value shouldBe a[GcsParseError]
    parseGcsPath("gs://a%buck%et/a/relative/path").left.value shouldBe a[GcsParseError]
    parseGcsPath("gs://aO#&%)?et/a/relative/path").left.value shouldBe a[GcsParseError]
    parseGcsPath("mailto:mduerst@ifi.unizh.ch").left.value shouldBe a[GcsParseError]
    parseGcsPath("http://a-bucket/a/relative/path").left.value shouldBe a[GcsParseError]
    parseGcsPath("ftp://a-bucket/a/relative/path").left.value shouldBe a[GcsParseError]
    parseGcsPath("file:///a-bucket/a/relative/path").left.value shouldBe a[GcsParseError]
    parseGcsPath(
      "gs://a-bucket-which-has-a-very-long-name-because-i-am-extremely-verbose-in-my-descriptions-but-is-otherwise-valid/foo"
    ).left.value shouldBe a[GcsParseError]
  }

  it should "return valid URIs" in {
    GcsPath(GcsBucketName("a-bucket"), GcsObjectName("a/relative/path")).toUri shouldBe "gs://a-bucket/a/relative/path"
    GcsPath(GcsBucketName("b-bucket"), GcsObjectName("foo")).toUri shouldBe "gs://b-bucket/foo"
    GcsPath(GcsBucketName("c-bucket"), GcsObjectName("")).toUri shouldBe "gs://c-bucket/"
    // No validation happens here
    GcsPath(GcsBucketName("aCamelCaseBucket"), GcsObjectName("foo/bar")).toUri shouldBe "gs://aCamelCaseBucket/foo/bar"
  }

  it should "roundtrip correctly" in {
    val testStr = "gs://a-bucket/a/relative/path"
    val expectedGcsPath = GcsPath(GcsBucketName("a-bucket"), GcsObjectName("a/relative/path"))
    parseGcsPath(testStr).right.value shouldBe expectedGcsPath
    expectedGcsPath.toUri shouldBe testStr
  }

}
