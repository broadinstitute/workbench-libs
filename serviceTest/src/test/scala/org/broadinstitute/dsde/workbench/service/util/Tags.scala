package org.broadinstitute.dsde.workbench.service.util

import org.scalatest.Tag

object Tags {
  object ProdTest extends Tag("ProdTest")
  object SmokeTest extends Tag("SmokeTest")
  object SignInRealTest extends Tag("SignInRealTest")
  object ExcludeInAlpha extends Tag("ExcludeInAlpha")
}
