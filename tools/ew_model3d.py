"""Modelos de bloque/objeto de Minecraft hechos de cubos (elementos) y una vista previa con su textura.

- `Model`: lista de elementos con `box(...)`, cada cara con su trozo de la textura de materiales.
- `Palette`: una textura de 16x16 con 8 materiales (4x8 píxeles cada uno: dos filas de cuatro), cada uno con su color,
  su ruido y, si se quiere, su dibujo (granos, vetas, rayas...).
- `render(...)`: dibuja un modelo (o varios) en isométrico con z-buffer, sombreado como el juego, para revisar los
  diseños sin abrir Minecraft.
"""
import math
import random

import numpy as np
from PIL import Image

FACES = ("north", "south", "east", "west", "up", "down")
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def clamp(v, a=0, b=255):
    return max(a, min(b, int(round(v))))


class Palette:
    """Ocho materiales en una textura de 16x16 (material i: x = (i % 4) * 4, y = (i // 4) * 8, de 4 x 8)."""

    def __init__(self, seed=0):
        self.im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        self.rng = random.Random(seed)
        self.names = {}

    def add(self, name, color, noise=0.08, pattern=None, accent=None):
        i = len(self.names)
        if i >= 8:
            raise ValueError("ocho materiales como mucho")
        self.names[name] = i
        x0, y0 = (i % 4) * 4, (i // 4) * 8
        accent = accent or tuple(clamp(c * 0.7) for c in color)
        for y in range(8):
            for x in range(4):
                c = color
                if pattern == "kernels" and (x % 2 == 1 or y % 2 == 1):
                    c = accent
                elif pattern == "vein" and x == 1:
                    c = accent
                elif pattern == "stripes" and y % 3 == 0:
                    c = accent
                elif pattern == "spots" and self.rng.random() < 0.25:
                    c = accent
                elif pattern == "rings" and y % 4 == 1:
                    c = accent
                elif pattern == "skin" and self.rng.random() < 0.18:
                    c = accent
                f = 1.0 + self.rng.uniform(-noise, noise)
                self.im.putpixel((x0 + x, y0 + y), tuple(clamp(v * f) for v in c[:3]) + (255,))
        return name

    def region(self, name):
        i = self.names[name]
        return (i % 4) * 4, (i // 4) * 8


class Model:
    def __init__(self, palette, texture, rng_seed=1):
        self.palette = palette
        self.texture = texture
        self.elements = []
        self.rng = random.Random(rng_seed)

    def box(self, x0, y0, z0, x1, y1, z1, mat, rot=None, faces=FACES, shade=True):
        """Un cubo de (x0, y0, z0) a (x1, y1, z1) del material; rot = (eje, ángulo, origen)."""
        if x1 < x0:
            x0, x1 = x1, x0
        if y1 < y0:
            y0, y1 = y1, y0
        if z1 < z0:
            z0, z1 = z1, z0
        rx, ry = self.palette.region(mat)
        sizes = {"north": (x1 - x0, y1 - y0), "south": (x1 - x0, y1 - y0), "east": (z1 - z0, y1 - y0),
                 "west": (z1 - z0, y1 - y0), "up": (x1 - x0, z1 - z0), "down": (x1 - x0, z1 - z0)}
        fs = {}
        for f in faces:
            w, h = sizes[f]
            w = max(0.5, min(4.0, w))
            h = max(0.5, min(8.0, h))
            u0 = rx + self.rng.uniform(0, 4.0 - w)
            v0 = ry + self.rng.uniform(0, 8.0 - h)
            fs[f] = {"uv": [round(u0, 3), round(v0, 3), round(u0 + w, 3), round(v0 + h, 3)], "texture": "#t"}
        e = {"from": [round(x0, 3), round(y0, 3), round(z0, 3)], "to": [round(x1, 3), round(y1, 3), round(z1, 3)], "faces": fs}
        if not shade:
            e["shade"] = False
        if rot:
            axis, angle, origin = rot
            e["rotation"] = {"origin": [round(o, 3) for o in origin], "axis": axis, "angle": angle}
        self.elements.append(e)
        return e

    def json(self, display=None, ao=False):
        out = {"ambientocclusion": ao, "textures": {"t": self.texture, "particle": self.texture}, "elements": self.elements}
        if display:
            out["display"] = display
        return out


# ------------------------------------------------------------------ vista previa

def _rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def _corners(e, face):
    (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
    # arriba-izquierda, arriba-derecha, abajo-derecha, abajo-izquierda (vista desde fuera de la cara)
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def render(models, texture_images, size=160, scale=7.0, yaw=45.0, pitch=30.0, bg=(70, 74, 80), offsets=None, ground=None):
    """Dibuja modelos (dicts json) en isométrico. texture_images: {id de textura: PIL}. offsets: desplazamiento de cada modelo."""
    img = np.zeros((size, size, 3), dtype=np.float32)
    img[:, :] = bg
    zbuf = np.full((size, size), -1e9, dtype=np.float32)
    view = _rot("x", pitch) @ _rot("y", yaw)
    cx = cy = size / 2.0
    offsets = offsets or [(0, 0, 0)] * len(models)
    items = list(zip(models, offsets))
    if ground is not None:
        items.append(ground)
    for model, off in items:
        tex = np.array(texture_images[model["textures"]["t"]].convert("RGBA"), dtype=np.float32)
        th, tw = tex.shape[0], tex.shape[1]
        for e in model["elements"]:
            r = np.eye(3)
            origin = np.zeros(3)
            if "rotation" in e:
                r = _rot(e["rotation"]["axis"], e["rotation"]["angle"])
                origin = np.array(e["rotation"]["origin"], dtype=float)
            for face, fd in e["faces"].items():
                pts = []
                for p in _corners(e, face):
                    q = r @ (np.array(p, dtype=float) - origin) + origin + np.array(off, dtype=float) - 8.0
                    v = view @ q
                    pts.append(v)
                p0, p1, _, p3 = pts
                e1 = p1 - p0
                e2 = p3 - p0
                n = np.cross(e1, e2)
                if n[2] <= 1e-6:
                    # de espaldas (la cámara mira hacia -z)
                    continue
                sx = lambda v: cx + v[0] * scale
                sy = lambda v: cy - v[1] * scale
                xs = [sx(p) for p in pts]
                ys = [sy(p) for p in pts]
                bx0, bx1 = max(0, int(min(xs))), min(size - 1, int(max(xs)) + 1)
                by0, by1 = max(0, int(min(ys))), min(size - 1, int(max(ys)) + 1)
                if bx1 < bx0 or by1 < by0:
                    continue
                gx, gy = np.meshgrid(np.arange(bx0, bx1 + 1) + 0.5, np.arange(by0, by1 + 1) + 0.5)
                # resolver p = p0 + a e1 + b e2 en pantalla
                a11, a12 = e1[0] * scale, e2[0] * scale
                a21, a22 = -e1[1] * scale, -e2[1] * scale
                det = a11 * a22 - a12 * a21
                if abs(det) < 1e-9:
                    continue
                dx = gx - sx(p0)
                dy = gy - sy(p0)
                a = (dx * a22 - a12 * dy) / det
                b = (a11 * dy - a21 * dx) / det
                inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
                if not inside.any():
                    continue
                depth = p0[2] + a * e1[2] + b * e2[2]
                u0, v0, u1, v1 = fd["uv"]
                u = (u0 + a * (u1 - u0)) / 16.0 * tw
                v = (v0 + b * (v1 - v0)) / 16.0 * th
                ui = np.clip(u.astype(int), 0, tw - 1)
                vi = np.clip(v.astype(int), 0, th - 1)
                col = tex[vi, ui]
                ok = inside & (col[..., 3] > 127)
                sub = zbuf[by0:by1 + 1, bx0:bx1 + 1]
                ok &= depth > sub
                f = SHADE[face] if e.get("shade", True) else 1.0
                region = img[by0:by1 + 1, bx0:bx1 + 1]
                region[ok] = col[..., :3][ok] * f
                sub[ok] = depth[ok]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
