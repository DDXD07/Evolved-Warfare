"""Un rasterizador pequeño con texturas y z-buffer para las vistas previas de las armaduras (y sus iconos): las caras de
los cubos con la misma geometría y las mismas coordenadas de textura que los ModelPart de Minecraft."""
import math

import numpy as np
from PIL import Image


def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def S(x, y, z):
    return np.diag([x, y, z, 1.0])


def RX(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1.0]])


def RY(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1.0]])


def RZ(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1.0]])


def D(a):
    return math.radians(a)


# del espacio del modelo (1/16 de bloque, y hacia abajo) al mundo, como LivingEntityRenderer con el cuerpo mirando al sur
ENTITY = T(0, 1.501, 0) @ S(1, -1, -1)


def part_matrix(pivot, rot):
    """ModelPart.rotate: trasladar al pivote y girar Z, Y, X (grados)."""
    return T(*(c / 16.0 for c in pivot)) @ RZ(D(rot[2])) @ RY(D(rot[1])) @ RX(D(rot[0]))


def cuboid_quads(m, box, uv, dil=0.0, mirror=False):
    """Las 6 caras de un cubo como las hace ModelPart.Cuboid: [(4 vértices en el mundo, 4 (u, v) en píxeles)]."""
    x, y, z, w, h, d = box
    u, v = uv
    dx, dy, dz = dil if isinstance(dil, tuple) else (dil, dil, dil)
    x0, y0, z0 = x - dx, y - dy, z - dz
    x1, y1, z1 = x + w + dx, y + h + dy, z + d + dz
    if mirror:
        x0, x1 = x1, x0
    pts = {
        1: (x0, y0, z0), 2: (x1, y0, z0), 3: (x1, y1, z0), 4: (x0, y1, z0),
        5: (x0, y0, z1), 6: (x1, y0, z1), 7: (x1, y1, z1), 8: (x0, y1, z1),
    }
    j, k, l, mm, n, o = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    p, q, r = v, v + d, v + d + h
    faces = [
        ((6, 5, 1, 2), (k, p, l, q)),   # arriba (DOWN en Minecraft: y mínima)
        ((3, 4, 8, 7), (l, q, mm, p)),  # abajo
        ((1, 5, 8, 4), (j, q, k, r)),   # derecha del personaje (x mínima)
        ((2, 1, 4, 3), (k, q, l, r)),   # delante (z mínima)
        ((6, 2, 3, 7), (l, q, n, r)),   # izquierda
        ((5, 6, 7, 8), (n, q, o, r)),   # detrás
    ]
    out = []
    for verts, (u1, v1, u2, v2) in faces:
        world = []
        for i in verts:
            p4 = m @ np.array([pts[i][0] / 16.0, pts[i][1] / 16.0, pts[i][2] / 16.0, 1.0])
            world.append(p4[:3])
        uvs = [(u2, v1), (u1, v1), (u1, v2), (u2, v2)]
        out.append((np.array(world), uvs))
    return out


def look(eye, target):
    eye = np.array(eye, float)
    fwd = np.array(target, float) - eye
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    v = np.eye(4)
    v[0, :3], v[1, :3], v[2, :3] = right, up, -fwd
    return v @ T(*(-eye))


LIGHT0 = np.array([0.2, 1.0, -0.7])
LIGHT1 = np.array([-0.2, 1.0, 0.7])
LIGHT0 /= np.linalg.norm(LIGHT0)
LIGHT1 /= np.linalg.norm(LIGHT1)


class Canvas:
    """Imagen + z-buffer. project(p) -> (x, y, profundidad) con perspectiva o paralela."""

    def __init__(self, w, h, view, fov=None, scale=None, bg=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.view = view
        self.fov = fov
        self.scale = scale
        self.color = np.zeros((h, w, 4), np.float32)
        self.color[:, :] = bg
        self.depth = np.full((h, w), -1e9, np.float32)
        self.boost = 1.0

    def project(self, p):
        q = self.view @ np.append(p, 1.0)
        if self.fov is not None:
            f = 1.0 / math.tan(math.radians(self.fov) / 2)
            if q[2] > -0.01:
                return None
            x = q[0] / -q[2] * f * (self.h / self.w)
            y = q[1] / -q[2] * f
            return ((x + 1) * self.w / 2, (1 - y) * self.h / 2, q[2], 1.0 / -q[2])
        return (self.w / 2 + q[0] * self.scale, self.h / 2 - q[1] * self.scale, q[2], 1.0)

    def quad(self, world, uvs, tex, flat=None):
        n = np.cross(world[1] - world[0], world[2] - world[0])
        nl = np.linalg.norm(n)
        if nl < 1e-12:
            return
        n /= nl
        # luz de las criaturas de Minecraft: dos luces y un mínimo
        shade = min(1.0, 0.4 + 0.6 * (max(0.0, n @ LIGHT0) + max(0.0, n @ LIGHT1)) * 0.6 + 0.25)
        shade = max(shade, 0.4 + 0.6 * max(abs(n @ LIGHT0), abs(n @ LIGHT1)) * 0.6 + 0.25)
        shade = min(1.0, shade * self.boost)
        pr = [self.project(p) for p in world]
        if any(p is None for p in pr):
            return
        for tri in ((0, 1, 2), (0, 2, 3)):
            self._tri([pr[i] for i in tri], [uvs[i] for i in tri], tex, shade, flat)

    def _tri(self, p, uv, tex, shade, flat):
        xs = [a[0] for a in p]
        ys = [a[1] for a in p]
        x0, x1 = max(0, int(math.floor(min(xs)))), min(self.w - 1, int(math.ceil(max(xs))))
        y0, y1 = max(0, int(math.floor(min(ys)))), min(self.h - 1, int(math.ceil(max(ys))))
        if x0 > x1 or y0 > y1:
            return
        (ax, ay, az, aw), (bx, by, bz, bw), (cx, cy, cz, cw) = p
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            return
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        l0 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / den
        l1 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den
        l2 = 1.0 - l0 - l1
        inside = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
        if not inside.any():
            return
        # corrección de perspectiva
        w0, w1, w2 = l0 * aw, l1 * bw, l2 * cw
        ws = w0 + w1 + w2
        z = (l0 * az + l1 * bz + l2 * cz)
        if flat is not None:
            col = np.empty(gx.shape + (4,), np.float32)
            col[...] = (*flat, 255)
        else:
            tu = (w0 * uv[0][0] + w1 * uv[1][0] + w2 * uv[2][0]) / ws
            tv = (w0 * uv[0][1] + w1 * uv[1][1] + w2 * uv[2][1]) / ws
            th, tw = tex.shape[:2]
            iu = np.clip(np.floor(tu - 1e-4).astype(int), 0, tw - 1)
            iv = np.clip(np.floor(tv - 1e-4).astype(int), 0, th - 1)
            col = tex[iv, iu].astype(np.float32)
        ok = inside & (col[..., 3] > 127) & (z > self.depth[y0:y1 + 1, x0:x1 + 1])
        if not ok.any():
            return
        region = self.color[y0:y1 + 1, x0:x1 + 1]
        region[ok, :3] = col[ok, :3] * shade
        region[ok, 3] = 255
        self.depth[y0:y1 + 1, x0:x1 + 1][ok] = z[ok]

    def image(self):
        return Image.fromarray(np.clip(self.color, 0, 255).astype(np.uint8), "RGBA")
