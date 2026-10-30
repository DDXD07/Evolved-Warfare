"""El árbol de habilidades (tipo Skyrim): textos de las habilidades, las ventajas, la pantalla y los avisos, en los ocho
idiomas del mod (español para todos los es_*, inglés para en_us), y el icono del efecto "Sangrado". Quita los textos de
la investigación de antes.

Uso: python3 tools/ew_skills.py <raíz del proyecto>
"""
import collections
import glob
import json
import os
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
LANG = os.path.join(ROOT, "src/main/resources/assets/evolvedwarfare/lang")

SKILLS = {
    # id: (es, cómo sube (es), en, how (en))
    "blades": ("Hojas", "Sube al golpear con espadas, dagas y mandobles.", "Blades", "Rises by hitting with swords, daggers and longswords."),
    "axes": ("Hachas", "Sube al golpear con hachas y hachas de batalla.", "Axes", "Rises by hitting with axes and battle axes."),
    "maces": ("Mazas", "Sube al golpear con martillos de guerra, manguales y mazas.", "Maces",
              "Rises by hitting with warhammers, flails and maces."),
    "polearms": ("Astas", "Sube al golpear con lanzas, alabardas y tridentes.", "Polearms", "Rises by hitting with spears, halberds and tridents."),
    "archery": ("Arquería", "Sube al acertar con arcos, ballestas y jabalinas.", "Archery", "Rises by hitting with bows, crossbows and javelins."),
    "firearms": ("Armas de fuego", "Sube al acertar con el mosquete.", "Firearms", "Rises by hitting with the musket."),
    "block": ("Escudo", "Sube al parar golpes con el escudo.", "Block", "Rises by blocking hits with a shield."),
    "heavy_armor": ("Armadura pesada", "Sube al recibir golpes con armadura de hierro, diamante o netherita.", "Heavy Armor",
                    "Rises by taking hits in iron, diamond or netherite armor."),
    "light_armor": ("Armadura ligera", "Sube al recibir golpes con armadura de cuero, cota de malla, oro o tortuga.", "Light Armor",
                    "Rises by taking hits in leather, chainmail, gold or turtle armor."),
    "leadership": ("Liderazgo", "Sube cuando tus soldados luchan y cuando cobran su paga.", "Leadership",
                   "Rises when your soldiers fight and when they get their pay."),
    "siege": ("Asedio", "Sube al acertar y al romper muros con las máquinas de asedio.", "Siege",
              "Rises by hitting and breaking walls with siege engines."),
}
GROUPS = {"melee": ("Cuerpo a cuerpo", "Melee"), "ranged": ("A distancia", "Ranged"), "defense": ("Defensa", "Defense"),
          "command": ("Mando", "Command")}

