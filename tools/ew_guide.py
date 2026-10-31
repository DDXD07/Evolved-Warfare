"""Guía de Evolved Warfare: icono del libro, su modelo, el libro abierto de la pantalla, la receta (libro + papel) y los
textos de las cuatro secciones (cortos: tres cosas por página).

Uso: python3 tools/ew_guide.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFont

args = sys.argv[1:]
PREVIEW = None
if "--preview" in args:
    i = args.index("--preview")
    PREVIEW = args[i + 1]
    del args[i:i + 2]
ROOT = args[0] if args else "."
ASSETS = os.path.join(ROOT, "src/main/resources/assets/evolvedwarfare")
DATA = os.path.join(ROOT, "src/main/resources/data/evolvedwarfare")
BOOK_W, BOOK_H = 292, 190

# sección -> (nombre, (título, 3 líneas) izquierda, (título, 3 líneas) derecha); en español y en inglés
ES = {
    "cities": ("Ciudades y comercio",
               ("Ciudades", ["Ciudades de tres estilos, con comerciantes y reclutas.",
                             "Tienen nivel del 1 al 10: cuanto más lejos del inicio, más nivel.",
                             "A más nivel, cosas de más calidad, pero más caras."]),
               ("Comercio", ["Se paga en Glory Coins: vende cosas o búscalas en cofres.",
                             "La Bolsa de Gloria guarda todas tus monedas en un solo hueco.",
                             "Cultivos, carnes y pescados: solo en Cajas de víveres llenas."])),
    "people": ("Tu gente",
               ("Reclutas", ["Contrata reclutas con un Contrato: clic derecho sobre uno libre.",
                             "Cobran cada día en comida o monedas; sin paga, se van.",
                             "Con un arma y Entrenar, son soldados. El Bastón de mando los guía."]),
               ("Tu pueblo", ["Pon un puesto de trabajo y Asigna a un recluta: trabajará solo.",
                              "El Ayuntamiento da de comer a tu gente cercana.",
                              "El Almacén une tus cofres; el mensajero lleva las cosas."])),
    "war": ("Facciones y guerra",
            ("Facciones", ["Bandidos, vikingos y piratas viven en asentamientos de nivel 1 a 10.",
                           "Tu reputación decide si te atacan. Con la Bandera blanca se negocia.",
                           "Asaltan pueblos y, cada 7 días (día en rojo), llega una horda."]),
            ("Armas y asedio", ["Armas medievales, cada una con su golpe. En la cabeza hacen más daño.",
                                "Mosquete: clic derecho apunta, clic izquierdo dispara, R recarga.",
                                "Catapulta, balista, trebuchet y ariete: súbete y mira al blanco."])),
    "skills": ("Habilidades",
               ("Habilidades", ["11 habilidades que suben al usarlas: armas, escudo, armadura, mando…",
                                "Al subirlas subes de nivel. Cada nivel da 1 punto de ventaja.",
                                "Tecla K (o la Mesa de investigación): tu árbol."]),
               ("Ventajas", ["Gasta puntos en ventajas: más daño, críticos, mejores soldados…",
                             "Las ventajas de forja te dejan fabricar las armas y máquinas del mod.",
                             "¿Perdiste esta guía? Libro + papel."])),
}
EN = {
    "cities": ("Cities and trade",
               ("Cities", ["Medieval, desert and northern cities, with traders and recruits.",
                           "They have a level from 1 to 10: the farther from spawn, the higher.",
                           "Higher level: better quality goods, but pricier."]),
               ("Trade", ["Everything is paid in Glory Coins: sell to traders or find them in chests.",
                          "The Glory Pouch holds all your coins in a single slot.",
                          "Crops, meat and fish only sell in full Produce Crates."])),
    "people": ("Your people",
               ("Recruits", ["They live in cities, camps and towers. Hire them with a Contract.",
                             "They get paid daily (food or coins). After 3 unpaid days, they leave.",
                             "A weapon and Train make a soldier. The Command Staff leads them."]),
               ("Your town", ["Place a work post and Assign a recruit: they work on their own.",
                              "The Town Hall feeds your people nearby.",
                              "The Warehouse links your chests; the courier carries things."])),
    "war": ("Factions and war",
            ("Factions", ["Bandits, vikings and pirates live in settlements of level 1 to 10.",
                          "Your reputation decides if they attack. The White Flag lets you talk.",
                          "They raid towns, and every 7 days (red day) a horde attacks."]),
            ("Weapons and siege", ["Medieval weapons, each with its own strike. Headshots hurt more.",
                                   "Musket: right-click aims, left-click fires, R reloads.",
                                   "Catapult, ballista, trebuchet and ram: get on and look at the target."])),
    "skills": ("Skills",
               ("Skills", ["11 skills that rise as you use them: weapons, shield, armor, command…",
                           "Leveling them levels you up. Each level gives 1 perk point.",
                           "Press K (or use the Research Desk): your tree."]),
               ("Perks", ["Spend points on perks: more damage, crits, better soldiers…",
                          "Forge perks let you craft the mod's weapons and machines.",
                          "Lost this guide? Book + paper."])),
}
OTHER = {
    "item.evolvedwarfare.guide_book": ("Guía de Evolved Warfare", "Evolved Warfare Guide"),
    "message.evolvedwarfare.guide.given": ("Tienes la Guía de Evolved Warfare en el inventario: clic derecho para leerla.",
                                           "You have the Evolved Warfare Guide in your inventory: right-click to read it."),
}


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def jit(c, rnd, amt=6):
    n = rnd.randint(-amt, amt)
    return tuple(max(0, min(255, v + n)) for v in c[:3]) + (c[3],)


LEATHER, LEATHER_D, LEATHER_L = hexc("#7A3B22"), hexc("#4E2414"), hexc("#94502E")
GOLD, GOLD_D = hexc("#E3B341"), hexc("#A67A1E")
PAPER, PAPER_D = hexc("#F1E4C3"), hexc("#D9C69B")


def icon():
    """16x16: el libro cerrado, con su espada cruzada de oro en la tapa, las hojas a la derecha y la cinta."""
    rnd = random.Random(5)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(1, 15):
        for x in range(2, 14):
            if x >= 12:
                px[x, y] = PAPER_D if (y % 2 == 0) else PAPER        # canto de las hojas
            elif x <= 3:
                px[x, y] = jit(LEATHER_D, rnd, 4)                     # lomo
            else:
                px[x, y] = jit(LEATHER, rnd, 6)
    for x in range(2, 14):
        px[x, 1] = LEATHER_D if x < 12 else PAPER_D
        px[x, 14] = LEATHER_D if x < 12 else PAPER_D
    for y in range(1, 15):
        px[1, y] = hexc("#2E140A")
        px[14, y] = PAPER_D
    # marco de oro y esquinas
    for x in range(5, 11):
        px[x, 3] = GOLD_D
        px[x, 12] = GOLD_D
    for y in range(3, 13):
        px[5, y] = GOLD_D
        px[10, y] = GOLD_D
    # dos espadas cruzadas (diagonales) en el centro
    for i in range(4):
        px[6 + i, 5 + i] = GOLD
        px[9 - i, 5 + i] = GOLD
    px[7, 10], px[8, 10] = GOLD_D, GOLD_D
    # cinta que asoma por abajo
    px[8, 15] = hexc("#B02A2A")
    px[9, 15] = hexc("#8A1E1E")
    return im


def book():
    """El libro abierto: tapa de cuero con esquinas de oro, dos páginas de pergamino y la sombra del lomo."""
    rnd = random.Random(11)
    im = Image.new("RGBA", (BOOK_W, BOOK_H), (0, 0, 0, 0))
    px = im.load()
    for y in range(BOOK_H):
        for x in range(BOOK_W):
            edge = min(x, y, BOOK_W - 1 - x, BOOK_H - 1 - y)
            if edge == 0:
                if (x in (0, BOOK_W - 1)) and (y in (0, BOOK_H - 1)):
                    continue
                px[x, y] = hexc("#2E140A")
            elif edge == 1:
                px[x, y] = LEATHER_L
            else:
                px[x, y] = jit(LEATHER, rnd, 5)
    # esquinas de oro
    for cx, cy in ((3, 3), (BOOK_W - 4, 3), (3, BOOK_H - 4), (BOOK_W - 4, BOOK_H - 4)):
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                if abs(dx) + abs(dy) <= 2:
                    px[cx + dx, cy + dy] = GOLD if abs(dx) + abs(dy) < 2 else GOLD_D
    # páginas (con unas hojas de más por debajo)
    mid = BOOK_W // 2
    for layer, col in ((3, PAPER_D), (2, hexc("#E6D6B0")), (0, PAPER)):
        for y in range(7 + layer, BOOK_H - 7 + layer):
            for x in range(9, BOOK_W - 9):
                if layer == 0 or y >= BOOK_H - 9:
                    c = jit(col, rnd, 3) if layer == 0 else col
                    px[x, y] = c
    # bordes de las páginas, sombra del lomo y una costura
    for y in range(7, BOOK_H - 7):
        for x in range(9, BOOK_W - 9):
            d = abs(x - mid)
            if d < 10:
                f = 0.72 + 0.28 * (d / 10)
                c = px[x, y]
                px[x, y] = (int(c[0] * f), int(c[1] * f), int(c[2] * f), 255)
            if x in (9, BOOK_W - 10) or y == 7:
                px[x, y] = PAPER_D
    for y in range(7, BOOK_H - 7, 6):
        px[mid, y] = hexc("#8A6A3A")
    # adorno: una línea fina bajo el sitio del título en cada página
    return im


# avance (píxeles) de cada letra en la letra por defecto de Minecraft; lo que no está, 6
NARROW = {"i": 2, "l": 3, "t": 4, "f": 5, "k": 5, " ": 4, "I": 4, ",": 2, ".": 2, ":": 2, ";": 2, "!": 2, "¡": 2,
          "(": 5, ")": 5, "í": 3, "'": 2, '"': 4, "*": 4, "<": 5, ">": 5, "[": 4, "]": 4, "@": 7, "~": 7}
WRAP = 98          # lo que cabe a lo ancho de una línea (GuideScreen: PAGE_W - 22)
LINE, GAP, ROOM = 9, 6, 124


def text_width(text):
    return sum(NARROW.get(c, 6) for c in text)


def wrap(text):
    lines, cur = [], ""
    for word in text.split(" "):
        t = (cur + " " + word) if cur else word
        if text_width(t) > WRAP and cur:
            lines.append(cur)
            cur = word
        else:
            cur = t
    return lines + [cur]


def check():
    """Que cada página quepa sin encoger la letra (GuideScreen la encoge si no, pero se lee peor)."""
    bad = []
    for lang, src in (("es", ES), ("en", EN)):
        for sid, (name, left, right) in src.items():
            for title, lines in (left, right):
                height = sum(max(16, len(wrap(line)) * LINE) for line in lines) + GAP * (len(lines) - 1)
                if height > ROOM:
                    bad.append(f"{lang} {sid} '{title}': {height} > {ROOM}")
    if bad:
        raise SystemExit("no caben:\n" + "\n".join(bad))


def lang_entries(english):
    src = EN if english else ES
    out = {}
    for sid, (name, left, right) in src.items():
        base = f"guide.evolvedwarfare.{sid}."
        out[base + "name"] = name
        for side, (title, lines) in (("left", left), ("right", right)):
            out[base + side + ".title"] = title
            for n, line in enumerate(lines, 1):
                out[base + side + "." + str(n)] = line
    for key, (es, en) in OTHER.items():
        out[key] = en if english else es
    return out


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


COLORS = {"cities": (201, 162, 39), "people": (63, 143, 63), "war": (168, 50, 50), "skills": (106, 63, 176)}


def preview(out):
    """Cómo queda cada sección, con los mismos cortes de línea que el juego (la letra no es la del juego)."""
    os.makedirs(out, exist_ok=True)
    k = 3
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 7 * k)
        bold = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 8 * k)
    except OSError:
        font = bold = ImageFont.load_default()
    frames = []
    for index, (sid, (name, left, right)) in enumerate(ES.items()):
        im = Image.new("RGB", ((BOOK_W + 34) * k, BOOK_H * k), (24, 24, 28))
        dr = ImageDraw.Draw(im)
        for i, other in enumerate(ES):
            col = COLORS[other] if i == index else tuple(int(c * 0.72) for c in COLORS[other])
            w = 30 if i == index else 26
            dr.rectangle([(BOOK_W - 6) * k, (18 + i * 28) * k, (BOOK_W - 6 + w) * k, (18 + i * 28 + 22) * k], fill=col)
        im.paste(book().convert("RGB").resize((BOOK_W * k, BOOK_H * k), Image.NEAREST), (0, 0))
        col = tuple(int(c * 0.8) for c in COLORS[sid])
        for x0, (title, lines) in ((18, left), (156, right)):
            dr.text(((x0 + 60) * k, 16 * k), title, fill=col, font=bold, anchor="mt")
            dr.line([((x0 + 10) * k, 27 * k), ((x0 + 110) * k, 27 * k)], fill=col, width=1)
            y = 36
            for line in lines:
                rows = wrap(line)
                h = max(16, len(rows) * LINE)
                iy = y + max(0, (h - 16) // 2 - 2)
                dr.rectangle([x0 * k, iy * k, (x0 + 16) * k, (iy + 16) * k], outline=(150, 120, 80), width=2)
                for r, row in enumerate(rows):
                    dr.text(((x0 + 20) * k, (y + r * LINE + (4 if len(rows) == 1 else 0)) * k), row, fill=(59, 42, 26), font=font)
                y += h + GAP
        dr.text(((156 + 60) * k, (BOOK_H - 22) * k), f"{index + 1} / 4", fill=(138, 117, 86), font=font, anchor="mt")
        frames.append(im)
    w, h = frames[0].size
    sheet = Image.new("RGB", (w * 2 + 12, h * 2 + 12), (30, 30, 34))
    for i, f in enumerate(frames):
        sheet.paste(f, ((i % 2) * (w + 12), (i // 2) * (h + 12)))
    sheet.save(os.path.join(out, "guide_sections.png"))
    icon().resize((128, 128), Image.NEAREST).save(os.path.join(out, "guide_icon.png"))


def main():
    check()
    os.makedirs(os.path.join(ASSETS, "textures/item"), exist_ok=True)
    icon().save(os.path.join(ASSETS, "textures/item/guide_book.png"))
    book().save(os.path.join(ASSETS, "textures/gui/guide.png"))
    write_json(os.path.join(ASSETS, "models/item/guide_book.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "evolvedwarfare:item/guide_book"}})
    write_json(os.path.join(DATA, "recipe/guide_book.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:book"}, {"item": "minecraft:paper"}],
        "result": {"id": "evolvedwarfare:guide_book", "count": 1}})
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        lang.update(lang_entries(english))
        write_json(path, lang)
    if PREVIEW:
        preview(PREVIEW)
    print("guía: icono, libro, receta y textos de", len(ES), "secciones")


if __name__ == "__main__":
    main()
