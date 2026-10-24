"""Estructuras de Evolved Warfare: ciudad medieval (jigsaw), campamento de mercenarios y torre de vigilancia.

Uso: python3 ew_structures.py <ruta a src/main/resources/data/evolvedwarfare>
Genera los .nbt (structure/...) y los JSON de worldgen (template_pool, structure, structure_set,
processor_list) y las etiquetas de biomas.

Alturas (igual que las aldeas):
  * Pieza inicial (plaza, centro del campamento, torre): y=0 es la capa de hierba del terreno.
  * Calles (terrain_matching): y=0 sustituye la hierba, y=1 es el aire de encima (ahí van sus jigsaws).
  * Edificios junto a una calle: y=0 queda un bloque por encima de la hierba (cimiento + escalón).
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import nbt  # noqa: E402
from ew_structlib import (NS, S, Template, YAW, armor_stand, barrel, bed, chest, door, gable_x, gable_z,  # noqa: E402
                          lantern, slab, stairs, state_str, wall_sign)

STREET = f"{NS}:street"
ENTRANCE = f"{NS}:building_entrance"
TENT = f"{NS}:tent"
P_STREETS = f"{NS}:city/streets"
P_HOUSES = f"{NS}:city/houses"
P_TERMINATORS = f"{NS}:city/terminators"
P_TENTS = f"{NS}:camp/tents"
RESIDENTS = f"{NS}:npc/residents"

LOOT_WEAPONS = "minecraft:chests/village/village_weaponsmith"
LOOT_ARMOR = "minecraft:chests/village/village_armorer"
LOOT_TOOLS = "minecraft:chests/village/village_toolsmith"
LOOT_HOUSE = "minecraft:chests/village/village_plains_house"

# Paleta común
POST = S("dark_oak_log", axis="y")
PLASTER = S("calcite")
STONE = S("stone_bricks")
FLOOR = S("spruce_planks")


def beam(axis):
    return S("stripped_dark_oak_log", axis=axis)


def walls(t, x0, x1, z0, z1, y0, y1, infill, corner=None, top=None, posts=None, every=4):
    """Muros perimetrales. top: bloque o función(axis) para la última fila."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                on_x, on_z = x in (x0, x1), z in (z0, z1)
                if not (on_x or on_z):
                    continue
                if on_x and on_z and corner is not None:
                    t.put(x, y, z, corner)
                elif y == y1 and top is not None:
                    t.put(x, y, z, top("z" if on_x else "x") if callable(top) else top)
                elif posts is not None and ((on_z and (x - x0) % every == 0) or (on_x and (z - z0) % every == 0)):
                    t.put(x, y, z, posts)
                else:
                    t.put(x, y, z, infill)


def foundation(t, x0, x1, z0, z1, ring=STONE, inner=FLOOR, y=0):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            t.put(x, y, z, ring if x in (x0, x1) or z in (z0, z1) else inner)


def clear(t, x0, y0, z0, x1, y1, z1):
    t.fill(x0, y0, z0, x1, y1, z1, "air")


def entrance(t, z, final_state):
    """Jigsaw de la puerta en la fila del porche (x=0), mirando a la calle (oeste)."""
    t.jigsaw(0, 0, z, "west_up", ENTRANCE, ENTRANCE, "minecraft:empty", final_state)


def step(base):
    return state_str(stairs(base, "east"))


def lamp_post(t, x, y, z, base="stone_brick_wall", pole="dark_oak_fence", height=2):
    t.put(x, y, z, base)
    for k in range(1, height + 1):
        t.put(x, y + k, z, pole)
    t.put(x, y + height + 1, z, lantern())


def pane_x():
    return S("glass_pane")


# ======================================================================== CIUDAD: edificios
def cottage():
    """Casita de entramado: una planta, 9x9."""
    t = Template(9, 9, 9)
    clear(t, 1, 1, 1, 7, 8, 7)
    foundation(t, 1, 7, 1, 7)
    walls(t, 1, 7, 1, 7, 1, 4, PLASTER, corner=POST, top=beam, posts=POST, every=3)
    door(t, 1, 1, 4, "east")
    for z in (2, 6):
        t.put(1, 2, z, pane_x())
    for x in (2, 3, 5, 6):
        t.put(x, 2, 1, pane_x())
        t.put(x, 2, 7, pane_x())
    for z in (2, 3, 5, 6):
        t.put(7, 2, z, pane_x())
    gable_x(t, 0, 8, 0, 8, 4, "dark_oak", "dark_oak", gable_xs=(1, 7), gable_block=PLASTER)
    # interior
    bed(t, 5, 1, 6, "east", "blue")
    t.put(6, 1, 2, *chest("west", LOOT_HOUSE))
    t.put(2, 1, 2, "crafting_table")
    t.put(6, 1, 3, *barrel())
    t.put(6, 2, 3, lantern())
    t.put(2, 1, 4, "red_carpet")
    t.put(3, 1, 4, "red_carpet")
    t.put(4, 7, 4, lantern(hanging=True))
    t.put(2, 1, 6, "potted_red_tulip")
    t.npc_slot(4, 0, 3, RESIDENTS)
    # porche
    t.put(0, 2, 3, S("wall_torch", facing="west"))
    t.put(0, 2, 5, S("wall_torch", facing="west"))
    entrance(t, 4, step("spruce"))
    return t


def townhouse():
    """Casa de dos plantas: piedra abajo, entramado arriba, tejado de pizarra."""
    t = Template(10, 14, 11)
    clear(t, 1, 1, 1, 8, 13, 9)
    foundation(t, 1, 8, 1, 9)
    walls(t, 1, 8, 1, 9, 1, 4, STONE)
    # forjado de la primera planta
    for x in range(1, 9):
        for z in range(1, 10):
            edge = x in (1, 8) or z in (1, 9)
            t.put(x, 5, z, beam("z" if x in (1, 8) else "x") if edge else FLOOR)
    walls(t, 1, 8, 1, 9, 6, 8, PLASTER, corner=POST, top=beam, posts=POST, every=4)
    door(t, 1, 1, 5, "east")
    for z in (3, 7):
        t.put(1, 2, z, pane_x())
        t.put(1, 7, z, pane_x())
    for x in (3, 6):
        for zz in (1, 9):
            t.put(x, 2, zz, pane_x())
            t.put(x, 3, zz, pane_x())
    for x in (2, 3, 6, 7):
        t.put(x, 7, 1, pane_x())
        t.put(x, 7, 9, pane_x())
    for z in (3, 7):
        t.put(8, 2, z, pane_x())
        t.put(8, 7, z, pane_x())
    gable_x(t, 0, 9, 0, 10, 8, "deepslate_tile", "deepslate_tile", gable_xs=(1, 8), gable_block=PLASTER)
    # planta baja
    for y in range(1, 6):
        t.put(7, y, 7, S("ladder", facing="west", waterlogged="false"))
    t.put(4, 1, 4, "spruce_fence")
    t.put(4, 2, 4, "spruce_pressure_plate")
    t.put(4, 1, 3, stairs("spruce", "north"))
    t.put(4, 1, 5, stairs("spruce", "south"))
    t.put(7, 1, 2, S("furnace", facing="west", lit="false"))
    t.put(6, 1, 2, *barrel())
    t.put(2, 1, 8, "crafting_table")
    t.put(5, 4, 6, lantern(hanging=True))
    t.npc_slot(3, 0, 6, RESIDENTS)
    # primera planta
    bed(t, 6, 6, 2, "east", "green")
    bed(t, 6, 6, 4, "east", "green")
    t.put(2, 6, 2, *chest("east", LOOT_HOUSE))
    t.put(2, 6, 8, "bookshelf")
    t.put(3, 6, 8, "bookshelf")
    t.put(4, 12, 5, lantern(hanging=True))
    # porche
    t.put(0, 2, 3, S("wall_torch", facing="west"))
    t.put(0, 2, 7, S("wall_torch", facing="west"))
    entrance(t, 5, step("stone_brick"))
    return t


def barracks():
    """Cuartel: literas, armeros, cofres de armas. Hasta 3 reclutas."""
    t = Template(11, 11, 15)
    clear(t, 1, 1, 1, 9, 10, 13)
    foundation(t, 1, 9, 1, 13)
    walls(t, 1, 9, 1, 13, 1, 5, STONE, corner=S("polished_andesite"), posts=S("polished_andesite"), every=4)
    gable_z(t, 0, 14, 0, 10, 5, "deepslate_tile", "deepslate_tile", gable_zs=(1, 13), gable_block=STONE)
    door(t, 1, 1, 7, "east")
    for z in (3, 11):
        t.put(1, 2, z, "iron_bars")
        t.put(1, 3, z, "iron_bars")
    for x in (3, 7):
        for zz in (1, 13):
            t.put(x, 2, zz, "iron_bars")
            t.put(x, 3, zz, "iron_bars")
    for z in (3, 7, 11):
        t.put(9, 2, z, "iron_bars")
        t.put(9, 3, z, "iron_bars")
    # literas
    for x in (3, 5, 7):
        bed(t, x, 1, 3, "north", "red")
        bed(t, x, 1, 11, "south", "red")
    # mesa larga
    for x in (4, 5, 6):
        t.put(x, 1, 7, slab("spruce", "top"))
    t.put(4, 1, 6, stairs("spruce", "north"))
    t.put(6, 1, 6, stairs("spruce", "north"))
    t.put(4, 1, 8, stairs("spruce", "south"))
    t.put(6, 1, 8, stairs("spruce", "south"))
    # armería
    t.put(8, 1, 6, *chest("west", LOOT_WEAPONS))
    t.put(8, 1, 8, *chest("west", LOOT_WEAPONS))
    t.entity(armor_stand(head="iron_helmet", chest_="leather_chestplate"), 8, 1, 7, YAW["west"])
    t.put(5, 9, 4, lantern(hanging=True))
    t.put(5, 9, 10, lantern(hanging=True))
    for (x, z) in ((3, 7), (7, 5), (7, 9)):
        t.npc_slot(x, 0, z)
    # fachada
    t.put(0, 3, 5, S("red_wall_banner", facing="west"))
    t.put(0, 3, 9, S("red_wall_banner", facing="west"))
    t.put(0, 2, 4, S("wall_torch", facing="west"))
    t.put(0, 2, 10, S("wall_torch", facing="west"))
    entrance(t, 7, step("stone_brick"))
    return t


