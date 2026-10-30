"""Vistas previas de las máquinas de asedio (sin abrir el juego), con las mismas cuentas que SiegeEngineRenderer."""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from ew_musket_preview import RX, RY, RZ, S, T, D, cube_faces, draw, grid, label, perspective  # noqa: E402

COL = {"dark": (86, 58, 34), "wood": (156, 114, 68), "hide": (132, 92, 60), "iron": (96, 98, 104), "rope": (156, 124, 82),
       "stone": (128, 128, 130), "hay": (200, 170, 76), "cord": (230, 222, 200)}

# --- lo mismo que SiegeEngineRenderer (Java) ---
CATAPULT_COCKED, CATAPULT_FIRED, CATAPULT_SWING = -60.0, 32.0, 3
TREBUCHET_COCKED, TREBUCHET_FIRED, TREBUCHET_SWING = -140.0, 40.0, 8


def lerp(a, b, t):
    return a + (b - a) * t


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def arm_angle(ticks, total, cocked, fired, swing):
    """ticks: tics desde el disparo (None = cargada)."""
    if ticks is None:
        return cocked
    if ticks < swing:
        return lerp(cocked, fired, ticks / swing)
    return lerp(fired, cocked, (ticks - swing) / (total - swing))


def sling_alpha(ticks, total):
    """Ángulo de la honda en el mundo (0 = hacia arriba, 90 = hacia delante, 180 = colgando)."""
    if ticks is None:
        return 110.0
    if ticks < TREBUCHET_SWING:
        return lerp(110.0, 60.0, smooth(ticks / TREBUCHET_SWING))
    u = (ticks - TREBUCHET_SWING) / (total - TREBUCHET_SWING)
    if u < 0.15:
        return lerp(60.0, 180.0, smooth(u / 0.15))
    if u < 0.85:
        return 180.0
    return lerp(180.0, 110.0, smooth((u - 0.85) / 0.15))


def part_matrix(p):
    return T(*(v / 16.0 for v in p.pivot)) @ RZ(D(p.rot[2])) @ RY(D(p.rot[1])) @ RX(D(p.rot[0]))


def faces_of(part, m, overrides, hidden, out):
    if part.name in hidden:
        return
    pm = m @ (overrides[part.name] if part.name in overrides else part_matrix(part))
    for mat, *box in part.cubes:
        for f in cube_faces(pm, *box):
            out.append((f, COL[mat]))
    for child in part.children:
        faces_of(child, pm, overrides, hidden, out)


def pose(kind, ticks, total, rel_yaw=0.0, elevation=0.0):
    """Lo que mueve el renderizador: matrices de las piezas animadas y piezas ocultas."""
    o, hidden, extra = {}, set(), []
    ready = ticks is None
    if kind == "catapult":
        a = arm_angle(ticks, total, CATAPULT_COCKED, CATAPULT_FIRED, CATAPULT_SWING)
        o["arm"] = T(0, 12.5 / 16, -3.5 / 16) @ RX(D(a))
        if not ready:
            hidden.add("stone")
    elif kind == "trebuchet":
        a = arm_angle(ticks, total, TREBUCHET_COCKED, TREBUCHET_FIRED, TREBUCHET_SWING)
        o["arm"] = T(0, 96 / 16, 0) @ RX(D(a))
        o["weight"] = T(0, -26 / 16, 0) @ RX(D(-a))
        alpha = sling_alpha(ticks, total)
        o["sling"] = T(0, 104 / 16, 0) @ RX(D(alpha + 180.0 - a))
        if not ready:
            hidden.add("stone")
    elif kind == "ram":
        strike = 0.0 if ticks is None else max(0.0, 12 - ticks)
        theta = -35.0 * math.sqrt(strike / 12.0)
        o["rope_a"] = T(0, 43 / 16, -10 / 16) @ RX(D(theta))
        o["rope_b"] = T(0, 43 / 16, 10 / 16) @ RX(D(theta))
        rad = math.radians(theta)
        o["log"] = T(0, 19 * (1 - math.cos(rad)) / 16, -19 * math.sin(rad) / 16)
    elif kind == "ballista":
        r = 1.0 if ready else min(1.0, ticks / total)
        nut = lerp(9.6, -9.0, r)
        psi = lerp(12.0, -28.0, r)
        turret = T(0, 16 / 16, 0) @ RY(D(-rel_yaw)) @ RX(D(-elevation))
        o["turret"] = turret
        o["arm_l"] = T(-5 / 16, 2 / 16, 15.5 / 16) @ RY(D(psi))
        o["arm_r"] = T(5 / 16, 2 / 16, 15.5 / 16) @ RY(D(-psi))
        o["nut"] = T(0, 0, nut / 16)
        o["bolt"] = T(0, 0, nut / 16)
        if not ready:
            hidden.add("bolt")
        hidden.add("cord")
        # cuerdas: de la punta de cada brazo a la nuez
        for side in (-1, 1):
            p = math.radians(psi)
            tip = (side * (5 + 17 * math.cos(p)), 2.0, 15.5 + 17 * math.sin(p) * 1.0)
            nut_pt = (0.0, 5.0, nut)
            d = [nut_pt[i] - tip[i] for i in range(3)]
            length = math.sqrt(sum(v * v for v in d))
            yaw = math.atan2(d[0], d[2])
            pitch = -math.asin(d[1] / length)
            extra.append(turret @ T(*(v / 16 for v in tip)) @ RY(yaw) @ RX(pitch) @ S(1, 1, length))
    return o, hidden, extra


