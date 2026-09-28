# Changelog

All notable changes to jev4s. The versions follow
[early semantic versioning](https://www.scala-lang.org/blog/2021/02/16/preventing-version-conflicts-with-versionscheme.html):
while the version is `0.x`, a new minor version, such as `0.2.0`, can break your code, and a new
patch version, such as `0.1.1`, cannot. jev4s stays at `0.x` while the Jev API is in early access.

Each version publishes four artifacts, all with the same version: `jev4s_3`, `jev4s_2.13`,
`jev4s-testkit_3` and `jev4s-testkit_2.13`.

A new kind of `JevEvent` is always listed here: a `match` on the events that lists every case
warns until it handles the new one.

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
