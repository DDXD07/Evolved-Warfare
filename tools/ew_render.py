"""Vista isométrica de estructuras (colores medios de las texturas vanilla) para revisar los diseños."""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from ew_structlib import is_full, short

TEX = os.environ.get("MC_BLOCK_TEXTURES", "/home/claude/ref/mcassets/assets/minecraft/textures/block")
GRASS_TOP = (124, 176, 82)
WATER = (63, 118, 228)
_cache = {}

ALIASES = {
    "smooth_sandstone": "sandstone_top", "smooth_red_sandstone": "red_sandstone_top", "cut_sandstone": "cut_sandstone",
    "brick": "bricks", "stone_brick": "stone_bricks", "deepslate_tile": "deepslate_tiles",
    "mossy_stone_brick": "mossy_stone_bricks", "cobblestone": "cobblestone", "polished_andesite": "polished_andesite",
}
SPECIAL = {
    "grass_block": (GRASS_TOP, (134, 96, 67)),
    "water": (WATER, WATER),
    "lava_cauldron": ((207, 92, 20), (74, 74, 74)),
    "water_cauldron": (WATER, (74, 74, 74)),
    "glass_pane": ((200, 225, 235), (170, 205, 220)),
    "lantern": ((255, 205, 90), (255, 190, 80)),
    "campfire": ((255, 140, 40), (110, 80, 50)),
    "jigsaw": ((255, 0, 255), (255, 0, 255)),
    "chest": ((160, 115, 50), (140, 100, 45)),
    "wall_torch": ((255, 210, 100), (255, 210, 100)),
    "chain": ((60, 65, 80), (60, 65, 80)),
}
NPC_COLOR = ((70, 110, 200), (60, 95, 180))      # túnica azul: así se ven los reclutas en la vista previa
BANDIT_COLOR = ((190, 40, 40), (150, 30, 30))    # rojo: bandidos
STAND_COLOR = ((150, 150, 150), (120, 120, 120))


def avg(path):
    if path in _cache:
        return _cache[path]
    im = Image.open(path).convert("RGBA")
    a = np.asarray(im, dtype=np.float32)[:16]  # primer fotograma si es animada
    w = a[..., 3:4] / 255.0
    col = tuple(int(v) for v in ((a[..., :3] * w).sum((0, 1)) / max(w.sum(), 1)))
    _cache[path] = col
    return col


def tex(*names):
    for n in names:
        p = os.path.join(TEX, n + ".png")
        if os.path.exists(p):
            return avg(p)
    return None


def base_name(n):
    for suf in ("_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_trapdoor", "_door",
                "_wall_sign", "_sign", "_button"):
        if n.endswith(suf):
            b = n[: -len(suf)]
            return ALIASES.get(b, b)
    return n


MISSING = set()


def colors(st):
    top, side = _colors(st)
    if top is None or side is None:
        MISSING.add(short(st))
        c = top or side or (200, 0, 200)
        return (c, c)
    return (top, side)


def _colors(st):
    n = short(st)
    if n in SPECIAL:
        return SPECIAL[n]
    if n.endswith("_bed") or n.endswith("_carpet") or n.endswith("_banner") or n.endswith("_wall_banner"):
        color = n.split("_")[0] if not n.startswith("light_") else "_".join(n.split("_")[:2])
        c = tex(color + "_wool")
        return (c, c)
    if n.startswith("potted_"):
        c = tex("flower_pot")
        return (c, c)
    if n in ("wheat", "carrots", "potatoes"):
        c = tex(n + "_stage7", n + "_stage3")
        return (c, c)
    if n == "ladder":
        c = tex("ladder")
        return (c, c)
    if n.endswith("_door"):
        c = tex(n + "_bottom")
        return (c, c)
    if n.endswith("_trapdoor"):
        c = tex(n)
        return (c, c)
    if n in ALIASES and n != ALIASES[n]:
        c = tex(ALIASES[n])
        if c:
            return (c, c)
    b = base_name(n)
    if b != n:
        top = tex(b, b + "_planks", b + "s")
        if top is None:
            top = tex(b + "_top")
        return (top, top)
    top = tex(n + "_top", n, n + "_side", n + "_front")
    side = tex(n + "_side", n, n + "_front", n + "_top")
    if top is None:
        top = side
    if top is None:
        return ((255, 0, 255), (255, 0, 255))
    return (top, side)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def shape_of(st):
    """(alto, ancho) relativos del bloque para dibujarlo."""
    n = short(st)
    p = st[1]
    if n.endswith("_slab"):
        return (1.0, 1.0) if p.get("type") in ("double",) else (0.5, 1.0)
    if n.endswith("_carpet") or n.endswith("pressure_plate"):
        return (0.08, 1.0)
    if n.endswith("_bed"):
        return (0.56, 1.0)
    if n in ("lantern",):
        return (0.5, 0.4)
    if n.endswith("_fence") or (n.endswith("_wall") and "sign" not in n and "banner" not in n and "torch" not in n):
        return (1.0, 0.35)
    if n in ("chain", "wall_torch", "torch") or n.endswith("_wall_banner") or n.endswith("_banner"):
        return (0.9, 0.25)
    if n.endswith("_pane") or n == "iron_bars" or n == "ladder" or n.endswith("_wall_sign"):
        return (1.0, 0.3)
    if n.endswith("_trapdoor") and p.get("open") == "false":
        return (0.2, 1.0)
    if n.startswith("potted_"):
        return (0.4, 0.4)
    if n in ("wheat", "carrots", "potatoes"):
        return (0.6, 0.8)
    if n == "water":
        return (0.88, 1.0)
    return (1.0, 1.0)


