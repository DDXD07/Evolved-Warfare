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

# Los modelos van como los de una vagoneta (la de vanilla): a lo largo del eje x, de -14 a 14 (1,75 bloques), de ancho
# -8 a 8 (un bloque), con y hacia abajo: la vía está en y=6 y lo de arriba en negativo (hasta y=-18, 1,5 bloques).
# Todas las cajas miden píxeles enteros: con medidas con decimales (0,2, 0,6...) Minecraft coloca mal la textura de cada
# cara y se veían franjas y colores de otras piezas.
BLACK = (30, 30, 34)
STEEL = (120, 122, 130)
COPPER = (176, 98, 56)
LAMP = (255, 236, 160)
FIRE = (255, 140, 40)
NAVY = (36, 48, 86)
RUST = (118, 50, 34)
COAL = (22, 20, 22)


def wheel_set(a, xs, r, z=6, name="w"):
    """Ruedas de radio r (diámetro 2r) en esas x, a los dos lados (de z a z+1 y de -z-1 a -z), con su cubo y su contrapeso."""
    for k, x in enumerate(xs):
        for side in (-1, 1):
            zz = z if side > 0 else -z - 1
            w = a.part("%s%d_%d" % (name, k, side + 1), "frame", (x, 6 - r, zz), role="wheel")
            w.cube((-r, -r, 0), (2 * r, 2 * r, 1), **C(DARK, rings=(RED if r >= 3 else (60, 58, 62), 1), spots=((70, 68, 72), 0.08)))
            w.cube((-1, -1, side), (2, 2, 1), **C(STEEL, high=((180, 180, 188), 1)))                       # el cubo
            if r >= 3:
                w.cube((-r + 1, 0, 0), (2, 2, 1), **C(BLACK))                                                    # el contrapeso


def frame_and_bogies(a, half=14, bogie_x=9):
    frame = a.part("frame", None, (0, 0, 0), role="body")
    frame.cube((-half, 1, -6), (2 * half, 2, 12), **C(IRON, rings=((70, 68, 72), 1)))           # el bastidor
    for x in (-half - 1, half):
        frame.cube((x, 0, -7), (1, 3, 14), **C(RED, low=(DARK, 1)))                            # las vigas de los topes
        bx = x - 1 if x < 0 else x + 1
        for z in (-6, 4):
            frame.cube((bx, 1, z), (1, 2, 2), **C(STEEL))                                        # los topes
        frame.cube((bx, 1, -1), (1, 1, 2), **C(DARK))                                            # el enganche
    for x in (-bogie_x, bogie_x):
        frame.cube((x - 4, 3, -7), (8, 1, 14), **C(DARK))                                         # los bogies
        frame.cube((x - 1, 2, -7), (2, 2, 1), **C(STEEL))                                         # las cajas de grasa
        frame.cube((x - 1, 2, 6), (2, 2, 1), **C(STEEL))
    wheel_set(a, (-bogie_x - 2, -bogie_x + 2, bogie_x - 2, bogie_x + 2), 2, z=6)
    return frame


