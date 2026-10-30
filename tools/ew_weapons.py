"""Fase 8: armas medievales (iconos, modelos, recetas, mejora a netherita y etiquetas), arco largo, jabalina y la mesa
de investigación.

Uso: python3 ew_weapons.py <src/main/resources> <carpeta de vistas previas>

Los iconos se dibujan en diagonal como las armas del juego: la empuñadura abajo a la izquierda y la punta arriba a la
derecha. Cada arma se pinta con la paleta de su material (hierro, diamante, netherita).
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


# claro, medio, oscuro, contorno
METALS = {
    "iron": (hexc("#F2F2F2"), hexc("#C8C8C8"), hexc("#8E8E8E"), hexc("#3F3F3F")),
    "diamond": (hexc("#D6FFF8"), hexc("#5FE6D6"), hexc("#26A89A"), hexc("#0E4A44")),
    "netherite": (hexc("#7A6E78"), hexc("#4D4249"), hexc("#332B31"), hexc("#16121A")),
}
WOOD = (hexc("#A57A48"), hexc("#7A5530"), hexc("#4E3420"))
DARK_GRIP = (hexc("#5A4A5A"), hexc("#3A2E3A"), hexc("#221A22"))
GOLD = (hexc("#FFF07A"), hexc("#E8B830"), hexc("#9A6A10"))


class C:
    def __init__(self):
        self.im = Image.new("RGBA", (16, 16), T)

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.im.putpixel((x, y), c)

    def get(self, x, y):
        return self.im.getpixel((x, y)) if 0 <= x < 16 and 0 <= y < 16 else T


def diag(c, x0, y0, n, colors, width=1):
    """Una franja en diagonal (hacia arriba a la derecha) de n píxeles; colors = (claro, medio, oscuro)."""
    light, mid, dark = colors[0], colors[1], colors[2]
    for i in range(n):
        x, y = x0 + i, y0 - i
        c.px(x, y, mid)
        if width >= 2:
            c.px(x + 1, y, light)
            c.px(x, y + 1, dark)


def outline(c, color):
    """Contorno oscuro alrededor de todo lo pintado (como los objetos del juego)."""
    src = c.im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] > 0:
                        c.px(x, y, color)
                        break


def grip(material):
    return DARK_GRIP if material == "netherite" else WOOD


def guard_color(material):
    return GOLD if material == "netherite" else (hexc("#6A4A2A"), hexc("#4E3420"), hexc("#3A2614"))


def dagger(material):
    l, m, d, o = METALS[material]
    c = C()
    # hoja corta
    for i in range(6):
        x, y = 7 + i, 8 - i
        c.px(x, y, m)
        c.px(x + 1, y, l)
        c.px(x, y - 1, l if i == 5 else m)
    c.px(13, 2, l)
    # guarda
    g = guard_color(material)
    for (x, y) in ((5, 8), (6, 9), (7, 10), (8, 11)):
        pass
    for (x, y) in ((4, 8), (5, 9), (6, 10), (7, 11)):
        c.px(x + 1, y - 1, g[1])
    # empuñadura
    gr = grip(material)
    for i in range(4):
        c.px(2 + i, 13 - i, gr[1] if i % 2 else gr[2])
    c.px(1, 14, g[0])
    outline(c, o)
    return c.im


def spear(material):
    l, m, d, o = METALS[material]
    c = C()
    gr = grip(material)
    for i in range(11):
        c.px(1 + i, 14 - i, gr[1] if i % 3 else gr[2])
    # punta en hoja
    for (x, y, col) in ((11, 4, d), (12, 3, m), (13, 2, l), (14, 1, l), (12, 4, m), (11, 3, l), (13, 3, d), (12, 2, l),
                        (10, 4, d), (12, 5, d)):
        c.px(x, y, col)
    c.px(10, 5, guard_color(material)[1])
    c.px(11, 6, guard_color(material)[1])
    outline(c, o)
    return c.im


def longsword(material):
    l, m, d, o = METALS[material]
    c = C()
    for i in range(10):
        x, y = 5 + i, 10 - i
        c.px(x, y, m)
        c.px(x + 1, y, l)
        c.px(x, y + 1, d)
    c.px(15, 0, l)
    g = guard_color(material)
    for (x, y) in ((2, 9), (3, 10), (4, 11), (5, 12), (6, 13)):
        c.px(x + 1, y - 1, g[1])
    c.px(2, 8, g[0])
    c.px(7, 13, g[2])
    gr = grip(material)
    for i in range(3):
        c.px(2 + i, 13 - i, gr[1] if i % 2 else gr[2])
    c.px(1, 14, g[0])
    c.px(0, 15, g[1])
    outline(c, o)
    return c.im


def halberd(material):
    l, m, d, o = METALS[material]
    c = C()
    gr = grip(material)
    for i in range(12):
        c.px(1 + i, 14 - i, gr[1] if i % 3 else gr[2])
    # punta
    c.px(13, 1, l)
    c.px(14, 0, l)
    c.px(13, 2, m)
    # hoja de hacha (abajo a la derecha del asta) y pico (arriba a la izquierda)
    for (x, y) in ((10, 3), (11, 4), (12, 5), (13, 6), (12, 4), (13, 5), (11, 5), (12, 6), (13, 7), (14, 6), (14, 5)):
        c.px(x, y, m)
    for (x, y) in ((14, 4), (14, 7), (13, 8)):
        c.px(x, y, l)
    for (x, y) in ((11, 6), (10, 5)):
        c.px(x, y, d)
    for (x, y) in ((9, 2), (8, 2), (8, 1)):
        c.px(x, y, d)
    outline(c, o)
    return c.im


def warhammer(material):
    l, m, d, o = METALS[material]
    c = C()
    gr = grip(material)
    for i in range(10):
        c.px(2 + i, 14 - i, gr[1] if i % 3 else gr[2])
    # cabeza: bloque girado
    head = [(9, 2), (10, 1), (11, 0), (10, 3), (11, 2), (12, 1), (11, 4), (12, 3), (13, 2), (12, 5), (13, 4), (14, 3), (13, 6),
            (14, 5), (15, 4)]
    for i, (x, y) in enumerate(head):
        c.px(x, y, l if i % 3 == 0 else (m if i % 3 == 1 else d))
    # pico de atrás
    c.px(14, 7, m)
    c.px(15, 8, d)
    c.px(8, 1, d)
    outline(c, o)
    return c.im


def flail(material):
    l, m, d, o = METALS[material]
    c = C()
    gr = grip(material)
    for i in range(6):
        c.px(1 + i, 14 - i, gr[1] if i % 2 else gr[2])
    c.px(7, 8, d)
    # cadena
    chain = hexc("#7A7A7A") if material != "diamond" else hexc("#8A9A9A")
    for (x, y) in ((8, 7), (9, 7), (9, 6), (10, 5), (10, 6)):
        c.px(x, y, chain if (x + y) % 2 else shade(chain, 0.7))
    # bola con pinchos
    for (x, y) in ((11, 3), (12, 3), (13, 3), (11, 4), (12, 4), (13, 4), (11, 2), (12, 2), (13, 2), (12, 1), (12, 5), (10, 3),
                   (14, 3)):
        c.px(x, y, m)
    c.px(11, 2, l)
    c.px(12, 2, l)
    c.px(13, 4, d)
    for (x, y) in ((12, 0), (14, 1), (15, 3), (14, 5), (10, 1), (9, 3), (10, 5)):
        c.px(x, y, l)
    outline(c, o)
    return c.im


def battle_axe(material):
    l, m, d, o = METALS[material]
    c = C()
    gr = grip(material)
    for i in range(12):
        c.px(1 + i, 14 - i, gr[1] if i % 3 else gr[2])
    # hoja doble
    blade = [(8, 1), (9, 1), (10, 2), (7, 2), (8, 2), (9, 2), (8, 3), (13, 6), (14, 6), (14, 7), (13, 7), (12, 7), (14, 8),
             (13, 8), (12, 6), (14, 5), (11, 7), (14, 9)]
    for i, (x, y) in enumerate(blade):
        c.px(x, y, m if i % 4 else l)
    for (x, y) in ((7, 1), (6, 2), (15, 6), (15, 7), (15, 8)):
        c.px(x, y, l)
    for (x, y) in ((9, 3), (12, 8), (11, 6)):
        c.px(x, y, d)
    outline(c, o)
    return c.im


SHAPES = {"dagger": dagger, "spear": spear, "longsword": longsword, "halberd": halberd, "warhammer": warhammer,
          "flail": flail, "battle_axe": battle_axe}


def longbow(pull):
    """Arco largo: casi de esquina a esquina; con 'pull' (0..3) la cuerda se tensa y asoma la flecha."""
    c = C()
    wood = (hexc("#9A6E3C"), hexc("#6E4A26"), hexc("#462E16"))
    bend = [0, 1, 2, 3][pull]
    pts = [(1 + i, 14 - i) for i in range(14)]
    # curva hacia arriba a la izquierda
    for i, (x, y) in enumerate(pts):
        off = int(round(((i - 6.5) ** 2) / 14.0)) - 3
        off = max(-3 - bend // 2, min(0, off))
        c.px(x + off, y + off, wood[1] if i % 3 else wood[0])
        c.px(x + off + 1, y + off + 1, wood[2])
    c.px(7, 7, hexc("#3E3E3E"))
    c.px(8, 8, hexc("#3E3E3E"))
    # cuerda (recta entre las puntas; tensa, hacia abajo a la derecha)
    string = hexc("#E6E6E6")
    for i in range(14):
        x, y = 2 + i, 14 - i
        mid = 7 - abs(i - 7)
        pullx = (bend * mid) // 7
        c.px(x + pullx, y + pullx, string)
    if pull > 0:
        for i in range(6):
            c.px(5 + i + bend, 10 - i + bend, hexc("#B08050"))
        c.px(11 + bend, 4 + bend, hexc("#D8D8D8"))
    return c.im


def javelin():
    c = C()
    for i in range(12):
        c.px(1 + i, 14 - i, WOOD[1] if i % 3 else WOOD[2])
        c.px(2 + i, 14 - i, WOOD[0] if i % 4 == 0 else WOOD[1])
    l, m, d, o = METALS["iron"]
    for (x, y, col) in ((12, 3, m), (13, 2, l), (14, 1, l), (15, 0, l), (13, 3, d), (12, 2, l)):
        c.px(x, y, col)
    c.px(4, 12, hexc("#C83A3A"))
    c.px(5, 11, hexc("#C83A3A"))
    outline(c, hexc("#2A2A2A"))
    return c.im


# ----------------------------------------------------------------- mesa de investigación
def desk_top():
    rnd = random.Random(91)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            seam = x % 4 == 3
            base = hexc("#8A6238")
            n = rnd.randint(-8, 8)
            col = shade(base, 0.8) if seam else tuple(max(0, min(255, v + n)) for v in base[:3]) + (255,)
            im.putpixel((x, y), col)
    # libro abierto y pluma
    for y in range(4, 11):
        for x in range(2, 13):
            page = hexc("#EDE5CC") if x != 7 else hexc("#9A8A6A")
            im.putpixel((x, y), page)
    for y in (5, 7, 9):
        for x in list(range(3, 7)) + list(range(8, 12)):
            if (x + y) % 3:
                im.putpixel((x, y), hexc("#6A5A4A"))
    for i in range(5):
        im.putpixel((13 - i // 2, 11 + i // 2 + i % 2 - 1), hexc("#F4F4F4"))
    im.putpixel((11, 14), hexc("#2A2A2A"))
    im.putpixel((13, 13), hexc("#1E1E3A"))
    im.putpixel((14, 13), hexc("#1E1E3A"))
    for x in range(0, 16):
        im.putpixel((x, 0), hexc("#4E3420"))
        im.putpixel((x, 15), hexc("#4E3420"))
    for y in range(0, 16):
        im.putpixel((0, y), hexc("#4E3420"))
        im.putpixel((15, y), hexc("#4E3420"))
    return im


def desk_side():
    rnd = random.Random(92)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = hexc("#7A5530")
            n = rnd.randint(-7, 7)
            col = tuple(max(0, min(255, v + n)) for v in base[:3]) + (255,)
            if y <= 2:
                col = hexc("#4E3420") if y == 0 else hexc("#8A6238")
            if x in (0, 15) or y == 15:
                col = hexc("#3A2614")
            im.putpixel((x, y), col)
    # estante con pergaminos
    for x in range(2, 14):
        im.putpixel((x, 9), hexc("#4E3420"))
    for (x0, col) in ((3, "#E8DDB8"), (6, "#C8B890"), (9, "#E8DDB8"), (11, "#B83A3A")):
        for y in range(5, 9):
            for x in range(x0, x0 + 2):
                im.putpixel((x, y), hexc(col))
    for (x0, col) in ((2, "#3A5A9A"), (4, "#7A2A2A"), (6, "#2A6A3A"), (8, "#9A7A2A"), (10, "#5A3A7A"), (12, "#3A5A9A")):
        for y in range(11, 15):
            im.putpixel((x0, y), hexc(col))
            im.putpixel((x0 + 1, y), shade(hexc(col), 0.8))
    return im


# ----------------------------------------------------------------- JSON
def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def shaped(name, pattern, key, count=1, category="equipment"):
    keys = {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()}
    write_json(f"{DATA}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
                                              "key": keys, "result": {"id": f"{NS}:{name}", "count": count}})


PATTERNS = {
    "dagger": ["M", "S"],
    "spear": ["  M", " S ", "S  "],
    "longsword": [" M ", " M ", "MSM"],
    "halberd": ["M M", " S ", " S "],
    "warhammer": ["MMM", "MSM", " S "],
    "flail": ["SCM"],
    "battle_axe": ["MMM", "MS ", " S "],
}


def merge_tag(path, values):
    data = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    for v in values:
        if v not in data["values"]:
            data["values"].append(v)
    write_json(path, data)


def up(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


if __name__ == "__main__":
    idir = f"{ASSETS}/textures/item"
    bdir = f"{ASSETS}/textures/block"
    os.makedirs(idir, exist_ok=True)
    os.makedirs(bdir, exist_ok=True)
    os.makedirs(PREVIEW, exist_ok=True)
    swords, axes, sheet = [], [], []
    for weapon, painter in SHAPES.items():
        row = []
        for material in ("iron", "diamond", "netherite"):
            name = f"{material}_{weapon}"
            im = painter(material)
            im.save(f"{idir}/{name}.png")
            row.append(im)
            # el icono plano (inventario); el modelo del objeto es el 3D de tools/ew_melee.py
            write_json(f"{ASSETS}/models/item/{name}_icon.json", {"parent": "minecraft:item/handheld",
                                                                  "textures": {"layer0": f"{NS}:item/{name}"}})
            (axes if weapon == "battle_axe" else swords).append(f"{NS}:{name}")
            if material == "netherite":
                write_json(f"{DATA}/recipe/{name}_smithing.json", {
                    "type": "minecraft:smithing_transform",
                    "template": {"item": "minecraft:netherite_upgrade_smithing_template"},
                    "base": {"item": f"{NS}:diamond_{weapon}"},
                    "addition": {"item": "minecraft:netherite_ingot"},
                    "result": {"id": f"{NS}:{name}", "count": 1}})
            else:
                key = {"M": "minecraft:iron_ingot" if material == "iron" else "minecraft:diamond", "S": "minecraft:stick"}
                if weapon == "flail":
                    key["C"] = "minecraft:chain"
                shaped(name, PATTERNS[weapon], key)
        sheet.append(row)

    # arco largo con sus tres fases de tensado
    bows = [longbow(i) for i in range(4)]
    bows[0].save(f"{idir}/longbow.png")
    for i in range(3):
        bows[i + 1].save(f"{idir}/longbow_pulling_{i}.png")
        write_json(f"{ASSETS}/models/item/longbow_pulling_{i}.json", {"parent": "minecraft:item/bow",
                                                                     "textures": {"layer0": f"{NS}:item/longbow_pulling_{i}"}})
    write_json(f"{ASSETS}/models/item/longbow.json", {
        "parent": "minecraft:item/bow", "textures": {"layer0": f"{NS}:item/longbow"},
        "overrides": [
            {"predicate": {"pulling": 1}, "model": f"{NS}:item/longbow_pulling_0"},
            {"predicate": {"pulling": 1, "pull": 0.65}, "model": f"{NS}:item/longbow_pulling_1"},
            {"predicate": {"pulling": 1, "pull": 0.9}, "model": f"{NS}:item/longbow_pulling_2"}]})
    shaped("longbow", [" SR", "I R", " SR"], {"S": "minecraft:stick", "R": "minecraft:string", "I": "minecraft:iron_ingot"})

    javelin().save(f"{idir}/javelin.png")
    write_json(f"{ASSETS}/models/item/javelin.json", {"parent": "minecraft:item/handheld",
                                                      "textures": {"layer0": f"{NS}:item/javelin"}})
    shaped("javelin", ["M  ", " S ", "  F"], {"M": "minecraft:iron_ingot", "S": "minecraft:stick", "F": "minecraft:feather"},
           count=2)

    # mesa de investigación
    desk_top().save(f"{bdir}/research_desk_top.png")
    desk_side().save(f"{bdir}/research_desk_side.png")
    write_json(f"{ASSETS}/models/block/research_desk.json", {"parent": "minecraft:block/orientable", "textures": {
        "top": f"{NS}:block/research_desk_top", "front": f"{NS}:block/research_desk_side",
        "side": f"{NS}:block/research_desk_side"}})
    write_json(f"{ASSETS}/blockstates/research_desk.json", {"variants": {
        f"facing={d}": ({"model": f"{NS}:block/research_desk", "y": rot} if rot else {"model": f"{NS}:block/research_desk"})
        for d, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    write_json(f"{ASSETS}/models/item/research_desk.json", {"parent": f"{NS}:block/research_desk"})
    write_json(f"{DATA}/loot_table/blocks/research_desk.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{NS}:research_desk"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"{NS}:blocks/research_desk"})
    shaped("research_desk", ["BFB", "PTP", "P P"], {"B": "minecraft:book", "F": "minecraft:feather", "P": "#minecraft:planks",
                                                     "T": "minecraft:crafting_table"}, category="misc")

    # etiquetas: encantamientos de espada / hacha / arco, y se rompe con hacha
    write_json(f"{RES}/data/minecraft/tags/item/swords.json", {"replace": False, "values": swords})
    write_json(f"{RES}/data/minecraft/tags/item/axes.json", {"replace": False, "values": axes})
    write_json(f"{RES}/data/minecraft/tags/item/enchantable/bow.json", {"replace": False, "values": [f"{NS}:longbow"]})
    write_json(f"{RES}/data/minecraft/tags/item/enchantable/durability.json", {"replace": False, "values": [f"{NS}:longbow"]})
    merge_tag(f"{RES}/data/minecraft/tags/block/mineable/axe.json", [f"{NS}:research_desk"])

    # vista previa
    rows = len(sheet)
    out = Image.new("RGBA", (3 * (16 * 6 + 8) + 8 + 4 * (16 * 6 + 8), rows * (16 * 6 + 8) + 8), (236, 232, 222, 255))
    for r, row in enumerate(sheet):
        for col, im in enumerate(row):
            out.alpha_composite(up(im, 6), (8 + col * (16 * 6 + 8), 8 + r * (16 * 6 + 8)))
    extra = bows + [javelin(), desk_top(), desk_side()]
    for i, im in enumerate(extra):
        out.alpha_composite(up(im, 6), (8 + (3 + i % 4) * (16 * 6 + 8), 8 + (i // 4) * (16 * 6 + 8)))
    out.convert("RGB").save(f"{PREVIEW}/armas_x6.png")
    print("ok")
