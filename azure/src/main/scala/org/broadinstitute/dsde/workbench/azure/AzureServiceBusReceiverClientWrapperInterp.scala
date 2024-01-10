package org.broadinstitute.dsde.workbench.azure

import com.azure.messaging.servicebus.{ServiceBusClientBuilder, ServiceBusReceivedMessage, ServiceBusReceiverAsyncClient}
import reactor.core.publisher.Flux

class AzureServiceBusReceiverClientWrapperInterp(subscriberConfig: AzureServiceBusSubscriberConfig) extends AzureServiceBusReceiverClientWrapper {

  private val receiverClient: ServiceBusReceiverAsyncClient = new ServiceBusClientBuilder()
    .connectionString(subscriberConfig.connectionString.getOrElse(throw new Exception("Connection string not provided")))
    .receiver()
    .topicName(subscriberConfig.topicName)
    .subscriptionName(subscriberConfig.subscriptionName)
    .buildAsyncClient()

  override def close(): Unit = {
    receiverClient.close()
  }

  override def receiveMessagesAsync(): Flux[ServiceBusReceivedMessage] = {
    receiverClient.receiveMessages()
  }

  override def complete(message: ServiceBusReceivedMessage): Unit = {
    receiverClient.complete(message)
  }

  override def abandon(message: ServiceBusReceivedMessage): Unit = {
    receiverClient.abandon(message)
  }
}
