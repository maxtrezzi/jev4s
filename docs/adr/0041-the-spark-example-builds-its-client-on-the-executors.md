# ADR-0041: The Spark example builds its client on the executors

- **Status:** Accepted
- **Date:** 2026-09-25
- **Supersedes:** —
- **Amends:** —

## Context

M8 asks for an Apache Spark example of the 2.13 module. Spark is the main reason the module
exists, and why it compiles with Scala 2.13.16, the Scala of Spark 4.0
([ADR-0036](0036-the-2-13-module-compiles-with-spark-4s-scala.md)). Spark 4.0.4 is the latest
4.0 patch on Maven Central (read 2026-09-25).

Spark runs a job's functions on executors, other JVMs, and sends them there serialized. Facts
that shape the example, measured on 2026-09-25 with Spark 4.0.4 in local mode:

- `JevClient` and `JdkTransport` are not `Serializable`. A function that captures the enclosing
  object fails with "Task not serializable"; a value that a function captures reaches the tasks
  as a copy.
- **Each action computes the Dataset again, and so calls Jev again.** For 10 tickets:
  `collect` made 10 calls, `count` 10, `orderBy("id").collect` 20, because the sort samples its
  input first. After `cache`, a first `count` made 10, then `orderBy` and `count` made none.
- The account's rate limit is shared by every executor
  ([ADR-0039](0039-the-library-does-not-limit-the-request-rate.md)): a limiter per process
  cannot see the others.
- Spark does not redact `spark.executorEnv.TYPESAFE_API_KEY` in its UI: the default
  `spark.redaction.regex` is `(?i)secret|password|token|access[.]?key` (spark-core 4.0.4).
- Spark 4.0.4 in local mode passed the example's tests on JDK 17 and 21 without the
  `--add-opens` options that Spark's launcher adds.

## Forces

- **Spark's dependencies are large.** Adding `spark-sql` to `scala213Live` would put them into
  the project that the guides' tests compile on every CI run of the 2.13 module.
- **An example that no build compiles goes stale**, and a test that runs Spark costs CI time.
- **Where the client comes from.** Built on the driver, it cannot travel; passed as a
  serialized config, the `ApiKey` would travel in every task. A function that builds it runs on
  the executor, reads the key from the executor's own environment, and lets a test pass a
  client over a fake transport.
- **The rate.** A pacer per partition at the account's rate multiplies the rate by the number of
  partitions. Dividing it by the number of partitions is simple and never goes over; it goes
  under when fewer partitions run at once than there are.
- **Errors are values** ([ADR-0002](0002-direct-style-no-effect-system.md)): one failed call
  must not fail a job that has already paid for the others.

## Decision

- The example lives in its own sbt project, `scala213Spark` in `live/spark`, which depends on
  `scala213Live` for the guide's `Pacer`, and on `spark-sql` 4.0.4 as `Provided`. The root does
  not aggregate it. Its `run`, `runMain` and tests use the `Provided` classes, in a forked JVM.
- `SparkTriage.triage(tickets, partitions, perSecond, newClient)` repartitions the tickets,
  builds one client per partition with `newClient` inside `mapPartitions`, and paces each
  partition at `perSecond / partitions`. Each ticket becomes a `Triaged` row, with the answer or
  the error as columns.
- Its tests run Spark in local mode with a fake transport or a local server: no key, no cost.
  CI checks the project's formatting and runs its tests in the 2.13 test job, on JDK 17 and 21.
- `docs/guide/spark.md` quotes the example ([ADR-0032](0032-documentation-quotes-compiled-examples.md)),
  and says what the measurements above mean for a caller: one action or a cached result, the
  key in the executors' environment and out of the Spark UI, the shared rate.

## Consequences

- The Spark version of the example moves with the oldest supported Spark 4, as the module's
  Scala does (ADR-0036).
- CI resolves Spark in the 2.13 test job; the sbt cache keeps it after the first run.
- A caller who copies `triage` and runs two actions on its result pays twice; the guide and a
  test say so, and the example makes one action.
- Do not build the client on the driver or put the `ApiKey` in a serialized config to "save" a
  client per partition: the first does not run, the second sends the key with every task.
- Do not move the example into `scala213Live` to "remove a project": Spark would then be
  resolved for every guide test.
