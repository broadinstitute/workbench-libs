package org.broadinstitute.dsde.workbench.model.google.iam

import org.broadinstitute.dsde.workbench.model.ValueObject

case class Binding(role: String, members: Set[String], condition: Expr)

case class Policy(bindings: Set[Binding], etag: String)

case class Expr(description: String, expression: String, location: String, title: String)

object IamMemberTypes {

  /**
   * Typing for the Google IAM member types as described at https://cloud.google.com/iam/docs/overview
   */
  sealed trait IamMemberType extends ValueObject

  final case object User extends IamMemberType { val value = "user" }
  final case object Group extends IamMemberType { val value = "group" }
  final case object ServiceAccount extends IamMemberType { val value = "serviceAccount" }
  final case object Domain extends IamMemberType { val value = "domain" }

  def withName(name: String): IamMemberType = name.toLowerCase() match {
    case "user"           => User
    case "group"          => Group
    case "serviceAccount" => ServiceAccount
    case "domain"         => Domain
  }
}

object IamResourceTypes {
  sealed trait IamResourceType extends ValueObject
  case object Bucket extends IamResourceType { val value = "bucket" }
  case object Project extends IamResourceType { val value = "project" }
  def withName(name: String): IamResourceType = name.toLowerCase() match {
    case "bucket"  => Bucket
    case "project" => Project
  }
}
