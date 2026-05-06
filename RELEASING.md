# Releasing

- 1. Verify CI is passes on the [Actions tab](https://github.com/cdr-chakotay/ALS_Kt/actions) and confirm the latest commit on `main` passes.

- 2. Summarize changes in `CHANGELOG.md`. Write a heading for the release and include changes:
  - **Added:** — new features
  - **Changed:** — changes in existing functionality
  - **Fixed:** — bug fixes
  - **Removed:** — features removed in this release
  - **Breaking:** - Highlight what might break existing installs and how to mitigate that

- 3. Bump the version in `gradle.properties` following [Semantic Versioning](https://semver.org/):
- 4. Commit `gradle.properties` and `CHANGELOG.md` together with a message like `Release X.Y.Z`, merge to `main`, and wait for the CI to pass.

- 5. Create the GitHub release
     - Go to the repo's [Releases page](https://github.com/cdr-chakotay/ALS_Kt/releases) and draft a new release.
     - Add a matching tag to the version file. Select **"Create new tag on publish"**.
     - Set the release target to `main`.
     - Set release title: `X.Y.Z` and the summary sentence from `CHANGELOG.md`.
     - Paste the matching section from `CHANGELOG.md` as description.

6. Click "Publish release" or cast a pre-release if applicable.

## If something goes wrong

- **Workflow failed before publish step**: fix the issue, delete the GitHub release and tag, repeat from step 2.
- **Workflow failed during publish step**: Maven Central may have partially received artifacts. Check [central.sonatype.com](https://central.sonatype.com). If the version is visible there we cannot reuse that version number. In this case we proceed with a bump to the next patch release.
