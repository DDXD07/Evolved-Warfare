"""Fase 7: asedio. Iconos de las máquinas y su munición, texturas del rastrillo, el puente levadizo y el torno, y sus
JSON (modelos, estados, botín, recetas y etiquetas).

Uso: python3 ew_siege.py <src/main/resources> <carpeta de vistas previas>

Las máquinas en el mundo no llevan textura propia: se dibujan con bloques del juego (ver SiegeEngineRenderer).
"""
import json
import os
import random
import sys

from PIL import Image

RES = sys.argv[1]
PREVIEW = sys.argv[2]
NS = "evolvedwarfare"
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data", NS)
T = (0, 0, 0, 0)


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def jit(c, rnd, amt=7):
    n = rnd.randint(-amt, amt)
    return tuple(max(0, min(255, v + n)) for v in c[:3]) + (c[3],)


WOOD = hexc("#A07443")
WOOD_D = hexc("#6B4A2A")
WOOD_L = hexc("#C49A5E")
DARK = hexc("#4A3220")
IRON = hexc("#B8B8B8")
IRON_D = hexc("#6E6E6E")
IRON_L = hexc("#E2E2E2")
ROPE = hexc("#C9A86A")
STONE = hexc("#8A8A8A")
STONE_D = hexc("#5A5A5A")


class Canvas:
    def __init__(self, seed=1):
        self.im = Image.new("RGBA", (16, 16), T)
        self.rnd = random.Random(seed)

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.im.putpixel((x, y), c)

    def rect(self, x0, y0, x1, y1, c, edge=None, noise=0):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                col = c
                if edge is not None and (x in (x0, x1) or y in (y0, y1)):
                    col = edge
                self.px(x, y, jit(col, self.rnd, noise) if noise else col)

    def line(self, pts, c, edge=None):
        for i, (x, y) in enumerate(pts):
            self.px(x, y, c)
            if edge is not None:
                self.px(x, y + 1, edge)


def bresenham(x0, y0, x1, y1):
    pts = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        pts.append((x0, y0))
        if x0 == x1 and y0 == y1:
            return pts
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def wheel(c, cx, cy):
    """Rueda de 5x5 con buje."""
    for (x, y) in ((-1, -2), (0, -2), (1, -2), (-2, -1), (2, -1), (-2, 0), (2, 0), (-2, 1), (2, 1), (-1, 2), (0, 2), (1, 2)):
        c.px(cx + x, cy + y, DARK)
    for (x, y) in ((-1, -1), (1, -1), (-1, 1), (1, 1), (0, -1), (0, 1), (-1, 0), (1, 0)):
        c.px(cx + x, cy + y, WOOD_D)
    c.px(cx, cy, IRON)


# ----------------------------------------------------------------- iconos
def icon_catapult():
    c = Canvas(71)
    c.rect(1, 11, 14, 12, WOOD, edge=WOOD_D)
    wheel(c, 4, 13)
    wheel(c, 11, 13)
    # tope
    c.rect(10, 5, 11, 10, WOOD_D)
    c.rect(9, 5, 12, 5, DARK)
    # brazo inclinado hacia atrás y cazoleta con piedra
    for (x, y) in bresenham(9, 10, 3, 3):
        c.px(x, y, WOOD_L)
        c.px(x + 1, y, WOOD)
    c.rect(1, 1, 4, 2, WOOD_D)
    c.rect(2, 0, 3, 0, STONE)
    c.px(1, 0, STONE_D)
    c.px(4, 0, STONE_D)
    # madeja
    c.rect(7, 9, 9, 10, ROPE)
    return c.im


