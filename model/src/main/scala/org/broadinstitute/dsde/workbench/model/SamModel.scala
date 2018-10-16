package org.broadinstitute.dsde.workbench.model

import spray.json.DefaultJsonProtocol

object SamJsonSupport {
  import DefaultJsonProtocol._
  import org.broadinstitute.dsde.workbench.model.WorkbenchIdentityJsonSupport._

  implicit val AccessPolicyMembershipFormat = jsonFormat3(AccessPolicyMembership.apply)

  implicit val AccessPolicyResponseEntryFormat = jsonFormat3(AccessPolicyResponseEntry.apply)
}

final case class AccessPolicyMembership(memberEmails: Set[WorkbenchEmail], actions: Set[String], roles: Set[String])
final case class AccessPolicyResponseEntry(policyName: String, policy: AccessPolicyMembership, email: WorkbenchEmail)