PERKS = {
    # id: (nombre, descripción, name, description) — %s es el valor del rango
    "blade_mastery": ("Maestría con hojas", "+%s %% de daño con espadas, dagas y mandobles.", "Blade Mastery",
                      "+%s%% damage with swords, daggers and longswords."),
    "dagger_smith": ("Forja de dagas", "Puedes fabricar dagas.", "Dagger Smithing", "You can craft daggers."),
    "backstab": ("Puñalada trapera", "Por la espalda, +%s %% de daño con las hojas (las dagas ya hacen más).", "Backstab",
                 "From behind, +%s%% damage with blades (daggers already deal more)."),
    "blade_dance": ("Danza de acero", "+%s %% de velocidad de ataque con hojas.", "Steel Dance", "+%s%% attack speed with blades."),
    "longsword_smith": ("Forja de mandobles", "Puedes fabricar mandobles.", "Longsword Smithing", "You can craft longswords."),
    "blade_crit": ("Golpe certero", "%s %% de probabilidad de golpe crítico con hojas (×1,5).", "True Strike",
                   "%s%% chance of a critical hit with blades (×1.5)."),
    "whirlwind": ("Torbellino", "El barrido de las hojas hace +%s %% del daño del golpe.", "Whirlwind",
                  "Blade sweeps deal +%s%% of the hit's damage."),
    "axe_mastery": ("Maestría con hachas", "+%s %% de daño con hachas.", "Axe Mastery", "+%s%% damage with axes."),
    "battle_axe_smith": ("Forja de hachas de batalla", "Puedes fabricar hachas de batalla.", "Battle Axe Smithing",
                         "You can craft battle axes."),
    "lumberjack": ("Leñador", "+%s %% de velocidad al picar con un hacha.", "Lumberjack", "+%s%% mining speed with an axe."),
    "bleed": ("Sangrado", "Los hachazos hacen sangrar: %s de daño por segundo durante 3 s.", "Bleed",
              "Axe hits cause bleeding: %s damage per second for 3 s."),
    "heavy_chop": ("Hachazo", "Los golpes críticos (saltando) con hacha hacen +%s %%.", "Heavy Chop",
                   "Critical (jumping) hits with axes deal +%s%%."),
    "butcher": ("Carnicero", "+%s %% de daño a quien esté sangrando.", "Butcher", "+%s%% damage to bleeding targets."),
    "fury": ("Furia", "Matar con un hacha te da fuerza durante %s s.", "Fury", "Killing with an axe grants Strength for %s s."),
    "mace_mastery": ("Maestría con mazas", "+%s %% de daño con martillos, manguales y mazas.", "Mace Mastery",
                     "+%s%% damage with warhammers, flails and maces."),
    "mace_smith": ("Forja de mazas", "Puedes fabricar martillos de guerra y manguales.", "Mace Smithing",
                   "You can craft warhammers and flails."),
    "bonebreaker": ("Rompehuesos", "Las mazas atraviesan armadura: +%s %% de la armadura del blanco en daño.", "Bonebreaker",
                    "Maces pierce armor: +%s%% of the target's armor as damage."),
    "stun": ("Aturdir", "%s %% de probabilidad de aturdir (lentitud y debilidad 2 s).", "Stun",
             "%s%% chance to stun (slowness and weakness for 2 s)."),
    "executioner": ("Golpe de gracia", "+%s %% de daño a los que tienen menos de un 30 %% de vida.", "Executioner",
                    "+%s%% damage to targets below 30%% health."),
    "concussion": ("Conmoción", "Los críticos con maza marean y empujan.", "Concussion", "Critical mace hits cause nausea and knockback."),
    "crush": ("Aplastar", "Los críticos con maza dañan también a los de alrededor (%s %% del golpe).", "Crush",
              "Critical mace hits also damage those around (%s%% of the hit)."),
    "pole_mastery": ("Maestría con astas", "+%s %% de daño con lanzas, alabardas y tridentes.", "Polearm Mastery",
                     "+%s%% damage with spears, halberds and tridents."),
    "spear_smith": ("Forja de lanzas", "Puedes fabricar lanzas.", "Spear Smithing", "You can craft spears."),
    "keep_at_bay": ("Mantener a raya", "+%s bloques de alcance con armas de asta.", "Keep at Bay", "+%s blocks of reach with polearms."),
    "lunge": ("Estocada", "Corriendo, las armas de asta hacen +%s %%.", "Lunge", "While sprinting, polearms deal +%s%%."),
    "halberd_smith": ("Forja de alabardas", "Puedes fabricar alabardas.", "Halberd Smithing", "You can craft halberds."),
    "anti_cavalry": ("Contra jinetes", "+%s %% de daño a jinetes y monturas.", "Anti-Cavalry", "+%s%% damage to riders and mounts."),
    "pike_wall": ("Muro de picas", "Con un arma de asta, quien te golpea de frente sale empujado.", "Pike Wall",
                  "Holding a polearm, whoever hits you from the front is pushed back."),
    "impale": ("Empalar", "Corriendo, las armas de asta clavan al blanco en el sitio %s s.", "Impale",
               "While sprinting, polearms pin the target in place for %s s."),
    "overdraw": ("Tensar", "+%s %% de daño con flechas, virotes y jabalinas.", "Overdraw", "+%s%% damage with arrows, bolts and javelins."),
    "javelin_smith": ("Jabalinas", "Puedes fabricar jabalinas.", "Javelins", "You can craft javelins."),
    "longbow_smith": ("Arco largo", "Puedes fabricar arcos largos.", "Longbow", "You can craft longbows."),
    "hunter": ("Cazador", "+%s %% de daño a los animales.", "Hunter", "+%s%% damage to animals."),
    "recovery": ("Recuperar flechas", "%s %% de probabilidad de recuperar la flecha al acertar.", "Arrow Recovery",
                 "%s%% chance to get the arrow back on a hit."),
    "crit_shot": ("Tiro crítico", "%s %% de probabilidad de disparo crítico (×1,5).", "Critical Shot",
                  "%s%% chance of a critical shot (×1.5)."),
    "bullseye": ("Diana", "Los disparos a la cabeza hacen +%s %%.", "Bullseye", "Headshots deal +%s%%."),
    "ranger": ("Explorador", "+%s %% de velocidad con un arco o una ballesta en la mano.", "Ranger",
               "+%s%% speed with a bow or crossbow in hand."),
    "fine_powder": ("Pólvora fina", "+%s %% de daño con el mosquete.", "Fine Powder", "+%s%% damage with the musket."),
    "spare_balls": ("Bolsillo de balas", "%s %% de probabilidad de recargar sin gastar bala.", "Spare Balls",
                    "%s%% chance to reload without using a ball."),
    "quick_reload": ("Recarga rápida", "Recargas un %s %% más rápido.", "Quick Reload", "You reload %s%% faster."),
    "steady_aim": ("Pulso firme", "Apuntando con la mira, +%s %% de daño.", "Steady Aim", "Aiming down the sights, +%s%% damage."),
    "sharpshooter": ("Tirador de élite", "Los disparos a la cabeza hacen +%s %%.", "Sharpshooter", "Headshots deal +%s%%."),
    "thunder": ("Atronador", "Los balazos empujan lejos y frenan al blanco.", "Thunderclap", "Musket hits knock far back and slow the target."),
    "sturdy_shield": ("Escudo recio", "El escudo se gasta un %s %% menos.", "Sturdy Shield", "Your shield wears %s%% less."),
    "immovable": ("Inamovible", "Cubriéndote con el escudo, no te empujan.", "Immovable", "While blocking, you can't be knocked back."),
    "quick_guard": ("Guardia rápida", "Si un hacha te rompe la guardia, recuperas el escudo en %s s.", "Quick Guard",
                    "If an axe breaks your guard, you get your shield back in %s s."),
    "counter": ("Contraataque", "Devuelves al atacante un %s %% de lo que paras.", "Counterattack",
                "You return %s%% of what you block to the attacker."),
    "shield_bash": ("Golpe de escudo", "Al parar un golpe, el atacante queda frenado %s s.", "Shield Bash",
                    "When you block a hit, the attacker is slowed for %s s."),
    "shield_wall": ("Muro de escudos", "Cubriéndote, tus soldados cercanos reciben un %s %% menos de daño.", "Shield Wall",
                    "While blocking, your soldiers nearby take %s%% less damage."),
    "juggernaut": ("Coloso", "+%s %% de armadura con armadura pesada (según las piezas).", "Juggernaut",
                   "+%s%% armor with heavy armor (by pieces worn)."),
    "well_fitted": ("Bien ajustada", "Con las cuatro piezas pesadas, +%s %% de armadura.", "Well Fitted",
                    "Wearing all four heavy pieces, +%s%% armor."),
    "steadfast": ("Firme como una roca", "Con dos o más piezas pesadas, +%s %% de resistencia al empuje.", "Steadfast",
                  "With two or more heavy pieces, +%s%% knockback resistance."),
    "conqueror": ("Conquistador", "Con las cuatro piezas pesadas, +%s %% de daño cuerpo a cuerpo.", "Conqueror",
                  "Wearing all four heavy pieces, +%s%% melee damage."),
    "cushioned": ("Acolchado", "Con armadura pesada, las caídas te hacen un %s %% menos.", "Cushioned",
                  "In heavy armor, falls deal %s%% less."),
    "reflect_blows": ("Devolver golpes", "%s %% de probabilidad de devolver un golpe cuerpo a cuerpo sin recibirlo.", "Reflect Blows",
                      "%s%% chance to reflect a melee hit back without taking it."),
    "agile_defense": ("Defensa ágil", "+%s %% de armadura con armadura ligera (según las piezas).", "Agile Defense",
                      "+%s%% armor with light armor (by pieces worn)."),
    "light_step": ("Paso ligero", "Solo con armadura ligera, +%s %% de velocidad.", "Light Step", "Wearing only light armor, +%s%% speed."),
    "custom_fit": ("A medida", "Con las cuatro piezas ligeras, +%s %% de armadura.", "Custom Fit", "Wearing all four light pieces, +%s%% armor."),
    "evasion": ("Evasión", "%s %% de probabilidad de esquivar un ataque (dos o más piezas ligeras).", "Evasion",
                "%s%% chance to dodge an attack (two or more light pieces)."),
    "second_wind": ("Segundo aliento", "Con poca vida, velocidad y regeneración durante %s s (una vez por minuto).", "Second Wind",
                    "At low health, speed and regeneration for %s s (once a minute)."),
    "cat_fall": ("Caída de gato", "Con armadura ligera, las caídas te hacen un %s %% menos.", "Cat's Fall",
                 "In light armor, falls deal %s%% less."),
    "drill": ("Instrucción", "Tus soldados tienen +%s %% de vida.", "Drill", "Your soldiers have +%s%% health."),
    "tactics": ("Tácticas", "Tus soldados hacen +%s %% de daño.", "Tactics", "Your soldiers deal +%s%% damage."),
    "field_medicine": ("Medicina de campaña", "Tus médicos curan un %s %% más y tus soldados se recuperan solos.", "Field Medicine",
                       "Your medics heal %s%% more and your soldiers recover on their own."),
    "quartermaster": ("Intendencia", "El %s %% de los días, tus soldados no gastan su paga.", "Quartermaster",
                      "%s%% of days, your soldiers don't use up their pay."),
    "inspire": ("Inspirar", "Tus soldados a menos de %s bloques de ti tienen fuerza.", "Inspire",
                "Your soldiers within %s blocks of you have Strength."),
    "veterans": ("Veteranos", "Tus soldados ganan un %s %% más de experiencia.", "Veterans", "Your soldiers gain %s%% more experience."),
    "banner": ("Estandarte", "Tus soldados cerca de ti tienen resistencia.", "Banner", "Your soldiers near you have Resistance."),
    "ram_smith": ("Carpintería de asedio", "Puedes fabricar el ariete.", "Siege Carpentry", "You can craft the battering ram."),
    "fortification": ("Fortificación", "Puedes fabricar el rastrillo, el puente levadizo y el torno.", "Fortification",
                      "You can craft the portcullis, the drawbridge and the winch."),
    "torsion": ("Máquinas de torsión", "Puedes fabricar la catapulta, la balista y los virotes.", "Torsion Engines",
                "You can craft the catapult, the ballista and bolts."),
    "gunner": ("Artillero", "Las máquinas de asedio que manejas recargan un %s %% antes.", "Gunner",
               "Siege engines you man reload %s%% faster."),
    "incendiaries": ("Fuego de asedio", "Puedes fabricar vasijas incendiarias.", "Siege Fire", "You can craft fire pots."),
    "thrift": ("Ahorro", "%s %% de probabilidad de disparar sin gastar munición.", "Thrift", "%s%% chance to fire without using ammo."),
    "counterweight": ("Contrapeso", "Puedes fabricar el trebuchet.", "Counterweight", "You can craft the trebuchet."),
    "sapper": ("Zapador", "Tus máquinas de asedio rompen muros con un %s %% más de fuerza.", "Sapper",
               "Your siege engines break walls with %s%% more force."),
}

