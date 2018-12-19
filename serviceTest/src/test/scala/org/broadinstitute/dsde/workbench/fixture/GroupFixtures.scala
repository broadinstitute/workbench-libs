package org.broadinstitute.dsde.workbench.fixture

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.service.Orchestration.groups.GroupRole
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.service.Orchestration
import org.broadinstitute.dsde.workbench.service.test.RandomUtil
import org.broadinstitute.dsde.workbench.service.util.ExceptionHandling
import org.broadinstitute.dsde.workbench.service.test.CleanUp
import org.scalatest.TestSuite

import scala.util.Try

/**
  * Fixtures for creating and cleaning up test groups.
  */
trait GroupFixtures extends ExceptionHandling with LazyLogging with RandomUtil { self: TestSuite =>

  def groupNameToEmail(groupName: String)(implicit token: AuthToken): String = Orchestration.groups.getGroup(groupName).groupEmail

  def withGroup(namePrefix: String, memberEmails: List[String] = List())
               (testCode: (String) => Any)
               (implicit token: AuthToken): Unit = {
    val groupName = uuidWithPrefix(namePrefix)

    val testTrial = Try {
      // logger.info(s"Creating new group $groupName")
      Orchestration.groups.create(groupName)
      memberEmails foreach { email =>
        logger.info(s"Adding user $email with role of member to group $groupName")
        Orchestration.groups.addUserToGroup(groupName, email, GroupRole.Member)
      }

      testCode(groupName)
    }

    val cleanupTrial = Try {
      Orchestration.groups.delete(groupName)
    }

    CleanUp.runCodeWithCleanup(testTrial, cleanupTrial)
  }
}
