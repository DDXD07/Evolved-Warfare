"""Prototipo (en Python) de las mejoras de las aldeas vanilla: empedrado, farolas y empalizada.

Carga los pools y piezas reales de las aldeas 1.21.1, las monta con el simulador jigsaw y aplica el
mismo algoritmo que luego hace el mod en Java (VillageExtras). Sirve para comprobar el resultado y
generar vistas previas.

  python3 ew_village.py <dir datos vanilla> [salida.png] [bioma] [semilla]
"""
import glob
import json
import math
import os
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(__file__))
import ew_jigsaw_sim as SIM  # noqa: E402
import nbt  # noqa: E402
from ew_structlib import S, Template, short  # noqa: E402

# Parámetros (los mismos que en VillageExtras.java)
CLOSING_RADIUS = 10   # rellena entrantes de hasta ~20 bloques
WALL_MARGIN = 4       # distancia de la empalizada a la aldea
LAMP_SPACING = 8      # distancia mínima entre farolas de calle
WALL_LANTERN_SPACING = 8

STYLES = {
    "plains": dict(log="minecraft:oak_log", post="minecraft:oak_fence", road=None),
    "savanna": dict(log="minecraft:acacia_log", post="minecraft:acacia_fence", road=None),
    "taiga": dict(log="minecraft:spruce_log", post="minecraft:spruce_fence", road=None),
    "snowy": dict(log="minecraft:spruce_log", post="minecraft:spruce_fence", road=None),
    "desert": dict(log="minecraft:jungle_log", post="minecraft:sandstone_wall", road="minecraft:smooth_sandstone"),
}
ROAD_MIX = ["minecraft:cobblestone"] * 6 + ["minecraft:andesite"] * 2 + ["minecraft:gravel", "minecraft:mossy_cobblestone"]


def road_block(style, x, z):
    if STYLES[style]["road"]:
        return STYLES[style]["road"]
    h = ((x * 73856093) ^ (z * 19349663)) & 0x7FFFFFFF
    return ROAD_MIX[h % len(ROAD_MIX)]


# ------------------------------------------------------------------ datos vanilla
def load_vanilla(data_dir):
    pools, templates = {}, {}
    for f in glob.glob(os.path.join(data_dir, "worldgen/template_pool/village/**/*.json"), recursive=True):
        rel = os.path.relpath(f, os.path.join(data_dir, "worldgen/template_pool"))[:-5]
        pools["minecraft:" + rel] = json.load(open(f))
    for f in glob.glob(os.path.join(data_dir, "structure/village/**/*.nbt"), recursive=True):
        rel = os.path.relpath(f, os.path.join(data_dir, "structure"))[:-4]
        d = nbt.read_file(f)
        pal = d["palette"] if "palette" in d else d["palettes"][0]
        t = Template(*d["size"])
        for b in d["blocks"]:
            p = pal[b["state"]]
            t.blocks[tuple(b["pos"])] = ((p["Name"], dict(p.get("Properties", {}))), b.get("nbt"))
        templates[rel] = t
    # elementos 'feature' (árboles, montones de heno...): caja 1x1x1 con un jigsaw mirando abajo
    for pool in pools.values():
        for e in pool["elements"]:
            el = e["element"]
            if el["element_type"] == "minecraft:feature_pool_element":
                key = "feature/" + el["feature"].split(":", 1)[1]
                if key not in templates:
                    t = Template(1, 1, 1)
                    t.blocks[(0, 0, 0)] = (S("jigsaw", orientation="down_south"),
                                           {"name": "minecraft:bottom", "target": "minecraft:empty",
                                            "pool": "minecraft:empty", "final_state": "minecraft:air"})
                    t.feature = el["feature"]
                    templates[key] = t
    return pools, templates


def village(pools_obj, biome, seed):
    st = {"start_pool": f"minecraft:village/{biome}/town_centers", "size": 6, "max_distance_from_center": 80,
          "use_expansion_hack": True}
    return SIM.generate_with(pools_obj, st, seed)


# ------------------------------------------------------------------ algoritmo (igual que en Java)
def world_cells(pc, predicate):
    t = pc.element.template
    out = []
    for (x, y, z), (st, be) in t.blocks.items():
        if predicate(st, be):
            rx, rz = SIM.rot_pos(x, z, pc.rot)
            out.append((pc.origin[0] + rx, pc.origin[2] + rz, st, be))
    return out


def is_street(pc):
    return "/streets/" in pc.element.name


