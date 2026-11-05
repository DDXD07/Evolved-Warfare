"""La tala y los frutales (fase 21).

Frutales (manzano, peral, ciruelo, melocotonero, limonero y naranjo): las hojas en 4 edades (hojas, en flor, fruta verde
y fruta madura, en 3D colgando de las hojas, con 4 giros al azar), sus brotes, la fruta en 3D, los zumos, el botín (hojas:
brote, palos y fruta), la generación del mundo (árbol y dónde sale), etiquetas y textos. Y la tala: el hacha de sílex,
las recetas de palos -> losa -> tablones y los textos. Con --preview dibuja las hojas y la fruta.

Uso: python3 tools/ew_orchard.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import copy
import glob
import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ew_model3d import Model, Palette, render  # noqa: E402

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
MC_DATA = os.path.join(ROOT, "src/main/resources/data/minecraft")
VANILLA_TEX = "/home/claude/ref/mcassets/assets/minecraft/textures"


def write_json(path, obj, indent=1):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=indent)
        f.write("\n")


def add_tag(path, values):
    t = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            t = json.load(f)
    for v in values:
        if v not in t["values"]:
            t["values"].append(v)
    write_json(path, t, indent=2)


# ------------------------------------------------------------------ cada frutal

# id: (nombre es, nombre en, fruta es, fruta en, color maduro, acento, color verde, flor, hojas de vanilla, fruta de vanilla?)
FRUITS = collections.OrderedDict([
    ("apple", ("Manzano", "Apple Tree", None, None, (196, 34, 30), (236, 92, 60), (138, 176, 60), (246, 236, 240), "oak_leaves", "minecraft:apple")),
    ("pear", ("Peral", "Pear Tree", "Pera", "Pear", (196, 196, 72), (226, 168, 70), (126, 168, 62), (250, 250, 244), "oak_leaves", None)),
    ("plum", ("Ciruelo", "Plum Tree", "Ciruela", "Plum", (98, 38, 112), (140, 70, 150), (120, 160, 70), (250, 244, 250), "oak_leaves", None)),
    ("peach", ("Melocotonero", "Peach Tree", "Melocotón", "Peach", (240, 160, 90), (212, 70, 60), (150, 180, 80), (244, 160, 196), "oak_leaves", None)),
    ("lemon", ("Limonero", "Lemon Tree", "Limón", "Lemon", (244, 220, 60), (252, 240, 120), (110, 160, 50), (252, 252, 236), "jungle_leaves", None)),
    ("orange", ("Naranjo", "Orange Tree", "Naranja", "Orange", (240, 130, 20), (252, 168, 60), (100, 150, 46), (252, 252, 236), "jungle_leaves", None)),
])


def palette(fid):
    _, _, _, _, ripe, accent, unripe, blossom, _, _ = FRUITS[fid]
    p = Palette(sum(map(ord, fid)))
    p.add("ripe", ripe, pattern="spots", accent=accent)
    p.add("unripe", unripe, pattern="spots", accent=tuple(min(255, c + 20) for c in unripe))
    p.add("stem", (92, 66, 40))
    p.add("blossom", blossom, noise=0.04)
    p.add("center", (236, 200, 70), noise=0.03)
    p.add("leaf", (84, 136, 44), pattern="vein", accent=(110, 160, 60))
    return p


PALS = {fid: palette(fid) for fid in FRUITS}


def tex(fid):
    return "%s:block/orchard/%s" % (NS, fid)


def cube(m, cx, cy, cz, sx, sy, sz, mat):
    return m.box(cx - sx / 2, cy - sy / 2, cz - sz / 2, cx + sx / 2, cy + sy / 2, cz + sz / 2, mat)


def fruit(m, fid, cx, cy, cz, k, mat):
    """Una fruta centrada en (cx, cy, cz) (k = tamaño: 1 madura, <1 verde)."""
    if fid == "apple":
        cube(m, cx, cy, cz, 3.6 * k, 3.4 * k, 3.6 * k, mat)
    elif fid == "pear":
        cube(m, cx, cy - 0.7 * k, cz, 3.4 * k, 2.8 * k, 3.4 * k, mat)
        cube(m, cx, cy + 1.4 * k, cz, 2.2 * k, 2.0 * k, 2.2 * k, mat)
    elif fid == "plum":
        cube(m, cx, cy, cz, 2.6 * k, 3.0 * k, 2.6 * k, mat)
    elif fid == "peach":
        cube(m, cx, cy, cz, 3.6 * k, 3.6 * k, 3.6 * k, mat)
    elif fid == "lemon":
        cube(m, cx, cy, cz, 3.0 * k, 3.0 * k, 4.0 * k, mat)
        cube(m, cx, cy, cz, 1.0 * k, 1.0 * k, 5.2 * k, mat)
    elif fid == "orange":
        cube(m, cx, cy, cz, 3.6 * k, 3.6 * k, 3.6 * k, mat)
        cube(m, cx, cy, cz, 4.2 * k, 2.6 * k, 2.6 * k, mat)
        cube(m, cx, cy, cz, 2.6 * k, 2.6 * k, 4.2 * k, mat)


def height(fid, k):
    return {"pear": 4.8, "plum": 3.0, "orange": 3.6, "peach": 3.6, "apple": 3.4, "lemon": 3.0}[fid] * k


# dónde cuelgan: (x, z) debajo; y a los lados: (lado, a lo largo, altura)
UNDER = [(4.5, 5.0), (11.0, 10.5), (7.0, 12.0)]
SIDES = [("e", 4.5, 7.0), ("n", 11.0, 9.0), ("w", 9.5, 5.0)]


def hang(m, fid, k, mat, n_under, n_side):
    h = height(fid, k)
    for (x, z) in UNDER[:n_under]:
        cy = 0.6 - h / 2
        fruit(m, fid, x, cy, z, k, mat)
        m.box(x - 0.25, cy + h / 2 - 0.1, z - 0.25, x + 0.25, 1.2, z + 0.25, "stem")
    for (side, along, y) in SIDES[:n_side]:
        w = 3.6 * k
        if side == "e":
            fruit(m, fid, 16 + w / 2 - 0.9, y, along, k, mat)
        elif side == "w":
            fruit(m, fid, -w / 2 + 0.9, y, along, k, mat)
        elif side == "n":
            fruit(m, fid, along, y, -w / 2 + 0.9, k, mat)


def blossoms(m):
    for (x, z) in ((3, 4), (9, 2.5), (12.5, 8), (5.5, 11.5), (10, 13)):
        m.box(x, 16.0, z, x + 1.5, 16.5, z + 1.5, "blossom")
        m.box(x + 0.5, 16.3, z + 0.5, x + 1.0, 16.6, z + 1.0, "center")
    for (x, y) in ((4, 11), (11, 6)):
        m.box(16.0, y, x, 16.5, y + 1.5, x + 1.5, "blossom")
        m.box(x, y, -0.5, x + 1.5, y + 1.5, 0.0, "blossom")
    for (z, y) in ((7, 9),):
        m.box(-0.5, y, z, 0.0, y + 1.5, z + 1.5, "blossom")


def leaves_model(fid, age):
    leaves = "minecraft:block/" + FRUITS[fid][8]
    m = Model(PALS[fid], tex(fid), 100 + age)
    if age == 1:
        blossoms(m)
    elif age == 2:
        hang(m, fid, 0.65, "unripe", 2, 1)
    elif age == 3:
        hang(m, fid, 1.0, "ripe", 2, 2)
    out = m.json(ao=True)
    cube_faces = {f: {"uv": [0, 0, 16, 16], "texture": "#leaves", "tintindex": 0, "cullface": f}
                  for f in ("north", "south", "east", "west", "up", "down")}
    out["elements"] = [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": cube_faces}] + out["elements"]
    out["textures"]["leaves"] = leaves
    out["textures"]["particle"] = leaves
    return out


DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [0.8, 0.8, 0.8]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
}


def item_fruit(fid):
    """La fruta en la mano: grande, con su rabito y una hoja."""
    m = Model(PALS[fid], tex(fid), 7)
    fruit(m, fid, 8, 7, 8, 1.9, "ripe")
    top = 7 + height(fid, 1.9) / 2
    m.box(7.6, top - 0.3, 7.6, 8.4, top + 1.6, 8.4, "stem")
    m.box(8.4, top + 0.6, 7.4, 11.0, top + 1.1, 9.0, "leaf")
    return m


def sapling_texture(fid):
    base = Image.open(os.path.join(VANILLA_TEX, "block/oak_sapling.png")).convert("RGBA")
    im = base.copy()
    ripe = FRUITS[fid][4] + (255,)
    dark = tuple(int(c * 0.7) for c in FRUITS[fid][4]) + (255,)
    for (x, y) in ((4, 5), (10, 3), (11, 8)):
        im.putpixel((x, y), ripe)
        im.putpixel((x + 1, y), ripe)
        im.putpixel((x, y + 1), dark)
        im.putpixel((x + 1, y + 1), ripe)
    return im


def hatchet_texture():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    wood, wood_d = (137, 103, 55, 255), (94, 70, 36, 255)
    for k in range(11):
        x, y = 3 + k, 13 - k
        im.putpixel((x, y), wood)
        if k % 3 == 0:
            im.putpixel((x - 1, y), wood_d)
    flint, flint_l, flint_d = (60, 58, 62, 255), (110, 108, 116, 255), (30, 30, 34, 255)
    d.polygon([(8, 2), (12, 1), (14, 4), (13, 7), (10, 6)], fill=flint)
    d.line([(9, 2), (12, 2)], fill=flint_l)
    d.line([(13, 5), (13, 6)], fill=flint_l)
    d.line([(8, 2), (10, 6)], fill=flint_d)
    cord = (214, 196, 150, 255)
    for (x, y) in ((9, 6), (10, 5), (11, 4), (10, 7)):
        im.putpixel((x, y), cord)
    return im


# ------------------------------------------------------------------ datos

def leaves_loot(fid):
    leaves = "%s:%s_leaves" % (NS, fid)
    sapling = "%s:%s_sapling" % (NS, fid)
    fruit_item = FRUITS[fid][9] or "%s:%s" % (NS, fid)
    shears_or_silk = {"condition": "minecraft:any_of", "terms": [
        {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
        {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
            {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}
    not_shears = {"condition": "minecraft:inverted", "term": shears_or_silk}
    return {"type": "minecraft:block", "random_sequence": "%s:blocks/%s_leaves" % (NS, fid), "pools": [
        {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": leaves, "conditions": [shears_or_silk]},
            {"type": "minecraft:item", "name": sapling, "conditions": [
                {"condition": "minecraft:survives_explosion"},
                {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune", "chances": [0.08, 0.1, 0.125, 0.16]}]}]}]},
        {"rolls": 1, "bonus_rolls": 0, "conditions": [not_shears], "entries": [
            {"type": "minecraft:item", "name": "minecraft:stick",
             "conditions": [{"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                             "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1]}],
             "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}, "add": False},
                           {"function": "minecraft:explosion_decay"}]}]},
        {"rolls": 1, "bonus_rolls": 0, "conditions": [
            not_shears, {"condition": "minecraft:block_state_property", "block": leaves, "properties": {"age": "3"}}],
         "entries": [{"type": "minecraft:item", "name": fruit_item,
                      "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}, "add": False},
                                    {"function": "minecraft:explosion_decay"}]}]},
    ]}


def tree_feature(fid):
    leaves = "%s:%s_leaves" % (NS, fid)
    citrus = fid in ("lemon", "orange")
    entries = [{"weight": w, "data": {"Name": leaves, "Properties": {"age": str(a), "distance": "7", "persistent": "false",
                                                                       "waterlogged": "false"}}}
               for a, w in ((0, 5), (2, 2), (3, 3))]
    return {"type": "minecraft:tree", "config": {
        "decorators": [],
        "dirt_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:dirt"}},
        "foliage_placer": {"type": "minecraft:blob_foliage_placer", "height": 3, "offset": 0, "radius": 2},
        "foliage_provider": {"type": "minecraft:weighted_state_provider", "entries": entries},
        "force_dirt": False, "ignore_vines": True,
        "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1},
        "trunk_placer": {"type": "minecraft:straight_trunk_placer", "base_height": 3 if citrus else 4, "height_rand_a": 1, "height_rand_b": 0},
        "trunk_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:oak_log", "Properties": {"axis": "y"}}},
    }}


def placed_feature(fid):
    return {"feature": "%s:%s_tree" % (NS, fid), "placement": [
        {"type": "minecraft:rarity_filter", "chance": 14},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:block_predicate_filter", "predicate": {
            "type": "minecraft:would_survive", "state": {"Name": "%s:%s_sapling" % (NS, fid), "Properties": {"stage": "0"}}}},
        {"type": "minecraft:biome"},
    ]}


TEXTS = {
    "item.evolvedwarfare.flint_hatchet": ("Hacha de sílex", "Flint Hatchet"),
    "item.evolvedwarfare.lemonade": ("Limonada", "Lemonade"),
    "item.evolvedwarfare.lemonade.desc": ("Quita mucha sed y refresca. Agua hervida, un limón y azúcar.",
                                         "Quenches a lot of thirst and cools you down. Boiled water, a lemon and sugar."),
    "item.evolvedwarfare.orange_juice": ("Zumo de naranja", "Orange Juice"),
    "item.evolvedwarfare.orange_juice.desc": ("Quita la sed y alimenta. Un frasco y dos naranjas.",
                                             "Quenches thirst and feeds you. A bottle and two oranges."),
    "item.evolvedwarfare.pear.desc": ("Dulce y jugosa. Del peral.", "Sweet and juicy. From the pear tree."),
    "item.evolvedwarfare.plum.desc": ("Pequeña y dulce. Del ciruelo.", "Small and sweet. From the plum tree."),
    "item.evolvedwarfare.peach.desc": ("Jugoso y alimenta bien. Del melocotonero.", "Juicy and filling. From the peach tree."),
    "item.evolvedwarfare.lemon.desc": ("Muy ácido para comerlo solo: mejor en limonada. Del limonero.",
                                      "Too sour to eat alone: better as lemonade. From the lemon tree."),
    "item.evolvedwarfare.orange.desc": ("Dulce, quita la sed. Del naranjo.", "Sweet and thirst-quenching. From the orange tree."),
    "message.evolvedwarfare.wood.need_axe": ("Necesitas un hacha", "You need an axe"),
    "config.evolvedwarfare.axeForLogs": ("Talar solo con hacha", "Chop only with an axe"),
    "config.evolvedwarfare.axeForLogs.tip": ("Los troncos no se rompen con la mano ni con otras herramientas.",
                                            "Logs can't be broken by hand or with other tools."),
    "config.evolvedwarfare.treeFelling": ("Los árboles caen enteros", "Trees fall whole"),
    "config.evolvedwarfare.treeFelling.tip": ("Al talar uno de los dos primeros troncos, el árbol cae entero (agáchate para quitar solo ese tronco).",
                                             "Chopping one of the two lowest logs fells the whole tree (sneak to take only that log)."),
}
for _fid, (_tes, _ten, _fes, _fen, *_rest) in FRUITS.items():
    TEXTS["block.%s.%s_leaves" % (NS, _fid)] = ("Hojas de " + _tes.lower(), _ten + " Leaves")
    TEXTS["block.%s.%s_sapling" % (NS, _fid)] = ("Brote de " + _tes.lower(), _ten + " Sapling")
    if _fes:
        TEXTS["item.%s.%s" % (NS, _fid)] = (_fes, _fen)


def main():
    tex_dir = os.path.join(ASSETS, "textures/block/orchard")
    os.makedirs(tex_dir, exist_ok=True)
    images = {}
    for fid, pal in PALS.items():
        pal.im.save(os.path.join(tex_dir, fid + ".png"))
        images[tex(fid)] = pal.im
    previews = []
    for fid in FRUITS:
        row = []
        for age in range(4):
            model = leaves_model(fid, age)
            write_json(os.path.join(ASSETS, "models/block/%s_leaves_%d.json" % (fid, age)), model)
            row.append(model)
        previews.append(row)
        write_json(os.path.join(ASSETS, "blockstates/%s_leaves.json" % fid), {"variants": {
            "age=%d" % a: [{"model": "%s:block/%s_leaves_%d" % (NS, fid, a), "y": y} for y in (0, 90, 180, 270)] for a in range(4)}})
        write_json(os.path.join(ASSETS, "models/item/%s_leaves.json" % fid), {"parent": "%s:block/%s_leaves_3" % (NS, fid)})
        # el brote
        sapling_texture(fid).save(os.path.join(ASSETS, "textures/block/%s_sapling.png" % fid))
        write_json(os.path.join(ASSETS, "models/block/%s_sapling.json" % fid),
                   {"parent": "minecraft:block/cross", "textures": {"cross": "%s:block/%s_sapling" % (NS, fid)}})
        write_json(os.path.join(ASSETS, "blockstates/%s_sapling.json" % fid),
                   {"variants": {"stage=%d" % s: {"model": "%s:block/%s_sapling" % (NS, fid)} for s in (0, 1)}})
        write_json(os.path.join(ASSETS, "models/item/%s_sapling.json" % fid),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:block/%s_sapling" % (NS, fid)}})
        write_json(os.path.join(DATA, "loot_table/blocks/%s_sapling.json" % fid),
                   {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                                                          "entries": [{"type": "minecraft:item", "name": "%s:%s_sapling" % (NS, fid)}]}]})
        write_json(os.path.join(DATA, "loot_table/blocks/%s_leaves.json" % fid), leaves_loot(fid))
        write_json(os.path.join(DATA, "worldgen/configured_feature/%s_tree.json" % fid), tree_feature(fid))
        write_json(os.path.join(DATA, "worldgen/placed_feature/%s_tree.json" % fid), placed_feature(fid))
        if not FRUITS[fid][9]:
            model = item_fruit(fid).json(display=DISPLAY)
            model["gui_light"] = "side"
            write_json(os.path.join(ASSETS, "models/item/%s.json" % fid), model)
    # etiquetas
    leaves = ["%s:%s_leaves" % (NS, f) for f in FRUITS]
    saplings = ["%s:%s_sapling" % (NS, f) for f in FRUITS]
    add_tag(os.path.join(MC_DATA, "tags/block/leaves.json"), leaves)
    add_tag(os.path.join(MC_DATA, "tags/block/mineable/hoe.json"), leaves)
    add_tag(os.path.join(MC_DATA, "tags/block/saplings.json"), saplings)
    add_tag(os.path.join(MC_DATA, "tags/item/leaves.json"), leaves)
    add_tag(os.path.join(MC_DATA, "tags/item/saplings.json"), saplings)
    add_tag(os.path.join(MC_DATA, "tags/item/axes.json"), ["%s:flint_hatchet" % NS])
    # el hacha de sílex y los zumos
    hatchet_texture().save(os.path.join(ASSETS, "textures/item/flint_hatchet.png"))
    write_json(os.path.join(ASSETS, "models/item/flint_hatchet.json"),
               {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/flint_hatchet" % NS}})
    for item in ("lemonade", "orange_juice"):
        write_json(os.path.join(ASSETS, "models/item/%s.json" % item),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/potion_overlay", "layer1": "minecraft:item/potion"}})
    # recetas
    rec = os.path.join(DATA, "recipe")
    write_json(os.path.join(rec, "oak_slab_from_sticks.json"),
               {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["SS", "SS"],
                "key": {"S": {"item": "minecraft:stick"}}, "result": {"id": "minecraft:oak_slab", "count": 1}})
    write_json(os.path.join(rec, "oak_planks_from_slabs.json"),
               {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["S", "S"],
                "key": {"S": {"item": "minecraft:oak_slab"}}, "result": {"id": "minecraft:oak_planks", "count": 1}})
    write_json(os.path.join(rec, "flint_hatchet.json"),
               {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["F", "S", "S"],
                "key": {"F": {"item": "minecraft:flint"}, "S": {"item": "minecraft:stick"}}, "result": {"id": "%s:flint_hatchet" % NS, "count": 1}})
    write_json(os.path.join(rec, "lemonade.json"),
               {"type": "minecraft:crafting_shapeless", "category": "misc",
                "ingredients": [{"item": "%s:boiled_water" % NS}, {"item": "%s:lemon" % NS}, {"item": "minecraft:sugar"}],
                "result": {"id": "%s:lemonade" % NS, "count": 1}})
    write_json(os.path.join(rec, "orange_juice.json"),
               {"type": "minecraft:crafting_shapeless", "category": "misc",
                "ingredients": [{"item": "minecraft:glass_bottle"}, {"item": "%s:orange" % NS}, {"item": "%s:orange" % NS}],
                "result": {"id": "%s:orange_juice" % NS, "count": 1}})
    # textos
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang, indent=2)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        leaf_img = {}
        for name in ("oak_leaves", "jungle_leaves"):
            im = Image.open(os.path.join(VANILLA_TEX, "block", name + ".png")).convert("RGBA")
            tint = (72, 181, 24)
            px = im.load()
            for y in range(im.size[1]):
                for x in range(im.size[0]):
                    r, g, b, a = px[x, y]
                    px[x, y] = (r * tint[0] // 255, g * tint[1] // 255, b * tint[2] // 255, a)
            leaf_img["minecraft:block/" + name] = im
        cell = 170
        sheet = Image.new("RGB", (cell * 5, cell * len(previews)), (70, 74, 80))
        for ri, (fid, row) in enumerate(zip(FRUITS, previews)):
            for ai, model in enumerate(row):
                leaf_m = {"textures": {"t": model["textures"]["leaves"]}, "elements": model["elements"][:1]}
                fruit_m = {"textures": {"t": model["textures"]["t"]}, "elements": model["elements"][1:]}
                imgs = dict(images)
                imgs.update(leaf_img)
                im = render([leaf_m, fruit_m], imgs, size=cell, scale=5.0, pitch=-15 if ai >= 2 else 30)
                sheet.paste(im, (ai * cell, ri * cell))
            if not FRUITS[fid][9]:
                im = render([item_fruit(fid).json()], images, size=cell, scale=6.0)
                sheet.paste(im, (4 * cell, ri * cell))
        sheet.save(os.path.join(PREVIEW, "orchard.png"))
    print("frutales: hojas (4 edades), brotes, fruta, zumos, botín, árboles y textos; tala: hacha de sílex y recetas")


if __name__ == "__main__":
    main()
