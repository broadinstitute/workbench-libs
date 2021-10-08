package org.broadinstitute.dsde.workbench.service.test

import java.io.{File, FileInputStream, FileOutputStream}
import java.net.URL
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path, Paths}
import java.text.SimpleDateFormat
import java.util.logging.Level

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.config.ServiceTestConfig
import org.broadinstitute.dsde.workbench.service.Orchestration
import org.broadinstitute.dsde.workbench.service.util.ExceptionHandling
import org.openqa.selenium.chrome.{ChromeDriverService, ChromeOptions}
import org.openqa.selenium.logging.{LogType, LoggingPreferences}
import org.openqa.selenium.remote._
import org.openqa.selenium.{OutputType, TakesScreenshot, WebDriver}
import org.scalatest.Suite

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
 * Base spec for writing FireCloud web browser tests.
 */
trait WebBrowserSpec extends WebBrowserUtil with ExceptionHandling with LazyLogging { self: Suite =>

  lazy val api: Orchestration.type = Orchestration
  lazy val headless: Option[String] = sys.props.get("headless")
  lazy val isHeadless: Boolean = {
    logger.info(s"Is running in headless mode? $headless")
    headless match {
      case Some("false") =>
        logger.info(s"Running in non headless mode")
        false
      case _ =>
        logger.info(s"Running in headless mode")
        true
    }
  }

  /**
   * Executes a test in a fixture with a managed WebDriver. A test that uses this will get its own WebDriver instance
   * will be destroyed when the test is complete. This encourages test case isolation.
   *
   * @param testCode
   *   the test code to run
   */
  def withWebDriver(testCode: WebDriver => Any): Unit =
    withWebDriver(s"/app/${sys.props.get("java.io.tmpdir")}")(testCode)

  /**
   * Executes a test in a fixture with a managed WebDriver. A test that uses this will get its own WebDriver instance
   * will be destroyed when the test is complete. This encourages test case isolation.
   *
   * @param downloadPath
   *   a directory where downloads should be saved
   * @param testCode
   *   the test code to run
   */
  def withWebDriver(downloadPath: String)(testCode: WebDriver => Any): Unit = {
    val options = getChromeIncognitoOption(downloadPath)
    if (isHeadless) {
      logger.info("Using chromedriver service from docker")
      runDockerChrome(options, testCode)
    } else {
      logger.info("starting local chromedriver service")
      runLocalChrome(options, testCode)
    }
  }

  private def getChromeIncognitoOption(downloadPath: String): ChromeOptions = {
    val fullDownloadPath = new File(downloadPath).getAbsolutePath

    val options = new ChromeOptions
    options.addArguments("--incognito")
    options.addArguments("--no-experiments")
    options.addArguments("--no-sandbox")
    options.addArguments("--dns-prefetch-disable")
    options.addArguments("--lang=en-US")
    options.addArguments("--disable-setuid-sandbox")
    options.addArguments("--disable-extensions")
    options.addArguments("--disable-dev-shm-usage")
    options.addArguments("--window-size=2880,1800")
    options.addArguments("--disable-background-timer-throttling")
    options.addArguments("--disable-backgrounding-occluded-windows")
    options.addArguments("--disable-breakpad")
    options.addArguments("--disable-component-extensions-with-background-pages")
    options.addArguments("--disable-features=TranslateUIBlinkGenPropertyTrees")
    options.addArguments("--disable-ipc-flooding-protection")
    options.addArguments("--disable-renderer-backgrounding")
    options.addArguments("--enable-features=NetworkServiceNetworkServiceInProcess")
    options.addArguments("--force-color-profile=srgb")
    options.addArguments("--hide-scrollbars")
    options.addArguments("--metrics-recording-only")
    options.addArguments("--mute-audio")
    options.setExperimentalOption("useAutomationExtension", false)

    if (java.lang.Boolean.parseBoolean(System.getProperty("burp.proxy"))) {
      options.addArguments("--proxy-server=http://127.0.0.1:8080")
    }

    // Note that download.prompt_for_download will be ignored if download.default_directory is invalid or doesn't exist
    options.setExperimentalOption(
      "prefs",
      Map(
        "download.default_directory" -> fullDownloadPath,
        "download.prompt_for_download" -> "false",
        "profile.default_content_settings.cookies" -> 1,
        "profile.block_third_party_cookies" -> false
      ).asJava
    )

    // ChromeDriver log
    val logPref = new LoggingPreferences()
    logPref.enable(LogType.BROWSER, Level.ALL)
    logPref.enable(LogType.CLIENT, Level.ALL)
    logPref.enable(LogType.DRIVER, Level.ALL)
    logPref.enable(LogType.SERVER, Level.ALL)

    options.setCapability(CapabilityType.LOGGING_PREFS, logPref)
    options
  }

  lazy val chromeDriverHost: String = ServiceTestConfig.ChromeSettings.chromedriverHost
  lazy val chromeDriverFile: File = new File(ServiceTestConfig.ChromeSettings.chromeDriverPath)

