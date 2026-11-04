"""Los cultivos en 3D (fase 16): cada etapa de cada cultivo y lo que se cosecha, hechos de cubos.

Cultivos de vanilla (se sustituyen sus modelos: trigo, zanahorias, patatas, remolachas, verruga del Nether y arbusto de
bayas dulces) y los nuevos del mod (tomate, maíz, col, cebolla y ajo), con sus estados de bloque, modelos de bloque y de
objeto (lo cosechado también en 3D), texturas de materiales, botín, recetas y textos. Con --preview dibuja cada etapa
encima de la tierra de cultivo y lo cosechado como en el inventario.

Uso: python3 tools/ew_crops.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ew_model3d import Model, Palette, render  # noqa: E402

args = sys.argv[1:]
PREVIEW = None
if "--preview" in args:
    i = args.index("--preview")
    PREVIEW = args[i + 1]
    del args[i:i + 2]
ROOT = args[0] if args else os.path.join(os.path.dirname(__file__), "..")
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
MC_ASSETS = os.path.join(ROOT, "src/main/resources/assets/minecraft")
DATA = os.path.join(ROOT, "src/main/resources/data", NS)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write("\n")


# ------------------------------------------------------------------ materiales de cada cultivo

def palettes():
    P = {}

    p = Palette(11)
    p.add("stalk", (104, 160, 52))
    p.add("straw", (206, 176, 86), accent=(176, 146, 64), pattern="stripes")
    p.add("leaf", (84, 146, 44), pattern="vein", accent=(110, 170, 66))
    p.add("leaf_dry", (184, 162, 82), pattern="vein", accent=(160, 136, 64))
    p.add("grain_green", (150, 186, 76), pattern="kernels", accent=(118, 152, 56))
    p.add("grain", (226, 190, 92), pattern="kernels", accent=(176, 138, 56))
    p.add("awn", (236, 214, 150))
    p.add("twine", (196, 160, 100), pattern="stripes", accent=(150, 116, 70))
    P["wheat"] = p

    p = Palette(12)
    p.add("leaf", (76, 154, 46), pattern="vein", accent=(104, 178, 66))
    p.add("leaf_dark", (54, 118, 36))
    p.add("stem", (112, 172, 62))
    p.add("root", (238, 132, 30), pattern="rings", accent=(204, 102, 22))
    p.add("root_top", (214, 116, 30))
    P["carrot"] = p

    p = Palette(13)
    p.add("stem", (92, 150, 52))
    p.add("leaf", (72, 140, 46), pattern="vein", accent=(100, 168, 70))
    p.add("leaf_dark", (56, 114, 36), pattern="vein", accent=(80, 140, 52))
    p.add("flower", (238, 236, 246))
    p.add("flower_eye", (232, 198, 62))
    p.add("potato", (196, 156, 92), pattern="skin", accent=(150, 112, 60))
    p.add("baked", (176, 116, 52), pattern="skin", accent=(132, 84, 36))
    p.add("baked_inside", (240, 214, 120), pattern="spots", accent=(250, 232, 160))
    P["potato"] = p

    p = Palette(14)
    p.add("poison", (170, 176, 82), pattern="skin", accent=(110, 140, 50))
    p.add("poison_dark", (120, 140, 60))
    P["poisonous_potato"] = p

    p = Palette(15)
    p.add("leaf", (62, 132, 52), pattern="vein", accent=(160, 44, 56))
    p.add("leaf_dark", (50, 108, 44), pattern="vein", accent=(130, 36, 46))
    p.add("stem", (164, 42, 62))
    p.add("bulb", (156, 30, 62), pattern="skin", accent=(112, 20, 42))
    p.add("bulb_top", (124, 26, 52))
    P["beetroot"] = p

    p = Palette(16)
    p.add("wart", (146, 32, 32), pattern="skin", accent=(104, 20, 24))
    p.add("wart_light", (186, 56, 44), pattern="skin", accent=(150, 36, 34))
    p.add("stem", (112, 26, 26))
    P["nether_wart"] = p

    p = Palette(17)
    p.add("leaf", (52, 112, 52), pattern="vein", accent=(70, 136, 66))
    p.add("leaf_dark", (40, 92, 46))
    p.add("stem", (92, 66, 42))
    p.add("berry_green", (134, 174, 82), pattern="spots", accent=(170, 200, 110))
    p.add("berry", (184, 22, 42), pattern="spots", accent=(236, 96, 106))
    P["sweet_berries"] = p

    p = Palette(18)
    p.add("stake", (146, 108, 66), pattern="stripes", accent=(118, 86, 50))
    p.add("stem", (84, 142, 52))
    p.add("leaf", (64, 132, 42), pattern="vein", accent=(92, 160, 62))
    p.add("leaf_dark", (50, 108, 34))
    p.add("green", (124, 172, 62), pattern="spots", accent=(156, 196, 92))
    p.add("red", (212, 40, 30), pattern="spots", accent=(242, 120, 100))
    p.add("calyx", (62, 112, 36))
    p.add("twine", (222, 210, 176))
    P["tomato"] = p

    p = Palette(19)
    p.add("stalk", (112, 170, 62), pattern="stripes", accent=(90, 146, 48))
    p.add("leaf", (92, 162, 52), pattern="vein", accent=(124, 186, 80))
    p.add("husk", (150, 186, 96), pattern="vein", accent=(182, 206, 130))
    p.add("kernel", (242, 202, 62), pattern="kernels", accent=(208, 158, 30))
    p.add("tassel", (206, 176, 112))
    p.add("silk", (196, 124, 82))
    p.add("roasted", (214, 156, 48), pattern="spots", accent=(120, 70, 26))
    p.add("husk_dry", (210, 196, 140), pattern="vein", accent=(186, 170, 110))
    P["corn"] = p

    p = Palette(20)
    p.add("outer", (92, 152, 70), pattern="vein", accent=(150, 200, 120))
    p.add("inner", (172, 212, 132), pattern="vein", accent=(204, 232, 172))
    p.add("head", (184, 218, 144), pattern="stripes", accent=(152, 196, 112))
    p.add("stem", (134, 172, 102))
    p.add("sprout", (110, 170, 80))
    P["cabbage"] = p

    p = Palette(21)
    p.add("leaf", (82, 152, 62))
    p.add("leaf_light", (112, 176, 82))
    p.add("bulb", (204, 152, 82), pattern="stripes", accent=(170, 120, 58))
    p.add("bulb_top", (222, 184, 112))
    p.add("root", (230, 220, 190))
    P["onion"] = p

    p = Palette(22)
    p.add("leaf", (102, 162, 82), pattern="vein", accent=(130, 186, 104))
    p.add("leaf_dark", (80, 136, 64))
    p.add("bulb", (238, 232, 216), pattern="stripes", accent=(212, 202, 184))
    p.add("bulb_purple", (214, 190, 206))
    p.add("stem", (152, 182, 112))
    P["garlic"] = p

    p = Palette(23)
    p.add("farmland", (88, 58, 36), pattern="stripes", accent=(70, 44, 26))
    p.add("soul", (84, 64, 52), pattern="spots", accent=(64, 48, 38))
    p.add("grass", (96, 150, 64), pattern="spots", accent=(80, 128, 52))
    P["ground"] = p
    return P


PAL = palettes()


def tex(name):
    return "%s:block/crop/%s" % (NS, name)


# ------------------------------------------------------------------ piezas

def blade(m, x, z, y, h, mat, w=1.0, t=0.5, axis="z", angle=0.0):
    """Una hoja vertical (de ancho w y grueso t) que sale de (x, y, z) y se inclina sobre el eje dado."""
    if axis == "z":
        x0, x1, z0, z1 = x - w / 2, x + w / 2, z - t / 2, z + t / 2
    else:
        x0, x1, z0, z1 = x - t / 2, x + t / 2, z - w / 2, z + w / 2
    rot = (axis, angle, (x, y, z)) if angle else None
    return m.box(x0, y, z0, x1, y + h, z1, mat, rot=rot)


def flat_leaf(m, x, z, y, length, width, mat, direction, droop=22.5):
    """Una hoja tumbada que sale hacia un lado (n, s, e, o) y cae un poco."""
    if direction == "e":
        e = m.box(x, y, z - width / 2, x + length, y + 0.5, z + width / 2, mat, rot=("z", -droop, (x, y, z)))
    elif direction == "w":
        e = m.box(x - length, y, z - width / 2, x, y + 0.5, z + width / 2, mat, rot=("z", droop, (x, y, z)))
    elif direction == "s":
        e = m.box(x - width / 2, y, z, x + width / 2, y + 0.5, z + length, mat, rot=("x", droop, (x, y, z)))
    else:
        e = m.box(x - width / 2, y, z - length, x + width / 2, y + 0.5, z, mat, rot=("x", -droop, (x, y, z)))
    return e


def cube(m, cx, cy, cz, sx, sy, sz, mat, rot=None):
    return m.box(cx - sx / 2, cy - sy / 2, cz - sz / 2, cx + sx / 2, cy + sy / 2, cz + sz / 2, mat, rot=rot)


def spots(seed, pts, jitter=0.8):
    r = random.Random(seed)
    return [(x + r.uniform(-jitter, jitter), z + r.uniform(-jitter, jitter)) for x, z in pts]


TILTS = [("z", 22.5), ("x", -22.5), ("z", -22.5), ("x", 22.5), ("z", 45.0), ("x", -45.0), ("z", -45.0), ("x", 45.0)]


def tuft(m, x, z, y, n, h, mat, w=1.0, seed=0, tilt_from=0, vertical=True, mats=None):
    """Un manojo de hojas: una recta en medio y las demás inclinadas hacia los lados."""
    r = random.Random(seed)
    if vertical:
        blade(m, x, z, y, h, mat, w=w)
    for k in range(n):
        axis, angle = TILTS[(tilt_from + k) % len(TILTS)]
        blade(m, x + r.uniform(-0.3, 0.3), z + r.uniform(-0.3, 0.3), y, h * r.uniform(0.75, 1.0),
              (mats[k % len(mats)] if mats else mat), w=w, axis=axis, angle=angle)


# ------------------------------------------------------------------ etapas de cada cultivo (y = -1: encima de la tierra de cultivo)

B = -1.0


def wheat(stage, n=8):
    g = stage / (n - 1)
    m = Model(PAL["wheat"], tex("wheat"), 100 + stage)
    pts = spots(7, [(x, z) for x in (3, 8, 13) for z in (3, 8, 13)], 0.9)
    r = random.Random(70 + stage)
    for i, (x, z) in enumerate(pts):
        h = 2.0 + 11.0 * g + r.uniform(-1.0, 0.8)
        ripe = stage >= 6
        blade(m, x, z, B, h, "straw" if ripe else "stalk", w=0.8, t=0.8)
        if stage >= 1:
            axis, angle = TILTS[i % 4]
            blade(m, x, z, B + h * 0.3, 2.0 + 3.0 * g, "leaf_dry" if ripe else "leaf", w=1.2, axis=axis, angle=angle * (2 if stage >= 4 else 1))
        if stage >= 4:
            head = 1.0 + 2.6 * min(1.0, (stage - 3) / 3.0)
            mat = "grain" if stage >= 6 else "grain_green"
            cube(m, x, B + h + head / 2 - 0.2, z, 1.6, head, 1.6, mat)
            if stage == n - 1:
                blade(m, x, z, B + h + head - 0.2, 1.4, "awn", w=0.4, t=0.4)
    return m


def carrots(stage):
    m = Model(PAL["carrot"], tex("carrot"), 200 + stage)
    pts = spots(8, [(4, 4), (12, 4.5), (4.5, 12), (12, 12)] + ([(8, 8)] if stage >= 2 else []), 0.7)
    for i, (x, z) in enumerate(pts):
        if stage == 0:
            blade(m, x - 0.5, z, B, 1.6, "stem", w=0.6, t=0.6)
            blade(m, x + 0.5, z, B, 1.2, "leaf", w=0.8, axis="z", angle=-22.5)
            continue
        h = {1: 3.5, 2: 5.5, 3: 7.5}[stage]
        tuft(m, x, z, B, 3 + stage, h, "leaf", w=1.0, seed=i * 7 + stage, tilt_from=i * 2, mats=["leaf", "leaf_dark", "stem"])
        if stage >= 2:
            top = 1.0 if stage == 2 else 1.6
            cube(m, x, B + top / 2 - 0.3, z, 1.6 + stage * 0.5, top, 1.6 + stage * 0.5, "root_top")
    return m


def potatoes(stage):
    m = Model(PAL["potato"], tex("potato"), 300 + stage)
    pts = spots(9, [(4.5, 4.5), (11.5, 5), (5, 11.5), (11.5, 11.5)], 0.6)
    for i, (x, z) in enumerate(pts):
        if stage == 0:
            flat_leaf(m, x, z, B + 0.5, 1.6, 1.4, "leaf", "e", 22.5)
            flat_leaf(m, x, z, B + 0.5, 1.6, 1.4, "leaf", "w", 22.5)
            continue
        h = {1: 2.5, 2: 4.0, 3: 5.0}[stage]
        blade(m, x, z, B, h, "stem", w=0.8, t=0.8)
        size = {1: 2.0, 2: 2.8, 3: 3.2}[stage]
        for k, d in enumerate("nsew"):
            flat_leaf(m, x, z, B + h - 0.6 - (k % 2) * 0.8, size, size * 0.65, "leaf" if k % 2 else "leaf_dark", d, 22.5 + (k % 2) * 22.5)
        if stage >= 2:
            cube(m, x, B + h + 0.4, z, 1.6, 1.0, 1.6, "leaf")
        if stage == 3 and i % 2 == 0:
            cube(m, x, B + h + 1.1, z, 1.2, 0.8, 1.2, "flower")
            cube(m, x, B + h + 1.55, z, 0.5, 0.3, 0.5, "flower_eye")
    if stage == 3:
        cube(m, 8.0, B + 0.6, 8.3, 2.6, 1.6, 2.2, "potato")
        cube(m, 9.6, B + 0.4, 6.7, 1.8, 1.2, 1.8, "potato")
    return m


def beetroots(stage):
    m = Model(PAL["beetroot"], tex("beetroot"), 400 + stage)
    pts = spots(10, [(4, 4), (12, 4), (4, 12), (12, 12)], 0.7)
    for i, (x, z) in enumerate(pts):
        if stage == 0:
            blade(m, x, z, B, 1.4, "stem", w=0.5, t=0.5)
            cube(m, x, B + 1.6, z, 1.4, 0.4, 1.0, "leaf")
            continue
        h = {1: 3.0, 2: 4.6, 3: 5.8}[stage]
        w = {1: 1.8, 2: 2.4, 3: 2.8}[stage]
        for k in range(2 + stage):
            axis, angle = TILTS[(i + k) % len(TILTS)]
            blade(m, x, z, B + 0.6, h, "leaf" if k % 2 == 0 else "leaf_dark", w=w, t=0.4, axis=axis, angle=angle)
            blade(m, x, z, B, 1.2, "stem", w=0.5, t=0.5, axis=axis, angle=angle)
        if stage == 3:
            cube(m, x, B + 0.6, z, 3.2, 2.2, 3.2, "bulb")
            cube(m, x, B + 1.8, z, 2.0, 0.4, 2.0, "bulb_top")
    return m


def nether_wart(stage):
    m = Model(PAL["nether_wart"], tex("nether_wart"), 500 + stage)
    pts = spots(11, [(3.5, 4), (8, 3), (12.5, 4.5), (4, 9), (9, 8.5), (13, 10), (5.5, 13), (10.5, 13)], 0.8)
    r = random.Random(55)
    for i, (x, z) in enumerate(pts):
        if stage == 0:
            if i % 2 == 0:
                cube(m, x, B + 0.8, z, 1.0, 1.6, 1.0, "stem")
                cube(m, x, B + 1.8, z, 1.4, 1.0, 1.4, "wart")
            continue
        h = (2.5 if stage == 1 else 5.0) + r.uniform(-0.8, 1.6)
        blade(m, x, z, B, h, "stem", w=0.9, t=0.9)
        head = 1.6 if stage == 1 else 2.4
        cube(m, x, B + h + head / 2 - 0.3, z, head, head * 0.9, head, "wart_light" if i % 3 == 0 else "wart")
        if stage == 2 and i % 2 == 1:
            cube(m, x + 0.9, B + h * 0.55, z, 1.3, 1.2, 1.3, "wart")
    return m


def sweet_berry_bush(stage):
    m = Model(PAL["sweet_berries"], tex("sweet_berries"), 600 + stage)
    r = random.Random(66 + stage)
    if stage == 0:
        tuft(m, 8, 8, 0, 3, 3.0, "leaf", w=1.6, seed=3, mats=["leaf", "leaf_dark"])
        cube(m, 6.5, 1.2, 9, 2.0, 2.0, 2.0, "leaf_dark")
        return m
    radius = {1: 3.5, 2: 5.5, 3: 6.0}[stage]
    height = {1: 6.0, 2: 10.0, 3: 11.0}[stage]
    blade(m, 8, 8, 0, height * 0.5, "stem", w=1.2, t=1.2)
    # el arbusto: racimos de hojas en una cúpula
    clusters = []
    for k in range(10 + stage * 6):
        a = r.uniform(0, 6.283)
        d = r.uniform(0.2, 1.0) * radius
        y = r.uniform(0.25, 1.0) * height * (1.0 - 0.45 * (d / radius) ** 2)
        x = 8 + d * __import__("math").cos(a)
        z = 8 + d * __import__("math").sin(a)
        s = r.uniform(2.4, 3.6)
        cube(m, x, max(s / 2, y - s / 2), z, s, s, s, "leaf" if k % 3 else "leaf_dark")
        clusters.append((x, max(s / 2, y - s / 2), z, s))
    if stage >= 2:
        for k, (x, y, z, s) in enumerate(clusters):
            if k % 2 == 0 or stage == 3:
                ripe = stage == 3 or k % 4 == 0
                bx = 8 + (x - 8) * 1.08 + (s / 2 if x >= 8 else -s / 2)
                cube(m, bx, y + s * 0.3 + r.uniform(-0.4, 0.6), z + r.uniform(-0.6, 0.6), 1.7, 1.7, 1.7, "berry" if ripe else "berry_green")
    return m


def tomato(stage):
    m = Model(PAL["tomato"], tex("tomato"), 700 + stage)
    if stage == 0:
        for (x, z) in ((6.5, 7), (9.5, 9)):
            blade(m, x, z, B, 2.0, "stem", w=0.6, t=0.6)
            flat_leaf(m, x, z, B + 1.8, 1.4, 1.0, "leaf", "e", 22.5)
            flat_leaf(m, x, z, B + 1.8, 1.4, 1.0, "leaf", "w", 22.5)
        return m
    h = {1: 5.5, 2: 10.0, 3: 13.0}[stage]
    if stage >= 2:
        # la caña de apoyo y su atadura
        m.box(9.0, B, 9.0, 10.0, B + 15.0, 10.0, "stake")
        for y in (B + 4, B + 9):
            m.box(8.6, y, 8.6, 10.4, y + 0.6, 10.4, "twine")
    m.box(7.6, B, 7.6, 8.6, B + h, 8.6, "stem")
    dirs = "nesw"
    n_leaves = {1: 4, 2: 8, 3: 11}[stage]
    for k in range(n_leaves):
        y = B + 1.5 + (h - 2.0) * k / max(1, n_leaves - 1)
        flat_leaf(m, 8.1, 8.1, y, 2.6 + stage * 0.4, 1.8, "leaf" if k % 2 else "leaf_dark", dirs[k % 4], 22.5)
    if stage >= 2:
        fruit = [(5.5, 4.0, 7.0), (11.0, 6.5, 6.5), (6.5, 8.0, 11.0), (10.5, 3.0, 11.5), (7.0, 10.5, 5.5)]
        for k, (x, y, z) in enumerate(fruit[: 3 if stage == 2 else 5]):
            s = 1.8 if stage == 2 else 2.4
            cube(m, x, B + y, z, s, s * 0.9, s, "red" if stage == 3 else "green")
            cube(m, x, B + y + s * 0.45 + 0.15, z, 1.0, 0.3, 1.0, "calyx")
    return m


def corn(stage):
    m = Model(PAL["corn"], tex("corn"), 800 + stage)
    pts = [(5.0, 5.5), (11.0, 10.5)]
    for i, (x, z) in enumerate(pts):
        if stage == 0:
            tuft(m, x, z, B, 2, 2.2, "leaf", w=0.9, seed=i, tilt_from=i)
            continue
        h = {1: 5.0, 2: 11.0, 3: 16.0}[stage]
        m.box(x - 0.8, B, z - 0.8, x + 0.8, B + h, z + 0.8, "stalk")
        n = {1: 2, 2: 4, 3: 6}[stage]
        for k in range(n):
            axis, angle = [("z", 45.0), ("x", -45.0), ("z", -45.0), ("x", 45.0)][(k + i) % 4]
            y = B + 1.0 + (h - 3.0) * k / max(1, n - 1) * 0.8
            blade(m, x, z, y, 3.5 + stage * 1.2, "leaf", w=1.4, t=0.4, axis=axis, angle=angle)
        if stage == 3:
            # la mazorca, de lado, con las barbas, y la espiga arriba
            cube(m, x + 1.7, B + 8.0, z, 1.8, 4.8, 1.8, "kernel", rot=("z", -22.5, (x + 0.8, B + 6.0, z)))
            cube(m, x + 1.6, B + 7.4, z, 2.3, 4.0, 2.3, "husk", rot=("z", -22.5, (x + 0.8, B + 6.0, z)))
            cube(m, x + 2.6, B + 10.6, z, 0.6, 1.4, 0.6, "silk", rot=("z", -22.5, (x + 0.8, B + 6.0, z)))
            for k, (axis, angle) in enumerate([("z", 22.5), ("x", 22.5), ("z", -22.5), ("x", -22.5)]):
                blade(m, x, z, B + h - 0.5, 2.6, "tassel", w=0.4, t=0.4, axis=axis, angle=angle)
        elif stage == 2:
            cube(m, x + 1.3, B + 7.0, z, 1.4, 2.6, 1.4, "husk")
    return m


def cabbage(stage):
    m = Model(PAL["cabbage"], tex("cabbage"), 900 + stage)
    if stage <= 1:
        pts = spots(12, [(4.5, 4.5), (11.5, 4.5), (4.5, 11.5), (11.5, 11.5)], 0.5)
        for (x, z) in pts:
            if stage == 0:
                blade(m, x, z, B, 1.4, "sprout", w=0.6, t=0.6)
                cube(m, x, B + 1.5, z, 1.6, 0.4, 1.2, "outer")
            else:
                for d in "nsew":
                    flat_leaf(m, x, z, B + 0.6, 2.4, 2.0, "outer", d, 22.5)
                cube(m, x, B + 1.0, z, 1.6, 1.6, 1.6, "inner")
        return m
    big = stage == 3
    leaf = 4.5 if big else 3.6
    for k, d in enumerate("nsew"):
        flat_leaf(m, 8, 8, B + 0.5, leaf + 1.5, leaf, "outer", d, 22.5)
    for k, d in enumerate("nsew"):
        flat_leaf(m, 8, 8, B + 1.5, leaf, leaf * 0.8, "outer", d, 45.0)
    if big:
        cube(m, 8, B + 3.4, 8, 6.4, 4.6, 6.4, "head")
        cube(m, 8, B + 3.6, 8, 5.2, 5.6, 7.0, "inner")
        cube(m, 8, B + 3.6, 8, 7.0, 5.0, 5.2, "inner")
        cube(m, 8, B + 6.1, 8, 4.0, 0.8, 4.0, "head")
    else:
        cube(m, 8, B + 2.4, 8, 3.8, 3.0, 3.8, "head")
        cube(m, 8, B + 2.4, 8, 3.0, 3.4, 4.4, "inner")
    return m


def onion(stage):
    m = Model(PAL["onion"], tex("onion"), 1000 + stage)
    pts = spots(13, [(4, 4.5), (11.5, 4), (8, 8.5), (4.5, 12), (12, 12)], 0.6)
    for i, (x, z) in enumerate(pts):
        if stage == 0:
            blade(m, x, z, B, 1.8, "leaf_light", w=0.6, t=0.6)
            continue
        h = {1: 4.0, 2: 6.5, 3: 7.5}[stage]
        for k in range(1 + stage):
            axis, angle = [("z", 0.0), ("z", 22.5), ("x", -22.5), ("z", -45.0)][k] if stage < 3 else TILTS[(i + k) % len(TILTS)]
            blade(m, x, z, B + 0.4, h * (1.0 - 0.12 * k), "leaf" if k % 2 == 0 else "leaf_light", w=0.9, t=0.9, axis=axis, angle=angle)
        if stage >= 2:
            s = 1.8 if stage == 2 else 3.0
            cube(m, x, B + s * 0.35, z, s, s * 0.8, s, "bulb")
            cube(m, x, B + s * 0.75 + 0.1, z, s * 0.5, 0.5, s * 0.5, "bulb_top")
    return m


def garlic(stage):
    m = Model(PAL["garlic"], tex("garlic"), 1100 + stage)
    pts = spots(14, [(3.5, 4), (8.5, 3.5), (13, 5), (5, 9), (10.5, 9.5), (4, 13), (9, 13), (13, 13)], 0.6)
    for i, (x, z) in enumerate(pts):
        if stage == 0:
            if i % 2 == 0:
                blade(m, x, z, B, 1.6, "leaf", w=0.8, t=0.4)
            continue
        h = {1: 3.2, 2: 5.0, 3: 6.0}[stage]
        for k in range(2 + (stage >= 2)):
            axis, angle = TILTS[(i * 3 + k) % len(TILTS)]
            blade(m, x, z, B + 0.3, h * (1.0 - 0.15 * k), "leaf" if k else "leaf_dark", w=1.2, t=0.3, axis=axis, angle=angle * 0.5 if angle == 45.0 else angle)
        blade(m, x, z, B, 1.0, "stem", w=0.7, t=0.7)
        if stage == 3:
            cube(m, x, B + 0.6, z, 2.2, 1.6, 2.2, "bulb")
            cube(m, x, B + 1.5, z, 1.0, 0.4, 1.0, "bulb_purple")
    return m


# ------------------------------------------------------------------ lo cosechado (objetos)

DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [0.8, 0.8, 0.8]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
}


def item_carrot():
    m = Model(PAL["carrot"], tex("carrot"), 1)
    rot = ("z", 45.0, (8, 8, 8))
    for (y0, y1, w) in ((1.5, 4.0, 1.6), (4.0, 7.0, 2.6), (7.0, 10.0, 3.4), (10.0, 13.0, 4.0)):
        cube(m, 8, (y0 + y1) / 2, 8, w, y1 - y0, w, "root", rot=rot)
    for k, (dx, dz, h) in enumerate(((-0.8, 0, 3.0), (0.6, 0.6, 3.6), (0.3, -0.8, 2.6), (0, 0, 4.0))):
        cube(m, 8 + dx, 13.0 + h / 2, 8 + dz, 0.8, h, 0.8, "leaf" if k % 2 else "leaf_dark", rot=rot)
    return m


def item_potato(kind="potato"):
    m = Model(PAL["poisonous_potato"] if kind == "poison" else PAL["potato"], tex("poisonous_potato" if kind == "poison" else "potato"), 2)
    skin = {"potato": "potato", "baked": "baked", "poison": "poison"}[kind]
    rot = ("y", 22.5, (8, 8, 8))
    cube(m, 8, 7, 8, 7.0, 4.6, 5.0, skin, rot=rot)
    cube(m, 8, 7, 8, 5.6, 5.4, 4.0, skin, rot=rot)
    cube(m, 8.6, 7.2, 8, 4.4, 4.0, 6.0, skin, rot=rot)
    cube(m, 5.2, 6.8, 8, 1.4, 3.4, 3.6, skin, rot=rot)
    if kind == "baked":
        cube(m, 8, 9.55, 8, 4.0, 0.5, 2.2, "baked_inside", rot=rot)
    if kind == "poison":
        cube(m, 9.5, 9.3, 7.0, 1.2, 0.6, 1.2, "poison_dark", rot=rot)
    return m


def item_beetroot():
    m = Model(PAL["beetroot"], tex("beetroot"), 3)
    cube(m, 8, 6.5, 8, 5.6, 4.6, 5.6, "bulb")
    cube(m, 8, 6.5, 8, 6.4, 3.0, 4.4, "bulb")
    cube(m, 8, 6.5, 8, 4.4, 3.0, 6.4, "bulb")
    cube(m, 8, 3.4, 8, 3.0, 1.8, 3.0, "bulb")
    cube(m, 8, 1.6, 8, 1.0, 2.0, 1.0, "bulb_top")
    cube(m, 8, 9.0, 8, 3.0, 0.6, 3.0, "bulb_top")
    for k, (axis, angle) in enumerate((("z", 22.5), ("x", -22.5), ("z", -22.5))):
        blade(m, 8, 8, 9.2, 4.0, "stem", w=0.6, t=0.6, axis=axis, angle=angle)
    return m


def item_wheat():
    m = Model(PAL["wheat"], tex("wheat"), 4)
    rot = ("z", 22.5, (8, 8, 8))
    ring = [(8, 8), (6.6, 7.6), (9.4, 7.4), (7.4, 9.4), (8.8, 9.6), (6.8, 9.0), (9.6, 8.8)]
    for k, (x, z) in enumerate(ring):
        m.box(x - 0.4, 1.0 + (k % 2) * 0.5, z - 0.4, x + 0.4, 12.0 + (k % 3) * 0.5, z + 0.4, "straw", rot=rot)
        cube(m, x, 13.4 + (k % 3) * 0.5, z, 1.4, 3.0, 1.4, "grain", rot=rot)
    cube(m, 8.1, 6.0, 8.5, 4.2, 1.4, 4.2, "twine", rot=rot)
    return m


def item_nether_wart():
    m = Model(PAL["nether_wart"], tex("nether_wart"), 5)
    for (x, y, z, s) in ((7, 9, 8, 3.6), (10, 7, 7, 2.8), (6, 6, 10, 2.4), (8.5, 4.5, 9, 2.0)):
        cube(m, x, y, z, s, s * 0.9, s, "wart" if s > 2.5 else "wart_light")
    m.box(7.6, 2.5, 7.6, 8.4, 8.0, 8.4, "stem")
    return m


def item_sweet_berries():
    m = Model(PAL["sweet_berries"], tex("sweet_berries"), 6)
    for (x, y, z) in ((6.5, 6, 8), (9.5, 6.4, 7.4), (8, 5.2, 10), (8.2, 8.6, 8.2)):
        cube(m, x, y, z, 2.6, 2.6, 2.6, "berry")
    m.box(7.7, 9.8, 7.7, 8.5, 13.0, 8.5, "stem")
    cube(m, 9.2, 12.6, 8.1, 2.2, 0.5, 1.4, "leaf")
    return m


def item_tomato():
    m = Model(PAL["tomato"], tex("tomato"), 7)
    cube(m, 8, 7, 8, 6.4, 5.0, 6.4, "red")
    cube(m, 8, 7, 8, 7.4, 3.6, 5.2, "red")
    cube(m, 8, 7, 8, 5.2, 3.6, 7.4, "red")
    cube(m, 8, 9.7, 8, 3.2, 0.6, 1.0, "calyx")
    cube(m, 8, 9.7, 8, 1.0, 0.6, 3.2, "calyx")
    m.box(7.6, 9.9, 7.6, 8.4, 11.2, 8.4, "stem")
    return m


def item_corn(roasted=False):
    m = Model(PAL["corn"], tex("corn"), 8)
    rot = ("z", 45.0, (8, 8, 8))
    cube(m, 8, 9, 8, 3.2, 10.0, 3.2, "roasted" if roasted else "kernel", rot=rot)
    cube(m, 8, 3.2, 8, 1.6, 2.0, 1.6, "husk_dry" if roasted else "stalk", rot=rot)
    if not roasted:
        m.box(6.2, 3.5, 7.2, 6.6, 11.0, 8.8, "husk", rot=rot)
        m.box(9.4, 3.5, 7.2, 9.8, 10.0, 8.8, "husk", rot=rot)
        m.box(7.2, 3.5, 9.4, 8.8, 9.0, 9.8, "husk", rot=rot)
        cube(m, 8, 14.6, 8, 0.8, 1.4, 0.8, "silk", rot=rot)
    return m


def item_cabbage():
    m = Model(PAL["cabbage"], tex("cabbage"), 9)
    cube(m, 8, 7.5, 8, 7.4, 6.0, 7.4, "head")
    cube(m, 8, 7.5, 8, 6.0, 7.2, 8.4, "inner")
    cube(m, 8, 7.5, 8, 8.4, 6.6, 6.0, "outer")
    cube(m, 8, 11.0, 8, 4.4, 0.8, 4.4, "inner")
    cube(m, 8, 4.0, 8, 2.0, 1.2, 2.0, "stem")
    return m


def item_onion():
    m = Model(PAL["onion"], tex("onion"), 10)
    cube(m, 8, 6.5, 8, 6.0, 4.6, 6.0, "bulb")
    cube(m, 8, 6.5, 8, 4.6, 6.2, 4.6, "bulb")
    cube(m, 8, 10.4, 8, 2.4, 1.6, 2.4, "bulb_top")
    m.box(7.5, 11.0, 7.5, 8.5, 13.5, 8.5, "leaf")
    for (dx, dz) in ((-0.8, 0), (0.6, 0.5), (0, -0.7)):
        m.box(8 + dx - 0.2, 2.6, 8 + dz - 0.2, 8 + dx + 0.2, 3.6, 8 + dz + 0.2, "root")
    return m


def item_garlic():
    m = Model(PAL["garlic"], tex("garlic"), 11)
    cube(m, 8, 6.0, 8, 6.0, 4.2, 6.0, "bulb")
    for (x, z) in ((5.6, 8), (10.4, 8), (8, 5.6), (8, 10.4)):
        cube(m, x, 6.2, z, 2.2, 3.4, 2.2, "bulb")
    cube(m, 8, 8.6, 8, 3.0, 1.2, 3.0, "bulb_purple")
    m.box(7.5, 9.0, 7.5, 8.5, 12.5, 8.5, "bulb")
    return m


# ------------------------------------------------------------------ todo lo que hay

# (nombre del bloque, generador, número de etapas, namespace)
VANILLA_CROPS = [
    ("wheat", lambda s: wheat(s, 8), 8),
    ("carrots", carrots, 4),
    ("potatoes", potatoes, 4),
    ("beetroots", beetroots, 4),
    ("nether_wart", nether_wart, 3),
    ("sweet_berry_bush", sweet_berry_bush, 4),
]
NEW_CROPS = [
    ("tomatoes", tomato, 4),
    ("corn", corn, 4),
    ("cabbages", cabbage, 4),
    ("onions", onion, 4),
    ("garlic", garlic, 4),
]
VANILLA_ITEMS = {
    "carrot": item_carrot, "potato": lambda: item_potato("potato"), "baked_potato": lambda: item_potato("baked"),
    "poisonous_potato": lambda: item_potato("poison"), "beetroot": item_beetroot, "wheat": item_wheat,
    "nether_wart": item_nether_wart, "sweet_berries": item_sweet_berries,
}
NEW_ITEMS = {
    "tomato": item_tomato, "corn": lambda: item_corn(False), "roasted_corn": lambda: item_corn(True), "cabbage": item_cabbage,
    "onion": item_onion, "garlic": item_garlic,
}
# objetos planos de siempre (semillas y el guiso)
FLAT_ITEMS = ["tomato_seeds", "corn_seeds", "cabbage_seeds", "vegetable_stew"]


def stage_of(age, stages):
    """Las edades 0-7 de un cultivo de 4 etapas, como las zanahorias de vanilla (0-1, 2-3, 4-6, 7)."""
    if stages == 8:
        return age
    return {0: 0, 1: 0, 2: 1, 3: 1, 4: 2, 5: 2, 6: 2, 7: 3}[age]


def flat_icon(name):
    """Los iconos planos (16x16): las semillas de los cultivos nuevos y el guiso de verduras."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    r = random.Random(name)
    if name.endswith("_seeds"):
        color = {"tomato_seeds": (236, 214, 150), "corn_seeds": (242, 200, 62), "cabbage_seeds": (96, 70, 52)}[name]
        dark = tuple(int(c * 0.7) for c in color)
        for k in range(7):
            x, y = r.randint(3, 11), r.randint(4, 11)
            d.rectangle([x, y, x + 1, y + (1 if name != "corn_seeds" else 2)], fill=color + (255,))
            im.putpixel((x + 1, y + 1), dark + (255,))
    else:
        # un cuenco con verduras
        bowl, bowl_d = (140, 96, 56), (100, 66, 36)
        d.rectangle([2, 9, 13, 11], fill=bowl + (255,))
        d.rectangle([3, 12, 12, 13], fill=bowl_d + (255,))
        d.rectangle([4, 14, 11, 14], fill=bowl_d + (255,))
        d.rectangle([3, 7, 12, 8], fill=(196, 92, 40, 255))
        for (x, y, c) in ((4, 6, (212, 40, 30)), (7, 6, (172, 212, 132)), (10, 6, (238, 132, 30)), (6, 7, (240, 220, 180)),
                          (9, 7, (100, 160, 60)), (11, 7, (212, 40, 30))):
            d.rectangle([x, y, x + 1, y + 1], fill=c + (255,))
        d.line([(2, 9), (13, 9)], fill=(60, 40, 24, 255))
    return im


