# ADR-0046: The Spark example holds one client per executor JVM

- **Status:** Accepted
- **Date:** 2026-09-25
- **Supersedes:** —
- **Amends:** [ADR-0041](0041-the-spark-example-builds-its-client-on-the-executors.md)

## Context

[ADR-0041](0041-the-spark-example-builds-its-client-on-the-executors.md) builds the Spark
example's client on the executors, with a function `newClient` that `SparkTriage.triage` calls
once per partition, inside `mapPartitions`. Each call builds a `JdkTransport`, and so a
`java.net.http.HttpClient` with its own threads, which jev4s never closes
([ADR-0038](0038-the-caller-may-pass-its-own-http-client.md)); the Scaladoc of `JevClient` warns
against a new client per request for this reason. A job has as many partitions as the rate
needs, and runs the same code again for each action, so the clients add up.

Measured on 2026-09-25, JDK 21, Spark 4.0.4 in local mode with two executor threads, 48 tickets
in 48 partitions against a local server, with the JDK's own counter of `HttpClient` ids:

| Client | `HttpClient`s built | Their threads alive after the job | After a GC |
|---|---|---|---|
| One per partition | 43, one per partition with rows | 78 | 0 |
| One per executor JVM | 1 | 6 | 6 |

A partition with no rows builds a `JdkTransport` but no `HttpClient`, which is built on the first
send. The threads of an unreachable `HttpClient` stop after a GC, so the cost is threads and
connections held until the next collection, not a leak that grows for ever.

This ADR amends ADR-0041 ("builds one client per partition with `newClient` inside
`mapPartitions`").

## Forces

- **The usual Spark answer is a `lazy val` in an `object`.** An `object` exists once in each JVM,
  and a function that names it does not serialize it: each executor builds the client the first
  time a task needs it, and keeps it for the tasks after. The key is still read on the executor
  and never travels.
- **The pacer's rate belongs to the partitions, not to the clients.** The account's limit is
  divided by the number of partitions, because Spark schedules partitions, and an executor runs
  as many at once as it has cores. A pacer per JVM would need the number of executors, which the
  code does not know; one per partition does not change with where the client lives.
- **The tests pass a client over a fake transport or a local server.** A parameter keeps that:
  `triage` still takes a function that gives the client, and the example's function names the
  `object`. A test that needs one client per JVM keeps it in an `object` of its own.
- **A `lazy val` that fails is tried again.** Without a key, each task that asks for the client
  fails with the same `ConfigError` message, and Spark fails the job, as before.
- **Alternatives considered.** A cache keyed by the function: each task deserializes a new copy
  of the function, and the lambda's class is the same for two local servers. A `Broadcast` of the
  config: the `ApiKey` would travel, against ADR-0041. Closing the client at the end of each
  partition: `JevClient` does not own a way to close, and on JDK 17 an `HttpClient` cannot be
  closed at all.

## Decision

- `SparkTriage.triage(tickets, partitions, perSecond, client: () => JevClient)` calls `client()`
  once per partition, as before; the parameter is renamed, because it gives a client, and no
  longer promises a new one.
- `SparkTriage.client` is a `lazy val` in the `object`, built from `JevConfig.fromEnv` on the
  executor, and `main` passes `() => client`. Each executor JVM builds one client.
- The pacer stays one per partition, at `perSecond / partitions`.
- A test checks that 12 partitions over one `object`'s client start one `HttpClient`, by the
  JDK's selector threads. The tests over a fake transport keep a client per partition: a fake
  has no threads.
- `docs/guide/spark.md` says why the client lives in an `object`, with the numbers above.

## Consequences

- An executor holds one `HttpClient`, with its connections and a few threads, for its whole
  life, whatever the number of partitions and actions.
- The client of an executor is built from the environment of the first task that needs it: a
  change of the key in the environment is not seen until the executor restarts.
- In local mode the driver is the executor, so the client is shared by every job of the program.
- Do not move the pacer into the `object` "to share it too": its rate would then depend on how
  many executors Spark starts, which the code cannot see.
- Do not go back to a client per partition "to keep the example short": the measured cost grows
  with the partitions and the actions.
