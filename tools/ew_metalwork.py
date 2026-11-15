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


# ------------------------------------------------------------------ fase 79: las ballestas mecánicas

WOOD, WOOD_DARK = (138, 98, 56), (92, 62, 34)
STRING = (230, 226, 210)


def _line(im, a, b, color, width=1):
    dr = ImageDraw.Draw(im)
    dr.line([a, b], fill=color + (255,), width=width)


def crossbow(kind, state):
    """state: standby, pulling_0..2, arrow, firework."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    pull = {"standby": 1, "pulling_0": 2, "pulling_1": 3, "pulling_2": 4, "arrow": 4, "firework": 4}[state]
    # la culata, de abajo a la izquierda hacia arriba a la derecha
    _line(im, (2, 14), (11, 5), WOOD_DARK, 2)
    _line(im, (3, 13), (11, 5), WOOD)
    prod = (150, 166, 188) if kind == "arbalest" else (120, 84, 48)
    prod_dark = (84, 96, 116) if kind == "arbalest" else (80, 54, 30)
    # el arco (se dobla hacia atrás al tensarlo)
    bend = (pull - 1) * 0.5
    t1 = (int(round(7 - bend)), int(round(1 + bend)))
    t2 = (int(round(15 - bend)), int(round(9 + bend)))
    # la cuerda (detrás del arco)
    nut = (11 - pull, 5 + pull)
    dr.line([t1, nut, t2], fill=STRING + (255,), width=1)
    dr.line([t1, (12, 4), t2], fill=prod + (255,), width=1)
    dr.line([(t1[0] + 1, t1[1]), (12, 3), (t2[0], t2[1] - 1)], fill=prod_dark + (255,), width=1)
    if state == "arrow":
        _line(im, (nut[0] - 1, nut[1] + 1), (13, 3), (110, 86, 54))
        im.putpixel((13, 3), (200, 200, 206, 255))
        im.putpixel((14, 2), (230, 230, 236, 255))
    elif state == "firework":
        _line(im, (nut[0] - 1, nut[1] + 1), (13, 3), (190, 40, 40), 2)
        im.putpixel((14, 2), (230, 200, 80, 255))
    if kind == "arbalest":
        # el torno de manivela en la culata
        dr.ellipse((1, 11, 5, 15), outline=(84, 96, 116, 255), fill=(150, 166, 188, 255))
        im.putpixel((3, 13), (60, 60, 66, 255))
        _line(im, (4, 15), (6, 15), (60, 60, 66))
    else:
        # el cargador encima de la culata
        dr.polygon([(5, 7), (8, 4), (10, 6), (7, 9)], fill=(110, 76, 42, 255), outline=(70, 48, 26, 255))
        im.putpixel((7, 6), (170, 170, 178, 255))
    return im


# ------------------------------------------------------------------ fase 80: herramientas y armas de acero

STEEL_PAL = ((214, 226, 240), (142, 162, 188), (94, 110, 136), (34, 40, 54))   # claro, medio, oscuro, contorno
HANDLE = ((84, 62, 44), (58, 42, 30), (40, 28, 20))                         # mango de madera oscura con cuero


def _shade_fill(mask, pal):
    """Pinta una forma (conjunto de píxeles) con luz arriba a la izquierda y sombra abajo a la derecha."""
    out = {}
    for (x, y) in mask:
        up = (x, y - 1) in mask
        left = (x - 1, y) in mask
        down = (x, y + 1) in mask
        right = (x + 1, y) in mask
        if not up or not left:
            out[(x, y)] = pal[0]
        elif not down or not right:
            out[(x, y)] = pal[2]
        else:
            out[(x, y)] = pal[1]
    return out


def _tool(head, handle_len=9):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # el mango, en diagonal desde abajo a la izquierda
    for i in range(handle_len):
        x, y = 2 + i, 13 - i
        im.putpixel((x, y), (HANDLE[1] if i % 2 else HANDLE[0]) + (255,))
        if i < 3:
            im.putpixel((x, y), (HANDLE[2] if i % 2 else HANDLE[1]) + (255,))
    for (x, y), c in _shade_fill(set(head), STEEL_PAL).items():
        if 0 <= x < 16 and 0 <= y < 16:
            im.putpixel((x, y), c + (255,))
    # contorno
    src = im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] > 0:
                        im.putpixel((x, y), STEEL_PAL[3] + (255,))
                        break
    return im


def steel_sword():
    head = []
    for i in range(9):
        x, y = 5 + i, 10 - i
        head += [(x, y), (x + 1, y), (x, y - 1)]
    head += [(14, 1), (14, 0)]
    im = _tool(head, handle_len=0)
    # guarda y empuñadura
    guard = (70, 76, 90)
    for (x, y) in ((3, 9), (4, 10), (5, 11), (6, 12), (4, 9), (6, 11)):
        im.putpixel((x, y), guard + (255,))
    for i in range(3):
        im.putpixel((2 + i - 0, 13 - i + 0), (HANDLE[0] if i % 2 else HANDLE[1]) + (255,))
    im.putpixel((1, 14), (150, 162, 180, 255))
    return im


def steel_pickaxe():
    head = set()
    import math
    for a in range(0, 121):
        f = a / 120.0
        t = math.radians(-88 + f * 86)
        radii = (10.4, 11.2, 12.0) if 0.25 < f < 0.75 else (10.8, 11.6)
        for r in radii:
            head.add((int(round(2 + r * math.cos(t))), int(round(13 + r * math.sin(t)))))
    head = {(x, y) for (x, y) in head if 0 <= x < 16 and 0 <= y < 16}
    return _tool(sorted(head), handle_len=10)


def _poly(points):
    im = Image.new("L", (16, 16), 0)
    ImageDraw.Draw(im).polygon(points, fill=255)
    return [(x, y) for x in range(16) for y in range(16) if im.getpixel((x, y))]


def steel_axe():
    head = _poly([(5, 1), (9, 1), (11, 3), (11, 5), (9, 7), (6, 7), (4, 5), (4, 2)])
    head = [(x, y) for (x, y) in head if not (x >= 9 and y >= 6)]
    return _tool(head, handle_len=10)


def steel_shovel():
    head = [(x, y) for x in range(9, 15) for y in range(1, 7) if abs((x - 12) + (y - 4)) + abs((x - 12) - (y - 4)) <= 5]
    return _tool(head, handle_len=8)


def steel_hoe():
    head = [(8, 2), (9, 2), (10, 2), (11, 2), (12, 2), (8, 3), (9, 3), (12, 3), (13, 3), (13, 4), (12, 4)]
    return _tool(head, handle_len=11)


STEEL_TOOLS = {"sword": steel_sword, "pickaxe": steel_pickaxe, "axe": steel_axe, "shovel": steel_shovel, "hoe": steel_hoe}
STEEL_WEAPONS = ("dagger", "spear", "longsword", "halberd", "warhammer", "flail", "battle_axe")


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
    ("arbalest", ("Ballesta pesada", "Arbalest",
                  "Arco de acero que se tensa con un torno de manivela: tarda el triple en cargar, pero el virote sale mucho más rápido, hace más del doble de daño y atraviesa a uno.",
                  "Steel prod spanned with a crank windlass: three times slower to load, but the bolt flies much faster, deals over twice the damage and pierces one target.")),
    ("repeating_crossbow", ("Ballesta de repetición", "Repeating Crossbow",
                            "Con cargador: al tensarla carga hasta 5 flechas, y luego cada clic dispara una (algo menos de daño).",
                            "With a magazine: spanning it loads up to 5 arrows, then each click shoots one (a bit less damage).")),
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
    "tooltip.evolvedwarfare.crossbow.loaded": ("Cargadas: %s", "Loaded: %s"),
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
    shaped("arbalest", ["SSS", "GCG", " T "], {"S": NS + ":steel_plate", "G": NS + ":iron_small_gear", "C": "minecraft:crossbow",
                                                 "T": "minecraft:tripwire_hook"}, NS + ":arbalest")
    shaped("repeating_crossbow", ["PIP", "GCG", " T "], {"P": "#minecraft:planks", "I": NS + ":iron_plate", "G": NS + ":small_gear",
                                                       "C": "minecraft:crossbow", "T": "minecraft:tripwire_hook"}, NS + ":repeating_crossbow")
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
    for name in ("arbalest", "repeating_crossbow"):
        kind = "arbalest" if name == "arbalest" else "repeater"
        for st in ("standby", "pulling_0", "pulling_1", "pulling_2", "arrow", "firework"):
            crossbow(kind, st).save(os.path.join(item_dir, "%s_%s.png" % (name, st)))
            if st != "standby":
                write_json(os.path.join(ASSETS, "models/item/%s_%s.json" % (name, st)),
                           {"parent": "minecraft:item/crossbow", "textures": {"layer0": "%s:item/%s_%s" % (NS, name, st)}})
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name), {
            "parent": "minecraft:item/crossbow", "textures": {"layer0": "%s:item/%s_standby" % (NS, name)},
            "overrides": [
                {"predicate": {"pulling": 1}, "model": "%s:item/%s_pulling_0" % (NS, name)},
                {"predicate": {"pulling": 1, "pull": 0.58}, "model": "%s:item/%s_pulling_1" % (NS, name)},
                {"predicate": {"pulling": 1, "pull": 1.0}, "model": "%s:item/%s_pulling_2" % (NS, name)},
                {"predicate": {"charged": 1}, "model": "%s:item/%s_arrow" % (NS, name)},
                {"predicate": {"charged": 1, "firework": 1}, "model": "%s:item/%s_firework" % (NS, name)}]})
    for name in [n for n in ITEMS if n not in ("arbalest", "repeating_crossbow")]:
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    # herramientas de acero (iconos propios) y armas medievales de acero (los iconos con los dibujos de ew_weapons)
    for kind, painter in STEEL_TOOLS.items():
        painter().save(os.path.join(item_dir, "steel_%s.png" % kind))
        write_json(os.path.join(ASSETS, "models/item/steel_%s.json" % kind),
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/steel_%s" % (NS, kind)}})
    saved = sys.argv[:]
    sys.argv = [sys.argv[0], os.path.join(ROOT, "src/main/resources"), "/tmp/ew_weapons_preview"]
    import ew_weapons as WP
    sys.argv = saved
    WP.METALS["steel"] = tuple(WP.hexc("#%02X%02X%02X" % c) for c in STEEL_PAL)
    for w in STEEL_WEAPONS:
        WP.SHAPES[w]("steel").save(os.path.join(item_dir, "steel_%s.png" % w))
    tags = {"swords": ["%s:steel_sword" % NS] + ["%s:steel_%s" % (NS, w) for w in STEEL_WEAPONS if w != "battle_axe"],
            "axes": ["%s:steel_axe" % NS, "%s:steel_battle_axe" % NS], "pickaxes": ["%s:steel_pickaxe" % NS],
            "shovels": ["%s:steel_shovel" % NS], "hoes": ["%s:steel_hoe" % NS]}
    for tag, values in tags.items():
        path = os.path.join(ROOT, "src/main/resources/data/minecraft/tags/item", tag + ".json")
        old = []
        if os.path.exists(path):
            with open(path, encoding="utf-8") as fh:
                old = json.load(fh).get("values", [])
        write_json(path, {"replace": False, "values": [v for v in old if v not in values] + values})
    recipes()
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for key, (es, en, des, den) in ITEMS.items():
            data["item.%s.%s" % (NS, key)] = en if english else es
            data["item.%s.%s.desc" % (NS, key)] = den if english else des
        for key, (es, en) in STEEL_NAMES.items():
            data.pop("item.%s.steel.%s" % (NS, key), None)
            data["item.%s.steel_%s" % (NS, key)] = en if english else es
        for key, (es, en) in TEXTS.items():
            data[key] = en if english else es
        write_json(path, data)
    print("metalurgia: %d objetos" % len(ITEMS))


if __name__ == "__main__":
    main()
