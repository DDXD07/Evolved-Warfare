"""Metalurgia (2): las armaduras de placas (de hierro y de acero) y los escudos reforzados (escudo reforzado y pavés).

Las armaduras van con el reparto de textura de las de vanilla (dos capas de 64x32), como las de clase; los escudos, con
el del escudo de vanilla (64x64: el tablero de 12x22 y el asa).

Uso: python3 tools/ew_plate_armor.py [--preview carpeta]
"""
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
PREVIEW = None
if "--preview" in sys.argv:
    PREVIEW = sys.argv[sys.argv.index("--preview") + 1]
sys.argv = sys.argv[:1]
import ew_armor as A  # noqa: E402
import ew_armor_paint as P  # noqa: E402
import ew_class_armor as CA  # noqa: E402

NS = "evolvedwarfare"
ASSETS = CA.ASSETS
DATA = CA.DATA
PIECES = A.PIECES

SETS = {
    "iron_plate": {"base": (198, 198, 204), "dark": (118, 118, 126), "trim": (232, 232, 236), "item": NS + ":iron_plate",
                   "es": ["Yelmo de placas de hierro", "Coraza de placas de hierro", "Quijotes de placas de hierro", "Escarpes de placas de hierro"],
                   "en": ["Iron Plate Helm", "Iron Plate Cuirass", "Iron Plate Cuisses", "Iron Plate Sabatons"]},
    "steel_plate": {"base": (150, 166, 188), "dark": (84, 96, 116), "trim": (214, 188, 104), "item": NS + ":steel_plate",
                    "es": ["Yelmo de placas de acero", "Coraza de placas de acero", "Quijotes de placas de acero", "Escarpes de placas de acero"],
                    "en": ["Steel Plate Helm", "Steel Plate Cuirass", "Steel Plate Cuisses", "Steel Plate Sabatons"]},
}


def mats(s):
    return {
        "m": {"kind": "plate", "c": s["base"], "rivets": True},
        "dark": {"kind": "flat", "c": s["dark"]},
        "trim": {"kind": "flat", "c": s["trim"]},
        "strap": {"kind": "leather", "c": (92, 60, 38)},
        "black": {"kind": "flat", "c": (20, 18, 22)},
    }


DECALS = {
    # yelmo cerrado: rendija de la visera, agujeros de respirar y cresta
    "head": [("hole", "N", 1, 3, 6, 1), ("px", "N", [(5, 5, "dark"), (6, 5, "dark"), (5, 6, "dark"), (6, 6, "dark")]), ("vband", "N", 3, 5, "trim"),
             ("vband", "U", 3, 5, "trim"), ("band", "sides", 7, 8, "dark")],
    # coraza: arista del pecho, faldón por placas y correas
    "body": [("vband", "N", 3, 5, "trim"), ("band", "N", 6, 7, "dark"), ("band", "sides", 8, 9, "strap"), ("band", "sides", 10, 11, "dark"),
             ("vband", "S", 3, 5, "dark")],
    # hombreras
    "arm": [("band", "sides", 0, 3, "trim"), ("band", "sides", 3, 4, "dark"), ("band", "sides", 7, 8, "dark")],
    "waist": [("band", "sides", 8, 9, "strap"), ("band", "sides", 10, 12, "dark")],
    "leg": [("rect", "N", 0, 4, 4, 2, "trim"), ("band", "sides", 9, 10, "dark")],
    "boot": [("band", "sides", 9, 10, "dark"), ("band", "sides", 7, 8, "trim")],
}


def layers(name):
    s = SETS[name]
    m = mats(s)
    d = DECALS
    rnd = P.new_rng(sum(ord(c) for c in name) * 17)
    l1 = np.zeros((32, 64, 4), np.float32)
    l2 = np.zeros((32, 64, 4), np.float32)
    P.paint_box(l1, CA.HEAD, CA.box("m", (-4, -8, -4, 8, 8, 8), CA.HEAD, d["head"]), m, rnd)
    P.paint_box(l1, CA.BODY, CA.box("m", (-4, 0, -2, 8, 12, 4), CA.BODY, d["body"]), m, rnd)
    P.paint_box(l1, CA.ARM, CA.box("m", (-3, -2, -2, 4, 12, 4), CA.ARM, d["arm"]), m, rnd)
    P.paint_box(l1, CA.LEG, CA.box("m", (-2, 0, -2, 4, 12, 4), CA.LEG, d["boot"]), m, rnd)
    CA.clear(l1, P.box_regions(*CA.LEG, 4, 12, 4), "NSEWU", rows=7)
    P.paint_box(l2, CA.BODY, CA.box("m", (-4, 0, -2, 8, 12, 4), CA.BODY, d["waist"]), m, rnd)
    CA.clear(l2, P.box_regions(*CA.BODY, 8, 12, 4), "NSEWU", rows=7)
    P.paint_box(l2, CA.LEG, CA.box("m", (-2, 0, -2, 4, 12, 4), CA.LEG, d["leg"]), m, rnd)
    return l1.astype(np.uint8), l2.astype(np.uint8)


