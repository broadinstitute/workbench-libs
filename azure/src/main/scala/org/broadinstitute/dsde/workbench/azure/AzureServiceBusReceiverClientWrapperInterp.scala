package org.broadinstitute.dsde.workbench.azure

import com.azure.messaging.servicebus._

class AzureServiceBusReceiverClientWrapperInterp(subscriberConfig: AzureServiceBusSubscriberConfig,
                                                 messageHandler: AzureEventMessageHandler
) extends AzureServiceBusReceiverClientWrapper {

  private val processor: ServiceBusProcessorClient = createNewProcessor()

  private def handleError(context: ServiceBusErrorContext): Unit =
    messageHandler.handleError(context)

  override def stopProcessor(): Unit =
    processor.stop()

  private def createNewProcessor(): ServiceBusProcessorClient = new ServiceBusClientBuilder()
    .connectionString(
      subscriberConfig.connectionString.getOrElse(throw new Exception("Connection string not provided"))
    )
    .processor()
    .topicName(subscriberConfig.topicName)
    .processMessage(context => processMessageWithHandler(context))
    .processError(handleError)
    .subscriptionName(subscriberConfig.subscriptionName)
    .buildProcessorClient()

  override def startProcessor(): Unit =
    // According to the documentation the start method is idempotent
    processor.start()

  private def processMessageWithHandler(
    context: ServiceBusReceivedMessageContext
  ): Unit = messageHandler.handleMessage(context)
}
