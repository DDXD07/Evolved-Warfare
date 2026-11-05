"""Los modelos 3D (de cubos) de la herrería y de la pesca: el martillo de herrero, los lingotes al rojo, las 12 piezas
forjadas, los peces (los 8 del mod y los 4 de vanilla), los cebos y la caña de pescar (también lanzada).

Cada modelo se diseña "de pie" (las herramientas con el mango a lo largo de y y la cabeza arriba; los peces a lo largo de
x, con la cabeza a la derecha) y luego se inclina 45° en la geometría, como los dibujos planos del juego: así valen las
posiciones en la mano de vanilla (item/generated, item/handheld, item/handheld_rod) sin tocarlas. Los giros de los
detalles (las aletas, la punta doblada de la caña) se suman a esa inclinación en pasos de 22,5°.

Cada modelo lleva su textura de materiales (`textures/item/3d/<id>.png`, una paleta de ew_model3d) y, para las partículas
(al comer, al romperse), el dibujo plano de siempre.

Uso: python3 tools/ew_gear3d.py [raíz del proyecto] [--preview carpeta]
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from ew_model3d import FACES, Model, Palette, render  # noqa: E402

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


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write("\n")


# ------------------------------------------------------------------ cómo se ponen en la mano (los de vanilla)

def T(rot=(0, 0, 0), tr=(0, 0, 0), sc=1.0):
    return {"rotation": list(rot), "translation": list(tr), "scale": [sc, sc, sc]}


GENERATED = {
    "ground": T((0, 0, 0), (0, 2, 0), 0.5),
    "head": T((0, 180, 0), (0, 13, 7), 1.0),
    "thirdperson_righthand": T((0, 0, 0), (0, 3, 1), 0.55),
    "firstperson_righthand": T((0, -90, 25), (1.13, 3.2, 1.13), 0.68),
    "fixed": T((0, 180, 0), (0, 0, 0), 1.0),
}
HANDHELD = {
    "ground": T((0, 0, 0), (0, 2, 0), 0.5),
    "head": T((0, 180, 0), (0, 13, 7), 1.0),
    "thirdperson_righthand": T((0, -90, 55), (0, 4.0, 0.5), 0.85),
    "thirdperson_lefthand": T((0, 90, -55), (0, 4.0, 0.5), 0.85),
    "firstperson_righthand": T((0, -90, 25), (1.13, 3.2, 1.13), 0.68),
    "firstperson_lefthand": T((0, 90, -25), (1.13, 3.2, 1.13), 0.68),
    "fixed": T((0, 180, 0), (0, 0, 0), 1.0),
}
ROD = dict(HANDHELD)
ROD.update({
    "thirdperson_righthand": T((0, 90, 55), (0, 4.0, 2.5), 0.85),
    "thirdperson_lefthand": T((0, -90, -55), (0, 4.0, 2.5), 0.85),
    "firstperson_righthand": T((0, 90, 25), (0, 1.6, 0.8), 0.68),
    "firstperson_lefthand": T((0, -90, -25), (0, 1.6, 0.8), 0.68),
})
# las cosas sueltas (lingotes, cebos): como un bloque pequeño
OBJECT = {
    "gui": T((30, 225, 0), (0, 0, 0), 0.9),
    "ground": T((0, 0, 0), (0, 2, 0), 0.45),
    "fixed": T((0, 180, 0), (0, 0, 0), 0.8),
    "head": T((0, 180, 0), (0, 13, 7), 0.8),
    "thirdperson_righthand": T((75, 45, 0), (0, 2.5, 0), 0.45),
    "firstperson_righthand": T((0, 45, 0), (0, 2, 0), 0.55),
    "firstperson_lefthand": T((0, 225, 0), (0, 2, 0), 0.55),
}
# un poco de giro en el inventario para que se vea el volumen (los planos van de frente)
GUI_TILT = T((-14, 22, 0), (0, 0, 0), 1.0)


# ------------------------------------------------------------------ el constructor

def _rz(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1.0]])


class Gear:
    """Cubos en el marco "de pie" del modelo; `build` los centra, los escala para que quepan y los inclina."""

    def __init__(self, name, palette, tilt):
        self.name = name
        self.palette = palette
        self.tilt = tilt
        self.boxes = []
        # si se da, el marco para centrar y encajar (así la caña lanzada queda donde la otra)
        self.frame = None

    def box(self, x0, y0, z0, x1, y1, z1, mat, rot=0.0, pivot=None, tint=False, shade=True, faces=FACES):
        x0, x1 = sorted((x0, x1))
        y0, y1 = sorted((y0, y1))
        z0, z1 = sorted((z0, z1))
        if pivot is None:
            pivot = ((x0 + x1) / 2, (y0 + y1) / 2)
        self.boxes.append(dict(b=(x0, y0, z0, x1, y1, z1), mat=mat, rot=rot, pivot=pivot, tint=tint, shade=shade, faces=faces))

    def c(self, cx, cy, cz, sx, sy, sz, mat, **kw):
        """Un cubo por su centro y su tamaño."""
        self.box(cx - sx / 2, cy - sy / 2, cz - sz / 2, cx + sx / 2, cy + sy / 2, cz + sz / 2, mat, **kw)

    def _corners(self, bx):
        x0, y0, z0, x1, y1, z1 = bx["b"]
        px, py = bx["pivot"]
        r = _rz(bx["rot"])
        out = []
        for x in (x0, x1):
            for y in (y0, y1):
                for z in (z0, z1):
                    q = r @ np.array([x - px, y - py, z]) + np.array([px, py, 0.0])
                    out.append(q)
        return out

    def build(self, fit=15.0, seed=1):
        pts = [p for bx in self.boxes for p in self._corners(bx)]
        if self.frame:
            (ax, ay, az), (bx_, by, bz) = self.frame
            pts = [np.array([x, y, z]) for x in (ax, bx_) for y in (ay, by) for z in (az, bz)]
        lo = np.min(pts, axis=0)
        hi = np.max(pts, axis=0)
        center = (lo + hi) / 2
        rt = _rz(self.tilt)
        world = [rt @ (p - center) for p in pts]
        ext = np.max(world, axis=0) - np.min(world, axis=0)
        s = fit / max(ext[0], ext[1])
        C = np.array([8.0, 8.0, 8.0])
        m = Model(self.palette, "%s:item/3d/%s" % (NS, self.name), seed)
        for bx in self.boxes:
            x0, y0, z0, x1, y1, z1 = bx["b"]
            p = np.array([bx["pivot"][0], bx["pivot"][1], center[2]])
            O = C + s * (rt @ (p - center))
            total = self.tilt + bx["rot"]
            quarter = int(round(total / 90.0))
            r = total - 90 * quarter
            if r > 45:
                r -= 90
                quarter += 1
            elif r < -45:
                r += 90
                quarter -= 1
            rq = _rz(90 * quarter)
            cs = []
            for x in (x0, x1):
                for y in (y0, y1):
                    for z in (z0, z1):
                        y_ = O + s * (np.array([x, y, z]) - p)
                        cs.append(rq @ (y_ - O) + O)
            a = np.min(cs, axis=0)
            b = np.max(cs, axis=0)
            rot = ("z", round(r, 3), tuple(O)) if abs(r) > 1e-6 else None
            if rot and abs(abs(r) - 22.5) > 1e-6 and abs(abs(r) - 45) > 1e-6:
                raise ValueError("%s: giro no válido %s" % (self.name, r))
            e = m.box(a[0], a[1], a[2], b[0], b[1], b[2], bx["mat"], rot=rot, faces=bx["faces"], shade=bx["shade"])
            if bx["tint"]:
                for f in e["faces"].values():
                    f["tintindex"] = 0
            for v in e["from"] + e["to"]:
                if v < -16 or v > 32:
                    raise ValueError("%s: fuera de los límites (%s)" % (self.name, v))
        return m


def model_json(m, display, particle, gui=None, light="front"):
    d = dict(display)
    if gui is not None:
        d["gui"] = gui
    out = m.json(display=d)
    out["textures"]["particle"] = particle
    out["gui_light"] = light
    return out


# ------------------------------------------------------------------ la herrería

def metal_palette(seed):
    p = Palette(seed)
    p.add("metal", (206, 206, 210), noise=0.05)
    p.add("light", (250, 250, 252), noise=0.03)
    p.add("dark", (140, 140, 146), noise=0.06)
    p.add("darker", (96, 96, 102), noise=0.06)
    p.add("wood", (137, 103, 55), pattern="stripes", accent=(108, 80, 42))
    p.add("leather", (104, 64, 38), pattern="stripes", accent=(78, 46, 26))
    p.add("steel", (150, 154, 162), noise=0.05)
    p.add("steel_light", (214, 218, 226), noise=0.03)
    return p


def hammer():
    g = Gear("smithing_hammer", metal_palette(11), -45)
    g.c(8, 6, 8, 1.6, 15, 1.6, "wood")
    g.c(8, 1.5, 8, 2.0, 5, 2.0, "leather")
    g.c(8, -1.3, 8, 1.8, 0.6, 1.8, "darker")
    g.c(8, 12.9, 8, 2.1, 1.0, 2.1, "darker")
    # la cabeza: una cara plana ancha a un lado y la peña, más estrecha, al otro
    g.box(4.6, 13.2, 6.0, 11.6, 17.2, 10.0, "steel")
    g.box(3.6, 12.8, 5.6, 4.6, 17.6, 10.4, "steel_light")
    g.box(11.6, 13.8, 6.8, 13.4, 16.6, 9.2, "steel")
    g.box(13.4, 14.4, 7.4, 14.4, 16.0, 8.6, "steel_light")
    g.box(7.4, 17.2, 7.4, 8.6, 17.5, 8.6, "darker")
    return g


def ingot(gold):
    p = Palette(30 + gold)
    if gold:
        p.add("core", (255, 236, 150), noise=0.04)
        p.add("hot", (255, 196, 70), noise=0.06)
        p.add("edge", (240, 140, 40), noise=0.06)
        p.add("scale", (200, 90, 30), noise=0.08)
    else:
        p.add("core", (255, 226, 120), noise=0.04)
        p.add("hot", (250, 150, 40), noise=0.06)
        p.add("edge", (214, 70, 20), noise=0.06)
        p.add("scale", (120, 34, 14), noise=0.1)
    g = Gear("hot_%s_ingot" % ("gold" if gold else "iron"), p, 0)
    # un lingote de verdad: más ancho abajo, con los cantos biselados (en escalones)
    g.box(2.0, 0.0, 4.6, 14.0, 1.4, 11.4, "edge", shade=False)
    g.box(2.6, 1.4, 5.2, 13.4, 2.6, 10.8, "hot", shade=False)
    g.box(3.4, 2.6, 6.0, 12.6, 3.6, 10.0, "core", shade=False)
    # cascarilla oscura y chispas
    rng = random.Random(5 + gold)
    for _ in range(5):
        x = rng.uniform(4.0, 11.0)
        z = rng.uniform(6.4, 9.0)
        g.box(x, 3.6, z, x + rng.uniform(0.6, 1.4), 3.7, z + rng.uniform(0.5, 1.0), "scale", shade=False)
    for _ in range(3):
        x = rng.uniform(3.0, 12.5)
        g.box(x, 1.4, 4.9, x + 0.8, 2.0, 5.2, "scale", shade=False)
    return g


def part(pid):
    """Las piezas forjadas, de pie (la cabeza arriba, la espiga o el ojo del mango abajo); se tiñen del metal."""
    g = Gear("forged_part_%s" % pid, metal_palette(100 + sum(map(ord, pid))), -45)
    t = dict(tint=True)

    def blade(y0, y1, w, fuller=True, d=0.8):
        g.box(8 - w / 2, y0, 8 - d / 2, 8 + w / 2, y1, 8 + d / 2, "metal", **t)
        g.box(8 - w / 2 - 0.15, y0 + 0.4, 8 - d / 2 + 0.15, 8 - w / 2, y1 - 0.4, 8 + d / 2 - 0.15, "light", **t)
        g.box(8 + w / 2, y0 + 0.4, 8 - d / 2 + 0.15, 8 + w / 2 + 0.15, y1 - 0.4, 8 + d / 2 - 0.15, "light", **t)
        if fuller:
            g.box(7.6, y0 + 0.4, 8 - d / 2 - 0.06, 8.4, y1 - 2.0, 8 + d / 2 + 0.06, "dark", **t)
        # la punta
        g.box(8 - w / 2 + 0.5, y1, 8 - d / 2 + 0.1, 8 + w / 2 - 0.5, y1 + 1.2, 8 + d / 2 - 0.1, "metal", **t)
        g.box(7.6, y1 + 1.2, 8 - d / 2 + 0.2, 8.4, y1 + 2.2, 8 + d / 2 - 0.2, "light", **t)

    def socket(y0, y1, w=2.2):
        g.box(8 - w / 2, y0, 8 - w / 2, 8 + w / 2, y1, 8 + w / 2, "dark", **t)
        g.box(8 - w / 2 + 0.3, y0 - 0.05, 8 - w / 2 + 0.3, 8 + w / 2 - 0.3, y0, 8 + w / 2 - 0.3, "darker", **t)

    if pid == "sword_blade":
        g.box(7.4, 0, 7.6, 8.6, 4.2, 8.4, "darker", **t)
        g.box(4.2, 4.2, 7.0, 11.8, 5.4, 9.0, "dark", **t)
        blade(5.4, 15.6, 2.8)
    elif pid == "dagger_blade":
        g.box(7.5, 0, 7.6, 8.5, 2.6, 8.4, "darker", **t)
        g.box(5.4, 2.6, 7.1, 10.6, 3.6, 8.9, "dark", **t)
        blade(3.6, 9.6, 2.2, d=0.9)
    elif pid == "longsword_blade":
        g.box(7.4, -2, 7.6, 8.6, 3.6, 8.4, "darker", **t)
        g.box(3.0, 3.6, 7.0, 13.0, 4.8, 9.0, "dark", **t)
        g.box(3.0, 4.8, 7.2, 4.0, 5.8, 8.8, "dark", **t)
        g.box(12.0, 4.8, 7.2, 13.0, 5.8, 8.8, "dark", **t)
        g.box(7.0, 4.8, 7.3, 9.0, 5.8, 8.7, "dark", **t)
        blade(5.8, 18.0, 2.6)
    elif pid == "pickaxe_head":
        socket(11.6, 15.6)
        g.box(4.0, 12.6, 7.0, 12.0, 14.8, 9.0, "metal", **t)
        for side in (-1, 1):
            xa = 8 + side * 4.0
            g.box(xa, 12.0, 7.2, xa + side * 3.0, 14.2, 8.8, "metal", **t)
            g.box(xa + side * 3.0, 10.8, 7.4, xa + side * 5.0, 13.4, 8.6, "metal", **t)
            g.box(xa + side * 5.0, 9.4, 7.6, xa + side * 6.4, 12.0, 8.4, "light", **t)
        g.box(4.0, 14.8, 7.3, 12.0, 15.1, 8.7, "light", **t)
    elif pid == "axe_head":
        socket(10.6, 16.0)
        g.box(9.1, 11.6, 7.0, 11.0, 15.0, 9.0, "dark", **t)
        g.box(3.2, 11.4, 7.2, 6.9, 15.4, 8.8, "metal", **t)
        g.box(1.6, 10.2, 7.4, 3.2, 16.4, 8.6, "metal", **t)
        g.box(0.6, 9.2, 7.6, 1.6, 17.4, 8.4, "light", **t)
    elif pid == "shovel_head":
        socket(2.0, 5.6, 2.0)
        g.box(4.0, 5.6, 7.5, 12.0, 13.6, 8.5, "metal", **t)
        g.box(7.3, 5.6, 7.2, 8.7, 12.0, 8.8, "dark", **t)
        g.box(4.8, 13.6, 7.55, 11.2, 14.8, 8.45, "metal", **t)
        g.box(6.2, 14.8, 7.6, 9.8, 15.8, 8.4, "light", **t)
    elif pid == "hoe_head":
        socket(11.6, 15.4)
        g.box(2.6, 13.0, 7.3, 6.9, 15.0, 8.7, "metal", **t)
        g.box(0.6, 9.4, 7.5, 2.8, 15.4, 8.5, "metal", **t)
        g.box(0.6, 8.4, 7.6, 2.8, 9.4, 8.4, "light", **t)
        g.box(9.1, 12.6, 7.4, 10.0, 14.4, 8.6, "dark", **t)
    elif pid == "spearhead":
        socket(0.0, 5.0, 1.8)
        g.box(6.7, 4.6, 6.7, 9.3, 5.6, 9.3, "darker", **t)
        g.box(6.6, 6.0, 7.6, 9.4, 6.6, 8.4, "metal", **t)
        g.box(5.8, 6.6, 7.6, 10.2, 10.4, 8.4, "metal", **t)
        g.box(6.4, 10.4, 7.6, 9.6, 12.0, 8.4, "metal", **t)
        g.box(7.0, 12.0, 7.65, 9.0, 13.8, 8.35, "light", **t)
        g.box(7.6, 13.8, 7.7, 8.4, 15.4, 8.3, "light", **t)
        g.box(7.6, 5.6, 7.3, 8.4, 13.0, 8.7, "dark", **t)
        g.box(5.6, 7.2, 7.7, 5.8, 10.0, 8.3, "light", **t)
        g.box(10.2, 7.2, 7.7, 10.4, 10.0, 8.3, "light", **t)
    elif pid == "halberd_head":
        socket(2.0, 12.6, 1.8)
        g.box(7.3, 12.6, 7.4, 8.7, 15.0, 8.6, "metal", **t)
        g.box(7.6, 15.0, 7.6, 8.4, 17.6, 8.4, "light", **t)
        g.box(3.4, 7.4, 7.3, 7.1, 12.0, 8.7, "metal", **t)
        g.box(2.0, 6.4, 7.5, 3.4, 13.0, 8.5, "metal", **t)
        g.box(1.2, 5.6, 7.6, 2.0, 13.8, 8.4, "light", **t)
        g.box(8.9, 9.4, 7.4, 10.8, 10.8, 8.6, "metal", **t)
        g.box(10.8, 8.0, 7.6, 12.4, 10.4, 8.4, "metal", **t)
        g.box(11.6, 7.0, 7.7, 12.4, 8.0, 8.3, "light", **t)
    elif pid == "warhammer_head":
        socket(9.6, 16.6, 2.0)
        g.box(3.6, 12.0, 5.8, 10.4, 16.2, 10.2, "metal", **t)
        g.box(2.6, 12.4, 6.2, 3.6, 15.8, 9.8, "light", **t)
        g.box(10.4, 13.0, 7.0, 12.8, 15.2, 9.0, "dark", **t)
        g.box(12.8, 13.6, 7.5, 14.8, 14.6, 8.5, "light", **t)
        g.box(7.3, 16.2, 7.3, 8.7, 17.8, 8.7, "metal", **t)
        g.box(3.6, 16.2, 5.8, 10.4, 16.4, 10.2, "dark", **t)
    elif pid == "flail_head":
        # la bola con pinchos y la argolla de la cadena
        g.c(8, 12, 8, 5.0, 5.0, 5.0, "metal", **t)
        g.c(8, 12, 8, 6.0, 4.0, 4.0, "metal", **t)
        g.c(8, 12, 8, 4.0, 6.0, 4.0, "metal", **t)
        g.c(8, 12, 8, 4.0, 4.0, 6.0, "metal", **t)
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, 0, 1), (0, 0, -1)):
            g.c(8 + dx * 3.6, 12 + dy * 3.6, 8 + dz * 3.6, 1.2, 1.2, 1.2, "light", **t)
        for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            g.c(8 + dx * 2.6, 12 + dy * 2.6, 8, 1.0, 1.0, 1.0, "light", **t)
            g.c(8 + dx * 2.4, 12 + dy * 0.0, 8 + dy * 2.4, 0.9, 0.9, 0.9, "light", **t)
        g.c(8, 8.8, 8, 0.8, 1.6, 0.8, "dark", **t)
        g.box(7.0, 6.6, 7.6, 7.6, 8.2, 8.4, "darker", **t)
        g.box(8.4, 6.6, 7.6, 9.0, 8.2, 8.4, "darker", **t)
        g.box(7.0, 6.2, 7.6, 9.0, 6.6, 8.4, "darker", **t)
    elif pid == "battle_axe_head":
        socket(8.8, 16.6, 2.0)
        for side in (-1, 1):
            xa = 8 + side * 1.0
            g.box(xa, 10.4, 7.2, xa + side * 3.6, 15.0, 8.8, "metal", **t)
            g.box(xa + side * 3.6, 9.0, 7.4, xa + side * 5.2, 16.4, 8.6, "metal", **t)
            g.box(xa + side * 5.2, 7.8, 7.6, xa + side * 6.2, 17.6, 8.4, "light", **t)
        g.box(7.4, 16.6, 7.4, 8.6, 18.4, 8.6, "light", **t)
    return g


# ------------------------------------------------------------------ los peces

def interp(points, t):
    for (t0, v0), (t1, v1) in zip(points, points[1:]):
        if t0 <= t <= t1:
            u = (t - t0) / (t1 - t0) if t1 > t0 else 0
            u = u * u * (3 - 2 * u)
            return v0 + (v1 - v0) * u
    return points[-1][1]


DEFAULT = [(0, 0.30), (0.15, 0.42), (0.4, 0.86), (0.6, 1.0), (0.8, 0.92), (0.92, 0.72), (1, 0.45)]

# id: (largo, alto, grueso, perfil, lomo, vientre, aletas, acento, ojo, cola, extras, textura plana)
FISH = {
    "perch": (16, 7.2, 0.45, DEFAULT, (96, 120, 40), (214, 200, 110), (210, 80, 40), (52, 66, 24), (236, 196, 60), "fork", ("bars", "spiny"), "evolvedwarfare:item/perch"),
    "carp": (16, 8.0, 0.48, [(0, 0.32), (0.15, 0.45), (0.4, 0.9), (0.6, 1.0), (0.82, 0.9), (0.94, 0.66), (1, 0.42)], (150, 110, 50), (220, 190, 120), (170, 110, 60), (196, 150, 70), (220, 180, 60), "fan", ("scales", "barbels"), "evolvedwarfare:item/carp"),
    "trout": (16, 6.1, 0.45, DEFAULT, (98, 112, 70), (226, 210, 196), (140, 120, 90), (226, 120, 130), (220, 190, 120), "fan", ("band", "dots"), "evolvedwarfare:item/trout"),
    "pike": (17, 4.9, 0.5, [(0, 0.36), (0.15, 0.62), (0.4, 0.92), (0.7, 1.0), (0.85, 0.82), (1, 0.38)], (70, 100, 50), (210, 214, 170), (120, 110, 60), (214, 214, 120), (236, 200, 60), "fan", ("spots", "back_dorsal"), "evolvedwarfare:item/pike"),
    "eel": (18, 3.0, 0.85, [(0, 0.32), (0.2, 0.75), (0.5, 0.96), (0.85, 1.0), (1, 0.72)], (60, 56, 40), (120, 110, 70), (50, 46, 30), (84, 78, 54), (200, 190, 120), "eel", ("long_fins",), "evolvedwarfare:item/eel"),
    "tuna": (17, 7.8, 0.62, [(0, 0.18), (0.12, 0.3), (0.35, 0.86), (0.55, 1.0), (0.78, 0.9), (0.92, 0.66), (1, 0.4)], (30, 50, 110), (200, 210, 220), (60, 70, 110), (220, 190, 40), (30, 30, 30), "crescent", ("finlets",), "evolvedwarfare:item/tuna"),
    "swordfish": (14, 5.4, 0.6, [(0, 0.2), (0.12, 0.32), (0.35, 0.86), (0.55, 1.0), (0.8, 0.88), (0.94, 0.6), (1, 0.4)], (40, 70, 130), (190, 200, 214), (40, 60, 110), (70, 100, 160), (30, 30, 30), "crescent", ("bill", "sail"), "evolvedwarfare:item/swordfish"),
    "sturgeon": (18, 5.0, 0.75, [(0, 0.3), (0.2, 0.62), (0.5, 1.0), (0.75, 0.95), (0.9, 0.7), (1, 0.3)], (110, 110, 100), (200, 196, 180), (90, 90, 82), (226, 222, 204), (40, 40, 36), "shark", ("scutes", "barbels"), "evolvedwarfare:item/sturgeon"),
    # los de vanilla, que también salen pescando
    "cod": (16, 6.6, 0.5, DEFAULT, (164, 132, 92), (222, 208, 172), (150, 120, 84), (116, 90, 60), (40, 40, 36), "fan", ("dots", "barbels", "three_dorsals"), "minecraft:item/cod"),
    "salmon": (17, 6.4, 0.5, DEFAULT, (84, 96, 96), (230, 170, 150), (150, 70, 60), (204, 72, 62), (40, 40, 36), "fork", ("band_wide",), "minecraft:item/salmon"),
    "tropical_fish": (13, 8.4, 0.45, [(0, 0.4), (0.15, 0.55), (0.4, 0.95), (0.6, 1.0), (0.85, 0.86), (1, 0.5)], (240, 120, 30), (246, 150, 60), (226, 104, 26), (250, 250, 246), (30, 30, 30), "fan", ("white_bands",), "minecraft:item/tropical_fish"),
}


def fish_palette(fid):
    _, _, _, _, back, belly, fin, accent, eye, *_ = FISH[fid]
    p = Palette(sum(map(ord, fid)))
    p.add("back", back, noise=0.07, pattern="spots" if fid in ("carp", "cod") else None,
          accent=tuple(min(255, int(c * 1.18)) for c in back) if fid == "carp" else None)
    p.add("belly", belly, noise=0.05)
    p.add("fin", fin, noise=0.06, pattern="stripes", accent=tuple(int(c * 0.8) for c in fin))
    p.add("accent", accent, noise=0.05)
    p.add("eye", eye, noise=0.02)
    p.add("pupil", (16, 16, 18), noise=0.02)
    p.add("dark", tuple(int(c * 0.55) for c in back), noise=0.05)
    p.add("flank", tuple(int((a + b) / 2) for a, b in zip(back, belly)), noise=0.06)
    return p


def fish(fid):
    L, H, W, prof, *_rest = FISH[fid]
    tail, extras = FISH[fid][9], FISH[fid][10]
    g = Gear(fid, fish_palette(fid), 45)
    n = max(10, int(round(L / 1.15)))
    dl = L / n

    def h(x):
        return H * interp(prof, min(1.0, max(0.0, x / L)))

    def w(x):
        return max(0.9, W * h(x))

    def top(x):
        return 0.55 * h(x)

    def bottom(x):
        return -0.45 * h(x)

    for i in range(n):
        xa, xb = i * dl, (i + 1) * dl
        xm = (xa + xb) / 2
        hh, ww = h(xm), w(xm)
        yb, yt = -0.45 * hh, 0.55 * hh
        ys = yb + 0.38 * hh
        yr = yt - 0.24 * hh
        g.box(xa, yb, -ww * 0.42, xb, ys, ww * 0.42, "belly")
        mat = "back"
        if "bars" in extras and i % 3 == 1 and 0.15 < xm / L < 0.8:
            mat = "dark"
        g.box(xa, ys, -ww / 2, xb, yr, ww / 2, "flank" if fid not in ("tuna", "swordfish", "eel") else mat)
        g.box(xa, yr, -ww * 0.3, xb, yt, ww * 0.3, mat)
        if mat == "dark":
            # las franjas de la perca, por los costados
            g.box(xa, ys + 0.3, -ww / 2 - 0.06, xb, yr, ww / 2 + 0.06, "dark")

    def sides(xa, xb, ya, yb, mat, d=0.07, shade=True):
        ww = min(w(xa), w(xb))
        g.box(xa, ya, ww / 2 - 0.02, xb, yb, ww / 2 + d, mat, shade=shade)
        g.box(xa, ya, -ww / 2 - d, xb, yb, -ww / 2 + 0.02, mat, shade=shade)

    def fin_plate(xa, xb, ya, yb, mat="fin", rot=0.0, pivot=None, d=0.22):
        g.box(xa, ya, -d, xb, yb, d, mat, rot=rot, pivot=pivot)

    rng = random.Random(sum(map(ord, fid)) * 7)
    # dibujos de los costados
    if "band" in extras or "band_wide" in extras:
        k = 0.9 if "band_wide" in extras else 0.45
        for i in range(n):
            xa, xb = i * dl, (i + 1) * dl
            if 0.05 < xa / L < 0.86:
                hh = h((xa + xb) / 2)
                sides(xa, xb, -0.05 * hh, (-0.05 + k * 0.5) * hh * 0.9, "accent")
    if "white_bands" in extras:
        for t0, t1 in ((0.2, 0.3), (0.52, 0.62), (0.8, 0.86)):
            xa, xb = t0 * L, t1 * L
            hh = h((xa + xb) / 2)
            sides(xa, xb, -0.42 * hh, 0.52 * hh, "accent", d=0.09)
            sides(xa - 0.35, xa, -0.4 * hh, 0.5 * hh, "pupil", d=0.1)
            sides(xb, xb + 0.35, -0.4 * hh, 0.5 * hh, "pupil", d=0.1)
    if "dots" in extras or "spots" in extras:
        mat = "dark" if "dots" in extras else "accent"
        for _ in range(16 if "spots" in extras else 12):
            x = rng.uniform(0.12, 0.8) * L
            hh = h(x)
            y = rng.uniform(-0.05, 0.4) * hh
            sides(x, x + 0.7, y, y + 0.7, mat, d=0.1)
    if "scales" in extras:
        for i in range(2, n - 2):
            x = i * dl
            hh = h(x)
            for k in range(3):
                y = (-0.1 + k * 0.17) * hh + (0.3 if i % 2 else 0)
                sides(x, x + dl * 0.6, y, y + 0.5, "accent", d=0.08)
    if "scutes" in extras:
        for i in range(1, n - 1, 1):
            x = i * dl + dl * 0.25
            g.box(x, top(x) - 0.2, -0.45, x + dl * 0.55, top(x) + 0.45, 0.45, "accent")
            hh = h(x)
            sides(x, x + dl * 0.5, 0.05 * hh, 0.05 * hh + 0.6, "accent", d=0.22)
            sides(x, x + dl * 0.5, -0.32 * hh, -0.32 * hh + 0.5, "accent", d=0.16)

    # la cabeza: el ojo (a los dos lados), la agalla y la boca
    xe = L * (0.86 if fid not in ("pike", "sturgeon") else 0.82)
    he = h(xe)
    ye = 0.14 * he
    es = max(1.0, min(1.6, H * 0.2))
    sides(xe - es / 2, xe + es / 2, ye - es / 2, ye + es / 2, "eye", d=0.12, shade=False)
    sides(xe - es / 4, xe + es / 4, ye - es / 4, ye + es / 4, "pupil", d=0.2)
    xg = L * 0.76
    hg = h(xg)
    sides(xg, xg + 0.35, -0.3 * hg, 0.4 * hg, "dark", d=0.08)
    hm = h(L)
    g.box(L - 0.5, -0.2 * hm, -0.5, L + 0.12, 0.02 * hm, 0.5, "dark")
    if "barbels" in extras:
        g.box(L - 1.2, bottom(L - 1.0) - 1.1, -0.15, L - 0.85, bottom(L - 1.0) + 0.2, 0.15, "dark")
        g.box(L - 1.9, bottom(L - 1.6) - 0.9, -0.35, L - 1.6, bottom(L - 1.6) + 0.2, -0.05, "dark")
    if "bill" in extras:
        g.box(L - 0.2, -0.05 * hm, -0.35, L + 0.75 * L, 0.3 * hm, 0.35, "dark")
        g.box(L + 0.5 * L, 0.0, -0.25, L + 0.85 * L, 0.2 * hm, 0.25, "dark")

    # la cola
    h0 = h(0)
    hl = max(2.0, H * 0.55)
    if tail == "fork":
        fin_plate(-hl * 0.9, 0.6, -0.1, 0.9, rot=-22.5, pivot=(0.6, 0.2))
        fin_plate(-hl * 0.9, 0.6, -0.9, 0.1, rot=22.5, pivot=(0.6, -0.2))
        fin_plate(-hl * 0.35, 0.6, -h0 * 0.4, h0 * 0.4)
    elif tail == "fan":
        fin_plate(-hl * 0.8, 0.6, 0.0, 0.9, rot=-22.5, pivot=(0.6, 0.2))
        fin_plate(-hl * 0.8, 0.6, -0.9, 0.0, rot=22.5, pivot=(0.6, -0.2))
        fin_plate(-hl * 0.75, 0.6, -hl * 0.45, hl * 0.45)
    elif tail == "crescent":
        fin_plate(-hl * 1.25, 0.6, -0.1, 0.75, rot=-22.5, pivot=(0.6, 0.2), d=0.18)
        fin_plate(-hl * 1.25, 0.6, -0.75, 0.1, rot=22.5, pivot=(0.6, -0.2), d=0.18)
        fin_plate(-hl * 0.9, -hl * 0.55, -hl * 0.6, hl * 0.6, d=0.18)
        fin_plate(-0.8, 0.6, -h0 * 0.5, h0 * 0.5, mat="dark")
    elif tail == "shark":
        fin_plate(-hl * 1.3, 0.6, -0.1, 0.9, rot=-22.5, pivot=(0.6, 0.2))
        fin_plate(-hl * 0.6, 0.6, -0.8, 0.1, rot=45, pivot=(0.6, -0.2))
    elif tail == "eel":
        fin_plate(-1.6, 0.6, -h0 * 0.7, h0 * 0.7)

    # aletas: dorsal(es), anal, pélvica y pectoral
    def dorsal(t0, t1, k, mat="fin"):
        xa, xb = t0 * L, t1 * L
        base = min(top(xa), top(xb)) - 0.5
        hd = max(0.9, k * H)
        fin_plate(xa, xb - (xb - xa) * 0.4, base, base + 0.5 + hd, mat=mat)
        fin_plate(xa, xb, base, base + 0.5 + hd * 0.55, mat=mat)

    def anal(t0, t1, k, mat="fin"):
        xa, xb = t0 * L, t1 * L
        base = max(bottom(xa), bottom(xb)) + 0.5
        hd = max(0.8, k * H)
        fin_plate(xa + (xb - xa) * 0.3, xb, base - 0.5 - hd, base, mat=mat)

    if "long_fins" in extras:
        fin_plate(0.0, L * 0.7, top(L * 0.35) - 0.4, top(L * 0.35) + 0.6)
        fin_plate(0.0, L * 0.5, bottom(L * 0.3) - 0.6, bottom(L * 0.3) + 0.4)
    elif "sail" in extras:
        xa = L * 0.55
        base = top(xa) - 0.5
        fin_plate(L * 0.5, L * 0.78, base, base + H * 0.95, mat="fin")
        fin_plate(L * 0.4, L * 0.5, base, base + H * 0.45, mat="fin")
        anal(0.25, 0.4, 0.25)
    elif "three_dorsals" in extras:
        dorsal(0.58, 0.74, 0.2)
        dorsal(0.38, 0.54, 0.18)
        dorsal(0.18, 0.33, 0.15)
        anal(0.2, 0.5, 0.14)
    elif "back_dorsal" in extras:
        dorsal(0.12, 0.3, 0.32)
        anal(0.1, 0.28, 0.3)
    else:
        dorsal(0.42, 0.66, 0.3 if fid not in ("tuna", "sturgeon") else 0.22)
        if "spiny" in extras:
            dorsal(0.22, 0.4, 0.24)
            for k in range(4):
                x = L * (0.44 + k * 0.05)
                g.box(x, top(x) - 0.3, -0.24, x + 0.3, top(x) + 0.4 + 0.3 * H, 0.24, "dark")
        elif fid in ("tuna",):
            dorsal(0.28, 0.38, 0.22, mat="accent")
        anal(0.22, 0.42, 0.18 if fid != "tuna" else 0.22, mat="accent" if fid == "tuna" else "fin")
    if "finlets" in extras:
        for k in range(5):
            x = L * (0.06 + k * 0.045)
            g.box(x, top(x) - 0.3, -0.2, x + 0.4, top(x) + 0.55, 0.2, "accent")
            g.box(x, bottom(x) - 0.55, -0.2, x + 0.4, bottom(x) + 0.3, 0.2, "accent")
    # pélvica (abajo) y pectoral (al costado, detrás de la agalla)
    xp = L * 0.55
    fin_plate(xp - 1.6, xp + 0.4, bottom(xp) - 1.0, bottom(xp) + 0.4, rot=22.5, pivot=(xp + 0.4, bottom(xp)))
    xq = L * 0.72
    yq = -0.12 * h(xq)
    wq = w(xq)
    for side in (1, -1):
        g.box(xq - max(1.6, H * 0.3), yq - 0.5, side * (wq / 2 + 0.05), xq, yq + 0.5, side * (wq / 2 + 0.3), "fin", rot=22.5, pivot=(xq, yq))
    return g


def pufferfish():
    p = Palette(77)
    p.add("body", (226, 196, 60), noise=0.06, pattern="spots", accent=(196, 160, 40))
    p.add("belly", (246, 236, 190), noise=0.04)
    p.add("spike", (250, 242, 200), noise=0.03)
    p.add("fin", (214, 150, 40), noise=0.05, pattern="stripes", accent=(180, 120, 30))
    p.add("eye", (250, 250, 250), noise=0.01)
    p.add("pupil", (16, 16, 18), noise=0.02)
    p.add("dark", (120, 100, 40), noise=0.05)
    p.add("mouth", (60, 40, 20), noise=0.04)
    g = Gear("pufferfish", p, 45)
    # inflado: una bola de cubos con pinchos
    g.c(0, 0, 0, 8.0, 7.0, 7.0, "body")
    g.c(0, -0.6, 0, 7.0, 6.6, 7.6, "body")
    g.c(0, -3.7, 0, 6.0, 0.8, 6.0, "belly")
    g.c(0, 0, 0, 9.0, 5.2, 5.4, "body")
    g.c(0, 3.8, 0, 5.4, 0.8, 5.0, "body")
    for x in (-3, -1, 1, 3):
        for y in (-2.5, 0, 2.5):
            for side in (1, -1):
                g.c(x, y, side * 3.95, 0.5, 0.5, 0.9, "spike")
        g.c(x, 4.5, 0.6 * (1 if x > 0 else -1), 0.5, 0.9, 0.5, "spike")
    for y in (-1.5, 1.5):
        g.c(-4.8, y, 0, 0.9, 0.5, 0.5, "spike")
    g.c(4.6, 1.0, 2.9, 1.6, 1.6, 1.2, "eye", shade=False)
    g.c(4.6, 1.0, -2.9, 1.6, 1.6, 1.2, "eye", shade=False)
    g.c(5.0, 1.0, 3.3, 0.8, 0.8, 0.8, "pupil")
    g.c(5.0, 1.0, -3.3, 0.8, 0.8, 0.8, "pupil")
    g.c(4.7, -1.0, 0, 0.8, 1.0, 1.8, "mouth")
    g.c(-5.6, 0, 0, 1.8, 3.4, 0.4, "fin")
    g.c(1.0, -1.0, 4.0, 2.0, 1.6, 0.3, "fin", rot=22.5)
    g.c(1.0, -1.0, -4.0, 2.0, 1.6, 0.3, "fin", rot=22.5)
    g.c(-1.2, 4.6, 0, 2.2, 1.0, 0.4, "fin", rot=-22.5)
    return g


# ------------------------------------------------------------------ cebos y caña

def bait(magic):
    p = Palette(90 + magic)
    if magic:
        p.add("glass", (200, 230, 240), noise=0.03)
        p.add("glow", (120, 230, 250), noise=0.08)
        p.add("gold", (255, 220, 90), noise=0.06)
        p.add("cork", (176, 130, 80), pattern="spots", accent=(140, 100, 60))
        p.add("rim", (220, 236, 240), noise=0.02)
        p.add("worm", (230, 140, 200), noise=0.06)
    else:
        p.add("tin", (150, 156, 160), noise=0.05)
        p.add("label", (60, 110, 60), pattern="stripes", accent=(220, 200, 120))
        p.add("dirt", (90, 62, 40), pattern="spots", accent=(70, 46, 30))
        p.add("worm", (214, 120, 120), noise=0.06, pattern="rings", accent=(184, 96, 98))
        p.add("rim", (196, 200, 204), noise=0.02)
        p.add("dark", (90, 94, 98), noise=0.05)
    g = Gear("magic_bait" if magic else "bait", p, 0)
    if magic:
        # un tarro de cristal con cebo que brilla y unas chispas doradas
        g.box(4.4, 0.0, 4.4, 11.6, 0.6, 11.6, "rim")
        g.box(5.4, 0.6, 5.4, 10.6, 7.6, 10.6, "glow", shade=False)
        g.box(4.2, 0.6, 4.2, 11.8, 8.4, 4.6, "glass")
        g.box(4.2, 0.6, 11.4, 11.8, 8.4, 11.8, "glass")
        g.box(4.2, 0.6, 4.6, 4.6, 8.4, 11.4, "glass")
        g.box(11.4, 0.6, 4.6, 11.8, 8.4, 11.4, "glass")
        g.box(5.0, 8.4, 5.0, 11.0, 9.2, 11.0, "rim")
        g.box(5.6, 9.2, 5.6, 10.4, 11.0, 10.4, "cork")
        for x, y, z in ((6.4, 3.0, 7.0), (8.8, 5.2, 9.0), (7.6, 6.6, 6.2), (9.4, 2.2, 6.8), (6.8, 5.6, 9.2)):
            g.c(x, y, z, 0.9, 0.9, 0.9, "gold", shade=False)
        for x, y, z in ((12.8, 9.6, 7.0), (3.2, 7.4, 9.4), (10.6, 12.2, 10.6)):
            g.c(x, y, z, 0.6, 0.6, 0.6, "gold", shade=False)
    else:
        # una lata de lombrices
        g.box(4.6, 0.0, 4.6, 11.4, 5.4, 11.4, "tin")
        g.box(4.2, 1.4, 4.2, 11.8, 4.2, 11.8, "label")
        g.box(4.4, 5.4, 4.4, 11.6, 6.0, 11.6, "rim")
        g.box(5.0, 5.4, 5.0, 11.0, 6.2, 11.0, "dirt")
        g.box(4.4, 0.0, 4.4, 11.6, 0.4, 11.6, "dark")
        for x0, z0, x1, z1, y in ((6.0, 7.0, 9.8, 7.8, 6.2), (7.0, 8.6, 7.8, 10.4, 6.4), (8.6, 6.0, 10.4, 6.8, 6.6)):
            g.box(x0, y, z0, x1, y + 0.8, z1, "worm")
        g.box(9.0, 6.2, 8.8, 9.8, 9.0, 9.6, "worm")
        g.box(9.8, 8.4, 8.8, 11.2, 9.2, 9.6, "worm")
        g.box(6.2, 6.2, 5.6, 7.0, 7.8, 6.4, "worm")
    return g


def rod(cast):
    p = Palette(60 + cast)
    p.add("cork", (196, 150, 98), pattern="spots", accent=(160, 116, 70))
    p.add("blank", (120, 74, 40), pattern="stripes", accent=(98, 58, 30))
    p.add("wrap", (150, 30, 30), noise=0.05)
    p.add("metal", (170, 176, 184), noise=0.05)
    p.add("metal_dark", (80, 84, 92), noise=0.05)
    p.add("line", (232, 232, 236), noise=0.02)
    p.add("hook", (110, 114, 120), noise=0.04)
    p.add("cap", (40, 40, 44), noise=0.04)
    g = Gear("fishing_rod_cast" if cast else "fishing_rod", p, -45)
    g.box(7.2, -2.2, 7.2, 8.8, -1.4, 8.8, "cap")
    g.box(7.1, -1.4, 7.1, 8.9, 3.4, 8.9, "cork")
    g.box(7.3, 3.4, 7.3, 8.7, 5.6, 8.7, "metal_dark")
    # el carrete, debajo
    g.box(8.7, 4.0, 7.7, 9.4, 4.6, 8.3, "metal_dark")
    g.box(9.4, 3.4, 6.8, 11.2, 5.4, 9.2, "metal")
    g.box(9.6, 3.6, 9.2, 11.0, 5.2, 9.5, "metal_dark")
    g.box(10.0, 4.2, 9.5, 10.6, 4.8, 10.6, "metal_dark")
    g.box(10.0, 4.2, 10.6, 10.6, 5.8, 11.0, "cap")
    g.box(7.6, 5.6, 7.6, 8.4, 6.2, 8.4, "wrap")
    g.box(7.62, 6.2, 7.62, 8.38, 11.0, 8.38, "blank")
    g.box(7.58, 10.8, 7.58, 8.42, 11.3, 8.42, "wrap")
    tip_rot = -22.5 if cast else 0.0
    g.box(7.7, 11.0, 7.7, 8.3, 15.4, 8.3, "blank")
    # la punta (lanzada, doblada por el sedal)
    g.box(7.78, 15.4, 7.78, 8.22, 19.6, 8.22, "blank", rot=tip_rot, pivot=(8.0, 15.4))
    for y in (8.6, 13.0):
        g.box(8.3, y, 7.85, 9.0, y + 0.3, 8.15, "metal")
    g.box(8.22, 17.6, 7.88, 8.8, 17.85, 8.12, "metal", rot=tip_rot, pivot=(8.0, 15.4))
    if not cast:
        # el sedal cuelga de la punta, vertical (la caña va inclinada 45°: el sedal, 45° al revés) con su anzuelo
        g.box(7.93, 7.0, 7.93, 8.07, 19.6, 8.07, "line", rot=45, pivot=(8.0, 19.6), shade=False)
        g.box(7.8, 6.2, 7.8, 8.2, 7.0, 8.2, "hook", rot=45, pivot=(8.0, 19.6))
        g.box(8.2, 6.2, 7.85, 8.7, 6.5, 8.15, "hook", rot=45, pivot=(8.0, 19.6))
        g.box(8.4, 6.5, 7.85, 8.7, 7.0, 8.15, "hook", rot=45, pivot=(8.0, 19.6))
    g.frame = ((6.8, -2.2, 6.8), (11.2, 19.6, 11.0))
    return g


# ------------------------------------------------------------------ todo

PART_IDS = ["sword_blade", "pickaxe_head", "axe_head", "shovel_head", "hoe_head", "dagger_blade", "spearhead", "longsword_blade",
            "halberd_head", "warhammer_head", "flail_head", "battle_axe_head"]


def all_models():
    """(gear, ruta del json, display, partícula, gui, luz, encaje)"""
    out = []
    out.append((hammer(), os.path.join(ASSETS, "models/item/smithing_hammer.json"), HANDHELD, "%s:item/smithing_hammer" % NS, GUI_TILT, "front", 15.5))
    for gold in (False, True):
        name = "hot_%s_ingot" % ("gold" if gold else "iron")
        out.append((ingot(gold), os.path.join(ASSETS, "models/item/%s.json" % name), OBJECT, "%s:item/%s" % (NS, name), None, "side", 13.0))
    for pid in PART_IDS:
        out.append((part(pid), os.path.join(ASSETS, "models/item/forged_part_%s.json" % pid), HANDHELD, "%s:item/part/%s" % (NS, pid), GUI_TILT, "front", 14.0))
    for fid in FISH:
        root = ASSETS if FISH[fid][11].startswith(NS) else MC_ASSETS
        out.append((fish(fid), os.path.join(root, "models/item/%s.json" % fid), GENERATED, FISH[fid][11], GUI_TILT, "front", 15.0))
    out.append((pufferfish(), os.path.join(MC_ASSETS, "models/item/pufferfish.json"), GENERATED, "minecraft:item/pufferfish", GUI_TILT, "front", 13.5))
    for magic in (False, True):
        name = "magic_bait" if magic else "bait"
        out.append((bait(magic), os.path.join(ASSETS, "models/item/%s.json" % name), OBJECT, "%s:item/%s" % (NS, name), None, "side", 12.0))
    for cast in (False, True):
        out.append((rod(cast), os.path.join(MC_ASSETS, "models/item/fishing_rod%s.json" % ("_cast" if cast else "")), ROD,
                    "minecraft:item/fishing_rod", GUI_TILT, "front", 15.5))
    return out


def euler(rot):
    rx, ry, rz = [math.radians(v) for v in rot]

    def R(axis, a):
        c, s = math.cos(a), math.sin(a)
        if axis == "x":
            return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
        if axis == "y":
            return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
        return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])

    return R("x", rx) @ R("y", ry) @ R("z", rz)


def main():
    tex_dir = os.path.join(ASSETS, "textures/item/3d")
    os.makedirs(tex_dir, exist_ok=True)
    previews = []
    for gear, path, display, particle, gui, light, fit in all_models():
        m = gear.build(fit=fit)
        im = gear.palette.im.copy()
        if "glass" in gear.palette.names:
            x0, y0 = gear.palette.region("glass")
            for y in range(y0, y0 + 8):
                for x in range(x0, x0 + 4):
                    r, gg, b, _ = im.getpixel((x, y))
                    im.putpixel((x, y), (r, gg, b, 96))
        im.save(os.path.join(tex_dir, gear.name + ".png"))
        obj = model_json(m, display, particle, gui=gui, light=light)
        if gear.name == "fishing_rod":
            obj["overrides"] = [{"predicate": {"cast": 1}, "model": "minecraft:item/fishing_rod_cast"}]
        write_json(path, obj)
        previews.append((gear.name, obj, im))
    # la pieza sin dibujo conocido: la hoja de espada, con las demás por el predicado
    overrides = [{"predicate": {"%s:part" % NS: round(k / 100.0, 3)}, "model": "%s:item/forged_part_%s" % (NS, pid)} for k, pid in enumerate(PART_IDS)]
    write_json(os.path.join(ASSETS, "models/item/forged_part.json"), {"parent": "%s:item/forged_part_%s" % (NS, PART_IDS[0]), "overrides": overrides})
    print("modelos 3D:", len(previews))
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        cell = 150
        cols = 6
        rows = (len(previews) + cols - 1) // cols
        for view in ("gui", "3d"):
            sheet = Image.new("RGB", (cols * cell, rows * cell), (40, 40, 44))
            for k, (name, obj, im) in enumerate(previews):
                imgs = {obj["textures"]["t"]: im}
                if view == "gui":
                    gd = obj["display"].get("gui")
                    pre = euler(gd["rotation"]) * gd["scale"][0] if gd else np.eye(3)
                    pic = render([obj], imgs, size=cell, scale=8.0, yaw=0, pitch=0, pre=pre, bg=(139, 139, 139))
                else:
                    pic = render([obj], imgs, size=cell, scale=7.0, yaw=35, pitch=25)
                sheet.paste(pic, ((k % cols) * cell, (k // cols) * cell))
            sheet.save(os.path.join(PREVIEW, "gear3d_%s.png" % view))


if __name__ == "__main__":
    main()
