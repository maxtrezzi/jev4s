#!/usr/bin/env python3
"""Consistency checks for the tracked documentation.

Each check guards against a mistake that is easy to make and hard to see:

  1. An ADR's own Status line and its row in the index drift apart. The WHOLE string is
     compared, not only the text before an em-dash, because an amendment lives after it.
  2. An ADR file exists with no index row, or a row points at no file.
  3. A link or anchor resolves on the author's machine but not in a fresh clone, because the
     target is git-ignored. Everything is resolved against `git ls-files`: what a stranger
     actually gets.
  4. A status line records an amendment in prose ("per ADR-NNNN") instead of the documented
     shape ("amended by ADR-NNNN"), so a reader scanning for amendments misses it.
  5. An amendment or a replacement points one way only. If A's status says it was amended by B,
     B's `Amends` header has to name A, and the reverse.
  6. A code block quoted from an example no longer matches the example. The README and the
     guides quote the examples in `live/`, which CI compiles: a block that follows the line
     `<!-- snippet: live/scala3/src/main/scala/guide/Client.scala#first -->` must be the lines
     between `// snippet: first` and `// end: first` in that file, without their common indent.
  7. A documented compile error has no test. A block that follows the line
     `<!-- compile-errors: scala3/src/test/scala/ReadmeErrorsSuite.scala -->` lists lines that must
     not compile, each ending with `// error: <message>` (ADR-0044). The suite checks each message
     against the compiler; this check makes sure that each entry has its case in the suite: a
     `documented("<code>", compileErrors("<code>"))` call, with the same code in both literals.

Run: python3 build/check-docs.py                    (exit 0 clean, 1 with findings)
     python3 build/check-docs.py --write-snippets   (first copy every quoted example into its block)
"""
import os
import re
import subprocess
import sys

ADR_DIR = "docs/adr"
TEMPLATE = "0000"

# The four shapes documented in docs/adr/README.md. The amending verb may be "amended",
# "widened" or "narrowed", where the narrower word is more precise.
STATUS_SHAPE = re.compile(
    r"^(Proposed"
    r"|Accepted"
    r"|Accepted — .+ (?:amended|widened|narrowed) by ADR-\d{4}"
    r"|Superseded by ADR-\d{4})$"
)


def tracked_files():
    """What a fresh clone contains — never the working tree."""
    out = subprocess.run(["git", "ls-files"], capture_output=True, text=True, check=True)
    return set(out.stdout.split())


def strip_links(text):
    return re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)


def normalise(text):
    return re.sub(r"\s+", " ", strip_links(text)).strip().rstrip(".")


def github_anchor(heading):
    """GitHub's slug: drop punctuation, then EVERY space becomes its own hyphen."""
    t = re.sub(r"`([^`]*)`", r"\1", strip_links(heading))
    t = re.sub(r"[*_]", "", t).strip().lower()
    t = "".join(c for c in t if c.isalnum() or c in " -_")
    return t.replace(" ", "-")


def adr_header(path, field):
    with open(path, encoding="utf-8") as fh:
        head = fh.read(1500)
    m = re.search(rf"^- \*\*{field}:\*\* (.+)$", head, re.M)
    return normalise(m.group(1)) if m else None


def adr_status(path):
    with open(path, encoding="utf-8") as fh:
        head = fh.read(1200)
    m = re.search(r"^- \*\*Status:\*\* (.+)$", head, re.M)
    return normalise(m.group(1)) if m else None


def index_rows():
    rows = {}
    with open(f"{ADR_DIR}/README.md", encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"\|\s*\[(\d{4})\]\([^)]*\)\s*\|([^|]*)\|([^|]*)\|", line)
            if m:
                rows[m.group(1)] = normalise(m.group(3))
    return rows


