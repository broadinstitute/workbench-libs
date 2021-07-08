# Changelog

This file documents changes to the `workbench-util2` library, including notes on how to upgrade to new versions.

## 0.1

- Add `ConsoleLogger`
- Target java 11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util2" % "0.1-89d0d9e"`

Moved a few utilities that depends on `circe`, `fs2` from `util` to `util2`
