#!/usr/bin/env python3
"""Fase 112: el nivel de superviviente (como en ARK). Solo los textos (en todos los idiomas)."""
import collections
import glob
import json
import os

NS = "evolvedwarfare"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)

TEXTS = {
    "hud.evolvedwarfare.survivor.xp": ("+%s XP", "+%s XP"),
    "hud.evolvedwarfare.survivor.xp_why": ("+%s XP · %s", "+%s XP · %s"),
    "hud.evolvedwarfare.survivor.biome": ("Nuevo bioma: %s", "New biome: %s"),
    "hud.evolvedwarfare.survivor.craft": ("Primera vez: %s", "First time: %s"),
    "hud.evolvedwarfare.survivor.kill": ("%s abatido", "%s slain"),
    "hud.evolvedwarfare.survivor.dawn": ("Sobreviviste a la noche %s", "Survived night %s"),
    "hud.evolvedwarfare.skills.level_up": ("¡Nivel %s! +%s puntos de engrama · +%s de atributo",
                                           "Level %s! +%s engram points · +%s attribute"),
    "screen.evolvedwarfare.skills.level": ("Superviviente: nivel %s", "Survivor: level %s"),
    "screen.evolvedwarfare.skills.survivor_points": ("Engramas: %s · Atributos: %s", "Engrams: %s · Attributes: %s"),
    "config.evolvedwarfare.survivorXpRate": ("Experiencia de superviviente", "Survivor experience"),
    "config.evolvedwarfare.survivorXpRate.tip": (
        "Lo que da matar, minar, talar, fabricar por primera vez, explorar y amanecer. 1 = normal; 0 = solo las habilidades.",
        "What killing, mining, chopping, first crafts, exploring and dawns give. 1 = normal; 0 = skills only."),
}


def main():
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            data[key] = en if english else es
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
    print("superviviente: %d textos" % len(TEXTS))


if __name__ == "__main__":
    main()
