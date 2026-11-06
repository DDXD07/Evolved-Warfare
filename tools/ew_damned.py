"""El Nether bíblico, fase 2 (las criaturas): langostas del Abismo (y las mayores), serpientes de fuego, cabras del
desierto (y las de cuernos de azufre), caídos, nephilim (y los acorazados), espíritus de los condenados y estatuas de sal.

Sus modelos (assets/evolvedwarfare/hell/<id>.json, con el papel de cada parte para animarla, como los mini jefes), sus
texturas y lo que brilla, los objetos que sueltan (metal del Abismo, plumas oscuras, fragmentos de armadura caída) y la
sal bendita, sonidos, botín, huevos y textos.

Uso: python3 tools/ew_damned.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
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
add_tag = D.add_tag

IRON_D = (52, 54, 60)
IRON = (96, 98, 106)
RUST = (120, 64, 36)
EYE_RED = (230, 40, 20)
EYE_ORANGE = (255, 150, 30)


# ------------------------------------------------------------------ los modelos

def locust():
    """Langosta del Abismo: un saltamontes de hierro oscuro, con cuernos, mandíbulas y alas de chapa."""
    a = W.Animal("abyss_locust")
    body = a.part("body", None, (0, 18, 0), role="body")
    body.cube((-2, -2, -3), (4, 4, 6), **C(IRON, bands=(IRON_D, 2), spots=(RUST, 0.12)))
    body.cube((-1.5, -1.5, 3), (3, 3, 5), **C(IRON_D, rings=(IRON, 2)))           # el abdomen
    head = a.part("head", "body", (0, -1, -3), role="head")
    head.cube((-2, -2, -3), (4, 4, 3), **C(IRON_D, eyes=("front", 1, EYE_RED), glow_eyes=True))
    head.cube((-2, -4, -2), (1, 2, 1), **C((30, 30, 34)))
    head.cube((1, -4, -2), (1, 2, 1), **C((30, 30, 34)))
    a.part("jaw", "head", (0, 1, -3), role="jaw").cube((-1, 0, -1), (2, 1, 1), **C((150, 40, 20)))
    for side in (-1, 1):
        o = (0, 0, 0) if side > 0 else (-7, 0, 0)
        a.part("wing_%d" % (side + 1), "body", (side * 1, -2, 0), (0, side * 15, 0), role="wing", side=side, flap="roll").cube(
            o, (7, 0, 8), **C((70, 72, 80), spots=((40, 40, 46), 0.3)))
        for k, z in enumerate((-2, 0, 2)):
            a.part("leg_%d_%d" % (side + 1, k), "body", (side * 2, 2, z), (0, 0, side * -30), role="leg", phase=(k + side) % 2).cube(
                (-0.5, 0, -0.5), (1, 4, 1), **C((30, 30, 34)))
    return a


def serpent():
    """Serpiente de fuego: larga, de escamas negras con grietas al rojo, cabeza de víbora y ojos encendidos."""
    a = W.Animal("fire_serpent")
    scale = dict(spots=((210, 70, 20), 0.15), bands=((40, 18, 14), 2))
    head = a.part("head", None, (0, 21, -8), role="head")
    head.cube((-2.5, -2, -5), (5, 3, 5), **C((50, 24, 20), eyes=("front", 0, EYE_ORANGE), glow_eyes=True, **scale))
    a.part("jaw", "head", (0, 1, -1), role="jaw").cube((-2, 0, -4), (4, 1, 4), **C((150, 40, 20)))
    head.cube((-0.5, 1, -6), (1, 2, 1), **C((255, 120, 30)))                       # la lengua
    prev = "head"
    z = 0
    for k in range(6):
        w = 4 if k < 3 else 3 if k < 5 else 2
        seg = a.part("seg_%d" % k, prev, (0, 0, z), role="segment", phase=k + 1)
        seg.cube((-w / 2, -1 if w > 2 else -0.5, 0), (w, 3 if w > 2 else 2, 5), **C((44, 22, 18), **scale))
        prev = "seg_%d" % k
        z = 5
    return a


def goat(sulfur):
    """Cabra del desierto: flaca, de pelo ralo y quemado, barba, pezuñas; cuernos retorcidos (de azufre en algunas)."""
    a = W.Animal("desert_goat_sulfur" if sulfur else "desert_goat")
    fur = (150, 120, 92)
    dark = (90, 66, 50)
    horn = (226, 196, 40) if sulfur else (70, 60, 52)
    for side, z, phase in ((-1, -4, 0), (1, -4, 1), (-1, 5, 1), (1, 5, 0)):
        a.part("leg_%d_%d" % (side + 1, z), None, (side * 2.5, 14, z), role="leg", phase=phase).cube(
            (-1.5, 0, -1.5), (3, 10, 3), **C(fur, low=((40, 32, 26), 2)))
    body = a.part("body", None, (0, 14, 0), role="body")
    body.cube((-4, -7, -7), (8, 8, 15), **C(fur, spots=(dark, 0.2), belly=((190, 160, 130), 2)))
    head = a.part("head", "body", (0, -6, -7), (30, 0, 0), role="head")
    head.cube((-2.5, -5, -5), (5, 5, 6), **C(fur, eyes=("side", 1, EYE_ORANGE), glow_eyes=False, nose=(40, 30, 24)))
    head.cube((-1, 0, -4), (2, 3, 2), **C(dark))                                      # la barba
    for side in (-1, 1):
        o = (0, -1, -1) if side > 0 else (-4, -1, -1)
        h = a.part("horn_%d" % (side + 1), "head", (side * 2, -5, 0), (-40, 0, side * -20), role="horn")
        h.cube(o, (4, 2, 2), **C(horn, rings=((40, 30, 20) if not sulfur else (170, 130, 20), 1)))
        h2 = a.part("horn_tip_%d" % (side + 1), "horn_%d" % (side + 1), (side * 3.5, 0, 1), (60, 0, 0), role="horn")
        h2.cube((-1, -1, 0), (2, 2, 4), **C(horn, tip=((30, 24, 20) if not sulfur else (255, 240, 140), 1)))
    a.part("tail", "body", (0, -6, 8), (-30, 0, 0), role="tail").cube((-1, 0, 0), (2, 1, 3), **C(dark))
    return a


def fallen():
    """Caído: un hombre alto y demacrado con armadura ligera y rota, capucha y alas negras desgarradas (una casi arrancada)."""
    a = W.Animal("fallen")
    skin = (150, 140, 136)
    cloth = (34, 28, 32)
    plate = (110, 110, 118)
    for side, name, phase in ((-1, "leg_r", 0), (1, "leg_l", 1)):
        a.part(name, None, (side * 2, 10, 0), role="leg", phase=phase).cube((-2, 0, -2), (4, 14, 4), **C(cloth, low=((20, 18, 20), 2)))
    body = a.part("body", None, (0, 10, 0), role="body")
    body.cube((-4, -13, -2), (8, 13, 4), **C(cloth, bands=(plate, 4), spots=(RUST, 0.06)))
    head = a.part("head", "body", (0, -13, 0), role="head")
    head.cube((-4, -8, -4), (8, 8, 8), **C((20, 16, 20), front_color=skin, eyes=("front", 4, (230, 230, 255)), glow_eyes=True, mouth=(40, 30, 34)))
    for side, name in ((-1, "arm_r"), (1, "arm_l")):
        a.part(name, "body", (side * 5, -12, 0), role="arm", side=side).cube((-1.5, -1, -1.5), (3, 14, 3), **C(skin, high=(plate, 4)))
    # las alas: la derecha entera, la izquierda rota
    a.part("wing_r", "body", (-2, -11, 2), (0, 30, 0), role="wing", side=-1, flap="yaw").cube((-16, -4, 0), (16, 18, 0),
                                                                                             **C((20, 16, 20), spots=((60, 54, 60), 0.25)))
    a.part("wing_l", "body", (2, -11, 2), (0, -30, 20), role="wing", side=1, flap="yaw").cube((0, -2, 0), (7, 9, 0),
                                                                                             **C((20, 16, 20), spots=((60, 54, 60), 0.35)))
    return a


def nephilim(armored):
    """Nephilim: un gigante de piedra y carne quemada, encorvado, con puños enormes (algunos con restos de armadura)."""
    a = W.Animal("nephilim_armored" if armored else "nephilim")
    stone = (84, 80, 78)
    flesh = (110, 40, 30)
    plate = (150, 130, 80) if armored else stone
    for side, name, phase in ((-1, "leg_r", 0), (1, "leg_l", 1)):
        a.part(name, None, (side * 6, -2, 0), role="leg", phase=phase).cube((-4, 0, -4), (8, 26, 8),
                                                                           **C(stone, spots=(flesh, 0.18), low=((40, 36, 34), 4)))
    body = a.part("body", None, (0, -2, 0), (15, 0, 0), role="body")
    body.cube((-11, -26, -7), (22, 26, 14), **C(stone, spots=(flesh, 0.22), **({"bands": (plate, 7)} if armored else {})))
    head = a.part("head", "body", (0, -26, -3), (-15, 0, 0), role="head")
    head.cube((-5, -9, -6), (10, 9, 10), **C(stone, spots=(flesh, 0.3), eyes=("front", 4, EYE_ORANGE), glow_eyes=True, mouth=(30, 10, 8)))
    if armored:
        head.cube((-6, -11, -7), (12, 4, 12), **C(plate, rings=((110, 90, 50), 1)))
    a.part("jaw", "head", (0, 0, -2), role="jaw").cube((-4, 0, -4), (8, 3, 7), **C(flesh))
    for side in (-1, 1):
        arm = a.part("arm_%d" % (side + 1), "body", (side * 13, -24, 0), (-15, 0, 0), role="arm", side=side)
        arm.cube((-3, -2, -3), (6, 26, 6), **C(stone, spots=(flesh, 0.25), **({"high": (plate, 6)} if armored else {})))
        arm.cube((-4, 22, -4), (8, 8, 8), **C(stone, spots=(flesh, 0.1)))            # el puño
    return a


def spirit():
    """Espíritu de los condenados: una figura de bruma azulada, encadenada, que se deshilacha por abajo."""
    a = W.Animal("damned_spirit")
    mist = (150, 190, 210)
    body = a.part("body", None, (0, 6, 0), role="body")
    body.cube((-4, -8, -2), (8, 10, 4), **C(mist, bands=((90, 90, 96), 3)))
    body.cube((-3, 2, -1), (6, 6, 3), **C(mist))
    body.cube((-2, 8, -1), (4, 5, 2), **C(mist))
    head = a.part("head", "body", (0, -8, 0), role="head")
    head.cube((-3.5, -7, -3.5), (7, 7, 7), **C(mist, eyes=("front", 3, (20, 40, 60)), mouth=(20, 40, 60)))
    for side, name in ((-1, "arm_r"), (1, "arm_l")):
        arm = a.part(name, "body", (side * 5, -7, 0), (-50, 0, 0), role="arm", side=side)
        arm.cube((-1, -1, -1), (2, 11, 2), **C(mist, low=((90, 90, 96), 2)))
    return a


def statue():
    """Estatua de sal: una persona petrificada, con las manos tapándose la cara (no mira atrás)."""
    a = W.Animal("salt_statue")
    salt = (232, 228, 220)
    for side, name, phase in ((-1, "leg_r", 0), (1, "leg_l", 1)):
        a.part(name, None, (side * 2, 12, 0), role="leg", phase=phase).cube((-2, 0, -2), (4, 12, 4), **C(salt, noise=0.04))
    body = a.part("body", None, (0, 12, 0), role="none")
    body.cube((-4, -12, -2), (8, 12, 4), **C(salt, noise=0.04, spots=((200, 196, 186), 0.1)))
    body.cube((-5, -2, -3), (10, 4, 6), **C(salt, noise=0.05))                       # la falda
    head = a.part("head", "body", (0, -12, 0), role="none")
    head.cube((-4, -8, -4), (8, 8, 8), **C(salt, noise=0.04))
    for side, name in ((-1, "arm_r"), (1, "arm_l")):
        arm = a.part(name, "body", (side * 5, -11, -1), (-130, side * 25, 0), role="none")
        arm.cube((-1.5, -1, -1.5), (3, 11, 3), **C(salt, noise=0.04))
    return a


MODELS = collections.OrderedDict([
    ("abyss_locust", locust), ("fire_serpent", serpent), ("desert_goat", lambda: goat(False)), ("desert_goat_sulfur", lambda: goat(True)),
    ("fallen", fallen), ("nephilim", lambda: nephilim(False)), ("nephilim_armored", lambda: nephilim(True)), ("damned_spirit", spirit),
    ("salt_statue", statue),
])

# las que son una entidad (las variantes usan el modelo de la primera)
ENTITIES = ["abyss_locust", "greater_locust", "fire_serpent", "desert_goat", "fallen", "nephilim", "damned_spirit", "salt_statue"]

NAMES = {
    "abyss_locust": ("Langosta del Abismo", "Abyss Locust"),
    "greater_locust": ("Langosta mayor", "Greater Locust"),
    "fire_serpent": ("Serpiente de fuego", "Fire Serpent"),
    "desert_goat": ("Cabra del desierto", "Desert Goat"),
    "fallen": ("Caído", "Fallen"),
    "nephilim": ("Nephilim", "Nephilim"),
    "damned_spirit": ("Espíritu de los condenados", "Spirit of the Damned"),
    "salt_statue": ("Estatua de sal", "Salt Statue"),
}


def ev(name, pitch=1.0, volume=1.0):
    return {"name": name, "type": "event", "pitch": pitch, "volume": volume}


SOUNDS = {
    "abyss_locust": {"ambient": [ev("minecraft:entity.silverfish.ambient", 0.6), ev("minecraft:block.chain.step", 1.4)],
                     "hurt": [ev("minecraft:entity.silverfish.hurt", 0.7)], "death": [ev("minecraft:block.chain.break", 1.2)]},
    "fire_serpent": {"ambient": [ev("minecraft:entity.cat.hiss", 0.5), ev("minecraft:block.fire.ambient", 1.3)],
                     "hurt": [ev("minecraft:entity.cat.hiss", 0.7)], "death": [ev("minecraft:entity.blaze.death", 1.4)]},
    "desert_goat": {"ambient": [ev("minecraft:entity.goat.screaming.ambient", 0.8)], "hurt": [ev("minecraft:entity.goat.screaming.hurt", 0.8)],
                    "death": [ev("minecraft:entity.goat.screaming.death", 0.8)]},
    "fallen": {"ambient": [ev("minecraft:entity.vex.ambient", 0.5, 0.7), ev("minecraft:entity.phantom.flap", 0.6)],
               "hurt": [ev("minecraft:entity.vex.hurt", 0.6)], "death": [ev("minecraft:entity.vex.death", 0.5)]},
    "nephilim": {"ambient": [ev("minecraft:entity.ravager.ambient", 0.5), ev("minecraft:entity.iron_golem.repair", 0.4)],
                 "hurt": [ev("minecraft:entity.iron_golem.hurt", 0.5)], "death": [ev("minecraft:entity.ravager.death", 0.5)]},
    "damned_spirit": {"ambient": [ev("minecraft:entity.allay.ambient_without_item", 0.4, 0.6), ev("minecraft:particle.soul_escape", 0.8)],
                      "hurt": [ev("minecraft:entity.vex.hurt", 1.2)], "death": [ev("minecraft:particle.soul_escape", 0.6)]},
    "salt_statue": {"ambient": [ev("minecraft:block.calcite.step", 0.6, 0.4)], "hurt": [ev("minecraft:block.calcite.hit", 0.8)],
                    "death": [ev("minecraft:block.calcite.break", 0.7)]},
}


def count(item, lo, hi, chance=None, looting=True):
    entry = {"type": "minecraft:item", "name": item, "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False}]}
    if looting:
        entry["functions"].append({"function": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0, "max": 1},
                                   "enchantment": "minecraft:looting"})
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [entry]}
    if chance:
        pool["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return pool


def n(name):
    return "%s:%s" % (NS, name)


LOOT = {
    "abyss_locust": [count(n("abyss_metal"), 0, 1, chance=0.15)],
    "greater_locust": [count(n("abyss_metal"), 1, 3)],
    "fire_serpent": [count("minecraft:blaze_powder", 0, 2), count("minecraft:magma_cream", 0, 1)],
    "desert_goat": [count("minecraft:leather", 0, 2), count(n("brimstone"), 0, 2, chance=0.4)],
    "fallen": [count(n("dark_feather"), 1, 3), count(n("fallen_armor_shard"), 0, 2)],
    "nephilim": [count("minecraft:bone", 2, 6), count("minecraft:blackstone", 2, 5), count(n("fallen_armor_shard"), 0, 3, chance=0.5),
                 count("minecraft:gold_ingot", 0, 2)],
    "damned_spirit": [count("minecraft:ghast_tear", 0, 1, chance=0.4)],
    "salt_statue": [count(n("fine_salt"), 2, 4)],
}

ITEMS = {
    "abyss_metal": ("Metal del Abismo", "Abyss Metal", "Lo sueltan las langostas mayores. Oscuro y frío.", "Dropped by greater locusts. Dark and cold."),
    "dark_feather": ("Pluma oscura", "Dark Feather", "De las alas de un caído.", "From the wings of a fallen one."),
    "fallen_armor_shard": ("Fragmento de armadura caída", "Fallen Armor Shard", "Un trozo de una armadura que fue gloriosa.",
                           "A piece of armor that was once glorious."),
    "blessed_salt": ("Sal bendita", "Blessed Salt", "Purifica: libera a un espíritu de los condenados, o úsala para quitarte corrupción y una maldición.",
                     "Purifies: frees a spirit of the damned, or use it to remove corruption and a curse."),
}

TEXTS = {
    "message.evolvedwarfare.fallen.whisper.0": ("«Tú también caerás»", "\"You will fall too\""),
    "message.evolvedwarfare.fallen.whisper.1": ("«No mires arriba. Ya no está»", "\"Don't look up. He's gone\""),
    "message.evolvedwarfare.fallen.whisper.2": ("«Éramos luz»", "\"We were light\""),
    "message.evolvedwarfare.fallen.whisper.3": ("«¿Por qué sigues andando?»", "\"Why do you keep walking?\""),
    "message.evolvedwarfare.fallen.whisper.4": ("«Detrás de ti»", "\"Behind you\""),
    "message.evolvedwarfare.fallen.whisper.5": ("«Él nos dejó aquí»", "\"He left us here\""),
    "message.evolvedwarfare.fallen.whisper.6": ("«Quédate. Para siempre»", "\"Stay. Forever\""),
    "message.evolvedwarfare.fallen.whisper.7": ("«Sus ojos no te salvarán»", "\"His eyes won't save you\""),
    "message.evolvedwarfare.fallen.name": ("Caído", "Fallen"),
    "message.evolvedwarfare.spirit.stole": ("¡Un espíritu te ha robado %s!", "A spirit stole your %s!"),
    "message.evolvedwarfare.spirit.burned": ("El espíritu ha tirado %s a la lava", "The spirit threw your %s into the lava"),
    "message.evolvedwarfare.spirit.freed": ("El espíritu descansa por fin", "The spirit finally rests"),
    "message.evolvedwarfare.blessed_salt.used": ("La sal bendita te purifica", "The blessed salt purifies you"),
    "effect.evolvedwarfare.scorched": ("Abrasado", "Scorched"),
}
for _id, (_es, _en) in NAMES.items():
    TEXTS["entity.%s.%s" % (NS, _id)] = (_es, _en)
    TEXTS["item.%s.%s_spawn_egg" % (NS, _id)] = ("Huevo de " + _es.lower() if _id != "nephilim" else "Huevo de nephilim", _en + " Spawn Egg")
for _id, (_es, _en, _des, _den) in ITEMS.items():
    TEXTS["item.%s.%s" % (NS, _id)] = (_es, _en)
    TEXTS["item.%s.%s.desc" % (NS, _id)] = (_des, _den)
for _id in SOUNDS:
    for _k in ("ambient", "hurt", "death"):
        _es, _en = NAMES[_id]
        TEXTS["subtitles.%s.entity.%s.%s" % (NS, _id, _k)] = (
            {"ambient": "%s se oye", "hurt": "%s se duele", "death": "%s muere"}[_k] % _es,
            {"ambient": "%s nearby", "hurt": "%s hurts", "death": "%s dies"}[_k] % _en)


# ------------------------------------------------------------------ los objetos

def item_textures():
    out = {}
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(3, 10), (8, 4), (13, 7), (11, 13), (5, 13)], fill=(56, 58, 66, 255), outline=(26, 26, 30, 255))
    d.line([(6, 9), (10, 7)], fill=(120, 124, 140, 255))
    d.point((9, 11), fill=(150, 40, 30, 255))
    out["abyss_metal"] = im
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.line([(3, 14), (12, 2)], fill=(60, 54, 60, 255), width=1)
    for k in range(8):
        x, y = 4 + k, 13 - int(k * 1.4)
        d.line([(x, y), (x - 3, y - 2)], fill=(24, 18, 24, 255))
        d.line([(x, y), (x + 2, y + 1)], fill=(36, 28, 36, 255))
    out["dark_feather"] = im
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(2, 5), (9, 2), (14, 6), (12, 13), (4, 12)], fill=(170, 170, 180, 255), outline=(90, 90, 100, 255))
    d.line([(4, 7), (12, 7)], fill=(210, 190, 110, 255))
    d.line([(7, 3), (10, 12)], fill=(60, 60, 70, 255))
    out["fallen_armor_shard"] = im
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((3, 8, 13, 14), fill=(110, 80, 50, 255), outline=(70, 50, 30, 255))   # un saquito
    d.polygon([(5, 9), (8, 3), (11, 9)], fill=(110, 80, 50, 255))
    d.line([(6, 6), (10, 6)], fill=(230, 200, 80, 255))
    r = random.Random(3)
    for _ in range(8):
        d.point((r.randint(4, 12), r.randint(9, 13)), fill=(250, 248, 236, 255))
    d.point((8, 2), fill=(255, 240, 150, 255))
    out["blessed_salt"] = im
    return out


def scorched_icon():
    im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((3, 3, 14, 14), fill=(60, 20, 14, 255), outline=(255, 120, 30, 255))
    d.line((5, 9, 12, 9), fill=(255, 160, 40, 255))
    d.line((8, 5, 8, 12), fill=(255, 160, 40, 255))
    return im


def main():
    tex_dir = os.path.join(ASSETS, "textures/entity/hell")
    os.makedirs(tex_dir, exist_ok=True)
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
        if mid == "damned_spirit":
            # bruma: medio transparente, y más abajo
            px = img.load()
            for y in range(th):
                for x in range(tw):
                    r_, g_, b_, a_ = px[x, y]
                    if a_:
                        px[x, y] = (r_, g_, b_, 150)
        if mid == "fire_serpent":
            # las grietas al rojo brillan
            px = img.load()
            gp = glow.load()
            for y in range(th):
                for x in range(tw):
                    r_, g_, b_, a_ = px[x, y]
                    if a_ and r_ > 180 and g_ < 110:
                        gp[x, y] = (r_, g_, b_, 255)
        img.save(os.path.join(tex_dir, mid + ".png"))
        glow.save(os.path.join(tex_dir, mid + "_eyes.png"))
        parts = [{"name": p.name, "parent": p.parent, "pivot": p.pivot, "rotation": p.rot, "role": p.role,
                  "cubes": [{"uv": list(c.uv), "origin": c.origin, "size": c.size} for c in p.cubes]} for p in a.parts]
        if True:
            write_json(os.path.join(ASSETS, "hell/%s.json" % mid), {"texture": [tw, th], "parts": parts}, indent=None)
        previews.append((mid, parts, img))
    for eid in ENTITIES:
        write_json(os.path.join(DATA, "loot_table/entities/%s.json" % eid),
                   {"type": "minecraft:entity", "pools": LOOT[eid], "random_sequence": "%s:entities/%s" % (NS, eid)})
        write_json(os.path.join(ASSETS, "models/item/%s_spawn_egg.json" % eid), {"parent": "minecraft:item/template_spawn_egg"})
    for name, im in item_textures().items():
        im.save(os.path.join(ASSETS, "textures/item/%s.png" % name))
        write_json(os.path.join(ASSETS, "models/item/%s.json" % name), {"parent": "minecraft:item/generated", "textures": {"layer0": n("item/" + name)}})
    scorched_icon().save(os.path.join(ASSETS, "textures/mob_effect/scorched.png"))
    write_json(os.path.join(DATA, "recipe/blessed_salt.json"), {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [
        {"item": n("fine_salt")}, {"item": n("fine_salt")}, {"item": "minecraft:glowstone_dust"}, {"item": "minecraft:gold_nugget"}],
        "result": {"id": n("blessed_salt"), "count": 2}})
    path = os.path.join(ASSETS, "sounds.json")
    with open(path, encoding="utf-8") as f:
        sounds = json.load(f, object_pairs_hook=collections.OrderedDict)
    for eid, kinds in SOUNDS.items():
        for kind, files in kinds.items():
            key = "entity.%s.%s" % (eid, kind)
            sounds[key] = {"subtitle": "subtitles.%s.%s" % (NS, key), "sounds": files}
    write_json(path, sounds)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        cell = 220
        sheet = Image.new("RGB", (cell * 5, cell * 2), (70, 74, 80))
        dd = ImageDraw.Draw(sheet)
        for k, (mid, parts, img) in enumerate(previews):
            im = render_entity(parts, img, size=cell, yaw=35.0, pitch=12.0, center="auto")
            sheet.paste(im, ((k % 5) * cell, (k // 5) * cell))
            dd.text(((k % 5) * cell + 6, (k // 5) * cell + 4), mid, fill=(240, 240, 240))
        sheet.save(os.path.join(PREVIEW, "damned.png"))
    print("criaturas del Nether: langostas, serpientes, cabras, caídos, nephilim, espíritus y estatuas de sal")


if __name__ == "__main__":
    main()