def icon_ballista():
    c = Canvas(72)
    # patas
    for (x, y) in bresenham(7, 9, 3, 15):
        c.px(x, y, WOOD_D)
    for (x, y) in bresenham(8, 9, 12, 15):
        c.px(x, y, WOOD_D)
    c.rect(7, 8, 8, 12, WOOD)
    # arco
    bow = [(1, 4), (2, 3), (3, 3), (4, 3), (5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4), (11, 3), (12, 3), (13, 3), (14, 4)]
    for (x, y) in bow:
        c.px(x, y, WOOD_D)
        c.px(x, y + 1, DARK)
    c.px(1, 5, IRON)
    c.px(14, 5, IRON)
    # cuerda tensa
    for (x, y) in bresenham(1, 6, 7, 9) + bresenham(14, 6, 8, 9):
        c.px(x, y, hexc("#EDEDED"))
    # caja y virote
    c.rect(6, 5, 9, 8, WOOD, edge=WOOD_D)
    for y in range(0, 9):
        c.px(7, y, WOOD_L)
    c.px(7, 0, IRON_L)
    c.px(8, 0, IRON)
    c.px(6, 1, IRON)
    return c.im


def icon_trebuchet():
    c = Canvas(73)
    c.rect(0, 13, 15, 14, WOOD, edge=WOOD_D)
    wheel(c, 3, 14)
    wheel(c, 12, 14)
    # caballete en A
    for (x, y) in bresenham(3, 12, 7, 4) + bresenham(11, 12, 8, 4):
        c.px(x, y, WOOD_D)
    c.px(7, 4, IRON)
    c.px(8, 4, IRON)
    # brazo largo hacia arriba y contrapeso abajo
    for (x, y) in bresenham(8, 4, 14, 0):
        c.px(x, y, WOOD_L)
    for (x, y) in bresenham(7, 4, 4, 6):
        c.px(x, y, WOOD_L)
    c.rect(2, 7, 5, 10, STONE, edge=STONE_D, noise=6)
    c.px(4, 6, IRON_D)
    c.px(15, 0, STONE)
    return c.im


def icon_ram():
    c = Canvas(74)
    # tejado
    for x in range(1, 15):
        top = 2 + abs(x - 7) // 2
        for y in range(top, 7):
            c.px(x, y, jit(hexc("#5A3E24"), c.rnd, 6) if y > top else DARK)
    # postes y bastidor
    c.rect(2, 7, 3, 11, WOOD_D)
    c.rect(12, 7, 13, 11, WOOD_D)
    c.rect(1, 11, 14, 12, WOOD, edge=WOOD_D)
    wheel(c, 4, 13)
    wheel(c, 11, 13)
    # el tronco y la cabeza de hierro asomando
    c.rect(4, 8, 13, 9, WOOD_L, edge=WOOD)
    c.rect(13, 7, 15, 10, IRON, edge=IRON_D)
    c.px(14, 8, IRON_L)
    return c.im


def icon_bolt():
    c = Canvas(75)
    for (x, y) in bresenham(3, 12, 12, 3):
        c.px(x, y, WOOD_L)
        c.px(x + 1, y + 1, WOOD_D)
    for (x, y) in ((12, 3), (13, 2), (14, 1), (13, 3), (12, 2), (14, 2), (13, 1)):
        c.px(x, y, IRON)
    c.px(15, 0, IRON_L)
    for (x, y) in ((1, 12), (2, 13), (3, 14), (2, 11), (3, 12), (4, 13), (1, 14), (2, 14)):
        c.px(x, y, hexc("#EDEDED") if (x + y) % 2 else hexc("#C8C8C8"))
    return c.im


def icon_fire_pot():
    c = Canvas(76)
    clay, clay_d, clay_l = hexc("#B5562E"), hexc("#7A3418"), hexc("#D7794A")
    rows = {6: (5, 10), 7: (4, 11), 8: (3, 12), 9: (3, 12), 10: (3, 12), 11: (4, 11), 12: (4, 11), 13: (5, 10), 14: (6, 9)}
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            col = clay_d if x in (x0, x1) or y == 14 else (clay_l if x == x0 + 1 and y < 11 else clay)
            c.px(x, y, jit(col, c.rnd, 4))
    c.rect(6, 4, 9, 5, clay_d)
    # cuerda y llama
    for x in range(3, 13):
        c.px(x, 9, ROPE if x % 2 else shade(ROPE, 0.8))
    for (x, y, col) in ((7, 3, "#FFB030"), (8, 3, "#FFB030"), (7, 2, "#FF7A20"), (8, 1, "#FF7A20"), (6, 2, "#FFD060"),
                        (9, 2, "#FFD060"), (7, 0, "#FFE080")):
        c.px(x, y, hexc(col))
    return c.im