def check_adrs(problems):
    files = {}
    for name in sorted(os.listdir(ADR_DIR)):
        m = re.match(r"(\d{4})-.*\.md$", name)
        if m and m.group(1) != TEMPLATE:
            files[m.group(1)] = f"{ADR_DIR}/{name}"
    rows = index_rows()

    for num in sorted(set(files) - set(rows)):
        problems.append(f"ADR-{num} has a file but no row in {ADR_DIR}/README.md")
    for num in sorted(set(rows) - set(files)):
        problems.append(f"ADR-{num} has an index row but no file")

    for num in sorted(set(files) & set(rows)):
        status, row = adr_status(files[num]), rows[num]
        if status is None:
            problems.append(f"ADR-{num} has no '- **Status:**' line")
        elif status != row:
            problems.append(
                f"ADR-{num} status drift\n"
                f"      file  : {status}\n"
                f"      index : {row}\n"
                f"      (the ADR's own header is authoritative — fix the index to match, "
                f"or add the missing pointer to the header)"
            )

    for num in sorted(set(files) & set(rows)):
        status = adr_status(files[num])
        if status and not STATUS_SHAPE.match(status):
            problems.append(
                f"ADR-{num} status does not match a documented shape: {status!r}\n"
                f"      (see the Status values table in {ADR_DIR}/README.md)"
            )

    # An amendment has two ends. Both must name the other, or the trail is one-way.
    def cited(text):
        return set(re.findall(r"ADR-(\d{4})", text or ""))

    for num, path in sorted(files.items()):
        status = adr_status(path) or ""
        superseded = "uperseded" in status
        for target in cited(status):
            if target not in files:
                problems.append(f"ADR-{num} status names ADR-{target}, which has no file")
                continue
            field = "Supersedes" if superseded else "Amends"
            if num not in cited(adr_header(files[target], field)):
                problems.append(
                    f"ADR-{num} status says it was {'superseded' if superseded else 'amended'} "
                    f"by ADR-{target}, but ADR-{target}'s {field} header does not name ADR-{num}"
                )
        for field, verb in (("Amends", "amends"), ("Supersedes", "supersedes")):
            for target in cited(adr_header(path, field)):
                if target not in files:
                    problems.append(f"ADR-{num} {field} ADR-{target}, which has no file")
                    continue
                if num not in cited(adr_status(files[target])):
                    problems.append(
                        f"ADR-{num} {verb} ADR-{target}, but ADR-{target}'s status "
                        f"does not record it"
                    )

    numbers = sorted(int(n) for n in files)
    if numbers:
        gaps = [n for n in range(numbers[0], numbers[-1] + 1) if n not in numbers]
        if gaps:
            problems.append(f"ADR numbering gaps: {gaps}")
    return len(files)


def check_links(problems, tracked):
    docs = sorted(f for f in tracked if f.endswith(".md"))
    anchors = {}
    for path in docs:
        with open(path, encoding="utf-8") as fh:
            anchors[path] = {
                github_anchor(m.group(2))
                for m in (re.match(r"^(#{1,6})\s+(.*?)\s*$", line) for line in fh)
                if m
            }
    dirs = {os.path.dirname(f) for f in tracked} | {""}

    for path in docs:
        with open(path, encoding="utf-8") as fh:
            body = fh.read()
        # a link inside a code span is literal text, not a link
        spans = [(m.start(), m.end()) for m in re.finditer(r"`[^`]*`", body)]
        for m in re.finditer(r"\[[^\]]*\]\(([^)\s]+)\)", body):
            if any(s <= m.start() < e for s, e in spans):
                continue
            link = m.group(1)
            if link.startswith(("http://", "https://", "mailto:")):
                continue
            rel, _, frag = link.partition("#")
            target = os.path.normpath(os.path.join(os.path.dirname(path), rel)) if rel else path
            if rel and target not in tracked and target not in dirs:
                problems.append(f"{path}: link to untracked target -> {link}")
                continue
            if frag and target.endswith(".md") and frag not in anchors.get(target, set()):
                problems.append(f"{path}: no such anchor -> {link}")
    return len(docs)


SNIPPET_REF = re.compile(r"^<!-- snippet: (\S+)#(\S+) -->$")


def snippet(path, name):
    """The lines between `// snippet: name` and `// end: name`, without other markers and without
    their common indent; None when the file or the region does not exist."""
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as fh:
        lines = fh.read().split("\n")
    marks = [i for i, line in enumerate(lines) if line.strip() in (f"// snippet: {name}", f"// end: {name}")]
    if len(marks) != 2:
        return None
    body = [
        line
        for line in lines[marks[0] + 1 : marks[1]]
        if not re.match(r"^\s*// (snippet|end): \S+$", line)
    ]
    indent = min((len(line) - len(line.lstrip()) for line in body if line.strip()), default=0)
    return [line[indent:] for line in body]


