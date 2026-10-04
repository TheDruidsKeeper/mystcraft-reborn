#!/usr/bin/env python3
"""
Import structure pieces into the Facility datapack as vanilla structure templates.

    python scripts/structures/import.py --pool facility/rooms --weight 1 \
        --source-pack Stonevaults --author TheGrimsey --license MIT \
        --url https://github.com/TheGrimsey/Stonevaults \
        --remap stonevaults:dungeon/=mystcraft:facility/ \
        --prefix sv_ path/to/room_a.nbt path/to/room_b.schem

Accepts vanilla structure templates (.nbt), Sponge schematics v1-v3 (.schem) and Litematica (.litematic) and writes
gzipped vanilla templates to

    src/main/resources/datapacks/mystcraft_facility/data/<ns>/structure/<pool>/<prefix><basename>.nbt

and records provenance + pool membership in the datapack manifest (manifest.json next to pack.mcmeta). The Gradle task
`generateStructurePools` turns the manifest into template_pool JSON, so a new room is: import, (optionally place
markers in a dev world and re-save), build.

Id remapping (`--remap old=new`, repeatable) rewrites every string tag inside block-entity NBT that starts with `old`
(jigsaw pool/name/target, container LootTable, structure-block metadata, ...). Foreign ids that remain afterwards are
listed so they can be provided or remapped.

Requires: nbtlib (`pip install nbtlib`). Stdlib otherwise.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
from pathlib import Path

try:
    import nbtlib
    from nbtlib import tag as T
except ImportError:  # pragma: no cover
    sys.exit("nbtlib is required: python -m pip install nbtlib")

REPO = Path(__file__).resolve().parents[2]
PACK_DIR = REPO / "src/main/resources/datapacks/mystcraft_facility"
MANIFEST = PACK_DIR / "manifest.json"
ID_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")


# --------------------------------------------------------------------------------------------------------------------
# Intermediate representation
# --------------------------------------------------------------------------------------------------------------------
class Template:
    """A vanilla-style template: palette of block states, sparse block list, entities."""

    def __init__(self, size, data_version):
        self.size = size
        self.data_version = data_version
        self.palette: list[T.Compound] = []
        self._index: dict[str, int] = {}
        self.blocks: list[T.Compound] = []
        self.entities: list[T.Compound] = []

    def state(self, name: str, props: dict[str, str] | None) -> int:
        key = name + "[" + ",".join(f"{k}={v}" for k, v in sorted((props or {}).items())) + "]"
        idx = self._index.get(key)
        if idx is None:
            c = T.Compound({"Name": T.String(name)})
            if props:
                c["Properties"] = T.Compound({k: T.String(v) for k, v in props.items()})
            idx = len(self.palette)
            self.palette.append(c)
            self._index[key] = idx
        return idx

    def add(self, x, y, z, state: int, nbt: T.Compound | None):
        b = T.Compound({"pos": T.List[T.Int]([T.Int(x), T.Int(y), T.Int(z)]), "state": T.Int(state)})
        if nbt is not None:
            b["nbt"] = nbt
        self.blocks.append(b)

    def to_nbt(self) -> T.Compound:
        return T.Compound({
            "size": T.List[T.Int]([T.Int(v) for v in self.size]),
            "palette": T.List[T.Compound](self.palette),
            "blocks": T.List[T.Compound](self.blocks),
            "entities": T.List[T.Compound](self.entities),
            "DataVersion": T.Int(self.data_version),
        })


def parse_state_string(s: str) -> tuple[str, dict[str, str]]:
    """'minecraft:stairs[facing=north,half=top]' -> (name, props)."""
    if "[" not in s:
        return s, {}
    name, rest = s.split("[", 1)
    rest = rest.rstrip("]")
    props = {}
    for kv in filter(None, rest.split(",")):
        k, v = kv.split("=", 1)
        props[k] = v
    return name, props


# --------------------------------------------------------------------------------------------------------------------
# Readers
# --------------------------------------------------------------------------------------------------------------------
def read_vanilla(root: T.Compound) -> Template:
    size = [int(v) for v in root["size"]]
    t = Template(size, int(root.get("DataVersion", 0)))
    for p in root["palette"]:
        props = {k: str(v) for k, v in p.get("Properties", {}).items()} or None
        t.state(str(p["Name"]), props)
    for b in root["blocks"]:
        pos = [int(v) for v in b["pos"]]
        t.add(*pos, int(b["state"]), b.get("nbt"))
    t.entities = list(root.get("entities", []))
    return t


def _read_varints(data: bytes):
    i = 0
    n = len(data)
    while i < n:
        value = 0
        shift = 0
        while True:
            byte = data[i]
            i += 1
            value |= (byte & 0x7F) << shift
            if not byte & 0x80:
                break
            shift += 7
        yield value


def read_sponge(root: T.Compound) -> Template:
    if "Schematic" in root:  # v3 wraps everything
        root = root["Schematic"]
    version = int(root.get("Version", 1))
    w, h, l = int(root["Width"]), int(root["Height"]), int(root["Length"])
    t = Template([w, h, l], int(root.get("DataVersion", 0)))
    if version >= 3:
        blocks = root["Blocks"]
        palette_tag = blocks["Palette"]
        data = bytes(blocks["Data"])
        block_entities = blocks.get("BlockEntities", [])
    else:
        palette_tag = root["Palette"]
        data = bytes(root["BlockData"])
        block_entities = root.get("BlockEntities", root.get("TileEntities", []))
    # Sponge palette: state string -> index. Build index -> template state.
    by_index: dict[int, int] = {}
    for state_str, idx in palette_tag.items():
        name, props = parse_state_string(state_str)
        by_index[int(idx)] = t.state(name, props or None)
    be_at: dict[tuple[int, int, int], T.Compound] = {}
    for be in block_entities:
        pos = tuple(int(v) for v in be["Pos"])
        if version >= 3:
            nbt = T.Compound(dict(be.get("Data", {})))
            nbt["id"] = T.String(str(be["Id"]))
        else:
            nbt = T.Compound({k: v for k, v in be.items() if k not in ("Pos", "Id")})
            nbt["id"] = T.String(str(be["Id"]))
        be_at[pos] = nbt
    for i, pal in enumerate(_read_varints(data)):
        y, rem = divmod(i, w * l)
        z, x = divmod(rem, w)
        state = by_index[pal]
        name = str(t.palette[state]["Name"])
        if name == "minecraft:structure_void":
            continue
        t.add(x, y, z, state, be_at.get((x, y, z)))
    return t


def _unpack_litematica(longs, bits: int, count: int):
    mask = (1 << bits) - 1
    out = []
    buf = 0
    buf_bits = 0
    li = 0
    for _ in range(count):
        while buf_bits < bits:
            v = int(longs[li]) & 0xFFFFFFFFFFFFFFFF
            li += 1
            buf |= v << buf_bits
            buf_bits += 64
        out.append(buf & mask)
        buf >>= bits
        buf_bits -= bits
    return out


def read_litematic(root: T.Compound) -> Template:
    regions = root["Regions"]
    # Enclosing box over all regions (sizes may be negative: position is then the far corner).
    boxes = []
    for name, r in regions.items():
        p = [int(r["Position"][k]) for k in "xyz"]
        s = [int(r["Size"][k]) for k in "xyz"]
        lo = [p[i] + (s[i] + 1 if s[i] < 0 else 0) for i in range(3)]
        hi = [lo[i] + abs(s[i]) for i in range(3)]
        boxes.append((name, r, lo, hi, [abs(v) for v in s]))
    gmin = [min(b[2][i] for b in boxes) for i in range(3)]
    gmax = [max(b[3][i] for b in boxes) for i in range(3)]
    size = [gmax[i] - gmin[i] for i in range(3)]
    t = Template(size, int(root.get("MinecraftDataVersion", 0)))
    for name, r, lo, hi, s in boxes:
        pal = list(r["BlockStatePalette"])
        states = [t.state(str(p["Name"]), {k: str(v) for k, v in p.get("Properties", {}).items()} or None) for p in pal]
        bits = max(2, math.ceil(math.log2(len(pal)))) if len(pal) > 1 else 2
        count = s[0] * s[1] * s[2]
        packed = _unpack_litematica(r["BlockStates"], bits, count)
        be_at = {}
        for be in r.get("TileEntities", []):
            key = (int(be["x"]), int(be["y"]), int(be["z"]))
            nbt = T.Compound({k: v for k, v in be.items() if k not in ("x", "y", "z")})
            be_at[key] = nbt
        for i, pi in enumerate(packed):
            y, rem = divmod(i, s[0] * s[2])
            z, x = divmod(rem, s[0])
            state = states[pi]
            if str(t.palette[state]["Name"]) == "minecraft:structure_void":
                continue
            gx, gy, gz = lo[0] - gmin[0] + x, lo[1] - gmin[1] + y, lo[2] - gmin[2] + z
            t.add(gx, gy, gz, state, be_at.get((x, y, z)))
    return t


def load_any(path: Path) -> Template:
    f = nbtlib.load(str(path))
    root = f if isinstance(f, T.Compound) else f.root
    if "blocks" in root and "palette" in root and "size" in root:
        return read_vanilla(root)
    if "Schematic" in root or ("Width" in root and ("BlockData" in root or "Blocks" in root)):
        return read_sponge(root)
    if "Regions" in root:
        return read_litematic(root)
    raise SystemExit(f"{path}: unrecognised format (keys: {list(root.keys())[:8]})")


# --------------------------------------------------------------------------------------------------------------------
# Post-processing
# --------------------------------------------------------------------------------------------------------------------
def remap_strings(tag, remaps: list[tuple[str, str]], foreign: set[str], keep_ns: set[str]):
    """Rewrite id-like strings in place (recursively); collect ids from namespaces we do not own."""
    if isinstance(tag, T.Compound):
        for k, v in list(tag.items()):
            if isinstance(v, T.String):
                s = str(v)
                for old, new in remaps:
                    if s.startswith(old):
                        s = new + s[len(old):]
                        break
                if s != str(v):
                    tag[k] = T.String(s)
                if ID_RE.match(s) and s.split(":", 1)[0] not in keep_ns:
                    foreign.add(s)
            else:
                remap_strings(v, remaps, foreign, keep_ns)
    elif isinstance(tag, T.List):
        for v in tag:
            remap_strings(v, remaps, foreign, keep_ns)


def strip_entities(t: Template):
    t.entities = []


def upstream_path(src: Path) -> str:
    """Provenance path: from the pack's `data/` root when the source is an unpacked jar/datapack, else the name."""
    s = str(src).replace("\\", "/")
    i = s.find("/data/")
    return s[i + 1:] if i >= 0 else src.name