def render(blocks, entities=(), scale=6, ground=None, margin=3, bg=(242, 238, 228), title=None,
           ground_block=("minecraft:grass_block", {})):
    """blocks: {(x,y,z): state}. ground: y de la capa de hierba a dibujar (None = sin suelo)."""
    blocks = dict(blocks)
    if ground is not None and blocks:
        xs = [p[0] for p in blocks]
        zs = [p[2] for p in blocks]
        for x in range(min(xs) - margin, max(xs) + margin + 1):
            for z in range(min(zs) - margin, max(zs) + margin + 1):
                if (x, ground, z) not in blocks:
                    blocks[(x, ground, z)] = ground_block
    items = [(p, st, False) for p, st in blocks.items() if short(st) not in ("air", "structure_void")]
    for (eid, (x, y, z)) in entities:
        kind = "bandit" if "bandit" in eid else ("npc" if "evolvedwarfare" in eid else "stand")
        items.append(((x, y, z), kind, True))
        items.append(((x, y + 1, z), kind, True))
    if not items:
        return Image.new("RGB", (10, 10), bg)
    s = scale
    h = s * 1.18

    def proj(x, y, z):
        return ((x - z) * s, (x + z) * s / 2 - y * h)

    pts = [proj(x + dx, y + dy, z + dz) for (x, y, z), _, _ in items for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
    minx, maxx = min(p[0] for p in pts), max(p[0] for p in pts)
    miny, maxy = min(p[1] for p in pts), max(p[1] for p in pts)
    top_pad = 30 if title else 6
    W, H = int(maxx - minx) + 12, int(maxy - miny) + 12 + top_pad
    img = Image.new("RGB", (W, H), bg)
    dr = ImageDraw.Draw(img)
    ox, oy = -minx + 6, -miny + 6 + top_pad

    solid = {p for p, st in blocks.items() if is_full(st)}
    items.sort(key=lambda it: (it[0][0] + it[0][2]) + 0.82 * it[0][1])
    for (x, y, z), st, is_ent in items:
        if is_ent:
            top, side = {"npc": NPC_COLOR, "bandit": BANDIT_COLOR}.get(st, STAND_COLOR)
            hgt, wid = 1.0, 0.5
        else:
            top, side = colors(st)
            hgt, wid = shape_of(st)
            n = short(st)
            if n.endswith("_slab") and st[1].get("type") == "top":
                y = y + 0.5
        full = (not is_ent) and is_full(st)
        vis_top = not ((x, y + 1, z) in solid) if full else True
        vis_e = not ((x + 1, y, z) in solid) if full else True
        vis_s = not ((x, y, z + 1) in solid) if full else True
        if not (vis_top or vis_e or vis_s):
            continue
        m = (1 - wid) / 2
        x0, x1, z0, z1 = x + m, x + 1 - m, z + m, z + 1 - m
        y0, y1 = y, y + hgt

        def P(a, b, c):
            px, py = proj(a, b, c)
            return (px + ox, py + oy)

        if vis_e:
            dr.polygon([P(x1, y0, z0), P(x1, y0, z1), P(x1, y1, z1), P(x1, y1, z0)], fill=shade(side, 0.78))
        if vis_s:
            dr.polygon([P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)], fill=shade(side, 0.62))
        if vis_top:
            dr.polygon([P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1), P(x0, y1, z1)], fill=shade(top, 1.0))
    if title:
        try:
            font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 18)
        except OSError:
            font = ImageFont.load_default()
        dr.text((8, 6), title, fill=(40, 40, 40), font=font)
    return img


def template_blocks(t):
    out = {}
    for (x, y, z), (st, be) in t.blocks.items():
        if short(st) == "jigsaw":
            fs = be["final_state"]
            name = fs.split("[")[0]
            if name == "minecraft:structure_void":
                continue
            props = dict(kv.split("=") for kv in fs.split("[")[1].rstrip("]").split(",")) if "[" in fs else {}
            st = (name, props)
        out[(x, y, z)] = st
    return out


def template_entities(t):
    out = []
    for e in t.entities:
        out.append((e["nbt"]["id"], tuple(e["blockPos"].value)))
    return out


if __name__ == "__main__":
    print("usar desde ew_preview.py", file=sys.stderr)
