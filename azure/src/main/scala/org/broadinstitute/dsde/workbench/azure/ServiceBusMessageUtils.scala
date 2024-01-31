package org.broadinstitute.dsde.workbench.azure

import com.azure.messaging.servicebus.ServiceBusReceivedMessage
import com.google.protobuf.Timestamp
import org.broadinstitute.dsde.workbench.model.TraceId

import java.time.Instant

object ServiceBusMessageUtils {

  def getTraceIdFromCorrelationId(message: ServiceBusReceivedMessage): Option[TraceId] =
    Option(message.getCorrelationId).map(new TraceId(_))
}
