# Contributing

This repo contains libraries used by many services in the Workbench ecosystem. For this reason API stability is especially important.

If you are making breaking API changes, do the first one of these that you can:

1. Don't make breaking API changes.
2. Mark functionality as `@deprecated` but don't change it. Explain how to upgrade in the deprecation message.
3. Mark functionality as `@deprecated` and change its behavior as little as possible (i.e. provide a similar (or sensible-default) implementation). Explain both the difference in behavior _and_ how to upgrade in the deprecation message.
4. Only if a similar or sensible-default implementation using the same API impossible: remove the functionality entirely to cause an error in downstream code. This should be rare (and requires a major version bump).

## Versioning

Developers should take care to increment library versions when the library changes in ways that may affect users.

To do this, find the `val xxxSettings =` block in `project/Settings.scala` for the library you are incrementing, and change the string inside the call to `createVersion()` using the scheme outlined below.

Major and minor version increments also require a **GitHub release**. To do this, go [here](https://github.com/broadinstitute/workbench-libs/releases/new), and create a new tag `vX.Y` pointing at the relevant commit on develop. Write release notes explaining the changes and how to upgrade.

### No version bump necessary

Users can drop-in replace the updated version of this library without making any code changes. Their code will continue to work as before.

- Obvious bugfixes
- Addition of optional parameters to existing, publicly accessible classes or functions that only modify behavior when specified
- Addition of new public functions on classes
- Changes to private / protected functionality that do not affect stated behavior

### Minor version bump

Users can drop-in replace the updated version of this library without making any code changes. However, they may encounter deprecation warnings or minor changes to behavior. They may also have to update their imports.

- Changes to the public API of any function or class
- Moving classes between packages
- Behavioral changes

### Major version bump

- Deletion of anything in the public API (functions, classes, etc)
- Movement of classes between libraries
- Breaking API changes that will require significant rewriting on the part of clients, including deletion of any functionality

## Publishing

Travis automatically builds Scala 2.11 and 2.12 versions of these libraries and publishes them to [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/). Commits to any other branch than develop will be labelled `<version>-<githash>-SNAP`; commits to develop will be labelled `<version>-<githash>`. You probably shouldn't be using `-SNAP` versions in downstream code.
