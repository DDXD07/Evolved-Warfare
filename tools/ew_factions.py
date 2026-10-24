"""Fase 5: skins de vikingos y piratas (64x64), objetos (bandera blanca y sello de embajador) y vista previa.

Uso: python3 ew_factions.py <src/main/resources/assets/evolvedwarfare> <carpeta de vistas previas>
"""
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from ew_textures import back_preview, box, front_preview, hexc, jitter, make_skin, shade, up  # noqa: E402

RES = sys.argv[1]
PREVIEW = sys.argv[2]

# ----------------------------------------------------------------- vikingos
VIKINGS = [
    dict(skin="#F0C8A8", hair="#D9B45A", tunic="#6B4A2E", pants="#4A3B2A", boots="#3A2A1C", style="long", beard=True,
         hood=None, braids=True, fur="#8A7A5A"),
    dict(skin="#EBC3A0", hair="#A8442A", tunic="#3E4F5E", pants="#3E3528", boots="#2B1E14", style="short", beard=True,
         hood=None, paint="#2E5A9A", fur="#6B6258"),
    dict(skin="#E3B48F", hair="#5A3A1E", tunic="#7A2E22", pants="#4A3F33", boots="#2B1E14", style="long", beard=True,
         hood=None, braids=True, bracers=True),
    dict(skin="#F2D0B4", hair="#E3C77A", tunic="#4E5E7A", pants="#3E3528", boots="#2B1E14", style="braid", beard=False,
         hood=None, fur="#8A7A5A", bracers=True),
]
JARL = dict(skin="#EBC3A0", hair="#C8642E", tunic="#5E1B1B", pants="#3A2A1E", boots="#2B1E14", style="long", beard=True,
            hood=None, braids=True, fur="#B8B0A0", gold="#D9B13B", bracers=True)

# ----------------------------------------------------------------- piratas
PIRATES = [
    dict(skin="#C68B5E", hair="#1E1A18", tunic="#E8E2D0", pants="#2B2B2B", boots="#141010", style="short", beard=True,
         hood=None, bandana="#B3262B", stripes="#B3262B", earring=True),
    dict(skin="#E3B48F", hair="#5A3A1E", tunic="#E8E2D0", pants="#1E2A4A", boots="#141010", style="crop", beard=False,
         hood=None, bandana="#1E1E1E", stripes="#1E2A4A", eyepatch=True),
    dict(skin="#7A4E2E", hair="#141010", tunic="#7A1E1E", pants="#3A3026", boots="#1E140C", style="short", beard=True,
         hood=None, bandana="#2E5A3A", sash="#D9B13B", earring=True),
    dict(skin="#D9A47E", hair="#6B4A2A", tunic="#E8E2D0", pants="#2B2B2B", boots="#141010", style="long", beard=False,
         hood=None, bandana="#7A1E1E", stripes="#2B2B2B"),
]
CAPTAIN = dict(skin="#D2A07A", hair="#1A1410", tunic="#7A1E1E", pants="#1A1A1A", boots="#0E0A08", style="long", beard=True,
               hood=None, tricorn="#1A1A1A", trim="#D9B13B", eyepatch=True, earring=True)


def outer(img, u, v, w, h, d, painter):
    """Capa exterior (sombrero, capa) con el mismo desplegado que box()."""
    box(img, u, v, w, h, d, painter)


