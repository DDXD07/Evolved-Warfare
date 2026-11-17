"""La salud al estilo RLCraft (fase 92): vendas, entablillados y cristales de corazón (iconos, modelos, recetas), el tipo
de daño de sangrar y los textos.

Uso: python3 tools/ew_health.py [raíz]
"""
import collections
import glob
import json
import os
import sys

from PIL import Image

NS = "evolvedwarfare"
ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
MC_DATA = os.path.join(ROOT, "src/main/resources/data/minecraft")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def sprite(rows, palette):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                img.putpixel((x, y), palette[ch])
    return img


BANDAGE = [
    "................",
    "................",
    "................",
    "........ooo.....",
    "......oowwwo....",
    ".....owwwwwwo...",
    "....owwsswwwwo..",
    "....owsooswwwo..",
    "....owsooswwwo..",
    "....owwsswwwo...",
    ".....owwwwwo....",
    "......ooowwwo...",
    ".........owwwo..",
    "..........owwwo.",
    "...........oooo.",
    "................",
]
SPLINT = [
    "................",
    "..........ob....",
    ".........obbo...",
    "........obbo....",
    ".......owwo.....",
    "......obwwo.....",
    ".....obbo.......",
    "....obbo..ob....",
    "...owwo..obbo...",
    "..obwwo.obbo....",
    "..obbo.owwo.....",
    "..oo..obwwo.....",
    ".....obbo.......",
    "....obbo........",
    "....oo..........",
    "................",
]
HEART = [
    "................",
    "................",
    "...oo.....oo....",
    "..oLLo...oPLo...",
    ".oLwLPo.oPLLPo..",
    ".oLLPPPoPPPPPo..",
    ".oPPPPPPPPPPdo..",
    ".oPPPPPPPPPddo..",
    "..oPPPPPPPddo...",
    "...oPPPPPddo....",
    "....oPPPddo.....",
    ".....oPddo......",
    "......odo.......",
    ".......o........",
    "................",
    "................",
]
PAL_BANDAGE = {"o": (120, 110, 92, 255), "w": (238, 232, 216, 255), "s": (176, 40, 40, 255)}
PAL_SPLINT = {"o": (70, 48, 26, 255), "b": (166, 124, 72, 255), "w": (232, 226, 208, 255)}
PAL_HEART = {"o": (70, 10, 40, 255), "L": (255, 170, 220, 255), "w": (255, 255, 255, 255), "P": (226, 50, 120, 255),
             "d": (150, 20, 80, 255)}

ITEMS = {
    "bandage": ("Venda", "Bandage",
                "Corta la sangre y cura la parte del cuerpo más herida (que no esté rota). Mantén pulsado para ponerla.",
                "Stops bleeding and heals your most wounded body part (unless it is broken). Hold use to apply it."),
    "splint": ("Entablillado", "Splint",
               "Para un hueso roto: con él, el hueso suelda en unos 4 minutos (y se cojea menos). Mantén pulsado para ponerlo.",
               "For a broken bone: splinted, it mends in about 4 minutes (and you limp less). Hold use to apply it."),
    "heart_crystal": ("Cristal de corazón", "Heart Crystal",
                      "Un corazón más para siempre. Lo guardan los jefes de las mazmorras y los tesoros más difíciles.",
                      "One more heart, for good. Dungeon bosses and the hardest treasures keep them."),
}

