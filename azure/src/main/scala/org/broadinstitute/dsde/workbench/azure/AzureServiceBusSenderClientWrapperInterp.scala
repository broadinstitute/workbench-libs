package org.broadinstitute.dsde.workbench.azure
import com.azure.messaging.servicebus.{ServiceBusClientBuilder, ServiceBusMessage, ServiceBusSenderAsyncClient}
import reactor.core.publisher.Mono

class AzureServiceBusSenderClientWrapperInterp(publisherConfig: AzureServiceBusPublisherConfig)
    extends AzureServiceBusSenderClientWrapper {

  private val senderClient: ServiceBusSenderAsyncClient = new ServiceBusClientBuilder()
    .connectionString(publisherConfig.connectionString.getOrElse(throw new Exception("Connection string not provided")))
    .sender()
    .topicName(publisherConfig.topicName)
    .buildAsyncClient()
  override def sendMessageAsync(message: ServiceBusMessage): Mono[Void] =
    senderClient.sendMessage(message)

  override def close(): Unit =
    senderClient.close()
}
