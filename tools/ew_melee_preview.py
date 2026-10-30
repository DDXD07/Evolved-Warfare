"""Vistas previas de las armas cuerpo a cuerpo (sin abrir el juego): las mismas cuentas que MeleeWeaponRenderer y el
mixin de los brazos (BipedEntityModelMixin). Lo usa tools/ew_melee.py cuando se le pasa una carpeta de vistas."""
import math
import os

import numpy as np
from PIL import Image

import ew_melee as M
from ew_musket_preview import RX, RY, RZ, S, T, D, cube_faces, draw, grid, label, ortho, perspective, player_faces

TP_DISPLAY = M.DISPLAY["thirdperson_righthand"]
FP_DISPLAY = M.DISPLAY["firstperson_righthand"]
EQUIP = (0.56, -0.52, -0.72)


def display(d, left=False):
    tx, ty, tz = (v / 16.0 for v in d["translation"])
    rx, ry, rz = d["rotation"]
    s = d["scale"][0]
    if left:
        tx, ry, rz = -tx, -ry, -rz
    return T(tx, ty, tz) @ RX(D(rx)) @ RY(D(ry)) @ RZ(D(rz)) @ S(s)


def colors(material):
    return M.PALETTES[material]


def rotate_to(a, b):
    """Rotación que lleva el vector a al b (los dos unitarios)."""
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    v = np.cross(a, b)
    cth = float(a @ b)
    if np.linalg.norm(v) < 1e-9:
        return np.eye(4) if cth > 0 else RX(math.pi)
    k = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    r = np.eye(3) + k + k @ k * (1 / (1 + cth))
    out = np.eye(4)
    out[:3, :3] = r
    return out


def chain_dir(local, space_down, chain):
    """Hacia dónde va la cadena del mangual en el marco del arma (lo mismo que MeleeWeaponRenderer.chainDirection)."""
    inv = np.linalg.inv(local[:3, :3])
    down = inv @ np.asarray(space_down, float)
    down /= np.linalg.norm(down)
    along = np.array([0.0, 1.0, 0.0])          # estirada: sigue al mango
    d = down * (1 - chain) + along * chain
    n = np.linalg.norm(d)
    return d / n if n > 1e-6 else down


def weapon_faces(name, material, item, space_down=(0, -1, 0), chain=0.0):
    """item: matriz del espacio del objeto (bloques) al mundo de la vista previa."""
    pal = colors(material)
    faces = []
    w = item @ T(*(v / 16.0 for v in M.GRIP)) @ RZ(D(M.DIAGONAL))
    for mat, *box in M.SHAPES[name]():
        for f in cube_faces(w, *box):
            faces.append((f, pal[mat]))
    if name == "flail":
        anchor = w @ T(0, M.CHAIN_ANCHOR / 16.0, 0)
        d = chain_dir(anchor, space_down, chain)
        cm = anchor @ rotate_to((0, -1, 0), d)
        for mat, *box in M.flail_chain():
            for f in cube_faces(cm, *box):
                faces.append((f, pal[mat]))
    return faces


# ------------------------------------------------------------------ primera persona
def fp_item_matrix(fp):
    """Espacio del objeto -> vista: la mano de vanilla (sin su golpe) y el nuestro alrededor de la empuñadura."""
    dx, dy, dz, pitch, yaw, roll, chain = fp
    disp = display(FP_DISPLAY) @ T(-0.5, -0.5, -0.5)
    g = (disp @ np.array([*(v / 16.0 for v in M.GRIP), 1.0]))[:3]
    anim = T(*g) @ T(dx, dy, dz) @ RY(D(yaw)) @ RX(D(pitch)) @ RZ(D(roll)) @ T(*(-g))
    return T(*EQUIP) @ anim @ disp


def fp_frame(name, material, p, title, w=520, h=300):
    fp = M.sample(M.ANIMATIONS[name]["fp"], p)
    item = fp_item_matrix(fp)
    faces = weapon_faces(name, material, item, space_down=(0, -1, 0), chain=fp[6])
    img = draw(faces, perspective(np.eye(4), w, h, 70), (w, h), (150, 190, 230))
    return label(img, title)


# ------------------------------------------------------------------ tercera persona
ENTITY = RY(math.pi) @ S(-1, -1, 1) @ S(0.9375) @ T(0, -1.501, 0)


def arm_matrix(pivot, rot):
    return ENTITY @ T(*(c / 16.0 for c in pivot)) @ RZ(rot[2]) @ RY(rot[1]) @ RX(rot[0])


def tp_pose(name, p):
    a = M.ANIMATIONS[name]
    tp = M.sample(a["tp"], p)
    arm_pitch, arm_yaw, arm_roll, off_pitch, off_yaw, off_roll, body_yaw, tilt, chain = tp
    by = D(body_yaw)
    main = (D(arm_pitch), D(arm_yaw) + by, D(arm_roll))
    off = (D(off_pitch), D(off_yaw) + by, D(off_roll)) if a["two_handed"] else (0.0, by, 0.0)
    pivots = ((-math.cos(by) * 5, 2, math.sin(by) * 5), (math.cos(by) * 5, 2, -math.sin(by) * 5))
    return main, off, by, pivots, tilt, chain


