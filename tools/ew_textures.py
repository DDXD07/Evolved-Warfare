"""Texturas originales de Evolved Warfare: skins de reclutas y bandidos 64x64, objetos, GUI e icono."""
import os
import random
import sys
from PIL import Image

RES = sys.argv[1]       # .../assets/evolvedwarfare
PREVIEW = sys.argv[2]


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def jitter(c, rnd, amt=6):
    n = rnd.randint(-amt, amt)
    return tuple(max(0, min(255, v + n)) for v in c[:3]) + (c[3],)


# ----------------------------------------------------------------- skins
FACES = ["top", "bottom", "right", "front", "left", "back"]


def box(img, u, v, w, h, d, painter):
    """Pinta una caja con el desplegado UV estándar de Minecraft."""
    layout = {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }
    for face, (fx, fy, fw, fh) in layout.items():
        for y in range(fh):
            for x in range(fw):
                c = painter(face, x, y, fw, fh)
                if c is not None:
                    img.putpixel((fx + x, fy + y), c)


VARIANTS = [
    # piel, pelo, túnica, pantalón, botas, estilo
    dict(skin="#E3B48F", hair="#5A3A1E", tunic="#7A5230", pants="#3E3528", boots="#2B1E14", style="short", beard=False, hood=None),
    dict(skin="#C68B5E", hair="#1E1A18", tunic="#3F6B3A", pants="#4A3B2A", boots="#2B1E14", style="short", beard=True, hood=None),
    dict(skin="#7A4E2E", hair="#141010", tunic="#34507A", pants="#3A342E", boots="#231710", style="crop", beard=False, hood=None),
    dict(skin="#EBC3A0", hair="#D9B45A", tunic="#8A2E2E", pants="#4A3F33", boots="#2B1E14", style="short", beard=True, hood="#6B5E4E"),
    dict(skin="#F0C8A8", hair="#A8442A", tunic="#4E5E7A", pants="#3E3528", boots="#2B1E14", style="long", beard=False, hood=None),
    dict(skin="#C99068", hair="#3B2416", tunic="#5E6B32", pants="#4A3B2A", boots="#231710", style="braid", beard=False, hood=None),
]


