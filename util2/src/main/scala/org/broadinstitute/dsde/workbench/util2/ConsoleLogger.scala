package org.broadinstitute.dsde.workbench.util2

import cats.effect.IO
import org.typelevel.log4cats.StructuredLogger

// This is useful for testing library locally to see logs, or use this in ammonite scripts
// To use this in console test:
// implicit def logger = new ConsoleLogger("prefix-you-like", LogLevel(true, true, true, true))
class ConsoleLogger(prefix: String, logLevel: LogLevel) extends StructuredLogger[IO] {
  override def error(message: => String): IO[Unit] = IO(println(s"$prefix | ERROR: $message"))

  override def warn(message: => String): IO[Unit] =
    if (logLevel.enableWarn) IO(println(s"$prefix | WARN: $message")) else IO.unit

  override def info(message: => String): IO[Unit] =
    if (logLevel.enableInfo)
      IO(println(s"$prefix | INFO: $message"))
    else IO.unit

  override def debug(message: => String): IO[Unit] =
    if (logLevel.enableDebug)
      IO(println(s"$prefix | DEBUG: $message"))
    else IO.unit

  override def trace(message: => String): IO[Unit] =
    if (logLevel.enableTrace) IO(println(s"$prefix | TRACE: $message")) else IO.unit

  override def trace(ctx: Map[String, String])(msg: => String): IO[Unit] =
    if (logLevel.enableTrace) IO(println(s"$prefix $ctx | TRACE: $msg")) else IO.unit

  override def trace(ctx: Map[String, String], t: Throwable)(msg: => String): IO[Unit] =
    if (logLevel.enableTrace) IO(println(s"$prefix $ctx | TRACE: $msg due to $t")) else IO.unit

  override def debug(ctx: Map[String, String])(msg: => String): IO[Unit] =
    if (logLevel.enableDebug)
      IO(println(s"$prefix $ctx | DEBUG: $msg"))
    else IO.unit

  override def debug(ctx: Map[String, String], t: Throwable)(msg: => String): IO[Unit] =
    if (logLevel.enableDebug)
      IO(println(s"$prefix $ctx | DEBUG: $msg due to $t"))
    else IO.unit

  override def info(ctx: Map[String, String])(msg: => String): IO[Unit] =
    if (logLevel.enableInfo)
      IO(println(s"$prefix $ctx | INFO: $msg"))
    else IO.unit

  override def info(ctx: Map[String, String], t: Throwable)(msg: => String): IO[Unit] =
    if (logLevel.enableInfo)
      IO(println(s"$prefix $ctx | INFO: $msg due to $t"))
    else IO.unit

  override def warn(ctx: Map[String, String])(msg: => String): IO[Unit] =
    if (logLevel.enableWarn)
      IO(println(s"$prefix $ctx | INFO: $msg"))
    else IO.unit

  override def warn(ctx: Map[String, String], t: Throwable)(msg: => String): IO[Unit] =
    if (logLevel.enableWarn)
      IO(println(s"$prefix $ctx | INFO: $msg due to $t"))
    else IO.unit

  override def error(ctx: Map[String, String])(msg: => String): IO[Unit] =
    IO(println(s"$prefix $ctx | ERROR: $msg"))

  override def error(ctx: Map[String, String], t: Throwable)(msg: => String): IO[Unit] =
    IO(println(s"$prefix $ctx | ERROR: $msg due to $t"))

  override def error(t: Throwable)(message: => String): IO[Unit] = IO(println(s"$prefix | ERROR: $message"))

  override def warn(t: Throwable)(message: => String): IO[Unit] =
    if (logLevel.enableWarn)
      IO(println(s"$prefix | INFO: $message due to $t"))
    else IO.unit

  override def info(t: Throwable)(message: => String): IO[Unit] =
    if (logLevel.enableInfo)
      IO(println(s"$prefix | INFO: $message due to $t"))
    else IO.unit

  override def debug(t: Throwable)(message: => String): IO[Unit] =
    if (logLevel.enableDebug)
      IO(println(s"$prefix | INFO: $message due to $t"))
    else IO.unit

  override def trace(t: Throwable)(message: => String): IO[Unit] =
    if (logLevel.enableTrace)
      IO(println(s"$prefix | INFO: $message due to $t"))
    else IO.unit
}

final case class LogLevel(enableDebug: Boolean, enableTrace: Boolean, enableInfo: Boolean, enableWarn: Boolean)
