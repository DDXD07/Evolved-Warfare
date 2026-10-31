"""Armaduras de las facciones (bandidos, vikingos y piratas; 4 por facción, por rango): sus modelos 3D (cubos sobre la
cabeza, el cuerpo, los brazos y las piernas, como un modelo de Minecraft), las texturas, los iconos, las clases Java de
los modelos, los modelos de objeto, las recetas, las etiquetas y los textos. Los diseños están en ew_armor_sets.py.

Uso: python3 tools/ew_armor.py [raíz del proyecto] [--preview carpeta]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import ew_armor_paint as P
import ew_armor_raster as R

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

# las partes del modelo, a qué pieza pertenecen y dónde va su pivote (el del modelo de jugador de Minecraft)
PIVOTS = {
    "head": (0, 0, 0), "body": (0, 0, 0), "waist": (0, 0, 0),
    "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
    "right_skirt": (-1.9, 12, 0), "left_skirt": (1.9, 12, 0),
    "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0),
    "right_boot": (-1.9, 12, 0), "left_boot": (1.9, 12, 0),
}
PART_ORDER = list(PIVOTS)
SLOT_PARTS = {
    "helmet": ["head"],
    "chestplate": ["body", "right_arm", "left_arm", "right_skirt", "left_skirt"],
    "leggings": ["waist", "right_leg", "left_leg"],
    "boots": ["right_boot", "left_boot"],
}
PIECES = ["helmet", "chestplate", "leggings", "boots"]
MIRRORS = {"left_arm": "right_arm", "left_skirt": "right_skirt", "left_leg": "right_leg", "left_boot": "right_boot"}
# de qué parte del cuerpo copia la postura cada parte de la armadura
FOLLOWS = {"head": "head", "body": "body", "waist": "body", "right_arm": "right_arm", "left_arm": "left_arm",
           "right_skirt": "right_leg", "left_skirt": "left_leg", "right_leg": "right_leg", "left_leg": "left_leg",
           "right_boot": "right_leg", "left_boot": "left_leg"}


class Box:
    def __init__(self, mat, x, y, z, w, h, d, dil=0.0, decals=(), faces=None):
        assert all(float(v).is_integer() for v in (w, h, d)), ("tamaños enteros", mat, w, h, d)
        self.mat = mat
        self.box = (x, y, z, int(w), int(h), int(d))
        self.dil = dil
        self.decals = list(decals)
        self.face_materials = dict(faces or {})
        self.mirror = False
        self.source = None  # la caja de la que es reflejo (comparte su sitio en la textura)
        self.uv = None

    def size(self):
        return self.box

    def mirrored(self):
        x, y, z, w, h, d = self.box
        b = Box(self.mat, -x - w, y, z, w, h, d, self.dil, self.decals, self.face_materials)
        b.mirror = not self.mirror
        b.source = self.source or self
        return b

    def tex_uv(self):
        return (self.source or self).uv


class Node:
    def __init__(self, *items, children=(), pivot=(0, 0, 0), rot=(0, 0, 0)):
        # cajas y, mezclados, nodos hijos (con su propio giro)
        self.boxes = [b for b in items if not isinstance(b, Node)]
        self.children = [c for c in items if isinstance(c, Node)] + list(children)
        self.pivot = pivot
        self.rot = rot

    def mirrored(self):
        n = Node(*[b.mirrored() for b in self.boxes], children=[c.mirrored() for c in self.children],
                 pivot=(-self.pivot[0], self.pivot[1], self.pivot[2]), rot=(self.rot[0], -self.rot[1], -self.rot[2]))
        return n

    def all_boxes(self):
        out = list(self.boxes)
        for c in self.children:
            out += c.all_boxes()
        return out


def finish(armor):
    """Completa un diseño: las partes izquierdas (reflejo de las derechas si no se dan) y las vacías."""
    parts = armor["parts"]
    for left, right in MIRRORS.items():
        if left not in parts and right in parts:
            parts[left] = parts[right].mirrored()
    for name in PART_ORDER:
        parts.setdefault(name, Node())
    return armor


# ------------------------------------------------------------------ textura

def pack(armor):
    """Reparte las cajas (las que no son reflejo) por la textura. Devuelve (ancho, alto)."""
    boxes = []
    seen = set()
    for name in PART_ORDER:
        for b in armor["parts"][name].all_boxes():
            src = b.source or b
            if id(src) not in seen:
                seen.add(id(src))
                boxes.append(src)
    armor["_boxes"] = boxes
    width = 128
    order = sorted(boxes, key=lambda b: (-(b.box[5] + b.box[4]), -(2 * (b.box[3] + b.box[5]))))
    x = y = shelf = 0
    for b in order:
        _, _, _, w, h, d = b.box
        bw, bh = 2 * (w + d), d + h
        assert bw <= width, (armor["id"], b.box)
        if x + bw > width:
            x = 0
            y += shelf
            shelf = 0
        b.uv = (x, y)
        x += bw
        shelf = max(shelf, bh)
    height = y + shelf
    th = 32
    while th < height:
        th *= 2
    return width, th


def texture(armor):
    w, h = pack(armor)
    img = np.zeros((h, w, 4), np.float32)
    seed = sum(ord(c) * (i + 1) for i, c in enumerate(armor["id"]))
    rnd = P.new_rng(seed)
    for b in armor["_boxes"]:
        P.paint_box(img, b.uv, b, armor["materials"], rnd)
    return img.astype(np.uint8), (w, h)


# ------------------------------------------------------------------ vistas previas

SKIN = {"skin": (196, 150, 118), "hair": (70, 46, 28), "shirt": (60, 130, 150), "pants": (52, 50, 110), "eye": (40, 40, 90),
        "shoe": (70, 70, 70)}


def body_quads(pose):
    """El jugador (sin texturas): cabeza, cuerpo, brazos y piernas."""
    out = []
    boxes = [
        ("head", (-4, -8, -4, 8, 8, 8), "skin"),
        ("body", (-4, 0, -2, 8, 12, 4), "shirt"),
        ("right_arm", (-3, -2, -2, 4, 12, 4), "skin"),
        ("left_arm", (-1, -2, -2, 4, 12, 4), "skin"),
        ("right_leg", (-2, 0, -2, 4, 12, 4), "pants"),
        ("left_leg", (-2, 0, -2, 4, 12, 4), "pants"),
    ]
    for part, box, col in boxes:
        m = R.ENTITY @ R.part_matrix(PIVOTS[part], pose.get(part, (0, 0, 0)))
        for i, (world, uvs) in enumerate(R.cuboid_quads(m, box, (0, 0))):
            c = SKIN[col]
            if part == "head" and i == 3:
                c = SKIN["skin"]
            if part == "head" and i == 0:
                c = SKIN["hair"]
            out.append((world, uvs, None, c))
    return out


def node_quads(node, m, tex):
    out = []
    m = m @ R.part_matrix(node.pivot, node.rot)
    for b in node.boxes:
        for world, uvs in R.cuboid_quads(m, b.box, b.tex_uv(), b.dil, b.mirror):
            out.append((world, uvs, tex, None))
    for c in node.children:
        out += node_quads(c, m, tex)
    return out


def armor_quads(armor, tex, pose, pieces=PIECES):
    out = []
    for piece in pieces:
        for part in SLOT_PARTS[piece]:
            node = armor["parts"][part]
            follow = FOLLOWS[part]
            m = R.ENTITY @ R.part_matrix(PIVOTS[follow], pose.get(follow, (0, 0, 0)))
            # la parte de la armadura copia la postura de su parte del cuerpo; sus hijos van en su espacio
            inner = Node(*node.boxes, children=node.children, pivot=(0, 0, 0), rot=(0, 0, 0))
            out += node_quads(inner, m, tex)
    return out


WALK = {"right_arm": (-30, 0, 0), "left_arm": (30, 0, 0), "right_leg": (28, 0, 0), "left_leg": (-28, 0, 0)}


def figure(armor, tex, eye, target=(0, 1.0, 0), w=260, h=360, pose=None, pieces=PIECES, body=True, fov=40):
    pose = pose or {}
    cv = R.Canvas(w, h, R.look(eye, target), fov=fov, bg=(236, 232, 222, 255))
    quads = (body_quads(pose) if body else []) + armor_quads(armor, tex, pose, pieces)
    for world, uvs, t, flat in quads:
        cv.quad(world, uvs, t, flat)
    return cv.image().convert("RGB")


def label(img, text):
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 13)
    except OSError:
        font = ImageFont.load_default()
    d.rectangle([0, 0, img.width, 20], fill=(30, 30, 34))
    d.text((6, 3), text, fill=(240, 230, 200), font=font)
    return img


def grid(frames, cols, pad=4, bg=(30, 30, 34)):
    w = max(f.width for f in frames)
    h = max(f.height for f in frames)
    rows = (len(frames) + cols - 1) // cols
    out = Image.new("RGB", (cols * (w + pad) + pad, rows * (h + pad) + pad), bg)
    for i, f in enumerate(frames):
        out.paste(f, (pad + (i % cols) * (w + pad), pad + (i // cols) * (h + pad)))
    return out


def preview_set(armor, tex):
    front = figure(armor, tex, (-2.1, 1.9, 4.2), (0, 1.0, 0))
    back = figure(armor, tex, (2.2, 1.9, -4.1), (0, 1.0, 0))
    walk = figure(armor, tex, (-4.4, 1.6, 1.2), (0, 1.0, 0), pose=WALK)
    return [label(front, armor["names"]["es"][4] + " (delante)"), label(back, "detrás"), label(walk, "andando")]


# ------------------------------------------------------------------ iconos (16x16)

# vista de cada icono: (desde dónde mira, hacia dónde, postura)
ICON_VIEW = {
    "helmet": ((-1.2, 2.75, 2.6), (0, 1.75, 0), {}),
    "chestplate": ((-0.55, 1.45, 3.0), (0, 1.1, 0), {"right_arm": (0, 0, 22), "left_arm": (0, 0, -22)}),
    "leggings": ((-0.45, 0.75, 3.0), (0, 0.45, 0), {"right_leg": (0, 0, 9), "left_leg": (0, 0, -9)}),
    "boots": ((-1.7, 0.95, 2.5), (0, 0.18, 0), {"right_leg": (0, 0, 14), "left_leg": (0, 0, -14)}),
}


def icon(armor, tex, piece, size=16, fill=14.5):
    """El icono: la pieza sola en proyección paralela, encuadrada en 16x16 (muestreando el centro de cada píxel, sin
    mezclar colores) y con un contorno oscuro."""
    eye, target, pose = ICON_VIEW[piece]
    quads = armor_quads(armor, tex, pose, [piece])
    view = R.look(eye, target)
    pts = np.array([(view @ np.append(p, 1.0))[:2] for q in quads for p in q[0]])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    sub = 5
    big = size * sub
    scale = fill * sub / span
    centre = (lo + hi) / 2
    view = R.T(-centre[0], -centre[1], 0) @ view
    cv = R.Canvas(big, big, view, scale=scale)
    cv.boost = 1.25
    for world, uvs, t, flat in quads:
        cv.quad(world, uvs, t, flat)
    img = cv.color
    out = np.zeros((size, size, 4), np.uint8)
    c = sub // 2
    for j in range(size):
        for i in range(size):
            blk = img[j * sub:(j + 1) * sub, i * sub:(i + 1) * sub]
            px = blk[c, c]
            if px[3] < 128:
                # el centro está vacío: si casi todo el bloque está lleno, se coge el color más cercano al centro
                solid = blk[..., 3] > 127
                if solid.sum() < sub * sub * 0.5:
                    continue
                ys, xs = np.nonzero(solid)
                k = np.argmin((ys - c) ** 2 + (xs - c) ** 2)
                px = blk[ys[k], xs[k]]
            out[j, i, :3] = px[:3]
            out[j, i, 3] = 255
    alpha = out[..., 3] > 0
    edge = np.zeros_like(alpha)
    for dj, di in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        shifted = np.zeros_like(alpha)
        if dj == 1:
            shifted[1:] = alpha[:-1]
        elif dj == -1:
            shifted[:-1] = alpha[1:]
        elif di == 1:
            shifted[:, 1:] = alpha[:, :-1]
        else:
            shifted[:, :-1] = alpha[:, 1:]
        edge |= alpha & ~shifted
    out[edge, :3] = (out[edge, :3] * 0.6).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


# ------------------------------------------------------------------ Java

def fmt(v):
    s = ("%.4f" % v).rstrip("0").rstrip(".")
    return (s if s not in ("-0", "") else "0") + "F"


def java_node(lines, parent, name, node, var, depth):
    builder = ["ModelPartBuilder.create()"]
    for b in node.boxes:
        u, v = b.tex_uv()
        x, y, z, w, h, d = b.box
        dil = b.dil if isinstance(b.dil, tuple) else (b.dil,)
        builder.append(".mirrored(%s).uv(%d, %d).cuboid(%s, %s, %s, %s, %s, %s, new Dilation(%s))"
                       % ("true" if b.mirror else "false", u, v, fmt(x), fmt(y), fmt(z), fmt(w), fmt(h), fmt(d),
                          ", ".join(fmt(v) for v in dil)))
    px, py, pz = node.pivot
    rx, ry, rz = (math.radians(a) for a in node.rot)
    transform = ("ModelTransform.of(%s, %s, %s, %s, %s, %s)" % (fmt(px), fmt(py), fmt(pz), fmt(rx), fmt(ry), fmt(rz))
                 if any(node.rot) else "ModelTransform.pivot(%s, %s, %s)" % (fmt(px), fmt(py), fmt(pz)))
    lines.append("\t\tModelPartData %s = %s.addChild(\"%s\", %s,\n\t\t\t\t%s);" % (var, parent, name, "\n\t\t\t\t".join(builder),
                                                                                      transform))
    for i, c in enumerate(node.children):
        java_node(lines, var, "c%d" % i, c, "%s_%d" % (var, i), depth + 1)


def java_models(armors):
    out = []
    w = out.append
    w("package com.evolvedwarfare.client.render.armor;")
    w("")
    w("import com.evolvedwarfare.armor.FactionArmor;")
    w("import net.minecraft.client.model.Dilation;")
    w("import net.minecraft.client.model.ModelData;")
    w("import net.minecraft.client.model.ModelPartBuilder;")
    w("import net.minecraft.client.model.ModelPartData;")
    w("import net.minecraft.client.model.ModelTransform;")
    w("import net.minecraft.client.model.TexturedModelData;")
    w("")
    w("/** Generado por tools/ew_armor.py: los modelos 3D de las armaduras de las facciones (no editar a mano). */")
    w("public final class FactionArmorModels {")
    w("\t/** Las partes de cada modelo (las mismas en todos), en el orden del modelo de jugador. */")
    w("\tpublic static final String[] PARTS = {%s};" % ", ".join('"%s"' % p for p in PART_ORDER))
    w("")
    w("\tprivate FactionArmorModels() {")
    w("\t}")
    w("")
    w("\tpublic static TexturedModelData of(FactionArmor armor) {")
    w("\t\treturn switch (armor) {")
    for a in armors:
        w("\t\t\tcase %s -> %s();" % (a["id"].upper(), camel(a["id"])))
    w("\t\t};")
    w("\t}")
    for a in armors:
        w("")
        w("\tprivate static TexturedModelData %s() {" % camel(a["id"]))
        w("\t\tModelData data = new ModelData();")
        w("\t\tModelPartData root = data.getRoot();")
        lines = []
        for p in PART_ORDER:
            node = a["parts"][p]
            top = Node(*node.boxes, children=node.children, pivot=PIVOTS[p], rot=(0, 0, 0))
            java_node(lines, "root", p, top, p, 0)
        out.extend(lines)
        w("\t\treturn TexturedModelData.of(data, %d, %d);" % a["tex_size"])
        w("\t}")
    w("}")
    return "\n".join(out) + "\n"


def camel(s):
    parts = s.split("_")
    return parts[0] + "".join(p.title() for p in parts[1:])


# ------------------------------------------------------------------ recetas, modelos de objeto, etiquetas, textos

SHAPES = {
    "helmet": ["XAX", "X X"],
    "chestplate": ["X X", "XAX", "XXX"],
    "leggings": ["XAX", "X X", "X X"],
    "boots": ["A A", "X X"],
}


def recipe(armor, piece):
    keys = {"X": {"item": armor["recipe"]["X"]}, "A": {"item": armor["recipe"]["A"]}}
    pattern = list(SHAPES[piece])
    if "D" in armor["recipe"]:
        # el de capitán lleva además un diamante (u otro material noble) en lugar de un X
        row = 1 if piece != "boots" else 1
        r = list(pattern[row])
        r[0] = "D"
        pattern[row] = "".join(r)
        keys["D"] = {"item": armor["recipe"]["D"]}
    return {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": keys,
            "result": {"id": "%s:%s_%s" % (NS, armor["id"], piece), "count": 1}}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def lang(armors):
    es, en = {}, {}
    for a in armors:
        for i, piece in enumerate(PIECES):
            key = "item.%s.%s_%s" % (NS, a["id"], piece)
            es[key] = a["names"]["es"][i]
            en[key] = a["names"]["en"][i]
        es["armor.%s.%s" % (NS, a["id"])] = a["names"]["es"][4]
        en["armor.%s.%s" % (NS, a["id"])] = a["names"]["en"][4]
    es["armor.%s.tooltip" % NS] = "Facción: %s · rango %s de 4"
    en["armor.%s.tooltip" % NS] = "Faction: %s · rank %s of 4"
    es["armor.%s.light" % NS] = "Ligera"
    en["armor.%s.light" % NS] = "Light"
    es["armor.%s.heavy" % NS] = "Pesada"
    en["armor.%s.heavy" % NS] = "Heavy"
    return es, en


def merge_lang(es, en):
    folder = os.path.join(ASSETS, "lang")
    for f in sorted(os.listdir(folder)):
        path = os.path.join(folder, f)
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        data.update(en if f.startswith("en") else es)
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2, ensure_ascii=False)
            fh.write("\n")


def main():
    import ew_armor_sets as SETS
    armors = [finish(a) for a in SETS.ARMORS]
    textures = {}
    for a in armors:
        img, size = texture(a)
        a["tex_size"] = size
        textures[a["id"]] = img
        path = os.path.join(ASSETS, "textures/entity/faction_armor", a["id"] + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        Image.fromarray(img, "RGBA").save(path)
        for piece in PIECES:
            item = "%s_%s" % (a["id"], piece)
            icon(a, img, piece).save(os.path.join(ASSETS, "textures/item", item + ".png"))
            write_json(os.path.join(ASSETS, "models/item", item + ".json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, item)}})
            write_json(os.path.join(DATA, "recipe", item + ".json"), recipe(a, piece))
    # etiquetas: así se encantan como las armaduras de siempre
    for piece, tag in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"), ("boots", "foot_armor")):
        write_json(os.path.join(ROOT, "src/main/resources/data/minecraft/tags/item", tag + ".json"),
                   {"replace": False, "values": ["%s:%s_%s" % (NS, a["id"], piece) for a in armors]})
    folder = os.path.join(ROOT, "src/client/java/com/evolvedwarfare/client/render/armor")
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, "FactionArmorModels.java"), "w", encoding="utf-8") as f:
        f.write(java_models(armors))
    merge_lang(*lang(armors))
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        render_previews(armors, textures, PREVIEW)
    print("armaduras: %d conjuntos, %d piezas" % (len(armors), 4 * len(armors)))


def render_previews(armors, textures, out):
    by_faction = {}
    for a in armors:
        by_faction.setdefault(a["faction"], []).append(a)
    for faction, items in by_faction.items():
        frames = []
        for a in items:
            frames += preview_set(a, textures[a["id"]])
        grid(frames, 6).save(os.path.join(out, "armor_%s.png" % faction))
    # iconos ampliados
    icons = []
    for a in armors:
        row = Image.new("RGBA", (4 * 68, 68), (60, 60, 66, 255))
        for i, piece in enumerate(PIECES):
            ic = Image.open(os.path.join(ASSETS, "textures/item", "%s_%s.png" % (a["id"], piece))).resize((64, 64), Image.NEAREST)
            row.alpha_composite(ic, (2 + i * 68, 2))
        icons.append(row.convert("RGB"))
    grid(icons, 3).save(os.path.join(out, "armor_icons.png"))


if __name__ == "__main__":
    # los diseños importan este módulo como ew_armor: se usa esa misma copia para que las clases coincidan
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import ew_armor
    ew_armor.PREVIEW, ew_armor.ROOT = PREVIEW, ROOT
    ew_armor.ASSETS, ew_armor.DATA = ASSETS, DATA
    ew_armor.main()
