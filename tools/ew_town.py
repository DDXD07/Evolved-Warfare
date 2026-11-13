"""La gente de los pueblos (fases 58 en adelante): los textos de los oficios, los caracteres, contratar y (luego) la
rutina, las charlas y el diálogo.

Uso: python3 tools/ew_town.py [raíz del proyecto]
"""
import collections
import glob
import json
import os
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..")
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)

# oficio: (masculino, femenino, inglés)
JOBS = {
    "laborer": ("peón", "peona", "laborer"),
    "farmer": ("campesino", "campesina", "farmer"),
    "baker": ("panadero", "panadera", "baker"),
    "blacksmith": ("herrero", "herrera", "blacksmith"),
    "carpenter": ("carpintero", "carpintera", "carpenter"),
    "lumberjack": ("leñador", "leñadora", "lumberjack"),
    "miner": ("minero", "minera", "miner"),
    "hunter": ("cazador", "cazadora", "hunter"),
    "fisher": ("pescador", "pescadora", "fisher"),
    "shepherd": ("pastor", "pastora", "shepherd"),
    "tailor": ("sastre", "sastra", "tailor"),
    "mason": ("cantero", "cantera", "mason"),
    "innkeeper": ("tabernero", "tabernera", "innkeeper"),
    "merchant": ("mercader", "mercader", "merchant"),
    "guard": ("guardia", "guardia", "guard"),
    "mercenary": ("mercenario", "mercenaria", "mercenary"),
}
PERSONALITIES = {
    "cheerful": ("alegre", "alegre", "cheerful"),
    "grumpy": ("gruñón", "gruñona", "grumpy"),
    "fearful": ("miedoso", "miedosa", "fearful"),
    "brave": ("valiente", "valiente", "brave"),
    "lazy": ("perezoso", "perezosa", "lazy"),
    "kind": ("amable", "amable", "kind"),
    "curious": ("curioso", "curiosa", "curious"),
}

TEXTS = {
    "npc.evolvedwarfare.profile": ("%s, %s", "%s, %s"),
    "message.evolvedwarfare.for_hire": ("%s (%s) busca trabajo. Contrátalo con un Contrato de reclutamiento.",
                                        "%s (%s) is looking for work. Hire them with a Recruitment Contract."),
    "message.evolvedwarfare.hire.coins": ("Para contratarlo hacen falta %s Glory Coins, además del contrato.",
                                          "Hiring them takes %s Glory Coins as well as the contract."),
    "message.evolvedwarfare.city_guard.hello": ("«Paz y orden en la ciudad, forastero.» (Con un contrato y 15 Glory Coins dejaría su puesto por ti.)",
                                                "\"Keep the peace in our city, stranger.\" (With a contract and 15 Glory Coins they'd leave their post for you.)"),
}
for table, prefix in ((JOBS, "npc.evolvedwarfare.job."), (PERSONALITIES, "npc.evolvedwarfare.personality.")):
    for key, (m, f, en) in table.items():
        TEXTS[prefix + key] = (m, en)
        TEXTS[prefix + key + ".f"] = (f, en)

