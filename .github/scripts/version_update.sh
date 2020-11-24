#!/usr/bin/env bash

set -e

# thanks to https://gist.github.com/willprice/e07efd73fb7f13f917ea

# branch we're updating
PUSH_BRANCH="develop"

# name for our tokenized git remote (arbitrary)
REMOTE_NAME="token"

# text string target for auto-replacement
TO_REPLACE="TRAVIS-REPLACE-ME"

HASH=$(git rev-parse HEAD | cut -c -7)

git config --global user.email "broadbot@broadinstitute.org"
git config --global user.name "broadbot"

echo "Auto-updating version hashes..."

for f in README.md */CHANGELOG.md
  do
    echo "Replacing ${TO_REPLACE} with ${HASH} in ${f}..."
    sed -i "s/${TO_REPLACE}/${HASH}/g" ${f}

    # will do nothing if there's nothing to add
    git add ${f}

    # will fail if no change: ignore it
    git commit --message "Auto update hash in ${f} to ${HASH}" || true
  done

echo "Finished updating version hashes."
