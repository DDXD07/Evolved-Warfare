#!/usr/bin/env python3
"""La noche más dura: el icono de la infección, su tipo de daño y los textos (noches de zombis, configuración)."""
import collections
import glob
import json
import os
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..")
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
MC_DATA = os.path.join(ROOT, "src/main/resources/data/minecraft")


def write_json(path, obj, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=indent)
        f.write("\n")


def infection_icon():
    """Una mordedura verdosa: dos arcos de dientes sobre una mancha enferma."""
    im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    for y in range(18):
        for x in range(18):
            dx, dy = (x - 8.5) / 7.5, (y - 8.5) / 6.0
            r = dx * dx + dy * dy
            if r <= 1.0:
                g = int(120 + 60 * (1 - r))
                im.putpixel((x, y), (int(70 + 30 * (1 - r)), g, 50, 255))
    dark = (40, 20, 20, 255)
    tooth = (235, 230, 210, 255)
    for x in range(4, 14):
        top = 5 + (abs(x - 8.5) > 3)
        bot = 12 - (abs(x - 8.5) > 3)
        im.putpixel((x, top), dark)
        im.putpixel((x, bot), dark)
        if x % 2 == 0:
            im.putpixel((x, top + 1), tooth)
            im.putpixel((x, bot - 1), tooth)
    for (x, y) in [(6, 8), (11, 9), (9, 7)]:
        im.putpixel((x, y), (150, 30, 30, 255))
    path = os.path.join(ASSETS, "textures/mob_effect/infection.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


TEXTS = {
    "effect.evolvedwarfare.infection": ("Infección", "Infection"),
    "message.evolvedwarfare.infection": ("Te ha mordido un zombi: estás infectado (la leche te cura)",
                                         "A zombie bit you: you are infected (milk cures it)"),
    "message.evolvedwarfare.infection.worse": ("Otro mordisco: la infección empeora (la leche te cura)",
                                               "Another bite: the infection gets worse (milk cures it)"),
    "death.attack.evolvedwarfare.infection": ("%1$s murió por la infección", "%1$s died of an infection"),
    "death.attack.evolvedwarfare.infection.player": ("%1$s murió por la infección huyendo de %2$s",
                                                     "%1$s died of an infection whilst fighting %2$s"),
    "message.evolvedwarfare.zombies.today": ("Día %s: esta noche viene una horda de zombis. Prepárate.",
                                             "Day %s: a zombie horde comes tonight. Get ready."),
    "message.evolvedwarfare.zombies.come": ("¡Ya vienen! Una horda de %s zombis sabe dónde estás.",
                                            "Here they come! A horde of %s zombies knows where you are."),
    "config.evolvedwarfare.nightHostility": ("Noches hostiles", "Hostile nights"),
    "config.evolvedwarfare.nightHostility.tip": ("De noche los monstruos ven el doble de lejos y los endermans atacan en cuanto te ven.",
                                                 "At night monsters see twice as far and endermen attack as soon as they see you."),
    "config.evolvedwarfare.zombieHordeEveryDays": ("Noche de zombis cada (días)", "Zombie night every (days)"),
    "config.evolvedwarfare.zombieHordeEveryDays.tip": ("Ese día el contador se pone verde y al anochecer llega una horda de zombis (0 = nunca).",
                                                       "That day the counter turns green and a zombie horde comes at nightfall (0 = never)."),
    "config.evolvedwarfare.zombieHordeSize": ("Zombis por horda (mínimo)", "Zombies per horde (minimum)"),
    "config.evolvedwarfare.zombieHordeSize.tip": ("Sube uno cada noche de zombis (hasta el doble) y 5 más por cada jugador de más.",
                                                  "Grows by one each zombie night (up to double) and 5 more per extra player."),
    "config.evolvedwarfare.zombieBreakBlocks": ("Los zombis rompen bloques", "Zombies break blocks"),
    "config.evolvedwarfare.zombieBreakBlocks.tip": ("Rompen, excavan y se abren camino hasta ti (hasta la obsidiana, con su tiempo). También hace falta mobGriefing.",
                                                    "They break, dig and make their way to you (up to obsidian, given time). mobGriefing must be on too."),
    "config.evolvedwarfare.zombieInfectionChance": ("Probabilidad de infección (%)", "Infection chance (%)"),
    "config.evolvedwarfare.zombieInfectionChance.tip": ("Probabilidad de que el golpe de un zombi te infecte.",
                                                        "Chance that a zombie's hit infects you."),
}


ES_PHRASES = ['Se escuchan los muertos levantarse de su tumba', 'La tierra se agrieta: algo sale de debajo', 'Un gemido lejano rompe el silencio de la noche', 'Los muertos tienen hambre esta noche', 'Huele a podrido en el viento', 'Algo arrastra los pies en la oscuridad', 'Las tumbas se han quedado vacías', 'Se oyen uñas arañando la tierra', 'La noche se llena de lamentos', 'No están muertos del todo', 'Los perros dejan de ladrar de golpe', 'Una procesión de cadáveres viene hacia ti', 'El cementerio ha despertado', 'Gruñidos entre los árboles', 'Los que enterraste no descansan', 'La luna ilumina cuerpos que caminan', 'Pasos torpes se acercan desde todas partes', 'La muerte ha vuelto a ponerse en pie', 'Se oye carne desgarrándose a lo lejos', 'Vienen a por los vivos', 'Atranca la puerta: ya vienen', 'El silencio se rompe con un coro de gemidos', 'Manos podridas salen del barro', 'Ni la muerte los detiene', 'La niebla trae olor a tumba', 'Huesos que crujen en la noche', 'Tu sangre los llama', 'Sienten tu calor desde lejos', 'Los muertos no duermen esta noche', 'Una horda hambrienta despierta', 'La oscuridad se mueve sola', 'Algo golpea la tierra desde dentro', 'Los caídos se levantan otra vez', 'Esta noche, nadie está a salvo', 'Se acerca el hedor de la muerte', 'Que empiece la noche más larga']
EN_PHRASES = ['You hear the dead rising from their graves', 'The earth cracks: something crawls out from below', 'A distant moan breaks the silence of the night', 'The dead are hungry tonight', 'The wind smells of rot', 'Something drags its feet in the dark', 'The graves lie empty', 'You hear nails scratching at the soil', 'The night fills with wailing', 'They are not quite dead', 'The dogs suddenly stop barking', 'A procession of corpses is coming for you', 'The graveyard has awoken', 'Growls among the trees', 'Those you buried do not rest', 'The moon lights up walking bodies', 'Clumsy footsteps close in from every side', 'Death has stood up again', 'Flesh tears somewhere in the distance', 'They come for the living', 'Bar the door: they are coming', 'The silence breaks into a choir of moans', 'Rotting hands rise out of the mud', 'Not even death stops them', 'The fog carries the smell of the grave', 'Bones creak in the night', 'Your blood calls to them', 'They feel your warmth from afar', 'The dead do not sleep tonight', 'A starving horde awakens', 'The darkness moves on its own', 'Something pounds the earth from within', 'The fallen rise once more', 'Tonight, no one is safe', 'The stench of death draws near', 'Let the longest night begin']
for _i, (_es, _en) in enumerate(zip(ES_PHRASES, EN_PHRASES)):
    TEXTS["message.evolvedwarfare.zombies.phrase.%d" % _i] = (_es + "...", _en + "...")


def main():
    infection_icon()
    write_json(os.path.join(DATA, "damage_type/infection.json"),
               {"exhaustion": 0.0, "message_id": NS + ".infection", "scaling": "never"})
    for tag in ("bypasses_armor", "no_knockback"):
        path = os.path.join(MC_DATA, "tags/damage_type", tag + ".json")
        t = {"replace": False, "values": []}
        if os.path.exists(path):
            with open(path) as f:
                t = json.load(f)
        if NS + ":infection" not in t["values"]:
            t["values"].append(NS + ":infection")
        write_json(path, t)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    print("noche: icono de la infección, tipo de daño y textos")


if __name__ == "__main__":
    main()
