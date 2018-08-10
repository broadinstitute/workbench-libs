# Getting Started with workbench-libs

Many services rely on workbench-libs so it's important to be aware of breaking changes. See [CONTRIBUTING.md](CONTRIBUTING.md).

During development, you can make changes to workbench-libs and use them immediately in other services without waiting for CI builds. You don't even have to publish to Artifactory (though you can if you want to share work-in-progress).

Before starting, set artifactory credentials in your environment:

```
export ARTIFACTORY_USERNAME=dsdejenkins
export ARTIFACTORY_PASSWORD=$(docker run -v $HOME:/root --rm broadinstitute/dsde-toolbox:dev vault read -field=password secret/dsde/firecloud/common/artifactory/dsdejenkins)
```

## Bootstrapping

1. Check [CONTRIBUTING.md](CONTRIBUTING.md) to decide whether or not you need a major or minor version bump and update `Settings.scala` if so
1. `sbt +publish-local -Dproject.isSnapshot=true`, or `sbt "porject <project_name>" +publish-local -Dproject.isSnapshot=true` (Note: **`publish-local`**)
1. Search output for a line like `published workbench-service-test_2.12 to https://broadinstitute.jfrog.io/broadinstitute/libs-release-local;build.timestamp=1517520351/org/broadinstitute/dsde/workbench/workbench-service-test_2.12/0.1-99285a4-SNAP/workbench-service-test_2.12-0.1-99285a4-SNAP.jar` and copy the `0.1-99285a4-SNAP` part
1. Update versions in dependent projects as needed

## Development

1. Write code
1. `sbt +publish-local -Dproject.isSnapshot=true`, or `sbt "porject <project_name>" +publish-local -Dproject.isSnapshot=true` if you want to publish a specific project (Note: **`publish-local`**)

## Making Intermediate Commits

1. Create a branch from `develop`
1. Commit changes
1. Redo Bootstrapping steps if making further changes

## Sharing Branch Artifacts via Artifactory

1. Commit changes to branch (push optional)
1. `sbt +publish -Dproject.isSnapshot=true`, or `sbt "porject <project_name>" +publish -Dproject.isSnapshot=true`
1. Search output for a line like `published workbench-service-test_2.12 to https://broadinstitute.jfrog.io/broadinstitute/libs-release-local;build.timestamp=1517520351/org/broadinstitute/dsde/workbench/workbench-service-test_2.12/0.1-99285a4-SNAP/workbench-service-test_2.12-0.1-99285a4-SNAP.jar` and copy the `0.1-99285a4-SNAP` part
1. Share version # with all your friends

## Sharing Artifacts with the World

1. Double-check [CONTRIBUTING.md](CONTRIBUTING.md) to decide whether or not you need a major or minor version bump
1. Update [README.md](README.md) for new artifact version(s). Find the relevant "Latest SBT dependency" lines, update the version number if required, and replace the short hash with `TRAVIS-REPLACE-ME`, for example `0.9-e094fde` => `0.9-TRAVIS-REPLACE-ME`
1. Update the `CHANGELOG.md`s for modified libraries. On the "SBT dependency" line, use the same `TRAVIS-REPLACE-ME` version as above.
1. Create PR, get thumbs, and merge. travis will automatically insert the short git commit hash.
1. Update dependent projects with new non-`-SNAP` versions as needed

## Additional Info

Artifact versions are based on a stated version (`major`.`minor`), the git commit hash, and an optional `-SNAP` suffix for work-in-progress. Because of the use of the commit hash, don't forget to update the version in dependent projects when publishing further changes after a commit.

It’s important to use the non-SNAP version of workbench-libs that Travis publishes upon merging to develop. If you use a SNAP version as a dependency when you merge your code into develop, it’s possible that you’ll be missing code that has since been merged to workbench-libs by another developer. You may still see some SNAPs lingering around, those are probably OK at the moment because there used to be very little contention in the workbench-libs repo, but we really should be avoiding SNAPs from now on.

When publishing branch builds to Artifactory (`sbt publish`), code _must_ be committed (to a branch) to avoid clobbering artifacts using the hash from `develop`.

When publishing for local use (`sbt publish-local`), artifacts are simply dropped into your Ivy cache (`~/.ivy2`) so the only foot you can shoot is your own. As long as you use `-Dproject.isSnapshot=true` to make a `-SNAP`-versioned artifact, you can safely experiment with changes prior to your first branch commit.

If we ever get all dependants moved to Scala 2.12 (I'm looking at you, [firecloud-orchestration](https://github.com/broadinstitute/firecloud-orchestration)), we can remove the `+` from the `sbt publish`/`sbt publish-local`. If your use case during development uses only 2.12, you can probably omit the `+` to possibly reduce build/publish time.

**Future improvement**: Make sbt show the versions of all published artifacts at the end of the build so we don't have to scan and extract them from the build output.