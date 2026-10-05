#!/usr/bin/env python3
"""Retrofit Facility puzzle markers into imported structure templates (docs/STRUCTURES.md, FACILITY_PLAN.md §2.2).

Reads ``manifest.json`` and, for every piece with a ``markers`` object, rewrites its ``.nbt`` in place so that vanilla
structure blocks in DATA mode carry the marker strings ``facility/FacilityMarkers`` resolves at placement. Idempotent:
every marker remembers the block it replaced (``mystcraft_original``) and is reverted before the spec is applied again.

``markers`` spec (all keys optional):
  doors:   true            door markers one block inside every horizontal doorway (3x3 air cells next to a jigsaw)
  lock:    "symbol[:N]" | "sequence[:N]" | "offering[:item]" | "trial"   one lock on a free floor cell near the centre
  spawner: "melee/zombie"  a trial_spawner marker on another free floor cell (any vanilla trial-chamber kind)
  clues:   [0, 1, ...]     clue:<n> markers replacing wall blocks at eye level, spread around the room
  rewards: {"vault": true, "chests": {"<loot table>": "<marker>"}, "linkbook": true}
           vault turns the first chest with the facility_uncommon table into a vanilla vault; chests replaces every
           other chest carrying one of the listed loot tables by that marker (e.g. reward:<table>, reward:linkbook);
           linkbook adds a reward:linkbook cache on a free floor cell (skipped when the room has none)

Usage: python scripts/structures/markers.py [--manifest path] [--check]
``--check`` only reports what would change (exit 1 if anything would).
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

try:
    import nbtlib
    from nbtlib import Compound, Int, List, String
except ImportError:  # pragma: no cover
    sys.exit("pip install nbtlib")

HERE = Path(__file__).resolve().parent
PACK = HERE.parent.parent / "src/main/resources/datapacks/mystcraft_facility"
STRUCTURE_BLOCK = "minecraft:structure_block"
ORIGINAL = "mystcraft_original"
UNCOMMON = "mystcraft:chests/facility_uncommon"
CHESTS = {"minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel"}
HORIZONTAL = {"west_up": (-1, 0), "east_up": (1, 0), "north_up": (0, -1), "south_up": (0, 1)}


class Template:
    def __init__(self, path: Path):
        self.path = path
        self.nbt = nbtlib.load(path)
        self.size = [int(v) for v in self.nbt["size"]]
        self.palette = self.nbt["palette"]
        self.blocks = self.nbt["blocks"]
        self.by_pos = {tuple(int(v) for v in b["pos"]): b for b in self.blocks}

    def name(self, block) -> str:
        return str(self.palette[int(block["state"])]["Name"])

    def at(self, pos) -> str:
        b = self.by_pos.get(tuple(pos))
        return self.name(b) if b is not None else "minecraft:air"

    def is_air(self, pos) -> bool:
        return self.at(pos) in ("minecraft:air", "minecraft:cave_air")

    def inside(self, pos) -> bool:
        return all(0 <= pos[i] < self.size[i] for i in range(3))

    def solid(self, pos) -> bool:
        return self.inside(pos) and not self.is_air(pos) and self.at(pos) not in ("minecraft:jigsaw", STRUCTURE_BLOCK)

    def marker_state(self) -> int:
        for i, p in enumerate(self.palette):
            if str(p["Name"]) == STRUCTURE_BLOCK and str(p.get("Properties", {}).get("mode", "")) == "data":
                return i
        self.palette.append(Compound({"Name": String(STRUCTURE_BLOCK), "Properties": Compound({"mode": String("data")})}))
        return len(self.palette) - 1

    def revert_markers(self) -> int:
        n = 0
        for b in self.blocks:
            nbt = b.get("nbt")
            if self.name(b) == STRUCTURE_BLOCK and nbt is not None and ORIGINAL in nbt:
                orig = nbt[ORIGINAL]
                b["state"] = Int(int(orig["state"]))
                if "nbt" in orig:
                    b["nbt"] = orig["nbt"]
                else:
                    del b["nbt"]
                n += 1
        return n

    def mark(self, pos, data: str):
        pos = tuple(pos)
        b = self.by_pos.get(pos)
        state = self.marker_state()
        if b is None:
            b = Compound({"pos": List[Int]([Int(v) for v in pos]), "state": Int(state)})
            self.blocks.append(b)
            self.by_pos[pos] = b
            orig = Compound({"state": Int(self.air_state())})
        else:
            orig = Compound({"state": Int(int(b["state"]))})
            if "nbt" in b:
                orig["nbt"] = b["nbt"]
            b["state"] = Int(state)
        b["nbt"] = Compound({"mode": String("DATA"), "metadata": String(data), "name": String(""), "author": String(""),
                             "posX": Int(0), "posY": Int(1), "posZ": Int(0), "sizeX": Int(0), "sizeY": Int(0), "sizeZ": Int(0),
                             "rotation": String("NONE"), "mirror": String("NONE"), ORIGINAL: orig})

    def air_state(self) -> int:
        for i, p in enumerate(self.palette):
            if str(p["Name"]) == "minecraft:air":
                return i
        self.palette.append(Compound({"Name": String("minecraft:air")}))
        return len(self.palette) - 1

    def jigsaws(self):
        for b in self.blocks:
            if self.name(b) == "minecraft:jigsaw":
                props = self.palette[int(b["state"])].get("Properties", {})
                yield tuple(int(v) for v in b["pos"]), str(props.get("orientation", ""))

    def chests(self):
        for b in self.blocks:
            if self.name(b) in CHESTS:
                yield tuple(int(v) for v in b["pos"]), str(b.get("nbt", {}).get("LootTable", ""))

    def doorway_cells(self):
        """Air cells one block inside each horizontal doorway (3 wide x 3 high around the jigsaw)."""
        cells = []
        for (x, y, z), orient in self.jigsaws():
            d = HORIZONTAL.get(orient)
            if d is None:
                continue
            ix, iz = -d[0], -d[1]  # inward
            lateral = (0, 1) if d[0] != 0 else (1, 0)
            for dy in range(0, 3):
                for s in (-1, 0, 1):
                    p = (x + ix + lateral[0] * s, y + dy, z + iz + lateral[1] * s)
                    if self.inside(p) and self.is_air(p):
                        cells.append(p)
        return cells

    def floor_cells(self, avoid, min_dist=2):
        """Free floor cells (air with air above and solid below), interior, away from doorways; best first."""
        door = set(avoid)
        door_xz = {(p[0], p[2]) for p in door}
        out = []
        for x in range(1, self.size[0] - 1):
            for z in range(1, self.size[2] - 1):
                for y in range(1, self.size[1] - 2):
                    p = (x, y, z)
                    if not (self.is_air(p) and self.is_air((x, y + 1, z)) and self.solid((x, y - 1, z))):
                        continue
                    open_sides = sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                                     if self.is_air((x + dx, y, z + dz)) and self.is_air((x + dx, y + 1, z + dz)))
                    if open_sides < 2:
                        continue  # enclosed (inside a decoration), unreachable
                    if any(abs(x - dx) + abs(z - dz) < min_dist for dx, dz in door_xz):
                        continue
                    wall = min(x, z, self.size[0] - 1 - x, self.size[2] - 1 - z)
                    centre = math.hypot(x - (self.size[0] - 1) / 2, z - (self.size[2] - 1) / 2)
                    out.append((y, -wall, centre, p))
        out.sort()
        return [p for _, _, _, p in out]

    def wall_cells_eye_level(self, y=2):
        """Solid wall cells at eye level whose inward neighbour is air, one per wall side, spread out."""
        picks = []
        sides = [((0, 1), "x0"), ((self.size[0] - 1, -1), "x1"), ((0, 1), "z0"), ((self.size[2] - 1, -1), "z1")]
        for (w, inward), side in sides:
            cands = []
            if side.startswith("x"):
                for z in range(2, self.size[2] - 2):
                    p, q = (w, y, z), (w + inward, y, z)
                    if self.solid(p) and self.is_air(q):
                        cands.append(p)
            else:
                for x in range(2, self.size[0] - 2):
                    p, q = (x, y, w), (x, y, w + inward)
                    if self.solid(p) and self.is_air(q):
                        cands.append(p)
            if cands:
                picks.append(cands[len(cands) // 3])
        return picks

    def save(self):
        self.nbt.save(self.path)


def spaced(cells, n, spacing=2):
    out = []
    for c in cells:
        if all(abs(c[0] - o[0]) + abs(c[2] - o[2]) >= spacing for o in out):
            out.append(c)
        if len(out) == n:
            break
    return out


def apply(template: Template, spec: dict) -> list[str]:
    log = []
    reverted = template.revert_markers()
    if reverted:
        log.append(f"reverted {reverted} markers")
    doors = template.doorway_cells()
    if spec.get("doors"):
        for p in doors:
            template.mark(p, "door")
        log.append(f"{len(doors)} door cells")
    taken = []
    floor = template.floor_cells(doors)
    lock = spec.get("lock")
    if lock:
        kind = lock.split(":")[0]
        count = 1
        if kind == "sequence":
            count = int(lock.split(":")[1]) if ":" in lock else 3
            for p in spaced(floor, count):
                template.mark(p, "lock:sequence")
                taken.append(p)
            log.append(f"sequence bank of {count}")
        else:
            p = floor[0]
            template.mark(p, "lock:" + lock)
            taken.append(p)
            log.append(f"lock {lock} at {p}")
    spawner = spec.get("spawner")
    if spawner:
        rest = [c for c in floor if all(abs(c[0] - t[0]) + abs(c[2] - t[2]) >= 3 for t in taken)]
        p = rest[len(rest) // 2] if rest else floor[-1]
        template.mark(p, "trial_spawner:" + spawner if spawner is not True else "trial_spawner")
        taken.append(p)
        log.append(f"trial spawner at {p}")
    for i, n in enumerate(spec.get("clues", [])):
        walls = template.wall_cells_eye_level()
        if i < len(walls):
            template.mark(walls[i], f"clue:{n}")
            log.append(f"clue:{n} at {walls[i]}")
    rewards = spec.get("rewards", {})
    if rewards:
        vault = rewards.get("vault")
        chests = rewards.get("chests", {})
        for pos, table in list(template.chests()):
            if vault and table == UNCOMMON:
                template.mark(pos, "vault")
                vault = False
                log.append(f"vault replaces uncommon chest at {pos}")
            elif table in chests:
                template.mark(pos, chests[table])
                log.append(f"{chests[table]} replaces chest at {pos}")
        if rewards.get("linkbook"):
            rest = [c for c in floor if all(abs(c[0] - t[0]) + abs(c[2] - t[2]) >= 2 for t in taken)]
            if rest:
                template.mark(rest[0], "reward:linkbook")
                log.append(f"linkbook cache at {rest[0]}")
            else:
                log.append("no floor cell for a linkbook cache")
    return log


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", default=str(PACK / "manifest.json"))
    ap.add_argument("--check", action="store_true", help="report only; exit 1 if any template would change")
    args = ap.parse_args()
    manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    ns = manifest.get("namespace", "mystcraft")
    changed = 0
    for piece in manifest["pieces"]:
        spec = piece.get("markers") or {}
        path = PACK / "data" / ns / "structure" / (piece["file"] + ".nbt")
        template = Template(path)
        if not spec and not any(template.name(b) == STRUCTURE_BLOCK for b in template.blocks):
            continue
        before = path.read_bytes()
        log = apply(template, spec)
        if args.check:
            if Template(path).nbt != template.nbt:
                changed += 1
                print(f"{piece['file']}: would change ({'; '.join(log)})")
        else:
            template.save()
            if path.read_bytes() != before:
                changed += 1
            print(f"{piece['file']}: {'; '.join(log) or 'no markers'}")
    if args.check:
        print(f"{changed} templates out of date")
        return 1 if changed else 0
    print(f"{changed} templates rewritten")
    return 0


if __name__ == "__main__":
    sys.exit(main())
