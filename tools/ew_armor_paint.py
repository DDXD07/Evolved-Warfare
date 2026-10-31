"""Las texturas de las armaduras de las facciones: cada cubo tiene su sitio en la textura (como el UV de caja de
Minecraft) y cada cara se pinta con el patrón de su material (tela, cuero, piel, malla, placas, escamas, oro...) más sus
detalles (cintos, ribetes, botones, emblemas, rendijas, flecos)."""
import random

import numpy as np

# ------------------------------------------------------------------ dibujitos (1 = color, 2 = sombra)
SPRITES = {
    "skull": ["01110", "11111", "10101", "11111", "01010"],
    "skull_small": ["111", "101", "010"],
    "cross_bones": ["10001", "01010", "00100", "01010", "10001"],
    "buckle": ["111", "101", "111"],
    "raven": ["0110", "1111", "0111", "0010"],
    "knot": ["11", "11"],
    "brooch": ["010", "111", "010"],
    "anchor": ["010", "111", "010", "010", "101"],
    "star": ["010", "111", "010"],
    "rune": ["10", "11", "10"],
}


def clamp(a):
    return np.clip(a, 0, 255)


def _noise(rnd, shape, amt):
    return np.array([[rnd.randint(-amt, amt) for _ in range(shape[1])] for _ in range(shape[0])], np.float32)


