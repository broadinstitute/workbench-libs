package org.broadinstitute.dsde.workbench.service

import org.broadinstitute.dsde.workbench.service.BillingProject.BillingProjectRole.BillingProjectRole
import org.broadinstitute.dsde.workbench.service.BillingProject.BillingProjectStatus.BillingProjectStatus

object BillingProject {

  object BillingProjectRole extends Enumeration {
    type BillingProjectRole = Value

    val User = Value("User")
    val Owner = Value("Owner")
  }

  object BillingProjectStatus extends Enumeration {
    type BillingProjectStatus = Value

    val Creating = Value("Creating")
    val Ready = Value("Ready")
    val Error = Value("Error")

    val terminalStates = List(Ready, Error)
    def isTerminal(status: BillingProjectStatus): Boolean = terminalStates.contains(status)
    def isActive(status: BillingProjectStatus): Boolean = !isTerminal(status)
  }

}

case class BillingProject(projectName: String, role: BillingProjectRole, creationStatus: BillingProjectStatus)
