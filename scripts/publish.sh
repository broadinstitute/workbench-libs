#!/usr/bin/env bash

set -e

if [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
	sbt +publish -Dproject.isSnapshot=false
else
	sbt +publish -Dproject.isSnapshot=true
fi
