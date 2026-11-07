"""El Nether vivo (fase 37): flora y fauna infernales, como las del mundo normal pero del infierno.

- Bloques: tierra chamuscada, tronco y tablones calcinados, hojas de brasa (brillan) y hojas de espino, hierba de brasa,
  helecho de brasa, flor infernal (brilla), lirio cadáver, sangrecasco (una seta que brilla), zarza de espinas (pincha) y
  liana de brasas (cuelga del techo).
- Árboles y matas: árboles calcinados (normales y grandes), espinos gigantes con lianas, y las matas de cada planta; los
  árboles de los ahorcados y las lianas del techo los hace el código (HellFlora).
- Fauna: polillas de brasa (vuelan y cuelgan del techo), ciervos de ceniza (en manada, huyen; se crían con flores
  infernales) y sapos de lava (andan sobre la lava; se crían con sangrecascos). Su carne, botín, huevos y textos.

Uso: python3 tools/ew_hellflora.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ew_wildlife as W  # noqa: E402
import ew_demons as D  # noqa: E402
from ew_model3d import render_entity  # noqa: E402

PREVIEW = W.PREVIEW
ROOT = W.ROOT
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
MC_DATA = os.path.join(ROOT, "src/main/resources/data/minecraft")
VANILLA = "/home/claude/ref/mcassets/assets/minecraft/textures/block"
C = W.C
write_json = D.write_json
add_tag = D.add_tag


def n(name):
    return "%s:%s" % (NS, name)


# ------------------------------------------------------------------ texturas

def van(name):
    return Image.open(os.path.join(VANILLA, name + ".png")).convert("RGBA").crop((0, 0, 16, 16))


def tint(im, dark, light, glow=None, thresh=0.75):
    """Pasa la textura a escala de grises y la colorea de oscuro a claro (lo más claro, si glow, con ese color)."""
    out = Image.new("RGBA", im.size)
    px, po = im.load(), out.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                po[x, y] = (0, 0, 0, 0)
                continue
            v = (0.3 * r + 0.59 * g + 0.11 * b) / 255.0
            c = glow if glow and v > thresh else tuple(int(dark[i] + (light[i] - dark[i]) * v) for i in range(3))
            po[x, y] = c + (a,)
    return out


def specks(im, colors, count, seed):
    r = random.Random(seed)
    d = ImageDraw.Draw(im)
    for _ in range(count):
        d.point((r.randrange(16), r.randrange(16)), fill=r.choice(colors) + (255,))
    return im


def flower(petal, center, stem=(70, 30, 20), seed=1):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.line([(8, 15), (8, 8)], fill=stem + (255,))
    d.line([(8, 12), (5, 10)], fill=stem + (255,))
    d.line([(8, 11), (11, 9)], fill=stem + (255,))
    for (x, y) in ((8, 3), (6, 4), (10, 4), (5, 6), (11, 6), (7, 7), (9, 7), (8, 5)):
        d.point((x, y), fill=petal + (255,))
    d.point((7, 4), fill=petal + (255,))
    d.point((9, 4), fill=petal + (255,))
    d.point((8, 5), fill=center + (255,))
    d.point((8, 2), fill=petal + (255,))
    return im


def textures():
    T = {}
    # la tierra chamuscada: arriba ceniza parda con brasas, el lado como la tierra con la capa de arriba
    top = specks(tint(van("crimson_nylium"), (36, 22, 18), (92, 54, 36)), [(255, 120, 30), (200, 60, 20), (40, 30, 26)], 14, 1)
    T["scorched_soil_top"] = top
    side = tint(van("crimson_nylium_side"), (40, 22, 18), (110, 64, 44))
    T["scorched_soil_side"] = side
    T["charred_log"] = specks(tint(van("dark_oak_log"), (12, 10, 10), (58, 48, 44)), [(220, 80, 20), (255, 140, 40)], 6, 2)
    T["charred_log_top"] = tint(van("dark_oak_log_top"), (14, 10, 10), (90, 60, 40), glow=(255, 110, 30), thresh=0.85)
    T["charred_planks"] = tint(van("dark_oak_planks"), (20, 16, 16), (70, 56, 50))
    T["ember_leaves"] = tint(van("oak_leaves"), (90, 18, 8), (255, 150, 40), glow=(255, 220, 120), thresh=0.8)
    T["thorn_leaves"] = specks(tint(van("jungle_leaves"), (40, 6, 10), (150, 24, 30)), [(230, 210, 190)], 10, 3)
    T["ember_grass"] = tint(van("short_grass"), (110, 30, 10), (255, 150, 50), glow=(255, 230, 140), thresh=0.85)
    T["ember_fern"] = tint(van("fern"), (80, 14, 10), (220, 80, 30))
    T["hellbloom"] = flower((255, 110, 20), (255, 240, 120), seed=4)
    T["corpse_lily"] = tint(van("lily_of_the_valley"), (60, 50, 60), (230, 222, 214))
    T["bloodcap"] = specks(tint(van("red_mushroom"), (60, 6, 10), (200, 30, 40)), [(255, 200, 120)], 3, 5)
    T["thorn_bush"] = tint(van("dead_bush"), (40, 8, 10), (130, 30, 30))
    T["ember_vine"] = tint(van("weeping_vines_plant"), (110, 26, 8), (255, 150, 40), glow=(255, 230, 120), thresh=0.8)
    return T


# ------------------------------------------------------------------ bloques

PLANTS = ["ember_grass", "ember_fern", "hellbloom", "corpse_lily", "bloodcap", "thorn_bush"]


def block_assets():
    tex = os.path.join(ASSETS, "textures/block")
    for name, im in textures().items():
        im.save(os.path.join(tex, name + ".png"))
    M = os.path.join(ASSETS, "models")
    B = os.path.join(ASSETS, "blockstates")
    write_json(os.path.join(M, "block/scorched_soil.json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": n("block/scorched_soil_top"), "side": n("block/scorched_soil_side"), "bottom": "minecraft:block/soul_soil"}})
    write_json(os.path.join(B, "scorched_soil.json"), {"variants": {"": {"model": n("block/scorched_soil")}}})
    write_json(os.path.join(M, "block/charred_log.json"), {"parent": "minecraft:block/cube_column", "textures": {
        "end": n("block/charred_log_top"), "side": n("block/charred_log")}})
    write_json(os.path.join(M, "block/charred_log_horizontal.json"), {"parent": "minecraft:block/cube_column_horizontal", "textures": {
        "end": n("block/charred_log_top"), "side": n("block/charred_log")}})
    write_json(os.path.join(B, "charred_log.json"), {"variants": {
        "axis=y": {"model": n("block/charred_log")}, "axis=z": {"model": n("block/charred_log_horizontal"), "x": 90},
        "axis=x": {"model": n("block/charred_log_horizontal"), "x": 90, "y": 90}}})
    for name in ("charred_planks", "ember_leaves", "thorn_leaves"):
        parent = "minecraft:block/leaves" if "leaves" in name else "minecraft:block/cube_all"
        write_json(os.path.join(M, "block/%s.json" % name), {"parent": parent, "textures": {"all": n("block/" + name)}})
        write_json(os.path.join(B, "%s.json" % name), {"variants": {"": {"model": n("block/" + name)}}})
    for name in PLANTS + ["ember_vine"]:
        write_json(os.path.join(M, "block/%s.json" % name), {"parent": "minecraft:block/cross", "textures": {"cross": n("block/" + name)}})
        write_json(os.path.join(B, "%s.json" % name), {"variants": {"": {"model": n("block/" + name)}}})
        write_json(os.path.join(M, "item/%s.json" % name), {"parent": "minecraft:item/generated", "textures": {"layer0": n("block/" + name)}})
    for name in ("scorched_soil", "charred_log", "charred_planks", "ember_leaves", "thorn_leaves"):
        write_json(os.path.join(M, "item/%s.json" % name), {"parent": n("block/" + name)})
    # botín de los bloques
    L = os.path.join(DATA, "loot_table/blocks")
    for name in ["charred_log", "charred_planks"] + PLANTS + ["ember_vine"]:
        write_json(os.path.join(L, "%s.json" % name), {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [
            {"type": "minecraft:item", "name": n(name)}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(os.path.join(L, "scorched_soil.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": n("scorched_soil"), "conditions": [{"condition": "minecraft:match_tool", "predicate": {"predicates": {
                "minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
            {"type": "minecraft:item", "name": "minecraft:soul_soil"}]}]}]})
    for name in ("ember_leaves", "thorn_leaves"):
        write_json(os.path.join(L, "%s.json" % name), {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [
            {"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "name": n(name), "conditions": [{"condition": "minecraft:any_of", "terms": [
                    {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
                    {"condition": "minecraft:match_tool", "predicate": {"predicates": {
                        "minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}]},
                {"type": "minecraft:item", "name": "minecraft:stick", "conditions": [{"condition": "minecraft:random_chance", "chance": 0.04}]}]}]}]})
    # etiquetas
    add_tag(os.path.join(MC_DATA, "tags/block/logs.json"), [n("charred_log")])
    add_tag(os.path.join(MC_DATA, "tags/item/logs.json"), [n("charred_log")])
    add_tag(os.path.join(MC_DATA, "tags/block/planks.json"), [n("charred_planks")])
    add_tag(os.path.join(MC_DATA, "tags/item/planks.json"), [n("charred_planks")])
    add_tag(os.path.join(MC_DATA, "tags/block/leaves.json"), [n("ember_leaves"), n("thorn_leaves")])
    add_tag(os.path.join(MC_DATA, "tags/block/mineable/axe.json"), [n("charred_log"), n("charred_planks")])
    add_tag(os.path.join(MC_DATA, "tags/block/mineable/hoe.json"), [n("ember_leaves"), n("thorn_leaves")])
    add_tag(os.path.join(MC_DATA, "tags/block/mineable/shovel.json"), [n("scorched_soil")])
    add_tag(os.path.join(MC_DATA, "tags/block/climbable.json"), [n("ember_vine")])
    add_tag(os.path.join(MC_DATA, "tags/block/replaceable_by_trees.json"), [n(p) for p in PLANTS] + [n("ember_vine")])
    add_tag(os.path.join(MC_DATA, "tags/block/small_flowers.json"), [n("hellbloom"), n("corpse_lily")])
    add_tag(os.path.join(MC_DATA, "tags/item/small_flowers.json"), [n("hellbloom"), n("corpse_lily")])
    # recetas
    R = os.path.join(DATA, "recipe")
    write_json(os.path.join(R, "charred_planks.json"), {"type": "minecraft:crafting_shapeless", "category": "building",
                                                        "ingredients": [{"item": n("charred_log")}], "result": {"id": n("charred_planks"), "count": 4}})
    write_json(os.path.join(R, "cooked_ash_venison.json"), {"type": "minecraft:smelting", "category": "food", "ingredient": {"item": n("raw_ash_venison")},
                                                            "result": {"id": n("cooked_ash_venison")}, "experience": 0.35, "cookingtime": 200})
    write_json(os.path.join(R, "cooked_ash_venison_smoking.json"), {"type": "minecraft:smoking", "category": "food",
                                                                    "ingredient": {"item": n("raw_ash_venison")},
                                                                    "result": {"id": n("cooked_ash_venison")}, "experience": 0.35, "cookingtime": 100})
    write_json(os.path.join(R, "orange_dye_from_hellbloom.json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                                   "ingredients": [{"item": n("hellbloom")}], "result": {"id": "minecraft:orange_dye", "count": 2}})
    write_json(os.path.join(R, "light_gray_dye_from_corpse_lily.json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                                         "ingredients": [{"item": n("corpse_lily")}],
                                                                         "result": {"id": "minecraft:light_gray_dye", "count": 1}})


# ------------------------------------------------------------------ rasgos del mundo (árboles y matas)

def state(name, **props):
    s = {"Name": name if ":" in name else n(name)}
    if props:
        s["Properties"] = {k: str(v).lower() for k, v in props.items()}
    return s


def simple(st):
    return {"type": "minecraft:simple_state_provider", "state": st}


LEAF = {"distance": 7, "persistent": False, "waterlogged": False}


def tree(trunk, foliage, minimum, leaves="ember_leaves", decorators=()):
    return {"type": "minecraft:tree", "config": {
        "ignore_vines": True, "force_dirt": False, "minimum_size": minimum,
        "dirt_provider": simple(state("scorched_soil")), "trunk_provider": simple(state("charred_log", axis="y")),
        "foliage_provider": simple(state(leaves, **LEAF)), "trunk_placer": trunk, "foliage_placer": foliage, "decorators": list(decorators)}}


def two_layers(limit=1, lower=0, upper=1):
    return {"type": "minecraft:two_layers_feature_size", "limit": limit, "lower_size": lower, "upper_size": upper}


def patch(block, tries=48, spread=7):
    return {"type": "minecraft:random_patch", "config": {"tries": tries, "xz_spread": spread, "y_spread": 3, "feature": {
        "feature": {"type": "minecraft:simple_block", "config": {"to_place": simple(state(block))}},
        "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
            {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
            {"type": "minecraft:would_survive", "state": state(block)}]}}]}}}


ON_SOLID = {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
    {"type": "minecraft:solid", "offset": [0, -1, 0]}, {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}]}}


def layers(k):
    return {"type": "minecraft:count_on_every_layer", "count": k}


def rarity(k):
    return {"type": "minecraft:rarity_filter", "chance": k}


BIOME_F = {"type": "minecraft:biome"}


def features():
    cf, pf = {}, {}
    cf["charred_tree"] = tree({"type": "minecraft:straight_trunk_placer", "base_height": 5, "height_rand_a": 3, "height_rand_b": 1},
                              {"type": "minecraft:blob_foliage_placer", "radius": 2, "offset": 0, "height": 3}, two_layers())
    cf["charred_fancy_tree"] = tree({"type": "minecraft:fancy_trunk_placer", "base_height": 9, "height_rand_a": 6, "height_rand_b": 0},
                                    {"type": "minecraft:fancy_foliage_placer", "radius": 2, "offset": 4, "height": 4}, two_layers(0, 0, 0))
    vines = {"type": "minecraft:attached_to_leaves", "probability": 0.35, "exclusion_radius_xz": 0, "exclusion_radius_y": 0,
             "required_empty_blocks": 2, "block_provider": simple(state("ember_vine")), "directions": ["down"]}
    cf["thorn_giant"] = tree({"type": "minecraft:giant_trunk_placer", "base_height": 13, "height_rand_a": 6, "height_rand_b": 5},
                             {"type": "minecraft:jungle_foliage_placer", "radius": 2, "offset": 0, "height": 2}, two_layers(1, 1, 2),
                             leaves="thorn_leaves", decorators=[vines])
    cf["gallows_tree"] = {"type": n("gallows_tree"), "config": {}}
    cf["ceiling_vines"] = {"type": n("ceiling_vines"), "config": {}}
    cf["roof_lavafall"] = {"type": "minecraft:spring_feature", "config": {
        "state": {"Name": "minecraft:lava", "Properties": {"falling": "true"}}, "requires_block_below": False, "rock_count": 4, "hole_count": 1,
        "valid_blocks": ["minecraft:netherrack", "minecraft:blackstone", "minecraft:basalt", "minecraft:magma_block"]}}
    for p in PLANTS:
        cf[p + "_patch"] = patch(p, tries=64 if p in ("ember_grass", "ember_fern") else 24, spread=7 if p != "bloodcap" else 4)
    cf["ember_grass_meadow"] = patch("ember_grass", tries=96, spread=8)
    cf["hellbloom_meadow"] = patch("hellbloom", tries=48, spread=8)

    pf["charred_trees"] = [layers(6), ON_SOLID, BIOME_F]
    pf["charred_fancy_trees"] = [layers(1), ON_SOLID, BIOME_F]
    pf["meadow_trees"] = [rarity(3), layers(1), ON_SOLID, BIOME_F]
    pf["thorn_giants"] = [layers(2), ON_SOLID, BIOME_F]
    pf["gallows_trees"] = [layers(2), ON_SOLID, BIOME_F]
    pf["ember_grass_patch"] = [layers(3), BIOME_F]
    pf["ember_fern_patch"] = [layers(2), BIOME_F]
    pf["hellbloom_patch"] = [layers(1), BIOME_F]
    pf["corpse_lily_patch"] = [layers(2), BIOME_F]
    pf["bloodcap_patch"] = [rarity(2), layers(1), BIOME_F]
    pf["thorn_bush_patch"] = [layers(2), BIOME_F]
    pf["ember_grass_meadow"] = [layers(5), BIOME_F]
    pf["hellbloom_meadow"] = [layers(2), BIOME_F]
    # el techo: lianas de brasas y cascadas de lava (en todos los biomas)
    pf["ceiling_vines"] = [{"type": "minecraft:count", "count": 24}, {"type": "minecraft:in_square"},
                           {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 150},
                                                                        "max_inclusive": {"absolute": 245}}}, BIOME_F]
    # las cascadas del techo, pocas: cada una cae al suelo y lo cubre de lava (con cinco por chunk el suelo llano de los
    # biomas con flora se llenaba de mantos de lava que crecían sin parar)
    pf["roof_lavafall"] = [{"type": "minecraft:rarity_filter", "chance": 6}, {"type": "minecraft:in_square"},
                           {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 170},
                                                                        "max_inclusive": {"absolute": 260}}}, BIOME_F]
    feature_of = {"charred_trees": "charred_tree", "charred_fancy_trees": "charred_fancy_tree", "meadow_trees": "charred_tree",
                  "thorn_giants": "thorn_giant", "gallows_trees": "gallows_tree"}
    for k, v in cf.items():
        write_json(os.path.join(DATA, "worldgen/configured_feature/%s.json" % k), v)
    for k, placement in pf.items():
        write_json(os.path.join(DATA, "worldgen/placed_feature/%s.json" % k), {"feature": n(feature_of.get(k, k)), "placement": placement})


# ------------------------------------------------------------------ la fauna

def moth():
    """Polilla de brasa: cuerpo peludo de ceniza y cuatro alas que brillan como ascuas."""
    a = W.Animal("ember_moth")
    body = a.part("body", None, (0, 20, 0), role="body")
    body.cube((-1, -1, -2), (2, 2, 4), **C((50, 36, 30), spots=((90, 60, 40), 0.3)))
    head = a.part("head", "body", (0, 0, -2), role="head")
    head.cube((-1, -1, -1.5), (2, 2, 2), **C((40, 30, 26), eyes=("front", 0, (255, 200, 80)), glow_eyes=True))
    for side in (-1, 1):
        o = (0, 0, -2) if side > 0 else (-5, 0, -2)
        a.part("wing_%d" % (side + 1), "body", (side * 1, -1, 0), (0, 0, side * -10), role="wing", side=side, flap="roll").cube(
            o, (5, 0, 5), **C((255, 120, 30), spots=((255, 220, 120), 0.3), glow_all=True))
    return a


def stag():
    """Ciervo de ceniza: pelaje gris carbón con grietas de brasa, ojos encendidos y astas de hueso quemado."""
    fur = (70, 64, 62)
    a = W.quadruped("ash_stag", body=(8, 8, 16), leg=(3, 14), head=(5, 5, 6), snout=(3, 3, 3), neck=(4, 8, 4, 32), ear=(1, 3, 2), tail=(3, 2, 2, 30),
                    colors={"body": C(fur, spots=((230, 90, 30), 0.06), belly=((110, 100, 96), 2)), "leg": C((50, 46, 44), low=((24, 20, 18), 1)),
                            "neck": C(fur, spots=((230, 90, 30), 0.05)), "head": C(fur, eyes=("side", 1, (255, 120, 30)), glow_eyes=True),
                            "snout": C((40, 36, 34), nose=(20, 16, 14)), "ear": C(fur, inner=(160, 60, 30)), "tail": C((40, 36, 34))})
    W.antlers(a, "deer", (60, 44, 34))
    return a


def toad():
    """Sapo de lava: piel de roca negra con grietas al rojo, ancho y achaparrado, con la papada brillante."""
    a = W.Animal("lava_toad")
    rock = (40, 30, 28)
    for side, z, phase in ((-1, -3, 0), (1, -3, 1), (-1, 3, 1), (1, 3, 0)):
        a.part("leg_%d_%d" % (side + 1, z), None, (side * 3.5, 21, z), (0, 0, side * -20), role="leg", phase=phase).cube(
            (-1.5, 0, -1.5), (3, 3, 3), **C(rock, low=((20, 14, 12), 1)))
    body = a.part("body", None, (0, 21, 0), role="body")
    body.cube((-4, -4, -4.5), (8, 5, 9), **C(rock, spots=((255, 90, 20), 0.18), belly=((200, 70, 30), 1)))
    head = a.part("head", "body", (0, -2, -4.5), role="head")
    head.cube((-3.5, -2.5, -3), (7, 3, 3), **C(rock, spots=((255, 90, 20), 0.12), eyes=("front", 0, (255, 200, 40)), glow_eyes=True))
    a.part("jaw", "head", (0, 0.5, -1), role="jaw").cube((-3, 0, -2), (6, 1, 2), **C((230, 90, 30), glow_all=True))
    return a


MODELS = collections.OrderedDict([("ember_moth", moth), ("ash_stag", stag), ("lava_toad", toad)])
NAMES = {"ember_moth": ("Polilla de brasa", "Ember Moth"), "ash_stag": ("Ciervo de ceniza", "Ash Stag"), "lava_toad": ("Sapo de lava", "Lava Toad")}


def count(item, lo, hi, chance=None, smelt=False):
    entry = {"type": "minecraft:item", "name": item, "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False},
        {"function": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0, "max": 1}, "enchantment": "minecraft:looting"}]}
    if smelt:
        entry["functions"].append({"function": "minecraft:furnace_smelt", "conditions": [{"condition": "minecraft:entity_properties", "entity": "this",
                                                                                         "predicate": {"flags": {"is_on_fire": True}}}]})
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [entry]}
    if chance:
        pool["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return pool


LOOT = {
    "ember_moth": [count("minecraft:glowstone_dust", 0, 1, chance=0.5)],
    "ash_stag": [count(n("raw_ash_venison"), 1, 3, smelt=True), count("minecraft:leather", 0, 2), count("minecraft:bone", 0, 1)],
    "lava_toad": [count("minecraft:magma_cream", 0, 2), count("minecraft:slime_ball", 0, 1, chance=0.2)],
}

TEXTS = {
    "block.evolvedwarfare.scorched_soil": ("Tierra chamuscada", "Scorched Soil"),
    "block.evolvedwarfare.charred_log": ("Tronco calcinado", "Charred Log"),
    "block.evolvedwarfare.charred_planks": ("Tablones calcinados", "Charred Planks"),
    "block.evolvedwarfare.ember_leaves": ("Hojas de brasa", "Ember Leaves"),
    "block.evolvedwarfare.thorn_leaves": ("Hojas de espino", "Thorn Leaves"),
    "block.evolvedwarfare.ember_grass": ("Hierba de brasa", "Ember Grass"),
    "block.evolvedwarfare.ember_fern": ("Helecho de brasa", "Ember Fern"),
    "block.evolvedwarfare.hellbloom": ("Flor infernal", "Hellbloom"),
    "block.evolvedwarfare.corpse_lily": ("Lirio cadáver", "Corpse Lily"),
    "block.evolvedwarfare.bloodcap": ("Sangrecasco", "Bloodcap"),
    "block.evolvedwarfare.thorn_bush": ("Zarza de espinas", "Thorn Bush"),
    "block.evolvedwarfare.ember_vine": ("Liana de brasas", "Ember Vine"),
    "item.evolvedwarfare.raw_ash_venison": ("Carne de ciervo de ceniza", "Raw Ash Venison"),
    "item.evolvedwarfare.cooked_ash_venison": ("Asado de ciervo de ceniza", "Roasted Ash Venison"),
    "biome.evolvedwarfare.charred_forest": ("Bosque Calcinado", "Charred Forest"),
    "biome.evolvedwarfare.thorn_jungle": ("Selva de Espinas", "Thorn Jungle"),
    "biome.evolvedwarfare.hanged_swamp": ("Ciénaga de los Ahorcados", "Swamp of the Hanged"),
    "biome.evolvedwarfare.ember_meadow": ("Pradera de Brasas", "Ember Meadow"),
}
for _id, (_es, _en) in NAMES.items():
    TEXTS["entity.%s.%s" % (NS, _id)] = (_es, _en)
    TEXTS["item.%s.%s_spawn_egg" % (NS, _id)] = ("Huevo de " + _es[0].lower() + _es[1:], _en + " Spawn Egg")


def items():
    it = os.path.join(ASSETS, "textures/item")
    raw = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(raw)
    d.ellipse((3, 4, 13, 12), fill=(150, 50, 50, 255), outline=(90, 20, 24, 255))
    d.line([(5, 7), (11, 9)], fill=(230, 200, 190, 255))
    d.line([(1, 13), (4, 10)], fill=(230, 220, 200, 255), width=2)
    raw.save(os.path.join(it, "raw_ash_venison.png"))
    cooked = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(cooked)
    d.ellipse((3, 4, 13, 12), fill=(110, 60, 30, 255), outline=(60, 30, 14, 255))
    d.line([(5, 7), (11, 9)], fill=(255, 140, 40, 255))
    d.line([(1, 13), (4, 10)], fill=(230, 220, 200, 255), width=2)
    cooked.save(os.path.join(it, "cooked_ash_venison.png"))
    for name in ("raw_ash_venison", "cooked_ash_venison"):
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name), {"parent": "minecraft:item/generated", "textures": {"layer0": n("item/" + name)}})


def main():
    block_assets()
    features()
    items()
    tex_dir = os.path.join(ASSETS, "textures/entity/hell")
    previews = []
    for mid, gen in MODELS.items():
        a = gen()
        tw, th = a.pack()
        img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        glow = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        rng = random.Random(mid)
        for p in a.parts:
            for c in p.cubes:
                W.paint_cube(img, c, rng)
                D.paint_extra(img, glow, c, rng)
        img.save(os.path.join(tex_dir, mid + ".png"))
        glow.save(os.path.join(tex_dir, mid + "_eyes.png"))
        parts = [{"name": p.name, "parent": p.parent, "pivot": p.pivot, "rotation": p.rot, "role": p.role,
                  "cubes": [{"uv": list(c.uv), "origin": c.origin, "size": c.size} for c in p.cubes]} for p in a.parts]
        write_json(os.path.join(ASSETS, "hell/%s.json" % mid), {"texture": [tw, th], "parts": parts}, indent=None)
        previews.append((mid, parts, img))
        write_json(os.path.join(DATA, "loot_table/entities/%s.json" % mid),
                   {"type": "minecraft:entity", "pools": LOOT[mid], "random_sequence": "%s:entities/%s" % (NS, mid)})
        write_json(os.path.join(ASSETS, "models/item/%s_spawn_egg.json" % mid), {"parent": "minecraft:item/template_spawn_egg"})
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    # algunas estructuras también salen en los biomas nuevos
    for sid, biomes in (("false_temple", ["charred_forest", "ember_meadow", "thorn_jungle"]), ("fallen_graveyard", ["hanged_swamp", "charred_forest"]),
                        ("sodom_house", ["ember_meadow"])):
        add_tag(os.path.join(DATA, "tags/worldgen/biome/has_structure/%s.json" % sid), [n(b) for b in biomes])
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        cell = 220
        sheet = Image.new("RGB", (cell * 3 + 16 * 4 * 6, cell), (70, 74, 80))
        for k, (mid, parts, img) in enumerate(previews):
            sheet.paste(render_entity(parts, img, size=cell, yaw=35.0, pitch=12.0, center="auto"), (k * cell, 0))
        for k, (name, im) in enumerate(textures().items()):
            sheet.paste(im.resize((64, 64), Image.NEAREST), (cell * 3 + (k % 6) * 64, (k // 6) * 64), im.resize((64, 64), Image.NEAREST))
        sheet.save(os.path.join(PREVIEW, "hellflora.png"))
    print("el Nether vivo: flora (bosque, selva, ciénaga y pradera) y fauna (polillas, ciervos y sapos)")


if __name__ == "__main__":
    main()
