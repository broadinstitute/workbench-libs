package org.broadinstitute.dsde.workbench.azure

import com.azure.messaging.servicebus.ServiceBusReceivedMessage
import com.google.protobuf.Timestamp
import org.broadinstitute.dsde.workbench.model.TraceId

object ServiceBusMessageUtils {
  def getEnqueuedTimeOrDefault(message: ServiceBusReceivedMessage): Timestamp = {
    val enqueuedTimeInstantOption = Option(message.getEnqueuedTime).map(_.toInstant)

    val timestamp = enqueuedTimeInstantOption match {
      case Some(instant) =>
        Timestamp
          .newBuilder()
          .setSeconds(instant.getEpochSecond)
          .setNanos(instant.getNano)
          .build()
      case None =>
        Timestamp.getDefaultInstance
    }
    timestamp
  }

  def getTraceIdFromCorrelationId(message: ServiceBusReceivedMessage): Option[TraceId] = {
    Option(message.getCorrelationId).map(new TraceId(_))
  }
}
