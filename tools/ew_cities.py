"""Ciudades amuralladas tal como las genera CityDesigner.java (Java puro): compila el generador junto a
tools/java/CityDump.java, lo ejecuta con varias semillas y dibuja cada ciudad en isométrico con ew_render, más un
plano visto desde arriba y un corte de una tienda (mostrador, escalera y cama).

Uso: python3 ew_cities.py <carpeta de salida> [estilos separados por comas] [ciudades por estilo] [primera semilla]
"""
import json
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image  # noqa: E402

from ew_render import colors, render  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.join(ROOT, "src/main/java/com/evolvedwarfare/world/city")
DUMP = os.path.join(ROOT, "tools/java/CityDump.java")
GROUND = {"MEDIEVAL": "minecraft:grass_block", "DESERT": "minecraft:sand", "SNOW": "minecraft:snow_block"}


def parse_state(text):
    name, props = text, {}
    if "[" in text:
        name, rest = text.split("[", 1)
        for kv in rest.rstrip("]").split(","):
            k, v = kv.split("=")
            props[k] = v
    return (name, props)


def dump(styles, count, first):
    out = tempfile.mkdtemp()
    sources = [os.path.join(GEN, f) for f in sorted(os.listdir(GEN)) if f.endswith(".java")] + [DUMP]
    subprocess.run(["javac", "-d", out] + sources, check=True)
    res = subprocess.run(["java", "-cp", out, "CityDump", styles, str(count), str(first)], check=True, capture_output=True, text=True)
    return [json.loads(line) for line in res.stdout.splitlines() if line.startswith("{")]


def blocks_of(c, box=None, cut=None):
    blocks = {}
    for x, y, z, st, flags in c["blocks"]:
        if st == "minecraft:air":
            continue
        if box and not (box[0] <= x <= box[2] and box[1] <= z <= box[3]):
            continue
        if cut is not None and y > cut:
            continue
        blocks[(x, y, z)] = parse_state(st)
    return blocks


def entities_of(c, box=None):
    ents = []
    for t in c["traders"]:
        ents.append(("evolvedwarfare:trader", (t[1], t[2], t[3])))
    for g in c["guards"]:
        ents.append(("evolvedwarfare:guard", (g[1], g[2], g[3])))
    if box:
        ents = [e for e in ents if box[0] <= e[1][0] <= box[2] and box[1] <= e[1][2] <= box[3]]
    return ents


def plan_view(c, px=4):
    """Desde arriba: el color de lo más alto de cada columna."""
    top = {}
    for x, y, z, st, flags in c["blocks"]:
        if st == "minecraft:air" or flags & 4:
            continue
        if (x, z) not in top or y > top[(x, z)][0]:
            top[(x, z)] = (y, st)
    xs = [k[0] for k in top]
    zs = [k[1] for k in top]
    x0, z0 = min(xs), min(zs)
    w, h = max(xs) - x0 + 1, max(zs) - z0 + 1
    img = Image.new("RGB", (w * px, h * px), (120, 160, 90))
    for (x, z), (y, st) in top.items():
        col = colors(parse_state(st))[0]
        f = 0.75 + min(y, 16) / 64
        col = tuple(min(255, int(v * f)) for v in col[:3])
        for i in range(px):
            for j in range(px):
                img.putpixel(((x - x0) * px + i, (z - z0) * px + j), col)
    for t in c["traders"]:
        for i in range(px):
            for j in range(px):
                img.putpixel(((t[1] - x0) * px + i, (t[3] - z0) * px + j), (230, 40, 200))
    for g in c["guards"]:
        for i in range(px):
            for j in range(px):
                img.putpixel(((g[1] - x0) * px + i, (g[3] - z0) * px + j), (30, 60, 220))
    return img


def main():
    out = sys.argv[1]
    styles = sys.argv[2] if len(sys.argv) > 2 else "MEDIEVAL,DESERT,SNOW"
    count = int(sys.argv[3]) if len(sys.argv) > 3 else 1
    first = int(sys.argv[4]) if len(sys.argv) > 4 else 1
    os.makedirs(out, exist_ok=True)
    for c in dump(styles, count, first):
        name = "%s_%d" % (c["style"].lower(), c["seed"])
        ground = (GROUND[c["style"]], {})
        title = "%s · semilla %d · %s" % (c["style"], c["seed"], c["desc"])
        render(blocks_of(c), entities_of(c), scale=3, ground=None, title=title, ground_block=ground).save(
            os.path.join(out, name + "_iso.png"))
        plan_view(c).save(os.path.join(out, name + "_plano.png"))
        # una tienda de cerca, cortada a la altura del primer piso
        t = c["traders"][0]
        box = (t[1] - 9, t[3] - 9, t[1] + 9, t[3] + 9)
        render(blocks_of(c, box, cut=3), entities_of(c, box), scale=14, title="tienda (%s), cortada" % t[0]).save(
            os.path.join(out, name + "_tienda_planta.png"))
        render(blocks_of(c, box), entities_of(c, box), scale=12, title="tienda (%s)" % t[0]).save(
            os.path.join(out, name + "_tienda.png"))
        print(name, c["desc"], "· mercaderes atascados:", c["stranded"], "· puertas cerradas:", c["closed"])


if __name__ == "__main__":
    main()