def pattern(mat, fw, fh, face, rnd):
    """Un trozo (fh x fw) RGBA con el patrón del material (s hacia la derecha, t hacia abajo)."""
    kind = mat["kind"]
    c = np.array(mat["c"], np.float32)
    s, t = np.meshgrid(np.arange(fw), np.arange(fh))
    out = np.zeros((fh, fw, 4), np.float32)
    out[..., :3] = c
    out[..., 3] = 255
    add = np.zeros((fh, fw), np.float32)
    side = face in "NSEW"
    if kind == "cloth":
        add += _noise(rnd, (fh, fw), 4) - ((s + t) % 2) * 5
        if side and fh > 2:
            add[fh - 1] -= 12  # dobladillo
    elif kind == "leather":
        add += _noise(rnd, (fh, fw), 6)
        for _ in range(max(1, fw * fh // 14)):
            add[rnd.randrange(fh), rnd.randrange(fw)] -= 14
        if fw > 2 and fh > 2:
            add[0, :] += 10
            add[-1, :] -= 16
            add[:, 0] -= 6
            add[:, -1] -= 10
    elif kind == "fur":
        cols = np.array([rnd.randint(-14, 14) for _ in range(fw)], np.float32)
        add += cols[None, :] + _noise(rnd, (fh, fw), 10)
        add += np.where((t + (s * 3) % 4) % 4 == 0, 12, 0)
        add += (t / max(1, fh - 1)) * -10 if side else 0
    elif kind == "chain":
        ring = np.where((s % 2 == 0) & (t % 2 == 0), 26, np.where((s % 2 == 1) & (t % 2 == 1), -30, -6))
        add += ring + _noise(rnd, (fh, fw), 4)
    elif kind == "plate":
        rows = np.array([rnd.randint(-5, 5) for _ in range(fh)], np.float32)
        add += rows[:, None] + _noise(rnd, (fh, fw), 3)
        if fw > 2 and fh > 2:
            add[0, :] += 28
            add[1, :] += 10
            add[-1, :] -= 30
            add[:, 0] += 12
            add[:, -1] -= 18
        if mat.get("rust"):
            for _ in range(max(1, fw * fh // 6)):
                j, i = rnd.randrange(fh), rnd.randrange(fw)
                out[j, i, :3] = (150 + rnd.randint(-15, 15), 82 + rnd.randint(-10, 10), 44)
        if mat.get("rivets") and fw >= 4 and fh >= 4:
            for (a, b) in ((1, 1), (1, fw - 2), (fh - 2, 1), (fh - 2, fw - 2)):
                add[a, b] += 45
    elif kind == "gold":
        add += np.where(((s + t) % 5) == 0, 40, 0) + _noise(rnd, (fh, fw), 6)
        if fh > 1:
            add[0, :] += 25
            add[-1, :] -= 35
    elif kind == "stripes":
        period = mat.get("period", 2)
        c2 = np.array(mat["c2"], np.float32)
        band = (t // period) % 2 == 1
        out[band, :3] = c2
        add += _noise(rnd, (fh, fw), 4) - ((s + t) % 2) * 3
    elif kind == "scale":
        row = t // 3
        off = (row % 2) * 1
        ps = (s + off) % 2
        pt = t % 3
        add += np.where(pt == 0, 30, np.where(pt == 2, -32, 0)) + np.where(ps == 1, -10, 6) + _noise(rnd, (fh, fw), 3)
    elif kind == "lamellar":  # laminillas verticales atadas con tiras de cuero cada 4 filas
        lace = np.array(mat.get("lace", (96, 64, 40)), np.float32)
        add += np.where(s % 2 == 1, -14, 8) + np.where(t % 4 == 0, 20, 0) + _noise(rnd, (fh, fw), 3)
        rows = (t % 4) == 3
        out[rows, :3] = lace
    elif kind == "studded":
        add += _noise(rnd, (fh, fw), 6)
        stud = ((s % 3) == 1) & ((t % 3) == 1)
        out[stud, :3] = np.array(mat.get("stud", (170, 170, 175)), np.float32)
        add[stud] += np.array([rnd.randint(-10, 20) for _ in range(int(stud.sum()))], np.float32)
        if fw > 2 and fh > 2:
            add[-1, :] -= 16
    elif kind == "horn":
        g = t / max(1, fh - 1) if side else np.zeros_like(t, dtype=np.float32)
        add += -40 * g + np.where(t % 3 == 0, -14, 0) + _noise(rnd, (fh, fw), 4)
    elif kind == "feather":
        mid = fw // 2
        add += np.where(s == mid, -40, 0) + np.where((s + t) % 3 == 0, 14, 0) + _noise(rnd, (fh, fw), 6)
    elif kind == "rope":
        add += np.where(((s + t) % 3) == 0, -24, 10) + _noise(rnd, (fh, fw), 4)
    elif kind == "flat":
        add += _noise(rnd, (fh, fw), mat.get("noise", 3))
    else:
        raise ValueError(kind)
    out[..., :3] = clamp(out[..., :3] + add[..., None])
    return out


def _color(ref, materials):
    if isinstance(ref, str):
        return np.array(materials[ref]["c"], np.float32)
    return np.array(ref, np.float32)


def _faces(spec):
    return "NSEWUD" if spec == "all" else ("NSEW" if spec == "sides" else spec)


def decal(img, regions, d, materials, rnd):
    """Un detalle encima de lo pintado. regions: cara -> (x, y, ancho, alto) en la textura."""
    kind = d[0]
    if kind == "band":  # franja horizontal en esas caras (filas t0..t1), de un color o del patrón de un material
        _, faces, t0, t1, ref = d
        for f in _faces(faces):
            if f not in regions:
                continue
            x, y, fw, fh = regions[f]
            a, b = max(0, t0), min(fh, t1)
            if a >= b:
                continue
            if isinstance(ref, str) and ref in materials:
                img[y + a:y + b, x:x + fw] = pattern(materials[ref], fw, b - a, f, rnd)
            else:
                img[y + a:y + b, x:x + fw, :3] = clamp(_color(ref, materials) + _noise(rnd, (b - a, fw), 4)[..., None])
                img[y + a:y + b, x:x + fw, 3] = 255
    elif kind == "vband":  # franja vertical (columnas s0..s1)
        _, faces, s0, s1, ref = d
        for f in _faces(faces):
            if f not in regions:
                continue
            x, y, fw, fh = regions[f]
            a, b = max(0, s0), min(fw, s1)
            if a >= b:
                continue
            if isinstance(ref, str) and ref in materials:
                img[y:y + fh, x + a:x + b] = pattern(materials[ref], b - a, fh, f, rnd)
            else:
                img[y:y + fh, x + a:x + b, :3] = clamp(_color(ref, materials) + _noise(rnd, (fh, b - a), 4)[..., None])
                img[y:y + fh, x + a:x + b, 3] = 255
    elif kind == "rect":
        _, face, s0, t0, w, h, ref = d
        x, y, fw, fh = regions[face]
        if s0 < 0:
            s0 = fw + s0
        img[y + t0:y + t0 + h, x + s0:x + s0 + w, :3] = clamp(_color(ref, materials) + _noise(rnd, (h, w), 4)[..., None])
        img[y + t0:y + t0 + h, x + s0:x + s0 + w, 3] = 255
    elif kind == "hole":
        _, face, s0, t0, w, h = d
        x, y, fw, fh = regions[face]
        img[y + t0:y + t0 + h, x + s0:x + s0 + w, 3] = 0
    elif kind == "px":
        _, face, pts = d
        x, y, fw, fh = regions[face]
        for s, t, ref in pts:
            img[y + t, x + s, :3] = _color(ref, materials)
            img[y + t, x + s, 3] = 255
    elif kind == "sprite":
        _, face, name, s0, t0, ref = d
        x, y, fw, fh = regions[face]
        if s0 == "c":
            s0 = (fw - len(SPRITES[name][0])) // 2
        col = _color(ref, materials)
        for j, row in enumerate(SPRITES[name]):
            for i, ch in enumerate(row):
                if ch == "1" and 0 <= t0 + j < fh and 0 <= s0 + i < fw:
                    img[y + t0 + j, x + s0 + i, :3] = clamp(col + rnd.randint(-6, 6))
                    img[y + t0 + j, x + s0 + i, 3] = 255
    elif kind == "buttons":
        _, face, s, t0, t1, step, ref = d
        x, y, fw, fh = regions[face]
        if s == "c":
            s = fw // 2
        for t in range(t0, min(fh, t1), step):
            img[y + t, x + s, :3] = _color(ref, materials)
            img[y + t, x + s, 3] = 255
    elif kind == "trim":  # ribete alrededor de la cara
        _, faces, ref = d
        for f in _faces(faces):
            if f not in regions:
                continue
            x, y, fw, fh = regions[f]
            col = _color(ref, materials)
            for (a, b) in [(y, slice(x, x + fw)), (y + fh - 1, slice(x, x + fw))]:
                img[a, b, :3] = clamp(col + rnd.randint(-5, 5))
                img[a, b, 3] = 255
            img[y:y + fh, x, :3] = col
            img[y:y + fh, x + fw - 1, :3] = col
            img[y:y + fh, x, 3] = 255
            img[y:y + fh, x + fw - 1, 3] = 255
    elif kind == "fringe":  # borde de abajo deshilachado (transparente a trozos)
        _, faces, depth = d
        for f in _faces(faces):
            if f not in regions:
                continue
            x, y, fw, fh = regions[f]
            for i in range(fw):
                k = rnd.randint(0, depth)
                if k:
                    img[y + fh - k:y + fh, x + i, 3] = 0
    elif kind == "diag":  # tira en diagonal (bandolera) en una cara: de (s0, 0) bajando hacia un lado
        _, faces, s0, slope, width, ref = d
        col = _color(ref, materials)
        for face in _faces(faces):
            if face not in regions:
                continue
            x, y, fw, fh = regions[face]
            for t in range(fh):
                for k in range(width):
                    s = int(round(s0 + t * slope)) % max(1, fw) + k if abs(slope) >= 1 else int(round(s0 + t * slope)) + k
                    if 0 <= s < fw:
                        img[y + t, x + s, :3] = clamp(col + rnd.randint(-6, 6) - (12 if k == width - 1 else 0))
                        img[y + t, x + s, 3] = 255
    elif kind == "shade":  # oscurece (o aclara) un poco esas caras
        _, faces, amount = d
        for f in _faces(faces):
            if f not in regions:
                continue
            x, y, fw, fh = regions[f]
            img[y:y + fh, x:x + fw, :3] = clamp(img[y:y + fh, x:x + fw, :3] + amount)
    else:
        raise ValueError(kind)


def box_regions(u, v, w, h, d):
    """Dónde cae cada cara en la textura (UV de caja de Minecraft)."""
    return {
        "U": (u + d, v, w, d),
        "D": (u + d + w, v, w, d),
        "W": (u, v + d, d, h),
        "N": (u + d, v + d, w, h),
        "E": (u + d + w, v + d, d, h),
        "S": (u + 2 * d + w, v + d, w, h),
    }


def paint_box(img, uv, box, materials, rnd):
    x, y, z, w, h, d = box.size()
    regions = box_regions(uv[0], uv[1], w, h, d)
    mats = box.face_materials
    for f, (rx, ry, fw, fh) in regions.items():
        if fw <= 0 or fh <= 0:
            continue
        mat = materials[mats.get(f, box.mat)]
        img[ry:ry + fh, rx:rx + fw] = pattern(mat, fw, fh, f, rnd)
    for dd in box.decals:
        decal(img, regions, dd, materials, rnd)


def new_rng(seed):
    return random.Random(seed)
