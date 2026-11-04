"""El agua por tuberías (fase 19): la tubería de cobre (se une sola en las seis direcciones), el grifo de palanca (la
palanca gira un cuarto de vuelta al abrirlo) y la bomba de mano (la palanca sube y baja), con sus texturas, modelos en
3D, estados de bloque, recetas, botín y textos (también los del agua y la lava realistas).

Uso: python3 tools/ew_plumbing.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ew_model3d import Model, Palette, render  # noqa: E402

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


def write_json(path, obj, indent=1):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=indent)
        f.write("\n")


def palettes():
    copper = Palette(31)
    copper.add("copper", (200, 112, 72), pattern="stripes", accent=(170, 90, 56))
    copper.add("copper_dark", (150, 80, 52))
    copper.add("copper_light", (232, 150, 108))
    copper.add("verdigris", (88, 160, 130), pattern="spots", accent=(70, 130, 110))
    brass = Palette(32)
    brass.add("brass", (206, 170, 82), pattern="stripes", accent=(176, 140, 60))
    brass.add("brass_dark", (150, 118, 48))
    brass.add("handle", (176, 36, 30), pattern="spots", accent=(140, 24, 22))
    brass.add("iron", (150, 150, 156))
    brass.add("copper", (200, 112, 72), pattern="stripes", accent=(170, 90, 56))
    pump = Palette(33)
    pump.add("iron", (120, 124, 132), pattern="spots", accent=(96, 100, 108))
    pump.add("iron_dark", (74, 76, 84))
    pump.add("wood", (140, 100, 60), pattern="stripes", accent=(110, 78, 46))
    pump.add("base", (100, 104, 112), pattern="stripes", accent=(82, 86, 94))
    pump.add("copper", (200, 112, 72), pattern="stripes", accent=(170, 90, 56))
    return {"pipe": copper, "faucet": brass, "hand_pump": pump}


PAL = palettes()


def tex(name):
    return "%s:block/plumbing/%s" % (NS, name)


def pipe_core():
    m = Model(PAL["pipe"], tex("pipe"), 1)
    m.box(5, 5, 5, 11, 11, 11, "copper")
    m.box(4.5, 4.5, 7.5, 11.5, 11.5, 8.5, "copper_dark")
    return m


def pipe_side():
    """Un brazo hacia el norte (los demás, girados en el estado de bloque), con su brida al final."""
    m = Model(PAL["pipe"], tex("pipe"), 2)
    m.box(5.5, 5.5, 0, 10.5, 10.5, 5, "copper")
    m.box(4.5, 4.5, 0, 11.5, 11.5, 1.2, "copper_dark")
    m.box(6.0, 10.4, 2.0, 7.0, 10.6, 3.0, "verdigris")
    return m


def pipe_item():
    m = Model(PAL["pipe"], tex("pipe"), 3)
    m.box(0, 5.5, 5.5, 16, 10.5, 10.5, "copper")
    m.box(0, 4.5, 4.5, 1.2, 11.5, 11.5, "copper_dark")
    m.box(14.8, 4.5, 4.5, 16, 11.5, 11.5, "copper_dark")
    m.box(7, 4.8, 4.8, 9, 11.2, 11.2, "copper_light")
    return m


def faucet(open_):
    """El grifo mirando al sur: pegado a la tubería por detrás (norte), el caño delante y hacia abajo, la palanca arriba."""
    m = Model(PAL["faucet"], tex("faucet"), 4)
    m.box(4, 4, 0, 12, 12, 1, "brass_dark")            # brida
    m.box(6, 6, 1, 10, 10, 6, "brass")                # tubo
    m.box(5, 5, 5, 11, 11, 10, "brass")               # cuerpo de la llave
    m.box(6.5, 7, 10, 9.5, 9.5, 14, "brass")          # caño
    m.box(6.5, 4, 12, 9.5, 7, 14.5, "brass")          # caño hacia abajo
    m.box(6.8, 3.6, 12.3, 9.2, 4.0, 14.2, "brass_dark")
    m.box(7.5, 11, 7, 8.5, 13, 8, "iron")             # vástago
    if open_:
        m.box(7, 13, 3, 9, 14.5, 12, "handle")        # a lo largo del caño: abierto
        m.box(6.5, 12.8, 10.5, 9.5, 15, 12.5, "handle")
    else:
        m.box(3, 13, 6.5, 13, 14.5, 8.5, "handle")    # atravesado: cerrado
        m.box(2.5, 12.8, 6, 4.5, 15, 9, "handle")
    return m


def pump(stroke):
    m = Model(PAL["hand_pump"], tex("hand_pump"), 5)
    m.box(3.5, 0, 3.5, 12.5, 2, 12.5, "base")
    m.box(5, 2, 5, 11, 13, 11, "iron")
    m.box(4.5, 6, 4.5, 11.5, 7, 11.5, "iron_dark")
    m.box(4.5, 13, 4.5, 11.5, 14, 11.5, "iron_dark")
    m.box(7, 9, 1, 9, 10.5, 5, "iron")                 # caño delante (norte)
    m.box(7, 7.5, 1, 9, 9, 2.2, "iron_dark")
    m.box(7.5, 14, 9.5, 8.5, 16, 10.5, "iron_dark")    # el eje de la palanca, detrás
    angle = -22.5 if stroke else 22.5
    m.box(7.25, 15.0, 2.0, 8.75, 16.0, 11.0, "wood", rot=("x", angle, (8, 15.5, 10)))
    m.box(4.5, 3, 4.5, 5.5, 5, 11.5, "copper")         # empalmes de la tubería a los lados
    m.box(10.5, 3, 4.5, 11.5, 5, 11.5, "copper")
    return m


TEXTS = {
    "block.evolvedwarfare.pipe": ("Tubería de cobre", "Copper Pipe"),
    "block.evolvedwarfare.faucet": ("Grifo de palanca", "Lever Faucet"),
    "block.evolvedwarfare.hand_pump": ("Bomba de mano", "Hand Pump"),
    "message.evolvedwarfare.faucet.dry": ("No llega agua (¿falta la toma, o hay que bombear?)", "No water comes out (no intake, or it needs pumping?)"),
    "message.evolvedwarfare.pump.charge": ("Bombeado: %s/%s capas", "Pumped: %s/%s layers"),
    "config.evolvedwarfare.realisticFluids": ("Agua y lava realistas", "Realistic water and lava"),
    "config.evolvedwarfare.realisticFluids.tip": ("El agua y la lava no se crean ni se destruyen: un cubo son 8 capas que se extienden, sacar agua baja el nivel del lago. El mar, los ríos y los pantanos son infinitos.",
                                                  "Water and lava are neither created nor destroyed: a bucket is 8 layers that spread out, taking water lowers the lake. Oceans, rivers and swamps are infinite."),
}


def main():
    tex_dir = os.path.join(ASSETS, "textures/block/plumbing")
    os.makedirs(tex_dir, exist_ok=True)
    images = {}
    for name, pal in PAL.items():
        pal.im.save(os.path.join(tex_dir, name + ".png"))
        images[tex(name)] = pal.im
    models = {
        "pipe_core": pipe_core().json(ao=True), "pipe_side": pipe_side().json(ao=True),
        "faucet": faucet(False).json(ao=True), "faucet_open": faucet(True).json(ao=True),
        "hand_pump": pump(False).json(ao=True), "hand_pump_stroke": pump(True).json(ao=True),
    }
    for name, model in models.items():
        write_json(os.path.join(ASSETS, "models/block/%s.json" % name), model)
    # tubería: el centro siempre y un brazo por cada lado unido (como la planta de coro)
    side = NS + ":block/pipe_side"
    parts = [{"apply": {"model": NS + ":block/pipe_core"}}]
    for d, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}), ("up", {"x": 270}), ("down", {"x": 90})):
        apply = {"model": side}
        apply.update(rot)
        parts.append({"when": {d: "true"}, "apply": apply})
    write_json(os.path.join(ASSETS, "blockstates/pipe.json"), {"multipart": parts})
    rot = {"south": 0, "west": 90, "north": 180, "east": 270}
    write_json(os.path.join(ASSETS, "blockstates/faucet.json"), {"variants": {
        "facing=%s,open=%s" % (f, o): dict({"model": NS + ":block/faucet" + ("_open" if o == "true" else "")}, **({"y": r} if r else {}))
        for f, r in rot.items() for o in ("true", "false")}})
    prot = {"north": 0, "east": 90, "south": 180, "west": 270}
    write_json(os.path.join(ASSETS, "blockstates/hand_pump.json"), {"variants": {
        "facing=%s,stroke=%s" % (f, s): dict({"model": NS + ":block/hand_pump" + ("_stroke" if s == "true" else "")}, **({"y": r} if r else {}))
        for f, r in prot.items() for s in ("true", "false")}})
    item = pipe_item().json(ao=True)
    item["parent"] = "minecraft:block/block"
    write_json(os.path.join(ASSETS, "models/item/pipe.json"), item)
    write_json(os.path.join(ASSETS, "models/item/faucet.json"), {"parent": NS + ":block/faucet"})
    write_json(os.path.join(ASSETS, "models/item/hand_pump.json"), {"parent": NS + ":block/hand_pump"})
    # ya que los modelos de bloque no tienen padre, la vista en la mano y el inventario
    display = {"gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
               "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
               "fixed": {"scale": [0.5, 0.5, 0.5]},
               "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
               "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
               "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]}}
    for name in ("faucet", "hand_pump"):
        path = os.path.join(ASSETS, "models/block/%s.json" % name)
        with open(path) as f:
            mm = json.load(f)
        mm["display"] = display
        write_json(path, mm)
    rec = os.path.join(DATA, "recipe")
    write_json(os.path.join(rec, "pipe.json"), {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["CCC"],
                                                "key": {"C": {"item": "minecraft:copper_ingot"}}, "result": {"id": NS + ":pipe", "count": 6}})
    write_json(os.path.join(rec, "faucet.json"), {"type": "minecraft:crafting_shaped", "category": "building", "pattern": [" L ", "CPC"],
                                                  "key": {"L": {"item": "minecraft:lever"}, "C": {"item": "minecraft:copper_ingot"},
                                                          "P": {"item": NS + ":pipe"}}, "result": {"id": NS + ":faucet", "count": 1}})
    write_json(os.path.join(rec, "hand_pump.json"), {"type": "minecraft:crafting_shaped", "category": "building",
                                                     "pattern": [" L ", " I ", "IPI"],
                                                     "key": {"L": {"item": "minecraft:lever"}, "I": {"item": "minecraft:iron_ingot"},
                                                             "P": {"item": NS + ":pipe"}}, "result": {"id": NS + ":hand_pump", "count": 1}})
    for name in ("pipe", "faucet", "hand_pump"):
        write_json(os.path.join(DATA, "loot_table/blocks/%s.json" % name),
                   {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": NS + ":" + name}],
                                                          "conditions": [{"condition": "minecraft:survives_explosion"}]}],
                    "random_sequence": NS + ":blocks/" + name})
    # se pican con pico
    tag = os.path.join(ROOT, "src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json")
    t = {"replace": False, "values": []}
    if os.path.exists(tag):
        with open(tag) as f:
            t = json.load(f)
    for name in ("pipe", "faucet", "hand_pump"):
        if NS + ":" + name not in t["values"]:
            t["values"].append(NS + ":" + name)
    write_json(tag, t, indent=2)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang, indent=2)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        cell = 200
        sheet = Image.new("RGB", (cell * 5, cell), (70, 74, 80))
        tee = [models["pipe_core"]]
        for _ in range(1):
            pass
        shots = [
            [models["pipe_core"], models["pipe_side"]],
            [item],
            [models["faucet"]], [models["faucet_open"]],
            [models["hand_pump"]],
        ]
        for k, ms in enumerate(shots):
            im = render(ms, images, size=cell, scale=7.5, yaw=215 if k >= 2 else 35)
            sheet.paste(im, (k * cell, 0))
        sheet.save(os.path.join(PREVIEW, "plumbing.png"))
    print("tuberías: tubería, grifo y bomba (modelos, estados, recetas, botín y textos)")


if __name__ == "__main__":
    main()
