package org.broadinstitute.dsde.workbench.google

import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import cats.instances.list._
import cats.instances.set._
import cats.instances.map._
import cats.syntax.foldable._
import cats.syntax.semigroup._
import org.broadinstitute.dsde.workbench.model.google.iam.IamMemberTypes.IamMemberType
import org.broadinstitute.dsde.workbench.model.google.iam.{Binding, Expr, Policy}

object IamOperations {

  val policyVersion = 3

  /**
   * Read-modify-write a Policy to insert or remove new bindings for the given member and roles.
   * The optional condition, if present, will be applied to new role bindings for the member, not existing ones
   * Note that if the same role is in both rolesToAdd and rolesToRemove, the deletion takes precedence.
   */
  def updatePolicy(policy: Policy,
                   email: WorkbenchEmail,
                   memberType: IamMemberType,
                   rolesToAdd: Set[String],
                   rolesToRemove: Set[String],
                   condition: Option[Expr]
  ): Policy = {
    val memberTypeAndEmail = s"${memberType.value}:${email.value}"

    // Current members grouped by role
    val curMembersByRole: Map[(String, Expr), Set[String]] = policy.bindings.toList.foldMap { binding =>
      Map((binding.role, binding.condition) -> binding.members)
    }

    // Apply additions
    val withAdditions = if (rolesToAdd.nonEmpty) {
      val rolesToAddMap: Map[(String, Expr), Set[String]] =
        rolesToAdd.map(r => (r, condition.orNull) -> Set(memberTypeAndEmail)).toMap
      curMembersByRole |+| rolesToAddMap
    } else {
      curMembersByRole
    }

    // Apply deletions
    val newMembersByRole: Map[(String, Expr), Set[String]] = if (rolesToRemove.nonEmpty) {
      withAdditions.toList.foldMap { case (role, members) =>
        if (rolesToRemove.contains(role._1)) {
          val filtered = members.filterNot(_.equalsIgnoreCase(memberTypeAndEmail))
          if (filtered.isEmpty) Map.empty[(String, Expr), Set[String]]
          else Map(role -> filtered)
        } else {
          Map(role -> members)
        }
      }
    } else {
      withAdditions
    }

    val bindings = newMembersByRole.map { case (role, members) =>
      Binding(role._1, members, role._2)
    }.toSet

    Policy(bindings, policy.etag)
  }
}
