package org.broadinstitute.dsde.workbench.service.util

import org.scalatest.Tag

object Tags {

  object SmokeTest extends Tag("SmokeTest")
  object SignInRealTest extends Tag("SignInRealTest")

  // intent: do not run tagged tests in given environment
  object ExcludeInDev extends Tag("ExcludeInDev")
  object ExcludeInFiab extends Tag("ExcludeInFiab")
  object ExcludeInAlpha extends Tag("ExcludeInAlpha")
  object ExcludeInPerf extends Tag("ExcludeInPerf")
  object ExcludeInStaging extends Tag("ExcludeInStaging")
  object ExcludeInProd extends Tag("ExcludeInProd")

  // intent: run tagged tests in given environment
  object DevTest extends Tag("DevTest")
  object FiabTest extends Tag("FiabTest")
  object AlphaTest extends Tag("AlphaTest")
  object PerfTest extends Tag("PerfTest")
  object StagingTest extends Tag("StagingTest")
  object ProdTest extends Tag("ProdTest")

}
