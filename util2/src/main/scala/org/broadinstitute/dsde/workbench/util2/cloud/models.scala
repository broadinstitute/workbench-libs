package org.broadinstitute.dsde.workbench.util2.cloud

import org.broadinstitute.dsde.workbench.model.google.GoogleProject

// this is NOT analogous to clusterName in the context of dataproc/GCE. A single cluster can have multiple nodes, pods, services, containers, deployments, etc.
// clusters should most likely NOT be provisioned per user as they are today. More design/security research is needed
final case class KubernetesClusterName(value: String) extends AnyVal

// TODO: fix location
final case class KubernetesClusterId(cloudContext: CloudContext, location: String, clusterName: KubernetesClusterName) {
  override lazy val toString: String =
    s"projects/${project.value}/locations/${location}/clusters/${clusterName.value}"
}
