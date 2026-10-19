"""Simulador del generador jigsaw de Minecraft (StructurePoolBasedGenerator, 1.21.1) sobre terreno llano.

Reproduce lo que importa para comprobar las estructuras antes de compilar:
  * orden de expansión (cola FIFO), piezas de reserva (fallback) y límite de profundidad (size);
  * encaje de jigsaws (nombre/objetivo, direcciones opuestas, 'rollable');
  * espacio libre: si el destino cae dentro de la pieza padre se usa el hueco interior del padre;
    si no, el espacio exterior (caja de max_distance_from_center alrededor del centro);
  * 'use_expansion_hack' (las calles se estiran hacia arriba para que quepan las casas);
  * alturas rigid / terrain_matching.
"""
import random
import sys
from collections import Counter, deque

import ew_structures as E
from ew_structlib import short

ROT_ORDER = ["none", "cw90", "cw180", "ccw90"]
TURN = {"none": 0, "cw90": 1, "cw180": 2, "ccw90": 3}
HORIZ = ["north", "east", "south", "west"]


def rot_pos(x, z, rot):
    n = TURN[rot]
    for _ in range(n):  # CLOCKWISE_90 con pivote en el origen: (x, z) -> (-z, x)
        x, z = -z, x
    return x, z


def rot_dir(d, rot):
    if d not in HORIZ:
        return d
    return HORIZ[(HORIZ.index(d) + TURN[rot]) % 4]


DELTA = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0),
         "down": (0, -1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}


class Box:
    def __init__(self, x0, y0, z0, x1, y1, z1):
        self.x0, self.y0, self.z0, self.x1, self.y1, self.z1 = x0, y0, z0, x1, y1, z1

    def contains(self, p):
        return self.x0 <= p[0] <= self.x1 and self.y0 <= p[1] <= self.y1 and self.z0 <= p[2] <= self.z1

    def inside(self, o):
        return o.x0 <= self.x0 and self.x1 <= o.x1 and o.y0 <= self.y0 and self.y1 <= o.y1 and \
            o.z0 <= self.z0 and self.z1 <= o.z1

    def overlaps(self, o):
        return self.x0 <= o.x1 and o.x0 <= self.x1 and self.y0 <= o.y1 and o.y0 <= self.y1 and \
            self.z0 <= o.z1 and o.z0 <= self.z1

    def offset(self, dx, dy, dz):
        return Box(self.x0 + dx, self.y0 + dy, self.z0 + dz, self.x1 + dx, self.y1 + dy, self.z1 + dz)


class Space:
    """Forma libre = contenedor menos cajas ocupadas."""

    def __init__(self, container):
        self.container = container
        self.taken = []

    def fits(self, b):
        return b.inside(self.container) and not any(b.overlaps(t) for t in self.taken)


class Element:
    def __init__(self, raw, templates):
        self.raw = raw
        self.empty = raw["element_type"] == "minecraft:empty_pool_element"
        if not self.empty:
            if raw["element_type"] == "minecraft:feature_pool_element":
                self.name = "feature/" + raw["feature"].split(":", 1)[1]
            else:
                self.name = raw["location"].split(":", 1)[1]
            self.template = templates[self.name]
            self.projection = raw["projection"]

    def box(self, rot, origin):
        sx, sy, sz = self.template.size
        xs, zs = [], []
        for (x, z) in ((0, 0), (sx - 1, 0), (0, sz - 1), (sx - 1, sz - 1)):
            rx, rz = rot_pos(x, z, rot)
            xs.append(rx)
            zs.append(rz)
        return Box(min(xs), 0, min(zs), max(xs), sy - 1, max(zs)).offset(*origin)

    def jigsaws(self, rot):
        out = []
        for (x, y, z), orient, be in self.template.jigsaws():
            facing = orient.split("_")[0]
            rx, rz = rot_pos(x, z, rot)
            out.append(((rx, y, rz), rot_dir(facing, rot), be))
        return out


