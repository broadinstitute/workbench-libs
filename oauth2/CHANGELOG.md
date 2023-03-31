# Changelog

This file documents changes to the `workbench-oauth2` library, including notes on how to upgrade to new versions.

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-oauth2" % "0.3-TRAVIS-REPLACE-ME"`

### Dependency upgrades
| Dependency   |      Old Version      |  New Version |
|----------|:-------------:|------:|
| azure-resourcemanager-compute |  xxx | 2.25.0 |
| azure-resourcemanager-containerservice |  xxx | 2.25.0 |
| azure-storage-blob |  xxx | 12.21.1 |
| cats-effect |  xxx | 3.4.8 |
| circe-core |  xxx | 0.14.5 |
| circe-fs2 |  xxx | 0.14.1 |
| client-java |  xxx | 17.0.1 |
| fs2-io |  xxx | 3.6.1 |
| google-api-services-container |  xxx | v1-rev20230304-2.0.0 |
| google-cloud-bigquery |  xxx | 2.20.2 |
| google-cloud-container |  xxx | 2.16.0 |
| google-cloud-dataproc |  xxx | 4.10.0 |
| google-cloud-nio |  xxx | 0.126.10 |
| google-cloud-pubsub |  xxx | 1.123.7 |
| google-cloud-storage |  xxx | 2.20.2 |
| google-cloud-storage-transfer |  xxx | 1.13.0 |
| grpc-core |  xxx | 1.51.3 |
| http4s-circe |  xxx | 1.0.0-M39 |
| jackson-module-scala |  xxx | 2.14.2 |
| logstash-logback-encoder |  xxx | 7.3 |
| sbt-scoverage |  xxx | 2.0.7 |
| scalatest |  xxx | 3.2.15 |

## 0.2

Changed:
- Changed method on `OpenIDConnectConfiguration` to return `OpenIDProviderMetadata` case class instead of strings
- Fixed issue causing akka-http `Stream cannot be materialized more than once` on `/oauth2/token` endpoint
- Support authority endpoints with a dynamic policy on the query string, e.g.:
   - https://terradevb2c.b2clogin.com/terradevb2c.onmicrosoft.com/v2.0?p=B2C_1A_SIGNUP_SIGNIN
- Update swagger-ui to 4.11.1

Added:
- Added methods for clientId and authorityEndpoint to `OpenIDConnectConfiguration`
- Added akka-http route `/oauth2/configuration` which returns JSON containing the clientId and authorityEndpoint

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-oauth2" % "0.2-c70ff12"`

## 0.1

- Initial commit
- Provides functionality for /oauth2 routes needed by services rolling out B2C
- Includes swagger-ui dependency and provides Swagger routes

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-oauth2" % "0.1-a78f6e9"`