def chamfer(grid_w, grid_h, seeds):
    """Distancia euclídea aproximada (chamfer 1-√2) desde las celdas semilla."""
    INF = 1e9
    d = [INF] * (grid_w * grid_h)
    for i in seeds:
        d[i] = 0.0
    r2 = math.sqrt(2)
    for z in range(grid_h):
        for x in range(grid_w):
            i = z * grid_w + x
            v = d[i]
            if x > 0: v = min(v, d[i - 1] + 1)
            if z > 0:
                v = min(v, d[i - grid_w] + 1)
                if x > 0: v = min(v, d[i - grid_w - 1] + r2)
                if x < grid_w - 1: v = min(v, d[i - grid_w + 1] + r2)
            d[i] = v
    for z in range(grid_h - 1, -1, -1):
        for x in range(grid_w - 1, -1, -1):
            i = z * grid_w + x
            v = d[i]
            if x < grid_w - 1: v = min(v, d[i + 1] + 1)
            if z < grid_h - 1:
                v = min(v, d[i + grid_w] + 1)
                if x < grid_w - 1: v = min(v, d[i + grid_w + 1] + r2)
                if x > 0: v = min(v, d[i + grid_w - 1] + r2)
            d[i] = v
    return d


def compute_extras(pieces, style):
    road_name = "minecraft:smooth_sandstone" if style == "desert" else "minecraft:dirt_path"
    path, no_lamp = set(), set()
    house_boxes = []
    for pc in pieces:
        if is_street(pc):
            for x, z, st, be in world_cells(pc, lambda st, be: st[0] == road_name or short(st) == "jigsaw"):
                if st[0] == road_name:
                    path.add((x, z))
                elif be and ("houses" in be["pool"] or be["name"] == "minecraft:street"):
                    for dx in (-1, 0, 1):
                        for dz in (-1, 0, 1):
                            no_lamp.add((x + dx, z + dz))
        else:
            house_boxes.append(pc.box)

    # ---- farolas en el borde de las calles
    edges = sorted(c for c in path if any((c[0] + dx, c[1] + dz) not in path
                                          for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))))
    lamps = []
    for c in edges:
        if c in no_lamp:
            continue
        if all((c[0] - l[0]) ** 2 + (c[1] - l[1]) ** 2 >= LAMP_SPACING ** 2 for l in lamps):
            lamps.append(c)

    # ---- contorno de la empalizada (cierre morfológico + margen)
    x0 = min(p.box.x0 for p in pieces); x1 = max(p.box.x1 for p in pieces)
    z0 = min(p.box.z0 for p in pieces); z1 = max(p.box.z1 for p in pieces)
    pad = CLOSING_RADIUS + WALL_MARGIN + 3
    gx0, gz0 = x0 - pad, z0 - pad
    W, H = (x1 - x0 + 1) + 2 * pad, (z1 - z0 + 1) + 2 * pad
    occ = set()
    for p in pieces:
        for x in range(p.box.x0, p.box.x1 + 1):
            for z in range(p.box.z0, p.box.z1 + 1):
                occ.add((z - gz0) * W + (x - gx0))
    d1 = chamfer(W, H, occ)
    inside_d = [v <= CLOSING_RADIUS + WALL_MARGIN for v in d1]
    d2 = chamfer(W, H, [i for i in range(W * H) if not inside_d[i]])
    region = [v > CLOSING_RADIUS for v in d2]
    outside = [False] * (W * H)
    q = deque()
    for x in range(W):
        for z in (0, H - 1):
            i = z * W + x
            if not region[i] and not outside[i]:
                outside[i] = True; q.append(i)
    for z in range(H):
        for x in (0, W - 1):
            i = z * W + x
            if not region[i] and not outside[i]:
                outside[i] = True; q.append(i)
    while q:
        i = q.popleft()
        x, z = i % W, i // W
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if 0 <= nx < W and 0 <= nz < H:
                j = nz * W + nx
                if not region[j] and not outside[j]:
                    outside[j] = True; q.append(j)
    wall = []
    for z in range(1, H - 1):
        for x in range(1, W - 1):
            i = z * W + x
            if region[i] and any(outside[(z + dz) * W + x + dx] for dx in (-1, 0, 1) for dz in (-1, 0, 1)):
                wall.append((x + gx0, z + gz0))
    wall_set = set(wall)

    # ---- puertas: donde una calle llega más cerca de la muralla, sin atravesar casas
    def in_house(c):
        return any(b.x0 <= c[0] <= b.x1 and b.z0 <= c[1] <= b.z1 for b in house_boxes)

    def line(a, b):
        (ax, az), (bx, bz) = a, b
        n = max(abs(bx - ax), abs(bz - az))
        return [(round(ax + (bx - ax) * k / n), round(az + (bz - az) * k / n)) for k in range(n + 1)] if n else [a]

    gates, gate_cells, post_cells, road = [], set(), set(), set()
    for dx, dz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        cand = sorted(path, key=lambda c: -(c[0] * dx + c[1] * dz))[:60]
        for p in cand:
            w = min(wall, key=lambda c: (c[0] - p[0]) ** 2 + (c[1] - p[1]) ** 2)
            seg = line(p, w)
            if any(in_house(c) for c in seg[1:]):
                continue
            if any((w[0] - g[0]) ** 2 + (w[1] - g[1]) ** 2 < 15 ** 2 for g in gates):
                break
            gates.append(w)
            for c in wall:
                dd = (c[0] - w[0]) ** 2 + (c[1] - w[1]) ** 2
                if dd <= 2:
                    gate_cells.add(c)
                elif dd <= 6.5:
                    post_cells.add(c)
            # camino de 3 de ancho desde la calle hasta 3 bloques más allá de la puerta
            vx, vz = w[0] - p[0], w[1] - p[1]
            n = max(1, math.hypot(vx, vz))
            far = (round(w[0] + vx / n * 3), round(w[1] + vz / n * 3))
            for c in line(p, far):
                side = ((0, 1), (0, -1)) if abs(vx) >= abs(vz) else ((1, 0), (-1, 0))
                for cc in [c, (c[0] + side[0][0], c[1] + side[0][1]), (c[0] + side[1][0], c[1] + side[1][1])]:
                    if cc not in path and not in_house(cc) and (cc not in wall_set or cc in gate_cells):
                        road.add(cc)
            break
    lanterns = []
    for c in sorted(wall):
        if c in gate_cells or c in post_cells:
            continue
        if all((c[0] - l[0]) ** 2 + (c[1] - l[1]) ** 2 >= WALL_LANTERN_SPACING ** 2 for l in lanterns):
            lanterns.append(c)
    return dict(path=path, lamps=lamps, wall=[c for c in wall if c not in gate_cells], gates=gates,
                gate_cells=gate_cells, posts=post_cells, road=road, lanterns=set(lanterns))


