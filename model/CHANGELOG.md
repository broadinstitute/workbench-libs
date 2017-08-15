# Changelog

This file documents changes to the `workbench-model` library, including notes on how to upgrade to new versions.

## 0.1

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.1-17b01fe"`

### Added

- This library
- `ErrorReport`

### Upgrade notes

#### ErrorReport

In order to use `ErrorReport` you must define your own `implicit val errorReportSource = ErrorReportSource("yourproject")` and have it in scope when you construct `ErrorReport` instances. Failing to do this can cause incredibly cryptic errors, especially if you're using akka-http code at the time.

#### Moving from Rawls' `workbench-util`

If you're moving from the `workbench-util` published by Rawls, you'll have to do the following things:

- Move imports from `org.broadinstitute.dsde.rawls.model` to `org.broadinstitute.dsde.workbench.model`
- Upgrade from spray to akka-http
