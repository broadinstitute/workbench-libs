package org.broadinstitute.dsde.workbench.azure

import com.azure.messaging.servicebus._

import scala.util.{Failure, Success, Try}

class AzureServiceBusReceiverClientWrapperInterp(subscriberConfig: AzureServiceBusSubscriberConfig,
                                                                messageHandler: AzureEventMessageHandler)
  extends AzureServiceBusReceiverClientWrapper {

  private val processor: ServiceBusProcessorClient = createNewProcessor()

  private def handleError(context: ServiceBusErrorContext): Unit =
    println(s"Error when receiving message: ${context.getException}.")


  override def stopProcessor(): Unit =
    processor.stop()

  private def createNewProcessor(): ServiceBusProcessorClient = new ServiceBusClientBuilder()
    .connectionString(
      subscriberConfig.connectionString.getOrElse(throw new Exception("Connection string not provided"))
    )
    .processor()
    .topicName(subscriberConfig.topicName)
    .processMessage(msg=>processMessageWithHandler(messageHandler.handleMessage)(msg))
    .processError(handleError)
    .subscriptionName(subscriberConfig.subscriptionName)
    .buildProcessorClient()

  override def startProcessor(): Unit = {
    //According to the documentation the start method is idempotent
    processor.start()
  }

  private def processMessageWithHandler(handler: ServiceBusReceivedMessage => Try[Unit] ): ServiceBusReceivedMessageContext => Unit =
  {
    context: ServiceBusReceivedMessageContext =>
      handler(context.getMessage) match {
        case Success(_) =>
          context.complete()
        case Failure(_) =>
          //no need to handle the error as the handler logged it
          context.abandon()
      }
  }
}
