# Open decisions

Items waiting on the owner rather than on work. Do not resolve these alone: each one closes by
writing an ADR ([ADR-0011](../adr/0011-record-decisions-as-adrs.md)). An entry marked
`Needs decision` blocks the code that depends on it rather than inviting a guess.

---

### D1 — Scala 3 target version

**Status:** Needs decision

[ADR-0006](../adr/0006-scala-3-7-rather-than-3-3-lts.md) compiles the Scala 3 module with 3.7.3,
for named tuples, and excludes users on 3.3 LTS. Now that the Scala 3 module is the showcase
([ADR-0009](../adr/0009-two-native-modules-no-shared-code.md)), that exclusion weighs more. If
a Scala 3 LTS release with named tuples exists, it is the natural target. To settle: check the
current Scala 3 release and LTS lines at the source, then keep 3.7.3 or supersede ADR-0006.

### D2 — The name "Jev" in the library name

**Status:** Needs decision

"Jev" is a TypeSafe AI product name ([ADR-0007](../adr/0007-name-coordinates-and-package.md)).
Should the owner ask TypeSafe AI before publishing under `jev4s`, or publish and rename if they
ask?

### D3 — When mutation testing runs in CI

**Status:** Blocked on M1

[ADR-0008](../adr/0008-full-coverage-and-mutation-testing.md) runs Stryker4s on every pull
request while that is affordable, otherwise nightly. M1 gives the first measurement of how long
a run takes in each module; decide then.
