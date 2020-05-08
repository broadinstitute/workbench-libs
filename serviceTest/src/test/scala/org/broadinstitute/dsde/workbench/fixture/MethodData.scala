package org.broadinstitute.dsde.workbench.fixture

case class Method(methodName: String,
                  methodNamespace: String,
                  snapshotId: Int,
                  rootEntityType: String,
                  synopsis: String,
                  documentation: String,
                  payload: String) {

  def creationAttributes =
    Map("namespace" -> methodNamespace,
        "name" -> methodName,
        "synopsis" -> synopsis,
        "documentation" -> documentation,
        "payload" -> payload,
        "entityType" -> "Workflow")

  def methodRepoInfo: Map[String, Any] = Map(
    "methodNamespace" -> methodNamespace,
    "methodName" -> methodName,
    "methodVersion" -> snapshotId
  )

}

case class DockstoreMethod(
  methodPath: String,
  methodVersion: String
) {
  val source = "dockstore"
  def methodRepoInfo = Map(
    "sourceRepo" -> source,
    "methodPath" -> methodPath,
    "methodVersion" -> methodVersion
  )
}

object DockstoreMethodData {
  val dockstoreMethod = DockstoreMethod(
    methodPath = "github.com/DataBiosphere/topmed-workflows/u_of_Michigan_alignment_pipeline",
    methodVersion = "1.13.0"
  )
}
/* METHODS */
object MethodData {
  val SimpleMethod = Method(
    methodName = "DO_NOT_CHANGE_test_method",
    methodNamespace = "automationmethods",
    snapshotId = 1,
    rootEntityType = "participant",
    synopsis = "testtestsynopsis",
    documentation = "",
    payload =
      "task hello {\n  String? name\n\n  command {\n    echo 'hello ${name}!'\n  }\n  output {\n    File response = stdout()\n  }\n  runtime {\n    docker: \"ubuntu\"\n  }\n}\n\nworkflow test {\n  call hello\n}"
  )

  val InputRequiredMethod = Method(
    methodName = "DO_NOT_CHANGE_test_method_input_required",
    methodNamespace = "automationmethods",
    snapshotId = 1,
    rootEntityType = "participant",
    synopsis = "method with required inputs for testing",
    documentation = "",
    payload =
      "task hello {\n  String name\n\n  command {\n    echo 'hello ${name}!'\n  }\n  output {\n    File response = stdout()\n  }\n  runtime {\n    docker: \"ubuntu\"\n  }\n}\n\nworkflow test {\n  call hello\n}"
  )
}

/* CONFIGS */
object SimpleMethodConfig {
  val configName = "DO_NOT_CHANGE_test1_config"
  val configNamespace = "automationmethods"
  val snapshotId = 1
  val rootEntityType = "participant"
  val inputs = Map("test.hello.name" -> "\"a\"") // shouldn't be needed for config
  val outputs = Map("test.hello.response" -> "workspace.result")
}

object InputRequiredMethodConfig {
  val inputs = Map("test.hello.name" -> "\"a\"")
  val outputs = Map("test.hello.response" -> "workspace.result")
}
