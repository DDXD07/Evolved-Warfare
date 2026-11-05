"""La pesca con minijuego (fase 26): los peces nuevos (texturas, recetas para cocinarlos), los cebos, las estrellas de
calidad, el botín de los cofres del minijuego, las etiquetas y los textos.

Uso: python3 tools/ew_fishing.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import math
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
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
MC_DATA = os.path.join(ROOT, "src/main/resources/data/minecraft")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
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


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c) + (255,)


# id: (largo 0-1, alto 0-1, lomo, vientre, aletas, dibujo, cocinado)
FISH = collections.OrderedDict([
    ("perch", (0.8, 0.45, (96, 120, 40), (214, 200, 110), (210, 80, 40), "bars", "cooked_cod")),
    ("carp", (0.85, 0.5, (150, 110, 50), (220, 190, 120), (170, 110, 60), "scales", "cooked_cod")),
    ("trout", (0.9, 0.38, (98, 112, 70), (226, 210, 196), (140, 120, 90), "trout", "cooked_salmon")),
    ("pike", (1.0, 0.3, (70, 100, 50), (210, 214, 170), (120, 110, 60), "spots", "cooked_cod")),
    ("eel", (1.0, 0.18, (60, 56, 40), (120, 110, 70), (50, 46, 30), "none", "cooked_salmon")),
    ("tuna", (0.95, 0.48, (30, 50, 110), (200, 210, 220), (220, 190, 40), "finlets", "cooked_salmon")),
    ("swordfish", (0.8, 0.38, (40, 70, 130), (190, 200, 214), (40, 60, 110), "bill", "cooked_salmon")),
    ("sturgeon", (1.0, 0.3, (110, 110, 100), (200, 196, 180), (90, 90, 82), "plates", "cooked_cod")),
])


def fish_icon(fid):
    length, height, back, belly, fin, pattern, _ = FISH[fid]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    r = random.Random(fid)
    x0, x1 = 1.5, 1.5 + 11.5 * length
    if pattern == "bill":
        x0 = 4.0
    cy = 8.0
    hh = 6.5 * height + 0.6
    outline = shade(back, 0.45)
    for y in range(16):
        for x in range(16):
            # el cuerpo: un huso (más gordo delante)
            u = (x + 0.5 - x0) / max(0.1, (x1 - x0))
            if 0.0 <= u <= 1.0:
                prof = math.sin(math.pi * u) ** 0.6 if u < 0.5 else 1.0 - (u - 0.5) / 0.5 * 0.72
                half = hh * prof
                dy = y + 0.5 - cy
                if abs(dy) <= half:
                    t = (dy + half) / max(0.1, 2 * half)
                    c = back if t < 0.45 else belly
                    c = shade(c, 1.05 - 0.15 * abs(t - 0.4))
                    if abs(abs(dy) - half) < 0.7:
                        c = outline
                    # dibujos
                    if pattern == "bars" and t < 0.6 and int(x) % 3 == 0:
                        c = shade(back, 0.55)
                    elif pattern == "spots" and t < 0.7 and r.random() < 0.18:
                        c = shade(belly, 1.0)
                    elif pattern == "trout" and 0.45 < t < 0.6:
                        c = (210, 110, 120, 255)
                    elif pattern == "trout" and t < 0.45 and r.random() < 0.2:
                        c = (40, 40, 30, 255)
                    elif pattern == "scales" and (x + y) % 2 == 0 and t < 0.8:
                        c = shade(c[:3], 0.88)
                    elif pattern == "plates" and abs(dy + half * 0.55) < 0.6 and x % 2 == 0:
                        c = (220, 216, 200, 255)
                    im.putpixel((x, y), c)
    # la cola
    tx = int(x1) - 1
    for k in range(4):
        for dy in range(-k - 1, k + 2):
            px, py = tx + k, int(cy + dy)
            if 0 <= px < 16 and 0 <= py < 16:
                im.putpixel((px, py), shade(fin, 1.0 - 0.06 * k))
    # aletas (arriba y abajo)
    mid = int((x0 + x1) / 2)
    for k in range(3):
        for (yy, sgn) in ((int(cy - hh) - 1, -1), (int(cy + hh) + 1, 1)):
            px = mid - 1 + k
            if 0 <= yy < 16 and pattern != "eel":
                im.putpixel((px, yy), shade(fin, 0.95))
    if pattern == "finlets":
        for px in range(int(x1) - 4, int(x1)):
            for yy in (int(cy - hh * 0.6) - 1, int(cy + hh * 0.6) + 1):
                if 0 <= yy < 16 and px % 2 == 0:
                    im.putpixel((px, yy), (230, 200, 40, 255))
    if pattern == "bill":
        for px in range(0, int(x0) + 1):
            im.putpixel((px, int(cy) - 1), (70, 80, 100, 255))
    # el ojo
    ex = int(x0 + (x1 - x0) * 0.12) + 1
    ey = int(cy - hh * 0.25)
    if 0 <= ex < 16:
        im.putpixel((ex, ey), (240, 240, 230, 255))
        if ex + 1 < 16:
            im.putpixel((ex + 1, ey), (20, 20, 20, 255))
    return im


def bait_icon(magic):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # un bote de lombrices (o de cebo brillante)
    jar, lid = (150, 110, 70), (90, 60, 40)
    for y in range(6, 14):
        for x in range(4, 12):
            im.putpixel((x, y), shade(jar, 1.0 - 0.03 * (y - 6)))
    for x in range(3, 13):
        im.putpixel((x, 5), lid + (255,))
        im.putpixel((x, 14), shade(lid, 0.8))
    worm = (200, 90, 110) if not magic else (120, 230, 240)
    for (x, y) in ((5, 4), (6, 3), (7, 3), (8, 4), (9, 3), (10, 4), (6, 8), (7, 9), (8, 9), (9, 8)):
        im.putpixel((x, y), worm + (255,))
    if magic:
        for (x, y) in ((3, 2), (12, 1), (13, 6), (2, 9)):
            im.putpixel((x, y), (255, 250, 180, 255))
    return im


def stars():
    im = Image.new("RGBA", (28, 7), (0, 0, 0, 0))
    shape = ["...#...", "...#...", "#######", ".#####.", "..###..", ".##.##.", "##...##"]
    colors = [(205, 127, 50), (216, 220, 228), (244, 196, 48), (154, 240, 240)]
    for k, col in enumerate(colors):
        for y, row in enumerate(shape):
            for x, ch in enumerate(row):
                if ch == "#":
                    edge = y == 0 or x in (0, 6) or y == 6
                    im.putpixel((k * 7 + x, y), shade(col, 0.75 if edge else 1.05))
    return im


NAMES = {
    "perch": ("Perca", "Perch", "Pez de río y lago, fácil de sacar."),
    "carp": ("Carpa", "Carp", "Pez de lago tranquilo, de los más fáciles; las grandes pesan mucho."),
    "trout": ("Trucha", "Trout", "De agua dulce y fría; se mueve a tirones."),
    "pike": ("Lucio", "Pike", "Depredador de río: rápido y traicionero."),
    "eel": ("Anguila", "Eel", "En los pantanos (y de noche, en cualquier agua dulce); se esconde en el fondo."),
    "tuna": ("Atún", "Tuna", "Del mar profundo. Enorme y fuerte."),
    "swordfish": ("Pez espada", "Swordfish", "De los mares cálidos. Rarísimo y muy difícil: salta como loco."),
    "sturgeon": ("Esturión", "Sturgeon", "Gigante de río, raro; tira hacia el fondo."),
}
NAMES_EN = {
    "perch": "A river and lake fish, easy to land.", "carp": "A calm lake fish, one of the easiest; big ones are heavy.",
    "trout": "Cold fresh water; moves in jerks.", "pike": "A river predator: fast and treacherous.",
    "eel": "In swamps (and at night, in any fresh water); hides at the bottom.", "tuna": "From the deep sea. Huge and strong.",
    "swordfish": "Warm seas. Very rare and very hard: jumps like mad.", "sturgeon": "A rare river giant; pulls to the bottom.",
}

TEXTS = {
    "item.evolvedwarfare.bait": ("Cebo", "Bait"),
    "item.evolvedwarfare.bait.desc": ("En la otra mano al pescar: la zona verde es más grande. Se gasta uno por pez.",
                                     "In your off hand while fishing: the green zone is bigger. One is used per fish."),
    "item.evolvedwarfare.magic_bait": ("Cebo mágico", "Magic Bait"),
    "item.evolvedwarfare.magic_bait.desc": ("Como el cebo, pero mejor: zona verde aún más grande, más cofres y atrae a los peces grandes.",
                                           "Like bait, but better: an even bigger green zone, more chests, and it attracts big fish."),
    "quality.evolvedwarfare.bronze": ("Bronce", "Bronze"),
    "quality.evolvedwarfare.silver": ("Plata", "Silver"),
    "quality.evolvedwarfare.gold": ("Oro", "Gold"),
    "quality.evolvedwarfare.platinum": ("Platino", "Platinum"),
    "tooltip.evolvedwarfare.fish.mailbox": ("Solo se vende en el Buzón de ventas", "Can only be sold in the Sales Mailbox"),
    "screen.evolvedwarfare.fishing": ("Pesca", "Fishing"),
    "screen.evolvedwarfare.fishing.hint": ("¡Mantén pulsado!", "Hold to reel!"),
    "screen.evolvedwarfare.fishing.level": ("Pesca nv. %s", "Fishing lv. %s"),
    "screen.evolvedwarfare.fishing.perfect": ("Perfecto", "Perfect"),
    "screen.evolvedwarfare.fishing.caught": ("¡Pescado!", "Caught!"),
    "screen.evolvedwarfare.fishing.lost": ("Se escapó...", "It got away..."),
    "message.evolvedwarfare.fishing.caught": ("%s · %s · %s kg %s", "%s · %s · %s kg %s"),
    "message.evolvedwarfare.fishing.perfect": ("· ¡PERFECTO!", "· PERFECT!"),
    "message.evolvedwarfare.fishing.escaped": ("El pez se ha escapado", "The fish got away"),
    "message.evolvedwarfare.fishing.treasure": ("¡Has sacado un cofre del tesoro!", "You fished up a treasure chest!"),
    "message.evolvedwarfare.fishing.level": ("¡Nivel de pesca %s! La zona verde es más grande.", "Fishing level %s! The green zone is bigger."),
    "config.evolvedwarfare.fishingMinigame": ("Minijuego de pesca", "Fishing minigame"),
    "config.evolvedwarfare.fishingMinigame.tip": ("Al picar un pez sale un minijuego (como en Stardew Valley); los peces tienen peso y calidad.",
                                                 "When a fish bites, a minigame appears (like Stardew Valley); fish have weight and quality."),
}
for _fid, (_es, _en, _desc) in NAMES.items():
    TEXTS["item.%s.%s" % (NS, _fid)] = (_es, _en)
    TEXTS["item.%s.%s.desc" % (NS, _fid)] = (_desc, NAMES_EN[_fid])

CHEST = {"type": "minecraft:fishing", "pools": [
    {"rolls": {"type": "minecraft:uniform", "min": 1, "max": 2}, "bonus_rolls": 0,
     "entries": [{"type": "minecraft:loot_table", "value": "minecraft:gameplay/fishing/treasure", "weight": 1}]},
    {"rolls": {"type": "minecraft:uniform", "min": 1, "max": 3}, "bonus_rolls": 1, "entries": [
        {"type": "minecraft:item", "name": "minecraft:gold_ingot", "weight": 10,
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 4}, "add": False}]},
        {"type": "minecraft:item", "name": "minecraft:emerald", "weight": 8,
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 5}, "add": False}]},
        {"type": "minecraft:item", "name": "minecraft:diamond", "weight": 3},
        {"type": "minecraft:item", "name": "minecraft:prismarine_shard", "weight": 8,
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 6}, "add": False}]},
        {"type": "minecraft:item", "name": "minecraft:heart_of_the_sea", "weight": 1},
        {"type": "minecraft:item", "name": "%s:magic_bait" % NS, "weight": 6,
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 5}, "add": False}]},
        {"type": "minecraft:item", "name": "%s:glory_coin" % NS, "weight": 6,
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}, "add": False}]}]}]}


def main():
    tex = os.path.join(ASSETS, "textures/item")
    os.makedirs(tex, exist_ok=True)
    icons = []
    for fid in FISH:
        im = fish_icon(fid)
        im.save(os.path.join(tex, fid + ".png"))
        icons.append(im)
        write_json(os.path.join(ASSETS, "models/item/%s.json" % fid), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, fid)}})
        cooked = "minecraft:" + FISH[fid][6]
        for kind, time in (("smelting", 200), ("smoking", 100), ("campfire_cooking", 600)):
            write_json(os.path.join(DATA, "recipe/%s_from_%s.json" % (FISH[fid][6] + "_" + fid, kind)),
                       {"type": "minecraft:" + kind, "category": "food", "ingredient": {"item": "%s:%s" % (NS, fid)},
                        "result": {"id": cooked, "count": 2 if fid in ("tuna", "swordfish", "sturgeon", "pike", "carp") else 1},
                        "experience": 0.35, "cookingtime": time})
    for name, magic in (("bait", False), ("magic_bait", True)):
        im = bait_icon(magic)
        im.save(os.path.join(tex, name + ".png"))
        icons.append(im)
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    write_json(os.path.join(DATA, "recipe/bait.json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [[{"item": "minecraft:rotten_flesh"}, {"item": "minecraft:spider_eye"}, {"item": "minecraft:cod"}],
                        [{"item": "minecraft:wheat_seeds"}, {"item": "minecraft:bread"}]],
        "result": {"id": "%s:bait" % NS, "count": 4}})
    write_json(os.path.join(DATA, "recipe/magic_bait.json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "%s:bait" % NS}, {"item": "%s:bait" % NS}, {"item": "minecraft:glowstone_dust"}, {"item": "minecraft:prismarine_crystals"}],
        "result": {"id": "%s:magic_bait" % NS, "count": 2}})
    gui = os.path.join(ASSETS, "textures/gui")
    os.makedirs(gui, exist_ok=True)
    stars().save(os.path.join(gui, "fish_stars.png"))
    add_tag(os.path.join(MC_DATA, "tags/item/fishes.json"), ["%s:%s" % (NS, f) for f in FISH])
    write_json(os.path.join(DATA, "loot_table/gameplay/fishing_chest.json"), CHEST)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        sheet = Image.new("RGBA", (18 * len(icons), 18), (139, 139, 139, 255))
        for k, im in enumerate(icons):
            sheet.alpha_composite(im, (18 * k + 1, 1))
        sheet = sheet.resize((sheet.width * 6, sheet.height * 6), Image.NEAREST)
        sheet.alpha_composite(stars().resize((28 * 6, 7 * 6), Image.NEAREST), (0, 0))
        sheet.save(os.path.join(PREVIEW, "fishing.png"))
    print("pesca: %d peces nuevos, cebos, estrellas, botín y textos" % len(FISH))


if __name__ == "__main__":
    main()
