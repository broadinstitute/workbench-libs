package org.broadinstitute.dsde.workbench.azure
import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.messaging.servicebus.{ServiceBusClientBuilder, ServiceBusMessage, ServiceBusSenderAsyncClient}
import reactor.core.publisher.Mono

class AzureServiceBusSenderClientWrapperInterp(publisherConfig: AzureServiceBusPublisherConfig)
    extends AzureServiceBusSenderClientWrapper {

  private val senderClient: ServiceBusSenderAsyncClient = createNewSenderClient()

  private def createNewSenderClient(): ServiceBusSenderAsyncClient = {
    val builder = publisherConfig.connectionString.fold(
      createManagedIdentityClientBuilder()
    )(_ => createConnectionStringClientBuilder())

    builder
      .sender()
      .topicName(publisherConfig.topicName)
      .buildAsyncClient()
  }

  private def createManagedIdentityClientBuilder(): ServiceBusClientBuilder = new ServiceBusClientBuilder()
    .credential(new DefaultAzureCredentialBuilder().build())
    .fullyQualifiedNamespace(publisherConfig.namespace.getOrElse(throw new Exception("Namespace not provided")))

  private def createConnectionStringClientBuilder(): ServiceBusClientBuilder = new ServiceBusClientBuilder()
    .connectionString(
      publisherConfig.connectionString.getOrElse(throw new Exception("Connection string not provided"))
    )

  override def sendMessageAsync(message: ServiceBusMessage): Mono[Void] =
    senderClient.sendMessage(message)

  override def close(): Unit =
    senderClient.close()
}
