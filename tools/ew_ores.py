"""
Las menas realistas (fase 49): yacimientos como en la vida real en lugar de las menas de vanilla (mismos nombres de los
placed features, así no cambia el orden de las features de los biomas), el oro de aluvión de los ríos, los guijarros que
delatan un yacimiento y el pico de prospector.

Geología (simplificada) que se imita:
- Carbón: mantos sedimentarios, capas anchas y finas; en llanuras, bosques y pantanos (las cuencas de carbón).
- Hierro: lentes grandes (formaciones de hierro bandeado), en montañas, colinas y mesetas; algo en todas partes.
- Cobre: pórfidos, cuerpos enormes y pobres, en desiertos, mesas y montañas; cuevas de goteo, ricos.
- Oro: vetas finas de cuarzo, hondas, en montañas y mesas; en las mesas, también altas y ricas; aluvión en los ríos.
- Redstone: lentes hondas (como el cinabrio, de aguas termales).
- Lapislázuli: lentes en el mármol de las montañas.
- Diamante: chimeneas de kimberlita (tubos verticales de toba) en los escudos antiguos (taiga, tundra, sabana, desierto).
- Esmeralda: vetas en las montañas.
- Nether: vetas de cuarzo y de oro (de aguas termales) y bolsas pequeñas y raras de escombros ancestrales.
En total, algo menos que en vanilla (un 30 % menos), pero muy concentrado: hay que buscar.

Uso: python3 tools/ew_ores.py [raíz del proyecto]
"""
import collections
import glob
import json
import os
import random
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
NS = "evolvedwarfare"
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data", NS)
MC = os.path.join(RES, "data/minecraft")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def add_tag(path, values):
    tag = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            tag = json.load(f)
    for v in values:
        if v not in tag["values"]:
            tag["values"].append(v)
    write_json(path, tag)


def target(tag, block, predicate="minecraft:tag_match"):
    return {"target": {"predicate_type": predicate, "tag": tag}, "state": {"Name": block}}


def stone(ore):
    return [target("minecraft:stone_ore_replaceables", "minecraft:" + ore),
            target("minecraft:deepslate_ore_replaceables", "minecraft:deepslate_" + ore)]


def uniform(a, b):
    return {"type": "minecraft:uniform", "min_inclusive": a, "max_inclusive": b}


# nombre: (forma, tamaño, densidad, objetivos, roca de la chimenea, pista)
DEPOSITS = {
    "coal": ("seam", uniform(9, 13), 0.7, stone("coal_ore"), None, "coal"),
    "coal_deep": ("seam", uniform(8, 12), 0.65, stone("coal_ore"), None, "coal"),
    "iron": ("lens", uniform(8, 12), 0.5, stone("iron_ore"), None, "iron"),
    "iron_mountain": ("lens", uniform(9, 13), 0.55, stone("iron_ore"), None, "iron"),
    "copper": ("disseminated", uniform(9, 12), 0.09, stone("copper_ore"), None, "copper"),
    "copper_rich": ("lens", uniform(8, 12), 0.35, stone("copper_ore"), None, "copper"),
    "gold": ("vein", uniform(7, 10), 0.55, stone("gold_ore"), None, "gold"),
    "gold_badlands": ("vein", uniform(8, 12), 0.6, stone("gold_ore"), None, "gold"),
    "redstone": ("lens", uniform(6, 9), 0.45, stone("redstone_ore"), None, "redstone"),
    "lapis": ("lens", uniform(5, 7), 0.5, stone("lapis_ore"), None, "lapis"),
    "diamond": ("pipe", uniform(2, 3), 0.1, stone("diamond_ore"), {"Name": "minecraft:tuff"}, "diamond"),
    "emerald": ("vein", uniform(4, 6), 0.4, stone("emerald_ore"), None, "emerald"),
    "quartz": ("vein", uniform(6, 9), 0.55, [target("minecraft:nether_carver_replaceables", "minecraft:nether_quartz_ore", "minecraft:tag_match")],
               None, None),
    "nether_gold": ("vein", uniform(5, 8), 0.5, [target("minecraft:nether_carver_replaceables", "minecraft:nether_gold_ore")], None, None),
    "debris": ("lens", uniform(2, 3), 0.35, [target("minecraft:base_stone_nether", "minecraft:ancient_debris")], None, None),
}
# las menas del Nether, solo en el netherrack (no en el basalto ni la piedra negra)
for k in ("quartz", "nether_gold"):
    shape, size, dens, _, host, ind = DEPOSITS[k]
    block = "minecraft:nether_quartz_ore" if k == "quartz" else "minecraft:nether_gold_ore"
    DEPOSITS[k] = (shape, size, dens, [{"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:netherrack"}, "state": {"Name": block}}],
                   host, ind)

