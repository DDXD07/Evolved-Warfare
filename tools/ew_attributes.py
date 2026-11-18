#!/usr/bin/env python3
"""Fase 114: los atributos del superviviente y la barra de vida. Solo los textos (en todos los idiomas)."""
import collections
import glob
import json
import os

NS = "evolvedwarfare"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)

ATTRS = {
    "health": ("Vida", "Health", "+0,2 corazones de vida máxima por punto. Con más de 15 corazones salen en una barra.",
               "+0.2 max hearts per point. Above 15 hearts they become a health bar."),
    "stamina": ("Aguante", "Stamina", "El aguante se gasta un 0,5 % menos y se recupera un 0,5 % más rápido por punto (hasta el 50 %).",
                "Stamina drains 0.5% less and recovers 0.5% faster per point (up to 50%)."),
    "melee": ("Daño cuerpo a cuerpo", "Melee damage", "+0,5 % de daño con las armas cuerpo a cuerpo por punto.",
              "+0.5% melee damage per point."),
    "speed": ("Velocidad", "Movement speed", "+0,2 % de velocidad al andar y correr por punto (hasta el 25 %).",
              "+0.2% movement speed per point (up to 25%)."),
    "fortitude": ("Fortaleza", "Fortitude",
                  "Un 0,5 % menos de daño por punto de sangrado, veneno, infección, parásitos, frío, calor, sed y hambre (hasta el 50 %).",
                  "0.5% less damage per point from bleeding, venom, infection, parasites, cold, heat, thirst and hunger (up to 50%)."),
    "metabolism": ("Metabolismo", "Metabolism", "El hambre y la sed bajan un 0,5 % más despacio por punto (hasta el 50 %).",
                   "Hunger and thirst drop 0.5% slower per point (up to 50%)."),
    "weight": ("Carga", "Weight", "La carga pesada de la mochila frena un 0,8 % menos por punto (hasta el 80 %).",
               "Heavy backpack slots slow you 0.8% less per point (up to 80%)."),
    "oxygen": ("Oxígeno", "Oxygen", "Aguantas más la respiración bajo el agua (33 puntos, como Respiración I).",
               "Hold your breath longer underwater (33 points = Respiration I)."),
}

TEXTS = {
    "screen.evolvedwarfare.attributes.title": ("Atributos", "Attributes"),
    "screen.evolvedwarfare.attributes.points": ("Puntos de atributo: %s", "Attribute points: %s"),
    "screen.evolvedwarfare.attributes.next": ("Con un punto más: %s", "With one more point: %s"),
    "screen.evolvedwarfare.attributes.cap": ("Ya en el máximo", "Already at the maximum"),
    "screen.evolvedwarfare.attributes.click": ("Clic: +1 · Mayús + clic: +5", "Click: +1 · Shift-click: +5"),
    "screen.evolvedwarfare.attributes.no_points": ("No te quedan puntos (cada nivel da uno)", "No points left (each level gives one)"),
    "screen.evolvedwarfare.attributes.hint": ("Cada nivel de superviviente da un punto de atributo.", "Each survivor level gives one attribute point."),
    "screen.evolvedwarfare.attributes.effect.health": ("+%s corazones", "+%s hearts"),
    "screen.evolvedwarfare.attributes.effect.stamina": ("−%s %% de gasto y +%1$s %% de recuperación", "−%s%% drain, +%1$s%% recovery"),
    "screen.evolvedwarfare.attributes.effect.melee": ("+%s %% de daño cuerpo a cuerpo", "+%s%% melee damage"),
    "screen.evolvedwarfare.attributes.effect.speed": ("+%s %% de velocidad", "+%s%% speed"),
    "screen.evolvedwarfare.attributes.effect.fortitude": ("−%s %% de daño de sangrado, veneno, frío, calor...", "−%s%% bleeding, venom, cold, heat... damage"),
    "screen.evolvedwarfare.attributes.effect.metabolism": ("El hambre y la sed, un %s %% más despacio", "Hunger and thirst %s%% slower"),
    "screen.evolvedwarfare.attributes.effect.weight": ("La carga pesada frena un %s %% menos", "Heavy load slows %s%% less"),
    "screen.evolvedwarfare.attributes.effect.oxygen": ("%s %% de no gastar aire", "%s%% chance not to use air"),
    "commands.evolvedwarfare.attributes.reset": ("Puntos de atributo devueltos a %s jugadores", "Attribute points refunded for %s players"),
}


def main():
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for key, (es, en, des, den) in ATTRS.items():
            data["attribute.%s.survivor.%s" % (NS, key)] = en if english else es
            data["attribute.%s.survivor.%s.desc" % (NS, key)] = den if english else des
        for key, (es, en) in TEXTS.items():
            data[key] = en if english else es
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(data, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
    print("atributos: %d" % len(ATTRS))


if __name__ == "__main__":
    main()
