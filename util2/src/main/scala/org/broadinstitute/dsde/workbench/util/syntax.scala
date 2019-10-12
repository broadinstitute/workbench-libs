package org.broadinstitute.dsde.workbench.util2
import io.chrisdavenport.log4cats.Logger

object syntax {
  implicit def logSyntax[F[_]](log: Logger[F]): ContextLogger[F] = ContextLogger(log)
}
