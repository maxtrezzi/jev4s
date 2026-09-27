# ADR-0053: An answer has a probability for every level and option

- **Status:** Accepted
- **Date:** 2026-09-27
- **Supersedes:** —
- **Amends:** ADR-0021

## Context

[ADR-0021](0021-what-the-codec-keeps-from-a-jev-reply.md) keys the `probabilities` of a
`ScoreAnswer` by the question's levels, and rejects a key that is not a level; the same holds for
a `ChoiceAnswer` and its options. It did not say what happens when the reply leaves a level or an
option out: the codec accepted it, and the map had fewer entries than the question.

The documentation reads the map with `apply`. ADR-0021's own consequence is
`answer.probabilities("Very angry")`, and the Scaladoc of the Scala 3 `ScoreAnswer` shows
`probabilities(Mood.Angry)`. On a map without that key, `apply` throws `NoSuchElementException`,
in the caller's code, although no call of jev4s throws
([ADR-0002](0002-direct-style-no-effect-system.md)).

The review before the first release (T30) found it. The nine real replies in `golden/`, read on
2026-09-27, give every level and every option a probability, zeros included, so the API as
measured never triggers it; a gateway, or a later version of the API, could.

## Forces

- **Where the check belongs.** The codec is where a reply is read, and already turns every
  malformed reply into `JevError.Decoding`. A check there makes the map total once, for every
  caller.
- **Documenting `get` instead** keeps the codec as it is and moves the burden to every caller,
  in both modules, for a reply that the API does not send.
- **Filling the missing entries with 0** keeps the call successful, but invents probabilities
  that Jev did not give, and hides a reply that does not match the question.
- **The test kits** build the reply from the answers a test gives. A whole `ScoreAnswer` or
  `ChoiceAnswer` with a level missing would now decode to `JevError.Decoding` in the code under
  test, far from the mistake.

## Decision

- **The codec requires a probability for every level of a Score and every option of a Choice.**
  A reply that leaves one out is `JevError.Decoding`, naming the question and the first level or
  option missing, in the question's order: `'q': no probability for the level 'Low'`,
  `'q': no probability for the option 'b'`.
- **`mostLikely` is the level with the highest probability among all levels**, the lower one on a
  tie, as before; it no longer needs a case for a reply with no probabilities.
- **The test kits refuse a whole answer whose `probabilities` leave out a level or an option**,
  with `IllegalArgumentException` when the client is built, as they already refuse an answer that
  is not one of the question's levels or options.

## Consequences

- `probabilities(level)` never fails for a level or an option of the question, in either module.
  A misspelt text level still fails with `apply`, and gives `None` with `get`.
- A reply with a partial distribution fails the whole call, although its other answers may be
  fine. That is the rule for every malformed reply since ADR-0021.
- A test that gave a partial whole answer must now give every level or option, zeros included,
  as the tutorials' third test of chapter 12 does.
- Do not relax the check "to be tolerant" of a gateway: the map would stop being total, and the
  exception would move back into the caller's code.
