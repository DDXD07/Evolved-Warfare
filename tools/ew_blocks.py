"""Fase 3: texturas propias de los puestos de trabajo y del ayuntamiento, y todos sus JSON
(blockstates, modelos, modelos de objeto, botín, recetas y tags).

Uso: python3 ew_blocks.py <src/main/resources> <carpeta de vistas previas> [texturas vanilla para la vista previa]

El dibujo del frente de cada puesto es la textura vanilla del objeto de su oficio (azada, hacha...), referenciada
desde el modelo (no se copia): el atlas de bloques de Minecraft también incluye las texturas de objetos.
"""
import json
import os
import random
import sys

from PIL import Image

RES = sys.argv[1]
PREVIEW = sys.argv[2]
VANILLA = sys.argv[3] if len(sys.argv) > 3 else "/home/claude/ref/mcassets/assets/minecraft/textures"
NS = "evolvedwarfare"
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data", NS)

# oficio, icono vanilla, cuerpo (wood = puesto de campo, stone = taller), objeto clave de la receta
PROFESSIONS = [
    ("farmer", "item/iron_hoe", "wood", {"tag": "minecraft:hoes"}),
    ("lumberjack", "item/iron_axe", "wood", {"tag": "minecraft:axes"}),
    ("miner", "item/iron_pickaxe", "wood", {"tag": "minecraft:pickaxes"}),
    ("digger", "item/iron_shovel", "wood", {"tag": "minecraft:shovels"}),
    ("fisherman", "item/fishing_rod", "wood", {"item": "minecraft:fishing_rod"}),
    ("shepherd", "item/shears", "wood", {"item": "minecraft:shears"}),
    ("breeder", "item/egg", "wood", {"item": "minecraft:hay_block"}),
    ("beekeeper", "item/honeycomb", "wood", {"item": "minecraft:honeycomb"}),
    ("gardener", "item/melon_slice", "wood", {"item": "minecraft:bone_meal"}),
    ("blacksmith", "item/iron_ingot", "stone", {"item": "minecraft:blast_furnace"}),
    ("cook", "item/bread", "stone", {"item": "minecraft:smoker"}),
    ("charcoal_burner", "item/charcoal", "stone", {"item": "minecraft:furnace"}),
    ("stonemason", "item/brick", "stone", {"item": "minecraft:stonecutter"}),
    ("fletcher", "item/arrow", "stone", {"item": "minecraft:fletching_table"}),
    # fase 4
    ("courier", "item/saddle", "wood", {"item": "minecraft:leather"}),
    ("merchant", "item/emerald", "wood", {"item": "minecraft:gold_ingot"}),
    # fase 6
    ("builder", "block/scaffolding_side", "wood", {"item": "minecraft:stone_bricks"}),
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


# ----------------------------------------------------------------- madera (puestos de campo)
PLANK = hexc("#A8814E")
PLANK_D = hexc("#7C5A32")
FRAME = hexc("#5A3E22")
FRAME_D = hexc("#3E2914")
IRON = hexc("#9A9A9A")
IRON_D = hexc("#5E5E5E")


def frame_px(x, y, rnd):
    """Marco de 2 píxeles con esquinas de hierro; None si es el interior."""
    edge = min(x, y, 15 - x, 15 - y)
    corner = (x < 3 or x > 12) and (y < 3 or y > 12)
    if corner and edge < 2:
        return IRON_D if (x in (0, 15) or y in (0, 15)) else IRON
    if edge == 0:
        return FRAME_D
    if edge == 1:
        return jit(FRAME, rnd, 5)
    return None


def planks_px(x, y, rnd, vertical=False):
    a, b = (x, y) if not vertical else (y, x)
    seam = (b - 2) % 4 == 3
    c = PLANK_D if seam else jit(PLANK, rnd, 8)
    # vetas
    if not seam and (a * 7 + (b // 4) * 5) % 11 == 0:
        c = shade(c, 0.88)
    return c


def wood_side():
    return tex(lambda x, y, r: frame_px(x, y, r) or planks_px(x, y, r), 11)


def wood_top():
    def p(x, y, r):
        f = frame_px(x, y, r)
        if f:
            return f
        # tablero de trabajo: tablas en vertical con una diagonal de refuerzo
        if abs((x - 2) - (y - 2)) <= 0 and 2 <= x <= 13:
            return jit(FRAME, r, 4)
        return planks_px(x, y, r, vertical=True)
    return tex(p, 12)


def wood_bottom():
    return tex(lambda x, y, r: planks_px(x, y, r) if min(x, y, 15 - x, 15 - y) else FRAME_D, 13)


PANEL = hexc("#3B2A1A")
PANEL_L = hexc("#4E3824")


def wood_front():
    def p(x, y, r):
        f = frame_px(x, y, r)
        if f:
            return f
        # tablero oscuro con bisel donde va el dibujo del oficio
        if x == 2 or y == 2:
            return shade(FRAME_D, 0.9)
        if x == 13 or y == 13:
            return jit(PLANK_D, r, 4)
        return jit(PANEL if (x + y) % 5 else PANEL_L, r, 3)
    return tex(p, 14)


# ----------------------------------------------------------------- piedra (talleres)
STONE = hexc("#8E8E8E")
STONE_D = hexc("#5F5F5F")
STONE_L = hexc("#A8A8A8")
MORTAR = hexc("#6E6A64")
BEAM = hexc("#6B4A2A")


def bricks_px(x, y, rnd):
    row = y // 4
    if y % 4 == 3:
        return jit(MORTAR, rnd, 4)
    off = 4 if row % 2 else 0
    if (x + off) % 8 == 7:
        return jit(MORTAR, rnd, 4)
    c = jit(STONE, rnd, 9)
    if y % 4 == 0:
        c = shade(c, 1.08)
    return c


def stone_side():
    def p(x, y, r):
        if y <= 1:  # viga de madera arriba
            return jit(BEAM if y else shade(BEAM, 0.7), r, 5)
        if y >= 14:
            return jit(STONE_D, r, 5)
        return bricks_px(x, y + 2, r)
    return tex(p, 21)


def stone_top():
    def p(x, y, r):
        edge = min(x, y, 15 - x, 15 - y)
        if edge == 0:
            return jit(BEAM, r, 5)
        if edge == 1:
            return jit(STONE_L, r, 5)
        # plancha de hierro con rejilla
        if 4 <= x <= 11 and 4 <= y <= 11:
            return hexc("#2E2E2E") if (x + y) % 2 == 0 else hexc("#444444")
        return jit(STONE, r, 7)
    return tex(p, 22)


def stone_bottom():
    return tex(lambda x, y, r: jit(STONE_D, r, 6), 23)


def stone_front():
    def p(x, y, r):
        if y <= 1:
            return jit(BEAM if y else shade(BEAM, 0.7), r, 5)
        if y >= 14:
            return jit(STONE_D, r, 5)
        if x <= 1 or x >= 14:
            return bricks_px(x, y + 2, r)
        # boca del taller: hueco oscuro con brasas abajo
        if x == 2 or x == 13 or y == 2:
            return jit(STONE_L, r, 4)
        if y >= 11:
            return hexc("#E07A1F") if (x * 3 + y) % 4 == 0 else (hexc("#9A3A10") if (x + y) % 3 else hexc("#F2B33D"))
        return jit(hexc("#2A2522"), r, 3)
    return tex(p, 24)


# ----------------------------------------------------------------- ayuntamiento
DARK = hexc("#4A3220")
DARK_D = hexc("#2E1E12")
GOLD = hexc("#D9B13B")
GOLD_D = hexc("#9A7420")
RED = hexc("#8E1E22")
RED_D = hexc("#5E1216")


def hall_frame(x, y, r):
    edge = min(x, y, 15 - x, 15 - y)
    if edge == 0:
        return DARK_D
    if edge == 1:
        return GOLD if (x + y) % 2 else GOLD_D
    return None


def hall_side():
    def p(x, y, r):
        f = hall_frame(x, y, r)
        if f:
            return f
        # estandarte rojo con franja dorada en el centro
        if 6 <= x <= 9:
            if y == 9 or y == 10:
                return GOLD
            if y >= 12 and (x + y) % 2 == 0:
                return DARK
            return jit(RED if x in (7, 8) else RED_D, r, 4)
        seam = (y - 2) % 4 == 3
        return DARK_D if seam else jit(DARK, r, 6)
    return tex(p, 31)


def hall_top():
    def p(x, y, r):
        f = hall_frame(x, y, r)
        if f:
            return f
        if 5 <= x <= 10 and 5 <= y <= 10 and (x in (5, 10) or y in (5, 10)):
            return GOLD_D
        seam = (x - 2) % 4 == 3
        return DARK_D if seam else jit(DARK, r, 6)
    return tex(p, 32)


def hall_front():
    def p(x, y, r):
        f = hall_frame(x, y, r)
        if f:
            return f
        if x == 2 or y == 2:
            return DARK_D
        return jit(hexc("#2A1A10") if (x + y) % 6 else hexc("#3A2616"), r, 3)
    return tex(p, 33)


# ----------------------------------------------------------------- almacén (fase 4)
CRATE = hexc("#B08A55")
CRATE_D = hexc("#82612F")
STRAP = hexc("#5A3E22")


def crate_px(x, y, rnd):
    """Dos cajas apiladas, con tablas en cruz."""
    ly = y % 7
    if ly == 0 or x in (2, 13):
        return jit(STRAP, rnd, 4)
    cx, cy = x - 2, ly
    if cx == cy * 11 // 6 or cx == 11 - cy * 11 // 6:
        return jit(CRATE_D, rnd, 5)
    return jit(CRATE, rnd, 7)


def warehouse_side():
    return tex(lambda x, y, r: frame_px(x, y, r) or crate_px(x, y - 1, r), 41)


def warehouse_top():
    def p(x, y, r):
        f = frame_px(x, y, r)
        if f:
            return f
        # tapa de la caja con una argolla
        if 6 <= x <= 9 and 6 <= y <= 9 and (x in (6, 9) or y in (6, 9)):
            return IRON_D
        return planks_px(x, y, r)
    return tex(p, 42)


def warehouse_front():
    def p(x, y, r):
        f = frame_px(x, y, r)
        if f:
            return f
        # estantería oscura con un estante en medio (el dibujo del saco va encima)
        if y in (8, 13):
            return jit(PLANK_D, r, 4)
        if x == 2 or y == 2:
            return shade(FRAME_D, 0.9)
        return jit(PANEL if (x * 3 + y) % 7 else PANEL_L, r, 3)
    return tex(p, 43)


def route_slip():
    """Hoja de ruta: papel con un camino punteado entre paradas rojas y un sello."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rnd = random.Random(44)
    paper = hexc("#E9E1C8")
    edge = hexc("#B9AE8E")
    for y in range(1, 15):
        for x in range(2, 14):
            c = jit(paper, rnd, 5)
            if x in (2, 13) or y in (1, 14):
                c = edge
            im.putpixel((x, y), c)
    # esquina doblada
    for (x, y) in ((12, 1), (13, 1), (13, 2)):
        im.putpixel((x, y), (0, 0, 0, 0))
    im.putpixel((12, 2), edge)
    # camino punteado
    path = [(4, 3), (5, 4), (6, 4), (7, 5), (8, 6), (9, 6), (10, 7), (10, 8), (9, 9), (8, 9), (7, 10), (6, 10), (5, 11),
            (6, 12), (7, 12), (8, 12), (9, 12), (10, 12)]
    for i, (x, y) in enumerate(path):
        if i % 2 == 0:
            im.putpixel((x, y), hexc("#6B5434"))
    for (x, y) in ((4, 3), (10, 7), (5, 11), (11, 12)):
        im.putpixel((x, y), hexc("#B3262B"))
        im.putpixel((x + 1, y), hexc("#7E1519")) if x < 12 else None
    return im


# ----------------------------------------------------------------- JSON
def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def template():
    face = lambda t, cull: {"uv": [0, 0, 16, 16], "texture": t, "cullface": cull}
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#side"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                "down": face("#bottom", "down"), "up": face("#top", "up"),
                "north": face("#front", "north"), "south": face("#side", "south"),
                "west": face("#side", "west"), "east": face("#side", "east")}},
            # el dibujo del oficio, encima del frente (como la capa de hierba del bloque de césped)
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"north": face("#icon", "north")}},
        ],
    }


def block_files(name, body, icon):
    write_json(f"{ASSETS}/blockstates/{name}.json", {"variants": {
        f"facing={d}": ({"model": f"{NS}:block/{name}", "y": rot} if rot else {"model": f"{NS}:block/{name}"})
        for d, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    write_json(f"{ASSETS}/models/block/{name}.json", {
        "parent": f"{NS}:block/work_site_template",
        "textures": {
            "side": f"{NS}:block/{body}_side", "top": f"{NS}:block/{body}_top",
            "bottom": f"{NS}:block/{body}_bottom", "front": f"{NS}:block/{body}_front",
            "icon": f"minecraft:{icon}"}})
    write_json(f"{ASSETS}/models/item/{name}.json", {"parent": f"{NS}:block/{name}"})
    write_json(f"{DATA}/loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"{NS}:blocks/{name}"})


def recipe(name, key_item):
    write_json(f"{DATA}/recipe/{name}.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PPP", "PTP", "PCP"],
        "key": {"P": {"tag": "minecraft:planks"}, "T": key_item, "C": {"item": "minecraft:chest"}},
        "result": {"id": f"{NS}:{name}", "count": 1}})


# ----------------------------------------------------------------- vista previa isométrica
def iso_block(top, left, right, icon=None, scale=6):
    """Cubo isométrico a partir de tres caras de 16x16 (la de la izquierda es el frente)."""
    import numpy as np
    if icon is not None:
        left = left.copy()
        left.alpha_composite(icon)
    S = 16 * scale
    W, H = 2 * S, 2 * S
    out = np.zeros((H, W, 4), dtype=np.float32)
    faces = {
        "top": (np.asarray(top.convert("RGBA")).astype(np.float32), 1.0),
        "left": (np.asarray(left.convert("RGBA")).astype(np.float32), 0.80),
        "right": (np.asarray(right.convert("RGBA")).astype(np.float32), 0.64),
    }
    for Y in range(H):
        for X in range(W):
            for name in ("top", "left", "right"):
                if name == "top":
                    u, v = (2 * Y + X - S) / 2, (2 * Y - X + S) / 2
                elif name == "left":
                    u, v = X, Y - S / 2 - X / 2
                else:
                    u, v = X - S, Y - S + (X - S) / 2
                if 0 <= u < S and 0 <= v < S:
                    a, dim = faces[name]
                    px = a[int(v / scale), int(u / scale)]
                    out[Y, X] = px * np.array([dim, dim, dim, 1.0])
                    break
    return Image.fromarray(out.astype(np.uint8))


if __name__ == "__main__":
    tdir = f"{ASSETS}/textures/block"
    os.makedirs(tdir, exist_ok=True)
    os.makedirs(PREVIEW, exist_ok=True)
    bodies = {
        "work_site": {"side": wood_side(), "top": wood_top(), "bottom": wood_bottom(), "front": wood_front()},
        "workshop": {"side": stone_side(), "top": stone_top(), "bottom": stone_bottom(), "front": stone_front()},
        "town_hall": {"side": hall_side(), "top": hall_top(), "bottom": wood_bottom(), "front": hall_front()},
        "warehouse": {"side": warehouse_side(), "top": warehouse_top(), "bottom": wood_bottom(), "front": warehouse_front()},
    }
    for body, faces in bodies.items():
        for face, im in faces.items():
            if body in ("town_hall", "warehouse") and face == "bottom":
                continue
            im.save(f"{tdir}/{body}_{face}.png")

    write_json(f"{ASSETS}/models/block/work_site_template.json", template())
    for prof, icon, kind, key in PROFESSIONS:
        name = f"{prof}_post"
        block_files(name, "work_site" if kind == "wood" else "workshop", icon)
        recipe(name, key)
    # ayuntamiento (cara de abajo: la de los puestos de madera)
    block_files("town_hall", "town_hall", "item/bell")
    with open(f"{ASSETS}/models/block/town_hall.json", encoding="utf-8") as f:
        m = json.load(f)
    m["textures"]["bottom"] = f"{NS}:block/work_site_bottom"
    write_json(f"{ASSETS}/models/block/town_hall.json", m)
    write_json(f"{DATA}/recipe/town_hall.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["GBG", "PCP", "PPP"],
        "key": {"G": {"item": "minecraft:gold_ingot"}, "B": {"item": "minecraft:book"},
                "P": {"tag": "minecraft:planks"}, "C": {"item": "minecraft:chest"}},
        "result": {"id": f"{NS}:town_hall", "count": 1}})

    # almacén (fase 4): mismo modelo con el dibujo de un saco
    block_files("warehouse", "warehouse", "item/bundle")
    with open(f"{ASSETS}/models/block/warehouse.json", encoding="utf-8") as f:
        m = json.load(f)
    m["textures"]["bottom"] = f"{NS}:block/work_site_bottom"
    write_json(f"{ASSETS}/models/block/warehouse.json", m)
    write_json(f"{DATA}/recipe/warehouse.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["PBP", "CPC", "PPP"],
        "key": {"B": {"item": "minecraft:book"}, "P": {"tag": "minecraft:planks"}, "C": {"item": "minecraft:chest"}},
        "result": {"id": f"{NS}:warehouse", "count": 1}})

    # hoja de ruta (objeto)
    idir = f"{ASSETS}/textures/item"
    os.makedirs(idir, exist_ok=True)
    route_slip().save(f"{idir}/route_slip.png")
    write_json(f"{ASSETS}/models/item/route_slip.json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/route_slip"}})
    write_json(f"{DATA}/recipe/route_slip.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:paper"}, {"item": "minecraft:ink_sac"}, {"item": "minecraft:feather"}],
        "result": {"id": f"{NS}:route_slip", "count": 1}})

    # se rompen más rápido con hacha (madera) o pico (talleres)
    axe = [f"{NS}:{p}_post" for p, _, k, _ in PROFESSIONS if k == "wood"] + [f"{NS}:town_hall", f"{NS}:warehouse",
                                                                                 f"{NS}:architect_table",
                                                                                 f"{NS}:drawbridge", f"{NS}:gate_winch"]
    pick = [f"{NS}:{p}_post" for p, _, k, _ in PROFESSIONS if k == "stone"] + [f"{NS}:portcullis"]
    write_json(f"{RES}/data/minecraft/tags/block/mineable/axe.json", {"replace": False, "values": axe})
    write_json(f"{RES}/data/minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": pick})

    # vista previa: todos los bloques
    tiles = []
    for prof, icon, kind, _ in PROFESSIONS + [("town_hall", "item/bell", "hall", None), ("warehouse", "item/bundle", "store", None)]:
        body = {"wood": "work_site", "stone": "workshop", "hall": "town_hall", "store": "warehouse"}[kind]
        f = bodies[body]
        ic = Image.open(f"{VANILLA}/{icon}.png").convert("RGBA").crop((0, 0, 16, 16))
        tiles.append((prof, iso_block(f["top"], f["front"], f["side"], ic, 5)))
    cols = 6
    tw, th = tiles[0][1].size
    sheet = Image.new("RGBA", (cols * (tw + 10) + 10, ((len(tiles) + cols - 1) // cols) * (th + 10) + 10), (236, 232, 222, 255))
    for i, (_, t) in enumerate(tiles):
        sheet.alpha_composite(t, (10 + (i % cols) * (tw + 10), 10 + (i // cols) * (th + 10)))
    sheet.convert("RGB").save(f"{PREVIEW}/puestos.png")
    route_slip().resize((128, 128), Image.NEAREST).save(f"{PREVIEW}/hoja_de_ruta.png")
    print("ok", len(tiles), "bloques")
