"""Carreta: iconos (rueda, plancha y carreta), modelos de los objetos, la pantalla de la carga (vista desde arriba),
las recetas (rueda y plancha primero; luego 2 ruedas + plancha + 2 palos), los textos y vistas previas en 3D con las
mismas medidas que CartEntityRenderer.java.

Uso: python3 tools/ew_cart.py [raíz del proyecto] [--preview carpeta]
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
from ew_musket_preview import RX, RY, S, T, D, cube_faces, draw, grid, label, perspective  # noqa: E402

args = sys.argv[1:]
PREVIEW = None
if "--preview" in args:
    i = args.index("--preview")
    PREVIEW = args[i + 1]
    del args[i:i + 2]
ROOT = args[0] if args else "."
ASSETS = os.path.join(ROOT, "src/main/resources/assets/evolvedwarfare")
DATA = os.path.join(ROOT, "src/main/resources/data/evolvedwarfare")


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def jit(c, rnd, amt=6):
    n = rnd.randint(-amt, amt)
    return tuple(max(0, min(255, v + n)) for v in c[:3]) + (c[3],)


WOOD, WOOD_D, WOOD_L = hexc("#A07443"), hexc("#6B4A28"), hexc("#C0935C")
DARK = hexc("#4A3220")
IRON, IRON_D = hexc("#B8B8B8"), hexc("#6E6E6E")


# ------------------------------------------------------------------ iconos (16x16)
def wheel_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    c = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - c, y - c)
            a = math.degrees(math.atan2(y - c, x - c)) % 60
            if 6.6 <= d <= 7.6:
                px[x, y] = IRON_D if d > 7.2 else IRON
            elif 5.0 <= d < 6.6:
                px[x, y] = WOOD_D if d > 6.0 else WOOD
            elif d < 1.8:
                px[x, y] = IRON_D if d > 1.2 else IRON
            elif d < 5.0 and (a < 9 or a > 51):
                px[x, y] = WOOD_L if a < 4 or a > 56 else WOOD
    return im


def bed_icon():
    """La plancha: tablas vistas un poco de lado, con dos vigas debajo."""
    rnd = random.Random(3)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(4, 11):
        for x in range(1, 15):
            seam = (x - 1) % 4 == 3
            px[x, y] = WOOD_D if seam else jit(WOOD, rnd, 7)
            if y == 4:
                px[x, y] = WOOD_L
    for x in range(1, 15):
        px[x, 11] = DARK
    for x in (3, 4, 11, 12):
        px[x, 12] = DARK
        px[x, 13] = WOOD_D
    return im


def cart_icon():
    """La carreta de lado: la tabla, una rueda grande y la vara hacia la derecha."""
    rnd = random.Random(4)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for x in range(0, 10):
        for y in range(5, 8):
            px[x, y] = WOOD_D if y == 7 else jit(WOOD, rnd, 6)
        px[x, 4] = WOOD_L
    # una caja encima
    for x in range(1, 5):
        for y in range(1, 4):
            px[x, y] = hexc("#8A5A2B") if y > 1 else hexc("#A8763F")
    # la vara
    for i, x in enumerate(range(9, 16)):
        y = 6 + i // 3
        px[x, y] = WOOD_L
        px[x, y + 1] = WOOD_D
    # la rueda
    c = (5.0, 10.5)
    for y in range(6, 16):
        for x in range(0, 11):
            d = math.hypot(x - c[0], y - c[1])
            if 3.6 <= d <= 4.6:
                px[x, y] = IRON_D if d > 4.2 else DARK
            elif d < 1.2:
                px[x, y] = IRON
            elif d < 3.6 and (abs(x - c[0]) < 0.6 or abs(y - c[1]) < 0.6):
                px[x, y] = WOOD
    return im


# ------------------------------------------------------------------ pantalla (176x200): la carreta desde arriba
W, H = 176, 200
GRID_X, GRID_Y, CELL = 52, 26, 24
INVENTORY_Y, HOTBAR_Y = 118, 176


def gui():
    rnd = random.Random(9)
    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    px = im.load()
    bg, hi, sh, black = hexc("#C6C6C6"), hexc("#FFFFFF"), hexc("#555555"), hexc("#000000")

    def rect(x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = c

    rect(1, 1, W - 1, H - 1, bg)
    rect(2, 0, W - 2, 1, black)
    rect(2, H - 1, W - 2, H, black)
    rect(0, 2, 1, H - 2, black)
    rect(W - 1, 2, W, H - 2, black)
    for p in [(1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)]:
        px[p] = black
    rect(2, 1, W - 3, 3, hi)
    rect(1, 2, 3, H - 3, hi)
    rect(3, H - 3, W - 2, H - 1, sh)
    rect(W - 3, 3, W - 1, H - 2, sh)
    px[W - 3, 2] = bg
    px[2, H - 3] = bg
    # suelo de hierba bajo la carreta
    for y in range(14, 106):
        for x in range(28, 148):
            px[x, y] = jit(hexc("#7FA05A"), rnd, 7)
    # varas hacia arriba (delante) y travesaño
    for side, x0 in ((-1, 68), (1, 106)):
        for y in range(4, 26):
            t = (26 - y) / 22
            x = int(round(x0 + side * -6 * t))
            for dx in (-1, 0, 1):
                px[x + dx, y] = WOOD_L if dx < 0 else WOOD if dx == 0 else WOOD_D
    rect(66, 22, 110, 25, WOOD_D)
    # ruedas a los lados (de canto: largas y finas)
    for x0 in (40, 128):
        rect(x0, 46, x0 + 8, 80, DARK)
        rect(x0 + 1, 47, x0 + 7, 79, WOOD)
        for y in range(48, 79, 5):
            rect(x0 + 1, y, x0 + 7, y + 1, WOOD_D)
        rect(x0, 46, x0 + 8, 47, IRON_D)
        rect(x0, 79, x0 + 8, 80, IRON_D)
    # la tabla: marco oscuro y tablones
    rect(GRID_X - 3, GRID_Y - 3, GRID_X + 3 * CELL + 3, GRID_Y + 3 * CELL + 3, DARK)
    for y in range(GRID_Y - 1, GRID_Y + 3 * CELL + 1):
        for x in range(GRID_X - 1, GRID_X + 3 * CELL + 1):
            seam = (y - GRID_Y) % 6 == 5
            px[x, y] = WOOD_D if seam else jit(WOOD, rnd, 6)
    # las celdas (huecos de 18 como en vanilla, en el centro de cada una)
    for r in range(3):
        for c in range(3):
            x = GRID_X + c * CELL + 3
            y = GRID_Y + r * CELL + 3
            rect(x, y, x + 18, y + 18, hexc("#8B8B8B"))
            rect(x, y, x + 17, y + 1, hexc("#373737"))
            rect(x, y, x + 1, y + 17, hexc("#373737"))
            rect(x + 1, y + 17, x + 18, y + 18, hi)
            rect(x + 17, y + 1, x + 18, y + 18, hi)

    def slot(x, y):
        rect(x, y, x + 18, y + 18, hexc("#8B8B8B"))
        rect(x, y, x + 17, y + 1, hexc("#373737"))
        rect(x, y, x + 1, y + 17, hexc("#373737"))
        rect(x + 1, y + 17, x + 18, y + 18, hi)
        rect(x + 17, y + 1, x + 18, y + 18, hi)

    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, INVENTORY_Y - 1 + r * 18)
    for c in range(9):
        slot(7 + c * 18, HOTBAR_Y - 1)
    return im


# ------------------------------------------------------------------ recetas y textos
RECIPES = {
    "cart_wheel": {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" S ", "SPS", " S "],
                   "key": {"S": {"item": "minecraft:stick"}, "P": {"tag": "minecraft:planks"}},
                   "result": {"id": "evolvedwarfare:cart_wheel", "count": 1}},
    "cart_bed": {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["WWW", "LLL"],
                 "key": {"W": {"tag": "minecraft:wooden_slabs"}, "L": {"tag": "minecraft:logs"}},
                 "result": {"id": "evolvedwarfare:cart_bed", "count": 1}},
    "cart": {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["S S", "WBW"],
             "key": {"S": {"item": "minecraft:stick"}, "W": {"item": "evolvedwarfare:cart_wheel"},
                     "B": {"item": "evolvedwarfare:cart_bed"}},
             "result": {"id": "evolvedwarfare:cart", "count": 1}},
}

TEXTS = {
    "item.evolvedwarfare.cart_wheel": ("Rueda de carreta", "Cart Wheel"),
    "item.evolvedwarfare.cart_bed": ("Plancha de carreta", "Cart Bed"),
    "item.evolvedwarfare.cart": ("Carreta", "Cart"),
    "entity.evolvedwarfare.cart": ("Carreta", "Cart"),
    "item.evolvedwarfare.cart.tooltip": ("Rienda: tirar de ella (o engancha un caballo). Mano vacía: su carga.",
                                         "Lead: pull it (or hitch a horse). Empty hand: its load."),
    "message.evolvedwarfare.cart.pulling": ("Tiras de la carreta. Agáchate para soltarla.",
                                            "You're pulling the cart. Sneak to let go."),
    "message.evolvedwarfare.cart.horse": ("Caballo enganchado: súbete para llevar la carreta. Para soltarlo, agachado + clic derecho en ella.",
                                          "Horse hitched: ride it to move the cart. To unhitch, sneak + right-click the cart."),
    "message.evolvedwarfare.cart.released": ("Has soltado la carreta.", "You let go of the cart."),
    "message.evolvedwarfare.cart.snapped": ("¡La carreta se ha quedado atrás y se ha soltado!",
                                            "The cart got stuck behind and came loose!"),
    "message.evolvedwarfare.cart.busy": ("Ya hay alguien tirando de esta carreta.", "Someone is already pulling this cart."),
    "message.evolvedwarfare.cart.no_room": ("No cabe aquí: la carreta necesita 3x3 bloques libres.",
                                            "No room here: the cart needs 3x3 free blocks."),
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


# ------------------------------------------------------------------ vista previa 3D (mismas medidas que el renderizador)
DECK = 0.75
R = 0.55
WX = 1.62
COLS = {"deck": (176, 136, 84), "beam": (92, 62, 40), "wheel": (120, 86, 52), "iron": (170, 170, 175), "shaft": (196, 160, 110),
        "chest": (160, 110, 50), "barrel": (120, 84, 44), "furnace": (110, 110, 112), "table": (150, 100, 60), "bed": (176, 46, 38),
        "pillow": (236, 236, 236), "player": (60, 90, 140), "skin": (196, 150, 118), "horse": (140, 96, 60)}


def box(faces, m, x0, y0, z0, x1, y1, z1, col):
    for f in cube_faces(m, x0 * 16, y0 * 16, z0 * 16, (x1 - x0) * 16, (y1 - y0) * 16, (z1 - z0) * 16):
        faces.append((f, COLS[col]))


def cart_faces(m, modules, puller=None, angle=0.0):
    faces = []
    box(faces, m, -1.5, 0.55, -1.5, 1.5, DECK, 1.5, "deck")
    box(faces, m, 0.95, 0.40, -1.65, 1.2, 0.55, 1.6, "beam")
    box(faces, m, -1.2, 0.40, -1.65, -0.95, 0.55, 1.6, "beam")
    box(faces, m, -WX, R - 0.06, -0.06, WX, R + 0.06, 0.06, "beam")
    for x in (WX, -WX):
        wm = m @ T(0, R, 0) @ RX(angle)
        seg = 2 * math.pi * R / 12 * 0.56
        for k in range(12):
            km = wm @ RX(D(k * 30))
            box(faces, km, x - 0.06, R - 0.1, -seg, x + 0.06, R, seg, "wheel")
        for k in range(3):
            box(faces, wm @ RX(D(k * 60)), x - 0.035, -R + 0.08, -0.035, x + 0.035, R - 0.08, 0.035, "wheel")
        box(faces, wm, x - 0.09, -0.09, -0.09, x + 0.09, 0.09, 0.09, "iron")
    if puller == "horse":
        tip = (0.78, 1.05, 3.6 + 0.45)
    elif puller == "player":
        tip = (0.42, 0.68, 3.0 + 0.2)
    else:
        tip = (0.55, 0.06, 2.95)
    for side in (1, -1):
        b = np.array([side * 0.7, 0.62, 1.45])
        d = np.array([side * tip[0], tip[1], tip[2]]) - b
        h = math.hypot(d[0], d[2])
        sm = m @ T(*b) @ RY(math.atan2(d[0], d[2])) @ RX(-math.atan2(d[1], h))
        box(faces, sm, -0.05, -0.05, 0, 0.05, 0.05, float(np.linalg.norm(d)), "shaft")
    box(faces, m, -0.75, 0.57, 1.48, 0.75, 0.67, 1.6, "shaft")
    for cell, kind, partner in modules:
        cx, cz = 1 - cell % 3, 1 - cell // 3
        if kind == "bed":
            px_, pz = 1 - partner % 3, 1 - partner // 3
            x0, x1 = min(cx, px_) - 0.5, max(cx, px_) + 0.5
            z0, z1 = min(cz, pz) - 0.5, max(cz, pz) + 0.5
            box(faces, m, x0, DECK, z0, x1, DECK + 0.5625, z1, "bed")
            box(faces, m, cx - 0.4, DECK + 0.5625, cz - 0.4, cx + 0.4, DECK + 0.68, cz + 0.4, "pillow")
        else:
            box(faces, m, cx - 0.5, DECK, cz - 0.5, cx + 0.5, DECK + (0.875 if kind == "chest" else 1.0), cz + 0.5, kind)
    if puller == "player":
        box(faces, m, -0.3, 0.75, 2.85, 0.3, 1.5, 3.15, "player")
        box(faces, m, -0.15, 0.0, 2.9, 0.15, 0.75, 3.1, "beam")
        box(faces, m, -0.25, 1.5, 2.75, 0.25, 2.0, 3.25, "skin")
    elif puller == "horse":
        box(faces, m, -0.6, 0.8, 2.9, 0.6, 1.6, 4.3, "horse")
        box(faces, m, -0.25, 1.4, 4.2, 0.25, 2.2, 4.8, "horse")
        for lx in (-0.4, 0.4):
            for lz in (3.1, 4.1):
                box(faces, m, lx - 0.12, 0, lz - 0.12, lx + 0.12, 0.8, lz + 0.12, "beam")
    return faces


def look(eye, target):
    eye = np.array(eye, float)
    fwd = np.array(target, float) - eye
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    view = np.eye(4)
    view[0, :3], view[1, :3], view[2, :3] = right, up, -fwd
    return view @ T(*(-eye))


def preview(out):
    os.makedirs(out, exist_ok=True)
    loads = [(0, "chest", -1), (2, "barrel", -1), (4, "bed", 7), (3, "furnace", -1), (5, "table", -1), (8, "chest", -1)]
    frames = []
    for title, mods, puller, eye in [("Vacía, puesta en el suelo", [], None, (5.5, 4.0, 6.5)),
                                     ("Con carga (la cama ocupa 2 huecos)", loads, None, (5.0, 5.5, -5.5)),
                                     ("Tirando tú (entre las varas)", loads, "player", (6.5, 3.5, 3.0)),
                                     ("Con caballo", loads, "horse", (7.0, 4.0, 4.0))]:
        faces = cart_faces(np.eye(4), mods, puller, angle=0.4)
        img = draw(faces, perspective(look(eye, (0, 0.8, 0.8)), 560, 400, 50), (560, 400), (170, 200, 160))
        frames.append(label(img, title))
    grid(frames, 2).save(os.path.join(out, "cart_3d.png"))
    g = gui().crop((0, 0, W, H)).resize((W * 3, H * 3), Image.NEAREST)
    g.save(os.path.join(out, "cart_gui.png"))
    strip = Image.new("RGBA", (16 * 3 + 8, 16), (0, 0, 0, 0))
    for i, im in enumerate((wheel_icon(), bed_icon(), cart_icon())):
        strip.paste(im, (i * 20, 0))
    strip.resize((strip.width * 8, strip.height * 8), Image.NEAREST).save(os.path.join(out, "cart_icons.png"))


def main():
    os.makedirs(os.path.join(ASSETS, "textures/item"), exist_ok=True)
    for name, im in (("cart_wheel", wheel_icon()), ("cart_bed", bed_icon()), ("cart", cart_icon())):
        im.save(os.path.join(ASSETS, "textures/item", name + ".png"))
        write_json(os.path.join(ASSETS, "models/item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "evolvedwarfare:item/" + name}})
    os.makedirs(os.path.join(ASSETS, "textures/gui/container"), exist_ok=True)
    gui().save(os.path.join(ASSETS, "textures/gui/container/cart.png"))
    for name, recipe in RECIPES.items():
        write_json(os.path.join(DATA, "recipe", name + ".json"), recipe)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        preview(PREVIEW)
    print("carreta: iconos, pantalla, recetas y textos")


if __name__ == "__main__":
    main()
