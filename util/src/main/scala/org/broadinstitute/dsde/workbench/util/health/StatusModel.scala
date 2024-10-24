package org.broadinstitute.dsde.workbench.util.health

import org.broadinstitute.dsde.workbench.util.health.Subsystems.Subsystem
import org.broadinstitute.dsde.workbench.model.{ValueObject, ValueObjectFormat}
import spray.json.RootJsonFormat

case class SubsystemStatus(
  ok: Boolean,
  messages: Option[List[String]]
)

case class StatusCheckResponse(
  ok: Boolean,
  systems: Map[Subsystem, SubsystemStatus]
)

object Subsystems {
  sealed trait Subsystem extends ValueObject with Product with Serializable {
    override val value: String = this match {
      case Custom(name) => name
      case _            => this.productPrefix
    }
  }

  def withName(name: String): Subsystem =
    name match {
      case "Agora"          => Agora
      case "Consent"        => Consent
      case "Cromwell"       => Cromwell
      case "Database"       => Database
      case "GoogleBilling"  => GoogleBilling
      case "GoogleBuckets"  => GoogleBuckets
      case "GoogleDataproc" => GoogleDataproc
      case "GoogleGenomics" => GoogleGenomics
      case "GoogleGroups"   => GoogleGroups
      case "GoogleIam"      => GoogleIam
      case "GooglePubSub"   => GooglePubSub
      case "Leonardo"       => Leonardo
      case "LibraryIndex"   => LibraryIndex
      case "Mongo"          => Mongo
      case "OntologyIndex"  => OntologyIndex
      case "OpenDJ"         => OpenDJ
      case "Rawls"          => Rawls
      case "Sam"            => Sam
      case "Thurloe"        => Thurloe
      case customName       => Custom(customName)
    }

  case object Agora extends Subsystem
  case object Consent extends Subsystem
  case object Cromwell extends Subsystem
  case object Database extends Subsystem
  case object GoogleBilling extends Subsystem
  case object GoogleBuckets extends Subsystem
  case object GoogleDataproc extends Subsystem
  case object GoogleGenomics extends Subsystem
  case object GoogleGroups extends Subsystem
  case object GoogleIam extends Subsystem
  case object GooglePubSub extends Subsystem
  case object Leonardo extends Subsystem
  case object LibraryIndex extends Subsystem
  case object Mongo extends Subsystem
  case object OntologyIndex extends Subsystem
  case object OpenDJ extends Subsystem
  case object Rawls extends Subsystem
  case object Sam extends Subsystem
  case object Thurloe extends Subsystem
  case class Custom(customName: String) extends Subsystem
}

object StatusJsonSupport {
  import spray.json.DefaultJsonProtocol._

  implicit val SubsystemFormat: ValueObjectFormat[Subsystem] = ValueObjectFormat(Subsystems.withName)

  implicit val SubsystemStatusFormat: RootJsonFormat[SubsystemStatus] = jsonFormat2(SubsystemStatus)

  implicit val StatusCheckResponseFormat: RootJsonFormat[StatusCheckResponse] = jsonFormat2(StatusCheckResponse)
}
