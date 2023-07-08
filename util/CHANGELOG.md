# Changelog

This file documents changes to the `workbench-util` library, including notes on how to upgrade to new versions.

## 0.9
- Added increased retry interval backoff
- Added configurable interval backoff function

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.9-32f499b"`

## 0.7

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.7-d764a9b"`

### Dependency upgrades
| Dependency   |      Old Version      |  New Version |
|----------|:-------------:|------:|
| azure-resourcemanager-compute |  2.17.0 | 2.25.0 |
| azure-resourcemanager-containerservice |  2.19.0 | 2.25.0 |
| azure-storage-blob |  12.19.1 | 12.21.1 |
| cats-effect |  3.4.4 | 3.4.8 |
| circe-core |  0.14.3 | 0.14.5 |
| circe-fs2 |  0.14.0 | 0.14.1 |
| client-java |  17.0.0 | 17.0.1 |
| fs2-io |  3.4.0 | 3.6.1 |
| google-api-services-container |  v1-rev20221110-2.0.0 | v1-rev20230304-2.0.0 |
| google-cloud-bigquery |  2.20.0 | 2.20.2 |
| google-cloud-container |  2.10.0 | 2.16.0 |
| google-cloud-dataproc |  4.4.0 | 4.10.0 |
| google-cloud-nio |  0.126.0 | 0.126.10 |
| google-cloud-pubsub |  1.122.2 | 1.123.7 |
| google-cloud-storage |  2.16.0 | 2.20.2 |
| google-cloud-storage-transfer |  1.6.0 | 1.13.0 |
| grpc-core |  1.51.1 | 1.51.3 |
| http4s-circe |  1.0.0-M35 | 1.0.0-M38 |
| jackson-module-scala |  2.14.1 | 2.14.2 |
| logstash-logback-encoder |  7.2 | 7.3 |
| sbt-scoverage |  2.0.6 | 2.0.7 |
| scalatest |  3.2.14 | 3.2.15 |

## 0.6
### Changed
- Moved code that depends on `circe`, `fs2` to `util2` since these libraries no longer support 2.11
- Cross build 2.13
- Target java 11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.6-a78f6e9"`

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
