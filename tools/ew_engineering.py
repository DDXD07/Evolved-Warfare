"""La ingeniería medieval (fase 72 en adelante): la fuerza mecánica (velocidad y par).

Fase 1: el eje, los engranajes (pequeño y grande), la caja de engranajes, la manivela, las ruedas hidráulicas y los molinos
de viento (pequeños, medianos y grandes), y los medidores de velocidad y de fuerza. Aquí salen los modelos (se dibujan
girando en el juego), los iconos, las texturas de los bloques quietos, las recetas, lo que sueltan y los textos.

Los modelos giran sobre el eje x del modelo (y hacia abajo, como los de las entidades), con el centro del bloque en el
origen; el juego los orienta según el bloque.

Uso: python3 tools/ew_engineering.py [raíz] [--preview carpeta]
"""
import collections
import glob
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ew_wildlife as W  # noqa: E402
import ew_demons as D  # noqa: E402
from ew_model3d import render_entity  # noqa: E402
from ew_trains import pack  # noqa: E402

NS = "evolvedwarfare"
ROOT = W.ROOT
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
C = W.C
write_json = D.write_json

OAK = (150, 112, 66)
OAK_DARK = (104, 74, 42)
STRIPPED = (176, 136, 84)
IRON = (96, 96, 104)
IRON_DARK = (54, 54, 60)
CLOTH = (226, 218, 196)
CLOTH_DARK = (190, 180, 156)
BRASS = (196, 156, 72)
RED = (170, 40, 34)

WATER_WHEELS = {"small": (3, 8, 4), "medium": (5, 12, 6), "large": (7, 16, 8)}       # diámetro, palas, radios
WINDMILLS = {"small": 5, "medium": 9, "large": 13}                                    # diámetro de las aspas


def rotor(a):
    return a.part("rotor", None, (0, 0, 0))


def shaft():
    a = W.Animal("kinetic_shaft")
    r = rotor(a)
    r.cube((-8, -2, -2), (16, 4, 4), **C(STRIPPED, rings=(OAK_DARK, 4)))
    r.cube((-7, -3, -3), (2, 6, 6), **C(IRON_DARK, high=(IRON, 1)))
    r.cube((5, -3, -3), (2, 6, 6), **C(IRON_DARK, high=(IRON, 1)))
    return a


def small_gear():
    a = W.Animal("kinetic_small_gear")
    r = rotor(a)
    r.cube((-8, -2, -2), (16, 4, 4), **C(STRIPPED, rings=(OAK_DARK, 4)))
    r.cube((-2, -6, -6), (4, 12, 12), **C(OAK, rings=(OAK_DARK, 2), spots=((170, 128, 78), 0.05)))
    r.cube((-3, -3, -3), (6, 6, 6), **C(IRON_DARK, high=(IRON, 1)))
    for k in range(8):
        t = a.part("tooth%d" % k, "rotor", (0, 0, 0), rot=(45 * k, 0, 0))
        t.cube((-2, -8, -1), (4, 2, 2), **C(OAK_DARK, high=(OAK, 1)))
    return a


def large_gear():
    a = W.Animal("kinetic_large_gear")
    r = rotor(a)
    r.cube((-8, -2, -2), (16, 4, 4), **C(STRIPPED, rings=(OAK_DARK, 4)))
    r.cube((-3, -4, -4), (6, 8, 8), **C(IRON_DARK, high=(IRON, 1)))
    for k in range(16):
        s = a.part("rim%d" % k, "rotor", (0, 0, 0), rot=(22.5 * k, 0, 0))
        s.cube((-2, -14, -3), (4, 3, 6), **C(OAK, rings=(OAK_DARK, 3)))
        s.cube((-2, -16, -1), (4, 2, 2), **C(OAK_DARK, high=(OAK, 1)))
    for k in range(4):
        s = a.part("spoke%d" % k, "rotor", (0, 0, 0), rot=(90 * k + 45, 0, 0))
        s.cube((-1, -12, -1), (2, 8, 2), **C(OAK_DARK))
    return a


def crank():
    a = W.Animal("kinetic_crank")
    base = a.part("base", None, (0, 0, 0))
    base.cube((-8, -5, -5), (3, 10, 10), **C(OAK_DARK, rings=(IRON_DARK, 3)))
    base.cube((-5, -2, -2), (2, 4, 4), **C(IRON_DARK))
    r = rotor(a)
    r.cube((-3, -1, -1), (6, 2, 2), **C(IRON, high=((150, 150, 160), 1)))
    r.cube((2, -8, -1), (2, 9, 2), **C(OAK, rings=(OAK_DARK, 3)))
    r.cube((3, -9, -1), (5, 2, 2), **C(OAK_DARK, high=(OAK, 1)))
    return a