TEXTS = {
    "itemGroup.evolvedwarfare.survival": ("Evolved Warfare: Supervivencia", "Evolved Warfare: Survival"),
    "health.evolvedwarfare.part.head": ("la cabeza", "head"),
    "health.evolvedwarfare.part.torso": ("el torso", "torso"),
    "health.evolvedwarfare.part.right_arm": ("el brazo derecho", "right arm"),
    "health.evolvedwarfare.part.left_arm": ("el brazo izquierdo", "left arm"),
    "health.evolvedwarfare.part.right_leg": ("la pierna derecha", "right leg"),
    "health.evolvedwarfare.part.left_leg": ("la pierna izquierda", "left leg"),
    "message.evolvedwarfare.health.bleeding": ("¡Estás sangrando! Ponte una venda.", "You are bleeding! Apply a bandage."),
    "message.evolvedwarfare.health.broken": ("¡Crac! Se te ha roto un hueso: entablíllalo.", "Crack! You broke a bone: splint it."),
    "message.evolvedwarfare.health.mended": ("Se ha soldado el hueso de %s.", "The bone in your %s has mended."),
    "message.evolvedwarfare.health.bandaged": ("Has vendado %s.", "You bandaged your %s."),
    "message.evolvedwarfare.health.splinted": ("Has entablillado %s.", "You splinted your %s."),
    "message.evolvedwarfare.health.nothing_to_bandage": ("No tienes nada que vendar.", "You have nothing to bandage."),
    "message.evolvedwarfare.health.nothing_broken": ("No tienes ningún hueso roto sin entablillar.", "You have no unsplinted broken bone."),
    "message.evolvedwarfare.health.heart": ("¡Un corazón más! Ahora tienes %s.", "One more heart! You now have %s."),
    "message.evolvedwarfare.health.max_hearts": ("Ya tienes el máximo de corazones (%s).", "You already have the most hearts (%s)."),
    "hud.evolvedwarfare.health.bleeding": ("Sangrando", "Bleeding"),
    "death.attack.evolvedwarfare.bleeding": ("%1$s se desangró", "%1$s bled out"),
    "death.attack.evolvedwarfare.bleeding.player": ("%1$s se desangró huyendo de %2$s", "%1$s bled out while fighting %2$s"),
    "config.evolvedwarfare.category.health": ("Salud", "Health"),
    "config.evolvedwarfare.woundsEnabled": ("Heridas por partes", "Body-part wounds"),
    "config.evolvedwarfare.woundsEnabled.tip": (
        "Cabeza, torso, brazos y piernas tienen su propia vida: los huesos se rompen, las heridas sangran y hay que vendarlas y entablillarlas.",
        "Head, torso, arms and legs have their own health: bones break, wounds bleed and need bandages and splints."),
    "config.evolvedwarfare.startHearts": ("Corazones al empezar", "Starting hearts"),
    "config.evolvedwarfare.startHearts.tip": ("Con cuántos corazones se empieza (10 = como en el juego normal).",
                                              "How many hearts you start with (10 = vanilla)."),
    "config.evolvedwarfare.maxHearts": ("Corazones como máximo", "Maximum hearts"),
    "config.evolvedwarfare.maxHearts.tip": ("Hasta dónde se sube con los cristales de corazón.", "How high heart crystals can take you."),
    "config.evolvedwarfare.regenRate": ("Regeneración", "Regeneration"),
    "config.evolvedwarfare.regenRate.tip": ("Lo que cura la comida (1 = como siempre; menos = más lento).",
                                            "How much food heals (1 = vanilla; lower = slower)."),
    "config.evolvedwarfare.boneBreakChance": ("Huesos rotos en caídas", "Bones broken by falls"),
    "config.evolvedwarfare.boneBreakChance.tip": ("Multiplica la probabilidad de romperse una pierna en una caída fuerte.",
                                                  "Multiplies the chance of breaking a leg in a hard fall."),
}

RECIPES = {
    "bandage": {"type": "minecraft:crafting_shapeless", "category": "misc",
                "ingredients": [{"item": "minecraft:white_wool"}, {"item": "minecraft:string"}],
                "result": {"id": "%s:bandage" % NS, "count": 3}},
    "splint": {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["S S", "SBS", "S S"],
               "key": {"S": {"item": "minecraft:stick"}, "B": {"item": "%s:bandage" % NS}},
               "result": {"id": "%s:splint" % NS, "count": 2}},
}


def main():
    item_dir = os.path.join(ASSETS, "textures/item")
    os.makedirs(item_dir, exist_ok=True)
    sprite(BANDAGE, PAL_BANDAGE).save(os.path.join(item_dir, "bandage.png"))
    sprite(SPLINT, PAL_SPLINT).save(os.path.join(item_dir, "splint.png"))
    sprite(HEART, PAL_HEART).save(os.path.join(item_dir, "heart_crystal.png"))
    for name in ITEMS:
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    for name, recipe in RECIPES.items():
        write_json(os.path.join(DATA, "recipe/%s.json" % name), recipe)
    write_json(os.path.join(DATA, "damage_type/bleeding.json"),
               {"exhaustion": 0.0, "message_id": "evolvedwarfare.bleeding", "scaling": "never"})
    for tag in ("bypasses_armor", "no_knockback"):
        path = os.path.join(MC_DATA, "tags/damage_type/%s.json" % tag)
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh, object_pairs_hook=collections.OrderedDict)
        if "%s:bleeding" % NS not in data["values"]:
            data["values"].append("%s:bleeding" % NS)
        write_json(path, data)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for key, (es, en, des, den) in ITEMS.items():
            data["item.%s.%s" % (NS, key)] = en if english else es
            data["item.%s.%s.desc" % (NS, key)] = den if english else des
        for key, (es, en) in TEXTS.items():
            data[key] = en if english else es
        write_json(path, data)
    print("salud: %d objetos" % len(ITEMS))


if __name__ == "__main__":
    main()