# --------------------------------------------------------------------------------------------------------------------
# Manifest
# --------------------------------------------------------------------------------------------------------------------
def load_manifest() -> dict:
    if MANIFEST.exists():
        return json.loads(MANIFEST.read_text(encoding="utf-8"))
    return {"namespace": "mystcraft", "pools": {}, "pieces": [], "external": []}


def save_manifest(m: dict):
    m["pieces"].sort(key=lambda p: (p["pool"], p["file"]))
    MANIFEST.write_text(json.dumps(m, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


# --------------------------------------------------------------------------------------------------------------------
def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("files", nargs="+", type=Path)
    ap.add_argument("--pool", required=True, help="template pool path without namespace, e.g. facility/rooms")
    ap.add_argument("--weight", type=int, default=1)
    ap.add_argument("--projection", default="rigid", choices=["rigid", "terrain_matching"])
    ap.add_argument("--processors", default=None, help="processor list id for these pieces (default: pool default)")
    ap.add_argument("--prefix", default="", help="output file name prefix, e.g. sv_")
    ap.add_argument("--name", default=None, help="explicit output name (single file only), relative to structure/")
    ap.add_argument("--remap", action="append", default=[], metavar="OLD=NEW", help="id prefix rewrite, repeatable")
    ap.add_argument("--keep-entities", action="store_true", help="keep entities (armor stands, item frames, ...)")
    ap.add_argument("--data-version", type=int, default=None, help="override DataVersion (default: from source)")
    ap.add_argument("--tags", default="", help="comma separated tags for the manifest, e.g. needs-markers,trial")
    ap.add_argument("--source-pack", required=True)
    ap.add_argument("--author", required=True)
    ap.add_argument("--license", required=True)
    ap.add_argument("--url", required=True)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv)

    if args.name and len(args.files) != 1:
        ap.error("--name needs exactly one input file")
    remaps = []
    for r in args.remap:
        old, new = r.split("=", 1)
        remaps.append((old, new))
    manifest = load_manifest()
    ns = manifest["namespace"]
    keep_ns = {"minecraft", ns}

    for src in args.files:
        t = load_any(src)
        if not args.keep_entities:
            strip_entities(t)
        if args.data_version:
            t.data_version = args.data_version
        foreign: set[str] = set()
        for b in t.blocks:
            if "nbt" in b:
                remap_strings(b["nbt"], remaps, foreign, keep_ns)
        for e in t.entities:
            remap_strings(e, remaps, foreign, keep_ns)
        rel = args.name or f"{args.pool}/{args.prefix}{src.stem}"
        out = PACK_DIR / "data" / ns / "structure" / (rel + ".nbt")
        print(f"{src} -> {out.relative_to(REPO)}  size={t.size} palette={len(t.palette)} blocks={len(t.blocks)} "
              f"entities={len(t.entities)} DataVersion={t.data_version}")
        if foreign:
            print("  foreign ids left (provide or --remap):", ", ".join(sorted(foreign)))
        if args.dry_run:
            continue
        out.parent.mkdir(parents=True, exist_ok=True)
        nbtlib.File(t.to_nbt(), gzipped=True).save(str(out))
        entry = {
            "file": rel,
            "pool": args.pool,
            "weight": args.weight,
            "projection": args.projection,
            "tags": [x for x in args.tags.split(",") if x],
            "source": {
                "pack": args.source_pack,
                "author": args.author,
                "license": args.license,
                "url": args.url,
                "path": upstream_path(src),
            },
        }
        if args.processors:
            entry["processors"] = args.processors
        manifest["pieces"] = [p for p in manifest["pieces"] if p["file"] != rel] + [entry]
        manifest["pools"].setdefault(args.pool, {"fallback": "minecraft:empty"})
    if not args.dry_run:
        save_manifest(manifest)
        print(f"manifest: {MANIFEST.relative_to(REPO)} ({len(manifest['pieces'])} pieces)")


if __name__ == "__main__":
    main()
