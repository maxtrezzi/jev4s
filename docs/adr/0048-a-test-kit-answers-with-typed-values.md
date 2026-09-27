# ADR-0048: A test kit answers with typed values

- **Status:** Accepted
- **Date:** 2026-09-25
- **Supersedes:** —
- **Amends:** —

## Context

Chapter 12 of both tutorials tested the caller's code over `JevClient.withTransport`, with a
reply written in the API's JSON, while the concepts guide says "With jev4s you never write this
JSON". D4 asked for a way to answer with typed values instead, and listed three options: a helper
in each published module, a separate test artifact, or the files in `golden/` as fixtures. The
owner chose the separate artifact on 2026-09-25.

The client decodes a reply from a `Transport`, so a fake that answers with typed values has to
write the reply those values would come from. `typesafe-sdk-scala` records and replays real
replies instead (its README, read 2026-09-25).

## Forces

- **The published modules stay small** ([ADR-0005](0005-minimal-dependencies-ujson-and-the-jdk.md)):
  an encoder of answers is test code, and does not belong in the jar that runs in production.
- **One artifact per Scala version, no shared code** ([ADR-0009](0009-two-native-modules-no-shared-code.md)).
  The test kit is written for each module, in that module's style, Scala 3 first.
- **What a test reads must be what a real reply gives.** If the kit built answers directly, a
  `mostLikely` or a `normalized` could disagree with the `Codec`. Writing a reply and letting the
  client decode it keeps one source of truth, the `Codec`, at the cost of an encoder that must
  match it. The tests check the encoder against the real replies in `golden/`: a reply decoded
  by the client, given back to the kit, comes back the same.
- **The short form and the whole answer.** A test mostly says "Billing" or "urgent": the kit
  takes a level, an option or a `Boolean`. A test of a confidence limit needs a whole
  `ChoiceAnswer` or `ScoreAnswer`, so the kit takes those too.
- **A fake answer that its question cannot have.** A level that is not among the Score's, an
  option that is not among the Choice's: the reply could not name it. The API of jev4s returns
  errors as values ([ADR-0002](0002-direct-style-no-effect-system.md)), but this is a mistake in
  the test, and a test fixture that fails at once, with the name and the value, is what a test
  needs. A `Left` would surface as a confusing failure later, or not at all.
- **Alternatives considered.** A helper in each published module: less to publish, but the
  encoder ships to production. The `golden/` files as fixtures: nothing to build, but the tests
  still hold JSON, and the answers are those of the recordings, not the ones a test needs.
  Recorded replies, as `typesafe-sdk-scala` has: they need the paid API to record, and are a
  later addition to the kit, not a replacement.

## Decision

- Two artifacts, `jev4s-testkit_3` and `jev4s-testkit_2.13`, in `testkit/scala3` and
  `testkit/scala213` (sbt projects `scala3Testkit` and `scala213Testkit`), package
  `io.github.maxtrezzi.jev4s.testkit`. Each depends on its module, at the same version, and on
  nothing else; a caller adds it with `% Test`. The root aggregates them, and they are held to
  full coverage and zero undetected mutants ([ADR-0008](0008-full-coverage-and-mutation-testing.md)).
- Scala 3: `JevTestkit.answering(questions, model, onEvent)(answers)`, where `answers` is a named
  tuple with the names of `questions` and, for each, a `FakeAnswer` of its question:
  `Boolean | Probability` for a Noul, `L | ScoreAnswer[L]` for a `Score[L]`, `C | ChoiceAnswer[C]`
  for a `Choice[C]`. The compiler checks the names and the types.
- Scala 2.13: `import io.github.maxtrezzi.jev4s.testkit._` gives each key `is(...)`, with the same
  answers as overloads, and `JevTestkit.answering(team.is(Team.Billing), urgent.is(true))`, or
  `answering(model, onEvent)(...)`. The compiler checks that each answer has its key's type.
- Both: `JevTestkit.failing(error)` returns `error` on every call; `JevTestkit.defaultModel` is
  `jev4s-testkit`, the model the reply names unless the test gives one.
- The kit writes the reply once, when the client is built, and returns it for every request
  without reading it. A level, an option or `true` is all the probability on it, with a
  confidence of 1; `false` is 0. A whole answer gives its score, confidence and probabilities;
  `normalized` and `mostLikely` come from the question when the client decodes the reply. The
  reply has no `usage`, so `Reply.inputTokens` is `None` ([ADR-0035](0035-a-reply-without-input-tokens-still-answers.md)).
- An answer that is not one of its question's levels or options throws
  `IllegalArgumentException` when the client is built, naming the question and the value.
- M7 publishes the kit with the modules, and CI tests, covers and mutates it in each module's
  jobs. The tutorials' chapter 12 tests with it.

## Consequences

- A caller's tests hold typed values, not JSON, and the compiler checks them as it checks `ask`.
- Two more artifacts to publish and keep at the modules' version, and two more projects in the
  coverage and mutation jobs.
- The encoder of the kit must follow the `Codec`: a change to the reply format changes both, and
  the round trip over `golden/` fails until they agree.
- A question asked under a name the kit has no answer for gets `JevError.Decoding`, as a real
  reply without it would; the kit does not check the request.
- Do not move the encoder into the published modules "to share it with the `Codec`": that is the
  option this decision rejected.
- Do not make a wrong fake answer a `Left`: it would hide a mistake in the test behind an error
  the code under test may handle.
