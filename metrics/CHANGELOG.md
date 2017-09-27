# Changelog

This file documents changes to the `workbench-metrics` library, including notes on how to upgrade to new versions.

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-metrics" % "0.3-0085f3f"`

### Changed

- The `WorkbenchInstrumented.asXXX` functions now return our own metric types so they don't immediately forget their own names.
  - You may need to switch some imports from `nl.grons.metrics.scala` to `org.broadinstitute.dsde.workbench.metrics`.

### Added

- Support for transient metrics (e.g. metrics that get automagically deleted in Hosted Graphite).

## 0.2

### Added

- Added WorkbenchStatsD which filters out detailed Timer metrics from being sent to StatsD.
- Added `ExpandedMetricBuilder.asGaugeIfAbsent` method to handle gauge name conflicts.
- Added InstrumentedRetry trait which updates a histogram with the number of errors in a `RetryableFuture`.

### Changed 

- Updated method signature of `org.broadinstitute.dsde.workbench.metrics.startStatsDReporter` to take an API key

## 0.1

### Added

- This library
- `WorkbenchInstrumented`, a mixin trait for instrumenting arbitrary code
- `InstrumentationDirectives.instrumentRequest`, an akka-http directive for instrumenting incoming HTTP requests
