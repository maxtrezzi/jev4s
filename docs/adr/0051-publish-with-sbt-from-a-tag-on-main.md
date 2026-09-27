# ADR-0051: Publish with sbt's own Central Portal support, from a tag on `main`

- **Status:** Accepted
- **Date:** 2026-09-27
- **Supersedes:** —
- **Amends:** —

## Context

M7 publishes four artifacts to Maven Central at one version: `jev4s_3`, `jev4s_2.13`,
`jev4s-testkit_3` and `jev4s-testkit_2.13` ([ADR-0048](0048-a-test-kit-answers-with-typed-values.md)).
The namespace `io.github.maxtrezzi` is already verified on the Central Portal: the owner publishes
modelrack4j under it. A version on Maven Central can never be changed or deleted.

Fixed by earlier decisions: a release is a squashed pull request from `dev` to `main`, whose
subject is the version, and that commit on `main` carries the tag
([ADR-0013](0013-one-branch-per-task-work-lands-on-dev.md)). After a release, `dev` and `main`
hold the same tree with unrelated histories, so no tag is ever reachable from `dev`.

What the sources say, read on 2026-09-27:

- Sonatype closed the legacy OSSRH endpoint on 2025-06-30. sbt publishes to the Central Portal
  on its own from 1.11: `publishTo := localStaging.value`, then `publishSigned` stages the
  signed artifacts and `sonaRelease` uploads and releases them. sbt reads the Portal token from
  `SONATYPE_USERNAME` and `SONATYPE_PASSWORD`. Signing still needs sbt-pgp (2.3.2), which calls
  the `gpg` command and reads the passphrase from `PGP_PASSPHRASE`.
- sbt-ci-release (1.12.1) wraps the same steps, and adds sbt-dynver, which derives the version
  from the nearest git tag. Its workflow publishes a snapshot on every push to `main`, and no
  setting turns that off.

## Forces

- **The version from the tag does not fit ADR-0013.** With sbt-dynver, `dev` never sees a tag,
  so every build there is `0.0.0+<commits>-<sha>`: `publishLocal` from `dev` gives a version that
  says nothing, and the README's instructions would have to name it. Each commit on `main` is a
  release, so the snapshots that sbt-ci-release publishes from `main` are useless as well.
- **A version written in `build.sbt` is visible in review.** The release pull request changes
  it, next to the CHANGELOG, and the diff shows what is being released. The cost is one line to
  change by hand, and a tag that can disagree with it: the workflow checks that.
- **Fewer plugins.** sbt's own support needs only sbt-pgp; sbt-ci-release adds sbt-dynver, which
  the point above rules out, and its own release commands on top of sbt's.
- **The same secrets either way.** Both read `PGP_SECRET`, `PGP_PASSPHRASE`, `SONATYPE_USERNAME`
  and `SONATYPE_PASSWORD`, so the choice can be changed later without new secrets.
- **The JDK of the published jar.** jev4s supports JDK 17, and CI builds on 17, 21 and 25. A jar
  compiled on 21 against 21's library can call an API that 17 lacks, and still pass every test
  on 21.

## Decision

- **sbt's own Central Portal support, with sbt-pgp.** `ThisBuild / publishTo` is
  `localStaging.value`; a release runs `publishSigned` and then `sonaRelease`. No sbt-ci-release,
  no sbt-dynver.
- **The version is written in `build.sbt`** as `ThisBuild / version`, a `-SNAPSHOT` on `dev`
  between releases. The release pull request sets the version to release and adds its section
  to the CHANGELOG; the next change on `dev` after the release moves it to the next
  `-SNAPSHOT`. `versionScheme` is `early-semver`: in `0.x`, a new minor version may break the API.
- **The release workflow runs on a tag `v<major>.<minor>.<patch>`**
  (`.github/workflows/release.yml`), and publishes only when the tagged commit is on `main`, the
  version of every published project is the version of the tag, and the tests pass. Snapshots are
  not published.
- **The bytecode and the API are JDK 17's**: `-java-output-version:17` in the Scala 3 module and
  `-release:17` in the 2.13 module, so a call to a newer API fails to compile on any JDK. The
  release job runs on JDK 17 as well.
- **The owner holds the credentials**: the Portal token and the signing key live only in the
  repository's secrets, and the owner pushes the tag.

## Consequences

- `sbt publishLocal` on `dev` gives the `-SNAPSHOT` that `build.sbt` names, as the README says.
- Forgetting to set the version in the release pull request cannot publish a wrong version: the
  tag and the build disagree, and the workflow stops before signing.
- A release is four steps: a pull request on `dev` that sets the version and the CHANGELOG; a
  squashed pull request from `dev` to `main` whose subject is the version; the tag on that commit;
  and, after the release, a change on `dev` to the next `-SNAPSHOT`.
- Do not "simplify" by adding sbt-dynver or sbt-ci-release: on the squashed history of ADR-0013,
  the version of `dev` would stop meaning anything. Do not drop the `-release` options because
  CI tests on JDK 17: the published jar is built by one JDK, not by the matrix.
- A binary compatibility check after `0.1.0` (MiMa, TASTy-MiMa) is not part of this decision.