BIOMES = {
    "coal": ["#minecraft:is_forest", "#minecraft:is_taiga", "minecraft:plains", "minecraft:sunflower_plains", "minecraft:swamp",
             "minecraft:mangrove_swamp", "minecraft:meadow", "minecraft:snowy_plains"],
    "iron": ["#minecraft:is_mountain", "#minecraft:is_hill", "#minecraft:is_badlands", "minecraft:savanna_plateau", "minecraft:windswept_savanna",
             "minecraft:stony_shore", "minecraft:deep_dark", "minecraft:dripstone_caves"],
    "copper": ["minecraft:desert", "#minecraft:is_badlands", "#minecraft:is_savanna", "#minecraft:is_mountain", "minecraft:dripstone_caves"],
    "gold": ["#minecraft:is_mountain", "#minecraft:is_badlands", "#minecraft:is_hill"],
    "lapis": ["#minecraft:is_mountain", "#minecraft:is_hill"],
    "diamond": ["#minecraft:is_taiga", "minecraft:snowy_plains", "minecraft:ice_spikes", "#minecraft:is_savanna", "minecraft:desert",
                "minecraft:deep_dark", "minecraft:plains"],
    "emerald": ["#minecraft:is_mountain"],
}


def placed(feature, *placement):
    return {"feature": feature, "placement": list(placement)}


def rarity(n):
    return {"type": "minecraft:rarity_filter", "chance": n}


def count(n):
    return {"type": "minecraft:count", "count": n}


SQUARE = {"type": "minecraft:in_square"}
BIOME = {"type": "minecraft:biome"}