TEXTS = {
    "block.evolvedwarfare.tomatoes": ("Tomateras", "Tomato Plants"),
    "block.evolvedwarfare.corn": ("Maíz", "Corn"),
    "block.evolvedwarfare.cabbages": ("Coles", "Cabbages"),
    "block.evolvedwarfare.onions": ("Cebollas", "Onions"),
    "block.evolvedwarfare.garlic": ("Ajos", "Garlic"),
    "item.evolvedwarfare.tomato": ("Tomate", "Tomato"),
    "item.evolvedwarfare.tomato_seeds": ("Semillas de tomate", "Tomato Seeds"),
    "item.evolvedwarfare.corn": ("Mazorca de maíz", "Corn"),
    "item.evolvedwarfare.corn_seeds": ("Granos de maíz", "Corn Kernels"),
    "item.evolvedwarfare.roasted_corn": ("Mazorca asada", "Roasted Corn"),
    "item.evolvedwarfare.cabbage": ("Col", "Cabbage"),
    "item.evolvedwarfare.cabbage_seeds": ("Semillas de col", "Cabbage Seeds"),
    "item.evolvedwarfare.onion": ("Cebolla", "Onion"),
    "item.evolvedwarfare.garlic": ("Ajo", "Garlic"),
    "item.evolvedwarfare.vegetable_stew": ("Guiso de verduras", "Vegetable Stew"),
    "item.evolvedwarfare.tomato.desc": ("Se come crudo o en el guiso. Sus semillas: un tomate en la mesa.",
                                        "Eat it raw or in a stew. Seeds: one tomato in the crafting grid."),
    "item.evolvedwarfare.corn.desc": ("Ásala en el horno o en la fogata. Sus granos (semillas): una mazorca en la mesa.",
                                      "Roast it in a furnace or on a campfire. Kernels (seeds): one cob in the crafting grid."),
    "item.evolvedwarfare.cabbage.desc": ("Crece en otoño y en invierno. Sus semillas: una col en la mesa.",
                                         "Grows in autumn and winter. Seeds: one cabbage in the crafting grid."),
    "item.evolvedwarfare.onion.desc": ("Se planta la propia cebolla.", "Plant the onion itself."),
    "item.evolvedwarfare.garlic.desc": ("Se planta el propio ajo. Comerlo quita los parásitos.", "Plant the garlic itself. Eating it cures parasites."),
    "item.evolvedwarfare.vegetable_stew.desc": ("Alimenta mucho, calienta y quita la sed.", "Very filling, warms you up and quenches thirst."),
    "tooltip.evolvedwarfare.crop.seasons": ("Crece en: %s", "Grows in: %s"),
    "tooltip.evolvedwarfare.crop.all_seasons": ("Crece en todas las estaciones", "Grows in every season"),
    "tooltip.evolvedwarfare.crop.indoors": ("Fuera de temporada, solo bajo techo y despacio", "Out of season, only under a roof and slowly"),
    "season.evolvedwarfare.list_sep": (", ", ", "),
}


