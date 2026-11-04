"""Ropa para el frío y el calor (fase 14, clima): el conjunto de piel (gorro, abrigo, pantalones y botas de piel) y el
de lino (sombrero de paja, túnica, calzones y sandalias). Con el aspecto de las armaduras de vanilla: las dos capas de
textura (64x32), los iconos, los modelos, las recetas, las etiquetas y los textos.

Uso: python3 tools/ew_climate_wear.py [raíz del proyecto] [--preview carpeta]
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ew_armor as A  # noqa: E402
import ew_armor_paint as P  # noqa: E402
import ew_class_armor as C  # noqa: E402

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
HEAD, BODY, ARM, LEG = C.HEAD, C.BODY, C.ARM, C.LEG

# conjunto -> piezas (id, español, inglés), materiales y receta
SETS = {
    "fur": {
        "pieces": [("fur_hood", "Gorro de piel", "Fur Hat"), ("fur_coat", "Abrigo de piel", "Fur Coat"),
                   ("fur_trousers", "Pantalones de piel", "Fur Trousers"), ("fur_boots", "Botas de piel", "Fur Boots")],
        "mats": {"m": {"kind": "fur", "c": (128, 92, 60)}, "trim": {"kind": "fur", "c": (232, 226, 214)},
                 "strap": {"kind": "leather", "c": (82, 52, 32)}, "dark": {"kind": "flat", "c": (60, 40, 26)},
                 "gold": {"kind": "gold", "c": (196, 160, 70)}},
        "keys": [{"X": {"item": "minecraft:leather"}, "A": {"tag": "minecraft:wool"}}] * 4,
    },
    "linen": {
        "pieces": [("straw_hat", "Sombrero de paja", "Straw Hat"), ("linen_tunic", "Túnica de lino", "Linen Tunic"),
                   ("linen_trousers", "Calzones de lino", "Linen Trousers"), ("sandals", "Sandalias", "Sandals")],
        "mats": {"m": {"kind": "cloth", "c": (226, 216, 186)}, "straw": {"kind": "fur", "c": (214, 182, 104)},
                 "ribbon": {"kind": "cloth", "c": (170, 52, 44)}, "strap": {"kind": "leather", "c": (122, 82, 48)},
                 "dark": {"kind": "flat", "c": (150, 140, 116)}, "trim": {"kind": "cloth", "c": (196, 184, 150)}},
        "keys": [{"X": {"item": "minecraft:wheat"}, "A": {"item": "minecraft:string"}},
                 {"X": {"item": "minecraft:string"}, "A": {"tag": "minecraft:wool"}},
                 {"X": {"item": "minecraft:string"}, "A": {"tag": "minecraft:wool"}},
                 {"X": {"item": "minecraft:leather"}, "A": {"item": "minecraft:string"}}],
    },
}


def decals(kind):
    if kind == "fur":
        return {
            # gorro con orejeras: piel por arriba, borde blanco y la cara al aire
            "head": [("band", "sides", 5, 8, "trim"), ("band", "N", 4, 5, "trim")],
            "body": [("band", "sides", 0, 2, "trim"), ("vband", "N", 3, 5, "trim"), ("band", "sides", 9, 10, "strap"),
                     ("rect", "N", 3, 9, 2, 1, "gold"), ("band", "sides", 10, 12, "trim")],
            "arm": [("band", "sides", 9, 12, "trim")],
            "waist": [("band", "sides", 8, 9, "strap")],
            "leg": [("band", "sides", 9, 10, "dark")],
            "boot": [("band", "sides", 7, 9, "trim"), ("band", "sides", 11, 12, "dark")],
        }
    return {
        # sombrero de paja de ala ancha con cinta roja
        "head": [("rect", "U", 0, 0, 8, 8, "straw"), ("band", "sides", 0, 3, "straw"), ("band", "sides", 3, 4, "ribbon")],
        "body": [("vband", "N", 3, 5, "trim"), ("band", "sides", 9, 10, "strap"), ("band", "sides", 0, 1, "trim")],
        "arm": [("band", "sides", 4, 5, "trim")],
        "waist": [("band", "sides", 8, 9, "strap")],
        "leg": [("band", "sides", 8, 9, "trim")],
        "boot": [("band", "sides", 10, 11, "strap"), ("band", "sides", 11, 12, "strap")],
    }


def layers(kind):
    m = SETS[kind]["mats"]
    d = decals(kind)
    rnd = P.new_rng(sum(ord(ch) for ch in kind) * 17)
    l1 = np.zeros((32, 64, 4), np.float32)
    l2 = np.zeros((32, 64, 4), np.float32)
    head = C.box("m" if kind == "fur" else "straw", (-4, -8, -4, 8, 8, 8), HEAD, d["head"] + [("hole", "N", 1, 3, 6, 4)])
    P.paint_box(l1, HEAD, head, m, rnd)
    if kind == "linen":
        # el sombrero solo cubre la parte de arriba de la cabeza (de lado, las 4 filas de arriba)
        C.clear(l1, P.box_regions(*HEAD, 8, 8, 8), "NSEW", rows=None)
        regions = P.box_regions(*HEAD, 8, 8, 8)
        head2 = np.zeros_like(l1)
        P.paint_box(head2, HEAD, C.box("straw", (-4, -8, -4, 8, 8, 8), HEAD, d["head"]), m, rnd)
        for f in "NSEW":
            x, y, fw, fh = regions[f]
            l1[y:y + 4, x:x + fw] = head2[y:y + 4, x:x + fw]
    body = C.box("m", (-4, 0, -2, 8, 12, 4), BODY, d["body"])
    P.paint_box(l1, BODY, body, m, rnd)
    arm = C.box("m", (-3, -2, -2, 4, 12, 4), ARM, d["arm"])
    P.paint_box(l1, ARM, arm, m, rnd)
    if kind == "linen":
        # mangas cortas
        C.clear(l1, P.box_regions(*ARM, 4, 12, 4), "D")
        regions = P.box_regions(*ARM, 4, 12, 4)
        for f in "NSEW":
            x, y, fw, fh = regions[f]
            l1[y + 6:y + fh, x:x + fw, 3] = 0
    boot = C.box("m" if kind == "fur" else "strap", (-2, 0, -2, 4, 12, 4), LEG, d["boot"])
    P.paint_box(l1, LEG, boot, m, rnd)
    # botas: solo la parte de abajo (las sandalias, solo la suela y las tiras)
    C.clear(l1, P.box_regions(*LEG, 4, 12, 4), "NSEWU", rows=7 if kind == "fur" else 10)
    waist = C.box("m", (-4, 0, -2, 8, 12, 4), BODY, d["waist"])
    P.paint_box(l2, BODY, waist, m, rnd)
    C.clear(l2, P.box_regions(*BODY, 8, 12, 4), "NSEWU", rows=7)
    leg = C.box("m", (-2, 0, -2, 4, 12, 4), LEG, d["leg"])
    P.paint_box(l2, LEG, leg, m, rnd)
    return l1.astype(np.uint8), l2.astype(np.uint8)


def recipe(kind, index, piece):
    item = "%s:%s" % (NS, SETS[kind]["pieces"][index][0])
    return {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": list(A.SHAPES[piece]),
            "key": SETS[kind]["keys"][index], "result": {"id": item, "count": 1}}


def lang():
    es, en = {}, {}
    for kind, s in SETS.items():
        for pid, e, n in s["pieces"]:
            es["item.%s.%s" % (NS, pid)] = e
            en["item.%s.%s" % (NS, pid)] = n
    return es, en


def main():
    textures = {}
    folder = os.path.join(ASSETS, "textures/models/armor")
    os.makedirs(folder, exist_ok=True)
    for kind, s in SETS.items():
        l1, l2 = layers(kind)
        textures[kind] = (l1, l2)
        Image.fromarray(l1, "RGBA").save(os.path.join(folder, "%s_clothes_layer_1.png" % kind))
        Image.fromarray(l2, "RGBA").save(os.path.join(folder, "%s_clothes_layer_2.png" % kind))
        for i, piece in enumerate(A.PIECES):
            item = s["pieces"][i][0]
            C.icon(l1, l2, piece).save(os.path.join(ASSETS, "textures/item", item + ".png"))
            A.write_json(os.path.join(ASSETS, "models/item", item + ".json"),
                         {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, item)}})
            A.write_json(os.path.join(DATA, "recipe", item + ".json"), recipe(kind, i, piece))
    for i, tag in enumerate(("head_armor", "chest_armor", "leg_armor", "foot_armor")):
        C.merge_tag(tag, ["%s:%s" % (NS, s["pieces"][i][0]) for s in SETS.values()])
    A.ASSETS = ASSETS
    A.merge_lang(*lang())
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        frames = []
        for kind, (l1, l2) in textures.items():
            frames.append(A.label(C.figure(l1, l2, (-2.0, 1.9, 4.0)), kind))
            frames.append(A.label(C.figure(l1, l2, (2.1, 1.9, -4.0)), "detrás"))
        A.grid(frames, 4).save(os.path.join(PREVIEW, "climate_wear.png"))
        row = Image.new("RGBA", (8 * 68, 68), (60, 60, 66, 255))
        n = 0
        for s in SETS.values():
            for pid, _, _ in s["pieces"]:
                ic = Image.open(os.path.join(ASSETS, "textures/item", pid + ".png")).resize((64, 64), Image.NEAREST)
                row.alpha_composite(ic, (2 + n * 68, 2))
                n += 1
        row.convert("RGB").save(os.path.join(PREVIEW, "climate_icons.png"))
    print("ropa del clima: 2 conjuntos, 8 piezas")


if __name__ == "__main__":
    main()