def tp_faces(name, material, p):
    main, off, by, pivots, tilt, chain = tp_pose(name, p)
    faces = []
    skin, shirt, pants = (196, 150, 118), (60, 90, 140), (52, 50, 70)
    boxes = [
        ((0, 0, 0), (0, by, 0), (-4, -8, -4, 8, 8, 8), skin),
        ((0, 0, 0), (0, by, 0), (-4, 0, -2, 8, 12, 4), shirt),
        (pivots[0], main, (-3, -2, -2, 4, 12, 4), shirt),
        (pivots[1], off, (-1, -2, -2, 4, 12, 4), shirt),
        ((-1.9, 12, 0), (0, 0, 0), (-2, 0, -2, 4, 12, 4), pants),
        ((1.9, 12, 0), (0, 0, 0), (-2, 0, -2, 4, 12, 4), pants),
    ]
    for pivot, rot, box, col in boxes:
        m = arm_matrix(pivot, rot)
        for f in cube_faces(m, *box):
            faces.append((f, col))
    hand = arm_matrix(pivots[0], main) @ RX(D(-90)) @ RY(D(180)) @ T(1 / 16, 0.125, -0.625)
    disp = display(TP_DISPLAY) @ T(-0.5, -0.5, -0.5)
    g = (disp @ np.array([*(v / 16.0 for v in M.GRIP), 1.0]))[:3]
    tilt_m = T(*g) @ RX(D(tilt)) @ T(*(-g))
    faces += weapon_faces(name, material, hand @ tilt_m @ disp, space_down=(0, -1, 0), chain=chain)
    return faces


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


def tp_frame(name, material, p, title, w=520, h=420):
    faces = tp_faces(name, material, p)
    ground = []
    for gx in range(-3, 3):
        for gz in range(-3, 4):
            q = np.array([[gx, 0, gz], [gx + 1, 0, gz], [gx + 1, 0, gz + 1], [gx, 0, gz + 1]], float)
            ground.append((q, (124, 160, 98) if (gx + gz) % 2 == 0 else (116, 152, 92)))
    proj = perspective(look((-2.7, 1.95, 2.9), (0.0, 1.3, 0.45)), w, h, 58)
    img = draw(ground, proj, (w, h), (170, 200, 160))
    img = draw(faces, proj, (w, h), (170, 200, 160), img)
    return label(img, title)


# ------------------------------------------------------------------ todas
TITLES = {"dagger": "Daga", "spear": "Lanza", "longsword": "Mandoble", "halberd": "Alabarda", "warhammer": "Martillo",
          "flail": "Mangual", "battle_axe": "Hacha de batalla"}


def overview(out):
    frames = []
    for name in M.WEAPONS:
        for material in M.MATERIALS:
            # centrado en el arma (las largas se salen del cubo del objeto)
            ys = [b[2] + b[5] for b in M.SHAPES[name]()] + [b[2] for b in M.SHAPES[name]()]
            mid = (min(ys) + max(ys)) / 2 / 16.0
            k = math.radians(M.DIAGONAL)
            center = np.array(M.GRIP) / 16.0 + np.array([-math.sin(k) * -mid, math.cos(k) * mid, 0]) * 1.0
            center = np.array(M.GRIP) / 16.0 + mid * np.array([-math.sin(k), math.cos(k), 0])
            scale = 300 / max(1.2, (max(ys) - min(ys)) / 16.0 * 0.8)
            faces = weapon_faces(name, material, RY(D(-25)) @ RX(D(12)) @ T(*(-center)))
            img = draw(faces, ortho(scale * 0.55, 300, 300), (300, 300), (60, 62, 70))
            frames.append(label(img, "%s (%s)" % (TITLES[name], material)))
    grid(frames, 3).save(os.path.join(out, "melee_models.png"))
    idle = [tp_frame(name, "iron", 0.0, TITLES[name] + ": en la mano", 360, 360) for name in M.WEAPONS]
    idle += [fp_frame(name, "diamond", 0.0, TITLES[name] + ": 1ª persona", 360, 203) for name in M.WEAPONS]
    grid(idle, 7).save(os.path.join(out, "melee_in_hand.png"))


def attack(name, out):
    keys = [k[0] for k in M.ANIMATIONS[name]["fp"]][1:-1]
    ps = [0.0] + keys + [0.8]
    ps = ps[:6]
    fp = [fp_frame(name, "iron", p, "%s 1ª · %d %%" % (TITLES[name], round(p * 100))) for p in ps]
    tp = [tp_frame(name, "iron", p, "%s 3ª · %d %%" % (TITLES[name], round(p * 100)), 520, 420) for p in ps]
    top = grid(fp, len(fp))
    bottom = grid(tp, len(tp))
    img = Image.new("RGB", (max(top.width, bottom.width), top.height + bottom.height + 8), (30, 30, 34))
    img.paste(top, (0, 0))
    img.paste(bottom, (0, top.height + 8))
    img.save(os.path.join(out, "melee_attack_%s.png" % name))


def render_all(out):
    os.makedirs(out, exist_ok=True)
    overview(out)
    for name in M.WEAPONS:
        attack(name, out)
