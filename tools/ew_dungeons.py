"""Las mazmorras (fase 66 en adelante): estructuras grandes, de superficie y subterráneas, con generadores de monstruos,
trampas, salas secretas, botín y un mini jefe al final (un monstruo vanilla potenciado que despierta el Corazón de la
mazmorra y que encierra al jugador tras unas rejas hasta que cae).

Cada mazmorra sale de una semilla: se generan varias variantes (laberintos, salas y ruinas distintas) y el juego elige
una al azar y la gira. Las coordenadas de diseño: (0, 0, 0) es la entrada, a ras de suelo (y = 0 es la primera capa de
aire sobre el terreno); la mazmorra se adentra hacia +z. El juego coloca (0, 0) de diseño sobre el punto elegido.

Uso: python3 tools/ew_dungeons.py [raíz del mod] [--preview DIR]
"""
import collections
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
import nbt  # noqa: E402
from ew_structlib import S, Template, chest, is_full, short, slab, stairs, state_str  # noqa: E402

NS = "evolvedwarfare"
ROOT = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else os.path.join(os.path.dirname(__file__), "..")
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
VARIANTS = 3

AIR = S("air")
HEART = S(NS + ":dungeon_heart")
GATE = S(NS + ":dungeon_gate", closed="false")
DIR = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}


def dir_of(dx, dz):
    for d, v in DIR.items():
        if v == (dx, dz):
            return d
    raise ValueError((dx, dz))


class Pal:
    """Paleta: estados con pesos (para muros con musgo, grietas...)."""

    def __init__(self, *items):
        self.items = [(S(n) if isinstance(n, str) else n, w) for n, w in items]
        self.total = sum(w for _, w in self.items)

    def pick(self, rnd):
        r = rnd.random() * self.total
        for st, w in self.items:
            r -= w
            if r < 0:
                return st
        return self.items[-1][0]


def P(*names):
    return Pal(*[(n, 1) for n in names])


STONE_BRICK = Pal(("stone_bricks", 6), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 2), ("andesite", 0.4))
DEEP_BRICK = Pal(("deepslate_bricks", 5), ("cracked_deepslate_bricks", 2), ("deepslate_tiles", 2), ("cobbled_deepslate", 1))
SAND_BRICK = Pal(("sandstone", 4), ("smooth_sandstone", 2), ("cut_sandstone", 3))
CAVE = Pal(("stone", 5), ("tuff", 2), ("cobblestone", 2), ("mossy_cobblestone", 1), ("andesite", 1))
BLACK_BRICK = Pal(("polished_blackstone_bricks", 5), ("cracked_polished_blackstone_bricks", 2), ("blackstone", 1), ("deepslate_tiles", 1))


class B:
    """Un plano de mazmorra en coordenadas de diseño (sin límites); luego se pasa a una plantilla."""

    def __init__(self, seed, name):
        self.rnd = random.Random(seed)
        self.name = name
        self.blocks = {}          # (x,y,z) -> (estado, nbt)
        self.marks = {"spawner": [], "chest": [], "trap": [], "secret": [], "gate": [], "heart": []}
        self.start = (0, 0, -2)   # dónde aparece el jugador para la prueba de recorrido

    # ---- poner bloques
    def put(self, x, y, z, st, be=None):
        if isinstance(st, Pal):
            st = st.pick(self.rnd)
        elif isinstance(st, str):
            st = S(st)
        self.blocks[(x, y, z)] = (st, be)

    def soft(self, x, y, z, st):
        """Solo si no hay nada (los muros no tapan lo ya excavado)."""
        if (x, y, z) not in self.blocks:
            self.put(x, y, z, st)

    def get(self, x, y, z):
        v = self.blocks.get((x, y, z))
        return v[0] if v else None

    def is_air(self, x, y, z):
        v = self.get(x, y, z)
        return v is not None and short(v) == "air"

    def fill(self, x0, y0, z0, x1, y1, z1, st, soft=False):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    (self.soft if soft else self.put)(x, y, z, st)

    def air(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, AIR)

    def room(self, x0, y0, z0, x1, y1, z1, wall, floor=None, ceil=None):
        """Caja con muros (blandos) y el hueco (duro): (x0..x1, y0..y1, z0..z1) es el HUECO; los muros van alrededor."""
        floor = floor or wall
        ceil = ceil or wall
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                self.soft(x, y0 - 1, z, floor)
                self.soft(x, y1 + 1, z, ceil)
                for y in range(y0, y1 + 1):
                    if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                        self.soft(x, y, z, wall)
        self.air(x0, y0, z0, x1, y1, z1)

    def corridor(self, a, b, y, wall, width=3, height=4, floor=None, ceil=None):
        """Pasillo en L de a=(x,z) a b=(x,z), a la altura y (suelo en y-1); primero en x y luego en z."""
        h = width // 2
        (ax, az), (bx, bz) = a, b
        if ax != bx:
            self.room(min(ax, bx) - h, y, az - h, max(ax, bx) + h, y + height - 1, az + h, wall, floor, ceil)
        if az != bz:
            self.room(bx - h, y, min(az, bz) - h, bx + h, y + height - 1, max(az, bz) + h, wall, floor, ceil)

    # ---- cosas de mazmorra
    def spawner(self, x, y, z, mob, count=3, near=6):
        be = {"id": "minecraft:mob_spawner", "SpawnData": {"entity": {"id": mob}}, "Delay": nbt.Short(40),
              "MinSpawnDelay": nbt.Short(200), "MaxSpawnDelay": nbt.Short(600), "SpawnCount": nbt.Short(count),
              "MaxNearbyEntities": nbt.Short(near), "RequiredPlayerRange": nbt.Short(16), "SpawnRange": nbt.Short(4)}
        self.put(x, y, z, S("spawner"), be)
        self.marks["spawner"].append((x, y, z))

    def chest(self, x, y, z, facing, table, kind="chest"):
        st, be = chest(facing, f"{NS}:chests/dungeon/{self.name}/{table}")
        if kind == "barrel":
            st = S("barrel", facing="up", open="false")
            be["id"] = "minecraft:barrel"
        self.put(x, y, z, st, be)
        self.marks["chest"].append((x, y, z))

    def heart(self, x, y, z):
        self.put(x, y, z, HEART, {"id": f"{NS}:dungeon_heart", "Dungeon": self.name})
        self.marks["heart"].append((x, y, z))

    def gate(self, x, y, z):
        self.put(x, y, z, GATE)
        self.marks["gate"].append((x, y, z))

    def arrow_trap(self, x, y, z, toward):
        """Placa de presión en (x,y,z) y un dispensador con flechas en la pared de al lado, disparando hacia 'toward'."""
        dx, dz = DIR[toward]
        self.put(x, y, z, S("stone_pressure_plate", powered="false"))
        self.put(x - dx, y, z - dz, S("dispenser", facing=toward, triggered="false"), {"id": "minecraft:dispenser", "Items": nbt.List(
            nbt.COMPOUND, [{"Slot": nbt.Byte(0), "id": "minecraft:arrow", "count": 24}])})
        self.marks["trap"].append((x, y, z))

    def torch(self, x, y, z, facing, soul=False):
        self.put(x, y, z, S("soul_wall_torch" if soul else "wall_torch", facing=facing))

    def lantern(self, x, y, z, hanging=True, soul=False):
        self.put(x, y, z, S("soul_lantern" if soul else "lantern", hanging=hanging, waterlogged="false"))

    def candles(self, x, y, z, color=None, n=None):
        n = n or self.rnd.randint(1, 4)
        self.put(x, y, z, S(f"{color}_candle" if color else "candle", candles=n, lit="true", waterlogged="false"))

    def web(self, x, y, z):
        if self.is_air(x, y, z):
            self.put(x, y, z, S("cobweb"))

    def webs(self, x0, y0, z0, x1, y1, z1, chance):
        """Telarañas en los rincones (aire pegado a dos muros o más)."""
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    if not self.is_air(x, y, z):
                        continue
                    solid = sum(1 for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, 0, 1), (0, 0, -1))
                                if is_full(self.get(x + d[0], y + d[1], z + d[2])))
                    if solid >= 2 and self.rnd.random() < chance:
                        self.web(x, y, z)

    def skull(self, x, y, z, kind="skeleton", rot=None):
        self.put(x, y, z, S(f"{kind}_skull", rotation=self.rnd.randint(0, 15) if rot is None else rot, powered="false"))

    # ---- a plantilla
    def template(self):
        xs = [p[0] for p in self.blocks]
        ys = [p[1] for p in self.blocks]
        zs = [p[2] for p in self.blocks]
        mx, my, mz = min(xs), min(ys), min(zs)
        t = Template(max(xs) - mx + 1, max(ys) - my + 1, max(zs) - mz + 1)
        for (x, y, z), (st, be) in self.blocks.items():
            t.put(x - mx, y - my, z - mz, st, be)
        return t, (-mx, -my, -mz)


# ---------------------------------------------------------------------------------------------- recorrido
PASS = ("air", "cobweb", "torch", "wall_torch", "soul_wall_torch", "lantern", "soul_lantern", "candle", "pressure_plate", "carpet",
        "skull", "head", "rail", "dungeon_gate", "vine", "ladder", "button", "lever", "chain", "dead_bush", "short_grass", "fern",
        "snow", "sign", "banner", "redstone_wire", "tripwire", "glow_lichen", "cave_vines", "moss_carpet", "flower_pot", "bones",
        "amethyst_cluster", "pointed_dripstone", "sculk_vein", "torchflower", "mushroom", "frogspawn")


def passable(st):
    if st is None:
        return True
    n = short(st)
    return n == "air" or any(p in n for p in PASS[1:])


def standable(st):
    if st is None:
        return False
    n = short(st)
    return is_full(st) or n.endswith("_slab") or n.endswith("_stairs") or n in ("spawner", "chest", "barrel", "dungeon_heart", "anvil",
                                                                                  "dirt_path", "farmland", "soul_sand", "mud")


def walk(b, start, goals, ground=None, sealed=False):
    """¿Se llega andando (saltando 1 y cayendo hasta 4) de start a alguno de goals (bloques junto a los que estar)?
    ground: y por debajo de la cual lo que no está en el plano es tierra maciza (superficie); None = subterránea (todo
    lo que no está es roca)."""

    def cell(x, y, z):
        st = b.get(x, y, z)
        if sealed and st is not None and short(st) == "dungeon_gate":
            return S("iron_block")
        if st is None:
            if ground is None or y < ground:
                return S("stone")
            return None
        return st

    def ok(x, y, z):
        here, head = cell(x, y, z), cell(x, y + 1, z)
        if not (passable(here) and passable(head)):
            return False
        below = cell(x, y - 1, z)
        return standable(below) or (here is not None and "ladder" in short(here))

    sx, sy, sz = start
    while not ok(sx, sy, sz) and sy > -200:
        sy -= 1
    seen = {(sx, sy, sz)}
    todo = collections.deque([(sx, sy, sz)])
    targets = set()
    for gx, gy, gz in goals:
        for dx, dz in DIR.values():
            for dy in (-1, 0, 1):
                targets.add((gx + dx, gy + dy, gz + dz))
        targets.add((gx, gy + 1, gz))
    while todo:
        x, y, z = todo.popleft()
        if (x, y, z) in targets:
            return True
        cur = cell(x, y, z)
        nexts = []
        if cur is not None and "ladder" in short(cur):
            nexts += [(x, y + 1, z), (x, y - 1, z)]
        for dx, dz in DIR.values():
            nx, nz = x + dx, z + dz
            for ny in (y + 1, y, y - 1, y - 2, y - 3, y - 4):
                if ny == y + 1 and not passable(cell(x, y + 2, z)):
                    continue
                if ok(nx, ny, nz):
                    # para bajar, el hueco de encima tiene que estar libre
                    if ny < y and not all(passable(cell(nx, k, nz)) for k in range(ny + 2, y + 2)):
                        break
                    nexts.append((nx, ny, nz))
                    break
                if ny <= y and not passable(cell(nx, ny, nz)):
                    break
        for n in nexts:
            if n not in seen and len(seen) < 400000:
                seen.add(n)
                todo.append(n)
    return False


# ---------------------------------------------------------------------------------------------- laberintos
def maze(rnd, w, h, start, loops=0.2):
    """Árbol de expansión aleatorio en una rejilla w x h (más algunos lazos): devuelve el conjunto de aristas."""
    edges = set()
    seen = {start}
    stack = [start]
    while stack:
        c = stack[-1]
        ns = [(c[0] + dx, c[1] + dz) for dx, dz in DIR.values()]
        ns = [n for n in ns if 0 <= n[0] < w and 0 <= n[1] < h and n not in seen]
        if not ns:
            stack.pop()
            continue
        n = rnd.choice(ns)
        edges.add(frozenset((c, n)))
        seen.add(n)
        stack.append(n)
    for x in range(w):
        for z in range(h):
            for n in ((x + 1, z), (x, z + 1)):
                if n[0] < w and n[1] < h and rnd.random() < loops:
                    edges.add(frozenset(((x, z), n)))
    return edges


def distances(edges, start):
    adj = collections.defaultdict(list)
    for e in edges:
        a, b = tuple(e)
        adj[a].append(b)
        adj[b].append(a)
    d = {start: 0}
    q = collections.deque([start])
    while q:
        c = q.popleft()
        for n in adj[c]:
            if n not in d:
                d[n] = d[c] + 1
                q.append(n)
    return d, adj


# ---------------------------------------------------------------------------------------------- piezas comunes
def secret_room(b, wall_pal, loot="secret", tries=200, y=None, levels=None):
    """Una sala escondida detrás de un muro agrietado, junto a un pasillo: busca un trozo de muro con roca virgen
    detrás. Devuelve True si cupo."""
    r = b.rnd
    cands = []
    for (x, yy, z), (st, be) in b.blocks.items():
        if short(st) != "air" or (levels and yy not in levels) or (y is not None and yy != y):
            continue
        if not standable(b.get(x, yy - 1, z)):
            continue
        for d, (dx, dz) in DIR.items():
            wx, wz = x + dx, z + dz
            if b.get(wx, yy, wz) is not None and is_full(b.get(wx, yy, wz)) and is_full(b.get(wx, yy + 1, wz)):
                cands.append((wx, yy, wz, d))
    r.shuffle(cands)
    for wx, yy, wz, d in cands[:tries]:
        dx, dz = DIR[d]
        px, pz = -dz, dx
        # la sala: 3 de fondo, 5 de ancho, 3 de alto, detrás del muro
        cells = [(wx + dx * k + px * s, yy + h, wz + dz * k + pz * s) for k in range(1, 6) for s in range(-3, 4) for h in range(-1, 5)]
        if any(c in b.blocks for c in cells):
            continue
        for k in range(1, 6):
            for s in range(-3, 4):
                for h in range(-1, 5):
                    edge = k in (1, 5) or s in (-3, 3) or h in (-1, 4)
                    b.put(wx + dx * k + px * s, yy + h, wz + dz * k + pz * s, wall_pal if edge else AIR)
        b.put(wx, yy, wz, S("cracked_stone_bricks") if "stone" in str(wall_pal.items[0][0]) else wall_pal)
        b.put(wx + dx, yy, wz + dz, AIR)
        b.put(wx + dx, yy + 1, wz + dz, AIR)
        b.put(wx + dx * 3, yy, wz + dz * 3, *chest_args(b, OPP[d]))
        b.marks["chest"].append((wx + dx * 3, yy, wz + dz * 3))
        b.marks["secret"].append((wx, yy, wz))
        b.skull(wx + dx * 2 + px, yy, wz + dz * 2 + pz)
        b.candles(wx + dx * 4 - px, yy, wz + dz * 4 - pz)
        b.put(wx + dx * 4 + px * 2, yy, wz + dz * 4 + pz * 2, S("gold_block") if r.random() < 0.5 else S("bone_block"))
        return True
    return False


def chest_args(b, facing):
    return chest(facing, f"{NS}:chests/dungeon/{b.name}/secret")


def niches(b, y, chance, contents, region=None):
    """Nichos en los muros de los pasillos (a la altura y): se cambia el bloque del muro por lo que guarda."""
    for (x, yy, z), (st, be) in list(b.blocks.items()):
        if yy != y or short(st) != "air":
            continue
        if region and not region(x, z):
            continue
        for d, (dx, dz) in DIR.items():
            w = b.get(x + dx, y, z + dz)
            if w is not None and is_full(w) and b.get(x + dx * 2, y, z + dz * 2) is None and b.rnd.random() < chance:
                kind = b.rnd.choice(contents)
                if kind == "skull":
                    b.put(x + dx, y, z + dz, S("skeleton_skull", rotation=b.rnd.randint(0, 15), powered="false"))
                elif kind == "candle":
                    b.put(x + dx, y, z + dz, S("candle", candles=b.rnd.randint(1, 3), lit="true", waterlogged="false"))
                elif kind == "web":
                    b.put(x + dx, y, z + dz, S("cobweb"))
                elif kind == "empty":
                    b.put(x + dx, y, z + dz, AIR)
                else:
                    b.put(x + dx, y, z + dz, S(kind) if kind != "bone_block" else S("bone_block", axis=b.rnd.choice("xz")))


