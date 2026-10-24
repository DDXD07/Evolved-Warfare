"""Fase 5: botín de los cofres de vikingos y piratas y de los propios vikingos y piratas.

Uso: python3 ew_loot.py <src/main/resources/data/evolvedwarfare>
"""
import json
import os
import sys

OUT = sys.argv[1]


def item(name, weight, lo=1, hi=1, extra=None):
    e = {"type": "minecraft:item", "name": "minecraft:" + name, "weight": weight}
    fns = []
    if (lo, hi) != (1, 1):
        fns.append({"function": "minecraft:set_count", "add": False,
                    "count": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}})
    if extra:
        fns.extend(extra)
    if fns:
        e["functions"] = fns
    return e


def chest(rolls, entries, rare=None):
    pools = [{"rolls": {"type": "minecraft:uniform", "min": float(rolls[0]), "max": float(rolls[1])},
              "bonus_rolls": 0.0, "entries": entries}]
    if rare:
        pools.append({"rolls": 1.0, "bonus_rolls": 0.0, "entries": rare})
    return {"type": "minecraft:chest", "pools": pools}


def looting(name, lo, hi):
    return {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{
        "type": "minecraft:item", "name": "minecraft:" + name,
        "functions": [
            {"function": "minecraft:set_count", "add": False, "count": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}},
            {"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
             "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0}}]}]}


ENCHANT = [{"function": "minecraft:enchant_randomly"}]
EMPTY = {"type": "minecraft:empty", "weight": 1}

TABLES = {
    "chests/viking_longhouse": chest((4, 7), [
        item("iron_ingot", 8, 2, 6), item("gold_ingot", 5, 1, 4), item("cooked_salmon", 6, 2, 6), item("cooked_cod", 6, 2, 6),
        item("bread", 6, 2, 5), item("honey_bottle", 5, 1, 3), item("leather", 5, 2, 6), item("arrow", 5, 6, 16),
        item("emerald", 4, 1, 5), item("chainmail_chestplate", 2), item("chainmail_helmet", 2), item("iron_axe", 3, extra=ENCHANT),
        item("shield", 3), item("iron_helmet", 2)],
        rare=[item("golden_apple", 2), item("diamond", 1), item("goat_horn", 1, extra=[{"function": "minecraft:set_instrument", "options": "#minecraft:regular_goat_horns"}]), EMPTY | {"weight": 6}]),
    "chests/viking_hut": chest((2, 5), [
        item("cooked_salmon", 6, 1, 4), item("bread", 6, 1, 3), item("leather", 5, 1, 4), item("white_wool", 4, 1, 3),
        item("spruce_log", 5, 2, 6), item("iron_nugget", 5, 2, 8), item("honey_bottle", 3), item("arrow", 4, 3, 10),
        item("flint", 3, 1, 4), item("emerald", 2, 1, 2)]),
    "chests/pirate_ship": chest((3, 6), [
        item("gold_nugget", 8, 4, 16), item("gold_ingot", 5, 1, 4), item("emerald", 5, 1, 6), item("honey_bottle", 6, 1, 3),
        item("gunpowder", 6, 2, 6), item("cooked_cod", 5, 2, 6), item("bread", 4, 1, 3), item("string", 4, 2, 6),
        item("compass", 2), item("map", 2), item("spyglass", 1), item("crossbow", 2, extra=ENCHANT), item("arrow", 4, 6, 16)]),
    "chests/pirate_treasure": chest((4, 8), [
        item("gold_ingot", 8, 2, 8), item("emerald", 7, 2, 9), item("diamond", 3, 1, 3), item("gold_nugget", 6, 6, 20),
        item("golden_apple", 2), item("iron_ingot", 4, 2, 6), item("golden_sword", 2, extra=ENCHANT),
        item("golden_helmet", 2, extra=ENCHANT), item("nautilus_shell", 2, 1, 2), item("prismarine_crystals", 2, 2, 6)],
        rare=[item("heart_of_the_sea", 1), item("enchanted_golden_apple", 1), item("diamond", 2, 1, 2), EMPTY | {"weight": 8}]),
    "chests/pirate_camp": chest((2, 5), [
        item("honey_bottle", 6, 1, 3), item("cooked_cod", 6, 1, 4), item("gold_nugget", 6, 2, 9), item("gunpowder", 5, 1, 4),
        item("string", 4, 1, 4), item("arrow", 4, 4, 12), item("emerald", 3, 1, 3), item("sugar", 4, 1, 4),
        item("rotten_flesh", 3, 1, 3)]),
}

def explorer_map(destination, decoration, name, weight):
    return {"type": "minecraft:item", "name": "minecraft:map", "weight": weight, "functions": [
        {"function": "minecraft:exploration_map", "destination": f"evolvedwarfare:{destination}",
         "decoration": decoration, "zoom": 1, "skip_existing_chunks": False},
        {"function": "minecraft:set_name", "target": "item_name", "name": {"translate": name}}]}


VIKING_MAP = explorer_map("on_viking_maps", "minecraft:village_snowy", "filled_map.evolvedwarfare.viking_settlement", 1)
PIRATE_MAP = explorer_map("on_pirate_maps", "minecraft:red_x", "filled_map.evolvedwarfare.pirate_cove", 1)


def map_pool(entries, empty):
    """Un mapa de vez en cuando (entries con peso 1 cada uno; 'empty' es el peso de no dar nada)."""
    return {"rolls": 1.0, "bonus_rolls": 0.0, "entries": entries + [{"type": "minecraft:empty", "weight": empty}]}


# los vikingos guardan cartas de las calas piratas (sus rivales) y los piratas de los asentamientos que saquear
TABLES["chests/viking_longhouse"]["pools"].append(map_pool([PIRATE_MAP], 1))
TABLES["chests/pirate_ship"]["pools"].append(map_pool([VIKING_MAP], 1))
TABLES["chests/pirate_treasure"]["pools"].append(map_pool([PIRATE_MAP], 2))
TABLES["chests/viking_hut"]["pools"].append(map_pool([VIKING_MAP, PIRATE_MAP], 8))
TABLES["chests/pirate_camp"]["pools"].append(map_pool([VIKING_MAP, PIRATE_MAP], 8))

# ------------------------------------------------------------------ botín según el nivel del asentamiento
LEVEL = "evolvedwarfare:settlement_level"


def at_level(n):
    return [{"condition": LEVEL, "min": n}]


def enchanted(name, weight, lo, hi):
    return item(name, weight, extra=[{"function": "minecraft:enchant_with_levels",
                                      "levels": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}}])


def book(weight, lo=None, hi=None):
    if lo is None:
        return item("book", weight, extra=[{"function": "minecraft:enchant_randomly"}])
    return enchanted("book", weight, lo, hi)


TIER_1 = [item("diamond", 3, 1, 2), book(3), enchanted("iron_sword", 2, 10, 20), enchanted("iron_axe", 2, 10, 20),
          enchanted("iron_chestplate", 2, 10, 20), item("golden_apple", 2), item("emerald", 4, 4, 10),
          item("experience_bottle", 3, 3, 8)]
TIER_2 = [item("diamond", 4, 2, 4), enchanted("diamond_sword", 2, 20, 30), enchanted("diamond_axe", 2, 20, 30),
          enchanted("diamond_chestplate", 2, 20, 30), item("golden_apple", 3, 1, 3), book(3, 25, 30),
          item("experience_bottle", 3, 6, 14), item("emerald_block", 1)]
TIER_3 = [item("netherite_scrap", 3, 1, 2), item("enchanted_golden_apple", 1), enchanted("diamond_helmet", 2, 30, 30),
          enchanted("diamond_leggings", 2, 30, 30), item("totem_of_undying", 1), item("diamond_block", 1),
          book(2, 30, 30)]


def level_pools(base_entries):
    """Más botín cuanto más nivel: tiradas extra del mismo cofre desde el 3, el 6 y el 9; objetos mejores desde el 4
    (diamantes, libros, equipo de hierro encantado), el 7 (equipo de diamante, manzanas de oro) y el 9 (chatarra de
    netherita, manzana encantada, tótem)."""
    def extra(lo, hi, n):
        return {"rolls": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}, "bonus_rolls": 0.0,
                "entries": base_entries, "conditions": at_level(n)}

    def tier(entries, n, rolls=1.0):
        return {"rolls": rolls, "bonus_rolls": 0.0, "entries": entries, "conditions": at_level(n)}

    return [extra(1, 2, 3), extra(1, 2, 6), extra(2, 3, 9), tier(TIER_1, 4), tier(TIER_1, 6), tier(TIER_2, 7),
            tier(TIER_2, 8), tier(TIER_3, 9), tier(TIER_3, 10)]


def entity_level_pools():
    """Los defensores de asentamientos de nivel alto llevan algo encima."""
    return [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": at_level(4),
             "entries": [item("emerald", 1, 1, 2), {"type": "minecraft:empty", "weight": 3}]},
            {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": at_level(7),
             "entries": [item("diamond", 1), item("experience_bottle", 1, 1, 3), {"type": "minecraft:empty", "weight": 8}]}]


