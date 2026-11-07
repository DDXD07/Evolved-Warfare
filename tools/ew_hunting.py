"""
La caza (fase 46): los cuchillos (sílex, cobre, hierro y obsidiana) para despellejar lo cazado, y la mancha de sangre.

Uso: python3 tools/ew_hunting.py [raíz del proyecto]
"""
import collections
import glob
import json
import os
import random
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
MC_DATA = os.path.join(ROOT, "src/main/resources/data/minecraft")

# nombre: (material de la receta, colores de la hoja (claro, medio, oscuro), mango (claro, oscuro))
KNIVES = collections.OrderedDict([
    ("flint_knife", ("minecraft:flint", ((122, 120, 128), (70, 68, 74), (36, 35, 40)), ((150, 112, 62), (96, 70, 38)))),
    ("copper_knife", ("minecraft:copper_ingot", ((236, 160, 120), (196, 108, 72), (128, 66, 42)), ((120, 84, 50), (78, 52, 30)))),
    ("iron_knife", ("minecraft:iron_ingot", ((238, 238, 238), (190, 190, 196), (110, 110, 118)), ((92, 62, 36), (58, 38, 22)))),
    ("obsidian_knife", ("minecraft:obsidian", ((120, 80, 170), (56, 34, 92), (20, 12, 34)), ((60, 46, 40), (34, 26, 22)))),
])

TEXTS = {
    "item.evolvedwarfare.flint_knife": ("Cuchillo de sílex", "Flint Knife"),
    "item.evolvedwarfare.copper_knife": ("Cuchillo de cobre", "Copper Knife"),
    "item.evolvedwarfare.iron_knife": ("Cuchillo de hierro", "Iron Knife"),
    "item.evolvedwarfare.obsidian_knife": ("Cuchillo de obsidiana", "Obsidian Knife"),
    "item.evolvedwarfare.knife.desc": ("Clic derecho a un animal abatido para despellejarlo", "Right-click a downed animal to skin it"),
    "entity.evolvedwarfare.carcass": ("Animal abatido", "Downed Animal"),
    "entity.evolvedwarfare.blood_stain": ("Mancha de sangre", "Blood Stain"),
    "message.evolvedwarfare.skinning": ("Despellejando %s", "Skinning %s"),
    "message.evolvedwarfare.skinning.need_knife": ("Necesitas un cuchillo en la mano para despellejarlo", "You need a knife in hand to skin it"),
    "config.evolvedwarfare.category.inventory": ("Inventario", "Inventory"),
    "config.evolvedwarfare.stackLimit": ("Límite por hueco", "Stack limit"),
    "config.evolvedwarfare.stackLimit.tip": ("Cuánto cabe en un hueco de lo que se apila: Estándar (64), 100, 500, 1000 o 5000. Lo que no se apila (herramientas, armaduras) sigue de uno en uno. En un servidor manda lo del servidor.",
                                             "How many stackable items fit in a slot: Standard (64), 100, 500, 1000 or 5000. Unstackable items (tools, armor) stay at one. On a server, the server's setting applies."),
    "config.evolvedwarfare.stackLimit.standard": ("Estándar (64)", "Standard (64)"),
    "message.evolvedwarfare.skinning.busy": ("Alguien ya lo está despellejando", "Someone is already skinning it"),
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def add_tag(path, values):
    tag = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            tag = json.load(f)
    for v in values:
        if v not in tag["values"]:
            tag["values"].append(v)
    write_json(path, tag)


def knife_texture(blade, handle, seed):
    """Un cuchillo de monte en diagonal: mango con guarda abajo a la izquierda y la hoja con el filo claro y el lomo oscuro."""
    rng = random.Random(seed)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    light, mid, dark = [c + (255,) for c in blade]
    hl, hd = [c + (255,) for c in handle]
    # el mango (3-4 píxeles de diagonal, gruesos)
    for k in range(5):
        x, y = 2 + k, 13 - k
        im.putpixel((x, y), hl if k % 2 == 0 else hd)
        im.putpixel((x + 1, y), hd)
    # la guarda
    for (x, y) in ((6, 10), (7, 11), (8, 8), (5, 9)):
        im.putpixel((x, y), dark)
    # la hoja: 7 píxeles, el lomo arriba y el filo abajo, con punta
    for k in range(7):
        x, y = 8 + k, 8 - k
        if 0 <= y < 16 and x < 16:
            im.putpixel((x - 1, y), dark)                # lomo
            im.putpixel((x, y), mid if rng.random() < 0.8 else light)
            if k < 6 and y + 1 < 16:
                im.putpixel((x, y + 1), light)           # filo
    im.putpixel((14, 1), light)
    return im


def blood_texture():
    """Mancha de sangre vista desde arriba: un charco irregular con salpicaduras alrededor (32x32)."""
    rng = random.Random(7)
    n = 32
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    blobs = [(16 + rng.uniform(-3, 3), 16 + rng.uniform(-3, 3), rng.uniform(5, 8)) for _ in range(5)]
    blobs += [(rng.uniform(3, 29), rng.uniform(3, 29), rng.uniform(1.0, 2.2)) for _ in range(12)]
    for y in range(n):
        for x in range(n):
            v = 0.0
            for (bx, by, r) in blobs:
                d = ((x - bx) ** 2 + (y - by) ** 2) ** 0.5
                v = max(v, 1.0 - d / r)
            if v > 0.0:
                dark = v > 0.45
                c = (92, 6, 8) if dark else (128, 12, 12)
                jitter = rng.randint(-10, 10)
                a = 235 if v > 0.15 else 170
                px[x, y] = (max(0, c[0] + jitter), c[1], c[2], a)
    return im


def main():
    tex = os.path.join(ASSETS, "textures/item")
    os.makedirs(tex, exist_ok=True)
    R = os.path.join(DATA, "recipe")
    for name, (material, blade, handle) in KNIVES.items():
        knife_texture(blade, handle, name).save(os.path.join(tex, name + ".png"))
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name),
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
        # en el 2x2: el material arriba a la derecha y el palo abajo a la izquierda
        write_json(os.path.join(R, name + ".json"), {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" M", "S "],
                                                     "key": {"M": {"item": material}, "S": {"item": "minecraft:stick"}},
                                                     "result": {"id": "%s:%s" % (NS, name), "count": 1}})
    # hilo de la lana (deshacer un bloque de lana: lo mismo que costó hacerlo)
    write_json(os.path.join(R, "string_from_wool.json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                         "ingredients": [{"tag": "minecraft:wool"}],
                                                         "result": {"id": "minecraft:string", "count": 4}})
    # la rienda (cuerda) sin bola de slime: tres hilos trenzados, en cualquier sitio del 2x2
    write_json(os.path.join(R, "lead_from_string.json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                         "ingredients": [{"item": "minecraft:string"}] * 3,
                                                         "result": {"id": "minecraft:lead", "count": 1}})
    os.makedirs(os.path.join(ASSETS, "textures/entity"), exist_ok=True)
    blood_texture().save(os.path.join(ASSETS, "textures/entity/blood_stain.png"))
    knives = ["%s:%s" % (NS, k) for k in KNIVES]
    add_tag(os.path.join(DATA, "tags/item/knives.json"), knives)
    add_tag(os.path.join(DATA, "tags/item/loadout/tools.json"), knives)
    add_tag(os.path.join(MC_DATA, "tags/item/swords.json"), knives)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    print("la caza: %d cuchillos y la mancha de sangre" % len(KNIVES))


if __name__ == "__main__":
    main()
