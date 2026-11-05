"""Los mini jefes demoníacos del Nether bíblico (fase 24) y la Bendición Divina.

Cuatro demonios, cada uno más grande, más feo y más difícil: Legión (I), Belcebú, Señor de las Moscas (II), Moloc (III)
y Abadón, el Destructor (IV). Sus modelos (assets/evolvedwarfare/hell/<id>.json, con el papel de cada parte para
animarla), sus texturas (y la capa que brilla: ojos, el horno del pecho), sonidos, botín, huevos y textos; y el
encantamiento Bendición Divina (la única forma de herir a un demonio), con sus etiquetas.

Uso: python3 tools/ew_demons.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ew_wildlife as W  # noqa: E402
from ew_model3d import render_entity  # noqa: E402

PREVIEW = W.PREVIEW
YAW = float(os.environ.get("EW_YAW", "35"))
ROOT = W.ROOT
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
MC_DATA = os.path.join(ROOT, "src/main/resources/data/minecraft")
C = W.C


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


# ------------------------------------------------------------------ los modelos

FLESH = (150, 92, 86)
ROT = (96, 46, 44)
PALE = (206, 176, 160)
EYE_RED = (220, 30, 20)
EYE_GOLD = (255, 200, 40)
BONE = (222, 212, 182)
BONE_D = (150, 138, 112)


def legion():
    """Legión: una masa encorvada de carne podrida llena de caras, con brazos que llegan al suelo y una segunda cabeza."""
    a = W.Animal("legion")
    for side, name, phase in ((-1, "leg_r", 0), (1, "leg_l", 1)):
        a.part(name, None, (side * 3.5, 10, 0), role="leg", phase=phase).cube((-2.5, 0, -2.5), (5, 14, 5), **C(ROT, low=((50, 30, 28), 2)))
    body = a.part("body", None, (0, 10, 0), (22, 0, 0), role="body")
    body.cube((-6, -14, -4), (12, 14, 8), **C(FLESH, spots=(ROT, 0.18)))
    # caras que asoman de la carne
    for (x, y, z, s) in ((-5, -12, -6, 4), (1, -9, -6, 4), (-3, -5, -6, 3), (2, -13, 3, 3), (-5, -6, 3, 4)):
        body.cube((x, y, z), (s, s, 2), **C(PALE, eyes=("front", 1, (20, 10, 10)), mouth=(40, 10, 10)))
    head = a.part("head", "body", (0, -14, -1), (-22, 0, 0), role="head")
    head.cube((-4, -7, -4), (8, 7, 8), **C(PALE, spots=(ROT, 0.1), eyes=("front", 3, EYE_RED), glow_eyes=True))
    a.part("jaw", "head", (0, 0, -1), role="jaw").cube((-3, 0, -3), (6, 2, 6), **C(ROT, mouth=(30, 8, 8)))
    h2 = a.part("head2", "body", (-6, -13, 0), (0, 20, -25), role="head")
    h2.cube((-2.5, -5, -2.5), (5, 5, 5), **C(PALE, spots=(ROT, 0.15), eyes=("front", 2, EYE_RED), glow_eyes=True))
    for side, name in ((-1, "arm_r"), (1, "arm_l")):
        a.part(name, "body", (side * 7.5, -12, 0), (-22, 0, 0), role="arm", side=side).cube((-2, -1, -2), (4, 19, 4),
                                                                                          **C(FLESH, low=(ROT, 3), spots=(ROT, 0.12)))
    return a


def beelzebub():
    """Belcebú: una mosca hinchada del tamaño de un hombre: ojos compuestos rojos, trompa, cuatro brazos y alas."""
    a = W.Animal("beelzebub")
    chitin = (42, 54, 32)
    body = a.part("body", None, (0, 6, 0), role="body")
    body.cube((-7, -4, -6), (14, 13, 12), **C(chitin, bands=((150, 120, 40), 2), spots=((20, 26, 16), 0.06)))
    body.cube((-5, -11, -4), (10, 7, 8), **C(chitin, spots=((90, 110, 60), 0.08)))
    head = a.part("head", "body", (0, -11, -1), role="head")
    head.cube((-4, -7, -5), (8, 7, 8), **C((30, 30, 26), spots=((70, 70, 60), 0.05)))
    for x in (-6, 2):
        head.cube((x, -7, -6), (4, 5, 4), **C((150, 16, 16), spots=((90, 6, 6), 0.35), glow_all=True))
    a.part("proboscis", "head", (0, -2, -5), (30, 0, 0), role="jaw").cube((-1, 0, -1), (2, 6, 2), **C((60, 40, 30), low=((120, 40, 40), 1)))
    for side, name in ((-1, "wing_r"), (1, "wing_l")):
        o = (0, 0, 0) if side > 0 else (-15, 0, 0)
        a.part(name, "body", (side * 3, -10, 4), (0, side * 20, 0), role="wing", side=side, flap="roll").cube(
            o, (15, 0, 10), **C((178, 182, 170), spots=((120, 124, 110), 0.2)))
    for side in (-1, 1):
        for k, (y, z) in enumerate(((-9, -2), (-3, -4))):
            a.part("arm_%d_%d" % (side + 1, k), "body", (side * 6, y, z), (-30, 0, side * -20), role="arm", side=side).cube(
                (-1, 0, -1), (2, 11, 2), **C(chitin, low=((20, 20, 16), 2)))
        a.part("leg_%d" % (side + 1), "body", (side * 3, 9, 0), role="leg", phase=0 if side < 0 else 1).cube(
            (-1, 0, -1), (2, 10, 2), **C(chitin, low=((20, 20, 16), 2)))
    return a


def moloch():
    """Moloc: un gigante con cabeza de toro, cuernos enormes, pezuñas y un horno encendido en el pecho."""
    a = W.Animal("moloch")
    skin = (104, 58, 40)
    fur = (58, 34, 26)
    for side, name, phase in ((-1, "leg_r", 0), (1, "leg_l", 1)):
        a.part(name, None, (side * 5, -4, 0), role="leg", phase=phase).cube((-4, 0, -4), (8, 28, 8),
                                                                           **C(fur, low=((26, 22, 20), 4), spots=((40, 24, 18), 0.1)))
    body = a.part("body", None, (0, -4, 0), role="body")
    body.cube((-10, -24, -6), (20, 24, 12), **C(skin, furnace=True, spots=((70, 36, 24), 0.08)))
    head = a.part("head", "body", (0, -24, -2), role="head")
    head.cube((-6, -10, -7), (12, 10, 11), **C(skin, top=fur, eyes=("front", 4, (255, 120, 20)), glow_eyes=True))
    head.cube((-4, -5, -12), (8, 6, 5), **C((80, 44, 32), nose=(20, 14, 12)))
    head.cube((-1, -1, -13), (2, 1, 1), **C((200, 170, 60)))  # la argolla de la nariz
    for side in (-1, 1):
        o = (0, -2, -2) if side > 0 else (-9, -2, -2)
        horn = a.part("horn_%d" % (side + 1), "head", (side * 6, -8, -2), (0, 0, side * -25), role="horn")
        horn.cube(o, (9, 4, 4), **C((214, 200, 168), tip=((40, 34, 30), 2)))
        a.part("horn_tip_%d" % (side + 1), "horn_%d" % (side + 1), (side * 8.5, -1, 0), (0, 0, side * -60), role="horn").cube(
            (-1.5, -7, -1.5), (3, 7, 3), **C((60, 50, 44), high=((20, 18, 16), 2)))
        a.part("arm_%d" % (side + 1), "body", (side * 13, -22, 0), role="arm", side=side).cube((-3, -1, -3), (6, 24, 6),
                                                                                             **C(skin, low=((60, 32, 22), 4)))
    a.part("tail", "body", (0, -2, 6), (-40, 0, 0), role="tail").cube((-1, -1, 0), (2, 2, 12), **C(fur, tip=((30, 20, 16), 3)))
    return a


def abaddon():
    """Abadón: un esqueleto gigante coronado, con alas de murciélago desgarradas, garras largas y cola de escorpión."""
    a = W.Animal("abaddon")
    for side, name, phase in ((-1, "leg_r", 0), (1, "leg_l", 1)):
        leg = a.part(name, None, (side * 5, -6, 0), role="leg", phase=phase)
        leg.cube((-2, 0, -2), (4, 30, 4), **C(BONE, bands=(BONE_D, 6)))
    body = a.part("body", None, (0, -6, 0), role="body")
    body.cube((-6, -4, -4), (12, 5, 8), **C(BONE_D))                      # pelvis
    body.cube((-2, -26, 0), (4, 26, 4), **C(BONE_D, bands=(BONE, 3)))       # espinazo
    body.cube((-9, -26, -5), (18, 14, 10), **C(BONE, bands=((40, 20, 20), 2)))  # costillas
    head = a.part("head", "body", (0, -28, -1), role="head")
    head.cube((-5, -10, -6), (10, 10, 10), **C(BONE, eyes=("front", 4, EYE_RED), glow_eyes=True, mouth=(30, 10, 10)))
    head.cube((-6, -13, -6), (12, 3, 10), **C((210, 170, 50), bands=((150, 110, 30), 1)))  # la corona
    for x in (-6, -2, 2, 5):
        head.cube((x, -16, -6), (1, 3, 1), **C((230, 190, 60)))
    a.part("jaw", "head", (0, 0, -1), role="jaw").cube((-4, 0, -5), (8, 3, 8), **C(BONE_D))
    for side in (-1, 1):
        o = (0, -14, 0) if side > 0 else (-28, -14, 0)
        a.part("wing_%d" % (side + 1), "body", (side * 6, -22, 5), (0, side * -30, 0), role="wing", side=side, flap="yaw").cube(
            o, (28, 24, 0), **C((70, 18, 18), spots=((30, 8, 8), 0.25), bands=((150, 138, 112), 6)))
        a.part("arm_%d" % (side + 1), "body", (side * 11, -24, 0), role="arm", side=side).cube((-2, -1, -2), (4, 30, 4),
                                                                                             **C(BONE, low=((40, 30, 26), 5)))
    t1 = a.part("tail1", "body", (0, -2, 4), (35, 0, 0), role="tail")
    t1.cube((-2, -2, 0), (4, 4, 12), **C(BONE, rings=(BONE_D, 2)))
    t2 = a.part("tail2", "tail1", (0, 0, 12), (50, 0, 0), role="tail")
    t2.cube((-1.5, -1.5, 0), (3, 3, 10), **C(BONE, rings=(BONE_D, 2)))
    a.part("stinger", "tail2", (0, 0, 10), (50, 0, 0), role="tail").cube((-1, -1, 0), (2, 2, 6), **C((40, 20, 30), tip=((150, 20, 20), 2)))
    return a


BOSSES = collections.OrderedDict([("legion", legion), ("beelzebub", beelzebub), ("moloch", moloch), ("abaddon", abaddon)])


def paint_extra(img, glow, c, rng):
    """Lo que brilla: los ojos de la cara (o toda la caja), y el horno del pecho de Moloc."""
    p = c.paint
    reg = W.regions(c)
    if p.get("glow_all"):
        for name, (rx, ry, w, h) in reg.items():
            for yy in range(h):
                for xx in range(w):
                    if 0 <= rx + xx < img.width and 0 <= ry + yy < img.height:
                        glow.putpixel((rx + xx, ry + yy), img.getpixel((rx + xx, ry + yy)))
    if p.get("glow_eyes") and p.get("eyes"):
        _, row, col = p["eyes"]
        rx, ry, w, h = reg["front"]
        for x in (rx + max(0, w // 2 - 2), rx + min(w - 1, (w + 1) // 2 + 1)):
            glow.putpixel((x, ry + row), tuple(col) + (255,))
    if p.get("furnace"):
        rx, ry, w, h = reg["front"]
        x0, y0, x1, y1 = rx + w // 2 - 5, ry + 6, rx + w // 2 + 5, ry + 15
        for y in range(y0, y1):
            for x in range(x0, x1):
                bar = (x - x0) % 3 == 0 or y in (y0, y1 - 1)
                if bar:
                    col = (34, 30, 30, 255)
                    img.putpixel((x, y), col)
                else:
                    f = rng.uniform(0.75, 1.0)
                    col = (255, int(140 * f), int(30 * f), 255)
                    img.putpixel((x, y), col)
                    glow.putpixel((x, y), col)


# ------------------------------------------------------------------ datos

NAMES = {
    "legion": ("Legión", "Legion"),
    "beelzebub": ("Belcebú, Señor de las Moscas", "Beelzebub, Lord of the Flies"),
    "moloch": ("Moloc", "Moloch"),
    "abaddon": ("Abadón, el Destructor", "Abaddon, the Destroyer"),
}

SOUNDS = {
    "legion": {"ambient": [("minecraft:mob/zombie/say1", 0.5), ("minecraft:mob/zombie/say2", 0.45), ("minecraft:mob/ghast/moan3", 0.5)],
               "hurt": [("minecraft:mob/zombie/hurt1", 0.5), ("minecraft:mob/zombie/hurt2", 0.5)], "death": [("minecraft:mob/ghast/death", 0.45)]},
    "beelzebub": {"ambient": [("minecraft:mob/bee/aggressive1", 0.5), ("minecraft:mob/bee/aggressive2", 0.45), ("minecraft:mob/silverfish/say1", 0.4)],
                  "hurt": [("minecraft:mob/silverfish/hit1", 0.45), ("minecraft:mob/silverfish/hit2", 0.45)],
                  "death": [("minecraft:mob/silverfish/kill", 0.4)]},
    "moloch": {"ambient": [("minecraft:mob/ravager/roar1", 0.55), ("minecraft:mob/ravager/roar2", 0.5), ("minecraft:mob/hoglin/angry1", 0.45)],
               "hurt": [("minecraft:mob/ravager/hurt1", 0.5), ("minecraft:mob/ravager/hurt2", 0.5)],
               "death": [("minecraft:mob/ravager/death1", 0.45)]},
    "abaddon": {"ambient": [("minecraft:mob/wither/idle1", 0.45), ("minecraft:mob/wither/idle2", 0.45), ("minecraft:mob/ghast/scream1", 0.4)],
                "hurt": [("minecraft:mob/wither/hurt1", 0.45), ("minecraft:mob/wither/hurt2", 0.45)],
                "death": [("minecraft:mob/wither/death", 0.4)]},
}


def count(item, lo, hi, chance=None):
    entry = {"type": "minecraft:item", "name": item, "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False}]}
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [entry]}
    if chance:
        pool["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return pool


def blessed_book(chance):
    return {"rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:random_chance", "chance": chance}],
            "entries": [{"type": "minecraft:item", "name": "minecraft:book", "functions": [
                {"function": "minecraft:set_enchantments", "enchantments": {"%s:divine_blessing" % NS: 1}}]}]}


LOOT = {
    "legion": [count("minecraft:blaze_rod", 2, 4), count("minecraft:gold_ingot", 2, 5), count("%s:brimstone" % NS, 2, 5), blessed_book(0.15)],
    "beelzebub": [count("minecraft:blaze_rod", 3, 6), count("minecraft:diamond", 1, 2), count("minecraft:ghast_tear", 1, 3), blessed_book(0.3)],
    "moloch": [count("minecraft:blaze_rod", 4, 8), count("minecraft:diamond", 2, 4), count("minecraft:netherite_scrap", 1, 2), blessed_book(0.5)],
    "abaddon": [count("minecraft:netherite_ingot", 1, 2), count("minecraft:diamond", 4, 8), count("minecraft:nether_star", 1, 1, 0.5),
                blessed_book(1.0)],
}

ENCHANTMENT = {
    "description": {"translate": "enchantment.%s.divine_blessing" % NS},
    "supported_items": "#%s:enchantable/blessable" % NS,
    "weight": 3, "max_level": 1,
    "min_cost": {"base": 14, "per_level_above_first": 0}, "max_cost": {"base": 60, "per_level_above_first": 0},
    "anvil_cost": 4, "slots": ["mainhand"],
    "effects": {"minecraft:damage": [{"effect": {"type": "minecraft:add", "value": 4.0},
                                      "requirements": {"condition": "minecraft:entity_properties", "entity": "this",
                                                       "predicate": {"type": "#%s:demons" % NS}}}]},
}

TEXTS = {
    "enchantment.evolvedwarfare.divine_blessing": ("Bendición Divina", "Divine Blessing"),
    "message.evolvedwarfare.demon.unharmed": ("Tu arma no le hace nada: solo un arma con Bendición Divina hiere a un demonio",
                                             "Your weapon does nothing: only a weapon with Divine Blessing can harm a demon"),
    "boss.evolvedwarfare.tier": ("%s · Mini jefe %s", "%s · Mini boss %s"),
    "message.evolvedwarfare.legion.split": ("«Mi nombre es Legión, porque somos muchos»", "\"My name is Legion, for we are many\""),
    "message.evolvedwarfare.abaddon.summon": ("Abadón llama a sus siervos", "Abaddon calls his servants"),
}
for _id, (_es, _en) in NAMES.items():
    TEXTS["entity.%s.%s" % (NS, _id)] = (_es, _en)
    TEXTS["item.%s.%s_spawn_egg" % (NS, _id)] = ("Huevo de " + _es.split(",")[0], _en.split(",")[0] + " Spawn Egg")
    for _k in ("ambient", "hurt", "death"):
        TEXTS["subtitles.%s.entity.%s.%s" % (NS, _id, _k)] = (
            {"ambient": "%s ruge", "hurt": "%s se duele", "death": "%s cae"}[_k] % _es.split(",")[0],
            {"ambient": "%s roars", "hurt": "%s hurts", "death": "%s falls"}[_k] % _en.split(",")[0])


def main():
    previews = []
    for bid, gen in BOSSES.items():
        a = gen()
        tw, th = a.pack()
        img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        glow = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        rng = random.Random(bid)
        for p in a.parts:
            for c in p.cubes:
                W.paint_cube(img, c, rng)
                paint_extra(img, glow, c, rng)
        tex_dir = os.path.join(ASSETS, "textures/entity/hell")
        os.makedirs(tex_dir, exist_ok=True)
        img.save(os.path.join(tex_dir, bid + ".png"))
        glow.save(os.path.join(tex_dir, bid + "_eyes.png"))
        parts = [{"name": p.name, "parent": p.parent, "pivot": p.pivot, "rotation": p.rot, "role": p.role,
                  "cubes": [{"uv": list(c.uv), "origin": c.origin, "size": c.size} for c in p.cubes]} for p in a.parts]
        write_json(os.path.join(ASSETS, "hell/%s.json" % bid), {"texture": [tw, th], "parts": parts}, indent=None)
        write_json(os.path.join(DATA, "loot_table/entities/%s.json" % bid),
                   {"type": "minecraft:entity", "pools": LOOT[bid], "random_sequence": "%s:entities/%s" % (NS, bid)})
        write_json(os.path.join(ASSETS, "models/item/%s_spawn_egg.json" % bid), {"parent": "minecraft:item/template_spawn_egg"})
        previews.append((bid, parts, img))
    # sonidos
    path = os.path.join(ASSETS, "sounds.json")
    with open(path, encoding="utf-8") as f:
        sounds = json.load(f, object_pairs_hook=collections.OrderedDict)
    for bid, kinds in SOUNDS.items():
        for kind, files in kinds.items():
            key = "entity.%s.%s" % (bid, kind)
            sounds[key] = {"subtitle": "subtitles.%s.%s" % (NS, key), "sounds": [{"name": n, "pitch": pi, "volume": 1.0} for n, pi in files]}
    write_json(path, sounds)
    # la Bendición Divina
    write_json(os.path.join(DATA, "enchantment/divine_blessing.json"), ENCHANTMENT)
    add_tag(os.path.join(DATA, "tags/item/enchantable/blessable.json"),
            ["#minecraft:swords", "#minecraft:axes", "#minecraft:enchantable/bow", "#minecraft:enchantable/crossbow",
             "#minecraft:enchantable/trident", "#minecraft:enchantable/mace"])
    for tag in ("non_treasure", "tradeable", "on_random_loot", "in_enchanting_table"):
        add_tag(os.path.join(MC_DATA, "tags/enchantment/%s.json" % tag), ["%s:divine_blessing" % NS])
    add_tag(os.path.join(DATA, "tags/entity_type/demons.json"), ["%s:%s" % (NS, n) for n in ("demon", "imp", *BOSSES)])
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        cell = 300
        sheet = Image.new("RGB", (cell * len(previews), cell), (70, 74, 80))
        d = ImageDraw.Draw(sheet)
        for k, (bid, parts, img) in enumerate(previews):
            im = render_entity(parts, img, size=cell, yaw=YAW, pitch=12.0, center="auto")
            sheet.paste(im, (k * cell, 0))
            d.text((k * cell + 6, 4), NAMES[bid][0], fill=(240, 240, 240))
        sheet.save(os.path.join(PREVIEW, "demons.png"))
    print("mini jefes: Legión, Belcebú, Moloc y Abadón; Bendición Divina")


if __name__ == "__main__":
    main()
