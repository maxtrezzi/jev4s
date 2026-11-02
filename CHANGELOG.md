# Changelog

All notable changes to jev4s. The versions follow
[early semantic versioning](https://www.scala-lang.org/blog/2021/02/16/preventing-version-conflicts-with-versionscheme.html):
while the version is `0.x`, a new minor version, such as `0.2.0`, can break your code, and a new
patch version, such as `0.1.1`, cannot. jev4s stays at `0.x` while the Jev API is in early access.

Each version publishes four artifacts, all with the same version: `jev4s_3`, `jev4s_2.13`,
`jev4s-testkit_3` and `jev4s-testkit_2.13`.

A new kind of `JevEvent` is always listed here: a `match` on the events that lists every case
warns until it handles the new one.

## Unreleased

This will be version 0.2.0. It can break your code in three places: a `match` that lists every
kind of answer, or every `Problem`, and a `JevConfig` built with all its arguments by position or
matched on.

### Both modules

- **Images, experimental.** Jev 1.14 reads PNG and JPEG images. `Image(bytes)` builds an image
  from the bytes of a PNG or JPEG file, or returns an `ImageError`. Give the image as the state,
  or put its `ref` in JSON with `State(json, image)`, or give it in `images` of your own
  `ToState`. Turn images on with `JevConfig(..., dangerZones = Set(DangerZone.Images))` and a
  model that reads them, such as `jev-1.14.0`. TypeSafe calls images experimental, and does not
  promise them for production.
- **The Box question.** `Box("The dog")` asks where something is in the image. Its answer,
  `BoxAnswer`, has a `Region`, with four edges from 0 to 1, and a confidence, which can be
  missing. `ifConfident` gives the region only when Jev is sure enough, and `inPixels` gives it in
  pixels.
- **A new kind of answer, `BoxAnswer`.** A `match` on the answers of `askMap` that lists every
  kind warns until it handles `BoxAnswer`.
- **New checks before sending.** A request is not sent when it has a Box and no image
  (`Problem.BoxWithoutImage`), an image `ref` in the state without its image
  (`Problem.UnknownImage`), or an image that the state does not use (`Problem.UnusedImage`). A
  `match` on a `Problem` that lists every case warns until it handles these three.
- **`JevConfig` has a new field, `dangerZones`**, with no zone turned on by default. A `ToState`
  that you wrote keeps compiling: `images` has a default of no image.

### Scala 3

- `Region.inPixels` gives a named tuple, `(x, y, width, height)`.

### Scala 2.13

- `Region.inPixels` gives `Pixels(x, y, width, height)`, and `BoxAnswer.ifConfident` takes a
  `Double`, as the other answers do. An `Image` is `Serializable`, so a Spark closure can carry
  one.
- **Every answer can be serialized.** A `Probability` inside a `Map` or an `Option` could not, so
  in 0.1.0 a `ScoreAnswer` or a `ChoiceAnswer` failed with `NotSerializableException` when Spark
  sent it. `Probability` is `Serializable` now.

### Test kits

- A Box answers with a `Region`, which means a confidence of 1, or with a whole `BoxAnswer`.

## 0.1.0

The first release.

### Both modules

- The three kinds of question, `Noul`, `Score` and `Choice`, and a typed answer for each one.
  The options of a Choice and the levels of a Score can be your own types.
- A Score's answer gives its score, its score from 0 to 1 (`normalized`), and its most likely
  level (`mostLikely`). A Noul's answer gives "yes" or "no" when its probability is high enough
  (`ifConfident`).
- The `probabilities` of an answer hold every level of the Score, or every option of the
  Choice. A reply that leaves one out is a `JevError.Decoding`.
- `JevClient`, over the JDK's `java.net.http`: retries like the official SDKs, with a limit of
  30 seconds on each call. You can give it your own `HttpClient`, or your own `Transport`.
- Direct style: every call returns `Either[JevError, A]`, and no call throws an exception. A
  request with problems is not sent, and every problem is returned at once.
- The API key is an `ApiKey`: it never appears in a `toString` or in a log.
- Events for replies, retries and each HTTP response with its request id, given to a function of
  yours. jev4s never logs.
- `askMap`, for questions built at runtime.
- One runtime dependency: ujson.
- Works on JDK 17, 21 and 25.

### Scala 3 (`jev4s_3`, Scala 3.9 or later)

- Questions and answers are named tuples with the same names. A wrong name, an answer read as the
  wrong type, or a probability above 1 is a compile error.
- `derives JevChoice` and `derives JevScale` for your own enums.

### Scala 2.13 (`jev4s_2.13`, Scala 2.13.16 or later)

- Typed keys, 1 to 10 in a call, and the answers come back as a tuple.
- `JevChoice.named` and `JevScale.named` list your case objects once.
- Built with the Scala of Spark 4.0, so it runs on every Spark 4.

### Test kits (`jev4s-testkit_3`, `jev4s-testkit_2.13`)

- A client for your tests that answers with typed values, such as
  `(team = Team.Billing, urgent = true)`, with no network. A mistake in a test, such as an answer
  that is not one of the question's options, throws at once.
