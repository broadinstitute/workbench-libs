# Changelog

This file documents changes to the `workbench-util` library, including notes on how to upgrade to new versions.

## 0.6
### Changed
- Moved code that depends on `circe`, `fs2` to `util2` since these libraries no longer support 2.11
- Cross build 2.13
- Target java 11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.6-65bba14"`

## 0.5
- add retry for IO
- upgrade cats-effect
- add `org.broadinstitute.dsde.workbench.util.PropertyBasedTesting`
- add `org.broadinstitute.dsde.workbench.util.readJsonFileToA`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.5-4bc7050"`

### Changed
- Moved `SamModel`

## 0.4
- bump cats-core to 1.4.0
- fixed scaladoc compilation errors
- turn on more scalac options. upgrade scala to 2.11.12
- add `cats.effect.Resource` for readFile and ExecutionContext

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.4-8d718f2"`

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.3-2771e2d" % "test" classifier "tests"`


### Added

- `NoopActor`, an actor that receives all messages and doesn't do anything with them. This is in the test package.
- Custom subsystem
- DelegatePool

## 0.2

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.2-0b34b93"`

### Changed

- FutureSupport's `withTimeout` function now takes an implicit `akka.actor.Scheduler` instead of an `akka.actor.ActorContext`. The latter is hard to find and schedulers are everywhere.
- `addJitter` now applies a max jitter of 10% for durations <= 10s, and a max jitter of 1s otherwise
- `Retry` methods now return a `RetryableFuture[T]` which tracks intermediate errors. It comes with an implicit conversion to `Future[T]` so callers need not take action if they only care about the final result.
- Added HealthMonitor
- Added implicit class FutureTry
- Added GoogleIam subsystem
- Added Consent, LibraryIndex, OntologyIndex, Rawls subsystems

### Upgrade notes

- Calls to `withTimeout` may fail with a compiler error complaining that it needs an implicit `Scheduler` when you've provided an `ActorContext`. If you have an `ActorContext`, you're likely inside an Actor, so you can provide `system.scheduler` instead.

## 0.1

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.1-e8bdfd0"`

### Added

- This library
- `FutureSupport` contains functions for working with `Future`s
- `Retry` contains functions for retrying `Future`s, including with exponential backoff
- `MockitoTestUtils` contains Scala sugar for Mockito's `ArgumentCaptor`
- some handy time-conversion functions in the `util` package

### Upgrade notes

#### Moving from Rawls' `workbench-util`

If you're moving from the `workbench-util` published by Rawls, you'll have to do the following things:

- Move imports from `org.broadinstitute.dsde.rawls.util` to `org.broadinstitute.dsde.workbench.util`
- You might need to upgrade `"com.typesafe.akka" %% "akka-actor"` to `2.5.3`
    - You may find Akka has deprecated a few functions you use. `ActorSystem.shutdown()` has been replaced with `ActorSystem.terminate()`.
- If you're using `MockitoTestUtils`, upgrade `"org.scalatest" %% "scalatest"` to `3.0.1` and `"org.mockito" % "mockito-core"` to `2.8.47`