  private def runLocalChrome(options: ChromeOptions, testCode: WebDriver => Any): Unit = {
    val service = new ChromeDriverService.Builder().usingDriverExecutable(chromeDriverFile).usingAnyFreePort().build()
    service.start()
    implicit val driver: RemoteWebDriver = startRemoteWebdriver(service.getUrl, options)
    try withScreenshot {
      testCode(driver)
    } finally {
      try driver.close()
      catch nonFatalAndLog
      try driver.quit()
      catch nonFatalAndLog
      try service.stop()
      catch nonFatalAndLog
    }
  }

  private def runDockerChrome(options: ChromeOptions, testCode: WebDriver => Any): Unit = {
    implicit val driver: RemoteWebDriver = startRemoteWebdriver(new URL(chromeDriverHost), options)
    try withScreenshot {
      testCode(driver)
    } finally {
      try driver.close()
      catch nonFatalAndLog
      try driver.quit()
      catch nonFatalAndLog
    }
  }

  private def startRemoteWebdriver(url: URL, options: ChromeOptions, trials: Int = 5): RemoteWebDriver = {
    logger.info("Starting a new Chrome RemoteWebDriver...")
    val result = tryStart(url, options)
    result match {
      case Failure(_) if trials > 0 =>
        logger.error(s"Retry start a new Chrome RemoteWebDriver. ${trials - 1} more times.")
        Thread.sleep(10000)
        startRemoteWebdriver(url, options, trials - 1)
      case Failure(e) =>
        logger.error(s"Failed to start a new Chrome RemoteWebDriver.", e)
        throw e
      case Success(driver) =>
        driver.setFileDetector(new LocalFileDetector())
        driver
    }
  }

  private def tryStart(url: URL, options: ChromeOptions): Try[RemoteWebDriver] =
    for {
      e <- Try(new RemoteWebDriver(url, options))
    } yield e

  /**
   * Override of withScreenshot that works with a remote Chrome driver and lets us control the image file name.
   */
  override def withScreenshot[T](f: => T)(implicit driver: WebDriver): T =
    try f
    catch {
      case t: Throwable =>
        val date = new SimpleDateFormat("HH-mm-ss-SSS").format(new java.util.Date())
        val path = "failure_screenshots"
        val name = s"${suiteName}_$date"
        val fileName = s"$path/$name.png"
        val htmlSourceFileName = s"$path/$name.html"
        val logFileNamePrefix = s"$path/$name"
        try {
          val directory = new File(s"$path")
          if (!directory.exists()) {
            directory.mkdir()
          }
          val tmpFile = new Augmenter().augment(driver).asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.FILE)
          logger.error(s"Failure screenshot saved to $fileName")
          new FileOutputStream(new File(fileName)).getChannel
            .transferFrom(new FileInputStream(tmpFile).getChannel, 0, Long.MaxValue)

          val html = tagName("html").element.underlying.getAttribute("outerHTML")
          new FileOutputStream(new File(htmlSourceFileName)).write(html.getBytes)

          saveLog(LogType.BROWSER, s"$logFileNamePrefix")
          saveLog(LogType.CLIENT, s"$logFileNamePrefix")
          saveLog(LogType.DRIVER, s"$logFileNamePrefix")
          saveLog(LogType.SERVER, s"$logFileNamePrefix")

          logger.error(s"Screenshot $name.png Exception. ", t)
        } catch nonFatalAndLog(s"FAILED TO SAVE SCREENSHOT $fileName")
        throw t
    }

  def createDownloadDirectory(): String = {
    val downloadDir = "chrome/downloads"
    createTempDirectory(downloadDir)
  }

  def createTempDirectory(dir: String): String = {
    val basePath: Path = Paths.get(dir)
    val path: Path = Files.createTempDirectory(basePath, "temp")

    val permissions = Set(
      PosixFilePermission.OWNER_WRITE,
      PosixFilePermission.OWNER_READ,
      PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.GROUP_WRITE,
      PosixFilePermission.GROUP_READ,
      PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_WRITE,
      PosixFilePermission.OTHERS_READ,
      PosixFilePermission.OTHERS_EXECUTE
    )

    import scala.collection.JavaConverters._
    Files.setPosixFilePermissions(path, permissions.asJava)
    path.toString
  }

  private def saveLog(logtype: String, filePrefix: String)(implicit driver: WebDriver): Unit = {
    val chromeLogs = driver.manage().logs()
    val availableLogs = chromeLogs.getAvailableLogTypes.asScala
    val logLines =
      if (availableLogs.contains(logtype))
        chromeLogs.get(logtype).getAll.iterator().asScala.toList
      else List.empty
    if (logLines.nonEmpty) {
      val logString = logLines.map(_.toString).mkString("\n")
      new FileOutputStream(new File(s"$filePrefix-$logtype.txt")).write(logString.getBytes)
    }
  }

}
