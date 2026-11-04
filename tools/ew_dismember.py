"""El desmembramiento (fase 18): el tipo de daño de la decapitación, sus etiquetas y los textos.

Uso: python3 tools/ew_dismember.py [raíz del proyecto]
"""
import collections
import glob
import json
import os
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..")
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data")

TEXTS = {
    "message.evolvedwarfare.dismember.right_arm": ("¡Le has cortado el brazo derecho!", "You cut off their right arm!"),
    "message.evolvedwarfare.dismember.left_arm": ("¡Le has cortado el brazo izquierdo!", "You cut off their left arm!"),
    "message.evolvedwarfare.dismember.right_leg": ("¡Le has cortado la pierna derecha!", "You cut off their right leg!"),
    "message.evolvedwarfare.dismember.left_leg": ("¡Le has cortado la pierna izquierda!", "You cut off their left leg!"),
    "message.evolvedwarfare.dismember.head": ("¡DECAPITADO!", "DECAPITATED!"),
    "death.attack.evolvedwarfare.decapitation": ("%1$s fue decapitado", "%1$s was decapitated"),
    "death.attack.evolvedwarfare.decapitation.player": ("%1$s fue decapitado por %2$s", "%1$s was decapitated by %2$s"),
    "death.attack.evolvedwarfare.decapitation.item": ("%1$s fue decapitado por %2$s con %3$s", "%1$s was decapitated by %2$s using %3$s"),
    "entity.evolvedwarfare.severed_limb": ("Miembro cortado", "Severed Limb"),
    "config.evolvedwarfare.dismemberment": ("Desmembramiento", "Dismemberment"),
    "config.evolvedwarfare.dismemberment.tip": ("Con armas de filo se pueden cortar brazos y piernas a los NPCs y, con suerte, decapitarlos.",
                                                "Edged weapons can cut off NPCs' arms and legs and, with luck, behead them."),
    "config.evolvedwarfare.dismembermentChance": ("Probabilidad de desmembrar", "Dismemberment chance"),
    "config.evolvedwarfare.dismembermentChance.tip": ("Multiplica la probabilidad de cortar (1 = normal, 0 = nunca).",
                                                      "Multiplies the chance of cutting (1 = normal, 0 = never)."),
}


def write_json(path, obj, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=indent)
        f.write("\n")


def stump():
    """Los muñones (32x16): un cubo de 4x3x4 (brazos y piernas) en (0, 0) y uno de 4x1x4 (el cuello) en (0, 8); carne
    oscura por los lados y el corte rojo con el hueso en medio."""
    import random
    from PIL import Image
    r = random.Random(7)
    im = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
    px = im.load()

    def box(u, v, dx, dy, dz):
        for (x0, y0, w, h, cut) in ((u + dz, v, dx, dz, True), (u + dz + dx, v, dx, dz, True), (u, v + dz, dz, dy, False),
                                     (u + dz, v + dz, dx, dy, False), (u + dz + dx, v + dz, dz, dy, False), (u + 2 * dz + dx, v + dz, dx, dy, False)):
            for y in range(h):
                for x in range(w):
                    f = r.uniform(0.85, 1.12)
                    c = (178, 30, 30) if cut else (120, 22, 22)
                    px[x0 + x, y0 + y] = tuple(min(255, int(k * f)) for k in c) + (255,)
            if cut:
                cx, cy = x0 + w // 2 - 1, y0 + h // 2 - 1
                for (x, y) in ((cx, cy), (cx + 1, cy), (cx, cy + 1), (cx + 1, cy + 1)):
                    px[x, y] = (232, 222, 204, 255)
    box(0, 0, 4, 3, 4)
    box(0, 8, 4, 1, 4)
    path = os.path.join(ASSETS, "textures/entity/stump.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def main():
    stump()
    write_json(os.path.join(DATA, NS, "damage_type/decapitation.json"),
               {"exhaustion": 0.1, "message_id": NS + ".decapitation", "scaling": "never"})
    for tag in ("bypasses_armor", "bypasses_shield", "bypasses_enchantments"):
        path = os.path.join(DATA, "minecraft/tags/damage_type", tag + ".json")
        t = {"replace": False, "values": []}
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                t = json.load(f)
        if NS + ":decapitation" not in t["values"]:
            t["values"].append(NS + ":decapitation")
        write_json(path, t)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    print("desmembramiento: daño, etiquetas y textos")


if __name__ == "__main__":
    main()