class Pools:
    def __init__(self, templates, pools=None):
        self.pools = {}
        if pools is not None:
            self.pools.update(pools)
        else:
            for name, data in E.POOLS.items():
                self.pools[f"{E.NS}:{name}"] = data
        self.templates = templates
        self.cache = {}

    def elements(self, pool_id):
        if pool_id == "minecraft:empty" or pool_id not in self.pools:
            if pool_id != "minecraft:empty":
                raise KeyError(f"pool inexistente: {pool_id}")
            return []
        return [(Element(e["element"], self.templates), e["weight"]) for e in self.pools[pool_id]["elements"]]

    def fallback(self, pool_id):
        if pool_id == "minecraft:empty":
            return "minecraft:empty"
        return self.pools[pool_id]["fallback"]

    def shuffled(self, pool_id, rnd):
        lst = []
        for el, w in self.elements(pool_id):
            lst += [el] * w
        rnd.shuffle(lst)
        return lst

    def highest_y(self, pool_id):
        return max([el.template.size[1] for el, _ in self.elements(pool_id) if not el.empty] or [0])


class Piece:
    def __init__(self, element, rot, origin, box, depth):
        self.element, self.rot, self.origin, self.box, self.depth = element, rot, origin, box, depth

    def world_jigsaws(self):
        ox, oy, oz = self.origin
        return [((p[0] + ox, p[1] + oy, p[2] + oz), f, be) for p, f, be in self.element.jigsaws(self.rot)]


def attach_matches(j1, j2):
    (_, f1, be1), (_, f2, be2) = j1, j2
    return f1 == OPP[f2] and be1["target"] == be2["name"]


_CACHE = {}


def _templates():
    if "t" not in _CACHE:
        _CACHE["t"] = E.build_all()
    return _CACHE["t"]


def generate(structure_name, seed, ground=0):
    st = E.STRUCTURES[structure_name]
    templates = _templates()
    return generate_with(Pools(templates), st, seed, ground)


def generate_with(pools, st, seed, ground=0):
    """st: dict con start_pool, size, max_distance_from_center, use_expansion_hack."""
    rnd = random.Random(seed)
    max_size, max_dist, hack = st["size"], st["max_distance_from_center"], st["use_expansion_hack"]
    height_on_ground = ground + 1

    start_el = pools.shuffled(st["start_pool"], rnd)[0]
    rot = rnd.choice(ROT_ORDER)
    box0 = start_el.box(rot, (0, 0, 0))
    origin = (0, ground - box0.y0, 0)
    start = Piece(start_el, rot, origin, start_el.box(rot, origin), 0)
    cx, cz = (start.box.x0 + start.box.x1) // 2, (start.box.z0 + start.box.z1) // 2
    outer = Space(Box(cx - max_dist, -64, cz - max_dist, cx + max_dist, 320, cz + max_dist))
    outer.taken.append(start.box)
    pieces = [start]
    queue = deque([(start, outer)])
    while queue:
        piece, piece_space = queue.popleft()
        inner = None
        js = piece.world_jigsaws()
        rnd.shuffle(js)
        for j in js:
            pos, facing, be = j
            d = DELTA[facing]
            target = (pos[0] + d[0], pos[1] + d[1], pos[2] + d[2])
            pool_id = be["pool"]
            if pool_id == "minecraft:empty":
                continue
            if piece.box.contains(target):
                if inner is None:
                    inner = Space(piece.box)
                space = inner
            else:
                space = piece_space
            candidates = []
            if piece.depth != max_size:
                candidates += pools.shuffled(pool_id, rnd)
            candidates += pools.shuffled(pools.fallback(pool_id), rnd)
            placed = False
            for el in candidates:
                if el.empty:
                    break
                rots = ROT_ORDER[:]
                rnd.shuffle(rots)
                for r in rots:
                    cjs = el.jigsaws(r)
                    rnd.shuffle(cjs)
                    cbox0 = el.box(r, (0, 0, 0))
                    ext = 0
                    if hack and cbox0.y1 - cbox0.y0 + 1 <= 16:
                        for (p2, f2, be2) in cjs:
                            d2 = DELTA[f2]
                            if cbox0.contains((p2[0] + d2[0], p2[1] + d2[1], p2[2] + d2[2])):
                                pid = be2["pool"]
                                ext = max(ext, pools.highest_y(pid), pools.highest_y(pools.fallback(pid)))
                    for cj in cjs:
                        if not attach_matches(j, cj):
                            continue
                        lp = cj[0]
                        if piece.element.projection == "rigid" and el.projection == "rigid":
                            oy = pos[1] + d[1] - lp[1]
                        else:
                            oy = height_on_ground - lp[1]
                        corigin = (target[0] - lp[0], oy, target[2] - lp[2])
                        cbox = el.box(r, corigin)
                        if ext > 0:
                            cbox.y1 = cbox.y0 + max(ext + 1, cbox.y1 - cbox.y0)
                        if not space.fits(cbox):
                            continue
                        space.taken.append(cbox)
                        child = Piece(el, r, corigin, cbox, piece.depth + 1)
                        pieces.append(child)
                        if piece.depth + 1 <= max_size:
                            queue.append((child, space))
                        placed = True
                        break
                    if placed:
                        break
                if placed:
                    break
    return pieces


