"""El Nether bíblico, fase 4 (el Apocalipsis): los tres mini jefes, los sellos, los rituales y sus objetos.

- El Ángel del Abismo (Abadón, el que guarda la llave del pozo): seis alas negras, una corona rota y una espada de fuego.
- El Falso Profeta: túnica blanca manchada, mitra de oro, cuernos de cordero y un báculo.
- La Bestia mayor: un cuerpo de leopardo, patas de oso y siete cabezas con diez cuernos (las cabezas caen una a una).
- Los siete fragmentos de sello, la Llave del Abismo, el báculo del profeta, el cuerno de la bestia, las alas del abismo,
  el círculo de ritual (con su receta), los iconos de la protección bendita y del pacto oscuro, el botín y los textos.

Uso: python3 tools/ew_apocalypse.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ew_wildlife as W  # noqa: E402
import ew_demons as D  # noqa: E402
from ew_model3d import render_entity  # noqa: E402

PREVIEW = W.PREVIEW
ROOT = W.ROOT
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
C = W.C
write_json = D.write_json


def n(name):
    return "%s:%s" % (NS, name)


GOLD = (220, 180, 60)
BLACK = (22, 18, 24)
ASHEN = (70, 64, 72)


# ------------------------------------------------------------------ los modelos

def angel():
    """El Ángel del Abismo: alto, con armadura negra y dorada, seis alas, la corona rota y una espada de fuego."""
    a = W.Animal("abyss_angel")
    plate = (40, 36, 46)
    skin = (190, 180, 176)
    for side, name, phase in ((-1, "leg_r", 0), (1, "leg_l", 1)):
        a.part(name, None, (side * 2.5, 8, 0), role="leg", phase=phase).cube((-2, 0, -2), (4, 16, 4),
                                                                            **C(plate, bands=(GOLD, 5), low=((20, 18, 22), 2)))
    body = a.part("body", None, (0, 8, 0), role="body")
    body.cube((-5, -15, -3), (10, 15, 6), **C(plate, bands=(GOLD, 5), spots=((90, 20, 20), 0.05)))
    body.cube((-6, -3, -3.5), (12, 5, 7), **C((60, 20, 24), rings=(GOLD, 2)))          # la faja y el faldón
    head = a.part("head", "body", (0, -15, 0), role="head")
    head.cube((-4, -8, -4), (8, 8, 8), **C(skin, front_color=(230, 226, 220), eyes=("front", 4, (255, 240, 160)), glow_eyes=True,
                                           mouth=(60, 40, 40)))
    # la corona rota (puntas de distinta altura)
    for k, (x, h) in enumerate(((-4, 3), (-2, 1), (0, 4), (2, 2), (3, 3))):
        head.cube((x, -8 - h, -4.5 if k % 2 else 3.5), (1, h, 1), **C(GOLD, glow_all=True))
    head.cube((-4.5, -9, -4.5), (9, 1, 9), **C(GOLD, glow_all=True))
    for side, name in ((-1, "arm_r"), (1, "arm_l")):
        arm = a.part(name, "body", (side * 6.5, -14, 0), role="arm", side=side)
        arm.cube((-1.5, -1, -1.5), (3, 14, 3), **C(plate, high=(GOLD, 3)))
    # la espada de fuego (en la mano derecha)
    sword = a.part("sword", "arm_r", (0, 12, 0), (-90, 0, 0), role="none")
    sword.cube((-0.5, -1, -1), (1, 3, 2), **C((60, 40, 30)))
    sword.cube((-1.5, -2, -0.5), (3, 1, 1), **C(GOLD))
    sword.cube((-0.5, -16, -0.5), (1, 14, 1), **C((255, 150, 40), glow_all=True))
    # seis alas: tres pares, de mayor a menor
    for k, (y, size, ang) in enumerate(((-13, (20, 22), 20), (-9, (16, 16), -5), (-4, (11, 10), -30))):
        for side in (-1, 1):
            w = a.part("wing_%d_%d" % (k, side + 1), "body", (side * 2, y, 3), (ang, side * 25, 0), role="wing", side=side,
                       flap="yaw" if k != 1 else "roll")
            sx, sy = size
            inner = (sx * 0.55, sy * 0.55)
            w.cube((0, -4, 0) if side > 0 else (-inner[0], -4, 0), (inner[0], inner[1], 0), **C(BLACK, spots=((60, 54, 66), 0.2)))
            w.cube((inner[0] - 1, -5, 0) if side > 0 else (-sx, -5, 0), (sx - inner[0] + 1, sy, 0),
                   **C(BLACK, spots=((60, 54, 66), 0.3), low=(GOLD, 1)))
    return a


def prophet():
    """El Falso Profeta: túnica blanca manchada de rojo, mitra de oro, cuernos de cordero y un báculo con un ojo."""
    a = W.Animal("false_prophet")
    robe = (226, 220, 206)
    stain = (140, 30, 30)
    skin = (200, 170, 150)
    body = a.part("body", None, (0, 24, 0), role="body")
    body.cube((-5, -12, -3.5), (10, 12, 7), **C(robe, spots=(stain, 0.08), low=((110, 100, 90), 2)))   # la falda (sin piernas)
    body.cube((-4, -24, -2.5), (8, 12, 5), **C(robe, bands=(GOLD, 6), spots=(stain, 0.05)))
    head = a.part("head", "body", (0, -24, 0), role="head")
    head.cube((-4, -8, -4), (8, 8, 8), **C(skin, eyes=("front", 4, (255, 60, 40)), glow_eyes=True, mouth=(70, 30, 30)))
    head.cube((-3, -14, -3), (6, 6, 6), **C(GOLD, rings=((160, 120, 30), 2)))         # la mitra
    head.cube((-1, -16, -1), (2, 2, 2), **C(GOLD))
    for side in (-1, 1):
        h = a.part("horn_%d" % (side + 1), "head", (side * 4, -7, 0), (0, 0, side * -30), role="horn")
        h.cube((0 if side > 0 else -3, -1, -1), (3, 2, 2), **C((230, 220, 200), rings=((170, 160, 140), 1)))
    for side, name in ((-1, "arm_r"), (1, "arm_l")):
        arm = a.part(name, "body", (side * 5, -23, 0), (-20 if side < 0 else 0, 0, 0), role="arm", side=side)
        arm.cube((-1.5, -1, -1.5), (3, 12, 3), **C(robe, low=(skin, 2)))
    staff = a.part("staff", "arm_r", (0, 10, -2), (-10, 0, 0), role="none")
    staff.cube((-0.5, -16, -0.5), (1, 26, 1), **C((90, 60, 36)))
    staff.cube((-2, -20, -2), (4, 4, 4), **C(GOLD, eyes=("front", 1, (255, 40, 20)), glow_eyes=True))
    return a


def beast():
    """La Bestia mayor: cuerpo de leopardo, patas de oso y siete cabezas de cuello largo con diez cuernos."""
    a = W.Animal("greater_beast")
    fur = (170, 120, 50)
    spots = (60, 34, 18)
    scale_ = (120, 30, 30)
    for side, z, phase in ((-1, -9, 0), (1, -9, 1), (-1, 10, 1), (1, 10, 0)):
        a.part("leg_%d_%d" % (side + 1, z), None, (side * 7, 8, z), role="leg", phase=phase).cube((-3.5, 0, -3.5), (7, 16, 7),
                                                                                              **C((70, 44, 30), low=((30, 20, 14), 3)))
    body = a.part("body", None, (0, 8, 0), role="body")
    body.cube((-9, -12, -14), (18, 14, 28), **C(fur, spots=(spots, 0.28), belly=((200, 160, 100), 3)))
    a.part("tail", "body", (0, -8, 14), (-25, 0, 0), role="tail").cube((-1.5, -1.5, 0), (3, 3, 14), **C(fur, spots=(spots, 0.3)))
    # siete cabezas: tres de león, dos de oso, dos de dragón; diez cuernos repartidos (la del centro lleva cuatro)
    horns = [2, 1, 1, 0, 2, 1, 3]
    for k in range(7):
        ang = (k - 3) * 24
        x = (k - 3) * 2.4
        lift = -(45 - abs(k - 3) * 8)
        neck = a.part("neck_%d" % k, "body", (x, -9, -13), (lift, -ang, 0), role="multihead", phase=k + 1)
        neck.cube((-1.5, -1.5, -14), (3, 3, 14), **C(scale_, bands=((70, 16, 16), 2)))
        head = a.part("bhead_%d" % k, "neck_%d" % k, (0, 0, -14), (-lift, 0, 0), role="multihead", phase=k + 1)
        dragon = k in (0, 6)
        color = (110, 26, 26) if dragon else fur if k % 2 else (80, 56, 40)
        head.cube((-2.5, -3, -5), (5, 5, 5), **C(color, eyes=("front", 1, (255, 200, 40)), glow_eyes=True, mouth=(30, 10, 10)))
        head.cube((-1.5, -1, -8) if dragon else (-2, -1, -7), (3, 3, 3) if dragon else (4, 3, 2), **C(color, nose=(30, 20, 16)))
        for h in range(horns[k]):
            hx = -2 + h * (4 / max(1, horns[k] - 1)) if horns[k] > 1 else -0.5
            head.cube((hx, -6, -2), (1, 3, 1), **C((40, 36, 32), tip=((230, 210, 160), 1)))
        # una corona en cada cuerno (las diez coronas)
        if horns[k]:
            head.cube((-2.5, -4, -3), (5, 1, 2), **C(GOLD, glow_all=True))
    return a


MODELS = collections.OrderedDict([("abyss_angel", angel), ("false_prophet", prophet), ("greater_beast", beast)])
NAMES = {
    "abyss_angel": ("Ángel del Abismo", "Angel of the Abyss"),
    "false_prophet": ("Falso Profeta", "False Prophet"),
    "greater_beast": ("Bestia mayor", "Greater Beast"),
}


# ------------------------------------------------------------------ el botín

def count(item, lo, hi, chance=None):
    entry = {"type": "minecraft:item", "name": item, "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False}]}
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [entry]}
    if chance:
        pool["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return pool


def seals(rolls):
    return {"rolls": rolls, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": n("seal_fragment"), "functions": [
        {"function": "minecraft:set_components", "components": {n("seal"): k}}]} for k in range(7)]}


LOOT = {
    "abyss_angel": [count(n("abyss_wings"), 1, 1), count(n("abyss_metal"), 6, 12), count("minecraft:nether_star", 1, 1),
                    count(n("dark_feather"), 6, 12), count("minecraft:diamond", 3, 6)],
    "false_prophet": [count(n("prophet_staff"), 1, 1), seals(1), count("minecraft:gold_ingot", 6, 14), count(n("book_of_condemnation"), 1, 2),
                      count("minecraft:emerald", 3, 8)],
    "greater_beast": [count(n("beast_horn"), 1, 1), seals(2), count("minecraft:magma_cream", 4, 10), count(n("abyss_metal"), 3, 8),
                      count("minecraft:netherite_scrap", 1, 3)],
}

SEALS = [  # (es, en): lo que abrió cada sello
    ("del caballo blanco", "of the white horse"),
    ("del caballo rojo", "of the red horse"),
    ("del caballo negro", "of the black horse"),
    ("del caballo pálido", "of the pale horse"),
    ("de las almas del altar", "of the souls beneath the altar"),
    ("del gran terremoto", "of the great earthquake"),
    ("del silencio en el cielo", "of the silence in heaven"),
]
ORD_ES = ["primer", "segundo", "tercer", "cuarto", "quinto", "sexto", "séptimo"]
ORD_EN = ["First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh"]
SEAL_COLORS = [(240, 240, 240), (210, 40, 30), (40, 40, 44), (170, 200, 160), (240, 200, 80), (140, 100, 60), (150, 170, 230)]

ITEMS = {
    "seal_fragment": ("Fragmento de sello", "Seal Fragment", "Uno de los siete sellos. Reúnelos en un círculo de ritual.",
                      "One of the seven seals. Gather them in a ritual circle."),
    "abyss_key": ("Llave del Abismo", "Key of the Abyss", "Hecha de los siete sellos. Úsala en el trono del Abismo... si te atreves.",
                  "Made from the seven seals. Use it on the Throne of the Abyss... if you dare."),
    "prophet_staff": ("Báculo del Falso Profeta", "Staff of the False Prophet", "Clic derecho: fuego del cielo donde miras (cuesta corrupción).",
                      "Right click: fire from heaven where you look (costs corruption)."),
    "beast_horn": ("Cuerno de la Bestia", "Horn of the Beast", "Tócalo: las criaturas hostiles cercanas se asustan y huyen.",
                   "Blow it: nearby hostile creatures are frightened and flee."),
    "abyss_wings": ("Alas del Abismo", "Wings of the Abyss", "Clic derecho: un gran salto alado y caída lenta.",
                    "Right click: a great winged leap and slow fall."),
}

TEXTS = {
    "block.evolvedwarfare.ritual_circle": ("Círculo de ritual", "Ritual Circle"),
    "block.evolvedwarfare.ritual_circle.desc": ("Pon encima las ofrendas (clic derecho) y tócalo con la mano vacía para el ritual. Agachado, recoges la última.",
                                                "Place offerings on it (right click) and touch it with an empty hand to perform the ritual. Sneak to take the last back."),
    "effect.evolvedwarfare.blessed_ward": ("Protección bendita", "Blessed Ward"),
    "effect.evolvedwarfare.dark_pact": ("Pacto oscuro", "Dark Pact"),
    "message.evolvedwarfare.ritual.placed": ("Ofrenda %s de %s", "Offering %s of %s"),
    "message.evolvedwarfare.ritual.full": ("El círculo está lleno", "The circle is full"),
    "message.evolvedwarfare.ritual.nothing": ("No ocurre nada. Las ofrendas no forman ningún rito.", "Nothing happens. The offerings form no rite."),
    "message.evolvedwarfare.ritual.purification": ("Ritual de purificación: tus maldiciones se deshacen y una luz te protege.",
                                                   "Purification ritual: your curses come undone and a light protects you."),
    "message.evolvedwarfare.ritual.dark_pact": ("Pacto oscuro: el abismo te presta su fuerza... a cambio de ti.",
                                                "Dark pact: the abyss lends you its strength... in exchange for you."),
    "message.evolvedwarfare.ritual.seals": ("Los siete sellos se funden: la Llave del Abismo.", "The seven seals melt together: the Key of the Abyss."),
    "message.evolvedwarfare.ritual.consecration": ("Consagración: el libro arde con una luz blanca.", "Consecration: the book burns with a white light."),
    "message.evolvedwarfare.ritual.beast": ("El lago de fuego hierve... ¡Algo sale de la lava!", "The lake of fire boils... Something rises from the lava!"),
    "message.evolvedwarfare.ritual.prophet": ("Una voz responde a tu llamada: «Yo soy el que esperabais».",
                                              "A voice answers your call: \"I am the one you were waiting for\"."),
    "message.evolvedwarfare.ritual.need_nether": ("Este rito solo tiene poder en el Nether.", "This rite only has power in the Nether."),
    "message.evolvedwarfare.ritual.need_lava": ("La Bestia sale de la lava: haz el rito junto a un lago de fuego.",
                                                "The Beast rises from lava: perform the rite beside a lake of fire."),
    "message.evolvedwarfare.ritual.busy": ("Ya hay uno así cerca.", "There is already one like that nearby."),
    "message.evolvedwarfare.angel.summoned": ("La llave gira. El pozo se abre y el Ángel del Abismo sube con el humo.",
                                              "The key turns. The pit opens and the Angel of the Abyss rises with the smoke."),
    "message.evolvedwarfare.angel.locusts": ("«¡Salid del humo!»", "\"Come forth from the smoke!\""),
    "message.evolvedwarfare.angel.pit": ("«El pozo no tiene fondo. Tú tampoco lo tendrás.»", "\"The pit has no bottom. Neither will you.\""),
    "message.evolvedwarfare.prophet.altar": ("El Falso Profeta responde a la ofrenda...", "The False Prophet answers the offering..."),
    "message.evolvedwarfare.prophet.miracle": ("«¡Mirad el milagro! ¡Seguidme!»", "\"Behold the miracle! Follow me!\""),
    "message.evolvedwarfare.prophet.deceive": ("«Lo que tienes en la mano no es lo que crees.»", "\"What you hold is not what you think.\""),
    "message.evolvedwarfare.prophet.illusions": ("«¿Cuál de nosotros es el verdadero?»", "\"Which of us is the true one?\""),
    "message.evolvedwarfare.beast.head": ("¡Una de las cabezas de la Bestia cae! (quedan %s)", "One of the Beast's heads falls! (%s left)"),
    "message.evolvedwarfare.beast.healed": ("La herida mortal de la Bestia sana... ¡y vuelven a crecer cabezas!",
                                            "The Beast's mortal wound heals... and heads grow back!"),
    "message.evolvedwarfare.horn.blown": ("El cuerno retumba: %s criaturas huyen.", "The horn booms: %s creatures flee."),
    "entity.evolvedwarfare.prophet_follower": ("Seguidor del profeta", "Follower of the Prophet"),
    "entity.evolvedwarfare.prophet_illusion": ("Falso Profeta", "False Prophet"),
}
for k in range(7):
    TEXTS["seal.evolvedwarfare.%d" % k] = ("Fragmento del %s sello (%s)" % (ORD_ES[k], SEALS[k][0]), "%s Seal Fragment (%s)" % (ORD_EN[k], SEALS[k][1]))
for _id, (_es, _en) in NAMES.items():
    TEXTS["entity.%s.%s" % (NS, _id)] = (_es, _en)
    TEXTS["item.%s.%s_spawn_egg" % (NS, _id)] = ("Huevo de " + _es[0].lower() + _es[1:], _en + " Spawn Egg")
for _id, (_es, _en, _des, _den) in ITEMS.items():
    TEXTS["item.%s.%s" % (NS, _id)] = (_es, _en)
    TEXTS["item.%s.%s.desc" % (NS, _id)] = (_des, _den)
# los ritos (para el libro y el tooltip del círculo)
RITES = [
    ("purification", "Purificación: sal bendita + lingote de oro + polvo de piedra luminosa", "Purification: blessed salt + gold ingot + glowstone dust"),
    ("dark_pact", "Pacto oscuro: metal del Abismo + pluma oscura + arena de almas", "Dark pact: abyss metal + dark feather + soul sand"),
    ("seals", "Los siete sellos: un fragmento de cada sello", "The seven seals: one fragment of each seal"),
    ("consecration", "Consagración: libro + 2 sales benditas + bloque de oro + un fragmento de sello",
     "Consecration: book + 2 blessed salt + gold block + a seal fragment"),
    ("prophet", "Llamar al profeta: bloque de oro + libro escrito + vela", "Call the prophet: gold block + written book + candle"),
    ("beast", "Despertar a la Bestia: crema de magma + vara de blaze + fragmento de armadura caída + libro de condena (junto a la lava)",
     "Wake the Beast: magma cream + blaze rod + fallen armor shard + book of condemnation (beside lava)"),
]
for rid, es, en in RITES:
    TEXTS["rite.evolvedwarfare.%s" % rid] = (es, en)


# ------------------------------------------------------------------ las texturas de los objetos

def img16():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def item_textures():
    out = {}
    for k, col in enumerate(SEAL_COLORS):
        im, d = img16()
        # un trozo de sello de cera y piedra, con su marca
        pts = [(2, 9), (4, 3), (10, 2), (14, 6), (13, 12), (7, 14)]
        d.polygon(pts, fill=(150, 26, 30, 255), outline=(80, 10, 14, 255))
        d.line([(5, 5), (11, 11)], fill=(60, 8, 10, 255))
        d.ellipse((5, 5, 11, 11), outline=col + (255,))
        d.point((8, 8), fill=col + (255,))
        for j in range(k + 1):     # tantas muescas como su número
            d.point((3 + j * 1.5, 12 - (j % 2)), fill=(240, 210, 120, 255))
        out["seal_fragment_%d" % k] = im
    im, d = img16()
    d.ellipse((2, 1, 8, 7), outline=GOLD + (255,), width=2)
    d.line([(6, 6), (13, 13)], fill=GOLD + (255,), width=2)
    d.line([(11, 13), (13, 11)], fill=GOLD + (255,), width=1)
    d.line([(9, 11), (11, 9)], fill=GOLD + (255,), width=1)
    for k, col in enumerate(SEAL_COLORS):
        a = k / 7 * math.pi * 2
        d.point((int(5 + math.cos(a) * 2), int(4 + math.sin(a) * 2)), fill=col + (255,))
    out["abyss_key"] = im
    im, d = img16()
    d.line([(3, 14), (11, 6)], fill=(110, 76, 44, 255), width=2)
    d.ellipse((9, 1, 15, 7), fill=GOLD + (255,), outline=(150, 110, 30, 255))
    d.ellipse((11, 3, 13, 5), fill=(250, 250, 250, 255))
    d.point((12, 4), fill=(220, 30, 20, 255))
    out["prophet_staff"] = im
    im, d = img16()
    d.arc((1, 2, 15, 16), 200, 340, fill=(60, 40, 30, 255), width=3)
    d.line([(2, 9), (4, 12)], fill=(30, 20, 14, 255), width=3)
    d.ellipse((11, 2, 15, 6), outline=GOLD + (255,))
    d.line([(5, 5), (9, 3)], fill=(220, 200, 150, 255))
    out["beast_horn"] = im
    im, d = img16()
    for side in (-1, 1):
        for k in range(5):
            x0 = 8 + side * (1 + k)
            d.line([(x0, 3 + k), (8 + side * (3 + k * 1.2), 12 + k // 2)], fill=(24 + k * 6, 20, 28 + k * 6, 255), width=1)
        d.line([(8, 3), (8 + side * 7, 5)], fill=GOLD + (255,))
    out["abyss_wings"] = im
    return out


def circle_textures():
    tex = os.path.join(ASSETS, "textures/block")
    top = Image.new("RGBA", (16, 16), (24, 20, 26, 255))
    d = ImageDraw.Draw(top)
    r = random.Random(5)
    for _ in range(40):
        d.point((r.randrange(16), r.randrange(16)), fill=(36, 30, 38, 255))
    d.ellipse((1, 1, 14, 14), outline=(236, 232, 220, 255))
    d.ellipse((4, 4, 11, 11), outline=(200, 40, 30, 255))
    for k in range(7):
        a = k / 7 * math.pi * 2 - math.pi / 2
        x, y = 7.5 + math.cos(a) * 5.2, 7.5 + math.sin(a) * 5.2
        d.point((round(x), round(y)), fill=(240, 200, 80, 255))
    pts = [(7.5 + math.cos(k * 4 * math.pi / 5 - math.pi / 2) * 6, 7.5 + math.sin(k * 4 * math.pi / 5 - math.pi / 2) * 6) for k in range(6)]
    d.line(pts, fill=(200, 196, 186, 255))
    top.save(os.path.join(tex, "ritual_circle_top.png"))
    side = Image.open(os.path.join("/home/claude/ref/mcassets/assets/minecraft/textures/block/polished_blackstone.png")).convert("RGBA")
    side.save(os.path.join(tex, "ritual_circle_side.png"))


def effect_icons():
    out = {}
    im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(9, 2), (15, 5), (14, 11), (9, 16), (4, 11), (3, 5)], fill=(230, 230, 200, 255), outline=(200, 170, 60, 255))
    d.line([(9, 5), (9, 13)], fill=(200, 170, 60, 255))
    d.line([(6, 8), (12, 8)], fill=(200, 170, 60, 255))
    out["blessed_ward"] = im
    im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((3, 3, 15, 15), fill=(40, 10, 20, 255), outline=(160, 20, 40, 255))
    d.polygon([(9, 5), (12, 12), (6, 12)], outline=(230, 60, 60, 255))
    d.point((9, 9), fill=(255, 120, 80, 255))
    out["dark_pact"] = im
    return out


# ------------------------------------------------------------------ todo

def main():
    tex_dir = os.path.join(ASSETS, "textures/entity/hell")
    previews = []
    for mid, gen in MODELS.items():
        a = gen()
        tw, th = a.pack()
        img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        glow = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        rng = random.Random(mid)
        for p in a.parts:
            for c in p.cubes:
                W.paint_cube(img, c, rng)
                D.paint_extra(img, glow, c, rng)
        img.save(os.path.join(tex_dir, mid + ".png"))
        glow.save(os.path.join(tex_dir, mid + "_eyes.png"))
        parts = [{"name": p.name, "parent": p.parent, "pivot": p.pivot, "rotation": p.rot, "role": p.role,
                  "cubes": [{"uv": list(c.uv), "origin": c.origin, "size": c.size} for c in p.cubes]} for p in a.parts]
        write_json(os.path.join(ASSETS, "hell/%s.json" % mid), {"texture": [tw, th], "parts": parts}, indent=None)
        previews.append((mid, parts, img))
        write_json(os.path.join(DATA, "loot_table/entities/%s.json" % mid),
                   {"type": "minecraft:entity", "pools": LOOT[mid], "random_sequence": "%s:entities/%s" % (NS, mid)})
        write_json(os.path.join(ASSETS, "models/item/%s_spawn_egg.json" % mid), {"parent": "minecraft:item/template_spawn_egg"})
    for name, im in item_textures().items():
        im.save(os.path.join(ASSETS, "textures/item/%s.png" % name))
    M = os.path.join(ASSETS, "models/item")
    for k in range(7):
        write_json(os.path.join(M, "seal_fragment_%d.json" % k), {"parent": "minecraft:item/generated", "textures": {"layer0": n("item/seal_fragment_%d" % k)}})
    write_json(os.path.join(M, "seal_fragment.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": n("item/seal_fragment_0")},
                                                       "overrides": [{"predicate": {n("seal"): k / 10.0}, "model": n("item/seal_fragment_%d" % k)}
                                                                     for k in range(7)]})
    for name in ("abyss_key", "beast_horn", "abyss_wings"):
        write_json(os.path.join(M, "%s.json" % name), {"parent": "minecraft:item/generated", "textures": {"layer0": n("item/" + name)}})
    write_json(os.path.join(M, "prophet_staff.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": n("item/prophet_staff")}})
    # el círculo de ritual: una losa fina con el dibujo arriba
    circle_textures()
    write_json(os.path.join(ASSETS, "models/block/ritual_circle.json"), {"parent": "minecraft:block/block", "textures": {
        "particle": n("block/ritual_circle_side"), "top": n("block/ritual_circle_top"), "side": n("block/ritual_circle_side")}, "elements": [
        {"from": [0, 0, 0], "to": [16, 2, 16], "faces": {"up": {"texture": "#top"}, "down": {"texture": "#side"},
                                                         **{f: {"texture": "#side", "uv": [0, 14, 16, 16]} for f in ("north", "south", "east", "west")}}}]})
    write_json(os.path.join(ASSETS, "blockstates/ritual_circle.json"), {"variants": {"": {"model": n("block/ritual_circle")}}})
    write_json(os.path.join(M, "ritual_circle.json"), {"parent": n("block/ritual_circle")})
    write_json(os.path.join(DATA, "loot_table/blocks/ritual_circle.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [
        {"type": "minecraft:item", "name": n("ritual_circle")}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(os.path.join(DATA, "recipe/ritual_circle.json"), {"type": "minecraft:crafting_shaped", "category": "misc",
                                                                 "pattern": [" B ", "BSB", " B "],
                                                                 "key": {"B": {"item": "minecraft:polished_blackstone"}, "S": {"item": n("blessed_salt")}},
                                                                 "result": {"id": n("ritual_circle"), "count": 1}})
    D.add_tag(os.path.join(ROOT, "src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json"), [n("ritual_circle")])
    for name, im in effect_icons().items():
        im.save(os.path.join(ASSETS, "textures/mob_effect/%s.png" % name))
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        cell = 300
        sheet = Image.new("RGB", (cell * 3, cell), (70, 74, 80))
        dd = ImageDraw.Draw(sheet)
        for k, (mid, parts, img) in enumerate(previews):
            im = render_entity(parts, img, size=cell, yaw=35.0, pitch=12.0, center="auto")
            sheet.paste(im, (k * cell, 0))
            dd.text((k * cell + 6, 4), mid, fill=(240, 240, 240))
        sheet.save(os.path.join(PREVIEW, "apocalypse.png"))
    print("el Apocalipsis: el Ángel del Abismo, el Falso Profeta, la Bestia mayor, los sellos y los rituales")


if __name__ == "__main__":
    main()
