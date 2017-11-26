package org.broadinstitute.dsde.workbench.model

package object google {
  def isServiceAccount(email: WorkbenchEmail): Boolean = {
    email.value.endsWith(".gserviceaccount.com")
  }

  def toAccountName(serviceAccountEmail: WorkbenchEmail): ServiceAccountName = {
    ServiceAccountName(serviceAccountEmail.value.split("@")(0))
  }
}
