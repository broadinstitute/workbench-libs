package org.broadinstitute.dsde.workbench.config

import com.typesafe.config.ConfigFactory

trait CommonConfig {
  protected val config = ConfigFactory.load()

  trait CommonUsers {
    protected val usersConfig = config.getConfig("users")

    private val notSoSecretPassword = usersConfig.getString("notSoSecretPassword")
    private val userDataJson = scala.io.Source.fromFile(usersConfig.getString("userDataPath")).getLines().mkString.asInstanceOf[Map[String, Map[String, String]]]
    private def makeCredsMap(jsonMap: Map[String, String]): Map[String, Credentials] = {
      for((k,v) <- jsonMap) yield (k, Credentials(v, notSoSecretPassword))
    }

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
    val trialBillingPemFile = gcsConfig.getString("trialBillingPemFile")
    val trialBillingPemFileClientId = gcsConfig.getString("trialBillingPemFileClientId")
    val subEmail = gcsConfig.getString("subEmail")
  }

  // note: no separate "projects" config stanza
  trait CommonProjects extends CommonGCS {
    val billingAccountId = gcsConfig.getString("billingAccountId")
  }

  trait CommonFireCloud {
    protected val fireCloudConfig = config.getConfig("fireCloud")

    val fireCloudId: String = fireCloudConfig.getString("fireCloudId")
    val orchApiUrl: String = fireCloudConfig.getString("orchApiUrl")
    val rawlsApiUrl: String = fireCloudConfig.getString("rawlsApiUrl")
    val samApiUrl: String = fireCloudConfig.getString("samApiUrl")
    val thurloeApiUrl: String = fireCloudConfig.getString("thurloeApiUrl")
    val gpAllocApiUrl: String = fireCloudConfig.getString("gpAllocApiUrl")
  }

  trait CommonChromeSettings {
    private val chromeSettingsConfig = config.getConfig("chromeSettings")

    val chromedriverHost = chromeSettingsConfig.getString("chromedriverHost")
    val chromeDriverPath = chromeSettingsConfig.getString("chromedriverPath")
  }

}
