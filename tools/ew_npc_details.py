"""Las piezas en 3D de las personas del mod (fase 111): cada aspecto (tools/ew_skins.py) recibe sus propias piezas,
sacadas de lo que lleva y del mismo color: barbas con volumen, coletas, trenzas y moños, capuchas con su pico,
sombreros (de paja, tricornios, gorras, boinas, turbantes) y la nariguera del casco, máscaras de bandido con el nudo,
parches, pendientes; cinturones con hebilla, bolsas, herramientas, bandoleras, fajas con su lazo, mandiles, tabardos con
el emblema, capas, mantos de piel y bufandas; hombreras, brazales y guantes; vendas de las piernas, rodilleras y suelas.

Cada pieza va pegada a una parte del cuerpo (cabeza, torso, brazo, antebrazo, pierna, espinilla): si al NPC le cortan
esa parte, desaparece con ella (y sale en el trozo cortado), y si lleva armadura en ese hueco, no se pone. El daño
locacional va por la caja del NPC, así que no cambia.

Salida: assets/evolvedwarfare/npc_details/<carpeta>/<aspecto>.json y textures/entity/<carpeta>/<aspecto>_details.png.

Uso: python3 tools/ew_npc_details.py [raíz] [--preview carpeta]
"""
import json
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
args = [a for a in sys.argv[1:]]
PREVIEW = None
if "--preview" in args:
    PREVIEW = args[args.index("--preview") + 1]
    args = args[:args.index("--preview")]
ROOT = args[0] if args else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.argv = [sys.argv[0], ROOT]
import ew_skins as S  # noqa: E402
import ew_wildlife as W  # noqa: E402
from ew_model3d import render_entity  # noqa: E402

NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
C = W.C


def rgb(h):
    c = S.hexc(h) if isinstance(h, str) else h
    return tuple(c[:3])


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


METAL = (186, 186, 194)
GOLD = (214, 176, 64)


class Detail:
    """Las piezas de un aspecto: grupos pegados a una parte del cuerpo, con el hueco de armadura que los tapa."""

    def __init__(self, sid):
        self.a = W.Animal(sid)
        self.groups = []

    def group(self, bone, slot, pivot=(0, 0, 0), rot=(0, 0, 0)):
        name = "g%d" % len(self.groups)
        part = self.a.part(name, None, pivot, rot)
        self.groups.append({"name": name, "bone": bone, "slot": slot, "part": part})
        return part


def cube(part, origin, size, paint, inflate=0.0):
    part.cube(origin, size, **paint)
    part.cubes[-1].inflate = inflate


