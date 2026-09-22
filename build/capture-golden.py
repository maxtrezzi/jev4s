#!/usr/bin/env python3
"""Records real requests and responses of the Jev API into golden/ (M2).

Golden tests on invented JSON prove little, so the files in golden/ come from real calls, made
by this script and never written by hand. Every case is a fixed request: the script sends it
byte for byte as stored, and stores the response body byte for byte as received.

Each case becomes a directory:

  golden/<case>/request.json    the body sent
  golden/<case>/response.json   the body received, unchanged
  golden/<case>/meta.json       HTTP status, a few response headers, model, date

Real calls cost money, so:

  - without --run the script only prints the plan, and makes no call;
  - it stops before making more than JEV_MAX_CALLS calls;
  - it refuses to overwrite a case that is already recorded, unless --force.

The API key is read from TYPESAFE_API_KEY and is never written: the script checks every file
it writes for it. The 401 case sends a made-up key instead.

Run:  set -a; . ./.env; set +a
      python3 build/capture-golden.py            # print the plan
      python3 build/capture-golden.py --run      # make the calls

Source for the request and response format: https://docs.typesafe.ai/api.md, read 2026-09-22.
"""
import datetime
import json
import os
import sys
import urllib.error
import urllib.request

GOLDEN = "golden"
TIMEOUT_SECONDS = 30
KEPT_HEADERS = ("content-type", "retry-after")
FAKE_KEY = "jev4s-golden-invalid-key"

TICKET = "Help! My payouts have been failing for 3 days and I have a launch tomorrow."


def cases(model):
    """The recorded cases, in the order they run. Each: name, why, body, whether to use a fake key."""
    return [
        ("noul", "a Noul with no criteria", False, {
            "state": TICKET,
            "model": model,
            "questions": {"is_urgent": {"type": "noul", "instructions": "Does this convey urgency?"}},
        }),
        ("noul-criteria", "a Noul with both criteria", False, {
            "state": TICKET,
            "model": model,
            "questions": {"is_urgent": {
                "type": "noul",
                "instructions": "Does this convey urgency?",
                "criteria": {"true": "Explicitly time-sensitive", "false": "No urgency expressed"},
            }},
        }),
        ("choice", "a Choice, one option with no description (null)", False, {
            "state": TICKET,
            "model": model,
            "questions": {"department": {
                "type": "choice",
                "instructions": "Which team should handle this?",
                "criteria": {
                    "billing": "Payments, invoicing, refunds",
                    "technical": "Bugs, outages, integrations",
                    "sales": None,
                },
            }},
        }),
        ("score", "a Score with three levels", False, {
            "state": TICKET,
            "model": model,
            "questions": {"frustration": {
                "type": "score",
                "instructions": "How frustrated is the customer?",
                "criteria": ["Calm", "Frustrated", "Very angry"],
            }},
        }),
        ("mixed", "all three types in one request, structured state, names not in alphabetical order", False, {
            "state": {"channel": "email", "customer_tier": "pro", "message": TICKET},
            "model": model,
            "questions": {
                "urgent": {"type": "noul", "instructions": "Does `message` convey urgency?"},
                "department": {
                    "type": "choice",
                    "instructions": "Which team should handle `message`?",
                    "criteria": {"billing": "Payments, invoicing, refunds", "technical": None},
                },
                "frustration": {
                    "type": "score",
                    "instructions": "How frustrated is the customer?",
                    "criteria": ["Calm", "Frustrated", "Very angry"],
                },
            },
        }),
        ("structured", "JSON in every place that takes it: instructions, Noul criteria, Choice options, "
                       "Score levels (one left as text)", False, {
            "state": {
                "sender": {"display_name": "Beaver Dam Builders Ltd.", "email": "donotreply@payroll.example"},
                "message": "Your Q3 bonus is ready. Reply with your login password so we can release the funds.",
            },
            "model": model,
            "questions": {
                "requests_credentials": {
                    "type": "noul",
                    "instructions": {
                        "question": "Does the `message` ask the recipient to disclose a sensitive credential?",
                        "focus": "A request to send the credential itself, not to change or reset it.",
                    },
                    "criteria": {
                        "true": {"what": "Asks for a password, PIN or one-time code",
                                 "examples": ["Reply with your password"]},
                        "false": {"what": "No credential is requested",
                                  "examples": ["Reset your password from the settings page"]},
                    },
                },
                "department": {
                    "type": "choice",
                    "instructions": {
                        "question": "Which team should handle this message?",
                        "compare": ["sender.display_name", "sender.email"],
                    },
                    "criteria": {
                        "security": {"what": "Phishing, fraud or account takeover",
                                     "examples": ["Someone asked for my password"]},
                        "payroll": {"what": "Salary, bonus or tax questions", "not_for": "Security threats"},
                        "other": None,
                    },
                },
                "risk": {
                    "type": "score",
                    "instructions": {"question": "How dangerous is this message to the recipient?"},
                    "criteria": [
                        "Harmless",
                        {"summary": "Suspicious", "signals": ["Unusual sender", "Urgent tone"]},
                        {"summary": "Dangerous", "signals": ["Asks for credentials or money"]},
                    ],
                },
            },
        }),
        ("error-422", "a Choice without its required criteria", False, {
            "state": TICKET,
            "model": model,
            "questions": {"department": {"type": "choice", "instructions": "Which team should handle this?"}},
        }),
        ("error-400", "a valid request for a model that does not exist", False, {
            "state": TICKET,
            "model": "jev-0.0.0",
            "questions": {"is_urgent": {"type": "noul", "instructions": "Does this convey urgency?"}},
        }),
        ("error-401", "a valid request with a made-up API key", True, {
            "state": TICKET,
            "model": model,
            "questions": {"is_urgent": {"type": "noul", "instructions": "Does this convey urgency?"}},
        }),
    ]


