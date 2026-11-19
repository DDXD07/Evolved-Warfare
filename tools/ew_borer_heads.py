#!/usr/bin/env python3
"""
Fase 123: el carbón activado (sube el máximo de la locomotora y la tuneladora) y los cabezales del taladro de la
tuneladora (hierro, acero, netherita): iconos, modelos, recetas y textos. El modelo del taladro está en ew_trains.py.

    python3 tools/ew_borer_heads.py
"""
import collections
import glob
import json
import math
import os

from PIL import Image

NS = "evolvedwarfare"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)

MATS = {
    "iron": ((168, 168, 174), (110, 110, 118), (226, 226, 234)),
    "steel": ((94, 102, 116), (58, 64, 76), (170, 180, 200)),
    "netherite": ((70, 60, 66), (38, 32, 36), (150, 128, 96)),
}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def head_icon(mat):
    """La broca de lado: un cono que apunta arriba a la derecha, con su aro y la espiral."""
    body, dark, high = MATS[mat]
    img = canvas()
    # el cono: de la base (abajo a la izquierda, ancho 7) a la punta (arriba a la derecha)
    for t in range(11):
        cx, cy = 3 + t, 12 - t
        half = max(0, round(3.5 * (1 - t / 11.0)))
        for k in range(-half, half + 1):
            x, y = cx + k, cy + k  # perpendicular al eje (diagonal)
            c = high if k == -half else dark if k == half else body
            put(img, x, y, c)
            put(img, x + 1, y, c)
    # la espiral: rayas oscuras en diagonal
    for t in range(1, 10, 2):
        cx, cy = 3 + t, 12 - t
        half = max(0, round(3.5 * (1 - t / 11.0)))
        for k in range(-half, half + 1, 2):
            put(img, cx + k, cy + k, dark)
    put(img, 14, 1, high)
    put(img, 15, 0, high)
    # el aro de atrás y el eje
    for k in range(-5, 6):
        put(img, 2 + k // 2 - 1, 13 + k // 2 + (k % 2), (80, 78, 76))
    put(img, 1, 14, (50, 48, 46))
    put(img, 0, 15, (50, 48, 46))
    return img


def tube_icon():
    """Un tubo de hierro con remaches y el carbón negro asomando por las ventanas."""
    img = canvas()
    iron, dark, high = (150, 150, 158), (90, 90, 98), (210, 210, 218)
    coal, glint = (24, 22, 26), (70, 110, 120)
    for x in range(2, 14):
        for y in range(5, 11):
            c = high if y == 5 else dark if y == 10 else iron
            put(img, x, y, c)
    for x in (4, 7, 10):
        for y in (7, 8):
            put(img, x, y, coal)
            put(img, x + 1, y, coal)
        put(img, x + 1, 7, glint)
    for x in (2, 13):
        for y in range(4, 12):
            put(img, x, y, (70, 70, 78))
    for y in (5, 10):
        put(img, 3, y, high)
        put(img, 12, y, high)
    return img


def crate_icon():
    """Una caja con flejes de hierro, llena de carbón activado (negro con brillos azulados)."""
    img = canvas()
    wood, wdark = (120, 84, 50), (82, 56, 32)
    iron = (150, 150, 158)
    coal, glint = (24, 22, 26), (70, 110, 120)
    for x in range(2, 14):
        for y in range(5, 14):
            put(img, x, y, wdark if (x + y) % 5 == 0 else wood)
    for x in range(2, 14):
        put(img, x, 5, iron)
        put(img, x, 13, iron)
    for y in range(5, 14):
        put(img, 2, y, iron)
        put(img, 13, y, iron)
    for x in range(3, 13):
        for y in range(2, 5):
            if (x * 3 + y) % 4 != 0:
                put(img, x, y, coal)
    for x, y in ((4, 3), (8, 2), (11, 3), (6, 4)):
        put(img, x, y, glint)
    for x in range(2, 14):
        put(img, x, 9, iron)
    return img


ITEMS = {
    "activated_carbon_tube": ("Tubo de carbón activado", "Activated Carbon Tube",
                              "En la caldera de la locomotora o la tuneladora: mientras arde, el máximo sube a 30 km/h.",
                              "In a locomotive or borer firebox: while it burns, top speed rises to 30 km/h."),
    "activated_carbon_crate": ("Caja de carbón activado", "Activated Carbon Crate",
                               "En la caldera de la locomotora o la tuneladora: mientras arde, el máximo sube a 50 km/h.",
                               "In a locomotive or borer firebox: while it burns, top speed rises to 50 km/h."),
    "borer_head_iron": ("Cabezal de hierro", "Iron Drill Head",
                        "Para el taladro de la tuneladora: pica como un pico de hierro. Clic derecho en la tuneladora para cambiarlo.",
                        "For the borer's drill: digs like an iron pickaxe. Right click the borer to swap it."),
    "borer_head_steel": ("Cabezal de acero", "Steel Drill Head",
                         "Para el taladro de la tuneladora: pica como un pico de acero (un 10 % más que el de hierro). Clic derecho en la tuneladora para cambiarlo.",
                         "For the borer's drill: digs like a steel pickaxe (10% faster than iron). Right click the borer to swap it."),
    "borer_head_netherite": ("Cabezal de netherita", "Netherite Drill Head",
                             "Para el taladro de la tuneladora: pica como un pico de netherita (un 50 % más que el de hierro) y saca la obsidiana. Clic derecho en la tuneladora para cambiarlo.",
                             "For the borer's drill: digs like a netherite pickaxe (50% faster than iron) and gets obsidian. Right click the borer to swap it."),
}

TEXTS = {
    "message.evolvedwarfare.train.top": (" · máx. %s km/h", " · top %s km/h"),
    "message.evolvedwarfare.borer.no_head": ("La tuneladora no tiene cabezal: clic derecho en ella con uno en la mano",
                                             "The borer has no drill head: right click it holding one"),
    "message.evolvedwarfare.borer.head": ("Taladro: %s", "Drill: %s"),
    "item.evolvedwarfare.steam_borer.desc": (
        "Ponla en una vía grande. Súbete: W y S, el regulador; A, baja en cuesta; D, sube. Su taladro abre un túnel de 6 x 6 y pone las vías sola; "
        "lleva el cabezal de hierro (clic derecho con otro para cambiarlo). Clic derecho en la compuerta del depósito (a la derecha): vías, carbón y lo "
        "excavado; en la puerta de la caldera (a la izquierda): el fuego.",
        "Place it on a big track. Ride it: W and S, the throttle; A slopes down, D slopes up. Its drill bores a 6 x 6 tunnel and lays its own track; "
        "it comes with the iron drill head (right click with another to swap). Right click the hopper hatch (right side): track, coal and spoil; "
        "the firebox door (left side): the fire."),
}


def main():
    item_dir = os.path.join(ASSETS, "textures/item")
    for mat in MATS:
        head_icon(mat).save(os.path.join(item_dir, "borer_head_%s.png" % mat))
    tube_icon().save(os.path.join(item_dir, "activated_carbon_tube.png"))
    crate_icon().save(os.path.join(item_dir, "activated_carbon_crate.png"))
    for name in ITEMS:
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    R = os.path.join(DATA, "recipe")

    def shaped(name, pattern, key, count=1):
        write_json(os.path.join(R, name + ".json"), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                                                    "key": key, "result": {"id": "%s:%s" % (NS, name), "count": count}})
    it = lambda i: {"item": i}  # noqa: E731
    shaped("activated_carbon_tube", ["ICI", "CSC", "ICI"],
           {"I": it("minecraft:iron_ingot"), "C": it("minecraft:charcoal"), "S": it(NS + ":salt")}, 2)
    shaped("activated_carbon_crate", ["ITI", "TBT", "ITI"],
           {"I": it("minecraft:iron_ingot"), "T": it(NS + ":activated_carbon_tube"), "B": it("minecraft:blaze_powder")})
    shaped("borer_head_iron", ["IBI", "BPB", "IBI"],
           {"I": it("minecraft:iron_ingot"), "B": it("minecraft:iron_block"), "P": it("minecraft:piston")})
    shaped("borer_head_steel", ["SSS", "SHS", "SSS"], {"S": it(NS + ":steel_ingot"), "H": it(NS + ":borer_head_iron")})
    shaped("borer_head_netherite", [" N ", "DHD", " N "],
           {"N": it("minecraft:netherite_ingot"), "D": it("minecraft:diamond"), "H": it(NS + ":borer_head_steel")})
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            lang = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for name, (es, en, des, den) in ITEMS.items():
            lang["item.%s.%s" % (NS, name)] = en if english else es
            lang["item.%s.%s.desc" % (NS, name)] = den if english else des
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    print("cabezales y carbón activado: %d objetos" % len(ITEMS))


if __name__ == "__main__":
    main()
