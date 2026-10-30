"""Vistas previas de la Caja de cultivos en las manos (sin abrir el juego): las mismas cuentas que CrateCarry.java.

Primera persona: la caja delante, abajo, con las dos manos en sus asas, al cogerla del suelo (sube) y andando (se
mece). Tercera persona: los brazos adelante, la caja entre las manos, al cogerla (agachado) y andando.

Uso: python3 tools/ew_crate_preview.py carpeta
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from ew_musket_preview import RX, RY, RZ, S, T, D, cube_faces, draw, grid, label, perspective  # noqa: E402

# ------------------------------------------------------------------ lo mismo que CrateCarry.java
# primera persona (espacio de la vista: x derecha, y arriba, -z adelante; en bloques)
FP_SIZE = 0.5
FP_REST = (0.0, -0.46, -0.92, 24.0)    # centro de la caja y cuánto se inclina (grados, la tapa hacia ti)
FP_LOW = (0.0, -1.25, -0.80, 50.0)     # al empezar a cogerla: abajo del todo
FP_HAND_Y = 0.09                       # altura de las manos respecto al centro de la caja (en las asas)
FP_HAND_IN = 0.3 / 16                  # cuánto se meten las manos en la caja (agarrando)
FP_SHOULDER = (0.70, -0.62, 0.36)      # hacia dónde va el brazo desde la mano (abajo, afuera y atrás)
FP_ARM_ROLL = 0.0
FP_WALK_Y, FP_WALK_X, FP_WALK_ROLL = 0.022, 0.012, 2.2
# tercera persona (espacio del modelo: y hacia abajo, -z adelante; en bloques)
TP_SIZE = 0.56
TP_PITCH = -0.95                       # brazos adelante y un poco abajo
TP_PITCH_LOW = -0.30                   # al cogerla del suelo
TP_YAW_OUT = 0.06                      # un poco abiertos (la caja es más ancha que el pecho)
TP_GRIP = 9.5                          # dónde agarra la mano (píxeles desde el hombro)
TP_WALK = 0.05
TP_HANDLE = 3.0 / 16                   # las asas están 3 píxeles (de la caja) por encima de su centro
LIFT_TICKS = 12.0
EQUIP_TICKS = 5.0
# el modelo de la caja (los mismos elementos que models/block/crop_crate.json, más las asas)
WOOD, DARK, RIM, STRAW, WHEAT = (180, 138, 82), (122, 86, 48), (194, 154, 98), (196, 170, 90), (222, 190, 70)


def crate_faces(m, fill=1.0):
    """m: del espacio del bloque (0..1) a la vista."""
    faces = []
    parts = [((0, 0, 0, 16, 2, 16), DARK), ((0, 2, 0, 16, 13, 2), WOOD), ((0, 2, 14, 16, 13, 2), WOOD),
             ((0, 2, 2, 2, 13, 12), WOOD), ((14, 2, 2, 2, 13, 12), WOOD),
             ((-1, 10, 5, 1, 2, 6), DARK), ((16, 10, 5, 1, 2, 6), DARK)]
    for box, col in parts:
        for f in cube_faces(m, *box):
            faces.append((f, col))
    if fill > 0:
        h = 2 + 10.5 * fill
        for f in cube_faces(m, 2, 2, 2, 12, h - 2 - 0.6, 12):
            faces.append((f, STRAW))
        for i in range(9):
            x, z = 2.5 + (i % 3) * 4, 2.5 + (i // 3) * 4
            for f in cube_faces(m, x, h - 0.6, z, 3, 0.6, 3):
                faces.append((f, WHEAT))
    return faces


def smooth(t):
    t = min(1.0, max(0.0, t))
    return t * t * (3 - 2 * t)


def ease_out(t):
    t = min(1.0, max(0.0, t))
    return 1 - (1 - t) ** 3


def lerp(a, b, t):
    return a + (b - a) * t


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


# ------------------------------------------------------------------ primera persona
def fp_crate_matrix(lift, walk_pos, walk_amp, equip):
    e = ease_out(lift)
    x, y, z, pitch = (lerp(a, b, e) for a, b in zip(FP_LOW, FP_REST))
    phase = walk_pos * 0.6662
    y += -FP_WALK_Y * walk_amp * abs(math.cos(phase)) - equip * 0.6
    x += FP_WALK_X * walk_amp * math.sin(phase)
    roll = FP_WALK_ROLL * walk_amp * math.sin(phase)
    return T(x, y, z) @ RX(D(pitch)) @ RZ(D(roll))


def fp_arm_matrix(crate, side):
    """side 1 = derecha. Del espacio en que se dibuja el brazo de vanilla (renderRightArm/LeftArm) a la vista."""
    hand = (crate @ np.array([side * (FP_SIZE / 2 + 2 / 16 - FP_HAND_IN), FP_HAND_Y, 0.0, 1.0]))[:3]
    down = unit(-np.array([side * FP_SHOULDER[0], FP_SHOULDER[1], FP_SHOULDER[2]]))   # del hombro a la mano
    inward = np.array([-1.0, 0.0, 0.0])
    b = unit(inward - down * float(inward @ down))
    c = np.cross(b, down)
    r = np.eye(4)
    r[:3, 0], r[:3, 1], r[:3, 2] = b, down, c
    end = np.array([-6 * side / 16, 12 / 16, 0.0])
    return T(*hand) @ r @ RY(D(FP_ARM_ROLL * side)) @ T(*(-end))


def arm_box(side):
    return (-8, 0, -2, 4, 12, 4) if side > 0 else (4, 0, -2, 4, 12, 4)


def fp_frame(title, lift=1.0, walk_pos=0.0, walk_amp=0.0, equip=0.0, fill=1.0, w=640, h=360):
    crate = fp_crate_matrix(lift, walk_pos, walk_amp, equip)
    faces = crate_faces(crate @ S(FP_SIZE) @ T(-0.5, -0.5, -0.5), fill)
    for side in (1, -1):
        m = fp_arm_matrix(crate, side)
        for f in cube_faces(m, *arm_box(side)):
            faces.append((f, (196, 150, 118)))
        # la manga (la primera mitad del brazo)
        x, y, z, bw, bh, bd = arm_box(side)
        for f in cube_faces(m, x - 0.25, y - 0.25, z - 0.25, bw + 0.5, 6.5, bd + 0.5):
            faces.append((f, (60, 90, 140)))
    img = draw(faces, perspective(np.eye(4), w, h, 70), (w, h), (150, 190, 230))
    return label(img, title)


# ------------------------------------------------------------------ tercera persona
ENTITY = RY(math.pi) @ S(-1, -1, 1) @ S(0.9375) @ T(0, -1.501, 0)


def part(pivot, rot):
    return T(*(c / 16.0 for c in pivot)) @ RZ(rot[2]) @ RY(rot[1]) @ RX(rot[0])


def tp_arms(lift, walk_pos, walk_amp, sneak):
    pitch = lerp(TP_PITCH_LOW, TP_PITCH, smooth(lift)) + (0.4 if sneak else 0.0)
    pitch += TP_WALK * walk_amp * math.cos(walk_pos * 0.6662 * 2)
    return (pitch, TP_YAW_OUT, 0.0), (pitch, -TP_YAW_OUT, 0.0)


def tp_frame(title, lift=1.0, walk_pos=0.0, walk_amp=0.0, sneak=False, eye=(-2.2, 1.7, 2.6), w=520, h=420, fill=1.0):
    right, left = tp_arms(lift, walk_pos, walk_amp, sneak)
    body_pitch = 0.5 if sneak else 0.0
    ys = 3.2 if sneak else 0.0
    arm_y = 5.2 if sneak else 2.0
    leg_z = 4.0 if sneak else 0.0
    rp, lp = (-5, arm_y, 0), (5, arm_y, 0)
    skin, shirt, pants = (196, 150, 118), (60, 90, 140), (52, 50, 70)
    swing = math.cos(walk_pos * 0.6662) * 1.4 * walk_amp
    boxes = [
        ((0, ys, 0), (0, 0, 0), (-4, -8, -4, 8, 8, 8), skin),
        ((0, ys, 0), (body_pitch, 0, 0), (-4, 0, -2, 8, 12, 4), shirt),
        (rp, right, (-3, -2, -2, 4, 12, 4), shirt),
        (lp, left, (-1, -2, -2, 4, 12, 4), shirt),
        ((-1.9, 12.2 if sneak else 12, leg_z), (swing, 0, 0), (-2, 0, -2, 4, 12, 4), pants),
        ((1.9, 12.2 if sneak else 12, leg_z), (-swing, 0, 0), (-2, 0, -2, 4, 12, 4), pants),
    ]
    faces = []
    for pivot, rot, box, col in boxes:
        for f in cube_faces(ENTITY @ part(pivot, rot), *box):
            faces.append((f, col))
    hr = (part(rp, right) @ np.array([-1 / 16, TP_GRIP / 16, 0, 1]))[:3]
    hl = (part(lp, left) @ np.array([1 / 16, TP_GRIP / 16, 0, 1]))[:3]
    mid = (hr + hl) / 2 + np.array([0, TP_HANDLE * TP_SIZE, 0])
    crate = ENTITY @ T(*mid) @ RZ(math.pi) @ S(TP_SIZE) @ T(-0.5, -0.5, -0.5)
    faces += crate_faces(crate, fill)
    view = look(eye, (0.0, 1.0, 0.3))
    img = draw(faces, perspective(view, w, h, 50), (w, h), (170, 200, 160))
    return label(img, title), hr, hl


def look(eye, target):
    eye = np.array(eye, float)
    fwd = unit(np.array(target, float) - eye)
    right = unit(np.cross(fwd, [0, 1, 0]))
    up = np.cross(right, fwd)
    view = np.eye(4)
    view[0, :3], view[1, :3], view[2, :3] = right, up, -fwd
    return view @ T(*(-eye))


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    fp = [fp_frame("1ª: llevándola"), fp_frame("1ª: andando (paso)", walk_pos=2.36, walk_amp=1.0),
          fp_frame("1ª: andando (otro pie)", walk_pos=7.07, walk_amp=1.0), fp_frame("1ª: vacía", fill=0.0),
          fp_frame("1ª: cogiéndola 0 %", lift=0.0), fp_frame("1ª: cogiéndola 35 %", lift=0.35),
          fp_frame("1ª: cogiéndola 70 %", lift=0.7), fp_frame("1ª: cambiando de mano", equip=0.5)]
    grid(fp, 2).save(os.path.join(out, "crate_fp.png"))
    frames = []
    for title, kw in [("3ª: llevándola", {}), ("3ª: de lado", {"eye": (-3.0, 1.3, 0.2)}),
                      ("3ª: andando", {"walk_pos": 2.36, "walk_amp": 1.0}),
                      ("3ª: cogiéndola (agachado) 0 %", {"lift": 0.0, "sneak": True}),
                      ("3ª: cogiéndola 50 %", {"lift": 0.5, "sneak": True}),
                      ("3ª: agachado, ya cogida", {"lift": 1.0, "sneak": True})]:
        img, hr, hl = tp_frame(title, **kw)
        frames.append(img)
        if not kw:
            print(f"manos (modelo, px): derecha {np.round(hr * 16, 2)}, izquierda {np.round(hl * 16, 2)}")
    grid(frames, 3).save(os.path.join(out, "crate_tp.png"))
    print("vistas en", out)


if __name__ == "__main__":
    main()
