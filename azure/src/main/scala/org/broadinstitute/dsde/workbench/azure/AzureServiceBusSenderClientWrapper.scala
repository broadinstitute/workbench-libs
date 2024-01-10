package org.broadinstitute.dsde.workbench.azure

import com.azure.messaging.servicebus.ServiceBusMessage
import reactor.core.publisher.Mono

// A simple wrapper around the Azure ServiceBusSenderAsyncClient to facilitate testing
// given that the client is final and serializable, which prevents mockito from mocking it
trait AzureServiceBusSenderClientWrapper {
  def sendMessageAsync(message: ServiceBusMessage): Mono[Void]
  def close(): Unit
}

object AzureServiceBusSenderClientWrapper {
    def createSenderClientWrapper(publisherConfig: AzureServiceBusPublisherConfig): AzureServiceBusSenderClientWrapper = {
        new AzureServiceBusSenderClientWrapperInterp(publisherConfig)
    }
}
