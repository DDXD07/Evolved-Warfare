"""Buzón de Ventas: texturas, modelo 3D (banderita bajada y levantada), estados, objeto, pantalla, receta, botín,
etiquetas y textos.

El buzón: un poste de abeto sobre una peana, una ménsula y encima la caja de madera con el techo redondeado, dos flejes
de hierro, la puerta delante con la ranura de latón y una Glory Coin pintada, y la banderita roja al costado (se
levanta cuando hay monedas que recoger).

Todas las caras usan las coordenadas de textura automáticas (las del propio bloque), así que cada textura se pinta "en
el sitio" de la cara que la usa, a un píxel por 1/16 de bloque como en vanilla.

Uso: python3 tools/ew_mailbox.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))

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
NAME = "sales_mailbox"


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def jit(c, rnd, amt=6):
    n = rnd.randint(-amt, amt)
    return tuple(max(0, min(255, v + n)) for v in c[:3]) + (c[3],)


def tex(painter, seed):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), painter(x, y, rnd))
    return im


WOOD = hexc("#9C6B3C")      # tablas de la caja
WOOD_L = hexc("#B07E4A")
WOOD_D = hexc("#6E4826")
SPRUCE = hexc("#5E3F22")    # poste
SPRUCE_L = hexc("#74502D")
IRON = hexc("#5A5D63")
IRON_L = hexc("#8A8E95")
IRON_D = hexc("#35373B")
BRASS = hexc("#C9A23A")
BRASS_L = hexc("#EBCB5F")
BRASS_D = hexc("#8C6A1E")
SLIT = hexc("#1C1209")
RED = hexc("#B3262A")
RED_D = hexc("#7E1719")


def planks_h():
    """Costados y fondo: tablas a lo largo (filas), con juntas oscuras y algún clavo."""
    def p(x, y, r):
        if y % 3 == 0:
            return shade(WOOD_D, 1.05)
        c = jit(WOOD if (y // 3) % 2 == 0 else WOOD_L, r, 6)
        if (x * 7 + y * 3) % 17 == 0:
            c = shade(c, 0.88)
        if y % 3 == 1 and x in (2, 13):
            return hexc("#3B3B3B")
        return c
    return tex(p, 201)


def planks_v():
    """Techo: las mismas tablas pero en columnas (van de delante a atrás)."""
    return planks_h().transpose(Image.Transpose.ROTATE_90)


def back():
    """La trasera: tablas con un marco."""
    def p(x, y, r):
        if x in (4, 11) or y in (1, 7):
            return jit(WOOD_D, r, 4)
        if x % 3 == 0:
            return shade(WOOD_D, 1.1)
        return jit(WOOD, r, 6)
    return tex(p, 202)


def front():
    """La puerta (en la zona x 4..12, y 0..7 de la textura: la cara de delante de la caja y del techo)."""
    def p(x, y, r):
        # marco de la puerta
        if x in (4, 11) or y >= 6 or y <= 1:
            return jit(WOOD_D, r, 4)
        # placa de latón con la ranura
        if 2 <= y <= 3 and 5 <= x <= 10:
            if y == 3 and 6 <= x <= 9:
                return SLIT
            return BRASS_L if y == 2 else BRASS
        # la Glory Coin pintada
        if y == 4 and x in (7, 8):
            return BRASS_L
        if y == 5 and x in (7, 8):
            return BRASS_D
        return jit(WOOD_L, r, 5)
    return tex(p, 203)


def iron():
    """Flejes y brazo de la banderita: hierro con remaches."""
    def p(x, y, r):
        c = jit(IRON, r, 5)
        if y % 4 == 0:
            c = IRON_L
        if (x + y) % 6 == 0:
            c = IRON_D
        if x % 5 == 2 and y % 5 == 2:
            c = IRON_L
        return c
    return tex(p, 204)


def post():
    """Poste y peana de abeto: veta vertical."""
    def p(x, y, r):
        c = jit(SPRUCE if x % 4 else shade(SPRUCE, 0.85), r, 5)
        if (x * 5 + y) % 11 == 0:
            c = SPRUCE_L
        return c
    return tex(p, 205)


def flag():
    """La banderita: roja con una moneda dorada en medio (cabe en cualquier zona de la textura)."""
    def p(x, y, r):
        c = jit(RED, r, 6)
        if x % 3 == 2 or y % 3 == 2:
            c = shade(c, 0.92)
        cx = 3.75 if x < 8 else 11.75
        cy = 11.75
        d = math.hypot(x + 0.5 - cx - 0.5, y + 0.5 - cy - 0.5)
        if d < 1.1:
            return BRASS_L
        if d < 1.6:
            return BRASS_D
        if x in (0, 15) or y in (0, 15):
            return RED_D
        return c
    return tex(p, 206)


TEXTURES = collections.OrderedDict([
    ("side", planks_h), ("top", planks_v), ("back", back), ("front", front), ("iron", iron), ("post", post),
    ("flag", flag),
])


# ------------------------------------------------------------------ modelo (mirando al norte: la puerta en z pequeña)

def box(frm, to, faces, tint=None):
    """faces: {cara: textura}; uv automático (las coordenadas del bloque)."""
    return {"from": list(frm), "to": list(to), "faces": {k: {"texture": "#" + v} for k, v in faces.items()}}


def all6(t, **over):
    d = {f: t for f in ("north", "south", "east", "west", "up", "down")}
    d.update(over)
    return d


BODY = [
    # caja y techo redondeado (tres escalones)
    box((4, 9, 1), (12, 14, 15), all6("side", north="front", south="back", up="top")),
    box((4.5, 14, 1), (11.5, 14.75, 15), all6("top", north="front", south="back", east="side", west="side")),
    box((5.5, 14.75, 1), (10.5, 15.5, 15), all6("top", north="front", south="back", east="side", west="side")),
]
for z0, z1 in ((3, 4.5), (11.5, 13)):
    # flejes de hierro alrededor de la caja y del techo
    BODY.append(box((3.75, 8.75, z0), (12.25, 14.25, z1), all6("iron")))
    BODY.append(box((4.25, 14.0, z0), (11.75, 15.0, z1), all6("iron")))
    BODY.append(box((5.25, 14.75, z0), (10.75, 15.75, z1), all6("iron")))
STAND = [
    box((5.5, 0, 5.5), (10.5, 1.5, 10.5), all6("post")),
    box((6.5, 1.5, 6.5), (9.5, 8, 9.5), all6("post")),
    box((5, 8, 2.5), (11, 9, 13.5), all6("post")),
    # bisagra de la banderita (en el costado este)
    box((12.25, 11, 11), (12.5, 12, 12), all6("iron")),
]
FLAG_DOWN = [
    box((12.5, 11.25, 4.5), (13, 11.75, 12), all6("iron")),
    box((12.5, 10.5, 2.5), (13, 13, 5), all6("flag")),
]
FLAG_UP = [
    box((12.5, 11.0, 11.25), (13, 18.5, 11.75), all6("iron")),
    box((12.5, 18, 10.5), (13, 20.5, 13), all6("flag")),
]


def model(flag_up):
    textures = {"particle": f"evolvedwarfare:block/{NAME}_side"}
    for name in TEXTURES:
        textures[name] = f"evolvedwarfare:block/{NAME}_{name}"
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": textures,
        "elements": STAND + BODY + (FLAG_UP if flag_up else FLAG_DOWN),
    }


def blockstate():
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for up in (False, True):
            v = {"model": f"evolvedwarfare:block/{NAME}" + ("_flag" if up else "")}
            if rot:
                v["y"] = rot
            variants[f"facing={facing},flag={'true' if up else 'false'}"] = v
    return {"variants": variants}


ITEM_MODEL = {
    "parent": f"evolvedwarfare:block/{NAME}",
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.75, 0.75, 0.75]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, -1, 0], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    },
}


# ------------------------------------------------------------------ pantalla (176x186)

GRID_X, GRID_Y = 17, 18          # como en SalesMailboxScreenHandler
COIN_X, COIN_Y = 134, 45
INV_Y = 104


def gui():
    W, H = 176, 186
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

    def slot(x, y, s=18):
        rect(x, y, x + s, y + s, hexc("#8B8B8B"))
        rect(x, y, x + s - 1, y + 1, hexc("#373737"))
        rect(x, y, x + 1, y + s - 1, hexc("#373737"))
        rect(x + 1, y + s - 1, x + s, y + s, hi)
        rect(x + s - 1, y + 1, x + s, y + s, hi)

    # la caja del buzón: marco de madera con los flejes alrededor de los 4x4 huecos
    gx0, gy0 = GRID_X - 5, GRID_Y - 5
    gx1, gy1 = GRID_X + 4 * 18 + 3, GRID_Y + 4 * 18 + 3
    rect(gx0, gy0, gx1, gy1, WOOD_D)
    rect(gx0 + 1, gy0 + 1, gx1 - 1, gy1 - 1, WOOD)
    for y in range(gy0 + 1, gy1 - 1):
        if (y - gy0) % 6 == 0:
            rect(gx0 + 1, y, gx1 - 1, y + 1, WOOD_D)
    for x0 in (gx0 + 18, gx1 - 21):
        rect(x0, gy0, x0 + 3, gy1, IRON)
        rect(x0, gy0, x0 + 1, gy1, IRON_L)
        for y in range(gy0 + 3, gy1, 9):
            im.putpixel((x0 + 1, y), IRON_D)
    for r in range(4):
        for c in range(4):
            slot(GRID_X - 1 + c * 18, GRID_Y - 1 + r * 18)

    # flecha de la caja a las monedas
    ax, ay = 112, COIN_Y + 4
    for i in range(9):
        rect(ax + i, ay + 3, ax + i + 1, ay + 6, hexc("#8B8B8B"))
    for i in range(5):
        rect(ax + 9 + i, ay + i, ax + 10 + i, ay + 9 - i, hexc("#8B8B8B"))

    # el hueco de las monedas: grande, con marco de latón
    cx, cy = COIN_X - 5, COIN_Y - 5
    rect(cx - 2, cy - 2, cx + 28, cy + 28, BRASS_D)
    rect(cx - 1, cy - 1, cx + 27, cy + 27, BRASS)
    rect(cx - 1, cy - 1, cx + 27, cy, BRASS_L)
    slot(cx, cy, 26)

    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, INV_Y - 1 + r * 18)
    for c in range(9):
        slot(7 + c * 18, INV_Y + 57)
    return im


RECIPE = {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "pattern": ["IGI", "ICI", " F "],
    "key": {
        "I": {"item": "minecraft:iron_ingot"},
        "G": {"item": "evolvedwarfare:glory_coin"},
        "C": {"item": "minecraft:chest"},
        "F": {"tag": "minecraft:wooden_fences"},
    },
    "result": {"id": f"evolvedwarfare:{NAME}", "count": 1},
}

LOOT = {
    "type": "minecraft:block",
    "pools": [{
        "rolls": 1,
        "bonus_rolls": 0,
        "entries": [{"type": "minecraft:item", "name": f"evolvedwarfare:{NAME}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}],
    }],
    "random_sequence": f"evolvedwarfare:blocks/{NAME}",
}

# (español, inglés)
TEXTS = {
    "block.evolvedwarfare.sales_mailbox": ("Buzón de Ventas", "Sales Mailbox"),
    "block.evolvedwarfare.sales_mailbox.tooltip": (
        "Lo que metas se vende solo cada día nuevo, a 1/4 del precio de mercado",
        "Whatever you put in sells by itself every new day, at 1/4 of the market price"),
    "gui.evolvedwarfare.sales_mailbox.next": ("Venta en", "Sale in"),
    "gui.evolvedwarfare.sales_mailbox.pending": ("≈ %s", "≈ %s"),
    "gui.evolvedwarfare.sales_mailbox.last": ("Última: %s → %s", "Last: %s → %s"),
    "gui.evolvedwarfare.sales_mailbox.worth": ("Se vende por ≈ %s Glory Coins", "Sells for ≈ %s Glory Coins"),
    "gui.evolvedwarfare.sales_mailbox.unsellable": ("No se vende: no tiene precio en el mercado",
                                                    "Won't sell: it has no market price"),
    "message.evolvedwarfare.sales_mailbox.sold": (
        "Buzón de Ventas: vendidos %s objetos por %s Glory Coins (%s, %s, %s)",
        "Sales Mailbox: sold %s items for %s Glory Coins (%s, %s, %s)"),
    "message.evolvedwarfare.sales_mailbox.not_yours": ("Este buzón no es tuyo", "This mailbox isn't yours"),
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


# ------------------------------------------------------------------ vista previa (con el rasterizador de las armaduras)

def face_quads(el):
    """Las caras de un elemento con sus uv automáticos, como las pinta Minecraft (en bloques, y arriba)."""
    (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
    out = []
    for f, spec in el["faces"].items():
        if f == "north":
            v = [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)]
            uv = (16 - x1, 16 - y1, 16 - x0, 16 - y0)
        elif f == "south":
            v = [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)]
            uv = (x0, 16 - y1, x1, 16 - y0)
        elif f == "west":
            v = [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)]
            uv = (z0, 16 - y1, z1, 16 - y0)
        elif f == "east":
            v = [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)]
            uv = (16 - z1, 16 - y1, 16 - z0, 16 - y0)
        elif f == "up":
            v = [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)]
            uv = (x0, z0, x1, z1)
        else:
            v = [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]
            uv = (x0, 16 - z1, x1, 16 - z0)
        u1, v1, u2, v2 = uv
        out.append((np.array(v, float) / 16.0, [(u1, v1), (u2, v1), (u2, v2), (u1, v2)], spec["texture"][1:]))
    return out


def preview(images, gui_im):
    from ew_armor_raster import Canvas, look
    arrays = {k: np.array(im) for k, im in images.items()}
    shots = []
    for flag_up in (False, True):
        m = model(flag_up)
        for eye in ((1.9, 1.5, -1.5), (-1.3, 1.4, 2.1)):
            c = Canvas(360, 360, look(np.array(eye) + 0.5, (0.5, 0.55, 0.5)), fov=38, bg=(236, 232, 222, 255))
            for el in m["elements"]:
                for verts, uvs, t in face_quads(el):
                    c.quad(verts, uvs, arrays[t])
            shots.append(c.image())
    sheet = Image.new("RGBA", (360 * 4, 360), (236, 232, 222, 255))
    for i, im in enumerate(shots):
        sheet.paste(im, (i * 360, 0))
    os.makedirs(PREVIEW, exist_ok=True)
    sheet.save(os.path.join(PREVIEW, "mailbox_model.png"))
    strip = Image.new("RGBA", (18 * len(images), 16), (0, 0, 0, 0))
    for i, im in enumerate(images.values()):
        strip.paste(im, (i * 18, 0))
    strip.resize((strip.width * 8, strip.height * 8), Image.NEAREST).save(os.path.join(PREVIEW, "mailbox_textures.png"))
    gui_im.crop((0, 0, 176, 186)).resize((176 * 3, 186 * 3), Image.NEAREST).save(os.path.join(PREVIEW, "mailbox_gui.png"))


def main():
    block = os.path.join(ASSETS, "textures/block")
    os.makedirs(block, exist_ok=True)
    images = collections.OrderedDict((name, painter()) for name, painter in TEXTURES.items())
    for name, im in images.items():
        im.save(os.path.join(block, f"{NAME}_{name}.png"))
    write_json(os.path.join(ASSETS, f"models/block/{NAME}.json"), model(False))
    write_json(os.path.join(ASSETS, f"models/block/{NAME}_flag.json"), model(True))
    write_json(os.path.join(ASSETS, f"blockstates/{NAME}.json"), blockstate())
    write_json(os.path.join(ASSETS, f"models/item/{NAME}.json"), ITEM_MODEL)
    os.makedirs(os.path.join(ASSETS, "textures/gui/container"), exist_ok=True)
    g = gui()
    g.save(os.path.join(ASSETS, f"textures/gui/container/{NAME}.png"))
    write_json(os.path.join(DATA, f"evolvedwarfare/recipe/{NAME}.json"), RECIPE)
    write_json(os.path.join(DATA, f"evolvedwarfare/loot_table/blocks/{NAME}.json"), LOOT)
    add_to_tag(os.path.join(DATA, "minecraft/tags/block/mineable/axe.json"), f"evolvedwarfare:{NAME}")
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        preview(images, g)
    print("buzón de ventas: texturas, modelos, estados, pantalla, receta, botín, etiquetas y textos")


if __name__ == "__main__":
    main()
