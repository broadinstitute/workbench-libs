package org.broadinstitute.dsde.workbench.google

import org.broadinstitute.dsde.workbench.google.GoogleIamDAO.MemberType
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import cats.instances.list._
import cats.instances.set._
import cats.instances.map._
import cats.syntax.foldable._
import cats.syntax.semigroup._
import org.broadinstitute.dsde.workbench.google.HttpGoogleIamDAO._

object IamModel {

  case class Binding(role: String, members: Set[String], condition: Expr)

  case class Policy(bindings: Set[Binding], etag: String)

  case class Expr(description: String, expression: String, location: String, title: String)

  /**
   * Read-modify-write a Policy to insert or remove new bindings for the given member and roles.
   * Note that if the same role is in both rolesToAdd and rolesToRemove, the deletion takes precedence.
   */
  def updatePolicy(policy: Policy,
                   email: WorkbenchEmail,
                   memberType: MemberType,
                   rolesToAdd: Set[String],
                   rolesToRemove: Set[String],
                   condition: Option[Expr] = None
  ): Policy = {
    val memberTypeAndEmail = s"$memberType:${email.value}"

    // Current members grouped by role
    val curMembersByRole: Map[String, Set[String]] = policy.bindings.toList.foldMap { binding =>
      Map(binding.role -> binding.members)
    }

    // Apply additions
    val withAdditions = if (rolesToAdd.nonEmpty) {
      val rolesToAddMap = rolesToAdd.map(_ -> Set(memberTypeAndEmail)).toMap
      curMembersByRole |+| rolesToAddMap
    } else {
      curMembersByRole
    }

    // Apply deletions
    val newMembersByRole = if (rolesToRemove.nonEmpty) {
      withAdditions.toList.foldMap { case (role, members) =>
        if (rolesToRemove.contains(role)) {
          val filtered = members.filterNot(_ == memberTypeAndEmail)
          if (filtered.isEmpty) Map.empty[String, Set[String]]
          else Map(role -> filtered)
        } else {
          Map(role -> members)
        }
      }
    } else {
      withAdditions
    }

    val bindings = newMembersByRole.map { case (role, members) =>
      Binding(role, members, condition.orNull)
    }.toSet

    Policy(bindings, policy.etag)
  }
}