def loot(block, produce, seeds):
    """Como las zanahorias (si se planta lo que se cosecha) o como la remolacha (semillas aparte)."""
    mature = {"condition": "minecraft:block_state_property", "block": "evolvedwarfare:" + block, "properties": {"age": "7"}}
    bonus = {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:binomial_with_bonus_count",
             "parameters": {"extra": 3, "probability": 0.5714286}}
    if seeds is None:
        pools = [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "evolvedwarfare:" + produce}]},
                 {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "evolvedwarfare:" + produce, "functions": [bonus]}],
                  "conditions": [mature]}]
    else:
        pools = [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": "evolvedwarfare:" + produce, "conditions": [mature]},
            {"type": "minecraft:item", "name": "evolvedwarfare:" + seeds}]}]},
                 {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "evolvedwarfare:" + seeds, "functions": [bonus]}],
                  "conditions": [mature]}]
    return {"type": "minecraft:block", "functions": [{"function": "minecraft:explosion_decay"}], "pools": pools,
            "random_sequence": "evolvedwarfare:blocks/" + block}


NEW_LOOT = {"tomatoes": ("tomato", "tomato_seeds"), "corn": ("corn", "corn_seeds"), "cabbages": ("cabbage", "cabbage_seeds"),
            "onions": ("onion", None), "garlic": ("garlic", None)}