def make_viking(p, seed):
    img = make_skin(p, seed)
    rnd = random.Random(seed * 11 + 5)
    hair = hexc(p["hair"])
    if p.get("braids") and p.get("beard"):
        # trenzas de la barba: dos colas por debajo de la barbilla (capa exterior de la cara)
        for (x, y) in ((2, 7), (5, 7)):
            img.putpixel((40 + x, 8 + y), shade(hair, 0.85))
            img.putpixel((40 + x + 1, 8 + y), shade(hair, 1.1))
    if p.get("paint"):
        paint = hexc(p["paint"])
        for x in range(0, 8):
            if x not in (2, 5):
                img.putpixel((8 + x, 8 + 4), paint)
    if p.get("fur"):
        fur = hexc(p["fur"])

        def cape(face, x, y, w, h):
            if face == "top":
                return jitter(fur, rnd, 10)
            if face == "back" and y < 7:
                return jitter(fur if y < 2 else shade(fur, 0.85), rnd, 10)
            if face in ("right", "left") and y < 2:
                return jitter(fur, rnd, 10)
            if face == "front" and y < 2 and x in (0, 1, 6, 7):
                return jitter(fur, rnd, 10)
            return None
        outer(img, 16, 32, 8, 12, 4, cape)
    if p.get("bracers"):
        leather = hexc("#4A3020")
        for x in range(4):
            for y in (7, 8, 9):
                img.putpixel((44 + x, 20 + y), jitter(leather, rnd, 4))
                img.putpixel((36 + x, 52 + y), jitter(leather, rnd, 4))
    if p.get("gold"):
        gold = hexc(p["gold"])
        # diadema en la capa exterior de la cabeza y brazalete
        for x in range(8):
            img.putpixel((40 + x, 8 + 1), gold if x % 3 else shade(gold, 0.75))
            img.putpixel((32 + x, 8 + 1), gold)
            img.putpixel((48 + x, 8 + 1), gold)
            img.putpixel((56 + x, 8 + 1), gold)
        for x in range(4):
            img.putpixel((44 + x, 20 + 4), gold)
    return img


def make_pirate(p, seed):
    img = make_skin(p, seed)
    rnd = random.Random(seed * 13 + 7)
    if p.get("stripes"):
        st = hexc(p["stripes"])
        # camisa a rayas horizontales (cuerpo y mangas)
        for y in range(0, 8):
            if y % 2 == 1:
                for x in range(8):
                    img.putpixel((20 + x, 20 + y), jitter(st, rnd, 5))
                    img.putpixel((32 + x, 20 + y), jitter(st, rnd, 5))
                for x in range(4):
                    img.putpixel((16 + x, 20 + y), jitter(st, rnd, 5))
                    img.putpixel((28 + x, 20 + y), jitter(st, rnd, 5))
        for y in range(0, 6):
            if y % 2 == 1:
                for x in range(4):
                    img.putpixel((44 + x, 20 + y), jitter(st, rnd, 5))
                    img.putpixel((36 + x, 52 + y), jitter(st, rnd, 5))
    if p.get("sash"):
        sash = hexc(p["sash"])
        for x in range(8):
            img.putpixel((20 + x, 20 + 8), sash)
            img.putpixel((32 + x, 20 + 8), sash)
        img.putpixel((20 + 2, 20 + 9), sash)
    if p.get("bandana"):
        b = hexc(p["bandana"])

        def bandana(face, x, y, w, h):
            if face == "top":
                return jitter(b, rnd, 6) if (x + y) % 5 else hexc("#E8E2D0")
            if face == "bottom":
                return None
            if y < 2:
                return jitter(b, rnd, 6)
            if face == "back" and y < 4 and x in (3, 4, 5):
                return shade(b, 0.8)  # nudo
            return None
        outer(img, 32, 0, 8, 8, 8, bandana)
    if p.get("tricorn"):
        hat, trim = hexc(p["tricorn"]), hexc(p.get("trim", "#D9B13B"))

        def tricorn(face, x, y, w, h):
            if face == "top":
                return jitter(hat, rnd, 4)
            if face == "bottom":
                return None
            if y == 0:
                return trim if face == "front" and x in (3, 4) else jitter(hat, rnd, 4)
            if y == 1:
                return jitter(hat, rnd, 4) if face != "front" or x in (0, 1, 6, 7) else trim
            return None
        outer(img, 32, 0, 8, 8, 8, tricorn)
    if p.get("eyepatch"):
        black, strap = hexc("#121212"), hexc("#2A2420")
        for x in range(8):
            img.putpixel((8 + x, 8 + 3), strap)
            img.putpixel((0 + x, 8 + 3), strap)
            img.putpixel((16 + x, 8 + 3), strap)
            img.putpixel((24 + x, 8 + 3), strap)
        for (x, y) in ((5, 3), (6, 3), (5, 4), (6, 4), (5, 5), (6, 5)):
            img.putpixel((8 + x, 8 + y), black)
    if p.get("earring"):
        img.putpixel((16 + 2, 8 + 6), hexc("#F2CC4A"))   # oreja izquierda
    if p.get("trim"):
        gold = hexc(p["trim"])
        for y in range(0, 12):
            if y == 8:
                continue
            img.putpixel((20 + 0, 20 + y), gold)
            img.putpixel((20 + 7, 20 + y), gold)
        for y in (2, 4, 6):
            img.putpixel((20 + 2, 20 + y), gold)
            img.putpixel((20 + 5, 20 + y), gold)
        for x in range(4):
            img.putpixel((44 + x, 20 + 5), gold)
            img.putpixel((36 + x, 52 + 5), gold)
    return img