def player_faces(seat, out):
    """Un jugador sentado: el culo 0,1 por encima del punto de asiento y las piernas hacia delante."""
    sx, sy, sz = seat
    hip = sy + 0.1
    skin, shirt, pants = (196, 150, 118), (60, 90, 140), (52, 50, 70)
    boxes = [
        (shirt, sx - 0.25, hip, sz - 0.125, 0.5, 0.75, 0.25),
        (skin, sx - 0.25, hip + 0.75, sz - 0.25, 0.5, 0.5, 0.5),
        (pants, sx - 0.25, hip - 0.25, sz - 0.125, 0.25, 0.25, 0.8),
        (pants, sx, hip - 0.25, sz - 0.125, 0.25, 0.25, 0.8),
        (shirt, sx - 0.5, hip + 0.35, sz - 0.1, 0.25, 0.25, 0.55),
        (shirt, sx + 0.25, hip + 0.35, sz - 0.1, 0.25, 0.25, 0.55),
    ]
    for col, x, y, z, w, h, d in boxes:
        for f in cube_faces(np.eye(4), x * 16, y * 16, z * 16, w * 16, h * 16, d * 16):
            out.append((f, col))


def view(eye, target):
    eye = np.array(eye, dtype=float)
    fwd = np.array(target, dtype=float) - eye
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    v = np.eye(4)
    v[0, :3], v[1, :3], v[2, :3] = right, up, -fwd
    return v @ T(*(-eye))


CAMS = {"catapult": ((-4.2, 3.0, -4.6), (0, 1.0, 0)), "ballista": ((-3.0, 2.2, -3.4), (0, 0.8, 0)),
        "trebuchet": ((-11.0, 7.5, -12.0), (0, 4.2, 0)), "ram": ((-4.4, 3.0, -4.6), (0, 1.2, 0))}
TOTAL = {"catapult": 70, "ballista": 40, "trebuchet": 140, "ram": 30}
TITLES = {"catapult": "Catapulta", "ballista": "Balista", "trebuchet": "Trebuchet", "ram": "Ariete"}


def frame(models, seats, kind, ticks, title, w=640, h=480):
    faces = []
    o, hidden, extra = pose(kind, ticks, TOTAL[kind], 0.0, 8.0 if kind == "ballista" else 0.0)
    faces_of(models[kind], np.eye(4), o, hidden, faces)
    for m in extra:
        for f in cube_faces(m, -0.4, -0.4, 0, 0.8, 0.8, 1):
            faces.append((f, COL["cord"]))
    player_faces(seats[kind], faces)
    # suelo
    g = 8 if kind == "trebuchet" else 4
    eye, target = CAMS[kind]
    img = Image.new("RGB", (w, h), (170, 200, 160))
    from PIL import ImageDraw
    proj = perspective(view(eye, target), w, h, 50)
    ground = []
    for gx in range(-g, g):
        for gz in range(-g, g):
            quad = np.array([[gx, 0, gz], [gx + 1, 0, gz], [gx + 1, 0, gz + 1], [gx, 0, gz + 1]], dtype=float)
            ground.append((quad, (124, 160, 98) if (gx + gz) % 2 == 0 else (116, 152, 92)))
    img = draw(ground, proj, (w, h), (170, 200, 160), img)
    img = draw(faces, proj, (w, h), (170, 200, 160), img)
    return label(img, title)


def render_all(models, seats, out):
    os.makedirs(out, exist_ok=True)
    frames = []
    for kind in ("catapult", "ballista", "trebuchet", "ram"):
        frames.append(frame(models, seats, kind, None, TITLES[kind] + ": lista"))
        mid = {"catapult": 2, "ballista": 3, "trebuchet": 5, "ram": 1}[kind]
        frames.append(frame(models, seats, kind, mid, TITLES[kind] + ": disparando"))
    grid(frames, 2).save(os.path.join(out, "siege_models.png"))
    # el trebuchet en toda su altura, de lado, al disparar
    tre = [frame(models, seats, "trebuchet", t, "Trebuchet: tic %d" % t, 480, 480) for t in (0, 4, 8, 40)]
    grid(tre, 4).save(os.path.join(out, "trebuchet_swing.png"))
