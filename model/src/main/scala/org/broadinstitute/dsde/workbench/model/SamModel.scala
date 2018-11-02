package org.broadinstitute.dsde.workbench.model

import spray.json.DefaultJsonProtocol

object SamJsonSupport {
  import DefaultJsonProtocol._
  import org.broadinstitute.dsde.workbench.model.WorkbenchIdentityJsonSupport._

  implicit val AccessPolicyMembershipFormat = jsonFormat3(AccessPolicyMembership.apply)

  implicit val AccessPolicyResponseEntryFormat = jsonFormat3(AccessPolicyResponseEntry.apply)

  implicit val CreateResourceRequestFormat = jsonFormat3(CreateResourceRequest.apply)
}

final case class AccessPolicyMembership(memberEmails: Set[String], actions: Set[String], roles: Set[String])
final case class AccessPolicyResponseEntry(policyName: String, policy: AccessPolicyMembership, email: WorkbenchEmail)

final case class CreateResourceRequest(resourceId: String, policies: Map[String, AccessPolicyMembership], authDomain: Set[String])