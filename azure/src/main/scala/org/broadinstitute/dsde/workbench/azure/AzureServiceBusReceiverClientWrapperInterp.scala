package org.broadinstitute.dsde.workbench.azure

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.messaging.servicebus._
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

private class AzureServiceBusReceiverClientWrapperInterp(subscriberConfig: AzureServiceBusSubscriberConfig,
                                                         messageHandler: AzureReceivedMessageHandler
) extends AzureServiceBusReceiverClientWrapper {

  private val logger = LoggerFactory.getLogger(classOf[AzureServiceBusReceiverClientWrapperInterp])
  private val processor: ServiceBusProcessorClient = createNewProcessor()

  private def handleError(context: ServiceBusErrorContext): Unit =
    logger.error(s"Error when receiving message: ${context.getException}.")

  override def stopProcessor(): Unit = {
    processor.stop()
    processor.close()
  }

  private def createNewProcessor(): ServiceBusProcessorClient = {

    val builder = subscriberConfig.connectionString.fold(
      managedIdentityClientBuilder()
    )(_ => connectionStringClientBuilder())

    builder
      .processor()
      .topicName(subscriberConfig.topicName)
      .processMessage(ctx => processMessageWithHandler(messageHandler.handleMessage)(ctx))
      .processError(handleError)
      .subscriptionName(subscriberConfig.subscriptionName)
      .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
      .prefetchCount(subscriberConfig.prefetchCount)
      .maxConcurrentCalls(subscriberConfig.maxConcurrentCalls)
      .disableAutoComplete()
      .buildProcessorClient()
  }

  private def managedIdentityClientBuilder(): ServiceBusClientBuilder = new ServiceBusClientBuilder()
    .credential(new DefaultAzureCredentialBuilder().build())
    .fullyQualifiedNamespace(subscriberConfig.namespace.getOrElse(throw new Exception("Namespace not provided")))

  private def connectionStringClientBuilder(): ServiceBusClientBuilder = new ServiceBusClientBuilder()
    .connectionString(
      subscriberConfig.connectionString.getOrElse(throw new Exception("Connection string not provided"))
    )

  override def startProcessor(): Unit =
    // According to the documentation the start method is idempotent
    processor.start()

  private def processMessageWithHandler(
    handler: ServiceBusReceivedMessageContext => Try[Unit]
  ): ServiceBusReceivedMessageContext => Unit = { context: ServiceBusReceivedMessageContext =>
    handler(context) match {
      case Success(_) =>
      // the message is acked by the consumer
      case Failure(_) =>
        // no need to handle the error as the handler should log it
        context.abandon()
    }
  }
}