def tavern():
    """Taberna: barra, mesas y chimenea. Aquí los mercenarios esperan trabajo."""
    t = Template(11, 12, 13)
    clear(t, 1, 1, 1, 9, 10, 11)
    foundation(t, 1, 9, 1, 11)
    walls(t, 1, 9, 1, 11, 1, 5, PLASTER, corner=POST, top=beam, posts=POST, every=4)
    gable_z(t, 0, 12, 0, 10, 5, "brick", "brick", gable_zs=(1, 11), gable_block=PLASTER)
    door(t, 1, 1, 6, "east")
    for z in (3, 9):
        t.put(1, 2, z, pane_x())
        t.put(1, 3, z, pane_x())
    for x in (3, 7):
        for zz in (1, 11):
            t.put(x, 2, zz, pane_x())
            t.put(x, 3, zz, pane_x())
    # chimenea (sale por el tejado y echa humo)
    for y in range(1, 11):
        t.put(9, y, 9, "cobblestone")
    t.put(9, 11, 9, S("campfire", facing="north", lit="true", signal_fire="false", waterlogged="false"))
    t.put(8, 1, 9, S("campfire", facing="west", lit="true", signal_fire="false", waterlogged="false"))
    for (y, z) in ((1, 8), (1, 10), (2, 8), (2, 9), (2, 10)):
        t.put(8, y, z, "bricks")
    # barra
    for z in range(3, 7):
        t.put(6, 1, z, slab("spruce", "top"))
        t.put(8, 1, z, *barrel("west"))
    t.put(8, 2, 4, *barrel("west"))
    t.put(6, 2, 3, lantern())
    # mesas
    for z in (3, 4, 8, 9):
        t.put(3, 1, z, slab("spruce", "top"))
        t.put(4, 1, z, stairs("spruce", "east"))
    t.put(5, 9, 4, lantern(hanging=True))
    t.put(5, 9, 8, lantern(hanging=True))
    t.npc_slot(5, 0, 5)
    t.npc_slot(3, 0, 6)
    # letrero
    t.put(0, 3, 6, *wall_sign("spruce_wall_sign", "west",
                              ["", ("Taberna", {"color": "gold", "bold": True}), "Tavern", ""]))
    t.put(0, 2, 4, S("wall_torch", facing="west"))
    t.put(0, 2, 8, S("wall_torch", facing="west"))
    entrance(t, 6, step("spruce"))
    return t


def blacksmith():
    """Herrería: fragua abierta delante y taller cerrado detrás."""
    t = Template(10, 10, 11)
    clear(t, 1, 1, 1, 8, 9, 9)
    foundation(t, 1, 8, 1, 9, ring=S("cobblestone"), inner=S("cobblestone"))
    # taller trasero
    walls(t, 5, 8, 1, 9, 1, 4, S("cobblestone"), corner=POST)
    door(t, 5, 1, 5, "east")
    for z in (3, 7):
        t.put(8, 2, z, pane_x())
    # fragua delantera: pilares y vigas
    for z in (1, 9):
        for y in range(1, 4):
            t.put(1, y, z, POST)
        t.put(1, 4, z, POST)
        for x in range(2, 5):
            t.put(x, 4, z, beam("x"))
    for z in range(2, 9):
        t.put(1, 4, z, beam("z"))
    gable_x(t, 0, 9, 0, 10, 4, "spruce", "spruce", gable_xs=(1, 5, 8), gable_block=FLOOR)
    t.put(3, 1, 3, S("anvil", facing="north"))
    t.put(4, 1, 2, "smithing_table")
    t.put(2, 1, 2, S("grindstone", face="floor", facing="east"))
    t.put(3, 1, 8, "lava_cauldron")
    t.put(2, 1, 8, S("water_cauldron", level="3"))
    t.put(4, 1, 8, S("blast_furnace", facing="north", lit="false"))
    for y in range(4, 9):
        t.put(3, y, 5, S("chain", axis="y", waterlogged="false"))
    t.put(3, 3, 5, lantern(hanging=True))
    t.npc_slot(3, 0, 5, RESIDENTS)
    # taller
    t.put(7, 1, 2, *chest("west", LOOT_TOOLS))
    t.put(7, 1, 3, *barrel())
    bed(t, 6, 1, 8, "east", "brown")
    t.put(7, 1, 6, "crafting_table")
    entrance(t, 5, step("stone_brick"))
    return t


def guard_post():
    """Torre de guardia pequeña con parapeto."""
    t = Template(7, 12, 7)
    clear(t, 2, 1, 2, 4, 11, 4)
    foundation(t, 1, 5, 1, 5, ring=STONE, inner=STONE)
    walls(t, 1, 5, 1, 5, 1, 9, STONE)
    for x in range(2, 5):
        for z in range(2, 5):
            t.put(x, 9, z, FLOOR)
    for y in range(1, 9):
        t.put(4, y, 3, S("ladder", facing="west", waterlogged="false"))
    t.put(4, 9, 3, S("spruce_trapdoor", facing="west", half="top", open="false", powered="false",
                     waterlogged="false"))
    for x in range(1, 6):
        for z in range(1, 6):
            if x in (1, 5) or z in (1, 5):
                t.put(x, 10, z, "stone_brick_wall")
    for (x, z) in ((1, 1), (1, 5), (5, 1), (5, 5)):
        t.put(x, 11, z, lantern())
    door(t, 1, 1, 3, "east")
    for (x, y, z) in ((3, 6, 1), (3, 6, 5), (5, 6, 2), (1, 6, 3)):
        t.put(x, y, z, "air")
    t.put(2, 1, 2, *chest("east", LOOT_ARMOR))
    t.npc_slot(2, 0, 4, RESIDENTS)
    entrance(t, 3, step("stone_brick"))
    return t


def market_stalls():
    """Dos puestos de mercado con toldos de colores."""
    t = Template(7, 5, 11)
    for x in range(1, 6):
        for z in range(0, 11):
            t.put(x, 0, z, "cobblestone")
    for (z0, color) in ((1, "red"), (7, "blue")):
        for (x, z) in ((2, z0), (2, z0 + 2), (4, z0), (4, z0 + 2)):
            t.put(x, 1, z, "dark_oak_fence")
            t.put(x, 2, z, "dark_oak_fence")
        for x in range(1, 6):
            for z in range(z0, z0 + 3):
                t.put(x, 3, z, f"{color}_wool" if (x + z) % 2 == 0 else "white_wool")
        t.put(2, 1, z0 + 1, slab("spruce", "top"))
        t.put(4, 1, z0 + 1, *barrel())
        t.put(3, 1, z0, "hay_block")
    t.put(2, 2, 2, "pumpkin")
    t.put(2, 2, 8, "melon")
    lamp_post(t, 3, 1, 5, base="cobblestone_wall")
    t.npc_slot(4, 0, 6, RESIDENTS)
    entrance(t, 5, step("cobblestone"))
    return t


def garden():
    """Huerto vallado con espantapájaros."""
    t = Template(9, 4, 9)
    for x in range(1, 8):
        for z in range(1, 8):
            ring = x in (1, 7) or z in (1, 7)
            t.put(x, 0, z, "coarse_dirt" if ring else S("farmland", moisture="7"))
            if ring:
                t.put(x, 1, z, "dark_oak_fence")
    t.put(1, 1, 4, S("spruce_fence_gate", facing="east", in_wall="false", open="false", powered="false"))
    t.put(4, 0, 4, S("water", level="0"))
    for x in range(2, 7):
        for z in range(2, 7):
            if (x, z) == (4, 4):
                continue
            crop = "wheat" if z < 4 else "carrots"
            t.put(x, 1, z, S(crop, age="7"))
    t.put(6, 0, 6, "coarse_dirt")
    t.put(6, 1, 6, S("composter", level="3"))
    t.put(2, 0, 6, "coarse_dirt")
    t.put(2, 1, 6, "dark_oak_fence")
    t.put(2, 2, 6, "hay_block")
    t.put(2, 3, 6, S("carved_pumpkin", facing="west"))
    entrance(t, 4, "minecraft:coarse_dirt")
    return t


# ======================================================================== CIUDAD: plazas (centro)
def plaza_jigsaws(t, n):
    c = n // 2
    for (x, z, f) in ((c, 0, "north"), (c, n - 1, "south"), (0, c, "west"), (n - 1, c, "east")):
        t.jigsaw(x, 1, z, f + "_up", STREET, STREET, P_STREETS)


def plaza_fountain():
    n = 19
    t = Template(n, 6, n)
    c = n // 2
    for x in range(n):
        for z in range(n):
            d = max(abs(x - c), abs(z - c))
            if d == c:
                b = "stone_bricks"
            elif d == 3:
                b = "polished_andesite"
            elif d <= 2:
                b = "stone_bricks"
            else:
                b = "cobblestone"
            t.put(x, 0, z, b)
    # fuente
    for x in range(c - 2, c + 3):
        for z in range(c - 2, c + 3):
            d = max(abs(x - c), abs(z - c))
            if d == 2:
                t.put(x, 1, z, "stone_bricks")
            elif d == 1:
                t.put(x, 1, z, S("water", level="0"))
    t.put(c, 1, c, "chiseled_stone_bricks")
    t.put(c, 2, c, "stone_brick_wall")
    t.put(c, 3, c, "stone_brick_wall")
    t.put(c, 4, c, lantern())
    for (x, z) in ((c - 2, c - 2), (c - 2, c + 2), (c + 2, c - 2), (c + 2, c + 2)):
        t.put(x, 2, z, "potted_red_tulip")
    # farolas y bancos
    for (x, z) in ((3, 3), (3, n - 4), (n - 4, 3), (n - 4, n - 4)):
        lamp_post(t, x, 1, z)
    for dx in (-1, 1):
        t.put(c + dx, 1, c - 4, stairs("spruce", "north"))
        t.put(c + dx, 1, c + 4, stairs("spruce", "south"))
        t.put(c - 4, 1, c + dx, stairs("spruce", "west"))
        t.put(c + 4, 1, c + dx, stairs("spruce", "east"))
    t.npc_slot(6, 0, 12)
    t.npc_slot(12, 0, 6)
    plaza_jigsaws(t, n)
    return t


