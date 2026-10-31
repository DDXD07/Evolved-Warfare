"""Armaduras de clase (arquero, guerrero, ladrón y mosquetero) en cuero, cobre, hierro, diamante y netherita, con el
aspecto de las de vanilla: las dos capas de textura (64x32, con el mismo reparto que las de Minecraft), los iconos, los
modelos de objeto, las recetas (la de netherita, en la mesa de herrería), las etiquetas y los textos.

Uso: python3 tools/ew_class_armor.py [raíz del proyecto] [--preview carpeta]
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ew_armor as A  # noqa: E402
import ew_armor_paint as P  # noqa: E402
import ew_armor_raster as R  # noqa: E402

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
PIECES = A.PIECES

# ------------------------------------------------------------------ materiales y clases

TIERS = {
    "leather": {"base": {"kind": "leather", "c": (134, 86, 52)}, "dark": (92, 58, 36), "trim": (164, 112, 70),
                "item": "minecraft:leather", "es": "de cuero", "en": "Leather"},
    "copper": {"base": {"kind": "plate", "c": (198, 112, 72), "rivets": True}, "dark": (140, 74, 46), "trim": (110, 176, 150),
               "item": "minecraft:copper_ingot", "es": "de cobre", "en": "Copper"},
    "iron": {"base": {"kind": "plate", "c": (206, 206, 210)}, "dark": (150, 150, 156), "trim": (236, 236, 238),
             "item": "minecraft:iron_ingot", "es": "de hierro", "en": "Iron"},
    "diamond": {"base": {"kind": "plate", "c": (96, 214, 208)}, "dark": (44, 150, 150), "trim": (200, 250, 246),
                "item": "minecraft:diamond", "es": "de diamante", "en": "Diamond"},
    "netherite": {"base": {"kind": "plate", "c": (74, 66, 70), "rivets": True}, "dark": (44, 38, 42), "trim": (118, 96, 104),
                  "item": None, "es": "de netherita", "en": "Netherite"},
}
TIER_ORDER = ["leather", "copper", "iron", "diamond", "netherite"]

CLASSES = {
    "archer": {"color": (94, 140, 58), "accent": "minecraft:feather",
               "es": ["Capucha de arquero", "Jubón de arquero", "Calzas de arquero", "Botas de arquero"], "es_name": "Arquero",
               "en": ["Archer's Hood", "Archer's Jerkin", "Archer's Leggings", "Archer's Boots"], "en_name": "Archer"},
    "warrior": {"color": (176, 53, 46), "accent": "minecraft:red_dye",
                "es": ["Yelmo de guerrero", "Coraza de guerrero", "Grebas de guerrero", "Botas de guerrero"], "es_name": "Guerrero",
                "en": ["Warrior's Helm", "Warrior's Cuirass", "Warrior's Greaves", "Warrior's Boots"], "en_name": "Warrior"},
    "thief": {"color": (52, 46, 60), "accent": "minecraft:ink_sac",
              "es": ["Capucha de ladrón", "Chaleco de ladrón", "Calzas de ladrón", "Botas de ladrón"], "es_name": "Ladrón",
              "en": ["Thief's Hood", "Thief's Vest", "Thief's Leggings", "Thief's Boots"], "en_name": "Thief"},
    "musketeer": {"color": (58, 95, 168), "accent": "minecraft:gunpowder",
                  "es": ["Morrión de mosquetero", "Casaca de mosquetero", "Calzones de mosquetero", "Botas de mosquetero"],
                  "es_name": "Mosquetero",
                  "en": ["Musketeer's Morion", "Musketeer's Tabard", "Musketeer's Breeches", "Musketeer's Boots"], "en_name": "Musketeer"},
}

# ------------------------------------------------------------------ texturas (reparto de vanilla)

HEAD, BODY, ARM, LEG = (0, 0), (16, 16), (40, 16), (0, 16)


def mats(cls, tier):
    t = TIERS[tier]
    c = CLASSES[cls]["color"]
    return {
        "m": t["base"],
        "dark": {"kind": "flat", "c": t["dark"]},
        "trim": {"kind": "flat", "c": t["trim"]},
        "cloth": {"kind": "cloth", "c": c},
        "cloth_dark": {"kind": "cloth", "c": tuple(int(v * 0.7) for v in c)},
        "strap": {"kind": "leather", "c": (92, 60, 38)},
        "white": {"kind": "cloth", "c": (232, 228, 214)},
        "gold": {"kind": "gold", "c": (214, 172, 66)},
        "feather": {"kind": "feather", "c": (236, 232, 224)},
        "black": {"kind": "flat", "c": (20, 18, 22)},
    }


def box(mat, size, uv, decals=()):
    b = A.Box(mat, *size, decals=decals)
    b.uv = uv
    return b


def class_decals(cls):
    """Los detalles de cada clase sobre el metal: casco, cuerpo, brazo, cintura, pierna y bota."""
    if cls == "archer":
        return {
            "head": [("band", "sides", 0, 3, "cloth"), ("rect", "U", 0, 0, 8, 8, "cloth"), ("band", "S", 0, 7, "cloth"),
                     ("px", "W", [(5, 3, "feather"), (6, 2, "feather"), (7, 1, "feather")])],
            "body": [("vband", "N", 2, 6, "cloth"), ("vband", "S", 2, 6, "cloth"), ("diag", "N", 0, 0.6, 1, "strap"),
                     ("diag", "S", 7, -0.6, 1, "strap"), ("band", "sides", 9, 10, "strap")],
            "arm": [("band", "sides", 0, 3, "cloth"), ("band", "sides", 8, 10, "strap")],
            "waist": [("band", "sides", 8, 9, "strap")],
            "leg": [("vband", "W", 1, 2, "cloth")],
            "boot": [("band", "sides", 7, 8, "cloth")],
        }
    if cls == "warrior":
        return {
            "head": [("vband", "U", 3, 5, "cloth"), ("band", "sides", 2, 3, "cloth"), ("vband", "N", 3, 5, "m")],
            "body": [("vband", "N", 1, 7, "cloth"), ("vband", "S", 1, 7, "cloth"), ("vband", "N", 3, 5, "gold"),
                     ("band", "N", 3, 4, "gold"), ("band", "sides", 9, 10, "strap")],
            "arm": [("band", "sides", 0, 1, "cloth"), ("band", "sides", 9, 10, "cloth")],
            "waist": [("band", "sides", 8, 9, "strap"), ("band", "sides", 10, 12, "cloth")],
            "leg": [("rect", "N", 0, 4, 4, 2, "trim")],
            "boot": [("band", "sides", 7, 8, "cloth")],
        }
    if cls == "thief":
        return {
            "head": [("rect", "U", 0, 0, 8, 8, "cloth"), ("band", "S", 0, 8, "cloth"), ("band", "W", 0, 6, "cloth"),
                     ("band", "E", 0, 6, "cloth"), ("band", "N", 0, 2, "cloth"), ("band", "N", 6, 8, "cloth_dark")],
            "body": [("vband", "W", 0, 4, "cloth"), ("vband", "E", 0, 4, "cloth"), ("band", "sides", 0, 2, "cloth"),
                     ("band", "sides", 8, 9, "strap"), ("rect", "N", 1, 9, 2, 2, "strap"), ("rect", "N", 5, 9, 2, 2, "strap")],
            "arm": [("band", "sides", 0, 5, "cloth"), ("band", "sides", 7, 10, "cloth_dark")],
            "waist": [("band", "sides", 8, 9, "strap")],
            "leg": [("band", "sides", 2, 5, "cloth")],
            "boot": [("band", "sides", 7, 9, "cloth")],
        }
    # mosquetero: casaca azul con la cruz blanca, banda azul y pluma en el morrión
    return {
        "head": [("band", "sides", 4, 5, "cloth"), ("px", "E", [(1, 0, "feather"), (2, 0, "feather"), (2, 1, "feather"), (3, 1, "feather")]),
                 ("vband", "U", 3, 5, "trim")],
        "body": [("vband", "N", 1, 7, "cloth"), ("vband", "S", 1, 7, "cloth"), ("vband", "N", 3, 5, "white"), ("band", "N", 3, 5, "white"),
                 ("vband", "S", 3, 5, "white"), ("band", "S", 3, 5, "white"), ("band", "sides", 9, 10, "strap")],
        "arm": [("band", "sides", 0, 2, "cloth"), ("band", "sides", 9, 10, "white")],
        "waist": [("band", "sides", 8, 9, "strap"), ("band", "sides", 10, 12, "cloth")],
        "leg": [("vband", "W", 1, 2, "gold")],
        "boot": [("band", "sides", 7, 9, "dark")],
    }


def clear(img, regions, faces, rows=None):
    """Hace transparente: caras enteras o, en las de los lados, las filas de arriba (hasta 'rows')."""
    for f in faces:
        if f not in regions:
            continue
        x, y, fw, fh = regions[f]
        if rows is None or f in "UD":
            img[y:y + fh, x:x + fw, 3] = 0
        else:
            img[y:y + rows, x:x + fw, 3] = 0


def layers(cls, tier):
    m = mats(cls, tier)
    d = class_decals(cls)
    rnd = P.new_rng(sum(ord(c) for c in cls + tier) * 31)
    l1 = np.zeros((32, 64, 4), np.float32)
    l2 = np.zeros((32, 64, 4), np.float32)
    # casco: con la cara al aire (rendija ancha) y algo de oscuro dentro
    hole = (1, 2, 6, 6) if cls == "thief" else (1, 3, 6, 4)
    head = box("m", (-4, -8, -4, 8, 8, 8), HEAD, d["head"] + [("hole", "N", *hole)])
    P.paint_box(l1, HEAD, head, m, rnd)
    body = box("m", (-4, 0, -2, 8, 12, 4), BODY, d["body"])
    P.paint_box(l1, BODY, body, m, rnd)
    arm = box("m", (-3, -2, -2, 4, 12, 4), ARM, d["arm"])
    P.paint_box(l1, ARM, arm, m, rnd)
    boot = box("m", (-2, 0, -2, 4, 12, 4), LEG, d["boot"])
    P.paint_box(l1, LEG, boot, m, rnd)
    clear(l1, P.box_regions(*LEG, 4, 12, 4), "NSEWU", rows=7)
    # capa 2: cintura y piernas
    waist = box("m", (-4, 0, -2, 8, 12, 4), BODY, d["waist"])
    P.paint_box(l2, BODY, waist, m, rnd)
    clear(l2, P.box_regions(*BODY, 8, 12, 4), "NSEWU", rows=7)
    leg = box("m", (-2, 0, -2, 4, 12, 4), LEG, d["leg"])
    P.paint_box(l2, LEG, leg, m, rnd)
    return l1.astype(np.uint8), l2.astype(np.uint8)


# ------------------------------------------------------------------ modelos de vanilla (para iconos y vistas)

def vanilla_armor(layer):
    """El modelo de armadura de vanilla con las coordenadas de textura de cada capa."""
    if layer == 1:
        parts = {
            "head": A.Node(_dil(box("m", (-4, -8, -4, 8, 8, 8), HEAD), 1.0)),
            "body": A.Node(_dil(box("m", (-4, 0, -2, 8, 12, 4), BODY), 1.0)),
            "right_arm": A.Node(_dil(box("m", (-3, -2, -2, 4, 12, 4), ARM), 1.0)),
            "right_boot": A.Node(_dil(box("m", (-2, 0, -2, 4, 12, 4), LEG), 1.0)),
        }
    else:
        parts = {
            "waist": A.Node(_dil(box("m", (-4, 0, -2, 8, 12, 4), BODY), 0.5)),
            "right_leg": A.Node(_dil(box("m", (-2, 0, -2, 4, 12, 4), LEG), 0.5)),
        }
    return A.finish({"id": "vanilla_%d" % layer, "parts": parts})


def _dil(b, dil):
    b.dil = dil
    return b


ARMOR1 = vanilla_armor(1)
ARMOR2 = vanilla_armor(2)


def quads(l1, l2, pose, pieces):
    out = []
    p1 = [p for p in pieces if p != "leggings"]
    if p1:
        out += A.armor_quads(ARMOR1, l1, pose, p1)
    if "leggings" in pieces:
        out += A.armor_quads(ARMOR2, l2, pose, ["leggings"])
    return out


def icon(l1, l2, piece, size=16, fill=14.5):
    """Icono 16x16: la pieza en proyección paralela, recortada a lo que se ve, sin mezclar colores, con contorno."""
    eye, target, pose = A.ICON_VIEW[piece]
    qs = quads(l1, l2, dict(pose), [piece])
    view = R.look(eye, target)
    sub = 6
    big = size * sub

    def render(view, scale):
        cv = R.Canvas(big, big, view, scale=scale)
        cv.boost = 1.2
        for world, uvs, t, flat in qs:
            cv.quad(world, uvs, t, flat)
        return cv.color

    pts = np.array([(view @ np.append(p, 1.0))[:2] for q in qs for p in q[0]])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    scale = big * 0.9 / max(hi - lo)
    v1 = R.T(-(lo + hi)[0] / 2, -(lo + hi)[1] / 2, 0) @ view
    img = render(v1, scale)
    ys, xs = np.nonzero(img[..., 3] > 127)
    # recortar a lo que se ve (las botas y la cintura tienen mucho transparente) y volver a encuadrar
    cx, cy = (xs.min() + xs.max() + 1) / 2, (ys.min() + ys.max() + 1) / 2
    span = max(xs.max() - xs.min(), ys.max() - ys.min()) + 1
    vx, vy = (cx - big / 2) / scale, (big / 2 - cy) / scale
    img = render(R.T(-vx, -vy, 0) @ v1, scale * fill * sub / span)
    out = np.zeros((size, size, 4), np.uint8)
    c = sub // 2
    for j in range(size):
        for i in range(size):
            blk = img[j * sub:(j + 1) * sub, i * sub:(i + 1) * sub]
            px = blk[c, c]
            if px[3] < 128:
                solid = blk[..., 3] > 127
                if solid.sum() < sub * sub * 0.5:
                    continue
                yy, xx = np.nonzero(solid)
                n = np.argmin((yy - c) ** 2 + (xx - c) ** 2)
                px = blk[yy[n], xx[n]]
            out[j, i, :3] = px[:3]
            out[j, i, 3] = 255
    alpha = out[..., 3] > 0
    edge = np.zeros_like(alpha)
    edge[1:] |= alpha[1:] & ~alpha[:-1]
    edge[:-1] |= alpha[:-1] & ~alpha[1:]
    edge[:, 1:] |= alpha[:, 1:] & ~alpha[:, :-1]
    edge[:, :-1] |= alpha[:, :-1] & ~alpha[:, 1:]
    edge[0] |= alpha[0]
    edge[-1] |= alpha[-1]
    edge[:, 0] |= alpha[:, 0]
    edge[:, -1] |= alpha[:, -1]
    out[edge, :3] = (out[edge, :3] * 0.6).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def figure(l1, l2, eye, target=(0, 1.0, 0), w=230, h=330, pose=None):
    pose = pose or {}
    cv = R.Canvas(w, h, R.look(eye, target), fov=40, bg=(236, 232, 222, 255))
    for world, uvs, t, flat in A.body_quads(pose) + quads(l1, l2, pose, PIECES):
        cv.quad(world, uvs, t, flat)
    return cv.image().convert("RGB")


# ------------------------------------------------------------------ datos

SHAPES = A.SHAPES


def recipe(cls, tier, piece):
    item = "%s:%s_%s_%s" % (NS, cls, tier, piece)
    if tier == "netherite":
        return {"type": "minecraft:smithing_transform",
                "template": {"item": "minecraft:netherite_upgrade_smithing_template"},
                "base": {"item": "%s:%s_diamond_%s" % (NS, cls, piece)},
                "addition": {"item": "minecraft:netherite_ingot"},
                "result": {"id": item, "count": 1}}
    return {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": list(SHAPES[piece]),
            "key": {"X": {"item": TIERS[tier]["item"]}, "A": {"item": CLASSES[cls]["accent"]}},
            "result": {"id": item, "count": 1}}


def lang():
    es, en = {}, {}
    for cls, c in CLASSES.items():
        es["armor_class.%s.%s" % (NS, cls)] = c["es_name"]
        en["armor_class.%s.%s" % (NS, cls)] = c["en_name"]
        for tier in TIER_ORDER:
            for i, piece in enumerate(PIECES):
                key = "item.%s.%s_%s_%s" % (NS, cls, tier, piece)
                es[key] = "%s %s" % (c["es"][i], TIERS[tier]["es"])
                en[key] = "%s %s" % (TIERS[tier]["en"], c["en"][i])
    for tier in TIER_ORDER:
        es["armor_tier.%s.%s" % (NS, tier)] = {"leather": "Cuero", "copper": "Cobre", "iron": "Hierro", "diamond": "Diamante",
                                               "netherite": "Netherita"}[tier]
        en["armor_tier.%s.%s" % (NS, tier)] = TIERS[tier]["en"]
    extra = {
        "hold": ("Mantén %s para ver las estadísticas", "Hold %s to see the stats"),
        "armor": ("Armadura: %s", "Armor: %s"),
        "toughness": ("Dureza: %s", "Toughness: %s"),
        "knockback": ("Resistencia al empuje: %s %%", "Knockback resistance: %s%%"),
        "set": ("Con el conjunto entero:", "With the full set:"),
        "bonus": ("%s %% %s", "%s%% %s"),
        "less_armor": ("−%s de armadura", "−%s armor"),
        "per_piece": ("Cada pieza da una cuarta parte.", "Each piece gives a quarter."),
        "effect.draw_speed": ("velocidad al tensar arcos y cargar ballestas", "bow draw and crossbow loading speed"),
        "effect.arrow_damage": ("daño de las flechas", "arrow damage"),
        "effect.melee_damage": ("daño cuerpo a cuerpo", "melee damage"),
        "effect.knockback": ("resistencia al empuje", "knockback resistance"),
        "effect.move_speed": ("velocidad al moverse", "movement speed"),
        "effect.backstab": ("daño por la espalda", "backstab damage"),
        "effect.reload_speed": ("velocidad de recarga de las armas de fuego", "firearm reload speed"),
        "effect.bullet_damage": ("daño de las balas", "bullet damage"),
    }
    for k, (e, n) in extra.items():
        es["armor_class.%s.%s" % (NS, k)] = e
        en["armor_class.%s.%s" % (NS, k)] = n
    return es, en


def merge_tag(name, values):
    path = os.path.join(ROOT, "src/main/resources/data/minecraft/tags/item", name + ".json")
    old = []
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            old = json.load(f).get("values", [])
    keep = [v for v in old if v not in values]
    A.write_json(path, {"replace": False, "values": keep + values})


def main():
    textures = {}
    for cls in CLASSES:
        for tier in TIER_ORDER:
            l1, l2 = layers(cls, tier)
            textures[(cls, tier)] = (l1, l2)
            folder = os.path.join(ASSETS, "textures/models/armor")
            os.makedirs(folder, exist_ok=True)
            Image.fromarray(l1, "RGBA").save(os.path.join(folder, "%s_%s_layer_1.png" % (cls, tier)))
            Image.fromarray(l2, "RGBA").save(os.path.join(folder, "%s_%s_layer_2.png" % (cls, tier)))
            for piece in PIECES:
                item = "%s_%s_%s" % (cls, tier, piece)
                icon(l1, l2, piece).save(os.path.join(ASSETS, "textures/item", item + ".png"))
                A.write_json(os.path.join(ASSETS, "models/item", item + ".json"),
                             {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, item)}})
                A.write_json(os.path.join(DATA, "recipe", item + ".json"), recipe(cls, tier, piece))
    for piece, tag in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"), ("boots", "foot_armor")):
        merge_tag(tag, ["%s:%s_%s_%s" % (NS, cls, tier, piece) for cls in CLASSES for tier in TIER_ORDER])
    merge_tag("trimmable_armor", ["%s:%s_%s_%s" % (NS, cls, tier, piece) for cls in CLASSES for tier in TIER_ORDER for piece in PIECES])
    A.ASSETS = ASSETS
    A.merge_lang(*lang())
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        previews(textures, PREVIEW)
    print("armaduras de clase: %d conjuntos, %d piezas" % (len(textures), 4 * len(textures)))


def previews(textures, out):
    for cls in CLASSES:
        frames = []
        for tier in TIER_ORDER:
            l1, l2 = textures[(cls, tier)]
            frames.append(A.label(figure(l1, l2, (-2.0, 1.9, 4.0)), "%s %s" % (CLASSES[cls]["es_name"], TIERS[tier]["es"])))
            frames.append(A.label(figure(l1, l2, (2.1, 1.9, -4.0)), "detrás"))
        A.grid(frames, 10).save(os.path.join(out, "class_%s.png" % cls))
    rows = []
    for cls in CLASSES:
        for tier in TIER_ORDER:
            row = Image.new("RGBA", (4 * 68, 68), (60, 60, 66, 255))
            for i, piece in enumerate(PIECES):
                ic = Image.open(os.path.join(ASSETS, "textures/item", "%s_%s_%s.png" % (cls, tier, piece))).resize((64, 64), Image.NEAREST)
                row.alpha_composite(ic, (2 + i * 68, 2))
            rows.append(row.convert("RGB"))
    A.grid(rows, 5).save(os.path.join(out, "class_icons.png"))


if __name__ == "__main__":
    main()
