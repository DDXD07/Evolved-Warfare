"""La fundición (fase 32): el crisol y el molde de arcilla.

Genera los modelos y texturas del crisol (un cuenco de ladrillo refractario) y del molde (una losa de arcilla con borde),
sus estados de bloque, la receta del crisol (5 ladrillos), la receta especial del molde (frasco de agua + arcilla + palo),
las etiquetas de herramienta y los textos (español e inglés).

Uso: python3 tools/ew_casting.py [raíz del proyecto]
"""
import collections
import glob
import json
import os
import random
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..")
NS = "evolvedwarfare"
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data", NS)
MC_DATA = os.path.join(RES, "data/minecraft")

TEXTS = collections.OrderedDict([
    ("block.evolvedwarfare.crucible", ("Crisol", "Crucible")),
    ("block.evolvedwarfare.clay_mold", ("Molde de arcilla", "Clay Mold")),
    ("item.evolvedwarfare.forged_part.rough", ("%s de %s en bruto", "Rough %2$s %1$s")),
    ("tooltip.evolvedwarfare.forged_part.rough", ("Afílala en el yunque con el martillo", "Sharpen it on the anvil with the hammer")),
    ("tooltip.evolvedwarfare.crucible.empty", ("Ponlo sobre el fuego y échale lingotes al rojo", "Put it over a fire and add red-hot ingots")),
    ("tooltip.evolvedwarfare.crucible.molten", ("%s fundido: %s", "Molten %s: %s")),
    ("tooltip.evolvedwarfare.crucible.solid", ("%s sin fundir: %s", "Unmelted %s: %s")),
    ("message.evolvedwarfare.crucible.cold", ("Tiene que estar al rojo", "It must be red-hot")),
    ("message.evolvedwarfare.crucible.no_molten", ("No hay metal fundido", "No molten metal")),
    ("message.evolvedwarfare.crucible.wrong_mold", ("Ese metal no va en este molde", "That metal doesn't fit this mold")),
    ("message.evolvedwarfare.smithing.use_crucible", ("Fúndelo en un crisol", "Melt it in a crucible")),
])


def write_json(path, obj, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=indent)
        f.write("\n")


def noisy(base, rng, amount=12):
    return tuple(max(0, min(255, c + rng.randint(-amount, amount))) for c in base) + (255,)


def bricks(seed):
    """Ladrillo refractario (anaranjado) con juntas."""
    rng = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = 4 if row % 2 else 0
            joint = y % 4 == 3 or (x + off) % 8 == 7
            im.putpixel((x, y), noisy((120, 96, 80) if joint else (176, 104, 66), rng, 6 if joint else 12))
    return im


def inner(seed):
    """Dentro del crisol: arcilla ennegrecida por el fuego."""
    rng = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), noisy((70, 54, 46), rng, 10))
    return im


def clay(seed, base=(160, 166, 178), amount=8):
    rng = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), noisy(base, rng, amount))
    return im


def box(fr, to, tex, faces="all", uv=None):
    out = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        if faces != "all" and f not in faces:
            continue
        t = tex[f] if isinstance(tex, dict) else tex
        out[f] = {"texture": t}
    return {"from": fr, "to": to, "faces": out}


def crucible_model():
    side, top, ins = "#side", "#top", "#inner"
    els = [
        box([3, 0, 3], [13, 2, 13], {"north": side, "south": side, "east": side, "west": side, "up": ins, "down": side}),
        box([2, 2, 2], [14, 10, 4], {"north": side, "south": ins, "east": side, "west": side, "up": top, "down": side}),
        box([2, 2, 12], [14, 10, 14], {"north": ins, "south": side, "east": side, "west": side, "up": top, "down": side}),
        box([2, 2, 4], [4, 10, 12], {"north": side, "south": side, "east": ins, "west": side, "up": top, "down": side}),
        box([12, 2, 4], [14, 10, 12], {"north": side, "south": side, "east": side, "west": ins, "up": top, "down": side}),
        # el pico para verter
        box([7, 8, 0.5], [9, 9.5, 2], {"north": side, "south": side, "east": side, "west": side, "up": ins, "down": side}),
        # las asas
        box([0.5, 6, 7], [2, 7.5, 9], side),
        box([14, 6, 7], [15.5, 7.5, 9], side),
    ]
    return {"parent": "minecraft:block/block", "textures": {"particle": "%s:block/crucible_side" % NS, "side": "%s:block/crucible_side" % NS,
                                                         "top": "%s:block/crucible_top" % NS, "inner": "%s:block/crucible_inner" % NS},
            "elements": els}


