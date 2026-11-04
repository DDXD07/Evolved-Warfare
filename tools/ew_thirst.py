"""La sed (fase 15, supervivencia): el medidor de gotas, el agua en el hueco de las manos, el icono de los parásitos,
la cantimplora (vacía, con agua sin hervir y con agua hervida), el agua hervida y los zumos (con las capas del frasco de
vanilla, teñidas desde el juego), sus modelos, las recetas (horno y fogata para hervir, mesa para la cantimplora y los
zumos), los tipos de daño y los textos.

Uso: python3 tools/ew_thirst.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import os
import sys

from PIL import Image

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


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def save(im, *parts):
    path = os.path.join(ASSETS, "textures", *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    return im


# ------------------------------------------------------------------ el medidor

# una gota de 9x9: punta arriba, panza abajo (1 = borde, 2 = relleno, 3 = brillo)
DROP = [
    "....1....",
    "...121...",
    "..12221..",
    ".1232221.",
    ".1322221.",
    "122222221",
    "122222221",
    ".1222221.",
    "..11111..",
]


def drop(fill, light, dark, outline=(26, 26, 26, 255), half=False, empty=False):
    im = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    for y, row in enumerate(DROP):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if empty:
                # el hueco: solo el borde y un fondo oscuro (como las piernas de pollo vacías)
                c = outline if ch == "1" else (52, 40, 34, 255)
            elif ch == "1":
                c = outline
            elif half and x < 4:
                # media gota: la mitad izquierda vacía (se gasta de izquierda a derecha, como el hambre)
                c = (52, 40, 34, 255)
            elif ch == "3":
                c = light
            else:
                c = fill if y < 6 else dark
            im.putpixel((x, y), c)
    return im


def meter():
    blue = ((48, 132, 230, 255), (190, 230, 255, 255), (30, 92, 186, 255))
    sick = ((112, 150, 52, 255), (206, 228, 140, 255), (72, 104, 32, 255))
    out = {
        "empty": drop(*blue, empty=True),
        "full": drop(*blue),
        "half": drop(*blue, half=True),
        "sick_full": drop(*sick),
        "sick_half": drop(*sick, half=True),
    }
    for name, im in out.items():
        save(im, "gui", "thirst", name + ".png")
    return out


# ------------------------------------------------------------------ el agua en las manos y los parásitos

def cupped_water():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            dx = (x - 7.5) / 7.5
            dy = (y - 7.5) / 6.5
            r = dx * dx + dy * dy
            if r > 1.0:
                continue
            b = int(200 + 40 * (1 - r))
            c = (int(60 + 50 * (1 - r)), int(140 + 50 * (1 - r)), b, int(150 + 70 * (1 - r)))
            # reflejos
            if (x + 2 * y) % 9 == 0 and r < 0.6:
                c = (220, 240, 255, 230)
            im.putpixel((x, y), c)
    return save(im, "misc", "cupped_water.png")


def parasites_icon():
    im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    worm = (128, 156, 60, 255)
    worm_d = (76, 98, 34, 255)
    # tres gusanitos ondulados
    for k, (ox, oy, n) in enumerate([(2, 4, 12), (4, 9, 11), (3, 13, 12)]):
        for t in range(n):
            x = ox + t
            y = oy + (0 if (t // 2) % 2 == 0 else 1)
            im.putpixel((x, y), worm)
            if y + 1 < 18:
                im.putpixel((x, y + 1), worm_d)
        # la cabeza
        hx = ox + n
        if hx < 18:
            im.putpixel((hx, oy), (40, 50, 20, 255))
    return save(im, "mob_effect", "parasites.png")


# ------------------------------------------------------------------ la cantimplora

def canteen(kind):
    """Una cantimplora de hojalata forrada de lona, con tapón y correa de cuero. RAW: tapón azul; BOILED: tapón blanco con vapor."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    tin = (168, 172, 176, 255)
    tin_l = (214, 218, 222, 255)
    tin_d = (110, 114, 120, 255)
    cloth = (126, 104, 70, 255)
    cloth_d = (92, 74, 48, 255)
    strap = (92, 56, 32, 255)
    outline = (40, 34, 30, 255)
    cx, cy = 7.5, 9.0
    for y in range(16):
        for x in range(16):
            dx = (x - cx) / 6.0
            dy = (y - cy) / 5.6
            r = dx * dx + dy * dy
            if r <= 1.0:
                if r > 0.78:
                    c = outline
                elif abs(y - cy) <= 1.5 or r > 0.55:
                    # la lona en medio y alrededor
                    c = cloth if (x + y) % 3 else cloth_d
                else:
                    c = tin_l if dx < -0.2 and dy < -0.1 else (tin if dy < 0.3 else tin_d)
                im.putpixel((x, y), c)
    # cuello y tapón
    cap = {"empty": (70, 60, 52, 255), "raw": (52, 120, 210, 255), "boiled": (236, 240, 244, 255)}[kind]
    for y in range(1, 4):
        for x in range(6, 10):
            im.putpixel((x, y), outline if x in (6, 9) or y == 1 else tin)
    for x in range(6, 10):
        im.putpixel((x, 1), cap)
        im.putpixel((x, 0), outline)
    # la correa que cruza
    for t in range(16):
        x, y = t, 3 + t // 3
        if 0 <= y < 16 and im.getpixel((x, y))[3] == 0:
            im.putpixel((x, y), strap)
    if kind == "raw":
        # gotitas sin hervir
        for (x, y) in [(12, 12), (13, 13)]:
            im.putpixel((x, y), (60, 140, 220, 255))
    if kind == "boiled":
        # vapor
        for (x, y) in [(11, 0), (12, 1), (11, 2), (13, 0), (14, 1), (13, 2)]:
            im.putpixel((x, y), (240, 244, 248, 200))
    return save(im, "item", ("canteen" if kind == "empty" else kind if kind != "raw" else "water") +
                ("" if kind == "empty" else "_canteen") + ".png")


