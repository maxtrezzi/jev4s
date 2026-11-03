#!/usr/bin/env python3
"""Records real requests and responses of the Jev API into golden/ (M2).

Golden tests on invented JSON prove little, so the files in golden/ come from real calls, made
by this script and never written by hand. Every case is a fixed request: the script sends it
byte for byte as stored, and stores the response body byte for byte as received.

Each case becomes a directory:

  golden/<case>/request.json    the body sent
  golden/<case>/response.json   the body received, unchanged
  golden/<case>/meta.json       HTTP status, every response header, response time, model, date

Real calls cost money, so:

  - without --run the script only prints the plan, and makes no call;
  - it stops before making more than JEV_MAX_CALLS calls;
  - it refuses to overwrite a case that is already recorded, unless --force;
  - --only <case> limits the run to that one case.

The API key is read from TYPESAFE_API_KEY and is never written: the script checks every file
it writes for it. The 401 case sends a made-up key instead.

Run:  set -a; . ./.env; set +a
      python3 build/capture-golden.py            # print the plan
      python3 build/capture-golden.py --run      # make the calls
      python3 build/capture-golden.py --run --force --only noul   # record one case again

Source for the request and response format: https://docs.typesafe.ai/api.md, read 2026-09-22;
images and box questions read 2026-11-01.
"""
import base64
import datetime
import hashlib
import json
import os
import struct
import sys
import time
import urllib.error
import urllib.request
import zlib

GOLDEN = "golden"
TIMEOUT_SECONDS = 30
FAKE_KEY = "jev4s-golden-invalid-key"

TICKET = "Help! My payouts have been failing for 3 days and I have a launch tomorrow."

# Images need a model that reads them and the danger-zone header (ADR-0056). Jev 1.14 is the first
# such model; JEV_MODEL stays the model of the other cases.
IMAGE_MODEL = "jev-1.14.0"
IMAGE_HEADERS = {"TypeSafe-Danger-Zone-Images": "true"}

# The image service answered 503 about one request in five on 2026-11-01, and the same request
# passed when sent again: a 503 is sent again, at most this many times in all, and never recorded.
ATTEMPTS = 3


def png(width, height, rect):
    """A white PNG with one red rectangle, rect = (top, bottom, left, right) as fractions: the same
    bytes on every run, so the image and its ref never change."""
    top, bottom, left, right = rect
    rows = b"".join(
        b"\x00" + b"".join(
            b"\xff\x00\x00" if top * height <= y < bottom * height and left * width <= x < right * width
            else b"\xff\xff\xff"
            for x in range(width))
        for y in range(height))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(rows, 9))
            + chunk(b"IEND", b""))


def image_ref(data):
    """The ref jev4s gives an image: "image-" and the first 16 bytes of its SHA-256, in hex."""
    return "image-" + hashlib.sha256(data).hexdigest()[:32]


# 448 x 448: the API refuses a side under 280 pixels (measured 2026-11-01).
RECTANGLE = png(448, 448, (0.125, 0.375, 0.25, 0.75))


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
        ("box", "a Box over a 448x448 PNG with a red rectangle at top 0.125, bottom 0.375, left 0.25, "
                "right 0.75", False, {
            "state": image_ref(RECTANGLE),
            "model": IMAGE_MODEL,
            "questions": {"rectangle": {"type": "box", "instructions": "The red rectangle"}},
            "assets": {image_ref(RECTANGLE): {
                "type": "image",
                "format": "png",
                "data_base64": base64.b64encode(RECTANGLE).decode("ascii"),
            }},
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


def call(url, key, payload, extra_headers):
    request = urllib.request.Request(url, data=payload, method="POST", headers={
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        **extra_headers,
    })
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.headers, error.read()


def all_headers(headers):
    """Every response header, by lower-case name; a repeated header keeps its values joined by ", ".
    A cookie's value is left out: since the move behind Cloudflare (2026-11-01) a response sets one,
    and golden/ is public."""
    kept = {}
    for name, value in headers.items():
        name = name.lower()
        if name == "set-cookie":
            value = value.split("=", 1)[0] + "=(left out)"
        kept[name] = f"{kept[name]}, {value}" if name in kept else value
    return dict(sorted(kept.items()))


def write_case(directory, files, key):
    for name, content in files.items():
        if key.encode("utf-8") in content:
            sys.exit(f"refusing to write {directory}/{name}: it contains the API key")
    os.makedirs(directory, exist_ok=True)
    for name, content in files.items():
        with open(os.path.join(directory, name), "wb") as fh:
            fh.write(content)


def main():
    args = sys.argv[1:]
    only = None
    if "--only" in args:
        at = args.index("--only")
        if at + 1 >= len(args):
            sys.exit("--only needs the name of a case")
        only = args[at + 1]
        del args[at:at + 2]
    run = "--run" in args
    force = "--force" in args
    unknown = [a for a in args if a not in ("--run", "--force")]
    if unknown:
        sys.exit(f"unknown arguments: {unknown}")

    model = require_env("JEV_MODEL")
    base_url = require_env("TYPESAFE_BASE_URL").rstrip("/")
    max_calls = int(require_env("JEV_MAX_CALLS"))
    url = f"{base_url}/v1/systemone"
    plan = cases(model)
    if only is not None:
        plan = [c for c in plan if c[0] == only]
        if not plan:
            sys.exit(f"no case named {only}: see the list without --only")

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
        extra_headers = IMAGE_HEADERS if "assets" in body else {}
        for attempt in range(1, ATTEMPTS + 1):
            started = time.monotonic()
            status, headers, received = call(url, FAKE_KEY if fake_key else key, payload, extra_headers)
            elapsed_ms = round((time.monotonic() - started) * 1000)
            if status != 503:
                break
            print(f"  {name:14} HTTP 503 on attempt {attempt}: {received[:120]!r}")
            time.sleep(2)
        if status == 503:
            sys.exit(f"{name}: HTTP 503 on every attempt: nothing was recorded for it")
        meta = {
            "case": name,
            "why": why,
            "status": status,
            "headers": all_headers(headers),
            "elapsed_ms": elapsed_ms,
            "requested_model": body["model"] if "assets" in body else model,
            "recorded_at": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
            "endpoint": "POST /v1/systemone",
        }
        if extra_headers:
            meta["request_headers"] = sorted(extra_headers)
        write_case(os.path.join(GOLDEN, name), {
            "request.json": payload,
            "response.json": received,
            "meta.json": (json.dumps(meta, indent=2) + "\n").encode("utf-8"),
        }, key)
        print(f"  {name:14} HTTP {status}, {len(received)} bytes, {elapsed_ms} ms")


if __name__ == "__main__":
    main()
