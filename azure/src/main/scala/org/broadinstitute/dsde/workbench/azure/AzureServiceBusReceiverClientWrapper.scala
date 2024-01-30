package org.broadinstitute.dsde.workbench.azure

// A simple wrapper around the Azure ServiceBusReceiverAsyncClient to facilitate testing
// given that the client is final and serializable, which prevents mockito from mocking it
trait AzureServiceBusReceiverClientWrapper {
  def startProcessor(): Unit
  def stopProcessor(): Unit
}

object AzureServiceBusReceiverClientWrapper {
  def createReceiverClientWrapper(
    subscriberConfig: AzureServiceBusSubscriberConfig,
    messageHandler: AzureReceivedMessageHandler
  ): AzureServiceBusReceiverClientWrapper =
    new AzureServiceBusReceiverClientWrapperInterp(subscriberConfig, messageHandler)
}
