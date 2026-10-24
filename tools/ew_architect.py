"""Fase 6: la mesa del arquitecto, el escáner de estructuras y el plano (texturas, modelos, recetas y botín).

Uso: python3 ew_architect.py <src/main/resources> <carpeta de vistas previas>
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


PLANK = hexc("#B08A58")
PLANK_D = hexc("#7C5A32")
FRAME = hexc("#5A3E22")
FRAME_D = hexc("#3E2914")
BRASS = hexc("#D9B13B")
BRASS_D = hexc("#9A7420")
PAPER = hexc("#2F5D9E")       # papel de plano (azul)
PAPER_L = hexc("#4D7BBE")
LINE = hexc("#E8F0FA")


def planks(x, y, r, vertical=False):
    a, b = (x, y) if not vertical else (y, x)
    seam = (b - 1) % 5 == 4
    c = PLANK_D if seam else jit(PLANK, r, 8)
    if not seam and (a * 7 + (b // 5) * 5) % 11 == 0:
        c = shade(c, 0.88)
    return c


# dibujo del plano sobre la mesa: una casita con tejado a dos aguas
HOUSE = set()
for x in range(5, 11):
    HOUSE.add((x, 11))
for y in range(7, 12):
    HOUSE.add((5, y))
    HOUSE.add((10, y))
for i in range(4):
    HOUSE.add((4 + i, 7 - i))
    HOUSE.add((11 - i, 7 - i))
for y in (9, 10, 11):
    HOUSE.add((7, y))
    HOUSE.add((8, y))
HOUSE.discard((7, 11))
HOUSE.discard((8, 11))


def table_top():
    def p(x, y, r):
        if min(x, y, 15 - x, 15 - y) == 0:
            return FRAME_D
        if min(x, y, 15 - x, 15 - y) == 1:
            return jit(FRAME, r, 4)
        # hoja azul ligeramente girada: deja ver la madera en dos esquinas
        if (x <= 3 and y >= 13) or (x >= 13 and y <= 2):
            return planks(x, y, r, vertical=True)
        if (x, y) in HOUSE:
            return LINE
        if x % 4 == 0 or y % 4 == 0:
            return jit(PAPER_L, r, 3)
        return jit(PAPER, r, 4)
    im = tex(p, 61)
    # lápiz en la esquina
    for i, (x, y) in enumerate(((12, 14), (13, 13), (14, 12))):
        im.putpixel((x, y), hexc("#E8C040") if i else hexc("#3A3A3A"))
    return im


def table_side():
    def p(x, y, r):
        if y <= 2:
            return FRAME_D if y == 0 else jit(FRAME, r, 4)       # canto del tablero
        if x in (0, 1, 14, 15):
            return FRAME_D if x in (0, 15) else jit(FRAME, r, 4)  # patas
        if y == 15:
            return FRAME_D
        if y in (8, 9):
            return jit(FRAME, r, 3)                              # travesaño
        return planks(x, y, r)
    return tex(p, 62)


def table_front():
    side = table_side()

    def p(x, y, r):
        c = side.getpixel((x, y))
        # dos cajones con tirador de latón
        for (x0, x1) in ((3, 7), (8, 12)):
            if x0 <= x <= x1 and 3 <= y <= 7:
                if x in (x0, x1) or y in (3, 7):
                    return FRAME_D
                if (x, y) == ((x0 + x1) // 2, 5):
                    return BRASS
                return jit(PLANK, r, 5)
        # rollos de planos en el estante de abajo
        if 10 <= y <= 13 and 3 <= x <= 12:
            k = (x - 3) // 3
            if (x - 3) % 3 == 2:
                return c
            return [hexc("#E9E1C8"), PAPER_L, hexc("#E9E1C8")][k % 3] if y != 13 else shade(PAPER, 0.8)
        return c
    return tex(p, 63)


def table_bottom():
    return tex(lambda x, y, r: planks(x, y, r) if min(x, y, 15 - x, 15 - y) else FRAME_D, 64)


def scanner():
    """Escáner: un marco de latón con una lente cian y una antena."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    body, body_d, body_l = hexc("#6B4A2A"), hexc("#3E2914"), hexc("#8A6A44")
    lens, lens_l, lens_d = hexc("#3FD0E0"), hexc("#B8F4FA"), hexc("#1E8090")
    # mango
    for y in range(10, 15):
        for x in (7, 8):
            im.putpixel((x, y), body if x == 7 else body_d)
    im.putpixel((7, 15), body_d)
    im.putpixel((8, 15), body_d)
    # cuerpo
    for y in range(3, 11):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (3, 10)
            im.putpixel((x, y), BRASS_D if edge else (BRASS if (x + y) % 5 else hexc("#E8C860")))
    # pantalla
    for y in range(4, 9):
        for x in range(4, 12):
            c = lens if (x + y) % 3 else lens_d
            if (x, y) in ((5, 5), (6, 5), (5, 6)):
                c = lens_l
            im.putpixel((x, y), c)
    # cruz de mira
    for x in range(6, 10):
        im.putpixel((x, 6), lens_l if x != 8 else lens_d)
    im.putpixel((8, 5), lens_l)
    im.putpixel((8, 7), lens_l)
    # botón y antena
    im.putpixel((10, 9), hexc("#C03030"))
    for i, (x, y) in enumerate(((11, 2), (12, 1), (13, 0))):
        im.putpixel((x, y), body_l if i < 2 else hexc("#FF5050"))
    return im


