#!/usr/bin/env python3
"""
Renders the crafting-recipe images of the player guide (docs/guide/images/recipe-*.png) in the style of the vanilla
crafting table: the 3x3 grid, arrow and result slot, cropped tight, scaled 3x.

    python scripts/docs/gen_recipes.py [--client-jar PATH]

Vanilla textures (crafting_table.png, item icons) are read straight out of the Minecraft client jar; the mod's own
textures from src/main/resources. The jar is found in the ModDevGradle cache
(~/.gradle/caches/neoformruntime/artifacts/minecraft_<version>_client.jar, version from gradle.properties) unless
--client-jar / MC_CLIENT_JAR says otherwise.

Every recipe JSON under data/mystcraft/recipe is listed in RECIPES; an item with several recipes gets one image per
recipe (the guide shows them side by side). The script fails when a recipe JSON has no entry or an entry's pattern
disagrees with its JSON, so the pictures cannot drift from the real recipes.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "guide" / "images"
MY = ROOT / "src" / "main" / "resources" / "assets" / "mystcraft" / "textures"
RECIPE_DIR = ROOT / "src" / "main" / "resources" / "data" / "mystcraft" / "recipe"

SCALE = 3  # GUI pixels -> output pixels
# Vanilla crafting_table.png: slot origins (texture coordinates) and the tight crop around grid, arrow and result.
GRID_ORIGIN = (30, 17)
SLOT_STEP = 18
RESULT_ORIGIN = (124, 35)
CROP = (25, 12, 150, 76)


def find_client_jar(explicit: str | None) -> Path:
    candidates = []
    if explicit:
        candidates.append(Path(explicit))
    if os.environ.get("MC_CLIENT_JAR"):
        candidates.append(Path(os.environ["MC_CLIENT_JAR"]))
    props = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    version = re.search(r"^minecraft_version=(.+)$", props, re.M).group(1).strip()
    cache = Path.home() / ".gradle" / "caches" / "neoformruntime" / "artifacts"
    candidates.append(cache / f"minecraft_{version}_client.jar")
    if cache.is_dir():
        candidates.extend(sorted(cache.glob(f"minecraft_{version}*_client.jar")))
    for c in candidates:
        if c.is_file():
            return c
    sys.exit("client jar not found; pass --client-jar (tried: " + ", ".join(map(str, candidates)) + ")")


class Textures:
    def __init__(self, jar: Path):
        self.jar = zipfile.ZipFile(jar)
        self.names = set(self.jar.namelist())

    def vanilla(self, rel: str) -> Image.Image:
        name = f"assets/minecraft/textures/{rel}"
        if name not in self.names:
            sys.exit(f"{name} not in {self.jar.filename}")
        return Image.open(io.BytesIO(self.jar.read(name))).convert("RGBA")

    def mine(self, rel: str) -> Image.Image:
        return Image.open(MY / rel).convert("RGBA")

    def guide(self, name: str) -> Image.Image:
        return Image.open(OUT / name).convert("RGBA")


TEX: Textures


def icon(im: Image.Image, size: int = 16) -> Image.Image:
    return im if im.size == (size, size) else im.resize((size, size), Image.Resampling.NEAREST)


def mc_item(name: str) -> Image.Image:
    for folder in ("item", "block"):
        try:
            return icon(TEX.vanilla(f"{folder}/{name}.png"))
        except SystemExit:
            continue
    sys.exit(f"no vanilla texture for {name}")


# Ingredient / result key -> guide icon (docs/guide/images/icon-*.png, cut from the game by scripts/docs/crop_icons.py so
# items look as they do in game) with the raw vanilla texture as a fallback until the icons exist.
ICONS = {
    "glass_bottle": ("icon-glass-bottle.png", "glass_bottle"),
    "feather": ("icon-feather.png", "feather"),
    "planks": ("icon-oak-planks.png", "oak_planks"),
    "leather": ("icon-leather.png", "leather"),
    "string": ("icon-string.png", "string"),
    "black_dye": ("icon-black-dye.png", "black_dye"),
    "potion": ("icon-potion.png", "potion"),
    "water_bucket": ("icon-water-bucket.png", "water_bucket"),
    "stone": ("icon-stone.png", "stone"),
    "iron": ("icon-iron-ingot.png", "iron_ingot"),
    "stick": ("icon-stick.png", "stick"),
    "paper": ("icon-paper.png", "paper"),
    "crystal": ("icon-crystal.png", None),
    "writing_desk": ("icon-writing-desk.png", None),
    "folder": ("icon-collation-folder.png", None),
    "ink_vial": ("icon-ink-vial.png", None),
    "ink_mixer": ("icon-ink-mixer.png", None),
    "book_binder": ("icon-book-binder.png", None),
    "bookstand": ("icon-bookstand.png", None),
    "lectern": ("icon-lectern.png", None),
    "book_receptacle": ("icon-book-receptacle.png", None),
    "link_panel": ("icon-page-link-panel.png", None),
    "unlinked": ("icon-unlinked-book.png", None),
}


def item_icon(key: str) -> Image.Image:
    guide_name, vanilla = ICONS[key]
    if (OUT / guide_name).is_file():
        return icon(TEX.guide(guide_name))
    if vanilla is None:
        sys.exit(f"missing {OUT / guide_name}: run the client smoke and scripts/docs/crop_icons.py first")
    return mc_item(vanilla)


# Vanilla ingredient ids as they appear in the recipe JSON -> ICONS key (tags drawn as a representative item).
INGREDIENT_KEYS = {
    "minecraft:glass_bottle": "glass_bottle", "minecraft:feather": "feather", "#minecraft:planks": "planks",
    "minecraft:item_frame": "item_frame", "minecraft:leather": "leather", "minecraft:string": "string",
    "minecraft:black_dye": "black_dye", "minecraft:potion": "potion", "minecraft:water_bucket": "water_bucket",
    "minecraft:stone": "stone", "minecraft:iron_ingot": "iron", "minecraft:stick": "stick", "minecraft:paper": "paper",
    "mystcraft:crystal": "crystal", "mystcraft:page": "link_panel",
}
RESULT_KEYS = {
    "mystcraft:writing_desk": "writing_desk",     "mystcraft:collation_folder": "folder", "mystcraft:ink_vial": "ink_vial", "mystcraft:ink_mixer": "ink_mixer",
    "mystcraft:book_binder": "book_binder", "mystcraft:bookstand": "bookstand", "mystcraft:lectern": "lectern",
    "mystcraft:book_receptacle": "book_receptacle", "mystcraft:unlinked_book": "unlinked",
}


def paste_item(canvas: Image.Image, key: str, x: int, y: int) -> None:
    im = item_icon(key)
    canvas.paste(im, (x + 1, y + 1), im)  # slot interior is 16x16 inset by 1 from the 18x18 cell


def render(pattern: list[str], keys: dict[str, str], result: str, result_count: int = 1) -> Image.Image:
    panel = TEX.vanilla("gui/container/crafting_table.png").crop((0, 0, 176, 84)).copy()
    rows = [row.ljust(3)[:3] for row in pattern] + ["   "] * (3 - len(pattern))
    for r, row in enumerate(rows):
        for c, ch in enumerate(row):
            if ch != " ":
                paste_item(panel, keys[ch], GRID_ORIGIN[0] + c * SLOT_STEP, GRID_ORIGIN[1] + r * SLOT_STEP)
    paste_item(panel, result, *RESULT_ORIGIN)
    if result_count > 1:
        draw = ImageDraw.Draw(panel)
        label = str(result_count)
        tx, ty = RESULT_ORIGIN[0] + 17 - 6 * len(label), RESULT_ORIGIN[1] + 9
        draw.text((tx + 1, ty + 1), label, fill=(63, 63, 63, 255))
        draw.text((tx, ty), label, fill=(255, 255, 255, 255))
    panel = panel.crop(CROP)
    return panel.resize((panel.width * SCALE, panel.height * SCALE), Image.Resampling.NEAREST)


def ingredient_key(ingredient) -> str:
    if isinstance(ingredient, dict):
        ingredient = ingredient.get("item") or "#" + ingredient["tag"]
    if ingredient not in INGREDIENT_KEYS:
        sys.exit(f"no icon mapping for ingredient {ingredient}")
    return INGREDIENT_KEYS[ingredient]


def render_shapeless(ingredient_keys: list[str], result_key: str, count: int = 1) -> Image.Image:
    pattern, keys = ["   ", "   ", "   "], {}
    for i, key in enumerate(ingredient_keys):  # left to right, top to bottom
        ch = chr(ord("a") + i)
        keys[ch] = key
        r, c = divmod(i, 3)
        pattern[r] = pattern[r][:c] + ch + pattern[r][c + 1:]
    return render(pattern, keys, result_key, count)


def render_recipe(data: dict) -> Image.Image:
    if data["type"] == "mystcraft:linking_book":
        # custom recipe (item/recipe/LinkingBookRecipe): one Link Panel + one Leather anywhere in the grid
        return render_shapeless(["link_panel", "leather"], "unlinked")
    result = data["result"]
    result_key = RESULT_KEYS[result["id"]]
    count = int(result.get("count", 1))
    if data["type"] == "minecraft:crafting_shaped":
        keys = {ch: ingredient_key(ing) for ch, ing in data["key"].items()}
        return render(data["pattern"], keys, result_key, count)
    if data["type"] == "minecraft:crafting_shapeless":
        return render_shapeless([ingredient_key(ing) for ing in data["ingredients"]], result_key, count)
    sys.exit(f"unsupported recipe type {data['type']}")


# recipe JSON name -> output image. One line per JSON file so a new recipe (or a second recipe for an item) cannot be
# forgotten: the script fails on a JSON without an entry.
RECIPES = {
    "writing_desk": "recipe-writing-desk.png",
    "collation_folder": "recipe-collation-folder.png",
    "ink_vial": "recipe-ink-vial.png",
    "ink_vial_bucket": "recipe-ink-vial-bucket.png",
    "ink_mixer": "recipe-ink-mixer.png",
    "book_binder": "recipe-book-binder.png",
    "bookstand": "recipe-bookstand.png",
    "lectern": "recipe-lectern.png",
    "book_receptacle": "recipe-book-receptacle.png",
    "linking_book": "recipe-unlinked-link-book.png",
}


def main() -> None:
    global TEX
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--client-jar", help="Minecraft client jar with the vanilla textures")
    args = parser.parse_args()
    TEX = Textures(find_client_jar(args.client_jar))
    on_disk = sorted(p.stem for p in RECIPE_DIR.glob("*.json"))
    missing = [n for n in on_disk if n not in RECIPES]
    if missing:
        sys.exit(f"recipe JSON without an image entry in RECIPES: {missing}")
    OUT.mkdir(parents=True, exist_ok=True)
    for name, image in RECIPES.items():
        data = json.loads((RECIPE_DIR / f"{name}.json").read_text(encoding="utf-8"))
        img = render_recipe(data)
        img.save(OUT / image)
        print(f"wrote {image} ({img.size[0]}x{img.size[1]}) from {name}.json")


if __name__ == "__main__":
    main()