def require_env(name):
    value = os.environ.get(name, "")
    if not value:
        sys.exit(f"{name} is not set: load .env first (set -a; . ./.env; set +a)")
    return value


def encode(body):
    """The exact bytes sent, and stored as request.json."""
    return (json.dumps(body, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def call(url, key, payload):
    request = urllib.request.Request(url, data=payload, method="POST", headers={
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
    })
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.headers, error.read()


def write_case(directory, files, key):
    for name, content in files.items():
        if key.encode("utf-8") in content:
            sys.exit(f"refusing to write {directory}/{name}: it contains the API key")
    os.makedirs(directory, exist_ok=True)
    for name, content in files.items():
        with open(os.path.join(directory, name), "wb") as fh:
            fh.write(content)


def main():
    run = "--run" in sys.argv[1:]
    force = "--force" in sys.argv[1:]
    unknown = [a for a in sys.argv[1:] if a not in ("--run", "--force")]
    if unknown:
        sys.exit(f"unknown arguments: {unknown}")

    model = require_env("JEV_MODEL")
    base_url = require_env("TYPESAFE_BASE_URL").rstrip("/")
    max_calls = int(require_env("JEV_MAX_CALLS"))
    url = f"{base_url}/v1/systemone"
    plan = cases(model)

    print(f"{len(plan)} calls to POST {url}, model {model}, limit {max_calls}")
    for name, why, fake_key, body in plan:
        exists = os.path.exists(os.path.join(GOLDEN, name))
        note = " (already recorded: skipped unless --force)" if exists and not force else ""
        print(f"  {name:14} {why}{' [fake key]' if fake_key else ''}{note}")
    if not run:
        print("plan only: nothing was sent. Add --run to make the calls.")
        return

    key = require_env("TYPESAFE_API_KEY")
    todo = [c for c in plan if force or not os.path.exists(os.path.join(GOLDEN, c[0]))]
    if len(todo) > max_calls:
        sys.exit(f"{len(todo)} calls needed, JEV_MAX_CALLS is {max_calls}: nothing was sent")

    for name, why, fake_key, body in todo:
        payload = encode(body)
        status, headers, received = call(url, FAKE_KEY if fake_key else key, payload)
        meta = {
            "case": name,
            "why": why,
            "status": status,
            "headers": {h: headers[h] for h in KEPT_HEADERS if headers.get(h) is not None},
            "requested_model": model,
            "recorded_at": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
            "endpoint": "POST /v1/systemone",
        }
        write_case(os.path.join(GOLDEN, name), {
            "request.json": payload,
            "response.json": received,
            "meta.json": (json.dumps(meta, indent=2) + "\n").encode("utf-8"),
        }, key)
        print(f"  {name:14} HTTP {status}, {len(received)} bytes")


if __name__ == "__main__":
    main()