def mold_model(full):
    c, f = "#clay", "#floor"
    els = [box([0, 0, 0], [16, 4 if full else 2, 16], {"north": c, "south": c, "east": c, "west": c, "up": c if full else f, "down": c})]
    if not full:
        els += [
            box([0, 2, 0], [16, 4, 1], c),
            box([0, 2, 15], [16, 4, 16], c),
            box([0, 2, 1], [1, 4, 15], c),
            box([15, 2, 1], [16, 4, 15], c),
        ]
    return {"parent": "minecraft:block/block", "textures": {"particle": "%s:block/clay_mold" % NS, "clay": "%s:block/clay_mold" % NS,
                                                         "floor": "%s:block/clay_mold_floor" % NS}, "elements": els}


CRATE_DISPLAY = None


def main():
    tex = os.path.join(ASSETS, "textures/block")
    bricks(1).save(os.path.join(tex, "crucible_side.png"))
    top = bricks(2)
    for x in range(16):
        top.putpixel((x, 0), (150, 90, 58, 255))
    top.save(os.path.join(tex, "crucible_top.png"))
    inner(3).save(os.path.join(tex, "crucible_inner.png"))
    clay(4).save(os.path.join(tex, "clay_mold.png"))
    clay(5, (96, 98, 108), 6).save(os.path.join(tex, "clay_mold_floor.png"))
    models = os.path.join(ASSETS, "models")
    write_json(os.path.join(models, "block/crucible.json"), crucible_model())
    write_json(os.path.join(models, "block/clay_mold.json"), mold_model(False))
    write_json(os.path.join(models, "item/clay_mold.json"), mold_model(True))
    with open(os.path.join(models, "item/crop_crate.json"), encoding="utf-8") as f:
        crate = json.load(f)
    display = crate["display"]
    display["gui"]["scale"] = [0.8, 0.8, 0.8]
    display["fixed"]["scale"] = [0.7, 0.7, 0.7]
    display["firstperson_righthand"]["scale"] = [0.55, 0.55, 0.55]
    display["firstperson_lefthand"]["scale"] = [0.55, 0.55, 0.55]
    display["thirdperson_righthand"]["scale"] = [0.5, 0.5, 0.5]
    write_json(os.path.join(models, "item/crucible.json"), {"parent": "builtin/entity", "gui_light": "side",
                                                            "textures": {"particle": "%s:block/crucible_side" % NS}, "display": display})
    for name in ("crucible", "clay_mold"):
        write_json(os.path.join(ASSETS, "blockstates/%s.json" % name), {"variants": {"": {"model": "%s:block/%s" % (NS, name)}}})
    write_json(os.path.join(DATA, "recipe/crucible.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["B B", "BBB"],
        "key": {"B": {"item": "minecraft:brick"}}, "result": {"id": "%s:crucible" % NS, "count": 1}})
    write_json(os.path.join(DATA, "recipe/clay_mold.json"), {"type": "%s:clay_mold" % NS, "category": "misc"})
    for tool, block in (("pickaxe", "crucible"), ("shovel", "clay_mold")):
        path = os.path.join(MC_DATA, "tags/block/mineable/%s.json" % tool)
        with open(path, encoding="utf-8") as f:
            tag = json.load(f)
        entry = "%s:%s" % (NS, block)
        if entry not in tag["values"]:
            tag["values"].append(entry)
        write_json(path, tag)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    print("fundición: crisol y molde")


if __name__ == "__main__":
    main()
