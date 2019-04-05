package org.broadinstitute.dsde.workbench.service

import org.broadinstitute.dsde.workbench.service.BillingProject.Role.Role
import org.broadinstitute.dsde.workbench.service.BillingProject.Status.Status

object BillingProject {

  object Role extends Enumeration {
    type Role = Value

    val User = Value("User")
    val Owner = Value("Owner")
  }

  object Status extends Enumeration {
    type Status = Value

    val Creating = Value("Creating")
    val Ready = Value("Ready")
    val Error = Value("Error")

    val terminalStates = List(Ready, Error)
    def isTerminal(status: Status): Boolean = terminalStates.contains(status)
    def isActive(status: Status): Boolean = !isTerminal(status)
  }

}

case class BillingProject(projectName: String, role: Role, creationStatus: Status)