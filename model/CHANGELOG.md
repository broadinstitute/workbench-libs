# Changelog

This file documents changes to the `workbench-model` library, including notes on how to upgrade to new versions.

## 0.1

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-model" % "0.1-xxxxxxx"`

### Added

- This project
- `ErrorReport`

### Upgrade notes

If you're moving from the `workbench-util` published by Rawls, you'll have to do the following things:

- Move imports from `org.broadinstitute.dsde.rawls.model` to `org.broadinstitute.dsde.workbench.model`
- Upgrade from spray to akka-http
