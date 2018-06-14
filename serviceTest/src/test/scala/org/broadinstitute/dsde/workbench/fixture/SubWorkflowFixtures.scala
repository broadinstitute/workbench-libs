package org.broadinstitute.dsde.workbench.fixture

import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.workbench.service.Orchestration
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, RandomUtil}

trait SubWorkflowFixtures extends RandomUtil {
  this: CleanUp =>

  /**
    * Create a tree of Methods that can be used to test subworkflows.
    *
    * After calling this (scala) method, N (workbench) Methods will be created in the environment, where N = the number
    * of levels, or depth of the tree.  Methods below the top level will be made public to the environment and will
    * have randomized names.  They will be removed at CleanUp time.
    *
    * @param levels The depth of the tree.  Minimum of 2.
    * @param scatterCount How many subworkflows should each level of workflow create
    * @param methodNamespace (optional)
    * @param topLevelMethodName (optional)
    * @param token (implicit)
    */
  def methodTree(levels: Int, scatterCount: Int, methodNamespace: String = uniqueMethodName(), topLevelMethodName: String = uniqueMethodName())(implicit token: AuthToken): Method = {
    if (levels < 2)
      throw new Exception(s"Test Logic Error: methodTree requires at least 2 levels.  Requested: $levels")

    val penultimateMethod = if (levels == 2)
      baseMethod(methodNamespace)
    else
      middleMethods(methodNamespace, levels - 2, scatterCount)

    val topLevelMethod = methodWithSubWorkflows(topLevelMethodName, methodNamespace, penultimateMethod, scatterCount)
    Orchestration.methods.createMethod(topLevelMethod.creationAttributes)
    register cleanUp Orchestration.methods.redact(topLevelMethod)

    topLevelMethod
  }

  def uniqueMethodName(): String = randomIdWithPrefix("M")  // can't start with a number, so we start with M

  // a Method Config suitable for use with a Method Tree created by the above (scala) method

  object topLevelMethodConfiguration {
    val configName = "subworkflow_top_level_mc"
    val configNamespace = "subworkflow_methods"
    val snapshotId = 1
    val rootEntityType = "participant"

    def inputs(method: Method) = Map(
      s"${method.methodName}.echo_input" -> "\"echo me!\"",
      // 4.0.5.0 is too big for our defaults.  Freeze at the previous version.
      s"${method.methodName}.docker" -> "\"us.gcr.io/broad-gatk/gatk:4.0.4.0\""
    )

    def outputs(method: Method) = Map(
      s"${method.methodName}.echo_out" -> "workspace.result"
    )
  }

  // a trivial entity to use when launching a Method Tree analysis

  object SingleParticipant {
    val participantEntity = "entity:participant_id\nparticipant1"
    val entityId = "participant1"
  }

  // ---------------------------------------------------------------------------------------------------------------

  private def createPublicMethod(method: Method)(implicit token: AuthToken): Method = {
    Orchestration.methods.createMethod(method.creationAttributes)
    register cleanUp Orchestration.methods.redact(method)
    // makes Method publicly readable so it can be referenced by other Methods
    Orchestration.methods.setMethodPermissions(method.methodNamespace, method.methodName, method.snapshotId, "public", "READER")
    method
  }

  private def baseMethod(methodNamespace: String, workflowName: String = uniqueMethodName())(implicit token: AuthToken): Method = {
    val method = Method(
      methodName = workflowName,
      methodNamespace = methodNamespace,
      snapshotId = 1,
      rootEntityType = "participant",
      synopsis = "no",
      documentation = "also none",
      payload =
        s"""
           |workflow $workflowName {
           |
           |	String echo_input
           |	String docker
           |
           |	call do_echo {
           |    input:
           |      echo_in = echo_input,
           |      docker_image = docker
           |	}
           |
           |	output {
           |    String echo_out = do_echo.echo_out
           |	}
           |}
           |
           |task do_echo {
           |
           |	String echo_in
           |	String docker_image
           |
           |  command {
           |    echo $${echo_in}
           |  }
           |
           |  output {
           |    String echo_out = "$${echo_in}"
           |  }
           |
           |  runtime {
           |     docker: docker_image
           |  }
           |
           |}
      """.stripMargin)

    createPublicMethod(method)
  }

  private def methodWithSubWorkflows(workflowName: String, methodNamespace: String, child: Method, scatterCount: Int ): Method = {

    // Orchestration in real environments has a globally resolvable name like "firecloud-orchestration.dsde-dev.b.o"
    // but not in FIABs; instead they can use the docker network name

    val orchUrl = if (Config.FireCloud.orchApiUrl.contains("fiab"))
      "http://orch-app:8080/"
    else
      Config.FireCloud.orchApiUrl

    val childUrl = s"${orchUrl}ga4gh/v1/tools/${child.methodNamespace}:${child.methodName}/versions/1/plain-WDL/descriptor"

    Method(
      methodName = workflowName,
      methodNamespace = methodNamespace,
      snapshotId = 1,
      rootEntityType = "participant",
      synopsis = "still nothing",
      documentation = "nope",
      payload =
        s"""
           |import "$childUrl" as my_child
           |
           |workflow $workflowName {
           |  String echo_input
           |  String docker
           |
           |  scatter(i in range($scatterCount)) {
           |    call my_child.${child.methodName} {
           |      input:
           |        echo_input = echo_input,
           |        docker = docker
           |    }
           |  }
           |
           |  output {
           |    String echo_out = ${child.methodName}.echo_out[0]
           |  }
           |}
      """.stripMargin)
  }

  // how many middle-level methods of the tree (not counting top and base, so total - 2)
  private def middleMethods(methodNamespace: String, count: Int, scatterCount: Int)(implicit token: AuthToken): Method = {
    val subMethod = if (count == 1)
      baseMethod(methodNamespace)
    else
      middleMethods(methodNamespace, count - 1, scatterCount)

    val oneMiddleMethod = methodWithSubWorkflows(uniqueMethodName(), methodNamespace, subMethod, scatterCount)
    createPublicMethod(oneMiddleMethod)
  }
}
