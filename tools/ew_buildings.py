"""Dibuja los edificios de los oficios (fase 3) tal como los genera BlueprintGenerator.java.

Compila el generador (Java puro, sin Minecraft) junto a tools/java/BlueprintDump.java, lo ejecuta y dibuja cada
plano en isométrico con ew_render.

Uso: python3 ew_buildings.py <carpeta de salida> [oficios separados por comas] [semillas por oficio] [primera semilla]
"""
import json
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image  # noqa: E402

from ew_render import render, MISSING  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.join(ROOT, "src/main/java/com/evolvedwarfare/building")
DUMP = os.path.join(ROOT, "tools/java/BlueprintDump.java")
ALL = "farmer,lumberjack,miner,digger,fisherman,shepherd,breeder,beekeeper,gardener,blacksmith,cook,charcoal_burner,stonemason,fletcher"
NAMES = {
    "farmer": "Granja", "lumberjack": "Cabaña del leñador", "miner": "Granero de la mina", "digger": "Cobertizo",
    "fisherman": "Caseta de pescador", "shepherd": "Establo y cercado", "breeder": "Gallinero y corral",
    "beekeeper": "Colmenar", "gardener": "Invernadero", "blacksmith": "Forja", "cook": "Cocina",
    "charcoal_burner": "Carbonera", "stonemason": "Taller de cantería", "fletcher": "Taller del flechero",
}


def parse_state(text):
    name = text
    props = {}
    if "[" in text:
        name, rest = text.split("[", 1)
        for kv in rest.rstrip("]").split(","):
            k, v = kv.split("=")
            props[k] = v
    return (name, props)


def dump(professions, seeds, first):
    out = tempfile.mkdtemp()
    # solo las clases de Java puro (las demás del paquete usan Minecraft)
    sources = [os.path.join(GEN, f) for f in ("Material.java", "Blueprint.java", "BlueprintGenerator.java")] + [DUMP]
    subprocess.run(["javac", "-d", out] + sources, check=True, stderr=subprocess.DEVNULL)
    res = subprocess.run(["java", "-cp", out, "BlueprintDump", professions, str(seeds), str(first)],
                         check=True, capture_output=True, text=True)
    return [json.loads(line) for line in res.stdout.splitlines() if line.startswith("{")]


SWAP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def turn(st):
    """Gira 180º un estado (para que el frente mire a la cámara)."""
    name, props = st
    out = {}
    for k, v in props.items():
        k2 = SWAP.get(k, k)
        out[k2] = SWAP.get(v, v) if k == "facing" else v
    return (name, out)


def draw(plan, scale=10, show_post=True):
    blocks = {}
    for x, y, z, state, material, flags in plan["pieces"]:
        if state is None:
            continue
        blocks[(-x, y, -z)] = turn(parse_state(state))
    if show_post:
        blocks[(0, 0, 0)] = ("minecraft:barrel", {"facing": "up"})
    title = f"{NAMES.get(plan['profession'], plan['profession'])} · semilla {plan['seed']}"
    return render(blocks, [], scale=scale, ground=-1, margin=1, title=title)


def grid(images, cols, bg=(242, 238, 228)):
    w = max(i.width for i in images)
    h = max(i.height for i in images)
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * w, rows * h), bg)
    for i, im in enumerate(images):
        sheet.paste(im, ((i % cols) * w + (w - im.width) // 2, (i // cols) * h + (h - im.height) // 2))
    return sheet


if __name__ == "__main__":
    outdir = sys.argv[1]
    professions = sys.argv[2] if len(sys.argv) > 2 else ALL
    seeds = int(sys.argv[3]) if len(sys.argv) > 3 else 3
    first = int(sys.argv[4]) if len(sys.argv) > 4 else 1
    os.makedirs(outdir, exist_ok=True)
    plans = dump(professions, seeds, first)
    for prof in professions.split(","):
        ims = [draw(p) for p in plans if p["profession"] == prof]
        grid(ims, len(ims)).save(os.path.join(outdir, f"edificio_{prof}.png"))
        for p in plans:
            if p["profession"] == prof:
                print(prof, p["seed"], p["materials"])
    if MISSING:
        print("sin color:", MISSING)
