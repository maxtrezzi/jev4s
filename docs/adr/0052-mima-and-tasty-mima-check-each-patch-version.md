# ADR-0052: MiMa and TASTy-MiMa check each patch version against its minor version

- **Status:** Accepted
- **Date:** 2026-09-27
- **Supersedes:** —
- **Amends:** —

## Context

The pom declares `versionScheme := "early-semver"`
([ADR-0051](0051-publish-with-sbt-from-a-tag-on-main.md)). sbt reads it to decide whether
two versions of jev4s on one classpath can be evicted safely: in `0.x`, `0.1.1` may
replace `0.1.0`, `0.2.0` may not. The declaration is a promise, and M7 left open whether and how
to check it after `0.1.0`.

What was measured on 2026-09-27, with sbt 1.13.0 and Scala 3.9.0, on a scratch project and on
jev4s published to a scratch repository as `0.1.0`:

- **MiMa 1.2.1** compares bytecode, and works on both modules. It found a public method of the
  2.13 module that gained a parameter. It reported nothing for three changes that break a Scala 3
  caller: a different case of a match type, a `Seq[Int]` parameter that becomes `Int*`, and in
  jev4s, `AnswerOf` answering `List[a]` instead of `a`. The bytecode erases all three.
- **The Scala 3 API is built on what the bytecode erases**: the match type `AnswerOf` in the
  result of `ask`, derivation through `Mirror` with evidence computed from types, and the
  `inline` `Probability.apply`.
- **TASTy-MiMa** compares the Scala types. Its sbt plugin 1.4.0 reads TASTy up to 28.7
  (Scala 3.7); Scala 3.9 writes 28.9. With the core 1.4.1 and tasty-query 1.9.0 through the
  plugin's two override settings, it reads 3.9 and reports the three changes above, each with
  its filter.
- **TASTy-MiMa reads only the JDK's `java.base`**, so every member of jev4s whose type mentions
  `java.net.http` was an internal error, until `java.net.http` joined its boot classpath.
- **TASTy-MiMa cannot read the Scala 3 test kit.** tasty-query fails with an `AssertionError`
  (`TypeRef ... has no underlying because it refers to a ClassSymbol`) on `FakeAnswer`, a match
  type on `AnswerOf` whose bound is the union `Answer`, even with no change at all. Ten lines
  reproduce it outside jev4s, on Scala 3.7.4 with the plugin as released.
- **TASTy-MiMa missed one break**: `AnswerOf` without its bound `<: Answer` passed, although a
  caller who relies on the bound stops compiling.
- sbt-version-policy 3.3.0 wraps MiMa, approximates source compatibility with MiMa in the other
  direction, and checks the dependencies; it does not run TASTy-MiMa, and each release declares
  its intention.

## Forces

- **No check** costs nothing, and `0.x` lets a minor version break the API. But a patch that
  breaks it contradicts the pom, and the users who find out are the ones whose build evicted
  `0.1.0` for `0.1.1` on sbt's word.
- **MiMa alone** checks the 2.13 module well, and the Scala 3 module only where it looks like
  Java: it cannot see the part of the API that makes jev4s worth using.
- **TASTy-MiMa** sees that part, at the price of two version overrides and one classpath line,
  and with the gaps above. Scala 3.9 is the LTS the module stays on
  ([ADR-0017](0017-scala-3-9-lts.md)), so the overrides change rarely.
- **sbt-version-policy** adds a dependency check, which matters little with one dependency
  ([ADR-0005](0005-minimal-dependencies-ujson-and-the-jdk.md)), and a declaration at each
  release, next to the version that ADR-0051 already writes by hand.

## Decision

- **Which releases**: a version `x.y.z` keeps the API of `x.y.0` to `x.y.(z-1)`, the earlier
  patches of its minor version. `build.sbt` derives them from the version (`compatibleReleases`),
  so `0.1.0`, `0.2.0` and every `-SNAPSHOT` before them compare with nothing, and
  `mimaFailOnNoPrevious` is off. From `1.0`, a minor version keeps the API of the earlier minor
  versions as well, and that needs a new decision.
- **MiMa** (sbt-mima-plugin 1.2.1) checks the four published artifacts.
- **TASTy-MiMa** (sbt-tasty-mima 1.4.0, core 1.4.1, tasty-query 1.9.0) checks `jev4s_3`, with
  `java.net.http` on its JDK classpath. Not `jev4s-testkit_3`, until tasty-query reads
  `FakeAnswer`.
- **Where**: in CI, in the test job of each module on JDK 21, and in the release workflow before
  the artifacts are signed.
- **A break that must ship** goes into a new minor version, never into a filter, while jev4s is
  `0.x`. A filter is for a reported change that breaks nobody, such as a change to a member that
  is not public API, and each one carries a comment that says why.

## Consequences

- From `0.1.1`, a patch that breaks a caller's build, in Scala 3 types or in bytecode, fails CI
  before it merges and the release workflow before it publishes.
- The Scala 3 test kit's types are checked by nobody: a change to `FakeAnswer` is caught only by
  review. Its bytecode is checked.
- Removing a bound from a type such as `AnswerOf` passes both checks. Review must catch it.
- A Scala 3 upgrade that writes a newer TASTy needs a newer tasty-query in `build.sbt`; until
  then, the TASTy check fails on every version with something to compare.
- Do not remove the overrides because the plugin "should" choose its versions: 1.4.0 cannot read
  Scala 3.9. Do not add `jev4s-testkit_3` to the TASTy check before trying it on `FakeAnswer`.
- Reporting the tasty-query failure upstream is for the owner to decide.
