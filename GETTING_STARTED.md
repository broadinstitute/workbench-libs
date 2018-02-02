# Getting started with workbench-libs

Before reading this, read over [CONTRIBUTING.md](CONTRIBUTING.md) to get a better understanding of versioning and how to deal with code deprecation. A lot of services rely on workbench-libs so it’s important to avoid all breaking changes.

You’ve made your changes to workbench-libs and you want to use them during development. Here’s how to do that:

1. Commit your changes to your workbench-libs branch
2. Set the artifactory credentials as environment variables, like so:  
```
export ARTIFACTORY_PASSWORD=getThisFromJenkins
export ARTIFACTORY_USERNAME=getThisFromJenkinsToo
```
Note: Get the credentials from a Jenkins build job, like rawls-build
3. Publish your snapshot:  
```
sbt +publish -Dproject.isSnapshot=true
```
4. sbt will now build a whole bunch of jars. It will publish Scala 2.11 and 2.12 versions for workbench-google, workbench-service-test, workbench-util, workbench-model, and workbench-metrics. Look for whichever package you care about, and grab it’s version number. The version number will be a combination of the package version number, the commit hash, and whether or not the release is a SNAP. 
```
Example:  [info] 	published workbench-service-test_2.12 to https://broadinstitute.jfrog.io/broadinstitute/libs-release-local;build.timestamp=1517520351/org/broadinstitute/dsde/workbench/workbench-service-test_2.12/0.1-99285a4-SNAP/workbench-service-test_2.12-0.1-99285a4-SNAP.jar
```
5. Replace the version number in your target repo’s dependencies file (probably in Dependencies.scala) and make sure IntelliJ refreshes the dependency.
6. Go through PR process etc etc etc
7. Merge your workbench-libs branch first. Travis will build an official non-SNAP version of workbench-libs. Grab this version number and update the dependency in your target repo to use this non-SNAP version. You can find it in the Travis build history of your commit here.
8. Now you can merge your branches that depend on your workbench-libs changes.

## Things to keep in mind

It’s important to use the non-SNAP version of workbench-libs that Travis publishes upon merging to develop. If you use a SNAP version as a dependency when you merge your code into develop, it’s possible that you’ll be missing code that has since been merged to workbench-libs by another developer. You may still see some SNAPs lingering around, those are probably OK at the moment because there used to be very little contention in the workbench-libs repo, but we really should be avoiding SNAPs from now on.