# ----------------------------------------------------------------- objetos
def white_flag():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pole, pole_d = hexc("#8A6A44"), hexc("#5A4028")
    cloth, cloth_d = hexc("#F4F2EC"), hexc("#C8C4B8")
    for y in range(1, 16):
        im.putpixel((3, y), pole)
        im.putpixel((4, y), pole_d)
    im.putpixel((3, 0), hexc("#D9B13B"))
    im.putpixel((4, 0), hexc("#9A7420"))
    for y in range(2, 9):
        for x in range(5, 14):
            wave = 1 if (x // 3) % 2 == 0 else 0
            yy = y + wave
            if yy < 10:
                im.putpixel((x, yy), cloth if (x + y) % 4 else cloth_d)
    for x in range(5, 14):
        wave = 1 if (x // 3) % 2 == 0 else 0
        im.putpixel((x, 9 + wave if 9 + wave < 16 else 15), cloth_d)
    return im


def ambassador_seal():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    paper, paper_d, edge = hexc("#EDE3C8"), hexc("#CDBF9C"), hexc("#9C8C68")
    wax, wax_d, wax_l = hexc("#A8202A"), hexc("#6E1018"), hexc("#D8505A")
    ribbon = hexc("#D9B13B")
    # pergamino enrollado en diagonal
    for i in range(1, 13):
        x, y = i + 1, 13 - i
        for k in (-1, 0, 1):
            if 0 <= y + k < 16:
                im.putpixel((x, y + k), paper if k == 0 else paper_d)
        im.putpixel((x, y + 2) if y + 2 < 16 else (x, 15), edge)
    for (x, y) in ((1, 13), (2, 14), (13, 1), (14, 2)):
        im.putpixel((x, y), edge)
    # cinta y sello de lacre
    for (x, y) in ((6, 10), (7, 11), (8, 12)):
        im.putpixel((x, y), ribbon)
    for (x, y) in ((6, 7), (7, 7), (8, 7), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (6, 9), (7, 9), (8, 9), (5, 9), (9, 9),
                   (6, 10), (7, 10), (8, 10)):
        im.putpixel((x, y), wax)
    for (x, y) in ((6, 7), (5, 8)):
        im.putpixel((x, y), wax_l)
    for (x, y) in ((9, 9), (8, 10)):
        im.putpixel((x, y), wax_d)
    im.putpixel((7, 8), wax_d)
    return im


if __name__ == "__main__":
    os.makedirs(PREVIEW, exist_ok=True)
    sheets = []
    for folder, variants, maker, chief, seed0 in (("viking", VIKINGS, make_viking, JARL, 500),
                                                 ("pirate", PIRATES, make_pirate, CAPTAIN, 600)):
        d = os.path.join(RES, "textures/entity", folder)
        os.makedirs(d, exist_ok=True)
        skins = []
        for i, p in enumerate(variants):
            s = maker(p, seed0 + i)
            s.save(os.path.join(d, f"{folder}_{i}.png"))
            skins.append(s)
        c = maker(chief, seed0 + 99)
        c.save(os.path.join(d, "captain.png"))
        skins.append(c)
        sheet = Image.new("RGBA", (len(skins) * 36, 34), (60, 60, 70, 255))
        for i, s in enumerate(skins):
            sheet.alpha_composite(front_preview(s), (i * 36 + 1, 1))
            sheet.alpha_composite(back_preview(s), (i * 36 + 18, 1))
        sheets.append(up(sheet, 6))
    total = Image.new("RGBA", (max(s.width for s in sheets), sum(s.height for s in sheets) + 12), (60, 60, 70, 255))
    y = 0
    for s in sheets:
        total.alpha_composite(s, (0, y))
        y += s.height + 12
    total.save(os.path.join(PREVIEW, "vikingos_y_piratas_x6.png"))
    for name, fn in (("white_flag", white_flag), ("ambassador_seal", ambassador_seal)):
        im = fn()
        im.save(os.path.join(RES, f"textures/item/{name}.png"))
        up(im, 8).save(os.path.join(PREVIEW, f"{name}_x8.png"))
    print("ok")
