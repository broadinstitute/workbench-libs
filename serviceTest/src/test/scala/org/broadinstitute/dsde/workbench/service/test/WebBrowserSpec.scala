package org.broadinstitute.dsde.workbench.service.test

import java.io.{File, FileInputStream, FileOutputStream}
import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFilePermission
import java.text.SimpleDateFormat
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.workbench.config.ServiceTestConfig
import org.broadinstitute.dsde.workbench.service.Orchestration
import org.broadinstitute.dsde.workbench.service.util.ExceptionHandling
import org.openqa.selenium.chrome.{ChromeDriverService, ChromeOptions}
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.remote.{Augmenter, LocalFileDetector, RemoteWebDriver}
import org.openqa.selenium.{OutputType, TakesScreenshot, WebDriver}
import org.scalatest.Suite

import scala.collection.JavaConverters._
import scala.sys.SystemProperties

/**
  * Base spec for writing FireCloud web browser tests.
  */
trait WebBrowserSpec extends WebBrowserUtil with ExceptionHandling with LazyLogging { self: Suite =>

  lazy val api = Orchestration
  lazy val headless = new SystemProperties().get("headless")

  val isHeadless: Boolean = {
    headless match {
      case Some("false") => false
      case _ => true
    }
  }

  /**
    * Executes a test in a fixture with a managed WebDriver. A test that uses
    * this will get its own WebDriver instance will be destroyed when the test
    * is complete. This encourages test case isolation.
    *
    * @param testCode the test code to run
    */
  def withWebDriver(testCode: WebDriver => Any): Unit = {
    withWebDriver(s"/app/${System.getProperty("java.io.tmpdir")}")(testCode)
  }

  /**
    * Executes a test in a fixture with a managed WebDriver. A test that uses
    * this will get its own WebDriver instance will be destroyed when the test
    * is complete. This encourages test case isolation.
    *
    * @param downloadPath a directory where downloads should be saved
    * @param testCode the test code to run
    */
  def withWebDriver(downloadPath: String)(testCode: WebDriver => Any): Unit = {
    val options = getChromeIncognitoOption(downloadPath)
    if (isHeadless) {
      runHeadless(options, testCode)
    } else {
      runLocalChrome(options, testCode)
    }
  }

  private def getChromeIncognitoOption(downloadPath: String): ChromeOptions = {
    val fullDownloadPath = new File(downloadPath).getAbsolutePath
    // logger.info(s"Chrome download path: $fullDownloadPath")
    val options = new ChromeOptions
    options.addArguments("--incognito")
    options.addArguments("--no-experiments")
    // https://github.com/GoogleChrome/chrome-launcher/blob/master/docs/chrome-flags-for-tools.md
    options.addArguments("--disable-background-networking")
    options.addArguments("--disable-client-side-phishing-detection")
    options.addArguments("--no-sandbox")
    options.addArguments("--disable-extensions")
    options.addArguments("--disable-default-apps")
    options.addArguments("--disable-gpu")
    options.addArguments("--no-first-run")
    options.addArguments("--enable-automation")
    options.addArguments("--test-type=webdriver")
    options.addArguments("--disable-dev-shm-usage")
    options.addArguments("--disable-hang-monitor")
    options.addArguments("--disable-popup-blocking")
    options.addArguments("--disable-sync")
    options.addArguments("--disable-background-timer-throttling")
    options.addArguments("--disable-renderer-backgrounding")
    options.addArguments("--disable-backing-store-limit ")
    options.addArguments("--disable-background-networking")
    if (java.lang.Boolean.parseBoolean(System.getProperty("burp.proxy"))) {
      options.addArguments("--proxy-server=http://127.0.0.1:8080")
    }
    // Note that download.prompt_for_download will be ignored if download.default_directory is invalid or doesn't exist
    options.setExperimentalOption("prefs", Map(
      "download.default_directory" -> fullDownloadPath,
      "download.prompt_for_download" -> "false").asJava)
    options
  }

  lazy val chromeDriverHost: String = ServiceTestConfig.ChromeSettings.chromedriverHost
  lazy val chromeDriverFile: File = new File(ServiceTestConfig.ChromeSettings.chromeDriverPath)

  private def runLocalChrome(options: ChromeOptions, testCode: WebDriver => Any): Unit = {
    val service = new ChromeDriverService.Builder().usingDriverExecutable(chromeDriverFile).usingAnyFreePort().build()
    service.start()
    implicit val driver: RemoteWebDriver = startRemoteWebdriver(service.getUrl, options)
    try {
      withScreenshot {
        testCode(driver)
      }
    } finally {
      try driver.quit() catch nonFatalAndLog
      try service.stop() catch nonFatalAndLog
    }
  }

  private def runHeadless(options: ChromeOptions, testCode: WebDriver => Any): Unit = {
    implicit val driver: RemoteWebDriver = startRemoteWebdriver(new URL(chromeDriverHost), options)
    try {
      withScreenshot {
        testCode(driver)
      }
    } finally {
      try driver.quit() catch nonFatalAndLog
    }
  }

  private def startRemoteWebdriver(url: URL, options: ChromeOptions): RemoteWebDriver = {
    val driver = new RemoteWebDriver(url, options)
    driver.manage.window.setSize(new org.openqa.selenium.Dimension(1600, 2400))
    driver.setFileDetector(new LocalFileDetector())
    // implicitlyWait(Span(2, Seconds))
    driver
  }

  /**
    * Override of withScreenshot that works with a remote Chrome driver and
    * lets us control the image file name.
    */
  override def withScreenshot[T](f: => T)(implicit driver: WebDriver): T = {
    try {
      f
    } catch {
      case t: Throwable =>
        val date = new SimpleDateFormat("HH-mm-ss-SSS").format(new java.util.Date())
        val path = "failure_screenshots"
        val name = s"${suiteName}_${date}"
        val fileName = s"$path/${name}.png"
        val htmlSourceFileName = s"$path/${name}.html"
        val logFileName = s"$path/${name}_console.txt"
        try {
          val directory = new File(s"$path")
          if (!directory.exists()) {
            directory.mkdir()
          }
          val tmpFile = new Augmenter().augment(driver).asInstanceOf[TakesScreenshot].getScreenshotAs(OutputType.FILE)
          logger.error(s"Failure screenshot saved to $fileName")
          new FileOutputStream(new File(fileName)).getChannel.transferFrom(
            new FileInputStream(tmpFile).getChannel, 0, Long.MaxValue)

          val html = tagName("html").element.underlying.getAttribute("outerHTML")
          new FileOutputStream(new File(htmlSourceFileName)).write(html.getBytes)

          val logLines = driver.manage().logs().get(LogType.BROWSER).iterator().asScala.toList
          if (logLines.nonEmpty) {
            val logString = logLines.map(_.toString).reduce(_ + "\n" + _)
            new FileOutputStream(new File(logFileName)).write(logString.getBytes)
          }
          logger.error(s"Screenshot ${name}.png Exception. ", t)
        } catch nonFatalAndLog(s"FAILED TO SAVE SCREENSHOT $fileName")

        throw t
    }
  }

  def createDownloadDirectory(): String = {
    val basePath: Path = Paths.get(s"chrome/downloads")
    val path: Path = Files.createTempDirectory(basePath, "temp")
    logger.info(s"mkdir: $path")
    val permissions = Set(
      PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE)
    import scala.collection.JavaConverters._
    Files.setPosixFilePermissions(path, permissions.asJava)
    path.toString
  }

}
