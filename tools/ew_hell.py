"""El Nether bíblico (fase 23): sin techo, montañas, colinas y lagos de fuego.

Sobrescribe el Nether de vanilla (tipo de dimensión, dimensión, ruido del terreno y reglas de superficie), crea los biomas
nuevos (llanuras de ceniza, campos de azufre y lagos de fuego), los bloques (bloque de ceniza y de azufre) y el azufre,
las texturas de los demonios, los diablillos y las almas en pena, sus sonidos (de vanilla, más graves), botín, recetas,
etiquetas y textos.

Uso: python3 tools/ew_hell.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import copy
import glob
import json
import os
import random
import sys

from PIL import Image

args = sys.argv[1:]
PREVIEW = None
if "--preview" in args:
    i = args.index("--preview")
    PREVIEW = args[i + 1]
    del args[i:i + 2]
ROOT = args[0] if args else os.path.join(os.path.dirname(__file__), "..")
HERE = os.path.dirname(os.path.abspath(__file__))
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
MC_DATA = os.path.join(ROOT, "src/main/resources/data/minecraft")


def write_json(path, obj, indent=2):
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
    write_json(path, t)


# ------------------------------------------------------------------ el terreno

def noise(name, scale=1.0):
    return {"type": "minecraft:noise", "noise": "%s:%s" % (NS, name), "xz_scale": scale, "y_scale": 0.0}


def add(a, b):
    return {"type": "minecraft:add", "argument1": a, "argument2": b}


def mul(a, b):
    return {"type": "minecraft:mul", "argument1": a, "argument2": b}


def clamp(x, lo, hi):
    return {"type": "minecraft:clamp", "input": x, "min": lo, "max": hi}


def flat(x):
    return {"type": "minecraft:flat_cache", "argument": {"type": "minecraft:cache_2d", "argument": x}}


NOISES = {
    "hell_hills": {"firstOctave": -7, "amplitudes": [1.0, 1.0, 0.5]},
    "hell_mountains": {"firstOctave": -8, "amplitudes": [1.0, 0.6, 0.3, 0.15]},
    "hell_mountain_mask": {"firstOctave": -9, "amplitudes": [1.0, 1.0]},
    "hell_basins": {"firstOctave": -8, "amplitudes": [1.0, 0.5]},
    "hell_detail": {"firstOctave": -5, "amplitudes": [1.0, 0.5, 0.25]},
    # el Abismo: dónde están los pozos (y sus paredes irregulares)
    "hell_abyss": {"firstOctave": -7, "amplitudes": [1.0, 0.6]},
    "hell_abyss_walls": {"firstOctave": -4, "amplitudes": [1.0, 0.5]},
    # el techo (fase 37): su altura, las estalactitas y los pilares que lo sostienen
    "hell_roof": {"firstOctave": -7, "amplitudes": [1.0, 0.5, 0.25]},
    "hell_stalactites": {"firstOctave": -3, "amplitudes": [1.0, 0.4]},
    "hell_pillars": {"firstOctave": -6, "amplitudes": [1.0, 0.3]},
}

# la altura del suelo (en "densidad": 1 = 64 bloques): llano a ~52, colinas de ±16, montañas de hasta +110 y hoyas de
# -30 (debajo de 32 se llenan de lava: lagos de fuego)
HILLS = mul(0.25, noise("hell_hills"))
RIDGE = add(1.0, mul(-2.0, {"type": "minecraft:abs", "argument": noise("hell_mountains")}))
MASK = clamp(mul(2.2, add(noise("hell_mountain_mask"), -0.05)), 0.0, 1.0)
MOUNTAINS = mul(MASK, mul(1.45, clamp(add(0.35, RIDGE), 0.0, 1.3)))
BASINS = mul(-0.75, clamp(mul(2.5, add(noise("hell_basins"), -0.15)), 0.0, 1.0))
TERRAIN = flat(add(add(-1.1875, HILLS), add(MOUNTAINS, BASINS)))
DETAIL = mul(0.12, {"type": "minecraft:noise", "noise": "%s:hell_detail" % NS, "xz_scale": 1.0, "y_scale": 1.5})
# sobre la superficie: 2 en y = 0 y -2 en y = 256
SURFACE = add(add({"type": "minecraft:y_clamped_gradient", "from_y": 0, "to_y": 256, "from_value": 2.0, "to_value": -2.0}, TERRAIN), DETAIL)
# las cavernas de siempre, solo en lo hondo (a más de ~20 bloques bajo la superficie) y con el suelo de abajo macizo
CAVES = add(add(0.3, "minecraft:nether/base_3d_noise"),
            {"type": "minecraft:y_clamped_gradient", "from_y": 0, "to_y": 16, "from_value": 2.5, "to_value": 0.0})
NEAR_SURFACE = mul(4.0, add(0.3, mul(-1.0, SURFACE)))
DENSITY = {"type": "minecraft:min", "argument1": SURFACE, "argument2": {"type": "minecraft:max", "argument1": CAVES, "argument2": NEAR_SURFACE}}
# el Abismo: pozos verticales enormes donde el ruido del abismo pasa de ~0,48 (con las paredes irregulares); el fondo
# (por debajo de y 10) queda macizo y lo de debajo de 32 es lava
ABYSS = noise("hell_abyss")
ABYSS_WALLS = {"type": "minecraft:noise", "noise": "%s:hell_abyss_walls" % NS, "xz_scale": 1.0, "y_scale": 0.6}
CARVE = {"type": "minecraft:max", "argument1": add(mul(10.0, add(0.46, mul(-1.0, ABYSS))), mul(0.3, ABYSS_WALLS)),
         "argument2": {"type": "minecraft:y_clamped_gradient", "from_y": 0, "to_y": 12, "from_value": 3.0, "to_value": -12.0}}
DENSITY = {"type": "minecraft:min", "argument1": DENSITY, "argument2": CARVE}
# el techo de roca (fase 37): una bóveda a ~230 (±10 con su ruido), muy por encima de las montañas, de la que cuelgan
# estalactitas de hasta ~25 bloques, y aquí y allá pilares colosales que la sostienen desde el suelo (y se ensanchan al
# llegar arriba); por encima, roca hasta la roca madre de y 320
ROOF_Y = 230


def gradient(y0, y1, v0, v1):
    return {"type": "minecraft:y_clamped_gradient", "from_y": y0, "to_y": y1, "from_value": v0, "to_value": v1}


STALACTITES = {"type": "minecraft:noise", "noise": "%s:hell_stalactites" % NS, "xz_scale": 1.0, "y_scale": 0.05}
ROOF = add(add(gradient(ROOF_Y - 70, ROOF_Y + 70, -6.0, 6.0), mul(0.9, noise("hell_roof"))),
           mul(gradient(ROOF_Y - 80, ROOF_Y, 0.0, 1.0), mul(3.0, clamp(add(STALACTITES, -0.3), 0.0, 1.0))))
PILLARS = clamp(mul(30.0, add(add(noise("hell_pillars"), mul(0.12, gradient(ROOF_Y - 90, ROOF_Y, 0.0, 1.0))), -0.8)), -1.0, 30.0)
DENSITY = {"type": "minecraft:max", "argument1": DENSITY, "argument2": {"type": "minecraft:max", "argument1": ROOF, "argument2": PILLARS}}
FINAL_DENSITY = {"type": "minecraft:squeeze", "argument": mul(0.64, {"type": "minecraft:interpolated",
                                                                      "argument": {"type": "minecraft:blend_density", "argument": DENSITY}})}

BIOMES = ["lake_of_fire", "valley_of_bones", "sodom_ruins", "abyss", "sulfur_mountains"]
# los biomas con flora (fase 37): como los del mundo normal, pero del infierno
FLORA = ["charred_forest", "thorn_jungle", "hanged_swamp", "ember_meadow"]
OLD_BIOMES = ["ash_plains", "brimstone_fields"]


def block(name, props=None):
    out = {"Name": name}
    if props:
        out["Properties"] = props
    return {"type": "minecraft:block", "result_state": out}


def cond(test, then):
    return {"type": "minecraft:condition", "if_true": test, "then_run": then}


def floor(depth=False):
    return {"type": "minecraft:stone_depth", "add_surface_depth": depth, "offset": 0, "secondary_depth_range": 0, "surface_type": "floor"}


def ceiling():
    return {"type": "minecraft:stone_depth", "add_surface_depth": True, "offset": 0, "secondary_depth_range": 0, "surface_type": "ceiling"}


def selector(minimum, name="minecraft:nether_state_selector"):
    return {"type": "minecraft:noise_threshold", "noise": name, "min_threshold": minimum, "max_threshold": 1.7976931348623157e308}


def biome_is(*names):
    return {"type": "minecraft:biome", "biome_is": ["%s:%s" % (NS, n) for n in names]}


def above(y):
    return {"type": "minecraft:y_above", "anchor": {"absolute": y}, "surface_depth_multiplier": 0, "add_stone_depth": False}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


ASH = "%s:ash_block" % NS
BRIMSTONE = "%s:brimstone_block" % NS
SALT = "%s:salt_block" % NS
FINE_SALT = "%s:fine_salt" % NS
BONE_DUST = "%s:bone_dust" % NS
CHARRED_BONE = "%s:charred_bone_block" % NS


def below(y):
    return {"type": "minecraft:not", "invert": above(y)}


SOIL = "%s:scorched_soil" % NS
HELL_RULES = [
    # la bóveda: por debajo, roca negra, basalto y magma (y netherrack, donde cuelga la piedra luminosa)
    cond(above(150), cond(ceiling(), seq(
        cond(selector(0.6), block("minecraft:magma_block")),
        cond(selector(0.25), block("minecraft:blackstone")),
        cond(selector(-0.1), block("minecraft:basalt", {"axis": "y"}))))),
    # los biomas con flora: tierra chamuscada (y barro y charcos de lava en la ciénaga)
    cond(biome_is("charred_forest", "ember_meadow"), seq(
        cond(floor(), seq(cond(selector(0.65), block("minecraft:soul_soil")), block(SOIL))),
        cond(floor(True), block("minecraft:soul_soil")))),
    cond(biome_is("thorn_jungle"), seq(
        cond(floor(), seq(cond(selector(0.45), block("minecraft:crimson_nylium")), block(SOIL))),
        cond(floor(True), block("minecraft:soul_soil")))),
    cond(biome_is("hanged_swamp"), seq(
        cond(floor(), seq(cond(selector(0.62), block("minecraft:lava", {"level": "0"})), cond(selector(0.25), block("minecraft:mud")),
                          cond(selector(-0.3), block("minecraft:soul_soil")), block(SOIL))),
        cond(floor(True), block("minecraft:mud")))),
    # las cumbres: piedra negra, basalto y obsidiana
    cond(above(118), cond(floor(True), seq(
        cond(selector(0.55), block("minecraft:obsidian")),
        cond(selector(0.15), block("minecraft:basalt", {"axis": "y"})),
        block("minecraft:blackstone")))),
    # Lago de Fuego y Azufre: orillas e islas de sal blanca, huesos calcinados y piedra negra
    cond(biome_is("lake_of_fire"), seq(
        cond(floor(), seq(
            cond(below(40), seq(cond(selector(0.45), block(CHARRED_BONE, {"axis": "y"})), block(SALT))),
            cond(selector(0.35), block("minecraft:magma_block")),
            cond(selector(-0.2), block(SALT)),
            block("minecraft:blackstone"))),
        cond(floor(True), block("minecraft:blackstone")),
        block("minecraft:blackstone"))),
    # Valle de los Huesos: polvo de hueso, bloques de hueso y suelo de almas
    cond(biome_is("valley_of_bones"), seq(
        cond(floor(), seq(cond(selector(0.5), block("minecraft:bone_block", {"axis": "y"})), cond(selector(-0.6), block("minecraft:soul_soil")),
                          block(BONE_DUST))),
        cond(floor(True), seq(cond(selector(0.3), block("minecraft:bone_block", {"axis": "y"})), block(BONE_DUST))))),
    # Ruinas de Sodoma y Gomorra: ceniza en las calles, manchas de azufre y sal
    cond(biome_is("sodom_ruins"), seq(
        cond(floor(), seq(cond(selector(0.55), block(BRIMSTONE)), cond(selector(0.25), block(FINE_SALT)), block(ASH))),
        cond(floor(True), seq(cond(selector(0.2), block("minecraft:basalt", {"axis": "y"})), block("minecraft:blackstone"))),
        block("minecraft:blackstone"))),
    # el Abismo: paredes de piedra negra y basalto
    cond(biome_is("abyss"), seq(
        cond(selector(0.4), block("minecraft:basalt", {"axis": "y"})),
        cond(selector(-0.5), block("minecraft:smooth_basalt")),
        block("minecraft:blackstone"))),
    # Montañas de Azufre y Oscuridad: azufre, basalto y piedra negra
    cond(biome_is("sulfur_mountains"), seq(
        cond(floor(), seq(cond(selector(0.2), block(BRIMSTONE)), cond(selector(-0.3), block("minecraft:basalt", {"axis": "y"})),
                          block("minecraft:blackstone"))),
        cond(floor(True), seq(cond(selector(0.1), block(BRIMSTONE)), block("minecraft:blackstone"))),
        cond(selector(0.5), block(BRIMSTONE)),
        block("minecraft:blackstone"))),
]


def noise_settings():
    with open(os.path.join(HERE, "vanilla", "nether_noise_settings.json"), encoding="utf-8") as f:
        s = json.load(f)
    s["noise"]["height"] = 320
    s["noise_router"]["final_density"] = FINAL_DENSITY
    # los biomas siguen al terreno: las montañas (continentalidad), las hoyas de lava (erosión) y el Abismo (rareza)
    s["noise_router"]["continents"] = noise("hell_mountain_mask")
    s["noise_router"]["erosion"] = noise("hell_basins")
    s["noise_router"]["ridges"] = ABYSS
    rules = s["surface_rule"]["sequence"]
    # vuelve el techo de roca madre (fase 37): las reglas de vanilla tal cual, y las nuestras después del suelo y el techo
    s["surface_rule"]["sequence"] = rules[:3] + HELL_RULES + rules[3:]
    return s


DIMENSION_TYPE = {
    "ambient_light": 0.1, "bed_works": False, "coordinate_scale": 8.0, "effects": "%s:hell" % NS, "fixed_time": 18000,
    "has_ceiling": True, "has_raids": False, "has_skylight": False, "height": 320, "infiniburn": "#minecraft:infiniburn_nether",
    "logical_height": 248, "min_y": 0, "monster_spawn_block_light_limit": 15, "monster_spawn_light_level": 7, "natural": False,
    "piglin_safe": True, "respawn_anchor_works": True, "ultrawarm": True,
}


ANY = [-1.0, 1.0]
LOW_C = [-1.0, 0.45]
LOW_E = [-1.0, 0.38]
LOW_W = [-1.0, 0.52]


def point(biome, temperature, humidity, offset, c=LOW_C, e=LOW_E, w=LOW_W):
    return {"biome": biome, "parameters": {"continentalness": c, "depth": 0.0, "erosion": e, "humidity": humidity,
                                           "offset": offset, "temperature": temperature, "weirdness": w}}


DIMENSION = {"type": "minecraft:the_nether", "generator": {"type": "minecraft:noise", "settings": "minecraft:nether", "biome_source": {
    "type": "minecraft:multi_noise", "biomes": [
        # temperatura y humedad repartidas en casillas (así cada sitio cae en una sola): los de siempre, en lo húmedo
        point("minecraft:basalt_deltas", [-1.0, -0.25], [0.35, 0.65], 0.0),
        point("minecraft:nether_wastes", [-0.25, 0.25], [0.35, 0.65], 0.0),
        point("minecraft:crimson_forest", [0.25, 1.0], [0.35, 0.65], 0.0),
        point("minecraft:warped_forest", [-1.0, 0.0], [0.65, 1.0], 0.0),
        point("minecraft:soul_sand_valley", [0.0, 1.0], [0.65, 1.0], 0.0),
        # los de flora, en lo templado
        point("%s:hanged_swamp" % NS, [-1.0, -0.4], [-0.2, 0.35], 0.0),
        point("%s:charred_forest" % NS, [-0.4, 0.05], [-0.2, 0.35], 0.0),
        point("%s:ember_meadow" % NS, [0.05, 0.45], [-0.2, 0.35], 0.0),
        point("%s:thorn_jungle" % NS, [0.45, 1.0], [-0.2, 0.35], 0.0),
        # los bíblicos, en lo seco
        point("%s:valley_of_bones" % NS, [-1.0, 0.0], [-1.0, -0.2], 0.0),
        point("%s:sodom_ruins" % NS, [0.0, 1.0], [-1.0, -0.2], 0.0),
        point("%s:sulfur_mountains" % NS, ANY, ANY, 0.0, c=[0.45, 1.0], e=ANY, w=LOW_W),
        point("%s:lake_of_fire" % NS, ANY, ANY, 0.0, c=LOW_C, e=[0.38, 1.0], w=LOW_W),
        point("%s:abyss" % NS, ANY, ANY, 0.0, c=ANY, e=ANY, w=[0.52, 1.0]),
    ]}}}

ORES = ["minecraft:ore_magma", "minecraft:spring_closed", "minecraft:ore_gravel_nether", "minecraft:ore_blackstone", "minecraft:ore_gold_nether",
        "minecraft:ore_quartz_nether", "minecraft:ore_ancient_debris_large", "minecraft:ore_debris_small"]


def biome(fog, particle, features7, loop=None, additions=None, mood=None, music=None, step2=(), step4=(), step9=("minecraft:spring_lava",),
          monsters=(), creature=True, chance=0.0111):
    effects = {"fog_color": fog, "sky_color": fog, "water_color": 4159204, "water_fog_color": 329011, "particle": particle}
    if loop:
        effects["ambient_sound"] = loop
    if additions:
        effects["additions_sound"] = {"sound": additions, "tick_chance": chance}
    if mood:
        effects["mood_sound"] = {"block_search_extent": 8, "offset": 2.0, "sound": mood, "tick_delay": 6000}
    if music:
        effects["music"] = {"max_delay": 24000, "min_delay": 12000, "replace_current_music": False, "sound": music}
    features = [[] for _ in range(11)]
    features[2] = list(step2)
    features[4] = list(step4)
    features[7] = list(features7) + ["minecraft:glowstone_extra", "minecraft:glowstone", f("ceiling_vines"), f("roof_lavafall")]
    features[9] = list(step9)
    return {"has_precipitation": False, "temperature": 2.0, "downfall": 0.0, "effects": effects,
            "carvers": {"air": "minecraft:nether_cave"}, "features": features, "spawn_costs": {},
            "spawners": {"ambient": [], "axolotls": [], "misc": [], "underground_water_creature": [], "water_ambient": [], "water_creature": [],
                         "creature": [{"type": "minecraft:strider", "weight": 60, "minCount": 1, "maxCount": 2}] if creature else [],
                         "monster": [{"type": t, "weight": w, "minCount": a, "maxCount": b} for (t, w, a, b) in monsters]}}


def f(name):
    return "%s:%s" % (NS, name)


FLORA_MOBS = [("%s:imp" % NS, 15, 1, 2), ("%s:demon" % NS, 6, 1, 1), ("%s:lost_soul" % NS, 10, 1, 2), ("minecraft:zombified_piglin", 15, 1, 2)]


def flora_biome(fog, particle, features, creatures, monsters, loop, mood, music, additions=None, chance=0.0111):
    b = biome(fog, particle, features + ORES, loop=loop, additions=additions, mood=mood, music=music, monsters=FLORA_MOBS + monsters, chance=chance)
    b["spawners"]["creature"] = [{"type": t, "weight": w, "minCount": a, "maxCount": c} for (t, w, a, c) in creatures]
    b["spawners"]["ambient"] = [{"type": "%s:ember_moth" % NS, "weight": 30, "minCount": 2, "maxCount": 5}]
    b["spawn_costs"] = {}
    return b


def flora_biomes():
    return {
        "charred_forest": flora_biome(0x3A1A10, {"options": {"type": "minecraft:lava"}, "probability": 0.0015},
                                      [f("charred_trees"), f("charred_fancy_trees"), f("ember_grass_patch"), f("hellbloom_patch"), f("bloodcap_patch"),
                                       f("ember_fern_patch")],
                                      [("%s:ash_stag" % NS, 30, 2, 4), ("minecraft:strider", 10, 1, 2)], [("%s:fallen" % NS, 4, 1, 1)],
                                      "minecraft:ambient.crimson_forest.loop", "minecraft:ambient.crimson_forest.mood", "minecraft:music.nether.crimson_forest",
                                      additions="minecraft:block.fire.ambient", chance=0.006),
        "thorn_jungle": flora_biome(0x4A0E0E, {"options": {"type": "minecraft:crimson_spore"}, "probability": 0.02},
                                    [f("thorn_giants"), f("charred_trees"), f("thorn_bush_patch"), f("ember_grass_patch"), f("bloodcap_patch"),
                                     f("ember_fern_patch"), "minecraft:weeping_vines"],
                                    [("minecraft:hoglin", 12, 2, 3), ("%s:ash_stag" % NS, 8, 1, 2)], [("%s:fire_serpent" % NS, 20, 1, 2)],
                                    "minecraft:ambient.crimson_forest.loop", "minecraft:ambient.crimson_forest.mood", "minecraft:music.nether.crimson_forest",
                                    additions="minecraft:ambient.crimson_forest.additions"),
        "hanged_swamp": flora_biome(0x1E1A14, {"options": {"type": "minecraft:smoke"}, "probability": 0.006},
                                    [f("gallows_trees"), f("corpse_lily_patch"), f("bloodcap_patch"), f("ember_grass_patch")],
                                    [("%s:lava_toad" % NS, 30, 2, 5), ("minecraft:strider", 20, 1, 2)],
                                    [("%s:damned_spirit" % NS, 10, 1, 2), ("minecraft:skeleton", 20, 1, 2), ("minecraft:witch", 5, 1, 1)],
                                    "minecraft:ambient.soul_sand_valley.loop", "minecraft:ambient.soul_sand_valley.mood",
                                    "minecraft:music.nether.soul_sand_valley", additions="minecraft:block.chain.step", chance=0.004),
        "ember_meadow": flora_biome(0x5A2A0A, {"options": {"type": "minecraft:lava"}, "probability": 0.003},
                                    [f("meadow_trees"), f("ember_grass_meadow"), f("hellbloom_meadow"), f("ember_fern_patch")],
                                    [("%s:ash_stag" % NS, 40, 3, 6), ("minecraft:strider", 10, 1, 2)], [("%s:desert_goat" % NS, 10, 1, 3)],
                                    "minecraft:ambient.nether_wastes.loop", "minecraft:ambient.nether_wastes.mood", "minecraft:music.nether.nether_wastes",
                                    additions="minecraft:block.fire.ambient", chance=0.004),
    }


def biomes():
    return {**flora_biomes(),
        "lake_of_fire": biome(0x6A1606, {"options": {"type": "minecraft:lava"}, "probability": 0.006},
                              ["minecraft:spring_open", "minecraft:patch_fire", f("salt_island"), f("bone_pile"), f("sunken_ruin")] + ORES,
                              loop="minecraft:ambient.basalt_deltas.loop", additions="minecraft:ambient.soul_sand_valley.additions",
                              mood="minecraft:ambient.basalt_deltas.mood", music="minecraft:music.nether.basalt_deltas", step4=["minecraft:delta"],
                              monsters=[("%s:demon" % NS, 25, 1, 2), ("%s:imp" % NS, 30, 1, 3), ("minecraft:magma_cube", 60, 2, 4),
                                        ("minecraft:ghast", 20, 1, 2), ("%s:moloch" % NS, 2, 1, 1), ("%s:abaddon" % NS, 1, 1, 1),
                                        ("%s:fire_serpent" % NS, 25, 1, 2), ("%s:desert_goat" % NS, 15, 1, 3)]),
        "valley_of_bones": biome(0x4A2A26, {"options": {"type": "minecraft:white_ash"}, "probability": 0.05},
                                 [f("bone_fossil"), f("skull_patch"), f("bone_spikes")] + ORES,
                                 loop="minecraft:ambient.soul_sand_valley.loop", additions="minecraft:ambient.soul_sand_valley.additions",
                                 mood="minecraft:ambient.soul_sand_valley.mood", music="minecraft:music.nether.soul_sand_valley",
                                 creature=False, step9=(),
                                 monsters=[("minecraft:skeleton", 60, 2, 4), ("minecraft:wither_skeleton", 15, 1, 2), ("%s:lost_soul" % NS, 40, 1, 3),
                                           ("minecraft:ghast", 20, 1, 1), ("%s:legion" % NS, 3, 1, 1), ("%s:fallen" % NS, 10, 1, 1),
                                           ("%s:damned_spirit" % NS, 12, 1, 2), ("%s:desert_goat" % NS, 8, 1, 2)]),
        "sodom_ruins": biome(0x2E2622, {"options": {"type": "minecraft:ash"}, "probability": 0.03},
                             [f("ruin"), f("salt_pillar"), f("salt_tree"), "minecraft:patch_fire"] + ORES,
                             additions="minecraft:block.deepslate.break", mood="minecraft:ambient.cave", chance=0.003,
                             monsters=[("minecraft:zombified_piglin", 30, 2, 3), ("%s:demon" % NS, 20, 1, 1), ("%s:imp" % NS, 20, 1, 2),
                                       ("%s:lost_soul" % NS, 20, 1, 2), ("%s:legion" % NS, 3, 1, 1), ("%s:nephilim" % NS, 5, 1, 1),
                                       ("%s:salt_statue" % NS, 14, 1, 3), ("%s:damned_spirit" % NS, 10, 1, 2), ("%s:fallen" % NS, 6, 1, 1)]),
        "abyss": biome(0x0C0606, {"options": {"type": "minecraft:smoke"}, "probability": 0.02},
                       [f("abyss_vents"), "minecraft:patch_soul_fire"] + ORES,
                       loop="minecraft:ambient.warped_forest.loop", additions="minecraft:entity.phantom.flap", mood="minecraft:block.chain.place",
                       chance=0.02, creature=False,
                       monsters=[("%s:lost_soul" % NS, 40, 1, 3), ("%s:imp" % NS, 30, 1, 3), ("%s:beelzebub" % NS, 3, 1, 1),
                                 ("minecraft:ghast", 10, 1, 1), ("minecraft:enderman", 5, 1, 2), ("%s:abyss_locust" % NS, 35, 8, 15),
                                 ("%s:greater_locust" % NS, 6, 1, 2), ("%s:fallen" % NS, 4, 1, 1)]),
        "sulfur_mountains": biome(0x241A10, {"options": {"type": "minecraft:large_smoke"}, "probability": 0.004},
                                  ["minecraft:patch_fire", f("sulfur_crystals"), f("sulfur_vents"), "%s:ore_brimstone" % NS] + ORES,
                                  loop="minecraft:ambient.basalt_deltas.loop", additions="minecraft:item.firecharge.use",
                                  mood="minecraft:ambient.basalt_deltas.mood", music="minecraft:music.nether.nether_wastes", chance=0.004,
                                  creature=False,
                                  monsters=[("minecraft:blaze", 30, 1, 2), ("%s:imp" % NS, 30, 1, 3), ("minecraft:magma_cube", 30, 1, 3),
                                            ("%s:demon" % NS, 10, 1, 1), ("%s:beelzebub" % NS, 1, 1, 1), ("%s:moloch" % NS, 2, 1, 1),
                                            ("%s:fire_serpent" % NS, 20, 1, 2), ("%s:desert_goat" % NS, 20, 1, 3)]),
    }


ORE_BRIMSTONE_CONFIGURED = {"type": "minecraft:ore", "config": {"size": 16, "discard_chance_on_air_exposure": 0.0, "targets": [
    {"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:netherrack"}, "state": {"Name": BRIMSTONE}}]}}
ORE_BRIMSTONE_PLACED = {"feature": "%s:ore_brimstone" % NS, "placement": [
    {"type": "minecraft:count", "count": 14}, {"type": "minecraft:in_square"},
    {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 10}, "max_inclusive": {"absolute": 140}}},
    {"type": "minecraft:biome"}]}


# ------------------------------------------------------------------ texturas

def noisy(im, box, base, var, seed, alpha=255):
    r = random.Random(seed)
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            k = r.uniform(-var, var)
            im.putpixel((x, y), tuple(max(0, min(255, int(c * (1 + k)))) for c in base) + (alpha,))


def block_textures():
    out = {}
    # ceniza: gris claro con motas
    im = Image.new("RGBA", (16, 16))
    noisy(im, (0, 0, 16, 16), (122, 116, 112), 0.12, 1)
    r = random.Random(2)
    for _ in range(18):
        x, y = r.randrange(16), r.randrange(16)
        im.putpixel((x, y), (r.choice([64, 70, 160, 172]),) * 3 + (255,))
    out["block/ash_block"] = im
    # azufre: amarillo cristalino con vetas naranjas
    im = Image.new("RGBA", (16, 16))
    noisy(im, (0, 0, 16, 16), (214, 190, 44), 0.12, 3)
    r = random.Random(4)
    for _ in range(5):
        x, y = r.randrange(16), r.randrange(16)
        for k in range(r.randint(2, 5)):
            xx, yy = (x + k) % 16, (y + k // 2) % 16
            im.putpixel((xx, yy), (238, 150, 30, 255))
    for _ in range(10):
        x, y = r.randrange(16), r.randrange(16)
        im.putpixel((x, y), (252, 240, 140, 255))
    out["block/brimstone_block"] = im
    # el azufre en trozos (objeto)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    r = random.Random(5)
    for (cx, cy, rad) in ((6, 9, 3.6), (10, 7, 3.0), (9, 11, 2.4)):
        for y in range(16):
            for x in range(16):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if d <= rad:
                    light = 1.0 - (x - cx + y - cy) / (rad * 4)
                    c = (int(214 * light), int(186 * light), int(40 * light))
                    if d > rad - 0.9:
                        c = (150, 118, 20)
                    im.putpixel((x, y), tuple(max(0, min(255, v)) for v in c) + (255,))
    for (x, y) in ((5, 7), (9, 5), (8, 10)):
        im.putpixel((x, y), (254, 246, 170, 255))
    out["item/brimstone"] = im
    return out


def box_faces(u, v, w, h, d):
    """Las caras de un cubo de UV en caja: (x0, y0, x1, y1) de cada una."""
    return {"top": (u + d, v, u + d + w, v + d), "bottom": (u + d + w, v, u + d + 2 * w, v + d),
            "right": (u, v + d, u + d, v + d + h), "front": (u + d, v + d, u + d + w, v + d + h),
            "left": (u + d + w, v + d, u + 2 * d + w, v + d + h), "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}


def paint_box(im, u, v, w, h, d, base, var, seed, alpha=255):
    for k, (x0, y0, x1, y1) in enumerate(box_faces(u, v, w, h, d).values()):
        noisy(im, (int(x0), int(y0), int(x1), int(y1)), base, var, seed + k, alpha)


def demon_texture(imp):
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    eyes = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin = (196, 70, 34) if imp else (118, 22, 16)
    dark = (130, 40, 18) if imp else (70, 12, 10)
    paint_box(im, 0, 0, 8, 8, 8, skin, 0.1, 10)                 # cabeza
    paint_box(im, 16, 16, 8, 12, 4, skin, 0.1, 20)              # cuerpo
    paint_box(im, 40, 16, 4, 12, 4, skin, 0.1, 30)              # brazo
    paint_box(im, 0, 16, 4, 12, 4, skin, 0.1, 40)               # pierna
    # piernas de macho cabrío: pelo oscuro y pezuñas
    for (x0, y0, x1, y1) in box_faces(0, 16, 4, 12, 4).values():
        noisy(im, (int(x0), int(y0) + (0 if y0 == 16 else 4), int(x1), int(y1)), (54, 30, 22), 0.15, 41)
    for (x0, y0, x1, y1) in list(box_faces(0, 16, 4, 12, 4).values())[2:]:
        noisy(im, (int(x0), int(y1) - 2, int(x1), int(y1)), (24, 20, 20), 0.1, 42)
    # cara: cejas, ojos amarillos que brillan, boca con colmillos
    fx, fy = 8, 8
    for x in range(fx, fx + 8):
        im.putpixel((x, fy + 2), dark + (255,))
    for (ex, ey) in ((fx + 1, fy + 3), (fx + 2, fy + 3), (fx + 5, fy + 3), (fx + 6, fy + 3)):
        im.putpixel((ex, ey), (255, 214, 40, 255))
        eyes.putpixel((ex, ey), (255, 220, 60, 255))
    for x in range(fx + 2, fx + 6):
        im.putpixel((x, fy + 6), (30, 8, 6, 255))
    im.putpixel((fx + 2, fy + 7), (236, 228, 200, 255))
    im.putpixel((fx + 5, fy + 7), (236, 228, 200, 255))
    # pecho y costillas
    for y in (22, 25):
        for x in range(21, 27):
            im.putpixel((x, y), dark + (255,))
    # cuernos (arriba: hueso; la punta, negra), rabo, alas de murciélago
    paint_box(im, 0, 32, 1, 4, 1, (200, 186, 150) if not imp else (60, 40, 30), 0.1, 50)
    paint_box(im, 0, 40, 1, 2, 1, (30, 24, 22), 0.1, 51)
    paint_box(im, 24, 32, 1, 1, 8, dark, 0.1, 52)
    w = 9 if imp else 12
    wh = 7 if imp else 10
    for (x0, y0, x1, y1) in box_faces(0, 48, w, wh, 0).values():
        if x1 > x0 and y1 > y0:
            noisy(im, (int(x0), int(y0), int(x1), int(y1)), (60, 14, 14) if not imp else (120, 40, 20), 0.12, 53)
            # los dedos del ala
            for k in range(int(x0), int(x1), 3):
                for y in range(int(y0), int(y1)):
                    im.putpixel((k, y), (30, 8, 8, 255))
    return im, eyes


def soul_texture():
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    pale = (196, 228, 236)
    paint_box(im, 0, 0, 8, 8, 8, pale, 0.08, 60, 230)
    paint_box(im, 0, 16, 8, 10, 4, pale, 0.08, 61, 210)
    paint_box(im, 0, 32, 6, 6, 3, pale, 0.08, 62, 160)
    paint_box(im, 0, 44, 3, 6, 2, pale, 0.08, 63, 110)
    paint_box(im, 32, 16, 3, 11, 3, pale, 0.08, 64, 200)
    paint_box(im, 48, 16, 3, 11, 3, pale, 0.08, 65, 200)
    # la cara: cuencas vacías y la boca abierta en un lamento
    fx, fy = 8, 8
    for (x, y) in ((fx + 1, fy + 2), (fx + 2, fy + 2), (fx + 1, fy + 3), (fx + 2, fy + 3),
                   (fx + 5, fy + 2), (fx + 6, fy + 2), (fx + 5, fy + 3), (fx + 6, fy + 3)):
        im.putpixel((x, y), (16, 24, 40, 255))
    for (x, y) in ((fx + 3, fy + 5), (fx + 4, fy + 5), (fx + 3, fy + 6), (fx + 4, fy + 6), (fx + 3, fy + 7), (fx + 4, fy + 7),
                   (fx + 2, fy + 6), (fx + 5, fy + 6)):
        im.putpixel((x, y), (16, 24, 40, 255))
    return im


# ------------------------------------------------------------------ sonidos, botín y textos

SOUNDS = {
    "entity.demon.ambient": [("minecraft:mob/hoglin/angry1", 0.6), ("minecraft:mob/hoglin/angry2", 0.6), ("minecraft:mob/ravager/ambient1", 0.7)],
    "entity.demon.hurt": [("minecraft:mob/ravager/hurt1", 0.7), ("minecraft:mob/ravager/hurt2", 0.7)],
    "entity.demon.death": [("minecraft:mob/ravager/death1", 0.6), ("minecraft:mob/ravager/death2", 0.6)],
    "entity.imp.ambient": [("minecraft:mob/piglin/angry1", 1.4), ("minecraft:mob/piglin/angry2", 1.4), ("minecraft:mob/vex/idle1", 0.8)],
    "entity.imp.hurt": [("minecraft:mob/piglin/hurt1", 1.4), ("minecraft:mob/piglin/hurt2", 1.4)],
    "entity.imp.death": [("minecraft:mob/piglin/death1", 1.3), ("minecraft:mob/piglin/death2", 1.3)],
    "entity.lost_soul.ambient": [("minecraft:mob/ghast/moan1", 0.5), ("minecraft:mob/ghast/moan2", 0.45), ("minecraft:mob/ghast/moan4", 0.5),
                                 ("minecraft:mob/ghast/moan6", 0.55)],
    "entity.lost_soul.hurt": [("minecraft:mob/vex/hurt1", 0.6), ("minecraft:mob/vex/hurt2", 0.6)],
    "entity.lost_soul.death": [("minecraft:mob/ghast/death", 0.5)],
    "weather.fire_rain": [("minecraft:random/explode1", 0.4), ("minecraft:ambient/weather/thunder1", 0.5), ("minecraft:ambient/weather/thunder2", 0.45)],
}

LOOT = {
    "demon": [("minecraft:blaze_rod", 0, 2), ("minecraft:gold_ingot", 0, 1), ("%s:brimstone" % NS, 1, 3)],
    "imp": [("minecraft:blaze_powder", 0, 2), ("minecraft:magma_cream", 0, 1)],
    "lost_soul": [("minecraft:ghast_tear", 0, 1), ("minecraft:soul_sand", 0, 1)],
}


def entity_loot(name):
    pools = []
    for item, lo, hi in LOOT[name]:
        pools.append({"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": item, "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False},
            {"function": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0, "max": 1}, "enchantment": "minecraft:looting"}]}]})
    return {"type": "minecraft:entity", "pools": pools, "random_sequence": "%s:entities/%s" % (NS, name)}


TEXTS = {
    "biome.evolvedwarfare.lake_of_fire": ("Lago de Fuego y Azufre", "Lake of Fire and Brimstone"),
    "biome.evolvedwarfare.valley_of_bones": ("Valle de los Huesos", "Valley of Bones"),
    "biome.evolvedwarfare.sodom_ruins": ("Ruinas de Sodoma y Gomorra", "Ruins of Sodom and Gomorrah"),
    "biome.evolvedwarfare.abyss": ("El Abismo", "The Abyss"),
    "biome.evolvedwarfare.sulfur_mountains": ("Montañas de Azufre y Oscuridad", "Mountains of Sulfur and Darkness"),
    "block.evolvedwarfare.ash_block": ("Bloque de ceniza", "Ash Block"),
    "block.evolvedwarfare.brimstone_block": ("Bloque de azufre", "Brimstone Block"),
    "item.evolvedwarfare.brimstone": ("Azufre", "Brimstone"),
    "item.evolvedwarfare.brimstone.desc": ("Arde mucho tiempo en el horno. Con carbón, pólvora.", "Burns a long time in a furnace. With coal, gunpowder."),
    "entity.evolvedwarfare.demon": ("Demonio", "Demon"),
    "entity.evolvedwarfare.imp": ("Diablillo", "Imp"),
    "entity.evolvedwarfare.lost_soul": ("Alma en pena", "Lost Soul"),
    "item.evolvedwarfare.demon_spawn_egg": ("Huevo de demonio", "Demon Spawn Egg"),
    "item.evolvedwarfare.imp_spawn_egg": ("Huevo de diablillo", "Imp Spawn Egg"),
    "item.evolvedwarfare.lost_soul_spawn_egg": ("Huevo de alma en pena", "Lost Soul Spawn Egg"),
    "subtitles.evolvedwarfare.entity.demon.ambient": ("Un demonio gruñe", "Demon growls"),
    "subtitles.evolvedwarfare.entity.demon.hurt": ("Un demonio se duele", "Demon hurts"),
    "subtitles.evolvedwarfare.entity.demon.death": ("Un demonio muere", "Demon dies"),
    "subtitles.evolvedwarfare.entity.imp.ambient": ("Un diablillo chilla", "Imp shrieks"),
    "subtitles.evolvedwarfare.entity.imp.hurt": ("Un diablillo se duele", "Imp hurts"),
    "subtitles.evolvedwarfare.entity.imp.death": ("Un diablillo muere", "Imp dies"),
    "subtitles.evolvedwarfare.entity.lost_soul.ambient": ("Un alma en pena gime", "Lost soul moans"),
    "subtitles.evolvedwarfare.entity.lost_soul.hurt": ("Un alma en pena se queja", "Lost soul wails"),
    "subtitles.evolvedwarfare.entity.lost_soul.death": ("Un alma en pena se desvanece", "Lost soul fades away"),
    "subtitles.evolvedwarfare.weather.fire_rain": ("Retumba el cielo", "The sky rumbles"),
    "message.evolvedwarfare.fire_rain.start": ("¡Llueve fuego y azufre del cielo! Ponte a cubierto.", "Fire and brimstone rain from the sky! Take cover."),
    "message.evolvedwarfare.fire_rain.end": ("El cielo se calma.", "The sky calms down."),
    "config.evolvedwarfare.fireRain": ("Lluvia de fuego en el Nether", "Fire rain in the Nether"),
    "config.evolvedwarfare.fireRain.tip": ("De vez en cuando cae fuego del cielo del Nether: quema lo que pilla al raso.",
                                          "Now and then fire falls from the Nether sky: it burns whatever is out in the open."),
    "config.evolvedwarfare.fireRainEveryMinutes": ("Lluvia de fuego cada (min)", "Fire rain every (min)"),
    "config.evolvedwarfare.fireRainEveryMinutes.tip": ("Minutos, más o menos, entre una lluvia de fuego y la siguiente.",
                                                      "Minutes, roughly, between one fire rain and the next."),
    "death.attack.evolvedwarfare.soul_touch": ("A %1$s le arrancó el alma un alma en pena", "%1$s had their soul torn out by a lost soul"),
}


def main():
    # el Nether de vanilla, sobrescrito
    write_json(os.path.join(MC_DATA, "dimension_type/the_nether.json"), DIMENSION_TYPE)
    write_json(os.path.join(MC_DATA, "dimension/the_nether.json"), DIMENSION)
    write_json(os.path.join(MC_DATA, "worldgen/noise_settings/nether.json"), noise_settings())
    for name, n in NOISES.items():
        write_json(os.path.join(DATA, "worldgen/noise/%s.json" % name), n)
    for name, b in biomes().items():
        write_json(os.path.join(DATA, "worldgen/biome/%s.json" % name), b)
    write_json(os.path.join(DATA, "worldgen/configured_feature/ore_brimstone.json"), ORE_BRIMSTONE_CONFIGURED)
    write_json(os.path.join(DATA, "worldgen/placed_feature/ore_brimstone.json"), ORE_BRIMSTONE_PLACED)
    add_tag(os.path.join(MC_DATA, "tags/worldgen/biome/is_nether.json"), ["%s:%s" % (NS, b) for b in BIOMES + FLORA])
    for tag in ("nether_fortress/has_structure", "bastion_remnant/has_structure", "nether_fossil/has_structure", "ruined_portal_nether/has_structure"):
        values = ["%s:%s" % (NS, b) for b in BIOMES]
        if tag.startswith("nether_fossil"):
            values = ["%s:valley_of_bones" % NS]
        if tag.startswith("bastion"):
            values = ["%s:sulfur_mountains" % NS, "%s:lake_of_fire" % NS]
        add_tag(os.path.join(MC_DATA, "tags/worldgen/biome/has_structure/" + tag.split("/")[0] + ".json"), values)
    # fuera los biomas viejos de las etiquetas (y sus archivos)
    for path in glob.glob(os.path.join(MC_DATA, "tags/worldgen/biome/**/*.json"), recursive=True):
        with open(path, encoding="utf-8") as fh:
            t = json.load(fh)
        kept = [v for v in t["values"] if v not in ["%s:%s" % (NS, b) for b in OLD_BIOMES]]
        if kept != t["values"]:
            t["values"] = kept
            write_json(path, t)
    for b in OLD_BIOMES:
        old = os.path.join(DATA, "worldgen/biome/%s.json" % b)
        if os.path.exists(old):
            os.remove(old)
    # bloques y azufre
    for name, im in block_textures().items():
        path = os.path.join(ASSETS, "textures", name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)
    for b in ("ash_block", "brimstone_block"):
        write_json(os.path.join(ASSETS, "models/block/%s.json" % b), {"parent": "minecraft:block/cube_all", "textures": {"all": "%s:block/%s" % (NS, b)}})
        write_json(os.path.join(ASSETS, "blockstates/%s.json" % b), {"variants": {"": {"model": "%s:block/%s" % (NS, b)}}})
        write_json(os.path.join(ASSETS, "models/item/%s.json" % b), {"parent": "%s:block/%s" % (NS, b)})
    write_json(os.path.join(ASSETS, "models/item/brimstone.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/brimstone" % NS}})
    write_json(os.path.join(DATA, "loot_table/blocks/ash_block.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
        "conditions": [{"condition": "minecraft:survives_explosion"}], "entries": [{"type": "minecraft:item", "name": ASH}]}]})
    write_json(os.path.join(DATA, "loot_table/blocks/brimstone_block.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
        "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": BRIMSTONE, "conditions": [{"condition": "minecraft:match_tool", "predicate": {"predicates": {
                "minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
            {"type": "minecraft:item", "name": "%s:brimstone" % NS, "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 4}, "add": False},
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count", "parameters": {"bonusMultiplier": 1}},
                {"function": "minecraft:explosion_decay"}]}]}]}]})
    add_tag(os.path.join(MC_DATA, "tags/block/mineable/shovel.json"), [ASH])
    add_tag(os.path.join(MC_DATA, "tags/block/mineable/pickaxe.json"), [BRIMSTONE])
    rec = os.path.join(DATA, "recipe")
    write_json(os.path.join(rec, "gunpowder_from_brimstone.json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "%s:brimstone" % NS}, {"item": "%s:brimstone" % NS}, [{"item": "minecraft:coal"}, {"item": "minecraft:charcoal"}]],
        "result": {"id": "minecraft:gunpowder", "count": 3}})
    write_json(os.path.join(rec, "brimstone_block.json"), {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["BB", "BB"],
        "key": {"B": {"item": "%s:brimstone" % NS}}, "result": {"id": BRIMSTONE, "count": 1}})
    # criaturas
    tex_dir = os.path.join(ASSETS, "textures/entity/hell")
    os.makedirs(tex_dir, exist_ok=True)
    for name, imp in (("demon", False), ("imp", True)):
        im, eyes = demon_texture(imp)
        im.save(os.path.join(tex_dir, name + ".png"))
        eyes.save(os.path.join(tex_dir, name + "_eyes.png"))
    soul_texture().save(os.path.join(tex_dir, "lost_soul.png"))
    for name in LOOT:
        write_json(os.path.join(DATA, "loot_table/entities/%s.json" % name), entity_loot(name))
        write_json(os.path.join(ASSETS, "models/item/%s_spawn_egg.json" % name), {"parent": "minecraft:item/template_spawn_egg"})
    # sonidos (de vanilla, más graves o agudos)
    path = os.path.join(ASSETS, "sounds.json")
    with open(path, encoding="utf-8") as f:
        sounds = json.load(f, object_pairs_hook=collections.OrderedDict)
    for key, files in SOUNDS.items():
        sounds[key] = {"subtitle": "subtitles.%s.%s" % (NS, key),
                       "sounds": [{"name": name, "pitch": pitch, "volume": 1.0} for name, pitch in files]}
    write_json(path, sounds)
    write_json(os.path.join(DATA, "damage_type/soul_touch.json"), {"exhaustion": 0.1, "message_id": NS + ".soul_touch", "scaling": "when_caused_by_living_non_player"})
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        sheet = Image.new("RGBA", (64 * 3 * 4, 64 * 4), (70, 74, 80, 255))
        for k, name in enumerate(("demon", "imp", "lost_soul")):
            im = Image.open(os.path.join(tex_dir, name + ".png")).resize((256, 256), Image.NEAREST)
            sheet.alpha_composite(im, (k * 256, 0))
        sheet.save(os.path.join(PREVIEW, "hell_textures.png"))
    print("Nether bíblico: dimensión, terreno, biomas, bloques, criaturas, sonidos y textos")


if __name__ == "__main__":
    main()