def height(lo, hi):
    def anchor(v):
        if isinstance(v, str):
            kind, n = v.split(":")
            return {kind: int(n)}
        return {"absolute": v}
    return {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": anchor(lo), "max_inclusive": anchor(hi)}}


def favored(tag, elsewhere):
    return {"type": NS + ":in_biomes", "favored": tag, "elsewhere": elsewhere}


def dep(name):
    return NS + ":deposit_" + name


NONE = placed("minecraft:ore_coal", count(0))
OW = "#minecraft:is_overworld"
PLACED = {
    "ore_coal_upper": placed(dep("coal"), rarity(3), SQUARE, height(40, 130), favored("#evolvedwarfare:ores/coal", 0.3), BIOME),
    "ore_coal_lower": placed(dep("coal_deep"), rarity(6), SQUARE, height(0, 40), favored("#evolvedwarfare:ores/coal", 0.3), BIOME),
    "ore_iron_upper": placed(dep("iron_mountain"), rarity(3), SQUARE, height(72, 220), favored("#evolvedwarfare:ores/iron", 0.0), BIOME),
    "ore_iron_middle": placed(dep("iron"), rarity(5), SQUARE, height(-24, 64), favored("#evolvedwarfare:ores/iron", 0.45), BIOME),
    "ore_iron_small": NONE,
    "ore_gold": placed(dep("gold"), rarity(6), SQUARE, height(-64, 32), favored("#evolvedwarfare:ores/gold", 0.35), BIOME),
    "ore_gold_lower": NONE,
    "ore_gold_extra": placed(dep("gold_badlands"), rarity(2), SQUARE, height(32, 120), favored("#minecraft:is_badlands", 1.0), BIOME),
    "ore_redstone": placed(dep("redstone"), rarity(3), SQUARE, height(-64, 12), favored(OW, 1.0), BIOME),
    "ore_redstone_lower": NONE,
    "ore_diamond": placed(dep("diamond"), rarity(12), SQUARE, height(-12, 8), favored("#evolvedwarfare:ores/diamond", 0.5), BIOME),
    "ore_diamond_medium": NONE,
    "ore_diamond_large": NONE,
    "ore_diamond_buried": NONE,
    "ore_lapis": placed(dep("lapis"), rarity(7), SQUARE, height(-32, 32), favored("#evolvedwarfare:ores/lapis", 0.3), BIOME),
    "ore_lapis_buried": NONE,
    "ore_copper": placed(dep("copper"), rarity(5), SQUARE, height(-8, 96), favored("#evolvedwarfare:ores/copper", 0.35), BIOME),
    "ore_copper_large": placed(dep("copper_rich"), rarity(2), SQUARE, height(-8, 96), favored(OW, 1.0), BIOME),
    "ore_emerald": placed(dep("emerald"), rarity(6), SQUARE, height(-16, 200), favored("#evolvedwarfare:ores/emerald", 0.0), BIOME),
    # el Nether
    "ore_quartz_nether": placed(dep("quartz"), count(2), SQUARE, height("above_bottom:10", "below_top:10"), BIOME),
    "ore_quartz_deltas": placed(dep("quartz"), count(3), SQUARE, height("above_bottom:10", "below_top:10"), BIOME),
    "ore_gold_nether": placed(dep("nether_gold"), count(1), SQUARE, height("above_bottom:10", "below_top:10"), BIOME),
    "ore_gold_deltas": placed(dep("nether_gold"), count(2), SQUARE, height("above_bottom:10", "below_top:10"), BIOME),
    "ore_ancient_debris_large": placed(dep("debris"), rarity(10), SQUARE, height(8, 40), BIOME),
    "ore_debris_small": NONE,
}

MINERALS = collections.OrderedDict([
    # nombre: (color principal, color de acento)
    ("coal", ((40, 38, 38), (20, 20, 22))),
    ("iron", ((168, 84, 52), (196, 128, 92))),
    ("copper", ((72, 150, 120), (186, 110, 72))),
    ("gold", ((222, 186, 70), (236, 232, 216))),
    ("redstone", ((170, 30, 24), (220, 60, 40))),
    ("lapis", ((40, 66, 168), (90, 120, 210))),
    ("diamond", ((86, 98, 110), (96, 220, 220))),
    ("emerald", ((224, 224, 216), (40, 190, 90))),
])

TEXTS = {
    "block.evolvedwarfare.mineral_pebbles": ("Guijarros de mineral", "Mineral Pebbles"),
    "block.evolvedwarfare.gold_bearing_gravel": ("Grava aurífera", "Gold-Bearing Gravel"),
    "item.evolvedwarfare.prospector_pick": ("Pico de prospector", "Prospector's Pick"),
    "item.evolvedwarfare.prospector_pick.desc": ("Clic derecho a la roca: qué mineral hay cerca, cuánto y hacia dónde",
                                                 "Right-click rock: which mineral is nearby, how much and which way"),
    "message.evolvedwarfare.prospect.none": ("La roca suena hueca: no hay mineral cerca", "The rock sounds empty: no ore nearby"),
    "message.evolvedwarfare.prospect.found": ("Suena a %s %s, a unos %s bloques %s", "Sounds like %s %s, about %s blocks %s"),
    "message.evolvedwarfare.prospect.amount.lots": ("mucho", "plenty of"),
    "message.evolvedwarfare.prospect.amount.some": ("algo de", "some"),
    "message.evolvedwarfare.prospect.amount.little": ("un poco de", "a little"),
    "message.evolvedwarfare.prospect.below": ("abajo", "below"),
    "message.evolvedwarfare.prospect.above": ("arriba", "above"),
    "message.evolvedwarfare.prospect.dir_and": ("%s y %s", "%s and %s"),
    "message.evolvedwarfare.prospect.dir.n": ("al norte", "north"),
    "message.evolvedwarfare.prospect.dir.ne": ("al noreste", "northeast"),
    "message.evolvedwarfare.prospect.dir.e": ("al este", "east"),
    "message.evolvedwarfare.prospect.dir.se": ("al sureste", "southeast"),
    "message.evolvedwarfare.prospect.dir.s": ("al sur", "south"),
    "message.evolvedwarfare.prospect.dir.sw": ("al suroeste", "southwest"),
    "message.evolvedwarfare.prospect.dir.w": ("al oeste", "west"),
    "message.evolvedwarfare.prospect.dir.nw": ("al noroeste", "northwest"),
    "mineral.evolvedwarfare.coal": ("carbón", "coal"),
    "mineral.evolvedwarfare.iron": ("hierro", "iron"),
    "mineral.evolvedwarfare.copper": ("cobre", "copper"),
    "mineral.evolvedwarfare.gold": ("oro", "gold"),
    "mineral.evolvedwarfare.redstone": ("redstone", "redstone"),
    "mineral.evolvedwarfare.lapis": ("lapislázuli", "lapis lazuli"),
    "mineral.evolvedwarfare.diamond": ("diamante", "diamond"),
    "mineral.evolvedwarfare.emerald": ("esmeralda", "emerald"),
}


def pebbles_texture(main, accent, seed):
    """Piedrecitas grises, algunas teñidas del mineral (óxido, verdín, motas...)."""
    rng = random.Random(seed)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for _ in range(9):
        x, y = rng.randint(1, 13), rng.randint(1, 13)
        tinted = rng.random() < 0.6
        base = main if tinted else (118, 116, 112)
        for dx in range(rng.randint(1, 3)):
            for dy in range(rng.randint(1, 2)):
                j = rng.randint(-14, 14)
                c = tuple(max(0, min(255, v + j)) for v in base)
                im.putpixel((min(15, x + dx), min(15, y + dy)), c + (255,))
        if rng.random() < 0.5:
            im.putpixel((x, y), accent + (255,))
    return im


def gold_gravel_texture():
    rng = random.Random(11)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            g = rng.randint(96, 150)
            c = (g + rng.randint(-8, 8), g - 4 + rng.randint(-6, 6), g - 10 + rng.randint(-6, 6))
            im.putpixel((x, y), c + (255,))
    for _ in range(9):
        x, y = rng.randint(0, 15), rng.randint(0, 15)
        im.putpixel((x, y), (250, 214, 74, 255))
        if x < 15:
            im.putpixel((x + 1, y), (200, 160, 40, 255))
    return im


def pick_texture():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    wood, wood_d = (150, 108, 60, 255), (100, 70, 38, 255)
    metal, metal_l, metal_d = (170, 170, 176, 255), (226, 226, 230, 255), (92, 92, 100, 255)
    for k in range(10):
        x, y = 3 + k, 14 - k
        if 0 <= x < 16 and 0 <= y < 16 and k < 9:
            im.putpixel((x, y), wood if k % 2 else wood_d)
    # la cabeza: martillo a un lado y pico al otro, cruzada sobre el mango
    head = [(9, 1), (10, 2), (11, 3), (12, 4), (13, 5), (14, 6), (8, 2), (7, 3), (6, 4), (5, 4), (14, 7), (13, 7)]
    for i, (x, y) in enumerate(head):
        im.putpixel((x, y), metal_l if i % 3 == 0 else metal)
    for (x, y) in ((5, 5), (6, 5), (4, 4), (15, 7)):
        im.putpixel((x, y), metal_d)
    return im


def main():
    cf = os.path.join(DATA, "worldgen/configured_feature")
    for name, (shape, size, dens, targets, host, ind) in DEPOSITS.items():
        cfg = {"targets": targets, "shape": shape, "size": size, "density": dens}
        if host:
            cfg["host"] = host
        if ind:
            cfg["indicator"] = ind
        write_json(os.path.join(cf, "deposit_%s.json" % name), {"type": NS + ":deposit", "config": cfg})
    write_json(os.path.join(cf, "alluvial_gold.json"), {"type": NS + ":alluvial_gold", "config": {}})
    write_json(os.path.join(DATA, "worldgen/placed_feature/alluvial_gold.json"),
               placed(NS + ":alluvial_gold", rarity(3), SQUARE, {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"}, BIOME))
    for vid, pf in PLACED.items():
        write_json(os.path.join(MC, "worldgen/placed_feature/%s.json" % vid), pf)
    for k, values in BIOMES.items():
        write_json(os.path.join(DATA, "tags/worldgen/biome/ores/%s.json" % k), {"replace": False, "values": values})

    # guijarros
    tex = os.path.join(ASSETS, "textures/block")
    os.makedirs(tex, exist_ok=True)
    variants = {}
    for i, (m, (main_c, accent)) in enumerate(MINERALS.items()):
        pebbles_texture(main_c, accent, i).save(os.path.join(tex, "pebbles_%s.png" % m))
        boxes = [(2, 3, 3, 2), (8, 2, 3, 1), (11, 9, 2, 2), (4, 10, 3, 1), (9, 12, 2, 1), (6, 6, 2, 2)]
        elements = []
        for (x, z, w, h) in boxes:
            elements.append({"from": [x, 0, z], "to": [x + w, h, z + w], "faces": {
                f: {"uv": [x, z, x + w, z + w] if f in ("up", "down") else [x, 16 - h, x + w, 16], "texture": "#t"}
                for f in ("up", "down", "north", "south", "east", "west")}})
        write_json(os.path.join(ASSETS, "models/block/pebbles_%s.json" % m),
                   {"parent": "minecraft:block/block", "ambientocclusion": False,
                    "textures": {"t": "%s:block/pebbles_%s" % (NS, m), "particle": "%s:block/pebbles_%s" % (NS, m)}, "elements": elements})
        for turn in range(4):
            v = {"model": "%s:block/pebbles_%s" % (NS, m)}
            if turn:
                v["y"] = turn * 90
            variants["mineral=%s,turn=%d" % (m, turn)] = v
    write_json(os.path.join(ASSETS, "blockstates/mineral_pebbles.json"), {"variants": variants})

    # grava aurífera
    gold_gravel_texture().save(os.path.join(tex, "gold_bearing_gravel.png"))
    write_json(os.path.join(ASSETS, "models/block/gold_bearing_gravel.json"),
               {"parent": "minecraft:block/cube_all", "textures": {"all": "%s:block/gold_bearing_gravel" % NS}})
    write_json(os.path.join(ASSETS, "blockstates/gold_bearing_gravel.json"), {"variants": {"": {"model": "%s:block/gold_bearing_gravel" % NS}}})
    write_json(os.path.join(ASSETS, "models/item/gold_bearing_gravel.json"), {"parent": "%s:block/gold_bearing_gravel" % NS})
    silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    write_json(os.path.join(DATA, "loot_table/blocks/gold_bearing_gravel.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": "%s:gold_bearing_gravel" % NS, "conditions": [silk]},
            {"type": "minecraft:item", "name": "minecraft:gold_nugget", "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 5}},
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count",
                 "parameters": {"bonusMultiplier": 1}},
                {"function": "minecraft:explosion_decay"}]}]}]},
        {"rolls": 1, "conditions": [{"condition": "minecraft:inverted", "term": silk}, {"condition": "minecraft:survives_explosion"}],
         "entries": [{"type": "minecraft:item", "name": "minecraft:gravel"}]}]})
    add_tag(os.path.join(MC, "tags/block/mineable/shovel.json"), ["%s:gold_bearing_gravel" % NS])

    # pico de prospector
    os.makedirs(os.path.join(ASSETS, "textures/item"), exist_ok=True)
    pick_texture().save(os.path.join(ASSETS, "textures/item/prospector_pick.png"))
    write_json(os.path.join(ASSETS, "models/item/prospector_pick.json"),
               {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/prospector_pick" % NS}})
    write_json(os.path.join(DATA, "recipe/prospector_pick.json"), {"type": "minecraft:crafting_shaped", "category": "equipment",
                                                                  "pattern": ["II", " S"],
                                                                  "key": {"I": {"item": "minecraft:iron_ingot"}, "S": {"item": "minecraft:stick"}},
                                                                  "result": {"id": "%s:prospector_pick" % NS, "count": 1}})
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    print("menas: %d yacimientos, %d de vanilla cambiados" % (len(DEPOSITS), len(PLACED)))


if __name__ == "__main__":
    main()
