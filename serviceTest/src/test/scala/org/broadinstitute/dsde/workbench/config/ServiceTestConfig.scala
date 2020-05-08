package org.broadinstitute.dsde.workbench.config

// configuration settings used internally by workbench-service-test
object ServiceTestConfig extends CommonConfig {
  object Users extends CommonUsers
  object GCS extends CommonGCS
  object Projects extends CommonProjects
  object FireCloud extends CommonFireCloud
  object ChromeSettings extends CommonChromeSettings
}
