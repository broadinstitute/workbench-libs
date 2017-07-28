# Changelog

This file documents changes to the `workbench-metrics` library, including notes on how to upgrade to new versions.

## 0.1

<<<<<<< 8c1da70ee3034eeb6fce9c671b889ae8d2e6ad4d
SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-metrics" % "0.1-715fd75"`
=======
SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-metrics" % "0.1-xxxxxxx"`
>>>>>>> moved metrics

### Added

- This library
- `WorkbenchInstrumented`, a mixin trait for instrumenting arbitrary code
- `InstrumentationDirectives.instrumentRequest`, an akka-http directive for instrumenting incoming HTTP requests
