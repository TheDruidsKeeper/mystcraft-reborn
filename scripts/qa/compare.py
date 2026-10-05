#!/usr/bin/env python3
"""
Visual regression for the QA shelf tour (docs/QA.md).

The client smoke test screenshots every QA world by day and by night (selfcheck_qa_<id>_<day|night>.png). This
script reduces each screenshot to a small signature - mean RGB of three horizontal bands (sky, horizon, ground) and
a 16x9 grey thumbnail - and compares it with the committed baseline (scripts/qa/baselines.json). A band colour that
moved further than --band-threshold or a thumbnail that differs by more than --thumb-threshold on average is reported
as drift; the exit code is 1 when anything drifted or a baselined screenshot is missing.

    python scripts/qa/compare.py out/screenshots                 # compare, print a report
    python scripts/qa/compare.py out/screenshots --update        # (re)write the baselines from these screenshots
    python scripts/qa/compare.py out/screenshots --report out/qa-report.txt

A human looks at the screenshots only when this reports drift or when a new world is baselined.
Requires Pillow.
"""
from __future__ import annotations

import argparse
import json
import sys
import warnings
from pathlib import Path

from PIL import Image, ImageOps

warnings.filterwarnings("ignore", category=DeprecationWarning)  # Pillow getdata() notice

HERE = Path(__file__).resolve().parent
BASELINES = HERE / "baselines.json"
BANDS = {"sky": (0.0, 0.2), "horizon": (0.4, 0.6), "ground": (0.75, 1.0)}
THUMB = (16, 9)


def signature(path: Path) -> dict:
    img = Image.open(path).convert("RGB")
    w, h = img.size
    bands = {}
    for name, (a, b) in BANDS.items():
        box = (0, int(h * a), w, max(int(h * a) + 1, int(h * b)))
        px = list(img.crop(box).resize((32, 8)).getdata())
        n = len(px)
        bands[name] = [round(sum(p[i] for p in px) / n, 1) for i in range(3)]
    thumb = list(ImageOps.grayscale(img).resize(THUMB).getdata())
    return {"bands": bands, "thumb": thumb}


def distance(a, b) -> float:
    return sum((x - y) ** 2 for x, y in zip(a, b)) ** 0.5


def compare(sig: dict, base: dict, band_threshold: float, thumb_threshold: float) -> list[str]:
    problems = []
    for name, rgb in sig["bands"].items():
        d = distance(rgb, base["bands"][name])
        if d > band_threshold:
            problems.append(f"{name} band moved {d:.0f} (was {base['bands'][name]}, now {rgb})")
    diff = sum(abs(x - y) for x, y in zip(sig["thumb"], base["thumb"])) / len(sig["thumb"])
    if diff > thumb_threshold:
        problems.append(f"thumbnail differs by {diff:.1f} on average")
    return problems


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("screenshots", type=Path, help="directory with selfcheck_qa_*.png")
    ap.add_argument("--update", action="store_true", help="write baselines from these screenshots")
    ap.add_argument("--band-threshold", type=float, default=40.0)
    ap.add_argument("--thumb-threshold", type=float, default=28.0)
    ap.add_argument("--report", type=Path, default=None)
    args = ap.parse_args(argv)

    shots = sorted(args.screenshots.glob("selfcheck_qa_*.png"))
    if not shots:
        print(f"no selfcheck_qa_*.png in {args.screenshots}")
        return 1
    sigs = {p.stem.removeprefix("selfcheck_"): signature(p) for p in shots}

    if args.update:
        BASELINES.write_text(json.dumps(sigs, separators=(",", ":")) + "\n", encoding="utf-8")
        print(f"baselined {len(sigs)} screenshots -> {BASELINES}")
        return 0

    base = json.loads(BASELINES.read_text(encoding="utf-8")) if BASELINES.exists() else {}
    lines = []
    drift = 0
    for name, sig in sigs.items():
        if name not in base:
            lines.append(f"NEW      {name}: no baseline (review the screenshot, then --update)")
            drift += 1
            continue
        problems = compare(sig, base[name], args.band_threshold, args.thumb_threshold)
        if problems:
            drift += 1
            lines.append(f"DRIFT    {name}: " + "; ".join(problems))
        else:
            lines.append(f"ok       {name}")
    for name in sorted(set(base) - set(sigs)):
        drift += 1
        lines.append(f"MISSING  {name}: baselined but not captured")
    report = "\n".join(lines) + f"\n\n{len(sigs)} screenshots, {drift} to review\n"
    print(report)
    if args.report:
        args.report.write_text(report, encoding="utf-8")
    return 1 if drift else 0


if __name__ == "__main__":
    sys.exit(main())
