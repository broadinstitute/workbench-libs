package org.broadinstitute.dsde.workbench.util2

import cats.data.Kleisli
import org.typelevel.log4cats.Logger
import io.circe.Encoder
import io.circe.syntax._

/**
 * Provides syntax extension for `Logger[F]`
 *
 * `ctxXXX` logs context `A` together with a given message
 */
final case class ContextLogger[F[_]](log: Logger[F]) extends AnyVal {
  // instances for io.chrisdavenport.log4cats.Logger already check if a specific log level is enabled.
  final def ctxDebug[A: Encoder, B: Encoder](message: B): Kleisli[F, A, Unit] = Kleisli { ctx =>
    val withMessage = Map("context" -> ctx.asJson, "message" -> message.asJson)
    log.debug(withMessage.asJson.noSpaces)
  }

  final def ctxDebug[A: Encoder, B: Encoder](message: B, cause: Throwable): Kleisli[F, A, Unit] = Kleisli { ctx =>
    val withMessage = Map("context" -> ctx.asJson, "message" -> message.asJson)
    log.debug(cause)(withMessage.asJson.noSpaces)
  }

  final def ctxInfo[A: Encoder, B: Encoder](message: B): Kleisli[F, A, Unit] = Kleisli { ctx =>
    val withMessage = Map("context" -> ctx.asJson, "message" -> message.asJson)
    log.info(withMessage.asJson.noSpaces)
  }

  final def ctxInfo[A: Encoder, B: Encoder](message: B, cause: Throwable): Kleisli[F, A, Unit] = Kleisli { ctx =>
    val withMessage = Map("context" -> ctx.asJson, "message" -> message.asJson)
    log.info(cause)(withMessage.asJson.noSpaces)
  }

  final def ctxWarn[A: Encoder, B: Encoder](message: B): Kleisli[F, A, Unit] = Kleisli { ctx =>
    val withMessage = Map("context" -> ctx.asJson, "message" -> message.asJson)
    log.warn(withMessage.asJson.noSpaces)
  }

  final def ctxWarn[A: Encoder, B: Encoder](message: B, cause: Throwable): Kleisli[F, A, Unit] = Kleisli { ctx =>
    val withMessage = Map("context" -> ctx.asJson, "message" -> message.asJson)
    log.warn(cause)(withMessage.asJson.noSpaces)
  }

  final def ctxError[A: Encoder, B: Encoder](message: B): Kleisli[F, A, Unit] = Kleisli { ctx =>
    val withMessage = Map("context" -> ctx.asJson, "message" -> message.asJson)
    log.error(withMessage.asJson.noSpaces)
  }

  final def ctxError[A: Encoder, B: Encoder](message: B, cause: Throwable): Kleisli[F, A, Unit] = Kleisli { ctx =>
    val withMessage = Map("context" -> ctx.asJson, "message" -> message.asJson)
    log.error(cause)(withMessage.asJson.noSpaces)
  }
}