def details(sid, o):
    d = Detail(sid)
    hair = rgb(o.get("hair", "#2E1F16"))
    beard = o.get("beard", "none")
    style = o.get("style", "short")
    hat = o.get("hat")
    hood = o.get("hood")
    covered = bool(hood) or hat in ("helm", "turban", "fur", "bandana")
    # ---------------- la cabeza
    if beard in ("full", "long", "braided", "short") or beard == "mustache":
        h = d.group("head", "head")
        bc = shade(hair, 0.92)
        if beard == "mustache":
            cube(h, (-3, -3, -4.6), (6, 1, 1), C(bc))
            cube(h, (-3.5, -3, -4.6), (1, 2, 1), C(bc))
            cube(h, (2.5, -3, -4.6), (1, 2, 1), C(bc))
        else:
            cube(h, (-4, -3, -4.6), (8, 3, 1), C(bc, low=(shade(bc, 0.8), 1)))
            if beard in ("full", "long", "braided"):
                cube(h, (-3.5, 0, -4.5), (7, 2, 2), C(bc, low=(shade(bc, 0.8), 1)))
            if beard == "long":
                cube(h, (-2.5, 2, -4.3), (5, 3, 1), C(bc, low=(shade(bc, 0.8), 1)))
            if beard == "braided":
                cube(h, (-2, 2, -4.3), (1, 4, 1), C(bc, rings=(shade(bc, 0.7), 1)))
                cube(h, (1, 2, -4.3), (1, 4, 1), C(bc, rings=(shade(bc, 0.7), 1)))
                cube(h, (-2, 5.5, -4.3), (1, 1, 1), C(METAL))
                cube(h, (1, 5.5, -4.3), (1, 1, 1), C(METAL))
    if not covered and style in ("ponytail", "braids", "topknot", "long"):
        h = d.group("head", "head")
        hc = rgb(hair)
        if style == "ponytail":
            cube(h, (-1, -6, 4.3), (2, 2, 1), C(shade(hc, 0.8)))
            cube(h, (-1, -4, 4.4), (2, 7, 1), C(hc, low=(shade(hc, 0.8), 2)))
        elif style == "braids":
            for x in (-4.7, 3.7):
                cube(h, (x, -4, -1), (1, 8, 1), C(hc, rings=(shade(hc, 0.75), 1)))
                cube(h, (x, 4, -1), (1, 1, 1), C((120, 86, 50)))
        elif style == "topknot":
            cube(h, (-1.5, -10, -1.5), (3, 2, 3), C(hc, low=(shade(hc, 0.8), 1)))
            cube(h, (-1, -8.6, -1), (2, 1, 2), C((120, 86, 50)))
        else:
            cube(h, (-4, -4, 4.3), (8, 5, 1), C(hc, low=(shade(hc, 0.8), 2)))
    if hood:
        hc = rgb(hood)
        h = d.group("head", "head")
        cube(h, (-4.5, -9, -4.9), (9, 1, 2), C(hc, low=(shade(hc, 0.8), 1)))                 # el pico de la capucha
        cube(h, (-4.9, -8.5, -4.9), (1, 6, 2), C(hc, low=(shade(hc, 0.8), 2)))
        cube(h, (3.9, -8.5, -4.9), (1, 6, 2), C(hc, low=(shade(hc, 0.8), 2)))
        cube(h, (-1.5, -9.5, 2), (3, 2, 3), C(shade(hc, 0.9)))                                # la punta, atrás
        cube(h, (-4, -1, 4.2), (8, 3, 1), C(shade(hc, 0.9), low=(shade(hc, 0.75), 1)))       # el embozo, en la nuca
    if hat and hat != "helm":
        hc = rgb(o.get("hat_color", "#5A4A3A"))
        h = d.group("head", "head")
        if hat == "straw":
            cube(h, (-7, -7, -7), (14, 1, 14), C(hc, rings=(shade(hc, 0.85), 2)))
            cube(h, (-4, -10, -4), (8, 3, 8), C(hc, rings=(shade(hc, 0.85), 1), low=((90, 50, 40), 1)), 0.3)
        elif hat == "cap":
            cube(h, (-4, -9, -4), (8, 1, 8), C(hc), 0.4)
            cube(h, (-4, -7.6, -7), (8, 1, 3), C(shade(hc, 0.85)))
        elif hat == "beret":
            cube(h, (-5, -10, -4.5), (9, 2, 8), C(hc, low=(shade(hc, 0.8), 1)))
            cube(h, (-0.5, -11, -0.5), (1, 1, 1), C(shade(hc, 0.7)))
        elif hat == "tricorn":
            trim = rgb(o.get("trim", "#C8A040"))
            cube(h, (-4, -10.5, -4), (8, 2, 8), C(hc), 0.4)
            cube(h, (-6, -10, -6), (12, 2, 1), C(hc, top=trim))
            cube(h, (-6, -10, -6), (1, 2, 11), C(hc, top=trim))
            cube(h, (5, -10, -6), (1, 2, 11), C(hc, top=trim))
            if o.get("skull"):
                cube(h, (-1, -10, -6.6), (2, 2, 1), C((230, 226, 214)))
        elif hat == "bandana":
            cube(h, (-1, -6, 4.4), (2, 2, 1), C(shade(hc, 0.85)))
            cube(h, (-1, -4, 4.4), (1, 4, 1), C(hc))
            cube(h, (0.2, -4, 4.4), (1, 3, 1), C(hc))
        elif hat == "turban":
            cube(h, (-4.5, -11, -4.5), (9, 3, 9), C(hc, rings=(shade(hc, 0.82), 1)))
            cube(h, (-0.5, -9.5, -5.2), (1, 1, 1), C(rgb(o.get("gem", "#C02040"))))
    if hat == "helm" and o.get("nasal"):
        h = d.group("head", "head")
        cube(h, (-0.5, -7, -5), (1, 4, 1), C(rgb(o.get("hat_color", "#8A8A90"))))
    if o.get("mask"):
        mc = rgb(o["mask"])
        h = d.group("head", "head")
        cube(h, (-4.3, -4, -4.7), (8, 4, 1), C(mc, low=(shade(mc, 0.8), 1)))
        cube(h, (-1, -3.5, 4.3), (2, 2, 1), C(shade(mc, 0.85)))
        cube(h, (-0.5, -1.5, 4.4), (1, 3, 1), C(mc))
    if o.get("eyepatch"):
        h = d.group("head", "head")
        cube(h, (-3.2, -5.2, -4.6), (2, 2, 1), C((22, 20, 20)))
        cube(h, (-4, -6.2, -4), (8, 1, 8), C((30, 26, 24)), 0.42)
    if o.get("earring"):
        h = d.group("head", "head")
        cube(h, (-4.6, -3, -0.5), (1, 1, 1), C(GOLD), -0.15)
    # ---------------- el torso
    if o.get("belt"):
        bc = rgb(o["belt"])
        b = d.group("body", "chest")
        cube(b, (-4, 10, -2), (8, 1, 4), C(bc), 0.32)
        cube(b, (-1, 9.6, -2.75), (2, 2, 1), C(rgb(o.get("buckle", "#BDBDBD"))))
        if o.get("pouch"):
            cube(b, (-3.8, 10.6, -2.9), (2, 2, 1), C(rgb(o["pouch"]), low=(shade(rgb(o["pouch"]), 0.75), 1)))
        if o.get("belt_tools"):
            cube(b, (3.6, 9, -1), (1, 5, 1), C((120, 86, 50)))
            cube(b, (3.4, 8.2, -1.8), (1, 1, 3), C(METAL))
    if o.get("sash"):
        sc = rgb(o["sash"])
        b = d.group("body", "chest")
        cube(b, (-4, 9.5, -2), (8, 2, 4), C(sc, rings=(shade(sc, 0.82), 1)), 0.3)
        cube(b, (2.4, 9.5, -2.8), (2, 2, 1), C(shade(sc, 0.85)))
        cube(b, (2.9, 11.5, -2.7), (1, 4, 1), C(sc))
    if o.get("bandolier"):
        bc = rgb(o["bandolier"])
        b = d.group("body", "chest", (0, 6, 0), (0, 0, -38))
        cube(b, (-0.5, -8.5, -2.45), (1, 17, 1), C(bc, spots=(METAL, 0.12)))
        cube(b, (-0.5, -8.5, 1.45), (1, 17, 1), C(bc))
    if o.get("apron"):
        ac = rgb(o["apron"])
        b = d.group("body", "chest")
        cube(b, (-3.5, 3, -2.6), (7, 11, 1), C(ac, low=(shade(ac, 0.85), 2)))
        if o.get("pocket"):
            cube(b, (-2, 8, -2.9), (4, 2, 1), C(shade(ac, 0.85)))
    if o.get("tabard"):
        tc = rgb(o["tabard"])
        b = d.group("body", "chest")
        trim = rgb(o.get("trim", "#C8A040"))
        cube(b, (-3, 2, -2.55), (6, 12, 1), C(tc, low=(trim, 1)))
        cube(b, (-3, 2, 1.55), (6, 12, 1), C(tc, low=(trim, 1)))
        if o.get("emblem"):
            cube(b, (-1, 4, -2.85), (2, 2, 1), C(rgb(o["emblem"])))
    if o.get("cloak"):
        cc = rgb(o["cloak"])
        b = d.group("body", "chest")
        cube(b, (-4.5, -0.6, 2.05), (9, 17, 1), C(cc, low=(shade(cc, 0.8), 3), spots=(shade(cc, 0.85), 0.08)))
        cube(b, (-4.5, -0.8, -2.5), (9, 1, 5), C(shade(cc, 0.9)))
        cube(b, (-0.5, -0.6, -2.85), (1, 1, 1), C(GOLD))
    if o.get("fur"):
        fc = rgb(o["fur"])
        b = d.group("body", "chest")
        cube(b, (-5, -1, -3), (10, 3, 6), C(fc, spots=(shade(fc, 0.8), 0.35)))
    if o.get("scarf"):
        sc = rgb(o["scarf"])
        b = d.group("body", "chest")
        cube(b, (-4, -1, -2), (8, 2, 4), C(sc, rings=(shade(sc, 0.82), 1)), 0.4)
        cube(b, (1.5, 1, -2.8), (2, 4, 1), C(sc, low=(shade(sc, 0.8), 1)))
    # ---------------- los brazos
    for side, bone in ((-1, "right_arm"), (1, "left_arm")):
        x0 = -3 if side < 0 else -1
        if o.get("shoulder"):
            a = d.group(bone, "chest")
            sc = rgb(o["shoulder"])
            cube(a, (x0 - 0.5, -2.6, -2.5), (5, 2, 5), C(sc, low=(shade(sc, 0.8), 1)))
            cube(a, (x0 - 0.5, -0.6, -2.5), (5, 1, 5), C(shade(sc, 0.85)))
        if o.get("bracers"):
            a = d.group(bone.replace("arm", "forearm"), "chest")
            bc = rgb(o["bracers"])
            cube(a, (x0, 0, -2), (4, 4, 4), C(bc, rings=(shade(bc, 0.8), 1)), 0.35)
        if o.get("gloves"):
            a = d.group(bone.replace("arm", "forearm"), "chest")
            gc = rgb(o["gloves"])
            cube(a, (x0, 4, -2), (4, 1, 4), C(shade(gc, 0.9)), 0.45)
        if o.get("sleeves") == "rolled":
            a = d.group(bone, "chest")
            sc = rgb(o.get("sleeve_color") or o.get("shirt", "#7A5230"))
            cube(a, (x0, 3, -2), (4, 1, 4), C(shade(sc, 0.9)), 0.35)
    # ---------------- las piernas
    boots = rgb(o.get("boots", "#2B1E14"))
    kind = o.get("boot_kind", "boots")
    for side, bone in ((-1, "right_shin"), (1, "left_shin")):
        if kind in ("boots", "shoes", "wraps"):
            f = d.group(bone, "feet")
            cube(f, (-2, 5.2, -2.6), (4, 1, 5), C(shade(boots, 0.7)))
            if kind == "boots":
                cube(f, (-2, 0, -2), (4, 1, 4), C(shade(boots, 1.1)), 0.42)
        if o.get("wraps") or kind == "wraps":
            f = d.group(bone, "legs")
            wc = rgb(o.get("wraps") or "#6B5E4E")
            cube(f, (-2, -1, -2), (4, 5, 4), C(wc, rings=(shade(wc, 0.78), 1)), 0.25)
        if o.get("kneepads"):
            f = d.group(bone, "legs")
            cube(f, (-2, -1.8, -2.7), (4, 2, 1), C(rgb(o["kneepads"])))
    return d


