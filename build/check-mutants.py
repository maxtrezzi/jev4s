#!/usr/bin/env python3
"""Fail when a module's latest Stryker4s report has a mutant that no test detected.

ADR-0008 allows no unexplained survivor. Stryker4s cannot enforce that by itself: it requires
`low > break`, so `break` tops out at 99, and a module with more than 100 mutants passes with
one survivor. This script reads the JSON report instead and fails on any `Survived` or
`NoCoverage` mutant. An equivalent mutant is excluded in the source with `@SuppressWarnings`,
so it never reaches the report, and is recorded in docs/testing/equivalent-mutants.md.

Run after `sbt "project <module>" stryker`:

    python3 build/check-mutants.py scala3
    python3 build/check-mutants.py scala213

Exit 0 when every mutant was detected, 1 otherwise.
"""
import collections
import glob
import json
import os
import sys

UNDETECTED = {"Survived", "NoCoverage"}


def latest_report(module):
    reports = glob.glob(os.path.join(module, "target", "stryker4s-report", "*", "report.json"))
    if not reports:
        sys.exit(f"no Stryker4s JSON report under {module}/target/stryker4s-report: run stryker first")
    return max(reports, key=os.path.getmtime)


def main(module):
    path = latest_report(module)
    with open(path, encoding="utf-8") as fh:
        report = json.load(fh)

    counts = collections.Counter()
    undetected = []
    for name, source in sorted(report["files"].items()):
        for mutant in source["mutants"]:
            counts[mutant["status"]] += 1
            if mutant["status"] in UNDETECTED:
                line = mutant["location"]["start"]["line"]
                undetected.append(
                    f"{name}:{line} [{mutant['status']}] {mutant['mutatorName']}: {mutant.get('replacement', '?')}"
                )

    summary = ", ".join(f"{status} {n}" for status, n in sorted(counts.items()))
    print(f"{module}: {sum(counts.values())} mutants ({summary}) in {path}")
    if undetected:
        print(f"\n{len(undetected)} mutant(s) no test detected:\n")
        for u in undetected:
            print(f"  - {u}")
        print("\nKill each one with a test, or exclude it as equivalent (ADR-0008).")
        return 1
    print("every mutant was detected")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: check-mutants.py <module directory>")
    sys.exit(main(sys.argv[1]))
