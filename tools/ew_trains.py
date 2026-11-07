"""El tren de vapor (fase 51): la locomotora y los vagones (pasajeros, mercancías y ténder), del tamaño de una vagoneta.

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


GREEN = (46, 92, 58)
CREAM = (214, 200, 160)
GLASS = (60, 86, 104)
BROWN = (110, 52, 34)
GRAY = (70, 70, 76)

# Los modelos van como los de una vagoneta (la de vanilla): a lo largo del eje x, de -12 a 12 (1,5 bloques), de ancho -7
# a 7, con y hacia abajo: la vía está en y=6 y lo de arriba en negativo (y=-16, 1,4 bloques sobre la vía).


def wheels(a, xs, r, z=6.5, y=None):
    y = 6 - r if y is None else y
    for k, x in enumerate(xs):
        for side in (-1, 1):
            w = a.part("wheel_%d_%d" % (k, side + 1), "frame", (x, y, side * z), role="wheel")
            w.cube((-r, -r, -0.5), (2 * r, 2 * r, 1), **C(DARK, rings=(RED, 1), spots=((90, 88, 92), 0.1)))
            w.cube((-0.6, -0.6, -0.8 * side - 0.3), (1.2, 1.2, 0.6), **C(BRASS))


def chassis(a, length=12, bogies=True):
    frame = a.part("frame", None, (0, 0, 0), role="body")
    frame.cube((-length, 1, -6), (2 * length, 2, 12), **C(IRON, rings=((80, 78, 82), 1)))       # el bastidor
    for x in (-length - 1, length):
        frame.cube((x, 1, -5), (1, 2, 10), **C(RED))                                                 # las vigas de los topes
        for z in (-4, 3):
            frame.cube((x - 0.5 if x < 0 else x + 0.5, 1.5, z), (1, 1, 1), **C(DARK))                # los topes
    if bogies:
        for x in (-8, 8):
            frame.cube((x - 3, 3, -6), (6, 1, 12), **C(DARK))                                         # los bogies
        wheels(a, (-10, -6, 6, 10), 2)
    return frame


def locomotive():
    a = W.Animal("steam_locomotive")
    frame = chassis(a, 12, bogies=False)
    frame.cube((-14, 2, -6), (2, 4, 12), **C(RED, low=(DARK, 1)))                     # el quitapiedras
    wheels(a, (-6, 0, 6), 3)
    wheels(a, (-10.5,), 1.8, z=5)
    for side in (-1, 1):
        frame.cube((-9, 2.5, side * 7 - 0.5), (16, 1, 1), **C((150, 150, 158)))       # las bielas
    boiler = a.part("boiler", "frame", (0, 0, 0), role="none")
    boiler.cube((-12, -8, -5), (16, 9, 10), **C(DARK, high=((60, 60, 66), 1)))                     # la caldera
    for x in (-9, -4, 1):
        boiler.cube((x, -8.3, -5.3), (0.6, 9.6, 10.6), **C(BRASS))                                   # sus aros
    boiler.cube((-13, -8.5, -5.5), (2, 10, 11), **C((34, 32, 36), rings=(BRASS, 1)))                # la caja de humos
    boiler.cube((-11, -14, -1.5), (3, 6, 3), **C(DARK))                                             # la chimenea
    boiler.cube((-11.5, -16, -2), (4, 2, 4), **C(DARK, rings=(BRASS, 1)))
    boiler.cube((-4, -10, -1.5), (3, 2, 3), **C(BRASS))                                             # el domo
    boiler.cube((0, -9.5, -1.2), (2.5, 1.5, 2.4), **C(BRASS))                                       # el arenero
    boiler.cube((-14, -5, -1), (1, 2, 2), **C((255, 240, 180)))                                     # el farol
    for side in (-1, 1):
        boiler.cube((-11, -1, side * 6 - (1 if side > 0 else 2) + 0.5), (6, 2.5, 2.5), **C(IRON, rings=(BRASS, 1)))   # los cilindros
    cab = a.part("cab", "frame", (0, 0, 0), role="none")
    cab.cube((4, -13, -7), (1, 14, 14), **C(RED, spots=((110, 22, 20), 0.05)))                  # la pared delantera
    for side in (-1, 1):
        z = 6 if side > 0 else -7
        cab.cube((5, -13, z), (7, 4, 1), **C(RED))                                                  # lo alto de los lados
        cab.cube((5, -5, z), (7, 6, 1), **C(RED))                                                   # lo bajo
        cab.cube((5, -9, z), (1, 4, 1), **C(DARK))                                                  # los postes (ventana)
        cab.cube((11, -9, z), (1, 4, 1), **C(DARK))
    cab.cube((3, -14.5, -7.5), (10, 1.5, 15), **C(DARK, low=(RED, 1)))                         # el techo
    cab.cube((4.6, -4, -1.5), (0.6, 3, 3), **C((255, 140, 40)))                                     # la puerta del hogar
    return a


def passenger():
    a = W.Animal("passenger_car")
    chassis(a)
    body = a.part("body", "frame", (0, 0, 0), role="none")
    body.cube((-11.5, -12, -6.5), (23, 13, 13), **C(GREEN, low=((30, 62, 40), 2)))   # la caja
    for side in (-1, 1):
        z = 6.6 if side > 0 else -6.8
        for x in (-9, -4, 1, 6):
            body.cube((x, -10, z), (3, 4, 0.2), **C(GLASS, high=((120, 150, 170), 1)))                # las ventanas
        body.cube((-11.5, -5, z), (23, 0.5, 0.2), **C(CREAM))                                           # la franja
    for x in (-12.2, 11.4):
        body.cube((x, -9, -2), (0.8, 9, 4), **C(BROWN))                                                 # las puertas de los extremos
    body.cube((-12.5, -14, -7.2), (25, 2, 14.4), **C(GRAY, high=((96, 96, 104), 1)))                  # el techo
    body.cube((-6, -15, -3), (12, 1, 6), **C(GRAY))                                                     # la linterna del techo
    return a


def freight():
    a = W.Animal("freight_car")
    chassis(a)
    body = a.part("body", "frame", (0, 0, 0), role="none")
    body.cube((-11.5, -12, -6.5), (23, 13, 13), **C(BROWN, rings=((84, 38, 24), 1), spots=((130, 64, 42), 0.06)))   # el furgón
    for side in (-1, 1):
        z = 6.6 if side > 0 else -6.8
        body.cube((-3.5, -11, z), (7, 11, 0.2), **C((92, 42, 28), rings=((60, 28, 18), 1)))                           # la puerta corredera
        body.cube((-4.5, -11.5, z), (9, 0.6, 0.3), **C(DARK))                                                          # su riel
        body.cube((-0.5, -6, z + 0.1 * side), (1, 1, 0.3), **C(BRASS))                                                 # el tirador
        for x in (-11, -6.5, 6.5, 11):
            body.cube((x - 0.25, -12, z), (0.5, 13, 0.2), **C(DARK))                                                    # los herrajes
    body.cube((-12, -13.5, -7.2), (24, 1.5, 14.4), **C((60, 30, 22)))                                                  # el techo
    body.cube((-12, -14, -0.5), (24, 0.5, 1), **C(DARK))                                                               # la pasarela
    return a


def tender():
    a = W.Animal("coal_tender")
    chassis(a, 10)
    body = a.part("body", "frame", (0, 0, 0), role="none")
    body.cube((-10, -5, -6.5), (20, 6, 13), **C(RED, low=(DARK, 1), rings=((110, 22, 20), 1)))    # la caja
    body.cube((-9.5, -6.5, -6), (19, 1.5, 12), **C((20, 18, 20), spots=((60, 56, 58), 0.35)))     # el carbón
    for x in (-4, 3):
        body.cube((x, -7.5, -3), (3, 1, 3), **C((24, 22, 24), spots=((70, 66, 68), 0.3)))         # los montones
    body.cube((5, -9, -6.5), (5, 4, 13), **C(RED))                                                  # el depósito de agua
    body.cube((6, -9.5, -1), (2, 0.5, 2), **C(BRASS))                                               # la tapa
    return a


MODELS = collections.OrderedDict([("steam_locomotive", locomotive), ("passenger_car", passenger), ("freight_car", freight), ("coal_tender", tender)])
TEXTS = {
    "entity.evolvedwarfare.steam_locomotive": ("Locomotora de vapor", "Steam Locomotive"),
    "entity.evolvedwarfare.passenger_car": ("Vagón de pasajeros", "Passenger Car"),
    "entity.evolvedwarfare.freight_car": ("Vagón de mercancías", "Freight Car"),
    "entity.evolvedwarfare.coal_tender": ("Ténder", "Coal Tender"),
    "item.evolvedwarfare.steam_locomotive": ("Locomotora de vapor", "Steam Locomotive"),
    "item.evolvedwarfare.steam_locomotive.desc": ("En una vía. Agachado + clic derecho: la caldera (carbón). Súbete: W y S, el regulador; espacio, el silbato.",
                                                  "On a rail. Sneak + right click: the firebox (coal). Ride it: W and S, the throttle; space, the whistle."),
    "item.evolvedwarfare.passenger_car": ("Vagón de pasajeros", "Passenger Car"),
    "item.evolvedwarfare.passenger_car.desc": ("Dos asientos. Ponlo en la vía justo detrás del tren y se engancha solo.",
                                               "Two seats. Place it on the rail right behind the train and it couples itself."),
    "item.evolvedwarfare.freight_car": ("Vagón de mercancías", "Freight Car"),
    "item.evolvedwarfare.freight_car.desc": ("54 huecos. Ponlo en la vía justo detrás del tren y se engancha solo.",
                                             "54 slots. Place it on the rail right behind the train and it couples itself."),
    "item.evolvedwarfare.coal_tender": ("Ténder", "Coal Tender"),
    "item.evolvedwarfare.coal_tender.desc": ("27 huecos de combustible: la locomotora tira de él cuando se le acaba el suyo.",
                                             "27 fuel slots: the locomotive draws from it when its own runs out."),
    "container.evolvedwarfare.steam_locomotive": ("Caldera", "Firebox"),
    "gui.evolvedwarfare.locomotive.fuel": ("Fuego: %s:%s", "Fire: %s:%s"),
    "gui.evolvedwarfare.locomotive.speed": ("%s,%s km/h", "%s.%s km/h"),
    "message.evolvedwarfare.train.notch.forward": ("Avante %s", "Ahead %s"),
    "message.evolvedwarfare.train.notch.reverse": ("Atrás %s", "Astern %s"),
    "message.evolvedwarfare.train.notch.neutral": ("Parado", "Stopped"),
    "message.evolvedwarfare.train.dash": ("%s km/h · %s · fuego para %s min", "%s km/h · %s · fire for %s min"),
    "message.evolvedwarfare.train.coupled": ("Enganchado al tren", "Coupled to the train"),
    "message.evolvedwarfare.train.uncoupled": ("Desenganchado", "Uncoupled"),
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
    for name in MODELS:
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    # lo del tren grande de antes
    for old in ("locomotive", "flatcar"):
        for f in (os.path.join(tex_dir, old + ".png"), os.path.join(tex_dir, old + "_glow.png"), os.path.join(ASSETS, "hell/%s.json" % old),
                  os.path.join(ASSETS, "models/item/%s.json" % old), os.path.join(ASSETS, "textures/item/%s.png" % old),
                  os.path.join(DATA, "recipe/%s.json" % old)):
            if os.path.exists(f):
                os.remove(f)
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

    def shaped(name, pattern, key):
        write_json(os.path.join(R, name + ".json"), {"type": "minecraft:crafting_shaped", "category": "transportation", "pattern": pattern,
                                                    "key": key, "result": {"id": "%s:%s" % (NS, name), "count": 1}})
    shaped("steam_locomotive", ["I  ", "IFI", "IMI"], {"I": {"item": "minecraft:iron_ingot"}, "F": {"item": "minecraft:furnace"},
                                                       "M": {"item": "minecraft:minecart"}})
    shaped("passenger_car", ["PGP", "PMP"], {"P": {"tag": "minecraft:planks"}, "G": {"item": "minecraft:glass_pane"}, "M": {"item": "minecraft:minecart"}})
    shaped("freight_car", ["PCP", "PMP"], {"P": {"tag": "minecraft:planks"}, "C": {"item": "minecraft:chest"}, "M": {"item": "minecraft:minecart"}})
    shaped("coal_tender", ["I I", "IMI"], {"I": {"item": "minecraft:iron_ingot"}, "M": {"item": "minecraft:chest_minecart"}})
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        sheet = Image.new("RGB", (1200, 300), (70, 74, 80))
        for k, (mid, parts, img) in enumerate(previews):
            sheet.paste(render_entity(parts, img, size=300, yaw=35.0, pitch=15.0, center="auto"), (k * 300, 0))
        sheet.save(os.path.join(PREVIEW, "trains.png"))
    print("el tren de vapor: locomotora, vagón de pasajeros, de mercancías y ténder")


if __name__ == "__main__":
    main()