def training_yard():
    n = 19
    t = Template(n, 7, n)
    c = n // 2
    for x in range(n):
        for z in range(n):
            lane = abs(x - c) <= 2 or abs(z - c) <= 2
            d = max(abs(x - c), abs(z - c))
            t.put(x, 0, z, "stone_bricks" if d == c else ("cobblestone" if lane else "coarse_dirt"))
    for x in range(1, n - 1):
        for z in (1, n - 2):
            if abs(x - c) > 2:
                t.put(x, 1, z, "dark_oak_fence")
                t.put(z, 1, x, "dark_oak_fence")
    # muñecos de entrenamiento
    for (x, z, f) in ((4, 4, "south"), (4, 6, "south"), (6, 4, "east")):
        t.put(x, 1, z, "dark_oak_fence")
        t.put(x, 2, z, "hay_block")
        t.put(x, 3, z, S("carved_pumpkin", facing=f))
    # dianas
    for z in (13, 15):
        t.put(15, 1, z, "hay_block")
        t.put(15, 2, z, "target")
    # armero
    t.entity(armor_stand(head="chainmail_helmet", chest_="leather_chestplate", main="wooden_sword"), 13, 1, 4,
             YAW["south"])
    t.entity(armor_stand(head="leather_helmet", chest_="leather_chestplate", main="bow"), 15, 1, 4, YAW["south"])
    t.put(14, 1, 3, S("grindstone", face="floor", facing="south"))
    t.put(14, 1, 5, *chest("south", LOOT_WEAPONS))
    # mástil con estandarte
    for y in range(1, 6):
        t.put(c, y, c, "dark_oak_fence")
    t.put(c, 6, c, S("red_banner", rotation="0"))
    for (x, z) in ((2, 2), (2, n - 3), (n - 3, 2), (n - 3, n - 3)):
        lamp_post(t, x, 1, z)
    for (x, z) in ((6, 12), (12, 12), (12, 6)):
        t.npc_slot(x, 0, z)
    plaza_jigsaws(t, n)
    return t


# ======================================================================== CIUDAD: calles
STREET_W0, STREET_W1 = 11, 15   # carril de 5 bloques


def street_tile(sx, sz, path, streets, entrances, lamps):
    t = Template(sx, 5, sz)
    cells = set(path)
    for (x, z) in cells:
        curb = any((x + dx, z + dz) not in cells and 0 <= x + dx < sx and 0 <= z + dz < sz
                   for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        t.put(x, 0, z, "stone_bricks" if curb else "cobblestone")
    for (x, z) in lamps:
        assert (x, z) in cells
        lamp_post(t, x, 1, z)
    for (x, z, f) in streets:
        t.jigsaw(x, 1, z, f + "_up", STREET, STREET, P_STREETS)
    for (x, z, f) in entrances:
        assert (x, z) in cells, (x, z)
        t.jigsaw(x, 1, z, f + "_up", ENTRANCE, ENTRANCE, P_HOUSES)
    return t


def lane_ns(z0, z1):
    return [(x, z) for x in range(STREET_W0, STREET_W1 + 1) for z in range(z0, z1 + 1)]


def lane_ew(x0, x1):
    return [(x, z) for z in range(STREET_W0, STREET_W1 + 1) for x in range(x0, x1 + 1)]


def street_straight():
    ent = [(11, z, "west") for z in (4, 7, 8, 11)] + [(15, z, "east") for z in (4, 7, 8, 11)]
    return street_tile(27, 16, lane_ns(0, 15), [(13, 0, "north"), (13, 15, "south")], ent, [(11, 1), (15, 14)])


def street_crossroad():
    ent = [(11, 5, "west"), (5, 11, "north"), (15, 5, "east"), (21, 11, "north"),
           (11, 21, "west"), (5, 15, "south"), (15, 21, "east"), (21, 15, "south")]
    return street_tile(27, 27, lane_ns(0, 26) + lane_ew(0, 26),
                       [(13, 0, "north"), (13, 26, "south"), (0, 13, "west"), (26, 13, "east")],
                       ent, [(11, 10), (16, 11), (15, 16), (10, 15)])


def street_t_junction():
    ent = [(11, z, "west") for z in (4, 8, 13, 18, 22)] + \
          [(15, 5, "east"), (21, 11, "north"), (15, 21, "east"), (21, 15, "south")]
    return street_tile(27, 27, lane_ns(0, 26) + lane_ew(16, 26),
                       [(13, 0, "north"), (13, 26, "south"), (26, 13, "east")],
                       ent, [(11, 1), (11, 25), (16, 11)])


def street_turn():
    ent = [(11, z, "west") for z in (4, 9, 14)] + [(x, 15, "south") for x in (13, 18, 22)] + \
          [(15, 5, "east"), (21, 11, "north")]
    return street_tile(27, 27, lane_ns(0, 15) + lane_ew(11, 26),
                       [(13, 0, "north"), (26, 13, "east")], ent, [(11, 1), (11, 15), (15, 10)])


def terminator_plain():
    t = Template(5, 2, 2)
    for x in range(5):
        t.put(x, 0, 0, "stone_bricks" if x in (0, 4) else "cobblestone")
    for x in (1, 2, 3):
        t.put(x, 0, 1, "cobblestone")
    t.jigsaw(2, 1, 0, "north_up", STREET, STREET, "minecraft:empty")
    return t


def terminator_gate():
    """Final de calle con dos pilares y faroles: la entrada de la ciudad."""
    t = Template(5, 5, 3)
    for x in range(5):
        for z in range(3):
            t.put(x, 0, z, "stone_bricks" if x in (0, 4) else "cobblestone")
    for x in (0, 4):
        for y in (1, 2, 3):
            t.put(x, y, 1, "stone_bricks")
        t.put(x, 4, 1, lantern())
    t.jigsaw(2, 1, 0, "north_up", STREET, STREET, "minecraft:empty")
    return t


# ======================================================================== NPC
def npc_recruit():
    t = Template(1, 2, 1)
    t.jigsaw(0, 0, 0, "down_south", f"{NS}:npc", f"{NS}:npc", "minecraft:empty")
    t.entity({"id": f"{NS}:recruit", "PersistenceRequired": nbt.Byte(1)}, 0, 0, 0, YAW["west"])
    return t


# ======================================================================== CAMPAMENTO
def camp_center(banner="red_banner", loot=LOOT_WEAPONS, npc_pool=None, tents=P_TENTS):
    """Centro del campamento. Con otros parámetros sirve también para el campamento de bandidos."""
    n = 11
    t = Template(n, 7, n)
    c = n // 2
    for x in range(n):
        for z in range(n):
            if (x - c) ** 2 + (z - c) ** 2 <= 30:
                d2 = (x - c) ** 2 + (z - c) ** 2
                b = "dirt_path" if 4 <= d2 <= 6 else ("gravel" if (x * 7 + z * 3) % 9 == 0 else "coarse_dirt")
                t.put(x, 0, z, b)
    t.put(c, 0, c, "cobblestone")
    t.put(c, 1, c, S("campfire", facing="north", lit="true", signal_fire="false", waterlogged="false"))
    for z in (4, 5, 6):
        t.put(3, 1, z, S("stripped_spruce_log", axis="z"))
        t.put(7, 1, z, S("stripped_spruce_log", axis="z"))
    for x in (4, 5, 6):
        t.put(x, 1, 7, S("stripped_spruce_log", axis="x"))
    t.put(8, 1, 2, *chest("west", loot))
    t.put(2, 1, 2, "hay_block")
    t.put(2, 2, 2, "hay_block")
    t.put(3, 1, 2, "hay_block")
    for y in range(1, 5):
        t.put(8, y, 8, "dark_oak_fence")
    t.put(8, 5, 8, S(banner, rotation="0"))
    t.put(2, 1, 8, "dark_oak_fence")
    t.put(2, 2, 8, lantern())
    t.npc_slot(3, 0, 8, *([npc_pool] if npc_pool else []))
    t.npc_slot(7, 0, 2, *([npc_pool] if npc_pool else []))
    for (x, z, f) in ((c, 0, "north"), (c, n - 1, "south"), (0, c, "west"), (n - 1, c, "east")):
        t.jigsaw(x, 1, z, f + "_up", TENT, TENT, tents)
    return t


def tent_jigsaw(t, z):
    t.jigsaw(0, 1, z, "west_up", TENT, TENT, "minecraft:empty")


def a_frame(t, x0, x1, half, color_fn):
    """Lona en forma de A a lo largo de x; la cumbrera queda en z=half."""
    for x in range(x0, x1 + 1):
        for k in range(half + 1):
            for z in {k, 2 * half - k}:
                t.put(x, 1 + k, z, color_fn(x))


def tent_small(wool="white_wool", bed_color="white", npc_pool=None):
    t = Template(6, 4, 5)
    for x in range(1, 6):
        for z in range(1, 4):
            t.put(x, 0, z, "coarse_dirt")
    a_frame(t, 1, 5, 2, lambda x: wool)
    for (y, z) in ((1, 1), (1, 2), (1, 3), (2, 2)):
        t.put(5, y, z, wool)
    bed(t, 3, 1, 2, "east", bed_color)
    t.npc_slot(2, 0, 2, *([npc_pool] if npc_pool else []))
    tent_jigsaw(t, 2)
    return t


def tent_large(stripe="red_wool", base="white_wool", bed_color="red", npc_pool=None, loot=None):
    t = Template(8, 5, 7)
    for x in range(1, 8):
        for z in range(1, 6):
            t.put(x, 0, z, "coarse_dirt")
    a_frame(t, 1, 7, 3, lambda x: stripe if x % 2 else base)
    for (y, zs) in ((1, range(1, 6)), (2, range(2, 5)), (3, (3,))):
        for z in zs:
            t.put(7, y, z, stripe)
    bed(t, 5, 1, 1, "east", bed_color)
    bed(t, 5, 1, 5, "east", bed_color)
    t.put(6, 1, 3, *barrel("west", loot))
    t.put(4, 3, 3, lantern(hanging=True))
    t.npc_slot(3, 0, 3, *([npc_pool] if npc_pool else []))
    tent_jigsaw(t, 3)
    return t


def camp_supply():
    t = Template(6, 3, 7)
    for (x, y, z) in ((2, 1, 1), (3, 1, 1), (2, 2, 1), (5, 1, 2)):
        t.put(x, y, z, *barrel())
    t.put(3, 2, 1, *barrel("up", LOOT_HOUSE))
    for (x, y, z) in ((4, 1, 5), (4, 2, 5), (5, 1, 5), (5, 1, 4)):
        t.put(x, y, z, "hay_block")
    t.put(5, 1, 1, "fletching_table")
    t.put(2, 1, 4, "crafting_table")
    tent_jigsaw(t, 3)
    return t


def camp_weapon_rack():
    t = Template(5, 4, 7)
    for z in range(1, 6):
        t.put(3, 1, z, "dark_oak_fence")
    t.put(3, 2, 1, "dark_oak_fence")
    t.put(3, 2, 5, "dark_oak_fence")
    t.put(3, 2, 3, lantern())
    t.entity(armor_stand(head="leather_helmet", chest_="leather_chestplate", main="stone_sword"), 2, 1, 1, YAW["west"])
    t.entity(armor_stand(chest_="leather_chestplate", main="bow"), 2, 1, 5, YAW["west"])
    t.put(2, 1, 3, S("grindstone", face="floor", facing="west"))
    tent_jigsaw(t, 3)
    return t


# ======================================================================== TORRE DE VIGILANCIA
def watchtower():
    t = Template(9, 15, 9)
    clear(t, 2, 1, 2, 6, 11, 6)
    foundation(t, 1, 7, 1, 7, ring=STONE, inner=STONE)
    walls(t, 1, 7, 1, 7, 1, 11, STONE, corner=S("polished_andesite"))
    for x in range(2, 7):
        for z in range(2, 7):
            t.put(x, 6, z, FLOOR)
    # plataforma superior con voladizo y parapeto
    for x in range(0, 9):
        for z in range(0, 9):
            inner = 2 <= x <= 6 and 2 <= z <= 6
            t.put(x, 12, z, FLOOR if inner else STONE)
            if x in (0, 8) or z in (0, 8):
                t.put(x, 13, z, "stone_brick_wall")
    for (x, z) in ((0, 0), (0, 8), (8, 0), (8, 8)):
        t.put(x, 14, z, lantern())
    # escalera de mano y trampilla
    for y in range(1, 12):
        t.put(6, y, 4, S("ladder", facing="west", waterlogged="false"))
    t.put(6, 12, 4, S("spruce_trapdoor", facing="west", half="top", open="false", powered="false",
                      waterlogged="false"))
    door(t, 1, 1, 4, "east")
    t.put(0, 0, 4, stairs("stone_brick", "east"))
    for (x, y, z) in ((4, 4, 1), (4, 9, 1), (4, 4, 7), (4, 9, 7), (7, 9, 2), (1, 8, 4)):
        t.put(x, y, z, "air")
    t.put(0, 10, 3, S("red_wall_banner", facing="west"))
    t.put(0, 10, 5, S("red_wall_banner", facing="west"))
    # interior
    t.put(2, 1, 6, *chest("east", LOOT_ARMOR))
    t.put(2, 1, 2, *barrel())
    t.put(2, 2, 2, lantern())
    bed(t, 3, 7, 6, "east", "gray")
    t.put(2, 7, 2, *barrel())
    t.put(4, 11, 4, lantern(hanging=True))
    t.npc_slot(4, 0, 3)
    t.npc_slot(3, 12, 3)
    return t


# ======================================================================== BANDIDOS
P_BANDITS = f"{NS}:npc/bandits"
P_BANDIT_ARCHERS = f"{NS}:npc/bandit_archers"
P_BANDIT_CAPTAIN = f"{NS}:npc/bandit_captain"
P_PRISONERS = f"{NS}:npc/prisoners"
P_BANDIT_TENTS = f"{NS}:bandit_camp/tents"
LOOT_BANDIT_CAMP = f"{NS}:chests/bandit_camp"
LOOT_BANDIT_FORT = f"{NS}:chests/bandit_fort"


def npc_bandit(role=None):
    t = Template(1, 2, 1)
    t.jigsaw(0, 0, 0, "down_south", f"{NS}:npc", f"{NS}:npc", "minecraft:empty")
    data = {"id": f"{NS}:bandit", "PersistenceRequired": nbt.Byte(1)}
    if role:
        data["Role"] = role
    t.entity(data, 0, 0, 0, YAW["west"])
    return t


def cage(t, x0, z0, gate_side="north", floor="cobblestone"):
    """Jaula 5x5 de barrotes con puerta de valla y un prisionero (recluta libre: rescátalo y contrátalo)."""
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 5):
            t.put(x, 0, z, floor)
            ring = x in (x0, x0 + 4) or z in (z0, z0 + 4)
            for y in (1, 2, 3):
                t.put(x, y, z, "iron_bars" if ring else "air")
            t.put(x, 4, z, "spruce_planks")
    gx, gz = (x0 + 2, z0) if gate_side == "north" else (x0, z0 + 2)
    t.put(gx, 1, gz, S("spruce_fence_gate", facing="north" if gate_side == "north" else "west", in_wall="false",
                       open="false", powered="false"))
    t.put(gx, 2, gz, "air")
    t.put(x0 + 3, 1, z0 + 3, "hay_block")
    t.put(x0 + 2, 3, z0 + 2, lantern(hanging=True))
    t.npc_slot(x0 + 1, 0, z0 + 2, P_PRISONERS)


