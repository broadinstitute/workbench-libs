package org.broadinstitute.dsde.workbench.azure

import cats.effect.IO
import com.azure.messaging.servicebus.{
  ServiceBusClientBuilder,
  ServiceBusErrorContext,
  ServiceBusProcessorClient,
  ServiceBusReceivedMessage,
  ServiceBusReceivedMessageContext,
  ServiceBusReceiverAsyncClient
}
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers

import java.util.concurrent.Executors

class AzureServiceBusReceiverClientWrapperInterp(subscriberConfig: AzureServiceBusSubscriberConfig)
    extends AzureServiceBusReceiverClientWrapper {

  private val executor = Executors.newScheduledThreadPool(10)
  private val scheduler = Schedulers.fromExecutor(executor)

  private val receiverClient: ServiceBusReceiverAsyncClient = new ServiceBusClientBuilder()
    .connectionString(
      subscriberConfig.connectionString.getOrElse(throw new Exception("Connection string not provided"))
    )
    .receiver()
    .topicName(subscriberConfig.topicName)
    .subscriptionName(subscriberConfig.subscriptionName)
    .buildAsyncClient()

  private val processor: ServiceBusProcessorClient = new ServiceBusClientBuilder()
    .connectionString(
      subscriberConfig.connectionString.getOrElse(throw new Exception("Connection string not provided"))
    )
    .processor()
    .topicName(subscriberConfig.topicName)
    .processMessage(handleMessage)
    .processError(handleError)
    .subscriptionName(subscriberConfig.subscriptionName)
    .buildProcessorClient()

  private def handleMessage(context: ServiceBusReceivedMessageContext): Unit =
    println(s"Received message: ${context.getMessage.getBody}")

  private def handleError(context: ServiceBusErrorContext): Unit =
    println(s"Error when receiving messages: ${context.getException}")

  def startProcessor(): Unit =
    processor.start()

  def stopProcessor(): Unit =
    processor.stop()

  override def close(): Unit = {
    receiverClient.close()
    executor.shutdown()
  }

  override def receiveMessagesAsync(): Flux[ServiceBusReceivedMessage] =
    receiverClient
      .receiveMessages()
      .subscribeOn(scheduler)

  override def complete(message: ServiceBusReceivedMessage): Unit =
    receiverClient.complete(message)

  override def abandon(message: ServiceBusReceivedMessage): Unit =
    receiverClient.abandon(message)
}
