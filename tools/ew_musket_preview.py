"""Vistas previas del mosquete (sin abrir el juego): repite las mismas cuentas que el renderizador del mod.

Lo usa tools/ew_musket.py cuando se le pasa una carpeta de vistas.
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

# --- lo mismo que MusketItemRenderer (Java) ---
RELOAD_TICKS = 40
GRIP = (8.0, 8.6, 15.0)
HAMMER_DOWN, HAMMER_COCKED = -30.0, 25.0
FRIZZEN_OPEN, FRIZZEN_CLOSED = -55.0, 0.0
BORE = (8.0, 9.8)
ROD_HEAD = (8.0, 7.65, -7.3)
RELOAD_TILT_FP, RELOAD_YAW_FP, RELOAD_TILT_TP = 60.0, 12.0, 95.0

DISPLAY = json.load(open(os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/evolvedwarfare/models/item/musket.json")))["display"]


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def lerp(a, b, t):
    return a + (b - a) * t


def seg(p, a, b):
    return (p - a) / (b - a)


def state(reload, loaded, since_fire, first_person):
    """reload: progreso 0..1 o None; since_fire: ticks desde el disparo o None."""
    s = dict(tilt=0.0, yaw=0.0, back=0.0, hammer=HAMMER_COCKED if loaded else HAMMER_DOWN,
             frizzen=FRIZZEN_CLOSED if loaded else FRIZZEN_OPEN, ball=None, rod=None)
    if reload is not None:
        p = reload
        if p < 0.15:
            a = smooth(p / 0.15)
        elif p < 0.70:
            a = 1.0
        else:
            a = 1.0 - smooth(seg(p, 0.70, 0.82))
        s["tilt"] = (RELOAD_TILT_FP if first_person else RELOAD_TILT_TP) * a
        s["yaw"] = (RELOAD_YAW_FP if first_person else 0.0) * a
        s["hammer"] = HAMMER_DOWN if p < 0.85 else lerp(HAMMER_DOWN, HAMMER_COCKED, smooth(seg(p, 0.85, 1.0)))
        s["frizzen"] = lerp(FRIZZEN_OPEN, FRIZZEN_CLOSED, smooth(seg(p, 0.74, 0.82)))
        if 0.15 <= p < 0.42:
            s["ball"] = lerp(-5.0, 1.2, smooth(seg(p, 0.15, 0.42)))
        if 0.42 <= p < 0.70:
            if p < 0.48:
                d = lerp(0, 11, smooth(seg(p, 0.42, 0.48)))
            elif p < 0.55:
                d = lerp(11, 5, smooth(seg(p, 0.48, 0.55)))
            elif p < 0.62:
                d = lerp(5, 11, smooth(seg(p, 0.55, 0.62)))
            else:
                d = lerp(11, 0, smooth(seg(p, 0.62, 0.70)))
            s["rod"] = d
        s["hide_rod"] = 0.40 <= p < 0.72
    if since_fire is not None and reload is None:
        if not loaded and since_fire < 2.0:
            q = smooth(since_fire / 1.5)
            s["hammer"] = lerp(HAMMER_COCKED, HAMMER_DOWN, q)
            s["frizzen"] = lerp(FRIZZEN_CLOSED, FRIZZEN_OPEN, q)
        if since_fire < 5.0:
            k = (1.0 - since_fire / 5.0) ** 2
            s["tilt"] += (14.0 if first_person else 8.0) * k
            s["back"] = (1.2 if first_person else 0.6) * k
    return s


# --- matrices ---
def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def S(x, y=None, z=None):
    y = x if y is None else y
    z = x if z is None else z
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


def display(mode, left=False):
    d = DISPLAY[mode]
    tx, ty, tz = (v / 16.0 for v in d.get("translation", [0, 0, 0]))
    rx, ry, rz = d.get("rotation", [0, 0, 0])
    sx, sy, sz = d.get("scale", [1, 1, 1])
    if left:
        tx, ry, rz = -tx, -ry, -rz
    return T(tx, ty, tz) @ RX(D(rx)) @ RY(D(ry)) @ RZ(D(rz)) @ S(sx, sy, sz)


def gun_matrix(st):
    """Movimiento de todo el arma (en el espacio del objeto, en bloques), alrededor de la empuñadura."""
    g = [c / 16.0 for c in GRIP]
    return T(0, 0, st["back"] / 16.0) @ T(*g) @ RY(D(st["yaw"])) @ RX(D(st["tilt"])) @ T(-g[0], -g[1], -g[2])


def part_matrix(pivot, rot):
    return T(*(c / 16.0 for c in pivot)) @ RZ(D(rot[2])) @ RY(D(rot[1])) @ RX(D(rot[0]))


def cube_faces(m, x, y, z, w, h, d):
    v = np.array([[x + dx * w, y + dy * h, z + dz * d, 16.0] for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]) / 16.0
    v[:, 3] = 1.0
    p = (m @ v.T).T[:, :3]
    idx = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    return [p[list(f)] for f in idx]


COL = {"wood": (112, 64, 32), "iron": (132, 136, 142), "brass": (198, 156, 62), "dark": (30, 30, 33), "lead": (98, 101, 108)}


def gun_faces(parts, base, st):
    faces = []
    gm = base @ T(0, 0, 0) @ gun_matrix(st)
    for name, (pivot, rot, cubes) in parts.items():
        rot = list(rot)
        if name == "hammer":
            rot[0] = st["hammer"]
        if name == "frizzen":
            rot[0] = st["frizzen"]
        if name == "ball":
            if st["ball"] is None:
                continue
            pm = gm @ T(0, 0, st["ball"] / 16.0)
        elif name == "ramrod":
            if st.get("hide_rod") and st["rod"] is None:
                continue
            if st["rod"] is not None:
                d = st["rod"]
                pm = gm @ T(BORE[0] / 16, BORE[1] / 16, (-8.0 + d) / 16) @ RY(math.pi) @ T(-ROD_HEAD[0] / 16, -ROD_HEAD[1] / 16, -ROD_HEAD[2] / 16)
            else:
                pm = gm
        else:
            pm = gm @ part_matrix(pivot, rot)
        for mat, *box in cubes:
            for f in cube_faces(pm, *box):
                faces.append((f, COL[mat]))
    return faces


# --- jugador sencillo (para la tercera persona) ---
SKIN = (196, 150, 118)
SHIRT = (60, 90, 140)
PANTS = (52, 50, 70)


def player_faces(entity, arms):
    faces = []
    boxes = [
        ("head", (0, 0, 0), (0, 0, 0), (-4, -8, -4, 8, 8, 8), SKIN),
        ("body", (0, 0, 0), (0, 0, 0), (-4, 0, -2, 8, 12, 4), SHIRT),
        ("rarm", (-5, 2, 0), arms[0], (-3, -2, -2, 4, 12, 4), SHIRT),
        ("larm", (5, 2, 0), arms[1], (-1, -2, -2, 4, 12, 4), SHIRT),
        ("rleg", (-1.9, 12, 0), (0, 0, 0), (-2, 0, -2, 4, 12, 4), PANTS),
        ("lleg", (1.9, 12, 0), (0, 0, 0), (-2, 0, -2, 4, 12, 4), PANTS),
    ]
    for name, pivot, rot, box, col in boxes:
        m = entity @ T(*(c / 16.0 for c in pivot)) @ RZ(rot[2]) @ RY(rot[1]) @ RX(rot[0])
        for f in cube_faces(m, *box):
            faces.append((f, col))
    return faces


def right_arm_matrix(entity, rot):
    return entity @ T(-5 / 16, 2 / 16, 0) @ RZ(rot[2]) @ RY(rot[1]) @ RX(rot[0])


# --- dibujo ---
LIGHT = np.array([0.35, 0.85, 0.45])
LIGHT /= np.linalg.norm(LIGHT)


def draw(faces, project, size, bg, img=None):
    if img is None:
        img = Image.new("RGB", size, bg)
    dr = ImageDraw.Draw(img)
    items = []
    for f, col in faces:
        n = np.cross(f[1] - f[0], f[2] - f[0])
        nl = np.linalg.norm(n)
        if nl < 1e-12:
            continue
        n /= nl
        pts, depth = project(f)
        if pts is None:
            continue
        shade = 0.55 + 0.45 * abs(float(n @ LIGHT))
        items.append((depth, pts, tuple(int(c * shade) for c in col)))
    items.sort(key=lambda t: t[0])
    for depth, pts, col in items:
        dr.polygon([tuple(p) for p in pts], fill=col, outline=tuple(max(0, c - 25) for c in col))
    return img


def perspective(view, w, h, fov=70.0):
    f = 1.0 / math.tan(math.radians(fov) / 2)

    def proj(face):
        pts = []
        zs = []
        for p in face:
            q = view @ np.append(p, 1.0)
            if q[2] > -0.03:
                return None, 0
            x = q[0] / -q[2] * f / (w / h)
            y = q[1] / -q[2] * f
            pts.append(((x + 1) * w / 2, (1 - y) * h / 2))
            zs.append(q[2])
        return pts, float(np.mean(zs))
    return proj


def ortho(scale, w, h):
    def proj(face):
        pts = [(w / 2 + p[0] * scale, h / 2 - p[1] * scale) for p in face]
        return pts, float(np.mean([p[2] for p in face]))
    return proj


def label(img, text):
    dr = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
    except OSError:
        font = ImageFont.load_default()
    dr.rectangle([0, 0, img.width, 34], fill=(20, 20, 24))
    dr.text((12, 5), text, fill=(240, 230, 200), font=font)
    return img


def fp_frame(parts, st, title):
    w, h = 640, 360
    base = T(0.56, -0.52, -0.72) @ display("firstperson_righthand") @ T(-0.5, -0.5, -0.5)
    faces = gun_faces(parts, base, st)
    img = draw(faces, perspective(np.eye(4), w, h), (w, h), (150, 190, 230))
    dr = ImageDraw.Draw(img)
    dr.line([(w / 2 - 6, h / 2), (w / 2 + 6, h / 2)], fill=(255, 255, 255))
    dr.line([(w / 2, h / 2 - 6), (w / 2, h / 2 + 6)], fill=(255, 255, 255))
    return label(img, title)


def tp_frame(parts, st, arms, title):
    w, h = 640, 480
    entity = RY(math.pi) @ S(-1, -1, 1) @ S(0.9375) @ T(0, -1.501, 0)
    entity = T(0, 0, 0) @ entity
    faces = player_faces(entity, arms)
    hand = right_arm_matrix(entity, arms[0]) @ RX(D(-90)) @ RY(D(180)) @ T(1 / 16, 0.125, -0.625)
    faces += gun_faces(parts, hand @ display("thirdperson_righthand") @ T(-0.5, -0.5, -0.5), st)
    # cámara delante a la derecha del jugador, mirándolo
    eye = np.array([-2.2, 1.9, 2.6])
    target = np.array([0.0, 1.25, 0.4])
    fwd = target - eye
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    view = np.eye(4)
    view[0, :3], view[1, :3], view[2, :3] = right, up, -fwd
    view = view @ T(*(-eye))
    img = draw(faces, perspective(view, w, h, 50), (w, h), (170, 200, 160))
    return label(img, title)


def gui_frame(parts, title):
    w = h = 320
    base = display("gui") @ T(-0.5, -0.5, -0.5)
    st = state(None, True, None, False)
    faces = gun_faces(parts, base, st)
    img = draw(faces, ortho(w, w, h), (w, h), (139, 139, 139))
    # el hueco del inventario (16 px del juego = todo el ancho)
    dr = ImageDraw.Draw(img)
    dr.rectangle([0, 0, w - 1, h - 1], outline=(55, 55, 55), width=6)
    return label(img, title)


def grid(frames, cols):
    fw, fh = frames[0].size
    rows = (len(frames) + cols - 1) // cols
    out = Image.new("RGB", (cols * fw + (cols - 1) * 8, rows * fh + (rows - 1) * 8), (30, 30, 34))
    for i, f in enumerate(frames):
        out.paste(f, ((i % cols) * (fw + 8), (i // cols) * (fh + 8)))
    return out


HOLD = ((-math.pi / 2 + 0.1, -0.3, 0), (-1.5, 0.6, 0))


def charge(h):
    return ((-0.97079635, -0.8, 0), (lerp(-0.97079635, -math.pi / 2, h), lerp(0.4, 0.85, h), 0))


def render_all(parts, zones, tex, out):
    os.makedirs(out, exist_ok=True)
    fp = [
        fp_frame(parts, state(None, True, None, True), "1ª persona: cargado (rastrillo arriba)"),
        fp_frame(parts, state(None, False, 1.0, True), "Disparo: cae el rastrillo y retrocede"),
        fp_frame(parts, state(0.10, False, None, True), "Recarga 10 %: alza el arma"),
        fp_frame(parts, state(0.30, False, None, True), "Recarga 30 %: la bala entra"),
        fp_frame(parts, state(0.50, False, None, True), "Recarga 50 %: baqueta"),
        fp_frame(parts, state(0.93, False, None, True), "Recarga 93 %: sube el rastrillo"),
    ]
    grid(fp, 2).save(os.path.join(out, "musket_first_person.png"))
    tp = [
        tp_frame(parts, state(None, True, None, False), HOLD, "3ª persona: apuntando"),
        tp_frame(parts, state(0.30, False, None, False), charge(1.0), "3ª persona: recargando (bala)"),
        tp_frame(parts, state(0.50, False, None, False), charge(1.0), "3ª persona: baqueta"),
        tp_frame(parts, state(None, False, 1.0, False), HOLD, "3ª persona: disparo"),
    ]
    grid(tp, 2).save(os.path.join(out, "musket_third_person.png"))
    gui_frame(parts, "Inventario").save(os.path.join(out, "musket_gui.png"))
