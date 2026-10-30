"""Caja de cultivos: texturas (fuera, dentro, borde, fondo y suelo), modelo del bloque (caja abierta por arriba), estado,
objeto (se dibuja con su contenido), pantalla, receta, botín (suelta la caja con lo suyo), etiquetas y textos.

Uso: python3 tools/ew_crate.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import os
import random
import sys

from PIL import Image

args = sys.argv[1:]
PREVIEW = None
if "--preview" in args:
    i = args.index("--preview")
    PREVIEW = args[i + 1]
    del args[i:i + 2]
ROOT = args[0] if args else "."
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/evolvedwarfare")
DATA = os.path.join(RES, "data")

# lo que cabe en una caja (los comerciantes solo compran estos en cajas llenas)
CROPS = [
    "minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot", "minecraft:melon_slice",
    "minecraft:melon", "minecraft:pumpkin", "minecraft:sweet_berries", "minecraft:glow_berries", "minecraft:cocoa_beans",
    "minecraft:sugar_cane", "minecraft:cactus", "minecraft:bamboo", "minecraft:nether_wart", "minecraft:apple",
]


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def jit(c, rnd, amt=7):
    n = rnd.randint(-amt, amt)
    return tuple(max(0, min(255, v + n)) for v in c[:3]) + (c[3],)


def tex(painter, seed):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), painter(x, y, rnd))
    return im


SLAT = hexc("#B48A52")
SLAT_D = hexc("#8C6A3C")
POST = hexc("#7A5630")
POST_D = hexc("#5C3F22")
GAP = hexc("#3A2614")
INNER = hexc("#94703F")
NAIL = hexc("#4A4A4A")


def side():
    """Fuera: postes en las esquinas, larguero arriba y abajo y tres tablas con rendijas (filas 1-15: pared y fondo)."""
    def p(x, y, r):
        if x <= 1 or x >= 14:                       # postes
            c = POST_D if x in (0, 15) else jit(POST, r, 5)
            if y in (3, 12) and x in (1, 14):
                return NAIL
            return c
        if y <= 2 or y >= 13:                       # larguero de arriba y de abajo
            if y in (0, 15):
                return POST_D
            return jit(POST, r, 5) if y not in (2, 13) else shade(POST, 0.85)
        if y in (6, 9):                             # rendijas entre tablas
            return GAP
        c = jit(SLAT, r, 8)
        if (x * 5 + y * 3) % 13 == 0:
            c = shade(c, 0.88)
        if y in (5, 8, 12):
            c = shade(c, 0.9)
        return c
    return tex(p, 71)


def inside():
    def p(x, y, r):
        if y <= 2 or y >= 13:
            return jit(shade(POST, 0.9), r, 4)
        if y in (6, 9):
            return shade(GAP, 0.8)
        c = jit(INNER, r, 6)
        if (x * 3 + y * 7) % 11 == 0:
            c = shade(c, 0.9)
        return c
    return tex(p, 72)


def rim():
    """Canto de arriba de las paredes (vetas de testa)."""
    def p(x, y, r):
        c = jit(hexc("#C29A62"), r, 6)
        if (x + y) % 5 == 0:
            c = shade(c, 0.9)
        return c
    return tex(p, 73)


def floor():
    """Suelo de dentro: tablas."""
    def p(x, y, r):
        if (x - 2) % 4 == 3:
            return shade(INNER, 0.7)
        c = jit(shade(INNER, 0.92), r, 6)
        return c
    return tex(p, 74)


def bottom():
    def p(x, y, r):
        if x <= 1 or x >= 14 or y <= 1 or y >= 14:
            return jit(POST, r, 5)
        if abs(x - y) <= 0 or abs(x - (15 - y)) <= 0:
            return jit(POST_D, r, 4)
        return jit(SLAT_D, r, 6)
    return tex(p, 75)


def face(texture, uv, cull=None):
    f = {"texture": "#" + texture, "uv": uv}
    if cull:
        f["cullface"] = cull
    return f


def model():
    side_uv = [0, 1, 16, 14]
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": True,
        "textures": {
            "particle": "evolvedwarfare:block/crop_crate_side",
            "side": "evolvedwarfare:block/crop_crate_side",
            "inside": "evolvedwarfare:block/crop_crate_inside",
            "rim": "evolvedwarfare:block/crop_crate_rim",
            "floor": "evolvedwarfare:block/crop_crate_floor",
            "bottom": "evolvedwarfare:block/crop_crate_bottom",
        },
        "elements": [
            {"from": [0, 0, 0], "to": [16, 2, 16], "faces": {
                "down": face("bottom", [0, 0, 16, 16], "down"),
                "up": face("floor", [0, 0, 16, 16]),
                "north": face("side", [0, 14, 16, 16], "north"),
                "south": face("side", [0, 14, 16, 16], "south"),
                "west": face("side", [0, 14, 16, 16], "west"),
                "east": face("side", [0, 14, 16, 16], "east")}},
            {"from": [0, 2, 0], "to": [16, 15, 2], "faces": {
                "north": face("side", side_uv, "north"),
                "south": face("inside", side_uv),
                "up": face("rim", [0, 0, 16, 2]),
                "west": face("side", [0, 1, 2, 14], "west"),
                "east": face("side", [14, 1, 16, 14], "east")}},
            {"from": [0, 2, 14], "to": [16, 15, 16], "faces": {
                "south": face("side", side_uv, "south"),
                "north": face("inside", side_uv),
                "up": face("rim", [0, 14, 16, 16]),
                "west": face("side", [14, 1, 16, 14], "west"),
                "east": face("side", [0, 1, 2, 14], "east")}},
            {"from": [0, 2, 2], "to": [2, 15, 14], "faces": {
                "west": face("side", [2, 1, 14, 14], "west"),
                "east": face("inside", [2, 1, 14, 14]),
                "up": face("rim", [0, 2, 2, 14])}},
            {"from": [14, 2, 2], "to": [16, 15, 14], "faces": {
                "east": face("side", [2, 1, 14, 14], "east"),
                "west": face("inside", [2, 1, 14, 14]),
                "up": face("rim", [14, 2, 16, 14])}},
        ],
    }


ITEM_MODEL = {
    "parent": "builtin/entity",
    "gui_light": "side",
    "textures": {"particle": "evolvedwarfare:block/crop_crate_side"},
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 315, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 315, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 315, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    },
}


def gui():
    """176x133 como la tolva: un hueco en el centro y tu inventario."""
    W, H = 176, 133
    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    bg, hi, sh, black = hexc("#C6C6C6"), hexc("#FFFFFF"), hexc("#555555"), hexc("#000000")

    def rect(x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                im.putpixel((x, y), c)

    rect(1, 1, W - 1, H - 1, bg)
    rect(2, 0, W - 2, 1, black)
    rect(2, H - 1, W - 2, H, black)
    rect(0, 2, 1, H - 2, black)
    rect(W - 1, 2, W, H - 2, black)
    for p in [(1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)]:
        im.putpixel(p, black)
    rect(2, 1, W - 3, 3, hi)
    rect(1, 2, 3, H - 3, hi)
    rect(3, H - 3, W - 2, H - 1, sh)
    rect(W - 3, 3, W - 1, H - 2, sh)
    im.putpixel((W - 3, 2), bg)
    im.putpixel((2, H - 3), bg)

    def slot(x, y, big=False):
        s = 26 if big else 18
        rect(x, y, x + s, y + s, hexc("#8B8B8B"))
        rect(x, y, x + s - 1, y + 1, hexc("#373737"))
        rect(x, y, x + 1, y + s - 1, hexc("#373737"))
        rect(x + 1, y + s - 1, x + s, y + s, hi)
        rect(x + s - 1, y + 1, x + s, y + s, hi)

    # el hueco de la caja, con un marco de madera alrededor
    rect(73, 13, 103, 43, hexc("#7A5630"))
    rect(74, 14, 102, 42, hexc("#A8814E"))
    for x in range(74, 102):
        for y in range(14, 42):
            if (x - 74) % 7 == 6:
                im.putpixel((x, y), hexc("#8C6A3C"))
    slot(79, 19)
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 50 + r * 18)
    for c in range(9):
        slot(7 + c * 18, 108)
    return im


RECIPE = {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "pattern": ["S S", "P P", "PPP"],
    "key": {"S": {"item": "minecraft:stick"}, "P": {"tag": "minecraft:planks"}},
    "result": {"id": "evolvedwarfare:crop_crate", "count": 1},
}

LOOT = {
    "type": "minecraft:block",
    "pools": [{
        "rolls": 1,
        "bonus_rolls": 0,
        "entries": [{
            "type": "minecraft:item",
            "name": "evolvedwarfare:crop_crate",
            "functions": [{"function": "minecraft:copy_components", "source": "block_entity",
                           "include": ["evolvedwarfare:crop_crate"]}],
        }],
    }],
    "random_sequence": "evolvedwarfare:blocks/crop_crate",
}

# (español, inglés)
TEXTS = {
    "block.evolvedwarfare.crop_crate": ("Caja de cultivos", "Crop Crate"),
    "item.evolvedwarfare.crop_crate.filled": ("Caja de %s", "Crate of %s"),
    "tooltip.evolvedwarfare.crop_crate.empty": ("Vacía: ponla y clic derecho para meterle un cultivo (hasta 64)",
                                                "Empty: place it and right-click to fill it with a crop (up to 64)"),
    "tooltip.evolvedwarfare.crop_crate.count": ("%s/%s de %s", "%s/%s %s"),
    "tooltip.evolvedwarfare.crop_crate.full": ("Llena: los comerciantes te la compran", "Full: traders will buy it"),
    "tooltip.evolvedwarfare.crop_crate.not_full": ("Faltan %s para llenarla (solo compran cajas llenas)",
                                                   "%s more to fill it (traders only buy full crates)"),
    "tooltip.evolvedwarfare.crop_crate.pickup": ("Agáchate + clic derecho con la mano vacía para llevártela",
                                                 "Sneak + right-click with an empty hand to pick it up"),
    "gui.evolvedwarfare.crop_crate.empty": ("Vacía", "Empty"),
    "gui.evolvedwarfare.crop_crate.full": ("¡Llena!", "Full!"),
    "gui.evolvedwarfare.crop_crate.missing": ("Faltan %s", "%s to go"),
    "gui.evolvedwarfare.trader.crates_only": ("Los cultivos solo los compra en cajas llenas (Caja de cultivos)",
                                              "Crops are only bought in full crates (Crop Crate)"),
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def add_to_tag(path, value):
    with open(path, encoding="utf-8") as f:
        tag = json.load(f, object_pairs_hook=collections.OrderedDict)
    if value not in tag["values"]:
        tag["values"].append(value)
    write_json(path, tag)


def main():
    block = os.path.join(ASSETS, "textures/block")
    os.makedirs(block, exist_ok=True)
    textures = {"side": side(), "inside": inside(), "rim": rim(), "floor": floor(), "bottom": bottom()}
    for name, im in textures.items():
        im.save(os.path.join(block, f"crop_crate_{name}.png"))
    write_json(os.path.join(ASSETS, "models/block/crop_crate.json"), model())
    write_json(os.path.join(ASSETS, "blockstates/crop_crate.json"), {"variants": {"": {"model": "evolvedwarfare:block/crop_crate"}}})
    write_json(os.path.join(ASSETS, "models/item/crop_crate.json"), ITEM_MODEL)
    os.makedirs(os.path.join(ASSETS, "textures/gui/container"), exist_ok=True)
    g = gui()
    g.save(os.path.join(ASSETS, "textures/gui/container/crop_crate.png"))
    write_json(os.path.join(DATA, "evolvedwarfare/recipe/crop_crate.json"), RECIPE)
    write_json(os.path.join(DATA, "evolvedwarfare/loot_table/blocks/crop_crate.json"), LOOT)
    write_json(os.path.join(DATA, "evolvedwarfare/tags/item/crate_crops.json"), {"replace": False, "values": CROPS})
    add_to_tag(os.path.join(DATA, "minecraft/tags/block/mineable/axe.json"), "evolvedwarfare:crop_crate")
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        strip = Image.new("RGBA", (16 * 5 + 8, 16), (0, 0, 0, 0))
        for i, im in enumerate(textures.values()):
            strip.paste(im, (i * 18, 0))
        strip.resize((strip.width * 8, strip.height * 8), Image.NEAREST).save(os.path.join(PREVIEW, "crate_textures.png"))
        g.crop((0, 0, 176, 133)).resize((176 * 3, 133 * 3), Image.NEAREST).save(os.path.join(PREVIEW, "crate_gui.png"))
    print("caja de cultivos: texturas, modelo, pantalla, receta, botín, etiquetas y textos")


if __name__ == "__main__":
    main()