TEXTS = {
    # clave: (es, en)
    "key.evolvedwarfare.skills": ("Árbol de habilidades", "Skill tree"),
    "screen.evolvedwarfare.skills.title": ("Habilidades", "Skills"),
    "screen.evolvedwarfare.skills.level": ("Nivel %s", "Level %s"),
    "screen.evolvedwarfare.skills.skill_level": ("Nivel %s", "Level %s"),
    "screen.evolvedwarfare.skills.points": ("Puntos de ventaja: %s", "Perk points: %s"),
    "screen.evolvedwarfare.skills.rank": ("Rango %s/%s", "Rank %s/%s"),
    "screen.evolvedwarfare.skills.now": ("Ahora: %s", "Now: %s"),
    "screen.evolvedwarfare.skills.next": ("Siguiente: %s", "Next: %s"),
    "screen.evolvedwarfare.skills.unlocks": ("Permite fabricar: %s", "Lets you craft: %s"),
    "screen.evolvedwarfare.skills.needs_level": ("Necesita %s %s", "Needs %s %s"),
    "screen.evolvedwarfare.skills.needs_perk": ("Necesita antes: %s", "Needs first: %s"),
    "screen.evolvedwarfare.skills.or": (" o ", " or "),
    "screen.evolvedwarfare.skills.no_points": ("No te quedan puntos de ventaja (se ganan al subir de nivel)",
                                               "No perk points left (you get one per level)"),
    "screen.evolvedwarfare.skills.click": ("Clic: cogerla (1 punto)", "Click: take it (1 point)"),
    "screen.evolvedwarfare.skills.click_free": ("Clic: cogerla (gratis en creativo)", "Click: take it (free in creative)"),
    "screen.evolvedwarfare.skills.max": ("Al máximo", "Maxed out"),
    "screen.evolvedwarfare.skills.owned": ("La tienes", "Owned"),
    "screen.evolvedwarfare.skills.hint": ("Las habilidades suben usándolas · cada nivel, un punto de ventaja · ←/→ cambiar",
                                          "Skills rise as you use them · one perk point per level · ←/→ switch"),
    "hud.evolvedwarfare.skills.skill_up": ("%s sube a %s", "%s rises to %s"),
    "hud.evolvedwarfare.skills.level_up": ("¡Nivel %s! +1 punto de ventaja (%s)", "Level %s! +1 perk point (%s)"),
    "tooltip.evolvedwarfare.skills.needed": ("Necesitas la ventaja «%s» (%s %s)", "Needs the perk “%s” (%s %s)"),
    "message.evolvedwarfare.skills.locked": ("Para fabricarlo necesitas la ventaja «%s» (%s %s): tecla K",
                                             "To craft it you need the perk “%s” (%s %s): press K"),
    "message.evolvedwarfare.skills.migrated": ("Lo que tenías investigado ya está en tu árbol de habilidades: %s",
                                               "What you had researched is now in your skill tree: %s"),
    "commands.evolvedwarfare.skills.summary": ("nivel %s · %s puntos sin gastar · %s gastados", "level %s · %s unspent points · %s spent"),
    "commands.evolvedwarfare.skills.points": ("%s puntos de ventaja a %s jugadores", "%s perk points to %s players"),
    "commands.evolvedwarfare.skills.level": ("%s en el nivel %s para %s jugadores", "%s set to level %s for %s players"),
    "commands.evolvedwarfare.skills.perk": ("%s ventajas dadas a %s jugadores", "%s perks given to %s players"),
    "commands.evolvedwarfare.skills.reset": ("Árbol reiniciado (puntos devueltos) a %s jugadores", "Skill tree reset (points refunded) for %s players"),
    "commands.evolvedwarfare.skills.unknown_skill": ("No existe esa habilidad", "No such skill"),
    "commands.evolvedwarfare.skills.unknown_perk": ("No existe esa ventaja", "No such perk"),
    "effect.evolvedwarfare.bleeding": ("Sangrado", "Bleeding"),
    "block.evolvedwarfare.research_desk.tooltip": ("Abre tu árbol de habilidades (también con la tecla K).",
                                                    "Opens your skill tree (also with the K key)."),
    "config.evolvedwarfare.category.research": ("Habilidades", "Skills"),
    "config.evolvedwarfare.researchRequired": ("Ventajas para fabricar", "Perks needed to craft"),
    "config.evolvedwarfare.researchRequired.tip": ("Hace falta la ventaja de forja del árbol para fabricar las armas, máquinas y fortificaciones.",
                                                   "You need the tree's smithing perk to craft the weapons, engines and fortifications."),
    "config.evolvedwarfare.skillXpRate": ("Velocidad de las habilidades", "Skill progression speed"),
    "config.evolvedwarfare.skillXpRate.tip": ("1 = normal; 2 = suben el doble de rápido.", "1 = normal; 2 = skills rise twice as fast."),
}