# ------------------------------------------------------------------ escudos (reparto del escudo de vanilla, 64x64)

def shield_texture(kind):
    rnd = random.Random(kind)
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    oak, oak_dark = (140, 104, 62), (98, 70, 40)
    iron, iron_dark = (170, 170, 178), (96, 96, 104)
    # todo el tablero (de 0,0 a 26,23) y el asa (26,0 a 42,12) en madera oscura primero
    dr.rectangle((0, 0, 25, 23), fill=oak_dark + (255,))
    dr.rectangle((26, 0, 41, 11), fill=(70, 48, 30, 255))
    # el frente: 12x22 en (1,1)
    x0, y0, w, h = 1, 1, 12, 22
    for x in range(w):
        for y in range(h):
            if kind == "pavise":
                band = ((x // 3) % 2 == 0)
                c = (172, 40, 34) if band else (226, 218, 196)
            else:
                plank = x // 3
                c = tuple(int(v * (0.9 + 0.1 * ((plank * 7) % 3) / 2 + rnd.uniform(-0.04, 0.04))) for v in oak)
                if x % 3 == 2:
                    c = tuple(int(v * 0.75) for v in oak)
            im.putpixel((x0 + x, y0 + y), c + (255,))
    # el canto de hierro
    for x in range(w):
        for y in range(h):
            if x in (0, w - 1) or y in (0, h - 1):
                im.putpixel((x0 + x, y0 + y), iron_dark + (255,))
            elif x in (1, w - 2) or y in (1, h - 2):
                if kind != "pavise" or y in (1, h - 2):
                    im.putpixel((x0 + x, y0 + y), iron + (255,))
    if kind == "pavise":
        # la arista del medio y un emblema
        for y in range(2, h - 2):
            im.putpixel((x0 + 5, y0 + y), iron_dark + (255,))
            im.putpixel((x0 + 6, y0 + y), iron + (255,))
        dr.ellipse((x0 + 3, y0 + 7, x0 + 8, y0 + 12), fill=(214, 176, 70, 255), outline=(150, 112, 40, 255))
    else:
        # bandas de hierro y el umbo
        for y in (6, 15):
            for x in range(1, w - 1):
                im.putpixel((x0 + x, y0 + y), iron_dark + (255,))
        dr.ellipse((x0 + 3, y0 + 8, x0 + 8, y0 + 13), fill=iron + (255,), outline=iron_dark + (255,))
        im.putpixel((x0 + 5, y0 + 10), (230, 230, 236, 255))
        for (x, y) in ((2, 3), (9, 3), (2, 18), (9, 18)):
            im.putpixel((x0 + x, y0 + y), (220, 220, 226, 255))
    # el dorso: madera lisa
    for x in range(w):
        for y in range(h):
            im.putpixel((14 + x, 1 + y), tuple(int(v * (0.8 if x % 3 == 2 else 0.95)) for v in oak_dark) + (255,))
    return im


def shield_icon(tex, kind):
    front = tex.crop((1, 1, 13, 23))
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    if kind == "pavise":
        f = front.resize((10, 16), Image.NEAREST)
        icon.paste(f, (3, 0))
    else:
        f = front.resize((9, 15), Image.NEAREST)
        icon.paste(f, (3, 1))
    return icon


SHIELDS = {
    "reinforced_shield": ("Escudo reforzado", "Reinforced Shield",
                          "Tablas con cantos y bandas de hierro: aguanta el doble y las hachas no lo bajan.",
                          "Planks with iron edges and bands: twice as tough, and axes can't knock it down."),
    "pavise": ("Pavés", "Pavise",
               "Un escudo alto de cuerpo entero: aguanta muchísimo, las hachas no lo bajan y con él en alto no te empujan. Pesa: vas más lento con él en la mano.",
               "A tall full-body shield: very tough, axes can't knock it down and you can't be pushed while it's raised. Heavy: slower to walk with."),
}


def main():
    textures = {}
    item_dir = os.path.join(ASSETS, "textures/item")
    folder = os.path.join(ASSETS, "textures/models/armor")
    es, en = {}, {}
    for name, s in SETS.items():
        l1, l2 = layers(name)
        textures[name] = (l1, l2)
        Image.fromarray(l1, "RGBA").save(os.path.join(folder, "%s_layer_1.png" % name))
        Image.fromarray(l2, "RGBA").save(os.path.join(folder, "%s_layer_2.png" % name))
        for i, piece in enumerate(PIECES):
            item = "%s_%s" % (name, piece)
            CA.icon(l1, l2, piece).save(os.path.join(item_dir, item + ".png"))
            A.write_json(os.path.join(ASSETS, "models/item", item + ".json"),
                         {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, item)}})
            pattern = [r.replace("A", "X") for r in A.SHAPES[piece]]
            A.write_json(os.path.join(DATA, "recipe", item + ".json"), {
                "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
                "key": {"X": {"item": s["item"]}}, "result": {"id": "%s:%s" % (NS, item), "count": 1}})
            es["item.%s.%s" % (NS, item)] = s["es"][i]
            en["item.%s.%s" % (NS, item)] = s["en"][i]
    for piece, tag in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"), ("boots", "foot_armor")):
        CA.merge_tag(tag, ["%s:%s_%s" % (NS, name, piece) for name in SETS])
    CA.merge_tag("trimmable_armor", ["%s:%s_%s" % (NS, name, piece) for name in SETS for piece in PIECES])
    es["armor.%s.plate.weight" % NS] = "Pesada: −3 %% de velocidad por pieza"
    en["armor.%s.plate.weight" % NS] = "Heavy: −3%% movement speed per piece"
    # escudos
    shield_dir = os.path.join(ASSETS, "textures/entity/shield")
    os.makedirs(shield_dir, exist_ok=True)
    for kind, (e, n, de, dn) in SHIELDS.items():
        tex = shield_texture("pavise" if kind == "pavise" else "reinforced")
        tex.save(os.path.join(shield_dir, kind + ".png"))
        shield_icon(tex, "pavise" if kind == "pavise" else "reinforced").save(os.path.join(item_dir, kind + ".png"))
        A.write_json(os.path.join(ASSETS, "models/item", kind + ".json"), {
            "parent": "minecraft:item/shield", "textures": {"particle": "%s:item/%s" % (NS, kind)},
            "overrides": [{"predicate": {"minecraft:blocking": 1}, "model": "%s:item/%s_blocking" % (NS, kind)}]})
        A.write_json(os.path.join(ASSETS, "models/item", kind + "_blocking.json"), {
            "parent": "minecraft:item/shield_blocking", "textures": {"particle": "%s:item/%s" % (NS, kind)}})
        es["item.%s.%s" % (NS, kind)] = e
        en["item.%s.%s" % (NS, kind)] = n
        es["item.%s.%s.desc" % (NS, kind)] = de
        en["item.%s.%s.desc" % (NS, kind)] = dn
    I, P_, S = NS + ":iron_plate", "#minecraft:planks", NS + ":steel_plate"
    A.write_json(os.path.join(DATA, "recipe", "reinforced_shield.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["IPI", "PSP", "IPI"],
        "key": {"I": {"item": I}, "P": {"tag": P_[1:]}, "S": {"item": "minecraft:shield"}}, "result": {"id": NS + ":reinforced_shield", "count": 1}})
    A.write_json(os.path.join(DATA, "recipe", "pavise.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["SPS", "PRP", "SPS"],
        "key": {"S": {"item": S}, "P": {"tag": P_[1:]}, "R": {"item": NS + ":reinforced_shield"}}, "result": {"id": NS + ":pavise", "count": 1}})
    A.ASSETS = ASSETS
    A.merge_lang(es, en)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        frames = []
        for name in SETS:
            l1, l2 = textures[name]
            frames.append(A.label(CA.figure(l1, l2, (-2.0, 1.9, 4.0)), name))
            frames.append(A.label(CA.figure(l1, l2, (2.1, 1.9, -4.0)), "detrás"))
        A.grid(frames, 4).save(os.path.join(PREVIEW, "plate_armor.png"))
        row = Image.new("RGBA", (10 * 68, 68), (60, 60, 66, 255))
        k = 0
        for name in SETS:
            for piece in PIECES:
                ic = Image.open(os.path.join(item_dir, "%s_%s.png" % (name, piece))).resize((64, 64), Image.NEAREST)
                row.alpha_composite(ic, (2 + k * 68, 2))
                k += 1
        for kind in SHIELDS:
            ic = Image.open(os.path.join(item_dir, kind + ".png")).resize((64, 64), Image.NEAREST)
            row.alpha_composite(ic, (2 + k * 68, 2))
            k += 1
        row.save(os.path.join(PREVIEW, "plate_icons.png"))
        big = Image.new("RGBA", (2 * 256, 256), (60, 60, 66, 255))
        for i, kind in enumerate(SHIELDS):
            t = Image.open(os.path.join(shield_dir, kind + ".png")).resize((256, 256), Image.NEAREST)
            big.alpha_composite(t, (i * 256, 0))
        big.save(os.path.join(PREVIEW, "shield_tex.png"))
    print("armaduras de placas: %d conjuntos; escudos: %d" % (len(SETS), len(SHIELDS)))


if __name__ == "__main__":
    main()