def place_traps(b, n, y, avoid=()):
    """Trampas de flechas en pasillos rectos: placa junto al muro y dispensador en el muro."""
    cands = []
    for (x, yy, z), (st, be) in b.blocks.items():
        if yy != y or short(st) != "air" or not standable(b.get(x, y - 1, z)):
            continue
        for d, (dx, dz) in DIR.items():
            # el muro al lado, roca detrás, y el pasillo sigue a lo largo (no es una esquina)
            px, pz = -dz, dx
            behind = b.get(x + 2 * dx, y, z + 2 * dz)
            if (is_full(b.get(x + dx, y, z + dz)) and (behind is None or is_full(behind)) and b.is_air(x - dx, y, z - dz)
                    and b.is_air(x + px, y, z + pz) and b.is_air(x - px, y, z - pz)):
                cands.append((x, z, OPP[d]))
    b.rnd.shuffle(cands)
    placed = []
    for x, z, toward in cands:
        if len(placed) >= n:
            break
        if any(abs(x - a) + abs(z - c) < 10 for a, c in placed + list(avoid)):
            continue
        b.arrow_trap(x, y, z, toward)
        placed.append((x, z))
    return placed


# ---------------------------------------------------------------------------------------------- 1. el mausoleo
def mausoleum(seed):
    """El Mausoleo del Rey Olvidado: un panteón en un cementerio y, debajo, unas catacumbas en laberinto (distinto en cada
    variante) con osarios, criptas y capillas; al fondo, la sala del trono del Rey."""
    b = B(seed, "forgotten_mausoleum")
    r = b.rnd
    W = STONE_BRICK
    b.start = (0, 0, -16)
    # --- la explanada (tierra removida) y el cementerio
    ground = Pal(("coarse_dirt", 4), ("podzol", 2), ("rooted_dirt", 1), ("grass_block", 3), ("gravel", 0.6))
    for x in range(-13, 14):
        for z in range(-17, 18):
            edge = max(abs(x) - 11, -z - 15, z - 15, 0)
            if edge > 0 and r.random() < 0.5:
                continue
            b.put(x, -1, z, ground)
            for y in range(-4, -1):
                b.soft(x, y, z, S("dirt"))
            b.air(x, 0, z, x, 9, z)
    for z in range(-17, -4):
        for x in range(-1, 2):
            b.put(x, -1, z, Pal(("gravel", 2), ("cobblestone", 2), ("mossy_cobblestone", 1)))
    # la verja (rota a trozos)
    for x in range(-12, 13):
        for z in (-16, 16):
            if abs(x) > 1 or z == 16:
                if r.random() < 0.8:
                    b.put(x, 0, z, W)
                    if r.random() < 0.7:
                        b.put(x, 1, z, S("iron_bars"))
    for z in range(-16, 17):
        for x in (-12, 12):
            if r.random() < 0.8:
                b.put(x, 0, z, W)
                if r.random() < 0.7:
                    b.put(x, 1, z, S("iron_bars"))
    for x in (-2, 2):
        b.fill(x, 0, -16, x, 2, -16, S("chiseled_stone_bricks"))
        b.lantern(x, 3, -16, hanging=False, soul=True)
    # tumbas
    for side in (-1, 1):
        for z in range(-13, 15, 3):
            for k, xx in enumerate((4, 7, 10)):
                x = side * xx
                if (abs(x) < 7 and z > -6) or r.random() < 0.25:
                    continue
                stone = r.choice(["cobblestone_wall", "mossy_cobblestone_wall", "stone_brick_wall", "mossy_stone_brick_wall"])
                b.put(x, 0, z, S(stone, up="true"))
                if r.random() < 0.4:
                    b.put(x, 1, z, S(r.choice(["stone_brick_slab", "cobblestone_slab"]), type="bottom", waterlogged="false"))
                b.put(x, -1, z + 1, S("coarse_dirt"))
                b.put(x, -1, z + 2, S("coarse_dirt"))
                if r.random() < 0.3:
                    b.put(x, 0, z + 1, S("dead_bush"))
                elif r.random() < 0.2:
                    b.candles(x, 0, z + 1, None, 1)
    # --- el panteón: x -6..6, z -4..12 (el pórtico hasta z = -9)
    b.fill(-7, -1, -10, 7, -1, 13, Pal(("polished_andesite", 3), ("andesite", 1), ("stone_bricks", 1)))
    b.room(-5, 0, -3, 5, 6, 11, W, floor=P("polished_andesite", "smooth_stone"))
    for z in range(-3, 12):
        for x in range(-5, 6):
            b.put(x, -1, z, S("polished_andesite") if (x + z) % 2 else S("smooth_stone"))
    for x in (-6, 6):
        for z in (-4, 0, 4, 8, 12):
            b.fill(x, 0, z, x, 7, z, S("chiseled_stone_bricks"))
        for z in (2, 6, 10):
            b.fill(x, 2, z, x, 4, z, S("iron_bars"))
    for x in range(-6, 7):
        b.put(x, 7, -4, W)
    b.air(-1, 0, -4, 1, 3, -4)
    b.put(-1, 3, -4, stairs("stone_brick", "west", "top"))
    b.put(1, 3, -4, stairs("stone_brick", "east", "top"))
    # el pórtico: columnas y techo
    for x in (-5, -2, 2, 5):
        b.put(x, 0, -8, S("chiseled_stone_bricks"))
        b.fill(x, 1, -8, x, 6, -8, S("polished_andesite") if r.random() < 0.85 else AIR)
        b.put(x, 6, -8, S("chiseled_stone_bricks"))
    b.fill(-6, 7, -9, 6, 7, -5, W)
    # tejado a dos aguas (cumbrera a lo largo de z) y frontones
    for k in range(8):
        for z in range(-10, 14):
            if k < 7:
                b.put(-7 + k, 7 + k, z, stairs("stone_brick", "east"))
                b.put(7 - k, 7 + k, z, stairs("stone_brick", "west"))
            else:
                b.put(0, 14, z, slab("stone_brick", "bottom"))
        for z in (-9, -4, 12):
            for x in range(-6 + k, 7 - k):
                b.put(x, 8 + k, z, W)
    b.put(0, 13, -10, S("chiseled_stone_bricks"))
    # dentro: sarcófagos, velas y faroles
    for x in (-4, 4):
        for z in (-1, 3, 7):
            for dz in (0, 1):
                b.put(x, 0, z + dz, S("polished_deepslate"))
                if r.random() < 0.8:
                    b.put(x, 1, z + dz, slab("smooth_stone", "bottom"))
            if r.random() < 0.6:
                b.candles(x, 1 if b.get(x, 1, z) is None else 2, z, None)
    for z in (0, 6):
        b.put(0, 6, z, S("chain", axis="y"))
        b.lantern(0, 5, z, soul=True)
    b.put(0, 0, 11, S("chiseled_stone_bricks"))
    b.skull(0, 1, 11, "wither_skeleton", 8)
    # --- la escalera a las catacumbas: x -1..1, de z = 2 (y -1) a z = 11 (y -10)
    for k in range(10):
        z = 2 + k
        b.room(-1, -k, z, 1, -1 if k else 2, z, W)
        b.fill(-1, -1 - k, z, 1, -1 - k, z, stairs("stone_brick", "north"))
        b.air(-1, -k, z, 1, -1, z)
    for z in range(3, 12):
        b.put(-2, 0, z, S("stone_brick_wall", up="true"))
        b.put(2, 0, z, S("stone_brick_wall", up="true"))
    b.corridor((0, 12), (0, 16), -10, W)
    # --- las catacumbas: rejilla de 7 x 5 celdas, cada 7 bloques, a y = -10
    Y = -10
    GW, GH = 7, 5
    cell = {(i, j): ((i - 3) * 8, 18 + j * 8) for i in range(GW) for j in range(GH)}
    edges = maze(r, GW, GH, (3, 0), 0.12)
    dist, adj = distances(edges, (3, 0))
    for e in edges:
        a, c = tuple(e)
        b.corridor(cell[a], cell[c], Y, W)
    # la sala del trono, detrás de la celda más lejana de la última fila
    back = max((c for c in cell if c[1] == GH - 1), key=lambda c: dist[c])
    bx, bz = cell[back]
    b.corridor((bx, bz + 2), (bx, bz + 6), Y, W)
    z0, z1 = bz + 7, bz + 25
    b.room(bx - 9, -11, z0, bx + 9, -3, z1, Pal(("deepslate_bricks", 4), ("cracked_deepslate_bricks", 2), ("deepslate_tiles", 2)),
           floor=P("polished_deepslate", "deepslate_tiles"))
    b.fill(bx - 1, -11, z0, bx + 1, -11, z0, stairs("deepslate_brick", "north"))
    for x in range(bx - 1, bx + 2):
        for y in range(Y, Y + 4):
            b.gate(x, y, bz + 5)
    for px in (bx - 5, bx + 4):
        for pz in (z0 + 4, z0 + 11):
            b.fill(px, -11, pz, px + 1, -3, pz + 1, S("polished_deepslate"))
            if r.random() < 0.6:
                b.torch(px, -9, pz - 1, "north", soul=True)
            b.put(px, -4, pz, S("chiseled_deepslate"))
    for x in range(bx - 4, bx + 5):
        b.put(x, -11, z1 - 1, S("polished_blackstone_bricks"))
        b.put(x, -11, z1, S("polished_blackstone_bricks"))
    b.put(bx, -10, z1, stairs("polished_blackstone", "south"))
    b.fill(bx - 1, -10, z1 + 1, bx + 1, -7, z1 + 1, S("chiseled_polished_blackstone"), soft=False)
    b.skull(bx, -6, z1 + 1, "wither_skeleton", 8)
    for x in (bx - 1, bx + 1):
        b.put(x, -10, z1, stairs("polished_blackstone", "south"))
    for z in range(z0 + 1, z1 - 7):
        b.put(bx, -10, z, S("red_carpet"))
    b.heart(bx, -11, z1 - 6)
    for x in (bx - 7, bx + 7):
        for z in range(z0 + 2, z1 - 1, 4):
            b.candles(x, -10, z)
    for x in range(bx - 6, bx + 7, 6):
        b.put(x, -4, z0 + 8, S("chain", axis="y"))
        b.lantern(x, -5, z0 + 8, soul=True)
    for k in range(10):
        x, z = bx + r.randint(-8, 8), r.randint(z0 + 2, z1 - 3)
        if r.random() < 0.6 and b.is_air(x, -10, z) and abs(x - bx) > 1:
            b.skull(x, -10, z)
    # --- las salas: en los callejones sin salida, osarios, criptas y capillas
    deads = [c for c in cell if len(adj[c]) == 1 and c not in ((3, 0), back)]
    r.shuffle(deads)
    others = [c for c in cell if c not in deads and c not in ((3, 0), back)]
    r.shuffle(others)
    kinds = ["ossuary", "crypt", "chapel", "ossuary", "crypt", "collapsed", "chapel"]
    spawners = 0
    for n, c in enumerate(deads + others[:2]):
        cx, cz = cell[c]
        b.room(cx - 2, Y, cz - 2, cx + 2, Y + 3, cz + 2, W)
        kind = kinds[n % len(kinds)] if n < len(deads) else "corner"
        (door,) = adj[c] if len(adj[c]) == 1 else (None,)
        if kind == "ossuary":
            for x in range(cx - 2, cx + 3):
                for z in range(cz - 2, cz + 3):
                    if (abs(x - cx) == 2 or abs(z - cz) == 2) and r.random() < 0.5 and not near_door(c, door, x, z, cell):
                        b.put(x, Y, z, S("bone_block", axis=r.choice("xyz")))
                        if r.random() < 0.4:
                            b.skull(x, Y + 1, z)
            b.spawner(cx, Y, cz, "minecraft:skeleton")
            spawners += 1
        elif kind == "crypt":
            for x in (cx - 2, cx + 2):
                for z in (cz - 1, cz + 1):
                    if not near_door(c, door, x, z, cell):
                        b.put(x, Y, z, S("dark_oak_planks"))
                        b.put(x, Y + 1, z, slab("dark_oak", "bottom"))
            b.spawner(cx, Y, cz, "minecraft:zombie")
            spawners += 1
            far = far_side(c, door, cell)
            b.chest(far[0], Y, far[1], far[2], "chest")
        elif kind == "chapel":
            far = far_side(c, door, cell)
            b.put(far[0], Y, far[1], S("chiseled_stone_bricks"))
            b.candles(far[0], Y + 1, far[1], "white", 4)
            b.chest(cx, Y, cz, far[2], "chest")
            for x in range(cx - 1, cx + 2):
                b.put(x, Y, cz - 1 if far[2] != "north" else cz + 1, S("gray_carpet"))
        elif kind == "collapsed":
            for _ in range(10):
                x, z = cx + r.randint(-2, 2), cz + r.randint(-2, 2)
                if not near_door(c, door, x, z, cell):
                    b.put(x, Y, z, Pal(("cobblestone", 2), ("gravel", 1), ("stone_bricks", 1)))
            b.spawner(cx, Y + 3, cz, "minecraft:cave_spider")
            spawners += 1
        else:
            corner = (cx + r.choice((-2, 2)), cz + r.choice((-2, 2)))
            b.spawner(corner[0], Y, corner[1], r.choice(["minecraft:zombie", "minecraft:skeleton"]))
            spawners += 1
        if r.random() < 0.5:
            b.put(cx, Y + 3, cz, S("chain", axis="y"))
            b.lantern(cx, Y + 2, cz, soul=True)
    # nichos con huesos y velas, telarañas, trampas y la sala secreta
    niches(b, Y + 1, 0.22, ["skull", "bone_block", "candle", "web", "empty", "skull"], lambda x, z: z >= 14 and z <= bz + 6)
    b.webs(-30, Y, 12, 30, Y + 3, bz + 6, 0.12)
    place_traps(b, 3, Y)
    secret_room(b, W, y=Y)
    return b


def near_door(c, door, x, z, cell):
    """¿(x, z) estorba la entrada a la celda c desde la celda door?"""
    if door is None:
        return False
    cx, cz = cell[c]
    dx, dz = cell[door][0] - cx, cell[door][1] - cz
    if dx:
        return (x - cx) * dx > 0 and abs(z - cz) <= 1
    return (z - cz) * dz > 0 and abs(x - cx) <= 1


def far_side(c, door, cell):
    """El sitio del fondo de la sala (frente a la entrada) y hacia dónde mira."""
    cx, cz = cell[c]
    if door is None:
        return cx, cz + 2, "north"
    dx, dz = cell[door][0] - cx, cell[door][1] - cz
    sx, sz = (-1 if dx > 0 else 1 if dx < 0 else 0), (-1 if dz > 0 else 1 if dz < 0 else 0)
    return cx + 2 * sx, cz + 2 * sz, dir_of(-sx, -sz)


# ---------------------------------------------------------------------------------------------- vistas previas
COLORS = {"spawner": (220, 30, 30), "chest": (240, 200, 40), "heart": (230, 40, 230), "gate": (40, 120, 255), "trap": (255, 140, 0),
          "secret": (40, 220, 220)}