# ----------------------------------------------------------------- bloques
def portcullis():
    c = Canvas(81)
    for y in range(16):
        for x in range(16):
            bar_v = x % 5 in (1, 2)
            bar_h = y % 5 in (1, 2)
            if bar_v or bar_h:
                col = IRON if (bar_v and x % 5 == 1) or (bar_h and y % 5 == 1) else IRON_D
                if bar_v and bar_h:
                    col = IRON_L if (x + y) % 2 else IRON
                c.px(x, y, jit(col, c.rnd, 5))
    # puntas abajo
    for x in (1, 6, 11):
        c.px(x, 15, IRON_D)
        c.px(x + 1, 15, IRON_D)
    return c.im


def portcullis_edge():
    c = Canvas(82)
    c.rect(0, 0, 15, 15, IRON_D, noise=5)
    for y in range(0, 16, 5):
        c.px(7, y, IRON_L)
    return c.im


def drawbridge():
    c = Canvas(83)
    planks = hexc("#6E4A2A")
    for y in range(16):
        for x in range(16):
            seam = y % 4 == 3
            col = shade(planks, 0.7) if seam else jit(planks, c.rnd, 8)
            if not seam and (x * 5 + (y // 4) * 7) % 13 == 0:
                col = shade(col, 0.85)
            c.px(x, y, col)
    for x in (3, 4, 11, 12):
        for y in range(16):
            c.px(x, y, jit(hexc("#3C3C3C") if x in (3, 11) else hexc("#555555"), c.rnd, 4))
    for (x, y) in ((3, 1), (11, 1), (3, 6), (11, 6), (3, 9), (11, 9), (3, 14), (11, 14)):
        c.px(x + 1, y, IRON_L)
    return c.im


def winch_side():
    c = Canvas(84)
    c.rect(0, 0, 15, 15, WOOD, noise=7)
    for i in range(16):
        c.px(i, 0, DARK)
        c.px(i, 15, DARK)
        c.px(0, i, DARK)
        c.px(15, i, DARK)
    # rueda dentada
    for (x, y) in bresenham(8, 3, 8, 12) + bresenham(3, 8, 12, 8) + bresenham(4, 4, 11, 11) + bresenham(11, 4, 4, 11):
        c.px(x, y, WOOD_D)
    for (x, y) in ((8, 2), (8, 13), (2, 8), (13, 8), (3, 3), (12, 12), (12, 3), (3, 12)):
        c.px(x, y, IRON_D)
    c.rect(7, 7, 8, 8, IRON)
    return c.im


def winch_front():
    c = Canvas(85)
    c.rect(0, 0, 15, 15, WOOD, noise=7)
    for i in range(16):
        c.px(i, 0, DARK)
        c.px(i, 15, DARK)
        c.px(0, i, DARK)
        c.px(15, i, DARK)
    # tambor con la cadena enrollada
    c.rect(2, 5, 13, 10, WOOD_D, edge=DARK)
    for x in range(3, 13):
        for y in range(6, 10):
            if (x + y) % 3 == 0:
                c.px(x, y, IRON_D)
            elif (x + y) % 3 == 1:
                c.px(x, y, IRON)
    # manivela
    c.rect(1, 2, 2, 7, IRON_D)
    c.rect(1, 2, 4, 3, IRON_D)
    c.px(4, 2, WOOD_L)
    return c.im


def winch_top():
    c = Canvas(86)
    for y in range(16):
        for x in range(16):
            seam = x % 5 == 4
            c.px(x, y, shade(WOOD, 0.75) if seam else jit(WOOD, c.rnd, 7))
    for i in range(16):
        c.px(i, 0, DARK)
        c.px(i, 15, DARK)
        c.px(0, i, DARK)
        c.px(15, i, DARK)
    return c.im


# ----------------------------------------------------------------- JSON
def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def block_loot(name):
    write_json(f"{DATA}/loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"{NS}:blocks/{name}"})


def shaped(name, pattern, key, count=1, category="misc"):
    keys = {}
    for k, v in key.items():
        keys[k] = {"tag": v[1:]} if v.startswith("#") else {"item": v}
    write_json(f"{DATA}/recipe/{name}.json", {
        "type": "minecraft:crafting_shaped", "category": category, "pattern": pattern, "key": keys,
        "result": {"id": f"{NS}:{name}", "count": count}})


def up(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def merge_tag(path, values):
    """Añade valores a una etiqueta que ya escribe otro generador (sin quitar los suyos)."""
    data = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    for v in values:
        if v not in data["values"]:
            data["values"].append(v)
    write_json(path, data)


if __name__ == "__main__":
    idir = f"{ASSETS}/textures/item"
    bdir = f"{ASSETS}/textures/block"
    os.makedirs(idir, exist_ok=True)
    os.makedirs(bdir, exist_ok=True)
    os.makedirs(PREVIEW, exist_ok=True)
    icons = {"catapult": icon_catapult(), "ballista": icon_ballista(), "trebuchet": icon_trebuchet(),
             "battering_ram": icon_ram(), "ballista_bolt": icon_bolt(), "fire_pot": icon_fire_pot()}
    for name, im in icons.items():
        im.save(f"{idir}/{name}.png")
        write_json(f"{ASSETS}/models/item/{name}.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})

    # rastrillo: reja fina con transparencias (capa cutout en el cliente)
    portcullis().save(f"{bdir}/portcullis.png")
    portcullis_edge().save(f"{bdir}/portcullis_edge.png")

    def face(tex, uv, cull=None):
        f = {"uv": uv, "texture": tex}
        if cull:
            f["cullface"] = cull
        return f
    write_json(f"{ASSETS}/models/block/portcullis.json", {
        "parent": "minecraft:block/block", "ambientocclusion": False,
        "textures": {"particle": f"{NS}:block/portcullis_edge", "grate": f"{NS}:block/portcullis",
                     "edge": f"{NS}:block/portcullis_edge"},
        "elements": [{"from": [0, 0, 6], "to": [16, 16, 10], "faces": {
            "north": face("#grate", [0, 0, 16, 16]), "south": face("#grate", [16, 0, 0, 16]),
            "east": face("#edge", [6, 0, 10, 16], "east"), "west": face("#edge", [6, 0, 10, 16], "west"),
            "up": face("#edge", [0, 6, 16, 10], "up"), "down": face("#edge", [0, 6, 16, 10], "down")}}]})
    write_json(f"{ASSETS}/blockstates/portcullis.json", {"variants": {
        "axis=x": {"model": f"{NS}:block/portcullis"},
        "axis=z": {"model": f"{NS}:block/portcullis", "y": 90}}})
    write_json(f"{ASSETS}/models/item/portcullis.json", {"parent": f"{NS}:block/portcullis"})

    # puente levadizo
    drawbridge().save(f"{bdir}/drawbridge.png")
    write_json(f"{ASSETS}/models/block/drawbridge.json", {"parent": "minecraft:block/cube_all",
                                                          "textures": {"all": f"{NS}:block/drawbridge"}})
    write_json(f"{ASSETS}/blockstates/drawbridge.json", {"variants": {"": {"model": f"{NS}:block/drawbridge"}}})
    write_json(f"{ASSETS}/models/item/drawbridge.json", {"parent": f"{NS}:block/drawbridge"})

    # torno: el tambor mira hacia quien lo pone (el torno 'mira' hacia donde baja el puente)
    winch_front().save(f"{bdir}/gate_winch_front.png")
    winch_side().save(f"{bdir}/gate_winch_side.png")
    winch_top().save(f"{bdir}/gate_winch_top.png")
    write_json(f"{ASSETS}/models/block/gate_winch.json", {"parent": "minecraft:block/orientable", "textures": {
        "top": f"{NS}:block/gate_winch_top", "front": f"{NS}:block/gate_winch_front", "side": f"{NS}:block/gate_winch_side"}})
    variants = {}
    for facing, rot in (("north", 180), ("east", 270), ("south", 0), ("west", 90)):
        for powered in ("false", "true"):
            v = {"model": f"{NS}:block/gate_winch"}
            if rot:
                v["y"] = rot
            variants[f"facing={facing},powered={powered}"] = v
    write_json(f"{ASSETS}/blockstates/gate_winch.json", {"variants": variants})
    write_json(f"{ASSETS}/models/item/gate_winch.json", {"parent": f"{NS}:block/gate_winch"})

    for name in ("portcullis", "drawbridge", "gate_winch"):
        block_loot(name)

    # recetas
    shaped("catapult", ["  L", "SLS", "PIP"], {"L": "#minecraft:logs", "S": "minecraft:string", "P": "#minecraft:planks",
                                               "I": "minecraft:iron_ingot"})
    shaped("ballista", ["ICI", "SLS", "P P"], {"I": "minecraft:iron_ingot", "C": "minecraft:crossbow", "S": "minecraft:string",
                                               "L": "#minecraft:logs", "P": "#minecraft:planks"})
    shaped("trebuchet", ["LSL", "LCL", "IBI"], {"L": "#minecraft:logs", "S": "minecraft:string", "C": f"{NS}:catapult",
                                                "I": "minecraft:iron_ingot", "B": "minecraft:iron_block"})
    shaped("battering_ram", ["PPP", "LLB", "P P"], {"P": "#minecraft:planks", "L": "#minecraft:logs",
                                                    "B": "minecraft:iron_block"})
    shaped("ballista_bolt", ["I", "S", "F"], {"I": "minecraft:iron_ingot", "S": "minecraft:stick", "F": "minecraft:feather"},
           count=4, category="equipment")
    shaped("fire_pot", [" S ", "BFB", " B "], {"S": "minecraft:string", "B": "minecraft:brick", "F": "minecraft:fire_charge"},
           count=2, category="equipment")
    shaped("portcullis", ["BIB", "IBI", "BIB"], {"B": "minecraft:iron_bars", "I": "minecraft:iron_ingot"}, count=8)
    shaped("drawbridge", ["PPP", "NNN", "PPP"], {"P": "#minecraft:planks", "N": "minecraft:iron_nugget"}, count=6)
    shaped("gate_winch", ["PCP", "LIL", "PPP"], {"P": "#minecraft:planks", "C": "minecraft:chain", "L": "#minecraft:logs",
                                                 "I": "minecraft:iron_ingot"})

    # etiquetas: piedras que valen de munición, y con qué herramienta se rompe cada bloque
    write_json(f"{DATA}/tags/item/siege_stones.json", {"values": [
        "minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:stone", "minecraft:blackstone",
        "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff", "minecraft:deepslate",
        "minecraft:mossy_cobblestone", "minecraft:sandstone"]})
    merge_tag(f"{RES}/data/minecraft/tags/block/mineable/pickaxe.json", [f"{NS}:portcullis"])
    merge_tag(f"{RES}/data/minecraft/tags/block/mineable/axe.json", [f"{NS}:drawbridge", f"{NS}:gate_winch"])

    # vista previa
    tiles = list(icons.values()) + [portcullis(), drawbridge(), winch_front(), winch_side()]
    sheet = Image.new("RGBA", (len(tiles) * (16 * 6 + 8) + 8, 16 * 6 + 16), (236, 232, 222, 255))
    for i, im in enumerate(tiles):
        sheet.alpha_composite(up(im, 6), (8 + i * (16 * 6 + 8), 8))
    sheet.convert("RGB").save(f"{PREVIEW}/asedio_iconos_x6.png")
    print("ok")
