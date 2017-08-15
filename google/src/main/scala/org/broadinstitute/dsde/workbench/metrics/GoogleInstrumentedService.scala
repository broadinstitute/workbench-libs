package org.broadinstitute.dsde.workbench.metrics

/**
  * An instrumented Google service.
  */
object GoogleInstrumentedService extends Enumeration {
  type GoogleInstrumentedService = Value
  val Billing, Storage, Genomics, Groups, PubSub = Value

  /**
    * Expansion for GoogleInstrumentedService which uses the default toString implementation.
    */
  implicit object GoogleInstrumentedServiceExpansion extends Expansion[GoogleInstrumentedService]
}
