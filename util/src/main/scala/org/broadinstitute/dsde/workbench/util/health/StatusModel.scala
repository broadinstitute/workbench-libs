package org.broadinstitute.dsde.workbench.util.health

import org.broadinstitute.dsde.workbench.util.health.Subsystems.Subsystem
import org.broadinstitute.dsde.workbench.model.{ValueObject, ValueObjectFormat, WorkbenchException}

case class SubsystemStatus(
                            ok: Boolean,
                            messages: Option[List[String]]
                          )

case class StatusCheckResponse(
                                ok: Boolean,
                                systems: Map[Subsystem, SubsystemStatus]
                              )

object Subsystems {
  sealed trait Subsystem extends ValueObject {
    override val value: String = getClass.getSimpleName.stripSuffix("$")
  }

  def withName(name: String): Subsystem = {
    name match {
      case "OpenDJ" => OpenDJ
      case "Agora" => Agora
      case "Cromwell" => Cromwell
      case "Database" => Database
      case "GoogleBilling" => GoogleBilling
      case "GoogleBuckets" => GoogleBuckets
      case "GoogleGenomics" => GoogleGenomics
      case "GoogleGroups" => GoogleGroups
      case "GooglePubSub" => GooglePubSub
      case "Sam" => Sam
      case "Thurloe" => Thurloe
      case "Mongo" => Mongo
      case _ => throw new WorkbenchException(s"invalid Subsystem [$name]")
    }
  }

  case object OpenDJ extends Subsystem
  case object Agora extends Subsystem
  case object Cromwell extends Subsystem
  case object Database extends Subsystem
  case object GoogleBilling extends Subsystem
  case object GoogleBuckets extends Subsystem
  case object GoogleGenomics extends Subsystem
  case object GoogleGroups extends Subsystem
  case object GooglePubSub extends Subsystem
  case object Sam extends Subsystem
  case object Thurloe extends Subsystem
  case object Mongo extends Subsystem
}

object StatusJsonSupport {
  import spray.json.DefaultJsonProtocol._

  implicit val SubsystemFormat = ValueObjectFormat(Subsystems.withName)

  implicit val SubsystemStatusFormat = jsonFormat2(SubsystemStatus)

  implicit val StatusCheckResponseFormat = jsonFormat2(StatusCheckResponse)
}