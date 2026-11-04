"""Los animales salvajes (fase 17): 20 especies con su modelo 3D (cubos, como los de vanilla), su textura pintada, lo que
sueltan al morir (botín), sus objetos (carnes, pieles, cornamentas...), los huevos de aparición y los textos.

El modelo de cada especie se escribe en assets/evolvedwarfare/wildlife/<especie>.json (partes, pivotes, giros, cubos y
su sitio en la textura); el juego lo lee al cargar los modelos y lo anima según el papel de cada parte (patas, cabeza,
cuello, cola, orejas, alas). La textura se pinta aquí con las mismas cuentas de UV de caja que ModelPart.

Uso: python3 tools/ew_wildlife.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ew_model3d import render_entity  # noqa: E402

args = sys.argv[1:]
PREVIEW = None
if "--preview" in args:
    i = args.index("--preview")
    PREVIEW = args[i + 1]
    del args[i:i + 2]
ROOT = args[0] if args else os.path.join(os.path.dirname(__file__), "..")
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)


def write_json(path, obj, indent=1):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=indent)
        f.write("\n")


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, f):
    return tuple(clamp(x * f) for x in c[:3])


# ------------------------------------------------------------------ el modelo: partes y cubos

class Cube:
    def __init__(self, origin, size, paint):
        self.origin = [float(o) for o in origin]
        self.size = [int(s) for s in size]
        self.paint = paint
        self.uv = None


class Part:
    def __init__(self, name, parent, pivot, rot=(0, 0, 0), role=None):
        self.name = name
        self.parent = parent
        self.pivot = [float(p) for p in pivot]
        self.rot = list(rot)
        self.cubes = []
        self.role = role or {}

    def cube(self, origin, size, **paint):
        c = Cube(origin, size, paint)
        self.cubes.append(c)
        return c


class Animal:
    def __init__(self, sid):
        self.sid = sid
        self.parts = []
        self.by = {}

    def part(self, name, parent, pivot, rot=(0, 0, 0), **role):
        p = Part(name, parent, pivot, rot, role)
        self.parts.append(p)
        self.by[name] = p
        return p

    def pack(self):
        """Coloca las cajas de UV en la textura (estanterías), y devuelve el tamaño."""
        cubes = [c for p in self.parts for c in p.cubes]
        boxes = sorted(cubes, key=lambda c: -(c.size[2] + c.size[1]))
        for width in (64, 128, 256):
            x = y = shelf = 0
            ok = True
            for c in boxes:
                dx, dy, dz = c.size
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
        raise ValueError("no cabe: " + self.sid)


# ------------------------------------------------------------------ pintar las cajas

def regions(c):
    u, v = c.uv
    dx, dy, dz = c.size
    return {
        "top": (u + dz, v, dx, dz), "bottom": (u + dz + dx, v, dx, dz),
        "right": (u, v + dz, dz, dy), "front": (u + dz, v + dz, dx, dy),
        "left": (u + dz + dx, v + dz, dz, dy), "back": (u + 2 * dz + dx, v + dz, dx, dy),
    }


SIDES = ("right", "front", "left", "back")


def paint_cube(img, c, rng):
    """Pinta una caja: color, ruido, sombra de arriba abajo y los adornos (vientre, pezuñas, ojos, nariz, manchas...)."""
    p = c.paint
    base = p.get("color", (128, 128, 128))
    noise = p.get("noise", 0.07)
    reg = regions(c)
    px = img.load()

    def put(x, y, col):
        if 0 <= x < img.width and 0 <= y < img.height:
            px[x, y] = tuple(col[:3]) + (255,)

    def fill(name, col, rows=None, cols=None, grad=True):
        rx, ry, w, h = reg[name]
        for yy in range(h):
            if rows is not None and yy not in rows:
                continue
            for xx in range(w):
                if cols is not None and xx not in cols:
                    continue
                f = 1.0 + rng.uniform(-noise, noise)
                if grad and name in SIDES and h > 2:
                    f *= 1.08 - 0.16 * yy / (h - 1)
                put(rx + xx, ry + yy, shade(col, f))

    for name in reg:
        fill(name, base)
    if "top" in p:
        fill("top", p["top"])
    if "back_color" in p:
        fill("back", p["back_color"])
    if "front_color" in p:
        fill("front", p["front_color"])
    if "belly" in p:
        col, n = p["belly"]
        fill("bottom", col, grad=False)
        for name in SIDES:
            h = reg[name][3]
            fill(name, col, rows=range(max(0, h - n), h))
    if "low" in p:
        col, n = p["low"]
        fill("bottom", col, grad=False)
        for name in SIDES:
            h = reg[name][3]
            fill(name, col, rows=range(max(0, h - n), h), grad=False)
    if "high" in p:
        col, n = p["high"]
        fill("top", col, grad=False)
        for name in SIDES:
            fill(name, col, rows=range(0, n), grad=False)
    if "tip" in p:
        # el final (+z) de una cola o un cuerno a lo largo
        col, n = p["tip"]
        fill("back", col, grad=False)
        dz = c.size[2]
        fill("right", col, cols=range(0, n), grad=False)
        fill("left", col, cols=range(dz - n, dz), grad=False)
        fill("top", col, rows=range(0, n), grad=False)
        fill("bottom", col, rows=range(dz - n, dz), grad=False)
    if "rings" in p:
        # anillos a lo largo de z (colas de mapache, cuernos)
        col, step = p["rings"]
        dz = c.size[2]
        for k in range(dz):
            if (k // step) % 2 == 1:
                fill("right", col, cols=[dz - 1 - k], grad=False)
                fill("left", col, cols=[k], grad=False)
                fill("top", col, rows=[dz - 1 - k], grad=False)
                fill("bottom", col, rows=[k], grad=False)
    if "bands" in p:
        # bandas de arriba abajo (cuernos, patas)
        col, step = p["bands"]
        for name in SIDES:
            h = reg[name][3]
            fill(name, col, rows=[r for r in range(h) if (r // step) % 2 == 1], grad=False)
    if "spots" in p:
        col, density = p["spots"]
        for name in reg:
            rx, ry, w, h = reg[name]
            for yy in range(h):
                for xx in range(w):
                    if rng.random() < density:
                        put(rx + xx, ry + yy, shade(col, 1.0 + rng.uniform(-0.05, 0.05)))
    if "stripe_top" in p:
        # una raya a lo largo del lomo (o de la cabeza)
        col, width = p["stripe_top"]
        rx, ry, w, h = reg["top"]
        c0 = (w - width) // 2
        for yy in range(h):
            for xx in range(c0, c0 + width):
                put(rx + xx, ry + yy, col)
        rx, ry, w, h = reg["front"]
        for yy in range(h):
            for xx in range(c0, min(w, c0 + width)):
                put(rx + xx, ry + yy, col)
    if "side_stripe" in p:
        # la raya oscura del costado (gacela)
        col, row = p["side_stripe"]
        for name in ("right", "left"):
            rx, ry, w, h = reg[name]
            for xx in range(w):
                put(rx + xx, ry + min(h - 1, row), col)
    if "mask" in p:
        # antifaz (mapache): una banda oscura a la altura de los ojos, por delante y por los lados
        col, row, n = p["mask"]
        for name in ("front", "right", "left"):
            rx, ry, w, h = reg[name]
            for yy in range(row, min(h, row + n)):
                for xx in range(w):
                    put(rx + xx, ry + yy, col)
    if "face_stripes" in p:
        # tejón: cara blanca con dos rayas negras de la nariz a las orejas
        col, white = p["face_stripes"]
        for name in ("front", "top"):
            rx, ry, w, h = reg[name]
            for yy in range(h):
                for xx in range(w):
                    put(rx + xx, ry + yy, white)
            for xx in (1, w - 2):
                for yy in range(h):
                    put(rx + xx, ry + yy, col)
    eyes = p.get("eyes")
    if eyes:
        mode, row, col = eyes
        if mode == "side":
            rx, ry, w, h = reg["right"]
            put(rx + w - 2, ry + row, col)
            rx, ry, w, h = reg["left"]
            put(rx + 1, ry + row, col)
        else:
            rx, ry, w, h = reg["front"]
            put(rx + max(0, w // 2 - 2), ry + row, col)
            put(rx + min(w - 1, (w + 1) // 2 + 1), ry + row, col)
            if p.get("eye_shine"):
                put(rx + max(0, w // 2 - 2), ry + row - 1, p["eye_shine"])
                put(rx + min(w - 1, (w + 1) // 2 + 1), ry + row - 1, p["eye_shine"])
    if "nose" in p:
        col = p["nose"]
        rx, ry, w, h = reg["front"]
        for xx in range(max(0, w // 2 - 1), min(w, w // 2 + 1)):
            put(rx + xx, ry + max(0, h - 2), col)
        if w >= 4:
            put(rx + w // 2 - 2, ry + max(0, h - 2), shade(col, 1.3))
            put(rx + w // 2 + 1, ry + max(0, h - 2), shade(col, 1.3))
    if "mouth" in p:
        col = p["mouth"]
        rx, ry, w, h = reg["front"]
        for xx in range(1, w - 1):
            put(rx + xx, ry + h - 1, col)
    if "inner" in p:
        # el interior de las orejas
        col = p["inner"]
        rx, ry, w, h = reg["front"]
        for yy in range(1, h):
            for xx in range(min(1, w - 1), max(1, w - 1)):
                put(rx + xx, ry + yy, col)


# ------------------------------------------------------------------ las piezas de siempre

EYE = (18, 14, 12)
HOOF = (46, 38, 32)
NOSE = (34, 26, 24)


def quadruped(sid, body, leg, head, snout=None, neck=None, ear=None, tail=None, colors=None, front_leg=None, head_y=None,
              head_pitch=0.0, ear_roll=35.0, eye_mode="side", leg_inset=1.0, leg_low=None, gait=1.0):
    """Un cuadrúpedo: tronco horizontal (ancho, alto, largo), cuatro patas hasta el suelo (y = 24), cuello, cabeza, hocico,
    orejas y cola. Devuelve el animal y las partes para añadir lo propio (cuernos, joroba...)."""
    a = Animal(sid)
    col = colors
    bw, bh, bl = body
    lt, ll = leg
    ft, fl = front_leg or leg
    bottom = 24 - ll
    top = bottom - bh
    a.part("body", None, (0, top + bh / 2, 0), role="body").cube((-bw / 2, -bh / 2, -bl / 2), (bw, bh, bl), **col["body"])
    for name, x, z, t, length, phase in (("leg_fl", -1, -1, ft, fl, 0), ("leg_fr", 1, -1, ft, fl, 1), ("leg_bl", -1, 1, lt, ll, 1),
                                         ("leg_br", 1, 1, lt, ll, 0)):
        px = x * (bw / 2 - t / 2 - leg_inset)
        pz = z * (bl / 2 - t / 2 - leg_inset)
        height = 24 - (bottom - 1) if length == ll else 24 - (bottom - 1)
        a.part(name, None, (px, bottom - 1, pz), role="leg", phase=phase, gait=gait).cube((-t / 2, 0, -t / 2), (t, height, t), **col["leg"])
    hw, hh, hl = head
    if neck:
        nw, nh, nl, npitch = neck
        a.part("neck", "body", (0, -bh / 2 + 1, -bl / 2 + nl / 2 + 0.5), (npitch, 0, 0), role="neck").cube(
            (-nw / 2, -nh, -nl / 2), (nw, nh + 1, nl), **col.get("neck", col["body"]))
        a.part("head", "neck", (0, -nh + 1, 0), (-npitch + head_pitch, 0, 0), role="head").cube(
            (-hw / 2, -hh + 1, -hl + nl / 2), (hw, hh, hl), **col["head"])
    else:
        hy = head_y if head_y is not None else -bh / 2 + hh / 2
        a.part("head", "body", (0, hy, -bl / 2 + 1), (head_pitch, 0, 0), role="head").cube(
            (-hw / 2, -hh / 2, -hl + 1), (hw, hh, hl), **col["head"])
    head = a.by["head"]
    hz = head.cubes[0].origin[2]
    hy0 = head.cubes[0].origin[1]
    if snout:
        sw, sh, sl = snout
        head.cube((-sw / 2, hy0 + hh - sh, hz - sl), (sw, sh, sl), **col["snout"])
    if ear:
        ew, eh, el = ear
        for name, x, sign in (("ear_l", -1, 1), ("ear_r", 1, -1)):
            a.part(name, "head", (x * (hw / 2 - ew / 2), hy0 + 0.5, hz + hl - el - 0.5), (0, 0, -x * ear_roll), role="ear",
                   side=x).cube((-ew / 2, -eh, -el / 2), (ew, eh, el), **col.get("ear", col["head"]))
    if tail:
        tw, th, tl, tpitch = tail
        a.part("tail", "body", (0, -bh / 2 + th / 2 + 0.5, bl / 2 - 0.5), (tpitch, 0, 0), role="tail").cube(
            (-tw / 2, -th / 2, 0), (tw, th, tl), **col.get("tail", col["body"]))
    return a


def bird(sid, body, leg, neck, head, beak, wing, tail, colors, tail_pitch=20.0, neck_pitch=15.0):
    a = Animal(sid)
    col = colors
    bw, bh, bl = body
    lt, ll = leg
    bottom = 24 - ll
    top = bottom - bh
    a.part("body", None, (0, top + bh / 2, 0), role="body").cube((-bw / 2, -bh / 2, -bl / 2), (bw, bh, bl), **col["body"])
    for name, x, phase in (("leg_l", -1, 0), ("leg_r", 1, 1)):
        leg_part = a.part(name, None, (x * bw / 4, bottom - 0.5, 0.5), role="leg", phase=phase, gait=1.6)
        leg_part.cube((-lt / 2, 0, -lt / 2), (lt, ll + 0.5 if False else ll, lt), **col["leg"])
        leg_part.cube((-1, ll - 1 + 0.5, -2), (2, 1, 3), **col["leg"])
    nw, nh, nl = neck
    a.part("neck", "body", (0, -bh / 2 + 1, -bl / 2 + nl / 2 + 0.5), (neck_pitch, 0, 0), role="neck").cube(
        (-nw / 2, -nh, -nl / 2), (nw, nh + 1, nl), **col.get("neck", col["body"]))
    hw, hh, hl = head
    a.part("head", "neck", (0, -nh + 1, 0), (-neck_pitch, 0, 0), role="head").cube((-hw / 2, -hh + 1, -hl + nl / 2), (hw, hh, hl), **col["head"])
    head = a.by["head"]
    hz = head.cubes[0].origin[2]
    bwk, bhk, blk = beak
    head.cube((-bwk / 2, -hh + 1 + hh - bhk - 0.5, hz - blk), (bwk, bhk, blk), **col["beak"])
    wt, wh, wl = wing
    for name, x in (("wing_l", -1), ("wing_r", 1)):
        a.part(name, "body", (x * bw / 2, -bh / 2 + 0.5, -bl / 2 + 1), role="wing", side=x).cube(
            ((-wt if x < 0 else 0), 0, 0), (wt, wh, wl), **col["wing"])
    tw, th, tl = tail
    a.part("tail", "body", (0, -bh / 2 + 1, bl / 2 - 0.5), (tail_pitch, 0, 0), role="tail").cube((-tw / 2, -th / 2, 0), (tw, th, tl),
                                                                                                    **col["tail"])
    return a


# ------------------------------------------------------------------ las 20 especies

def C(color, **kw):
    d = {"color": color}
    d.update(kw)
    return d


def antlers(a, style, color):
    """Cornamentas: ciervo (ramas), corzo (pitones), alce (palas), reno (altas, con pala delante)."""
    head = a.by["head"]
    hc = head.cubes[0]
    hy0, hz, hl = hc.origin[1], hc.origin[2], hc.size[2]
    top = hy0
    zc = hz + hl - 2.5
    for side in (-1, 1):
        name = "antler_l" if side < 0 else "antler_r"
        p = a.part(name, "head", (side * 1.5, top + 0.5, zc), (0, 0, side * 22), role="antler")
        paint = dict(color=color, high=(shade(color, 1.15), 1))
        if style == "deer":
            p.cube((-0.5, -8, -0.5), (1, 8, 1), **paint)
            p.cube((-0.5, -4, -3.5), (1, 1, 3), **paint)
            p.cube((-0.5, -7, -2.5), (1, 1, 2), **paint)
            p.cube((side * 0.5 - 0.5, -9, 0.5), (1, 2, 1), **paint)
            p.cube((-0.5, -10, -1.0), (1, 2, 1), **paint)
        elif style == "roe":
            p.cube((-0.5, -4, -0.5), (1, 4, 1), **paint)
            p.cube((-0.5, -3, -1.5), (1, 1, 1), **paint)
        elif style == "moose":
            p.cube((-0.5, -2, -0.5), (1, 2, 1), **paint)
            p.cube((side * 0.5 - 0.5 + (side * 3 if side > 0 else -3), -5, -3), (1, 4, 6), **paint)
            p.cube(((0 if side > 0 else -1) + side * 0.5, -3, -1), (side * 3 if side > 0 else 3, 1, 3), **paint) if False else \
                p.cube((min(0, side * 3), -3, -1), (3, 1, 3), **paint)
            for k in range(3):
                p.cube((side * 3.5 - 0.5 + (side * 0.5), -7, -3 + k * 2.5), (1, 2, 1), **paint)
        elif style == "reindeer":
            p.cube((-0.5, -9, -0.5), (1, 9, 1), **paint)
            p.cube((-0.5, -10, -2.5), (1, 1, 3), **paint)
            p.cube((-0.5, -3, -3.5), (1, 3, 1), **paint) if side < 0 else p.cube((-0.5, -2, -2.5), (1, 1, 2), **paint)
            p.cube((-0.5, -6, -2), (1, 1, 2), **paint)
            p.cube((side * 0.5 - 0.5, -11, 1), (1, 2, 1), **paint)


def curled_horns(a, color, big=True):
    head = a.by["head"]
    hc = head.cubes[0]
    hy0, hz, hl, hw = hc.origin[1], hc.origin[2], hc.size[2], hc.size[0]
    for side in (-1, 1):
        name = "horn_l" if side < 0 else "horn_r"
        p = a.part(name, "head", (side * (hw / 2 - 0.5), hy0 + 0.5, hz + hl - 2), (0, 0, side * 10), role="horn")
        paint = dict(color=color, bands=(shade(color, 0.8), 1))
        if big:
            p.cube((min(0, side * 2) + (0 if side < 0 else 0), -2, -1), (2, 2, 3), **paint)
            p.cube((side * 1.5 - 1 + (0.5 if side > 0 else -0.5), -2, 1), (2, 4, 2), **paint)
            p.cube((side * 1.5 - 1 + (0.5 if side > 0 else -0.5), 1, -2), (2, 2, 3), **paint)
        else:
            p.cube((-0.5, -3, -0.5), (1, 3, 1), **paint)


def species():
    S = {}

    # --- ciervos y parientes
    deer = (138, 88, 54)
    belly = (214, 196, 166)
    S["red_deer"] = a = quadruped(
        "red_deer", body=(8, 8, 16), leg=(3, 14), head=(5, 5, 6), snout=(3, 3, 3), neck=(4, 8, 4, 32), ear=(1, 3, 2), tail=(3, 2, 2, 30),
        colors={"body": C(deer, belly=(belly, 2), back_color=(226, 210, 176)), "leg": C(shade(deer, 0.85), low=(HOOF, 1)),
                "neck": C(shade(deer, 0.92), belly=(shade(deer, 0.7), 1)), "head": C(deer, eyes=("side", 1, EYE)),
                "snout": C(shade(deer, 0.8), nose=NOSE), "ear": C(deer, inner=(212, 170, 150)), "tail": C((230, 220, 200))})
    antlers(a, "deer", (196, 180, 148))

    roe = (160, 106, 64)
    S["roe_deer"] = a = quadruped(
        "roe_deer", body=(6, 6, 12), leg=(2, 11), head=(4, 4, 5), snout=(2, 2, 2), neck=(3, 6, 3, 30), ear=(1, 3, 2), tail=(2, 2, 1, 20),
        colors={"body": C(roe, belly=((206, 186, 154), 2), back_color=(238, 232, 222)), "leg": C(shade(roe, 0.9), low=(HOOF, 1)),
                "head": C(roe, eyes=("side", 1, EYE)), "snout": C((52, 40, 34), nose=(20, 16, 14), high=((236, 230, 220), 1)),
                "ear": C(roe, inner=(220, 186, 160)), "tail": C((240, 236, 228))})
    antlers(a, "roe", (204, 190, 160))

    moose = (82, 60, 44)
    S["moose"] = a = quadruped(
        "moose", body=(12, 12, 22), leg=(4, 18), head=(6, 6, 8), snout=(6, 5, 4), neck=(6, 6, 6, 50), ear=(2, 4, 2), tail=(2, 3, 1, -40),
        head_pitch=20.0,
        colors={"body": C(moose, belly=((64, 46, 34), 2)), "leg": C((150, 130, 108), low=(HOOF, 1)), "neck": C(shade(moose, 0.9)),
                "head": C(shade(moose, 0.95), eyes=("side", 2, EYE)), "snout": C((70, 52, 40), nose=(30, 22, 20)),
                "ear": C(moose, inner=(120, 96, 80))})
    a.by["body"].cube((-5, -9, -11), (10, 4, 9), color=shade(moose, 0.85))
    a.by["neck"].cube((-0.5, -2, -4.5), (1, 5, 2), color=shade(moose, 0.8))
    antlers(a, "moose", (206, 190, 152))

    rein = (152, 136, 116)
    S["reindeer"] = a = quadruped(
        "reindeer", body=(9, 9, 16), leg=(3, 12), head=(5, 5, 6), snout=(4, 3, 3), neck=(5, 7, 5, 35), ear=(1, 3, 2), tail=(3, 2, 2, 30),
        colors={"body": C(rein, belly=((222, 216, 204), 2), top=(120, 106, 90)), "leg": C((110, 96, 82), low=(HOOF, 1)),
                "neck": C((228, 224, 214)), "head": C(rein, eyes=("side", 1, EYE)), "snout": C((90, 78, 66), nose=NOSE),
                "ear": C(rein), "tail": C((232, 228, 220))})
    a.by["neck"].cube((-2.5, -2, -3.5), (5, 4, 2), color=(236, 232, 222))
    antlers(a, "reindeer", (198, 184, 156))

    gaz = (204, 162, 110)
    S["gazelle"] = a = quadruped(
        "gazelle", body=(6, 7, 12), leg=(2, 13), head=(4, 4, 5), snout=(2, 2, 2), neck=(3, 7, 3, 25), ear=(1, 3, 2), tail=(1, 1, 3, -50),
        colors={"body": C(gaz, belly=((244, 238, 226), 2), side_stripe=((96, 62, 40), 4)), "leg": C(gaz, low=((60, 44, 34), 1)),
                "head": C(gaz, eyes=("side", 1, EYE), front_color=(240, 232, 216)), "snout": C((230, 220, 200), nose=NOSE),
                "ear": C(gaz, inner=(230, 210, 190)), "tail": C((60, 44, 34))})
    head = a.by["head"]
    for side in (-1, 1):
        p = a.part("horn_l" if side < 0 else "horn_r", "head", (side * 1, -2.5, -1.5), (-30, 0, side * 8), role="horn")
        p.cube((-0.5, -5, -0.5), (1, 5, 1), color=(40, 34, 30), bands=((70, 60, 52), 1))
        p.cube((-0.5, -6, 0.5), (1, 2, 1), color=(40, 34, 30))

    mouf = (142, 90, 56)
    S["mouflon"] = a = quadruped(
        "mouflon", body=(8, 8, 14), leg=(3, 11), head=(4, 5, 6), snout=(3, 3, 2), neck=(4, 5, 4, 30), ear=(2, 1, 1), tail=(2, 2, 2, -60),
        ear_roll=70.0,
        colors={"body": C(mouf, belly=((236, 230, 220), 2), spots=((214, 204, 184), 0.04)), "leg": C((236, 230, 220), low=(HOOF, 1)),
                "head": C(shade(mouf, 0.9), eyes=("side", 2, EYE)), "snout": C((226, 220, 208), nose=NOSE), "ear": C(mouf)})
    a.by["body"].cube((-4.5, -2, -2), (1, 3, 5), color=(220, 210, 190))
    a.by["body"].cube((3.5, -2, -2), (1, 3, 5), color=(220, 210, 190))
    curled_horns(a, (126, 108, 84))

    # --- grandes
    bison = (98, 72, 48)
    S["bison"] = a = quadruped(
        "bison", body=(14, 13, 22), leg=(4, 10), head=(8, 8, 6), snout=(6, 4, 3), ear=(2, 1, 2), tail=(1, 1, 8, -75), head_y=0.5,
        head_pitch=25.0, ear_roll=80.0,
        colors={"body": C(bison, belly=(shade(bison, 0.75), 2)), "leg": C((70, 52, 38), low=(HOOF, 1)),
                "head": C((60, 44, 30), eyes=("side", 3, EYE)), "snout": C((48, 36, 26), nose=(22, 18, 16)), "ear": C((60, 44, 30)),
                "tail": C(bison, tip=((40, 30, 22), 2))})
    a.by["body"].cube((-6.5, -11, -11.5), (13, 6, 11), color=(70, 50, 34), spots=((90, 66, 44), 0.2))
    a.by["body"].cube((-7.5, -6, -11.5), (15, 10, 8), color=(74, 54, 36), spots=((96, 70, 46), 0.2))
    a.by["head"].cube((-2, 3, -6), (4, 5, 3), color=(56, 40, 28))
    for side in (-1, 1):
        p = a.part("horn_l" if side < 0 else "horn_r", "head", (side * 4, -2, -3), (0, 0, side * 60), role="horn")
        p.cube((-0.5, -3, -0.5), (1, 3, 1), color=(36, 32, 30))

    boar = (80, 68, 60)
    S["wild_boar"] = a = quadruped(
        "wild_boar", body=(8, 8, 14), leg=(3, 6), head=(6, 6, 6), snout=(4, 3, 3), ear=(2, 2, 1), tail=(1, 1, 4, -70), head_y=0.5,
        head_pitch=12.0, ear_roll=40.0, eye_mode="side",
        colors={"body": C(boar, spots=((60, 50, 44), 0.25), belly=((106, 92, 82), 2), top=(52, 44, 38)), "leg": C((60, 50, 44), low=(HOOF, 1)),
                "head": C(shade(boar, 0.9), eyes=("side", 2, EYE), spots=((60, 50, 44), 0.2)),
                "snout": C((150, 112, 102), front_color=(176, 136, 124), nose=(70, 44, 40)), "ear": C((60, 50, 44))})
    a.by["body"].cube((-1, -6, -7), (2, 2, 12), color=(44, 36, 32))
    for x in (-2, 1.5):
        a.by["head"].cube((x, -0.5, -8.5), (1, 2, 1), color=(234, 228, 212))

    bear = (104, 72, 46)
    S["brown_bear"] = a = quadruped(
        "brown_bear", body=(12, 11, 20), leg=(5, 10), head=(8, 7, 7), snout=(4, 3, 3), ear=(2, 2, 1), tail=(2, 2, 2, -30), head_y=-1.5,
        head_pitch=8.0, ear_roll=10.0, eye_mode="front", gait=0.8,
        colors={"body": C(bear, belly=(shade(bear, 0.8), 2), spots=((92, 62, 40), 0.15)), "leg": C(shade(bear, 0.85), low=((40, 32, 26), 1)),
                "head": C(bear, eyes=("front", 3, EYE)), "snout": C((156, 122, 88), nose=(28, 22, 20)), "ear": C(shade(bear, 0.8))})
    a.by["body"].cube((-5, -8.5, -9), (10, 3, 7), color=shade(bear, 0.95))

    # --- cazadores
    coy = (172, 144, 108)
    S["coyote"] = a = quadruped(
        "coyote", body=(5, 6, 12), leg=(2, 9), head=(5, 5, 4), snout=(3, 2, 4), ear=(2, 4, 1), tail=(3, 3, 9, -40), head_y=-2.5,
        ear_roll=8.0, eye_mode="front",
        colors={"body": C(coy, top=(126, 108, 88), belly=((226, 212, 188), 2)), "leg": C((190, 160, 120), low=((150, 126, 96), 1)),
                "head": C(coy, eyes=("front", 1, (200, 160, 40)), front_color=(196, 168, 128)), "snout": C((222, 206, 180), nose=(30, 26, 24)),
                "ear": C(coy, inner=(230, 200, 180)), "tail": C(coy, tip=((40, 34, 30), 2), top=(120, 100, 82))})

    lynx = (188, 160, 126)
    S["lynx"] = a = quadruped(
        "lynx", body=(6, 7, 12), leg=(3, 9), head=(6, 5, 5), snout=(3, 2, 1), ear=(1, 3, 1), tail=(2, 2, 3, -20), head_y=-2.5,
        ear_roll=8.0, eye_mode="front",
        colors={"body": C(lynx, spots=((110, 86, 64), 0.12), belly=((230, 220, 204), 2)), "leg": C(lynx, spots=((110, 86, 64), 0.12)),
                "head": C(lynx, eyes=("front", 2, (180, 160, 60)), spots=((110, 86, 64), 0.06)), "snout": C((236, 226, 210), nose=(150, 90, 80)),
                "ear": C(lynx, high=((30, 26, 22), 1)), "tail": C(lynx, tip=((30, 26, 22), 1))})
    a.by["head"].cube((-4, 0, -3), (8, 3, 2), color=(232, 222, 206))
    for side, name in ((-1, "ear_l"), (1, "ear_r")):
        a.by[name].cube((-0.5, -5, -0.5), (1, 2, 1), color=(24, 20, 18))

    cou = (194, 148, 98)
    S["cougar"] = a = quadruped(
        "cougar", body=(6, 7, 16), leg=(3, 10), head=(5, 5, 5), snout=(3, 2, 2), ear=(1, 2, 1), tail=(2, 2, 16, -55), head_y=-2.0,
        ear_roll=20.0, eye_mode="front",
        colors={"body": C(cou, belly=((236, 222, 198), 2)), "leg": C(cou, low=((168, 128, 86), 1)),
                "head": C(cou, eyes=("front", 2, (170, 150, 60))), "snout": C((240, 230, 214), nose=(150, 96, 86), mouth=(80, 60, 50)),
                "ear": C(shade(cou, 0.8), inner=(60, 50, 44)), "tail": C(cou, tip=((70, 50, 36), 3))})

    # --- pequeños
    hare = (150, 124, 90)
    S["hare"] = a = quadruped(
        "hare", body=(5, 5, 8), leg=(2, 5), front_leg=(1, 5), head=(4, 4, 4), snout=(2, 2, 1), ear=(1, 6, 2), tail=(2, 2, 2, 40),
        head_y=-2.0, ear_roll=12.0, gait=1.4,
        colors={"body": C(hare, belly=((238, 232, 222), 2), spots=((130, 104, 74), 0.1)), "leg": C(hare, low=((220, 212, 200), 1)),
                "head": C(hare, eyes=("side", 1, EYE)), "snout": C((220, 210, 196), nose=(160, 110, 100)),
                "ear": C(hare, high=((30, 26, 22), 1), inner=(210, 170, 150)), "tail": C((242, 240, 236))})

    sq = (182, 90, 40)
    S["squirrel"] = a = quadruped(
        "squirrel", body=(3, 3, 6), leg=(1, 3), head=(3, 3, 3), snout=(1, 1, 1), ear=(1, 2, 1), tail=(3, 3, 7, 70), head_y=-1.5,
        ear_roll=5.0, leg_inset=0.0, gait=1.5,
        colors={"body": C(sq, belly=((240, 226, 206), 1)), "leg": C(shade(sq, 0.85)), "head": C(sq, eyes=("side", 1, EYE)),
                "snout": C((230, 210, 190), nose=(40, 30, 26)), "ear": C(sq), "tail": C(shade(sq, 1.05), spots=((206, 120, 64), 0.2))})

    rac = (122, 120, 114)
    S["raccoon"] = a = quadruped(
        "raccoon", body=(6, 6, 10), leg=(2, 5), head=(5, 5, 4), snout=(2, 2, 2), ear=(2, 2, 1), tail=(3, 3, 9, -25), head_y=-1.5,
        ear_roll=15.0, eye_mode="front", leg_inset=0.5,
        colors={"body": C(rac, top=(100, 98, 94), belly=((150, 148, 140), 1)), "leg": C((50, 48, 46)),
                "head": C((200, 196, 188), mask=((30, 28, 28), 1, 2), eyes=("front", 2, (10, 10, 10)), eye_shine=(80, 80, 80)),
                "snout": C((214, 210, 202), nose=(20, 18, 18)), "ear": C((200, 196, 188), high=((40, 38, 36), 1)),
                "tail": C((150, 146, 138), rings=((40, 38, 36), 2), tip=((40, 38, 36), 1))})

    bad = (132, 130, 124)
    S["badger"] = a = quadruped(
        "badger", body=(8, 5, 12), leg=(2, 3), head=(5, 4, 4), snout=(3, 2, 3), ear=(1, 1, 1), tail=(2, 2, 2, -20), head_y=0.0,
        ear_roll=0.0, eye_mode="front", leg_inset=0.5,
        colors={"body": C(bad, spots=((110, 108, 104), 0.2), belly=((40, 38, 36), 2)), "leg": C((34, 32, 30)),
                "head": C((236, 234, 228), face_stripes=((26, 24, 22), (236, 234, 228)), eyes=("front", 2, (10, 10, 10))),
                "snout": C((230, 228, 222), nose=(20, 18, 18), top=(26, 24, 22)), "ear": C((236, 234, 228))})

    bea = (110, 72, 42)
    S["beaver"] = a = quadruped(
        "beaver", body=(7, 6, 11), leg=(2, 3), head=(5, 5, 4), snout=(3, 2, 2), ear=(1, 1, 1), tail=(5, 1, 8, -5), head_y=-0.5,
        ear_roll=0.0, eye_mode="side", leg_inset=0.5,
        colors={"body": C(bea, belly=(shade(bea, 0.85), 1)), "leg": C((60, 40, 28)), "head": C(bea, eyes=("side", 1, EYE)),
                "snout": C((96, 64, 40), nose=(24, 18, 16)), "ear": C(bea), "tail": C((62, 58, 56), spots=((48, 44, 42), 0.3))})
    a.by["head"].cube((-1, 2.5, -6), (2, 2, 1), color=(222, 144, 44))

    # --- aves
    S["duck"] = bird(
        "duck", body=(5, 5, 8), leg=(1, 2), neck=(2, 3, 2), head=(3, 3, 4), beak=(3, 1, 3), wing=(1, 4, 6), tail=(3, 1, 2),
        colors={"body": C((160, 142, 116), belly=((190, 180, 168), 2), spots=((130, 112, 90), 0.15)), "leg": C((228, 134, 40)),
                "neck": C((44, 112, 62), low=((240, 240, 236), 1)), "head": C((44, 112, 62), eyes=("side", 1, EYE)), "beak": C((226, 198, 62)),
                "wing": C((140, 122, 100), low=((60, 92, 186), 1)), "tail": C((60, 56, 52))}, tail_pitch=30.0, neck_pitch=10.0)

    S["pheasant"] = bird(
        "pheasant", body=(4, 4, 8), leg=(1, 4), neck=(2, 4, 2), head=(3, 3, 3), beak=(1, 1, 2), wing=(1, 3, 6), tail=(2, 1, 12),
        colors={"body": C((178, 98, 44), spots=((70, 40, 24), 0.2)), "leg": C((120, 110, 100)), "neck": C((40, 84, 92), low=((240, 240, 236), 1)),
                "head": C((40, 84, 92), eyes=("side", 1, (200, 30, 30)), front_color=(196, 40, 36)), "beak": C((214, 204, 160)),
                "wing": C((150, 120, 80), spots=((90, 70, 44), 0.25)), "tail": C((168, 132, 86), rings=((70, 50, 30), 1))},
        tail_pitch=12.0, neck_pitch=20.0)

    tk = bird(
        "turkey", body=(7, 8, 10), leg=(1, 6), neck=(2, 6, 2), head=(2, 3, 3), beak=(1, 1, 1), wing=(1, 6, 8), tail=(12, 10, 1),
        colors={"body": C((70, 58, 48), spots=((110, 96, 70), 0.18)), "leg": C((196, 144, 130)), "neck": C((160, 176, 196)),
                "head": C((150, 170, 190), eyes=("side", 1, EYE)), "beak": C((214, 200, 160)),
                "wing": C((90, 76, 62), bands=((220, 214, 200), 2)), "tail": C((110, 84, 56), bands=((60, 44, 30), 2), low=((226, 220, 206), 1))},
        tail_pitch=-15.0, neck_pitch=15.0)
    tk.by["head"].cube((-0.5, 0.5, -4.5), (1, 3, 1), color=(200, 40, 40))
    S["turkey"] = tk
    return S


# ------------------------------------------------------------------ objetos

ITEMS = {
    # id: (es, en, food (nutrición, saturación) o None, pintura)
    "venison": ("Venado crudo", "Raw Venison", (3, 0.3)),
    "cooked_venison": ("Venado asado", "Cooked Venison", (8, 0.8)),
    "game_meat": ("Carne de caza cruda", "Raw Game Meat", (3, 0.3)),
    "cooked_game_meat": ("Carne de caza asada", "Cooked Game Meat", (8, 0.8)),
    "fowl": ("Ave cruda", "Raw Fowl", (2, 0.3)),
    "cooked_fowl": ("Ave asada", "Cooked Fowl", (6, 0.6)),
    "fur_pelt": ("Piel con pelo", "Fur Pelt", None),
    "antler": ("Asta", "Antler", None),
    "horn": ("Cuerno", "Horn", None),
    "tusk": ("Colmillo", "Tusk", None),
    "bear_claw": ("Garra de oso", "Bear Claw", None),
}


def icon(name):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    r = random.Random(name)
    if name in ("venison", "game_meat", "fowl", "cooked_venison", "cooked_game_meat", "cooked_fowl"):
        cooked = name.startswith("cooked")
        meat = {"venison": (168, 52, 56), "game_meat": (150, 46, 40), "fowl": (226, 160, 140)}[name.replace("cooked_", "")]
        if cooked:
            meat = {"venison": (124, 70, 40), "game_meat": (110, 60, 34), "fowl": (206, 150, 80)}[name.replace("cooked_", "")]
        fat = (240, 224, 214) if not cooked else (214, 178, 120)
        if "fowl" in name:
            # un muslo
            d.ellipse([3, 3, 11, 11], fill=meat + (255,))
            d.line([(10, 10), (13, 13)], fill=(236, 230, 214, 255), width=2)
            d.point([(14, 14), (13, 14), (14, 13)], fill=(236, 230, 214, 255))
        else:
            d.polygon([(2, 6), (6, 3), (12, 3), (14, 7), (12, 12), (5, 13), (2, 10)], fill=meat + (255,))
            d.line([(4, 8), (11, 6)], fill=fat + (255,))
            d.line([(5, 10), (11, 9)], fill=fat + (255,))
        for _ in range(10):
            x, y = r.randint(3, 12), r.randint(4, 11)
            if im.getpixel((x, y))[3]:
                c = im.getpixel((x, y))
                im.putpixel((x, y), tuple(clamp(v * r.uniform(0.82, 1.12)) for v in c[:3]) + (255,))
        # contorno
        out = im.copy()
        for y in range(16):
            for x in range(16):
                if im.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and im.getpixel((x + dx, y + dy))[3]
                                                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out.putpixel((x, y), (shade(meat, 0.45)) + (255,))
        return out
    if name == "fur_pelt":
        fur = (128, 92, 60)
        d.polygon([(3, 2), (12, 2), (14, 5), (13, 13), (9, 14), (6, 14), (2, 13), (1, 5)], fill=fur + (255,))
        for _ in range(40):
            x, y = r.randint(2, 13), r.randint(2, 13)
            if im.getpixel((x, y))[3]:
                im.putpixel((x, y), shade(fur, r.uniform(0.7, 1.25)) + (255,))
        for (x, y) in ((1, 3), (14, 3), (1, 14), (14, 14)):
            im.putpixel((x, y), shade(fur, 0.6) + (255,))
        return im
    if name in ("antler", "horn", "tusk", "bear_claw"):
        col = {"antler": (204, 188, 154), "horn": (120, 102, 80), "tusk": (238, 232, 216), "bear_claw": (60, 52, 46)}[name]
        dark = shade(col, 0.7)
        if name == "antler":
            pts = [(3, 13), (5, 10), (7, 7), (8, 4), (9, 2)]
            for a_, b_ in zip(pts, pts[1:]):
                d.line([a_, b_], fill=col + (255,), width=2)
            d.line([(6, 9), (10, 8), (12, 6)], fill=col + (255,), width=1)
            d.line([(7, 6), (11, 4)], fill=col + (255,), width=1)
            d.line([(4, 12), (2, 11)], fill=dark + (255,), width=1)
        elif name == "horn":
            for k in range(8):
                a_ = k / 7 * 3.4
                x, y = 8 + 5 * math.cos(a_), 8 + 5 * math.sin(a_) - k * 0.3
                d.ellipse([x - 2 + k * 0.1, y - 2 + k * 0.1, x + 2 - k * 0.15, y + 2 - k * 0.15], fill=(col if k % 2 else dark) + (255,))
        elif name == "tusk":
            d.arc([2, 3, 14, 17], 200, 330, fill=col + (255,), width=3)
            d.point([(12, 5), (13, 6)], fill=dark + (255,))
        else:
            for k, x in enumerate((4, 8, 12)):
                d.line([(x, 4), (x - 1, 9), (x - 3, 12)], fill=col + (255,), width=2)
            d.line([(3, 4), (13, 4)], fill=(90, 64, 44, 255), width=2)
        return im
    raise KeyError(name)


# ------------------------------------------------------------------ botín y lo demás

def item_entry(name, lo, hi, smelt=None, chance=None, looting=True):
    e = {"type": "minecraft:item", "name": name, "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False}]}
    if smelt:
        e["functions"].append({"function": "minecraft:furnace_smelt", "conditions": [
            {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"flags": {"is_on_fire": True}}}]})
    if looting:
        e["functions"].append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                               "count": {"type": "minecraft:uniform", "min": 0, "max": 1}})
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [e]}
    if chance:
        pool["conditions"] = [{"condition": "minecraft:killed_by_player"},
                              {"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                               "unenchanted_chance": chance, "enchanted_chance": {"type": "minecraft:linear", "base": chance + 0.1,
                                                                                  "per_level_above_first": 0.1}}]
    return pool


V, GM, FW = NS + ":venison", NS + ":game_meat", NS + ":fowl"
FUR, ANT, HORN, TUSK, CLAW = NS + ":fur_pelt", NS + ":antler", NS + ":horn", NS + ":tusk", NS + ":bear_claw"
LOOT = {
    "red_deer": [(V, 1, 3, True), ("minecraft:leather", 0, 2, False), (ANT, 1, 1, False, 0.5)],
    "roe_deer": [(V, 1, 2, True), ("minecraft:leather", 0, 1, False), (ANT, 1, 1, False, 0.25)],
    "moose": [(V, 3, 5, True), ("minecraft:leather", 1, 3, False), (ANT, 1, 2, False, 0.6)],
    "reindeer": [(V, 1, 3, True), (FUR, 0, 1, False), (ANT, 1, 1, False, 0.5)],
    "gazelle": [(V, 1, 2, True), ("minecraft:leather", 0, 1, False), (HORN, 1, 1, False, 0.3)],
    "mouflon": [("minecraft:mutton", 1, 2, True), ("minecraft:brown_wool", 0, 1, False), (HORN, 1, 2, False, 0.4)],
    "bison": [(GM, 3, 5, True), ("minecraft:leather", 1, 3, False), (HORN, 1, 2, False, 0.4)],
    "wild_boar": [(GM, 1, 3, True), ("minecraft:leather", 0, 1, False), (TUSK, 1, 2, False, 0.5)],
    "brown_bear": [(GM, 2, 4, True), (FUR, 1, 2, False), (CLAW, 1, 2, False, 0.6)],
    "coyote": [(FUR, 0, 1, False), ("minecraft:bone", 0, 1, False)],
    "lynx": [(FUR, 1, 1, False), ("minecraft:bone", 0, 1, False)],
    "cougar": [(FUR, 1, 2, False), ("minecraft:bone", 0, 2, False)],
    "hare": [("minecraft:rabbit", 1, 1, True), ("minecraft:rabbit_hide", 0, 1, False), ("minecraft:rabbit_foot", 1, 1, False, 0.1)],
    "squirrel": [("minecraft:rabbit", 0, 1, True), ("minecraft:rabbit_hide", 0, 1, False)],
    "raccoon": [(FUR, 0, 1, False), ("minecraft:rabbit_hide", 0, 1, False)],
    "badger": [(FUR, 0, 1, False), ("minecraft:rabbit_hide", 0, 1, False)],
    "beaver": [(FUR, 1, 1, False), ("minecraft:stick", 0, 2, False)],
    "duck": [(FW, 1, 1, True), ("minecraft:feather", 0, 2, False)],
    "pheasant": [(FW, 1, 1, True), ("minecraft:feather", 1, 3, False)],
    "turkey": [(FW, 1, 3, True), ("minecraft:feather", 1, 3, False)],
}

NAMES = {
    "red_deer": ("Ciervo", "Red Deer"), "roe_deer": ("Corzo", "Roe Deer"), "moose": ("Alce", "Moose"), "reindeer": ("Reno", "Reindeer"),
    "gazelle": ("Gacela", "Gazelle"), "mouflon": ("Muflón", "Mouflon"), "bison": ("Bisonte", "Bison"), "wild_boar": ("Jabalí", "Wild Boar"),
    "brown_bear": ("Oso pardo", "Brown Bear"), "coyote": ("Coyote", "Coyote"), "lynx": ("Lince", "Lynx"), "cougar": ("Puma", "Cougar"),
    "hare": ("Liebre", "Hare"), "squirrel": ("Ardilla", "Squirrel"), "raccoon": ("Mapache", "Raccoon"), "badger": ("Tejón", "Badger"),
    "beaver": ("Castor", "Beaver"), "duck": ("Pato", "Duck"), "pheasant": ("Faisán", "Pheasant"), "turkey": ("Pavo salvaje", "Wild Turkey"),
}

ITEM_DESC = {
    "fur_pelt": ("Abriga: sirve para la ropa de piel (en lugar de la lana) y da cuero. El peletero la compra bien.",
                 "Warm: use it for fur clothing (instead of wool) or turn it into leather. The furrier pays well for it."),
    "antler": ("Trofeo de ciervos, alces y renos. Molido da harina de hueso; el peletero lo compra.",
               "Trophy from deer, moose and reindeer. Ground into bone meal; the furrier buys it."),
    "horn": ("De bisontes, muflones y gacelas. Molido da harina de hueso; el peletero lo compra.",
             "From bison, mouflon and gazelles. Ground into bone meal; the furrier buys it."),
    "tusk": ("Del jabalí. Molido da harina de hueso; el peletero lo compra.", "From wild boars. Ground into bone meal; the furrier buys it."),
    "bear_claw": ("Trofeo del oso pardo. El peletero paga mucho por ella.", "Brown bear trophy. The furrier pays a lot for it."),
}


def main():
    S = species()
    previews = []
    for sid, a in S.items():
        tw, th = a.pack()
        img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        rng = random.Random(sid)
        for p in a.parts:
            for c in p.cubes:
                paint_cube(img, c, rng)
        path = os.path.join(ASSETS, "textures/entity/wildlife/%s.png" % sid)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        parts = []
        for p in a.parts:
            parts.append({"name": p.name, "parent": p.parent, "pivot": p.pivot, "rotation": p.rot, "role": p.role,
                          "cubes": [{"uv": list(c.uv), "origin": c.origin, "size": c.size} for c in p.cubes]})
        write_json(os.path.join(ASSETS, "wildlife/%s.json" % sid), {"texture": [tw, th], "parts": parts}, indent=None)
        previews.append((sid, parts, img))
        # botín
        pools = []
        for entry in LOOT[sid]:
            name, lo, hi, smelt = entry[:4]
            chance = entry[4] if len(entry) > 4 else None
            pools.append(item_entry(name, lo, hi, smelt=smelt, chance=chance))
        write_json(os.path.join(DATA, "loot_table/entities/%s.json" % sid),
                   {"type": "minecraft:entity", "pools": pools, "random_sequence": "%s:entities/%s" % (NS, sid)})
        write_json(os.path.join(ASSETS, "models/item/%s_spawn_egg.json" % sid), {"parent": "minecraft:item/template_spawn_egg"})
    # objetos
    for name in ITEMS:
        icon(name).save(os.path.join(ASSETS, "textures/item/%s.png" % name))
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    rec = os.path.join(DATA, "recipe")
    for raw in ("venison", "game_meat", "fowl"):
        for kind, time in (("smelting", 200), ("smoking", 100), ("campfire_cooking", 600)):
            write_json(os.path.join(rec, "cooked_%s_from_%s.json" % (raw, kind)),
                       {"type": "minecraft:" + kind, "category": "food", "ingredient": {"item": "%s:%s" % (NS, raw)},
                        "result": {"id": "%s:cooked_%s" % (NS, raw), "count": 1}, "experience": 0.35, "cookingtime": time})
    for name, n in (("antler", 4), ("horn", 3), ("tusk", 3)):
        write_json(os.path.join(rec, "bone_meal_from_%s.json" % name),
                   {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [{"item": "%s:%s" % (NS, name)}],
                    "result": {"id": "minecraft:bone_meal", "count": n}})
    write_json(os.path.join(rec, "leather_from_fur_pelt.json"),
               {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [{"item": FUR}],
                "result": {"id": "minecraft:leather", "count": 1}})
    # la ropa de piel también con pieles (en lugar de lana)
    for path in glob.glob(os.path.join(rec, "fur_*.json")):
        if path.endswith("_from_pelts.json"):
            continue
        with open(path, encoding="utf-8") as f:
            r = json.load(f)
        if r.get("type") != "minecraft:crafting_shaped":
            continue
        changed = False
        for k, v in r["key"].items():
            if v == {"tag": "minecraft:wool"}:
                r["key"][k] = {"item": FUR}
                changed = True
        if changed:
            write_json(path.replace(".json", "_from_pelts.json"), r)
    # textos
    texts = {}
    for sid, (es, en) in NAMES.items():
        texts["entity.%s.%s" % (NS, sid)] = (es, en)
        texts["item.%s.%s_spawn_egg" % (NS, sid)] = ("Huevo de aparición de " + es.lower() if sid not in ("brown_bear", "turkey") else
                                                      "Huevo de aparición de " + es.lower(), en + " Spawn Egg")
    for name, (es, en, food) in ITEMS.items():
        texts["item.%s.%s" % (NS, name)] = (es, en)
    for name, (es, en) in ITEM_DESC.items():
        texts["item.%s.%s.desc" % (NS, name)] = (es, en)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in texts.items():
            lang[key] = en if english else es
        write_json(path, lang, indent=2)
    # la caja de víveres también lleva las carnes
    tag_path = os.path.join(DATA, "tags/item/crate_crops.json")
    with open(tag_path, encoding="utf-8") as f:
        tag = json.load(f)
    for name in ("venison", "cooked_venison", "game_meat", "cooked_game_meat", "fowl", "cooked_fowl"):
        if NS + ":" + name not in tag["values"]:
            tag["values"].append(NS + ":" + name)
    write_json(tag_path, tag, indent=2)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        cell = 220
        cols = 5
        sheet = Image.new("RGB", (cell * cols, cell * 4), (60, 64, 70))
        d = ImageDraw.Draw(sheet)
        for k, (sid, parts, img) in enumerate(previews):
            ys = [c["origin"][1] for p in parts for c in p["cubes"]]
            big = sid in ("moose", "bison", "brown_bear")
            small = sid in ("squirrel", "hare", "duck", "pheasant", "raccoon", "badger", "beaver")
            im = render_entity(parts, img, size=cell, yaw=215.0, pitch=15.0, center="auto")
            sheet.paste(im, ((k % cols) * cell, (k // cols) * cell))
            d.text(((k % cols) * cell + 6, (k // cols) * cell + 4), NAMES[sid][0], fill=(240, 240, 240))
        sheet.save(os.path.join(PREVIEW, "wildlife.png"))
        icons = Image.new("RGBA", (18 * len(ITEMS), 18), (139, 139, 139, 255))
        for k, name in enumerate(ITEMS):
            icons.alpha_composite(icon(name), (18 * k + 1, 1))
        icons.resize((icons.width * 6, icons.height * 6), Image.NEAREST).save(os.path.join(PREVIEW, "wildlife_items.png"))
        texs = Image.new("RGBA", (128 * 5, 128 * 4), (40, 40, 40, 255))
        for k, (sid, parts, img) in enumerate(previews):
            texs.alpha_composite(img.resize((min(128, img.width), min(128, img.height))), ((k % 5) * 128, (k // 5) * 128))
        texs.save(os.path.join(PREVIEW, "wildlife_textures.png"))
    print("animales: %d especies, %d objetos" % (len(S), len(ITEMS)))


if __name__ == "__main__":
    main()
