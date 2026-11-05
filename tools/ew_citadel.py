"""La ciudadela infernal (fase 25): sustituye a la fortaleza del Nether.

Una ciudadela de 88x88 rodeada por un foso de lava con su puente: muralla con adarve y almenas, cuatro torres con agujas,
puerta fortificada, patio con huertos de verruga, el altar de los blaze, obeliscos y jaulas; cuartel y armería al norte,
calabozos y laboratorio al sur, y en el centro el gran salón del trono, con columnas, canales de lava tras rejas, la
alfombra roja, el trono sobre su estrado, el tesoro detrás y un mini jefe demoníaco que lo guarda.

Escribe la plantilla (.nbt), las piezas del jefe, los grupos de plantillas, la estructura (con sus criaturas: blazes,
esqueletos wither, demonios...), el conjunto de estructuras del Nether (la fortaleza de vanilla deja de salir), el botín y
las etiquetas. Con --preview dibuja un plano de la planta.

Uso: python3 tools/ew_citadel.py [raíz del proyecto] [--preview carpeta]
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402
from ew_structlib import NS, S, Template, chest, lantern, slab, stairs, YAW, armor_stand  # noqa: E402

args = sys.argv[1:]
PREVIEW = None
if "--preview" in args:
    i = args.index("--preview")
    PREVIEW = args[i + 1]
    del args[i:i + 2]
ROOT = args[0] if args else os.path.join(os.path.dirname(__file__), "..")
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
MC_DATA = os.path.join(ROOT, "src/main/resources/data/minecraft")

N = 88
H = 40
LOOT_ARMORY = f"{NS}:chests/citadel_armory"
LOOT_TREASURY = f"{NS}:chests/citadel_treasury"
LOOT_BRIDGE = "minecraft:chests/nether_bridge"

BRICK = S("nether_bricks")
RED = S("red_nether_bricks")
BLACK = S("polished_blackstone_bricks")
CRACKED = S("cracked_polished_blackstone_bricks")
GILDED = S("gilded_blackstone")
BLACKSTONE = S("blackstone")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def wall_block(x, y, z):
    """La piedra de los muros: piedra negra abajo (con alguna grieta), ladrillo del Nether arriba."""
    h = (x * 73 + y * 31 + z * 17) % 23
    if y <= 4:
        return CRACKED if h < 3 else BLACK
    if h == 0:
        return GILDED
    return BRICK if h > 4 else RED


def floor_block(x, z):
    h = (x * 13 + z * 7) % 11
    return S("polished_blackstone") if (x + z) % 2 == 0 else (BLACKSTONE if h < 3 else S("nether_bricks"))


def put_if(t, x, y, z, st):
    if t.inside(x, y, z):
        t.put(x, y, z, st)


# ------------------------------------------------------------------ piezas

def moat_and_bridge(t):
    for x in range(N):
        for z in range(N):
            ring = min(x, z, N - 1 - x, N - 1 - z)
            if ring == 0:
                t.put(x, 0, z, BLACK)
                t.put(x, 1, z, S("nether_brick_fence"))
            elif ring <= 3:
                t.put(x, 0, z, S("lava"))
                for y in range(1, 6):
                    t.put(x, y, z, "air")
    # el puente (oeste), sobre arcos
    for x in range(0, 4):
        for z in range(41, 47):
            t.put(x, 0, z, BLACK if z in (41, 46) else S("polished_blackstone"))
            for y in range(1, 6):
                t.put(x, y, z, "air")
        t.put(x, 1, 41, S("nether_brick_fence"))
        t.put(x, 1, 46, S("nether_brick_fence"))
    t.put(0, 2, 41, S("soul_lantern", hanging="false", waterlogged="false"))
    t.put(0, 2, 46, S("soul_lantern", hanging="false", waterlogged="false"))


def curtain_wall(t, top=13):
    lo, hi = 4, N - 5
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            ring = min(x - lo, z - lo, hi - x, hi - z)
            if ring > 2:
                continue
            t.put(x, 0, z, BLACK)
            for y in range(1, top + 1):
                t.put(x, y, z, wall_block(x, y, z))
            if ring == 0 and (x + z) % 2 == 0:
                t.put(x, top + 1, z, S("polished_blackstone_brick_wall"))
                if (x + z) % 8 == 0:
                    t.put(x, top + 2, z, S("soul_lantern", hanging="false", waterlogged="false"))
            elif ring == 2:
                t.put(x, top + 1, z, "air")
            # aspilleras
            if ring == 0 and y and (x + z) % 9 == 0:
                for y in (8, 9):
                    t.put(x, y, z, S("iron_bars"))


def tower(t, cx, cz, r=6, top=24):
    """Torre redonda con planta a la altura del adarve, almenas, brasero y una aguja escalonada."""
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            d2 = (x - cx) ** 2 + (z - cz) ** 2
            if d2 > r * r + r or not t.inside(x, 0, z):
                continue
            edge = d2 > (r - 2) ** 2 + (r - 2)
            t.put(x, 0, z, BLACK)
            for y in range(1, top + 1):
                if edge:
                    t.put(x, y, z, wall_block(x, y, z))
                elif y in (13, top):
                    t.put(x, y, z, BRICK)
                else:
                    t.put(x, y, z, "air")
            if edge and d2 > (r - 1) ** 2 and (x + z) % 2 == 0:
                t.put(x, top + 1, z, S("nether_brick_fence"))
            elif not edge:
                t.put(x, top + 1, z, "air")
            # ventanas con rejas
            if edge and d2 > (r - 1) ** 2 and (x * 3 + z) % 7 == 0:
                for y in (6, 7, 18, 19):
                    t.put(x, y, z, S("iron_bars"))
    # escalera de mano para subir por dentro
    for y in range(1, top + 1):
        t.put(cx, y, cz - r + 2, S("ladder", facing="south", waterlogged="false"))
        if y in (13, top):
            t.put(cx, y, cz - r + 2, S("ladder", facing="south", waterlogged="false"))
    # puertas al adarve (por los cuatro lados)
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for k in range(r - 2, r + 1):
            for y in (14, 15):
                put_if(t, cx + dx * k, y, cz + dz * k, "air")
        for y in (1, 2):
            for k in range(r - 2, r + 1):
                if dx == 0:
                    pass
    # la aguja
    for (y0, y1, rr) in ((top + 1, top + 4, 2), (top + 5, top + 7, 1)):
        for y in range(y0, y1 + 1):
            for x in range(cx - rr, cx + rr + 1):
                for z in range(cz - rr, cz + rr + 1):
                    put_if(t, x, y, z, RED if (x + y + z) % 5 else GILDED)
    for y in range(top + 8, min(H, top + 12)):
        t.put(cx, y, cz, S("nether_brick_fence"))
    for (dx, dz) in ((3, 0), (-3, 0), (0, 3), (0, -3)):
        t.put(cx + dx, top + 1, cz + dz, S("soul_campfire", facing="north", lit="true", signal_fire="false", waterlogged="false"))
    t.put(cx, 14, cz, S("spawner"), spawner("minecraft:blaze")) if (cx + cz) % 2 == 0 else None


def spawner(mob):
    return {"id": "minecraft:mob_spawner", "SpawnData": {"entity": {"id": mob}}, "Delay": nbt.Short(20),
            "MinSpawnDelay": nbt.Short(200), "MaxSpawnDelay": nbt.Short(800), "SpawnCount": nbt.Short(3), "MaxNearbyEntities": nbt.Short(6),
            "RequiredPlayerRange": nbt.Short(16), "SpawnRange": nbt.Short(4)}


def gatehouse(t):
    x0, x1 = 4, 13
    for (z0, z1) in ((35, 40), (47, 52)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                t.put(x, 0, z, BLACK)
                for y in range(1, 20):
                    t.put(x, y, z, wall_block(x, y, z))
                if (x == x0 or z in (z0, z1)) and (x + z) % 2 == 0:
                    t.put(x, 20, z, S("polished_blackstone_brick_wall"))
        for y in range(20, 23):
            t.put((x0 + x1) // 2, y, (z0 + z1) // 2, S("nether_brick_fence"))
        t.put((x0 + x1) // 2, 23, (z0 + z1) // 2, S("soul_campfire", facing="north", lit="true", signal_fire="false", waterlogged="false"))
    # el paso de la puerta (4 de ancho, 7 de alto) y el rastrillo levantado
    for x in range(x0, x1 + 1):
        for z in range(41, 47):
            t.put(x, 0, z, S("polished_blackstone"))
            for y in range(1, 13):
                t.put(x, y, z, "air" if y <= 7 else wall_block(x, y, z))
    for z in range(41, 47):
        for y in (6, 7):
            t.put(x0, y, z, S("iron_bars"))
        t.put(x0 + 1, 8, z, RED)
    for z in (41, 46):
        t.put(x1 + 1, 4, z if z == 41 else 46, "air")
    t.put(x0 - 0, 9, 43, S("red_wall_banner", facing="west"))
    t.put(x0 - 0, 9, 44, S("black_wall_banner", facing="west"))
    for y in (2, 3):
        pass


def courtyard_floor(t):
    for x in range(7, N - 7):
        for z in range(7, N - 7):
            if (x, 0, z) not in t.blocks:
                t.put(x, 0, z, floor_block(x, z))
            for y in range(1, 16):
                if (x, y, z) not in t.blocks:
                    t.put(x, y, z, "air")
    # el camino de la puerta al salón
    for x in range(14, 40):
        for z in range(42, 46):
            t.put(x, 0, z, S("polished_blackstone_bricks") if z in (42, 45) else S("red_nether_bricks"))


def wart_garden(t, x0, z0, w=7, d=4):
    for x in range(x0 - 1, x0 + w + 1):
        for z in range(z0 - 1, z0 + d + 1):
            edge = x in (x0 - 1, x0 + w) or z in (z0 - 1, z0 + d)
            t.put(x, 0, z, BRICK if edge else S("soul_sand"))
            if edge:
                t.put(x, 1, z, slab("nether_brick"))
            else:
                t.put(x, 1, z, S("nether_wart", age=3))


def blaze_altar(t, cx, cz):
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 3, cz + 4):
            d = max(abs(x - cx), abs(z - cz))
            t.put(x, 1, z, BLACK if d == 3 else GILDED if d == 0 else S("polished_blackstone"))
            if d <= 2:
                t.put(x, 2, z, BLACK if d == 2 else S("polished_blackstone"))
    t.put(cx, 3, cz, S("spawner"), spawner("minecraft:blaze"))
    for (dx, dz) in ((3, 3), (-3, 3), (3, -3), (-3, -3)):
        for y in range(2, 5):
            t.put(cx + dx, y, cz + dz, S("polished_blackstone_brick_wall"))
        t.put(cx + dx, 5, cz + dz, S("soul_campfire", facing="north", lit="true", signal_fire="false", waterlogged="false"))
    for (dx, dz, f) in ((0, -3, "north"), (0, 3, "south"), (-3, 0, "west"), (3, 0, "east")):
        t.put(cx + dx, 1, cz + dz, stairs("polished_blackstone_brick", {"north": "south", "south": "north", "west": "east", "east": "west"}[f]))


def obelisk(t, x, z, h=7):
    t.put(x, 1, z, BLACK)
    for y in range(2, h):
        t.put(x, y, z, RED if y % 3 else GILDED)
    t.put(x, h, z, S("wither_skeleton_skull", rotation=4))


def cage(t, x, z):
    """Una jaula colgante con los huesos de un condenado."""
    for y in range(1, 5):
        for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            edge = True
            t.put(x + dx * 2, y, z + dz * 2, S("iron_bars"))
        t.put(x + 1, y, z, S("iron_bars"))
        t.put(x + 1, y, z + 2, S("iron_bars"))
        t.put(x, y, z + 1, S("iron_bars"))
        t.put(x + 2, y, z + 1, S("iron_bars"))
    t.put(x + 1, 1, z + 1, S("bone_block", axis="y"))
    t.put(x + 1, 2, z + 1, S("skeleton_skull", rotation=0))
    for dx in range(3):
        for dz in range(3):
            t.put(x + dx, 5, z + dz, BLACK)
    t.put(x + 1, 6, z + 1, S("chain", axis="y", waterlogged="false"))


def wall_stairs(t):
    """Escaleras del patio al adarve (junto a la puerta, al norte y al sur)."""
    for (z, step) in ((7, 1), (N - 8, -1)):
        for k in range(13):
            x = 16 + k
            for y in range(1, k + 2):
                t.put(x, y, z, BRICK)
            t.put(x, k + 1, z, stairs("nether_brick", "east"))
            t.put(x, k + 1, z + step, stairs("nether_brick", "east"))
            for y in range(1, k + 1):
                t.put(x, y, z + step, BRICK)


def barracks(t, x0, z0, x1, z1, top=9):
    """Cuartel y armería (al norte): armaduras de oro, cofres, yunques."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.put(x, 0, z, BLACK)
            for y in range(1, top + 1):
                t.put(x, y, z, wall_block(x, y, z) if edge or y == top else "air")
            if edge and (x + z) % 2 == 0:
                t.put(x, top + 1, z, S("polished_blackstone_brick_wall"))
    mid = (x0 + x1) // 2
    for y in (1, 2, 3):
        t.put(mid, y, z1, "air")
        t.put(mid + 1, y, z1, "air")
    for x in range(x0 + 2, x1 - 1, 3):
        t.put(x, 1, z0 + 1, *chest("south", LOOT_ARMORY if x % 2 else LOOT_BRIDGE))
        t.entity(armor_stand(head="golden_helmet", chest_="golden_chestplate", legs="golden_leggings", feet="golden_boots",
                             main="golden_sword" if x % 2 else "golden_axe"), x + 1, 1, z0 + 2, YAW["south"])
    t.put(x0 + 1, 1, z1 - 1, S("anvil", facing="east"))
    t.put(x1 - 1, 1, z1 - 1, S("smithing_table"))
    for x in range(x0 + 1, x1):
        t.put(x, top - 1, (z0 + z1) // 2, S("chain", axis="x", waterlogged="false")) if x % 4 == 0 else None
    t.put(mid, top - 1, (z0 + z1) // 2, S("soul_lantern", hanging="true", waterlogged="false"))
    t.put(mid, top, (z0 + z1) // 2, BLACK)


def dungeon(t, x0, z0, x1, z1, top=9):
    """Calabozos y laboratorio (al sur): celdas con rejas y huesos, telarañas, y el soporte de pociones."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.put(x, 0, z, BLACK)
            for y in range(1, top + 1):
                t.put(x, y, z, wall_block(x, y, z) if edge or y == top else "air")
            if edge and (x + z) % 2 == 0:
                t.put(x, top + 1, z, S("polished_blackstone_brick_wall"))
    mid = (x0 + x1) // 2
    for y in (1, 2, 3):
        t.put(mid, y, z0, "air")
        t.put(mid + 1, y, z0, "air")
    # celdas a lo largo del muro sur
    for x in range(x0 + 1, x1):
        for y in (1, 2, 3):
            t.put(x, y, z1 - 3, S("iron_bars") if (x - x0) % 4 else BLACK)
    for x in range(x0 + 2, x1 - 1, 4):
        t.put(x, 1, z1 - 1, S("bone_block", axis="x"))
        t.put(x + 1, 1, z1 - 2, S("skeleton_skull", rotation=8))
        t.put(x, 3, z1 - 1, S("cobweb"))
        t.put(x + 1, 2, z1 - 3, "air") if False else None
    # el laboratorio
    t.put(x0 + 2, 1, z0 + 2, S("brewing_stand", has_bottle_0="false", has_bottle_1="false", has_bottle_2="false"))
    t.put(x0 + 3, 1, z0 + 2, S("cauldron"))
    t.put(x0 + 4, 1, z0 + 2, *chest("south", LOOT_BRIDGE))
    t.put(x1 - 2, 1, z0 + 2, S("lectern", facing="south", has_book="false", powered="false"))
    t.put(mid, top - 1, (z0 + z1) // 2 - 1, S("soul_lantern", hanging="true", waterlogged="false"))


def throne_hall(t, x0=40, z0=28, x1=77, z1=59, top=18):
    """El gran salón del trono."""
    zc = (z0 + z1) // 2  # 43
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            t.put(x, 0, z, S("polished_blackstone") if (x + z) % 3 else BLACKSTONE)
            for y in range(1, top + 1):
                t.put(x, y, z, wall_block(x, y, z) if edge else "air")
            t.put(x, top, z, wall_block(x, top, z))
    # contrafuertes por fuera (norte y sur)
    for x in range(x0 + 3, x1, 6):
        for (z, dz) in ((z0 - 1, -1), (z1 + 1, 1)):
            for y in range(1, top - 2):
                t.put(x, y, z, RED)
            for y in range(1, top - 8):
                t.put(x, y, z + dz, RED)
            t.put(x, top - 2, z, stairs("red_nether_brick", "north" if dz > 0 else "south"))
    # tejado escalonado a dos aguas (la cumbrera a lo largo de x)
    half = (z1 - z0) // 2 + 1
    for k in range(half):
        y = top + 1 + k // 2
        if y >= H - 2:
            break
        for x in range(x0, x1 + 1):
            for z in (z0 + k, z1 - k):
                st = stairs("nether_brick", "south" if z == z0 + k else "north") if k % 2 == 0 else RED
                t.put(x, y, z, st)
    ridge_y = min(H - 2, top + 1 + half // 2)
    for x in range(x0, x1 + 1, 3):
        t.put(x, ridge_y, zc, S("nether_brick_fence"))
        t.put(x, ridge_y, zc + 1, S("nether_brick_fence"))
    # la entrada (oeste): arco de 4 de ancho y 7 de alto, con rejas arriba
    for z in range(zc - 1, zc + 3):
        for y in range(1, 8):
            t.put(x0, y, z, "air")
        for y in (6, 7):
            t.put(x0, y, z, S("iron_bars"))
    for z in (zc - 2, zc + 3):
        for y in range(1, 9):
            t.put(x0 - 1, y, z, RED)
        t.put(x0 - 1, 9, z, S("soul_lantern", hanging="false", waterlogged="false"))
    # vidrieras rojas
    for x in range(x0 + 3, x1 - 2, 6):
        for z in (z0, z1):
            for y in range(7, 13):
                t.put(x, y, z, S("red_stained_glass_pane"))
    # columnas
    for x in range(x0 + 4, x1 - 8, 6):
        for z in (z0 + 6, z1 - 7):
            for dx in (0, 1):
                for dz in (0, 1):
                    for y in range(1, top):
                        t.put(x + dx, y, z + dz, RED if y % 5 else GILDED)
    # canales de lava tras rejas, a lo largo de los muros
    for x in range(x0 + 2, x1 - 9):
        for z in (z0 + 2, z1 - 2):
            t.put(x, 0, z, S("lava"))
            t.put(x, 1, z, S("iron_bars"))
        for z in (z0 + 1, z1 - 1, z0 + 3, z1 - 3):
            t.put(x, 0, z, BLACK)
    # la alfombra roja hasta el trono
    for x in range(x0 + 1, x1 - 8):
        for z in (zc, zc + 1):
            t.put(x, 1, z, S("red_carpet"))
    # el estrado (tres escalones) y el trono
    dx0 = x1 - 9
    for step in range(3):
        for x in range(dx0 + step, x1):
            for z in range(zc - 5 + step, zc + 7 - step):
                t.put(x, 1 + step, z, BLACK if x > dx0 + step or z not in range(zc - 1, zc + 3) else stairs("polished_blackstone_brick", "west"))
    ty = 4
    tx = x1 - 3
    for z in (zc, zc + 1):
        t.put(tx, ty, z, stairs("blackstone", "west"))
    for z in (zc - 1, zc + 2):
        for y in (ty, ty + 1):
            t.put(tx, y, z, GILDED)
    for z in range(zc - 1, zc + 3):
        for y in range(ty, ty + 7):
            t.put(tx + 1, y, z, GILDED if (y + z) % 3 == 0 else S("crying_obsidian"))
    t.put(tx + 1, ty + 7, zc, S("wither_skeleton_skull", rotation=12))
    t.put(tx + 1, ty + 7, zc + 1, S("wither_skeleton_skull", rotation=12))
    for z in (zc - 3, zc + 4):
        t.put(x1 - 1, 9, z, S("red_wall_banner", facing="west"))
        t.put(x1 - 1, 8, z, "air")
    # el tesoro, detrás del trono
    t.put(x1 - 1, 4, zc - 3, *chest("west", LOOT_TREASURY))
    t.put(x1 - 1, 4, zc + 4, *chest("west", LOOT_TREASURY))
    t.put(x1 - 1, 4, zc - 4, S("gold_block"))
    t.put(x1 - 1, 4, zc + 5, S("gold_block"))
    # lámparas de almas colgadas del techo
    for x in range(x0 + 4, x1 - 8, 6):
        for z in (zc - 6, zc + 7):
            for y in range(top - 3, top):
                t.put(x, y, z, S("chain", axis="y", waterlogged="false"))
            t.put(x, top - 4, z, S("soul_lantern", hanging="true", waterlogged="false"))
    # el guardián: un mini jefe delante del estrado
    t.jigsaw(dx0 - 4, 0, zc, "up_north", f"{NS}:boss", f"{NS}:boss", f"{NS}:nether_citadel/boss", "minecraft:polished_blackstone", "rollable")
    return (dx0 - 4, zc)


def citadel():
    t = Template(N, H, N)
    moat_and_bridge(t)
    curtain_wall(t)
    gatehouse(t)
    for (cx, cz) in ((8, 8), (8, N - 9), (N - 9, 8), (N - 9, N - 9)):
        tower(t, cx, cz)
    # la puerta atraviesa la muralla
    for x in range(4, 14):
        for z in range(41, 47):
            for y in range(1, 8):
                t.put(x, y, z, "air")
    for (cx, cz) in ((8, 8), (8, N - 9), (N - 9, 8), (N - 9, N - 9)):
        # que la torre quede hueca aunque la muralla pase por ella
        for x in range(cx - 3, cx + 4):
            for z in range(cz - 3, cz + 4):
                for y in range(1, 13):
                    if (x - cx) ** 2 + (z - cz) ** 2 <= 10:
                        t.put(x, y, z, "air")
        for y in range(1, 25):
            t.put(cx, y, cz - 4, S("ladder", facing="south", waterlogged="false"))
        for y in (1, 2):
            dx = 4 if cx < N // 2 else -4
            t.put(cx + dx, y, cz, "air")
    throne_hall(t)
    barracks(t, 22, 10, 38, 22)
    dungeon(t, 22, 65, 38, 77)
    wall_stairs(t)
    courtyard_floor(t)
    for (x, z) in ((44, 10), (56, 10), (44, 70), (56, 70)):
        wart_garden(t, x, z)
    blaze_altar(t, 28, 33)
    blaze_altar(t, 28, 55)
    for x in range(16, 38, 6):
        obelisk(t, x, 40)
        obelisk(t, x, 47)
    cage(t, 66, 12)
    cage(t, 66, 72)
    cage(t, 16, 26)
    cage(t, 16, 60)
    return t


BOSSES = [("legion", 40), ("beelzebub", 30), ("moloch", 20), ("abaddon", 10)]


def boss_piece(bid):
    t = Template(1, 2, 1)
    t.jigsaw(0, 0, 0, "down_south", f"{NS}:boss", f"{NS}:boss", "minecraft:empty")
    t.entity({"id": f"{NS}:{bid}", "PersistenceRequired": nbt.Byte(1)}, 0, 0, 0, YAW["west"])
    return t


def element(location):
    return {"element_type": "minecraft:single_pool_element", "location": location, "processors": {"processors": []}, "projection": "rigid"}


def loot(pools):
    return {"type": "minecraft:chest", "pools": pools}


def entry(name, lo, hi, weight=1):
    return {"type": "minecraft:item", "name": name, "weight": weight,
            "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False}]}


LOOTS = {
    "citadel_armory": loot([
        {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "bonus_rolls": 0, "entries": [
            entry("minecraft:gold_ingot", 2, 7, 10), entry("minecraft:iron_ingot", 2, 6, 8), entry("minecraft:arrow", 6, 18, 8),
            entry("minecraft:golden_sword", 1, 1, 4), entry("minecraft:golden_chestplate", 1, 1, 3), entry("minecraft:golden_horse_armor", 1, 1, 2),
            entry("minecraft:blaze_rod", 1, 3, 5), entry("minecraft:nether_wart", 2, 6, 5), entry("%s:brimstone" % NS, 2, 6, 6),
            entry("minecraft:obsidian", 1, 4, 3), entry("minecraft:saddle", 1, 1, 2)]}]),
    "citadel_treasury": loot([
        {"rolls": {"type": "minecraft:uniform", "min": 4, "max": 7}, "bonus_rolls": 0, "entries": [
            entry("minecraft:diamond", 1, 4, 8), entry("minecraft:gold_block", 1, 2, 6), entry("minecraft:emerald", 2, 6, 6),
            entry("minecraft:netherite_scrap", 1, 2, 3), entry("minecraft:ancient_debris", 1, 2, 2), entry("minecraft:golden_apple", 1, 2, 4),
            entry("minecraft:enchanted_golden_apple", 1, 1, 1), entry("minecraft:blaze_rod", 2, 5, 5), entry("minecraft:ghast_tear", 1, 3, 4),
            entry("minecraft:netherite_upgrade_smithing_template", 1, 1, 2)]},
        {"rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.5}], "entries": [
            {"type": "minecraft:item", "name": "minecraft:book",
             "functions": [{"function": "minecraft:set_enchantments", "enchantments": {"%s:divine_blessing" % NS: 1}}]}]},
        {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "minecraft:diamond_sword", "functions": [
            {"function": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": 20, "max": 39},
             "options": "#minecraft:on_random_loot"}]}],
         "conditions": [{"condition": "minecraft:random_chance", "chance": 0.35}]},
    ]),
}

STRUCTURE = {
    "type": "minecraft:jigsaw",
    "biomes": f"#{NS}:has_structure/nether_citadel",
    "max_distance_from_center": 64,
    "project_start_to_heightmap": "WORLD_SURFACE_WG",
    "size": 2,
    "start_height": {"absolute": 0},
    "start_pool": f"{NS}:nether_citadel/base",
    "step": "surface_structures",
    "terrain_adaptation": "beard_box",
    "use_expansion_hack": False,
    "spawn_overrides": {"monster": {"bounding_box": "full", "spawns": [
        {"type": "minecraft:blaze", "weight": 10, "minCount": 2, "maxCount": 3},
        {"type": "minecraft:wither_skeleton", "weight": 9, "minCount": 2, "maxCount": 4},
        {"type": "minecraft:zombified_piglin", "weight": 4, "minCount": 2, "maxCount": 4},
        {"type": "minecraft:skeleton", "weight": 2, "minCount": 2, "maxCount": 3},
        {"type": "minecraft:magma_cube", "weight": 3, "minCount": 2, "maxCount": 4},
        {"type": f"{NS}:imp", "weight": 4, "minCount": 1, "maxCount": 3},
        {"type": f"{NS}:demon", "weight": 2, "minCount": 1, "maxCount": 1}]}},
}

# el conjunto de estructuras del Nether de vanilla: la fortaleza deja su sitio a la ciudadela (los bastiones siguen)
NETHER_COMPLEXES = {"placement": {"type": "minecraft:random_spread", "salt": 30084232, "spacing": 27, "separation": 4},
                    "structures": [{"structure": f"{NS}:nether_citadel", "weight": 2}, {"structure": "minecraft:bastion_remnant", "weight": 3}]}


def main():
    t = citadel()
    base = os.path.join(DATA, "structure/nether_citadel")
    t.save(os.path.join(base, "citadel.nbt"))
    for bid, _ in BOSSES:
        boss_piece(bid).save(os.path.join(base, "boss_%s.nbt" % bid))
    pools = os.path.join(DATA, "worldgen/template_pool/nether_citadel")
    write_json(os.path.join(pools, "base.json"), {"fallback": "minecraft:empty", "elements": [
        {"weight": 1, "element": element(f"{NS}:nether_citadel/citadel")}]})
    write_json(os.path.join(pools, "boss.json"), {"fallback": "minecraft:empty", "elements": [
        {"weight": w, "element": element(f"{NS}:nether_citadel/boss_{bid}")} for bid, w in BOSSES]})
    write_json(os.path.join(DATA, "worldgen/structure/nether_citadel.json"), STRUCTURE)
    write_json(os.path.join(MC_DATA, "worldgen/structure_set/nether_complexes.json"), NETHER_COMPLEXES)
    write_json(os.path.join(DATA, "tags/worldgen/biome/has_structure/nether_citadel.json"), {"replace": False, "values": ["#minecraft:is_nether"]})
    for name, l in LOOTS.items():
        write_json(os.path.join(DATA, "loot_table/chests/%s.json" % name), l)
    blocks = len(t.blocks)
    print("ciudadela infernal: %dx%dx%d, %d bloques, %d criaturas en la plantilla" % (N, H, N, blocks, len(t.entities)))
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        colors = {"lava": (230, 90, 20), "air": None}
        im = Image.new("RGB", (N, N), (30, 30, 30))
        tops = {}
        for (x, y, z), (st, _) in t.blocks.items():
            name = st[0].split(":")[1]
            if name in ("air", "structure_void"):
                continue
            if (x, z) not in tops or y > tops[(x, z)][0]:
                tops[(x, z)] = (y, name)
        for (x, z), (y, name) in tops.items():
            base = {"lava": (230, 90, 20), "nether_bricks": (90, 30, 34), "red_nether_bricks": (120, 20, 24), "soul_sand": (80, 60, 50),
                    "nether_wart": (160, 30, 30), "red_carpet": (190, 30, 30), "gilded_blackstone": (180, 140, 50)}.get(name, (60, 56, 64))
            f = 0.5 + y / H
            im.putpixel((x, z), tuple(min(255, int(c * f)) for c in base))
        im.resize((N * 6, N * 6), Image.NEAREST).save(os.path.join(PREVIEW, "citadel_plan.png"))


if __name__ == "__main__":
    main()
