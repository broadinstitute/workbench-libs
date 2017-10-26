package org.broadinstitute.dsde.workbench.google.model

import org.broadinstitute.dsde.workbench.model.{ValueObject, ValueObjectFormat}

case class GoogleProject(value: String) extends ValueObject

object GoogleModelJsonSupport {
  implicit val GoogleProjectFormat = ValueObjectFormat(GoogleProject)
}