OBSOLETE_PREFIXES = ("research.evolvedwarfare.", "gui.evolvedwarfare.research.", "message.evolvedwarfare.research.",
                     "commands.evolvedwarfare.research.", "tooltip.evolvedwarfare.research_needed", "config.evolvedwarfare.researchSpeed")


def entries(english):
    out = collections.OrderedDict()
    for key, (es, en) in TEXTS.items():
        out[key] = en if english else es
    for group, (es, en) in GROUPS.items():
        out["skill_group.evolvedwarfare." + group] = en if english else es
    for sid, (es, es_desc, en, en_desc) in SKILLS.items():
        out["skill.evolvedwarfare." + sid] = en if english else es
        out["skill.evolvedwarfare." + sid + ".desc"] = en_desc if english else es_desc
    for pid, (es, es_desc, en, en_desc) in PERKS.items():
        out["perk.evolvedwarfare." + pid] = en if english else es
        out["perk.evolvedwarfare." + pid + ".desc"] = en_desc if english else es_desc
    return out


def bleeding_icon(path):
    """Una gota de sangre (18x18, como los iconos de los efectos del juego)."""
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    px = img.load()
    for y in range(18):
        for x in range(18):
            # gota: punta arriba, redonda abajo
            cx, cy = 9.0, 11.0
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            inside = (dx * dx + dy * dy <= 20.0) or (y >= 2 and y < 11 and abs(dx) <= (y - 2) * 0.5)
            if inside:
                shade = 150 + int(70 * max(0.0, -dx / 5.0)) - int(30 * max(0.0, dy / 5.0))
                px[x, y] = (min(255, shade), 16, 20, 255)
    for (x, y) in ((7, 9), (7, 10), (8, 8)):
        px[x, y] = (255, 150, 150, 255)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def main():
    for path in sorted(glob.glob(os.path.join(LANG, "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f, object_pairs_hook=collections.OrderedDict)
        data = collections.OrderedDict((k, v) for k, v in data.items() if not k.startswith(OBSOLETE_PREFIXES))
        data.update(entries(english))
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
    bleeding_icon(os.path.join(ROOT, "src/main/resources/assets/evolvedwarfare/textures/mob_effect/bleeding.png"))


if __name__ == "__main__":
    main()