def export(d, folder, name):
    tw, th = d.a.pack()
    img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    rng = random.Random(name)
    for p in d.a.parts:
        for c in p.cubes:
            W.paint_cube(img, c, rng)
    img.save(os.path.join(ASSETS, "textures/entity", folder, name + "_details.png"))
    groups = []
    for g in d.groups:
        p = g["part"]
        groups.append({"bone": g["bone"], "slot": g["slot"], "pivot": list(p.pivot), "rotation": list(p.rot),
                       "cubes": [{"uv": list(c.uv), "origin": list(c.origin), "size": list(c.size), "inflate": getattr(c, "inflate", 0.0)}
                                 for c in p.cubes]})
    out = os.path.join(ASSETS, "npc_details", folder)
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, name + ".json"), "w", encoding="utf-8") as fh:
        json.dump({"texture": [tw, th], "groups": groups}, fh, separators=(",", ":"))
        fh.write("\n")
    return img, groups


def main():
    total = 0
    pieces = 0
    previews = []
    for folder, name, count, seed, make in S.ROLES:
        for i in range(count):
            r = random.Random(seed * 1000 + i)
            o = make(r, i)
            d = details("%s_%d" % (name, i), o)
            img, groups = export(d, folder, "%s_%d" % (name, i))
            total += 1
            pieces += sum(len(g["cubes"]) for g in groups)
            previews.append((folder, "%s_%d" % (name, i), groups, img))
        if folder in S.CAPTAINS:
            o = S.CAPTAINS[folder](random.Random(seed))
            d = details("captain", o)
            img, groups = export(d, folder, "captain")
            total += 1
            pieces += sum(len(g["cubes"]) for g in groups)
            previews.append((folder, "captain", groups, img))
    print("piezas de NPC: %d aspectos, %d piezas" % (total, pieces))
    if PREVIEW:
        preview(previews)


