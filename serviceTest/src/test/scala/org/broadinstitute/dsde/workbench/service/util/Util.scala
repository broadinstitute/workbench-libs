package org.broadinstitute.dsde.workbench.service.util

import java.io.File
import java.nio.file.Files

import com.typesafe.scalalogging.LazyLogging

/**
  */
object Util extends LazyLogging {

  def appendUnderscore(string: String): String = {
    string match {
      case "" => ""
      case s => s + "_"
    }
  }

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
