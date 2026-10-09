#!/usr/bin/env python3
"""
Checks that every file path and `package/Class` reference in the Markdown docs resolves in the repository, so the
docs cannot drift away from the code silently.

    python scripts/docs/check_links.py          # exit 1 and list the dangling references

Checked: `docs/*.md` links, backticked paths with a slash (`scripts/qa/compare.py`, `src/gametest`, `world/AgeSpawn`
→ `src/main/java/com/tbd/mystcraft/world/AgeSpawn.java` or a directory), `ClassName` tokens that look
like Java classes (`AgeBlueprint.fill` → a class named AgeBlueprint somewhere under src/). Anything else is skipped.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DOCS = [REPO / "CLAUDE.md", REPO / "README.md", *sorted((REPO / "docs").rglob("*.md"))]
JAVA_ROOTS = [REPO / "src/main/java/com/tbd/mystcraft", REPO / "src/gametest/java/com/tbd/mystcraft",
              REPO / "src/test/java/com/tbd/mystcraft"]
CLASS_INDEX = {p.stem: p for root in JAVA_ROOTS if root.exists() for p in root.rglob("*.java")}


MOD_PACKAGES = {p.name for root in JAVA_ROOTS if root.exists() for p in root.iterdir() if p.is_dir()}
TOP_LEVEL = {p.name for p in REPO.iterdir()}
SKIP_DOCS = {"docs/API_NOTES.md"}  # vanilla API names, by design not in this repo


def resolve_path(ref: str) -> bool:
    ref = ref.rstrip("/").split("#")[0]
    if ref.startswith("out/"):
        return True  # build output
    if (REPO / ref).exists():
        return True
    if (REPO / "src/main/resources/datapacks/mystcraft_facility/data/mystcraft/structure" / ref).exists():
        return True  # a facility pool id (docs/STRUCTURES.md)
    # strip a trailing .method on a class reference: world/AgeSpawn.findSpawn
    m = re.fullmatch(r"(.+/[A-Z]\w+)\.\w+(/\w+)*", ref)
    if m:
        ref = m.group(1)
    for root in JAVA_ROOTS:
        if (root / ref).exists() or (root / (ref + ".java")).exists():
            return True
    return False


def main() -> int:
    bad = []
    for doc in DOCS:
        rel = doc.relative_to(REPO).as_posix()
        if rel in SKIP_DOCS or rel.startswith("docs/plans/"):
            continue
        text = doc.read_text(encoding="utf-8")
        for m in re.finditer(r"`([^`\n]+)`", text):
            token = m.group(1)
            if re.search(r"[\s|=:()<>{}*]", token):
                continue
            if "/" in token:
                first = token.split("/")[0]
                if token.count("/") == 0 or (first not in TOP_LEVEL and first not in MOD_PACKAGES):
                    continue
                if not resolve_path(token):
                    bad.append(f"{rel}: path `{token}`")
            else:
                cm = re.fullmatch(r"([A-Z]\w+)[.#]\w+", token)
                if cm and cm.group(1) not in CLASS_INDEX and not token.endswith((".md", ".json", ".toml", ".txt")):
                    bad.append(f"{rel}: class `{token}`")
        for m in re.finditer(r"\]\(([^)]+)\)", text):
            link = m.group(1).split("#")[0]
            if not link or link.startswith("http"):
                continue
            if not (doc.parent / link).exists() and not (REPO / link).exists():
                bad.append(f"{rel}: link ({m.group(1)})")
    for b in bad:
        print(b)
    print(f"{len(DOCS)} docs, {len(bad)} dangling references")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
