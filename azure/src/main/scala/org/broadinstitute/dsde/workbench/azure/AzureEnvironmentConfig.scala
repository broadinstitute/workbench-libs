package org.broadinstitute.dsde.workbench.azure

import com.azure.core.management.AzureEnvironment

object AzureEnvironmentConfig {
  val AZURE_ENVIRONMENT_CONFIG = "AZURE_ENVIRONMENT"

  private val Azure: String = "AzureCloud"
  private val AzureGov: String = "AzureUSGovernmentCloud"

  def fromString(s: String): AzureEnvironment = s match {
    case AzureGov => AzureEnvironment.AZURE_US_GOVERNMENT
    case Azure    => AzureEnvironment.AZURE
    case _        => throw new IllegalArgumentException(s"Unknown Azure environment: $s")
  }

  def fromCurrentHostingEnv(): AzureEnvironment =
    fromString(scala.util.Properties.envOrElse(AZURE_ENVIRONMENT_CONFIG, Azure))
}
