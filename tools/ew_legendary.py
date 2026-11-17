"""Las criaturas legendarias (fase 101 en adelante), al estilo de Ice and Fire de RLCraft.

Tanda 1: los dragones de fuego y de hielo (el mismo cuerpo: cuatro patas, cuello largo, cuernos, mandíbula, dos alas de
dos tramos, cola de tres trozos con púas y espinas en el lomo), sus escamas y huesos, su botín y los textos. Los modelos
van a assets/evolvedwarfare/hell/<id>.json (los anima BossModel según el papel de cada parte).

Uso: python3 tools/ew_legendary.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ew_wildlife as W  # noqa: E402
import ew_demons as D  # noqa: E402
from ew_model3d import render_entity  # noqa: E402

PREVIEW = W.PREVIEW
ROOT = W.ROOT
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
C = W.C
write_json = D.write_json


def n(name):
    return "%s:%s" % (NS, name)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


# ------------------------------------------------------------------ los dragones

DRAGONS = {
    # escamas, vientre, membrana de las alas, cuernos, ojos
    "fire_dragon": ((150, 34, 26), (214, 150, 66), (96, 22, 20), (60, 44, 34), (255, 190, 40)),
    "ice_dragon": ((132, 178, 214), (232, 240, 248), (86, 120, 160), (226, 236, 244), (120, 220, 255)),
}


def dragon(mid):
    scale_, belly, membrane, horn, eye = DRAGONS[mid]
    dark = shade(scale_, 0.7)
    a = W.Animal(mid)
    # las patas (delante y detrás), con garras
    for side, z, phase in ((-1, -7, 0), (1, -7, 1), (-1, 8, 1), (1, 8, 0)):
        leg = a.part("leg_%d_%d" % (side + 1, z + 10), None, (side * 5.5, 12, z), role="leg", phase=phase)
        leg.cube((-2, 0, -2), (4, 9, 4), **C(scale_, low=(dark, 2)))
        leg.cube((-2.5, 9, -3.5), (5, 3, 5), **C(dark, tip=(horn, 1)))
    body = a.part("body", None, (0, 12, 0), role="body")
    body.cube((-6, -10, -12), (12, 10, 24), **C(scale_, belly=(belly, 3), spots=(dark, 0.12)))
    body.cube((-5, -11, -10), (10, 1, 20), **C(dark))
    for k in range(5):
        body.cube((-0.5, -13, -9 + k * 4.5), (1, 3, 2), **C(horn))
    # el cuello y la cabeza (mira a -z)
    neck = a.part("neck", "body", (0, -7, -12), (-30, 0, 0))
    neck.cube((-3, -3, -11), (6, 6, 11), **C(scale_, belly=(belly, 2)))
    neck.cube((-0.5, -5, -9), (1, 2, 2), **C(horn))
    neck.cube((-0.5, -5, -5), (1, 2, 2), **C(horn))
    head = a.part("head", "neck", (0, 0, -11), (30, 0, 0), role="head")
    head.cube((-4, -4, -7), (8, 7, 7), **C(scale_, eyes=("side", 2, eye), glow_eyes=True))
    head.cube((-3, -2, -14), (6, 4, 7), **C(scale_, nose=dark, front_color=dark))
    for side in (-1, 1):
        head.cube((side * 2.5 - 0.5, -7, -2), (1, 4, 1), **C(horn, tip=(shade(horn, 1.3), 1)))
        head.cube((side * 3 - 0.5, -6, 0), (1, 1, 3), **C(horn))
    jaw = a.part("jaw", "head", (0, 2, -7), role="jaw")
    jaw.cube((-2.5, 0, -7), (5, 2, 7), **C(belly, top=(150, 40, 40)))
    # la cola: tres trozos, cada vez más fina, con púas y la punta en flecha
    t1 = a.part("tail_1", "body", (0, -6, 12), (-12, 0, 0), role="tail")
    t1.cube((-3, -3, 0), (6, 6, 12), **C(scale_, belly=(belly, 2)))
    t1.cube((-0.5, -5, 3), (1, 2, 2), **C(horn))
    t1.cube((-0.5, -5, 8), (1, 2, 2), **C(horn))
    t2 = a.part("tail_2", "tail_1", (0, 0, 12), (8, 0, 0), role="tail")
    t2.cube((-2, -2, 0), (4, 4, 12), **C(scale_, belly=(belly, 1)))
    t2.cube((-0.5, -4, 5), (1, 2, 2), **C(horn))
    t3 = a.part("tail_3", "tail_2", (0, 0, 12), (6, 0, 0), role="tail")
    t3.cube((-1, -1, 0), (2, 2, 10), **C(scale_))
    t3.cube((-3, -0.5, 8), (6, 1, 4), **C(dark))
    # las alas: un brazo con la membrana y la punta (dos tramos que baten)
    for side in (-1, 1):
        sname = "l" if side > 0 else "r"
        wing = a.part("wing_" + sname, "body", (side * 6, -9, -7), (0, 0, side * -10), role="wing", side=side)
        if side > 0:
            wing.cube((0, -1, -1), (16, 2, 2), **C(scale_))
            wing.cube((0, 0, 0), (16, 1, 14), **C(membrane, bands=(shade(membrane, 0.8), 3)))
        else:
            wing.cube((-16, -1, -1), (16, 2, 2), **C(scale_))
            wing.cube((-16, 0, 0), (16, 1, 14), **C(membrane, bands=(shade(membrane, 0.8), 3)))
        tip = a.part("wingtip_" + sname, "wing_" + sname, (side * 16, 0, 0), (0, 0, side * 12), role="wing", side=side)
        if side > 0:
            tip.cube((0, -1, -1), (14, 2, 2), **C(scale_))
            tip.cube((0, 0, 0), (14, 1, 10), **C(membrane, bands=(shade(membrane, 0.8), 3)))
            tip.cube((13, -2, -2), (2, 2, 2), **C(horn))
        else:
            tip.cube((-14, -1, -1), (14, 2, 2), **C(scale_))
            tip.cube((-14, 0, 0), (14, 1, 10), **C(membrane, bands=(shade(membrane, 0.8), 3)))
            tip.cube((-15, -2, -2), (2, 2, 2), **C(horn))
    return a


MODELS = collections.OrderedDict([("fire_dragon", lambda: dragon("fire_dragon")), ("ice_dragon", lambda: dragon("ice_dragon"))])

NAMES = {
    "fire_dragon": ("Dragón de fuego", "Fire Dragon"),
    "ice_dragon": ("Dragón de hielo", "Ice Dragon"),
}


# ------------------------------------------------------------------ el botín

def count(item, lo, hi, chance=None):
    entry = {"type": "minecraft:item", "name": item, "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False}]}
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [entry]}
    if chance:
        pool["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return pool


LOOT = {
    "fire_dragon": [count(n("fire_dragon_scale"), 10, 18), count(n("dragon_bone"), 5, 10), count(n("fire_dragon_heart"), 1, 1),
                    count(n("heart_crystal"), 1, 1), count("minecraft:gold_ingot", 8, 20), count("minecraft:diamond", 2, 5)],
    "ice_dragon": [count(n("ice_dragon_scale"), 10, 18), count(n("dragon_bone"), 5, 10), count(n("ice_dragon_heart"), 1, 1),
                   count(n("heart_crystal"), 1, 1), count("minecraft:gold_ingot", 8, 20), count("minecraft:diamond", 2, 5)],
}

ITEMS = {
    "fire_dragon_scale": ("Escama de dragón de fuego", "Fire Dragon Scale",
                          "Dura como el acero y no se quema. Para la armadura de escamas (más adelante).",
                          "Hard as steel and fireproof. For scale armour (coming later)."),
    "ice_dragon_scale": ("Escama de dragón de hielo", "Ice Dragon Scale",
                         "Fría al tacto, nunca se derrite. Para la armadura de escamas (más adelante).",
                         "Cold to the touch, it never melts. For scale armour (coming later)."),
    "dragon_bone": ("Hueso de dragón", "Dragon Bone", "Más ligero y duro que el hierro. Para las armas de dragón (más adelante).",
                    "Lighter and harder than iron. For dragonbone weapons (coming later)."),
    "fire_dragon_heart": ("Corazón de dragón de fuego", "Fire Dragon Heart", "Aún late, caliente. Un trofeo... y algo más (más adelante).",
                          "Still beating, and hot. A trophy... and more (coming later)."),
    "ice_dragon_heart": ("Corazón de dragón de hielo", "Ice Dragon Heart", "Aún late, helado. Un trofeo... y algo más (más adelante).",
                         "Still beating, and freezing. A trophy... and more (coming later)."),
}

TEXTS = {
    "itemGroup.evolvedwarfare.legendary": ("Evolved Warfare: Legendarios", "Evolved Warfare: Legendary"),
    "message.evolvedwarfare.legendary.roar.fire_dragon": ("Un rugido estremece la tierra y el cielo se tiñe de rojo: un dragón de fuego anda cerca...",
                                                          "A roar shakes the earth and the sky turns red: a fire dragon is near..."),
    "message.evolvedwarfare.legendary.roar.ice_dragon": ("Un rugido helado corta el aire: un dragón de hielo anda cerca...",
                                                         "A freezing roar cuts the air: an ice dragon is near..."),
    "message.evolvedwarfare.legendary.slain": ("¡%s ha caído! Su leyenda será contada.", "%s has fallen! Its legend will be told."),
    "death.attack.evolvedwarfare.dragon_fire": ("%1$s fue calcinado por el aliento de un dragón", "%1$s was burnt to ashes by dragon breath"),
    "death.attack.evolvedwarfare.dragon_fire.player": ("%1$s fue calcinado por %2$s", "%1$s was burnt to ashes by %2$s"),
    "death.attack.evolvedwarfare.dragon_ice": ("%1$s se congeló con el aliento de un dragón", "%1$s was frozen solid by dragon breath"),
    "death.attack.evolvedwarfare.dragon_ice.player": ("%1$s fue congelado por %2$s", "%1$s was frozen solid by %2$s"),
    "config.evolvedwarfare.category.legendary": ("Criaturas legendarias", "Legendary creatures"),
    "config.evolvedwarfare.legendaryEnabled": ("Criaturas legendarias", "Legendary creatures"),
    "config.evolvedwarfare.legendaryEnabled.tip": ("Dragones (y más adelante otras) que aparecen lejos y en las zonas peligrosas.",
                                                   "Dragons (and more later) that appear far away and in dangerous areas."),
    "config.evolvedwarfare.legendaryMinLevel": ("Peligro mínimo", "Minimum danger"),
    "config.evolvedwarfare.legendaryMinLevel.tip": ("Desde qué nivel de peligro pueden aparecer.", "From which danger level they can appear."),
    "config.evolvedwarfare.legendaryChance": ("Probabilidad por minuto", "Chance per minute"),
    "config.evolvedwarfare.legendaryChance.tip": ("Probabilidad, cada minuto y por jugador, de que aparezca una cerca (si el sitio vale).",
                                                  "Chance per player each minute that one appears nearby (if the place fits)."),
    "config.evolvedwarfare.dragonGriefing": ("El aliento quema y congela", "Breath burns and freezes"),
    "config.evolvedwarfare.dragonGriefing.tip": ("El aliento de fuego prende fuego y el de hielo congela el agua y nieva (con mobGriefing).",
                                                 "Fire breath starts fires and ice breath freezes water and snows (with mobGriefing)."),
}


# ------------------------------------------------------------------ los iconos

def scale_icon(color):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dark = shade(color, 0.6)
    light = shade(color, 1.3)
    for y in range(2, 15):
        half = int(6 * min(1.0, (y - 1) / 6.0)) if y < 8 else int(6 - (y - 8) * 0.85)
        for x in range(8 - half, 8 + half):
            c = color
            if x == 8 - half or x == 8 + half - 1 or y == 14:
                c = dark
            elif (x + y) % 5 == 0:
                c = light
            img.putpixel((x, y), c + (255,))
    for y in range(4, 12):
        img.putpixel((8, y), shade(color, 0.8) + (255,))
    return img


def bone_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    bone, dark = (226, 218, 196), (150, 140, 120)
    for k in range(3, 13):
        img.putpixel((k, 15 - k), bone + (255,))
        img.putpixel((k + 1, 15 - k), bone + (255,))
        img.putpixel((k, 16 - k), dark + (255,))
    for cx, cy in ((3, 12), (2, 11), (4, 13), (12, 3), (13, 4), (11, 2)):
        for dx in (0, 1):
            for dy in (0, 1):
                img.putpixel((cx + dx - 1, cy + dy - 1), bone + (255,))
    return img


def heart_icon(color, glow):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rows = ["..##...##..", ".#oo#.#oo#.", "#oglo#ooooo#"[:11], "#ooooooooo#", "#ooooooooo#", ".#ooooooo#.", "..#ooooo#..", "...#ooo#...",
            "....#o#....", ".....#....."]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            c = {"#": shade(color, 0.45), "o": color, "g": glow, "l": shade(glow, 1.2)}.get(ch)
            if c:
                img.putpixel((x + 2, y + 3), c + (255,))
    return img


def main():
    tex_dir = os.path.join(ASSETS, "textures/entity/hell")
    os.makedirs(tex_dir, exist_ok=True)
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
    item_dir = os.path.join(ASSETS, "textures/item")
    scale_icon(DRAGONS["fire_dragon"][0]).save(os.path.join(item_dir, "fire_dragon_scale.png"))
    scale_icon(DRAGONS["ice_dragon"][0]).save(os.path.join(item_dir, "ice_dragon_scale.png"))
    bone_icon().save(os.path.join(item_dir, "dragon_bone.png"))
    heart_icon((150, 30, 30), (255, 160, 40)).save(os.path.join(item_dir, "fire_dragon_heart.png"))
    heart_icon((70, 120, 170), (180, 240, 255)).save(os.path.join(item_dir, "ice_dragon_heart.png"))
    for name in ITEMS:
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": n("item/" + name)}})
    for dt in ("dragon_fire", "dragon_ice"):
        write_json(os.path.join(DATA, "damage_type/%s.json" % dt), {"exhaustion": 0.1, "message_id": "%s.%s" % (NS, dt), "scaling": "never"})
    D.add_tag(os.path.join(ROOT, "src/main/resources/data/minecraft/tags/damage_type/is_fire.json"), [n("dragon_fire")])
    D.add_tag(os.path.join(ROOT, "src/main/resources/data/minecraft/tags/damage_type/is_freezing.json"), [n("dragon_ice")])
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in NAMES.items():
            data["entity.%s.%s" % (NS, key)] = en if english else es
            data["item.%s.%s_spawn_egg" % (NS, key)] = ("%s Spawn Egg" % en) if english else ("Huevo generador de %s" % es.lower())
        for key, (es, en, des, den) in ITEMS.items():
            data["item.%s.%s" % (NS, key)] = en if english else es
            data["item.%s.%s.desc" % (NS, key)] = den if english else des
        for key, (es, en) in TEXTS.items():
            data[key] = en if english else es
        write_json(path, data)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        for mid, parts, img in previews:
            for yaw in (35, 150):
                render_entity(parts, img, size=420, scale=3.0, yaw=yaw, pitch=20, center=(0.0, 8.0, 0.0)).save(
                    os.path.join(PREVIEW, "%s_%d.png" % (mid, yaw)))
    print("legendarios: %d modelos" % len(MODELS))


if __name__ == "__main__":
    main()
