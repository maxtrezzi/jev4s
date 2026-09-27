#!/usr/bin/env python3
"""Fail when the Scaladoc of a Scala 3 project has a warning.

Scala 3's Scaladoc does not take `-Werror` (it prints "Skipping unused scalacOptions: -Werror"),
so a link it cannot resolve is a warning, and the documentation is still built. The 2.13
Scaladoc takes `-Werror`, and the same link fails its build: the 2.13 projects need no check.

Scaladoc 3.9.0 always prints one warning of its own, "Option -classpath was updated": its TASTy
inspector appends the classpath of the Scaladoc tool to the one it was given, and the compiler
warns that the option changed. No option of the build avoids it, so it is the one warning this
script accepts.

It deletes the documentation first, because sbt does not build it again, and prints no warning,
when nothing changed. Run from the root of the repository:

    python3 build/check-scaladoc.py

Exit 0 when the only warning is Scaladoc's own, 1 otherwise.
"""
import glob
import re
import shutil
import subprocess
import sys

PROJECTS = {"scala3": "scala3", "scala3Testkit": "testkit/scala3"}
SCALADOCS_OWN = "Option -classpath was updated"
SUMMARY = re.compile(r"\w+ warnings? found")


def main():
    for directory in PROJECTS.values():
        for api in glob.glob(f"{directory}/target/scala-*/api"):
            shutil.rmtree(api)
    tasks = [f"{project}/doc" for project in PROJECTS]
    run = subprocess.run(["sbt", "-batch", "-no-colors", *tasks], capture_output=True, text=True)
    print(run.stdout, end="")
    print(run.stderr, end="", file=sys.stderr)
    if run.returncode != 0:
        sys.exit("the Scaladoc build failed")
    warnings = [line.removeprefix("[warn]").strip() for line in run.stdout.splitlines() if line.startswith("[warn]")]
    others = [w for w in warnings if w != SCALADOCS_OWN and not SUMMARY.fullmatch(w)]
    if others:
        sys.exit("Scaladoc warned:\n" + "\n".join(others))
    print(f"Scaladoc of {', '.join(PROJECTS)}: no warning but its own \"{SCALADOCS_OWN}\"")


if __name__ == "__main__":
    main()