# Charlas (fase 60). %1$s: a quien se habla; %2$s: alguien del pueblo; %3$s: el oficio del que habla.
CHAT = {
    "greet_morning": [("¡Buenos días, %1$s! ¿Has dormido bien?", "Morning, %1$s! Sleep well?"),
                      ("Ya cantan los gallos, %1$s. Otro día más.", "The roosters are up, %1$s. Another day."),
                      ("Madrugas mucho tú, ¿eh?", "You're an early riser, eh?")],
    "greet_evening": [("Buenas tardes, %1$s. ¿Qué tal la jornada?", "Evening, %1$s. How was your day?"),
                      ("Ya va cayendo el sol. Qué ganas de cenar.", "Sun's going down. I could eat a horse."),
                      ("Un día largo, ¿verdad?", "Long day, wasn't it?")],
    "rain": [("Vaya chaparrón... se me ha calado hasta el alma.", "What a downpour... soaked to the bone."),
             ("Con esta lluvia no hay quien trabaje.", "Nobody can work in this rain."),
             ("Al menos los campos lo agradecerán.", "At least the fields will be grateful.")],
    "clear": [("Qué buen día hace hoy.", "Lovely day today."),
              ("Con este sol da gusto salir.", "Days like this make it worth getting up."),
              ("Ni una nube, %1$s. Ojalá dure.", "Not a cloud, %1$s. Hope it lasts.")],
    "cold": [("Brrr... este frío se mete en los huesos.", "Brrr... this cold gets into your bones."),
             ("Habrá que cortar más leña esta noche.", "We'll need more firewood tonight."),
             ("Hasta el agua del pozo está helada.", "Even the well water's frozen.")],
    "hot": [("Qué calor... no se puede ni respirar.", "So hot... can barely breathe."),
            ("Ojalá llueva aunque sea un poco.", "I'd give anything for a bit of rain.")],
    "danger": [("¿Has visto lo de antes? ¡Casi no lo cuento!", "Did you see that earlier? I barely made it!"),
               ("Cada vez hay más monstruos por aquí...", "There are more monsters around every day..."),
               ("No pienso salir de noche nunca más.", "I'm never going out at night again.")],
    "hurt": [("Ay... todavía me duele todo.", "Ow... everything still hurts."),
             ("Mira cómo me han dejado, %1$s.", "Look what they did to me, %1$s.")],
    "night": [("Ya es tarde. Me voy a dormir.", "It's late. I'm off to bed."),
              ("Cierra bien la puerta esta noche, %1$s.", "Bolt your door tonight, %1$s.")],
    "family": [("¿Has comido algo, %1$s?", "Have you eaten anything, %1$s?"),
               ("Mañana arreglo lo del tejado, te lo prometo.", "I'll fix the roof tomorrow, I promise."),
               ("Qué bien se está en casa.", "It's good to be home."),
               ("Te he guardado un trozo de pan.", "I saved you a piece of bread.")],
    "gossip_good": [("¿Sabes? %2$s me ayudó ayer. Qué buena gente.", "You know, %2$s helped me yesterday. Good people."),
                    ("Me encanta hablar con %2$s, siempre te alegra el día.", "I love talking to %2$s, always brightens my day."),
                    ("%2$s trabaja como nadie en este pueblo.", "%2$s works harder than anyone in this town.")],
    "gossip_bad": [("¿Has visto a %2$s? Hoy está insoportable.", "Have you seen %2$s? Unbearable today."),
                   ("No me fío de %2$s, no sé por qué.", "I don't trust %2$s, can't say why."),
                   ("%2$s otra vez quejándose de todo...", "%2$s complaining about everything again...")],
    "guard": [("Sin novedad en la muralla.", "All quiet on the wall."),
              ("¿Has visto algo raro en tu ronda?", "See anything odd on your rounds?"),
              ("Mantén los ojos abiertos esta noche.", "Keep your eyes open tonight."),
              ("Me toca la puerta norte. ¿Y a ti?", "I've got the north gate. You?")],
    "player": [("Ese forastero lleva un rato mirándonos...", "That stranger's been watching us a while..."),
               ("Dicen que los forasteros pagan bien por gente como nosotros.", "They say strangers pay well for folk like us."),
               ("¿Quién será ese? No es de por aquí.", "Who's that? Not from around here.")],
    "trait.cheerful": [("¡Hoy me siento con suerte!", "I feel lucky today!"),
                       ("Ja, ja, ¿te acuerdas de la fiesta del año pasado?", "Ha, remember last year's feast?"),
                       ("Qué bonito está el pueblo, ¿no crees?", "Doesn't the town look lovely?")],
    "trait.grumpy": [("Todo va de mal en peor.", "Everything's going from bad to worse."),
                     ("Otra vez el mismo ruido toda la noche...", "Same racket all night again..."),
                     ("En mis tiempos esto no pasaba.", "In my day this never happened.")],
    "trait.fearful": [("¿Has oído eso? Juraría que hay algo ahí fuera.", "Did you hear that? Something's out there."),
                      ("No me gusta nada este silencio.", "I don't like this silence one bit."),
                      ("Dicen que en el bosque hay bandidos...", "They say there are bandits in the woods...")],
    "trait.brave": [("Si vienen monstruos, que vengan. Aquí les espero.", "Let the monsters come. I'll be waiting."),
                    ("Algún día me iré a ver mundo.", "Someday I'll go see the world."),
                    ("Ayer espanté a un lobo con un palo.", "Yesterday I scared off a wolf with a stick.")],
    "trait.lazy": [("Uf... ¿ya hay que trabajar otra vez?", "Ugh... back to work already?"),
                   ("Una siesta, eso es lo que necesito.", "A nap, that's what I need."),
                   ("Mañana lo hago, hoy no me apetece.", "I'll do it tomorrow, not in the mood today.")],
    "trait.kind": [("Si necesitas algo, %1$s, aquí me tienes.", "If you need anything, %1$s, I'm here."),
                   ("Tienes cara de sueño. Descansa un poco.", "You look tired. Rest a little."),
                   ("Hoy he llevado sopa a los vecinos.", "I brought soup to the neighbours today.")],
    "trait.curious": [("¿Tú sabes qué hay más allá de las montañas?", "Do you know what lies beyond the mountains?"),
                      ("Me pregunto quién construyó esas ruinas...", "I wonder who built those ruins..."),
                      ("¿Por qué crees que el cielo es azul, %1$s?", "Why do you think the sky is blue, %1$s?")],
    "work.laborer": [("Hoy he cargado más sacos que nunca.", "Hauled more sacks today than ever."),
                     ("Siempre hay algo que llevar de un lado a otro.", "Always something to carry somewhere.")],
    "work.farmer": [("El trigo viene bien este año.", "The wheat's coming in nicely this year."),
                    ("Los cuervos se me comen las semillas.", "The crows keep eating my seeds.")],
    "work.baker": [("El pan de hoy me ha salido de maravilla.", "Today's bread came out perfect."),
                   ("Me levanto antes que nadie para hornear.", "I'm up before anyone to bake.")],
    "work.blacksmith": [("Se me ha mellado otra espada en el yunque.", "Chipped another blade on the anvil."),
                        ("Necesito más hierro, la forja no para.", "I need more iron, the forge never stops.")],
    "work.carpenter": [("Hoy he terminado una mesa preciosa.", "Finished a beautiful table today."),
                       ("La madera de roble es la mejor, digan lo que digan.", "Oak's the best wood, whatever they say.")],
    "work.lumberjack": [("Hoy han caído cinco árboles.", "Five trees came down today."),
                        ("Me duelen los brazos de tanto hacha.", "My arms ache from all that chopping.")],
    "work.miner": [("Abajo hace un calor que no veas.", "It's sweltering down in the mine."),
                   ("Creí ver un brillo de diamante... era una piedra.", "Thought I saw a diamond... just a rock.")],
    "work.hunter": [("He visto huellas de ciervo junto al río.", "Found deer tracks by the river."),
                    ("Hoy la caza no ha ido bien.", "Hunting went badly today.")],
    "work.fisher": [("Hoy han picado bien.", "They were biting today."),
                    ("Se me escapó uno así de grande.", "One got away, this big!")],
    "work.shepherd": [("Una oveja se me ha vuelto a escapar.", "A sheep ran off on me again."),
                      ("Este año la lana sale buena.", "Good wool this year.")],
    "work.tailor": [("Me han encargado tres capas para el invierno.", "Got orders for three winter cloaks."),
                    ("Se me acaba el hilo azul.", "I'm running out of blue thread.")],
    "work.mason": [("Esa muralla la levanté yo, ¿sabes?", "I built that wall, you know."),
                   ("La piedra no perdona un mal golpe.", "Stone never forgives a bad strike.")],
    "work.innkeeper": [("Anoche la taberna estaba llena.", "The tavern was packed last night."),
                       ("Se me acaba la cerveza, como siempre.", "Running out of ale, as always.")],
    "work.merchant": [("Los precios suben cada día.", "Prices go up every day."),
                      ("Hoy he vendido poco, la gente no tiene monedas.", "Sold little today, nobody has coins.")],
    "work.guard": [("Mi turno se me hace eterno.", "My shift feels endless."),
                   ("El capitán nos tiene a todos vigilando.", "The captain's got us all on watch.")],
    "work.mercenary": [("Quien paga, manda. Así es la vida.", "Whoever pays, commands. That's life."),
                       ("He luchado en más batallas de las que recuerdo.", "I've fought more battles than I remember.")],
    "reply.friendly": [("¡Y que lo digas, %1$s!", "You said it, %1$s!"),
                       ("Ja, ja, tienes toda la razón.", "Ha, you're absolutely right."),
                       ("Siempre me alegra hablar contigo.", "Always nice talking to you."),
                       ("Eso mismo pensaba yo.", "That's just what I was thinking."),
                       ("Cuenta conmigo para lo que sea.", "Count on me for anything."),
                       ("¡Qué bien me lo pasas, %1$s!", "You always cheer me up, %1$s!")],
    "reply.neutral": [("Ya, ya...", "Yeah, yeah..."),
                      ("Puede ser.", "Could be."),
                      ("Bueno, así son las cosas.", "Well, that's how it goes."),
                      ("Mmm, no sé qué decirte.", "Hmm, I don't know what to say."),
                      ("Si tú lo dices...", "If you say so...")],
    "reply.rude": [("Déjame en paz, %1$s.", "Leave me alone, %1$s."),
                   ("¿Y a mí qué me cuentas?", "And why are you telling me?"),
                   ("Bah, siempre con lo mismo.", "Bah, same old story."),
                   ("No tengo tiempo para tus tonterías.", "I've no time for your nonsense."),
                   ("Habla con otro, anda.", "Go bother someone else.")],
    "reply.danger": [("Yo también lo vi. Menudo susto.", "I saw it too. What a fright."),
                     ("Hay que avisar a los guardias.", "We need to tell the guards."),
                     ("Menos mal que estás bien.", "Thank goodness you're all right.")],
    "reply.family": [("Sí, no te preocupes por mí.", "Yes, don't worry about me."),
                     ("Eres un sol, %1$s.", "You're a treasure, %1$s."),
                     ("Ya lo hablamos en la cena.", "We'll talk about it over dinner.")],
    "reply.guard": [("Nada por aquí tampoco.", "Nothing here either."),
                    ("Tranquilo, yo vigilo este lado.", "Relax, I've got this side."),
                    ("Que no se nos duerma nadie.", "Nobody falls asleep on watch.")],
    "reply.hurt": [("Ve a que te curen, %1$s.", "Go get patched up, %1$s."),
                   ("Descansa, ya me encargo yo.", "Rest, I'll handle things.")],
    "reply.gossip_agree": [("Totalmente de acuerdo.", "Completely agree."),
                           ("Eso mismo dice todo el pueblo.", "That's what the whole town says."),
                           ("No podría haberlo dicho mejor.", "Couldn't have said it better.")],
    "reply.gossip_defend": [("¡Eh! No hables así de %2$s.", "Hey! Don't talk about %2$s like that."),
                            ("Pues yo no lo veo así.", "Well, I don't see it that way."),
                            ("No seas así, anda.", "Don't be unfair now.")],
    "close.friendly": [("Bueno, sigo con lo mío. ¡Hasta luego!", "Well, back to it. See you!"),
                       ("Nos vemos luego, %1$s.", "See you later, %1$s."),
                       ("Ja, ja. Venga, cuídate.", "Ha. Take care now."),
                       ("Ya hablaremos más tarde.", "We'll talk more later.")],
    "close.rude": [("Hmpf. Como quieras.", "Hmph. Suit yourself."),
                   ("Pues nada, adiós.", "Fine. Bye."),
                   ("Ya veo que hoy no se puede hablar contigo.", "Clearly there's no talking to you today.")],
    "hello.friendly": [("¡Buenas, forastero!", "Hello there, stranger!"),
                       ("¡Hola! ¿Vienes de lejos?", "Hi! Come from far away?"),
                       ("¡Bienvenidos al pueblo!", "Welcome to our town."),
                       ("¿Buscas algo? Pregunta sin miedo.", "Looking for something? Just ask.")],
    "hello.grumpy": [("¿Qué miras?", "What are you looking at?"),
                     ("Otro forastero... lo que faltaba.", "Another stranger... just what we needed."),
                     ("No estorbes, que tengo prisa.", "Out of my way, I'm busy.")],
    "hello.fearful": [("¡Ay! Me has asustado...", "Oh! You scared me..."),
                      ("H-hola... ¿no vendrás a hacernos daño?", "H-hello... you're not here to hurt us?")],
    "hello.night": [("No es hora de andar por ahí, forastero.", "It's no hour to be wandering, stranger."),
                    ("Buenas noches. Cuidado con los monstruos.", "Good night. Mind the monsters.")],
}
for topic, lines in CHAT.items():
    for i, (es, en) in enumerate(lines, 1):
        TEXTS["chat.evolvedwarfare.%s.%d" % (topic, i)] = (es, en)


def write_json(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    print("la gente de los pueblos: %d textos" % len(TEXTS))


if __name__ == "__main__":
    main()