def water_wheel(size):
    d, paddles, spokes = WATER_WHEELS[size]
    R = d * 8 - 2
    a = W.Animal("water_wheel_" + size)
    r = rotor(a)
    w = 12
    r.cube((-8, -2, -2), (16, 4, 4), **C(STRIPPED, rings=(OAK_DARK, 4)))
    r.cube((-w // 2, -4, -4), (w, 8, 8), **C(IRON_DARK, high=(IRON, 1), rings=(OAK_DARK, 3)))
    for k in range(spokes):
        s = a.part("spoke%d" % k, "rotor", (0, 0, 0), rot=(360.0 * k / spokes, 0, 0))
        for x in (-w // 2, w // 2 - 2):
            s.cube((x, -R + 3, -1), (2, R - 6, 2), **C(OAK, rings=(OAK_DARK, 6)))
    nr = max(12, int(round(2 * math.pi * R / 10)))
    seg = int(math.ceil(2 * math.pi * R / nr)) + 1
    for k in range(nr):
        s = a.part("rim%d" % k, "rotor", (0, 0, 0), rot=(360.0 * k / nr, 0, 0))
        for x in (-w // 2, w // 2 - 2):
            s.cube((x, -R, -seg // 2), (2, 3, seg), **C(OAK_DARK, high=(OAK, 1)))
    for k in range(paddles):
        s = a.part("paddle%d" % k, "rotor", (0, 0, 0), rot=(360.0 * k / paddles + 180.0 / paddles, 0, 0))
        s.cube((-w // 2, -R - 2, -1), (w, 7, 2), **C(OAK, rings=(OAK_DARK, 3), spots=((86, 100, 60), 0.05)))
    return a


def windmill(size):
    d = WINDMILLS[size]
    R = d * 8 - 2
    a = W.Animal("windmill_" + size)
    r = rotor(a)
    r.cube((-4, -4, -4), (8, 8, 8), **C(OAK_DARK, rings=(IRON_DARK, 3)))
    r.cube((-6, -3, -3), (2, 6, 6), **C(IRON_DARK, high=(IRON, 1)))
    r.cube((4, -2, -2), (4, 4, 4), **C(STRIPPED, rings=(OAK_DARK, 2)))
    width = max(10, int(R * 0.32))
    length = int(R * 0.74)
    for k in range(4):
        s = a.part("arm%d" % k, "rotor", (0, 0, 0), rot=(90 * k, 0, 0))
        s.cube((-3, -R, -1), (2, R - 3, 2), **C(OAK, rings=(OAK_DARK, 8)))
        s.cube((-4, -R + 1, 1), (1, length, width), **C(CLOTH, rings=(CLOTH_DARK, 6), spots=((206, 196, 170), 0.04)))
        for y in range(-R + 2, -R + length, max(8, length // 6)):
            s.cube((-3, y, 1), (1, 1, width), **C(OAK_DARK))
        s.cube((-3, -R + 1, width), (1, length, 1), **C(OAK_DARK))
    return a


def needle():
    a = W.Animal("kinetic_gauge_needle")
    n = a.part("needle", None, (0, -8, 0))
    n.cube((-1, -1, -1), (7, 1, 2), **C(RED, high=((220, 80, 60), 1)))
    n.cube((-1, -2, -1), (2, 1, 2), **C(BRASS))
    return a


MODELS = collections.OrderedDict([
    ("kinetic_shaft", shaft), ("kinetic_small_gear", small_gear), ("kinetic_large_gear", large_gear), ("kinetic_crank", crank),
    ("kinetic_gauge_needle", needle)]
    + [("water_wheel_" + s, (lambda s=s: water_wheel(s))) for s in WATER_WHEELS]
    + [("windmill_" + s, (lambda s=s: windmill(s))) for s in WINDMILLS])

ITEM_OF = {"kinetic_shaft": "shaft", "kinetic_small_gear": "small_gear", "kinetic_large_gear": "large_gear", "kinetic_crank": "hand_crank"}
for s in WATER_WHEELS:
    ITEM_OF["water_wheel_" + s] = "water_wheel_" + s
for s in WINDMILLS:
    ITEM_OF["windmill_" + s] = "windmill_" + s

# id: (es, en, descripción es, descripción en)
NAMES = collections.OrderedDict([
    ("shaft", ("Eje", "Shaft", "Lleva el giro en línea recta. Clic derecho agachado con la mano vacía: velocidad y fuerza.",
               "Carries rotation in a straight line. Sneak + right click with an empty hand: speed and force.")),
    ("small_gear", ("Engranaje pequeño", "Small Gear",
                    "Engrana con otro pequeño al lado (gira al revés, igual de rápido) y con uno grande en diagonal (el pequeño va al doble de rápido).",
                    "Meshes with another small gear beside it (turns the other way, same speed) and with a large one diagonally (the small one turns twice as fast).")),
    ("large_gear", ("Engranaje grande", "Large Gear",
                    "Engrana en diagonal con los pequeños: el grande va a la mitad de velocidad (con el doble de par).",
                    "Meshes diagonally with small gears: the large one turns at half the speed (with twice the torque).")),
    ("gearbox", ("Caja de engranajes", "Gearbox", "Pasa el giro a cualquier lado (para doblar esquinas).",
                 "Passes rotation to any side (to turn corners).")),
    ("hand_crank", ("Manivela", "Hand Crank", "Mantén clic derecho para darle vueltas (16 rpm, poca fuerza, cansa).",
                    "Hold right click to turn it (16 rpm, little force, makes you hungry).")),
    ("water_wheel_small", ("Rueda hidráulica pequeña", "Small Water Wheel",
                           "3 de diámetro. Gira con el agua que corre o cae por ella: 16 rpm, poca fuerza.",
                           "3 wide. Turned by water flowing or falling through it: 16 rpm, little force.")),
    ("water_wheel_medium", ("Rueda hidráulica mediana", "Medium Water Wheel", "5 de diámetro: 12 rpm y bastante fuerza.",
                            "5 wide: 12 rpm and plenty of force.")),
    ("water_wheel_large", ("Rueda hidráulica grande", "Large Water Wheel", "7 de diámetro: 8 rpm y mucha fuerza.",
                           "7 wide: 8 rpm and lots of force.")),
    ("windmill_small", ("Molino de viento pequeño", "Small Windmill",
                        "Aspas de 5: 16 rpm. Necesitan aire libre; dan más fuerza cuanto más alto y con lluvia o tormenta.",
                        "5-wide sails: 16 rpm. They need open air; more force the higher they are, and in rain or storms.")),
    ("windmill_medium", ("Molino de viento mediano", "Medium Windmill", "Aspas de 9: 12 rpm y bastante fuerza.",
                         "9-wide sails: 12 rpm and plenty of force.")),
    ("windmill_large", ("Molino de viento grande", "Large Windmill", "Aspas de 13: 8 rpm y mucha fuerza.",
                        "13-wide sails: 8 rpm and lots of force.")),
    ("speed_gauge", ("Velocímetro", "Speedometer", "Marca la velocidad del eje que pasa por él.", "Shows the speed of the shaft through it.")),
    ("stress_gauge", ("Medidor de fuerza", "Force Gauge", "Marca cuánta fuerza se gasta de la que hay en la red.",
                      "Shows how much of the network's force is being used.")),
])
TEXTS = {
    "message.evolvedwarfare.kinetic.info": ("%s rpm · fuerza %s/%s", "%s rpm · force %s/%s"),
    "message.evolvedwarfare.kinetic.jammed": ("¡Atascado! Dos fuentes giran a distinta velocidad o en sentido contrario",
                                              "Jammed! Two sources turn at different speeds or opposite ways"),
    "message.evolvedwarfare.kinetic.overstressed": ("¡Sobrecargado! Hace falta más fuerza (%s/%s)", "Overstressed! More force is needed (%s/%s)"),
    "message.evolvedwarfare.kinetic.blocked": ("Algo estorba al giro", "Something is in the way"),
    "message.evolvedwarfare.kinetic.no_water": ("No le corre agua", "No water is running through it"),
    "message.evolvedwarfare.kinetic.still": ("Parado", "Not turning"),
    "itemGroup.evolvedwarfare.engineering": ("Ingeniería medieval", "Medieval Engineering"),
}


def block_textures():
    """La caja de engranajes y los medidores (bloques quietos)."""
    rnd = random.Random(72)
    out = {}

    def planks(seed_shift=0):
        im = Image.new("RGBA", (16, 16))
        for x in range(16):
            for y in range(16):
                row = y // 4
                v = 0.9 + 0.1 * ((row * 7 + seed_shift) % 3) / 2 + rnd.uniform(-0.05, 0.05)
                c = tuple(int(t * v) for t in OAK)
                if y % 4 == 3:
                    c = tuple(int(t * 0.72) for t in OAK)
                im.putpixel((x, y), c + (255,))
        return im

    def frame(im):
        for x in range(16):
            for y in range(16):
                if x in (0, 15) or y in (0, 15):
                    im.putpixel((x, y), IRON_DARK + (255,))
                elif (x in (1, 14) and y in (1, 14)):
                    im.putpixel((x, y), (150, 150, 160, 255))
        return im

    gb = frame(planks())
    dr = ImageDraw.Draw(gb)
    dr.ellipse((4, 4, 11, 11), fill=IRON_DARK + (255,))
    dr.ellipse((6, 6, 9, 9), fill=STRIPPED + (255,))
    out["gearbox"] = gb
    for kind, arc in (("speed_gauge", [(46, 140, 60), (210, 190, 50), (200, 60, 40)]), ("stress_gauge", [(60, 120, 200), (210, 190, 50), (200, 60, 40)])):
        top = frame(planks(1))
        dr = ImageDraw.Draw(top)
        dr.ellipse((2, 2, 13, 13), fill=(232, 222, 196, 255), outline=BRASS + (255,))
        for k, col in enumerate(arc):
            dr.arc((3, 3, 12, 12), 180 + 60 * k, 240 + 60 * k, fill=col + (255,))
        dr.point((7, 7), fill=IRON_DARK + (255,))
        out[kind + "_top"] = top
        side = frame(planks(2))
        dr = ImageDraw.Draw(side)
        dr.rectangle((5, 5, 10, 10), fill=BRASS + (255,))
        dr.rectangle((6, 6, 9, 9), fill=IRON_DARK + (255,))
        out[kind + "_side"] = side
    return out


def shaped(name, pattern, key, count=1, result=None):
    write_json(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": pattern,
        "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
        "result": {"id": result or ("%s:%s" % (NS, name)), "count": count}})


def recipes():
    P, S, L, I = "#minecraft:planks", "minecraft:stick", "#minecraft:logs", "minecraft:iron_ingot"
    H = NS + ":shaft"
    shaped("shaft", ["L", "L"], {"L": L}, 4)
    shaped("small_gear", [" P ", "PHP", " P "], {"P": P, "H": H})
    shaped("large_gear", ["PSP", "SGS", "PSP"], {"P": P, "S": S, "G": NS + ":small_gear"})
    shaped("gearbox", ["PNP", "NGN", "PNP"], {"P": P, "N": "minecraft:iron_nugget", "G": NS + ":small_gear"})
    shaped("hand_crank", ["SSS", "P  "], {"S": S, "P": P})
    shaped("water_wheel_small", ["PPP", "PHP", "PPP"], {"P": P, "H": H})
    shaped("water_wheel_medium", ["LPL", "PWP", "LPL"], {"L": L, "P": P, "W": NS + ":water_wheel_small"})
    shaped("water_wheel_large", ["LIL", "IWI", "LIL"], {"L": L, "I": I, "W": NS + ":water_wheel_medium"})
    shaped("windmill_small", ["WSW", "SHS", "WSW"], {"W": "minecraft:white_wool", "S": S, "H": H})
    shaped("windmill_medium", ["WLW", "LMW", "WLW"], {"W": "minecraft:white_wool", "L": L, "M": NS + ":windmill_small"})
    shaped("windmill_large", ["WIW", "IMI", "WIW"], {"W": "minecraft:white_wool", "I": I, "M": NS + ":windmill_medium"})
    shaped("speed_gauge", ["PCP", "PHP"], {"P": P, "C": "minecraft:compass", "H": H})
    shaped("stress_gauge", ["PCP", "PHP"], {"P": P, "C": "minecraft:clock", "H": H})


def main():
    prev = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    tex_dir = os.path.join(ASSETS, "textures/entity/engineering")
    item_dir = os.path.join(ASSETS, "textures/item")
    block_dir = os.path.join(ASSETS, "textures/block")
    for dd in (tex_dir, item_dir, block_dir):
        os.makedirs(dd, exist_ok=True)
    sheet = []
    for mid, gen in MODELS.items():
        a = gen()
        tw, th = pack(a)
        img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        glow = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        rng = random.Random(mid)
        for p in a.parts:
            for c in p.cubes:
                assert all(float(v).is_integer() for v in c.size), (mid, c.size)
                W.paint_cube(img, c, rng)
        img.save(os.path.join(tex_dir, mid + ".png"))
        glow.save(os.path.join(tex_dir, mid + "_glow.png"))
        parts = [{"name": p.name, "parent": p.parent, "pivot": p.pivot, "rotation": p.rot, "role": p.role,
                  "cubes": [{"uv": list(c.uv), "origin": c.origin, "size": c.size} for c in p.cubes]} for p in a.parts]
        write_json(os.path.join(ASSETS, "hell/%s.json" % mid), {"texture": [tw, th], "parts": parts}, indent=None)
        print("  %s: textura %dx%d, %d piezas" % (mid, tw, th, len(parts)))
        if mid in ITEM_OF:
            ic = render_entity(parts, img, size=96, yaw=125.0, pitch=20.0, center="auto").convert("RGBA")
            px = ic.load()
            bg = px[0, 0]
            for y in range(ic.height):
                for x in range(ic.width):
                    if px[x, y][:3] == bg[:3]:
                        px[x, y] = (0, 0, 0, 0)
            ic = ic.crop(ic.getbbox())
            side = max(ic.size)
            sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
            sq.paste(ic, ((side - ic.width) // 2, (side - ic.height) // 2))
            sq.resize((32, 32), Image.LANCZOS).save(os.path.join(item_dir, ITEM_OF[mid] + ".png"))
            write_json(os.path.join(ASSETS, "models/item/%s.json" % ITEM_OF[mid]),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, ITEM_OF[mid])}})
        if prev:
            sheet.append(render_entity(parts, img, size=220, yaw=125.0, pitch=20.0, center="auto"))
    if prev:
        os.makedirs(prev, exist_ok=True)
        cols = 4
        out = Image.new("RGB", (cols * 220, ((len(sheet) + cols - 1) // cols) * 220), (40, 40, 40))
        for i, im in enumerate(sheet):
            out.paste(im, ((i % cols) * 220, (i // cols) * 220))
        out.save(os.path.join(prev, "engineering.png"))
    # los bloques quietos y los que se dibujan aparte (solo las partículas)
    for name, im in block_textures().items():
        im.save(os.path.join(block_dir, name + ".png"))
    write_json(os.path.join(ASSETS, "blockstates/gearbox.json"), {"variants": {"": {"model": "%s:block/gearbox" % NS}}})
    write_json(os.path.join(ASSETS, "models/block/gearbox.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": "%s:block/gearbox" % NS}})
    write_json(os.path.join(ASSETS, "models/item/gearbox.json"), {"parent": "%s:block/gearbox" % NS})
    for g in ("speed_gauge", "stress_gauge"):
        write_json(os.path.join(ASSETS, "blockstates/%s.json" % g), {"variants": {"": {"model": "%s:block/%s" % (NS, g)}}})
        write_json(os.path.join(ASSETS, "models/block/%s.json" % g), {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": "%s:block/%s_top" % (NS, g), "side": "%s:block/%s_side" % (NS, g), "bottom": "minecraft:block/oak_planks"}})
        write_json(os.path.join(ASSETS, "models/item/%s.json" % g), {"parent": "%s:block/%s" % (NS, g)})
    for name in ITEM_OF.values():
        write_json(os.path.join(ASSETS, "blockstates/%s.json" % name), {"variants": {"": {"model": "%s:block/kinetic_particles" % NS}}})
    write_json(os.path.join(ASSETS, "models/block/kinetic_particles.json"), {"textures": {"particle": "minecraft:block/stripped_oak_log"}})
    for name in NAMES:
        write_json(os.path.join(DATA, "loot_table/blocks/%s.json" % name), {"type": "minecraft:block", "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}],
             "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    recipes()
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for key, (es, en, des, den) in NAMES.items():
            data["block.%s.%s" % (NS, key)] = en if english else es
            data["block.%s.%s.desc" % (NS, key)] = den if english else des
        for key, (es, en) in TEXTS.items():
            data[key] = en if english else es
        write_json(path, data)
    print("ingeniería: %d modelos" % len(MODELS))


if __name__ == "__main__":
    main()
