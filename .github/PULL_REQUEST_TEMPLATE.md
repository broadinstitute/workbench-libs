**PR checklist**
- [ ] For each library you've modified here, decide whether it requires a major, minor, or no version bump. (Click [here](/broadinstitute/workbench-libs/blob/develop/CONTRIBUTING.md) for guidance)

If you're doing a **major** or **minor** version bump to any libraries:
- [ ] Bump the version in `project/Settings.scala` `createVersion()`
- [ ] Update `CHANGELOG.md` for those libraries
- [ ] I promise I used `@deprecated` instead of deleting code where possible

In all cases:
- [ ] Get two thumbsworth of PR review
- [ ] Verify all tests go green (CI _and_ coverage tests)
- [ ] Squash commits and **merge to develop**
- [ ] Delete branch after merge

After merging, _even if you haven't bumped the version_: 
- [ ] Update `README.md` and the `CHANGELOG.md` for any libs you changed with the new dependency string. The git hash is the same short version you see in GitHub (i.e. seven characters). You can commit these changes straight to develop.
