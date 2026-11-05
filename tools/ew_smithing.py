"""La herrería realista (fase 27): las armas y herramientas de metal ya no se hacen en la mesa.

1. Se calienta el lingote (alto horno u horno): lingote al rojo.
2. En un yunque, con el martillo de herrero, se forja la pieza (cabeza, hoja...) con un minijuego: de la puntería sale la
   calidad (tosca, común, buena u obra maestra).
3. En la mesa, la pieza con su mango (palos; las hojas, con empuñadura de cuero o cuerda; el mangual, con cadena).

Este script hace las texturas de las piezas (sacadas de las de cada herramienta o arma, sin el mango, en gris para teñirlas
del metal), los lingotes al rojo, el martillo, los modelos, las recetas (calentar, el martillo, la receta especial de
montar) y quita las recetas de mesa de vanilla y del mod de las herramientas y armas de hierro, oro y diamante; y los textos.

Uso: python3 tools/ew_smithing.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import colorsys
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
VANILLA = "/home/claude/ref/mcassets/assets/minecraft/textures/item"

# las piezas, en el mismo orden que ForgedPart en Java: (id, textura de origen, nombre es, nombre en)
PARTS = [
    ("sword_blade", VANILLA + "/iron_sword.png", "Hoja de espada", "Sword Blade"),
    ("pickaxe_head", VANILLA + "/iron_pickaxe.png", "Cabeza de pico", "Pickaxe Head"),
    ("axe_head", VANILLA + "/iron_axe.png", "Cabeza de hacha", "Axe Head"),
    ("shovel_head", VANILLA + "/iron_shovel.png", "Hoja de pala", "Shovel Head"),
    ("hoe_head", VANILLA + "/iron_hoe.png", "Hoja de azada", "Hoe Head"),
    ("dagger_blade", "iron_dagger", "Hoja de daga", "Dagger Blade"),
    ("spearhead", "iron_spear", "Punta de lanza", "Spearhead"),
    ("longsword_blade", "iron_longsword", "Hoja de espada larga", "Longsword Blade"),
    ("halberd_head", "iron_halberd", "Cabeza de alabarda", "Halberd Head"),
    ("warhammer_head", "iron_warhammer", "Cabeza de martillo de guerra", "Warhammer Head"),
    ("flail_head", "iron_flail", "Bola de mangual", "Flail Head"),
    ("battle_axe_head", "iron_battle_axe", "Cabeza de hacha de batalla", "Battle Axe Head"),
]

VANILLA_TOOLS = ["sword", "pickaxe", "axe", "shovel", "hoe"]
MOD_WEAPONS = ["dagger", "spear", "longsword", "halberd", "warhammer", "flail", "battle_axe"]


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def is_wood(c):
    r, g, b, a = c
    if a < 128:
        return False
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return 0.03 <= h <= 0.14 and s > 0.3 and v < 0.8


CUT = {"iron_dagger": 12, "iron_spear": 22, "iron_longsword": 12, "iron_halberd": 18, "iron_warhammer": 19, "iron_flail": 19,
       "iron_battle_axe": 18}


def part_texture(src):
    cut = CUT.get(src, -1)
    path = src if src.endswith(".png") else os.path.join(ASSETS, "textures/item", src + ".png")
    im = Image.open(path).convert("RGBA")
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    lum = []
    for y in range(im.height):
        for x in range(im.width):
            c = im.getpixel((x, y))
            if c[3] >= 128 and not is_wood(c):
                lum.append(0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2])
    top = max(lum) if lum else 255
    for y in range(im.height):
        for x in range(im.width):
            c = im.getpixel((x, y))
            if c[3] < 128 or is_wood(c) or x + (im.height - 1 - y) * im.width // im.height < cut * im.width // 16:
                continue
            v = int(min(255, (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / top * 255))
            out.putpixel((x, y), (v, v, v, 255))
    return out.resize((16, 16), Image.NEAREST) if out.width != 16 else out


def hot_ingot(gold):
    im = Image.open(os.path.join(VANILLA, ("gold" if gold else "iron") + "_ingot.png")).convert("RGBA")
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a < 128:
                continue
            v = (0.299 * r + 0.587 * g + 0.114 * b) / 255
            # del rojo oscuro al amarillo casi blanco
            if gold:
                c = (255, int(150 + 100 * v), int(40 + 160 * v * v))
            else:
                c = (int(150 + 105 * v), int(30 + 190 * v * v), int(10 + 120 * v ** 3))
            out.putpixel((x, y), c + (255,))
    return out


def hammer_texture():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    wood, wood_d = (137, 103, 55, 255), (94, 70, 36, 255)
    for k in range(10):
        x, y = 3 + k, 13 - k
        im.putpixel((x, y), wood)
        if k % 2 == 0:
            im.putpixel((x - 1, y), wood_d)
    steel, steel_l, steel_d = (150, 154, 160, 255), (210, 214, 220, 255), (70, 72, 78, 255)
    for y in range(1, 6):
        for x in range(8, 15):
            if (x - 11) * (x - 11) + (y - 3) * (y - 3) * 0.6 <= 10:
                im.putpixel((x, y), steel if x > 9 else steel_d)
    for x in range(9, 14):
        im.putpixel((x, 1), steel_l)
    im.putpixel((14, 3), steel_l)
    return im


NAMES_MAT = {"iron": ("hierro", "Iron"), "gold": ("oro", "Gold"), "diamond": ("diamante", "Diamond")}
QUALITY = [("crude", "Tosca", "Crude"), ("common", "Común", "Common"), ("fine", "Buena", "Fine"), ("masterwork", "Obra maestra", "Masterwork")]

TEXTS = {
    "item.evolvedwarfare.forged_part": ("Pieza forjada", "Forged Part"),
    "item.evolvedwarfare.forged_part.named": ("%s de %s", "%2$s %1$s"),
    "item.evolvedwarfare.hot_iron_ingot": ("Lingote de hierro al rojo", "Red-Hot Iron Ingot"),
    "item.evolvedwarfare.hot_gold_ingot": ("Lingote de oro al rojo", "Red-Hot Gold Ingot"),
    "item.evolvedwarfare.smithing_hammer": ("Martillo de herrero", "Smithing Hammer"),
    "tooltip.evolvedwarfare.hot_ingot": ("Al rojo: fórjalo en un yunque (con el martillo de herrero) antes de que se enfríe (%s s)",
                                        "Red-hot: forge it on an anvil (with a smithing hammer) before it cools (%s s)"),
    "item.evolvedwarfare.smithing_hammer.desc": ("Con un lingote al rojo en la mano, clic derecho a un yunque para forjar",
                                                "With a red-hot ingot in hand, right-click an anvil to forge"),
    "tooltip.evolvedwarfare.smithing_hammer": ("Con un lingote al rojo en la mano, clic derecho a un yunque para forjar",
                                              "With a red-hot ingot in hand, right-click an anvil to forge"),
    "tooltip.evolvedwarfare.forged_part": ("Monta en la mesa con %s", "Assemble at a crafting table with %s"),
    "tooltip.evolvedwarfare.forged.quality": ("Forja: %s", "Forging: %s"),
    "tooltip.evolvedwarfare.handle.sticks": ("%s palo(s)", "%s stick(s)"),
    "tooltip.evolvedwarfare.handle.grip": ("%s palo(s) y una empuñadura (cuero o cuerda)", "%s stick(s) and a grip (leather or string)"),
    "tooltip.evolvedwarfare.handle.chain": ("%s palo(s) y una cadena", "%s stick(s) and a chain"),
    "message.evolvedwarfare.smithing.need_hammer": ("Necesitas un martillo de herrero", "You need a smithing hammer"),
    "message.evolvedwarfare.smithing.need_ingots": ("Te faltan lingotes al rojo (%s)", "Not enough red-hot ingots (%s)"),
    "message.evolvedwarfare.smithing.need_diamonds": ("Te faltan diamantes (%s)", "Not enough diamonds (%s)"),
    "message.evolvedwarfare.smithing.done": ("Has forjado: %s (%s)", "You forged: %s (%s)"),
    "message.evolvedwarfare.smithing.level": ("¡Nivel de herrería %s! Es más fácil acertar.", "Smithing level %s! Easier to strike true."),
    "message.evolvedwarfare.smithing.cooled": ("El lingote se ha enfriado", "The ingot has cooled down"),
    "screen.evolvedwarfare.forging": ("Forja", "Forging"),
    "screen.evolvedwarfare.forging.choose": ("¿Qué quieres forjar? (lingotes al rojo: %s)", "What do you want to forge? (red-hot ingots: %s)"),
    "screen.evolvedwarfare.forging.diamond": ("Con filo de diamante (%s diamantes)", "Diamond-edged (%s diamonds)"),
    "screen.evolvedwarfare.forging.cost": ("%s (%s)", "%s (%s)"),
    "screen.evolvedwarfare.forging.hint": ("¡Golpea cuando el martillo esté en lo naranja! (clic o espacio)",
                                          "Strike when the hammer is in the orange! (click or space)"),
    "screen.evolvedwarfare.forging.heat": ("Calor", "Heat"),
    "screen.evolvedwarfare.forging.level": ("Herrería nv. %s", "Smithing lv. %s"),
    "screen.evolvedwarfare.forging.perfect": ("¡Perfecto!", "Perfect!"),
    "screen.evolvedwarfare.forging.good": ("Bien", "Good"),
    "screen.evolvedwarfare.forging.poor": ("Regular", "Poor"),
    "screen.evolvedwarfare.forging.miss": ("Fallo", "Miss"),
    "screen.evolvedwarfare.forging.result": ("Resultado: %s", "Result: %s"),
    "config.evolvedwarfare.realisticSmithing": ("Herrería realista", "Realistic smithing"),
    "config.evolvedwarfare.realisticSmithing.tip": ("Las herramientas y armas de metal se forjan: calentar el lingote, forjar la pieza en el yunque y montarla con su mango.",
                                                   "Metal tools and weapons are forged: heat the ingot, forge the part on an anvil and assemble it with its handle."),
}
for _pid, _src, _es, _en in PARTS:
    TEXTS["part.evolvedwarfare." + _pid] = (_es, _en)
for _m, (_es, _en) in NAMES_MAT.items():
    TEXTS["material.evolvedwarfare." + _m] = (_es, _en)
for _q, _es, _en in QUALITY:
    TEXTS["forge_quality.evolvedwarfare." + _q] = (_es, _en)

DISABLED = {"fabric:load_conditions": [{"condition": "fabric:not", "value": {"condition": "fabric:true"}}],
            "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [{"item": "minecraft:barrier"}],
            "result": {"id": "minecraft:barrier", "count": 1}}


def main():
    tex = os.path.join(ASSETS, "textures/item")
    os.makedirs(os.path.join(tex, "part"), exist_ok=True)
    previews = []
    overrides = []
    for k, (pid, src, _, _) in enumerate(PARTS):
        im = part_texture(src)
        im.save(os.path.join(tex, "part", pid + ".png"))
        previews.append(im)
        write_json(os.path.join(ASSETS, "models/item/forged_part_%s.json" % pid),
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/part/%s" % (NS, pid)}})
        overrides.append({"predicate": {"%s:part" % NS: round(k / 100.0, 3)}, "model": "%s:item/forged_part_%s" % (NS, pid)})
    write_json(os.path.join(ASSETS, "models/item/forged_part.json"),
               {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/part/%s" % (NS, PARTS[0][0])}, "overrides": overrides})
    for name, gold in (("hot_iron_ingot", False), ("hot_gold_ingot", True)):
        im = hot_ingot(gold)
        im.save(os.path.join(tex, name + ".png"))
        previews.append(im)
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    im = hammer_texture()
    im.save(os.path.join(tex, "smithing_hammer.png"))
    previews.append(im)
    write_json(os.path.join(ASSETS, "models/item/smithing_hammer.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/smithing_hammer" % NS}})
    rec = os.path.join(DATA, "recipe")
    for metal in ("iron", "gold"):
        for kind, time in (("blasting", 100), ("smelting", 200)):
            write_json(os.path.join(rec, "hot_%s_ingot_from_%s.json" % (metal, kind)),
                       {"type": "minecraft:" + kind, "category": "misc", "ingredient": {"item": "minecraft:%s_ingot" % metal},
                        "result": {"id": "%s:hot_%s_ingot" % (NS, metal), "count": 1}, "experience": 0.0, "cookingtime": time})
    write_json(os.path.join(rec, "smithing_hammer.json"), {"type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": ["II ", "IS ", " S "], "key": {"I": {"item": "minecraft:iron_ingot"}, "S": {"item": "minecraft:stick"}},
        "result": {"id": "%s:smithing_hammer" % NS, "count": 1}})
    write_json(os.path.join(rec, "forged_tool.json"), {"type": "%s:forged_tool" % NS, "category": "equipment"})
    # fuera las recetas de mesa de las herramientas y armas de metal
    for metal in ("iron", "golden", "diamond"):
        for tool in VANILLA_TOOLS:
            write_json(os.path.join(MC_DATA, "recipe/%s_%s.json" % (metal, tool)), DISABLED)
    for metal in ("iron", "diamond"):
        for w in MOD_WEAPONS:
            write_json(os.path.join(rec, "%s_%s.json" % (metal, w)), DISABLED)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        sheet = Image.new("RGBA", (18 * len(previews), 18), (139, 139, 139, 255))
        for k, im in enumerate(previews):
            sheet.alpha_composite(im, (18 * k + 1, 1))
        sheet.resize((sheet.width * 5, sheet.height * 5), Image.NEAREST).save(os.path.join(PREVIEW, "smithing.png"))
    print("herrería: %d piezas, lingotes al rojo, martillo, recetas (y %d recetas de mesa quitadas)" % (len(PARTS), 15 + 14))


if __name__ == "__main__":
    main()
    # los modelos 3D (ew_gear3d.py), encima de los planos de aquí
    import ew_gear3d
    ew_gear3d.main()
