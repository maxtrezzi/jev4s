# ADR-0057: The box question answers with a Region

- **Status:** Proposed
- **Date:** 2026-11-01
- **Supersedes:** —
- **Amends:** —

## Context

Jev 1.14 (2026-10-14) adds a fourth kind of question, `box`: it locates what its instructions
describe in the request's image. In the API reference (read 2026-11-01), the question has
`instructions` only, which may be `null`, and no `criteria`. The answer is:

```json
{ "type": "box", "box": { "top": 0.2, "bottom": 0.66, "left": 0.19, "right": 0.68 }, "confidence": null }
```

The four edges are fractions from 0 to 1: `top` and `bottom` of the height, from the top; `left`
and `right` of the width, from the left. The schema describes each one as the expected value of
its edge's position, so nothing promises `top <= bottom`. `confidence` is a number from 0 to 1,
or `null` when the model does not give one; the field is always there. A `box` question needs
the images of [ADR-0056](0056-images-travel-in-the-state-as-base64-assets.md) and its header.

Measured on 2026-11-01 with raw HTTP calls, all answered by `jev-1.14.0`, on images with one
filled rectangle at a known place:

- In 27 answers, `top <= bottom` and `left <= right` always held, and `confidence` was never
  `null`.
- On 448×448 images, `left`, `right` and `bottom` were within 0.03 of the truth; `top` was too high,
  by up to 0.07 below 512 pixels.
- A `box` question about something not in the image answered with a box anyway, with a
  confidence of 0.33, against 0.93 for the right one.
- **A `box` question with no image answered 200**, with an invented box
  (`top 0.21, bottom 0.96, left 0.04, right 0.64`) and a confidence of 0.1. The API does not refuse
  it.
- Noul and Choice questions about an image work as they do about text.

When accepted, this ADR amends [ADR-0021](0021-what-the-codec-keeps-from-a-jev-reply.md) (the
answers the codec reads), [ADR-0022](0022-the-scala-3-client-api.md) and
[ADR-0023](0023-the-scala-2-13-client-api.md) (the message of `AllQuestions`), and
[ADR-0048](0048-a-test-kit-answers-with-typed-values.md) (the test kits). The branch that
implements it sets `Amends` here and records the amendment in their status.

## Forces

- **Instructions are optional in the API**, but a box with nothing to locate says nothing useful,
  and in 2.13 every `Question` has `def instructions: ujson.Value`.
- **The answer cannot be called `Box`**: that is the question. Both SDKs call the edges
  `BoxCoordinates`; a `Region` reads better in user code.
- **`null` confidence** is in the schema, even if it did not occur. `inputTokens` already shows
  how jev4s keeps a value the API may leave out ([ADR-0035](0035-a-reply-without-input-tokens-still-answers.md)).
- **How strict the codec is.** ADR-0053 rejects a reply that breaks what the caller will rely on.
  An edge outside [0, 1] breaks `inPixels`. An edge order that is reversed breaks nothing the
  library computes, if `width` and `height` do not assume it, and the schema allows it.
- **A box with no image** costs a paid request and returns a meaningless box with a low
  confidence. The `Validator` can see it before anything is sent.

## Decision

Proposed, Scala 3 first, then 2.13:

- **`Box(instructions: ujson.Value)`**, a `Question[BoxAnswer]`: a case of the `Question` enum in
  Scala 3, a case class in 2.13. Keys and typed tuples
  ([ADR-0037](0037-the-2-13-client-answers-with-a-typed-tuple.md)) need no change.
- **`BoxAnswer(region: Region, confidence: Option[Probability])`**, with
  `ifConfident(min: Probability): Option[Region]`, `None` also when there is no confidence.
- **`Region(top, bottom, left, right: Double)`**, as the API sends them, with `width`, `height`
  and `inPixels(width: Int, height: Int)`. `width` and `height` do not assume the order of the
  edges.
- **The codec** reads `type: "box"`, the four edges and `confidence`. An edge that is not a
  number from 0 to 1 is `JevError.Decoding`; `null` confidence is `None`; a missing `confidence`
  field is `JevError.Decoding`. It does not check the order of the edges.
- **`Answer` gains `BoxAnswer`**, in the Scala 3 union and the 2.13 sealed trait. The
  `AllQuestions` message becomes "Noul, Score, Choice or Box", in the README, the guide and the
  test of [ADR-0044](0044-documented-compile-errors-are-checked-by-a-test.md).
- **`Problem.BoxWithoutImage(name)`**, when a request has a `box` question and no image.
- **The test kits:** `FakeAnswer` of a `Box` is a `BoxAnswer`, and the fake reply writes `box`
  and `confidence`.
- It ships with ADR-0056, as 0.2.0.

## Consequences

- `r.dog.ifConfident(Probability(0.8)).map(_.inPixels(w, h))` gives a rectangle to draw or crop,
  typed in both modules.
- A `match` on an `Answer` that lists every case warns until it handles `BoxAnswer`: the CHANGELOG
  says so, as it does for a new `JevEvent`.
- A box with no image is a `Problem` and costs nothing.
- Do not drop `ifConfident` "since a box is always returned": a box about something absent comes
  back with a low confidence, and that is the only sign. Do not check the edge order in the codec
  "for safety": the schema allows a reversed pair, and the answer would be lost for a property
  nothing in jev4s needs.
