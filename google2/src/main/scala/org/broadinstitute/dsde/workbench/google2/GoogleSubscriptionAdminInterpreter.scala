package org.broadinstitute.dsde.workbench.google2

import cats.effect.Sync
import cats.effect.kernel.Temporal
import cats.mtl.Ask
import com.google.cloud.pubsub.v1.SubscriptionAdminClient
import com.google.pubsub.v1.{ProjectName, ProjectSubscriptionName, Subscription}
import fs2.Stream
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.util2.tracedLogging
import org.typelevel.log4cats.StructuredLogger

import scala.jdk.CollectionConverters._

private[google2] class GoogleSubscriptionAdminInterpreter[F[_]: Temporal](client: SubscriptionAdminClient)(implicit
  F: Sync[F],
  logger: StructuredLogger[F]
) extends GoogleSubscriptionAdmin[F] {
  def list(project: GoogleProject)(implicit ev: Ask[F, TraceId]): Stream[F, Subscription] = {
    val fa = F.delay(client.listSubscriptions(ProjectName.of(project.value)))
    for {
      resp <- Stream.eval(
        tracedLogging(fa, s"com.google.cloud.pubsub.v1.SubscriptionAdminClient.listSubscriptions(${project.value})")
      )
      pagedResponse <- Stream.fromIterator(resp.iteratePages().iterator().asScala, 1024)
      subscription <- Stream.fromIterator(pagedResponse.getValues.iterator().asScala, 1024)
    } yield subscription
  }

  def delete(projectSubscriptionName: ProjectSubscriptionName)(implicit ev: Ask[F, TraceId]): F[Unit] = {
    val fa = F.delay(client.deleteSubscription(projectSubscriptionName))
    tracedLogging(fa,
                  s"com.google.cloud.pubsub.v1.SubscriptionAdminClient.deleteSubscription($projectSubscriptionName)"
    )
  }
}
