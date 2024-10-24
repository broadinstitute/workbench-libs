package org.broadinstitute.dsde.workbench.config

import com.typesafe.config.ConfigFactory
import spray.json._
import DefaultJsonProtocol._
import org.broadinstitute.dsde.workbench.util.ScalaConfig.EnhancedScalaConfig

import java.time.temporal.{ChronoUnit, TemporalUnit}
import scala.concurrent.duration.{Duration, FiniteDuration}

trait CommonConfig {
  protected val config = ConfigFactory.load()

  trait CommonUsers {
    protected val usersConfig = config.getConfig("users")

    private val notSoSecretPassword = usersConfig.getString("notSoSecretPassword")
    private val userDataJson = scala.io.Source
      .fromFile(usersConfig.getString("userDataPath"))
      .getLines()
      .mkString
      .parseJson
      .convertTo[Map[String, Map[String, String]]]
    private def makeCredsMap(jsonMap: Map[String, String]): Map[String, Credentials] =
      for ((k, v) <- jsonMap) yield (k, Credentials(v, notSoSecretPassword))

    val Admins = UserSet(makeCredsMap(userDataJson("admins")))
    val Owners = UserSet(makeCredsMap(userDataJson("owners")))
    val Curators = UserSet(makeCredsMap(userDataJson("curators")))
    val Temps = UserSet(makeCredsMap(userDataJson("temps")))
    val AuthDomainUsers = UserSet(makeCredsMap(userDataJson("authdomains")))
    val Students = UserSet(makeCredsMap(userDataJson("students")))
    val NotebooksWhitelisted = UserSet(makeCredsMap(userDataJson("notebookswhitelisted")))
    val CampaignManager = UserSet(makeCredsMap(userDataJson("campaignManagers")))
  }

  trait CommonGCS {
    protected val gcsConfig = config.getConfig("gcs")

    val pathToQAPem = gcsConfig.getString("qaPemFile")
    val qaEmail = gcsConfig.getString("qaEmail")
    val subEmail = gcsConfig.getString("subEmail")
  }

  // note: no separate "projects" config stanza
  trait CommonProjects extends CommonGCS {
    val billingAccountId = gcsConfig.getString("billingAccountId")
    val googleAccessPolicy = gcsConfig.getString("googleAccessPolicy")
  }

  trait CommonFireCloud {
    protected val fireCloudConfig = config.getConfig("fireCloud")

    val fireCloudId: String = fireCloudConfig.getString("fireCloudId")
    val orchApiUrl: String = fireCloudConfig.getString("orchApiUrl")
    val rawlsApiUrl: String = fireCloudConfig.getString("rawlsApiUrl")
    val samApiUrl: String = fireCloudConfig.getString("samApiUrl")
    val thurloeApiUrl: String = fireCloudConfig.getString("thurloeApiUrl")
    lazy val dataRepoApiUrl: String = if (fireCloudConfig.hasPath("dataRepoApiUrl")) {
      fireCloudConfig.getString("dataRepoApiUrl")
    } else {
      "dataRepoApiUrl-value-not-in-config-file"
    }
    val waitForAccessTime: FiniteDuration = Duration.fromNanos(
      fireCloudConfig
        .getDurationOption("waitForAccessDuration")
        .getOrElse(java.time.Duration.of(2, ChronoUnit.MINUTES))
        .toNanos
    )
  }

  trait CommonChromeSettings {
    private val chromeSettingsConfig = config.getConfig("chromeSettings")

    val chromedriverHost = chromeSettingsConfig.getString("chromedriverHost")
    val chromeDriverPath = chromeSettingsConfig.getString("chromedriverPath")
  }

}
