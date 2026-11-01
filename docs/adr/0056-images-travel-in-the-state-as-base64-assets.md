# ADR-0056: Images travel in the state, as base64 assets, behind an explicit opt-in

- **Status:** Proposed
- **Date:** 2026-11-01
- **Supersedes:** —
- **Amends:** —

## Context

Jev 1.14 (2026-10-14) reads PNG and JPEG images. The API reference (read 2026-11-01) adds
`assets` to the request: a map from an opaque placeholder to an image, with `type: "image"`,
`format` and `data_base64`, or `part` in a `multipart/form-data` body. Each placeholder is
replaced by its image wherever it appears as, or inside, a string in `state`, `instructions` or
`criteria`. A request with assets, or with a `box` question
([ADR-0057](0057-the-box-question-answers-with-a-region.md)), must send the header
`TypeSafe-Danger-Zone-Images: true`: TypeSafe calls images experimental and does not promise them
for production.

Measured on 2026-11-01 with raw HTTP calls, all answered by `jev-1.14.0`:

- Both `jev-latest` and `jev-preview` take images. `GET /v1/models` lists only these two.
- Without the header: 400, `Images are experimental. Send the header
  TypeSafe-Danger-Zone-Images: true to use them.` jev4s 0.1.0 already returns it as
  `JevError.Rejected` with that message.
- Each side of an image must be at least 280 pixels, and at most a value between 1536 and 1791;
  otherwise 400, `Invalid image dimensions.` The number of pixels does not matter: 280×2000 is
  refused, 1536×1536 is accepted. The reference does not document any limit.
- About one request in five got 503, `Image processing is unavailable.`, valid ones included.
  The same request sent again always passed. jev4s retries a 503 as a `ServerError`
  ([ADR-0024](0024-http-retries-and-configuration.md)).
- Base64 and multipart gave the same answer and the same token count.
- An image costs about one input token per 380 pixels: 819 tokens for a 448×448 image with one
  question, 3045 for 1024×1024.
- A placeholder works in the `instructions` as well as in the `state`.

Today the state is a `ujson.Value`, from `ToState[S]` ([ADR-0022](0022-the-scala-3-client-api.md),
[ADR-0023](0023-the-scala-2-13-client-api.md)), and `Transport.send` takes and returns a `String`.

When accepted, this ADR amends ADR-0022 and ADR-0023 (`ToState`) and ADR-0024 (`JevConfig` and the
headers of `JdkTransport`). The branch that implements it sets `Amends` here and records the
amendment in their status.

## Forces

- **Multipart is what both official SDKs send** (Python 0.7.5, npm 0.7.0), and it carries a
  third fewer bytes. But `Transport.send(body: String)` is a public trait: multipart needs bytes
  and a content type, so every transport written by a user would break, and `java.net.http` has
  no multipart publisher. Base64 keeps the body a `String`: `Transport`, test fakes and retries
  stay as they are, with `java.util.Base64` ([ADR-0005](0005-minimal-dependencies-ujson-and-the-jdk.md)).
- **The header is the transport's job**, since `Transport` owns headers, but `JdkTransport` only
  sees the body. Sending it on every request ignores that TypeSafe asks for an informed opt-in.
  Finding `"assets"` in the body guesses at JSON in a component that does not read it. An
  explicit setting in `JevConfig` names what the caller accepts, as the SDKs do with their
  default headers.
- **Where an image goes.** `instructions` and `criteria` are `ujson.Value`s
  ([ADR-0031](0031-instructions-and-criteria-are-ujson-values.md)), a closed type that cannot hold
  an image. A `Content` type in their place, with an interpolator such as `img"Is $photo
  blurry?"`, is the most ergonomic and reverses ADR-0031. Images in the state cover the main use,
  a question about a picture, and the same placeholder can later go into a question.
- **The placeholder.** The SDKs give each image object a new random UUID. A placeholder derived
  from the image's bytes makes the same image give the same request: tests, `golden/` and the
  test kits stay deterministic, and two copies of one image become one asset.
- **Reading a file** can fail, and errors are values ([ADR-0002](0002-direct-style-no-effect-system.md));
  the library does no I/O of its own besides HTTP. `Files.readAllBytes` is one line for the
  caller.
- **The size limits** are undocumented and can change without notice. Checking them in jev4s
  means parsing PNG and JPEG headers to follow a number TypeSafe does not publish. A refused
  request reports no usage, and its message is clear.

## Decision

Proposed, Scala 3 first, then 2.13:

- **`Image`**, built only from bytes: `Image(bytes: Array[Byte]): Either[ImageError, Image]` and
  `Image.fromBase64(text: String): Either[ImageError, Image]`. The format, PNG or JPEG, comes from
  the first bytes; anything else, or no bytes, is an `ImageError`. The array is copied and never
  handed out. Two images are equal when their bytes are. In 2.13, `Image` is `Serializable`, for
  Spark ([ADR-0041](0041-the-spark-example-builds-its-client-on-the-executors.md)).
- **`image.ref`**, the placeholder, is derived from the SHA-256 of the bytes.
- **Images go in the state.** `ToState[S]` returns the state's JSON with the images it uses. A
  given for `Image` sends the image alone; a state value pairs any JSON with its images, as in
  `State(ujson.Obj("photo" -> photo.ref, "caption" -> caption), photo)`. The exact shape is
  settled in T33.
- **The codec writes each distinct image once in `assets`**, as `data_base64` with its `format`.
  The body stays a `String`, and `Transport` does not change.
- **`JevConfig(dangerZones: Set[DangerZone] = Set.empty)`**, with `DangerZone.Images`.
  `JdkTransport` sends `TypeSafe-Danger-Zone-Images: true` on every request when it is set. A
  transport of the caller's own sends its own headers.
- **The `Validator` reports** a placeholder in the state with no image, and an image that the
  state does not use. To confirm in T33 what the API does with an unused asset.
- **No size limit in the code.** The guide states the limits measured on 2026-11-01, with the
  date.
- **This ships as 0.2.0.** A new field in `JevConfig` changes its `apply`, `copy` and `unapply`,
  and `ToState` changes its result: MiMa and TASTy-MiMa refuse both in a patch
  ([ADR-0052](0052-mima-and-tasty-mima-check-each-patch-version.md)). To confirm by running them
  in T33.

## Consequences

- A caller asks about a picture with `client.ask(photo, questions)` once the config opts in, in
  both modules.
- A request without the opt-in fails with the API's own message, as a `JevError.Rejected`.
- A request body is a third larger than with multipart, and stays in memory whole: a 1536×1536
  PNG is a few megabytes at most. Multipart can come later inside `JdkTransport` without changing
  the model.
- Code that implements `ToState` for its own type changes, and so does code that builds a
  `JevConfig` with positional arguments or matches on it.
- Do not read files in `Image` "for convenience": it brings I/O errors into the library. Do not
  replace the content-derived placeholder with a random one "to match the SDKs": the requests
  stop being deterministic.