def main():
    # texturas de materiales
    tex_dir = os.path.join(ASSETS, "textures/block/crop")
    os.makedirs(tex_dir, exist_ok=True)
    images = {}
    for name, pal in PAL.items():
        pal.im.save(os.path.join(tex_dir, name + ".png"))
        images[tex(name)] = pal.im
    # cultivos de vanilla: se reemplazan sus modelos (los estados de bloque de vanilla ya apuntan a ellos)
    previews = []
    for block, gen, n in VANILLA_CROPS:
        row = []
        for s in range(n):
            model = gen(s).json()
            write_json(os.path.join(MC_ASSETS, "models/block/%s_stage%d.json" % (block, s)), model)
            row.append(model)
        previews.append((block, row))
    # cultivos nuevos: modelos, estados (edad 0-7) y botín
    for block, gen, n in NEW_CROPS:
        row = []
        for s in range(n):
            model = gen(s).json()
            write_json(os.path.join(ASSETS, "models/block/%s_stage%d.json" % (block, s)), model)
            row.append(model)
        previews.append((block, row))
        write_json(os.path.join(ASSETS, "blockstates/%s.json" % block),
                   {"variants": {"age=%d" % a: {"model": "%s:block/%s_stage%d" % (NS, block, stage_of(a, n))} for a in range(8)}})
        produce, seeds = NEW_LOOT[block]
        write_json(os.path.join(DATA, "loot_table/blocks/%s.json" % block), loot(block, produce, seeds))
    # lo cosechado en 3D
    item_models = []
    for name, gen in VANILLA_ITEMS.items():
        model = gen().json(display=DISPLAY)
        model["gui_light"] = "side"
        write_json(os.path.join(MC_ASSETS, "models/item/%s.json" % name), model)
        item_models.append((name, model))
    for name, gen in NEW_ITEMS.items():
        model = gen().json(display=DISPLAY)
        model["gui_light"] = "side"
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name), model)
        item_models.append((name, model))
    for name in FLAT_ITEMS:
        flat_icon(name).save(os.path.join(ASSETS, "textures/item/%s.png" % name))
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    # recetas
    rec = os.path.join(DATA, "recipe")
    for produce, seeds, n in (("tomato", "tomato_seeds", 2), ("corn", "corn_seeds", 3), ("cabbage", "cabbage_seeds", 2)):
        write_json(os.path.join(rec, seeds + ".json"),
                   {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [{"item": "evolvedwarfare:" + produce}],
                    "result": {"id": "evolvedwarfare:" + seeds, "count": n}})
    for kind, time in (("smelting", 200), ("smoking", 100), ("campfire_cooking", 600)):
        write_json(os.path.join(rec, "roasted_corn_from_%s.json" % kind),
                   {"type": "minecraft:" + kind, "category": "food", "ingredient": {"item": "evolvedwarfare:corn"},
                    "result": {"id": "evolvedwarfare:roasted_corn", "count": 1}, "experience": 0.35, "cookingtime": time})
    write_json(os.path.join(rec, "vegetable_stew.json"),
               {"type": "minecraft:crafting_shapeless", "category": "misc",
                "ingredients": [{"item": "minecraft:bowl"}, {"item": "evolvedwarfare:tomato"}, {"item": "evolvedwarfare:cabbage"},
                                {"item": "evolvedwarfare:onion"}, [{"item": "minecraft:carrot"}, {"item": "minecraft:potato"}]],
                "result": {"id": "evolvedwarfare:vegetable_stew", "count": 1}})
    # se venden en la Caja de víveres, como los demás cultivos
    tag_path = os.path.join(DATA, "tags/item/crate_crops.json")
    with open(tag_path, encoding="utf-8") as f:
        tag = json.load(f)
    for name in ("tomato", "corn", "cabbage", "onion", "garlic", "roasted_corn"):
        if "evolvedwarfare:" + name not in tag["values"]:
            tag["values"].append("evolvedwarfare:" + name)
    with open(tag_path, "w", encoding="utf-8") as f:
        json.dump(tag, f, ensure_ascii=False, indent=2)
        f.write("\n")
    # textos
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        with open(path, "w", encoding="utf-8") as f:
            json.dump(lang, f, ensure_ascii=False, indent=2)
            f.write("\n")
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        ground = Model(PAL["ground"], tex("ground"), 0)
        ground.box(0, -16, 0, 16, -1, 16, "farmland")
        grass = Model(PAL["ground"], tex("ground"), 0)
        grass.box(0, -16, 0, 16, 0, 16, "grass")
        cell = 150
        rows = len(previews)
        sheet = Image.new("RGB", (cell * 8, cell * rows), (60, 64, 70))
        for ri, (block, row) in enumerate(previews):
            base = grass.json() if block == "sweet_berry_bush" else ground.json()
            for si, model in enumerate(row):
                im = render([model], images, size=cell, scale=5.2, ground=(base, (0, 0, 0)), bg=(60 + 6 * (ri % 2), 64, 70))
                sheet.paste(im, (si * cell, ri * cell))
        sheet.save(os.path.join(PREVIEW, "crops_stages.png"))
        cols = 7
        sheet = Image.new("RGB", (cell * cols, cell * ((len(item_models) + cols - 1) // cols)), (139, 139, 139))
        for k, (name, model) in enumerate(item_models):
            im = render([model], images, size=cell, scale=7.0, yaw=225 - 180, pitch=30, bg=(139, 139, 139))
            sheet.paste(im, ((k % cols) * cell, (k // cols) * cell))
        sheet.save(os.path.join(PREVIEW, "crops_items.png"))
        icons = Image.new("RGBA", (16 * len(FLAT_ITEMS), 16), (139, 139, 139, 255))
        for k, name in enumerate(FLAT_ITEMS):
            icons.alpha_composite(flat_icon(name), (16 * k, 0))
        icons.resize((icons.width * 8, icons.height * 8), Image.NEAREST).save(os.path.join(PREVIEW, "crops_icons.png"))
    print("cultivos 3D: %d de vanilla y %d nuevos, %d cosechas en 3D" % (len(VANILLA_CROPS), len(NEW_CROPS), len(item_models)))


if __name__ == "__main__":
    main()
