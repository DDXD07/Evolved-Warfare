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

ENTITIES = {
    "entities/viking": {"type": "minecraft:entity", "pools": [looting("iron_nugget", 0, 3), looting("leather", 0, 2),
                                                               looting("cooked_salmon", 0, 1)]},
    "entities/pirate": {"type": "minecraft:entity", "pools": [looting("gold_nugget", 0, 4), looting("gunpowder", 0, 1),
                                                               looting("bone", 0, 1)]},
}


def main():
    for name, table in {**TABLES, **ENTITIES}.items():
        path = os.path.join(OUT, "loot_table", name + ".json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(table, f, indent=2)
            f.write("\n")
    print("ok", len(TABLES) + len(ENTITIES))


if __name__ == "__main__":
    main()
