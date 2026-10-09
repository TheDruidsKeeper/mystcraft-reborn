#!/usr/bin/env python3
"""
Cuts the player guide's item icons (docs/guide/images/icon-*.png) out of the client smoke's screenshot of the open
debug-scene supply chest, so every icon looks exactly as the item does in game (3D block items, tinted bottles,
dynamic page and book icons included).

    scripts/client-smoke.sh                      # produces out/screenshots/selfcheck_02z_screen_chest.png + the log
    python scripts/docs/crop_icons.py [--out DIR]

Which slot holds what comes from the `[base] chest slot <n> = <id>` lines QaBase logs
(out/logs/client-smoke.log). The chest GUI is the vanilla double chest (176 x 222) centred on the screen at the
smoke's guiScale (2, docker/client-smoke-entry.sh); slot pixels equal to the slot background become transparent.
Icon names: `icon-<item path>.png` with `_` -> `-` (pages: `icon-page-<kind>.png`).
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SCREENSHOT = ROOT / "out" / "screenshots" / "selfcheck_02z_screen_chest.png"
LOG = ROOT / "out" / "logs" / "client-smoke.log"
GUI_SCALE = 2
CHEST_W, CHEST_H = 176, 114 + 6 * 18  # double chest: ChestMenu imageHeight = 114 + rows * 18
SLOT_BG = (139, 139, 139)
SLOT_LINE = re.compile(r"\[scene\] chest slot (\d+) = (\S+)")


def slot_contents(log: Path) -> dict[int, str]:
    slots: dict[int, str] = {}
    for line in log.read_text(encoding="utf-8", errors="replace").splitlines():
        m = SLOT_LINE.search(line)
        if m:
            slots[int(m.group(1))] = m.group(2)  # the last scene build wins
    return slots


def icon_name(item_id: str) -> str:
    path = item_id.split(":", 1)[1]  # drop the namespace; "page/symbol_sun_normal" -> "page-symbol-sun-normal"
    return "icon-" + path.replace("/", "-").replace("_", "-") + ".png"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--screenshot", type=Path, default=SCREENSHOT)
    parser.add_argument("--log", type=Path, default=LOG)
    parser.add_argument("--out", type=Path, default=ROOT / "docs" / "guide" / "images")
    parser.add_argument("--gui-scale", type=int, default=GUI_SCALE)
    args = parser.parse_args()
    if not args.screenshot.is_file() or not args.log.is_file():
        sys.exit(f"need {args.screenshot} and {args.log}: run scripts/client-smoke.sh first")
    slots = slot_contents(args.log)
    if not slots:
        sys.exit("no '[base] chest slot' lines in the log")

    shot = Image.open(args.screenshot).convert("RGBA")
    s = args.gui_scale
    gui_w, gui_h = shot.width // s, shot.height // s
    left, top = (gui_w - CHEST_W) // 2, (gui_h - CHEST_H) // 2
    args.out.mkdir(parents=True, exist_ok=True)
    for slot, item_id in sorted(slots.items()):
        if slot >= 27:
            continue  # the lower half holds full stacks (count labels over the icons); only the showcase half is cropped
        row, col = divmod(slot, 9)
        x, y = (left + 8 + 18 * col) * s, (top + 18 + 18 * row) * s
        icon = shot.crop((x, y, x + 16 * s, y + 16 * s))
        px = icon.load()
        for iy in range(icon.height):
            for ix in range(icon.width):
                if px[ix, iy][:3] == SLOT_BG:
                    px[ix, iy] = (0, 0, 0, 0)
        if all(px[ix, iy][3] == 0 for iy in range(icon.height) for ix in range(icon.width)):
            print(f"slot {slot} ({item_id}) is empty in the screenshot - layout off? left={left} top={top}")
            continue
        name = icon_name(item_id)
        icon.save(args.out / name)
        print(f"wrote {name} from slot {slot}")


if __name__ == "__main__":
    main()
