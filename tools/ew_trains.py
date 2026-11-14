"""El tren de vapor grande (fase 52): la locomotora, el ténder y la plataforma, para las vías de dos de ancho.

Los modelos van a lo largo del eje x (el frente hacia -x), con y hacia abajo y la vía en y=6; el renderer los dibuja
grandes (la locomotora y el ténder a 2,5 veces, unos 5 y 4 bloques de largo; la plataforma al doble: 3 x 6 bloques). Todas
las cajas miden píxeles enteros (con decimales, Minecraft coloca mal la textura de sus caras).

También: los iconos (de los coches, sus partes, las vías, el martillo y las piezas de construcción, estas con las texturas
de vanilla), la mesa de ensamblaje, las recetas, lo que sueltan los bloques y los textos.

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


def frame_and_bogies(a, half=14, bogie_x=9, wz=5):
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
    wheel_set(a, (-bogie_x - 2, -bogie_x + 2, bogie_x - 2, bogie_x + 2), 2, z=wz)
    return frame


def locomotive(name="steam_locomotive", front=True):
    a = W.Animal(name)
    frame = a.part("frame", None, (0, 0, 0), role="body")
    frame.cube((-14, 1, -6), (28, 2, 12), **C(IRON, rings=((70, 68, 72), 1)))
    frame.cube((-15, -1, -7), (1, 4, 14), **C(RED, low=(DARK, 1)))                              # la viga delantera
    frame.cube((14, 0, -7), (1, 3, 14), **C(RED, low=(DARK, 1)))
    for z in (-6, 4):
        if front:
            frame.cube((-16, 0, z), (1, 2, 2), **C(STEEL))
        frame.cube((15, 1, z), (1, 2, 2), **C(STEEL))
    frame.cube((15, 1, -1), (1, 1, 2), **C(DARK))
    for k in range(3 if front else 0):                                                          # el quitapiedras, en escalones
        frame.cube((-16 - k, 3 + k, -6 + k), (1, 1, 12 - 2 * k), **C(RED, rings=(DARK, 1)))
    frame.cube((-14, 0, -8), (18, 1, 2), **C(BLACK))                                            # los estribos
    frame.cube((-14, 0, 6), (18, 1, 2), **C(BLACK))
    wheel_set(a, (-11,), 2, z=5, name="lead")                                                   # las ruedas guía
    wheel_set(a, (-5, 1, 7), 3, z=5, name="drive")                                              # las motrices
    for side in (-1, 1):
        z = 6 if side > 0 else -7
        frame.cube((-5, 2, z), (13, 1, 1), **C(STEEL, high=((190, 190, 198), 1)))               # la biela de acoplamiento
        frame.cube((-11, 1, z), (7, 1, 1), **C(STEEL))                                           # la biela motriz
        frame.cube((-13, -2, -9 if side < 0 else 6), (5, 3, 3), **C(BLACK, rings=(BRASS, 2)))   # los cilindros
    boiler = a.part("boiler", "frame", (0, 0, 0), role="none")
    boiler.cube((-13, -8, -5), (17, 8, 10), **C(BLACK, high=((54, 54, 60), 1), low=((20, 20, 24), 1)))   # la caldera
    boiler.cube((-13, -9, -4), (17, 1, 8), **C((44, 44, 50)))                                            # su lomo, redondeado
    boiler.cube((-13, -7, -6), (17, 6, 1), **C(BLACK, high=((54, 54, 60), 1)))                           # y sus costados
    boiler.cube((-13, -7, 5), (17, 6, 1), **C(BLACK, high=((54, 54, 60), 1)))
    for x in (-10, -5, 0):
        boiler.cube((x, -10, -6), (1, 10, 12), **C(BRASS, high=((236, 204, 120), 1)))                      # los aros de latón (sobresalen)
    boiler.cube((-15, -9, -5), (2, 9, 10), **C((36, 36, 40), rings=(STEEL, 3)))                           # la caja de humos
    if front:
        boiler.cube((-16, -7, -3), (1, 6, 6), **C((48, 48, 54), high=(STEEL, 1)))                         # su puerta
        boiler.cube((-17, -5, -1), (1, 2, 2), **C(STEEL))                                                 # el tirador
        boiler.cube((-16, -11, -2), (2, 2, 4), **C(DARK))                                                 # el farol
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


def borer():
    """La tuneladora de vapor (fase 64): la locomotora sin quitapiedras, con un mamparo y una cabeza perforadora de casi
    todo el túnel delante (la parte «cutter», que gira sobre el eje de la vía mientras excava)."""
    import math
    a = locomotive("steam_borer", front=False)
    frame = a.parts[0]
    # el mamparo (sostiene la cabeza) y sus tirantes hasta la caldera
    frame.cube((-17, -26, -14), (2, 30, 28), **C(DARK, rings=((70, 68, 72), 2), spots=((84, 80, 86), 0.05)))
    for z in (-13, 11):
        frame.cube((-15, -24, z), (9, 2, 2), **C(IRON, high=((80, 78, 84), 1)))
        frame.cube((-15, 0, z), (9, 2, 2), **C(IRON, high=((80, 78, 84), 1)))
    frame.cube((-15, -26, -10), (6, 2, 20), **C(IRON))
    for z in (-10, 8):
        frame.cube((-14, -3, z), (4, 4, 2), **C(BRASS, rings=((150, 110, 40), 1)))                # los pistones del empuje
    frame.cube((-12, -24, -2), (2, 7, 4), **C(LAMP))                                           # el farol de arriba
    # la cabeza, con el pivote en el eje
    cut = a.part("cutter", "frame", (-18, -12, 0), role="none")
    R = 16
    for y in range(-R, R, 2):
        h = math.sqrt(max(0.0, R * R - (y + 1) ** 2))
        w = int(round(h)) * 2
        if w >= 2:
            cut.cube((-1, y, -w // 2), (2, 2, w), **C((92, 88, 84), rings=((60, 58, 56), 1), spots=((120, 116, 110), 0.06)))
    for k in range(-R + 2, R - 1, 4):                                                          # los brazos, en cruz
        cut.cube((-2, k, -1), (1, 2, 2), **C(STEEL))
        cut.cube((-2, -1, k), (1, 2, 2), **C(STEEL))
    for ang in range(0, 360, 30):                                                              # los dientes del borde
        r = R - 2
        y = int(round(r * math.sin(math.radians(ang)))) - 1
        z = int(round(r * math.cos(math.radians(ang)))) - 1
        cut.cube((-3, y, z), (2, 2, 2), **C(STEEL, high=((200, 200, 210), 1)))
    for ang in range(15, 360, 45):                                                             # y los de dentro
        r = 8
        y = int(round(r * math.sin(math.radians(ang)))) - 1
        z = int(round(r * math.cos(math.radians(ang)))) - 1
        cut.cube((-3, y, z), (2, 2, 2), **C(STEEL, high=((200, 200, 210), 1)))
    cut.cube((-3, -3, -3), (2, 6, 6), **C(BRASS, rings=((150, 110, 40), 1)))                   # el cubo
    cut.cube((-5, -2, -2), (2, 4, 4), **C(STEEL, high=((200, 200, 210), 1)))                   # la punta
    cut.cube((-6, -1, -1), (1, 2, 2), **C((200, 200, 210)))
    return a


def tender():
    a = W.Animal("coal_tender")
    frame_and_bogies(a, half=12, bogie_x=7, wz=5)
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



def flatcar():
    """La plataforma (fase 63): un tablero de tablas de 80 x 40 (5 x 10 bloques al doble), con sus largueros, vigas de topes
    y dos bogies (a 3,5 bloques del centro)."""
    a = W.Animal("flatcar")
    frame = a.part("frame", None, (0, 0, 0), role="body")
    frame.cube((-40, -2, -20), (80, 2, 40), **C(WOOD, rings=((92, 62, 36), 3), spots=((140, 100, 62), 0.04)))   # el tablero
    for z in (-21, 20):
        frame.cube((-40, -2, z), (80, 4, 1), **C(RED, low=(DARK, 1), spots=((110, 24, 20), 0.05)))           # los largueros
        for x in range(-35, 36, 7):
            frame.cube((x, -3, z), (1, 3, 1), **C(DARK))                                                     # los cajetines de teleros
    for x in (-41, 40):
        frame.cube((x, -2, -21), (1, 5, 42), **C(RED, low=(DARK, 1)))                                        # las vigas de los topes
        bx = x - 1 if x < 0 else x + 1
        for z in (-14, 12):
            frame.cube((bx, 0, z), (1, 2, 2), **C(STEEL))                                                     # los topes
        frame.cube((bx, 0, -1), (1, 1, 2), **C(DARK))                                                         # el enganche
    frame.cube((-40, 0, -3), (80, 3, 6), **C(IRON, rings=((70, 68, 72), 1)))                                 # la viga central
    for z in (-12, 9):
        frame.cube((-40, 0, z), (80, 2, 3), **C(IRON, high=((80, 78, 84), 1)))                                # las vigas de los lados
    for x in (-28, 28):
        frame.cube((x - 6, 2, -7), (12, 1, 14), **C(DARK))                                                    # los bogies
        for z in (-8, 7):
            frame.cube((x - 6, 1, z), (12, 3, 1), **C(IRON, high=((80, 78, 84), 1)))                          # sus costados
            frame.cube((x - 1, 2, z - 1 if z < 0 else z + 1), (2, 2, 1), **C(STEEL))                          # las cajas de grasa
    wheel_set(a, (-31, -25, 25, 31), 3, z=5)
    return a


MODELS = collections.OrderedDict([("steam_locomotive", locomotive), ("coal_tender", tender), ("flatcar", flatcar), ("steam_borer", borer)])

TRACKS = [("track_straight", "Vía recta", "Straight Track", "Dos de ancho y dos de largo. Se engancha sola a la punta de vía más cercana.",
           "Two wide, two long. Snaps to the nearest loose track end."),
          ("track_slope", "Vía en cuesta", "Sloped Track", "Sube un bloque en cuatro. Agachado, baja.", "Rises one block over four. Sneak to go down."),
          ("track_curve_right", "Curva a la derecha", "Right Curve", "Curva amplia (radio 4) hacia la derecha.", "Wide curve (radius 4) to the right."),
          ("track_curve_left", "Curva a la izquierda", "Left Curve", "Curva amplia (radio 4) hacia la izquierda.", "Wide curve (radius 4) to the left."),
          ("track_switch_right", "Desvío a la derecha", "Right Switch", "Recta y curva a la derecha; clic derecho en él para cambiarlo.",
           "Straight and right curve; right click it to throw it."),
          ("track_switch_left", "Desvío a la izquierda", "Left Switch", "Recta y curva a la izquierda; clic derecho en él para cambiarlo.",
           "Straight and left curve; right click it to throw it.")]
PARTS = [("train_chassis", "Chasis de tren", "Train Chassis"), ("train_wheels", "Juego de ruedas", "Wheel Set"),
         ("steam_boiler", "Caldera de vapor", "Steam Boiler"), ("train_cab", "Cabina", "Driver's Cab"), ("coal_bunker", "Carbonera", "Coal Bunker"),
         ("drill_head", "Cabeza perforadora", "Drill Head")]

TEXTS = {
    "entity.evolvedwarfare.steam_locomotive": ("Locomotora de vapor", "Steam Locomotive"),
    "entity.evolvedwarfare.coal_tender": ("Ténder", "Coal Tender"),
    "entity.evolvedwarfare.flatcar": ("Plataforma", "Flatcar"),
    "item.evolvedwarfare.steam_locomotive": ("Locomotora de vapor", "Steam Locomotive"),
    "item.evolvedwarfare.steam_locomotive.desc": ("Ponla en una vía grande. Súbete: W y S, el regulador; espacio, el silbato. Agachado + clic derecho: la caldera.",
                                                  "Place it on a big track. Ride it: W and S, the throttle; space, the whistle. Sneak + right click: the firebox."),
    "item.evolvedwarfare.coal_tender": ("Ténder", "Coal Tender"),
    "item.evolvedwarfare.coal_tender.desc": ("Clic derecho en un coche del tren para engancharlo detrás. 27 huecos de carbón para la locomotora.",
                                             "Right click a train car to couple it at the back. 27 coal slots for the locomotive."),
    "item.evolvedwarfare.flatcar": ("Plataforma", "Flatcar"),
    "item.evolvedwarfare.flatcar.desc": ("Clic derecho en un coche del tren para engancharla detrás. 5 x 10: construye encima bloque a bloque.",
                                         "Right click a train car to couple it at the back. 5 x 10: build on it block by block."),
    "entity.evolvedwarfare.steam_borer": ("Tuneladora de vapor", "Steam Tunnel Borer"),
    "item.evolvedwarfare.steam_borer": ("Tuneladora de vapor", "Steam Tunnel Borer"),
    "item.evolvedwarfare.steam_borer.desc": ("Ponla en una vía grande. Súbete: W y S, el regulador; A, baja en cuesta; D, sube. Abre un túnel de 6 x 6 "
                                             "y pone las vías sola. Agachado + clic derecho: la caldera; con algo en la mano, el depósito (vías, carbón y lo excavado).",
                                             "Place it on a big track. Ride it: W and S, the throttle; A slopes down, D slopes up. Bores a 6 x 6 tunnel "
                                             "and lays its own track. Sneak + right click: the firebox; holding something, the hopper (track, coal and spoil)."),
    "container.evolvedwarfare.steam_borer": ("Depósito de la tuneladora", "Borer Hopper"),
    "message.evolvedwarfare.borer.dash": ("%s · %s · vías: %s", "%s · %s · track: %s"),
    "message.evolvedwarfare.borer.mode.up": ("Subiendo", "Climbing"),
    "message.evolvedwarfare.borer.mode.down": ("Bajando", "Descending"),
    "message.evolvedwarfare.borer.mode.flat": ("Recto", "Level"),
    "message.evolvedwarfare.borer.no_rails": ("Sin vías en el depósito ni en el ténder: %s", "No track in the hopper or the tender: %s"),
    "message.evolvedwarfare.borer.blocked": ("La tuneladora no puede con eso (roca irrompible o el límite del mundo)",
                                             "The borer can't get through that (unbreakable rock or the world limit)"),
    "item.evolvedwarfare.train_part.desc": ("Se monta en la mesa de ensamblaje del tren.", "Assembled at the train assembly table."),
    "item.evolvedwarfare.building_hammer": ("Martillo del tren", "Train Hammer"),
    "item.evolvedwarfare.building_hammer.desc": ("Agachado + clic derecho en un coche: desengancharlo (o recogerlo).",
                                                 "Sneak + right click a car: uncouple it (or pick it up)."),
    "block.evolvedwarfare.big_track": ("Vía grande", "Big Track"),
    "block.evolvedwarfare.train_assembly_table": ("Mesa de ensamblaje del tren", "Train Assembly Table"),
    "container.evolvedwarfare.steam_locomotive": ("Caldera", "Firebox"),
    "gui.evolvedwarfare.locomotive.fuel": ("Fuego: %s:%s", "Fire: %s:%s"),
    "gui.evolvedwarfare.locomotive.speed": ("%s,%s km/h", "%s.%s km/h"),
    "gui.evolvedwarfare.assembly.build": ("Montar", "Assemble"),
    "gui.evolvedwarfare.assembly.hint": ("Las partes se fabrican en la mesa de crafteo.", "Craft the parts at a crafting table."),
    "message.evolvedwarfare.track.no_room": ("No cabe la vía ahí", "The track doesn't fit there"),
    "message.evolvedwarfare.build.cant": ("No se puede poner ahí (no cabe o el sitio está ocupado)", "Can't place it there (no room or the spot is taken)"),
    "message.evolvedwarfare.train.no_room": ("No cabe otro vagón (vía corta, tren en marcha o máximo de 10)", "No room for another car (short track, moving train or 10 max)"),
    "message.evolvedwarfare.train.not_empty": ("Quita lo construido antes de desenganchar la plataforma", "Remove what's built before uncoupling the flatcar"),
    "message.evolvedwarfare.train.couple_hint": ("Engánchalo a un tren: clic derecho en la locomotora o en un vagón", "Couple it to a train: right click the locomotive or a car"),
    "message.evolvedwarfare.train.notch.forward": ("Avante %s", "Ahead %s"),
    "message.evolvedwarfare.train.notch.reverse": ("Atrás %s", "Astern %s"),
    "message.evolvedwarfare.train.notch.neutral": ("Parado", "Stopped"),
    "message.evolvedwarfare.train.dash": ("%s km/h · %s · fuego para %s min", "%s km/h · %s · fire for %s min"),
    "message.evolvedwarfare.train.coupled": ("Enganchado al tren", "Coupled to the train"),
    "message.evolvedwarfare.train.uncoupled": ("Desenganchado", "Uncoupled"),
}
for tid, es, en, des, den in TRACKS:
    TEXTS["item.evolvedwarfare." + tid] = (es, en)
    TEXTS["item.evolvedwarfare." + tid + ".desc"] = (des, den)
for pid, es, en in PARTS:
    TEXTS["item.evolvedwarfare." + pid] = (es, en)
OLD_KEYS = ["entity.evolvedwarfare.passenger_car", "entity.evolvedwarfare.freight_car", "item.evolvedwarfare.passenger_car",
            "item.evolvedwarfare.passenger_car.desc", "item.evolvedwarfare.freight_car", "item.evolvedwarfare.freight_car.desc",
            "entity.evolvedwarfare.locomotive", "item.evolvedwarfare.locomotive", "item.evolvedwarfare.locomotive.desc",
            "message.evolvedwarfare.train.fuel", "message.evolvedwarfare.train.full", "message.evolvedwarfare.train.not_buildable",
            "message.evolvedwarfare.train.no_sleep", "item.evolvedwarfare.piece.desc", "message.evolvedwarfare.build.hp"]
# las piezas de construcción de antes (fase 63: ahora se construye bloque a bloque)
OLD_PIECES = ["%s_%s" % (m, k) for m in ("straw", "wood", "stone", "metal")
              for k in ("wall", "doorway", "window", "ceiling", "roof", "stairs", "railing")]
OLD_KEYS += ["item.evolvedwarfare." + p for p in OLD_PIECES]
OLD_KEYS += ["gui.evolvedwarfare.build." + k for k in ("title", "craft", "max", "hp", "material.straw", "material.wood", "material.stone",
                                                       "material.metal", "hint1", "hint2")]


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


# ------------------------------------------------------------------ iconos dibujados a mano (16 x 16)

def icon(draw_fn):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c + (255,) if len(c) == 3 else c

    def rect(x0, y0, x1, y1, c):
        for yy in range(y0, y1 + 1):
            for xx in range(x0, x1 + 1):
                put(xx, yy, c)
    draw_fn(put, rect)
    return img


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def track_icon(kind):
    import math
    SLEEP = (76, 52, 32)
    RAIL = (150, 152, 160)
    GRAV = (120, 116, 110)

    def path_points(kind):
        if kind == "straight":
            return [[(8, 15 - t) for t in range(16)]]
        if kind == "slope":
            return [[(2 + t * 0.8, 14 - t * 0.8) for t in range(16)]]
        sgn = 1 if kind.endswith("right") else -1
        arc = [(8 + sgn * (9 - 9 * math.cos(a)), 15 - 9 * math.sin(a)) for a in [i / 15 * math.pi / 2 for i in range(16)]]
        if kind.startswith("switch"):
            return [[(8, 15 - t) for t in range(16)], arc]
        return [arc]

    def fn(put, rect):
        paths = path_points(kind)
        for pts in paths:
            for i in range(len(pts) - 1):
                (x0, y0), (x1, y1) = pts[i], pts[i + 1]
                dx, dy = x1 - x0, y1 - y0
                l = math.hypot(dx, dy) or 1
                nx, ny = -dy / l, dx / l
                if i % 2 == 0:
                    for k in range(-4, 5):
                        put(int(round(x0 + nx * k)), int(round(y0 + ny * k)), SLEEP if abs(k) < 4 else shade(SLEEP, 0.7))
        for pts in paths:
            for i in range(len(pts) - 1):
                (x0, y0), (x1, y1) = pts[i], pts[i + 1]
                dx, dy = x1 - x0, y1 - y0
                l = math.hypot(dx, dy) or 1
                nx, ny = -dy / l, dx / l
                for side in (-2.6, 2.6):
                    for f in (0.0, 0.5):
                        put(int(round(x0 + dx * f + nx * side)), int(round(y0 + dy * f + ny * side)), RAIL)
        if kind.startswith("switch"):
            rect(1, 11, 2, 13, (60, 40, 24))
            put(1, 10, (90, 200, 90))
            put(1, 9, (90, 200, 90))
    return icon(fn)


def part_icon(pid):
    def chassis(put, rect):
        rect(1, 7, 14, 9, IRON)
        rect(1, 6, 14, 6, shade(IRON, 1.5))
        rect(0, 5, 1, 10, RED)
        rect(14, 5, 15, 10, RED)
        for x in (4, 7, 10):
            rect(x, 6, x, 10, DARK)
        rect(3, 10, 5, 11, DARK)
        rect(10, 10, 12, 11, DARK)

    def wheels(put, rect):
        import math
        for cx in (4.5, 11.5):
            for y in range(16):
                for x in range(16):
                    d = math.hypot(x + 0.5 - cx, y + 0.5 - 9)
                    if d < 4.2:
                        put(x, y, RED if d > 3.2 else DARK if d > 1.3 else STEEL)
        rect(4, 9, 11, 9, STEEL)

    def boiler(put, rect):
        rect(1, 4, 14, 11, BLACK)
        rect(1, 4, 14, 5, (60, 60, 66))
        rect(1, 11, 14, 11, (16, 16, 18))
        for x in (4, 8, 12):
            rect(x, 3, x, 12, BRASS)
        rect(0, 5, 0, 10, (48, 48, 54))
        rect(5, 1, 7, 3, BLACK)
        rect(10, 2, 11, 3, BRASS)

    def cab(put, rect):
        rect(2, 4, 13, 14, RED)
        rect(1, 2, 14, 3, BLACK)
        rect(4, 6, 6, 9, GLASS)
        rect(9, 6, 11, 9, GLASS)
        rect(2, 14, 13, 14, (96, 20, 18))
        rect(6, 11, 9, 12, BRASS)

    def bunker(put, rect):
        rect(1, 6, 14, 14, BLACK)
        rect(1, 6, 14, 6, (60, 60, 66))
        rect(2, 4, 13, 6, COAL)
        rect(4, 3, 10, 4, COAL)
        for (x, y) in ((3, 4), (6, 3), (9, 4), (11, 5), (5, 5)):
            put(x, y, (80, 76, 80))
        rect(1, 10, 14, 10, BRASS)
        rect(5, 11, 9, 13, RED)

    def drill(put, rect):
        import math
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                if d < 7.3:
                    put(x, y, STEEL if d > 6.2 else (92, 88, 84) if d > 2.4 else BRASS)
        for ang in range(0, 360, 45):
            x = int(round(8 + 6.6 * math.cos(math.radians(ang)))) - 1
            y = int(round(8 + 6.6 * math.sin(math.radians(ang)))) - 1
            rect(max(0, x), max(0, y), min(15, x + 1), min(15, y + 1), (200, 200, 210))
        rect(3, 7, 12, 8, (70, 68, 72))
        rect(7, 3, 8, 12, (70, 68, 72))
        rect(6, 6, 9, 9, BRASS)
        rect(7, 7, 8, 8, (240, 214, 130))
    return icon({"train_chassis": chassis, "train_wheels": wheels, "steam_boiler": boiler, "train_cab": cab, "coal_bunker": bunker,
                 "drill_head": drill}[pid])


def hammer_icon():
    def fn(put, rect):
        HANDLE = (128, 88, 50)
        for i in range(10):
            put(3 + i, 14 - i, HANDLE)
            put(4 + i, 14 - i, shade(HANDLE, 0.75))
        rect(8, 1, 14, 4, STEEL)
        rect(8, 1, 14, 1, (190, 192, 200))
        rect(8, 4, 14, 4, (80, 82, 90))
        rect(14, 0, 15, 5, (90, 92, 100))
        rect(6, 2, 7, 3, (90, 92, 100))
    return icon(fn)


def table_textures():
    import random as R
    rng = R.Random(7)
    top = Image.new("RGBA", (16, 16))
    side = Image.new("RGBA", (16, 16))
    front = Image.new("RGBA", (16, 16))
    tp, sp, fp = top.load(), side.load(), front.load()
    for y in range(16):
        for x in range(16):
            v = rng.randint(-8, 8)
            tp[x, y] = shade((92, 94, 102), 1 + v / 100) + (255,)
            plank = (y // 4) % 2
            sp[x, y] = shade((118, 82, 48) if plank else (106, 74, 44), 1 + v / 90) + (255,)
            fp[x, y] = sp[x, y]
    for i in range(16):
        tp[i, 0] = tp[i, 15] = tp[0, i] = tp[15, i] = (56, 56, 62, 255)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        tp[x, y] = (170, 172, 180, 255)
    # una vía grabada en lo alto
    for y in range(2, 14):
        tp[5, y] = tp[10, y] = (176, 178, 186, 255)
        if y % 3 == 0:
            for x in range(4, 12):
                if x not in (5, 10):
                    tp[x, y] = (100, 70, 42, 255)
    for img in (sp, fp):
        for x in range(16):
            img[x, 0] = img[x, 1] = (72, 72, 80, 255)
            img[x, 15] = (60, 60, 66, 255)
    # el frente: una rueda de tren
    import math
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8.5)
            if d < 5:
                fp[x, y] = ((140, 30, 26, 255) if d > 3.8 else (40, 40, 44, 255) if d > 1.4 else (160, 162, 170, 255))
    return top, side, front


# ------------------------------------------------------------------ los modelos de objeto de las piezas (con texturas de vanilla)

def el(frm, to, tex):
    faces = {f: {"texture": "#" + tex} for f in ("north", "south", "east", "west", "up", "down")}
    return {"from": frm, "to": to, "faces": faces}


def main():
    tex_dir = os.path.join(ASSETS, "textures/entity/train")
    item_dir = os.path.join(ASSETS, "textures/item")
    block_dir = os.path.join(ASSETS, "textures/block")
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
                assert all(float(v).is_integer() for v in c.size), (mid, c.size)
                W.paint_cube(img, c, rng)
                D.paint_extra(img, glow, c, rng)
        img.save(os.path.join(tex_dir, mid + ".png"))
        glow.save(os.path.join(tex_dir, mid + "_glow.png"))
        parts = [{"name": p.name, "parent": p.parent, "pivot": p.pivot, "rotation": p.rot, "role": p.role,
                  "cubes": [{"uv": list(c.uv), "origin": c.origin, "size": c.size} for c in p.cubes]} for p in a.parts]
        write_json(os.path.join(ASSETS, "hell/%s.json" % mid), {"texture": [tw, th], "parts": parts}, indent=None)
        previews.append((mid, parts, img))
        print("  %s: textura %dx%d" % (mid, tw, th))

    def gen_item(name):
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    for name in MODELS:
        gen_item(name)
    # lo de los trenes de antes (los vagones de pasajeros y de mercancías, la plataforma vieja)
    for old in ("passenger_car", "freight_car", "locomotive"):
        for f in (os.path.join(tex_dir, old + ".png"), os.path.join(tex_dir, old + "_glow.png"), os.path.join(ASSETS, "hell/%s.json" % old),
                  os.path.join(ASSETS, "models/item/%s.json" % old), os.path.join(item_dir, "%s.png" % old)):
            if os.path.exists(f):
                os.remove(f)
    for old in ("passenger_car", "freight_car", "locomotive", "flatcar", "steam_locomotive", "coal_tender"):
        f = os.path.join(DATA, "recipe/%s.json" % old)
        if os.path.exists(f):
            os.remove(f)
    # los iconos de los coches (a partir del modelo)
    for mid, parts, img in previews:
        ic = render_entity(parts, img, size=64, yaw=35.0, pitch=15.0, center="auto").convert("RGBA")
        px = ic.load()
        bg = px[0, 0]
        for y in range(ic.height):
            for x in range(ic.width):
                if px[x, y][:3] == bg[:3]:
                    px[x, y] = (0, 0, 0, 0)
        ic.resize((32, 32), Image.LANCZOS).save(os.path.join(item_dir, "%s.png" % mid))
    # las vías, las partes y el martillo
    for tid, *_ in TRACKS:
        track_icon(tid.replace("track_", "")).save(os.path.join(item_dir, tid + ".png"))
        gen_item(tid)
    for pid, *_ in PARTS:
        part_icon(pid).save(os.path.join(item_dir, pid + ".png"))
        gen_item(pid)
    hammer_icon().save(os.path.join(item_dir, "building_hammer.png"))
    write_json(os.path.join(ASSETS, "models/item/building_hammer.json"),
               {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/building_hammer" % NS}})
    # las piezas de antes: fuera
    for old in OLD_PIECES:
        f = os.path.join(ASSETS, "models/item/%s.json" % old)
        if os.path.exists(f):
            os.remove(f)
    # las vías grandes (se dibujan aparte: el modelo solo da las partículas)
    write_json(os.path.join(ASSETS, "blockstates/big_track.json"), {"multipart": [{"apply": {"model": "%s:block/big_track" % NS}}]})
    write_json(os.path.join(ASSETS, "models/block/big_track.json"), {"textures": {"particle": "minecraft:block/gravel"}})
    pools = []
    for tid, *_ in TRACKS:
        shape = tid.replace("track_", "").replace("_right", "").replace("_left", "")
        props = {"part": "1", "shape": shape}
        if shape in ("curve", "switch"):
            props["left"] = "true" if tid.endswith("left") else "false"
        pools.append({"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, tid)}],
                      "conditions": [{"condition": "minecraft:block_state_property", "block": "%s:big_track" % NS, "properties": props}]})
    write_json(os.path.join(DATA, "loot_table/blocks/big_track.json"), {"type": "minecraft:block", "pools": pools})
    # la mesa de ensamblaje
    top, side, front = table_textures()
    os.makedirs(block_dir, exist_ok=True)
    top.save(os.path.join(block_dir, "train_assembly_table_top.png"))
    side.save(os.path.join(block_dir, "train_assembly_table_side.png"))
    front.save(os.path.join(block_dir, "train_assembly_table_front.png"))
    b = "%s:block/train_assembly_table" % NS
    write_json(os.path.join(ASSETS, "models/block/train_assembly_table.json"), {
        "parent": "minecraft:block/orientable_with_bottom",
        "textures": {"top": b + "_top", "side": b + "_side", "front": b + "_front", "bottom": "minecraft:block/oak_planks"}})
    write_json(os.path.join(ASSETS, "blockstates/train_assembly_table.json"), {"variants": {
        "facing=north": {"model": b}, "facing=east": {"model": b, "y": 90}, "facing=south": {"model": b, "y": 180}, "facing=west": {"model": b, "y": 270}}})
    write_json(os.path.join(ASSETS, "models/item/train_assembly_table.json"), {"parent": b})
    write_json(os.path.join(DATA, "loot_table/blocks/train_assembly_table.json"), {"type": "minecraft:block", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:train_assembly_table" % NS}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # las recetas
    R = os.path.join(DATA, "recipe")

    def shaped(name, pattern, key, count=1):
        write_json(os.path.join(R, name + ".json"), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                                                    "key": key, "result": {"id": "%s:%s" % (NS, name), "count": count}})

    def shapeless(file, result, ingredients, count=1):
        write_json(os.path.join(R, file + ".json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                    "ingredients": [{"item": i} if ":" in i and not i.startswith("#") else {"tag": i[1:]} for i in ingredients],
                                                    "result": {"id": "%s:%s" % (NS, result), "count": count}})
    I, P, N = {"item": "minecraft:iron_ingot"}, {"tag": "minecraft:planks"}, {"item": "minecraft:iron_nugget"}
    shaped("track_straight", ["IPI", "IGI", "IPI"], {"I": I, "P": P, "G": {"item": "minecraft:gravel"}}, 2)
    ns = lambda n: "%s:%s" % (NS, n)  # noqa: E731
    shapeless("track_slope", "track_slope", [ns("track_straight"), ns("track_straight"), "minecraft:cobblestone"])
    shapeless("track_curve_right", "track_curve_right", [ns("track_straight")] * 3)
    shapeless("track_curve_left", "track_curve_left", [ns("track_curve_right")])
    shapeless("track_curve_right_from_left", "track_curve_right", [ns("track_curve_left")])
    shapeless("track_switch_right", "track_switch_right", [ns("track_curve_right"), ns("track_straight"), ns("track_straight"), "minecraft:lever"])
    shapeless("track_switch_left", "track_switch_left", [ns("track_switch_right")])
    shapeless("track_switch_right_from_left", "track_switch_right", [ns("track_switch_left")])
    shaped("train_chassis", ["III", "PPP", "III"], {"I": I, "P": P})
    shaped("train_wheels", ["NIN", "I I", "NIN"], {"I": I, "N": N})
    shaped("steam_boiler", ["CIC", "IFI", "CIC"], {"C": {"item": "minecraft:copper_ingot"}, "I": I, "F": {"item": "minecraft:furnace"}})
    shaped("train_cab", ["PGP", "P P", "III"], {"P": P, "G": {"item": "minecraft:glass_pane"}, "I": I})
    shaped("coal_bunker", ["I I", "ICI", "III"], {"I": I, "C": {"item": "minecraft:chest"}})
    shaped("drill_head", ["DBD", "BFB", "DBD"], {"D": {"item": "minecraft:diamond"}, "B": {"item": "minecraft:iron_block"},
                                                 "F": {"item": "minecraft:piston"}})
    shaped("building_hammer", ["II ", "IS ", "  S"], {"I": I, "S": {"item": "minecraft:stick"}})
    shaped("train_assembly_table", ["III", "CTC", "PPP"], {"I": I, "C": {"item": "minecraft:copper_ingot"}, "T": {"item": "minecraft:crafting_table"},
                                                           "P": P})
    # los textos
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key in OLD_KEYS:
            lang.pop(key, None)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        sheet = Image.new("RGB", (1600, 400), (70, 74, 80))
        for k, (mid, parts, img) in enumerate(previews):
            sheet.paste(render_entity(parts, img, size=400, yaw=35.0, pitch=15.0, center="auto"), (k * 400, 0))
        sheet.save(os.path.join(PREVIEW, "trains.png"))
        icons = Image.new("RGBA", (16 * 15, 16), (60, 64, 70, 255))
        for k, n in enumerate([t[0] for t in TRACKS] + [p[0] for p in PARTS] + ["building_hammer"]):
            ic = Image.open(os.path.join(item_dir, n + ".png")).convert("RGBA")
            icons.alpha_composite(ic, (k * 16, 0))
        icons.resize((16 * 15 * 6, 16 * 6), Image.NEAREST).save(os.path.join(PREVIEW, "train_icons.png"))
    print("el tren grande: locomotora, ténder y plataforma; vías, partes, mesa y martillo")


if __name__ == "__main__":
    main()