def make_skin(p, seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin, hair, tunic = hexc(p["skin"]), hexc(p["hair"]), hexc(p["tunic"])
    pants, boots = hexc(p["pants"]), hexc(p["boots"])
    belt, buckle = hexc("#4A2F18"), hexc("#B8B8B8")
    long_hair = p["style"] in ("long", "braid")

    def head(face, x, y, w, h):
        if face == "top":
            return jitter(hair, rnd, 5)
        if face == "bottom":
            return jitter(skin, rnd, 3)
        hair_rows = 2 if p["style"] in ("short", "crop") else 2
        if face == "front":
            if y < hair_rows or (long_hair and (x == 0 or x == 7) and y < 7):
                return jitter(hair, rnd, 5)
            if y == 3 and x in (1, 2, 5, 6):
                return shade(hair, 0.85)  # cejas
            if y == 4 and x in (1, 6):
                return hexc("#F2F2F2")
            if y == 4 and x in (2, 5):
                return hexc("#3B5A8A") if seed % 2 else hexc("#4A3220")
            if y == 5 and x in (3, 4):
                return shade(skin, 0.86)  # nariz
            if p["beard"] and y >= 6 or (p["beard"] and y == 5 and x in (0, 7)):
                return jitter(shade(hair, 0.95), rnd, 4) if not (y == 6 and x in (3, 4)) else shade(skin, 0.6)
            if y == 6 and x in (3, 4):
                return shade(skin, 0.7)  # boca
            return jitter(skin, rnd, 3)
        if face == "back":
            if long_hair or y < 4:
                return jitter(hair, rnd, 5)
            return jitter(skin, rnd, 3)
        # laterales
        if y < 3 or (long_hair and y < 8) or (face in ("right", "left") and y < 4 and x > 3):
            return jitter(hair, rnd, 5)
        if p["beard"] and y >= 5 and x < 3 and face == "right" or p["beard"] and y >= 5 and x > 4 and face == "left":
            return jitter(hair, rnd, 4)
        return jitter(skin, rnd, 3)

    def body(face, x, y, w, h):
        if face == "front" and y == 0 and x in (3, 4):
            return jitter(skin, rnd, 3)  # escote
        if y == 8:
            if face == "front" and x in (3, 4):
                return buckle
            return jitter(belt, rnd, 3)
        c = jitter(tunic, rnd, 6)
        if face == "front" and x in (0, 7):
            c = shade(c, 0.9)
        if y >= 9:
            c = shade(c, 0.92)
        if y == 11:
            c = shade(c, 0.8)  # dobladillo
        return c

    def arm(face, x, y, w, h):
        if face == "bottom":
            return jitter(skin, rnd, 3)
        if face == "top":
            return jitter(tunic, rnd, 6)
        if y < 6:
            c = jitter(tunic, rnd, 6)
            return shade(c, 0.85) if y == 5 else c
        return jitter(skin, rnd, 3)

    def leg(face, x, y, w, h):
        if face == "bottom":
            return shade(boots, 0.8)
        if face == "top":
            return jitter(pants, rnd, 4)
        if y >= 9:
            c = jitter(boots, rnd, 4)
            return shade(c, 1.25) if y == 9 else c
        return jitter(pants, rnd, 5)

    box(img, 0, 0, 8, 8, 8, head)
    box(img, 16, 16, 8, 12, 4, body)
    box(img, 40, 16, 4, 12, 4, arm)       # brazo derecho
    box(img, 32, 48, 4, 12, 4, arm)       # brazo izquierdo
    box(img, 0, 16, 4, 12, 4, leg)        # pierna derecha
    box(img, 16, 48, 4, 12, 4, leg)       # pierna izquierda

    # Capa exterior: capucha o trenza
    if p["hood"]:
        hood = hexc(p["hood"])

        def hood_paint(face, x, y, w, h):
            if face == "bottom":
                return None
            if face == "front":
                return jitter(hood, rnd, 5) if y < 1 or (x in (0, 7) and y < 6) else None
            if face == "top":
                return jitter(hood, rnd, 5)
            return jitter(hood, rnd, 5) if y < 7 else None
        box(img, 32, 0, 8, 8, 8, hood_paint)

        def cape(face, x, y, w, h):
            if face in ("back",) and y < 5:
                return jitter(hood, rnd, 5)
            if face == "top":
                return jitter(hood, rnd, 5)
            if face in ("right", "left") and y < 2:
                return jitter(hood, rnd, 5)
            return None
        box(img, 16, 32, 8, 12, 4, cape)
    if p["style"] == "braid":
        def braid(face, x, y, w, h):
            if face == "back" and x in (3, 4) and y >= 2:
                return jitter(shade(hair, 1.15 if (y % 2) else 0.9), rnd, 3)
            return None
        box(img, 32, 0, 8, 8, 8, braid)
    return img


# ----------------------------------------------------------------- contrato (16x16)
def contract():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    paper, paper_d, paper_l = hexc("#E8D9B0"), hexc("#B89F6E"), hexc("#F7EED4")
    ink = hexc("#3A2E2A")
    # pergamino inclinado
    for y in range(2, 14):
        for x in range(3, 13):
            im.putpixel((x, y), paper)
    for x in range(3, 13):
        im.putpixel((x, 2), paper_l)
        im.putpixel((x, 13), paper_d)
    for y in range(2, 14):
        im.putpixel((3, y), paper_l)
        im.putpixel((12, y), paper_d)
    # rollos arriba y abajo
    for x in range(2, 14):
        im.putpixel((x, 1), paper_d)
        im.putpixel((x, 14), paper_d)
    im.putpixel((2, 2), paper_d)
    im.putpixel((13, 13), paper_d)
    # líneas de texto
    for y in (4, 6, 8):
        for x in range(5, 11 if y != 8 else 9):
            im.putpixel((x, y), ink)
    # firma
    for (x, y) in [(5, 11), (6, 10), (7, 11), (8, 10)]:
        im.putpixel((x, y), ink)
    # sello de cera rojo con cinta
    seal, seal_d, seal_l = hexc("#B3262B"), hexc("#7A151A"), hexc("#E0555A")
    for (x, y) in [(10, 10), (11, 10), (10, 11), (11, 11), (9, 10), (12, 10), (10, 9), (11, 9), (10, 12), (11, 12), (9, 11), (12, 11)]:
        im.putpixel((x, y), seal)
    im.putpixel((10, 10), seal_l)
    im.putpixel((11, 12), seal_d)
    im.putpixel((12, 11), seal_d)
    im.putpixel((10, 13), seal)
    im.putpixel((12, 13), seal)
    im.putpixel((10, 14), seal_d)
    im.putpixel((12, 14), seal_d)
    return im


# ----------------------------------------------------------------- GUI (estilo vanilla)
def gui():
    W, H = 176, 224
    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    bg, hi, sh, black = hexc("#C6C6C6"), hexc("#FFFFFF"), hexc("#555555"), hexc("#000000")

    def rect(x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                im.putpixel((x, y), c)
    rect(1, 1, W - 1, H - 1, bg)
    rect(2, 0, W - 2, 1, black)
    rect(2, H - 1, W - 2, H, black)
    rect(0, 2, 1, H - 2, black)
    rect(W - 1, 2, W, H - 2, black)
    for p in [(1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)]:
        im.putpixel(p, black)
    rect(2, 1, W - 3, 3, hi)
    rect(1, 2, 3, H - 3, hi)
    rect(3, H - 3, W - 2, H - 1, sh)
    rect(W - 3, 3, W - 1, H - 2, sh)
    im.putpixel((W - 3, 2), bg)
    im.putpixel((2, H - 3), bg)

    def slot(x, y):
        rect(x, y, x + 18, y + 18, hexc("#8B8B8B"))
        rect(x, y, x + 17, y + 1, hexc("#373737"))
        rect(x, y, x + 1, y + 17, hexc("#373737"))
        rect(x + 1, y + 17, x + 18, y + 18, hi)
        rect(x + 17, y + 1, x + 18, y + 18, hi)

    for i in range(4):
        slot(7, 17 + i * 18)
    slot(79, 53)
    slot(79, 71)
    slot(100, 62)
    slot(118, 62)
    # recuadro del retrato
    rect(25, 16, 77, 90, hexc("#373737"))
    rect(26, 17, 77, 90, hi)
    rect(26, 17, 76, 89, black)
    # inventario
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, 141 + r * 18)
    for c in range(9):
        slot(7 + c * 18, 199)
    return im


def up(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def front_preview(skin):
    """Vista frontal plana de una skin (para revisar)."""
    out = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    out.alpha_composite(skin.crop((8, 8, 16, 16)), (4, 0))
    out.alpha_composite(skin.crop((40, 8, 48, 16)), (4, 0))
    out.alpha_composite(skin.crop((20, 20, 28, 32)), (4, 8))
    out.alpha_composite(skin.crop((20, 36, 28, 48)), (4, 8))
    out.alpha_composite(skin.crop((44, 20, 48, 32)), (0, 8))
    out.alpha_composite(skin.crop((36, 52, 40, 64)), (12, 8))
    out.alpha_composite(skin.crop((4, 20, 8, 32)), (4, 20))
    out.alpha_composite(skin.crop((20, 52, 24, 64)), (8, 20))
    return out


def back_preview(skin):
    out = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    out.alpha_composite(skin.crop((24, 8, 32, 16)), (4, 0))
    out.alpha_composite(skin.crop((56, 8, 64, 16)), (4, 0))
    out.alpha_composite(skin.crop((32, 20, 40, 32)), (4, 8))
    out.alpha_composite(skin.crop((32, 36, 40, 48)), (4, 8))
    out.alpha_composite(skin.crop((52, 20, 56, 32)), (12, 8))
    out.alpha_composite(skin.crop((44, 52, 48, 64)), (0, 8))
    out.alpha_composite(skin.crop((12, 20, 16, 32)), (8, 20))
    out.alpha_composite(skin.crop((28, 52, 32, 64)), (4, 20))
    return out



# ----------------------------------------------------------------- bandidos (fase 2)
BANDIT_VARIANTS = [
    dict(skin="#D9A47E", hair="#2A1C12", tunic="#4A3423", pants="#2E2A24", boots="#1E140C", style="short",
         beard=False, hood="#3A2A1C", mask="#8E1F1F"),
    dict(skin="#B57A50", hair="#141010", tunic="#5E1B1B", pants="#2E2A24", boots="#1E140C", style="crop",
         beard=True, hood=None, mask="#1F1F1F"),
    dict(skin="#E8B896", hair="#6B4A2A", tunic="#3C3F2A", pants="#2B2620", boots="#1A120A", style="short",
         beard=False, hood="#262626", scar=True),
    dict(skin="#C99068", hair="#7A2E1A", tunic="#2B2B2B", pants="#3A3026", boots="#1E140C", style="long",
         beard=False, hood=None, mask="#3C3F2A"),
]
CAPTAIN = dict(skin="#D2A07A", hair="#1A1410", tunic="#1E1E22", pants="#3A2A1E", boots="#120C08", style="short",
               beard=True, hood=None, eyepatch=True, trim="#C9A33A")


def make_bandit(p, seed):
    img = make_skin(p, seed)
    rnd = random.Random(seed * 7 + 3)
    if p.get("mask"):
        m = hexc(p["mask"])
        # pañuelo en la capa exterior de la cabeza: cara, lados y nudo detrás
        for x in range(8):
            for y in (5, 6, 7):
                img.putpixel((40 + x, 8 + y), jitter(m, rnd, 6) if not (y == 7 and x in (0, 7)) else shade(m, 0.8))
                img.putpixel((32 + x, 8 + y), jitter(m, rnd, 6))   # lado derecho
                img.putpixel((48 + x, 8 + y), jitter(m, rnd, 6))   # lado izquierdo
        for (x, y) in ((3, 5), (4, 5), (3, 6), (4, 7), (5, 7)):
            img.putpixel((56 + x, 8 + y), shade(m, 0.75))          # nudo
    if p.get("scar"):
        scar = hexc("#9C4A42")
        for (x, y) in ((5, 2), (6, 3), (5, 4), (6, 5)):
            img.putpixel((8 + x, 8 + y), scar)
    if p.get("eyepatch"):
        black, strap = hexc("#121212"), hexc("#2A2420")
        for x in range(8):
            img.putpixel((8 + x, 8 + 3), strap)
        for (x, y) in ((1, 3), (2, 3), (1, 4), (2, 4), (1, 5), (2, 5)):
            img.putpixel((8 + x, 8 + y), black)
        for x in range(8):
            img.putpixel((0 + x, 8 + 3), strap)
            img.putpixel((16 + x, 8 + 3), strap)
            img.putpixel((24 + x, 8 + 3), strap)
    if p.get("trim"):
        gold = hexc(p["trim"])
        for y in range(0, 12):
            if y == 8:
                continue
            img.putpixel((20 + 0, 20 + y), gold)
            img.putpixel((20 + 7, 20 + y), gold)
        for y in (2, 4, 6):
            img.putpixel((20 + 3, 20 + y), gold)
            img.putpixel((20 + 4, 20 + y), shade(gold, 0.8))
        for x in range(4):  # puños dorados
            img.putpixel((44 + x, 20 + 5), gold)
            img.putpixel((36 + x, 52 + 5), gold)
    return img


# ----------------------------------------------------------------- objetos (fase 2)
def command_staff():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    wood, wood_d, wood_l = hexc("#7A5230"), hexc("#4A3018"), hexc("#A0723F")
    gold, gold_d, gold_l = hexc("#D9B13B"), hexc("#9A7420"), hexc("#F5DE7A")
    red, red_d = hexc("#B3262B"), hexc("#7A151A")
    for i in range(2, 12):
        x, y = i, 15 - i
        im.putpixel((x, y), wood)
        im.putpixel((x - 1, y), wood_d)
        im.putpixel((x, y + 1), wood_d) if y + 1 < 16 else None
        if i % 3 == 0:
            im.putpixel((x, y), wood_l)
    # remate dorado
    for (x, y) in ((11, 3), (12, 3), (11, 4), (12, 2), (13, 2), (13, 3), (12, 4), (14, 1), (13, 1)):
        im.putpixel((x, y), gold)
    for (x, y) in ((12, 2), (14, 1)):
        im.putpixel((x, y), gold_l)
    for (x, y) in ((11, 4), (12, 4)):
        im.putpixel((x, y), gold_d)
    # banderín rojo
    for (x, y) in ((9, 3), (8, 3), (7, 3), (8, 4), (7, 4), (6, 4), (7, 5), (9, 4), (8, 5), (9, 5), (10, 4), (10, 5)):
        im.putpixel((x, y), red)
    for (x, y) in ((6, 4), (7, 5), (8, 5)):
        im.putpixel((x, y), red_d)
    im.putpixel((8, 4), gold)
    return im


def medic_kit():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    box_c, box_d, box_l = hexc("#E8E2D0"), hexc("#A39C88"), hexc("#FFFFFF")
    red = hexc("#C0282D")
    strap = hexc("#6B4A2A")
    for y in range(5, 14):
        for x in range(2, 14):
            im.putpixel((x, y), box_c)
    for x in range(2, 14):
        im.putpixel((x, 5), box_l)
        im.putpixel((x, 13), box_d)
    for y in range(5, 14):
        im.putpixel((2, y), box_l)
        im.putpixel((13, y), box_d)
    # asa
    for x in range(6, 10):
        im.putpixel((x, 3), strap)
    im.putpixel((5, 4), strap)
    im.putpixel((10, 4), strap)
    # cruz roja
    for y in range(7, 12):
        im.putpixel((7, y), red)
        im.putpixel((8, y), red)
    for x in range(5, 11):
        im.putpixel((x, 9), red)
        im.putpixel((x, 8), red) if x in (5, 10) else None
    for x in range(5, 11):
        im.putpixel((x, 8), red)
    return im


if __name__ == "__main__":
    os.makedirs(PREVIEW, exist_ok=True)
    sheet = Image.new("RGBA", (len(VARIANTS) * 36, 34), (60, 60, 70, 255))
    for i, p in enumerate(VARIANTS):
        s = make_skin(p, 100 + i)
        s.save(os.path.join(RES, f"textures/entity/recruit/recruit_{i}.png"))
        sheet.alpha_composite(front_preview(s), (i * 36 + 1, 1))
        sheet.alpha_composite(back_preview(s), (i * 36 + 18, 1))
    up(sheet, 6).save(os.path.join(PREVIEW, "recruits_x6.png"))

    c = contract()
    c.save(os.path.join(RES, "textures/item/recruitment_contract.png"))
    up(c, 8).save(os.path.join(PREVIEW, "contract_x8.png"))

    g = gui()
    g.save(os.path.join(RES, "textures/gui/npc.png"))
    up(g.crop((0, 0, 176, 224)), 3).save(os.path.join(PREVIEW, "gui_x3.png"))

    # icono 128x128: cara del recluta 0 + contrato
    s0 = Image.open(os.path.join(RES, "textures/entity/recruit/recruit_0.png")).convert("RGBA")
    icon = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    face = s0.crop((8, 8, 16, 16))
    icon.alpha_composite(up(face, 12), (4, 4))
    icon.alpha_composite(up(c, 4), (64, 64))
    icon.save(os.path.join(RES, "icon.png"))

    # fase 2: bandidos, capitán y objetos
    os.makedirs(os.path.join(RES, "textures/entity/bandit"), exist_ok=True)
    sheet = Image.new("RGBA", ((len(BANDIT_VARIANTS) + 1) * 36, 34), (60, 60, 70, 255))
    for i, p in enumerate(BANDIT_VARIANTS):
        b = make_bandit(p, 300 + i)
        b.save(os.path.join(RES, f"textures/entity/bandit/bandit_{i}.png"))
        sheet.alpha_composite(front_preview(b), (i * 36 + 1, 1))
        sheet.alpha_composite(back_preview(b), (i * 36 + 18, 1))
    cap = make_bandit(CAPTAIN, 399)
    cap.save(os.path.join(RES, "textures/entity/bandit/captain.png"))
    k = len(BANDIT_VARIANTS)
    sheet.alpha_composite(front_preview(cap), (k * 36 + 1, 1))
    sheet.alpha_composite(back_preview(cap), (k * 36 + 18, 1))
    up(sheet, 6).save(os.path.join(PREVIEW, "bandits_x6.png"))
    for name, fn in (("command_staff", command_staff), ("medic_kit", medic_kit)):
        im = fn()
        im.save(os.path.join(RES, f"textures/item/{name}.png"))
        up(im, 8).save(os.path.join(PREVIEW, f"{name}_x8.png"))
    print("ok")
