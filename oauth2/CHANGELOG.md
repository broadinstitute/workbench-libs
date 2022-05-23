# Changelog

This file documents changes to the `workbench-oauth2` library, including notes on how to upgrade to new versions.

## 0.2

Changed:
- Changed method on `OpenIDConnectConfiguration` to return `OpenIDProviderMetadata` case class instead of strings
- Fixed issue causing akka-http `Stream cannot be materialized more than once` on `/oauth2/token` endpoint

Added:
- Added methods for clientId and authorityEndpoint to `OpenIDConnectConfiguration`
- Added akka-http route `/oauth2/configuration` which returns JSON containing the clientId and authorityEndpoint

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-oauth2" % "0.2-1962b9a"`

## 0.1

- Initial commit
- Provides functionality for /oauth2 routes needed by services rolling out B2C
- Includes swagger-ui dependency and provides Swagger routes

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-oauth2" % "0.1-a78f6e9"`

