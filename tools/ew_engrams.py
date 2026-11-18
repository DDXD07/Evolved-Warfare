#!/usr/bin/env python3
"""
Fase 113: los engramas (como en ARK). Escribe la lista de engramas que lee el juego
(data/evolvedwarfare/engrams/engrams.json) y sus nombres en todos los idiomas.

Cada engrama desbloquea un grupo de recetas: las que dan alguno de sus objetos ("items"), las de los objetos cuyo id
encaja con alguno de sus patrones ("patterns", expresiones regulares sobre "espacio:nombre") o de sus etiquetas
("tags"). El juego reparte cada receta en el PRIMER engrama que encaja (por eso los concretos van antes que los
generales) y lo que no encaja con ninguno cae en los "fallback" del final (por el tipo de objeto). Lo de "free" no
necesita engrama. "perk" convierte una ventaja de las de antes (forja, mando, asedio) en este engrama.

    python3 tools/ew_engrams.py           # escribe el JSON y los textos
    python3 tools/ew_engrams.py --check   # además, las cuentas (puntos totales por nivel)
"""
import collections
import glob
import json
import os
import sys

NS = "evolvedwarfare"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
OUT = os.path.join(ROOT, "src/main/resources/data", NS, "engrams/engrams.json")

WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped"]
COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
          "blue", "brown", "green", "red", "black"]
W = "(%s)" % "|".join(WOODS)
C = "(%s)" % "|".join(COLORS)


def mc(*names):
    return ["minecraft:" + n for n in names]


def ew(*names):
    return [NS + ":" + n for n in names]


# ---------------------------------------------------------------------------------------------- libres
FREE = {
    "items": mc("crafting_table", "furnace", "stick", "torch", "iron_ingot", "gold_ingot", "copper_ingot", "netherite_ingot",
                "iron_nugget", "gold_nugget", "diamond", "emerald", "redstone", "lapis_lazuli", "coal", "bone_meal", "wheat",
                "raw_iron", "raw_gold", "raw_copper", "slime_ball", "dried_kelp", "melon_slice", "honey_bottle", "quartz",
                "amethyst_shard", "netherite_scrap", "glass", "charcoal", "sugar", "string", "leather", "white_wool", "gunpowder")
    + ew("guide_book", "charred_planks", "cabbage_seeds", "corn_seeds", "tomato_seeds"),
    "tags": ["minecraft:planks"],
    "patterns": [r"^%s:hot_.*_ingot$" % NS],
}

E = []


def engram(id, cat, level, cost, es, en, icon=None, items=(), patterns=(), tags=(), requires=(), perk=None, rank=1,
           fallback=None, namespace=None, desc_es=None, desc_en=None):
    e = collections.OrderedDict()
    e["id"] = id
    e["category"] = cat
    e["level"] = level
    e["cost"] = cost
    if icon:
        e["icon"] = icon
    if items:
        e["items"] = list(items)
    if patterns:
        e["patterns"] = list(patterns)
    if tags:
        e["tags"] = list(tags)
    if requires:
        e["requires"] = list(requires)
    if perk:
        e["perk"] = perk
        e["perkRank"] = rank
    if fallback:
        e["fallback"] = fallback
    if namespace:
        e["namespace"] = namespace
    e["_es"] = es
    e["_en"] = en
    e["_des"] = desc_es
    e["_den"] = desc_en
    E.append(e)


# ---------------------------------------------------------------------------------------------- herramientas
engram("wood_tools", "tools", 1, 2, "Herramientas de madera", "Wooden tools", "minecraft:wooden_pickaxe",
       items=mc("wooden_pickaxe", "wooden_axe", "wooden_shovel", "wooden_hoe", "wooden_sword"))
engram("flint_tools", "tools", 2, 2, "Herramientas de sílex", "Flint tools", NS + ":flint_hatchet",
       items=ew("flint_hatchet", "flint_knife"))
engram("stone_tools", "tools", 3, 3, "Herramientas de piedra", "Stone tools", "minecraft:stone_pickaxe",
       items=mc("stone_pickaxe", "stone_axe", "stone_shovel", "stone_hoe", "stone_sword"), requires=["wood_tools"])
engram("copper_knife", "tools", 6, 2, "Cuchillo de cobre", "Copper knife", NS + ":copper_knife", items=ew("copper_knife"))
engram("shears_flint", "tools", 7, 3, "Tijeras y mechero", "Shears and flint and steel", "minecraft:shears",
       items=mc("shears", "flint_and_steel"))
engram("fishing", "tools", 5, 3, "Pesca", "Fishing", "minecraft:fishing_rod",
       items=mc("fishing_rod") + ew("bait"))
engram("bucket", "tools", 10, 4, "Cubo", "Bucket", "minecraft:bucket", items=mc("bucket"))
engram("canteen", "tools", 2, 2, "Cantimplora y botellas", "Canteen and bottles", NS + ":canteen",
       items=ew("canteen") + mc("glass_bottle"))
engram("iron_tools", "tools", 14, 8, "Herramientas de hierro", "Iron tools", "minecraft:iron_pickaxe",
       items=mc("iron_pickaxe", "iron_axe", "iron_shovel", "iron_hoe", "iron_sword") + ew("iron_knife"),
       requires=["stone_tools"])
engram("steel_tools", "tools", 30, 8, "Herramientas de acero", "Steel tools", NS + ":steel_pickaxe",
       patterns=[r"^%s:steel_(pickaxe|axe|shovel|hoe|sword)$" % NS], requires=["iron_tools"])
engram("gold_tools", "tools", 18, 4, "Herramientas de oro", "Golden tools", "minecraft:golden_pickaxe",
       items=mc("golden_pickaxe", "golden_axe", "golden_shovel", "golden_hoe", "golden_sword"), requires=["stone_tools"])
