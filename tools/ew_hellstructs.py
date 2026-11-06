"""El Nether bíblico, fase 3 (las estructuras).

- Torre de Babel rota: nueve pisos de piedra negra y ladrillos extraños, runas que cambian, suelos que se hunden, bloques
  que aparecen y desaparecen, un guardián en algunos pisos y, arriba, un portal de piedra que da visiones. Medio
  derrumbada: por un lado se ve el vacío.
- Templos de falsos dioses: un altar de ofrendas entre ídolos cuyos ojos te siguen, y una cripta con más ofrendas y
  libros de saber.
- Campos de pilares de sal: un laberinto de pilares, con suelo de sal fina y estatuas de sal.
- Cementerios de ángeles caídos: alas rotas clavadas en el suelo, armaduras oxidadas, espadas que aún brillan (se pueden
  arrancar) y tumbas abiertas con caídos dormidos.
- El trono del Lago de Fuego, las cámaras selladas del fondo del Abismo, las minas de azufre abandonadas y las casas y
  templos de Sodoma (con trampas de presión, suelos que se hunden y habitantes inmóviles).
- La Fortaleza del Abaddon (la Ciudadela): sellos rotos, biblioteca de libros de condena, el trono que se activa y la
  cámara secreta del estrado (eso lo añade tools/ew_citadel.py con abaddon_additions).

También los bloques nuevos (runas de Babel, suelo que se hunde, bloque cambiante, portal de las visiones, altar, ídolo,
alas rotas, espada clavada, trono del abismo, muro sellado, sello roto), los objetos (libro de condena, hojas caídas), el
botín (con libros de saber) y los textos.

Uso: python3 tools/ew_hellstructs.py [raíz del proyecto] [--preview carpeta]
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
import nbt  # noqa: E402
from ew_structlib import NS, S, Template, chest, slab, stairs, YAW  # noqa: E402

args = sys.argv[1:]
PREVIEW = None
if "--preview" in args:
    i = args.index("--preview")
    PREVIEW = args[i + 1]
    del args[i:i + 2]
ROOT = args[0] if args else os.path.join(os.path.dirname(__file__), "..")
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data", NS)
MC_DATA = os.path.join(RES, "data/minecraft")
VANILLA = "/home/claude/ref/mcassets/assets/minecraft/textures"


def n(name):
    return "%s:%s" % (NS, name)


def E(name, **props):
    return S(n(name), **props)


def write_json(path, obj, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, ensure_ascii=False, indent=indent)
        fh.write("\n")


def add_tag(path, values):
    t = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            t = json.load(fh)
    for v in values:
        if v not in t["values"]:
            t["values"].append(v)
    write_json(path, t)


BLACK = S("polished_blackstone_bricks")
CRACKED = S("cracked_polished_blackstone_bricks")
BLACKSTONE = S("blackstone")
GILDED = S("gilded_blackstone")
AIR = "air"


def rng_for(name):
    return random.Random(sum(map(ord, name)) * 7919)


def dormant(eid, name=None, **extra):
    d = {"id": eid, "NoAI": nbt.Byte(1), "Tags": nbt.List(nbt.STRING, ["ew_dormant"]), "PersistenceRequired": nbt.Byte(1)}
    if name:
        d["CustomName"] = json.dumps({"translate": name})
    d.update(extra)
    return d


def boss_nbt(eid, name, health, extra_attrs=()):
    attrs = [{"id": "minecraft:generic.max_health", "base": float(health)}] + [{"id": a, "base": float(v)} for a, v in extra_attrs]
    return {"id": eid, "CustomName": json.dumps({"translate": name}), "PersistenceRequired": nbt.Byte(1), "Health": nbt.Float(float(health)),
            "attributes": nbt.List(nbt.COMPOUND, attrs)}


# ------------------------------------------------------------------ Torre de Babel

BABEL_R = 11
BABEL_FLOORS = 9
FLOOR_H = 9
BABEL_N = 2 * BABEL_R + 3
BABEL_H = BABEL_FLOORS * FLOOR_H + 12


def babel_brick(x, y, z, r):
    """Los ladrillos extraños: cada piso de un material distinto (las lenguas confundidas)."""
    mats = [BLACK, S("deepslate_tiles"), S("mud_bricks"), S("red_nether_bricks"), S("polished_blackstone_bricks"), S("deepslate_bricks"),
            S("nether_bricks"), S("stone_bricks"), S("end_stone_bricks")]
    h = (x * 31 + y * 17 + z * 7) % 13
    base = mats[(y // FLOOR_H) % len(mats)]
    if h == 0:
        return CRACKED
    if h == 1:
        return GILDED
    return base


def babel():
    t = Template(BABEL_N, BABEL_H, BABEL_N)
    rnd = rng_for("babel")
    c = BABEL_N // 2
    # el lado derrumbado: desde el piso 4, el sector del este se va cayendo
    def collapsed(x, z, y):
        ang = math.atan2(z - c, x - c)
        floor = y // FLOOR_H
        return floor >= 4 and abs(ang) < 0.35 + (floor - 4) * 0.12
    for y in range(BABEL_H - 8):
        floor = y // FLOOR_H
        r = BABEL_R - floor // 3   # se estrecha al subir
        for x in range(BABEL_N):
            for z in range(BABEL_N):
                d = math.hypot(x - c, z - c)
                if d > r + 0.5:
                    continue
                wall = d > r - 1.0
                level = y % FLOOR_H == 0
                if collapsed(x, z, y) and (wall or not level or rnd.random() < 0.7):
                    continue
                if wall:
                    # ventanas en cruz y aspilleras
                    win = (y % FLOOR_H in (3, 4, 5)) and (int(math.degrees(math.atan2(z - c, x - c)) + 360) % 45 < 6)
                    t.put(x, y, z, AIR if win else babel_brick(x, y, z, r))
                elif level:
                    t.put(x, y, z, BLACKSTONE if (x + z) % 2 else BLACK)
                else:
                    t.put(x, y, z, AIR)
    # la escalera de caracol, pegada a la pared, de piso a piso
    for floor in range(BABEL_FLOORS):
        r = BABEL_R - floor // 3 - 2
        y0 = floor * FLOOR_H
        for k in range(FLOOR_H):
            ang = (floor * 2.1) + k * (math.pi * 1.2 / FLOOR_H)
            x = int(round(c + math.cos(ang) * r))
            z = int(round(c + math.sin(ang) * r))
            for dx, dz in ((0, 0), (1, 0), (0, 1)):
                if t.inside(x + dx, y0 + k, z + dz):
                    t.put(x + dx, y0 + k, z + dz, BLACK)
                    for up in (1, 2, 3):
                        if t.inside(x + dx, y0 + k + up, z + dz) and (y0 + k + up) % FLOOR_H != 0:
                            t.put(x + dx, y0 + k + up, z + dz, AIR)
            # el hueco en el techo para subir
            if k >= FLOOR_H - 3:
                for dx, dz in ((0, 0), (1, 0), (0, 1), (1, 1)):
                    if t.inside(x + dx, y0 + FLOOR_H, z + dz):
                        t.put(x + dx, y0 + FLOOR_H, z + dz, AIR)
    # lo de cada piso
    for floor in range(BABEL_FLOORS):
        y0 = floor * FLOOR_H
        r = BABEL_R - floor // 3
        # runas en las paredes (cambian de forma al acercarte)
        for k in range(10):
            ang = k * math.pi / 5 + floor
            x = int(round(c + math.cos(ang) * (r - 1)))
            z = int(round(c + math.sin(ang) * (r - 1)))
            if t.get(x, y0 + 3, z) not in (None, S(AIR)):
                t.put(x, y0 + 3, z, E("babel_rune", glyph=(k + floor) % 8))
        if floor == 0:
            # la entrada, al oeste
            for z in range(c - 1, c + 2):
                for y in range(1, 5):
                    for x in range(0, 4):
                        if t.get(x, y, z) is not None:
                            t.put(x, y, z, AIR)
            continue
        # suelos que se hunden (sobre el vacío del piso de abajo) y bloques que cambian
        for k in range(6 + floor):
            x = c + rnd.randint(-r + 3, r - 3)
            z = c + rnd.randint(-r + 3, r - 3)
            if t.get(x, y0, z) in (BLACK, BLACKSTONE):
                t.put(x, y0, z, E("crumbling_floor"))
                if t.get(x, y0 - 1, z) == S(AIR):
                    pass
        for k in range(3 + floor // 2):
            x = c + rnd.randint(-r + 3, r - 3)
            z = c + rnd.randint(-r + 3, r - 3)
            for y in (y0 + 1, y0 + 2):
                if t.get(x, y, z) == S(AIR):
                    t.put(x, y, z, E("shifting_block", solid="true"))
        # un cofre por piso
        x, z = c + rnd.randint(-3, 3), c + rnd.randint(-3, 3)
        if t.get(x, y0 + 1, z) == S(AIR):
            t.put(x, y0 + 1, z, *chest("north", n("chests/babel")))
        # los guardianes
        if floor in (2, 5):
            t.entity(boss_nbt(n("fallen"), "entity.evolvedwarfare.fallen_knight", 90,
                              (("minecraft:generic.armor", 14), ("minecraft:generic.attack_damage", 10))), c, y0 + 1, c, 0.0)
        elif floor == 4:
            t.entity(boss_nbt(n("nephilim"), "entity.evolvedwarfare.lesser_nephilim", 80, (("minecraft:generic.scale", 0.55),)), c, y0 + 1, c, 0.0)
        elif floor in (6, 7):
            for k in range(4):
                t.entity({"id": n("greater_locust"), "PersistenceRequired": nbt.Byte(1)}, c + k - 2, y0 + 3, c, 0.0)
    # la cima: un anillo de piedra en pie con el portal de las visiones
    top = BABEL_FLOORS * FLOOR_H
    rt = BABEL_R - (BABEL_FLOORS - 1) // 3
    for x in range(BABEL_N):
        for z in range(BABEL_N):
            if math.hypot(x - c, z - c) <= rt - 0.5 and not collapsed(x, z, top - 1):
                t.put(x, top, z, BLACK if (x + z) % 3 else GILDED)
    for k in range(-4, 5):
        for y in range(top + 1, top + 10):
            dy = y - (top + 5)
            d = math.hypot(k, dy)
            if 3.2 <= d <= 4.6:
                t.put(c, y, c + k, S("crying_obsidian") if (k + y) % 3 == 0 else S("obsidian"))
            elif d < 3.2:
                t.put(c, y, c + k, E("babel_portal"))
    t.put(c + 2, top + 1, c, *chest("west", n("chests/babel_top")))
    return t


# ------------------------------------------------------------------ Templos de falsos dioses

TEMPLE_N = 23
CRYPT = 8


def temple():
    t = Template(TEMPLE_N, CRYPT + 12, TEMPLE_N)
    c = TEMPLE_N // 2
    g = CRYPT  # el suelo del templo
    stone = S("polished_blackstone")
    for x in range(TEMPLE_N):
        for z in range(TEMPLE_N):
            d = math.hypot(x - c, z - c)
            if d > c + 0.4:
                continue
            for y in range(0, g):
                t.put(x, y, z, BLACKSTONE)
            t.put(x, g, z, S("polished_blackstone") if int(d) % 3 else GILDED)
            for y in range(g + 1, g + 11):
                t.put(x, y, z, AIR)
            # el círculo de columnas y el techo en anillo
            if c - 2.2 < d <= c - 1.0 and (x + z) % 3 == 0:
                for y in range(g + 1, g + 8):
                    t.put(x, y, z, S("chiseled_polished_blackstone") if y in (g + 1, g + 7) else S("polished_blackstone_bricks"))
            if c - 3.5 < d <= c + 0.4:
                t.put(x, g + 8, z, stairs("blackstone", "north") if d > c - 0.6 else S("polished_blackstone_bricks"))
    # el altar en el centro, con cadenas y braseros
    t.put(c, g + 1, c, E("false_altar"))
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        t.put(c + dx, g + 1, c + dz, S("soul_campfire", facing="north", lit="true", signal_fire="false", waterlogged="false"))
    # los ídolos: cuatro estatuas mirando al altar
    for (dx, dz, face) in ((5, 0, "west"), (-5, 0, "east"), (0, 5, "north"), (0, -5, "south")):
        x, z = c + dx, c + dz
        t.put(x, g + 1, z, S("chiseled_polished_blackstone"))
        t.put(x, g + 2, z, S("polished_blackstone"))
        t.put(x, g + 3, z, E("idol", facing=face))
    # la bajada a la cripta
    for k in range(CRYPT - 1):
        x = c + 4 - k if k < 6 else c - 2
        z = c - 7 + (0 if k < 6 else k - 5)
        for y in range(g - k, g + 3 - k):
            t.put(x, y, z, AIR)
            t.put(x, y, z + 1, AIR)
        t.put(x, g - k - 1, z, stairs("polished_blackstone_brick", "east"))
        t.put(x, g - k - 1, z + 1, stairs("polished_blackstone_brick", "east"))
    # la cripta: una sala baja con nichos, más ofrendas y libros
    for x in range(c - 6, c + 5):
        for z in range(c - 6, c + 6):
            for y in range(1, 5):
                t.put(x, y, z, AIR)
            t.put(x, 0, z, S("polished_blackstone_bricks"))
    for z in range(c - 5, c + 5, 2):
        t.put(c - 6, 2, z, S("skeleton_skull", rotation=4))
        t.put(c + 4, 2, z, S("skeleton_skull", rotation=12))
    t.put(c, 1, c, E("false_altar"))
    t.put(c - 4, 1, c + 4, *chest("east", n("chests/temple_crypt")))
    t.put(c + 2, 1, c + 4, *chest("west", n("chests/temple_crypt")))
    t.put(c - 1, 1, c - 5, S("bookshelf"))
    t.put(c, 1, c - 5, S("bookshelf"))
    t.put(c + 1, 1, c - 5, *chest("south", n("chests/lore")))
    for x in (c - 5, c + 3):
        t.put(x, 4, c, S("soul_lantern", hanging="true", waterlogged="false"))
    t.put(c + 3, g + 1, c + 3, *chest("north", n("chests/temple_offerings")))
    return t


# ------------------------------------------------------------------ Campos de pilares de sal

FIELD = 48


def salt_field():
    t = Template(FIELD, 12, FIELD)
    rnd = rng_for("salt_field")
    salt = E("salt_block")
    # un laberinto: pasillos de 2 entre bloques de pilares
    cells = FIELD // 4
    open_ = set()
    stack = [(0, 0)]
    seen = {(0, 0)}
    while stack:
        cx, cz = stack[-1]
        nb = [(cx + dx, cz + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= cx + dx < cells and 0 <= cz + dz < cells
              and (cx + dx, cz + dz) not in seen]
        if not nb:
            stack.pop()
            continue
        nx, nz = rnd.choice(nb)
        open_.add(((cx, cz), (nx, nz)))
        open_.add(((nx, nz), (cx, cz)))
        seen.add((nx, nz))
        stack.append((nx, nz))
    for x in range(FIELD):
        for z in range(FIELD):
            t.put(x, 0, z, E("fine_salt") if rnd.random() < 0.6 else salt)
    for cx in range(cells):
        for cz in range(cells):
            # los "muros" del laberinto son filas de pilares
            for dx, dz in ((1, 0), (0, 1)):
                nb = (cx + dx, cz + dz)
                if nb[0] >= cells or nb[1] >= cells or ((cx, cz), nb) in open_:
                    continue
                for k in range(4):
                    x = cx * 4 + (3 if dx else k)
                    z = cz * 4 + (k if dx else 3)
                    if rnd.random() < 0.85:
                        h = rnd.randint(3, 10)
                        for y in range(1, h):
                            t.put(x, y, z, salt)
                        if rnd.random() < 0.3 and h > 4:
                            t.put(x, h, z, S("calcite"))
    # estatuas de sal entre los pilares
    for k in range(8):
        x, z = rnd.randrange(2, FIELD - 2), rnd.randrange(2, FIELD - 2)
        if t.get(x, 1, z) is None:
            t.entity({"id": n("salt_statue"), "PersistenceRequired": nbt.Byte(1)}, x, 1, z, rnd.uniform(0, 360))
    for k in range(3):
        x, z = rnd.randrange(4, FIELD - 4), rnd.randrange(4, FIELD - 4)
        if t.get(x, 1, z) is None:
            t.put(x, 1, z, *chest("north", n("chests/salt_field")))
    return t


# ------------------------------------------------------------------ Cementerios de ángeles caídos

YARD = 36


def graveyard():
    t = Template(YARD, 8, YARD)
    rnd = rng_for("graveyard")
    for x in range(YARD):
        for z in range(YARD):
            t.put(x, 0, z, S("soul_soil") if rnd.random() < 0.5 else E("bone_dust"))
    faces = ["north", "south", "east", "west"]
    # alas rotas clavadas en el suelo (cientos), armaduras y espadas que aún brillan
    for k in range(170):
        x, z = rnd.randrange(YARD), rnd.randrange(YARD)
        if t.get(x, 1, z) is None:
            t.put(x, 1, z, E("fallen_wing", facing=rnd.choice(faces)))
            if rnd.random() < 0.3:
                t.put(x, 2, z, E("fallen_wing", facing=rnd.choice(faces)))
    for k in range(10):
        x, z = rnd.randrange(2, YARD - 2), rnd.randrange(2, YARD - 2)
        if t.get(x, 1, z) is None:
            t.put(x, 1, z, E("fallen_sword", facing=rnd.choice(faces)))
    for k in range(9):
        x, z = rnd.randrange(2, YARD - 2), rnd.randrange(2, YARD - 2)
        if t.get(x, 1, z) is None:
            from ew_structlib import armor_stand
            stand = armor_stand(head="chainmail_helmet" if k % 2 else "iron_helmet", chest_="chainmail_chestplate", legs="chainmail_leggings")
            stand["Pose"] = {"Head": nbt.List(nbt.FLOAT, [nbt.Float(35.0), nbt.Float(0.0), nbt.Float(0.0)]),
                             "Body": nbt.List(nbt.FLOAT, [nbt.Float(15.0), nbt.Float(0.0), nbt.Float(0.0)])}
            stand["NoBasePlate"] = nbt.Byte(1)
            t.entity(stand, x, 1, z, rnd.uniform(0, 360))
    # tumbas abiertas: un hoyo con la losa a un lado, y algunas con un caído dormido
    for k in range(8):
        x, z = 3 + (k % 4) * 8, 6 + (k // 4) * 18
        for dz in range(3):
            t.put(x, 0, z + dz, AIR)
            t.put(x, -0 + 0, z + dz, AIR)
            t.put(x + 1, 1, z + dz, slab("polished_blackstone"))
        t.put(x, 1, z - 1, S("polished_blackstone_brick_wall"))
        t.put(x, 2, z - 1, S("skeleton_skull", rotation=8))
        if k % 2 == 0:
            t.entity(dormant(n("fallen")), x, 0, z + 1, 0.0)
    t.put(YARD // 2, 1, YARD // 2, *chest("north", n("chests/graveyard")))
    return t


# ------------------------------------------------------------------ el trono del Lago de Fuego

def lake_throne():
    t = Template(13, 18, 13)
    c = 6
    g = 9  # la plataforma, a ras de la lava
    for x in range(13):
        for z in range(13):
            d = math.hypot(x - c, z - c)
            if d > 6.4:
                continue
            t.put(x, g, z, BLACK if d < 5 else CRACKED)
            for y in range(g + 1, g + 9):
                t.put(x, y, z, AIR)
            if d < 2.5 or (x - c) % 4 == 0 and (z - c) % 4 == 0:
                for y in range(0, g):
                    t.put(x, y, z, BLACKSTONE)
    # el trono vacío de piedra negra, con cadenas rotas
    t.put(c, g + 1, c, stairs("blackstone", "west"))
    for dz in (-1, 1):
        t.put(c, g + 1, c + dz, BLACK)
        t.put(c, g + 2, c + dz, S("polished_blackstone_brick_wall"))
    for y in range(g + 1, g + 6):
        t.put(c + 1, y, c, BLACK if y < g + 5 else GILDED)
    t.put(c + 1, g + 6, c, S("wither_skeleton_skull", rotation=4))
    for (x, z) in ((c - 4, c - 4), (c + 4, c - 4), (c - 4, c + 4), (c + 4, c + 4)):
        for y in range(g + 1, g + 5):
            t.put(x, y, z, BLACK)
        t.put(x, g + 5, z, S("chain", axis="y", waterlogged="false"))
        t.put(x, g + 1, z + (1 if z < c else -1), S("chain", axis="x", waterlogged="false"))
    t.put(c - 2, g + 1, c, E("broken_seal"))
    t.put(c - 3, g + 1, c + 2, *chest("east", n("chests/lake_throne")))
    return t


# ------------------------------------------------------------------ las cámaras selladas del Abismo

def abyss_vault():
    t = Template(15, 34, 15)
    c = 7
    for x in range(15):
        for z in range(15):
            for y in range(0, 10):
                edge = x in (0, 14) or z in (0, 14) or y in (0, 9)
                t.put(x, y, z, (CRACKED if (x * y + z) % 7 == 0 else BLACK) if edge else AIR)
    # el pozo (se ve como un brocal en el lago de lava del fondo)
    for y in range(9, 34):
        for x in range(c - 2, c + 3):
            for z in range(c - 2, c + 3):
                edge = x in (c - 2, c + 2) or z in (c - 2, c + 2)
                t.put(x, y, z, BLACK if edge else AIR)
        t.put(c, y, c - 1, S("ladder", facing="south", waterlogged="false"))
    for x in range(c - 2, c + 3):
        for z in range(c - 2, c + 3):
            if x in (c - 2, c + 2) or z in (c - 2, c + 2):
                t.put(x, 33, z, S("polished_blackstone_brick_wall"))
    # el sello del suelo (roto) y lo guardado
    for x in range(c - 3, c + 4):
        for z in range(c - 3, c + 4):
            if 2.5 < math.hypot(x - c, z - c) < 3.5 and (x + z) % 3:
                t.put(x, 0, z, E("broken_seal"))
    for (x, z, f) in ((1, 1, "south"), (13, 13, "north"), (1, 13, "north"), (13, 1, "south")):
        t.put(x, 1, z, *chest(f, n("chests/abyss_vault")))
    for (x, z) in ((3, 7), (11, 7)):
        t.put(x, 1, z, S("soul_campfire", facing="north", lit="false", signal_fire="false", waterlogged="false"))
    t.put(c, 1, c, S("chain", axis="y", waterlogged="false"))
    return t


# ------------------------------------------------------------------ las minas de azufre abandonadas

def sulfur_mine():
    t = Template(9, 28, 26)
    top = 20
    # la torre del pozo (de madera quemada)
    for y in range(top, top + 6):
        for (x, z) in ((1, 1), (7, 1), (1, 7), (7, 7)):
            t.put(x, y, z, S("stripped_dark_oak_log", axis="y"))
    for x in range(1, 8):
        t.put(x, top + 6, 4, S("dark_oak_planks"))
    t.put(4, top + 5, 4, S("chain", axis="y", waterlogged="false"))
    # el pozo hasta la galería
    for y in range(4, top + 1):
        for x in range(3, 6):
            for z in range(3, 6):
                t.put(x, y, z, AIR)
        t.put(4, y, 3, S("ladder", facing="south", waterlogged="false"))
    # la galería: entibada, con raíles, cristales de azufre y herramientas olvidadas
    for z in range(3, 26):
        for x in range(2, 7):
            for y in range(1, 5):
                t.put(x, y, z, AIR)
            t.put(x, 0, z, E("brimstone_block") if (x + z) % 4 == 0 else BLACKSTONE)
        if z % 4 == 0:
            for y in range(1, 5):
                t.put(2, y, z, S("dark_oak_fence"))
                t.put(6, y, z, S("dark_oak_fence"))
            for x in range(2, 7):
                t.put(x, 5, z, S("dark_oak_planks"))
        t.put(4, 1, z, S("rail", shape="north_south", waterlogged="false"))
        if z % 3 == 1:
            t.put(2 if z % 2 else 6, 1, z, E("sulfur_crystal", facing="up", waterlogged="false"))
            t.put(2 if z % 2 == 0 else 6, 4, z, E("sulfur_crystal", facing="down", waterlogged="false"))
    t.put(5, 1, 12, *chest("west", n("chests/sulfur_mine")))
    t.put(3, 1, 22, *chest("east", n("chests/sulfur_mine")))
    t.put(5, 1, 18, S("cobweb"))
    for z in (8, 16):
        t.put(4, 4, z, S("lantern", hanging="true", waterlogged="false"))
    return t


# ------------------------------------------------------------------ Sodoma: casas y templos

def sodom_house(seed):
    t = Template(11, 9, 11)
    rnd = random.Random(seed)
    for x in range(11):
        for z in range(11):
            edge = x in (0, 10) or z in (0, 10)
            t.put(x, 0, z, BLACKSTONE)
            t.put(x, 1, z, S("polished_blackstone") if not edge else BLACK)
            for y in range(2, 7):
                if edge:
                    broken = y > 3 + rnd.randint(0, 3)
                    t.put(x, y, z, AIR if broken else (CRACKED if rnd.random() < 0.2 else BLACK))
                else:
                    t.put(x, y, z, AIR)
    for y in (2, 3):
        t.put(0, y, 5, AIR)
    # la trampa de presión: una placa delante de la puerta y un dispensador escondido en la pared que dispara fuego
    t.put(2, 2, 5, S("polished_blackstone_pressure_plate", powered="false"))
    t.put(2, 1, 5, BLACK)
    t.put(2, 2, 6, S("dispenser", facing="north", triggered="false"), {"id": "minecraft:dispenser", "Items": nbt.List(nbt.COMPOUND, [
        {"Slot": nbt.Byte(0), "id": "minecraft:fire_charge", "count": 8}])})
    # el suelo que se hunde, sobre un hoyo de lava
    for x in range(5, 8):
        for z in range(4, 7):
            t.put(x, 1, z, E("crumbling_floor"))
            t.put(x, 0, z, S("lava"))
    # el habitante inmóvil (hasta que te acercas)
    t.entity(dormant("minecraft:husk", "entity.evolvedwarfare.sodom_dweller"), 8, 2, 8, 225.0)
    t.put(9, 2, 1, *chest("south", n("chests/sodom_house")))
    t.put(9, 2, 9, E("salt_block"))
    return t


def sodom_temple():
    t = Template(17, 14, 21)
    rnd = rng_for("sodom_temple")
    for x in range(17):
        for z in range(21):
            edge = x in (0, 16) or z in (0, 20)
            t.put(x, 0, z, BLACKSTONE)
            t.put(x, 1, z, S("polished_blackstone_bricks") if (x + z) % 2 else S("polished_blackstone"))
            for y in range(2, 12):
                if edge:
                    t.put(x, y, z, AIR if y > 7 + rnd.randint(0, 4) else (S("red_nether_bricks") if y % 4 == 0 else BLACK))
                else:
                    t.put(x, y, z, AIR)
    for y in range(2, 6):
        for z in range(9, 12):
            t.put(0, y, z, AIR)
    # columnas, el altar del fondo y los habitantes en sus nichos
    for x in (4, 12):
        for z in range(4, 18, 4):
            for y in range(2, 11):
                t.put(x, y, z, S("polished_blackstone") if y % 3 else GILDED)
    t.put(14, 2, 10, E("false_altar"))
    t.put(15, 2, 10, BLACK)
    t.put(15, 3, 10, E("idol", facing="west"))
    for z in (5, 15):
        t.entity(dormant("minecraft:husk", "entity.evolvedwarfare.sodom_dweller"), 13, 2, z, 270.0)
    # la nave: suelo que se hunde en una franja, con placas que disparan desde las columnas
    for x in range(6, 11):
        for z in range(9, 12):
            t.put(x, 1, z, E("crumbling_floor"))
            t.put(x, 0, z, S("magma_block"))
    for z in (8, 12):
        t.put(5, 2, z, S("polished_blackstone_pressure_plate", powered="false"))
        t.put(4, 2, z, S("dispenser", facing="east", triggered="false"), {"id": "minecraft:dispenser", "Items": nbt.List(nbt.COMPOUND, [
            {"Slot": nbt.Byte(0), "id": "minecraft:arrow", "count": 16}])})
    t.put(14, 2, 4, *chest("west", n("chests/sodom_temple")))
    t.put(14, 2, 16, *chest("west", n("chests/lore")))
    return t


# ------------------------------------------------------------------ las estructuras y su sitio

def element(location):
    return {"element_type": "minecraft:single_pool_element", "location": location, "processors": {"processors": []}, "projection": "rigid"}


STRUCTS = collections.OrderedDict([
    # id: (plantillas, biomas, altura, al suelo, adaptación, espaciado, separación, sal)
    ("babel_tower", ([("babel_tower", babel)], ["sodom_ruins", "valley_of_bones"], -2, True, "beard_box", 34, 14, 66611)),
    ("false_temple", ([("false_temple", temple)], ["sodom_ruins", "valley_of_bones", "sulfur_mountains", "lake_of_fire"], -CRYPT, True, "beard_thin", 22, 8,
                      66612)),
    ("salt_pillar_field", ([("salt_pillar_field", salt_field)], ["sodom_ruins", "valley_of_bones"], 0, True, "beard_thin", 18, 6, 66613)),
    ("fallen_graveyard", ([("fallen_graveyard", graveyard)], ["valley_of_bones", "sulfur_mountains"], 0, True, "beard_thin", 20, 7, 66614)),
    ("lake_throne", ([("lake_throne", lake_throne)], ["lake_of_fire"], -9, True, "none", 14, 5, 66615)),
    ("abyss_vault", ([("abyss_vault", abyss_vault)], ["abyss"], 0, False, "none", 10, 3, 66616)),
    ("sulfur_mine", ([("sulfur_mine", sulfur_mine)], ["sulfur_mountains"], -20, True, "none", 12, 4, 66617)),
    ("sodom_house", ([("sodom_house_%d" % k, (lambda k=k: sodom_house(k))) for k in range(3)], ["sodom_ruins"], -1, True, "beard_thin", 7, 2,
                     66618)),
    ("sodom_temple", ([("sodom_temple", sodom_temple)], ["sodom_ruins"], -1, True, "beard_thin", 16, 6, 66619)),
])


def loot_entry(name, lo, hi, weight=1, damage=None, named=None):
    f = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False}]
    if damage:
        f.append({"function": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": damage[0], "max": damage[1]}, "add": False})
    if named:
        f.append({"function": "minecraft:set_name", "name": {"translate": named, "italic": False}, "target": "item_name"})
    return {"type": "minecraft:item", "name": name, "weight": weight, "functions": f}


LORE = 10


def lore_book_entry():
    entries = []
    for k in range(LORE):
        entries.append({"type": "minecraft:item", "name": "minecraft:written_book", "functions": [
            {"function": "minecraft:set_book_cover", "title": "~", "author": "?", "generation": 3},
            {"function": "minecraft:set_written_book_pages", "mode": "replace_all", "pages": [
                {"translate": "lore.evolvedwarfare.%d.%d" % (k, p)} for p in range(2)]},
            {"function": "minecraft:set_name", "name": {"translate": "lore.evolvedwarfare.%d.title" % k, "italic": False, "color": "gold"},
             "target": "custom_name"}]})
    return entries


def pool(rolls, entries):
    return {"rolls": rolls if isinstance(rolls, (int, dict)) else {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "bonus_rolls": 0,
            "entries": entries}


def chance_pool(ch, entries):
    p = pool(1, entries)
    p["conditions"] = [{"condition": "minecraft:random_chance", "chance": ch}]
    return p


def L(*pools):
    return {"type": "minecraft:chest", "pools": list(pools)}


LOOTS = {
    "babel": L(pool((3, 6), [loot_entry("minecraft:gold_ingot", 1, 5, 8), loot_entry(n("abyss_metal"), 1, 2, 3), loot_entry("minecraft:paper", 2, 6, 6),
                             loot_entry("minecraft:ink_sac", 1, 3, 4), loot_entry(n("dark_feather"), 1, 3, 4), loot_entry("minecraft:lapis_lazuli", 2, 8, 5),
                             loot_entry("minecraft:clay_ball", 2, 6, 4), loot_entry("minecraft:brick", 2, 8, 5)]),
               chance_pool(0.35, [loot_entry(n("book_of_condemnation"), 1, 1)]), chance_pool(0.4, lore_book_entry())),
    "babel_top": L(pool((4, 7), [loot_entry("minecraft:diamond", 1, 4, 6), loot_entry("minecraft:gold_block", 1, 2, 4), loot_entry(n("abyss_metal"), 2, 5, 5),
                                 loot_entry("minecraft:netherite_scrap", 1, 2, 2), loot_entry("minecraft:ender_eye", 1, 3, 3),
                                 loot_entry("minecraft:echo_shard", 1, 2, 2)]),
                   pool(1, [loot_entry(n("book_of_condemnation"), 1, 2)]), pool(1, lore_book_entry())),
    "temple_offerings": L(pool((3, 6), [loot_entry("minecraft:gold_nugget", 3, 12, 8), loot_entry("minecraft:gold_ingot", 1, 4, 6),
                                        loot_entry("minecraft:bread", 1, 3, 4), loot_entry("minecraft:golden_carrot", 1, 3, 3),
                                        loot_entry("minecraft:candle", 1, 4, 5), loot_entry("minecraft:golden_sword", 1, 1, 2),
                                        loot_entry("minecraft:book", 1, 2, 3)])),
    "temple_crypt": L(pool((3, 5), [loot_entry("minecraft:gold_ingot", 2, 6, 6), loot_entry("minecraft:golden_apple", 1, 1, 2),
                                    loot_entry("minecraft:bone", 2, 6, 5), loot_entry("minecraft:emerald", 1, 4, 4), loot_entry(n("blessed_salt"), 1, 2, 3)]),
                      chance_pool(0.5, [loot_entry(n("book_of_condemnation"), 1, 1)]), chance_pool(0.6, lore_book_entry())),
    "lore": L(pool((1, 2), lore_book_entry()), chance_pool(0.4, [loot_entry(n("book_of_condemnation"), 1, 1)])),
    "salt_field": L(pool((2, 5), [loot_entry(n("fine_salt"), 2, 8, 8), loot_entry(n("blessed_salt"), 1, 2, 3), loot_entry("minecraft:gold_nugget", 2, 8, 5),
                                  loot_entry(n("stone_fruit"), 1, 2, 3), loot_entry("minecraft:bone", 1, 4, 4)])),
    "graveyard": L(pool((3, 6), [loot_entry(n("fallen_armor_shard"), 1, 3, 6), loot_entry(n("dark_feather"), 1, 4, 6),
                                 loot_entry("minecraft:iron_chestplate", 1, 1, 2, damage=(0.15, 0.4), named="item.evolvedwarfare.rusty_armor"),
                                 loot_entry("minecraft:iron_helmet", 1, 1, 2, damage=(0.15, 0.4), named="item.evolvedwarfare.rusty_armor"),
                                 loot_entry("minecraft:chainmail_leggings", 1, 1, 2, damage=(0.2, 0.5)), loot_entry("minecraft:gold_ingot", 1, 3, 3)]),
                   chance_pool(0.3, lore_book_entry())),
    "lake_throne": L(pool((3, 5), [loot_entry("minecraft:gold_ingot", 2, 6, 6), loot_entry("minecraft:blaze_rod", 1, 4, 5),
                                   loot_entry(n("brimstone"), 3, 8, 6), loot_entry("minecraft:magma_cream", 1, 4, 4),
                                   loot_entry("minecraft:fire_charge", 2, 5, 4), loot_entry("minecraft:diamond", 1, 2, 2)]),
                     chance_pool(0.4, lore_book_entry())),
    "abyss_vault": L(pool((4, 7), [loot_entry(n("abyss_metal"), 2, 6, 6), loot_entry("minecraft:diamond", 1, 3, 5), loot_entry("minecraft:netherite_scrap", 1, 2, 3),
                                   loot_entry("minecraft:echo_shard", 1, 2, 3), loot_entry("minecraft:ancient_debris", 1, 2, 2),
                                   loot_entry("minecraft:crying_obsidian", 2, 6, 4)]),
                     chance_pool(0.5, [loot_entry(n("book_of_condemnation"), 1, 1)]), chance_pool(0.5, lore_book_entry())),
    "sulfur_mine": L(pool((3, 6), [loot_entry(n("brimstone"), 4, 12, 8), loot_entry("minecraft:coal", 3, 10, 6),
                                   loot_entry("minecraft:iron_pickaxe", 1, 1, 3, damage=(0.1, 0.35), named="item.evolvedwarfare.rusty_pickaxe"),
                                   loot_entry("minecraft:iron_shovel", 1, 1, 2, damage=(0.1, 0.35), named="item.evolvedwarfare.rusty_shovel"),
                                   loot_entry("minecraft:torch", 3, 12, 5), loot_entry("minecraft:gold_nugget", 2, 9, 4), loot_entry("minecraft:rail", 2, 8, 3)])),
    "sodom_house": L(pool((2, 4), [loot_entry(n("fine_salt"), 2, 6, 6), loot_entry("minecraft:gold_nugget", 2, 7, 5), loot_entry("minecraft:charcoal", 1, 4, 4),
                                   loot_entry("minecraft:bread", 1, 2, 2), loot_entry(n("stone_fruit"), 1, 1, 2)]),
                     chance_pool(0.25, lore_book_entry())),
    "sodom_temple": L(pool((3, 6), [loot_entry("minecraft:gold_ingot", 2, 6, 6), loot_entry(n("blessed_salt"), 1, 3, 4), loot_entry("minecraft:emerald", 1, 4, 4),
                                    loot_entry("minecraft:golden_apple", 1, 1, 2), loot_entry("minecraft:diamond", 1, 2, 2)]),
                      chance_pool(0.5, [loot_entry(n("book_of_condemnation"), 1, 1)])),
    "citadel_library": L(pool((2, 4), [loot_entry("minecraft:paper", 2, 8, 6), loot_entry("minecraft:book", 1, 4, 5), loot_entry("minecraft:ink_sac", 1, 3, 3)]),
                         pool(1, [loot_entry(n("book_of_condemnation"), 1, 2)]), pool(1, lore_book_entry())),
    "citadel_secret": L(pool((5, 8), [loot_entry("minecraft:netherite_ingot", 1, 1, 2), loot_entry("minecraft:diamond", 3, 7, 6),
                                      loot_entry("minecraft:enchanted_golden_apple", 1, 1, 2), loot_entry(n("abyss_metal"), 3, 8, 5),
                                      loot_entry("minecraft:totem_of_undying", 1, 1, 2), loot_entry("minecraft:nether_star", 1, 1, 1)]),
                        pool(1, [{"type": "minecraft:item", "name": "minecraft:book", "functions": [
                            {"function": "minecraft:set_enchantments", "enchantments": {n("divine_blessing"): 1}}]}])),
}

# los textos de los libros de saber: (título, página 1, página 2), en español e inglés
LORE_TEXTS = [
    (("La torre", "The tower"),
     ("Quisieron llegar al cielo y el cielo les quitó la lengua. Ahora cada piedra habla un idioma distinto.",
      "They wanted to reach heaven and heaven took their tongue. Now every stone speaks a different language."),
     ("En lo alto hay una puerta que no lleva a ninguna parte. A veces, por un instante, sí.",
      "At the top there is a door that leads nowhere. Sometimes, for an instant, it does.")),
    (("Los caídos", "The fallen"),
     ("No cayeron de golpe. Cayeron durante nueve días y nueve noches, y al llegar ya no recordaban la luz.",
      "They did not fall at once. They fell for nine days and nine nights, and on arrival they no longer remembered the light."),
     ("No les mires a los ojos. Lo que queda en ellos ciega.", "Do not look into their eyes. What remains in them blinds.")),
    (("Las ciudades de la llanura", "The cities of the plain"),
     ("Llovió azufre y fuego. Quien miró atrás se quedó allí, de sal, mirando para siempre.",
      "Brimstone and fire rained down. Whoever looked back stayed there, made of salt, looking forever."),
     ("Las estatuas no se mueven mientras alguien las mira. Nunca estés solo entre ellas.",
      "The statues do not move while someone watches them. Never be alone among them.")),
    (("El pozo del abismo", "The bottomless pit"),
     ("Se abrió el pozo y subió humo como de un gran horno, y del humo salieron langostas.",
      "The pit was opened and smoke rose like from a great furnace, and out of the smoke came locusts."),
     ("Abajo del todo, bajo el fuego, hay cámaras que alguien selló. Alguien que tenía miedo.",
      "At the very bottom, under the fire, there are chambers someone sealed. Someone who was afraid.")),
    (("Abadón", "Abaddon"),
     ("Su nombre es Destrucción. Guarda un trono que no es suyo y espera a quien haya muerto lo bastante.",
      "His name is Destruction. He keeps a throne that is not his and waits for one who has died enough."),
     ("Tres veces hay que caer en este reino para que el trono te reconozca.", "Three times one must fall in this realm for the throne to know you.")),
    (("Los falsos dioses", "The false gods"),
     ("Los ídolos aceptan lo que les des. A veces devuelven fuerza; a veces, solo su mirada.",
      "The idols accept whatever you give them. Sometimes they return strength; sometimes, only their gaze."),
     ("Oro, pan, libros, espadas: todo les gusta. Nada les basta.", "Gold, bread, books, swords: they like everything. Nothing is enough.")),
    (("Los sellos", "The seals"),
     ("Eran siete. Los rompieron uno a uno, y por cada sello roto se abrió una puerta.",
      "There were seven. They broke them one by one, and for every broken seal a door opened."),
     ("Quien reúna sus pedazos podrá abrir lo que está más hondo.", "Whoever gathers their pieces will be able to open what lies deepest.")),
    (("La sal", "The salt"),
     ("La sal bendita aparta a los que roban. La sal maldita llama a los que esperan.",
      "Blessed salt keeps away those who steal. Cursed salt calls those who wait."),
     ("Mezclada con oro y luz, la sal recuerda lo que fue: pureza.", "Mixed with gold and light, salt remembers what it was: purity.")),
    (("El valle seco", "The dry valley"),
     ("Profetiza a estos huesos. Pero no te quedes quieto entre ellos: oyen.", "Prophesy to these bones. But do not stand still among them: they hear."),
     ("El viento del valle no es aire: es el aliento de los que esperan levantarse.",
      "The valley wind is not air: it is the breath of those waiting to rise.")),
    (("Diario de un minero", "A miner's diary"),
     ("Día 40. El azufre crece como si estuviera vivo. Las antorchas no aguantan. Juan dice que oye cantar a la roca.",
      "Day 40. The sulfur grows as if alive. Torches don't last. Juan says he hears the rock singing."),
     ("Día 41. No encontramos a Juan. Nos vamos. Dejamos las herramientas: que se las quede la montaña.",
      "Day 41. We can't find Juan. We're leaving. We leave the tools: let the mountain keep them.")),
]

TEXTS = {
    "entity.evolvedwarfare.fallen_knight": ("Caído acorazado", "Armored Fallen"),
    "entity.evolvedwarfare.lesser_nephilim": ("Nephilim menor", "Lesser Nephilim"),
    "entity.evolvedwarfare.sodom_dweller": ("Habitante de Sodoma", "Dweller of Sodom"),
    "item.evolvedwarfare.rusty_armor": ("Armadura oxidada", "Rusty Armor"),
    "item.evolvedwarfare.rusty_pickaxe": ("Pico oxidado", "Rusty Pickaxe"),
    "item.evolvedwarfare.rusty_shovel": ("Pala oxidada", "Rusty Shovel"),
    "block.evolvedwarfare.babel_rune": ("Runa de Babel", "Babel Rune"),
    "block.evolvedwarfare.crumbling_floor": ("Suelo agrietado", "Crumbling Floor"),
    "block.evolvedwarfare.shifting_block": ("Bloque cambiante", "Shifting Block"),
    "block.evolvedwarfare.babel_portal": ("Puerta de las visiones", "Gate of Visions"),
    "block.evolvedwarfare.false_altar": ("Altar de un falso dios", "Altar of a False God"),
    "block.evolvedwarfare.idol": ("Ídolo", "Idol"),
    "block.evolvedwarfare.fallen_wing": ("Ala rota", "Broken Wing"),
    "block.evolvedwarfare.fallen_sword": ("Espada clavada", "Embedded Sword"),
    "block.evolvedwarfare.abyss_throne": ("Trono del Abismo", "Throne of the Abyss"),
    "block.evolvedwarfare.sealed_wall": ("Muro sellado", "Sealed Wall"),
    "block.evolvedwarfare.broken_seal": ("Sello roto", "Broken Seal"),
    "item.evolvedwarfare.book_of_condemnation": ("Libro de condena", "Book of Condemnation"),
    "item.evolvedwarfare.book_of_condemnation.desc": ("Leerlo hace daño... pero revela.", "Reading it hurts... but it reveals."),
    "item.evolvedwarfare.fallen_blade": ("Hoja caída", "Fallen Blade"),
    "blade.evolvedwarfare.wrath": ("Hoja de la Ira", "Blade of Wrath"),
    "blade.evolvedwarfare.plague": ("Hoja de la Plaga", "Blade of Plague"),
    "blade.evolvedwarfare.silence": ("Hoja del Silencio", "Blade of Silence"),
    "blade.evolvedwarfare.fall": ("Hoja de la Caída", "Blade of the Fall"),
    "blade.evolvedwarfare.wrath.desc": ("Prende fuego a lo que hiere. A veces, a ti el calor del juicio.",
                                        "Sets fire to what it wounds. Sometimes, to you the heat of judgement."),
    "blade.evolvedwarfare.plague.desc": ("Envenena y marchita. A ti te da hambre.", "Poisons and withers. It makes you hungry."),
    "blade.evolvedwarfare.silence.desc": ("Ciega a lo que hiere. A veces te ciega a ti.", "Blinds what it wounds. Sometimes it blinds you."),
    "blade.evolvedwarfare.fall.desc": ("Golpea muy fuerte y lanza lejos. Cada golpe te cuesta vida.",
                                       "Hits very hard and throws far. Every blow costs you life."),
    "message.evolvedwarfare.altar.boon": ("El ídolo acepta tu ofrenda.", "The idol accepts your offering."),
    "message.evolvedwarfare.altar.curse": ("El ídolo te mira... y te maldice.", "The idol looks at you... and curses you."),
    "message.evolvedwarfare.altar.refused": ("El ídolo no quiere eso.", "The idol does not want that."),
    "message.evolvedwarfare.altar.purified": ("La sal bendita arde en el altar: tus maldiciones se deshacen.",
                                              "The blessed salt burns on the altar: your curses come undone."),
    "message.evolvedwarfare.altar.wait": ("El ídolo aún está saciado.", "The idol is still sated."),
    "message.evolvedwarfare.throne.deaths": ("El trono no te reconoce (has caído %s de %s veces en este reino).",
                                             "The throne does not know you (you have fallen %s of %s times in this realm)."),
    "message.evolvedwarfare.throne.open": ("El trono te reconoce: algo se abre bajo el estrado.", "The throne knows you: something opens beneath the dais."),
    "message.evolvedwarfare.condemnation.reveal": ("El libro te muestra el camino: %s, a %s bloques hacia el %s.",
                                                   "The book shows you the way: %s, %s blocks to the %s."),
    "message.evolvedwarfare.condemnation.weaken": ("El libro pronuncia los nombres: los señores del abismo cercanos se debilitan.",
                                                   "The book speaks the names: nearby lords of the abyss weaken."),
    "message.evolvedwarfare.condemnation.nothing": ("Las páginas están en blanco para ti.", "The pages are blank to you."),
    "message.evolvedwarfare.portal.vision": ("Por un instante ves: %s", "For an instant you see: %s"),
    "message.evolvedwarfare.dormant.wake": ("¡Se mueve!", "It moves!"),
    "message.evolvedwarfare.sword.pulled": ("Arrancas la espada. Aún brilla: %s", "You pull the sword. It still glows: %s"),
    "direction.evolvedwarfare.north": ("norte", "north"),
    "direction.evolvedwarfare.south": ("sur", "south"),
    "direction.evolvedwarfare.east": ("este", "east"),
    "direction.evolvedwarfare.west": ("oeste", "west"),
}
VISIONS = [("un trono vacío en medio de un lago de fuego", "an empty throne in a lake of fire"),
           ("una ciudad de piedra negra hundida en la lava", "a black stone city sunk in lava"),
           ("un campo de alas clavadas en el polvo", "a field of wings stuck in the dust"),
           ("un pozo sin fondo y, abajo, una puerta sellada", "a bottomless pit and, below, a sealed door"),
           ("un ídolo que abre los ojos", "an idol opening its eyes"),
           ("una montaña de azufre que arde por dentro", "a mountain of sulfur burning inside"),
           ("tu propia tumba, abierta", "your own grave, open"),
           ("un mundo verde, lejos, muy lejos", "a green world, far, far away")]
for k, (es, en) in enumerate(VISIONS):
    TEXTS["vision.evolvedwarfare.%d" % k] = (es, en)
for k, ((tes, ten), (p1es, p1en), (p2es, p2en)) in enumerate(LORE_TEXTS):
    TEXTS["lore.evolvedwarfare.%d.title" % k] = (tes, ten)
    TEXTS["lore.evolvedwarfare.%d.0" % k] = (p1es, p1en)
    TEXTS["lore.evolvedwarfare.%d.1" % k] = (p2es, p2en)
STRUCT_NAMES = {"babel_tower": ("Torre de Babel", "Tower of Babel"), "false_temple": ("Templo de un falso dios", "Temple of a false god"),
                "salt_pillar_field": ("Campo de pilares de sal", "Field of salt pillars"), "fallen_graveyard": ("Cementerio de los caídos", "Graveyard of the fallen"),
                "lake_throne": ("El trono del lago", "The lake throne"), "abyss_vault": ("Una cámara sellada", "A sealed chamber"),
                "sulfur_mine": ("Una mina de azufre", "A sulfur mine"), "sodom_temple": ("El templo de Sodoma", "The temple of Sodom"),
                "nether_citadel": ("La Fortaleza del Abaddon", "The Fortress of Abaddon")}
for k, (es, en) in STRUCT_NAMES.items():
    TEXTS["structure.evolvedwarfare.%s" % k] = (es, en)


# ------------------------------------------------------------------ texturas de los bloques y objetos

GLYPHS = [
    [(3, 2, 3, 13), (3, 2, 12, 2), (12, 2, 12, 8)],                    # una letra como hebrea
    [(2, 3, 13, 3), (7, 3, 4, 13), (8, 3, 11, 13)],                    # cuña
    [(8, 2, 8, 13), (8, 2, 3, 7), (8, 2, 13, 7)],                      # runa (flecha)
    [(3, 3, 12, 12), (12, 3, 3, 12), (2, 8, 13, 8)],                   # estrella
    [(4, 2, 4, 13), (4, 7, 11, 7), (11, 2, 11, 13)],                   # H
    [(3, 12, 8, 3), (8, 3, 13, 12), (5, 9, 11, 9)],                    # A
    [(8, 2, 3, 8), (3, 8, 8, 13), (8, 13, 13, 8), (13, 8, 8, 2)],      # rombo
    [(3, 4, 12, 4), (3, 8, 12, 8), (3, 12, 12, 12), (7, 4, 7, 12)],    # escalera
]


def base_tex(name):
    return Image.open(os.path.join(VANILLA, "block", name + ".png")).convert("RGBA").resize((16, 16))


def textures():
    tex = os.path.join(ASSETS, "textures/block")
    for k, lines in enumerate(GLYPHS):
        im = base_tex("polished_blackstone_bricks")
        d = ImageDraw.Draw(im)
        for (x0, y0, x1, y1) in lines:
            d.line([(x0, y0), (x1, y1)], fill=(255, 190, 60, 255), width=1)
        im.save(os.path.join(tex, "babel_rune_%d.png" % k))
    im = base_tex("polished_blackstone")
    d = ImageDraw.Draw(im)
    r = random.Random(3)
    for _ in range(4):
        x, y = r.randrange(16), r.randrange(16)
        pts = [(x, y)]
        for _ in range(5):
            x, y = max(0, min(15, x + r.choice([-2, -1, 1, 2]))), max(0, min(15, y + r.choice([-2, -1, 1, 2])))
            pts.append((x, y))
        d.line(pts, fill=(14, 10, 12, 255))
    im.save(os.path.join(tex, "crumbling_floor.png"))
    im = base_tex("crying_obsidian")
    im.save(os.path.join(tex, "shifting_block.png"))
    ghost = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(ghost)
    d.rectangle((0, 0, 15, 15), outline=(150, 60, 220, 120))
    d.line((0, 0, 15, 15), fill=(150, 60, 220, 60))
    ghost.save(os.path.join(tex, "shifting_block_ghost.png"))
    portal = Image.new("RGBA", (16, 16 * 8), (0, 0, 0, 0))
    for f in range(8):
        for y in range(16):
            for x in range(16):
                v = 0.5 + 0.5 * math.sin((x * 0.7 + y * 0.4 + f * 0.8)) * math.cos((y * 0.6 - x * 0.3 - f * 0.5))
                portal.putpixel((x, y + f * 16), (int(40 + 120 * v), int(10 + 30 * v), int(60 + 140 * v), int(150 + 80 * v)))
    portal.save(os.path.join(tex, "babel_portal.png"))
    write_json(os.path.join(tex, "babel_portal.png.mcmeta"), {"animation": {"frametime": 3, "interpolate": True}})
    # el altar (lados y cara de arriba con el cuenco)
    side = base_tex("chiseled_polished_blackstone")
    side.save(os.path.join(tex, "false_altar_side.png"))
    top = base_tex("polished_blackstone")
    d = ImageDraw.Draw(top)
    d.ellipse((3, 3, 12, 12), fill=(30, 20, 18, 255), outline=(210, 170, 60, 255))
    d.ellipse((6, 6, 9, 9), fill=(140, 20, 20, 255))
    top.save(os.path.join(tex, "false_altar_top.png"))
    # el ídolo: la cara con ojos rojos (para la cabeza que gira)
    face = base_tex("polished_blackstone")
    d = ImageDraw.Draw(face)
    d.rectangle((3, 5, 6, 7), fill=(255, 40, 20, 255))
    d.rectangle((9, 5, 12, 7), fill=(255, 40, 20, 255))
    d.line((4, 11, 11, 11), fill=(20, 10, 10, 255))
    d.line((2, 1, 13, 1), fill=(210, 170, 60, 255))
    face.save(os.path.join(tex, "idol_face.png"))
    base_tex("polished_blackstone").save(os.path.join(tex, "idol_stone.png"))
    # el ala rota (plumas negras, con el hueso)
    wing = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(wing)
    d.line((2, 15, 12, 1), fill=(210, 200, 170, 255), width=1)
    for k in range(9):
        x, y = 3 + k, 14 - int(k * 1.4)
        d.line((x, y, x + 3, min(15, y + 5)), fill=(26, 20, 26, 255))
        d.line((x + 1, y, x + 4, min(15, y + 4)), fill=(50, 44, 52, 255))
    wing.save(os.path.join(tex, "fallen_wing.png"))
    sword = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(sword)
    d.line((8, 4, 8, 15), fill=(190, 190, 200, 255))
    d.line((7, 6, 7, 15), fill=(120, 120, 130, 255))
    d.line((5, 4, 11, 4), fill=(170, 140, 60, 255))
    d.line((8, 0, 8, 3), fill=(90, 70, 40, 255))
    d.point((8, 9), fill=(255, 250, 200, 255))
    sword.save(os.path.join(tex, "fallen_sword.png"))
    base_tex("crying_obsidian").save(os.path.join(tex, "abyss_throne_side.png"))
    seal = base_tex("polished_blackstone")
    d = ImageDraw.Draw(seal)
    d.ellipse((1, 1, 14, 14), outline=(220, 170, 50, 255))
    d.line((8, 1, 8, 14), fill=(220, 170, 50, 255))
    d.line((3, 4, 7, 9), fill=(20, 14, 12, 255), width=2)  # la grieta
    seal.save(os.path.join(tex, "broken_seal.png"))
    sealed = base_tex("polished_blackstone_bricks")
    d = ImageDraw.Draw(sealed)
    d.rectangle((5, 5, 10, 10), outline=(180, 40, 200, 255))
    d.point((7, 7), fill=(220, 90, 255, 255))
    sealed.save(os.path.join(tex, "sealed_wall.png"))
    # objetos
    itex = os.path.join(ASSETS, "textures/item")
    book = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(book)
    d.rectangle((3, 2, 13, 14), fill=(40, 16, 16, 255), outline=(20, 8, 8, 255))
    d.rectangle((3, 2, 4, 14), fill=(20, 8, 8, 255))
    d.line((7, 5, 11, 9), fill=(200, 30, 20, 255))
    d.line((11, 5, 7, 9), fill=(200, 30, 20, 255))
    d.line((5, 14, 13, 14), fill=(220, 210, 190, 255))
    book.save(os.path.join(itex, "book_of_condemnation.png"))
    for k, col in enumerate(((255, 110, 30), (110, 200, 60), (160, 160, 200), (220, 220, 255))):
        b = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        d = ImageDraw.Draw(b)
        d.line((3, 13, 13, 3), fill=(60, 58, 66, 255), width=2)
        d.line((4, 13, 13, 4), fill=col + (255,), width=1)
        d.line((2, 10, 6, 14), fill=(150, 120, 50, 255), width=1)
        d.line((1, 15, 3, 13), fill=(60, 40, 30, 255), width=2)
        b.save(os.path.join(itex, "fallen_blade_%d.png" % k))


def block_assets():
    M = os.path.join(ASSETS, "models")
    B = os.path.join(ASSETS, "blockstates")
    for k in range(8):
        write_json(os.path.join(M, "block/babel_rune_%d.json" % k), {"parent": "minecraft:block/cube_all", "textures": {"all": n("block/babel_rune_%d" % k)}})
    write_json(os.path.join(B, "babel_rune.json"), {"variants": {"glyph=%d" % k: {"model": n("block/babel_rune_%d" % k)} for k in range(8)}})
    write_json(os.path.join(M, "item/babel_rune.json"), {"parent": n("block/babel_rune_0")})
    for name in ("crumbling_floor", "broken_seal", "sealed_wall"):
        write_json(os.path.join(M, "block/%s.json" % name), {"parent": "minecraft:block/cube_all", "textures": {"all": n("block/" + name)}})
        write_json(os.path.join(B, "%s.json" % name), {"variants": {"": {"model": n("block/" + name)}}})
        write_json(os.path.join(M, "item/%s.json" % name), {"parent": n("block/" + name)})
    write_json(os.path.join(M, "block/shifting_block.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": n("block/shifting_block")}})
    write_json(os.path.join(M, "block/shifting_block_ghost.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": n("block/shifting_block_ghost")}})
    write_json(os.path.join(B, "shifting_block.json"), {"variants": {"solid=true": {"model": n("block/shifting_block")},
                                                                     "solid=false": {"model": n("block/shifting_block_ghost")}}})
    write_json(os.path.join(M, "item/shifting_block.json"), {"parent": n("block/shifting_block")})
    write_json(os.path.join(M, "block/babel_portal.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": n("block/babel_portal")}})
    write_json(os.path.join(B, "babel_portal.json"), {"variants": {"": {"model": n("block/babel_portal")}}})
    write_json(os.path.join(M, "item/babel_portal.json"), {"parent": n("block/babel_portal")})
    write_json(os.path.join(M, "block/false_altar.json"), {"parent": "minecraft:block/block", "textures": {
        "particle": n("block/false_altar_side"), "side": n("block/false_altar_side"), "top": n("block/false_altar_top")}, "elements": [
        {"from": [1, 0, 1], "to": [15, 3, 15], "faces": {f: {"texture": "#side"} for f in ("north", "south", "east", "west", "down")} | {"up": {"texture": "#side"}}},
        {"from": [3, 3, 3], "to": [13, 11, 13], "faces": {f: {"texture": "#side"} for f in ("north", "south", "east", "west")}},
        {"from": [0, 11, 0], "to": [16, 14, 16], "faces": {f: {"texture": "#side"} for f in ("north", "south", "east", "west", "down")} | {"up": {"texture": "#top"}}}]})
    write_json(os.path.join(B, "false_altar.json"), {"variants": {"": {"model": n("block/false_altar")}}})
    write_json(os.path.join(M, "item/false_altar.json"), {"parent": n("block/false_altar")})
    # el ídolo: un pedestal (la cabeza la dibuja su renderer)
    write_json(os.path.join(M, "block/idol.json"), {"parent": "minecraft:block/block", "textures": {"particle": n("block/idol_stone"), "s": n("block/idol_stone")},
                                                   "elements": [{"from": [3, 0, 3], "to": [13, 4, 13], "faces": {f: {"texture": "#s"} for f in
                                                                                                                  ("north", "south", "east", "west", "up", "down")}}]})
    write_json(os.path.join(B, "idol.json"), {"variants": {"facing=%s" % f: {"model": n("block/idol")} for f in ("north", "south", "east", "west")}})
    write_json(os.path.join(M, "item/idol.json"), {"parent": n("block/idol")})
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    write_json(os.path.join(M, "block/fallen_wing.json"), {"parent": "minecraft:block/block", "textures": {"particle": n("block/fallen_wing"),
                                                                                                         "w": n("block/fallen_wing")},
                                                          "elements": [{"from": [0, 0, 7.5], "to": [16, 16, 8.5], "faces": {
                                                              "north": {"texture": "#w"}, "south": {"texture": "#w"}}},
                                                              {"from": [0, 0, 7.5], "to": [16, 16, 8.5], "rotation": {"angle": 45, "axis": "y", "origin": [8, 8, 8]},
                                                               "faces": {"north": {"texture": "#w"}, "south": {"texture": "#w"}}}]})
    write_json(os.path.join(B, "fallen_wing.json"), {"variants": {"facing=%s" % f: {"model": n("block/fallen_wing"), "y": r} for f, r in rot.items()}})
    write_json(os.path.join(M, "item/fallen_wing.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": n("block/fallen_wing")}})
    write_json(os.path.join(M, "block/fallen_sword.json"), {"parent": "minecraft:block/cross", "textures": {"cross": n("block/fallen_sword")}})
    write_json(os.path.join(B, "fallen_sword.json"), {"variants": {"facing=%s" % f: {"model": n("block/fallen_sword"), "y": r} for f, r in rot.items()}})
    write_json(os.path.join(M, "item/fallen_sword.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": n("block/fallen_sword")}})
    write_json(os.path.join(M, "block/abyss_throne.json"), {"parent": "minecraft:block/block", "textures": {"particle": n("block/abyss_throne_side"),
                                                                                                          "s": n("block/abyss_throne_side")}, "elements": [
        {"from": [0, 0, 0], "to": [16, 8, 16], "faces": {f: {"texture": "#s"} for f in ("north", "south", "east", "west", "up", "down")}},
        {"from": [12, 8, 0], "to": [16, 16, 16], "faces": {f: {"texture": "#s"} for f in ("north", "south", "east", "west", "up", "down")}}]})
    write_json(os.path.join(B, "abyss_throne.json"), {"variants": {"facing=%s" % f: {"model": n("block/abyss_throne"), "y": (r + 90) % 360} for f, r in
                                                                   rot.items()}})
    write_json(os.path.join(M, "item/abyss_throne.json"), {"parent": n("block/abyss_throne")})
    write_json(os.path.join(M, "item/book_of_condemnation.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": n("item/book_of_condemnation")}})
    for k in range(4):
        write_json(os.path.join(M, "item/fallen_blade_%d.json" % k), {"parent": "minecraft:item/handheld", "textures": {"layer0": n("item/fallen_blade_%d" % k)}})
    write_json(os.path.join(M, "item/fallen_blade.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": n("item/fallen_blade_0")}, "overrides": [
        {"predicate": {n("blade"): k / 10.0}, "model": n("item/fallen_blade_%d" % k)} for k in range(4)]})
    loot = os.path.join(DATA, "loot_table/blocks")
    for name in ("broken_seal", "false_altar", "idol", "abyss_throne"):
        write_json(os.path.join(loot, "%s.json" % name), {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [
            {"type": "minecraft:item", "name": n(name)}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for name in ("babel_rune", "crumbling_floor", "shifting_block"):
        write_json(os.path.join(loot, "%s.json" % name), {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [
            {"type": "minecraft:item", "name": "minecraft:polished_blackstone_bricks"}]}]})
    write_json(os.path.join(loot, "fallen_wing.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [
        {"type": "minecraft:item", "name": n("dark_feather"), "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}, "add": False}]}]}]})
    add_tag(os.path.join(MC_DATA, "tags/block/mineable/pickaxe.json"), [n(x) for x in ("babel_rune", "crumbling_floor", "shifting_block", "false_altar", "idol",
                                                                                         "broken_seal", "abyss_throne")])
    add_tag(os.path.join(MC_DATA, "tags/block/wither_immune.json"), [n("sealed_wall"), n("babel_portal")])
    add_tag(os.path.join(MC_DATA, "tags/block/dragon_immune.json"), [n("sealed_wall"), n("babel_portal")])


def structures():
    for sid, (templates, biomes, height, ground, adapt, spacing, sep, salt) in STRUCTS.items():
        base = os.path.join(DATA, "structure/hell")
        for name, gen in templates:
            t = gen()
            t.save(os.path.join(base, name + ".nbt"))
            print("  %s: %dx%dx%d, %d bloques, %d criaturas" % (name, *t.size, len(t.blocks), len(t.entities)))
        write_json(os.path.join(DATA, "worldgen/template_pool/hell/%s.json" % sid), {"fallback": "minecraft:empty", "elements": [
            {"weight": 1, "element": element(n("hell/" + name))} for name, _ in templates]})
        st = {"type": "minecraft:jigsaw", "biomes": "#%s:has_structure/%s" % (NS, sid), "max_distance_from_center": 80, "size": 1,
              "start_height": {"absolute": height}, "start_pool": n("hell/" + sid), "step": "surface_structures",
              "terrain_adaptation": adapt, "use_expansion_hack": False, "spawn_overrides": {}}
        if ground:
            st["project_start_to_heightmap"] = "WORLD_SURFACE_WG"
        if sid == "abyss_vault":
            st["start_height"] = {"absolute": 12}
            st["step"] = "underground_structures"
        write_json(os.path.join(DATA, "worldgen/structure/%s.json" % sid), st)
        write_json(os.path.join(DATA, "worldgen/structure_set/%s.json" % sid), {"structures": [{"structure": n(sid), "weight": 1}], "placement": {
            "type": "minecraft:random_spread", "spacing": spacing, "separation": sep, "salt": salt}})
        write_json(os.path.join(DATA, "tags/worldgen/biome/has_structure/%s.json" % sid), {"replace": False, "values": [n(b) for b in biomes]})
    add_tag(os.path.join(DATA, "tags/worldgen/structure/hell_landmarks.json"), [n(s) for s in STRUCTS if s != "sodom_house"])


def main():
    structures()
    textures()
    block_assets()
    for name, l in LOOTS.items():
        write_json(os.path.join(DATA, "loot_table/chests/%s.json" % name), l)
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            lang = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    print("estructuras del Nether bíblico: %d" % len(STRUCTS))


if __name__ == "__main__":
    main()
