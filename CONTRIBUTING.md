# Contributing

This repo contains libraries used by many services in the Workbench ecosystem. For this reason API stability is especially important.

If you are making breaking API changes, do the first one of these that you can:

1. Don't make breaking API changes.
2. Mark functionality as `@deprecated` but don't change it. Explain how to upgrade in the deprecation message.
3. Mark functionality as `@deprecated` and change its behavior as little as possible (i.e. provide a similar (or sensible-default) implementation). Explain both the difference in behavior _and_ how to upgrade in the deprecation message.
4. Only if a similar or sensible-default implementation using the same API is impossible: remove the functionality entirely to cause an error in downstream code. This should be rare (and requires a major version bump).

## Versioning

Developers should take care to increment library versions when the library changes in ways that may affect users.

To do this, find the `val xxxSettings =` block in `project/Settings.scala` for the library you are incrementing, and 
change the string inside the call to `createVersion()` using the scheme outlined below.

You should also update `README.md` and document your changes and upgrade path at `<yourlib>/CHANGELOG.md`. See 
[keepachangelog.com](http://keepachangelog.com/) for some guidance on the latter.  For any services you have updated, 
change the git hash in these files to the string TRAVIS-REPLACE-ME.  Travis will update this text to the correct hash values. 

### No version bump necessary

Users can drop-in replace the updated version of this library without making any code changes. Their code will continue to work as before.

You will still need to update `README.md` with the latest hash after you merge to develop.

Examples where no version bump is necessary include:

- Obvious bugfixes
- Addition of optional parameters to existing, publicly accessible classes or functions that only modify behavior when specified
- Addition of new public functions on classes
- Changes to private / protected functionality that do not affect stated behavior

### Minor version bump

Users can drop-in replace the updated version of this library with zero or very minimal code changes. If changes are required, no thinking should be required to make them: "remove an argument" is fine, but "pass in some new information" is not. Users may encounter deprecation warnings or minor changes to behavior. They may also have to update their imports.

Examples where a minor version bump is necessary include:

- Changes to the public API of any function or class
- Moving classes between packages
- Behavioral changes

To make a minor version bump, increment the version by 0.1.

### Major version bump

Examples where a major version bump is necessary include:

- Deletion of anything in the public API (functions, classes, etc)
- Movement of classes between libraries
- Breaking API changes that will require significant rewriting on the part of clients, including deletion of any functionality

To make a major version bump, increment the version by 1.0.

## Testing

We run coverage tests on this repo. Please try to avoid merging PRs that fail coverage requirements.

Defining your mocks in `src/test` rather than `src/main` will help greatly.

## Publishing

Travis automatically builds Scala 2.12 and 2.13 versions of these libraries and publishes them to [Artifactory](https://broadinstitute.jfrog.io/broadinstitute/webapp/#/artifacts/browse/tree/General/libs-release-local/org/broadinstitute/dsde/workbench/). Commits to any other branch than develop will be labelled `<version>-<githash>-SNAP`; commits to develop will be labelled `<version>-<githash>`. In both cases, the `<githash>` is the first 7 characters of the commit hash. You probably shouldn't be using `-SNAP` versions in downstream code.
