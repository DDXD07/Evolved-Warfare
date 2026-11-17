"""Las criaturas legendarias (fase 101 en adelante), al estilo de Ice and Fire de RLCraft.

Tanda 1: los dragones de fuego y de hielo (el mismo cuerpo: cuatro patas, cuello largo, cuernos, mandíbula, dos alas de
dos tramos, cola de tres trozos con púas y espinas en el lomo), sus escamas y huesos, su botín y los textos. Los modelos
van a assets/evolvedwarfare/hell/<id>.json (los anima BossModel según el papel de cada parte).

Uso: python3 tools/ew_legendary.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import os
import random
import sys

import numpy as np
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


def n(name):
    return "%s:%s" % (NS, name)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


# ------------------------------------------------------------------ los dragones

DRAGONS = {
    # escamas, vientre, membrana de las alas, cuernos, ojos
    "fire_dragon": ((150, 34, 26), (214, 150, 66), (96, 22, 20), (60, 44, 34), (255, 190, 40)),
    "ice_dragon": ((132, 178, 214), (232, 240, 248), (86, 120, 160), (226, 236, 244), (120, 220, 255)),
}


def dragon(mid):
    scale_, belly, membrane, horn, eye = DRAGONS[mid]
    dark = shade(scale_, 0.7)
    a = W.Animal(mid)
    # las patas (delante y detrás), con garras
    for side, z, phase in ((-1, -7, 0), (1, -7, 1), (-1, 8, 1), (1, 8, 0)):
        leg = a.part("leg_%d_%d" % (side + 1, z + 10), None, (side * 5.5, 12, z), role="leg", phase=phase)
        leg.cube((-2, 0, -2), (4, 9, 4), **C(scale_, low=(dark, 2)))
        leg.cube((-2.5, 9, -3.5), (5, 3, 5), **C(dark, tip=(horn, 1)))
    body = a.part("body", None, (0, 12, 0), role="body")
    body.cube((-6, -10, -12), (12, 10, 24), **C(scale_, belly=(belly, 3), spots=(dark, 0.12)))
    body.cube((-5, -11, -10), (10, 1, 20), **C(dark))
    for k in range(5):
        body.cube((-0.5, -13, -9 + k * 4.5), (1, 3, 2), **C(horn))
    # el cuello y la cabeza (mira a -z)
    neck = a.part("neck", "body", (0, -7, -12), (-30, 0, 0))
    neck.cube((-3, -3, -11), (6, 6, 11), **C(scale_, belly=(belly, 2)))
    neck.cube((-0.5, -5, -9), (1, 2, 2), **C(horn))
    neck.cube((-0.5, -5, -5), (1, 2, 2), **C(horn))
    head = a.part("head", "neck", (0, 0, -11), (30, 0, 0), role="head")
    head.cube((-4, -4, -7), (8, 7, 7), **C(scale_, eyes=("side", 2, eye), glow_eyes=True))
    head.cube((-3, -2, -14), (6, 4, 7), **C(scale_, nose=dark, front_color=dark))
    for side in (-1, 1):
        head.cube((side * 2.5 - 0.5, -7, -2), (1, 4, 1), **C(horn, tip=(shade(horn, 1.3), 1)))
        head.cube((side * 3 - 0.5, -6, 0), (1, 1, 3), **C(horn))
    jaw = a.part("jaw", "head", (0, 2, -7), role="jaw")
    jaw.cube((-2.5, 0, -7), (5, 2, 7), **C(belly, top=(150, 40, 40)))
    # la cola: tres trozos, cada vez más fina, con púas y la punta en flecha
    t1 = a.part("tail_1", "body", (0, -6, 12), (-12, 0, 0), role="tail")
    t1.cube((-3, -3, 0), (6, 6, 12), **C(scale_, belly=(belly, 2)))
    t1.cube((-0.5, -5, 3), (1, 2, 2), **C(horn))
    t1.cube((-0.5, -5, 8), (1, 2, 2), **C(horn))
    t2 = a.part("tail_2", "tail_1", (0, 0, 12), (8, 0, 0), role="tail")
    t2.cube((-2, -2, 0), (4, 4, 12), **C(scale_, belly=(belly, 1)))
    t2.cube((-0.5, -4, 5), (1, 2, 2), **C(horn))
    t3 = a.part("tail_3", "tail_2", (0, 0, 12), (6, 0, 0), role="tail")
    t3.cube((-1, -1, 0), (2, 2, 10), **C(scale_))
    t3.cube((-3, -0.5, 8), (6, 1, 4), **C(dark))
    # las alas: un brazo con la membrana y la punta (dos tramos que baten)
    for side in (-1, 1):
        sname = "l" if side > 0 else "r"
        wing = a.part("wing_" + sname, "body", (side * 6, -9, -7), (0, 0, side * -10), role="wing", side=side)
        if side > 0:
            wing.cube((0, -1, -1), (16, 2, 2), **C(scale_))
            wing.cube((0, 0, 0), (16, 1, 14), **C(membrane, bands=(shade(membrane, 0.8), 3)))
        else:
            wing.cube((-16, -1, -1), (16, 2, 2), **C(scale_))
            wing.cube((-16, 0, 0), (16, 1, 14), **C(membrane, bands=(shade(membrane, 0.8), 3)))
        tip = a.part("wingtip_" + sname, "wing_" + sname, (side * 16, 0, 0), (0, 0, side * 12), role="wing", side=side)
        if side > 0:
            tip.cube((0, -1, -1), (14, 2, 2), **C(scale_))
            tip.cube((0, 0, 0), (14, 1, 10), **C(membrane, bands=(shade(membrane, 0.8), 3)))
            tip.cube((13, -2, -2), (2, 2, 2), **C(horn))
        else:
            tip.cube((-14, -1, -1), (14, 2, 2), **C(scale_))
            tip.cube((-14, 0, 0), (14, 1, 10), **C(membrane, bands=(shade(membrane, 0.8), 3)))
            tip.cube((-15, -2, -2), (2, 2, 2), **C(horn))
    return a


# ------------------------------------------------------------------ tanda 3: el gigante de hielo

def frost_giant():
    """El gigante de hielo: piel azul grisácea, barba blanca con carámbanos, una corona de hielo, faldón y botas de piel,
    brazaletes de hielo y un garrote con la cabeza de hielo erizada."""
    a = W.Animal("frost_giant")
    skin, skin_dark = (146, 172, 196), (104, 128, 152)
    fur, fur_dark = (232, 230, 222), (176, 170, 158)
    leather = (110, 86, 62)
    ice, ice_dark = (196, 230, 248), (130, 180, 214)
    for side, name, phase in ((-1, "leg_r", 0), (1, "leg_l", 1)):
        leg = a.part(name, None, (side * 3.5, 10, 0), role="leg", phase=phase)
        leg.cube((-3, 0, -3), (6, 14, 6), **C(skin, low=(skin_dark, 2)))
        leg.cube((-3.5, 8, -3.5), (7, 6, 7), **C(fur, bands=(fur_dark, 2)))
    body = a.part("body", None, (0, 10, 0), role="body")
    body.cube((-7, -14, -4), (14, 14, 8), **C(skin, belly=(skin_dark, 2), spots=(skin_dark, 0.06)))
    body.cube((-7.5, -3, -4.5), (15, 6, 9), **C(fur, bands=(fur_dark, 2)))
    body.cube((-7.5, -4, -4.5), (15, 1, 9), **C(leather))
    body.cube((-7.5, -14, -4.5), (5, 4, 9), **C(fur, bands=(fur_dark, 2)))          # la piel al hombro
    head = a.part("head", "body", (0, -14, 0), role="head")
    head.cube((-4, -9, -4), (8, 9, 8), **C(skin, eyes=("front", 4, (150, 230, 255)), glow_eyes=True, mouth=(60, 70, 90)))
    head.cube((-4.5, -4, -5), (9, 7, 2), **C(fur, bands=(fur_dark, 2)))              # la barba
    for k in range(4):
        head.cube((-3.5 + k * 2.2, 3, -5), (1, 3, 1), **C(ice, tip=(ice_dark, 1)))    # carámbanos en la barba
    for k in range(5):
        h = 3 if k % 2 == 0 else 2
        head.cube((-4 + k * 1.8, -9 - h, -4), (1, h, 1), **C(ice, glow_all=False))     # la corona de hielo
    for side, name in ((-1, "arm_r"), (1, "arm_l")):
        arm = a.part(name, "body", (side * 9.5, -12, 0), role="arm", side=side)
        arm.cube((-2.5, -2, -3), (5, 15, 6), **C(skin, low=(skin_dark, 2)))
        arm.cube((-3, 8, -3.5), (6, 3, 7), **C(ice, bands=(ice_dark, 1)))              # el brazalete de hielo
    club = a.part("club", "arm_r", (0, 12, 0), (-70, 0, 0))
    club.cube((-1, -2, -1), (2, 14, 2), **C(leather))
    club.cube((-3, 10, -3), (6, 8, 6), **C(ice, bands=(ice_dark, 2)))
    for dx, dz in ((-4, 0), (3, 0), (0, -4), (0, 3)):
        club.cube((dx, 13, dz), (1, 2, 1), **C(ice_dark))
    return a


# ------------------------------------------------------------------ tanda 4: la serpiente marina

def sea_serpent():
    """La serpiente marina: cabeza de dragón con aletas a los lados y cuernos hacia atrás, mandíbula, ojos verdes; un cuerpo
    de ocho trozos (cada vez más fino) con aleta en el lomo y la cola en abanico. Los trozos ondulan uno tras otro."""
    a = W.Animal("sea_serpent")
    scale_, back, belly, fin = (40, 112, 122), (22, 70, 82), (150, 202, 180), (64, 164, 150)
    eye = (150, 255, 120)
    head = a.part("head", None, (0, 19, -24), role="head")
    head.cube((-5, -4, -10), (10, 7, 10), **C(scale_, eyes=("side", 2, eye), glow_eyes=True, top=back))
    head.cube((-3.5, -2, -17), (7, 4, 7), **C(scale_, nose=back, top=back))
    for side in (-1, 1):
        head.cube((side * 5.5 - 0.5, -7, -5), (1, 6, 7), **C(fin, bands=(shade(fin, 0.75), 2)))        # las aletas de la cabeza
        head.cube((side * 3 - 0.5, -6, -1), (1, 2, 5), **C((210, 216, 196), tip=((240, 240, 230), 1)))  # los cuernos
    jaw = a.part("jaw", "head", (0, 3, -9), role="jaw")
    jaw.cube((-3, 0, -8), (6, 2, 8), **C(belly, top=(160, 50, 50)))
    prev = "head"
    widths = [9, 9, 8, 8, 7, 6, 5, 4]
    for k, w in enumerate(widths):
        h = w - 1
        seg = a.part("seg_%d" % k, prev, (0, 0, 0 if k == 0 else 10), role="segment", phase=k + 1)
        seg.cube((-w / 2, -h / 2, 0), (w, h, 10), **C(scale_, belly=(belly, 2), top=back, spots=(back, 0.08)))
        seg.cube((-0.5, -h / 2 - 3, 1), (1, 3, 8), **C(fin, bands=(shade(fin, 0.75), 1)))
        prev = "seg_%d" % k
    tail = a.part("tail_fin", prev, (0, 0, 10), role="segment", phase=len(widths) + 1)
    tail.cube((-0.5, -6, 0), (1, 12, 7), **C(fin, bands=(shade(fin, 0.7), 2)))
    return a


# ------------------------------------------------------------------ fase 105: el ent (sale al talar)

def treant():
    """El ent: un árbol viejo que se ha arrancado del suelo. Un tronco retorcido y encorvado, de corteza agrietada con
    musgo, setas en los hombros y lianas colgando; las piernas son raíces gruesas que se abren en raíces más finas; los
    brazos, ramas nudosas con ramas que salen y dedos de ramitas puntiagudas. La cara, tallada en la corteza: cuencas
    hondas con un brillo verde enfermizo, unas cejas de corteza fruncidas y una boca de nudo abierta con astillas por
    dientes. Encima, una copa rota de hojas con ramas muertas que asoman como cuernos."""
    a = W.Animal("treant")
    bark, bark_dark, bark_deep = (98, 74, 48), (64, 46, 28), (34, 24, 14)
    moss, moss_dark = (88, 122, 50), (56, 84, 32)
    leaves, leaves_dark, dead = (58, 104, 36), (34, 70, 22), (120, 96, 66)
    glow = (150, 255, 90)
    shroom, shroom_spot = (176, 52, 40), (236, 226, 210)
    # las piernas: raíces gruesas, con raíces finas que se abren en el pie
    for side, name, phase in ((-1, "leg_r", 0), (1, "leg_l", 1)):
        leg = a.part(name, None, (side * 3, 8, 0), (0, 0, side * -4), role="leg", phase=phase)
        leg.cube((-2.5, 0, -2.5), (5, 10, 5), **C(bark, rings=(bark_dark, 3), spots=(moss, 0.1)))
        shin = a.part(name + "_shin", name, (0, 10, 0), (6, 0, 0))
        shin.cube((-2, 0, -2), (4, 5, 4), **C(bark, rings=(bark_dark, 2)))
        for k, (rx, rz, yaw) in enumerate(((-1, -1, -30), (1, -1, 30), (0, 1, 180), (side * 1.5, 0, side * 90))):
            root = a.part("%s_root_%d" % (name, k), name + "_shin", (rx, 4, rz), (35, yaw, 0))
            root.cube((-0.5, 0, -0.5), (1, 1, 5), **C(bark_dark, tip=(bark_deep, 1)))
    # el tronco, encorvado y retorcido
    body = a.part("body", None, (0, 8, 0), (18, 0, 0), role="body")
    body.cube((-5, -14, -3.5), (10, 14, 7), **C(bark, rings=(bark_dark, 3), spots=(moss, 0.14)))
    body.cube((-4, -6, -4.5), (8, 6, 1), **C(bark_dark, spots=(bark_deep, 0.3)))         # la corteza levantada del pecho
    body.cube((-0.5, -12, -4.2), (1, 9, 1), **C(bark_deep))                               # una grieta
    body.cube((-5.5, -10, -1), (1, 7, 3), **C(moss, spots=(moss_dark, 0.4)))               # musgo de los costados
    body.cube((4.5, -8, 0), (1, 6, 3), **C(moss, spots=(moss_dark, 0.4)))
    for k, (x, z) in enumerate(((-2, 3.5), (1, 3.5), (3, 3.5))):
        body.cube((x, -12 + k * 3, z), (1, 2, 1), **C(bark_dark))                          # nudos del lomo
    # setas en los hombros
    for x, z in ((-5, -1), (4, 0), (3.5, -2)):
        body.cube((x, -15.5, z), (2, 1, 2), **C(shroom, spots=(shroom_spot, 0.3)))
        body.cube((x + 0.5, -14.5, z + 0.5), (1, 1, 1), **C(shroom_spot))
    # lianas que cuelgan
    for x, z, h in ((-4.5, -3.8, 9), (3.5, -3.8, 7), (-2, 3.6, 10), (2.5, 3.6, 8)):
        body.cube((x, -14, z), (1, h, 1), **C(leaves, low=(leaves_dark, 3)))
    # la cabeza: la cara tallada en la corteza
    head = a.part("head", "body", (0, -14, -1), (-14, 0, 0), role="head")
    head.cube((-4, -8, -4), (8, 8, 8), **C(bark, rings=(bark_dark, 2), spots=(moss, 0.08)))
    head.cube((-3.2, -6, -4.2), (2, 2, 1), **C(bark_deep))                                  # las cuencas
    head.cube((1.2, -6, -4.2), (2, 2, 1), **C(bark_deep))
    head.cube((-2.7, -5.5, -4.4), (1, 1, 1), **C(glow, glow_all=True))                     # el brillo de los ojos
    head.cube((1.7, -5.5, -4.4), (1, 1, 1), **C(glow, glow_all=True))
    head.cube((-3.6, -7.2, -4.6), (3, 1, 1), **C(bark_dark))                                # las cejas fruncidas
    head.cube((0.6, -7.2, -4.6), (3, 1, 1), **C(bark_dark))
    head.cube((-0.5, -4.5, -4.6), (1, 2, 1), **C(bark_dark))                               # la nariz de nudo
    head.cube((-2.5, -2.5, -4.2), (5, 2, 1), **C(bark_deep))                               # la boca de nudo
    for k in range(4):
        head.cube((-2 + k * 1.3, -2.5, -4.5), (1, 1, 1), **C((214, 196, 150)))             # astillas por dientes
    jaw = a.part("jaw", "head", (0, -0.5, -2), role="jaw")
    jaw.cube((-3, 0, -2.4), (6, 2, 5), **C(bark, rings=(bark_dark, 1)))
    for k in range(3):
        jaw.cube((-1.5 + k * 1.3, -1, -2.6), (1, 1, 1), **C((214, 196, 150)))
    # la copa rota y las ramas muertas como cuernos
    head.cube((-6, -14, -5), (12, 6, 10), **C(leaves, spots=(leaves_dark, 0.35)))
    head.cube((-4, -17, -3), (8, 3, 7), **C(leaves, spots=(leaves_dark, 0.35)))
    head.cube((-7, -11, -2), (2, 4, 6), **C(leaves_dark, spots=(leaves, 0.3)))
    head.cube((5, -12, -3), (2, 4, 5), **C(leaves_dark, spots=(leaves, 0.3)))
    for side in (-1, 1):
        b1 = a.part("crown_%d" % (side + 1), "head", (side * 3, -9, 1), (-20, 0, side * 40))
        b1.cube((-0.5, -6, -0.5), (1, 6, 1), **C(dead))
        b2 = a.part("crown_tip_%d" % (side + 1), "crown_%d" % (side + 1), (0, -6, 0), (0, 0, side * -45))
        b2.cube((-0.5, -4, -0.5), (1, 4, 1), **C(dead, tip=(bark_dark, 1)))
        b3 = a.part("crown_twig_%d" % (side + 1), "crown_%d" % (side + 1), (0, -3, 0), (0, 0, side * 60))
        b3.cube((-0.5, -3, -0.5), (1, 3, 1), **C(dead))
    # los brazos: ramas nudosas, con una rama que sale y dedos de ramitas
    for side, name in ((-1, "arm_r"), (1, "arm_l")):
        arm = a.part(name, "body", (side * 6, -12, 0), (-20, 0, side * -8), role="arm", side=side)
        arm.cube((-1.5, -1, -1.5), (3, 11, 3), **C(bark, rings=(bark_dark, 2), spots=(moss, 0.1)))
        arm.cube((side * 1.2 - 0.5, 3, -2), (1, 2, 1), **C(bark_dark))                    # un nudo
        side_branch = a.part(name + "_branch", name, (side * 1.5, 5, 0), (0, 0, side * -50))
        side_branch.cube((-0.5, 0, -0.5), (1, 6, 1), **C(bark_dark))
        side_branch.cube((-1.5, 5, -1.5), (3, 2, 3), **C(leaves, spots=(leaves_dark, 0.4)))
        fore = a.part(name + "_fore", name, (0, 10, 0), (-25, 0, 0))
        fore.cube((-1.2, 0, -1.2), (2, 10, 2), **C(bark, rings=(bark_dark, 2)))
        hand = a.part(name + "_hand", name + "_fore", (0, 10, 0))
        hand.cube((-1.5, 0, -1.5), (3, 2, 3), **C(bark_dark))
        for k in range(4):
            twig = a.part("%s_twig_%d" % (name, k), name + "_hand", (-1 + k * 0.7, 2, 0), (10 * (k - 1.5), 0, 12 * (k - 1.5)))
            twig.cube((-0.5, 0, -0.5), (1, 5, 1), **C(bark_dark, tip=(bark_deep, 1)))
    return a


# ------------------------------------------------------------------ fase 106: el Coco

def coco():
    """El Coco: un ser altísimo y consumido, encorvado, de piel gris ceniza pegada a las costillas, con el espinazo marcado.
    Un manto negro hecho jirones que cuelga en tiras; una capucha de la que asoma una cara de calavera pálida con las
    cuencas hundidas, dos puntos rojos que brillan y una sonrisa llena de dientes (la mandíbula se le descuelga al atacar).
    Cuernos retorcidos hacia atrás. Cuello largo y fino. Piernas dobladas hacia atrás con garras en los pies; brazos
    larguísimos de dos tramos que llegan al suelo, con dedos como agujas. A la espalda, el saco... con una manita pálida
    que asoma."""
    a = W.Animal("coco")
    skin, rib = (44, 40, 48), (70, 64, 76)
    cloak, cloak_dark = (18, 16, 22), (8, 7, 10)
    bone, bone_dark = (204, 198, 182), (150, 142, 126)
    claw = (226, 222, 210)
    red = (255, 30, 30)
    # las piernas: muslo hacia delante, espinilla hacia atrás, pie con garras
    for side, name, phase in ((-1, "leg_r", 0), (1, "leg_l", 1)):
        thigh = a.part(name, None, (side * 2, 3, 0), (-20, 0, 0), role="leg", phase=phase)
        thigh.cube((-1, 0, -1), (2, 11, 2), **C(skin, low=(rib, 2)))
        shin = a.part(name + "_shin", name, (0, 11, 0), (38, 0, 0))
        shin.cube((-1, 0, -1), (2, 10, 2), **C(skin, low=(cloak_dark, 2)))
        foot = a.part(name + "_foot", name + "_shin", (0, 10, 0), (-18, 0, 0))
        foot.cube((-1.5, 0, -3), (3, 1, 4), **C(skin))
        for k in range(3):
            foot.cube((-1.5 + k, 0, -5), (1, 1, 2), **C(claw, tip=(bone_dark, 1)))
    # el cuerpo, encorvado hacia delante
    body = a.part("body", None, (0, 3, 0), (28, 0, 0), role="body")
    body.cube((-3, -13, -2), (6, 13, 4), **C(skin, bands=(rib, 3), low=(cloak_dark, 2)))
    for k in range(5):
        body.cube((-0.5, -12 + k * 2.4, 2), (1, 1, 1), **C(bone_dark))                      # el espinazo
    # el manto: los hombros y las tiras que cuelgan
    body.cube((-4.5, -14, -3), (9, 4, 7), **C(cloak, spots=(cloak_dark, 0.3)))
    strips = [(-4.5, -2.5, 15), (-4.5, 0.5, 12), (-4.5, 3, 17), (3.5, -2, 14), (3.5, 1, 18), (3.5, 3, 11),
              (-3, 3, 16), (-1, 3.5, 19), (1, 3, 15), (2.5, 3.5, 13)]
    for x, z, h in strips:
        body.cube((x, -11, z if z < 0 else z - 0.5), (1, h, 1), **C(cloak, low=(cloak_dark, 4), spots=(cloak_dark, 0.25)))
    # el saco a la espalda, con su cuerda... y una manita que asoma
    body.cube((-3.5, -12, 2.5), (7, 8, 5), **C((92, 70, 46), bands=((64, 48, 30), 2), spots=((50, 38, 24), 0.15)))
    body.cube((-1.5, -13, 3.5), (3, 1, 3), **C((60, 46, 30)))
    body.cube((-4.5, -13, -0.5), (9, 1, 1), **C((80, 60, 40)))
    body.cube((1, -15, 4), (1, 3, 1), **C((236, 212, 196)))                                  # el bracito
    body.cube((0.5, -16, 3.5), (2, 1, 2), **C((236, 212, 196)))                               # la mano
    # el cuello largo y la cabeza
    neck = a.part("neck", "body", (0, -13, -1), (-40, 0, 0))
    neck.cube((-1, -6, -1), (2, 6, 2), **C(skin, bands=(rib, 1)))
    head = a.part("head", "neck", (0, -6, 0), (12, 0, 0), role="head")
    # la cara de calavera, metida en la capucha
    head.cube((-2.5, -6.5, -3), (5, 6, 5), **C(bone, front_color=bone, low=(bone_dark, 1)))
    head.cube((-2.2, -5, -3.2), (2, 2, 1), **C((8, 6, 8)))                                     # las cuencas
    head.cube((0.2, -5, -3.2), (2, 2, 1), **C((8, 6, 8)))
    head.cube((-1.7, -4.5, -3.4), (1, 1, 1), **C(red, glow_all=True))                        # los ojos (puntos rojos)
    head.cube((0.7, -4.5, -3.4), (1, 1, 1), **C(red, glow_all=True))
    head.cube((-0.5, -3, -3.3), (1, 1, 1), **C((16, 10, 12)))                                 # la nariz hundida
    for k in range(5):
        head.cube((-2.25 + k, -1.6, -3.4), (1, 2, 1), **C(claw, tip=(bone_dark, 1)))          # los dientes de arriba
    # la capucha: por detrás, por encima y un marco alrededor de la cara
    head.cube((-3.5, -8, -2), (7, 8, 7), **C(cloak, spots=(cloak_dark, 0.3)))
    head.cube((-3.5, -8, -3.6), (7, 1, 2), **C(cloak, spots=(cloak_dark, 0.3)))
    head.cube((-3.5, -7, -3.6), (1, 7, 2), **C(cloak, low=(cloak_dark, 2)))
    head.cube((2.5, -7, -3.6), (1, 7, 2), **C(cloak, low=(cloak_dark, 2)))
    # los cuernos retorcidos, hacia atrás como los de un carnero
    for side in (-1, 1):
        h1 = a.part("horn_%d" % (side + 1), "head", (side * 2.8, -7.5, 0), (-55, 0, side * 30))
        h1.cube((-1, -4, -1), (2, 4, 2), **C(bone_dark, low=((90, 84, 72), 1)))
        h2 = a.part("horn_mid_%d" % (side + 1), "horn_%d" % (side + 1), (0, -4, 0), (-50, 0, 0))
        h2.cube((-0.5, -4, -0.5), (1, 4, 1), **C(bone_dark))
        h3 = a.part("horn_tip_%d" % (side + 1), "horn_mid_%d" % (side + 1), (0, -4, 0), (-60, 0, 0))
        h3.cube((-0.5, -3, -0.5), (1, 3, 1), **C((60, 54, 46), tip=((30, 26, 22), 1)))
    # la mandíbula que se descuelga, con los dientes de abajo
    jaw = a.part("jaw", "head", (0, -0.5, -1), role="jaw")
    jaw.cube((-2.5, 0, -2.5), (5, 1, 4), **C(bone_dark))
    for k in range(5):
        jaw.cube((-2.25 + k, -1, -2.6), (1, 1, 1), **C(claw))
    # los brazos larguísimos: brazo, antebrazo y una mano con dedos como agujas
    for side, name in ((-1, "arm_r"), (1, "arm_l")):
        arm = a.part(name, "body", (side * 4, -12, 0), (-28, 0, side * -6), role="arm", side=side)
        arm.cube((-1, -1, -1), (2, 13, 2), **C(skin, bands=(rib, 2)))
        fore = a.part(name + "_fore", name, (0, 12, 0), (-30, 0, 0))
        fore.cube((-1, 0, -1), (2, 13, 2), **C(skin, low=(cloak_dark, 2)))
        hand = a.part(name + "_hand", name + "_fore", (0, 13, 0), (20, 0, 0))
        hand.cube((-1.5, 0, -1), (3, 2, 2), **C(skin))
        for k in range(4):
            finger = a.part("%s_finger_%d" % (name, k), name + "_hand", (-1.5 + k + 0.5, 2, 0), (8 * (k - 1.5), 0, 6 * (k - 1.5)))
            finger.cube((-0.5, 0, -0.5), (1, 6, 1), **C(skin, low=(claw, 1)))
    return a


# ------------------------------------------------------------------ fase 107: el guardián de roca

def rock_guardian():
    """El guardián de roca: un gólem de piedra encorvado, con vetas de mineral, cristales que brillan en la espalda, la
    cabeza hundida entre los hombros, ojos de brasa y unos brazos larguísimos con puños de roca."""
    a = W.Animal("rock_guardian")
    stone, stone_dark = (124, 124, 128), (84, 84, 90)
    ore = (214, 160, 70)
    crystal = (255, 190, 80)
    for side, name, phase in ((-1, "leg_r", 0), (1, "leg_l", 1)):
        leg = a.part(name, None, (side * 4, 16, 1), role="leg", phase=phase)
        leg.cube((-2.5, 0, -2.5), (5, 8, 5), **C(stone, low=(stone_dark, 2), spots=(stone_dark, 0.2)))
    body = a.part("body", None, (0, 16, 1), (12, 0, 0), role="body")
    body.cube((-7, -14, -5), (14, 14, 10), **C(stone, spots=(ore, 0.08), low=(stone_dark, 3)))
    body.cube((-6, -16, -4), (12, 3, 8), **C(stone_dark, spots=(stone, 0.3)))
    for x, y, z, h in ((-4, -19, -1, 4), (-1, -20, 0, 5), (2, -18, -2, 3), (4, -19, 1, 4)):
        body.cube((x, y, z), (2, h, 2), **C(crystal, glow_all=True))                              # los cristales
    head = a.part("head", "body", (0, -12, -5), (-12, 0, 0), role="head")
    head.cube((-3.5, -5, -5), (7, 6, 6), **C(stone, eyes=("front", 2, (255, 140, 40)), glow_eyes=True, mouth=(40, 40, 44)))
    for side, name in ((-1, "arm_r"), (1, "arm_l")):
        arm = a.part(name, "body", (side * 9, -11, 0), role="arm", side=side)
        arm.cube((-2.5, -2, -2.5), (5, 16, 5), **C(stone, spots=(ore, 0.06), low=(stone_dark, 2)))
        arm.cube((-3.5, 14, -3.5), (7, 6, 7), **C(stone_dark, spots=(stone, 0.25)))               # el puño
    return a


# ------------------------------------------------------------------ fase 110: el diablito de la lava

def lava_imp():
    """El diablito: un demonio pequeño y encorvado que sale de la lava. Piel negra rojiza con grietas de lava que brillan,
    una cabeza grande con cuernos curvos, orejas en punta, ojos amarillos encendidos y una sonrisa de oreja a oreja llena
    de colmillos; alas de murciélago rotas, púas por el espinazo, una cola larga con punta de flecha, garras en manos y
    pies y una bola de fuego en la mano."""
    a = W.Animal("lava_imp")
    skin, skin_dark = (60, 22, 20), (30, 10, 10)
    lava = (255, 120, 20)
    horn, horn_dark = (40, 32, 30), (20, 16, 14)
    fang = (240, 230, 200)
    wing, wing_dark = (90, 26, 22), (54, 14, 12)
    eye = (255, 230, 60)
    # las piernas, dobladas hacia atrás, con garras
    for side, name, phase in ((-1, "leg_r", 0), (1, "leg_l", 1)):
        thigh = a.part(name, None, (side * 1.8, 15, 0), (-25, 0, 0), role="leg", phase=phase)
        thigh.cube((-1, 0, -1), (2, 5, 2), **C(skin, spots=(lava, 0.08)))
        shin = a.part(name + "_shin", name, (0, 5, 0), (45, 0, 0))
        shin.cube((-0.5, 0, -0.5), (1, 5, 1), **C(skin_dark))
        foot = a.part(name + "_foot", name + "_shin", (0, 5, 0), (-20, 0, 0))
        foot.cube((-1, 0, -2), (2, 1, 3), **C(skin_dark))
        for k in range(2):
            foot.cube((-1 + k, 0, -3), (1, 1, 1), **C(fang))
    # el cuerpo encorvado, con grietas de lava y púas en el espinazo
    body = a.part("body", None, (0, 15, 0), (25, 0, 0), role="body")
    body.cube((-2.5, -8, -1.5), (5, 8, 3), **C(skin, spots=(skin_dark, 0.2)))
    body.cube((-1.5, -7, -1.8), (1, 5, 1), **C(lava, glow_all=True))                        # grietas que brillan
    body.cube((0.5, -5, -1.8), (1, 4, 1), **C(lava, glow_all=True))
    body.cube((-0.5, -3, -1.9), (2, 1, 1), **C(lava, glow_all=True))
    for k in range(4):
        body.cube((-0.5, -8 + k * 2, 1.5), (1, 1, 2), **C(horn, tip=(horn_dark, 1)))        # las púas
    # la cola larga con la punta de flecha
    t1 = a.part("tail_1", "body", (0, -1, 1.5), (40, 0, 0), role="tail")
    t1.cube((-0.5, -0.5, 0), (1, 1, 6), **C(skin))
    t2 = a.part("tail_2", "tail_1", (0, 0, 6), (-30, 0, 0), role="tail")
    t2.cube((-0.5, -0.5, 0), (1, 1, 6), **C(skin))
    t2.cube((-1.5, -0.5, 5), (3, 1, 2), **C(horn))
    t2.cube((-0.5, -0.5, 7), (1, 1, 1), **C(horn_dark))
    # las alas de murciélago, rotas
    for side in (-1, 1):
        sname = "l" if side > 0 else "r"
        w = a.part("wing_" + sname, "body", (side * 2, -7, 1.5), (0, side * 25, side * -15), role="wing", side=side)
        if side > 0:
            w.cube((0, -1, 0), (6, 1, 1), **C(horn))
            w.cube((0, 0, 0), (6, 5, 1), **C(wing, spots=(wing_dark, 0.35)))
            w.cube((5, 5, 0), (1, 2, 1), **C(wing_dark))
        else:
            w.cube((-6, -1, 0), (6, 1, 1), **C(horn))
            w.cube((-6, 0, 0), (6, 5, 1), **C(wing, spots=(wing_dark, 0.35)))
            w.cube((-6, 5, 0), (1, 2, 1), **C(wing_dark))
    # la cabeza grande
    head = a.part("head", "body", (0, -8, -0.5), (-25, 0, 0), role="head")
    head.cube((-3, -6, -3), (6, 6, 6), **C(skin, spots=(skin_dark, 0.2)))
    head.cube((-2.6, -4.5, -3.2), (2, 1, 1), **C(skin_dark))                                 # el ceño
    head.cube((0.6, -4.5, -3.2), (2, 1, 1), **C(skin_dark))
    head.cube((-2.2, -3.6, -3.3), (1, 1, 1), **C(eye, glow_all=True))                        # los ojos
    head.cube((1.2, -3.6, -3.3), (1, 1, 1), **C(eye, glow_all=True))
    head.cube((-2.5, -1.8, -3.2), (5, 1, 1), **C((20, 4, 4)))                                # la sonrisa
    for k in range(5):
        head.cube((-2.4 + k * 1.1, -1.8 if k % 2 == 0 else -1.0, -3.4), (1, 1, 1), **C(fang))  # colmillos
    head.cube((-0.5, -6.6, -2), (1, 1, 2), **C(lava, glow_all=True))                         # grieta en la frente
    for side in (-1, 1):
        ear = a.part("ear_%d" % (side + 1), "head", (side * 3, -4, 0), (0, side * -20, side * -40))
        ear.cube((-0.5, -3, -0.5), (1, 3, 1), **C(skin))
        h1 = a.part("horn_%d" % (side + 1), "head", (side * 2, -6, -1), (-30, 0, side * 35))
        h1.cube((-1, -3, -1), (2, 3, 2), **C(horn))
        h2 = a.part("horn_mid_%d" % (side + 1), "horn_%d" % (side + 1), (0, -3, 0), (-35, 0, side * -25))
        h2.cube((-0.5, -3, -0.5), (1, 3, 1), **C(horn))
        h3 = a.part("horn_tip_%d" % (side + 1), "horn_mid_%d" % (side + 1), (0, -3, 0), (-40, 0, 0))
        h3.cube((-0.5, -2, -0.5), (1, 2, 1), **C(horn_dark, tip=(lava, 1)))
    jaw = a.part("jaw", "head", (0, 0, -1.5), role="jaw")
    jaw.cube((-2, 0, -1.6), (4, 1, 3), **C(skin_dark))
    # los brazos flacos con garras; en la derecha, la bola de fuego
    for side, name in ((-1, "arm_r"), (1, "arm_l")):
        arm = a.part(name, "body", (side * 3, -7, 0), (-30, 0, side * -10), role="arm", side=side)
        arm.cube((-0.5, -0.5, -0.5), (1, 5, 1), **C(skin))
        fore = a.part(name + "_fore", name, (0, 5, 0), (-35, 0, 0))
        fore.cube((-0.5, 0, -0.5), (1, 5, 1), **C(skin_dark))
        for k in range(3):
            fore.cube((-1 + k, 5, -0.5), (1, 2, 1), **C(fang, tip=(skin_dark, 1)))
        if side < 0:
            fore.cube((-1.5, 6, -2), (3, 3, 3), **C(lava, glow_all=True))
    return a


MODELS = collections.OrderedDict([("fire_dragon", lambda: dragon("fire_dragon")), ("ice_dragon", lambda: dragon("ice_dragon")),
                                  ("frost_giant", frost_giant), ("sea_serpent", sea_serpent), ("treant", treant), ("coco", coco),
                                  ("rock_guardian", rock_guardian), ("lava_imp", lava_imp)])

NAMES = {
    "lava_imp": ("Diablito", "Lava Imp"),
    "rock_guardian": ("Guardián de roca", "Rock Guardian"),
    "coco": ("El Coco", "The Bogeyman"),
    "treant": ("Ent", "Treant"),
    "sea_serpent": ("Serpiente marina", "Sea Serpent"),
    "frost_giant": ("Gigante de hielo", "Frost Giant"),
    "fire_dragon": ("Dragón de fuego", "Fire Dragon"),
    "ice_dragon": ("Dragón de hielo", "Ice Dragon"),
}


# ------------------------------------------------------------------ el botín

def count(item, lo, hi, chance=None):
    entry = {"type": "minecraft:item", "name": item, "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False}]}
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [entry]}
    if chance:
        pool["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return pool


LOOT = {
    "lava_imp": [count("minecraft:blaze_powder", 1, 2), count("minecraft:magma_cream", 0, 2), count(n("imp_horn"), 1, 1, 0.25)],
    "rock_guardian": [count("minecraft:cobblestone", 4, 9), count(n("rock_core"), 1, 1, 0.35), count("minecraft:flint", 1, 3, 0.5)],
    "coco": [count(n("coco_claw"), 1, 1), count(n("heart_crystal"), 1, 1, 0.5), count("minecraft:ender_pearl", 1, 3)],
    "treant": [count("minecraft:oak_log", 3, 6), count("minecraft:stick", 2, 6), count("minecraft:oak_sapling", 1, 2),
               count("minecraft:apple", 1, 2, 0.4), count(n("living_bark"), 1, 2)],
    "sea_serpent": [count(n("sea_serpent_scale"), 10, 18), count(n("serpent_fang"), 2, 4), count("minecraft:prismarine_shard", 6, 14),
                    count("minecraft:nautilus_shell", 1, 3), count("minecraft:trident", 1, 1, 0.3), count(n("heart_crystal"), 1, 1)],
    "frost_giant": [count(n("giant_club"), 1, 1), count(n("giant_pelt"), 2, 4), count("minecraft:packed_ice", 6, 16), count("minecraft:diamond", 2, 5),
                    count("minecraft:emerald", 4, 10), count(n("heart_crystal"), 1, 1)],
    "fire_dragon": [count(n("fire_dragon_scale"), 10, 18), count(n("dragon_bone"), 5, 10), count(n("fire_dragon_heart"), 1, 1),
                    count(n("heart_crystal"), 1, 1), count("minecraft:gold_ingot", 8, 20), count("minecraft:diamond", 2, 5)],
    "ice_dragon": [count(n("ice_dragon_scale"), 10, 18), count(n("dragon_bone"), 5, 10), count(n("ice_dragon_heart"), 1, 1),
                   count(n("heart_crystal"), 1, 1), count("minecraft:gold_ingot", 8, 20), count("minecraft:diamond", 2, 5)],
}

ITEMS = {
    "imp_horn": ("Cuerno de diablito", "Imp Horn", "Aún está caliente. Dicen que trae mala suerte.", "Still hot. They say it brings bad luck."),
    "rock_core": ("Núcleo de guardián", "Guardian Core", "El corazón de piedra que movía al guardián. Aún está caliente.",
                  "The stone heart that moved the guardian. Still warm."),
    "coco_claw": ("Garra del Coco", "Bogeyman's Claw", "La prueba de que lo venciste. Ya puedes dormir tranquilo... ¿o no?",
                  "Proof that you beat it. Now you can sleep soundly... or can you?"),
    "living_bark": ("Corteza viva", "Living Bark", "Aún se mueve un poco. Con ella, la madera se cura sola (más adelante).",
                    "It still twitches. With it, wood mends itself (coming later)."),
    "sea_serpent_scale": ("Escama de serpiente marina", "Sea Serpent Scale", "Brilla como el mar. Para la armadura de las mareas.",
                          "It shines like the sea. For tide armour."),
    "serpent_fang": ("Colmillo de serpiente marina", "Sea Serpent Fang", "Largo y curvo. Un trofeo... y más adelante, algo más.",
                     "Long and curved. A trophy... and something more later."),
    "giant_club": ("Garrote del gigante", "Giant's Club",
                   "Enorme y lento: pega muchísimo, empuja lejos y deja helado a lo que golpea.",
                   "Huge and slow: hits extremely hard, knocks far back and leaves what it hits frozen."),
    "giant_pelt": ("Piel de gigante", "Giant's Pelt",
                   "Gruesa y cálida. Para abrigos (más adelante).", "Thick and warm. For coats (coming later)."),
    "fire_dragon_scale": ("Escama de dragón de fuego", "Fire Dragon Scale",
                          "Dura como el acero y no se quema. Para la armadura de escamas (más adelante).",
                          "Hard as steel and fireproof. For scale armour (coming later)."),
    "ice_dragon_scale": ("Escama de dragón de hielo", "Ice Dragon Scale",
                         "Fría al tacto, nunca se derrite. Para la armadura de escamas (más adelante).",
                         "Cold to the touch, it never melts. For scale armour (coming later)."),
    "dragon_bone": ("Hueso de dragón", "Dragon Bone", "Más ligero y duro que el hierro. Para las armas de dragón (más adelante).",
                    "Lighter and harder than iron. For dragonbone weapons (coming later)."),
    "fire_dragon_heart": ("Corazón de dragón de fuego", "Fire Dragon Heart", "Aún late, caliente. Un trofeo... y algo más (más adelante).",
                          "Still beating, and hot. A trophy... and more (coming later)."),
    "ice_dragon_heart": ("Corazón de dragón de hielo", "Ice Dragon Heart", "Aún late, helado. Un trofeo... y algo más (más adelante).",
                         "Still beating, and freezing. A trophy... and more (coming later)."),
}

TEXTS = {
    "armor.evolvedwarfare.dragon.fire": ("Cada pieza: −15 %% de daño de fuego. Completa: inmune al fuego.",
                                         "Each piece: −15%% fire damage. Full set: immune to fire."),
    "armor.evolvedwarfare.dragon.ice": ("Cada pieza: −15 %% de daño de frío. Completa: no te congelas y ralentizas a quien te pega.",
                                        "Each piece: −15%% cold damage. Full set: you can't freeze and you slow whoever hits you."),
    "armor.evolvedwarfare.dragon.sea": ("Cada pieza: −25 %% de daño al ahogarte. Completa: respiras bajo el agua y nadas como un delfín.",
                                        "Each piece: −25%% drowning damage. Full set: water breathing and you swim like a dolphin."),
    "message.evolvedwarfare.legendary.roar.sea_serpent": ("El mar se agita y algo enorme se mueve bajo las olas: una serpiente marina...",
                                                          "The sea churns and something huge moves beneath the waves: a sea serpent..."),
    "message.evolvedwarfare.legendary.capsize": ("¡La serpiente ha partido tu barca!", "The serpent has smashed your boat!"),
    "message.evolvedwarfare.legendary.whirlpool": ("¡Un remolino te arrastra hacia el fondo!", "A whirlpool drags you down!"),
    "message.evolvedwarfare.ambush.stone": ("¡Has abierto un nido! Algo sale de la piedra...", "You broke into a nest! Something crawls out of the stone..."),
    "message.evolvedwarfare.ambush.deep": ("Algo se movía en la oscuridad de la roca...", "Something was moving in the darkness of the rock..."),
    "message.evolvedwarfare.ambush.log": ("¡El árbol se ha despertado!", "The tree has woken up!"),
    "message.evolvedwarfare.ambush.dirt": ("¡Algo estaba enterrado ahí!", "Something was buried there!"),
    "message.evolvedwarfare.ambush.nether": ("La roca del Nether escupe algo...", "The Nether rock spits something out..."),
    "message.evolvedwarfare.coco.appears": ("Algo te observaba mientras dormías... ¡El Coco!", "Something was watching you sleep... the Bogeyman!"),
    "message.evolvedwarfare.coco.gone": ("El Coco se ha esfumado entre las sombras... por ahora.", "The Bogeyman vanished into the shadows... for now."),
    "message.evolvedwarfare.coco.scream": ("¡El Coco grita y todo se oscurece!", "The Bogeyman screams and everything goes dark!"),
    "title.evolvedwarfare.rock_guardian": ("¡GRRAAAH!", "GRRAAAH!"),
    "subtitle.evolvedwarfare.rock_guardian": ("¡Un guardián de roca sale de la veta!", "A rock guardian bursts out of the vein!"),
    "config.evolvedwarfare.rockGuardianChance": ("Guardián de roca", "Rock guardian"),
    "config.evolvedwarfare.rockGuardianChance.tip": ("Probabilidad de que salga un guardián de roca al picar un mineral (0.015 = 1,5 %).",
                                                     "Chance that a rock guardian bursts out when mining an ore (0.015 = 1.5%)."),
    "message.evolvedwarfare.lava_imp": ("Algo se ríe dentro de la lava... ¡un diablito!", "Something giggles inside the lava... an imp!"),
    "config.evolvedwarfare.lavaImpChance": ("Diablitos de la lava", "Lava imps"),
    "config.evolvedwarfare.lavaImpChance.tip": ("Probabilidad, cada 10 s cerca de un pozo de lava de 2x2 o más, de que salga un diablito.",
                                                "Chance every 10 s near a lava pool of 2x2 or more that an imp comes out."),
    "config.evolvedwarfare.cocoChance": ("El Coco al despertar", "Bogeyman on waking"),
    "config.evolvedwarfare.cocoChance.tip": ("Probabilidad de que el Coco te esté esperando al despertar (0.02 = 2 %).",
                                             "Chance that the Bogeyman is waiting for you when you wake up (0.02 = 2%)."),
    "config.evolvedwarfare.blockMobsEnabled": ("Monstruos al picar, talar y cavar", "Monsters when mining, chopping and digging"),
    "config.evolvedwarfare.blockMobsEnabled.tip": (
        "A veces, al romper piedra, troncos, tierra o arena, sale algo: lepismas, mineros zombis, arañas, ents...",
        "Sometimes breaking stone, logs, dirt or sand lets something out: silverfish, zombie miners, spiders, treants..."),
    "config.evolvedwarfare.blockMobChance": ("Probabilidad por bloque", "Chance per block"),
    "config.evolvedwarfare.blockMobChance.tip": ("Probabilidad de que salga algo al romper un bloque (0.004 = 1 de cada 250).",
                                                 "Chance that something comes out when breaking a block (0.004 = 1 in 250)."),
    "itemGroup.evolvedwarfare.legendary": ("Evolved Warfare: Legendarios", "Evolved Warfare: Legendary"),
    "message.evolvedwarfare.legendary.roar.fire_dragon": ("Un rugido estremece la tierra y el cielo se tiñe de rojo: un dragón de fuego anda cerca...",
                                                          "A roar shakes the earth and the sky turns red: a fire dragon is near..."),
    "message.evolvedwarfare.legendary.roar.ice_dragon": ("Un rugido helado corta el aire: un dragón de hielo anda cerca...",
                                                         "A freezing roar cuts the air: an ice dragon is near..."),
    "message.evolvedwarfare.legendary.roar.frost_giant": ("La tierra tiembla bajo unos pasos enormes: un gigante de hielo anda cerca...",
                                                          "The ground trembles under huge footsteps: a frost giant is near..."),
    "message.evolvedwarfare.legendary.blizzard": ("¡La ventisca del gigante te hiela hasta los huesos!", "The giant's blizzard freezes you to the bone!"),
    "message.evolvedwarfare.legendary.slain": ("¡%s ha caído! Su leyenda será contada.", "%s has fallen! Its legend will be told."),
    "death.attack.evolvedwarfare.dragon_fire": ("%1$s fue calcinado por el aliento de un dragón", "%1$s was burnt to ashes by dragon breath"),
    "death.attack.evolvedwarfare.dragon_fire.player": ("%1$s fue calcinado por %2$s", "%1$s was burnt to ashes by %2$s"),
    "death.attack.evolvedwarfare.dragon_ice": ("%1$s se congeló con el aliento de un dragón", "%1$s was frozen solid by dragon breath"),
    "death.attack.evolvedwarfare.dragon_ice.player": ("%1$s fue congelado por %2$s", "%1$s was frozen solid by %2$s"),
    "config.evolvedwarfare.category.legendary": ("Criaturas legendarias", "Legendary creatures"),
    "config.evolvedwarfare.legendaryEnabled": ("Criaturas legendarias", "Legendary creatures"),
    "config.evolvedwarfare.legendaryEnabled.tip": ("Dragones (y más adelante otras) que aparecen lejos y en las zonas peligrosas.",
                                                   "Dragons (and more later) that appear far away and in dangerous areas."),
    "config.evolvedwarfare.legendaryMinLevel": ("Peligro mínimo", "Minimum danger"),
    "config.evolvedwarfare.legendaryMinLevel.tip": ("Desde qué nivel de peligro pueden aparecer.", "From which danger level they can appear."),
    "config.evolvedwarfare.legendaryChance": ("Probabilidad por minuto", "Chance per minute"),
    "config.evolvedwarfare.legendaryChance.tip": ("Probabilidad, cada minuto y por jugador, de que aparezca una cerca (si el sitio vale).",
                                                  "Chance per player each minute that one appears nearby (if the place fits)."),
    "config.evolvedwarfare.dragonGriefing": ("El aliento quema y congela", "Breath burns and freezes"),
    "config.evolvedwarfare.dragonGriefing.tip": ("El aliento de fuego prende fuego y el de hielo congela el agua y nieva (con mobGriefing).",
                                                 "Fire breath starts fires and ice breath freezes water and snows (with mobGriefing)."),
}


# ------------------------------------------------------------------ tanda 2: armaduras de escamas y espadas de hueso

ARMOR = {
    "sea_serpent_scale": {"base": (44, 120, 128), "dark": (22, 70, 82), "trim": (150, 210, 190), "item": n("sea_serpent_scale"),
                          "es": ["Yelmo de las mareas", "Peto de las mareas", "Grebas de las mareas", "Botas de las mareas"],
                          "en": ["Tide Helmet", "Tide Chestplate", "Tide Leggings", "Tide Boots"]},
    "fire_dragon_scale": {"base": (156, 36, 28), "dark": (96, 20, 16), "trim": (220, 160, 60), "item": n("fire_dragon_scale"),
                          "es": ["Yelmo de escamas de dragón de fuego", "Peto de escamas de dragón de fuego",
                                 "Grebas de escamas de dragón de fuego", "Botas de escamas de dragón de fuego"],
                          "en": ["Fire Dragon Scale Helmet", "Fire Dragon Scale Chestplate", "Fire Dragon Scale Leggings",
                                 "Fire Dragon Scale Boots"]},
    "ice_dragon_scale": {"base": (138, 184, 220), "dark": (84, 120, 160), "trim": (236, 244, 250), "item": n("ice_dragon_scale"),
                         "es": ["Yelmo de escamas de dragón de hielo", "Peto de escamas de dragón de hielo",
                                "Grebas de escamas de dragón de hielo", "Botas de escamas de dragón de hielo"],
                         "en": ["Ice Dragon Scale Helmet", "Ice Dragon Scale Chestplate", "Ice Dragon Scale Leggings",
                                "Ice Dragon Scale Boots"]},
}

ARMOR_DECALS = {
    # yelmo con cuernos pintados en la frente, coraza con la arista y el vientre claro
    "head": [("hole", "N", 1, 3, 6, 1), ("vband", "N", 3, 5, "trim"), ("band", "sides", 7, 8, "dark"), ("vband", "U", 3, 5, "dark")],
    "body": [("vband", "N", 3, 5, "trim"), ("band", "sides", 10, 11, "dark")],
    "arm": [("band", "sides", 0, 2, "dark"), ("band", "sides", 7, 8, "trim")],
    "waist": [("band", "sides", 9, 11, "dark")],
    "leg": [("band", "sides", 9, 10, "dark"), ("vband", "N", 1, 3, "trim")],
    "boot": [("band", "sides", 9, 10, "trim")],
}


def armor_layers(name):
    import numpy as np
    import ew_armor_paint as P
    import ew_class_armor as CA
    s = ARMOR[name]
    m = {"m": {"kind": "scale", "c": s["base"]}, "dark": {"kind": "flat", "c": s["dark"]}, "trim": {"kind": "flat", "c": s["trim"]}}
    d = ARMOR_DECALS
    rnd = P.new_rng(sum(ord(c) for c in name) * 31)
    l1 = np.zeros((32, 64, 4), np.float32)
    l2 = np.zeros((32, 64, 4), np.float32)
    P.paint_box(l1, CA.HEAD, CA.box("m", (-4, -8, -4, 8, 8, 8), CA.HEAD, d["head"]), m, rnd)
    P.paint_box(l1, CA.BODY, CA.box("m", (-4, 0, -2, 8, 12, 4), CA.BODY, d["body"]), m, rnd)
    P.paint_box(l1, CA.ARM, CA.box("m", (-3, -2, -2, 4, 12, 4), CA.ARM, d["arm"]), m, rnd)
    P.paint_box(l1, CA.LEG, CA.box("m", (-2, 0, -2, 4, 12, 4), CA.LEG, d["boot"]), m, rnd)
    CA.clear(l1, P.box_regions(*CA.LEG, 4, 12, 4), "NSEWU", rows=7)
    P.paint_box(l2, CA.BODY, CA.box("m", (-4, 0, -2, 8, 12, 4), CA.BODY, d["waist"]), m, rnd)
    CA.clear(l2, P.box_regions(*CA.BODY, 8, 12, 4), "NSEWU", rows=7)
    P.paint_box(l2, CA.LEG, CA.box("m", (-2, 0, -2, 4, 12, 4), CA.LEG, d["leg"]), m, rnd)
    return l1.astype(np.uint8), l2.astype(np.uint8)


SWORDS = {
    "dragonbone_sword": ((226, 218, 196), None, ("Espada de hueso de dragón", "Dragonbone Sword",
                                                "Más dura y ligera que la de netherita.", "Harder and lighter than netherite.")),
    "flamed_dragonbone_sword": ((226, 218, 196), (240, 110, 30), ("Espada flamígera de hueso de dragón", "Flamed Dragonbone Sword",
                                                                 "Quema a lo que toca y hiere de más a los dragones de hielo.",
                                                                 "Sets what it hits on fire and deals extra damage to ice dragons.")),
    "iced_dragonbone_sword": ((226, 218, 196), (110, 200, 250), ("Espada helada de hueso de dragón", "Iced Dragonbone Sword",
                                                                "Congela y ralentiza a lo que toca y hiere de más a los dragones de fuego.",
                                                                "Freezes and slows what it hits and deals extra damage to fire dragons.")),
}


def sword_icon(bone, glow):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dark = shade(bone, 0.6)
    edge = glow or shade(bone, 1.1)
    # la hoja en diagonal (de abajo izquierda a arriba derecha)
    for k in range(4, 15):
        x, y = k, 15 - k
        img.putpixel((x, y - 1), edge + (255,))
        img.putpixel((x, y), bone + (255,))
        img.putpixel((x - 1, y), dark + (255,))
    img.putpixel((14, 0), edge + (255,))
    # la guarda (de hueso, con dos púas) y el puño
    for k in range(-2, 3):
        img.putpixel((4 + k, 11 + k), dark + (255,))
    img.putpixel((2, 9), bone + (255,))
    img.putpixel((6, 13), bone + (255,))
    for k in range(3):
        img.putpixel((3 - k, 12 + k), (90, 60, 40, 255))
    img.putpixel((0, 15), dark + (255,))
    if glow:
        for k in (6, 9, 12):
            img.putpixel((k + 1, 14 - k), shade(glow, 1.2) + (255,))
    return img


# ------------------------------------------------------------------ los iconos

def scale_icon(color):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dark = shade(color, 0.6)
    light = shade(color, 1.3)
    for y in range(2, 15):
        half = int(6 * min(1.0, (y - 1) / 6.0)) if y < 8 else int(6 - (y - 8) * 0.85)
        for x in range(8 - half, 8 + half):
            c = color
            if x == 8 - half or x == 8 + half - 1 or y == 14:
                c = dark
            elif (x + y) % 5 == 0:
                c = light
            img.putpixel((x, y), c + (255,))
    for y in range(4, 12):
        img.putpixel((8, y), shade(color, 0.8) + (255,))
    return img


def club_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    leather, ice, ice_dark = (110, 86, 62), (196, 230, 248), (110, 160, 200)
    for k in range(1, 10):
        img.putpixel((k, 15 - k), leather + (255,))
        img.putpixel((k + 1, 15 - k), shade(leather, 0.7) + (255,))
    for x in range(8, 15):
        for y in range(1, 8):
            if (x - 11) ** 2 + (y - 4) ** 2 <= 11:
                img.putpixel((x, y), (ice if (x + y) % 3 else ice_dark) + (255,))
    for x, y in ((14, 1), (7, 3), (12, 8), (15, 5)):
        img.putpixel((x, y), ice_dark + (255,))
    return img


def pelt_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    fur, dark = (232, 230, 222), (170, 164, 150)
    for y in range(3, 14):
        w = 5 if y in (3, 13) else 6
        for x in range(8 - w, 8 + w):
            img.putpixel((x, y), (dark if x in (8 - w, 8 + w - 1) or (x * y) % 7 == 0 else fur) + (255,))
    for x, y in ((1, 4), (14, 4), (1, 12), (14, 12)):
        img.putpixel((x, y), dark + (255,))
    return img


def fang_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    ivory, dark = (236, 230, 206), (150, 140, 116)
    pts = [(4, 2), (5, 3), (6, 4), (7, 5), (8, 6), (9, 7), (10, 8), (10, 9), (11, 10), (11, 11), (11, 12), (10, 13)]
    for k, (x, y) in enumerate(pts):
        w = max(1, 3 - k // 4)
        for dx in range(w):
            img.putpixel((x - dx, y), (dark if dx == w - 1 else ivory) + (255,))
    return img


def bone_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    bone, dark = (226, 218, 196), (150, 140, 120)
    for k in range(3, 13):
        img.putpixel((k, 15 - k), bone + (255,))
        img.putpixel((k + 1, 15 - k), bone + (255,))
        img.putpixel((k, 16 - k), dark + (255,))
    for cx, cy in ((3, 12), (2, 11), (4, 13), (12, 3), (13, 4), (11, 2)):
        for dx in (0, 1):
            for dy in (0, 1):
                img.putpixel((cx + dx - 1, cy + dy - 1), bone + (255,))
    return img


def heart_icon(color, glow):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rows = ["..##...##..", ".#oo#.#oo#.", "#oglo#ooooo#"[:11], "#ooooooooo#", "#ooooooooo#", ".#ooooooo#.", "..#ooooo#..", "...#ooo#...",
            "....#o#....", ".....#....."]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            c = {"#": shade(color, 0.45), "o": color, "g": glow, "l": shade(glow, 1.2)}.get(ch)
            if c:
                img.putpixel((x + 2, y + 3), c + (255,))
    return img


def main():
    tex_dir = os.path.join(ASSETS, "textures/entity/hell")
    os.makedirs(tex_dir, exist_ok=True)
    previews = []
    for mid, gen in MODELS.items():
        a = gen()
        tw, th = a.pack()
        img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        glow = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        rng = random.Random(mid)
        for p in a.parts:
            for c in p.cubes:
                W.paint_cube(img, c, rng)
                D.paint_extra(img, glow, c, rng)
        img.save(os.path.join(tex_dir, mid + ".png"))
        glow.save(os.path.join(tex_dir, mid + "_eyes.png"))
        parts = [{"name": p.name, "parent": p.parent, "pivot": p.pivot, "rotation": p.rot, "role": p.role,
                  "cubes": [{"uv": list(c.uv), "origin": c.origin, "size": c.size} for c in p.cubes]} for p in a.parts]
        write_json(os.path.join(ASSETS, "hell/%s.json" % mid), {"texture": [tw, th], "parts": parts}, indent=None)
        previews.append((mid, parts, img))
        write_json(os.path.join(DATA, "loot_table/entities/%s.json" % mid),
                   {"type": "minecraft:entity", "pools": LOOT[mid], "random_sequence": "%s:entities/%s" % (NS, mid)})
        write_json(os.path.join(ASSETS, "models/item/%s_spawn_egg.json" % mid), {"parent": "minecraft:item/template_spawn_egg"})
    item_dir = os.path.join(ASSETS, "textures/item")
    scale_icon(DRAGONS["fire_dragon"][0]).save(os.path.join(item_dir, "fire_dragon_scale.png"))
    scale_icon(DRAGONS["ice_dragon"][0]).save(os.path.join(item_dir, "ice_dragon_scale.png"))
    bone_icon().save(os.path.join(item_dir, "dragon_bone.png"))
    club_icon().save(os.path.join(item_dir, "giant_club.png"))
    pelt_icon().save(os.path.join(item_dir, "giant_pelt.png"))
    scale_icon((44, 120, 128)).save(os.path.join(item_dir, "sea_serpent_scale.png"))
    fang_icon().save(os.path.join(item_dir, "serpent_fang.png"))
    bark = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rb = random.Random(105)
    for y in range(2, 14):
        for x in range(3, 13):
            c = (104, 80, 52) if (x + y // 3) % 3 else (70, 52, 32)
            if rb.random() < 0.15:
                c = (86, 120, 52)
            bark.putpixel((x, y), c + (255,))
    for x, y in ((6, 6), (9, 6)):
        bark.putpixel((x, y), (250, 220, 70, 255))
    bark.save(os.path.join(item_dir, "living_bark.png"))
    claw = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for k, (x0, y0) in enumerate(((3, 3), (7, 2), (11, 3))):
        for t in range(10):
            x = x0 + t // 4
            y = y0 + t
            if y < 16 and x < 16:
                claw.putpixel((x, y), ((214, 210, 200) if t > 1 else (255, 255, 255)) + (255,))
                if x + 1 < 16:
                    claw.putpixel((x + 1, y), (130, 126, 118, 255))
    for x in range(2, 15):
        for y in (12, 13, 14):
            claw.putpixel((x, y), (20, 18, 24, 255))
    claw.save(os.path.join(item_dir, "coco_claw.png"))
    core = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(2, 14):
        for x in range(2, 14):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d <= 6.5:
                c = (84, 84, 90) if d > 4.5 else (255, 190, 80) if d < 2.5 else (214, 140, 50)
                core.putpixel((x, y), c + (255,))
    core.putpixel((7, 6), (255, 240, 200, 255))
    core.save(os.path.join(item_dir, "rock_core.png"))
    ih = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pts = [(4, 13), (5, 12), (5, 11), (6, 10), (6, 9), (7, 8), (8, 7), (9, 6), (10, 5), (11, 4), (12, 4), (13, 3)]
    for k, (x, y) in enumerate(pts):
        w = 3 if k < 4 else 2 if k < 8 else 1
        for dx in range(w):
            ih.putpixel((x + dx, y), ((255, 120, 20) if k >= 10 else (40, 32, 30) if dx else (70, 58, 54)) + (255,))
    ih.save(os.path.join(item_dir, "imp_horn.png"))
    heart_icon((150, 30, 30), (255, 160, 40)).save(os.path.join(item_dir, "fire_dragon_heart.png"))
    heart_icon((70, 120, 170), (180, 240, 255)).save(os.path.join(item_dir, "ice_dragon_heart.png"))
    for name in ITEMS:
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name),
                   {"parent": "minecraft:item/handheld" if name == "giant_club" else "minecraft:item/generated",
                    "textures": {"layer0": n("item/" + name)}})
    # tanda 2: las armaduras de escamas y las espadas de hueso
    import ew_class_armor as CA
    import ew_armor as A
    armor_dir = os.path.join(ASSETS, "textures/models/armor")
    os.makedirs(armor_dir, exist_ok=True)
    armor_lang = {}
    for name, st in ARMOR.items():
        l1, l2 = armor_layers(name)
        Image.fromarray(l1, "RGBA").save(os.path.join(armor_dir, "%s_layer_1.png" % name))
        Image.fromarray(l2, "RGBA").save(os.path.join(armor_dir, "%s_layer_2.png" % name))
        for i, piece in enumerate(A.PIECES):
            item = "%s_%s" % (name, piece)
            CA.icon(l1, l2, piece).save(os.path.join(item_dir, item + ".png"))
            write_json(os.path.join(ASSETS, "models/item", item + ".json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": n("item/" + item)}})
            pattern = [r.replace("A", "X") for r in A.SHAPES[piece]]
            write_json(os.path.join(DATA, "recipe", item + ".json"), {
                "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
                "key": {"X": {"item": st["item"]}}, "result": {"id": n(item), "count": 1}})
            armor_lang["item.%s.%s" % (NS, item)] = (st["es"][i], st["en"][i])
    for piece, tag in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"), ("boots", "foot_armor")):
        CA.merge_tag(tag, [n("%s_%s" % (name, piece)) for name in ARMOR])
    CA.merge_tag("trimmable_armor", [n("%s_%s" % (name, piece)) for name in ARMOR for piece in A.PIECES])
    CA.merge_tag("swords", [n(name) for name in SWORDS])
    for name, (bone, glow, _) in SWORDS.items():
        sword_icon(bone, glow).save(os.path.join(item_dir, name + ".png"))
        write_json(os.path.join(ASSETS, "models/item", name + ".json"),
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": n("item/" + name)}})
    write_json(os.path.join(DATA, "recipe", "dragonbone_sword.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["B", "B", "S"],
        "key": {"B": {"item": n("dragon_bone")}, "S": {"item": "minecraft:stick"}}, "result": {"id": n("dragonbone_sword"), "count": 1}})
    for name, heart in (("flamed_dragonbone_sword", "fire_dragon_heart"), ("iced_dragonbone_sword", "ice_dragon_heart")):
        write_json(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shapeless", "category": "equipment",
            "ingredients": [{"item": n("dragonbone_sword")}, {"item": n(heart)}], "result": {"id": n(name), "count": 1}})
    for dt in ("dragon_fire", "dragon_ice"):
        write_json(os.path.join(DATA, "damage_type/%s.json" % dt), {"exhaustion": 0.1, "message_id": "%s.%s" % (NS, dt), "scaling": "never"})
    D.add_tag(os.path.join(ROOT, "src/main/resources/data/minecraft/tags/damage_type/is_fire.json"), [n("dragon_fire")])
    D.add_tag(os.path.join(ROOT, "src/main/resources/data/minecraft/tags/damage_type/is_freezing.json"), [n("dragon_ice")])
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in NAMES.items():
            data["entity.%s.%s" % (NS, key)] = en if english else es
            data["item.%s.%s_spawn_egg" % (NS, key)] = ("%s Spawn Egg" % en) if english else ("Huevo generador de %s" % es.lower())
        for key, (es, en, des, den) in ITEMS.items():
            data["item.%s.%s" % (NS, key)] = en if english else es
            data["item.%s.%s.desc" % (NS, key)] = den if english else des
        for key, (es, en) in TEXTS.items():
            data[key] = en if english else es
        for key, (es, en) in armor_lang.items():
            data[key] = en if english else es
        for name, (_, _, (es, en, des, den)) in SWORDS.items():
            data["item.%s.%s" % (NS, name)] = en if english else es
            data["item.%s.%s.desc" % (NS, name)] = den if english else des
        write_json(path, data)
    if PREVIEW:
        frames = []
        for name in ARMOR:
            l1 = np.array(Image.open(os.path.join(armor_dir, "%s_layer_1.png" % name)))
            l2 = np.array(Image.open(os.path.join(armor_dir, "%s_layer_2.png" % name)))
            frames.append(A.label(CA.figure(l1, l2, (-2.0, 1.9, 4.0)), name))
        A.grid(frames, 2).save(os.path.join(PREVIEW, "dragon_armor.png"))
        os.makedirs(PREVIEW, exist_ok=True)
        for mid, parts, img in previews:
            for yaw in (35, 150):
                render_entity(parts, img, size=420, scale=3.0, yaw=yaw, pitch=20, center=(0.0, 8.0, 0.0)).save(
                    os.path.join(PREVIEW, "%s_%d.png" % (mid, yaw)))
    print("legendarios: %d modelos" % len(MODELS))


if __name__ == "__main__":
    main()