def without_level_pools(table):
    table["pools"] = [p for p in table["pools"] if not any(c.get("condition") == LEVEL for c in p.get("conditions", []))]
    return table


for _name, _table in TABLES.items():
    _table["pools"].extend(level_pools(_table["pools"][0]["entries"]))

ENTITIES = {
    "entities/viking": {"type": "minecraft:entity", "pools": [looting("iron_nugget", 0, 3), looting("leather", 0, 2),
                                                               looting("cooked_salmon", 0, 1)]},
    "entities/pirate": {"type": "minecraft:entity", "pools": [looting("gold_nugget", 0, 4), looting("gunpowder", 0, 1),
                                                               looting("bone", 0, 1)]},
}


for _table in ENTITIES.values():
    _table["pools"].extend(entity_level_pools())

# tablas de la fase 2 (hechas a mano): se les añaden (o rehacen) los grupos por nivel
HAND_MADE_CHESTS = ["chests/bandit_camp", "chests/bandit_fort"]
HAND_MADE_ENTITIES = ["entities/bandit"]


def main():
    for name in HAND_MADE_CHESTS + HAND_MADE_ENTITIES:
        path = os.path.join(OUT, "loot_table", name + ".json")
        with open(path, encoding="utf-8") as f:
            table = without_level_pools(json.load(f))
        if name in HAND_MADE_CHESTS:
            table["pools"].extend(level_pools(table["pools"][0]["entries"]))
        else:
            table["pools"].extend(entity_level_pools())
        with open(path, "w", encoding="utf-8") as f:
            json.dump(table, f, indent=2)
            f.write("\n")
    for name, table in {**TABLES, **ENTITIES}.items():
        path = os.path.join(OUT, "loot_table", name + ".json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(table, f, indent=2)
            f.write("\n")
    fort = os.path.join(OUT, "loot_table", "chests", "bandit_fort.json")
    with open(fort, encoding="utf-8") as f:
        table = json.load(f)
    table["pools"] = [p for p in table["pools"] if not any(e.get("name") == "minecraft:map" for e in p.get("entries", []))]
    table["pools"].append(map_pool([VIKING_MAP, PIRATE_MAP], 3))
    with open(fort, "w", encoding="utf-8") as f:
        json.dump(table, f, indent=2)
        f.write("\n")
    print("ok", len(TABLES) + len(ENTITIES) + 1)


if __name__ == "__main__":
    main()