def bandit_cage():
    """Pieza de campamento: la jaula con un prisionero."""
    t = Template(7, 5, 7)
    cage(t, 1, 1, gate_side="west")
    tent_jigsaw(t, 3)
    return t


def bandit_fort():
    """Fortín de bandidos: empalizada, 4 torres con arqueros, puerta, patio, sala del capitán y calabozo."""
    n = 31
    t = Template(n, 11, n)
    last = n - 1
    # suelo del patio
    for x in range(n):
        for z in range(n):
            h = (x * 7 + z * 13) % 11
            b = "gravel" if h == 0 else ("dirt_path" if h == 1 else "coarse_dirt")
            if 13 <= z <= 17 and x <= 21:
                b = "cobblestone" if (x + z) % 4 else "gravel"
            t.put(x, 0, z, b)
    # empalizada
    for i in range(n):
        for (x, z) in ((i, 0), (i, last), (0, i), (last, i)):
            top = 5 + ((x + z) & 1)
            for y in range(1, top + 1):
                t.put(x, y, z, S("spruce_log", axis="y"))
            t.put(x, 0, z, S("spruce_log", axis="y"))
    # puerta (lado oeste)
    for z in (14, 15, 16):
        for y in range(1, 5):
            t.put(0, y, z, "air")
        t.put(0, 5, z, S("stripped_spruce_log", axis="z"))
        t.put(0, 6, z, "air")
    for z in (13, 17):
        for y in range(1, 8):
            t.put(0, y, z, S("spruce_log", axis="y"))
        t.put(0, 8, z, lantern())
    # torres en las esquinas
    for (cx, cz, sx, sz) in ((0, 0, 1, 1), (last, 0, -1, 1), (0, last, 1, -1), (last, last, -1, -1)):
        def P(dx, dz):
            return cx + sx * dx, cz + sz * dz
        for dx in range(5):
            for dz in range(5):
                x, z = P(dx, dz)
                if dx in (0, 4) and dz in (0, 4):
                    for y in range(0, 9):
                        t.put(x, y, z, S("spruce_log", axis="y"))
                t.put(x, 6, z, "spruce_planks")
                if dx in (0, 4) or dz in (0, 4):
                    t.put(x, 7, z, "spruce_fence")
        for dz in (1, 2, 3):
            for y in range(1, 6):
                x, z = P(4, dz)
                t.put(x, y, z, "spruce_planks")
        # escalera de mano por el lado del patio y hueco en la barandilla
        lx, lz = P(5, 2)
        facing = "east" if sx > 0 else "west"
        for y in range(1, 7):
            t.put(lx, y, lz, S("ladder", facing=facing, waterlogged="false"))
        bx, bz = P(4, 2)
        t.put(bx, 7, bz, "air")
        for (dx, dz) in ((0, 0), (4, 4)):
            x, z = P(dx, dz)
            t.put(x, 9, z, lantern())
        ax, az = P(2, 2)
        t.npc_slot(ax, 6, az, P_BANDIT_ARCHERS)
    # sala del capitán (lado este)
    x0, x1, z0, z1 = 20, 28, 10, 20
    foundation(t, x0, x1, z0, z1, ring=S("cobblestone"), inner=S("spruce_planks"))
    clear(t, x0 + 1, 1, z0 + 1, x1 - 1, 9, z1 - 1)
    walls(t, x0, x1, z0, z1, 1, 4, S("spruce_planks"), corner=S("stripped_spruce_log", axis="y"),
          top=lambda axis: S("stripped_spruce_log", axis=axis))
    gable_z(t, z0 - 1, z1 + 1, x0 - 1, x1 + 1, 4, "dark_oak", "dark_oak", gable_zs=(z0, z1),
            gable_block=S("spruce_planks"))
    door(t, x0, 1, 15, "east")
    for z in (12, 18):
        t.put(x0, 2, z, S("glass_pane"))
    t.put(x0 - 1, 3, 14, S("black_wall_banner", facing="west"))
    t.put(x0 - 1, 3, 16, S("black_wall_banner", facing="west"))
    t.put(x1 - 1, 1, z0 + 1, *chest("west", LOOT_BANDIT_FORT))
    t.put(x1 - 1, 1, z1 - 1, *chest("west", LOOT_BANDIT_FORT))
    for z in (14, 15, 16):
        t.put(25, 1, z, slab("dark_oak", "top"))
    t.put(26, 1, 15, stairs("dark_oak", "east"))
    bed(t, 22, 1, z0 + 1, "east", "black")
    t.put(24, 4, 15, lantern(hanging=True))
    t.put(x1 - 1, 2, 15, S("black_wall_banner", facing="west"))
    t.npc_slot(23, 0, 15, P_BANDIT_CAPTAIN)
    # tiendas del patio (norte)
    for (tx, wool) in ((7, "black_wool"), (14, "gray_wool")):
        for x in range(tx, tx + 5):
            for k in range(3):
                for z in {4 + k, 8 - k}:
                    t.put(x, 1 + k, z, wool)
        for (y, z) in ((1, 5), (1, 6), (1, 7), (2, 6)):
            t.put(tx + 4, y, z, wool)
        bed(t, tx + 2, 1, 6, "east", "black")
        t.npc_slot(tx + 1, 0, 6, P_BANDITS)
    t.put(19, 1, 5, *chest("west", LOOT_BANDIT_CAMP))
    # calabozo (sur)
    cage(t, 8, 22, gate_side="north")
    # hoguera, muñecos y armero
    t.put(12, 1, 20, S("campfire", facing="north", lit="true", signal_fire="false", waterlogged="false"))
    for x in (10, 14):
        t.put(x, 1, 20, S("stripped_spruce_log", axis="z"))
    for (x, z) in ((16, 23), (18, 25)):
        t.put(x, 1, z, "spruce_fence")
        t.put(x, 2, z, "hay_block")
        t.put(x, 3, z, S("carved_pumpkin", facing="west"))
    t.entity(armor_stand(head="leather_helmet", chest_="leather_chestplate", main="iron_sword"), 17, 1, 11, YAW["south"])
    t.entity(armor_stand(head="chainmail_helmet", chest_="leather_chestplate", main="crossbow"), 18, 1, 11, YAW["south"])
    for (x, z) in ((6, 12), (6, 18), (17, 19)):
        t.put(x, 1, z, "spruce_fence")
        t.put(x, 2, z, "spruce_fence")
        t.put(x, 3, z, lantern())
    for (x, y, z) in ((25, 1, 25), (26, 1, 25), (25, 2, 25), (26, 1, 26)):
        t.put(x, y, z, *barrel())
    t.put(24, 1, 26, "hay_block")
    # guarnición del patio
    for (x, z) in ((8, 15), (12, 12), (15, 18), (18, 9), (19, 21)):
        t.npc_slot(x, 0, z, P_BANDITS)
    return t