def locomotive():
    a = W.Animal("steam_locomotive")
    frame = a.part("frame", None, (0, 0, 0), role="body")
    frame.cube((-14, 1, -6), (28, 2, 12), **C(IRON, rings=((70, 68, 72), 1)))
    frame.cube((-15, -1, -7), (1, 4, 14), **C(RED, low=(DARK, 1)))                              # la viga delantera
    frame.cube((14, 0, -7), (1, 3, 14), **C(RED, low=(DARK, 1)))
    for z in (-6, 4):
        frame.cube((-16, 0, z), (1, 2, 2), **C(STEEL))
        frame.cube((15, 1, z), (1, 2, 2), **C(STEEL))
    frame.cube((15, 1, -1), (1, 1, 2), **C(DARK))
    for k in range(3):                                                                          # el quitapiedras, en escalones
        frame.cube((-16 - k, 3 + k, -6 + k), (1, 1, 12 - 2 * k), **C(RED, rings=(DARK, 1)))
    frame.cube((-14, 0, -8), (18, 1, 2), **C(BLACK))                                            # los estribos
    frame.cube((-14, 0, 6), (18, 1, 2), **C(BLACK))
    wheel_set(a, (-11,), 2, z=5, name="lead")                                                   # las ruedas guía
    wheel_set(a, (-5, 1, 7), 3, z=6, name="drive")                                              # las motrices
    for side in (-1, 1):
        z = 7 if side > 0 else -8
        frame.cube((-5, 2, z), (13, 1, 1), **C(STEEL, high=((190, 190, 198), 1)))               # la biela de acoplamiento
        frame.cube((-11, 1, z), (7, 1, 1), **C(STEEL))                                           # la biela motriz
        frame.cube((-13, -2, -7 if side < 0 else 4), (5, 3, 3), **C(BLACK, rings=(BRASS, 2)))   # los cilindros
    boiler = a.part("boiler", "frame", (0, 0, 0), role="none")
    boiler.cube((-13, -8, -5), (17, 8, 10), **C(BLACK, high=((54, 54, 60), 1), low=((20, 20, 24), 1)))   # la caldera
    boiler.cube((-13, -9, -4), (17, 1, 8), **C((44, 44, 50)))                                            # su lomo, redondeado
    boiler.cube((-13, -7, -6), (17, 6, 1), **C(BLACK, high=((54, 54, 60), 1)))                           # y sus costados
    boiler.cube((-13, -7, 5), (17, 6, 1), **C(BLACK, high=((54, 54, 60), 1)))
    for x in (-10, -5, 0):
        boiler.cube((x, -10, -6), (1, 10, 12), **C(BRASS, high=((236, 204, 120), 1)))                      # los aros de latón (sobresalen)
    boiler.cube((-15, -9, -5), (2, 9, 10), **C((36, 36, 40), rings=(STEEL, 3)))                           # la caja de humos
    boiler.cube((-16, -7, -3), (1, 6, 6), **C((48, 48, 54), high=(STEEL, 1)))                             # su puerta
    boiler.cube((-17, -5, -1), (1, 2, 2), **C(STEEL))                                                     # el tirador
    boiler.cube((-16, -11, -2), (2, 2, 4), **C(DARK))                                                     # el farol
    boiler.cube((-17, -11, -1), (1, 2, 2), **C(LAMP))
    boiler.cube((-13, -15, -2), (3, 6, 3), **C(BLACK, high=((60, 60, 66), 1)))                            # la chimenea
    boiler.cube((-14, -17, -3), (5, 2, 5), **C(BLACK, rings=(COPPER, 1)))                                 # su boca, ensanchada
    boiler.cube((-6, -12, -2), (4, 3, 4), **C(BRASS, high=((240, 214, 130), 1)))                          # el domo de vapor
    boiler.cube((-1, -11, -2), (3, 2, 4), **C(BLACK, rings=(BRASS, 1)))                                   # el arenero
    boiler.cube((3, -12, -1), (1, 3, 1), **C(BRASS))                                                      # el silbato
    boiler.cube((3, -10, 1), (1, 1, 1), **C(BRASS))                                                       # la válvula de seguridad
    for z in (-6, 5):
        boiler.cube((-13, -5, z), (16, 1, 1), **C(BRASS))                                                 # los pasamanos
    cab = a.part("cab", "frame", (0, 0, 0), role="none")
    red = C(RED, spots=((110, 22, 20), 0.04), low=((96, 20, 18), 1))
    # la pared delantera, con dos ventanas a los lados de la caldera
    cab.cube((4, -16, -8), (1, 4, 16), **red)
    cab.cube((4, -12, -8), (1, 12, 2), **red)
    cab.cube((4, -12, 6), (1, 12, 2), **red)
    cab.cube((4, -12, -3), (1, 12, 6), **red)
    cab.cube((4, -9, -6), (1, 9, 3), **red)
    cab.cube((4, -9, 3), (1, 9, 3), **red)
    for side in (-1, 1):
        z = 7 if side > 0 else -8
        cab.cube((5, -7, z), (10, 7, 1), **red)                                                 # lo bajo de los lados
        cab.cube((5, -16, z), (10, 2, 1), **red)                                                # lo alto
        cab.cube((5, -14, z), (1, 7, 1), **C(DARK))                                             # los postes (la ventana)
        cab.cube((14, -14, z), (1, 7, 1), **C(DARK))
        cab.cube((8, -5, z + side), (4, 3, 1), **C(BRASS, rings=((150, 110, 40), 1)))         # la placa del número
    cab.cube((3, -18, -9), (13, 2, 18), **C(BLACK, low=(RED, 1), high=((56, 56, 62), 1)))     # el techo, con alero
    cab.cube((7, -19, -2), (5, 1, 4), **C(BLACK))                                              # la claraboya
    cab.cube((5, -8, -4), (2, 8, 8), **C(BLACK, rings=(BRASS, 3)))                             # el frente del hogar
    cab.cube((6, -5, -1), (2, 2, 2), **C(FIRE, high=((255, 210, 120), 1)))                     # la puerta del hogar, encendida
    cab.cube((6, -11, -3), (1, 2, 2), **C(BRASS))                                              # los manómetros
    cab.cube((6, -11, 1), (1, 2, 2), **C(BRASS))
    cab.cube((11, -2, -6), (3, 2, 12), **C(COAL, spots=((70, 66, 70), 0.3)))                   # el carbón del fogonero
    return a