def check_snippets(problems, tracked, write):
    """Each quoted block against its example; with `write`, the block is replaced instead."""
    count = 0
    for path in sorted(f for f in tracked if f.endswith(".md")):
        with open(path, encoding="utf-8") as fh:
            lines = fh.read().split("\n")
        changed = False
        i = 0
        while i < len(lines):
            m = SNIPPET_REF.match(lines[i].strip())
            i += 1
            if not m:
                continue
            count += 1
            source, name = m.groups()
            if source not in tracked:
                problems.append(f"{path}: quotes {source}, which is not tracked")
                continue
            expected = snippet(source, name)
            if expected is None:
                problems.append(f"{path}: no snippet '{name}' in {source}")
                continue
            if i >= len(lines) or not lines[i].startswith("```"):
                problems.append(f"{path}: the line after the snippet reference to {source}#{name} is not a code fence")
                continue
            end = next((j for j in range(i + 1, len(lines)) if lines[j].startswith("```")), None)
            if end is None:
                problems.append(f"{path}: the code block of {source}#{name} is not closed")
                break
            if lines[i + 1 : end] != expected:
                if write:
                    lines[i + 1 : end] = expected
                    end = i + 1 + len(expected)
                    changed = True
                else:
                    problems.append(
                        f"{path}: the block quoting {source}#{name} differs from the example\n"
                        f"      (run python3 build/check-docs.py --write-snippets)"
                    )
            i = end + 1
        if changed:
            with open(path, "w", encoding="utf-8") as fh:
                fh.write("\n".join(lines))
    return count


ERRORS_REF = re.compile(r"^<!-- compile-errors: (\S+) -->$")
ERROR_ENTRY = re.compile(r"^(.*?)\s*// error: (.*)$")
# `documented("<code>", compileErrors("<code>"))`, each literal "..." or """...""", with the
# trailing comma that scalafmt adds to a call on several lines: one case of a suite. The two
# literals must be the same code.
LITERAL = r'(?:"""(.*?)"""|"((?:[^"\\\n]|\\.)*)")'
DOCUMENTED = re.compile(rf"documented\(\s*{LITERAL}\s*,\s*compileErrors\(\s*{LITERAL}\s*\)\s*,?\s*\)", re.S)


def error_entries(block):
    """The code of each entry of a block: its first line without the `// error:` comment, and the
    indented lines after it that have no marker."""
    entries = []
    for line in block:
        m = ERROR_ENTRY.match(line)
        if m:
            entries.append(m.group(1))
        elif entries and line.startswith(" ") and line.strip():
            entries[-1] += "\n" + line.rstrip()
    return entries


def documented_codes(path, problems):
    """The code of each case of a suite, when its two literals agree."""
    with open(path, encoding="utf-8") as fh:
        text = fh.read()
    unescape = lambda s: re.sub(r"\\(.)", lambda m: {"n": "\n", "t": "\t"}.get(m.group(1), m.group(1)), s)
    value = lambda raw, escaped: raw if raw is not None else unescape(escaped)
    codes = set()
    for m in DOCUMENTED.finditer(text):
        code, compiled = value(m.group(1), m.group(2)), value(m.group(3), m.group(4))
        if code == compiled:
            codes.add(code)
        else:
            problems.append(f"{path}: documented({code!r}, ...) compiles other code: {compiled!r}")
    return codes


def check_compile_errors(problems, tracked):
    """Each documented compile error against the cases of its suite."""
    count = 0
    for path in sorted(f for f in tracked if f.endswith(".md")):
        with open(path, encoding="utf-8") as fh:
            lines = fh.read().split("\n")
        for i, line in enumerate(lines):
            m = ERRORS_REF.match(line.strip())
            if not m:
                continue
            suite = m.group(1)
            if suite not in tracked:
                problems.append(f"{path}: its compile errors name {suite}, which is not tracked")
                continue
            if i + 1 >= len(lines) or not lines[i + 1].startswith("```"):
                problems.append(f"{path}: the line after the compile errors of {suite} is not a code fence")
                continue
            block = []
            for body in lines[i + 2 :]:
                if body.startswith("```"):
                    break
                block.append(body)
            entries = error_entries(block)
            if not entries:
                problems.append(f"{path}: the block of {suite} has no line with `// error:`")
            codes = documented_codes(suite, problems)
            for code in entries:
                count += 1
                if code not in codes:
                    problems.append(f"{path}: the compile error {code!r} has no documented(...) case in {suite}")
    return count


def main():
    problems = []
    tracked = tracked_files()
    snippet_count = check_snippets(problems, tracked, "--write-snippets" in sys.argv[1:])
    error_count = check_compile_errors(problems, tracked)
    adr_count = check_adrs(problems)
    doc_count = check_links(problems, tracked)

    print(
        f"checked {adr_count} ADRs, {doc_count} tracked markdown files, {snippet_count} quoted examples "
        f"and {error_count} documented compile errors"
    )
    if problems:
        print(f"\n{len(problems)} problem(s):\n")
        for p in problems:
            print(f"  - {p}")
        return 1
    print("no problems found")
    return 0


if __name__ == "__main__":
    sys.exit(main())
