package org.broadinstitute.dsde.workbench.service.util

import java.io.File
import java.nio.file.Files
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging

import scala.util.Random

/**
 */
object Util extends LazyLogging {

  def appendUnderscore(string: String): String =
    string match {
      case "" => ""
      case s  => s + "_"
    }

  def appendDelimiter(string: String, delimiter: String): String =
    string + delimiter

  @deprecated("Please use RandomUtil makeRandomId or randomIdWithPrefix instead.",
              "workbench-libs/workbench-service-tests 0.8"
  )
  def makeRandomId(length: Int = 7): String =
    Random.alphanumeric.take(length).mkString

  @deprecated("Please use RandomUtil randomUuid or uuidWithPrefix instead.",
              "workbench-libs/workbench-service-tests 0.8"
  )
  def makeUuid: String =
    UUID.randomUUID().toString

  /**
   * Move a file, making sure the destination directory exists.
   *
   * @param sourcePath path to source file
   * @param destPath path to desired destination file
   */
  def moveFile(sourcePath: String, destPath: String): Unit = {
    val destFile = new File(destPath)
    if (!destFile.getParentFile.exists()) {
      destFile.getParentFile.mkdirs()
    }
    val source = new File(sourcePath).toPath
    val dest = destFile.toPath
    logger.info(s"Moving $source to $dest")
    Files.move(source, dest)
  }
}
