# Changelog

This file documents changes to the `workbench-util` library, including notes on how to upgrade to new versions.

## 0.1

### Added

- This project
- `FutureSupport` contains functions for working with `Future`s
- `Retry` contains functions for retrying `Future`s, including with exponential backoff
- `MockitoTestUtils` contains Scala sugar for Mockito's `ArgumentCaptor`
- some handy time-conversion functions in the `util` package

### Upgrade notes

If you're moving from the `workbench-util` published by Rawls, you'll have to do the following things:

- Move imports from `org.broadinstitute.dsde.rawls.util` to `org.broadinstitute.dsde.workbench.util`
- You might need to upgrade `"com.typesafe.akka" %% "akka-actor"` to `2.5.3`
    - You may find Akka has deprecated a few functions you use. `ActorSystem.shutdown()` has been replaced with `ActorSystem.terminate()`.
- If you're using `MockitoTestUtils`, upgrade `"org.scalatest" %% "scalatest"` to `3.0.1` and `"org.mockito" % "mockito-core"` to `2.8.47`
