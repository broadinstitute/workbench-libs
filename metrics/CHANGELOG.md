# Changelog

This file documents changes to the `workbench-metrics` library, including notes on how to upgrade to new versions.

## 0.1

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-metrics" % "0.1-715fd75"`

### Added

- This library
- `WorkbenchInstrumented`, a mixin trait for instrumenting arbitrary code
- `InstrumentationDirectives.instrumentRequest`, an akka-http directive for instrumenting incoming HTTP requests
