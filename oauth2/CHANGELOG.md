# Changelog

This file documents changes to the `workbench-oauth2` library, including notes on how to upgrade to new versions.

## 0.5

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-oauth2" % "0.5-TRAVIS-REPLACE-ME"`

### Dependency upgrades
| Dependency   | Old Version | New Version |
|----------|:-----------:|------------:|
| sbt-scoverage |    2.0.8    |       2.0.9 |
| scalatest |   3.2.16    |      3.2.17 |
| scala       |   2.13.11   |     2.13.12 |
| sbt-scalafix       |   0.11.0    |      0.11.1 |
| jose4j      |    0.9.3    |       0.9.4 |

## 0.4

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-oauth2" % "0.4-d764a9b"`

## 0.3

SBT dependency: `"org.broadinstitute.dsde.workbench" %% "workbench-oauth2" % "0.3-01a11c3"`

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
| jackson-module-scala |  2.14.1 | 2.15.0 |
| logstash-logback-encoder |  7.2 | 7.3 |
| sbt-scoverage |  2.0.6 | 2.0.7 |
| scalatest |  3.2.14 | 3.2.16 |

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

