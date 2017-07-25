[![Build Status](https://travis-ci.org/broadinstitute/workbench-libs.svg?branch=develop)](https://travis-ci.org/broadinstitute/workbench-libs) [![Coverage Status](https://coveralls.io/repos/github/broadinstitute/workbench-libs/badge.svg?branch=develop)](https://coveralls.io/github/broadinstitute/workbench-libs?branch=develop)

# workbench-libs
Workbench utility libraries. In this repo:

## workbench-utils

Contains:

- Exponential backoff retries
- `FutureSupport.toFutureTry`, a function which turns `Future[T]` into a `Future.successful()` with the `Try` containing the status of the `Future`. 
- `MockitoTestUtils.captor`, some Scala sugar for Mockito's `ArgumentCaptor`
