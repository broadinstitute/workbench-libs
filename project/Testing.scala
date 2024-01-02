import sbt.Keys._
import sbt._

object Testing {

  def isIntegrationTest(name: String) = name contains "integrationtest"

  val commonTestSettings: Seq[Setting[_]] = List(
    Test / testOptions ++= Seq(Tests.Filter(s => !isIntegrationTest(s))),
    Test / parallelExecution := false
  )

  val noTestSettings = Seq(Test / test := {})

  implicit class ProjectTestSettings(val project: Project) extends AnyVal {
    def withTestSettings: Project = project
      .configs(IntegrationTest)
      .settings(inConfig(IntegrationTest)(Defaults.testTasks): _*)
  }
}
