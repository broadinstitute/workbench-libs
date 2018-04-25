package org.broadinstitute.dsde.workbench.model.google

import org.broadinstitute.dsde.workbench.model.WorkbenchEmail
import org.broadinstitute.dsde.workbench.model.google.GcsEntityTypes.User
import org.broadinstitute.dsde.workbench.model.google.ProjectTeamTypes.Viewers
import org.scalatest.{FlatSpecLike, Matchers}

class GcsEntitySpec extends FlatSpecLike with Matchers {
  private val emailGcsEntity = EmailGcsEntity(User, WorkbenchEmail("foo@bar.com"))

  "EmailGcsEntity stringification" should "work" in {
    emailGcsEntity.toString shouldBe "user-foo@bar.com"
  }

  private val projectGcsEntity = ProjectGcsEntity(Viewers, ProjectNumber("398512454")).toString

  "ProjectGcsEntity stringification" should "work" in {
    projectGcsEntity.toString shouldBe "project-viewers-398512454"
  }
}
