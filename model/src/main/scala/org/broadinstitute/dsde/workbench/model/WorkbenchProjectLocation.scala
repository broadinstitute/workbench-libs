package org.broadinstitute.dsde.workbench.model

sealed case class WorkbenchProjectLocation(name : String, storageClass : String, storageLocation: String, computeRegionAndZones : Map[String, Seq[String]] )

object WorkbenchProjectLocation {

  // empty, default to US
  object US extends WorkbenchProjectLocation("us", "multi_regional", "us",
   Map("us-central1" -> List("us-central1-b", "us-central1-c", "us-central1-f")) )

  object Finland extends WorkbenchProjectLocation("finland", "regional", "europe-north1", Map("europe-north1" -> List("europe-north1-a", "europe-north1-a", "europe-north1-c")))
  object Japan extends WorkbenchProjectLocation("japan", "regional", "asia-northeast1", Map("asia-northeast1" -> List("asia-northeast1-a","asia-northeast1-b","asia-northeast1-c")))
  object Australia extends WorkbenchProjectLocation( name = "australia", "regional","australia-southeast1", Map("australia-southeast1" -> List("australia-southeast1-a", "australia-southeast1-b", "australia-southeast1-c")))

  def fromName(name: String): Option[WorkbenchProjectLocation] = name.toLowerCase match {
    case US.name => Some(US)
    case "" => Some(US)
    case Finland.name => Some(Finland)
    case Japan.name => Some(Japan)
    case Australia.name => Some(Australia)
    case _ => None
  }
}


object WorkbenchProjectLocationJsonSupport {
  import spray.json.DefaultJsonProtocol._

  implicit val WorkbenchProjectLocationFormat = jsonFormat4(WorkbenchProjectLocation.apply)
}
