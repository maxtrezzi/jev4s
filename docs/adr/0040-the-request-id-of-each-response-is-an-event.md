# ADR-0040: The request id of each response is an event

- **Status:** Accepted
- **Date:** 2026-09-25
- **Supersedes:** —
- **Amends:** [ADR-0025](0025-client-events-through-onevent.md)

## Context

Every response of Jev carries an `x-typesafe-request-id` header, such as `req_01a0…`: recorded
on a real reply on 2026-09-24 (T15), and on a 401 on 2026-09-25 (`golden/error-401`), so an
error has one too. It is what a caller gives TypeSafe's support about one call.
TypeSafe's official SDKs keep it on their errors, as `request_id` in Python and `requestId` in
JavaScript: "the `x-typesafe-request-id` response header, or `None` if absent" (SDK references,
read 2026-09-25). A gateway may not send it (T13).

jev4s drops it. `Transport.send(body): Either[JevError, String]` returns only the body of a
successful response, and it is a single abstract method on purpose: a test builds a transport
from a lambda, `_ => Right(reply)`. `JevError` cases carry no header, and `Reply` holds what the
body says. Reply metadata and retries reach the caller as `JevEvent`s, never next to the
answers ([ADR-0025](0025-client-events-through-onevent.md)).

A call can make several HTTP attempts, one per retry, and each response has its own id. The id
matters most when a call fails.

## Forces

- **On the error, as the SDKs do.** Each `JevError` that comes from a response
  (`Unauthorized`, `Rejected`, `RateLimited`, `Overloaded`, `ServerError`, `Unexpected`) would
  gain `requestId: Option[String]`. The id is where the failure is handled, but `Unauthorized`
  and `Overloaded` stop being plain values, every pattern and every test that builds an error
  changes, and two errors that differ only by id are no longer equal. It gives only the id of the
  last attempt.
- **On `Reply`, through a second method on `Transport`** (`exchange`, returning the body and the
  id, with a default over `send`). The lambda still works and a success has its id, but a
  failure, the case that matters most, still has none.
- **A new event, `JevEvent.Responded`, sent by `JdkTransport` once per HTTP response**, success
  or failure, with the status and the id. Nothing that exists changes type: not `Transport`, not
  `JevError`, not `Reply`. It gives the id of every attempt, not only the last, and fits
  ADR-0025: what a response says besides the answers is an event. The cost is that a caller who
  wants the id next to its error must connect the two: events run on the calling thread before
  `ask` returns, in order, so the last `Responded` before a `Left` is that error's response.
- **A new case of `JevEvent` breaks every exhaustive match**, as a warning; ADR-0025 accepted
  that for new events, and nothing is published.
- **A transport of the caller's own sends no `Responded`**, as it sends no `Retrying`.

## Decision

- `JevEvent` gains `Responded(status: Int, requestId: Option[String])`, in both modules.
  `JdkTransport` sends it for every HTTP response it receives, before it decides to retry or to
  return; not for a request that got no response (`Network`).
- `requestId` is the `x-typesafe-request-id` header, or `None` when the response has none.
- `Transport`, `JevError` and `Reply` do not change.
- The guides' chapter on logs and metrics shows the event, and how to keep the id of the last
  response with the error.

## Consequences

- A caller can log the id of every response, retries included, and give it to TypeSafe's
  support.
- Every exhaustive match on `JevEvent` must handle `Responded`; the examples in `live/` do.
- A call that succeeds on its first attempt sends `Responded` then `Replied`; one that retries
  sends `Responded`, `Retrying`, and so on.
- The id of a failure is not on the `JevError`. If callers need it there, a later ADR can add it;
  do not put it in the error message string, which callers may compare or show.