def world_blocks(pieces, ground=0):
    """Bloques colocados (en coordenadas de mundo) y entidades, como en el juego."""
    blocks, ents = {}, []
    for pc in pieces:
        t = pc.element.template
        ox, oy, oz = pc.origin
        terrain = pc.element.projection == "terrain_matching"
        for (x, y, z), (st, be) in t.blocks.items():
            rx, rz = rot_pos(x, z, pc.rot)
            wy = (ground + y) if terrain else (oy + y)
            if short(st) == "jigsaw":
                fs = be["final_state"]
                name = fs.split("[")[0]
                if name in ("minecraft:structure_void",):
                    continue
                props = {}
                if "[" in fs:
                    props = dict(kv.split("=") for kv in fs.split("[")[1].rstrip("]").split(","))
                st = (name, props)
            if short(st) == "air" and pc.element.raw["element_type"].endswith("legacy_single_pool_element"):
                continue
            blocks[(ox + rx, wy, oz + rz)] = st
        for e in t.entities:
            bx, by, bz = e["blockPos"].value
            rx, rz = rot_pos(bx, bz, pc.rot)
            ents.append((e["nbt"]["id"], (ox + rx, oy + by, oz + rz)))
    return blocks, ents


def summary(pieces):
    c = Counter(p.element.name for p in pieces)
    xs = [p.box.x0 for p in pieces] + [p.box.x1 for p in pieces]
    zs = [p.box.z0 for p in pieces] + [p.box.z1 for p in pieces]
    return c, (max(xs) - min(xs) + 1, max(zs) - min(zs) + 1)


if __name__ == "__main__":
    name = sys.argv[1] if len(sys.argv) > 1 else "city"
    seeds = int(sys.argv[2]) if len(sys.argv) > 2 else 60
    tot = Counter()
    npcs, houses, sizes = [], [], []
    for s in range(seeds):
        pcs = generate(name, s)
        c, ext = summary(pcs)
        tot.update(c)
        npcs.append(c["npc/recruit"])
        houses.append(sum(v for k, v in c.items() if "/houses/" in k))
        sizes.append(ext)
    print(f"{name}: {seeds} semillas")
    print(f"  NPCs por estructura: min {min(npcs)}  media {sum(npcs) / len(npcs):.1f}  max {max(npcs)}")
    print(f"  edificios: min {min(houses)}  media {sum(houses) / len(houses):.1f}  max {max(houses)}")
    print(f"  tamaño (x*z): medio {sum(a for a, b in sizes) / len(sizes):.0f} x {sum(b for a, b in sizes) / len(sizes):.0f}"
          f"  máx {max(a for a, b in sizes)} x {max(b for a, b in sizes)}")
    for k, v in sorted(tot.items()):
        print(f"    {k:40s} {v / seeds:6.2f} por estructura")
