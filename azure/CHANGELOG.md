# Changelog

This file documents changes to the `workbench-azure` library, including notes on how to upgrade to new versions.

## 0.2

### Changed

- AzureVmService now calls deallocateAsync instead of powerOffAsync when pausing an Azure virtual machine.

## 0.1

### Added

- Added AzureStorageService and AzureStorageManualTest
- Added AzureContainerService#listClusters

latest SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-azure" % "TRAVIS-REPLACE-ME"`
