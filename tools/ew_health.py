"""La salud al estilo RLCraft: vendas, entablillados y cristales de corazón (fase 92); mosto, aguardiente, medicina,
antídoto y botiquín, el veneno fuerte y el desinfectado (fase 93). Iconos, modelos, recetas, tipos de daño y textos.

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

ALCOHOL = [
    "................",
    "......oo........",
    "......cc........",
    "......oo........",
    ".....oaao.......",
    ".....oaao.......",
    "....oaaaao......",
    "...oaaaaaao.....",
    "...oalaaaao.....",
    "...oallllao.....",
    "...oallllao.....",
    "...oalllllo.....",
    "...oallllao.....",
    "....oaaaao......",
    ".....oooo.......",
    "................",
]
MASH = [
    "................",
    "................",
    "......oo........",
    "......cc........",
    "......oo........",
    ".....oaao.......",
    "....oaaaao......",
    "...oaaaaaao.....",
    "...oammmmao.....",
    "...omymmymo.....",
    "...ommymmmo.....",
    "...omymmmyo.....",
    "...ommmmmmo.....",
    "....ommmmo......",
    ".....oooo.......",
    "................",
]
MEDKIT = [
    "................",
    "................",
    "......oooo......",
    ".....o....o.....",
    "..oooooooooooo..",
    ".orrrrrrrrrrrro.",
    ".orrrrrwwrrrrro.",
    ".orrrrrwwrrrrro.",
    ".orrrwwwwwwrrro.",
    ".orrrwwwwwwrrro.",
    ".orrrrrwwrrrrro.",
    ".orrrrrwwrrrrro.",
    ".odddddddddddddo",
    "..oooooooooooo..",
    "................",
    "................",
]
PAL_BOTTLE = {"o": (40, 40, 52, 255), "c": (150, 110, 70, 255), "a": (200, 225, 235, 200)}
PAL_ALCOHOL = dict(PAL_BOTTLE, l=(236, 200, 120, 255))
PAL_MEDICINE = dict(PAL_BOTTLE, l=(220, 70, 70, 255))
PAL_ANTIDOTE = dict(PAL_BOTTLE, l=(120, 210, 90, 255))
PAL_MASH = dict(PAL_BOTTLE, m=(176, 140, 72, 255), y=(230, 210, 140, 255))
PAL_MEDKIT = {"o": (50, 20, 20, 255), "r": (196, 40, 40, 255), "d": (130, 24, 24, 255), "w": (245, 245, 245, 255)}


def effect_icon(kind):
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    if kind == "venom":
        # una gota verde con una calavera clara
        for y in range(2, 17):
            for x in range(2, 16):
                dx, dy = x - 8.5, y - 11
                inside = (dx * dx + dy * dy <= 25) or (y < 11 and abs(dx) <= (y - 2) * 0.6)
                if inside:
                    img.putpixel((x, y), (60, 150, 40, 255) if dx < 1 else (40, 110, 30, 255))
        for x, y in ((7, 10), (10, 10), (8, 13), (9, 13)):
            img.putpixel((x, y), (20, 40, 10, 255))
    else:
        # una cruz blanca con borde dorado
        for y in range(2, 16):
            for x in range(2, 16):
                if 6 <= x <= 11 or 6 <= y <= 11:
                    edge = x in (6, 11) and not 6 <= y <= 11 or y in (6, 11) and not 6 <= x <= 11 or x in (2, 15) or y in (2, 15)
                    img.putpixel((x, y), (200, 160, 70, 255) if edge else (250, 248, 236, 255))
    return img


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
    "fermented_mash": ("Mosto fermentado", "Fermented Mash",
                       "Trigo y azúcar fermentando. Destílalo en un horno para sacar aguardiente.",
                       "Wheat and sugar, fermenting. Distil it in a furnace to make spirits."),
    "alcohol": ("Aguardiente", "Spirits",
                "Desinfecta las heridas (3 minutos sin infectarse) y baja un grado la infección. Marea un poco.",
                "Disinfects your wounds (3 minutes without infection) and lowers infection by one level. Makes you a bit dizzy."),
    "medicine": ("Medicina", "Medicine",
                 "Cura la infección y los parásitos.", "Cures infection and parasites."),
    "antidote": ("Antídoto", "Antidote",
                 "Cura el veneno (el de las arañas también; la leche ya no).", "Cures poison (spider venom too; milk no longer does)."),
    "medkit": ("Botiquín", "Medkit",
               "Lo cura todo: corta la sangre, cura cada parte, entablilla los huesos rotos y quita la infección y los venenos.",
               "Treats everything: stops bleeding, heals every part, splints broken bones and removes infection and venom."),
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
    "effect.evolvedwarfare.venom": ("Veneno fuerte", "Venom"),
    "effect.evolvedwarfare.disinfected": ("Desinfectado", "Disinfected"),
    "death.attack.evolvedwarfare.venom": ("%1$s murió envenenado", "%1$s succumbed to venom"),
    "death.attack.evolvedwarfare.venom.player": ("%1$s murió envenenado huyendo de %2$s", "%1$s succumbed to venom whilst fighting %2$s"),
    "message.evolvedwarfare.medicine.venom": ("¡Te han envenenado! Bebe un antídoto (la leche no sirve).",
                                              "You have been envenomed! Drink an antidote (milk won't help)."),
    "message.evolvedwarfare.medicine.wound_infected": ("La herida se ha infectado: aguardiente para bajarla, medicina para curarla.",
                                                       "Your wound got infected: spirits to ease it, medicine to cure it."),
    "message.evolvedwarfare.medicine.worse": ("La infección empeora sin tratar: necesitas medicina.",
                                              "The untreated infection gets worse: you need medicine."),
    "message.evolvedwarfare.medicine.alcohol": ("Te arde la garganta... pero las heridas quedan limpias.",
                                                "It burns going down... but your wounds are clean."),
    "message.evolvedwarfare.medicine.medicine": ("La medicina hace efecto.", "The medicine takes effect."),
    "message.evolvedwarfare.medicine.antidote": ("El antídoto corta el veneno.", "The antidote neutralises the venom."),
    "message.evolvedwarfare.medicine.medkit": ("Te has curado con el botiquín.", "You patched yourself up with the medkit."),
    "message.evolvedwarfare.medicine.nothing": ("No tienes nada que curar.", "You have nothing to treat."),
    "message.evolvedwarfare.infection": ("Te ha mordido un zombi: estás infectado (aguardiente o medicina)",
                                         "A zombie bit you: you are infected (spirits or medicine)"),
    "message.evolvedwarfare.infection.worse": ("Otro mordisco: la infección empeora (necesitas medicina)",
                                               "Another bite: the infection gets worse (you need medicine)"),
    "config.evolvedwarfare.medicineEnabled": ("Medicina", "Medicine"),
    "config.evolvedwarfare.medicineEnabled.tip": (
        "Las heridas se infectan, las arañas envenenan y la leche ya no cura la infección ni el veneno fuerte.",
        "Wounds get infected, spiders envenom and milk no longer cures infection or venom."),
    "config.evolvedwarfare.woundInfectionChance": ("Infección de heridas (%)", "Wound infection (%)"),
    "config.evolvedwarfare.woundInfectionChance.tip": ("Probabilidad por minuto de que se infecte una herida que sangra sin desinfectar.",
                                                       "Chance per minute that an undisinfected bleeding wound gets infected."),
    "config.evolvedwarfare.venomChance": ("Veneno de araña (%)", "Spider venom (%)"),
    "config.evolvedwarfare.venomChance.tip": ("Probabilidad de que una araña envenene al morder (la de cueva, siempre).",
                                              "Chance that a spider bite envenoms (cave spiders always do)."),
    "key.evolvedwarfare.health": ("Salud (tu cuerpo y tus heridas)", "Health (your body and wounds)"),
    "screen.evolvedwarfare.health.title": ("Salud", "Health"),
    "screen.evolvedwarfare.health.disabled": ("Las heridas están desactivadas en la configuración.", "Wounds are turned off in the config."),
    "health.evolvedwarfare.part_title.head": ("Cabeza", "Head"),
    "health.evolvedwarfare.part_title.torso": ("Torso", "Torso"),
    "health.evolvedwarfare.part_title.right_arm": ("Brazo derecho", "Right arm"),
    "health.evolvedwarfare.part_title.left_arm": ("Brazo izquierdo", "Left arm"),
    "health.evolvedwarfare.part_title.right_leg": ("Pierna derecha", "Right leg"),
    "health.evolvedwarfare.part_title.left_leg": ("Pierna izquierda", "Left leg"),
    "screen.evolvedwarfare.health.state.healthy": ("Sana", "Healthy"),
    "screen.evolvedwarfare.health.state.bruised": ("Rasguños", "Bruised"),
    "screen.evolvedwarfare.health.state.wounded": ("Herida", "Wounded"),
    "screen.evolvedwarfare.health.state.critical": ("Grave", "Critical"),
    "screen.evolvedwarfare.health.state.destroyed": ("Sin vida", "Destroyed"),
    "screen.evolvedwarfare.health.state.broken": ("Hueso roto", "Broken bone"),
    "screen.evolvedwarfare.health.state.splinted": ("Entablillado %s", "Splinted %s"),
    "screen.evolvedwarfare.health.status": ("Cómo estás", "Condition"),
    "screen.evolvedwarfare.health.hearts": ("Corazones: %s / %s", "Hearts: %s / %s"),
    "screen.evolvedwarfare.health.bleeding": ("Sangrando: %s s", "Bleeding: %s s"),
    "screen.evolvedwarfare.health.effect_graded.infection": ("Infección grado %s (%s)", "Infection level %s (%s)"),
    "screen.evolvedwarfare.health.effect_graded.venom": ("Veneno fuerte grado %s (%s)", "Venom level %s (%s)"),
    "screen.evolvedwarfare.health.effect.poison": ("Envenenado (%s)", "Poisoned (%s)"),
    "screen.evolvedwarfare.health.effect.parasites": ("Parásitos (%s)", "Parasites (%s)"),
    "screen.evolvedwarfare.health.effect.disinfected": ("Desinfectado (%s)", "Disinfected (%s)"),
    "screen.evolvedwarfare.health.regen.slow": ("Regeneración: lenta", "Regeneration: slow"),
    "screen.evolvedwarfare.health.regen.stopped": ("Regeneración: parada", "Regeneration: stopped"),
    "screen.evolvedwarfare.health.advice": ("Qué hacer", "What to do"),
    "screen.evolvedwarfare.health.advice.bandage": ("Ponte una venda para cortar la sangre.", "Apply a bandage to stop the bleeding."),
    "screen.evolvedwarfare.health.advice.splint": ("Entablilla %s.", "Splint your %s."),
    "screen.evolvedwarfare.health.advice.infection": ("Bebe medicina (el aguardiente solo la baja).",
                                                      "Drink medicine (spirits only ease it)."),
    "screen.evolvedwarfare.health.advice.venom": ("Bebe un antídoto.", "Drink an antidote."),
    "screen.evolvedwarfare.health.advice.parasites": ("Bebe medicina o leche, o come ajo.", "Drink medicine or milk, or eat garlic."),
    "screen.evolvedwarfare.health.advice.rest": ("Come bien y descansa: las partes se curan solas, y dormir en la cama cura mucho.",
                                                 "Eat well and rest: parts heal on their own, and sleeping in a bed heals a lot."),
    "screen.evolvedwarfare.health.advice.fine": ("Estás sano.", "You are healthy."),
    "screen.evolvedwarfare.health.danger": ("Peligro aquí: nivel %s", "Danger here: level %s"),
    "elite.evolvedwarfare.name": ("★ %1$s %2$s", "★ %2$s %1$s"),
    "elite.evolvedwarfare.and": ("%s y %s", "%s %s"),
    "elite.evolvedwarfare.comma": ("%s, %s", "%s %s"),
    "elite.evolvedwarfare.affix.burning": ("de Fuego", "Fiery"),
    "elite.evolvedwarfare.affix.frost": ("de Escarcha", "Frost"),
    "elite.evolvedwarfare.affix.vampiric": ("de Sangre", "Vampiric"),
    "elite.evolvedwarfare.affix.venomous": ("de Veneno", "Venomous"),
    "elite.evolvedwarfare.affix.swift": ("del Viento", "Swift"),
    "elite.evolvedwarfare.affix.armored": ("de Hierro", "Armored"),
    "elite.evolvedwarfare.affix.regenerating": ("de la Hidra", "Regenerating"),
    "elite.evolvedwarfare.affix.explosive": ("de Pólvora", "Explosive"),
    "elite.evolvedwarfare.affix.berserk": ("de Furia", "Berserk"),
    "elite.evolvedwarfare.affix.withering": ("de Ceniza", "Withering"),
    "config.evolvedwarfare.category.difficulty": ("Dificultad", "Difficulty"),
    "config.evolvedwarfare.scalingEnabled": ("Dificultad que sube", "Scaling difficulty"),
    "config.evolvedwarfare.scalingEnabled.tip": (
        "Los monstruos tienen un nivel de peligro según la distancia al punto de aparición, los días del mundo y la dimensión.",
        "Monsters get a danger level from the distance to world spawn, the world's age in days and the dimension."),
    "config.evolvedwarfare.difficultyBlocksPerLevel": ("Bloques por nivel", "Blocks per level"),
    "config.evolvedwarfare.difficultyBlocksPerLevel.tip": ("Cada cuántos bloques desde el punto de aparición sube un nivel.",
                                                           "How many blocks from spawn per danger level."),
    "config.evolvedwarfare.difficultyDaysPerLevel": ("Días por nivel", "Days per level"),
    "config.evolvedwarfare.difficultyDaysPerLevel.tip": ("Cada cuántos días del mundo sube un nivel.", "How many in-game days per danger level."),
    "config.evolvedwarfare.difficultyMaxLevel": ("Nivel máximo", "Maximum level"),
    "config.evolvedwarfare.difficultyMaxLevel.tip": ("Hasta dónde llega el nivel de peligro.", "The highest danger level."),
    "config.evolvedwarfare.difficultyHealthPerLevel": ("Vida por nivel", "Health per level"),
    "config.evolvedwarfare.difficultyHealthPerLevel.tip": ("Vida extra de los monstruos por cada nivel (0.10 = +10 %).",
                                                           "Extra monster health per level (0.10 = +10%)."),
    "config.evolvedwarfare.difficultyDamagePerLevel": ("Daño por nivel", "Damage per level"),
    "config.evolvedwarfare.difficultyDamagePerLevel.tip": ("Daño extra de los monstruos por cada nivel (0.05 = +5 %).",
                                                           "Extra monster damage per level (0.05 = +5%)."),
    "config.evolvedwarfare.elitesEnabled": ("Monstruos élite", "Elite monsters"),
    "config.evolvedwarfare.elitesEnabled.tip": ("Algunos monstruos salen élite: más grandes, más fuertes, con rasgos y mejor botín.",
                                                "Some monsters spawn as elites: bigger, stronger, with traits and better loot."),
    "config.evolvedwarfare.eliteChance": ("Probabilidad de élite", "Elite chance"),
    "config.evolvedwarfare.eliteChance.tip": ("Probabilidad base de que un monstruo salga élite.", "Base chance for a monster to be an elite."),
    "config.evolvedwarfare.eliteChancePerLevel": ("Élite por nivel", "Elite chance per level"),
    "config.evolvedwarfare.eliteChancePerLevel.tip": ("Lo que sube la probabilidad de élite con cada nivel de peligro (máximo 30 %).",
                                                      "Extra elite chance per danger level (up to 30%)."),
    "config.evolvedwarfare.smarterMobs": ("Monstruos más listos", "Smarter monsters"),
    "config.evolvedwarfare.smarterMobs.tip": (
        "Se avisan entre ellos, los esqueletos apuntan adelantándose y se apartan, huyen muy heridos, esquivan cuando les apuntas y los creepers revientan paredes.",
        "They alert each other, skeletons lead their shots and keep their distance, they flee when badly hurt, dodge when you aim and creepers blow through walls."),
    "config.evolvedwarfare.spiderWebs": ("Telarañas de las arañas", "Spider webs"),
    "config.evolvedwarfare.spiderWebs.tip": ("Las arañas te escupen telaraña a los pies desde lejos (se deshace a los 15 s).",
                                             "Spiders spit webs at your feet from afar (they dissolve after 15 s)."),
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
    "fermented_mash": {"type": "minecraft:crafting_shapeless", "category": "misc",
                       "ingredients": [{"item": "minecraft:glass_bottle"}, {"item": "minecraft:wheat"}, {"item": "minecraft:wheat"},
                                       {"item": "minecraft:sugar"}],
                       "result": {"id": "%s:fermented_mash" % NS, "count": 1}},
    "alcohol": {"type": "minecraft:smelting", "category": "misc", "ingredient": {"item": "%s:fermented_mash" % NS},
                "result": {"id": "%s:alcohol" % NS}, "experience": 0.2, "cookingtime": 300},
    "medicine": {"type": "minecraft:crafting_shapeless", "category": "misc",
                 "ingredients": [{"item": "%s:alcohol" % NS}, {"item": "minecraft:brown_mushroom"}, {"item": "minecraft:red_mushroom"},
                                 {"item": "minecraft:honeycomb"}],
                 "result": {"id": "%s:medicine" % NS, "count": 1}},
    "antidote": {"type": "minecraft:crafting_shapeless", "category": "misc",
                 "ingredients": [{"item": "minecraft:glass_bottle"}, {"item": "minecraft:spider_eye"}, {"item": "minecraft:charcoal"},
                                 {"item": "minecraft:sweet_berries"}],
                 "result": {"id": "%s:antidote" % NS, "count": 1}},
    "medkit": {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["LBL", "AMS", "LBL"],
               "key": {"L": {"item": "minecraft:leather"}, "B": {"item": "%s:bandage" % NS}, "A": {"item": "%s:alcohol" % NS},
                       "M": {"item": "%s:medicine" % NS}, "S": {"item": "%s:splint" % NS}},
               "result": {"id": "%s:medkit" % NS, "count": 1}},
}


def main():
    item_dir = os.path.join(ASSETS, "textures/item")
    os.makedirs(item_dir, exist_ok=True)
    sprite(BANDAGE, PAL_BANDAGE).save(os.path.join(item_dir, "bandage.png"))
    sprite(SPLINT, PAL_SPLINT).save(os.path.join(item_dir, "splint.png"))
    sprite(HEART, PAL_HEART).save(os.path.join(item_dir, "heart_crystal.png"))
    sprite(MASH, PAL_MASH).save(os.path.join(item_dir, "fermented_mash.png"))
    sprite(ALCOHOL, PAL_ALCOHOL).save(os.path.join(item_dir, "alcohol.png"))
    sprite(ALCOHOL, PAL_MEDICINE).save(os.path.join(item_dir, "medicine.png"))
    sprite(ALCOHOL, PAL_ANTIDOTE).save(os.path.join(item_dir, "antidote.png"))
    sprite(MEDKIT, PAL_MEDKIT).save(os.path.join(item_dir, "medkit.png"))
    for kind in ("venom", "disinfected"):
        effect_icon(kind).save(os.path.join(ASSETS, "textures/mob_effect/%s.png" % kind))
    for name in ITEMS:
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    for name, recipe in RECIPES.items():
        write_json(os.path.join(DATA, "recipe/%s.json" % name), recipe)
    for dt in ("bleeding", "venom"):
        write_json(os.path.join(DATA, "damage_type/%s.json" % dt),
                   {"exhaustion": 0.0, "message_id": "evolvedwarfare.%s" % dt, "scaling": "never"})
    for tag in ("bypasses_armor", "no_knockback"):
        path = os.path.join(MC_DATA, "tags/damage_type/%s.json" % tag)
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for dt in ("bleeding", "venom"):
            if "%s:%s" % (NS, dt) not in data["values"]:
                data["values"].append("%s:%s" % (NS, dt))
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