# ======================================================================== VIKINGOS (fase 5)
P_VIKINGS = f"{NS}:npc/vikings"
P_VIKING_ARCHERS = f"{NS}:npc/viking_archers"
P_VIKING_JARL = f"{NS}:npc/viking_jarl"
P_VIKING_PIECES = f"{NS}:viking/pieces"
LOOT_VIKING_HALL = f"{NS}:chests/viking_longhouse"
LOOT_VIKING_HUT = f"{NS}:chests/viking_hut"


def npc_faction(entity, role=None):
    t = Template(1, 2, 1)
    t.jigsaw(0, 0, 0, "down_south", f"{NS}:npc", f"{NS}:npc", "minecraft:empty")
    data = {"id": f"{NS}:{entity}", "PersistenceRequired": nbt.Byte(1)}
    if role:
        data["Role"] = role
    t.entity(data, 0, 0, 0, YAW["west"])
    return t


def ground_pad(t, x0, x1, z0, z1, blocks, y=0):
    """Suelo variado (el primero sale más a menudo)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            h = (x * 7 + z * 13 + x * z) % 11
            t.put(x, y, z, blocks[0] if h >= len(blocks) - 1 else blocks[1 + h % (len(blocks) - 1)])


def viking_longhouse():
    """Casa larga del jarl: muros de troncos, tejado muy inclinado con cabezas de dragón, hogar alargado y trono."""
    t = Template(27, 13, 15)
    for x in range(27):
        for z in range(15):
            h = (x * 5 + z * 11) % 13
            b = "podzol" if h == 0 else ("gravel" if h == 1 else "coarse_dirt")
            if 6 <= z <= 8 and x < 4:
                b = "dirt_path"
            t.put(x, 0, z, b)
    x0, x1, z0, z1 = 4, 22, 3, 11
    foundation(t, x0, x1, z0, z1, ring=S("cobblestone"), inner=S("spruce_planks"))
    clear(t, x0 + 1, 1, z0 + 1, x1 - 1, 9, z1 - 1)
    for y in range(1, 4):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                t.put(x, y, z, S("spruce_log", axis="x"))
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                t.put(x, y, z, S("spruce_log", axis="z"))
    for x in (x0, 10, 16, x1):
        for z in (z0, z1):
            for y in range(1, 4):
                t.put(x, y, z, S("stripped_dark_oak_log", axis="y"))
    # vigas de atar (de ellas cuelgan los faroles)
    for x in (10, 16):
        for z in range(z0, z1 + 1):
            t.put(x, 4, z, S("stripped_spruce_log", axis="z"))
    gable_x(t, x0 - 1, x1 + 1, z0 - 1, z1 + 1, 4, "dark_oak", "dark_oak", gable_xs=(x0, x1),
            gable_block=S("spruce_planks"))
    for z in range(z0, z1 + 1):
        t.put(x0, 4, z, S("spruce_planks"))
        t.put(x1, 4, z, S("spruce_planks"))
    # cabezas de dragón en los extremos de la cumbrera
    for (x, f) in ((x0 - 1, "east"), (x1 + 1, "west")):
        t.put(x, 10, 7, "dark_oak_fence")
        t.put(x, 11, 7, stairs("dark_oak", f))
    # puertas (hastial oeste y lateral sur) y ventanucos
    door(t, x0, 1, 7, "east")
    door(t, 13, 1, z1, "north")
    for x in (7, 19):
        t.put(x, 2, z0, "air")
        t.put(x, 2, z1, "air")
    t.put(x0 - 1, 3, 6, S("red_wall_banner", facing="west"))
    t.put(x0 - 1, 3, 8, S("white_wall_banner", facing="west"))
    # hogar alargado
    for x in range(9, 18):
        for z in (6, 7, 8):
            t.put(x, 0, z, "cobblestone" if z != 7 else "stone_bricks")
    for x in (11, 15):
        t.put(x, 1, 7, S("campfire", facing="north", lit="true", signal_fire="false", waterlogged="false"))
    for x in (10, 16):
        t.put(x, 3, 7, lantern(hanging=True))
    # bancos, camas, barriles y arcones
    for x in list(range(6, 9)) + list(range(17, 20)):
        t.put(x, 1, z0 + 1, stairs("spruce", "north"))
        t.put(x, 1, z1 - 1, stairs("spruce", "south"))
    bed(t, 12, 1, z0 + 1, "east", "red")
    bed(t, 15, 1, z1 - 1, "east", "white")
    t.put(x0 + 1, 1, z0 + 1, *barrel("up"))
    t.put(x0 + 1, 1, z1 - 1, *barrel("up", LOOT_VIKING_HUT))
    t.put(x1 - 1, 1, z0 + 1, *chest("west", LOOT_VIKING_HALL))
    t.put(x1 - 1, 1, z1 - 1, *chest("west", LOOT_VIKING_HALL))
    # trono del jarl
    t.put(x1 - 1, 1, 7, stairs("dark_oak", "east"))
    t.put(x1 - 1, 1, 6, slab("dark_oak", "top"))
    t.put(x1 - 1, 1, 8, slab("dark_oak", "top"))
    t.put(x1 - 1, 3, 6, S("red_wall_banner", facing="west"))
    t.put(x1 - 1, 3, 8, S("red_wall_banner", facing="west"))
    # armero junto a la entrada
    t.entity(armor_stand(head="iron_helmet", chest_="chainmail_chestplate", main="iron_axe", off="shield"), 2, 1, 3, YAW["east"])
    t.put(2, 1, 11, "spruce_fence")
    t.put(2, 2, 11, lantern())
    # quién vive aquí
    t.npc_slot(x1 - 2, 0, 7, P_VIKING_JARL)
    for (x, z) in ((8, 5), (14, 9), (18, 5)):
        t.npc_slot(x, 0, z, P_VIKINGS)
    t.npc_slot(2, 0, 5, P_VIKINGS)
    t.npc_slot(2, 0, 9, P_VIKINGS)
    # casas de alrededor
    for (x, z, f) in ((8, 0, "north"), (18, 0, "north"), (8, 14, "south"), (18, 14, "south"), (26, 7, "east")):
        t.jigsaw(x, 1, z, f + "_up", TENT, TENT, P_VIKING_PIECES)
    return t


def viking_hut():
    t = Template(10, 9, 9)
    ground_pad(t, 1, 9, 0, 8, ["coarse_dirt", "podzol", "gravel"])
    foundation(t, 2, 8, 1, 7, ring=S("cobblestone"), inner=S("spruce_planks"))
    clear(t, 3, 1, 2, 7, 6, 6)
    for y in (1, 2):
        for x in range(2, 9):
            for z in (1, 7):
                t.put(x, y, z, S("spruce_log", axis="x"))
        for z in range(2, 7):
            for x in (2, 8):
                t.put(x, y, z, S("spruce_log", axis="z"))
        for (x, z) in ((2, 1), (8, 1), (2, 7), (8, 7)):
            t.put(x, y, z, S("stripped_dark_oak_log", axis="y"))
    gable_x(t, 1, 9, 0, 8, 3, "dark_oak", "dark_oak", gable_xs=(2, 8), gable_block=S("spruce_planks"))
    for z in range(1, 8):
        t.put(2, 3, z, S("spruce_planks"))
        t.put(8, 3, z, S("spruce_planks"))
    door(t, 2, 1, 4, "east")
    t.put(8, 2, 4, "air")
    bed(t, 6, 1, 6, "east", "red")
    t.put(7, 1, 2, *barrel("up", LOOT_VIKING_HUT))
    t.put(3, 1, 6, "crafting_table")
    t.put(3, 1, 2, lantern())
    t.npc_slot(5, 0, 4, P_VIKINGS)
    tent_jigsaw(t, 4)
    return t


def viking_smithy():
    t = Template(9, 6, 9)
    ground_pad(t, 1, 8, 0, 8, ["coarse_dirt", "gravel"])
    for x in range(2, 8):
        for z in range(1, 8):
            t.put(x, 0, z, "cobblestone")
    for (x, z) in ((2, 1), (7, 1), (2, 7), (7, 7)):
        for y in range(1, 4):
            t.put(x, y, z, S("spruce_log", axis="y"))
    for x in range(1, 9):
        for z in range(0, 9):
            t.put(x, 4, z, slab("spruce"))
    t.put(5, 1, 3, S("anvil", facing="north"))
    t.put(6, 1, 6, "smithing_table")
    t.put(7, 1, 4, S("blast_furnace", facing="west", lit="false"))
    t.put(7, 1, 5, *chest("west", LOOT_WEAPONS))
    t.put(3, 1, 7, S("grindstone", face="floor", facing="north"))
    t.entity(armor_stand(head="iron_helmet", chest_="chainmail_chestplate", main="iron_axe"), 6, 1, 2, YAW["west"])
    t.put(2, 4, 4, lantern())
    t.npc_slot(4, 0, 5, P_VIKINGS)
    tent_jigsaw(t, 4)
    return t


def viking_runestones():
    """Círculo de piedras rúnicas con una hoguera de almas en el centro."""
    t = Template(11, 5, 11)
    for x in range(0, 11):
        for z in range(0, 11):
            if (x - 5) ** 2 + (z - 5) ** 2 <= 26:
                t.put(x, 0, z, "moss_block" if (x + z) % 3 == 0 else ("gravel" if (x * z) % 4 == 1 else "coarse_dirt"))
    for (x, z) in ((5, 1), (9, 3), (9, 7), (5, 9), (1, 7), (1, 3)):
        t.put(x, 0, z, "cobblestone")
        t.put(x, 1, z, "mossy_stone_bricks")
        t.put(x, 2, z, "chiseled_stone_bricks")
        t.put(x, 3, z, "mossy_stone_bricks" if (x + z) % 2 else "stone_bricks")
    t.put(5, 0, 5, "cobblestone")
    t.put(5, 1, 5, S("soul_campfire", facing="north", lit="true", signal_fire="false", waterlogged="false"))
    t.put(4, 1, 5, stairs("cobblestone", "east"))
    t.npc_slot(6, 0, 7, P_VIKINGS)
    t.jigsaw(0, 1, 5, "west_up", TENT, TENT, "minecraft:empty")
    return t


def viking_longship():
    """Drakkar varado: casco de abeto, escudos en la borda, vela a rayas rojas y blancas y cabeza de dragón."""
    t = Template(18, 12, 9)
    c = 4

    def hw(x):
        return {1: 0, 17: 0, 2: 1, 16: 1, 3: 2, 15: 2}.get(x, 3)

    for x in range(1, 18):
        for z in range(1, 8):
            t.put(x, 0, z, "gravel" if (x + z) % 3 else "sand")
    for x in range(1, 18):
        w = hw(x)
        for dz in range(-w, w + 1):
            t.put(x, 1, c + dz, "spruce_planks")
        if w > 0:
            t.put(x, 2, c - w, "spruce_planks")
            t.put(x, 2, c + w, "spruce_planks")
    for y in range(1, 6):
        t.put(17, y, c, S("spruce_log", axis="y"))
    t.put(17, 6, c, stairs("dark_oak", "west"))
    for y in range(1, 5):
        t.put(1, y, c, S("spruce_log", axis="y"))
    t.put(1, 5, c, stairs("dark_oak", "east"))
    # escudos en la borda
    for i, x in enumerate(range(5, 14, 2)):
        color = "red" if i % 2 == 0 else "white"
        t.put(x, 2, 0, S(f"{color}_wall_banner", facing="north"))
        t.put(x, 2, 8, S(f"{color}_wall_banner", facing="south"))
    # mástil, verga y vela
    for y in range(2, 11):
        t.put(9, y, c, S("spruce_log", axis="y"))
    for z in range(1, 8):
        t.put(10, 9, z, S("stripped_spruce_log", axis="z"))
    for y in range(4, 9):
        for z in range(2, 7):
            t.put(10, y, z, "red_wool" if z % 2 == 0 else "white_wool")
    t.put(6, 2, c, *barrel("up", LOOT_VIKING_HUT))
    t.put(13, 2, c, *barrel("up"))
    t.npc_slot(5, 1, 3, P_VIKINGS)
    t.npc_slot(13, 1, 5, P_VIKING_ARCHERS)
    t.jigsaw(0, 1, c, "west_up", TENT, TENT, "minecraft:empty")
    return t


def viking_watch():
    """Atalaya de madera sobre pilotes, con un arquero."""
    t = Template(7, 9, 7)
    ground_pad(t, 0, 6, 0, 6, ["coarse_dirt", "gravel"])
    for (x, z) in ((1, 1), (5, 1), (1, 5), (5, 5)):
        for y in range(1, 5):
            t.put(x, y, z, S("spruce_log", axis="y"))
    for y in range(1, 5):
        t.put(3, y, 3, S("spruce_log", axis="y"))
        t.put(2, y, 3, S("ladder", facing="west", waterlogged="false"))
    for x in range(1, 6):
        for z in range(1, 6):
            t.put(x, 5, z, "spruce_planks")
            if x in (1, 5) or z in (1, 5):
                t.put(x, 6, z, "spruce_fence")
    t.put(2, 5, 3, S("ladder", facing="west", waterlogged="false"))
    t.put(1, 7, 1, lantern())
    t.put(5, 7, 5, S("red_banner", rotation="4"))
    t.npc_slot(4, 5, 4, P_VIKING_ARCHERS)
    tent_jigsaw(t, 3)
    return t


def viking_storage():
    """Leñera, heno y barriles."""
    t = Template(9, 5, 7)
    ground_pad(t, 1, 8, 0, 6, ["coarse_dirt", "podzol"])
    for x in range(3, 8):
        for y in (1, 2):
            t.put(x, y, 1, S("spruce_log", axis="x"))
        t.put(x, 3, 1, slab("spruce"))
    for (x, y, z) in ((3, 1, 5), (4, 1, 5), (3, 2, 5)):
        t.put(x, y, z, S("hay_block", axis="y"))
    for (x, y, z) in ((6, 1, 4), (7, 1, 4), (7, 1, 5), (6, 2, 4)):
        t.put(x, y, z, *barrel("up"))
    t.put(7, 2, 5, *barrel("up", LOOT_VIKING_HUT))
    for z in (3, 5):
        t.put(1, 1, z, "spruce_fence")
    t.npc_slot(4, 0, 3, P_VIKINGS)
    tent_jigsaw(t, 3)
    return t


# ======================================================================== PIRATAS (fase 5)
P_PIRATES = f"{NS}:npc/pirates"
P_PIRATE_GUNNERS = f"{NS}:npc/pirate_gunners"
P_PIRATE_CAPTAIN = f"{NS}:npc/pirate_captain"
P_PIRATE_PIECES = f"{NS}:pirate/pieces"
LOOT_PIRATE_SHIP = f"{NS}:chests/pirate_ship"
LOOT_PIRATE_TREASURE = f"{NS}:chests/pirate_treasure"
LOOT_PIRATE_CAMP = f"{NS}:chests/pirate_camp"


def pirate_ship():
    """Barco pirata varado en la playa: casco negro, dos mástiles con velas negras rotas, cañones, camarote del
    capitán a popa, bodega con escotilla y pasarela."""
    t = Template(28, 21, 15)
    c = 6
    ground_pad(t, 0, 27, 0, 14, ["sand", "sand", "gravel", "sandstone"])
    widths = {1: 4, 2: 4, 3: 4, 21: 3, 22: 3, 23: 2, 24: 1, 25: 0}

    def hw(x):
        return widths.get(x, 4)

    hull = "dark_oak_planks"
    for x in range(1, 26):
        w = hw(x)
        for dz in range(-w, w + 1):
            z = c + dz
            if abs(dz) < w:
                t.put(x, 1, z, hull)
                for y in (2, 3, 4):
                    t.put(x, y, z, "air")
            else:
                for y in (1, 2, 4):
                    t.put(x, y, z, hull)
                t.put(x, 3, z, S("stripped_dark_oak_log", axis="x"))
            t.put(x, 5, z, "spruce_planks")
            if abs(dz) == w and w > 0:
                t.put(x, 6, z, "dark_oak_fence")
    t.put(25, 6, c, "dark_oak_fence")
    t.put(26, 6, c, S("stripped_spruce_log", axis="x"))
    t.put(27, 7, c, S("stripped_spruce_log", axis="x"))
    # cañones (dispensadores asomando por las troneras)
    for x in (8, 11, 14, 17):
        t.put(x, 3, c - 4, S("dispenser", facing="north", triggered="false"))
        t.put(x, 3, c + 4, S("dispenser", facing="south", triggered="false"))
    # castillo de popa con el camarote
    for x in range(1, 7):
        w = hw(x)
        for dz in range(-w, w + 1):
            z = c + dz
            for y in (6, 7, 8):
                edge = x in (1, 6) or abs(dz) == w
                t.put(x, y, z, "dark_oak_planks" if edge else "air")
            t.put(x, 9, z, "spruce_planks")
            if abs(dz) == w or x == 1 or x == 6:
                t.put(x, 10, z, "dark_oak_fence")
    door(t, 6, 6, c, "east", kind="dark_oak_door")
    for z in (c - 2, c + 2):
        t.put(1, 7, z, "glass_pane")
        t.put(6, 7, z, "glass_pane")
    t.put(2, 6, c - 3, *chest("east", LOOT_PIRATE_TREASURE))
    t.put(2, 6, c + 3, *barrel("up", LOOT_PIRATE_SHIP))
    t.put(4, 8, c, lantern(hanging=True))
    t.put(5, 6, c + 3, slab("dark_oak", "top"))
    # mástiles, vergas y velas negras (con algún roto)
    for (mx, top, yards) in ((12, 19, (10, 17)), (19, 15, (9, 14))):
        for y in range(6, top + 1):
            t.put(mx, y, c, S("spruce_log", axis="y"))
        sx = mx + 1
        for yy in yards:
            for z in range(c - 4, c + 5):
                t.put(sx, yy, z, S("stripped_spruce_log", axis="z"))
        for y in range(yards[0] + 1, yards[1]):
            for z in range(c - 3, c + 4):
                torn = (y * 7 + z * 5 + mx) % 11 == 0
                t.put(sx, y, z, "air" if torn else "black_wool")
    # cofa y bandera negra
    for (dx, dz) in ((-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)):
        t.put(12 + dx, 18, c + dz, slab("spruce", "top"))
        t.put(12 + dx, 19, c + dz, "spruce_fence")
    t.put(12, 20, c, S("black_banner", rotation="4"))
    # escotilla a la bodega
    for y in range(2, 5):
        t.put(16, y, c + 1, "spruce_planks")
    for y in range(2, 6):
        t.put(16, y, c, S("ladder", facing="north", waterlogged="false"))
    for (x, z) in ((8, c - 2), (8, c + 2), (14, c - 3)):
        t.put(x, 2, z, *barrel("up", LOOT_PIRATE_SHIP))
    t.put(20, 2, c, *chest("west", LOOT_PIRATE_TREASURE))
    t.put(10, 4, c, lantern(hanging=True))
    # pasarela hasta la arena
    t.put(12, 6, c + 4, "air")
    for i, y in enumerate((4, 3, 2, 1)):
        t.put(12, y, c + 5 + i, stairs("spruce", "north"))
    # tripulación
    t.npc_slot(3, 5, c, P_PIRATE_CAPTAIN)
    for (x, z) in ((9, c - 2), (16, c + 2), (22, c)):
        t.npc_slot(x, 5, z, P_PIRATES)
    t.npc_slot(9, 5, c + 2, P_PIRATE_GUNNERS)
    t.npc_slot(3, 9, c, P_PIRATE_GUNNERS)
    t.npc_slot(5, 0, 13, P_PIRATES)
    # campamento alrededor
    for (x, z, f) in ((6, 0, "north"), (20, 0, "north"), (4, 14, "south"), (20, 14, "south"), (0, c, "west"),
                      (27, c, "east")):
        t.jigsaw(x, 1, z, f + "_up", TENT, TENT, P_PIRATE_PIECES)
    return t


def pirate_tavern():
    """Chiringuito de madera de deriva: barriles de ron, mesa y taburetes."""
    t = Template(9, 7, 8)
    ground_pad(t, 1, 8, 0, 7, ["sand", "gravel"])
    foundation(t, 2, 8, 1, 6, ring=S("stripped_oak_log", axis="y"), inner=S("spruce_planks"))
    clear(t, 3, 1, 2, 7, 4, 5)
    for y in (1, 2, 3):
        for x in range(2, 9):
            for z in (1, 6):
                t.put(x, y, z, "stripped_oak_log" if x in (2, 8) else ("spruce_planks" if y != 2 or x % 3 else "air"))
        for z in range(2, 6):
            for x in (2, 8):
                t.put(x, y, z, "spruce_planks")
    for x in range(1, 10 - 1):
        for z in range(0, 8):
            t.put(x, 4, z, slab("spruce"))
    door(t, 2, 1, 3, "east", kind="spruce_door")
    for (x, y, z) in ((7, 1, 2), (7, 1, 3), (7, 2, 2)):
        t.put(x, y, z, *barrel("east", LOOT_PIRATE_CAMP if (x, y, z) == (7, 1, 3) else None))
    t.put(5, 1, 4, "spruce_fence")
    t.put(5, 2, 4, S("spruce_pressure_plate", powered="false"))
    t.put(4, 1, 4, stairs("spruce", "west"))
    t.put(6, 1, 4, stairs("spruce", "east"))
    t.put(5, 3, 3, lantern(hanging=True))
    t.npc_slot(4, 0, 2, P_PIRATES)
    t.npc_slot(6, 0, 5, P_PIRATES)
    tent_jigsaw(t, 3)
    return t


def pirate_cannons():
    """Batería de costa: dos cañones tras un parapeto y balas apiladas."""
    t = Template(7, 4, 9)
    ground_pad(t, 0, 6, 0, 8, ["sand", "gravel"])
    for z in range(1, 8):
        t.put(5, 1, z, "sandstone" if z % 3 else "smooth_sandstone")
    for z in (2, 6):
        t.put(4, 1, z, S("dispenser", facing="east", triggered="false"))
        t.put(3, 1, z, S("polished_blackstone_stairs", facing="east", half="bottom", shape="straight", waterlogged="false"))
        t.put(4, 1, z - 1, S("dark_oak_trapdoor", facing="north", half="bottom", open="true", powered="false", waterlogged="false"))
        t.put(4, 1, z + 1, S("dark_oak_trapdoor", facing="south", half="bottom", open="true", powered="false", waterlogged="false"))
    for (x, y, z) in ((2, 1, 4), (3, 1, 4), (2, 2, 4)):
        t.put(x, y, z, "blackstone")
    t.put(1, 1, 7, *barrel("up", LOOT_PIRATE_CAMP))
    t.npc_slot(2, 0, 2, P_PIRATE_GUNNERS)
    tent_jigsaw(t, 4)
    return t


def pirate_treasure():
    """El tesoro: una X roja en la arena, el cofre enterrado, una calavera y un arbusto seco."""
    t = Template(7, 3, 7)
    for x in range(0, 7):
        for z in range(0, 7):
            t.put(x, 0, z, "red_sand" if (x - z == 0 or x + z == 6) and 1 <= x <= 5 else "sand")
    t.put(3, 0, 3, *chest("north", LOOT_PIRATE_TREASURE))
    t.put(5, 1, 1, S("skeleton_skull", rotation="6"))
    t.put(1, 1, 5, "dead_bush")
    t.entity(armor_stand(main="iron_shovel"), 5, 1, 5, YAW["north"])
    t.npc_slot(1, 0, 1, P_PIRATES)
    tent_jigsaw(t, 3)
    return t


def pirate_lookout():
    """Vigía: un poste alto con plataforma, escalera de mano y bandera negra."""
    t = Template(6, 12, 6)
    ground_pad(t, 0, 5, 0, 5, ["sand", "gravel"])
    for y in range(1, 10):
        t.put(3, y, 3, S("spruce_log", axis="y"))
    for y in range(1, 9):
        t.put(2, y, 3, S("ladder", facing="west", waterlogged="false"))
    for x in range(1, 6):
        for z in range(1, 6):
            if (x, z) in ((3, 3), (2, 3)):
                continue
            t.put(x, 8, z, "spruce_planks")
            if x in (1, 5) or z in (1, 5):
                t.put(x, 9, z, "spruce_fence")
    t.put(3, 10, 3, S("black_banner", rotation="4"))
    t.put(1, 10, 1, lantern())
    t.npc_slot(4, 8, 4, P_PIRATE_GUNNERS)
    tent_jigsaw(t, 3)
    return t


# ======================================================================== JSON
def element(location, projection="rigid", processors=None, legacy=False):
    return {
        "element_type": "minecraft:legacy_single_pool_element" if legacy else "minecraft:single_pool_element",
        "location": location,
        "processors": processors if processors else {"processors": []},
        "projection": projection,
    }


EMPTY = {"element_type": "minecraft:empty_pool_element"}


def pool(elements, fallback="minecraft:empty"):
    return {"elements": [{"element": e, "weight": w} for (e, w) in elements], "fallback": fallback}


def rule(block, prob, out, location=None):
    r = {
        "input_predicate": ({"predicate_type": "minecraft:random_block_match", "block": block, "probability": prob}
                            if prob < 1 else {"predicate_type": "minecraft:block_match", "block": block}),
        "location_predicate": ({"predicate_type": "minecraft:block_match", "block": location}
                               if location else {"predicate_type": "minecraft:always_true"}),
        "output_state": {"Name": out},
    }
    return r


PROCESSORS = {
    "city_street": {"processors": [{"processor_type": "minecraft:rule", "rules": [
        rule("minecraft:cobblestone", 1, "minecraft:spruce_planks", "minecraft:water"),
        rule("minecraft:stone_bricks", 1, "minecraft:spruce_planks", "minecraft:water"),
        rule("minecraft:cobblestone", 0.12, "minecraft:mossy_cobblestone"),
        rule("minecraft:cobblestone", 0.08, "minecraft:gravel"),
        rule("minecraft:cobblestone", 0.08, "minecraft:andesite"),
        rule("minecraft:stone_bricks", 0.15, "minecraft:cracked_stone_bricks"),
        rule("minecraft:stone_bricks", 0.08, "minecraft:mossy_stone_bricks"),
    ]}]},
    "city_weathering": {"processors": [{"processor_type": "minecraft:rule", "rules": [
        rule("minecraft:stone_bricks", 0.10, "minecraft:cracked_stone_bricks"),
        rule("minecraft:stone_bricks", 0.06, "minecraft:mossy_stone_bricks"),
        rule("minecraft:cobblestone", 0.10, "minecraft:mossy_cobblestone"),
    ]}]},
}

TEMPLATES = {
    "city/town_centers/plaza_fountain": plaza_fountain,
    "city/town_centers/training_yard": training_yard,
    "city/streets/straight": street_straight,
    "city/streets/crossroad": street_crossroad,
    "city/streets/t_junction": street_t_junction,
    "city/streets/turn": street_turn,
    "city/terminators/street_end": terminator_plain,
    "city/terminators/gate": terminator_gate,
    "city/houses/cottage": cottage,
    "city/houses/townhouse": townhouse,
    "city/houses/barracks": barracks,
    "city/houses/tavern": tavern,
    "city/houses/blacksmith": blacksmith,
    "city/houses/guard_post": guard_post,
    "city/houses/market_stalls": market_stalls,
    "city/houses/garden": garden,
    "npc/recruit": npc_recruit,
    "camp/center": camp_center,
    "camp/tent_small": tent_small,
    "camp/tent_large": tent_large,
    "camp/supply": camp_supply,
    "camp/weapon_rack": camp_weapon_rack,
    "watchtower/tower": watchtower,
    "npc/bandit": npc_bandit,
    "npc/bandit_archer": lambda: npc_bandit("archer"),
    "npc/bandit_crossbowman": lambda: npc_bandit("crossbowman"),
    "npc/bandit_captain": lambda: npc_bandit("captain"),
    "bandit_camp/center": lambda: camp_center("black_banner", LOOT_BANDIT_CAMP, P_BANDITS, P_BANDIT_TENTS),
    "bandit_camp/tent_small": lambda: tent_small("black_wool", "black", P_BANDITS),
    "bandit_camp/tent_large": lambda: tent_large("red_wool", "black_wool", "black", P_BANDITS, LOOT_BANDIT_CAMP),
    "bandit_camp/cage": bandit_cage,
    "bandit_fort/fort": bandit_fort,
    "npc/viking": lambda: npc_faction("viking"),
    "npc/viking_archer": lambda: npc_faction("viking", "archer"),
    "npc/viking_jarl": lambda: npc_faction("viking", "captain"),
    "npc/pirate": lambda: npc_faction("pirate"),
    "npc/pirate_gunner": lambda: npc_faction("pirate", "crossbowman"),
    "npc/pirate_captain": lambda: npc_faction("pirate", "captain"),
    "viking/longhouse": viking_longhouse,
    "viking/hut": viking_hut,
    "viking/smithy": viking_smithy,
    "viking/runestones": viking_runestones,
    "viking/longship": viking_longship,
    "viking/watch": viking_watch,
    "viking/storage": viking_storage,
    "pirate/ship": pirate_ship,
    "pirate/tent": lambda: tent_large("black_wool", "red_wool", "black", P_PIRATES, LOOT_PIRATE_CAMP),
    "pirate/tavern": pirate_tavern,
    "pirate/cannons": pirate_cannons,
    "pirate/treasure": pirate_treasure,
    "pirate/lookout": pirate_lookout,
}


def L(path):
    return f"{NS}:{path}"


WEATHER = L("city_weathering")
STREET_PROC = L("city_street")

POOLS = {
    "city/town_centers": pool([(element(L("city/town_centers/plaza_fountain"), processors=WEATHER), 1),
                               (element(L("city/town_centers/training_yard"), processors=WEATHER), 1)]),
    "city/streets": pool([(element(L("city/streets/straight"), "terrain_matching", STREET_PROC, True), 6),
                          (element(L("city/streets/crossroad"), "terrain_matching", STREET_PROC, True), 2),
                          (element(L("city/streets/t_junction"), "terrain_matching", STREET_PROC, True), 3),
                          (element(L("city/streets/turn"), "terrain_matching", STREET_PROC, True), 2)],
                         fallback=P_TERMINATORS),
    "city/terminators": pool([(element(L("city/terminators/street_end"), "terrain_matching", STREET_PROC, True), 2),
                              (element(L("city/terminators/gate"), "terrain_matching", STREET_PROC, True), 1)]),
    "city/houses": pool([(element(L("city/houses/cottage"), processors=WEATHER), 6),
                         (element(L("city/houses/townhouse"), processors=WEATHER), 5),
                         (element(L("city/houses/barracks"), processors=WEATHER), 2),
                         (element(L("city/houses/tavern"), processors=WEATHER), 2),
                         (element(L("city/houses/blacksmith"), processors=WEATHER), 2),
                         (element(L("city/houses/guard_post"), processors=WEATHER), 1),
                         (element(L("city/houses/market_stalls"), processors=WEATHER), 2),
                         (element(L("city/houses/garden")), 2),
                         (EMPTY, 3)]),
    "npc/recruits": pool([(element(L("npc/recruit"), legacy=True), 4), (EMPTY, 1)]),
    "npc/residents": pool([(element(L("npc/recruit"), legacy=True), 1), (EMPTY, 2)]),
    "camp/centers": pool([(element(L("camp/center")), 1)]),
    "camp/tents": pool([(element(L("camp/tent_small")), 3), (element(L("camp/tent_large")), 2),
                        (element(L("camp/supply")), 2), (element(L("camp/weapon_rack")), 1), (EMPTY, 1)]),
    "watchtower/base": pool([(element(L("watchtower/tower"), processors=WEATHER), 1)]),
    "npc/bandits": pool([(element(L("npc/bandit"), legacy=True), 5), (EMPTY, 1)]),
    "npc/bandit_archers": pool([(element(L("npc/bandit_archer"), legacy=True), 3),
                                (element(L("npc/bandit_crossbowman"), legacy=True), 2), (EMPTY, 1)]),
    "npc/bandit_captain": pool([(element(L("npc/bandit_captain"), legacy=True), 1)]),
    "npc/prisoners": pool([(element(L("npc/recruit"), legacy=True), 1)]),
    "bandit_camp/centers": pool([(element(L("bandit_camp/center")), 1)]),
    "bandit_camp/tents": pool([(element(L("bandit_camp/tent_small")), 3), (element(L("bandit_camp/tent_large")), 2),
                               (element(L("bandit_camp/cage")), 2), (element(L("camp/supply")), 1), (EMPTY, 1)]),
    "bandit_fort/base": pool([(element(L("bandit_fort/fort"), processors=WEATHER), 1)]),
    "npc/vikings": pool([(element(L("npc/viking"), legacy=True), 5), (EMPTY, 1)]),
    "npc/viking_archers": pool([(element(L("npc/viking_archer"), legacy=True), 3), (EMPTY, 1)]),
    "npc/viking_jarl": pool([(element(L("npc/viking_jarl"), legacy=True), 1)]),
    "npc/pirates": pool([(element(L("npc/pirate"), legacy=True), 5), (EMPTY, 1)]),
    "npc/pirate_gunners": pool([(element(L("npc/pirate_gunner"), legacy=True), 3), (EMPTY, 1)]),
    "npc/pirate_captain": pool([(element(L("npc/pirate_captain"), legacy=True), 1)]),
    "viking/centers": pool([(element(L("viking/longhouse"), processors=WEATHER), 1)]),
    "viking/pieces": pool([(element(L("viking/hut"), processors=WEATHER), 4), (element(L("viking/smithy")), 2),
                           (element(L("viking/runestones"), processors=WEATHER), 1), (element(L("viking/longship")), 2),
                           (element(L("viking/watch")), 2), (element(L("viking/storage")), 2), (EMPTY, 1)]),
    "pirate/centers": pool([(element(L("pirate/ship")), 1)]),
    "pirate/pieces": pool([(element(L("pirate/tent")), 3), (element(L("pirate/tavern")), 2),
                           (element(L("pirate/cannons")), 2), (element(L("pirate/treasure")), 1),
                           (element(L("pirate/lookout")), 2), (EMPTY, 1)]),
}


def jigsaw_structure(start_pool, size, max_distance, biomes, expansion_hack):
    return {
        "type": "minecraft:jigsaw",
        "biomes": biomes,
        "max_distance_from_center": max_distance,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": size,
        "spawn_overrides": {},
        "start_height": {"absolute": 0},
        "start_pool": start_pool,
        "step": "surface_structures",
        "terrain_adaptation": "beard_thin",
        "use_expansion_hack": expansion_hack,
    }


STRUCTURES = {
    "city": jigsaw_structure(L("city/town_centers"), 5, 72, f"#{NS}:has_structure/city", True),
    "mercenary_camp": jigsaw_structure(L("camp/centers"), 2, 32, f"#{NS}:has_structure/mercenary_camp", False),
    "watchtower": jigsaw_structure(L("watchtower/base"), 1, 16, f"#{NS}:has_structure/watchtower", False),
    "bandit_camp": jigsaw_structure(L("bandit_camp/centers"), 2, 32, f"#{NS}:has_structure/bandit_camp", False),
    "bandit_fort": jigsaw_structure(L("bandit_fort/base"), 1, 24, f"#{NS}:has_structure/bandit_fort", False),
    "viking_settlement": jigsaw_structure(L("viking/centers"), 2, 40, f"#{NS}:has_structure/viking_settlement", False),
    "pirate_cove": jigsaw_structure(L("pirate/centers"), 2, 40, f"#{NS}:has_structure/pirate_cove", False),
}

STRUCTURE_SETS = {
    "cities": {
        "placement": {"type": "minecraft:random_spread", "salt": 1593274681, "spacing": 44, "separation": 16,
                      "exclusion_zone": {"other_set": "minecraft:villages", "chunk_count": 6}},
        "structures": [{"structure": L("city"), "weight": 1}],
    },
    "outposts": {
        "placement": {"type": "minecraft:random_spread", "salt": 1720193857, "spacing": 26, "separation": 9,
                      "exclusion_zone": {"other_set": "minecraft:villages", "chunk_count": 4}},
        "structures": [{"structure": L("mercenary_camp"), "weight": 1}, {"structure": L("watchtower"), "weight": 1}],
    },
    "bandit_lairs": {
        "placement": {"type": "minecraft:random_spread", "salt": 908712345, "spacing": 30, "separation": 11,
                      "exclusion_zone": {"other_set": "minecraft:villages", "chunk_count": 6}},
        "structures": [{"structure": L("bandit_camp"), "weight": 3}, {"structure": L("bandit_fort"), "weight": 1}],
    },
    "viking_settlements": {
        "placement": {"type": "minecraft:random_spread", "salt": 1357911131, "spacing": 32, "separation": 12,
                      "exclusion_zone": {"other_set": "minecraft:villages", "chunk_count": 6}},
        "structures": [{"structure": L("viking_settlement"), "weight": 1}],
    },
    "pirate_coves": {
        "placement": {"type": "minecraft:random_spread", "salt": 246813579, "spacing": 22, "separation": 8,
                      "exclusion_zone": {"other_set": "minecraft:villages", "chunk_count": 4}},
        "structures": [{"structure": L("pirate_cove"), "weight": 1}],
    },
}

BANDIT_BIOMES = ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest",
                 "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:taiga", "minecraft:old_growth_pine_taiga",
                 "minecraft:old_growth_spruce_taiga", "minecraft:snowy_taiga", "minecraft:snowy_plains",
                 "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_hills",
                 "minecraft:windswept_forest", "minecraft:windswept_savanna", "minecraft:badlands",
                 "minecraft:wooded_badlands"]

VIKING_BIOMES = ["minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:taiga", "minecraft:old_growth_pine_taiga",
                 "minecraft:old_growth_spruce_taiga", "minecraft:grove", "minecraft:windswept_hills",
                 "minecraft:windswept_forest", "minecraft:windswept_gravelly_hills"]
PIRATE_BIOMES = ["minecraft:beach", "minecraft:stony_shore", "minecraft:mangrove_swamp"]

BIOME_TAGS = {
    "viking_settlement": VIKING_BIOMES,
    "pirate_cove": ["minecraft:beach", "minecraft:stony_shore"],
    "bandit_camp": BANDIT_BIOMES,
    "bandit_fort": BANDIT_BIOMES,
    "city": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:savanna",
             "minecraft:forest", "minecraft:birch_forest", "minecraft:taiga", "minecraft:snowy_plains"],
    "mercenary_camp": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest",
                       "minecraft:birch_forest", "minecraft:flower_forest", "minecraft:taiga",
                       "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
                       "minecraft:snowy_taiga", "minecraft:savanna", "minecraft:savanna_plateau",
                       "minecraft:windswept_forest"],
    "watchtower": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest",
                   "minecraft:birch_forest", "minecraft:taiga", "minecraft:snowy_taiga", "minecraft:snowy_plains",
                   "minecraft:savanna", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills",
                   "minecraft:windswept_forest", "minecraft:stony_shore"],
}


def build_all():
    return {name: fn() for name, fn in TEMPLATES.items()}


def write(data_dir):
    def dump(rel, obj):
        path = os.path.join(data_dir, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(obj, f, indent=2)
            f.write("\n")

    templates = build_all()
    for name, t in templates.items():
        t.save(os.path.join(data_dir, "structure", name + ".nbt"))
    for name, obj in POOLS.items():
        dump(f"worldgen/template_pool/{name}.json", obj)
    for name, obj in PROCESSORS.items():
        dump(f"worldgen/processor_list/{name}.json", obj)
    for name, obj in STRUCTURES.items():
        dump(f"worldgen/structure/{name}.json", obj)
    for name, obj in STRUCTURE_SETS.items():
        dump(f"worldgen/structure_set/{name}.json", obj)
    for name, biomes in BIOME_TAGS.items():
        dump(f"tags/worldgen/biome/has_structure/{name}.json", {"values": biomes})
    # tierras de cada facción (patrullas y asaltos)
    dump("tags/worldgen/biome/viking_lands.json", {"values": VIKING_BIOMES + [
        "minecraft:ice_spikes", "minecraft:frozen_river", "minecraft:snowy_slopes", "minecraft:snowy_beach",
        "minecraft:frozen_peaks", "minecraft:jagged_peaks"]})
    dump("tags/worldgen/biome/pirate_coasts.json", {"values": PIRATE_BIOMES})
    return templates


if __name__ == "__main__":
    out = write(sys.argv[1])
    for name, t in out.items():
        print(f"{name:40s} {t.size}  bloques={len(t.blocks):5d}  entidades={len(t.entities)}  "
              f"jigsaws={len(t.jigsaws())}")
