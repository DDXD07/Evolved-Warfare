"""El tren de vapor (fase 41): la locomotora y la plataforma.

Los modelos van a media escala (1 unidad = 1/8 de bloque; el renderer los dibuja al doble), con el frente hacia -z como
todos los del mod. La locomotora: bastidor, cuatro ruedas motrices por lado con sus bielas, caldera con aros de latón,
caja de humos, chimenea, domo, farol, quitapiedras, cabina con techo y la carbonera. La plataforma: un tablero de 5 x 9,5
con sus vigas, topes y dos bogies.

Uso: python3 tools/ew_trains.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ew_wildlife as W  # noqa: E402
import ew_demons as D  # noqa: E402
from ew_model3d import render_entity  # noqa: E402

PREVIEW = W.PREVIEW
ROOT = W.ROOT
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
C = W.C
write_json = D.write_json

IRON = (46, 44, 48)
DARK = (26, 24, 28)
RED = (140, 30, 26)
BRASS = (200, 160, 70)
WOOD = (120, 84, 50)


def wheels(a, zs, r, x):
    for k, z in enumerate(zs):
        for side in (-1, 1):
            w = a.part("wheel_%d_%d" % (k, side + 1), "frame", (side * x, 20 - 14, z), role="wheel")
            w.cube((-1, -r, -r), (2, 2 * r, 2 * r), **C(DARK, rings=(RED, 1), spots=((90, 88, 92), 0.1)))
            w.cube((-1.2, -1, -1), (2.4, 2, 2), **C(BRASS))


def locomotive():
    a = W.Animal("locomotive")
    frame = a.part("frame", None, (0, 14, 0), role="body")
    frame.cube((-12, 0, -48), (24, 4, 96), **C(IRON, rings=((80, 78, 82), 1)))
    frame.cube((-12, 4, -52), (24, 5, 4), **C(RED, low=(DARK, 1)))                       # el quitapiedras
    frame.cube((-12, -1, 46), (24, 6, 3), **C(RED))                                       # la viga de atrás
    for side in (-1, 1):
        frame.cube((side * 10 - 1.5, 0, -51), (3, 3, 3), **C(DARK))                          # los topes
    wheels(a, (-30, -14, 2, 18), 6, 11)
    for side in (-1, 1):
        a.part("rod_%d" % (side + 1), "frame", (side * 12.6, 6, -6), role="rod", side=side).cube((-0.5, -0.75, -25), (1, 1.5, 50),
                                                                                                    **C((150, 150, 158)))
    boiler = a.part("boiler", "frame", (0, 0, 0), role="none")
    boiler.cube((-8, -16, -44), (16, 16, 60), **C(DARK, bands=(BRASS, 10), high=((60, 60, 66), 2)))
    boiler.cube((-8.5, -16.5, -47), (17, 17, 4), **C((34, 32, 36), rings=(BRASS, 1)))  # la caja de humos
    boiler.cube((-2, -23, -40), (4, 7, 4), **C(DARK))                                    # la chimenea
    boiler.cube((-3, -27, -41), (6, 4, 6), **C(DARK, rings=(BRASS, 1)))
    boiler.cube((-3, -19, -22), (6, 3, 6), **C(BRASS))                                   # el domo
    boiler.cube((-2.5, -18.5, -6), (5, 2.5, 5), **C(BRASS))                              # el arenero
    boiler.cube((-2, -10, -49), (4, 4, 2), **C((255, 240, 180), glow_all=True))          # el farol
    for side in (-1, 1):
        boiler.cube((side * 8 if side > 0 else -11, -3, -30), (3, 3, 20), **C(IRON, rings=(BRASS, 1)))   # los cilindros
    cab = a.part("cab", "frame", (0, 0, 0), role="none")
    cab.cube((-12, -28, 16), (24, 28, 2), **C(RED, spots=((110, 22, 20), 0.05)))         # la pared delantera
    for side in (-1, 1):
        x = 11 if side > 0 else -12
        cab.cube((x, -28, 18), (1, 10, 22), **C(RED))                                        # la parte alta de los lados
        cab.cube((x, -10, 34), (1, 10, 6), **C(RED))
        cab.cube((x, -28, 39), (1, 28, 1), **C(DARK))                                        # los postes
    cab.cube((-13, -30, 14), (26, 2, 28), **C(DARK, low=(RED, 1)))                       # el techo
    cab.cube((-3, -8, 17.5), (6, 5, 1), **C((255, 140, 40), glow_all=True))              # la puerta del hogar
    cab.cube((-11, -14, 40), (22, 14, 8), **C((20, 18, 20), spots=((60, 56, 58), 0.3)))  # la carbonera
    return a


def flatcar():
    a = W.Animal("flatcar")
    frame = a.part("frame", None, (0, 14, 0), role="body")
    frame.cube((-20, 0, -38), (40, 4, 76), **C(WOOD, rings=((90, 62, 36), 1), spots=((150, 110, 70), 0.05)))   # el tablero
    for side in (-1, 1):
        frame.cube((side * 19 - 1, 4, -38), (2, 3, 76), **C(IRON))                                                   # las vigas
    for z in (-39, 37):
        frame.cube((-6, 0, z), (12, 4, 2), **C(RED))                                                                # los topes
    wheels(a, (-32, -24, 24, 32), 4, 9)
    for z in (-28, 28):
        frame.cube((-10, 4, z - 6), (20, 2, 12), **C(DARK))                                                         # los bogies
    return a


MODELS = collections.OrderedDict([("locomotive", locomotive), ("flatcar", flatcar)])
TEXTS = {
    "entity.evolvedwarfare.locomotive": ("Locomotora de vapor", "Steam Locomotive"),
    "entity.evolvedwarfare.flatcar": ("Plataforma", "Flatcar"),
    "item.evolvedwarfare.locomotive": ("Locomotora de vapor", "Steam Locomotive"),
    "item.evolvedwarfare.locomotive.desc": ("Ponla en una vía. Quema bloques de carbón; súbete con clic derecho, W acelera, S frena, espacio silba.",
                                            "Place it on a rail. Burns coal blocks; right click to board, W speeds up, S brakes, space whistles."),
    "item.evolvedwarfare.flatcar": ("Plataforma", "Flatcar"),
    "item.evolvedwarfare.flatcar.desc": ("Clic derecho en una locomotora (o en un vagón de su tren) para engancharla detrás. Construye encima lo que quieras.",
                                         "Right click a locomotive (or a car of its train) to couple it behind. Build anything on it."),
    "message.evolvedwarfare.train.dash": ("%s km/h · regulador %s %% · carbón: %s bloques", "%s km/h · throttle %s %% · coal: %s blocks"),
    "message.evolvedwarfare.train.fuel": ("Carbón: %s bloques", "Coal: %s blocks"),
    "message.evolvedwarfare.train.full": ("La carbonera está llena", "The coal bunker is full"),
    "message.evolvedwarfare.train.no_room": ("No cabe otra plataforma (vía corta, tren en marcha o máximo de 12)",
                                             "No room for another flatcar (short track, moving train or 12 max)"),
    "message.evolvedwarfare.train.not_empty": ("Vacía la plataforma antes de desengancharla", "Empty the flatcar before uncoupling it"),
    "message.evolvedwarfare.train.no_sleep": ("No se puede dormir en un tren en marcha", "You can't sleep on a moving train"),
    "message.evolvedwarfare.train.couple_hint": ("Engancha la plataforma a una locomotora (clic derecho en ella)", "Couple the flatcar to a locomotive (right click it)"),
    "message.evolvedwarfare.train.not_buildable": ("Eso no se puede poner en el tren (cofres, puertas, camas, líquidos...)",
                                                   "That cannot go on the train (chests, doors, beds, liquids...)"),
}


def pack(a):
    """Como Animal.pack, pero para modelos grandes (hasta 1024 de ancho)."""
    import math
    cubes = [c for p in a.parts for c in p.cubes]
    boxes = sorted(cubes, key=lambda c: -(c.size[2] + c.size[1]))
    for width in (256, 512, 1024):
        x = y = shelf = 0
        ok = True
        for c in boxes:
            dx, dy, dz = (math.ceil(v) for v in c.size)
            w, h = 2 * (dx + dz), dz + dy
            if w > width:
                ok = False
                break
            if x + w > width:
                x, y, shelf = 0, y + shelf, 0
            c.uv = (x, y)
            x += w
            shelf = max(shelf, h)
        height = y + shelf
        if ok and height <= width:
            th = 32
            while th < height:
                th *= 2
            return width, th
    raise ValueError("no cabe: " + a.sid)


def main():
    tex_dir = os.path.join(ASSETS, "textures/entity/train")
    os.makedirs(tex_dir, exist_ok=True)
    previews = []
    for mid, gen in MODELS.items():
        a = gen()
        tw, th = pack(a)
        img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        glow = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        rng = random.Random(mid)
        for p in a.parts:
            for c in p.cubes:
                W.paint_cube(img, c, rng)
                D.paint_extra(img, glow, c, rng)
        img.save(os.path.join(tex_dir, mid + ".png"))
        glow.save(os.path.join(tex_dir, mid + "_glow.png"))
        parts = [{"name": p.name, "parent": p.parent, "pivot": p.pivot, "rotation": p.rot, "role": p.role,
                  "cubes": [{"uv": list(c.uv), "origin": c.origin, "size": c.size} for c in p.cubes]} for p in a.parts]
        write_json(os.path.join(ASSETS, "hell/%s.json" % mid), {"texture": [tw, th], "parts": parts}, indent=None)
        previews.append((mid, parts, img))
        print("  %s: textura %dx%d" % (mid, tw, th))
    for name in ("locomotive", "flatcar"):
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    # los iconos (a partir de la vista previa del modelo)
    for mid, parts, img in previews:
        icon = render_entity(parts, img, size=64, yaw=35.0, pitch=15.0, center="auto").convert("RGBA")
        px = icon.load()
        bg = px[0, 0]
        for y in range(icon.height):
            for x in range(icon.width):
                if px[x, y][:3] == bg[:3]:
                    px[x, y] = (0, 0, 0, 0)
        icon.resize((32, 32), Image.LANCZOS).save(os.path.join(ASSETS, "textures/item/%s.png" % mid))
    R = os.path.join(DATA, "recipe")
    write_json(os.path.join(R, "locomotive.json"), {"type": "minecraft:crafting_shaped", "category": "transportation",
                                                   "pattern": ["IGI", "FBF", "MMM"],
                                                   "key": {"I": {"item": "minecraft:iron_block"}, "G": {"item": "minecraft:glass"},
                                                           "F": {"item": "minecraft:furnace"}, "B": {"item": "minecraft:blast_furnace"},
                                                           "M": {"item": "minecraft:minecart"}},
                                                   "result": {"id": "%s:locomotive" % NS, "count": 1}})
    write_json(os.path.join(R, "flatcar.json"), {"type": "minecraft:crafting_shaped", "category": "transportation", "pattern": ["SSS", "IMI"],
                                                "key": {"S": {"tag": "minecraft:wooden_slabs"}, "I": {"item": "minecraft:iron_ingot"},
                                                        "M": {"item": "minecraft:minecart"}},
                                                "result": {"id": "%s:flatcar" % NS, "count": 1}})
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        sheet = Image.new("RGB", (900, 450), (70, 74, 80))
        for k, (mid, parts, img) in enumerate(previews):
            sheet.paste(render_entity(parts, img, size=450, yaw=35.0, pitch=15.0, center="auto"), (k * 450, 0))
        sheet.save(os.path.join(PREVIEW, "trains.png"))
    print("el tren de vapor: locomotora y plataforma")


if __name__ == "__main__":
    main()