def blueprint():
    """Plano: una hoja azul con la casita en blanco y la esquina enrollada."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rnd = random.Random(65)
    edge = hexc("#1E3E6E")
    for y in range(1, 15):
        for x in range(1, 15):
            c = jit(PAPER, rnd, 4)
            if x % 4 == 1 or y % 4 == 1:
                c = jit(PAPER_L, rnd, 3)
            if x in (1, 14) or y in (1, 14):
                c = edge
            im.putpixel((x, y), c)
    for (x, y) in HOUSE:
        if 1 < x < 14 and 1 < y < 14:
            im.putpixel((x, y), LINE)
    # rollo arriba a la derecha
    for (x, y) in ((12, 1), (13, 1), (14, 1), (13, 2), (14, 2), (14, 3)):
        im.putpixel((x, y), (0, 0, 0, 0))
    for (x, y) in ((12, 2), (13, 3), (12, 3)):
        im.putpixel((x, y), hexc("#E9E1C8"))
    im.putpixel((14, 4), edge)
    im.putpixel((11, 1), edge)
    return im


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def up(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


if __name__ == "__main__":
    bdir = f"{ASSETS}/textures/block"
    idir = f"{ASSETS}/textures/item"
    os.makedirs(bdir, exist_ok=True)
    os.makedirs(idir, exist_ok=True)
    os.makedirs(PREVIEW, exist_ok=True)
    faces = {"top": table_top(), "side": table_side(), "front": table_front(), "bottom": table_bottom()}
    for face, im in faces.items():
        im.save(f"{bdir}/architect_table_{face}.png")

    # modelo: cubo con el frente al norte; el estado gira según 'facing'
    def face(t, cull):
        return {"uv": [0, 0, 16, 16], "texture": t, "cullface": cull}
    write_json(f"{ASSETS}/models/block/architect_table.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{NS}:block/architect_table_side", "top": f"{NS}:block/architect_table_top",
                     "side": f"{NS}:block/architect_table_side", "front": f"{NS}:block/architect_table_front",
                     "bottom": f"{NS}:block/architect_table_bottom"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
            "down": face("#bottom", "down"), "up": face("#top", "up"), "north": face("#front", "north"),
            "south": face("#side", "south"), "west": face("#side", "west"), "east": face("#side", "east")}}]})
    write_json(f"{ASSETS}/blockstates/architect_table.json", {"variants": {
        f"facing={d}": ({"model": f"{NS}:block/architect_table", "y": rot} if rot else {"model": f"{NS}:block/architect_table"})
        for d, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    write_json(f"{ASSETS}/models/item/architect_table.json", {"parent": f"{NS}:block/architect_table"})
    write_json(f"{DATA}/loot_table/blocks/architect_table.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:architect_table"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"{NS}:blocks/architect_table"})
    write_json(f"{DATA}/recipe/architect_table.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["PPP", "LCL", "L L"],
        "key": {"P": {"item": "minecraft:paper"}, "L": {"tag": "minecraft:planks"},
                "C": {"item": "minecraft:cartography_table"}},
        "result": {"id": f"{NS}:architect_table", "count": 1}})

    for name, fn in (("structure_scanner", scanner), ("blueprint", blueprint)):
        im = fn()
        im.save(f"{idir}/{name}.png")
        write_json(f"{ASSETS}/models/item/{name}.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    write_json(f"{DATA}/recipe/structure_scanner.json", {
        "type": "minecraft:crafting_shaped", "category": "tools",
        "pattern": [" G ", "ICI", " R "],
        "key": {"G": {"item": "minecraft:glass_pane"}, "I": {"item": "minecraft:gold_ingot"},
                "C": {"item": "minecraft:compass"}, "R": {"item": "minecraft:redstone"}},
        "result": {"id": f"{NS}:structure_scanner", "count": 1}})

    # vista previa
    sheet = Image.new("RGBA", (16 * 6 * 6 + 70, 16 * 6 + 20), (236, 232, 222, 255))
    for i, im in enumerate([faces["top"], faces["front"], faces["side"], faces["bottom"], scanner(), blueprint()]):
        sheet.alpha_composite(up(im, 6), (10 + i * (16 * 6 + 10), 10))
    sheet.convert("RGB").save(f"{PREVIEW}/mesa_escaner_plano_x6.png")
    print("ok")