engram("diamond_tools", "tools", 42, 15, "Herramientas de diamante", "Diamond tools", "minecraft:diamond_pickaxe",
       items=mc("diamond_pickaxe", "diamond_axe", "diamond_shovel", "diamond_hoe", "diamond_sword"), requires=["iron_tools"])
engram("netherite_tools", "tools", 70, 20, "Herramientas de netherita", "Netherite tools", "minecraft:netherite_pickaxe",
       items=mc("netherite_pickaxe", "netherite_axe", "netherite_shovel", "netherite_hoe", "netherite_sword"),
       requires=["diamond_tools"])
engram("obsidian_knife", "tools", 40, 5, "Cuchillo de obsidiana", "Obsidian knife", NS + ":obsidian_knife",
       items=ew("obsidian_knife"))
engram("navigation", "tools", 18, 6, "Brújula, reloj y mapas", "Compass, clock and maps", "minecraft:compass",
       items=mc("compass", "clock", "map", "recovery_compass") + ew("atlas"))
engram("spyglass", "tools", 24, 4, "Catalejo", "Spyglass", "minecraft:spyglass", items=mc("spyglass"))
engram("builder_tools", "tools", 18, 6, "Herramientas de obra", "Builder's tools", NS + ":building_hammer",
       items=ew("building_hammer", "prospector_pick", "structure_scanner", "smithing_hammer", "sharpener"))
engram("brush_lead", "tools", 12, 3, "Rienda y pincel", "Lead and brush", "minecraft:lead",
       items=mc("lead", "brush", "carrot_on_a_stick", "warped_fungus_on_a_stick"))
engram("magic_bait", "tools", 30, 5, "Cebo mágico", "Magic bait", NS + ":magic_bait", items=ew("magic_bait"))

# ---------------------------------------------------------------------------------------------- armas
engram("bow", "weapons", 6, 4, "Arco y flechas", "Bow and arrows", "minecraft:bow", items=mc("bow", "arrow"))
engram("javelin", "weapons", 10, 4, "Jabalinas", "Javelins", NS + ":javelin", perk="JAVELIN_SMITH")
engram("dagger", "weapons", 12, 5, "Dagas", "Daggers", NS + ":iron_dagger", perk="DAGGER_SMITH")
engram("shield", "weapons", 12, 4, "Escudo", "Shield", "minecraft:shield", items=mc("shield"))
engram("spear", "weapons", 14, 5, "Lanzas", "Spears", NS + ":iron_spear", perk="SPEAR_SMITH")
engram("crossbow", "weapons", 20, 6, "Ballesta", "Crossbow", "minecraft:crossbow", items=mc("crossbow"), requires=["redstone_basic"])
engram("longbow", "weapons", 20, 6, "Arco largo", "Longbow", NS + ":longbow", perk="LONGBOW_SMITH", requires=["bow"])
engram("longsword", "weapons", 22, 7, "Espadas largas", "Longswords", NS + ":iron_longsword", perk="LONGSWORD_SMITH",
       requires=["dagger"])
engram("mace", "weapons", 24, 7, "Martillos y manguales", "Warhammers and flails", NS + ":iron_warhammer",
       perk="MACE_SMITH")
engram("battle_axe", "weapons", 26, 7, "Hachas de batalla", "Battle axes", NS + ":iron_battle_axe",
       perk="BATTLE_AXE_SMITH")
engram("pavise", "weapons", 31, 6, "Pavés", "Pavise", NS + ":pavise", items=ew("pavise"), requires=["reinforced_shield"])
engram("arbalest", "weapons", 28, 7, "Arbalesta", "Arbalest", NS + ":arbalest", items=ew("arbalest"),
       requires=["crossbow"])
engram("halberd", "weapons", 30, 8, "Alabardas", "Halberds", NS + ":iron_halberd", perk="HALBERD_SMITH",
       requires=["spear"])
engram("reinforced_shield", "weapons", 30, 7, "Escudo reforzado", "Reinforced shield", NS + ":reinforced_shield",
       items=ew("reinforced_shield"), requires=["shield"])
engram("musket", "weapons", 32, 8, "Pólvora y balas", "Powder and musket balls", NS + ":musket_ball",
       items=ew("musket_ball", "musket"))
engram("repeating_crossbow", "weapons", 34, 9, "Ballesta de repetición", "Repeating crossbow",
       NS + ":repeating_crossbow", items=ew("repeating_crossbow"), requires=["arbalest"])
engram("special_arrows", "weapons", 35, 5, "Flechas especiales", "Special arrows", "minecraft:spectral_arrow",
       items=mc("spectral_arrow", "tipped_arrow"), requires=["bow"])
engram("ammo_pouch", "weapons", 18, 4, "Bolsa de munición", "Ammo pouch", NS + ":ammo_pouch", items=ew("ammo_pouch"))
engram("glory_pouch", "weapons", 30, 5, "Bolsa de gloria", "Glory pouch", NS + ":glory_pouch", items=ew("glory_pouch"))
engram("mace_vanilla", "weapons", 45, 10, "Maza de viento", "Mace", "minecraft:mace", items=mc("mace", "wind_charge"))
engram("tnt", "weapons", 30, 8, "TNT", "TNT", "minecraft:tnt", items=mc("tnt", "tnt_minecart"))

# ---------------------------------------------------------------------------------------------- armadura
engram("linen", "armor", 4, 2, "Ropa de lino", "Linen clothes", NS + ":linen_tunic",
       items=ew("linen_tunic", "linen_trousers", "sandals", "straw_hat"))
engram("leather_armor", "armor", 5, 4, "Armadura de cuero", "Leather armor", "minecraft:leather_chestplate",
       items=mc("leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots", "leather_horse_armor"))