def passenger():
    a = W.Animal("passenger_car")
    frame_and_bogies(a)
    body = a.part("body", "frame", (0, 0, 0), role="none")
    green = C(GREEN, low=((30, 62, 40), 1), spots=((52, 104, 66), 0.03))
    body.cube((-14, 0, -8), (28, 1, 16), **C(DARK))                                                # el suelo
    for side in (-1, 1):
        z = 7 if side > 0 else -8
        body.cube((-14, -6, z), (28, 6, 1), **green)                                               # el zócalo
        body.cube((-14, -7, z), (28, 1, 1), **C(CREAM))                                            # la moldura
        body.cube((-14, -14, z), (28, 2, 1), **C(CREAM, low=((180, 164, 124), 1)))                 # el friso
        for x in (-14, -9, -4, 1, 6, 11):
            body.cube((x, -12, z), (2 if x == 11 else 1, 5, 1), **green)                            # los postes de las ventanas
        body.cube((-13, -12, z - side), (26, 5, 1), **C(GLASS, high=((126, 156, 176), 1)))         # el cristal (hundido)
    for x in (-15, 14):
        body.cube((x, -14, -8), (1, 14, 16), **C(BROWN, rings=((84, 38, 24), 2)))                   # los testeros
        body.cube((x - 1 if x < 0 else x + 1, -11, -2), (1, 10, 4), **C((92, 42, 28), high=((130, 64, 42), 1)))   # las puertas
        body.cube((x - 1 if x < 0 else x + 1, -6, 1), (1, 1, 1), **C(BRASS))
    body.cube((-11, -5, -6), (22, 3, 12), **C((96, 56, 34), rings=((70, 40, 24), 2)))             # los asientos (por las ventanas)
    body.cube((-16, -16, -9), (32, 2, 18), **C(GRAY, high=((100, 100, 108), 1), low=((54, 54, 60), 1)))   # el techo
    body.cube((-11, -18, -5), (22, 2, 10), **C(GRAY, rings=(GLASS, 3)))                            # la linterna del techo
    for x in (-8, 7):
        body.cube((x, -19, -1), (2, 1, 2), **C(DARK))                                             # las chimeneas de las lámparas
    return a