def plan_image(b, levels, scale=4):
    """Planos de planta: para cada y de 'levels', aire (claro), muros (gris), escaleras (verde) y marcas."""
    from PIL import Image, ImageDraw
    xs = [p[0] for p in b.blocks]
    zs = [p[2] for p in b.blocks]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
    w, h = (x1 - x0 + 1) * scale, (z1 - z0 + 1) * scale
    cols = min(len(levels), 4)
    rows = (len(levels) + cols - 1) // cols
    img = Image.new("RGB", (cols * (w + 8), rows * (h + 22)), (30, 30, 34))
    dr = ImageDraw.Draw(img)
    marks = {}
    for k, ps in b.marks.items():
        for p in ps:
            marks[p] = COLORS[k]
    for n, y in enumerate(levels):
        ox, oy = (n % cols) * (w + 8), (n // cols) * (h + 22) + 18
        dr.text((ox + 2, oy - 16), "y=%d" % y, fill=(230, 230, 230))
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                st = b.get(x, y, z)
                below = b.get(x, y - 1, z)
                if (x, y, z) in marks:
                    c = marks[(x, y, z)]
                elif st is None:
                    c = (52, 52, 58)
                elif short(st) == "air":
                    c = (215, 210, 195) if standable(below) else (120, 150, 190)
                elif short(st).endswith("_stairs"):
                    c = (90, 190, 90)
                elif passable(st):
                    c = (190, 180, 150)
                elif is_full(st):
                    c = (95, 95, 100)
                else:
                    c = (150, 120, 90)
                px, pz = ox + (x - x0) * scale, oy + (z - z0) * scale
                dr.rectangle([px, pz, px + scale - 1, pz + scale - 1], fill=c)
    return img


def preview(b, out, levels, cut=None):
    import ew_render as R
    os.makedirs(out, exist_ok=True)
    plan_image(b, levels).save(os.path.join(out, b.name + "_plan.png"))
    blocks = {p: st for p, (st, be) in b.blocks.items() if short(st) != "air" and (cut is None or p[1] <= cut)}
    R.render(blocks, scale=3, title=b.name).save(os.path.join(out, b.name + "_iso.png"))


def check(b, ground=0):
    ok = walk(b, b.start, b.marks["heart"], ground=ground)
    sealed = not walk(b, b.start, b.marks["heart"], ground=ground, sealed=True)
    ok = ok and sealed
    print("  %-22s bloques %6d  generadores %d  cofres %d  trampas %d  secreta %d  rejas %d  corazón %d  recorrido %s" % (
        b.name, len(b.blocks), len(b.marks["spawner"]), len(b.marks["chest"]), len(b.marks["trap"]), len(b.marks["secret"]),
        len(b.marks["gate"]), len(b.marks["heart"]), ("sí" if ok else "NO") + ("" if sealed else " (¡la reja no cierra!)")))
    return ok


# ---------------------------------------------------------------------------------------------- 2. la torre del hechicero
def ring4(rc):
    """Las celdas de un círculo de radio rc, unidas por los lados (se puede subir en espiral sin pasos en diagonal), en orden
    contrario a las agujas del reloj."""
    start = (int(round(rc)), 0)
    order, seen, cur = [start], {start}, start
    while True:
        best = None
        for dx, dz in DIR.values():
            n = (cur[0] + dx, cur[1] + dz)
            if n in seen:
                continue
            cr = cur[0] * n[1] - cur[1] * n[0]
            if cr < 0:
                continue
            key = (abs(math.hypot(*n) - rc), -cr)
            if best is None or key < best[0]:
                best = (key, n)
        if best is None:
            break
        cur = best[1]
        order.append(cur)
        seen.add(cur)
        if len(order) > 6 and abs(cur[0] - start[0]) + abs(cur[1] - start[1]) == 1:
            break
    return order


def spire(seed):
    """La Torre del Hechicero Loco: una torre redonda de 5 o 6 plantas (biblioteca, laboratorio, celdas, círculo de
    invocación, estudio, almacén, en otro orden en cada una) con una escalera de caracol pegada al muro; arriba, el
    observatorio donde espera el Archimago."""
    b = B(seed, "mad_mage_spire")
    r = b.rnd
    W = r.choice([STONE_BRICK, DEEP_BRICK, BLACK_BRICK])
    trim = {STONE_BRICK: "chiseled_stone_bricks", DEEP_BRICK: "chiseled_deepslate", BLACK_BRICK: "chiseled_polished_blackstone"}[W]
    stair_mat = {STONE_BRICK: "stone_brick", DEEP_BRICK: "deepslate_brick", BLACK_BRICK: "polished_blackstone_brick"}[W]
    R = r.choice([7, 7, 8])
    F = r.choice([5, 6])
    H = 7
    rc = R - 2.3
    b.start = (0, 0, -R - 9)
    # --- el patio: suelo, muro roto alrededor y restos
    R2 = R + 7
    for x in range(-R2 - 1, R2 + 2):
        for z in range(-R2 - 1, R2 + 2):
            d = math.hypot(x, z)
            if d > R2 + 1.5:
                continue
            b.put(x, -1, z, Pal(("grass_block", 3), ("coarse_dirt", 2), ("podzol", 1), ("mossy_cobblestone", 1), ("cobblestone", 1)))
            for y in range(-4, -1):
                b.soft(x, y, z, S("dirt"))
            b.air(x, 0, z, x, 10, z)
            if R2 - 0.5 <= d < R2 + 0.5 and not (abs(x) <= 1 and z < 0):
                hgt = r.choice([0, 1, 2, 2, 3])
                for y in range(0, hgt):
                    b.put(x, y, z, W)
                if hgt == 3 and r.random() < 0.3:
                    b.put(x, 3, z, S("amethyst_cluster", facing="up", waterlogged="false"))
    for z in range(-R2, -R + 1):
        b.fill(-1, -1, z, 1, -1, z, Pal(("gravel", 1), ("cobblestone", 2), ("stone_bricks", 1)))
    for _ in range(6):
        a = r.random() * 2 * math.pi
        x, z = int(round((R + 3.5) * math.cos(a))), int(round((R + 3.5) * math.sin(a)))
        if z < -R and abs(x) < 3:
            continue
        for y in range(0, r.randint(1, 4)):
            b.put(x, y, z, S(trim) if y == 0 else W)
    # --- la torre
    top = F * H - 1
    for x in range(-R - 1, R + 2):
        for z in range(-R - 1, R + 2):
            d = math.hypot(x, z)
            if d < R - 0.5:
                for k in range(F + 1):
                    b.put(x, k * H - 1, z, Pal(("polished_andesite", 1), ("stone_bricks", 1)) if k == 0 else W)
                    if k < F:
                        b.air(x, k * H, z, x, k * H + H - 2, z)
            elif d < R + 0.7:
                for y in range(-1, top + 3):
                    if y <= top or y == top + 1 or (x + z) % 2 == 0:
                        b.put(x, y, z, S(trim) if (y + 1) % H == 0 and y > 0 else W)
            elif d < R + 1.7:
                for y in range(-1, 2):
                    b.put(x, y, z, W if y < 1 else S(trim))
    # el portal
    b.air(-1, 0, -R - 2, 1, 3, -R + 1)
    b.put(-1, 3, -R, stairs(stair_mat, "west", "top"))
    b.put(1, 3, -R, stairs(stair_mat, "east", "top"))
    b.put(0, 3, -R, AIR)
    # ventanas en cada planta
    for k in range(F):
        for a in (45, 135, 225, 315, 180, 0):
            if r.random() < 0.7:
                x, z = int(round(R * math.cos(math.radians(a)))), int(round(R * math.sin(math.radians(a))))
                for y in (k * H + 2, k * H + 3):
                    b.put(x, y, z, S("purple_stained_glass_pane") if r.random() < 0.5 else S("iron_bars"))
    # --- la escalera de caracol (2 de ancho, pegada al muro)
    ring = ring4(rc)
    inside = set(ring)
    L = len(ring)
    start = 6
    for k in range(F):
        for i in range(H):
            n = start + k * H + i
            cx, cz = ring[n % L]
            nx, nz = ring[(n + 1) % L]
            px, pz = ring[(n - 1) % L]
            facing = dir_of(nx - cx, nz - cz) if i < H - 1 else dir_of(cx - px, cz - pz)
            y = k * H + i
            cells = [(cx, cz)]
            inner = min(((cx + dx, cz + dz) for dx, dz in DIR.values()), key=lambda c: math.hypot(*c))
            if inner not in inside:
                cells.append(inner)
            for x, z in cells:
                b.put(x, y, z, stairs(stair_mat, facing))
                for yy in range(k * H, y):
                    b.put(x, yy, z, W)
                for yy in range(y + 1, y + 4):
                    # la reja: encima del primer peldaño del último tramo (al despertar al Archimago ya se ha pasado)
                    if k == F - 1 and i == 0:
                        b.gate(x, yy, z)
                    else:
                        b.put(x, yy, z, AIR)
    # --- las plantas
    themes = ["library", "lab", "cells", "summoning", "study", "storage"]
    r.shuffle(themes)
    themes = ["hall"] + themes[:F - 1]
    mobs = {"hall": "minecraft:zombie", "library": "minecraft:skeleton", "lab": "minecraft:witch", "cells": "minecraft:cave_spider",
            "summoning": "minecraft:spider", "study": None, "storage": "minecraft:zombie"}
    cr = rc - 1.6   # lo que queda libre en el centro

    def free(x, z):
        return math.hypot(x, z) <= cr

    for k, theme in enumerate(themes):
        y = k * H
        if theme == "hall":
            for z in range(-R + 1, 2):
                b.put(0, y, z, S("purple_carpet"))
            for x in (-3, 3):
                b.put(x, y, 2, S(trim))
                b.put(x, y + 1, 2, S("amethyst_cluster", facing="up", waterlogged="false"))
        elif theme == "library":
            for x in range(-4, 5):
                for z in range(-4, 5):
                    if free(x, z) and abs(math.hypot(x, z) - cr) < 0.8:
                        for yy in range(y, y + 3):
                            b.put(x, yy, z, S("bookshelf"))
            b.put(0, y, 0, S("lectern", facing="south", has_book="false", powered="false"))
        elif theme == "lab":
            for x, z in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
                b.put(x, y, z, S("brewing_stand", has_bottle_0="true", has_bottle_1="false", has_bottle_2="true"))
            b.put(0, y, 2, S("water_cauldron", level=2))
            b.put(0, y, -2, S("cauldron"))
        elif theme == "cells":
            for a in range(0, 360, 60):
                x, z = int(round(2.6 * math.cos(math.radians(a)))), int(round(2.6 * math.sin(math.radians(a))))
                b.fill(x, y, z, x, y + 2, z, S("iron_bars"))
            b.put(0, y, 0, S("bone_block", axis="y"))
            b.skull(0, y + 1, 0)
        elif theme == "summoning":
            for a in range(0, 360, 20):
                x, z = int(round(3 * math.cos(math.radians(a)))), int(round(3 * math.sin(math.radians(a))))
                b.put(x, y - 1, z, S("crying_obsidian") if a % 60 == 0 else S("obsidian"))
            for x, z in ((3, 0), (-3, 0), (0, 3), (0, -3)):
                b.candles(x, y, z, "purple", 4)
        elif theme == "study":
            b.put(-1, y, 2, S("dark_oak_planks"))
            b.put(0, y, 2, S("dark_oak_planks"))
            b.put(1, y, 2, S("dark_oak_planks"))
            b.candles(0, y + 1, 2, "black", 3)
            b.chest(-2, y, 2, "north", "chest")
        elif theme == "storage":
            for x, z in ((-2, 2), (2, 2), (-2, -2), (2, -2), (0, 3)):
                if r.random() < 0.7:
                    b.put(x, y, z, S("barrel", facing="up", open="false"), {"id": "minecraft:barrel"})
            b.chest(0, y, -2 if r.random() < 0.5 else 2, "south", "chest")
        if theme not in ("hall", "study", "storage") and r.random() < 0.6:
            a = r.random() * 2 * math.pi
            x, z = int(round(2.2 * math.cos(a))), int(round(2.2 * math.sin(a)))
            if b.is_air(x, y, z) and not standable(b.get(x, y, z)):
                b.chest(x, y, z, r.choice(list(DIR)), "chest", "barrel" if theme == "lab" else "chest")
        mob = mobs[theme]
        if mob:
            spot = (0, 0) if theme in ("summoning", "hall", "lab", "storage") else (r.choice((-1, 1)), r.choice((-1, 1)))
            if theme == "hall":
                spot = (0, 3)
            if theme == "library":
                spot = (1, 1)
            b.spawner(spot[0], y, spot[1], mob)
        if k > 0:
            b.put(0, y + H - 2, 0, S("chain", axis="y"))
            b.lantern(0, y + H - 3, 0, soul=k % 2 == 1)
        else:
            b.torch(-1, 2, -R + 1, "south")
            b.torch(1, 2, -R + 1, "south")
    # el observatorio de arriba (la arena del Archimago)
    y = F * H
    for a in range(0, 360, 90):
        x, z = int(round((R - 1.5) * math.cos(math.radians(a + 45)))), int(round((R - 1.5) * math.sin(math.radians(a + 45))))
        if (x, z) in inside:
            continue
        b.fill(x, y, z, x, y + 2, z, S(trim))
        b.put(x, y + 3, z, S("amethyst_cluster", facing="up", waterlogged="false"))
    b.heart(0, y - 1 + 1, 0)
    for x, z in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        b.put(x, y, z, S("purpur_slab", type="bottom", waterlogged="false"))
    b.webs(-R, 0, -R, R, top, R, 0.04)
    # ruina: algún hueco en el muro de arriba y enredaderas por fuera
    for _ in range(14):
        a = r.random() * 2 * math.pi
        x, z = int(round(R * math.cos(a))), int(round(R * math.sin(a)))
        yy = r.randint(3 * H, top)
        if all(b.get(x, yy + dy, z) is not None and short(b.get(x, yy + dy, z)) not in ("air",) for dy in (0, 1)):
            b.put(x, yy, z, AIR)
    secret = False
    return b


# ---------------------------------------------------------------------------------------------- 3. la tumba del faraón
GLYPH = Pal(("chiseled_sandstone", 3), ("blue_terracotta", 2), ("orange_terracotta", 2), ("cut_sandstone", 3))


def pharaoh(seed):
    """La Tumba del Faraón Maldito: una pirámide escalonada con un laberinto de pasillos estrechos dentro (trampas de
    flechas, momias y suelos de magma) y, bajo ella, la cámara funeraria del Faraón."""
    b = B(seed, "pharaoh_tomb")
    r = b.rnd
    W = SAND_BRICK
    b.start = (0, 0, -22)
    # --- alrededor: arena, obeliscos y columnas medio enterradas
    for x in range(-20, 21):
        for z in range(-24, 21):
            if max(abs(x), abs(z)) > 15 and r.random() < 0.85:
                b.put(x, -1, z, Pal(("sand", 6), ("smooth_sandstone", 1), ("sandstone", 1)))
                b.air(x, 0, z, x, 4, z)
    for x in (-8, 8):
        b.fill(x - 1, 0, -21, x + 1, 0, -19, S("cut_sandstone"))
        b.fill(x, 1, -20, x, 7, -20, S("chiseled_sandstone") if r.random() < 0.3 else S("smooth_sandstone"))
        b.put(x, 8, -20, S("orange_terracotta"))
        for y in range(2, 7, 2):
            b.put(x, y, -20, S("blue_terracotta"))
    for _ in range(5):
        x, z = r.randint(-19, 19), r.choice([r.randint(-23, -17), r.randint(17, 19)])
        for y in range(0, r.randint(1, 4)):
            b.put(x, y, z, S("cut_sandstone") if y else S("chiseled_sandstone"))
    # --- la pirámide: 7 escalones de 3 de alto
    for t in range(7):
        hs = 15 - 2 * t
        for y in range(3 * t, 3 * t + 3):
            for x in range(-hs, hs + 1):
                for z in range(-hs, hs + 1):
                    edge = max(abs(x), abs(z)) >= hs - 1
                    if edge:
                        band = y == 3 * t and t % 2 == 1
                        b.put(x, y, z, S("orange_terracotta") if band and (x + z) % 3 == 0 else
                              S("smooth_sandstone") if y == 3 * t + 2 else W)
                    else:
                        b.soft(x, y, z, S("sandstone"))
    b.fill(-1, 21, -1, 1, 21, 1, S("gold_block"))
    b.put(0, 22, 0, S("gold_block"))
    for x in range(-15, 16):
        for z in range(-15, 16):
            for y in range(-5, 0):
                b.soft(x, y, z, S("sandstone"))
    # la entrada (con su portada) hasta el laberinto
    b.room(-2, 0, -16, -1, 2, -11, W)
    for x in (-4, 1):
        b.fill(x, 0, -16, x, 5, -16, S("cut_sandstone"))
        b.put(x, 6, -16, S("chiseled_sandstone"))
    b.fill(-4, 6, -16, 1, 6, -16, S("chiseled_sandstone"))
    b.fill(-3, 3, -16, 0, 5, -16, GLYPH)
    b.air(-2, 0, -16, -1, 2, -16)
    # --- el laberinto: rejilla de 6 x 6, pasillos de 2 de ancho, cada 4
    Y = 0
    cs = [-10, -6, -2, 2, 6, 10]
    cell = {(i, j): (cs[i], cs[j]) for i in range(6) for j in range(6)}
    edges = maze(r, 6, 6, (2, 0), 0.1)
    dist, adj = distances(edges, (2, 0))

    def carve2(a, c):
        (ax, az), (cx, cz) = a, c
        b.air(min(ax, cx) - 1, Y, min(az, cz) - 1, max(ax, cx), Y + 2, max(az, cz))

    for e in edges:
        a, c = tuple(e)
        carve2(cell[a], cell[c])
    for c, (cx, cz) in cell.items():
        carve2((cx, cz), (cx, cz))
    for (x, y, z), (st, be) in list(b.blocks.items()):
        if y == Y - 1 and -11 <= x <= 10 and -11 <= z <= 10 and b.is_air(x, Y, z):
            b.put(x, y, z, Pal(("sandstone", 3), ("cut_sandstone", 1), ("smooth_sandstone", 2)))
        if y == Y + 1 and b.get(x, y, z) is not None and short(b.get(x, y, z)) == "sandstone" and r.random() < 0.08:
            if any(b.is_air(x + dx, y, z + dz) for dx, dz in DIR.values()):
                b.put(x, y, z, GLYPH)
    # la bajada: en la celda más lejana, una escalera de caracol en un hueco de 3 x 3 hasta la cámara (y -11)
    down = max((c for c in cell if c[0] in (0, 5) or c[1] == 0), key=lambda c: dist[c])
    dx0, dz0 = cell[down]
    sx, sz = dx0, dz0
    loop = [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0)]
    b.fill(sx - 1, -12, sz - 1, sx + 1, -1, sz + 1, S("sandstone"), soft=False)
    b.air(sx - 1, Y, sz - 1, sx + 1, Y + 2, sz + 1)
    b.put(sx, Y, sz, S("chiseled_sandstone"))
    BY = -11
    b.room(sx - 1, BY, sz - 1, sx + 1, BY + 3, sz + 1, W)
    b.fill(sx - 1, BY - 1, sz - 1, sx + 1, BY - 1, sz + 1, S("cut_sandstone"))
    b.fill(sx, BY, sz, sx, BY + 3, sz, S("chiseled_sandstone"))
    for i in range(11):
        ox, oz = loop[i % 8]
        nx, nz = loop[(i + 1) % 8]
        y = -1 - i
        b.put(sx + ox, y, sz + oz, stairs("sandstone", dir_of(ox - nx, oz - nz)))
        for yy in range(y + 1, y + 4):
            if yy < Y:
                b.put(sx + ox, yy, sz + oz, AIR)
    # --- la cámara funeraria (debajo del centro) y el pasillo desde la escalera
    BY = -11
    ex, ez = sx + loop[11 % 8][0] * 2, sz + loop[11 % 8][1] * 2
    cz0 = -3
    exit_ = (sx + 3, sz) if sx < -2 else (sx - 3, sz) if sx > 2 else (sx, sz + 3)
    b.corridor(exit_, (0, cz0 - 3), BY, W)
    b.corridor((0, cz0 - 3), (0, cz0), BY, W)
    b.room(-7, BY, cz0, 7, BY + 7, 12, W, floor=Pal(("cut_sandstone", 3), ("smooth_sandstone", 2), ("chiseled_sandstone", 1)))
    for x in range(-1, 2):
        for y in range(BY, BY + 4):
            b.gate(x, y, cz0 - 1)
    for x in (-7, 7):
        for z in range(cz0, 13):
            for y in range(BY, BY + 8):
                if r.random() < 0.25:
                    b.put(x + (1 if x > 0 else -1), y, z, GLYPH)
    for px in (-4, 3):
        for pz in (cz0 + 3, cz0 + 10):
            for y in range(BY, BY + 8):
                b.put(px, y, pz, S("orange_terracotta") if y % 3 == 0 else S("cut_sandstone"))
                b.put(px + 1, y, pz, S("orange_terracotta") if y % 3 == 0 else S("cut_sandstone"))
            b.torch(px, BY + 3, pz - 1, "north")
    # el sarcófago: el Corazón entre bloques de oro, sobre un estrado
    b.fill(-3, BY, 8, 3, BY, 11, S("smooth_sandstone"))
    b.heart(0, BY + 1, 9)
    b.put(-1, BY + 1, 9, S("gold_block"))
    b.put(1, BY + 1, 9, S("gold_block"))
    b.put(0, BY + 1, 10, S("chiseled_sandstone"))
    for x in (-3, 3):
        b.put(x, BY + 1, 11, S("raw_gold_block"))
        b.put(x, BY + 2, 11, S("candle", candles=3, lit="true", waterlogged="false"))
    for z in range(cz0, 8):
        b.put(0, BY, z, S("blue_carpet"))
    # salas: momias, arañas y cofres en los callejones sin salida
    deads = [c for c in cell if len(adj[c]) == 1 and c not in ((2, 0), down)]
    r.shuffle(deads)
    mobs = ["minecraft:husk", "minecraft:husk", "minecraft:skeleton", "minecraft:husk", "minecraft:spider", "minecraft:husk"]
    for n, c in enumerate(deads):
        cx, cz = cell[c]
        if n < len(mobs):
            b.spawner(cx - 1 + (n % 2), Y, cz - 1 + (n // 2) % 2, mobs[n])
        if n % 2 == 1 or n >= len(mobs):
            (door,) = adj[c]
            ddx, ddz = cell[door][0] - cx, cell[door][1] - cz
            spot = (cx - 1 if ddx > 0 else cx, cz - 1 if ddz > 0 else cz)
            if b.is_air(spot[0], Y, spot[1]) and (spot[0], Y, spot[1]) not in b.marks["spawner"]:
                b.chest(spot[0], Y, spot[1], dir_of(1 if ddx > 0 else -1 if ddx < 0 else 0, 1 if ddz > 0 else -1 if ddz < 0 else 0)
                        if (ddx or ddz) else "north", "chest", "barrel" if n % 4 == 3 else "chest")
    extra = [c for c in cell if c not in deads and c not in ((2, 0), down)]
    r.shuffle(extra)
    for c in extra[:max(0, 4 - len(deads))]:
        cx, cz = cell[c]
        spot = (cx - r.randint(0, 1), cz - r.randint(0, 1))
        b.spawner(spot[0], Y + 2, spot[1], r.choice(["minecraft:husk", "minecraft:cave_spider"]))
    # suelo de magma maldito en algún cruce y trampas de flechas
    for c in r.sample([c for c in cell if len(adj[c]) >= 3], 2):
        cx, cz = cell[c]
        b.fill(cx - 1, Y - 1, cz - 1, cx, Y - 1, cz, S("magma_block"))
    place_traps(b, 4, Y)
    for (x, y, z) in list(b.blocks):
        if y == Y + 2 and b.is_air(x, y, z) and r.random() < 0.03:
            b.torch(x, y, z, "north") if is_full(b.get(x, y, z + 1)) else None
    b.webs(-11, Y, -11, 10, Y + 2, 10, 0.05)
    secret_room(b, W, y=BY)
    return b


# ---------------------------------------------------------------------------------------------- 4. la forja enana
def ring_square(h):
    """Las celdas del borde de un cuadrado de lado 2h+1 alrededor de (0, 0), en el sentido de las agujas del reloj."""
    out = []
    for x in range(-h, h + 1):
        out.append((x, -h))
    for z in range(-h + 1, h + 1):
        out.append((h, z))
    for x in range(h - 1, -h - 1, -1):
        out.append((x, h))
    for z in range(h - 1, -h, -1):
        out.append((-h, z))
    return out


def spiral_down(b, depth, wall, stair_mat, exit_cell, h=2, core=None):
    """Escalera de caracol de 1 de ancho alrededor de un pilar, de y = -1 a y = -depth; el último peldaño cae en exit_cell."""
    ring = ring_square(h)
    L = len(ring)
    k0 = (ring.index(exit_cell) - (depth - 1)) % L
    for x in range(-h - 1, h + 2):
        for z in range(-h - 1, h + 2):
            for y in range(-depth - 1, 1):
                if max(abs(x), abs(z)) == h + 1:
                    b.soft(x, y, z, wall)
                elif max(abs(x), abs(z)) < h:
                    b.put(x, y, z, core or wall)
    for i in range(depth):
        cx, cz = ring[(k0 + i) % L]
        nx, nz = ring[(k0 + i + 1) % L]
        y = -1 - i
        b.put(cx, y, cz, stairs(stair_mat, dir_of(cx - nx, cz - nz)))
        for yy in range(y + 1, min(y + 4, 1)):
            b.put(cx, yy, cz, AIR)
        for yy in range(y - 1, -depth - 1, -1):
            if b.get(cx, yy, cz) is None:
                b.put(cx, yy, cz, wall)
    return ring[(k0 + depth - 1) % L]


def forge(seed):
    """La Forja Enana Olvidada: una escalera de caracol desde unas ruinas baja a un gran salón de columnas con un río
    de lava, fraguas y yunques; a los lados, la cámara del tesoro, la mina, los barracones, la fundición o el almacén
    (cuatro, en otro orden en cada una), y al fondo la Gran Forja del Capataz."""
    b = B(seed, "dwarven_forge")
    r = b.rnd
    W = DEEP_BRICK
    D = 28
    Y = -D
    b.start = (0, 0, -8)
    # --- arriba: unas ruinas de piedra alrededor del pozo
    for x in range(-6, 7):
        for z in range(-6, 7):
            b.put(x, -1, z, Pal(("stone_bricks", 3), ("mossy_stone_bricks", 1), ("cracked_stone_bricks", 1), ("cobblestone", 1)))
            b.air(x, 0, z, x, 5, z)
            if max(abs(x), abs(z)) == 6 and not (abs(x) <= 1 and z == -6):
                for y in range(0, r.choice([0, 1, 2, 3, 4])):
                    b.put(x, y, z, STONE_BRICK)
    for x in (-2, 2):
        b.fill(x, 0, -6, x, 5, -6, S("polished_deepslate"))
    b.fill(-2, 6, -6, 2, 6, -6, S("chiseled_deepslate"))
    for x in (-1, 0, 1):
        b.put(x, 5, -6, stairs("deepslate_brick", "west" if x < 0 else "east" if x > 0 else "south", "top") if x else S("chiseled_deepslate"))
    for x in range(-3, 4):
        for z in range(-3, 4):
            if max(abs(x), abs(z)) == 3 and r.random() < 0.6:
                b.put(x, 0, z, S("deepslate_brick_wall", up="true"))
    # --- el pozo con la escalera de caracol
    last = spiral_down(b, D, W, "deepslate_brick", (0, 2), core=S("polished_deepslate"))
    for y in range(-D, 0, 6):
        b.lantern(0, y, -2, hanging=False)
    b.air(-1, Y, 3, 1, Y + 3, 5)
    b.room(-1, Y, 3, 1, Y + 3, 5, W)
    # --- el gran salón
    z0, z1 = 6, 32
    b.room(-16, Y, z0, 16, Y + 10, z1, W, floor=Pal(("polished_deepslate", 3), ("deepslate_tiles", 2), ("cracked_deepslate_tiles", 1)))
    LZ = r.choice([16, 17, 22, 23])
    pxs = r.choice([(-11, -5, 4, 10), (-12, -7, 6, 11), (-10, -3, 2, 9)])
    pzs = r.choice([(11, 19, 27), (10, 18, 26), (12, 26)])
    for px in pxs:
        for pz in pzs:
            if pz <= LZ <= pz + 1:
                continue
            b.fill(px, Y, pz, px + 1, Y + 10, pz + 1, S("deepslate_tiles"))
            b.fill(px, Y + 8, pz, px + 1, Y + 8, pz + 1, S("chiseled_deepslate"))
            b.lantern(px - 1, Y + 7, pz, hanging=False) if r.random() < 0.4 else None
    # el río de lava (de lado a lado) con tres puentes
    for x in range(-16, 17):
        if x in (-8, -7, 0, 1, 7, 8):
            b.put(x, Y - 1, LZ, S("smooth_basalt"))
            continue
        b.put(x, Y - 1, LZ, S("lava", level=0))
        b.put(x, Y - 2, LZ, S("magma_block"))
        b.soft(x, Y - 2, LZ - 1, W)
        b.soft(x, Y - 2, LZ + 1, W)
    # fraguas en las paredes
    for side in (-1, 1):
        x = side * 15
        for z in range(8, 31, 4):
            if abs(z - LZ) <= 1:
                continue
            kind = r.choice(["anvil", "blast", "smith", "cauldron", "grind", "campfire"])
            if kind == "anvil":
                b.put(x, Y, z, S("anvil", facing="north"))
            elif kind == "blast":
                b.put(x, Y, z, S("blast_furnace", facing="east" if side < 0 else "west", lit="false"))
                b.put(x, Y + 1, z, S("blast_furnace", facing="east" if side < 0 else "west", lit="false"))
            elif kind == "smith":
                b.put(x, Y, z, S("smithing_table"))
            elif kind == "cauldron":
                b.put(x, Y, z, S("lava_cauldron"))
            elif kind == "grind":
                b.put(x, Y, z, S("grindstone", face="floor", facing="north"))
            else:
                b.put(x, Y, z, S("campfire", facing="north", lit="true", signal_fire="false", waterlogged="false"))
        for z in range(9, 31, 6):
            b.put(x + side, Y + 6, z, S("chain", axis="y"))
    b.chest(-14, Y, 30, "east", "chest")
    for mob in ("minecraft:zombie", "minecraft:skeleton"):
        while True:
            x, z = r.randint(-13, 13), r.randint(8, 30)
            if b.is_air(x, Y, z) and abs(z - LZ) > 2 and abs(x) > 2:
                b.spawner(x, Y, z, mob)
                break
    # --- las cámaras de los lados
    kinds = ["vault", "mine", "barracks", "smeltery", "storage"]
    r.shuffle(kinds)
    rows = r.choice([(10, 24), (12, 26), (14, 28), (9, 27)])
    spots = [(-1, rows[0]), (1, rows[0]), (-1, rows[1]), (1, rows[1])]
    for (side, cz), kind in zip(spots, kinds):
        cx = side * 27
        b.corridor((side * 15, cz), (side * 21, cz), Y, W)
        for x in range(side * 17, side * 22, side):
            b.put(x, Y, cz + 1, S("rail", shape="east_west", waterlogged="false"))
        b.room(cx - 5, Y, cz - 4, cx + 5, Y + 5, cz + 4, W)
        inner = side * -1   # hacia el salón
        if kind == "vault":
            for z in range(cz - 4, cz + 5):
                b.put(cx + side * 5, Y, z, S("gold_block") if r.random() < 0.4 else S("raw_gold_block"))
            for z in range(cz - 4, cz + 5):
                for y in range(Y, Y + 4):
                    if z != cz:
                        b.put(cx + side * 2, y, z, S("iron_bars"))
            b.chest(cx + side * 4, Y, cz - 2, "east" if side < 0 else "west", "chest")
            b.chest(cx + side * 4, Y, cz + 2, "east" if side < 0 else "west", "chest")
            b.spawner(cx - side * 2, Y, cz + 3, "minecraft:skeleton")
        elif kind == "mine":
            ores = Pal(("deepslate_iron_ore", 4), ("deepslate_coal_ore", 3), ("deepslate_gold_ore", 2), ("deepslate_copper_ore", 2),
                       ("deepslate_redstone_ore", 1), ("deepslate_diamond_ore", 0.4), ("deepslate", 6), ("tuff", 2))
            for x in range(cx - 6, cx + 7):
                for z in range(cz - 5, cz + 6):
                    for y in range(Y - 1, Y + 7):
                        if (x in (cx - 6, cx + 6) or z in (cz - 5, cz + 5) or y in (Y - 1, Y + 6)) and not b.is_air(x, y, z):
                            b.put(x, y, z, ores)
            b.spawner(cx, Y, cz, "minecraft:silverfish", count=4, near=8)
            for z in range(cz - 3, cz + 4):
                b.put(cx + side * 3, Y, z, S("rail", shape="north_south", waterlogged="false"))
            b.chest(cx + side * 4, Y, cz + 4, "north", "chest", "barrel")
        elif kind == "barracks":
            for z in (cz - 3, cz - 1, cz + 1, cz + 3):
                b.put(cx + side * 3, Y, z, S("red_bed", facing="east" if side > 0 else "west", occupied="false", part="head"))
                b.put(cx + side * 2, Y, z, S("red_bed", facing="east" if side > 0 else "west", occupied="false", part="foot"))
            b.spawner(cx - side * 1, Y, cz, "minecraft:zombie")
            b.chest(cx + side * 5, Y, cz, "east" if side < 0 else "west", "chest")
        elif kind == "smeltery":
            for z in range(cz - 3, cz + 4, 2):
                b.put(cx + side * 5, Y, z, S("blast_furnace", facing="east" if side < 0 else "west", lit="false"))
                b.put(cx + side * 5, Y + 1, z, S("lava_cauldron"))
            b.fill(cx - 1, Y - 1, cz - 1, cx + 1, Y - 1, cz + 1, S("lava", level=0))
            b.fill(cx - 1, Y - 2, cz - 1, cx + 1, Y - 2, cz + 1, S("magma_block"))
            b.spawner(cx + side * 3, Y, cz + 3, "minecraft:magma_cube")
            b.chest(cx + side * 3, Y, cz - 3, "north", "chest", "barrel")
        else:
            for x in range(cx - 4, cx + 5):
                for z in (cz - 4, cz + 4):
                    if r.random() < 0.15:
                        b.chest(x, Y, z, "north", "chest", "barrel")
                    elif r.random() < 0.6:
                        b.put(x, Y, z, S("barrel", facing="up", open="false"), {"id": "minecraft:barrel"})
                        if r.random() < 0.4:
                            b.put(x, Y + 1, z, S("barrel", facing="up", open="false"), {"id": "minecraft:barrel"})
            b.chest(cx + side * 5, Y, cz, "east" if side < 0 else "west", "chest")
            b.spawner(cx, Y, cz, "minecraft:spider")
        b.put(cx, Y + 5, cz, S("chain", axis="y"))
        b.lantern(cx, Y + 4, cz)
    # --- la Gran Forja (la arena del Capataz)
    b.corridor((0, 32), (0, 36), Y, W)
    for x in range(-1, 2):
        for y in range(Y, Y + 4):
            b.gate(x, y, 35)
    fx, fz, FR = 0, 48, 11
    for x in range(-FR - 1, FR + 2):
        for z in range(fz - FR - 1, fz + FR + 2):
            d = math.hypot(x - fx, z - fz)
            if d < FR + 0.5:
                b.soft(x, Y - 1, z, Pal(("polished_blackstone", 2), ("blackstone", 1), ("polished_basalt", 1)))
                b.soft(x, Y + 12, z, W)
                b.air(x, Y, z, x, Y + 11, z)
                if abs(d - 7) < 0.5 and not (abs(x) <= 1 or abs(z - fz) <= 1):
                    b.put(x, Y - 1, z, S("lava", level=0))
                    b.put(x, Y - 2, z, S("magma_block"))
            elif d < FR + 1.7:
                for y in range(Y - 1, Y + 13):
                    b.soft(x, y, z, BLACK_BRICK)
    b.air(-1, Y, fz - FR - 1, 1, Y + 3, fz - FR)
    b.fill(-1, Y, fz - 1, 1, Y, fz + 1, S("polished_blackstone_bricks"))
    b.heart(0, Y + 1, fz)
    for x, z in ((-1, fz - 1), (1, fz - 1), (-1, fz + 1), (1, fz + 1)):
        b.put(x, Y + 1, z, S("anvil", facing="north"))
    for a in range(0, 360, 45):
        x, z = int(round(4.5 * math.cos(math.radians(a)))), fz + int(round(4.5 * math.sin(math.radians(a))))
        b.put(x, Y, z, S("campfire", facing="north", lit="true", signal_fire="false", waterlogged="false"))
        x2, z2 = int(round(9.5 * math.cos(math.radians(a + 22)))), fz + int(round(9.5 * math.sin(math.radians(a + 22))))
        for y in range(Y + 5, Y + 12):
            b.put(x2, y, z2, S("chain", axis="y"))
        b.lantern(x2, Y + 4, z2)
    for a in range(0, 360, 90):
        x, z = int(round(10 * math.cos(math.radians(a + 45)))), fz + int(round(10 * math.sin(math.radians(a + 45))))
        b.fill(x, Y, z, x, Y + 11, z, S("polished_basalt", axis="y"))
    # nichos, telarañas, trampas y la sala secreta
    b.webs(-32, Y, 0, 32, Y + 10, 34, 0.04)
    place_traps(b, 2, Y)
    secret_room(b, W, y=Y)
    return b


# ---------------------------------------------------------------------------------------------- 5. el nido de arañas
def blob(b, cx, cy, cz, rx, ry, rz, floor=None, noise=0.0):
    """Excava un elipsoide (con el suelo plano en 'floor' si se da)."""
    r = b.rnd
    for x in range(int(cx - rx - 1), int(cx + rx + 2)):
        for z in range(int(cz - rz - 1), int(cz + rz + 2)):
            for y in range(int(cy - ry - 1), int(cy + ry + 2)):
                if floor is not None and y < floor:
                    continue
                d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                if d <= 1.0 + (r.random() - 0.5) * noise:
                    b.put(x, y, z, AIR)


def tunnel(b, pts, rad=2.2):
    """Un túnel redondo por una línea de puntos (los puntos son el suelo por donde se anda)."""
    for (ax, ay, az), (cx, cy, cz) in zip(pts, pts[1:]):
        n = int(max(abs(cx - ax), abs(cz - az), abs(cy - ay)) * 2) + 1
        for i in range(n + 1):
            t = i / n
            x, y, z = ax + (cx - ax) * t, ay + (cy - ay) * t, az + (cz - az) * t
            fl = int(math.floor(y))
            blob(b, x, y + rad - 0.6, z, rad, rad, rad, floor=fl)


def shell(b, pal, top=-1):
    """Cierra lo excavado con un muro de roca (para que no se cuele el agua ni se vea desde las cuevas)."""
    for (x, y, z), (st, be) in list(b.blocks.items()):
        if short(st) != "air" or y > top:
            continue
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    b.soft(x + dx, y + dy, z + dz, pal)


def nest(seed):
    """El Nido de la Reina Araña: una dolina en la superficie baja por un túnel a una red de cuevas llenas de telarañas,
    huevos y restos de aventureros; en la caverna más honda espera la Reina Madre."""
    b = B(seed, "spider_nest")
    r = b.rnd
    b.start = (0, 0, -7)
    # la dolina
    for y in range(-4, 1):
        rad = 3.5 + (y + 4) * 0.4
        for x in range(-6, 7):
            for z in range(-6, 7):
                if math.hypot(x, z) <= rad + r.random() * 0.6:
                    b.put(x, y, z, AIR)
    for x in range(-7, 8):
        for z in range(-7, 8):
            if 4.5 < math.hypot(x, z) < 7 and r.random() < 0.5:
                b.put(x, -1, z, Pal(("coarse_dirt", 2), ("rooted_dirt", 1), ("mossy_cobblestone", 1)))
                if r.random() < 0.3:
                    b.put(x, 0, z, S("cobweb"))
    # las cavernas: una cadena con ramas
    rooms = []
    pos = (0, -12, 18)
    heading = r.choice((-1.2, 1.2))
    for k in range(6):
        rx, rz, ry = r.uniform(5, 7), r.uniform(5, 7), r.uniform(3.5, 4.5)
        rooms.append((pos, rx, ry, rz))
        last = k == 5
        best = None
        for _ in range(120):
            h = r.uniform(0, 2 * math.pi)
            dist = 26 if last else r.uniform(17, 20)
            cand = (int(round(pos[0] + dist * math.sin(h))), pos[1] - r.randint(0, 4), int(round(pos[2] + dist * math.cos(h))))
            if abs(cand[0]) > 36 or not 6 <= cand[2] <= 60 or math.hypot(cand[0], cand[2]) < 14:
                continue
            gap = min(math.hypot(cand[0] - q[0][0], cand[2] - q[0][2]) for q in rooms)
            if best is None or gap > best[0]:
                best = (gap, cand)
        pos = best[1]
    boss = (pos, 11.0, 5.0, 11.0)
    # túneles: dolina -> 1ª caverna, y en cadena (con una rama que vuelve atrás)
    paths = [[(0, -3, 0), (6, -6, 5), (2, -9, 11), rooms[0][0]]]
    for a, c in zip(rooms, rooms[1:] + [boss]):
        (ax, ay, az), (cx, cy, cz) = a[0], c[0]
        mid = ((ax + cx) // 2 + r.randint(-4, 4), (ay + cy) // 2, (az + cz) // 2)
        paths.append([a[0], mid, c[0]])
    paths.append([rooms[1][0], ((rooms[1][0][0] + rooms[3][0][0]) // 2 + r.choice((-12, 12)), rooms[2][0][1], rooms[2][0][2]), rooms[3][0]])
    for p in paths:
        tunnel(b, p, rad=r.uniform(2.0, 2.4))
    for (cx, cy, cz), rx, ry, rz in rooms:
        blob(b, cx, cy + ry * 0.55, cz, rx, ry, rz, floor=cy, noise=0.25)
    (bx, by, bz), brx, bry, brz = boss
    blob(b, bx, by + 3.5, bz, brx, bry + 2, brz, floor=by, noise=0.15)
    # el muro de roca alrededor
    shell(b, CAVE)
    for (x, y, z), (st, be) in list(b.blocks.items()):
        if short(st) == "air" and y < 0 and b.get(x, y - 1, z) is not None and short(b.get(x, y - 1, z)) != "air":
            b.put(x, y - 1, z, Pal(("stone", 3), ("tuff", 2), ("gravel", 0), ("coarse_dirt", 1), ("cobblestone", 1)))
    # la reja: un anillo alrededor de la caverna de la Reina (corta el túnel que entra)
    for (x, y, z), (st, be) in list(b.blocks.items()):
        if short(st) == "air" and by - 3 <= y <= by + 9 and brx + 0.3 <= math.hypot(x - bx, z - bz) < brx + 2.3:
            b.gate(x, y, z)
    # el nido de la Reina: montón de huesos con el Corazón, huevos y telarañas
    b.fill(bx - 1, by, bz - 1, bx + 1, by, bz + 1, S("bone_block", axis="y"))
    b.heart(bx, by + 1, bz)
    for _ in range(14):
        a = r.random() * 2 * math.pi
        d = r.uniform(3, brx - 2)
        x, z = int(round(bx + d * math.cos(a))), int(round(bz + d * math.sin(a)))
        if b.is_air(x, by, z):
            b.put(x, by, z, S("bone_block", axis=r.choice("xyz")) if r.random() < 0.5 else S("white_wool"))
            if r.random() < 0.5:
                b.skull(x, by + 1, z)
    # en cada caverna: un generador, huevos, huesos, liquen y quizá los restos de un aventurero
    mobs = ["minecraft:cave_spider", "minecraft:spider", "minecraft:cave_spider", "minecraft:spider", "minecraft:cave_spider",
            "minecraft:spider"]
    chests = r.sample(range(len(rooms)), 3)
    for k, ((cx, cy, cz), rx, ry, rz) in enumerate(rooms):
        b.spawner(cx, cy, cz, mobs[k])
        for _ in range(r.randint(2, 4)):
            a = r.random() * 2 * math.pi
            x, z = int(round(cx + (rx - 1.5) * math.cos(a))), int(round(cz + (rz - 1.5) * math.sin(a)))
            for dx in range(-1, 1):
                for dz in range(-1, 1):
                    for dy in range(0, 2):
                        if b.is_air(x + dx, cy + dy, z + dz) and r.random() < 0.75:
                            b.put(x + dx, cy + dy, z + dz, S("white_wool") if dy == 0 or r.random() < 0.5 else S("cobweb"))
        if k in chests:
            a = r.random() * 2 * math.pi
            x, z = int(round(cx + (rx - 2.5) * math.cos(a))), int(round(cz + (rz - 2.5) * math.sin(a)))
            if b.is_air(x, cy, z):
                b.chest(x, cy, z, r.choice(list(DIR)), "chest")
                b.skull(x + 1, cy, z) if b.is_air(x + 1, cy, z) else None
                b.put(x - 1, cy, z, S("torch")) if b.is_air(x - 1, cy, z) else None
    for (x, y, z), (st, be) in list(b.blocks.items()):
        if short(st) == "air" and y < -2 and standable(b.get(x, y - 1, z)) and r.random() < 0.04:
            b.put(x, y, z, S("glow_lichen", down="true", up="false", north="false", south="false", east="false", west="false",
                             waterlogged="false"))
    b.webs(-40, -40, -10, 40, -1, 120, 0.16)
    secret_room(b, CAVE, levels=[rooms[k][0][1] for k in range(len(rooms))])
    return b


# ---------------------------------------------------------------------------------------------- 6. la fortaleza helada
ICE = Pal(("packed_ice", 4), ("snow_block", 2), ("polished_diorite", 1), ("calcite", 1))


def straight_stairs(b, x0, x1, y0, z0, n, facing, mat, wall, gate_first=False, hole_y=None):
    """Tramo recto de n peldaños hacia 'facing' (sube 1 por bloque) desde (x0..x1, y0, z0) — solo en z (north/south)."""
    dz = DIR[facing][1]
    for i in range(n):
        z = z0 + dz * i
        y = y0 + i
        for x in range(x0, x1 + 1):
            b.put(x, y, z, stairs(mat, facing))
            for yy in range(y0, y):
                b.put(x, yy, z, wall)
            for yy in range(y + 1, y + 4):
                if gate_first and i == 0:
                    b.gate(x, yy, z)
                else:
                    b.put(x, yy, z, AIR)


def keep(seed):
    """La Fortaleza Helada: una muralla de hielo con cuatro torres, un patio con una fuente congelada y el torreón; en la
    planta baja, el gran salón; debajo, la bodega con celdas y almacenes; arriba, el salón del trono del Señor del Invierno."""
    b = B(seed, "frozen_keep")
    r = b.rnd
    W = ICE
    b.start = (0, 0, -7)
    for x in range(-21, 22):
        for z in range(-7, 39):
            b.put(x, -1, z, Pal(("snow_block", 4), ("packed_ice", 1), ("stone", 1)))
            for y in range(-4, -1):
                b.soft(x, y, z, S("dirt"))
            b.air(x, 0, z, x, 16, z)
            if not (-16 <= x <= 16 and 0 <= z <= 32) and r.random() < 0.5:
                b.put(x, 0, z, S("snow", layers=r.randint(1, 2)))
    # la muralla (2 de grueso, 6 de alto, almenada)
    for x in range(-16, 17):
        for z in range(0, 33):
            ring = min(x + 16, 16 - x, z, 32 - z)
            if ring <= 1:
                for y in range(0, 6):
                    b.put(x, y, z, W)
                if ring == 0 and (x + z) % 2 == 0:
                    b.put(x, 6, z, W)
    b.air(-1, 0, 0, 1, 3, 1)
    b.fill(-2, 4, 0, 2, 4, 0, S("chiseled_stone_bricks"))
    b.fill(-1, 2, 0, 1, 3, 0, S("iron_bars"))
    b.air(-1, 0, 0, 1, 1, 1)
    # las torres de las esquinas (con generador o cofre)
    corners = [(-16, 0), (16, 0), (-16, 32), (16, 32)]
    stuff = ["spawner", "chest", "spawner", "chest"]
    r.shuffle(stuff)
    for (cx, cz), what in zip(corners, stuff):
        b.room(cx - 2, 0, cz - 2, cx + 2, 9, cz + 2, W)
        for x in range(cx - 3, cx + 4):
            for z in range(cz - 3, cz + 4):
                if max(abs(x - cx), abs(z - cz)) == 3 and (x + z) % 2 == 0:
                    b.put(x, 11, z, W)
        b.fill(cx - 3, 10, cz - 3, cx + 3, 10, cz + 3, W)
        ix, iz = cx - (3 if cx > 0 else -3), cz - (3 if cz > 0 else -3)
        b.air(ix, 0, cz, ix, 2, cz)
        b.air(ix + (1 if cx > 0 else -1), 0, cz, ix + (1 if cx > 0 else -1), 2, cz)
        if what == "spawner":
            b.spawner(cx, 0, cz, r.choice(["minecraft:stray", "minecraft:skeleton"]))
        else:
            b.chest(cx + (1 if cx > 0 else -1), 0, cz + (1 if cz > 0 else -1), "north" if cz > 0 else "south", "chest")
        b.put(cx, 9, cz, S("chain", axis="y"))
        b.lantern(cx, 8, cz)
    # el patio: fuente helada, puestos y faroles
    for x in range(-3, 4):
        for z in range(7, 14):
            d = math.hypot(x, z - 10)
            if d <= 3.2:
                b.put(x, -1, z, S("blue_ice") if d > 2 else S("ice"))
                if 2.2 < d <= 3.2:
                    b.put(x, 0, z, S("packed_ice"))
    b.fill(0, 0, 10, 0, 2, 10, S("packed_ice"))
    b.put(0, 3, 10, S("blue_ice"))
    for x in (-10, 10):
        for z in (6, 12):
            b.put(x, 0, z, S("spruce_fence"))
            b.put(x, 1, z, S("spruce_fence"))
            b.lantern(x, 2, z, hanging=False)
    for _ in range(6):
        x, z = r.randint(-13, 13), r.randint(3, 15)
        if b.get(x, 0, z) is None or short(b.get(x, 0, z)) == "air":
            b.skull(x, 0, z) if r.random() < 0.4 else b.put(x, 0, z, S("snow", layers=2))
    # --- el torreón: x -10..10, z 18..31; planta baja y 0..5, piso y 7..12
    b.room(-9, 0, 19, 9, 12, 30, W, floor=Pal(("spruce_planks", 3), ("stone_bricks", 1)))
    b.fill(-9, 6, 19, 9, 6, 30, S("spruce_planks"))
    for x in range(-10, 11):
        for z in (18, 31):
            for y in (2, 3, 9, 10):
                if x % 4 == 0 and abs(x) > 2:
                    b.put(x, y, z, S("light_blue_stained_glass"))
    for k in range(11):
        for z in range(17, 33):
            b.put(-11 + k, 13 + k // 2, z, stairs("spruce", "east")) if k < 10 else None
            b.put(11 - k, 13 + k // 2, z, stairs("spruce", "west")) if k < 10 else None
    for k in range(10):
        for x in range(-10 + k, 11 - k):
            for z in (18, 31):
                if 13 + k // 2 <= 17:
                    b.put(x, 13 + k // 2, z, W)
    for k in range(10):
        for z in range(17, 33):
            for x in range(-10 + k, 11 - k):
                if b.get(x, 13 + k // 2, z) is None:
                    b.put(x, 13 + k // 2, z, S("spruce_planks"))
    b.air(-1, 0, 18, 1, 3, 18)
    # planta baja: el gran salón
    for x in (-4, 4):
        for z in range(21, 28):
            b.put(x, 0, z, S("spruce_slab", type="top", waterlogged="false"))
            b.put(x + (1 if x > 0 else -1), 0, z, stairs("spruce", "east" if x > 0 else "west"))
    b.fill(-2, 0, 30, 2, 3, 30, S("stone_bricks"))
    b.put(0, 0, 29, S("campfire", facing="north", lit="true", signal_fire="false", waterlogged="false"))
    b.spawner(-6, 0, 24, "minecraft:stray")
    b.spawner(6, 0, 25, "minecraft:zombie")
    for z in (22, 27):
        b.put(0, 5, z, S("chain", axis="y"))
        b.lantern(0, 4, z)
    # la escalera al salón del trono (pegada al muro oeste), con la reja abajo
    straight_stairs(b, -9, -8, 0, 21, 7, "south", "spruce", S("spruce_planks"), gate_first=True)
    b.air(-9, 6, 24, -8, 6, 26)
    for z in range(20, 28):
        b.put(-7, 7, z, S("spruce_fence")) if z < 24 or z > 27 else None
    # el piso de arriba: el salón del trono
    for z in range(20, 30):
        b.put(0, 7, z, S("light_blue_carpet"))
    b.fill(-3, 7, 29, 3, 7, 30, S("packed_ice"))
    b.put(0, 8, 30, stairs("quartz", "south"))
    b.fill(-1, 8, 31, 1, 11, 31, S("blue_ice"), soft=False)
    b.heart(0, 7, 25)
    for x in (-6, 6):
        for z in (22, 28):
            b.fill(x, 7, z, x, 12, z, S("packed_ice"))
    for x in (-4, 4):
        b.put(x, 12, 25, S("chain", axis="y"))
        b.lantern(x, 11, 25, soul=True)
    # la bodega: escalera abajo (pegada al muro este) hasta y -7
    for i in range(7):
        z = 21 + i
        y = -1 - i
        for x in (8, 9):
            b.put(x, y, z, stairs("stone_brick", "north"))
            b.air(x, y + 1, z, x, min(y + 3, -1), z) if y + 1 <= -1 else None
    b.air(8, -1, 21, 9, -1, 22)
    b.room(7, -7, 28, 10, -4, 30, STONE_BRICK)
    for i in range(7):
        b.soft(10, -1 - i, 21 + i, STONE_BRICK)
        b.soft(7, -1 - i, 21 + i, STONE_BRICK)
    Y = -7
    b.corridor((9, 30), (-12, 30), Y, STONE_BRICK)
    rooms = [(-10, "cells"), (-2, "store"), (6, "crypt")]
    r.shuffle(rooms)
    for cx, kind in rooms:
        b.corridor((cx, 31), (cx, 34), Y, STONE_BRICK)
        b.room(cx - 3, Y, 35, cx + 3, Y + 3, 40, STONE_BRICK)
        if kind == "cells":
            for x in range(cx - 3, cx + 4):
                if x != cx:
                    b.fill(x, Y, 37, x, Y + 3, 37, S("iron_bars"))
            b.skull(cx - 2, Y, 39)
            b.put(cx + 2, Y, 39, S("bone_block", axis="y"))
            b.spawner(cx, Y, 39, "minecraft:skeleton")
        elif kind == "store":
            for x in (cx - 3, cx + 3):
                for z in (35, 37, 39):
                    b.put(x, Y, z, S("barrel", facing="up", open="false"), {"id": "minecraft:barrel"})
            b.chest(cx, Y, 40, "north", "chest")
            b.chest(cx - 2, Y, 40, "north", "chest", "barrel")
        else:
            for x in (cx - 2, cx + 2):
                b.put(x, Y, 38, S("packed_ice"))
                b.put(x, Y, 39, S("packed_ice"))
            b.spawner(cx, Y, 38, "minecraft:stray")
        b.lantern(cx, Y + 3, 37)
    place_traps(b, 2, Y)
    secret_room(b, STONE_BRICK, y=Y)
    b.webs(-12, Y, 28, 12, Y + 3, 41, 0.08)
    return b


# ---------------------------------------------------------------------------------------------- 7. el templo de la serpiente
MOSS = Pal(("mossy_stone_bricks", 3), ("mossy_cobblestone", 3), ("cobblestone", 2), ("stone_bricks", 1), ("cracked_stone_bricks", 1))


def spiral3(b, sx, sz, top, depth, wall, mat):
    """Escalera de caracol en un hueco de 3 x 3 (alrededor de un pilar), de 'top' hacia abajo 'depth' peldaños."""
    loop = [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0)]
    b.fill(sx - 2, top - depth - 1, sz - 2, sx + 2, top - 1, sz + 2, wall, soft=True)
    b.fill(sx, top - depth - 1, sz, sx, top, sz, S("chiseled_stone_bricks"), soft=False)
    for i in range(depth):
        ox, oz = loop[i % 8]
        nx, nz = loop[(i + 1) % 8]
        y = top - 1 - i
        b.put(sx + ox, y, sz + oz, stairs(mat, dir_of(ox - nx, oz - nz)))
        for yy in range(y + 1, y + 4):
            if yy < top:
                b.put(sx + ox, yy, sz + oz, AIR)
        for yy in range(y - 1, top - depth - 2, -1):
            if b.get(sx + ox, yy, sz + oz) is None:
                b.put(sx + ox, yy, sz + oz, wall)
    return loop[(depth - 1) % 8]


def serpent(seed):
    """El Templo de la Serpiente: una pirámide de la selva con una escalinata hasta el santuario de arriba, un salón de
    columnas con ofrendas dentro y, debajo, pasillos con canales de agua hasta el cenote donde espera la Sacerdotisa."""
    b = B(seed, "serpent_temple")
    r = b.rnd
    W = MOSS
    b.start = (0, 0, -6)
    for x in range(-17, 18):
        for z in range(-6, 34):
            if r.random() < 0.9:
                b.put(x, -1, z, Pal(("grass_block", 3), ("mossy_cobblestone", 1), ("coarse_dirt", 1), ("moss_block", 1)))
                b.air(x, 0, z, x, 4, z)
    # la pirámide: 4 escalones de 3 de alto
    for t in range(4):
        hs = 13 - 2 * t
        for y in range(3 * t, 3 * t + 3):
            for x in range(-hs, hs + 1):
                for z in range(16 - hs, 17 + hs):
                    edge = max(abs(x), abs(z - 16)) >= hs - 1
                    if edge:
                        b.put(x, y, z, S("chiseled_stone_bricks") if y == 3 * t + 2 and (x + z) % 4 == 0 else W)
                    else:
                        b.soft(x, y, z, S("stone_bricks"))
    for x in range(-13, 14):
        for z in range(3, 30):
            for y in range(-5, 0):
                b.soft(x, y, z, S("stone_bricks"))
    # la escalinata delantera hasta arriba (y 12) y el santuario
    for k in range(12):
        z = 1 + k
        for x in range(-1, 2):
            b.put(x, k, z, stairs("mossy_stone_brick", "south"))
            for yy in range(k + 1, k + 4):
                if yy >= 12 or z < 16 - (13 - 2 * (yy // 3)) or yy < 12:
                    if b.get(x, yy, z) is not None and short(b.get(x, yy, z)) != "air" and yy > k:
                        b.put(x, yy, z, AIR)
        for x in (-2, 2):
            for y in range(0, k + 1):
                b.soft(x, y, z, W)
    b.room(-2, 12, 13, 2, 15, 19, W, floor=P("chiseled_stone_bricks", "mossy_stone_bricks"))
    b.air(-1, 12, 13, 1, 14, 13)
    b.spawner(0, 12, 18, "minecraft:spider")
    b.chest(-2, 12, 19, "east", "chest")
    for x in (-3, 3):
        b.put(x, 16, 16, S("jungle_leaves", persistent="true", distance=1, waterlogged="false"))
    # las puertas laterales y el salón de columnas (y 0..6)
    for side in (-1, 1):
        b.room(side * 12, 0, 15, side * 10, 3, 17, W) if False else None
        b.air(min(side * 13, side * 10), 0, 15, max(side * 13, side * 10), 3, 17)
        b.put(side * 13, 4, 15, S("chiseled_stone_bricks"))
        b.put(side * 13, 4, 17, S("chiseled_stone_bricks"))
    b.room(-9, 0, 7, 9, 6, 25, W, floor=P("mossy_stone_bricks", "stone_bricks", "chiseled_stone_bricks"))
    for side in (-1, 1):
        b.air(side * 10, 0, 15, side * 10, 3, 17)
    place_traps(b, 2, 0)
    for px in (-5, 5):
        for pz in (11, 21):
            b.fill(px, 0, pz, px, 6, pz, S("chiseled_stone_bricks"))
            for y in range(2, 6):
                for d in DIR.values():
                    if r.random() < 0.25:
                        b.put(px + d[0], y, pz + d[1], S("vine", **{k: ("true" if (k == OPP[dir_of(*d)]) else "false") for k in
                                                                  ("north", "south", "east", "west")}, up="false"))
    for x in (-7, 7):
        b.put(x, 0, 9, S("water_cauldron", level=3))
        b.candles(x, 0, 23, "green", 3)
    b.spawner(-7, 0, 15, "minecraft:cave_spider")
    b.spawner(7, 0, 19, "minecraft:zombie")
    b.chest(0, 0, 24, "north", "chest")
    for x, z in ((-8, 8), (8, 24), (-8, 24), (8, 8)):
        b.put(x, 5, z, S("chain", axis="y"))
        b.lantern(x, 4, z)
    # la bajada al nivel de los canales (y -12)
    Y = -12
    b.room(-1, Y, 15, 1, Y + 3, 17, W)
    spiral3(b, 0, 16, 0, 12, S("stone_bricks"), "mossy_stone_brick")
    b.corridor((0, 20), (0, 38), Y, W, width=5, height=5)
    for z in range(19, 38):
        for x in (-2, 2):
            b.put(x, Y - 1, z, S("water", level=0))
            b.put(x, Y - 2, z, W)
    # las salas a los lados del canal
    for side, zc, kind in ((-1, 24, "burial"), (1, 24, "nest"), (-1, 32, "offering"), (1, 32, "burial")):
        b.corridor((side * 3, zc), (side * 6, zc), Y, W)
        cx = side * 9
        b.room(cx - 2, Y, zc - 2, cx + 2, Y + 3, zc + 2, W)
        for z in range(zc - 1, zc + 2):
            for x in (side * 2,):
                b.put(x, Y - 1, z, S("mossy_stone_bricks"))
        if kind == "burial":
            b.spawner(cx, Y, zc, "minecraft:skeleton")
            b.chest(cx + side * 2, Y, zc - 2, "south", "chest")
        elif kind == "nest":
            b.spawner(cx, Y, zc, "minecraft:silverfish")
            b.webs(cx - 2, Y, zc - 2, cx + 2, Y + 3, zc + 2, 0.4)
        else:
            b.put(cx + side * 2, Y, zc, S("chiseled_stone_bricks"))
            b.put(cx + side * 2, Y + 1, zc, S("emerald_block"))
            b.chest(cx, Y, zc + 2, "north", "chest")
    # el cenote: anillo de agua con cuatro puentes y la isla del Corazón
    cz, R = 52, 10
    b.corridor((0, 38), (0, 41), Y, W)
    for x in range(-1, 2):
        for y in range(Y, Y + 4):
            b.gate(x, y, 40)
    for x in range(-R - 1, R + 2):
        for z in range(cz - R - 1, cz + R + 2):
            d = math.hypot(x, z - cz)
            if d < R + 0.5:
                b.soft(x, Y - 1, z, W)
                b.soft(x, Y + 10, z, W)
                b.air(x, Y, z, x, Y + 9, z)
                if 6.5 < d < 8.5 and abs(x) > 1 and abs(z - cz) > 1:
                    b.put(x, Y - 1, z, S("water", level=0))
                    b.put(x, Y - 2, z, S("water", level=0))
                    b.soft(x, Y - 3, z, W)
            elif d < R + 1.7:
                for y in range(Y - 3, Y + 11):
                    b.soft(x, y, z, W)
    b.air(-1, Y, cz - R - 1, 1, Y + 3, cz - R)
    b.fill(-1, Y, cz - 1, 1, Y, cz + 1, S("chiseled_stone_bricks"))
    b.heart(0, Y + 1, cz)
    for a in range(0, 360, 45):
        x, z = int(round(9.5 * math.cos(math.radians(a)))), cz + int(round(9.5 * math.sin(math.radians(a))))
        b.fill(x, Y + 5, z, x, Y + 9, z, S("vine", north="false", south="false", east="false", west="false", up="true"))
        x2, z2 = int(round(4 * math.cos(math.radians(a + 22)))), cz + int(round(4 * math.sin(math.radians(a + 22))))
        b.put(x2, Y + 9, z2, S("sea_lantern"))
    b.webs(-12, Y, 14, 12, Y + 5, 64, 0.05)
    secret_room(b, W, y=Y)
    return b


# ---------------------------------------------------------------------------------------------- 8. la prisión olvidada
PRISON = Pal(("stone_bricks", 4), ("cracked_stone_bricks", 2), ("polished_andesite", 2), ("andesite", 1), ("mossy_stone_bricks", 1))


def prison(seed):
    """La Prisión Olvidada: una caseta de guardia sobre un pozo con escalera de caracol baja a dos galerías de celdas
    (algunas abiertas, con presos que vuelven a la vida), la sala de guardia, la armería, la sala de tortura y el despacho
    del alcaide; al fondo, el patio de ejecuciones del Carcelero."""
    b = B(seed, "forgotten_prison")
    r = b.rnd
    W = PRISON
    D = 22
    Y = -D
    b.start = (0, 0, -7)
    for x in range(-6, 7):
        for z in range(-6, 7):
            b.put(x, -1, z, Pal(("cobblestone", 2), ("stone_bricks", 2), ("gravel", 1)))
            b.air(x, 0, z, x, 6, z)
    for x in range(-5, 6):
        for z in range(-5, 6):
            if max(abs(x), abs(z)) == 5 and not (abs(x) <= 1 and z == -5):
                for y in range(0, 4):
                    b.put(x, y, z, W)
                if (x + z) % 2 == 0:
                    b.put(x, 4, z, W)
    b.fill(-1, 3, -5, 1, 3, -5, S("iron_bars"))
    spiral_down(b, D, W, "stone_brick", (0, 2), core=S("polished_andesite"))
    b.room(-1, Y, 3, 1, Y + 3, 5, W)
    # la galería principal y las celdas
    L = 52
    b.corridor((0, 4), (0, L), Y, W)
    cross = r.sample([14, 18, 22, 26, 30, 34, 38], 2)
    cross.sort()
    open_cells = []
    for z in range(6, L - 3, 4):
        if any(abs(z - c) <= 2 for c in cross):
            continue
        for side in (-1, 1):
            x0, x1 = (side * 3, side * 6) if side > 0 else (side * 6, side * 3)
            b.room(x0, Y, z, x1, Y + 3, z + 2, W)
            front = side * 2
            is_open = r.random() < 0.35
            for zz in range(z, z + 3):
                for y in range(Y, Y + 4):
                    b.put(front, y, zz, S("iron_bars") if not (is_open and zz == z + 1 and y < Y + 2) else AIR)
            far = side * 6
            if is_open:
                open_cells.append((far, z + 1))
            else:
                kind = r.choice(["bones", "hay", "web", "chain"])
                if kind == "bones":
                    b.put(far, Y, z + 1, S("bone_block", axis="z"))
                    b.skull(far - side, Y, z + 2)
                elif kind == "hay":
                    b.put(far, Y, z, S("hay_block", axis="y"))
                elif kind == "web":
                    b.webs(min(x0, x1), Y, z, max(x0, x1), Y + 3, z + 2, 0.5)
                else:
                    b.put(far, Y + 3, z + 1, S("chain", axis="y"))
                    b.put(far, Y + 2, z + 1, S("chain", axis="y"))
    r.shuffle(open_cells)
    for n, (x, z) in enumerate(open_cells[:5]):
        if n < 3:
            b.spawner(x, Y, z, "minecraft:zombie")
        else:
            b.chest(x, Y, z, "east" if x < 0 else "west", "chest")
    for z in range(8, L, 8):
        b.put(0, Y + 3, z, S("chain", axis="y"))
        b.lantern(0, Y + 2, z)
    # las salas de los lados (por los pasillos que cruzan)
    kinds = ["guard", "armory", "torture", "office"]
    r.shuffle(kinds)
    for (zc, side), kind in zip([(cross[0], -1), (cross[0], 1), (cross[1], -1), (cross[1], 1)], kinds):
        b.corridor((0, zc), (side * 12, zc), Y, W)
        cx = side * 16
        b.room(cx - 4, Y, zc - 3, cx + 4, Y + 4, zc + 3, W)
        if kind == "guard":
            b.fill(cx - 1, Y, zc - 1, cx + 1, Y, zc + 1, S("spruce_planks"))
            b.candles(cx, Y + 1, zc)
            b.chest(cx + side * 4, Y, zc, "east" if side < 0 else "west", "chest")
            b.spawner(cx - side * 2, Y, zc + 3, "minecraft:skeleton")
        elif kind == "armory":
            for z in range(zc - 3, zc + 4):
                b.put(cx + side * 4, Y, z, S("barrel", facing="up", open="false"), {"id": "minecraft:barrel"})
            b.chest(cx + side * 4, Y, zc, "east" if side < 0 else "west", "chest", "barrel")
            b.put(cx, Y, zc - 3, S("grindstone", face="floor", facing="north"))
            b.put(cx + 2, Y, zc - 3, S("smithing_table"))
        elif kind == "torture":
            for x, z in ((cx - 2, zc - 2), (cx + 2, zc + 2), (cx - 2, zc + 2)):
                b.put(x, Y, z, S("anvil", facing="north"))
            b.put(cx, Y, zc, S("water_cauldron", level=2))
            for y in range(Y + 1, Y + 5):
                b.put(cx + 2, y, zc - 2, S("chain", axis="y"))
            b.spawner(cx + side * 3, Y, zc, "minecraft:zombie")
        else:
            for z in range(zc - 3, zc + 4):
                for y in range(Y, Y + 3):
                    b.put(cx + side * 4, y, z, S("bookshelf"))
            b.put(cx, Y, zc, S("lectern", facing="north", has_book="false", powered="false"))
            b.chest(cx + side * 3, Y, zc + 3, "north", "chest")
        b.lantern(cx, Y + 4, zc)
    # el patio de ejecuciones (la arena del Carcelero)
    b.corridor((0, L), (0, L + 3), Y, W)
    for x in range(-1, 2):
        for y in range(Y, Y + 4):
            b.gate(x, y, L + 1)
    z0, z1 = L + 4, L + 24
    b.room(-11, Y, z0, 11, Y + 9, z1, W, floor=Pal(("cobblestone", 3), ("gravel", 0), ("stone", 2), ("andesite", 1)))
    for gx in (-6, 6):
        gz = z1 - 6
        b.fill(gx - 2, Y, gz, gx + 2, Y, gz, S("dark_oak_planks"))
        for x in (gx - 2, gx + 2):
            b.fill(x, Y + 1, gz, x, Y + 5, gz, S("dark_oak_fence"))
        b.fill(gx - 2, Y + 6, gz, gx + 2, Y + 6, gz, S("dark_oak_planks"))
        for x in (gx - 1, gx + 1):
            b.put(x, Y + 5, gz, S("chain", axis="y"))
            b.put(x, Y + 4, gz, S("chain", axis="y"))
    for x in (-10, 10):
        for z in range(z0 + 2, z1 - 1, 5):
            b.fill(x, Y, z, x, Y + 2, z, S("iron_bars"))
            b.fill(x + (1 if x < 0 else -1), Y + 3, z, x, Y + 3, z, S("iron_bars"))
    b.heart(0, Y, z0 + 11)
    for x in (-5, 0, 5):
        b.put(x, Y + 9, z0 + 6, S("chain", axis="y"))
        b.lantern(x, Y + 8, z0 + 6)
    place_traps(b, 3, Y)
    secret_room(b, W, y=Y)
    b.webs(-20, Y, 4, 20, Y + 4, L, 0.06)
    return b


# ---------------------------------------------------------------------------------------------- 9. las cloacas olvidadas
SEWER = Pal(("bricks", 4), ("mossy_stone_bricks", 2), ("stone_bricks", 2), ("cracked_stone_bricks", 1), ("mud_bricks", 1))


def sewers(seed):
    """Las Cloacas Olvidadas: una rejilla de túneles de ladrillo con canales de agua (un laberinto distinto en cada una),
    fosos de limos, nidos de lepismas y almacenes inundados; al fondo, la Gran Cisterna del Rey Baboso."""
    b = B(seed, "forgotten_sewers")
    r = b.rnd
    W = SEWER
    D = 16
    Y = -D
    b.start = (0, 0, -6)
    for x in range(-4, 5):
        for z in range(-4, 5):
            b.put(x, -1, z, Pal(("cobblestone", 2), ("mossy_cobblestone", 2), ("gravel", 1)))
            b.air(x, 0, z, x, 4, z)
    for x in range(-4, 5):
        for z in range(-4, 5):
            if max(abs(x), abs(z)) == 4 and r.random() < 0.7:
                b.put(x, 0, z, S("cobblestone_wall", up="true"))
    spiral_down(b, D, W, "brick", (0, 2), core=S("bricks"))
    for z in (-3, 3):
        b.put(0, 0, z, S("iron_bars"))
    b.corridor((0, 5), (0, 6), Y, W, width=5, height=5)
    cols = [-16, -8, 0, 8, 16]
    cell = {(i, j): (cols[i], 10 + 8 * j) for i in range(5) for j in range(5)}
    edges = maze(r, 5, 5, (2, 0), 0.15)
    dist, adj = distances(edges, (2, 0))
    b.corridor((0, 6), cell[(2, 0)], Y, W, width=5, height=5)
    for e in edges:
        a, c = tuple(e)
        b.corridor(cell[a], cell[c], Y, W, width=5, height=5)
    # el canal: agua en el centro del túnel, menos en los cruces
    for e in edges:
        (ax, az), (cx, cz) = cell[tuple(e)[0]], cell[tuple(e)[1]]
        if az == cz:
            for x in range(min(ax, cx) + 3, max(ax, cx) - 2):
                b.put(x, Y - 1, az, S("water", level=0))
                b.soft(x, Y - 2, az, W)
        else:
            for z in range(min(az, cz) + 3, max(az, cz) - 2):
                b.put(ax, Y - 1, z, S("water", level=0))
                b.soft(ax, Y - 2, z, W)
    for c, (cx, cz) in cell.items():
        if r.random() < 0.4:
            b.put(cx, Y + 4, cz, S("chain", axis="y"))
            b.lantern(cx, Y + 3, cz)
    # la Gran Cisterna, detrás de la celda más lejana de la última fila
    back = max((c for c in cell if c[1] == 4), key=lambda c: dist[c])
    bx, bz = cell[back]
    b.corridor((bx, bz + 2), (bx, bz + 6), Y, W)
    for x in range(bx - 1, bx + 2):
        for y in range(Y, Y + 4):
            b.gate(x, y, bz + 5)
    z0 = bz + 7
    b.room(bx - 11, Y, z0, bx + 11, Y + 10, z0 + 22, W, floor=Pal(("bricks", 2), ("mossy_stone_bricks", 1), ("mud_bricks", 1)))
    for px in (bx - 6, bx + 5):
        for pz in (z0 + 5, z0 + 16):
            b.fill(px, Y, pz, px + 1, Y + 10, pz + 1, S("bricks"))
    for qx in (bx - 10, bx + 7):
        for qz in (z0 + 1, z0 + 18):
            b.fill(qx, Y - 1, qz, qx + 2, Y - 1, qz + 2, S("water", level=0))
            b.fill(qx, Y - 2, qz, qx + 2, Y - 2, qz + 2, S("mud_bricks"))
    b.fill(bx - 1, Y, z0 + 10, bx + 1, Y, z0 + 12, S("mossy_stone_bricks"))
    b.heart(bx, Y + 1, z0 + 11)
    for x in (bx - 8, bx, bx + 8):
        b.put(x, Y + 10, z0 + 11, S("chain", axis="y"))
        b.lantern(x, Y + 9, z0 + 11)
    # las salas en los callejones sin salida
    deads = [c for c in cell if len(adj[c]) == 1 and c not in ((2, 0), back)]
    r.shuffle(deads)
    others = [c for c in cell if c not in deads and c not in ((2, 0), back)]
    r.shuffle(others)
    kinds = ["slime", "rats", "store", "slime", "drain", "store", "rats"]
    for n, c in enumerate(deads + others[:max(0, 5 - len(deads))]):
        cx, cz = cell[c]
        kind = kinds[n % len(kinds)]
        b.room(cx - 3, Y, cz - 3, cx + 3, Y + 4, cz + 3, W)
        if kind == "slime":
            b.air(cx - 1, Y - 3, cz - 1, cx + 1, Y - 1, cz + 1)
            b.room(cx - 1, Y - 3, cz - 1, cx + 1, Y - 1, cz + 1, W)
            b.spawner(cx, Y - 3, cz, "minecraft:slime", count=2)
            for x in range(cx - 1, cx + 2):
                b.put(x, Y - 2, cz - 1, S("ladder", facing="south", waterlogged="false")) if x == cx else None
        elif kind == "rats":
            b.spawner(cx + r.choice((-2, 2)), Y, cz + r.choice((-2, 2)), "minecraft:silverfish", count=4, near=8)
            b.webs(cx - 3, Y, cz - 3, cx + 3, Y + 4, cz + 3, 0.3)
        elif kind == "store":
            for x in (cx - 3, cx + 3):
                for z in (cz - 2, cz, cz + 2):
                    if r.random() < 0.6:
                        b.put(x, Y, z, S("barrel", facing="up", open="false"), {"id": "minecraft:barrel"})
            b.chest(cx, Y, cz + 3, "north", "chest")
            b.spawner(cx - 2, Y, cz - 3, "minecraft:zombie")
        else:
            for x in range(cx - 1, cx + 2):
                for z in range(cz - 1, cz + 2):
                    b.put(x, Y - 1, z, S("iron_bars") if (x + z) % 2 else S("water", level=0))
                    b.soft(x, Y - 2, z, W)
            b.chest(cx + 3, Y, cz, "west", "chest")
    place_traps(b, 3, Y)
    secret_room(b, W, y=Y)
    b.webs(-24, Y, 4, 24, Y + 4, bz + 6, 0.05)
    return b


# ---------------------------------------------------------------------------------------------- 10. el santuario de las almas
def shrine(seed):
    """El Santuario de las Almas: un anillo de obeliscos de piedra negra guarda un pozo con escalera de caracol que baja a
    una nave de columnas con hogueras de almas y capillas a los lados; un puente cruza la sima de fuego de almas hasta la
    cúpula del altar, donde arde el Heraldo de las Almas."""
    b = B(seed, "soul_shrine")
    r = b.rnd
    W = BLACK_BRICK
    D = 24
    Y = -D
    b.start = (0, 0, -9)
    for x in range(-8, 9):
        for z in range(-8, 9):
            d = math.hypot(x, z)
            if d < 8.5:
                b.put(x, -1, z, Pal(("soul_soil", 2), ("blackstone", 2), ("gravel", 1), ("coarse_dirt", 2)))
                b.air(x, 0, z, x, 8, z)
    for a in range(0, 360, 60):
        x, z = int(round(6.5 * math.cos(math.radians(a)))), int(round(6.5 * math.sin(math.radians(a))))
        h = r.randint(3, 7)
        b.fill(x, 0, z, x, h, z, S("polished_blackstone_bricks"))
        b.put(x, h + 1, z, S("soul_lantern", hanging="false", waterlogged="false"))
    spiral_down(b, D, W, "polished_blackstone_brick", (0, 2), core=S("polished_blackstone"))
    for y in range(-D, 0, 5):
        b.put(0, y, -2, S("soul_lantern", hanging="false", waterlogged="false"))
    b.room(-3, Y, 3, 3, Y + 4, 8, W)
    # la nave
    z0, z1 = 9, 42
    b.room(-5, Y, z0, 5, Y + 11, z1, W, floor=Pal(("polished_blackstone", 3), ("polished_blackstone_bricks", 2), ("gilded_blackstone", 0.3)))
    b.air(-1, Y, 8, 1, Y + 3, 9)
    chapels = []
    for pz in range(z0 + 2, z1 - 1, 6):
        for x in (-4, 4):
            b.fill(x, Y, pz, x, Y + 11, pz, S("polished_blackstone"))
            b.put(x, Y + 10, pz, S("gilded_blackstone"))
        for side in (-1, 1):
            cz = pz + 3
            if cz > z1 - 2:
                continue
            cx = side * 8
            b.room(cx - 2, Y, cz - 2, cx + 2, Y + 4, cz + 2, W)
            b.air(side * 5, Y, cz - 1, side * 6, Y + 3, cz + 1)
            chapels.append((cx, cz, side))
        b.put(0, Y, pz + 3, S("soul_campfire", facing="north", lit="true", signal_fire="false", waterlogged="false")) if r.random() < 0.6 else None
        b.put(0, Y + 11, pz, S("chain", axis="y"))
        b.put(0, Y + 10, pz, S("chain", axis="y"))
        b.put(0, Y + 9, pz, S("soul_lantern", hanging="true", waterlogged="false"))
    r.shuffle(chapels)
    plan = ["spawner", "spawner", "spawner", "spawner", "chest", "chest", "chest", "shrine", "books", "shrine"]
    mobs = ["minecraft:skeleton", "minecraft:wither_skeleton", "minecraft:skeleton", "minecraft:zombie"]
    for (cx, cz, side), kind in zip(chapels, plan):
        far = cx + side * 2
        if kind == "spawner":
            b.spawner(cx, Y, cz, mobs.pop())
        elif kind == "chest":
            b.chest(far, Y, cz, "east" if side < 0 else "west", "chest")
            b.candles(far, Y, cz - 1, "black")
        elif kind == "shrine":
            b.put(far, Y, cz, S("soul_soil"))
            b.put(far, Y + 1, cz, S("soul_fire"))
            b.skull(far, Y, cz - 1) if False else None
        else:
            for z in range(cz - 2, cz + 3):
                b.fill(far, Y, z, far, Y + 2, z, S("bookshelf"))
            b.candles(cx, Y, cz, "purple")
    # la sima y el puente
    gz0, gz1 = z1 + 1, z1 + 8
    b.room(-5, Y - 14, gz0, 5, Y + 8, gz1, W)
    for x in range(-5, 6):
        for z in range(gz0, gz1 + 1):
            b.put(x, Y - 14, z, S("soul_soil"))
            if r.random() < 0.5:
                b.put(x, Y - 13, z, S("soul_fire"))
    for z in range(gz0, gz1 + 1):
        for x in range(-1, 2):
            b.put(x, Y - 1, z, S("polished_blackstone_bricks"))
        for x in (-2, 2):
            b.put(x, Y - 1, z, S("polished_blackstone_bricks"))
            b.put(x, Y, z, S("polished_blackstone_brick_wall", up="true"))
    b.air(-1, Y, z1, 1, Y + 3, z1 + 1)
    # la cúpula del altar
    cz, R = gz1 + 13, 11
    b.corridor((0, gz1), (0, gz1 + 2), Y, W)
    for x in range(-R - 1, R + 2):
        for z in range(cz - R - 1, cz + R + 2):
            d = math.hypot(x, z - cz)
            for y in range(Y - 1, Y + 13):
                dd = math.sqrt(x * x + (z - cz) ** 2 + max(0, y - Y - 4) ** 2 * 1.6)
                if dd < R + 0.5 and y >= Y:
                    b.put(x, y, z, AIR)
                elif dd < R + 1.7 or (y == Y - 1 and d < R + 1.7):
                    b.soft(x, y, z, W)
    for x in range(-R, R + 1):
        for z in range(cz - R, cz + R + 1):
            if b.is_air(x, Y, z) and r.random() < 0.25:
                b.put(x, Y - 1, z, S("soul_soil"))
    b.air(-1, Y, cz - R - 1, 1, Y + 3, cz - R + 1)
    for x in range(-1, 2):
        for y in range(Y, Y + 4):
            b.gate(x, y, gz1 + 1)
    b.fill(-2, Y, cz - 2, 2, Y, cz + 2, S("polished_blackstone_bricks"))
    b.fill(-1, Y + 1, cz - 1, 1, Y + 1, cz + 1, S("gilded_blackstone"))
    b.heart(0, Y + 2, cz)
    for a in range(0, 360, 45):
        x, z = int(round(7 * math.cos(math.radians(a)))), cz + int(round(7 * math.sin(math.radians(a))))
        b.put(x, Y, z, S("soul_campfire", facing="north", lit="true", signal_fire="false", waterlogged="false"))
    secret_room(b, W, y=Y)
    b.webs(-12, Y, 3, 12, Y + 5, z1, 0.03)
    return b


# ---------------------------------------------------------------------------------------------- el catálogo
LAND = ["plains", "sunflower_plains", "forest", "flower_forest", "birch_forest", "old_growth_birch_forest", "dark_forest", "taiga",
        "old_growth_pine_taiga", "old_growth_spruce_taiga", "snowy_taiga", "snowy_plains", "meadow", "cherry_grove", "savanna",
        "savanna_plateau", "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "windswept_savanna", "jungle",
        "sparse_jungle", "bamboo_jungle", "swamp", "badlands", "wooded_badlands", "desert", "grove", "snowy_slopes", "stony_shore"]
# id: (constructor, subterránea, biomas, nombre es, nombre en, jefe es, jefe en, reliquia)
DUNGEONS = collections.OrderedDict([
    ("forgotten_mausoleum", (mausoleum, False,
                             ["plains", "sunflower_plains", "forest", "birch_forest", "old_growth_birch_forest", "dark_forest", "taiga",
                              "meadow", "flower_forest", "snowy_plains", "snowy_taiga", "old_growth_pine_taiga", "savanna"],
                             "Mausoleo del Rey Olvidado", "Mausoleum of the Forgotten King", "el Rey Olvidado", "the Forgotten King",
                             ("minecraft:golden_helmet", "Corona del Rey Olvidado", "Crown of the Forgotten King",
                              {"minecraft:protection": 4, "minecraft:unbreaking": 3, "minecraft:mending": 1}))),
    ("mad_mage_spire", (spire, False,
                        ["forest", "dark_forest", "birch_forest", "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "swamp",
                         "plains", "windswept_hills", "windswept_forest", "meadow", "cherry_grove", "snowy_taiga", "grove"],
                        "Torre del Hechicero Loco", "Spire of the Mad Mage", "el Archimago Demente", "the Mad Archmage",
                        ("minecraft:enchanted_book", "Grimorio del Archimago", "Grimoire of the Archmage",
                         {"minecraft:mending": 1, "minecraft:unbreaking": 3, "minecraft:efficiency": 5}))),
    ("pharaoh_tomb", (pharaoh, False, ["desert"], "Tumba del Faraón Maldito", "Tomb of the Cursed Pharaoh", "el Faraón Maldito",
                      "the Cursed Pharaoh",
                      ("minecraft:golden_sword", "Cetro del Faraón", "Scepter of the Pharaoh",
                       {"minecraft:sharpness": 5, "minecraft:looting": 3, "minecraft:unbreaking": 3, "minecraft:fire_aspect": 2}))),
    ("dwarven_forge", (forge, True, LAND, "Forja Enana Olvidada", "Forgotten Dwarven Forge", "el Capataz de la Forja",
                       "the Forgemaster",
                       ("minecraft:diamond_pickaxe", "Pico del Capataz", "Forgemaster's Pick",
                        {"minecraft:efficiency": 5, "minecraft:fortune": 3, "minecraft:unbreaking": 3}))),
    ("spider_nest", (nest, True, [b for b in LAND if b not in ("desert", "badlands", "wooded_badlands", "snowy_slopes", "grove")],
                     "Nido de la Reina Araña", "Nest of the Spider Queen", "la Reina Madre", "the Brood Mother",
                     ("minecraft:diamond_sword", "Colmillo de la Reina", "Fang of the Queen",
                      {"minecraft:bane_of_arthropods": 5, "minecraft:sharpness": 3, "minecraft:unbreaking": 3, "minecraft:looting": 2}))),
    ("frozen_keep", (keep, False, ["snowy_plains", "snowy_taiga", "ice_spikes", "grove", "snowy_beach"],
                     "Fortaleza Helada", "Frozen Keep", "el Señor del Invierno", "the Winter Lord",
                     ("minecraft:bow", "Arco del Invierno", "Winter's Bow",
                      {"minecraft:power": 5, "minecraft:punch": 2, "minecraft:infinity": 1, "minecraft:unbreaking": 3}))),
    ("serpent_temple", (serpent, False, ["jungle", "sparse_jungle", "bamboo_jungle"],
                        "Templo de la Serpiente", "Serpent Temple", "la Sacerdotisa Serpiente", "the Serpent Priestess",
                        ("minecraft:trident", "Colmillo de la Serpiente", "Serpent's Fang",
                         {"minecraft:loyalty": 3, "minecraft:impaling": 4, "minecraft:unbreaking": 3}))),
    ("forgotten_prison", (prison, True, LAND, "Prisión Olvidada", "Forgotten Prison", "el Carcelero", "the Jailer",
                          ("minecraft:diamond_axe", "Hacha del Carcelero", "Jailer's Axe",
                           {"minecraft:sharpness": 5, "minecraft:efficiency": 4, "minecraft:unbreaking": 3}))),
    ("forgotten_sewers", (sewers, True, [b for b in LAND if b not in ("desert", "badlands", "wooded_badlands", "snowy_slopes", "grove")],
                          "Cloacas Olvidadas", "Forgotten Sewers", "el Rey Baboso", "the Slime King",
                          ("minecraft:diamond_boots", "Botas del Rey Baboso", "Slime King's Boots",
                           {"minecraft:feather_falling": 4, "minecraft:protection": 4, "minecraft:depth_strider": 3,
                            "minecraft:unbreaking": 3}))),
    ("soul_shrine", (shrine, True, LAND, "Santuario de las Almas", "Shrine of Souls", "el Heraldo de las Almas", "the Soul Herald",
                     ("minecraft:diamond_chestplate", "Coraza del Heraldo", "Herald's Cuirass",
                      {"minecraft:protection": 4, "minecraft:thorns": 3, "minecraft:unbreaking": 3}))),
])
THEME = {
    "forgotten_mausoleum": [("minecraft:bone", 2, 8, 6), ("minecraft:rotten_flesh", 2, 6, 4), ("minecraft:candle", 1, 4, 3),
                            ("minecraft:gold_nugget", 3, 12, 5), ("minecraft:iron_ingot", 1, 4, 4), ("minecraft:name_tag", 1, 1, 1)],
    "mad_mage_spire": [("minecraft:lapis_lazuli", 3, 10, 6), ("minecraft:amethyst_shard", 2, 6, 5), ("minecraft:glowstone_dust", 2, 8, 4),
                       ("minecraft:redstone", 3, 10, 4), ("minecraft:ender_pearl", 1, 2, 3), ("minecraft:experience_bottle", 2, 6, 4),
                       ("minecraft:paper", 2, 8, 3)],
    "pharaoh_tomb": [("minecraft:gold_ingot", 2, 6, 6), ("minecraft:gold_nugget", 4, 16, 5), ("minecraft:lapis_lazuli", 2, 8, 4),
                     ("minecraft:emerald", 1, 4, 3), ("minecraft:bone", 2, 6, 4), ("minecraft:string", 2, 6, 3)],
    "dwarven_forge": [("minecraft:iron_ingot", 3, 10, 6), ("minecraft:gold_ingot", 2, 6, 4), ("minecraft:raw_iron", 3, 8, 4),
                      ("minecraft:coal", 4, 16, 5), ("minecraft:copper_ingot", 4, 12, 3), ("minecraft:iron_block", 1, 1, 1)],
    "frozen_keep": [("minecraft:arrow", 6, 16, 6), ("minecraft:snowball", 4, 12, 4), ("minecraft:blue_ice", 1, 4, 3),
                    ("minecraft:iron_ingot", 2, 6, 5), ("minecraft:emerald", 1, 4, 3), ("minecraft:spectral_arrow", 4, 10, 3)],
    "serpent_temple": [("minecraft:emerald", 2, 6, 6), ("minecraft:gold_ingot", 2, 5, 5), ("minecraft:cocoa_beans", 2, 6, 3),
                       ("minecraft:bamboo", 3, 10, 3), ("minecraft:green_dye", 2, 6, 3),
                       ("minecraft:turtle_scute", 1, 2, 1)],
    "forgotten_prison": [("minecraft:iron_ingot", 3, 8, 6), ("minecraft:chain", 2, 6, 4), ("minecraft:bone", 2, 6, 4),
                         ("minecraft:iron_nugget", 6, 18, 4), ("minecraft:bread", 1, 3, 3), ("minecraft:crossbow", 1, 1, 1)],
    "forgotten_sewers": [("minecraft:slime_ball", 3, 10, 7), ("minecraft:rotten_flesh", 2, 6, 4), ("minecraft:iron_nugget", 4, 12, 4),
                         ("minecraft:gold_nugget", 4, 12, 4), ("minecraft:lead", 1, 2, 2), ("minecraft:emerald", 1, 3, 3)],
    "soul_shrine": [("minecraft:soul_sand", 2, 6, 3), ("minecraft:blaze_rod", 1, 4, 5), ("minecraft:gold_ingot", 2, 6, 5),
                    ("minecraft:quartz", 4, 12, 4), ("minecraft:gilded_blackstone", 1, 3, 2), ("minecraft:ender_pearl", 1, 2, 3)],
    "spider_nest": [("minecraft:string", 3, 12, 7), ("minecraft:spider_eye", 1, 4, 5), ("minecraft:bone", 2, 6, 4),
                    ("minecraft:iron_ingot", 1, 4, 3), ("minecraft:gold_ingot", 1, 3, 3), ("minecraft:cobweb", 1, 3, 2)],
}


def le(name, lo=1, hi=1, weight=1, extra=()):
    f = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False}] if hi > 1 else []
    return {"type": "minecraft:item", "name": name, "weight": weight, "functions": f + list(extra)}


def ench_levels(a, b):
    return {"function": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": a, "max": b}}


def pool(rolls, entries, chance=None):
    p = {"rolls": rolls if isinstance(rolls, (int, float)) else {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]},
         "bonus_rolls": 0, "entries": entries}
    if chance is not None:
        p["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return p


def loots(did):
    theme = [le(n, a, c, w) for n, a, c, w in THEME[did]]
    common = [le("minecraft:bread", 1, 4, 4), le("minecraft:arrow", 4, 12, 4), le("minecraft:torch", 3, 8, 3), le(f"{NS}:glory_coin", 1, 4, 4),
              le("minecraft:book", 1, 1, 2, [{"function": "minecraft:enchant_randomly"}]), le("minecraft:golden_apple", 1, 1, 1),
              le("minecraft:iron_sword", 1, 1, 1, [ench_levels(5, 15)]), le("minecraft:saddle", 1, 1, 1)]
    chest = {"type": "minecraft:chest", "pools": [pool((3, 6), theme + common), pool(1, [le("minecraft:diamond", 1, 2)], 0.25)]}
    secret = {"type": "minecraft:chest", "pools": [
        pool((4, 7), theme + [le("minecraft:diamond", 1, 3, 4), le("minecraft:emerald", 2, 6, 4), le(f"{NS}:glory_coin", 4, 10, 5),
                              le("minecraft:golden_apple", 1, 2, 3), le("minecraft:experience_bottle", 3, 8, 3)]),
        pool(1, [le("minecraft:book", 1, 1, 1, [ench_levels(20, 30)])]),
        pool(1, [le("minecraft:enchanted_golden_apple")], 0.08)]}
    item, es, en, enchants = DUNGEONS[did][7]
    relic = {"type": "minecraft:item", "name": item, "functions": [
        {"function": "minecraft:set_enchantments", "enchantments": enchants, "add": False},
        {"function": "minecraft:set_name", "name": {"translate": f"item.{NS}.relic.{did}", "italic": False, "color": "gold"},
         "target": "custom_name"},
        {"function": "minecraft:set_lore", "lore": [{"translate": f"item.{NS}.relic.{did}.lore", "italic": True, "color": "gray"}],
         "mode": "replace_all"}]}
    gear = [le("minecraft:diamond_" + p, 1, 1, 2, [ench_levels(25, 35)]) for p in ("helmet", "chestplate", "leggings", "boots", "sword", "axe")]
    boss = {"type": "minecraft:chest", "pools": [
        pool(1, [relic]),
        pool((5, 8), [le("minecraft:diamond", 2, 5, 5), le("minecraft:emerald", 4, 10, 5), le("minecraft:gold_ingot", 4, 10, 5),
                      le(f"{NS}:glory_coin", 8, 20, 6), le("minecraft:iron_ingot", 6, 14, 4), le("minecraft:experience_bottle", 4, 10, 4),
                      le("minecraft:golden_apple", 1, 3, 3)] + theme),
        pool((1, 2), gear),
        pool((1, 2), [le("minecraft:book", 1, 1, 1, [ench_levels(30, 39)])]),
        pool(1, [le("minecraft:enchanted_golden_apple")], 0.2),
        pool(1, [le("minecraft:totem_of_undying")], 0.25),
        pool(1, [le("minecraft:netherite_scrap", 1, 2)], 0.35),
        pool(1, [le("minecraft:music_disc_otherside"), le("minecraft:music_disc_pigstep"), le("minecraft:music_disc_relic")], 0.15)]}
    return {"chest": chest, "secret": secret, "boss": boss}


def lang():
    out = collections.OrderedDict()
    out[f"block.{NS}.dungeon_heart"] = ("Corazón de la mazmorra", "Dungeon Heart")
    out[f"block.{NS}.dungeon_gate"] = ("Reja de la mazmorra", "Dungeon Gate")
    out[f"message.{NS}.dungeon.awaken"] = ("%s despierta. ¡Las rejas se cierran!", "%s awakens. The gates slam shut!")
    out[f"message.{NS}.dungeon.cleared"] = ("%s ha caído: %s superada", "%s has fallen: %s cleared")
    out[f"message.{NS}.dungeon.rage"] = ("%s se enfurece", "%s becomes enraged")
    out[f"message.{NS}.dungeon.reset"] = ("La mazmorra vuelve a dormirse", "The dungeon falls asleep again")
    for did, (fn, under, biomes, es, en, bes, ben, relic) in DUNGEONS.items():
        out[f"dungeon.{NS}.{did}"] = (es, en)
        out[f"boss.{NS}.{did}"] = (bes[0].upper() + bes[1:], ben[0].upper() + ben[1:])
        out[f"item.{NS}.relic.{did}"] = (relic[1], relic[2])
        out[f"item.{NS}.relic.{did}.lore"] = ("Botín de " + bes, "Taken from " + ben)
    return out


def textures():
    from PIL import Image
    base = os.path.join(ASSETS, "textures/block")
    rnd = random.Random(66)
    # el corazón: un bloque de piedra negra labrada con un núcleo rojo que late
    for name, core in (("dungeon_heart_side", (200, 30, 40)), ("dungeon_heart_top", (230, 50, 60))):
        im = Image.new("RGBA", (16, 16))
        for x in range(16):
            for y in range(16):
                v = 34 + rnd.randint(-6, 6)
                c = (v, v - 2, v + 4, 255)
                if x in (0, 15) or y in (0, 15):
                    c = (58, 54, 62, 255)
                elif x in (1, 14) or y in (1, 14):
                    c = (22, 20, 26, 255)
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 4.6:
                    k = max(0.0, 1 - d / 4.6)
                    c = (int(core[0] * (0.55 + 0.45 * k)), int(core[1] * (0.4 + 0.6 * k)), int(core[2] * (0.4 + 0.6 * k)), 255)
                elif d < 5.6:
                    c = (90, 20, 26, 255)
                if name.endswith("side") and (x + y) % 5 == 0 and 2 < x < 13 and (y < 3 or y > 12):
                    c = (110, 90, 60, 255)
                im.putpixel((x, y), c)
        im.save(os.path.join(base, name + ".png"))
    # la reja cerrada: barrotes negros con remaches
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(16):
        for y in range(16):
            if x % 4 in (1, 2) or y in (1, 2, 13, 14):
                v = 46 if x % 4 == 1 or y in (1, 13) else 30
                im.putpixel((x, y), (v, v, v + 6, 255))
            if (x % 4 in (1, 2)) and y in (1, 2, 13, 14):
                im.putpixel((x, y), (90, 84, 70, 255))
    im.save(os.path.join(base, "dungeon_gate.png"))


def block_assets():
    def wj(path, obj):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(obj, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
    wj(os.path.join(ASSETS, "blockstates/dungeon_heart.json"), {"variants": {"": {"model": f"{NS}:block/dungeon_heart"}}})
    wj(os.path.join(ASSETS, "models/block/dungeon_heart.json"), {"parent": "minecraft:block/cube_column", "textures": {
        "end": f"{NS}:block/dungeon_heart_top", "side": f"{NS}:block/dungeon_heart_side"}})
    wj(os.path.join(ASSETS, "blockstates/dungeon_gate.json"), {"variants": {
        "closed=false": {"model": f"{NS}:block/dungeon_gate"}, "closed=true": {"model": f"{NS}:block/dungeon_gate"}}})
    wj(os.path.join(ASSETS, "models/block/dungeon_gate.json"), {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout",
                                                                  "textures": {"all": f"{NS}:block/dungeon_gate"}})
    for n in ("dungeon_heart", "dungeon_gate"):
        wj(os.path.join(ASSETS, f"models/item/{n}.json"), {"parent": f"{NS}:block/{n}"})


def good(b):
    return (walk(b, b.start, b.marks["heart"], ground=0) and not walk(b, b.start, b.marks["heart"], ground=0, sealed=True)
            and len(b.marks["heart"]) == 1 and len(b.marks["spawner"]) >= 4 and len(b.marks["chest"]) >= 2 and 0 < len(b.marks["gate"]) <= 60)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def main():
    prev = None
    if "--preview" in sys.argv:
        prev = sys.argv[sys.argv.index("--preview") + 1]
    tag_dir = os.path.join(DATA, "tags/worldgen/biome/has_structure/dungeon")
    sets = {False: [], True: []}
    only = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else None
    for did, (fn, under, biomes, *_rest) in DUNGEONS.items():
        entries = []
        seed = 0
        old = os.path.join(DATA, "worldgen/structure", f"dungeon_{did}.json")
        if only and did not in only and os.path.exists(old):
            with open(old, encoding="utf-8") as fh:
                entries = json.load(fh)["templates"]
            seed = 999
        while len(entries) < VARIANTS and seed < 60:
            b = fn(seed * 7919 + 13)
            seed += 1
            if not good(b):
                print("  (descartada %s semilla %d)" % (did, seed - 1))
                continue
            check(b)
            t, (ox, oy, oz) = b.template()
            name = "%s_%d" % (did, len(entries))
            t.save(os.path.join(DATA, "structure/dungeon", name + ".nbt"))
            entries.append({"id": f"{NS}:dungeon/{name}", "x": ox, "y": oy, "z": oz})
            if prev and len(entries) == 1:
                ys = sorted({y for (x, y, z), (st, be) in b.blocks.items() if short(st) == "air"})
                levels = [y for y in ys if sum(1 for (x, yy, z), (st, be) in b.blocks.items() if yy == y and short(st) == "air"
                                             and standable(b.get(x, y - 1, z))) > 40][:8]
                preview(b, prev, levels)
        assert len(entries) == VARIANTS, did
        write_json(os.path.join(DATA, "worldgen/structure", f"dungeon_{did}.json"), {
            "type": f"{NS}:dungeon", "biomes": f"#{NS}:has_structure/dungeon/{did}",
            "step": "underground_structures" if under else "surface_structures", "terrain_adaptation": "none", "spawn_overrides": {},
            "templates": entries, "underground": under, "max_slope": 6})
        write_json(os.path.join(tag_dir, did + ".json"), {"values": ["minecraft:" + x for x in biomes]})
        for k, table in loots(did).items():
            write_json(os.path.join(DATA, "loot_table/chests/dungeon", did, k + ".json"), table)
        sets[under].append({"structure": f"{NS}:dungeon_{did}", "weight": 1})
    for under, name, salt in ((False, "dungeons_surface", 66601), (True, "dungeons_underground", 66602)):
        write_json(os.path.join(DATA, "worldgen/structure_set", name + ".json"), {
            "placement": {"type": "minecraft:random_spread", "salt": salt, "spacing": 40, "separation": 16,
                          "exclusion_zone": {"other_set": "minecraft:villages", "chunk_count": 5}},
            "structures": sets[under]})
    textures()
    block_assets()
    import glob
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in lang().items():
            data[key] = en if english else es
        write_json(path, data)
    print("mazmorras: %d tipos, %d plantillas" % (len(DUNGEONS), len(DUNGEONS) * VARIANTS))


if __name__ == "__main__":
    main()
