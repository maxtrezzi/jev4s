# Security

## Reporting a problem

**Do not open a public issue for a security problem.** Report it privately, with the
**Report a vulnerability** button on the **Security** tab of this repository. Only the
maintainer can read the report.

Say what the problem is, which version of jev4s and which Scala version you use, and how to
reproduce it. **Never send your API key**, in a report or anywhere else: nobody needs it to
understand a problem.

## Supported versions

jev4s uses `0.x` versions while the Jev API is in early access. Fixes go into the latest
release only.

## What counts

jev4s handles your TypeSafe API key and sends your data to Jev. Report anything that breaks
what the documentation promises about them, for example:

- the API key in a log, a `toString`, an exception or an error message
  ([ADR-0027](docs/adr/0027-the-api-key-is-a-type-and-travels-over-tls.md));
- a request sent over plain `http` to a host other than `localhost` by `JevConfig.fromEnv`;
- a call that throws an exception instead of returning a `JevError`
  ([ADR-0002](docs/adr/0002-direct-style-no-effect-system.md)).

jev4s is not made by TypeSafe AI. A problem in the Jev API itself, or in your TypeSafe account,
goes to TypeSafe AI.
