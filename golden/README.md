# Golden files

Real requests and responses of the Jev API, read by the tests of **both** modules
([ADR-0009](../docs/adr/0009-two-native-modules-no-shared-code.md)). They are what keeps the
Scala 3 module and the Scala 2.13 module in step, since the two share no code.

Every file here is written by [`build/capture-golden.py`](../build/capture-golden.py), never by
hand: a golden test on invented JSON proves little. Never store an API key in one; the script
checks every file it writes for the key.

## Layout

One directory per case:

| File | Content |
|---|---|
| `request.json` | The body sent to `POST /v1/systemone`, byte for byte |
| `response.json` | The body received, byte for byte, unformatted |
| `meta.json` | HTTP status, the response headers, the model asked for, and when; `noul` also has every header and the response time |

| Case | What it shows |
|---|---|
| `noul` | A Noul with no criteria |
| `noul-criteria` | A Noul with both criteria |
| `choice` | A Choice with one option that has no description (`null`) |
| `score` | A Score with three levels: `legend` and per-level `probabilities` |
| `mixed` | All three types in one request, with a JSON object as state |
| `structured` | JSON instructions, Noul criteria, Choice options and Score levels; `legend` repeats the JSON levels |
| `error-400` | A model that does not exist: `detail` is an object, as for a 401 |
| `error-422` | A Choice without its criteria: `detail` is a list |
| `error-401` | A made-up API key: `detail` is an object |

Recorded on 2026-09-22 with `jev-1.13.0`; `noul` again on 2026-09-24, with every header and the
response time. To record again, load `.env` and run
`python3 build/capture-golden.py --run`; add `--force` to replace a case, and `--only <case>` to
record one case alone. A new recording
changes the numbers in the answers, so the tests must read them from the files, never repeat
them.