def freight():
    a = W.Animal("freight_car")
    frame_and_bogies(a)
    body = a.part("body", "frame", (0, 0, 0), role="none")
    body.cube((-14, -14, -8), (28, 15, 16), **C(RUST, rings=((96, 40, 26), 2), spots=((140, 66, 44), 0.05)))   # el furgón de tablas
    for side in (-1, 1):
        z = 8 if side > 0 else -9
        for x in (-14, -9, 8, 13):
            body.cube((x, -14, z), (1, 15, 1), **C(DARK))                                         # los montantes de hierro
        body.cube((-4, -12, z), (8, 12, 1), **C((96, 42, 28), rings=((70, 30, 20), 2)))           # la puerta corredera
        body.cube((-5, -13, z), (10, 1, 1), **C(DARK))                                            # su riel de arriba
        body.cube((-5, 0, z), (10, 1, 1), **C(DARK))                                              # y el de abajo
        body.cube((2, -7, z + side), (1, 2, 1), **C(BRASS))                                       # el tirador
        body.cube((-4, -12, z + side), (8, 1, 1), **C(DARK))                                      # los refuerzos de la puerta
        body.cube((-4, -1, z + side), (8, 1, 1), **C(DARK))
        body.cube((-12, -10, z + side), (3, 2, 1), **C(CREAM))                                    # el rótulo
    for x in (-15, 14):
        for y in (-12, -9, -6, -3):
            body.cube((x if x < 0 else x, y, 5), (1, 1, 2), **C(STEEL))                            # los peldaños de la escalera
    body.cube((15, -12, -2), (1, 3, 3), **C(BRASS, rings=(DARK, 1)))                              # el volante del freno
    body.cube((-15, -16, -9), (30, 2, 18), **C((70, 34, 24), low=((50, 24, 16), 1)))             # el techo
    body.cube((-14, -17, -1), (28, 1, 2), **C(WOOD, rings=((90, 62, 36), 2)))                     # la pasarela
    return a


def tender():
    a = W.Animal("coal_tender")
    frame_and_bogies(a, half=12, bogie_x=7)
    body = a.part("body", "frame", (0, 0, 0), role="none")
    tank = C(BLACK, high=((54, 54, 60), 1))
    body.cube((-12, -10, -8), (24, 10, 16), **tank)                                              # el depósito
    body.cube((-13, -1, -9), (26, 1, 18), **C(RED, low=(DARK, 1)))                               # el zócalo rojo
    for side in (-1, 1):
        z = 7 if side > 0 else -8
        body.cube((-12, -12, z), (14, 2, 1), **C(BLACK, high=(RED, 1)))                         # las bordas del carbón
        body.cube((-12, -5, z + side), (24, 1, 1), **C(BRASS))                                   # la franja de latón
        body.cube((-6, -9, z + side), (6, 3, 1), **C(RED, rings=(BRASS, 1)))                    # la placa
    body.cube((-13, -12, -8), (1, 2, 16), **C(BLACK, high=(RED, 1)))                            # la borda de delante
    body.cube((-12, -12, -7), (14, 2, 14), **C(COAL, spots=((76, 72, 76), 0.35)))                # el carbón
    for (x, z, w) in ((-10, -5, 5), (-4, 1, 4), (-9, 2, 3)):
        body.cube((x, -13, z), (w, 1, w - 1), **C(COAL, spots=((90, 86, 90), 0.4)))              # los montones
    body.cube((2, -13, -8), (10, 3, 16), **C(BLACK, high=((58, 58, 64), 1)))                    # el depósito de agua (detrás, más alto)
    body.cube((6, -14, -2), (3, 1, 4), **C(BRASS))                                               # su boca
    body.cube((12, -9, -7), (1, 9, 2), **C(DARK))                                                # la escalera de atrás
    body.cube((-13, -8, 4), (1, 2, 3), **C(WOOD))                                                # la caja de herramientas
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
