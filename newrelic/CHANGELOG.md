# Changelog

This file documents changes to the `workbench-newrelic` library, including notes on how to upgrade to new versions.

## 0.2

### Added
- Added `NewRelicMetrics` trait
- Added `FakeNewRelicMetricsInterpreter`

### Changed
- Changed constructor to `NewRelicMetrics.fromNewRelic(appName)`
- Renamed `NewRelicMetrics` class to `NewRelicMetricsInterpreter`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-newrelic" % "0.2-ad29822"`

## 0.1

### Added
- Add `NewRelicMetrics`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-newrelic" % "0.1-c5db8e4"`