# ------------------------------------------------------------------ vista previa
def render_village(pieces, style, extras, scale=6):
    from ew_render import render
    blocks, ents = SIM.world_blocks(pieces)
    road_name = "minecraft:smooth_sandstone" if style == "desert" else "minecraft:dirt_path"
    # empedrado (lo que hace la processor_list) y árboles de las piezas 'feature'
    for pc in pieces:
        if is_street(pc) and style != "desert":
            for x, z, st, be in world_cells(pc, lambda st, be: st[0] == road_name):
                blocks[(x, 0, z)] = (road_block(style, x, z), {})
        t = pc.element.template
        if hasattr(t, "feature"):
            x, y, z = pc.origin
            f = t.feature
            if any(k in f for k in ("oak", "spruce", "pine", "acacia")):
                wood = "spruce" if ("spruce" in f or "pine" in f) else ("acacia" if "acacia" in f else "oak")
                for k in range(4):
                    blocks[(x, y + k, z)] = (f"minecraft:{wood}_log", {})
                for ddx in (-1, 0, 1):
                    for ddz in (-1, 0, 1):
                        for k in (3, 4):
                            blocks.setdefault((x + ddx, y + k, z + ddz), (f"minecraft:{wood}_leaves", {}))
            elif "hay" in f:
                blocks[(x, y, z)] = ("minecraft:hay_block", {})
    log = STYLES[style]["log"]
    post = STYLES[style]["post"]
    for (x, z) in extras["road"]:
        blocks[(x, 0, z)] = (road_block(style, x, z), {})
    for (x, z) in extras["wall"]:
        h = 4 + ((x + z) & 1)
        for y in range(1, h + 1):
            blocks[(x, y, z)] = (log, {"axis": "y"})
        if (x, z) in extras["lanterns"]:
            blocks[(x, h + 1, z)] = ("minecraft:lantern", {"hanging": "false"})
    for (x, z) in extras["posts"]:
        for y in range(1, 7):
            blocks[(x, y, z)] = (log, {"axis": "y"})
        blocks[(x, 7, z)] = ("minecraft:lantern", {"hanging": "false"})
    for (x, z) in extras["lamps"]:
        blocks[(x, 1, z)] = (post, {})
        blocks[(x, 2, z)] = (post, {})
        blocks[(x, 3, z)] = ("minecraft:lantern", {"hanging": "false"})
    ground = {"desert": ("minecraft:sand", {}), "snowy": ("minecraft:snow_block", {})}.get(style, ("minecraft:grass_block", {}))
    img = render(blocks, ents, scale=scale, ground=0, margin=3, ground_block=ground)
    return img


if __name__ == "__main__":
    data = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/village.png"
    biome = sys.argv[3] if len(sys.argv) > 3 else "plains"
    seed = int(sys.argv[4]) if len(sys.argv) > 4 else 1
    pools, templates = load_vanilla(data)
    P = SIM.Pools(templates, pools)
    pcs = village(P, biome, seed)
    ex = compute_extras(pcs, biome)
    print(f"{biome} semilla {seed}: piezas {len(pcs)}, calles {sum(is_street(p) for p in pcs)}, "
          f"farolas {len(ex['lamps'])}, empalizada {len(ex['wall'])} bloques, puertas {len(ex['gates'])}, "
          f"faroles en muralla {len(ex['lanterns'])}")
    render_village(pcs, biome, ex).save(out)
