**PR checklist**
- [ ] For each library you've modified here, decide whether it requires a major, minor, or no version bump. (Click [here](/broadinstitute/workbench-libs/blob/develop/CONTRIBUTING.md) for guidance)

If you're doing a **major** or **minor** version bump to any libraries:
- [ ] Bump the version in `project/Settings.scala` `createVersion()`
- [ ] Update `CHANGELOG.md` for those libraries
- [ ] I promise I used `@deprecated` instead of deleting code where possible

In all cases:
- [ ] Add `TRAVIS-REPLACE-ME` to `README.md` and the `CHANGELOG.md` for any libs you changed to auto-update the version string
- [ ] Get two thumbsworth of PR review
- [ ] Verify all tests go green (CI _and_ coverage tests)
- [ ] Squash commits and **merge to develop**
- [ ] Delete branch after merge
