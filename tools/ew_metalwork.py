"""La metalurgia de la ingeniería medieval (fase 77 en adelante): placas, anillas y acero.

Dibuja los iconos (lingote y lingote al rojo de acero, placas de hierro, cobre, oro y acero, anillas de hierro), los
modelos de objeto, las recetas y los textos.

Uso: python3 tools/ew_metalwork.py
"""
import collections
import glob
import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ew_demons as D  # noqa: E402
import ew_wildlife as W  # noqa: E402

NS = "evolvedwarfare"
ROOT = W.ROOT
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
write_json = D.write_json

METALS = {
    "iron": ((222, 222, 226), (160, 160, 166), (108, 108, 116)),
    "copper": ((232, 150, 104), (188, 104, 70), (132, 68, 46)),
    "gold": ((252, 226, 100), (226, 176, 44), (170, 120, 22)),
    "steel": ((178, 194, 212), (124, 140, 162), (82, 94, 114)),
    "hot_steel": ((255, 196, 110), (236, 120, 46), (170, 60, 24)),
}


def ingot(kind):
    hi, mid, lo = METALS[kind]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    dr.polygon([(4, 5), (14, 5), (12, 8), (2, 8)], fill=hi + (255,))
    dr.polygon([(2, 8), (12, 8), (12, 11), (2, 11)], fill=mid + (255,))
    dr.polygon([(12, 8), (14, 5), (14, 8), (12, 11)], fill=lo + (255,))
    dr.line([(5, 6), (11, 6)], fill=(255, 255, 255, 160))
    dr.line([(2, 11), (12, 11)], fill=lo + (255,))
    return im


def plate(kind):
    hi, mid, lo = METALS[kind]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    top = [(1, 7), (8, 3), (15, 7), (8, 11)]
    dr.polygon([(1, 7), (8, 11), (8, 13), (1, 9)], fill=lo + (255,))
    dr.polygon([(8, 11), (15, 7), (15, 9), (8, 13)], fill=mid + (255,))
    dr.polygon(top, fill=hi + (255,))
    dr.line([(3, 7), (8, 4)], fill=(255, 255, 255, 150))
    for (x, y) in ((4, 7), (8, 5), (12, 7), (8, 9)):
        im.putpixel((x, y), lo + (255,))
    return im


def rings():
    hi, mid, lo = METALS["iron"]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    for (x, y) in ((1, 2), (6, 4), (3, 8), (8, 9), (10, 3)):
        dr.ellipse((x, y, x + 5, y + 5), outline=mid + (255,))
        dr.arc((x, y, x + 5, y + 5), 200, 320, fill=hi + (255,))
    return im


ITEMS = collections.OrderedDict([
    ("steel_ingot", ("Lingote de acero", "Steel Ingot",
                     "Sale del crisol: hierro fundido con carbón y un fuelle soplando. Más duro que el hierro.",
                     "Made in the crucible: molten iron with coal while a bellows blows. Harder than iron.")),
    ("hot_steel_ingot", ("Lingote de acero al rojo", "Hot Steel Ingot", "Para fundirlo en el crisol y forjar piezas de acero.",
                         "To melt in the crucible and forge steel parts.")),
    ("iron_plate", ("Placa de hierro", "Iron Plate", "Un lingote aplanado por el martinete sobre el yunque.",
                    "An ingot flattened by the trip hammer on the anvil.")),
    ("copper_plate", ("Placa de cobre", "Copper Plate", "Un lingote aplanado por el martinete sobre el yunque.",
                      "An ingot flattened by the trip hammer on the anvil.")),
    ("gold_plate", ("Placa de oro", "Gold Plate", "Un lingote aplanado por el martinete sobre el yunque.",
                    "An ingot flattened by the trip hammer on the anvil.")),
    ("steel_plate", ("Placa de acero", "Steel Plate", "Un lingote de acero aplanado por el martinete sobre el yunque.",
                     "A steel ingot flattened by the trip hammer on the anvil.")),
    ("iron_rings", ("Anillas de hierro", "Iron Rings", "Para la cota de malla. Salen de cortar una placa de hierro.",
                    "For chainmail. Cut from an iron plate.")),
])