def steam():
    """El vapor encima del frasco del agua hervida (capa 2 del modelo)."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y) in [(10, 0), (11, 1), (10, 2), (12, 0), (13, 1), (12, 2), (11, 3)]:
        im.putpixel((x, y), (244, 248, 252, 210))
    return save(im, "item", "boiled_water_steam.png")


# ------------------------------------------------------------------ los textos

TEXTS = {
    "item.evolvedwarfare.boiled_water": ("Frasco de agua hervida", "Bottle of Boiled Water"),
    "item.evolvedwarfare.boiled_water.desc": ("Agua sin parásitos: hierve un frasco de agua en el horno, en la fogata o llénalo en un caldero puesto al fuego.",
                                              "Parasite-free water: boil a water bottle in a furnace or on a campfire, or fill it from a cauldron over a fire."),
    "item.evolvedwarfare.melon_juice": ("Zumo de sandía", "Melon Juice"),
    "item.evolvedwarfare.melon_juice.desc": ("Quita mucha sed y refresca. Un frasco y tres rodajas de sandía.",
                                             "Quenches a lot of thirst and cools you down. A bottle and three melon slices."),
    "item.evolvedwarfare.apple_juice": ("Zumo de manzana", "Apple Juice"),
    "item.evolvedwarfare.apple_juice.desc": ("Quita la sed y alimenta un poco. Un frasco y dos manzanas.",
                                             "Quenches thirst and feeds a little. A bottle and two apples."),
    "item.evolvedwarfare.berry_juice": ("Zumo de bayas", "Berry Juice"),
    "item.evolvedwarfare.berry_juice.desc": ("Quita la sed. Un frasco y cuatro bayas dulces.",
                                             "Quenches thirst. A bottle and four sweet berries."),
    "item.evolvedwarfare.canteen": ("Cantimplora", "Canteen"),
    "item.evolvedwarfare.water_canteen": ("Cantimplora (agua sin hervir)", "Canteen (Unboiled Water)"),
    "item.evolvedwarfare.boiled_canteen": ("Cantimplora (agua hervida)", "Canteen (Boiled Water)"),
    "item.evolvedwarfare.canteen.desc.empty": ("Cuatro tragos de agua. Clic derecho en el agua para llenarla (o en un caldero puesto al fuego: hervida).",
                                               "Four sips of water. Right-click water to fill it (or a cauldron over a fire: boiled)."),
    "item.evolvedwarfare.canteen.desc.raw": ("Sin hervir: puede tener parásitos. Hiérvela en el horno o en la fogata.",
                                             "Unboiled: it may carry parasites. Boil it in a furnace or on a campfire."),
    "item.evolvedwarfare.canteen.desc.boiled": ("Agua hervida, sin peligro. Al rellenarla en un río vuelve a estar sin hervir.",
                                                "Boiled water, safe. Refilling it from a river makes it unboiled again."),
    "tooltip.evolvedwarfare.canteen.sips": ("Tragos: %s/%s", "Sips: %s/%s"),
    "tooltip.evolvedwarfare.thirst.quench": ("Quita la sed: +%s", "Thirst: +%s"),
    "tooltip.evolvedwarfare.thirst.dry": ("Da sed: -%s", "Makes you thirsty: -%s"),
    "tooltip.evolvedwarfare.thirst.unboiled": ("Sin hervir: puede tener parásitos", "Unboiled: may carry parasites"),
    "message.evolvedwarfare.thirst.full": ("No tienes sed", "You are not thirsty"),
    "message.evolvedwarfare.thirst.parasites": ("Tienes parásitos: el agua no estaba hervida (la leche te cura)",
                                                "You caught parasites: the water was not boiled (milk cures you)"),
    "effect.evolvedwarfare.parasites": ("Parásitos", "Parasites"),
    "death.attack.evolvedwarfare.dehydration": ("%1$s murió de sed", "%1$s died of thirst"),
    "death.attack.evolvedwarfare.dehydration.player": ("%1$s murió de sed huyendo de %2$s", "%1$s died of thirst whilst fleeing %2$s"),
    "death.attack.evolvedwarfare.parasites": ("%1$s murió por los parásitos", "%1$s was killed by parasites"),
    "death.attack.evolvedwarfare.parasites.player": ("%1$s murió por los parásitos huyendo de %2$s", "%1$s was killed by parasites whilst fleeing %2$s"),
    "config.evolvedwarfare.thirstEnabled": ("Sed", "Thirst"),
    "config.evolvedwarfare.thirstEnabled.tip": ("La sed y su medidor encima del hambre: hay que beber agua (mejor hervida).",
                                                "Thirst and its meter above the hunger bar: you have to drink water (preferably boiled)."),
    "config.evolvedwarfare.parasiteChance": ("Parásitos (%)", "Parasites (%)"),
    "config.evolvedwarfare.parasiteChance.tip": ("Probabilidad de coger parásitos al beber agua sin hervir.",
                                                 "Chance of catching parasites when drinking unboiled water."),
}


# ------------------------------------------------------------------ modelos, recetas, daño

WATER_POTION = {"fabric:type": "fabric:components", "base": {"item": "minecraft:potion"},
                "components": {"minecraft:potion_contents": {"potion": "minecraft:water"}}, "strict": False}


def data():
    for item in ("canteen", "water_canteen", "boiled_canteen"):
        write_json(os.path.join(ASSETS, "models/item/%s.json" % item),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "evolvedwarfare:item/%s" % item}})
    write_json(os.path.join(ASSETS, "models/item/boiled_water.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/potion_overlay", "layer1": "minecraft:item/potion",
                                                                   "layer2": "evolvedwarfare:item/boiled_water_steam"}})
    for item in ("melon_juice", "apple_juice", "berry_juice"):
        write_json(os.path.join(ASSETS, "models/item/%s.json" % item),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/potion_overlay", "layer1": "minecraft:item/potion"}})
    rec = os.path.join(DATA, "recipe")
    # hervir: horno (rápido) y fogata (despacio)
    for name, ingredient, result in (("boiled_water", WATER_POTION, "evolvedwarfare:boiled_water"),
                                     ("boiled_canteen", {"item": "evolvedwarfare:water_canteen"}, "evolvedwarfare:boiled_canteen")):
        write_json(os.path.join(rec, name + "_from_smelting.json"),
                   {"type": "minecraft:smelting", "category": "misc", "ingredient": ingredient, "result": {"id": result, "count": 1},
                    "experience": 0.05, "cookingtime": 100})
        write_json(os.path.join(rec, name + "_from_campfire_cooking.json"),
                   {"type": "minecraft:campfire_cooking", "category": "misc", "ingredient": ingredient, "result": {"id": result, "count": 1},
                    "experience": 0.05, "cookingtime": 300})
    write_json(os.path.join(rec, "canteen.json"),
               {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" S ", "LIL", " L "],
                "key": {"S": {"item": "minecraft:string"}, "L": {"item": "minecraft:leather"}, "I": {"item": "minecraft:iron_ingot"}},
                "result": {"id": "evolvedwarfare:canteen", "count": 1}})
    for name, fruit, n in (("melon_juice", "minecraft:melon_slice", 3), ("apple_juice", "minecraft:apple", 2),
                           ("berry_juice", "minecraft:sweet_berries", 4)):
        write_json(os.path.join(rec, name + ".json"),
                   {"type": "minecraft:crafting_shapeless", "category": "misc",
                    "ingredients": [{"item": "minecraft:glass_bottle"}] + [{"item": fruit}] * n,
                    "result": {"id": "evolvedwarfare:" + name, "count": 1}})
    for name in ("dehydration", "parasites"):
        write_json(os.path.join(DATA, "damage_type", name + ".json"),
                   {"exhaustion": 0.0, "message_id": "evolvedwarfare." + name, "scaling": "never"})
    for tag in ("bypasses_armor", "no_knockback"):
        path = os.path.join(MC_DATA, "tags/damage_type", tag + ".json")
        with open(path, encoding="utf-8") as f:
            t = json.load(f)
        for name in ("evolvedwarfare:dehydration", "evolvedwarfare:parasites"):
            if name not in t["values"]:
                t["values"].append(name)
        write_json(path, t)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)


def main():
    drops = meter()
    water = cupped_water()
    icon = parasites_icon()
    canteens = [canteen(k) for k in ("empty", "raw", "boiled")]
    steam()
    data()
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        sheet = Image.new("RGBA", (16 * 12, 16 * 2), (70, 70, 70, 255))
        for i, im in enumerate(drops.values()):
            sheet.alpha_composite(im, (i * 16 + 3, 3))
        for i, im in enumerate(canteens + [water]):
            sheet.alpha_composite(im, (i * 16, 16))
        sheet.alpha_composite(icon.resize((16, 16)), (16 * 6, 16))
        sheet.resize((sheet.width * 6, sheet.height * 6), Image.NEAREST).save(os.path.join(PREVIEW, "thirst.png"))
    print("sed: medidor, agua en las manos, parásitos, cantimploras, vapor, modelos, recetas, daño y textos")


if __name__ == "__main__":
    main()
