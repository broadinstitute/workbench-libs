package org.broadinstitute.dsde.workbench.azure

import com.azure.messaging.servicebus.{ServiceBusMessage, ServiceBusReceivedMessage}
import org.awaitility.Awaitility._

import scala.concurrent.duration._
import scala.util.Try
/*
 * This is a manual test for AzureServiceBusSenderClientWrapper and AzureServiceBusReceiverClientWrapper.
 * The goal of this test is to makes sure that the AzureServiceBusSenderClientWrapper and AzureServiceBusReceiverClientWrapper
 * can be instantiated and used to send and receive messages to/from Azure Service Bus.
 * This test is no intended to validate the message conversion logic, which is tested in unit tests and should be converted to
 * integration/automated test in the future.
 *
 * To launch this follow these steps:
 * 1. sbt "project workbenchAzure" test:console
 * 2. :paste
 * Copy/Paste the following lines:
      import org.broadinstitute.dsde.workbench.azure._
      val topic = "YOUR_TOPIC_NAME
      val connStr = "YOUR_CONNECTION_STRING"
      val subs = "YOUR_SUBSCRIPTION_NAME
      val pubConfig = AzureServiceBusPublisherConfig(topic,Some(connStr))
      val subConfig = AzureServiceBusSubscriberConfig(topic,subs,None,Some(connStr))
      val test = new AzureServiceBusClientsManualTest(pubConfig, subConfig)

 * 3. Execute test methods:
      test.sendMessage()
      test.receiveMessage()
      test.sendReceiveMultipleMessages(10) //sends 10 messages
 */
final class AzureServiceBusClientsManualTest(
  azureServiceBusPublisherConfig: AzureServiceBusPublisherConfig,
  azureServiceBusSubscriberConfig: AzureServiceBusSubscriberConfig
) {
  def sendMessage(): Unit = {
    val message = new ServiceBusMessage("test message")

    val sender = AzureServiceBusSenderClientWrapper.createSenderClientWrapper(azureServiceBusPublisherConfig)

    sender
      .sendMessageAsync(message)
      .doOnSuccess(_ => println("Message sent"))
      .doOnError(e => println(s"Error sending message: ${e.getMessage}"))
      .block(AzureServiceBusPublisherConfig.defaultTimeout)
  }

  def receiveMessage(): Unit = {
    println(s"Starting to receive messages from ${azureServiceBusSubscriberConfig.subscriptionName}")

    var msgCount = 0
    // for testing we are using a simple handler that counts the messages received.
    // the logic that converts the service bus message to an AzureEvent is tested in AzureSubscriberSpec
    val handler = new AzureEventMessageHandler {
      def handleMessage(message: ServiceBusReceivedMessage): Try[Unit] = Try {
        println(s"Handling message: ${message.getBody}")
        msgCount += 1
        ()
      }
    }

    val receiver =
      AzureServiceBusReceiverClientWrapper.createReceiverClientWrapper(azureServiceBusSubscriberConfig, handler)

    receiver.startProcessor()

    try {
      await().atMost(10, SECONDS).until(() => msgCount == 1)
      println(s"Received $msgCount messages")
    } catch {
      case e: Exception => println(s"Error receiving message: ${e.getMessage}")
    } finally {
      print("Stopping processor")
      receiver.stopProcessor()
    }
  }

  def sendReceiveMultipleMessages(numOfMessages: Int = 5): Unit = {
    var msgCount = 0

    // for testing we are using a simple handler that counts the messages received.
    // the logic that converts the service bus message to an AzureEvent is tested in AzureSubscriberSpec
    val handler = new AzureEventMessageHandler {
      def handleMessage(message: ServiceBusReceivedMessage): Try[Unit] = Try {
        println(s"Handling message: ${message.getBody}")
        msgCount += 1
        ()
      }
    }

    val receiver =
      AzureServiceBusReceiverClientWrapper.createReceiverClientWrapper(azureServiceBusSubscriberConfig, handler)

    receiver.startProcessor()

    val sender = AzureServiceBusSenderClientWrapper.createSenderClientWrapper(azureServiceBusPublisherConfig)

    for (i <- 1 to numOfMessages) {
      val message = new ServiceBusMessage(s"test message $i")
      sender
        .sendMessageAsync(message)
        .doOnSuccess(_ => println(s"Message $i sent"))
        .doOnError(e => println(s"Error sending message $i: ${e.getMessage}"))
        .block(AzureServiceBusPublisherConfig.defaultTimeout)
    }

    try {
      await().atMost(60, SECONDS).until(() => msgCount == numOfMessages)
      println(s"Received $msgCount messages")
    } catch {
      case e: Exception => println(s"Error receiving message: ${e.getMessage}")
    } finally {
      print("Stopping processor")
      receiver.stopProcessor()
    }
  }
}
