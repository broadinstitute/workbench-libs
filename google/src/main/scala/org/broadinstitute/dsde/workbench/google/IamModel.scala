package org.broadinstitute.dsde.workbench.google

import org.broadinstitute.dsde.workbench.google.GoogleIamDAO.MemberType
import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import cats.instances.list._
import cats.instances.set._
import cats.instances.map._
import cats.syntax.foldable._
import cats.syntax.semigroup._

object IamModel {

  case class Binding(role: String, members: Set[String], condition: Expr)

  case class Policy(bindings: Set[Binding], etag: String)

  case class Expr(description: String, expression: String, location: String, title: String)

  /**
   * Read-modify-write a Policy to insert or remove new bindings for the given member and roles.
   * The optional condition, if present, will be applied to new role bindings for the member, not existing ones
   * Note that if the same role is in both rolesToAdd and rolesToRemove, the deletion takes precedence.
   */
  def updatePolicy(policy: Policy,
                   email: WorkbenchEmail,
                   memberType: MemberType,
                   rolesToAdd: Set[String],
                   rolesToRemove: Set[String],
                   condition: Option[Expr]
  ): Policy =
    condition match {
      case Some(realCondition) =>
        updatePolicyWithCondition(policy, email, memberType, rolesToAdd, rolesToRemove, realCondition)
      case None => updatePolicyWithoutCondition(policy, email, memberType, rolesToAdd, rolesToRemove)
    }

  private def updatePolicyWithCondition(policy: Policy,
                                        email: WorkbenchEmail,
                                        memberType: MemberType,
                                        rolesToAdd: Set[String],
                                        rolesToRemove: Set[String],
                                        condition: Expr
  ): Policy = {
    val memberTypeAndEmail = s"$memberType:${email.value}"
    val withRolesRemoved = updatePolicyWithoutCondition(policy, email, memberType, Set.empty, rolesToRemove)
    val bindingsWithCondition = rolesToAdd.map(role => Binding(role, Set(memberTypeAndEmail), condition))
    Policy(withRolesRemoved.bindings ++ bindingsWithCondition, withRolesRemoved.etag)
  }

  private def updatePolicyWithoutCondition(policy: Policy,
                                           email: WorkbenchEmail,
                                           memberType: MemberType,
                                           rolesToAdd: Set[String],
                                           rolesToRemove: Set[String]
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
      Binding(role, members, null)
    }.toSet

    Policy(bindings, policy.etag)
  }
}
