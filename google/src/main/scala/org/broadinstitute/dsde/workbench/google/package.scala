package org.broadinstitute.dsde.workbench

import org.broadinstitute.dsde.workbench.model.ErrorReportSource

package object google {
  implicit val errorReportSource = ErrorReportSource("google")
}