STEEL_NAMES = {
    "sword": ("Espada de acero", "Steel Sword"), "pickaxe": ("Pico de acero", "Steel Pickaxe"), "axe": ("Hacha de acero", "Steel Axe"),
    "shovel": ("Pala de acero", "Steel Shovel"), "hoe": ("Azada de acero", "Steel Hoe"), "dagger": ("Daga de acero", "Steel Dagger"),
    "spear": ("Lanza de acero", "Steel Spear"), "longsword": ("Espada larga de acero", "Steel Longsword"),
    "halberd": ("Alabarda de acero", "Steel Halberd"), "warhammer": ("Martillo de guerra de acero", "Steel Warhammer"),
    "flail": ("Mangual de acero", "Steel Flail"), "battle_axe": ("Hacha de batalla de acero", "Steel Battle Axe"),
}

TEXTS = {
    "material.evolvedwarfare.steel": ("acero", "steel"),
    "tooltip.evolvedwarfare.steel": ("Acero: +50 % de durabilidad, +1 de daño, +10 % al picar", "Steel: +50% durability, +1 damage, +10% mining speed"),
    "message.evolvedwarfare.steel.made": ("¡El hierro se ha hecho acero! (%s de carbón)", "The iron has turned into steel! (%s coal)"),
    "message.evolvedwarfare.steel.need_molten": ("Hace falta hierro fundido del todo en el crisol, al fuego", "The crucible needs fully molten iron, on the fire"),
    "message.evolvedwarfare.steel.need_bellows": ("Hace falta un fuelle soplando al crisol (o a su fuego)", "A bellows must be blowing into the crucible (or its fire)"),
    "message.evolvedwarfare.steel.need_coal": ("Hace falta un carbón por cada lingote fundido (%s)", "One coal per molten ingot is needed (%s)"),
    "message.evolvedwarfare.crucible.ingots": ("Sacas %s lingotes del crisol", "You take %s ingots out of the crucible"),
}


def recipes():
    def shaped(name, pattern, key, result, count=1):
        write_json(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
            "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
            "result": {"id": result, "count": count}})

    write_json(os.path.join(DATA, "recipe", "iron_rings.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [{"item": NS + ":iron_plate"}],
        "result": {"id": NS + ":iron_rings", "count": 2}})
    R = NS + ":iron_rings"
    shaped("chainmail_helmet", ["RRR", "R R"], {"R": R}, "minecraft:chainmail_helmet")
    shaped("chainmail_chestplate", ["R R", "RRR", "RRR"], {"R": R}, "minecraft:chainmail_chestplate")
    shaped("chainmail_leggings", ["RRR", "R R", "R R"], {"R": R}, "minecraft:chainmail_leggings")
    shaped("chainmail_boots", ["R R", "R R"], {"R": R}, "minecraft:chainmail_boots")
    for kind, typ, t in (("blasting", "minecraft:blasting", 100), ("smelting", "minecraft:smelting", 200)):
        write_json(os.path.join(DATA, "recipe", "hot_steel_ingot_from_%s.json" % kind), {
            "type": typ, "category": "misc", "ingredient": {"item": NS + ":steel_ingot"},
            "result": {"id": NS + ":hot_steel_ingot", "count": 1}, "experience": 0.0, "cookingtime": t})


def main():
    item_dir = os.path.join(ASSETS, "textures/item")
    ingot("steel").save(os.path.join(item_dir, "steel_ingot.png"))
    ingot("hot_steel").save(os.path.join(item_dir, "hot_steel_ingot.png"))
    for m in ("iron", "copper", "gold", "steel"):
        plate(m).save(os.path.join(item_dir, "%s_plate.png" % m))
    rings().save(os.path.join(item_dir, "iron_rings.png"))
    for name in ITEMS:
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    recipes()
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for key, (es, en, des, den) in ITEMS.items():
            data["item.%s.%s" % (NS, key)] = en if english else es
            data["item.%s.%s.desc" % (NS, key)] = den if english else des
        for key, (es, en) in STEEL_NAMES.items():
            data["item.%s.steel.%s" % (NS, key)] = en if english else es
        for key, (es, en) in TEXTS.items():
            data[key] = en if english else es
        write_json(path, data)
    print("metalurgia: %d objetos" % len(ITEMS))


if __name__ == "__main__":
    main()