engram("fur", "armor", 8, 4, "Ropa de pieles", "Fur clothes", NS + ":fur_coat",
       items=ew("fur_hood", "fur_coat", "fur_trousers", "fur_boots"))
engram("faction_light", "armor", 10, 6, "Armaduras de forajido y vikingo", "Raider armors", NS + ":bandit_outlaw_chestplate",
       perk="FACTION_TAILOR")
engram("class_leather", "armor", 8, 5, "Armaduras de clase de cuero", "Leather class armors",
       NS + ":warrior_leather_chestplate", patterns=[r"^%s:(archer|musketeer|thief|warrior)_leather_.*" % NS],
       requires=["leather_armor"])
engram("class_copper", "armor", 12, 6, "Armaduras de clase de cobre", "Copper class armors",
       NS + ":warrior_copper_chestplate", patterns=[r"^%s:(archer|musketeer|thief|warrior)_copper_.*" % NS],
       requires=["class_leather"])
engram("chainmail", "armor", 14, 6, "Cota de malla", "Chainmail", "minecraft:chainmail_chestplate",
       items=mc("chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots") + ew("iron_rings"),
       requires=["leather_armor"])
engram("iron_armor", "armor", 18, 10, "Armadura de hierro", "Iron armor", "minecraft:iron_chestplate",
       items=mc("iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "iron_horse_armor"),
       requires=["leather_armor"])
engram("gold_armor", "armor", 20, 5, "Armadura de oro", "Golden armor", "minecraft:golden_chestplate",
       items=mc("golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots", "golden_horse_armor"),
       requires=["leather_armor"])
engram("class_iron", "armor", 20, 7, "Armaduras de clase de hierro", "Iron class armors", NS + ":warrior_iron_chestplate",
       patterns=[r"^%s:(archer|musketeer|thief|warrior)_iron_.*" % NS], requires=["class_copper"])
engram("iron_plate", "armor", 24, 8, "Armadura de placas de hierro", "Iron plate armor", NS + ":iron_plate_chestplate",
       patterns=[r"^%s:iron_plate_.*" % NS], requires=["iron_armor"])
engram("faction_medium", "armor", 28, 8, "Armaduras de bergante, huscarle y corsario", "Veteran raider armors",
       NS + ":viking_huscarl_chestplate", perk="FACTION_ARMORER", requires=["faction_light"])
engram("steel_plate", "armor", 34, 10, "Armadura de placas de acero", "Steel plate armor", NS + ":steel_plate_chestplate",
       patterns=[r"^%s:steel_plate_.*" % NS], requires=["iron_plate"])
engram("diamond_armor", "armor", 48, 18, "Armadura de diamante", "Diamond armor", "minecraft:diamond_chestplate",
       items=mc("diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots", "diamond_horse_armor"),
       requires=["iron_armor"])
engram("class_diamond", "armor", 50, 12, "Armaduras de clase de diamante", "Diamond class armors",
       NS + ":warrior_diamond_chestplate", patterns=[r"^%s:(archer|musketeer|thief|warrior)_diamond_.*" % NS],
       requires=["class_iron"])
engram("faction_heavy", "armor", 55, 12, "Armaduras de caudillo, jarl y capitán", "Warlord armors",
       NS + ":bandit_warlord_chestplate", perk="WARLORD_FORGE", requires=["faction_medium"])
engram("turtle", "armor", 45, 6, "Caparazón de tortuga", "Turtle shell", "minecraft:turtle_helmet",
       items=mc("turtle_helmet", "wolf_armor"))
engram("netherite_armor", "armor", 75, 25, "Armadura de netherita", "Netherite armor", "minecraft:netherite_chestplate",
       items=mc("netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots"),
       requires=["diamond_armor"])
engram("class_netherite", "armor", 76, 15, "Armaduras de clase de netherita", "Netherite class armors",
       NS + ":warrior_netherite_chestplate", patterns=[r"^%s:(archer|musketeer|thief|warrior)_netherite_.*" % NS],
       requires=["class_diamond"])

# ---------------------------------------------------------------------------------------------- legendario
engram("dragon_scales", "legendary", 80, 30, "Armaduras de escamas", "Scale armors", NS + ":fire_dragon_scale_chestplate",
       patterns=[r"^%s:(fire_dragon|ice_dragon|sea_serpent)_scale_.*" % NS], requires=["diamond_armor"])
engram("dragonbone", "legendary", 82, 20, "Espadas de hueso de dragón", "Dragonbone swords", NS + ":dragonbone_sword",
       items=ew("dragonbone_sword", "flamed_dragonbone_sword", "iced_dragonbone_sword"), requires=["dragon_scales"])

# ---------------------------------------------------------------------------------------------- medicina
engram("bandage", "medicine", 1, 2, "Vendas", "Bandages", NS + ":bandage", items=ew("bandage"))
engram("splint", "medicine", 8, 3, "Férula", "Splint", NS + ":splint", items=ew("splint"), requires=["bandage"])
engram("alcohol", "medicine", 10, 3, "Alcohol y fermentos", "Alcohol and mash", NS + ":alcohol",
       items=ew("alcohol", "fermented_mash"))
engram("medicine", "medicine", 18, 5, "Medicina", "Medicine", NS + ":medicine", items=ew("medicine"),
       requires=["alcohol"])
engram("antidote", "medicine", 22, 5, "Antídoto", "Antidote", NS + ":antidote", items=ew("antidote"),
       requires=["medicine"])
engram("medkit", "medicine", 30, 8, "Botiquín", "Medkit", NS + ":medkit", items=ew("medkit", "medic_kit"),
       requires=["splint", "medicine"])
engram("golden_food", "medicine", 28, 8, "Manzana y zanahoria de oro", "Golden apple and carrot",
       "minecraft:golden_apple", items=mc("golden_apple", "golden_carrot", "glistering_melon_slice"))

# ---------------------------------------------------------------------------------------------- comida
engram("cooking", "food", 3, 2, "Cocina básica", "Basic cooking", "minecraft:bread",
       items=mc("bread", "mushroom_stew", "beetroot_soup", "bowl") + ew("vegetable_stew"))
engram("campfire", "food", 3, 2, "Fogata", "Campfire", "minecraft:campfire", items=mc("campfire"))
engram("drinks", "food", 8, 3, "Zumos y bebidas", "Juices and drinks", NS + ":apple_juice",
       items=ew("apple_juice", "berry_juice", "melon_juice", "orange_juice", "lemonade"))
engram("preserving", "food", 10, 4, "Sal y conservas", "Salt and preserving", NS + ":salt",
       items=ew("salt", "fine_salt", "icebox"))
engram("baking", "food", 12, 4, "Repostería", "Baking", "minecraft:cake",
       items=mc("cake", "cookie", "pumpkin_pie", "rabbit_stew", "suspicious_stew", "honey_block", "hay_block"))
engram("smoker", "food", 12, 4, "Ahumador", "Smoker", "minecraft:smoker", items=mc("smoker"))

# ---------------------------------------------------------------------------------------------- construcción
engram("wood_building", "building", 2, 2, "Construcción de madera", "Wooden building", "minecraft:oak_stairs",
       items=mc("ladder", "scaffolding"),
       patterns=[r"^minecraft:%s_(stairs|slab|fence|fence_gate|door|trapdoor|button|pressure_plate|wood|hyphae|log)$" % W,
                 r"^minecraft:stripped_%s_.*" % W, r"^minecraft:bamboo_(block|mosaic|mosaic_stairs|mosaic_slab)$"])
engram("signs", "building", 6, 2, "Carteles", "Signs", "minecraft:oak_sign",
       patterns=[r"^minecraft:%s_(sign|hanging_sign)$" % W])
engram("chest", "utility", 2, 2, "Cofre", "Chest", "minecraft:chest", items=mc("chest"))
engram("bed", "utility", 5, 3, "Cama", "Bed", "minecraft:red_bed", patterns=[r"^minecraft:%s_bed$" % C])
engram("stone_building", "building", 4, 3, "Piedra labrada", "Cut stone", "minecraft:stone_bricks",
       patterns=[r"^minecraft:(cobblestone|mossy_cobblestone|stone|smooth_stone|stone_brick|mossy_stone_brick|andesite|"
                 r"diorite|granite|polished_andesite|polished_diorite|polished_granite|cobbled_deepslate|"
                 r"polished_deepslate|deepslate_brick|deepslate_tile|tuff|polished_tuff|tuff_brick)_(stairs|slab|wall)$",
                 r"^minecraft:(stone_bricks|mossy_stone_bricks|chiseled_stone_bricks|cracked_stone_bricks|"
                 r"polished_(andesite|diorite|granite|deepslate|tuff)|deepslate_(bricks|tiles)|chiseled_deepslate|"
                 r"chiseled_tuff|tuff_bricks|chiseled_tuff_bricks|mossy_cobblestone|stone_button|stone_pressure_plate|"
                 r"smooth_stone)$"], requires=["stone_tools"])
engram("sandstone", "building", 6, 2, "Arenisca", "Sandstone", "minecraft:sandstone",
       patterns=[r"^minecraft:.*sandstone.*"])
engram("colors", "building", 5, 3, "Tintes y lana", "Dyes and wool", "minecraft:red_wool",
       patterns=[r"^minecraft:%s_(dye|wool|carpet)$" % C])
engram("bricks", "building", 8, 3, "Ladrillos", "Bricks", "minecraft:bricks",
       items=mc("bricks", "flower_pot", "decorated_pot", "packed_mud"),
       patterns=[r"^minecraft:(brick|mud_brick)_(stairs|slab|wall)$", r"^minecraft:mud_bricks$"])
engram("glass", "building", 10, 3, "Cristalería", "Glasswork", "minecraft:glass_pane",
       items=mc("glass_pane", "tinted_glass"),
       patterns=[r"^minecraft:%s_stained_glass(_pane)?$" % C])
engram("lighting", "building", 10, 3, "Faroles y velas", "Lanterns and candles", "minecraft:lantern",
       items=mc("lantern", "candle", "jack_o_lantern"), patterns=[r"^minecraft:%s_candle$" % C])
engram("banners", "building", 12, 3, "Estandartes", "Banners", "minecraft:white_banner",
       patterns=[r"^minecraft:%s_banner$" % C, r"^minecraft:.*_banner_pattern$"], requires=["colors"])
engram("concrete", "building", 14, 4, "Hormigón y terracota", "Concrete and terracotta", "minecraft:white_concrete",
       patterns=[r"^minecraft:%s_(concrete_powder|terracotta|glazed_terracotta)$" % C])
engram("storage_blocks", "building", 6, 2, "Bloques de materiales", "Material blocks", "minecraft:iron_block",
       items=mc("iron_block", "gold_block", "diamond_block", "emerald_block", "redstone_block", "lapis_block",
                "coal_block", "copper_block", "netherite_block", "raw_iron_block", "raw_gold_block", "raw_copper_block",
                "bone_block", "slime_block", "dried_kelp_block", "quartz_block", "amethyst_block", "packed_ice",
                "blue_ice", "snow_block", "clay", "melon", "chain", "iron_bars", "iron_door", "iron_trapdoor",
                "heavy_weighted_pressure_plate", "light_weighted_pressure_plate"))
engram("copper_building", "building", 18, 4, "Construcción de cobre", "Copper building", "minecraft:cut_copper",
       patterns=[r"^minecraft:.*copper.*"])
engram("nether_building", "building", 35, 6, "Construcción del Nether", "Nether building", "minecraft:nether_bricks",
       items=mc("magma_block"), patterns=[r"^minecraft:.*(nether_brick|blackstone|quartz|basalt|nether_wart_block).*"])
engram("prismarine", "building", 45, 6, "Prismarina", "Prismarine", "minecraft:prismarine_bricks",
       patterns=[r"^minecraft:.*prismarine.*", r"^minecraft:sea_lantern$"])
engram("end_building", "building", 70, 6, "Construcción del End", "End building", "minecraft:purpur_block",
       patterns=[r"^minecraft:.*(purpur|end_stone|end_rod).*"])

# ---------------------------------------------------------------------------------------------- utilidad
engram("crafting_stations", "utility", 8, 4, "Mesas de oficio", "Workstations", "minecraft:loom",
       items=mc("loom", "fletching_table", "cartography_table", "composter"))
engram("barrel", "utility", 8, 2, "Barril", "Barrel", "minecraft:barrel", items=mc("barrel"))
engram("paper", "utility", 6, 2, "Papel y libros", "Paper and books", "minecraft:book",
       items=mc("paper", "book", "writable_book", "item_frame", "painting", "armor_stand", "glow_item_frame"))
engram("stonecutter", "utility", 14, 4, "Cortapiedras y afiladora", "Stonecutter and grindstone", "minecraft:stonecutter",
       items=mc("stonecutter", "grindstone"))
engram("cauldron", "utility", 14, 3, "Caldero", "Cauldron", "minecraft:cauldron", items=mc("cauldron"))
engram("lectern", "utility", 30, 4, "Atril y estantes", "Lectern and shelves", "minecraft:lectern",
       items=mc("lectern", "chiseled_bookshelf"), requires=["bookshelf"])
engram("blast_furnace", "utility", 20, 5, "Alto horno", "Blast furnace", "minecraft:blast_furnace",
       items=mc("blast_furnace"))
engram("jukebox", "utility", 24, 3, "Tocadiscos", "Jukebox", "minecraft:jukebox", items=mc("jukebox"))
engram("smithing_table", "utility", 28, 6, "Mesa de herrería", "Smithing table", "minecraft:smithing_table",
       items=mc("smithing_table"), patterns=[r"^minecraft:.*_smithing_template$"])
engram("anvil", "utility", 32, 10, "Yunque", "Anvil", "minecraft:anvil", items=mc("anvil"))
engram("backpack_basic", "utility", 6, 3, "Mochila", "Backpack", NS + ":backpack_basic", items=ew("backpack_basic"))
engram("backpack_traveler", "utility", 16, 5, "Mochila de viajero", "Traveler backpack", NS + ":backpack_traveler",
       items=ew("backpack_traveler"), requires=["backpack_basic"])
engram("backpack_explorer", "utility", 26, 6, "Mochila de explorador", "Explorer backpack", NS + ":backpack_explorer",
       items=ew("backpack_explorer"), requires=["backpack_traveler"])
engram("backpack_adventurer", "utility", 40, 8, "Mochila de aventurero", "Adventurer backpack",
       NS + ":backpack_adventurer", items=ew("backpack_adventurer"), requires=["backpack_explorer"])
engram("backpack_abyssal", "utility", 60, 12, "Mochila abisal", "Abyssal backpack", NS + ":backpack_abyssal",
       items=ew("backpack_abyssal"), requires=["backpack_adventurer"])
engram("ender_chest", "utility", 60, 12, "Cofre del End", "Ender chest", "minecraft:ender_chest",
       items=mc("ender_chest"))
engram("shulker_box", "utility", 65, 12, "Cajas de shulker", "Shulker boxes", "minecraft:shulker_box",
       patterns=[r"^minecraft:(%s_)?shulker_box$" % C])
engram("lodestone", "utility", 40, 5, "Magnetita", "Lodestone", "minecraft:lodestone", items=mc("lodestone"))
engram("respawn_anchor", "utility", 60, 8, "Nexo de reaparición", "Respawn anchor", "minecraft:respawn_anchor",
       items=mc("respawn_anchor"))
engram("beehive", "utility", 16, 3, "Colmena", "Beehive", "minecraft:beehive", items=mc("beehive"))

# ---------------------------------------------------------------------------------------------- redstone
engram("redstone_basic", "redstone", 20, 6, "Redstone básica", "Basic redstone", "minecraft:repeater",
       items=mc("redstone_torch", "lever", "repeater", "tripwire_hook", "trapped_chest", "redstone_lamp", "note_block", "target",
                "daylight_detector"))
engram("hopper", "redstone", 22, 5, "Tolva", "Hopper", "minecraft:hopper", items=mc("hopper", "hopper_minecart"),
       requires=["redstone_basic"])
engram("dispensers", "redstone", 24, 5, "Dispensador y soltador", "Dispenser and dropper", "minecraft:dispenser",
       items=mc("dispenser", "dropper"), requires=["redstone_basic"])
engram("pistons", "redstone", 26, 8, "Pistones", "Pistons", "minecraft:piston", items=mc("piston", "sticky_piston"),
       requires=["redstone_basic"])
engram("comparator", "redstone", 28, 6, "Comparador y observador", "Comparator and observer", "minecraft:comparator",
       items=mc("comparator", "observer", "crafter", "calibrated_sculk_sensor"), requires=["redstone_basic"])

# ---------------------------------------------------------------------------------------------- transporte
engram("boats", "transport", 6, 3, "Barcas", "Boats", "minecraft:oak_boat",
       patterns=[r"^minecraft:.*_(boat|raft)$"])
engram("carts", "transport", 14, 5, "Carros", "Carts", NS + ":cart", items=ew("cart", "cart_wheel", "cart_bed"))
engram("minecart", "transport", 22, 6, "Vagonetas y raíles", "Minecarts and rails", "minecraft:minecart",
       items=mc("minecart", "chest_minecart", "furnace_minecart", "rail"))
engram("powered_rails", "transport", 30, 6, "Raíles propulsores", "Powered rails", "minecraft:powered_rail",
       items=mc("powered_rail", "detector_rail", "activator_rail"), requires=["minecart", "redstone_basic"])
engram("railway", "transport", 50, 15, "Ferrocarril", "Railway", NS + ":train_cab",
       patterns=[r"^%s:(track_.*|train_.*)$" % NS], requires=["powered_rails"])

# ---------------------------------------------------------------------------------------------- magia
engram("bookshelf", "magic", 30, 5, "Estanterías", "Bookshelves", "minecraft:bookshelf", items=mc("bookshelf"),
       requires=["paper"])
engram("brewing", "magic", 30, 10, "Destilería", "Brewing", "minecraft:brewing_stand",
       items=mc("brewing_stand", "fermented_spider_eye", "magma_cream", "blaze_powder", "fire_charge"))
engram("enchanting", "magic", 35, 15, "Mesa de encantamientos", "Enchanting table", "minecraft:enchanting_table",
       items=mc("enchanting_table"), requires=["bookshelf"])
engram("ender_eye", "magic", 50, 8, "Ojo de Ender", "Eye of ender", "minecraft:ender_eye", items=mc("ender_eye"))
engram("blessed_salt", "magic", 45, 6, "Sal bendita y círculo ritual", "Blessed salt and ritual circle",
       NS + ":blessed_salt", items=ew("blessed_salt", "ritual_circle"))
engram("conduit", "magic", 70, 10, "Canalizador", "Conduit", "minecraft:conduit", items=mc("conduit"))
engram("end_crystal", "magic", 75, 10, "Cristal del End", "End crystal", "minecraft:end_crystal",
       items=mc("end_crystal"))
engram("beacon", "magic", 85, 30, "Faro", "Beacon", "minecraft:beacon", items=mc("beacon"))

# ---------------------------------------------------------------------------------------------- ingeniería (fase 17...)
engram("mechanics", "engineering", 16, 6, "Mecánica básica", "Basic mechanics", NS + ":gearbox",
       items=ew("shaft", "small_gear", "large_gear", "gearbox", "hand_crank"))
engram("wind_water", "engineering", 22, 8, "Molinos y norias", "Windmills and water wheels", NS + ":windmill_medium",
       patterns=[r"^%s:(water_wheel|windmill)_.*" % NS], requires=["mechanics"])
engram("transmission", "engineering", 26, 8, "Transmisión", "Transmission", NS + ":clutch",
       items=ew("clutch", "gearshift", "pulley", "redstone_cam", "iron_shaft", "iron_small_gear", "iron_large_gear",
                "steel_shaft", "steel_small_gear", "steel_large_gear", "tower_clock"), requires=["mechanics"])
engram("workshops", "engineering", 28, 10, "Talleres", "Workshops", NS + ":millstone",
       items=ew("millstone", "sawmill", "bellows", "crucible", "trip_hammer", "architect_table"),
       requires=["mechanics"])
engram("gauges", "engineering", 30, 4, "Medidores", "Gauges", NS + ":stress_gauge", items=ew("stress_gauge", "speed_gauge"),
       requires=["transmission"])
engram("lifting", "engineering", 35, 8, "Grúas y elevadores", "Cranes and lifts", NS + ":crane",
       items=ew("crane", "lift_platform", "screw", "drill_head"), requires=["transmission"])
engram("steam", "engineering", 45, 12, "Vapor y tuberías", "Steam and pipes", NS + ":steam_boiler",
       items=ew("steam_boiler", "pipe", "hand_pump", "faucet"), requires=["workshops"])

# ---------------------------------------------------------------------------------------------- pueblos
engram("town", "settlement", 10, 5, "Fundar un pueblo", "Found a town", NS + ":town_hall",
       items=ew("town_hall", "builder_post", "lumberjack_post", "farmer_post", "miner_post", "digger_post",
                "gardener_post", "warehouse"))
engram("trades", "settlement", 20, 6, "Oficios", "Trades", NS + ":blacksmith_post",
       patterns=[r"^%s:.*_post$" % NS], requires=["town"])
engram("commerce", "settlement", 18, 5, "Comercio", "Commerce", NS + ":sales_mailbox",
       items=ew("sales_mailbox", "trader_catalog", "route_slip", "coal_bunker", "crop_crate", "research_desk"))
engram("command", "settlement", 15, 4, "Mando", "Command", NS + ":command_staff",
       items=ew("command_staff", "recruitment_contract", "white_flag", "ambassador_seal"))

# ---------------------------------------------------------------------------------------------- militar (las ventajas de antes)
engram("drill", "military", 12, 4, "Instrucción", "Drill", NS + ":command_staff", perk="DRILL")
engram("field_medicine", "military", 22, 5, "Medicina de campaña", "Field medicine", NS + ":medic_kit",
       perk="FIELD_MEDICINE", requires=["drill"])
engram("tactics", "military", 24, 5, "Tácticas", "Tactics", "minecraft:map", perk="TACTICS", requires=["drill"])
engram("quartermaster", "military", 28, 5, "Intendencia", "Quartermaster", "minecraft:chest", perk="QUARTERMASTER",
       requires=["drill"])
engram("inspire", "military", 35, 6, "Arenga", "Inspire", "minecraft:goat_horn", perk="INSPIRE", requires=["tactics"])
engram("veterans", "military", 45, 8, "Veteranos", "Veterans", "minecraft:iron_sword", perk="VETERANS",
       requires=["field_medicine"])
engram("banner", "military", 60, 10, "Estandarte de guerra", "War banner", "minecraft:red_banner", perk="BANNER",
       requires=["inspire", "veterans"])
engram("battering_ram", "military", 20, 6, "Ariete", "Battering ram", NS + ":battering_ram", perk="RAM_SMITH")
engram("fortification", "military", 30, 7, "Fortificaciones", "Fortifications", NS + ":portcullis",
       perk="FORTIFICATION", items=ew("portcullis", "portcullis_winch"))
engram("gunner", "military", 32, 6, "Artillería", "Gunnery", NS + ":catapult", perk="GUNNER", rank=3,
       requires=["battering_ram"])
engram("torsion", "military", 35, 9, "Catapulta y balista", "Catapult and ballista", NS + ":ballista", perk="TORSION",
       requires=["battering_ram"])
engram("thrift", "military", 36, 5, "Ahorro de munición", "Thrift", NS + ":ballista_bolt", perk="THRIFT",
       requires=["gunner"])
engram("incendiaries", "military", 40, 7, "Incendiarios", "Incendiaries", NS + ":fire_pot", perk="INCENDIARIES",
       requires=["torsion"])
engram("sapper", "military", 42, 6, "Zapadores", "Sappers", "minecraft:tnt", perk="SAPPER", requires=["gunner"])
engram("trebuchet", "military", 55, 12, "Trabuquete", "Trebuchet", NS + ":trebuchet", perk="COUNTERWEIGHT",
       requires=["torsion"])

# ---------------------------------------------------------------------------------------------- lo que no encaja
engram("misc_armor", "armor", 30, 8, "Otras armaduras", "Other armor", "minecraft:chainmail_chestplate",
       fallback="armor")
engram("misc_tools", "tools", 25, 6, "Otras herramientas y armas", "Other tools and weapons", "minecraft:iron_pickaxe",
       fallback="tool")
engram("misc_food", "food", 8, 3, "Otras comidas", "Other food", "minecraft:pumpkin_pie", fallback="food")
engram("misc_blocks_mc", "building", 10, 5, "Otros bloques", "Other blocks", "minecraft:bricks", fallback="block",
       namespace="minecraft")
engram("misc_blocks_ew", "utility", 15, 6, "Otros bloques del mod", "Other mod blocks", NS + ":crop_crate",
       fallback="block", namespace=NS)
engram("misc_items_mc", "utility", 12, 5, "Otros objetos", "Other items", "minecraft:item_frame", fallback="item",
       namespace="minecraft")
engram("misc_items_ew", "utility", 15, 6, "Otros objetos del mod", "Other mod items", NS + ":guide_book",
       fallback="item", namespace=NS)

CATEGORIES = collections.OrderedDict([
    ("tools", ("Herramientas", "Tools")),
    ("weapons", ("Armas", "Weapons")),
    ("armor", ("Armadura", "Armor")),
    ("medicine", ("Medicina", "Medicine")),
    ("food", ("Comida", "Food")),
    ("building", ("Construcción", "Building")),
    ("utility", ("Utilidad", "Utility")),
    ("redstone", ("Redstone", "Redstone")),
    ("transport", ("Transporte", "Transport")),
    ("magic", ("Magia", "Magic")),
    ("engineering", ("Ingeniería", "Engineering")),
    ("settlement", ("Pueblos", "Towns")),
    ("military", ("Militar", "Military")),
    ("legendary", ("Legendario", "Legendary")),
])

TEXTS = {
    "guide.evolvedwarfare.skills.name": ("Nivel y engramas", "Level and engrams"),
    "guide.evolvedwarfare.skills.left.title": ("Nivel de superviviente", "Survivor level"),
    "guide.evolvedwarfare.skills.left.1": ("Sube matando, minando, talando, explorando, fabricando cosas nuevas y con las habilidades.",
                                           "Earned by killing, mining, chopping, exploring, crafting new things and with skills."),
    "guide.evolvedwarfare.skills.left.2": ("Cada nivel da puntos de engrama (y de atributo). Hasta el 100; al morir no se pierde.",
                                           "Each level gives engram (and attribute) points. Up to 100; never lost on death."),
    "guide.evolvedwarfare.skills.left.3": ("Tecla K (o la Mesa de investigación): tus engramas.", "K key (or the Research desk): your engrams."),
    "guide.evolvedwarfare.skills.right.title": ("Engramas", "Engrams"),
    "guide.evolvedwarfare.skills.right.1": ("Casi todo se fabrica solo con su engrama. Libres: mesa, horno, tablones, palos y antorchas.",
                                            "Almost everything needs its engram. Free: table, furnace, planks, sticks and torches."),
    "guide.evolvedwarfare.skills.right.2": ("Sin el engrama tampoco puedes usar el equipo que encuentres (bloques y comida, sí).",
                                            "Without it you can't use gear you find either (blocks and food are fine)."),
    "key.evolvedwarfare.skills": ("Engramas", "Engrams"),
    "key.evolvedwarfare.engrams": ("Engramas", "Engrams"),
    "screen.evolvedwarfare.engrams.title": ("Engramas", "Engrams"),
    "screen.evolvedwarfare.engrams.all": ("Todos", "All"),
    "hud.evolvedwarfare.engrams.new": ("%s engramas nuevos para aprender (%s)", "%s new engrams to learn (%s)"),
    "screen.evolvedwarfare.engrams.level": ("Nivel %s", "Level %s"),
    "screen.evolvedwarfare.engrams.points": ("Puntos de engrama: %s", "Engram points: %s"),
    "screen.evolvedwarfare.engrams.cost": ("Cuesta %s puntos", "Costs %s points"),
    "screen.evolvedwarfare.engrams.needs_level": ("Necesita nivel %s", "Requires level %s"),
    "screen.evolvedwarfare.engrams.requires": ("Antes: %s", "Requires: %s"),
    "screen.evolvedwarfare.engrams.learned": ("Aprendido", "Learned"),
    "screen.evolvedwarfare.engrams.click": ("Clic para aprenderlo", "Click to learn"),
    "screen.evolvedwarfare.engrams.no_points": ("No tienes puntos suficientes", "Not enough points"),
    "screen.evolvedwarfare.engrams.unlocks": ("Desbloquea:", "Unlocks:"),
    "screen.evolvedwarfare.engrams.more": ("…y %s más", "…and %s more"),
    "screen.evolvedwarfare.engrams.perk": ("Mejora a tus soldados y máquinas", "Upgrades your soldiers and engines"),
    "screen.evolvedwarfare.engrams.skills": ("Habilidades", "Skills"),
    "screen.evolvedwarfare.engrams.count": ("%s de %s aprendidos", "%s of %s learned"),
    "screen.evolvedwarfare.engrams.search": ("Buscar…", "Search…"),
    "screen.evolvedwarfare.engrams.off": ("Los engramas están desactivados en la configuración: todo se puede fabricar.",
                                          "Engrams are off in the config: everything can be crafted."),
    "message.evolvedwarfare.engrams.locked_craft": ("Necesitas el engrama «%s» (nivel %s) para fabricarlo",
                                                    "You need the engram “%s” (level %s) to craft this"),
    "message.evolvedwarfare.engrams.locked_use": ("Necesitas el engrama «%s» (nivel %s) para usarlo",
                                                  "You need the engram “%s” (level %s) to use this"),
    "message.evolvedwarfare.engrams.locked_armor": ("Te quitas %s: necesitas el engrama «%s» (nivel %s)",
                                                    "You take off %s: you need the engram “%s” (level %s)"),
    "message.evolvedwarfare.engrams.learned": ("Engrama aprendido: %s", "Engram learned: %s"),
    "message.evolvedwarfare.engrams.migrated": ("Lo que ya sabías forjar pasa a engramas: %s",
                                                "What you could already forge is now engrams: %s"),
    "message.evolvedwarfare.engrams.welcome": (
        "Ahora casi todo se desbloquea con engramas: pulsa %s para aprenderlos con los puntos que ganas al subir de nivel.",
        "Almost everything is now unlocked with engrams: press %s to learn them with the points you get by levelling up."),
    "tooltip.evolvedwarfare.engrams.needed": ("Necesitas el engrama «%s» (nivel %s)", "Requires the engram “%s” (level %s)"),
    "commands.evolvedwarfare.engrams.learned": ("%s engramas aprendidos para %s jugadores", "%s engrams learned for %s players"),
    "commands.evolvedwarfare.engrams.reset": ("Engramas olvidados y puntos devueltos a %s jugadores",
                                              "Engrams forgotten and points refunded for %s players"),
    "config.evolvedwarfare.researchRequired": ("Engramas para fabricar y usar", "Engrams to craft and use"),
    "config.evolvedwarfare.researchRequired.tip": (
        "Hace falta aprender el engrama para fabricar casi todo y para usar el equipo encontrado.",
        "You must learn the engram to craft almost anything and to use gear you find."),
    "config.evolvedwarfare.engramsOtherMods": ("Engramas para otros mods", "Engrams for other mods"),
    "config.evolvedwarfare.engramsOtherMods.tip": (
        "Las recetas de otros mods también necesitan engrama (van a «Otros bloques» y «Otros objetos»).",
        "Other mods' recipes also need an engram (they go to “Other blocks” and “Other items”)."),
}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def main():
    ids = [e["id"] for e in E]
    assert len(ids) == len(set(ids)), "engramas repetidos"
    for e in E:
        for r in e.get("requires", []):
            assert r in ids, "%s pide %s, que no existe" % (e["id"], r)
            req = next(x for x in E if x["id"] == r)
            assert req["level"] <= e["level"], "%s (nivel %s) pide %s (nivel %s)" % (e["id"], e["level"], r, req["level"])
        assert e["category"] in CATEGORIES, e["category"]
    data = collections.OrderedDict()
    data["free"] = FREE
    data["categories"] = list(CATEGORIES)
    data["engrams"] = [collections.OrderedDict((k, v) for k, v in e.items() if not k.startswith("_")) for e in E]
    write_json(OUT, data)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            lang = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for e in E:
            lang["engram.%s.%s" % (NS, e["id"])] = e["_en"] if english else e["_es"]
        for key, (es, en) in CATEGORIES.items():
            lang["engram_category.%s.%s" % (NS, key)] = en if english else es
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    total = sum(e["cost"] for e in E)
    print("engramas: %d, %d puntos en total" % (len(E), total))
    if "--check" in sys.argv:
        points = 10 + sum(6 + l // 10 for l in range(2, 101))
        print("puntos hasta el 100 (con los 10 del principio): %d" % points)
        for lv in (5, 10, 20, 30, 50, 70, 100):
            have = 10 + sum(6 + l // 10 for l in range(2, lv + 1))
            avail = sum(e["cost"] for e in E if e["level"] <= lv)
            print("  nivel %3d: %4d puntos ganados, %4d en engramas de su nivel o menos" % (lv, have, avail))
        for c in CATEGORIES:
            es = [e for e in E if e["category"] == c]
            print("  %-12s %2d engramas, %3d puntos" % (c, len(es), sum(e["cost"] for e in es)))


if __name__ == "__main__":
    main()
