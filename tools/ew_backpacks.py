"""El inventario nuevo (fase 38): las cinco mochilas.

Modelos 3D (cuerpo, bolsillo, solapa, correas y hebillas; más grandes cuanto mejor el tier), sus texturas (el cuero en
gris para teñirlo y las correas y herrajes de cada tier sin teñir), las recetas (la básica en la mesa; las demás, mejorando
la anterior en la mesa de herrería, sin perder lo de dentro), la etiqueta de tintes y los textos.

Uso: python3 tools/ew_backpacks.py [raíz del proyecto]
"""
import collections
import glob
import json
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..")
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
MC_DATA = os.path.join(ROOT, "src/main/resources/data/minecraft")

TIERS = ["basic", "traveler", "explorer", "adventurer", "abyssal"]
SIZES = [10, 18, 27, 36, 54]
# correas y herrajes de cada tier: (correa, herraje)
TRIMS = [((92, 60, 34), (150, 120, 90)), ((70, 52, 40), (200, 200, 205)), ((80, 50, 30), (240, 200, 70)),
         ((50, 40, 44), (110, 230, 220)), ((40, 32, 36), (90, 80, 90))]
NAMES = [("Mochila básica", "Basic Backpack"), ("Mochila de viajero", "Traveler's Backpack"), ("Mochila de explorador", "Explorer's Backpack"),
         ("Mochila de aventurero", "Adventurer's Backpack"), ("Mochila abisal", "Abyssal Backpack")]
UPGRADES = [None, ("minecraft:leather", "minecraft:iron_ingot"), ("minecraft:leather", "minecraft:gold_ingot"),
            ("minecraft:leather", "minecraft:diamond"), ("minecraft:netherite_upgrade_smithing_template", "minecraft:netherite_ingot")]


def n(name):
    return "%s:%s" % (NS, name)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, ensure_ascii=False, indent=2)
        fh.write("\n")


def add_tag(path, values):
    t = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            t = json.load(fh)
    for v in values:
        if v not in t["values"]:
            t["values"].append(v)
    write_json(path, t)


def textures(t):
    tex = os.path.join(ASSETS, "textures/item/backpack")
    os.makedirs(tex, exist_ok=True)
    r = random.Random(t)
    leather = Image.new("RGBA", (16, 16))
    px = leather.load()
    for y in range(16):
        for x in range(16):
            v = 200 + r.randint(-18, 14) - (8 if (x + y * 3) % 7 == 0 else 0)
            px[x, y] = (v, v, v, 255)
    d = ImageDraw.Draw(leather)
    d.line([(0, 0), (15, 0)], fill=(150, 150, 150, 255))
    d.line([(0, 15), (15, 15)], fill=(120, 120, 120, 255))
    for x in range(1, 15, 3):     # las puntadas
        d.point((x, 1), fill=(235, 235, 235, 255))
        d.point((x, 14), fill=(235, 235, 235, 255))
    leather.save(os.path.join(tex, "leather_%d.png" % t))
    strap, metal = TRIMS[t]
    trim = Image.new("RGBA", (16, 16), strap + (255,))
    d = ImageDraw.Draw(trim)
    for y in range(0, 16, 2):
        d.line([(0, y), (15, y)], fill=tuple(max(0, c - 14) for c in strap) + (255,))
    d.rectangle((5, 5, 10, 10), fill=metal + (255,), outline=tuple(max(0, c - 60) for c in metal) + (255,))
    d.point((7, 7), fill=(20, 20, 20, 255))
    if t == 4:
        d.point((3, 3), fill=(160, 60, 220, 255))
        d.point((12, 12), fill=(160, 60, 220, 255))
    trim.save(os.path.join(tex, "trim_%d.png" % t))


def box(frm, to, tex, tint=False, faces=("north", "south", "east", "west", "up", "down")):
    e = {"from": frm, "to": to, "faces": {}}
    for f in faces:
        face = {"texture": tex}
        if tint:
            face["tintindex"] = 0
        e["faces"][f] = face
    return e