# ------------------------------------------------------------------ la vista previa (con el cuerpo, para ver dónde caen)

BONES = {
    "head": ((0, 0, 0), [((-4, -8, -4), (8, 8, 8))]),
    "body": ((0, 0, 0), [((-4, 0, -2), (8, 12, 4))]),
    "right_arm": ((-5, 2, 0), [((-3, -2, -2), (4, 6, 4))]),
    "left_arm": ((5, 2, 0), [((-1, -2, -2), (4, 6, 4))]),
    "right_forearm": ((0, 4, 0), [((-3, -2, -2), (4, 8, 4))]),
    "left_forearm": ((0, 4, 0), [((-1, -2, -2), (4, 8, 4))]),
    "right_leg": ((-1.9, 12, 0), [((-2, 0, -2), (4, 6, 4))]),
    "left_leg": ((1.9, 12, 0), [((-2, 0, -2), (4, 6, 4))]),
    "right_shin": ((0, 6, 0), [((-2, -2, -2), (4, 8, 4))]),
    "left_shin": ((0, 6, 0), [((-2, -2, -2), (4, 8, 4))]),
}
PARENT = {"right_forearm": "right_arm", "left_forearm": "left_arm", "right_shin": "right_leg", "left_shin": "left_leg"}


def preview(previews):
    os.makedirs(PREVIEW, exist_ok=True)
    tiles = []
    for folder, name, groups, img in previews:
        skin = Image.open(os.path.join(ASSETS, "textures/entity", folder, name + ".png")).convert("RGBA")
        # el cuerpo (con la piel de verdad) y las piezas, en una textura juntas
        tex = Image.new("RGBA", (max(64, img.width), 64 + img.height), (0, 0, 0, 0))
        tex.alpha_composite(skin, (0, 0))
        tex.alpha_composite(img, (0, 64))
        uvs = {"head": (0, 0), "body": (16, 16), "right_arm": (40, 16), "left_arm": (32, 48), "right_leg": (0, 16), "left_leg": (16, 48)}
        parts = []
        for bone, (pivot, cubes) in BONES.items():
            base = bone.replace("forearm", "arm").replace("shin", "leg")
            u, v = uvs[base]
            low = bone != base
            parts.append({"name": bone, "parent": PARENT.get(bone), "pivot": list(pivot), "rotation": [0, 0, 0],
                          "cubes": [{"uv": [u, v + (4 if low else 0)], "origin": list(o), "size": list(s)} for o, s in cubes]})
        for k, g in enumerate(groups):
            parts.append({"name": "d%d" % k, "parent": g["bone"], "pivot": g["pivot"], "rotation": g["rotation"],
                          "cubes": [{"uv": [c["uv"][0], c["uv"][1] + 64], "origin": c["origin"], "size": c["size"]} for c in g["cubes"]]})
        tiles.append(render_entity(parts, tex, size=180, scale=4.6, yaw=200, pitch=8, center=(0.0, 12.0, 0.0), bg=(96, 100, 108)))
    cols = 13
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 180, rows * 180), (96, 100, 108))
    for k, t in enumerate(tiles):
        sheet.paste(t, ((k % cols) * 180, (k // cols) * 180))
    sheet.save(os.path.join(PREVIEW, "npc_details.png"))


if __name__ == "__main__":
    main()
