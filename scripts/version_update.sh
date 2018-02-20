#!/usr/bin/env bash

set -e

# thanks to https://gist.github.com/willprice/e07efd73fb7f13f917ea

# branch we're updating
PUSH_BRANCH="develop"

# name for our tokenized git remote (arbitrary)
REMOTE_NAME="token"

# text string target for auto-replacement
TO_REPLACE="TRAVIS-REPLACE-ME"

HASH=$(git rev-parse HEAD)

git config --global user.email "travis@travis-ci.org"
git config --global user.name "Travis CI"

# ignore failure if the remote does not exist
git remote rm ${REMOTE_NAME} || true
git remote add ${REMOTE_NAME} https://${GH_TOKEN}@github.com/broadinstitute/workbench-libs.git > /dev/null 2>&1
git fetch ${REMOTE_NAME}
git checkout ${REMOTE_NAME}/${PUSH_BRANCH}

# Don't run twice: prevent Scala 2.11 runs
if [[ "$TRAVIS_PULL_REQUEST" == "false" && "$TRAVIS_BRANCH" == "develop" && "$TRAVIS_SCALA_VERSION" == "2.12" ]]; then
  for f in README.md */CHANGELOG.md
    do
      echo "Replacing ${TO_REPLACE} with ${HASH} in ${f}..."
      sed -i "s/${TO_REPLACE}/${HASH}/g" ${f}

      # will do nothing if there's nothing to add
      git add ${f}

      # will fail if no change: ignore it
      git commit --message "Auto update hash in ${f} to ${HASH}" || true
    done

  # push one commit per update.  Will fail if no changes: ignore it
  git push ${REMOTE_NAME} HEAD:${PUSH_BRANCH} || true

  git remote rm ${REMOTE_NAME}
  git checkout -
fi
