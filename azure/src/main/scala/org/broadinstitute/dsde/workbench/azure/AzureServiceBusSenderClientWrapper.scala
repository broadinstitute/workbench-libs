package org.broadinstitute.dsde.workbench.azure

import com.azure.messaging.servicebus.ServiceBusMessage
import reactor.core.publisher.Mono

// A simple wrapper around the Azure ServiceBusSenderAsyncClient to facilitate testing and reduce coupling with the Azure SDK
private[azure] trait AzureServiceBusSenderClientWrapper {
  def sendMessageAsync(message: ServiceBusMessage): Mono[Void]
  def close(): Unit
}

private object AzureServiceBusSenderClientWrapper {
  def createSenderClientWrapper(publisherConfig: AzureServiceBusPublisherConfig): AzureServiceBusSenderClientWrapper =
    new AzureServiceBusSenderClientWrapperInterp(publisherConfig)
}
