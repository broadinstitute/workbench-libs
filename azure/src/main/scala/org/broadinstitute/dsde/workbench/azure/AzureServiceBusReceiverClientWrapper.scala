package org.broadinstitute.dsde.workbench.azure

import com.azure.messaging.servicebus.{ServiceBusMessage, ServiceBusReceivedMessage}
import reactor.core.publisher.{Flux, Mono}

// A simple wrapper around the Azure ServiceBusReceiverAsyncClient to facilitate testing
// given that the client is final and serializable, which prevents mockito from mocking it
trait AzureServiceBusReceiverClientWrapper {
  def complete(message: ServiceBusReceivedMessage) : Unit
  def abandon(message: ServiceBusReceivedMessage) : Unit
  def close(): Unit
  def receiveMessagesAsync():Flux[ServiceBusReceivedMessage]
}

object AzureServiceBusReceiverClientWrapper {
  def createReceiverClientWrapper(subscriberConfig: AzureServiceBusSubscriberConfig): AzureServiceBusReceiverClientWrapper = {
    new AzureServiceBusReceiverClientWrapperInterp(subscriberConfig)
  }
}

