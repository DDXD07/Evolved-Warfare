"""Torres de vigilancia tal como las genera TowerDesigner.java (Java puro): compila el generador junto a
tools/java/TowerDump.java, lo ejecuta con varias semillas y dibuja cada torre en isométrico con ew_render.

Uso: python3 ew_towers.py <carpeta de salida> [estilos separados por comas] [torres por estilo] [primera semilla] [snow]
"""
import json
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

from ew_render import render  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.join(ROOT, "src/main/java/com/evolvedwarfare/world/tower")
DUMP = os.path.join(ROOT, "tools/java/TowerDump.java")
NAMES = {"TEMPERATE": "Llanura/bosque", "BOREAL": "Taiga", "SAVANNA": "Sabana", "ROCKY": "Montaña/costa"}


def parse_state(text):
    name, props = text, {}
    if "[" in text:
        name, rest = text.split("[", 1)
        for kv in rest.rstrip("]").split(","):
            k, v = kv.split("=")
            props[k] = v
    return (name, props)


def dump(styles, count, first, snow):
    out = tempfile.mkdtemp()
    sources = [os.path.join(GEN, f) for f in ("TowerPlan.java", "TowerDesigner.java", "TowerChecks.java")] + [DUMP]
    subprocess.run(["javac", "-d", out] + sources, check=True)
    args = ["java", "-cp", out, "TowerDump", styles, str(count), str(first)] + (["snow"] if snow else [])
    res = subprocess.run(args, check=True, capture_output=True, text=True)
    return [json.loads(line) for line in res.stdout.splitlines() if line.startswith("{")]


def draw_tower(t, scale=5):
    blocks = {}
    for x, y, z, st, flags in t["blocks"]:
        if st == "minecraft:air":
            continue
        blocks[(x, y, z)] = parse_state(st)
    ents = [("evolvedwarfare:recruit", tuple(g)) for g in t["guards"]]
    title = "%s · semilla %d%s" % (NAMES[t["style"]], t["seed"], "" if t["reaches"] else " · ¡NO SE SUBE!")
    img = render(blocks, ents, scale=scale, ground=-1, title=title)
    dr = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
    except OSError:
        font = ImageFont.load_default()
    dr.text((8, img.height - 20), t["desc"], fill=(40, 40, 40), font=font)
    return img


def sheet(images, cols, bg=(242, 238, 228)):
    w = max(i.width for i in images)
    h = max(i.height for i in images)
    rows = (len(images) + cols - 1) // cols
    out = Image.new("RGB", (cols * w, rows * h), bg)
    for k, im in enumerate(images):
        out.paste(im, ((k % cols) * w + (w - im.width) // 2, (k // cols) * h + (h - im.height)))
    return out


def main():
    folder = sys.argv[1]
    styles = sys.argv[2] if len(sys.argv) > 2 else "TEMPERATE,BOREAL,SAVANNA,ROCKY"
    count = int(sys.argv[3]) if len(sys.argv) > 3 else 4
    first = int(sys.argv[4]) if len(sys.argv) > 4 else 1
    snow = len(sys.argv) > 5 and sys.argv[5] == "snow"
    os.makedirs(folder, exist_ok=True)
    towers = dump(styles, count, first, snow)
    bad = [t for t in towers if not t["reaches"]]
    for t in towers:
        print("%-9s %3d  sube=%s  guardias=%d  cofres=%d  %s" % (t["style"], t["seed"], t["reaches"], len(t["guards"]), t["loot"], t["desc"]))
    images = [draw_tower(t) for t in towers]
    sheet(images, min(4, len(images))).save(os.path.join(folder, "towers.png"))
    print("sin subida:", len(bad))


if __name__ == "__main__":
    main()
