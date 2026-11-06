"""El Nether bíblico, fase 1 (los biomas): los bloques nuevos (sal, sal fina, polvo de hueso, hueso calcinado, grietas del
Abismo, cristales de azufre, frutas de piedra y piedra maldita), las decoraciones de cada bioma (islas de sal, montones de
huesos, fósiles, calaveras, costillares, ruinas, pilares y árboles de sal, grietas que humean, cristales de azufre), los
iconos de las maldiciones, la silueta de las visiones y el ojo de la corrupción, y los textos.

El terreno, los biomas y sus criaturas están en tools/ew_hell.py.

Uso: python3 tools/ew_inferno.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import os
import random
import sys

from PIL import Image, ImageDraw

args = sys.argv[1:]
PREVIEW = None
if "--preview" in args:
    i = args.index("--preview")
    PREVIEW = args[i + 1]
    del args[i:i + 2]
ROOT = args[0] if args else os.path.join(os.path.dirname(__file__), "..")
NS = "evolvedwarfare"
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data", NS)
MC_DATA = os.path.join(RES, "data/minecraft")
VANILLA = "/home/claude/ref/mcassets/assets/minecraft/textures"


def write_json(path, obj, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, ensure_ascii=False, indent=indent)
        fh.write("\n")


def add_tag(path, values):
    t = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            t = json.load(fh)
    for v in values:
        if v not in t["values"]:
            t["values"].append(v)
    write_json(path, t)


def n(name):
    return "%s:%s" % (NS, name)


# ------------------------------------------------------------------ texturas

def noise_tex(base, var, seed, size=16):
    r = random.Random(seed)
    im = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            k = r.uniform(-var, var)
            im.putpixel((x, y), tuple(max(0, min(255, int(c * (1 + k)))) for c in base) + (255,))
    return im


def salt():
    im = noise_tex((230, 226, 218), 0.05, 1)
    r = random.Random(2)
    for _ in range(10):
        x, y = r.randrange(16), r.randrange(16)
        im.putpixel((x, y), (250, 250, 248, 255))
        im.putpixel(((x + 1) % 16, y), (204, 198, 188, 255))
    # cristales: líneas en diagonal
    for k in range(3):
        x0 = r.randrange(16)
        for t in range(4):
            im.putpixel(((x0 + t) % 16, (k * 5 + t) % 16), (214, 210, 204, 255))
    return im


def fine_salt():
    im = noise_tex((236, 232, 222), 0.07, 3)
    r = random.Random(4)
    for _ in range(30):
        im.putpixel((r.randrange(16), r.randrange(16)), (r.choice([200, 252]),) * 3 + (255,))
    return im


def bone_dust():
    im = noise_tex((176, 170, 156), 0.1, 5)
    r = random.Random(6)
    for _ in range(14):
        x, y = r.randrange(16), r.randrange(16)
        im.putpixel((x, y), (222, 216, 196, 255))
    for _ in range(4):
        # astillas de hueso
        x, y = r.randrange(14), r.randrange(16)
        for t in range(3):
            im.putpixel((x + t, y), (232, 226, 206, 255))
    return im


def charred_bone(top):
    src = Image.open(os.path.join(VANILLA, "block/bone_block_%s.png" % ("top" if top else "side"))).convert("RGBA")
    out = Image.new("RGBA", src.size)
    r = random.Random(7 + top)
    for y in range(src.height):
        for x in range(src.width):
            c = src.getpixel((x, y))
            k = 0.32 + r.uniform(-0.05, 0.05)
            burnt = (int(c[0] * k + 18), int(c[1] * k + 8), int(c[2] * k + 6), 255)
            if r.random() < 0.05:
                burnt = (150, 60, 20, 255)
            out.putpixel((x, y), burnt)
    return out


def abyss_vent():
    src = Image.open(os.path.join(VANILLA, "block/blackstone.png")).convert("RGBA").resize((16, 16))
    im = src.copy()
    d = ImageDraw.Draw(im)
    r = random.Random(9)
    for _ in range(3):
        x, y = r.randrange(16), r.randrange(16)
        pts = [(x, y)]
        for _ in range(5):
            x = max(0, min(15, x + r.choice([-1, 0, 1])))
            y = max(0, min(15, y + r.choice([1, 1, -1])))
            pts.append((x, y))
        d.line(pts, fill=(8, 6, 6, 255))
        for (px, py) in pts[1::2]:
            im.putpixel((min(15, px + 1), py), (60, 18, 12, 255))
    return im


def cursed_netherrack():
    src = Image.open(os.path.join(VANILLA, "block/netherrack.png")).convert("RGBA").resize((16, 16))
    im = Image.new("RGBA", (16, 16))
    r = random.Random(11)
    for y in range(16):
        for x in range(16):
            c = src.getpixel((x, y))
            im.putpixel((x, y), (int(c[0] * 0.45), int(c[1] * 0.25), int(c[2] * 0.5 + 20), 255))
    for _ in range(3):
        x, y = r.randrange(16), r.randrange(16)
        for t in range(6):
            im.putpixel(((x + t) % 16, (y + t // 2) % 16), (120, 40, 160, 255))
    return im


def sulfur_crystal():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    shards = [(8, 15, 8, 2, 2), (5, 15, 4, 7, 1), (11, 15, 12, 6, 1), (7, 15, 6, 9, 1)]
    for (bx, by, tx, ty, w) in shards:
        d.line([(bx, by), (tx, ty)], fill=(226, 196, 40, 255), width=w + 1)
        d.line([(bx - 1, by), (tx - 1, ty + 1)], fill=(170, 130, 20, 255), width=1)
        im.putpixel((tx, ty), (255, 248, 170, 255))
    return im


def stone_fruit():
    im = noise_tex((150, 146, 140), 0.08, 13)
    r = random.Random(14)
    for _ in range(12):
        im.putpixel((r.randrange(16), r.randrange(16)), (196, 192, 186, 255))
    # el rabito
    for y in range(0, 4):
        im.putpixel((8, y), (110, 104, 98, 255))
    return im


def effect_icon(kind):
    im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if kind == "fever":
        d.ellipse((3, 3, 14, 14), fill=(200, 60, 30, 255), outline=(120, 20, 10, 255))
        d.line((9, 5, 9, 12), fill=(255, 220, 160, 255), width=2)
        d.point((9, 14), fill=(255, 220, 160, 255))
    elif kind == "salt_thirst":
        d.polygon([(9, 2), (14, 11), (9, 16), (4, 11)], fill=(236, 232, 222, 255), outline=(150, 140, 120, 255))
        d.line((6, 10, 12, 10), fill=(180, 170, 150, 255))
    elif kind == "idol_eyes":
        d.ellipse((2, 5, 16, 13), fill=(30, 20, 20, 255), outline=(180, 140, 40, 255))
        d.ellipse((7, 6, 11, 12), fill=(230, 40, 20, 255))
        d.point((9, 9), fill=(0, 0, 0, 255))
    else:  # judgement_heat
        d.polygon([(9, 1), (14, 9), (12, 16), (6, 16), (4, 9)], fill=(255, 140, 30, 255), outline=(170, 50, 10, 255))
        d.polygon([(9, 7), (11, 12), (9, 15), (7, 12)], fill=(255, 230, 120, 255))
    return im


def vision():
    """Una figura con túnica y ojos que brillan, de espaldas a la luz (32x64)."""
    im = Image.new("RGBA", (32, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((10, 4, 22, 18), fill=(8, 4, 6, 230))
    d.polygon([(16, 10), (28, 62), (4, 62)], fill=(10, 5, 7, 215))
    d.polygon([(10, 18), (2, 40), (6, 42), (12, 24)], fill=(10, 5, 7, 190))
    d.polygon([(22, 18), (30, 40), (26, 42), (20, 24)], fill=(10, 5, 7, 190))
    for x in (13, 18):
        d.rectangle((x, 10, x + 1, 11), fill=(255, 60, 30, 255))
    # el borde deshilachado
    r = random.Random(15)
    for x in range(4, 29):
        for y in range(56, 64):
            if r.random() < 0.35:
                im.putpixel((x, y), (0, 0, 0, 0))
    return im


def corruption_icon():
    im = Image.new("RGBA", (18, 9), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for k, col in enumerate(((60, 40, 60, 255), (150, 40, 190, 255))):
        x0 = k * 9
        d.ellipse((x0, 1, x0 + 8, 7), fill=(20, 10, 20, 255), outline=col)
        d.ellipse((x0 + 3, 2, x0 + 5, 6), fill=col)
    return im


# ------------------------------------------------------------------ bloques

CUBES = {"salt_block": salt, "fine_salt": fine_salt, "bone_dust": bone_dust, "abyss_vent": abyss_vent, "cursed_netherrack": cursed_netherrack}


def models():
    tex = os.path.join(ASSETS, "textures/block")
    for name, fn in CUBES.items():
        fn().save(os.path.join(tex, name + ".png"))
        write_json(os.path.join(ASSETS, "models/block/%s.json" % name), {"parent": "minecraft:block/cube_all", "textures": {"all": n("block/" + name)}})
        write_json(os.path.join(ASSETS, "blockstates/%s.json" % name), {"variants": {"": {"model": n("block/" + name)}}})
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name), {"parent": n("block/" + name)})
    # el hueso calcinado (un pilar)
    charred_bone(False).save(os.path.join(tex, "charred_bone_block_side.png"))
    charred_bone(True).save(os.path.join(tex, "charred_bone_block_top.png"))
    write_json(os.path.join(ASSETS, "models/block/charred_bone_block.json"), {"parent": "minecraft:block/cube_column", "textures": {
        "end": n("block/charred_bone_block_top"), "side": n("block/charred_bone_block_side")}})
    write_json(os.path.join(ASSETS, "blockstates/charred_bone_block.json"), {"variants": {
        "axis=y": {"model": n("block/charred_bone_block")},
        "axis=x": {"model": n("block/charred_bone_block"), "x": 90, "y": 90},
        "axis=z": {"model": n("block/charred_bone_block"), "x": 90}}})
    write_json(os.path.join(ASSETS, "models/item/charred_bone_block.json"), {"parent": n("block/charred_bone_block")})
    # los cristales de azufre (como la amatista: según hacia dónde crecen)
    sulfur_crystal().save(os.path.join(tex, "sulfur_crystal.png"))
    write_json(os.path.join(ASSETS, "models/block/sulfur_crystal.json"), {"parent": "minecraft:block/cross", "textures": {"cross": n("block/sulfur_crystal")}})
    rot = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}
    write_json(os.path.join(ASSETS, "blockstates/sulfur_crystal.json"), {"variants": {"facing=%s" % k: dict({"model": n("block/sulfur_crystal")}, **v)
                                                                                     for k, v in rot.items()}})
    write_json(os.path.join(ASSETS, "models/item/sulfur_crystal.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": n("block/sulfur_crystal")}})
    # la fruta de piedra (pequeña, colgando)
    stone_fruit().save(os.path.join(tex, "stone_fruit.png"))
    fruit = {"parent": "minecraft:block/block", "textures": {"particle": n("block/stone_fruit"), "t": n("block/stone_fruit")}, "elements": [
        {"from": [5, 3, 5], "to": [11, 10, 11], "faces": {k: {"texture": "#t", "uv": [5, 5, 11, 12]} for k in ("north", "south", "east", "west")}
         | {"up": {"texture": "#t", "uv": [5, 5, 11, 11]}, "down": {"texture": "#t", "uv": [5, 5, 11, 11]}}},
        {"from": [7.5, 10, 7.5], "to": [8.5, 16, 8.5], "faces": {k: {"texture": "#t", "uv": [8, 0, 9, 4]} for k in ("north", "south", "east", "west")}},
    ]}
    write_json(os.path.join(ASSETS, "models/block/stone_fruit.json"), fruit)
    write_json(os.path.join(ASSETS, "blockstates/stone_fruit.json"), {"variants": {"": {"model": n("block/stone_fruit")}}})
    write_json(os.path.join(ASSETS, "models/item/stone_fruit.json"), {"parent": n("block/stone_fruit")})
    # botín
    for name in list(CUBES) + ["charred_bone_block"]:
        if name == "abyss_vent":
            drop = "minecraft:blackstone"
        elif name == "cursed_netherrack":
            drop = "minecraft:netherrack"
        else:
            drop = n(name)
        write_json(os.path.join(DATA, "loot_table/blocks/%s.json" % name), {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
            "conditions": [{"condition": "minecraft:survives_explosion"}], "entries": [{"type": "minecraft:item", "name": drop}]}]})
    write_json(os.path.join(DATA, "loot_table/blocks/sulfur_crystal.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
        "entries": [{"type": "minecraft:item", "name": n("brimstone"), "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}, "add": False}]}]}]})
    add_tag(os.path.join(MC_DATA, "tags/block/mineable/pickaxe.json"), [n("salt_block"), n("abyss_vent"), n("cursed_netherrack"), n("charred_bone_block"),
                                                                         n("sulfur_crystal")])
    add_tag(os.path.join(MC_DATA, "tags/block/mineable/shovel.json"), [n("fine_salt"), n("bone_dust")])
    # recetas: la sal se rompe en sal fina
    write_json(os.path.join(DATA, "recipe/fine_salt.json"), {"type": "minecraft:crafting_shapeless", "category": "building",
                                                            "ingredients": [{"item": n("salt_block")}], "result": {"id": n("fine_salt"), "count": 2}})
    write_json(os.path.join(DATA, "recipe/bone_meal_from_bone_dust.json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                                           "ingredients": [{"item": n("bone_dust")}], "result": {"id": "minecraft:bone_meal", "count": 3}})


# ------------------------------------------------------------------ las decoraciones de los biomas

def placed(feature, placement):
    return {"feature": feature, "placement": placement}


BIOME_F = {"type": "minecraft:biome"}
SQUARE = {"type": "minecraft:in_square"}


def count(k):
    return {"type": "minecraft:count", "count": k}


def rarity(k):
    return {"type": "minecraft:rarity_filter", "chance": k}


def heightmap(kind="WORLD_SURFACE_WG"):
    return {"type": "minecraft:heightmap", "heightmap": kind}


def uniform_height(lo, hi):
    return {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}}


def on(blocks):
    return {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
        {"type": "minecraft:replaceable"}, {"type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": blocks}]}}


FOSSILS = ["minecraft:fossil/spine_%d" % i for i in range(1, 5)] + ["minecraft:fossil/skull_%d" % i for i in range(1, 5)]


def features():
    cf = {}
    pf = {}
    cf["salt_island"] = {"type": n("boulder"), "config": {"state": {"Name": n("salt_block")}}}
    pf["salt_island"] = placed(n("salt_island"), [count(3), SQUARE, heightmap("MOTION_BLOCKING"), BIOME_F])
    cf["bone_pile"] = {"type": "minecraft:block_pile", "config": {"state_provider": {"type": "minecraft:weighted_state_provider", "entries": [
        {"weight": 3, "data": {"Name": "minecraft:bone_block", "Properties": {"axis": "y"}}},
        {"weight": 2, "data": {"Name": n("charred_bone_block"), "Properties": {"axis": "x"}}}]}}}
    pf["bone_pile"] = placed(n("bone_pile"), [rarity(2), SQUARE, heightmap("MOTION_BLOCKING_NO_LEAVES"), BIOME_F])
    cf["sunken_ruin"] = {"type": n("ruin"), "config": {}}
    pf["sunken_ruin"] = placed(n("sunken_ruin"), [rarity(10), SQUARE, heightmap("MOTION_BLOCKING"), BIOME_F])
    cf["bone_fossil"] = {"type": "minecraft:fossil", "config": {"fossil_structures": FOSSILS, "overlay_structures": FOSSILS,
                                                                "fossil_processors": "minecraft:fossil_rot", "overlay_processors": "minecraft:fossil_rot",
                                                                "max_empty_corners_allowed": 6}}
    pf["bone_fossil"] = placed(n("bone_fossil"), [rarity(4), SQUARE, heightmap(), {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": -3},
                                                    BIOME_F])
    skulls = [{"weight": 1, "data": {"Name": "minecraft:skeleton_skull", "Properties": {"rotation": str(r)}}} for r in range(0, 16, 2)]
    skulls.append({"weight": 1, "data": {"Name": "minecraft:wither_skeleton_skull", "Properties": {"rotation": "6"}}})
    cf["skull_patch"] = {"type": "minecraft:random_patch", "config": {"tries": 8, "xz_spread": 7, "y_spread": 3, "feature": {
        "feature": {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:weighted_state_provider", "entries": skulls}}},
        "placement": [on([n("bone_dust"), "minecraft:bone_block", "minecraft:soul_soil"])]}}}
    pf["skull_patch"] = placed(n("skull_patch"), [count(2), SQUARE, heightmap(), BIOME_F])
    cf["bone_spikes"] = {"type": n("ribcage"), "config": {}}
    pf["bone_spikes"] = placed(n("bone_spikes"), [rarity(5), SQUARE, heightmap(), BIOME_F])
    cf["ruin"] = {"type": n("ruin"), "config": {}}
    pf["ruin"] = placed(n("ruin"), [count(1), rarity(2), SQUARE, heightmap(), BIOME_F])
    cf["salt_pillar"] = {"type": n("salt_pillar"), "config": {}}
    pf["salt_pillar"] = placed(n("salt_pillar"), [count(3), SQUARE, heightmap(), BIOME_F])
    cf["salt_tree"] = {"type": n("salt_tree"), "config": {}}
    pf["salt_tree"] = placed(n("salt_tree"), [rarity(3), count(3), SQUARE, heightmap(), BIOME_F])
    cf["abyss_vents"] = {"type": "minecraft:ore", "config": {"size": 6, "discard_chance_on_air_exposure": 0.0, "targets": [
        {"target": {"predicate_type": "minecraft:block_match", "block": b}, "state": {"Name": n("abyss_vent")}}
        for b in ("minecraft:blackstone", "minecraft:basalt", "minecraft:smooth_basalt", "minecraft:netherrack")]}}
    pf["abyss_vents"] = placed(n("abyss_vents"), [count(45), SQUARE, uniform_height(10, 200), BIOME_F])
    cf["sulfur_vents"] = {"type": "minecraft:ore", "config": {"size": 4, "discard_chance_on_air_exposure": 0.0, "targets": [
        {"target": {"predicate_type": "minecraft:block_match", "block": b}, "state": {"Name": n("abyss_vent")}}
        for b in ("minecraft:blackstone", "minecraft:basalt")]}}
    pf["sulfur_vents"] = placed(n("sulfur_vents"), [count(12), SQUARE, uniform_height(40, 240), BIOME_F])
    cf["sulfur_crystals"] = {"type": "minecraft:random_patch", "config": {"tries": 24, "xz_spread": 6, "y_spread": 4, "feature": {
        "feature": {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:simple_state_provider",
                                                                              "state": {"Name": n("sulfur_crystal"), "Properties": {"facing": "up", "waterlogged": "false"}}}}},
        "placement": [on([n("brimstone_block")])]}}}
    pf["sulfur_crystals"] = placed(n("sulfur_crystals"), [count(4), SQUARE, heightmap(), BIOME_F])
    for k, v in cf.items():
        write_json(os.path.join(DATA, "worldgen/configured_feature/%s.json" % k), v)
    for k, v in pf.items():
        write_json(os.path.join(DATA, "worldgen/placed_feature/%s.json" % k), v)


# ------------------------------------------------------------------ textos

PHRASES = [
    ("Y el humo de su tormento sube por los siglos de los siglos.", "And the smoke of their torment rises for ever and ever."),
    ("No mires atrás.", "Do not look back."),
    ("Allí será el llanto y el crujir de dientes.", "There shall be weeping and gnashing of teeth."),
    ("El fuego que nunca se apaga.", "The fire that is never quenched."),
    ("Sal y azufre, toda su tierra quemada.", "Salt and brimstone, all its land burned."),
    ("Polvo eres...", "Dust you are..."),
    ("¿Dónde está tu luz ahora?", "Where is your light now?"),
    ("Pesado fuiste en la balanza...", "You have been weighed in the balance..."),
    ("Los huesos secos oyen.", "The dry bones hear."),
    ("Del abismo subieron como langostas.", "From the abyss they rose like locusts."),
    ("Mi nombre es Legión, porque somos muchos.", "My name is Legion, for we are many."),
    ("Ninguno que entra vuelve.", "None who go in return."),
    ("Las tinieblas de afuera.", "The outer darkness."),
    ("Él estaba allí cuando cayeron.", "He was there when they fell."),
    ("Busca el trono vacío.", "Seek the empty throne."),
    ("La puerta es ancha y el camino espacioso.", "Wide is the gate and broad is the way."),
    ("Se volvió estatua de sal.", "She became a pillar of salt."),
    ("Siete sellos. Ninguno entero.", "Seven seals. None whole."),
    ("El que tenga oídos, que oiga.", "He who has ears, let him hear."),
    ("Aún no es tu hora...", "It is not your hour yet..."),
]

TEXTS = {
    "block.evolvedwarfare.salt_block": ("Bloque de sal", "Salt Block"),
    "block.evolvedwarfare.fine_salt": ("Sal fina", "Fine Salt"),
    "block.evolvedwarfare.bone_dust": ("Polvo de hueso", "Bone Dust"),
    "block.evolvedwarfare.charred_bone_block": ("Hueso calcinado", "Charred Bone Block"),
    "block.evolvedwarfare.abyss_vent": ("Grieta del Abismo", "Abyss Vent"),
    "block.evolvedwarfare.sulfur_crystal": ("Cristal de azufre", "Sulfur Crystal"),
    "block.evolvedwarfare.stone_fruit": ("Fruta de piedra", "Stone Fruit"),
    "block.evolvedwarfare.cursed_netherrack": ("Piedra maldita", "Cursed Netherrack"),
    "effect.evolvedwarfare.fever": ("Fiebre", "Fever"),
    "effect.evolvedwarfare.salt_thirst": ("Sed de sal", "Salt Thirst"),
    "effect.evolvedwarfare.idol_eyes": ("Ojos del ídolo", "Idol's Eyes"),
    "effect.evolvedwarfare.judgement_heat": ("Calor del juicio", "Heat of Judgement"),
    "message.evolvedwarfare.hell.fever": ("El calor del lago te consume: tienes fiebre.", "The lake's heat consumes you: you have a fever."),
    "message.evolvedwarfare.hell.bones": ("La tierra cruje bajo tus pies...", "The ground creaks beneath your feet..."),
    "message.evolvedwarfare.hell.lights": ("Tu luz se apaga.", "Your light goes out."),
    "message.evolvedwarfare.hell.curses_lifted": ("Las maldiciones del Nether te abandonan.", "The Nether's curses leave you."),
    "message.evolvedwarfare.hell.corruption": ("La corrupción crece en ti (%s %%).", "Corruption grows in you (%s%%)."),
    "death.attack.evolvedwarfare.judgement": ("%1$s ardió bajo el calor del juicio", "%1$s burned under the heat of judgement"),
}
for k, (es, en) in enumerate(PHRASES):
    TEXTS["message.evolvedwarfare.hell.vision.%d" % k] = (es, en)


def main():
    models()
    features()
    effects = os.path.join(ASSETS, "textures/mob_effect")
    os.makedirs(effects, exist_ok=True)
    for kind in ("fever", "salt_thirst", "idol_eyes", "judgement_heat"):
        effect_icon(kind).save(os.path.join(effects, kind + ".png"))
    os.makedirs(os.path.join(ASSETS, "textures/entity/hell"), exist_ok=True)
    vision().save(os.path.join(ASSETS, "textures/entity/hell/vision.png"))
    corruption_icon().save(os.path.join(ASSETS, "textures/gui/corruption.png"))
    write_json(os.path.join(DATA, "tags/worldgen/structure/hell_landmarks.json"), {"replace": False, "values": [n("nether_citadel")]})
    write_json(os.path.join(DATA, "damage_type/judgement.json"), {"exhaustion": 0.0, "message_id": NS + ".judgement", "scaling": "never",
                                                                 "effects": "burning"})
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            lang = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        names = list(CUBES) + ["charred_bone_block_side", "sulfur_crystal", "stone_fruit"]
        sheet = Image.new("RGBA", (len(names) * 72 + 200, 140), (60, 30, 26, 255))
        for k, name in enumerate(names):
            im = Image.open(os.path.join(ASSETS, "textures/block", name + ".png")).resize((64, 64), Image.NEAREST)
            sheet.alpha_composite(im, (k * 72 + 4, 4))
        for k, kind in enumerate(("fever", "salt_thirst", "idol_eyes", "judgement_heat")):
            sheet.alpha_composite(effect_icon(kind).resize((54, 54), Image.NEAREST), (k * 60 + 4, 76))
        sheet.alpha_composite(vision().resize((64, 128), Image.NEAREST).crop((0, 0, 64, 128)).resize((32, 64)), (len(names) * 72 + 20, 70))
        sheet.save(os.path.join(PREVIEW, "inferno_blocks.png"))
    print("Nether bíblico (fase 1): bloques, decoraciones, maldiciones y textos")


if __name__ == "__main__":
    main()
