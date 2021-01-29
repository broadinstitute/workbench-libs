# Changelog

This file documents changes to the `workbench-notifications` library, including notes on how to upgrade to new versions.

## 0.4

### Changed
- Modified compiler scala 2.12 and 2.13 compiler settings and commented out: 
  `"-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.`

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.4-TRAVIS-REPLACE-ME"`

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-util" % "0.3-426a0c2"`

### Changed
- Moved `SamModel`

### Added
- Support scala 2.13

## 0.2
- upgrade cats to 1.4.0 and scala to 2.12.7
- turn on more scalac options. upgrade scala to 2.12.11

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-notifications" % "0.2-8d718f2"`

## 0.1

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-notifications" % "0.1-2ce3359"`

### Added

- This library
