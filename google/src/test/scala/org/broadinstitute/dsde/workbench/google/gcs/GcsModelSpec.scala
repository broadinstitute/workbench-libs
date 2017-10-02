package org.broadinstitute.dsde.workbench.google.gcs

import org.scalatest.{EitherValues, FlatSpecLike, Matchers}

/**
  * Created by rtitle on 9/28/17.
  */
class GcsModelSpec extends FlatSpecLike with Matchers with EitherValues {

  "gcs" should "generate valid bucket names" in {
    generateUniqueBucketName("myCluster").name should startWith ("mycluster-")
    generateUniqueBucketName("MyCluster").name should startWith ("mycluster-")
    generateUniqueBucketName("My_Cluster").name should startWith ("my_cluster-")
    generateUniqueBucketName("MY_CLUSTER").name should startWith ("my_cluster-")
    generateUniqueBucketName("MYCLUSTER").name should startWith ("mycluster-")
    generateUniqueBucketName("_myCluster_").name should startWith ("0mycluster_-")
    generateUniqueBucketName("my.cluster").name should startWith ("my.cluster-")
    generateUniqueBucketName("my+cluster").name should startWith ("mycluster-")
    generateUniqueBucketName("mY-?^&@%#@&^#cLuStEr.foo_bar").name should startWith ("my-cluster.foo_bar-")
    generateUniqueBucketName("googMyCluster").name should startWith ("g00gmycluster-")
    generateUniqueBucketName("my_Google_clUsTer").name should startWith("my_g00gle_cluster-")
    generateUniqueBucketName("myClusterWhichHasAVeryLongNameBecauseIAmExtremelyVerboseInMyDescriptions").name should startWith ("myclusterwhichhasaverylong-")
    generateUniqueBucketName("myClusterWhichHasAVeryLongNameBecauseIAmExtremelyVerboseInMyDescriptions").name.length shouldBe 63
  }

  "GcsPath" should "parse valid paths" in {
    GcsPath.parse("gs://a-bucket/a/relative/path").right.value shouldBe GcsPath(GcsBucketName("a-bucket"), GcsRelativePath("a/relative/path"))
    GcsPath.parse("gs://a-123-bucket/foo/").right.value shouldBe GcsPath(GcsBucketName("a-123-bucket"), GcsRelativePath("foo/"))
    GcsPath.parse("gs://a-bucket-123/").right.value shouldBe GcsPath(GcsBucketName("a-bucket-123"), GcsRelativePath(""))
    GcsPath.parse("gs://a-bucket-123").right.value shouldBe GcsPath(GcsBucketName("a-bucket-123"), GcsRelativePath(""))
    GcsPath.parse("//a-bucket-123/a/relative/path").right.value shouldBe GcsPath(GcsBucketName("a-bucket-123"), GcsRelativePath("a/relative/path"))
    GcsPath.parse("gs://a_123_bucket/a/relative/path").right.value shouldBe GcsPath(GcsBucketName("a_123_bucket"), GcsRelativePath("a/relative/path"))
    GcsPath.parse("gs://a.123.bucket.123/a/relative/path").right.value shouldBe GcsPath(GcsBucketName("a.123.bucket.123"), GcsRelativePath("a/relative/path"))
    GcsPath.parse("gs://a-bucket_123.456/a/relative/path").right.value shouldBe GcsPath(GcsBucketName("a-bucket_123.456"), GcsRelativePath("a/relative/path"))
    GcsPath.parse("gs://a-bucket-with-a-slightly-longer-name-42/a/relative/path").right.value shouldBe GcsPath(GcsBucketName("a-bucket-with-a-slightly-longer-name-42"), GcsRelativePath("a/relative/path"))
  }

  it should "fail to parse invalid paths" in {
    GcsPath.parse("foo").left.value shouldBe a [GcsParseError]
    GcsPath.parse("Foo/Bar").left.value shouldBe a [GcsParseError]
    GcsPath.parse("C:\\Windows\\System32").left.value shouldBe a [GcsParseError]
    GcsPath.parse("gs://aCamelCaseBucket/a/relative/path").left.value shouldBe a [GcsParseError]
    GcsPath.parse("gs://a+bucket/a/relative/path").left.value shouldBe a [GcsParseError]
    GcsPath.parse("gs://_abucket/a/relative/path").left.value shouldBe a [GcsParseError]
    GcsPath.parse("gs://abucket-/a/relative/path").left.value shouldBe a [GcsParseError]
    GcsPath.parse("gs://.abucket./a/relative/path").left.value shouldBe a [GcsParseError]
    GcsPath.parse("gs://a%buck%et/a/relative/path").left.value shouldBe a [GcsParseError]
    GcsPath.parse("gs://aO#&%)?et/a/relative/path").left.value shouldBe a [GcsParseError]
    GcsPath.parse("mailto:mduerst@ifi.unizh.ch").left.value shouldBe a [GcsParseError]
    GcsPath.parse("http://a-bucket/a/relative/path").left.value shouldBe a [GcsParseError]
    GcsPath.parse("ftp://a-bucket/a/relative/path").left.value shouldBe a [GcsParseError]
    GcsPath.parse("file:///a-bucket/a/relative/path").left.value shouldBe a [GcsParseError]
    GcsPath.parse("gs://a-bucket-which-has-a-very-long-name-because-i-am-extremely-verbose-in-my-descriptions-but-is-otherwise-valid/foo").left.value shouldBe a [GcsParseError]
  }

  it should "return valid URIs" in {
    GcsPath(GcsBucketName("a-bucket"), GcsRelativePath("a/relative/path")).toUri shouldBe "gs://a-bucket/a/relative/path"
    GcsPath(GcsBucketName("b-bucket"), GcsRelativePath("foo")).toUri shouldBe "gs://b-bucket/foo"
    GcsPath(GcsBucketName("c-bucket"), GcsRelativePath("")).toUri shouldBe "gs://c-bucket/"
    // No validation happens here
    GcsPath(GcsBucketName("aCamelCaseBucket"), GcsRelativePath("foo/bar")).toUri shouldBe "gs://aCamelCaseBucket/foo/bar"
  }

  it should "roundtrip correctly" in {
    val testStr = "gs://a-bucket/a/relative/path"
    val expectedGcsPath = GcsPath(GcsBucketName("a-bucket"), GcsRelativePath("a/relative/path"))
    GcsPath.parse(testStr).right.value shouldBe expectedGcsPath
    expectedGcsPath.toUri shouldBe testStr
  }

}
