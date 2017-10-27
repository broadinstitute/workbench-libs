package org.broadinstitute.dsde.workbench.google

import org.broadinstitute.dsde.workbench.model.WorkbenchEmail

package object model {
  def isServiceAccount(email: WorkbenchEmail): Boolean = {
    email.value.endsWith(".gserviceaccount.com")
  }
}
