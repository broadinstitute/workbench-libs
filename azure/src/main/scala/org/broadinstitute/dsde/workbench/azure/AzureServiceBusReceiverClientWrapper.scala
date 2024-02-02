package org.broadinstitute.dsde.workbench.azure

// A simple wrapper around the Azure ServiceBusSenderAsyncClient to facilitate testing and reduce coupling with the Azure SDK.
private[azure] trait AzureServiceBusReceiverClientWrapper {
  def startProcessor(): Unit
  def stopProcessor(): Unit
}

private object AzureServiceBusReceiverClientWrapper {
  def createReceiverClientWrapper(
    subscriberConfig: AzureServiceBusSubscriberConfig,
    messageHandler: AzureReceivedMessageHandler
  ): AzureServiceBusReceiverClientWrapper =
    new AzureServiceBusReceiverClientWrapperInterp(subscriberConfig, messageHandler)
}
