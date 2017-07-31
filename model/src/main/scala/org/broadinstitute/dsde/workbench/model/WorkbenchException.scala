package org.broadinstitute.dsde.workbench.model

class WorkbenchException(message: String = null, cause: Throwable = null) extends Exception(message, cause)

class WorkbenchExceptionWithErrorReport(val errorReport: ErrorReport) extends WorkbenchException(errorReport.toString)