def model(t):
    """En el modelo, el frente del bolsillo mira al norte (-z): a la espalda queda hacia fuera."""
    w = 10 + (t // 2) * 2           # 10, 10, 12, 12, 14
    h = 11 + t                       # 11 a 15
    dpt = 5 + (t + 1) // 2           # 5 a 7
    x0 = 8 - w / 2
    x1 = 8 + w / 2
    z0 = 8 - dpt / 2
    z1 = 8 + dpt / 2
    L, T = "#leather", "#trim"
    els = [
        box([x0, 0, z0], [x1, h, z1], L, True),                                       # el cuerpo
        box([x0 + 1.5, 1, z0 - 2], [x1 - 1.5, h * 0.55, z0], L, True),                # el bolsillo de delante
        box([x0 - 0.2, h - 3, z0 - 0.3], [x1 + 0.2, h + 0.5, z1 + 0.3], L, True),     # la solapa
        box([x0 + 2, h * 0.3, z0 - 2.3], [x0 + 3, h - 1, z0 - 0.2], T),               # correas de delante
        box([x1 - 3, h * 0.3, z0 - 2.3], [x1 - 2, h - 1, z0 - 0.2], T),
        box([8 - 1, h * 0.35, z0 - 2.6], [8 + 1, h * 0.35 + 2, z0 - 2], T),           # la hebilla
        box([x0 + 1.5, 1, z1], [x0 + 2.5, h - 1, z1 + 0.8], T),                       # tirantes (detrás)
        box([x1 - 2.5, 1, z1], [x1 - 1.5, h - 1, z1 + 0.8], T),
    ]
    if t >= 1:   # bolsas a los lados
        els.append(box([x0 - 1.5, 1.5, z0 + 0.5], [x0, h * 0.45, z1 - 0.5], L, True))
        els.append(box([x1, 1.5, z0 + 0.5], [x1 + 1.5, h * 0.45, z1 - 0.5], L, True))
    if t >= 2:   # un saco de dormir enrollado encima
        els.append(box([x0 + 0.5, h + 0.5, z0 + 0.5], [x1 - 0.5, h + 3, z1 - 0.5], T))
    if t >= 3:   # un farolillo colgado de un lado
        els.append(box([x1 + 0.3, h * 0.5, 7], [x1 + 2.3, h * 0.5 + 3, 9], T))
    s = 0.62 if t < 3 else 0.55
    return {"parent": "minecraft:block/block", "textures": {"particle": n("item/backpack/leather_%d" % t), "leather": n("item/backpack/leather_%d" % t),
                                                             "trim": n("item/backpack/trim_%d" % t)},
            "elements": els,
            "display": {
                "gui": {"rotation": [25, 155, 0], "translation": [0, 0, 0], "scale": [s, s, s]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
                "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
                "thirdperson_lefthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
                "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
                "firstperson_lefthand": {"rotation": [0, 315, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
                "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]}}}


TEXTS = {
    "item.evolvedwarfare.backpack.slots": ("Lleva %s de %s huecos", "Holds %s of %s slots"),
    "item.evolvedwarfare.backpack.desc": ("Clic derecho: a la espalda. Agachado sobre el suelo: la dejas como un cofre. Se tiñe.",
                                          "Right click: put it on. Sneak on the ground: place it like a chest. Dyeable."),
    "block.evolvedwarfare.backpack": ("Mochila", "Backpack"),
    "container.evolvedwarfare.backpack": ("%s (%s/%s)", "%s (%s/%s)"),
    "container.evolvedwarfare.backpack_slot": ("Mochila (ponla en el hueco de la espalda)", "Backpack (put it in the back slot)"),
    "container.evolvedwarfare.no_backpack": ("Sin mochila: solo llevas 5 huecos", "No backpack: you only carry 5 slots"),
    "container.evolvedwarfare.hotbar": ("Barra rápida (%s)", "Hotbar (%s)"),
    "message.evolvedwarfare.backpack.none": ("No llevas mochila", "You are not wearing a backpack"),
    "key.evolvedwarfare.backpack": ("Abrir la mochila", "Open backpack"),
    "item.evolvedwarfare.backpack.heavy": ("Los %s últimos huecos son de carga pesada: cada uno ocupado te frena un %s %%",
                                           "The last %s slots are heavy load: each one used slows you by %s %%"),
    "container.evolvedwarfare.sort": ("Ordenar y clasificar", "Sort and classify"),
}
for t in range(5):
    TEXTS["item.evolvedwarfare.backpack_%s" % TIERS[t]] = (NAMES[t][0] + " (%d)" % SIZES[t], NAMES[t][1] + " (%d)" % SIZES[t])


def main():
    for t in range(5):
        textures(t)
        write_json(os.path.join(ASSETS, "models/item/backpack_%s.json" % TIERS[t]), model(t))
    # el bloque (en el suelo lo dibuja su renderer; el modelo solo da las partículas)
    write_json(os.path.join(ASSETS, "models/block/backpack.json"), {"textures": {"particle": n("item/backpack/leather_0")}})
    write_json(os.path.join(ASSETS, "blockstates/backpack.json"), {"variants": {"facing=%s" % f: {"model": n("block/backpack")}
                                                                                for f in ("north", "south", "east", "west")}})
    # recetas
    R = os.path.join(DATA, "recipe")
    write_json(os.path.join(R, "backpack_basic.json"), {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["SLS", "L L", "LLL"],
                                                        "key": {"S": {"item": "minecraft:string"}, "L": {"item": "minecraft:leather"}},
                                                        "result": {"id": n("backpack_basic"), "count": 1}})
    for t in range(1, 5):
        template, addition = UPGRADES[t]
        write_json(os.path.join(R, "backpack_%s.json" % TIERS[t]), {"type": "minecraft:smithing_transform", "template": {"item": template},
                                                                     "base": {"item": n("backpack_" + TIERS[t - 1])}, "addition": {"item": addition},
                                                                     "result": {"id": n("backpack_" + TIERS[t]), "count": 1}})
    add_tag(os.path.join(MC_DATA, "tags/item/dyeable.json"), [n("backpack_" + x) for x in TIERS])
    # la carga (fase 39): qué es arma (hueco 1) y qué herramienta (huecos 2 y 3) además de lo de vanilla
    add_tag(os.path.join(DATA, "tags/item/loadout/weapons.json"), [n("musket"), n("javelin"), n("longbow"), n("prophet_staff")])
    add_tag(os.path.join(DATA, "tags/item/loadout/tools.json"), [n("smithing_hammer"), "minecraft:spyglass"])
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            lang = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    print("el inventario nuevo: 5 mochilas (%s)" % ", ".join(map(str, SIZES)))


if __name__ == "__main__":
    main()
