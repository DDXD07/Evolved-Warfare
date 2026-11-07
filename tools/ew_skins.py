"""
Los aspectos de las personas del mod (fase 50): pieles de 64x64 con las dos capas (la de dentro y la de encima: capuchas,
sombreros, chalecos, mangas, cinturones), pintadas con detalle (tejido, costuras, sombras, botones, hebillas, bordados) y
muchas variantes por oficio y facción: tonos de piel, pelo y peinados, barbas, ojos, cicatrices, parches, pinturas de
guerra, tatuajes, pendientes...

    reclutas 14 · soldados 12 · guardias de ciudad 10 · trabajadores 12 · comerciantes 10
    bandidos 12 + jefe · piratas 12 + capitán · vikingos 12 + jarl

Uso: python3 tools/ew_skins.py [raíz del proyecto] [carpeta de vistas previas]
"""
import os
import random
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None
OUT = os.path.join(ROOT, "src/main/resources/assets/evolvedwarfare/textures/entity")


# ------------------------------------------------------------------ colores

def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (255,)


SKINS = ["#F3D2B6", "#E8BB98", "#D9A27A", "#C48659", "#9C6640", "#7A4C2E", "#5A3620"]
HAIRS = ["#1A1412", "#2E1F16", "#4A2E1A", "#6B4424", "#8A5A2E", "#B07A3A", "#D8B460", "#E6D29A", "#A63E22", "#C25A2A", "#8C8C8C", "#D8D8D0"]
EYES = ["#3B5A8A", "#4A3220", "#2E6B3A", "#5E5E6E", "#6B4A2A", "#2A2A2A"]


class Painter:
    """Una piel: pinta cada cara de cada caja con su luz (arriba más claro, abajo más oscuro) y tejido."""

    BOXES = {
        # nombre: (u, v, ancho, alto, fondo)
        "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8),
        "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
        "rarm": (40, 16, 4, 12, 4), "rsleeve": (40, 32, 4, 12, 4),
        "larm": (32, 48, 4, 12, 4), "lsleeve": (48, 48, 4, 12, 4),
        "rleg": (0, 16, 4, 12, 4), "rpants": (0, 32, 4, 12, 4),
        "lleg": (16, 48, 4, 12, 4), "lpants": (0, 48, 4, 12, 4),
    }

    def __init__(self, seed):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.rnd = random.Random(seed)

    def noise(self, c, amt=6):
        j = self.rnd.randint(-amt, amt)
        return (max(0, min(255, c[0] + j)), max(0, min(255, c[1] + j)), max(0, min(255, c[2] + j)), c[3])

    def cloth(self, c, x, y, face, h, amt=7):
        """Tela: trama (cuadritos alternos un poco distintos), luz de arriba, sombra en los bordes y abajo."""
        f = 1.0
        if (x + y) % 2 == 0:
            f *= 1.03
        if face == "top":
            f *= 1.1
        elif face == "bottom":
            f *= 0.75
        elif face in ("right", "left"):
            f *= 0.9
        elif face == "back":
            f *= 0.94
        if h > 1:
            f *= 1.05 - 0.12 * (y / (h - 1))
        return self.noise(shade(c, f), amt)

    def paint(self, name, fn):
        u, v, w, h, d = self.BOXES[name]
        layout = {
            "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
        }
        for face, (fx, fy, fw, fh) in layout.items():
            for y in range(fh):
                for x in range(fw):
                    c = fn(face, x, y, fw, fh)
                    if c is not None:
                        self.img.putpixel((fx + x, fy + y), c)


# ------------------------------------------------------------------ una persona

def person(seed, **o):
    """
    Pinta a una persona. Opciones (todas con valor por defecto):
      skin, hair, eyes, style (short, crop, long, bald, ponytail, braids, topknot), beard (none, stubble, short, full, long,
      braided, mustache), shirt, shirt_kind (tunic, shirt, gambeson, robe, stripes), sleeves (long, short, rolled),
      sleeve_color, pants, boots, boot_kind (boots, shoes, wraps, sandals), belt, buckle,
      apron, vest, tabard (color, emblem color), sash, bandolier, cloak, scarf, gloves, bracers,
      hood, hat (straw, cap, tricorn, bandana, helm, turban, fur, beret), hat_color,
      scar, eyepatch, freckles, warpaint, tattoo, earring, pouch, emblem
    """
    p = Painter(seed)
    r = p.rnd
    skin = hexc(o.get("skin", SKINS[seed % len(SKINS)]))
    hair = hexc(o.get("hair", HAIRS[seed % len(HAIRS)]))
    eyes = hexc(o.get("eyes", EYES[seed % len(EYES)]))
    style = o.get("style", "short")
    beard = o.get("beard", "none")
    shirt = hexc(o.get("shirt", "#7A5230"))
    kind = o.get("shirt_kind", "tunic")
    sleeves = o.get("sleeves", "long")
    sleeve_c = hexc(o["sleeve_color"]) if o.get("sleeve_color") else shirt
    pants = hexc(o.get("pants", "#3E3528"))
    boots = hexc(o.get("boots", "#2B1E14"))
    boot_kind = o.get("boot_kind", "boots")
    belt = hexc(o["belt"]) if o.get("belt") else None
    buckle = hexc(o.get("buckle", "#BDBDBD"))
    beard_c = shade(hair, 0.92)
    dark_skin = shade(skin, 0.82)

    def skin_px(face, x, y, h):
        c = p.noise(skin, 3)
        if face == "bottom":
            return shade(c, 0.85)
        if face in ("right", "left"):
            return shade(c, 0.93)
        return c

    # -------- la cabeza
    long_hair = style in ("long", "braids", "ponytail")

    def head(face, x, y, w, h):
        if face == "top":
            if style == "bald":
                return p.noise(shade(skin, 1.04), 3)
            if style == "topknot":
                return p.noise(hair, 5) if 2 <= x <= 5 and 2 <= y <= 5 else p.noise(shade(skin, 1.02), 3)
            return p.noise(shade(hair, 1.08 if (x + y) % 3 else 0.95), 5)
        if face == "bottom":
            if beard in ("full", "long", "braided"):
                return p.noise(shade(beard_c, 0.85), 4)
            return shade(skin, 0.8)
        hair_rows = {"bald": 0, "crop": 1, "topknot": 1}.get(style, 2)
        if face == "front":
            # pelo y flequillo
            if y < hair_rows:
                return p.noise(shade(hair, 1.05 if x % 2 else 0.95), 5)
            if style in ("short", "long", "braids", "ponytail") and y == 2 and x in (0, 7):
                return p.noise(hair, 5)
            if long_hair and x in (0, 7) and y < 7:
                return p.noise(shade(hair, 0.9), 5)
            # cejas
            if y == 3 and x in (1, 2, 5, 6):
                return shade(hair, 0.75) if style != "bald" else shade(skin, 0.7)
            # ojos
            if y == 4:
                if o.get("eyepatch") and x in (1, 2):
                    return hexc("#141414")
                if x in (1, 6):
                    return hexc("#F0EEE8")
                if x in (2, 5):
                    return eyes
            if o.get("eyepatch") and ((y == 3 and x in (0, 1, 2, 3)) or (y == 2 and x in (3, 4, 5, 6, 7))):
                return hexc("#1E1E1E")
            # cicatriz (cruza la ceja y la mejilla)
            if o.get("scar") and x == 6 and 2 <= y <= 6 and y != 4:
                return hexc("#B07060") if skin[0] > 150 else hexc("#8A5040")
            # pintura de guerra
            if o.get("warpaint"):
                wp = hexc(o["warpaint"])
                if y == 5 and x in (0, 1, 6, 7):
                    return wp
                if y == 4 and x in (0, 7):
                    return wp
            # nariz y mejillas
            if y == 5 and x in (3, 4):
                return shade(skin, 0.84) if x == 4 else shade(skin, 0.92)
            if o.get("freckles") and y == 5 and x in (1, 2, 5, 6) and (x + seed) % 2:
                return shade(skin, 0.78)
            # barba y bigote
            if beard != "none":
                if beard == "stubble" and y >= 6:
                    return mix(skin, beard_c, 0.45) if (x + y) % 2 else mix(skin, beard_c, 0.3)
                if beard == "mustache":
                    if y == 6 and 2 <= x <= 5:
                        return p.noise(beard_c, 4)
                elif beard in ("short", "full", "long", "braided"):
                    if y == 6 and x in (3, 4):
                        return hexc("#5A2E24")  # boca
                    if y >= 6 or (y == 5 and x in (0, 1, 6, 7) and beard != "short"):
                        return p.noise(shade(beard_c, 1.05 if (x + y) % 2 else 0.95), 4)
            if y == 6 and x in (3, 4):
                return hexc("#8A4A3E") if skin[0] > 150 else hexc("#4A2420")
            if y == 7 and x in (0, 7):
                return shade(skin, 0.9)
            return p.noise(skin, 3)
        if face == "back":
            if style == "bald":
                return p.noise(skin, 3)
            if style == "crop" and y > 3:
                return p.noise(skin, 3)
            if style == "topknot" and y > 2:
                return p.noise(skin, 3)
            if style == "short" and y > 5:
                return p.noise(skin, 3)
            return p.noise(shade(hair, 1.05 if (x * 3 + y) % 4 else 0.9), 5)
        # los lados
        if style == "bald":
            if face == "right" and y == 4 and x == 6 or face == "left" and y == 4 and x == 1:
                return dark_skin  # oreja
            return skin_px(face, x, y, h)
        top_rows = {"crop": 2, "topknot": 1}.get(style, 3)
        back_half = (face == "right" and x < 4) or (face == "left" and x > 3)
        if y < top_rows or (long_hair and back_half) or (style == "short" and back_half and y < 6):
            return p.noise(shade(hair, 0.95), 5)
        if o.get("earring") and y == 5 and ((face == "right" and x == 5) or (face == "left" and x == 2)):
            return hexc("#E8C040")
        if beard in ("short", "full", "long", "braided") and y >= 5 and ((face == "right" and x >= 4) or (face == "left" and x <= 3)):
            return p.noise(beard_c, 4)
        if (face == "right" and x == 5 or face == "left" and x == 2) and y == 4:
            return dark_skin  # oreja
        return skin_px(face, x, y, h)

    p.paint("head", head)

    # -------- el cuerpo
    def shirt_px(face, x, y, w, h, base=None):
        c = base or shirt
        if kind == "stripes" and y % 2 == 0:
            c = hexc(o.get("stripe", "#E8E4DA"))
        if kind == "gambeson" and face in ("front", "back") and (y % 3 == 0 or x % 4 == 0):
            return p.noise(shade(c, 0.82), 4)  # acolchado
        return p.cloth(c, x, y, face, h)

    def body(face, x, y, w, h):
        if face == "top":
            return p.cloth(shirt, x, y, face, h)
        if face == "bottom":
            return p.cloth(pants, x, y, face, h)
        robe = kind == "robe"
        if y >= 8 and not robe:
            # cadera: el pantalón (con el cinturón encima)
            c = p.cloth(pants, x, y, face, h)
            if belt and y == 8:
                if face == "front" and x in (3, 4):
                    return buckle if not (x == 4) else shade(buckle, 0.75)
                return p.noise(belt, 3)
            if kind in ("tunic", "gambeson") and y == 9:
                return p.cloth(shade(shirt, 0.85), x, y, face, h)  # los faldones
            return c
        if face == "front":
            # escote y cuello
            if kind in ("tunic", "shirt") and y == 0 and x in (3, 4):
                return skin_px(face, x, y, h)
            if kind == "shirt" and y == 1 and x in (3, 4):
                return skin_px(face, x, y, h) if x == 3 else p.cloth(shirt, x, y, face, h)
            if kind == "shirt" and x == 4 and y in (3, 5, 7):
                return hexc("#D8D0B8")  # botones
            if kind == "tunic" and x == 4 and 1 <= y <= 3:
                return shade(shirt, 0.7)  # cordón
            if robe:
                if x in (3, 4):
                    return p.cloth(hexc(o.get("trim", "#C8A040")), x, y, face, h) if (y + x) % 2 else p.cloth(shade(hexc(o.get("trim", "#C8A040")), 0.85), x, y, face, h)
                if belt and y == 7:
                    return p.noise(belt, 3)
        if robe and belt and y == 7:
            return p.noise(belt, 3)
        c = shirt_px(face, x, y, w, h)
        if face == "front" and x in (0, w - 1):
            c = shade(c, 0.9)
        return c

    p.paint("body", body)

    # -------- los brazos
    def arm(left):
        def fn(face, x, y, w, h):
            if face == "top":
                return p.cloth(sleeve_c if sleeves != "none" else skin, x, y, face, h) if sleeves != "none" else skin_px(face, x, y, h)
            if face == "bottom":
                if o.get("gloves"):
                    return p.noise(shade(hexc(o["gloves"]), 0.8), 3)
                return shade(skin, 0.85)
            end = {"long": 10, "short": 4, "rolled": 6, "none": 0}[sleeves]
            if o.get("gloves") and y >= 9:
                return p.cloth(hexc(o["gloves"]), x, y, face, h, 4)
            if o.get("bracers") and 6 <= y <= 9:
                c = hexc(o["bracers"])
                return p.noise(shade(c, 1.15) if y in (6, 9) else c, 4)
            if y < end:
                c = p.cloth(sleeve_c, x, y, face, h)
                if sleeves == "rolled" and y == end - 1:
                    return shade(c, 1.15)
                if kind == "stripes" and y % 2 == 0:
                    return p.cloth(hexc(o.get("stripe", "#E8E4DA")), x, y, face, h)
                if kind == "gambeson" and y % 3 == 0:
                    return shade(c, 0.85)
                if y == end - 1:
                    return shade(c, 0.85)
                return c
            if o.get("tattoo") and face == "front" and 6 <= y <= 9 and ((x + y) % 3 == 0) and not left:
                return mix(skin, hexc(o["tattoo"]), 0.65)
            c = skin_px(face, x, y, h)
            if y == 11:
                return shade(c, 0.92)  # la mano
            return c
        return fn

    p.paint("rarm", arm(False))
    p.paint("larm", arm(True))

    # -------- las piernas
    def leg(face, x, y, w, h):
        if face == "top":
            return p.cloth(pants, x, y, face, h)
        if face == "bottom":
            return shade(boots, 0.7) if boot_kind != "sandals" else shade(skin, 0.8)
        top_boot = {"boots": 7, "shoes": 10, "wraps": 6, "sandals": 11}[boot_kind]
        if kind == "robe" and y < 6:
            return p.cloth(shirt, x, y, face, h)
        if y >= top_boot:
            if boot_kind == "wraps":
                return p.noise(shade(boots, 1.1 if (y + x) % 2 else 0.9), 3)
            if boot_kind == "sandals":
                return p.noise(boots, 3) if y == 11 or x == 1 else skin_px(face, x, y, h)
            c = p.noise(boots, 4)
            if y == top_boot:
                return shade(c, 1.25)  # el borde de la bota
            if y == 11:
                return shade(c, 0.7)  # la suela
            return c
        c = p.cloth(pants, x, y, face, h)
        if face == "front" and x == 3 and y < top_boot:
            return shade(c, 0.88)  # costura
        if y == 3 and o.get("knees"):
            return shade(c, 1.1)
        return c

    p.paint("rleg", leg)
    p.paint("lleg", leg)

    # ------------------------------------------------------------------ la capa de encima
    hat = o.get("hat")
    hat_c = hexc(o.get("hat_color", "#C8A860"))
    hood = hexc(o["hood"]) if o.get("hood") else None

    def overlay_head(face, x, y, w, h):
        if hood:
            if face == "bottom":
                return None
            if face == "front":
                if y == 0 or (x in (0, 7) and y < 7):
                    return p.cloth(shade(hood, 0.85 if y > 0 else 1.0), x, y, face, h)
                if o.get("mask") and y >= 5:
                    return p.cloth(hexc(o["mask"]), x, y, face, h)
                return None
            if face == "top":
                return p.cloth(shade(hood, 1.05), x, y, face, h)
            return p.cloth(hood, x, y, face, h)
        if o.get("mask"):
            if face == "front" and y >= 5:
                return p.cloth(hexc(o["mask"]), x, y, face, h)
            if face in ("right", "left") and y >= 5:
                return p.cloth(shade(hexc(o["mask"]), 0.9), x, y, face, h)
        if hat == "straw":
            if face == "top":
                return p.noise(shade(hat_c, 1.1 if (x + y) % 2 else 0.95), 6)
            if face != "bottom" and y < 2:
                return p.noise(shade(hat_c, 0.9 if y == 1 else 1.0), 6)
            if face != "bottom" and y == 2:
                return hexc(o.get("band", "#7A3A28"))
        elif hat == "cap":
            if face == "top":
                return p.cloth(hat_c, x, y, face, h)
            if face != "bottom" and y < 2:
                return p.cloth(shade(hat_c, 0.92), x, y, face, h)
        elif hat == "beret":
            if face == "top":
                return p.cloth(hat_c, x, y, face, h)
            if face != "bottom" and y < 1:
                return p.cloth(shade(hat_c, 0.9), x, y, face, h)
            if face == "right" and y == 1:
                return p.cloth(hat_c, x, y, face, h)
        elif hat == "tricorn":
            if face == "top":
                return p.cloth(hat_c, x, y, face, h, 4)
            if face != "bottom" and y < 3:
                c = p.cloth(shade(hat_c, 0.95), x, y, face, h, 4)
                if y == 2:
                    return hexc(o.get("trim", "#C8A040"))
                if face == "front" and y == 1 and x in (3, 4) and o.get("skull"):
                    return hexc("#E8E4DA")
                return c
        elif hat == "bandana":
            bc = hexc(o.get("hat_color", "#A02020"))
            if face == "top":
                return p.cloth(bc, x, y, face, h)
            if face != "bottom" and y < 2:
                c = p.cloth(bc, x, y, face, h)
                return hexc("#E8E4DA") if (x + y) % 4 == 0 and o.get("dots") else c
            if face == "back" and y < 5 and x in (3, 4):
                return p.cloth(shade(bc, 0.85), x, y, face, h)  # el nudo
        elif hat == "turban":
            if face == "top":
                return p.cloth(hat_c, x, y, face, h)
            if face != "bottom" and y < 3:
                c = p.cloth(shade(hat_c, 1.08 if (x + y) % 3 == 0 else 0.95), x, y, face, h)
                if face == "front" and y == 1 and x in (3, 4):
                    return hexc(o.get("gem", "#C02040"))
                return c
        elif hat == "fur":
            if face == "top":
                return p.noise(shade(hat_c, 1.1 if (x * 7 + y) % 3 else 0.85), 8)
            if face != "bottom" and y < 2:
                return p.noise(shade(hat_c, 1.0 if (x + y) % 2 else 0.85), 8)
        elif hat == "helm":
            mc = hexc(o.get("hat_color", "#8A8A90"))
            if face == "top":
                return p.noise(shade(mc, 1.15 if x in (3, 4) else 1.0), 4)
            if face != "bottom" and y < 3:
                c = p.noise(shade(mc, 1.1 if y == 0 else 0.95), 4)
                if face == "front" and x in (3, 4) and y >= 1:
                    return shade(mc, 0.8)  # la nariguera
                if y == 2:
                    return shade(mc, 0.75)
                return c
            if face == "front" and x in (3, 4) and y in (3, 4) and o.get("nasal"):
                return shade(mc, 0.85)
            if face in ("right", "left", "back") and y < 6 and o.get("aventail"):
                return mail_px(p, x, y)  # cota
        # trenzas y coletas
        if style == "braids" and face in ("back",) and x in (1, 6) and y >= 2:
            return p.noise(shade(hair, 1.15 if y % 2 else 0.85), 3)
        if style == "ponytail" and face == "back" and x in (3, 4) and y >= 3:
            return p.noise(shade(hair, 1.1 if y % 2 else 0.9), 3) if y < 8 else None
        if beard == "braided" and face == "front" and y == 7 and x in (3, 4):
            return p.noise(shade(beard_c, 1.2), 3)
        if beard == "long" and face == "front" and y == 7:
            return p.noise(shade(beard_c, 0.95), 4)
        return None

    p.paint("hat", overlay_head)

    vest = hexc(o["vest"]) if o.get("vest") else None
    apron = hexc(o["apron"]) if o.get("apron") else None
    tabard = hexc(o["tabard"]) if o.get("tabard") else None
    emblem = hexc(o.get("emblem", "#E8D070"))
    cloak = hexc(o["cloak"]) if o.get("cloak") else None
    scarf = hexc(o["scarf"]) if o.get("scarf") else None
    sash = hexc(o["sash"]) if o.get("sash") else None
    bandolier = hexc(o["bandolier"]) if o.get("bandolier") else None
    fur = hexc(o["fur"]) if o.get("fur") else None
    mail = o.get("mail")

    def jacket(face, x, y, w, h):
        if face == "bottom":
            return None
        if fur and (face == "top" or y < 2):
            return p.noise(shade(fur, 1.1 if (x * 5 + y) % 3 else 0.8), 9)  # el cuello de piel
        if scarf and y == 0 and face != "top":
            return p.cloth(scarf, x, y, face, h)
        if scarf and face == "top":
            return p.cloth(scarf, x, y, face, h)
        if cloak and face in ("back", "top") or cloak and face in ("right", "left") and y < 3:
            c = p.cloth(cloak, x, y, face, h)
            if face == "back" and y == h - 1:
                return shade(c, 0.8)
            return c
        if mail and face != "top" and y < 9:
            return mail_px(p, x, y)
        if tabard and face in ("front", "back") and 1 <= x <= 6:
            c = p.cloth(tabard, x, y, face, h)
            if face == "front" and o.get("emblem") and 2 <= y <= 5 and 2 <= x <= 5:
                pattern = o.get("emblem_shape", "cross")
                if pattern == "cross" and (x in (3, 4) or y in (3, 4)):
                    return emblem
                if pattern == "tower" and (y >= 3 and 2 <= x <= 5 or y == 2 and x in (2, 5)):
                    return emblem
                if pattern == "diamond" and abs(x - 3.5) + abs(y - 3.5) <= 1.5:
                    return emblem
            if x in (1, 6):
                return shade(c, 0.85)
            if y == h - 1:
                return hexc(o.get("trim", "#C8A040"))
            return c
        if apron and face == "front" and 1 <= x <= 6 and y >= 2:
            c = p.cloth(apron, x, y, face, h)
            if y == 2:
                return shade(c, 0.85)
            if o.get("pocket") and y == 7 and 2 <= x <= 5:
                return shade(c, 0.8)
            return c
        if apron and face == "front" and y == 0 and x in (2, 5):
            return shade(apron, 0.8)  # las tiras
        if vest and face in ("front", "back", "right", "left") and y < 8:
            if face == "front" and x in (3, 4):
                return None  # abierto
            c = p.cloth(vest, x, y, face, h)
            if face == "front" and x in (2, 5) and y % 2 == 1 and o.get("vest_buttons"):
                return hexc("#C8A040")
            return c
        if sash and y in (7, 8):
            c = p.cloth(sash, x, y, face, h)
            if face == "front" and x == 6 and y == 8:
                return shade(c, 0.8)
            return c
        if bandolier and face == "front" and (x == y - 1 or x == y - 2) and y < 9:
            return p.noise(bandolier, 3) if (x + y) % 3 else hexc("#BDBDBD")
        if bandolier and face == "back" and (7 - x == y - 1 or 7 - x == y - 2) and y < 9:
            return p.noise(bandolier, 3)
        if o.get("pouch") and face == "front" and y in (9, 10) and x in (5, 6):
            return p.noise(hexc(o["pouch"]), 4)
        if belt and o.get("belt_tools") and face == "front" and y == 9 and x in (0, 1):
            return hexc("#9A9AA0")
        return None

    p.paint("jacket", jacket)

    def sleeve(left):
        def fn(face, x, y, w, h):
            if face in ("bottom",):
                return None
            if fur and y < 2:
                return p.noise(shade(fur, 1.05 if (x + y) % 2 else 0.8), 8)
            if mail and y < 5 and face != "top":
                return mail_px(p, x, y)
            if cloak and face == ("left" if left else "right") and y < 3:
                return p.cloth(cloak, x, y, face, h)
            if o.get("shoulder") and y < 2:
                return p.noise(shade(hexc(o["shoulder"]), 1.1 if y == 0 else 0.9), 4)
            return None
        return fn

    p.paint("rsleeve", sleeve(False))
    p.paint("lsleeve", sleeve(True))

    def pants_over(face, x, y, w, h):
        if face in ("top", "bottom"):
            return None
        if kind == "robe" and y < 7:
            return p.cloth(shade(shirt, 0.95), x, y, face, h)
        if o.get("kneepads") and y in (4, 5):
            return p.noise(hexc(o["kneepads"]), 4)
        if boot_kind == "boots" and o.get("cuffs") and y == 6:
            return p.noise(shade(boots, 1.3), 4)
        if o.get("wraps") and 3 <= y <= 6 and (x + y) % 2 == 0:
            return p.noise(hexc(o["wraps"]), 4)
        return None

    p.paint("rpants", pants_over)
    p.paint("lpants", pants_over)
    return p.img


# ------------------------------------------------------------------ las variantes por oficio

def mail_px(p, x, y):
    """Cota de malla: anillos (filas desplazadas, brillo arriba de cada anillo y hueco oscuro), no un tablero."""
    m = hexc("#8C8C94")
    k = (x + (y % 2)) % 2
    f = 1.18 if k == 0 and y % 2 == 0 else (0.98 if k == 0 else 0.78)
    return p.noise(shade(m, f), 4)


def pick(r, seq):
    return seq[r.randrange(len(seq))]


def people(name, count, base_seed, make):
    out = []
    for i in range(count):
        r = random.Random(base_seed * 1000 + i)
        out.append(person(base_seed * 100 + i, **make(r, i)))
    return out


def face_traits(r, i, allow_women=True):
    """Piel, pelo, ojos, peinado, barba y marcas de una persona cualquiera."""
    woman = allow_women and r.random() < 0.35
    d = dict(skin=pick(r, SKINS), hair=pick(r, HAIRS[:10] if r.random() < 0.85 else HAIRS), eyes=pick(r, EYES))
    if woman:
        d["style"] = pick(r, ["long", "braids", "ponytail", "long"])
        d["beard"] = "none"
        d["freckles"] = r.random() < 0.3
    else:
        d["style"] = pick(r, ["short", "short", "crop", "bald", "long", "ponytail"])
        d["beard"] = pick(r, ["none", "none", "stubble", "short", "full", "mustache"])
        if d["hair"] in ("#8C8C8C", "#D8D8D0") and d["beard"] == "none":
            d["beard"] = "full"
    d["scar"] = r.random() < 0.15
    return d


PEASANT = ["#7A5230", "#5E6B32", "#34507A", "#8A2E2E", "#6B5E4E", "#4E5E7A", "#8A6A3A", "#5A4A3A", "#3F6B3A", "#7A6248"]
EARTH = ["#3E3528", "#4A3B2A", "#3A342E", "#2E2A24", "#504434", "#3C3A30"]
LEATHER = ["#2B1E14", "#3A2616", "#231710", "#4A3020", "#1E1610"]


def recruit(r, i):
    d = face_traits(r, i)
    d.update(shirt=pick(r, PEASANT), shirt_kind=pick(r, ["tunic", "tunic", "shirt"]), sleeves=pick(r, ["long", "rolled", "long"]),
             pants=pick(r, EARTH), boots=pick(r, LEATHER), boot_kind=pick(r, ["boots", "shoes", "wraps"]), belt=pick(r, LEATHER + ["#6B4A2A"]))
    if r.random() < 0.3:
        d["hood"] = pick(r, ["#6B5E4E", "#4A4038", "#5A5A4A", "#3A3A30"])
    elif r.random() < 0.25:
        d.update(hat=pick(r, ["straw", "cap"]), hat_color=pick(r, ["#C8A860", "#5A4A3A", "#3A4A5A"]))
    if r.random() < 0.3:
        d["vest"] = pick(r, ["#4A3020", "#3A3A30", "#5A3A28"])
    if r.random() < 0.25:
        d["pouch"] = "#6B4A2A"
    if r.random() < 0.2:
        d["scarf"] = pick(r, PEASANT)
    return d


def soldier(r, i):
    d = face_traits(r, i)
    tab = ["#8A2E2E", "#34507A", "#3F6B3A", "#6B2E6B"][i % 4]
    d.update(shirt=pick(r, ["#8A7A5A", "#7A6A4A", "#6B5E4E"]), shirt_kind="gambeson", sleeves="long", pants=pick(r, EARTH), boots=pick(r, LEATHER),
             boot_kind="boots", belt="#3A2616", tabard=tab, emblem="#E8D070", emblem_shape=pick(r, ["cross", "tower", "diamond"]),
             trim="#C8A040", bracers="#4A3020", cuffs=True)
    if r.random() < 0.5:
        d.update(hat="helm", hat_color=pick(r, ["#8A8A90", "#7A7A80", "#9A9AA0"]), nasal=r.random() < 0.5, aventail=r.random() < 0.4)
    if r.random() < 0.3:
        d["mail"] = True
    if r.random() < 0.3:
        d["shoulder"] = "#5A4030"
    return d


def city_guard(r, i):
    d = face_traits(r, i, allow_women=r.random() < 0.5)
    d.update(shirt="#2E3E6B", shirt_kind="gambeson", sleeves="long", pants="#2A2A34", boots="#1E1610", boot_kind="boots", belt="#1E1610",
             tabard=pick(r, ["#2E4A8A", "#3A5A9A"]), emblem=pick(r, ["#E8E4DA", "#E8D070"]), emblem_shape="tower", trim="#E8E4DA",
             gloves="#3A2616", mail=r.random() < 0.6, cuffs=True)
    d.update(hat="helm", hat_color="#9A9AA0", nasal=True, aventail=r.random() < 0.5) if r.random() < 0.7 else d.update(hat="cap", hat_color="#2E3E6B")
    if r.random() < 0.4:
        d["cloak"] = "#2E3E6B"
    return d


def worker(r, i):
    d = face_traits(r, i)
    d.update(shirt=pick(r, ["#C8C0A8", "#B8A888", "#8A7A5A", "#6B5E4E", "#5A6A7A"]), shirt_kind="shirt", sleeves="rolled", pants=pick(r, EARTH),
             boots=pick(r, LEATHER), boot_kind="boots", belt="#4A3020", belt_tools=True, knees=True)
    choice = i % 4
    if choice == 0:
        d.update(apron=pick(r, ["#5A3A28", "#4A3020"]), pocket=True, gloves="#6B4A2A")  # herrero
    elif choice == 1:
        d.update(hat="straw", hat_color=pick(r, ["#D8B868", "#C8A860"]), scarf=pick(r, ["#8A2E2E", "#34507A"]))  # campesino
    elif choice == 2:
        d.update(vest="#5A4A3A", hat="cap", hat_color="#4A4038", kneepads="#4A3020")  # minero / leñador
    else:
        d.update(apron="#D8D0B8", pocket=True)  # molinero, panadero
    return d


def trader(r, i):
    d = face_traits(r, i)
    rich = ["#6B1E3A", "#1E3A6B", "#2E5A3A", "#6B4A1E", "#4A1E6B", "#8A3A1E"]
    d.update(shirt=pick(r, rich), shirt_kind="robe", sleeves="long", pants=pick(r, EARTH), boots=pick(r, ["#4A3020", "#6B4A2A"]),
             boot_kind=pick(r, ["shoes", "boots"]), belt=pick(r, ["#C8A040", "#3A2616"]), trim=pick(r, ["#C8A040", "#E8E4DA", "#B8B8C0"]),
             pouch="#C8A040", earring=r.random() < 0.3)
    hat = pick(r, ["turban", "beret", "cap", None])
    if hat:
        d.update(hat=hat, hat_color=pick(r, rich + ["#E8E4DA"]), gem=pick(r, ["#C02040", "#2080C0", "#30A050"]))
    if r.random() < 0.4:
        d["sash"] = pick(r, rich)
    return d


def bandit(r, i):
    d = face_traits(r, i)
    d.update(shirt=pick(r, ["#3A342E", "#2E2A24", "#4A3B2A", "#3A3A30", "#5A3A28"]), shirt_kind=pick(r, ["tunic", "shirt"]), sleeves=pick(r, ["long", "rolled"]),
             pants=pick(r, EARTH), boots=pick(r, LEATHER), boot_kind=pick(r, ["boots", "wraps"]), belt="#231710",
             bracers=pick(r, ["#3A2616", None]), wraps=pick(r, ["#6B5E4E", None]))
    if r.random() < 0.6:
        d["hood"] = pick(r, ["#2E2A24", "#3A342E", "#4A4038", "#2A3028"])
    if r.random() < 0.55:
        d["mask"] = pick(r, ["#1E1E1E", "#4A2E24", "#2E3A2E", "#5A1E1E"])
    if r.random() < 0.4:
        d["bandolier"] = "#3A2616"
    if r.random() < 0.3:
        d["cloak"] = pick(r, ["#2E2A24", "#3A3020"])
    d["scar"] = r.random() < 0.35
    d["eyepatch"] = r.random() < 0.1
    return d


def bandit_captain(r):
    return dict(skin="#D2A07A", hair="#1A1410", eyes="#2A2A2A", style="short", beard="short", scar=True, shirt="#1E1E22", shirt_kind="shirt",
                sleeves="long", pants="#3A2A1E", boots="#120C08", boot_kind="boots", belt="#120C08", buckle="#C8A040", hood="#141418",
                mask="#141414", cloak="#141418", bandolier="#3A2616", gloves="#1E1610", cuffs=True)


def pirate(r, i):
    d = face_traits(r, i)
    d.update(shirt=pick(r, ["#E8E4DA", "#D8D0B8", "#1E2E5A", "#8A2E2E"]), shirt_kind=pick(r, ["stripes", "shirt", "stripes", "tunic"]),
             stripe=pick(r, ["#1E2E5A", "#8A2E2E", "#2A2A2A"]), sleeves=pick(r, ["rolled", "long", "short"]), pants=pick(r, ["#3A3A30", "#5A4A3A", "#2E3A4A", "#6B5E4E"]),
             boots=pick(r, LEATHER), boot_kind=pick(r, ["boots", "boots", "sandals"]), sash=pick(r, ["#A02020", "#C8A040", "#20508A", "#2E6B3A"]),
             earring=r.random() < 0.6, tattoo=pick(r, ["#20305A", None]))
    if r.random() < 0.6:
        d.update(hat="bandana", hat_color=pick(r, ["#A02020", "#20508A", "#2A2A2A", "#C8A040"]), dots=r.random() < 0.4)
    if r.random() < 0.4:
        d["vest"] = pick(r, ["#3A2616", "#1E1E22", "#4A3020"])
    d["eyepatch"] = r.random() < 0.15
    d["scar"] = r.random() < 0.25
    return d


def pirate_captain(r):
    return dict(skin="#C48659", hair="#1A1412", eyes="#2A2A2A", style="long", beard="braided", shirt="#6B1E1E", shirt_kind="shirt", sleeves="long",
                pants="#2A2A30", boots="#120C08", boot_kind="boots", sash="#C8A040", hat="tricorn", hat_color="#1E1E22", trim="#C8A040", skull=True,
                vest="#1E1E22", vest_buttons=True, earring=True, eyepatch=True, cuffs=True, gloves="#3A2616")


def viking(r, i):
    d = face_traits(r, i)
    d["hair"] = pick(r, ["#D8B460", "#E6D29A", "#A63E22", "#C25A2A", "#B07A3A", "#6B4424", "#8C8C8C"])
    if d.get("beard") not in ("none",) or r.random() < 0.6:
        d["beard"] = pick(r, ["full", "braided", "long", "braided"]) if d["style"] not in ("long",) or r.random() < 0.5 else d["beard"]
    d["style"] = pick(r, ["braids", "long", "topknot", "ponytail", "bald", "crop"])
    d.update(shirt=pick(r, ["#6B5E4E", "#5A4A3A", "#3A4A5A", "#4A5A3A", "#7A3A28"]), shirt_kind="tunic", sleeves=pick(r, ["long", "short"]),
             pants=pick(r, ["#5A4A3A", "#4A4038", "#3A3428"]), boots=pick(r, ["#4A3020", "#3A2616"]), boot_kind="wraps", belt="#3A2616", buckle="#C8A040",
             fur=pick(r, ["#5A4A3A", "#8A7A6A", "#3A3028", "#D8D0C0"]), bracers=pick(r, ["#4A3020", "#8A8A90"]), wraps="#6B5E4E",
             warpaint=pick(r, [None, None, "#2A3A6A", "#6A1E1E"]), tattoo=pick(r, ["#20305A", None]))
    if r.random() < 0.45:
        d.update(hat="helm", hat_color=pick(r, ["#8A8A90", "#7A7A80"]), nasal=True)
    if r.random() < 0.35:
        d["mail"] = True
    d["scar"] = r.random() < 0.35
    return d


def jarl(r):
    return dict(skin="#E8BB98", hair="#D8B460", eyes="#3B5A8A", style="braids", beard="braided", scar=True, shirt="#6B1E1E", shirt_kind="tunic",
                sleeves="long", pants="#3A3428", boots="#3A2616", boot_kind="wraps", belt="#3A2616", buckle="#E8C040", fur="#E8E0D0",
                cloak="#6B1E1E", bracers="#C8A040", hat="helm", hat_color="#9A9AA0", nasal=True, warpaint="#2A3A6A", wraps="#8A7A6A", mail=True)


ROLES = [
    # carpeta, nombre de archivo, cuántos, semilla, generador
    ("recruit", "recruit", 14, 11, recruit),
    ("soldier", "soldier", 12, 12, soldier),
    ("city_guard", "city_guard", 10, 13, city_guard),
    ("worker", "worker", 12, 14, worker),
    ("trader", "trader", 10, 15, trader),
    ("bandit", "bandit", 12, 16, bandit),
    ("pirate", "pirate", 12, 17, pirate),
    ("viking", "viking", 12, 18, viking),
]
CAPTAINS = {"bandit": bandit_captain, "pirate": pirate_captain, "viking": jarl}


def front(skin):
    """Vista de frente (8 de ancho, 32 de alto, con la capa de encima) para la hoja de muestras."""
    v = Image.new("RGBA", (16, 32), (0, 0, 0, 0))

    def put(src, dst, size):
        v.alpha_composite(skin.crop((src[0], src[1], src[0] + size[0], src[1] + size[1])), dst)
    put((8, 8), (4, 0), (8, 8))
    put((40, 8), (4, 0), (8, 8))
    put((20, 20), (4, 8), (8, 12))
    put((20, 36), (4, 8), (8, 12))
    put((44, 20), (0, 8), (4, 12))
    put((44, 36), (0, 8), (4, 12))
    put((36, 52), (12, 8), (4, 12))
    put((52, 52), (12, 8), (4, 12))
    put((4, 20), (4, 20), (4, 12))
    put((4, 36), (4, 20), (4, 12))
    put((20, 52), (8, 20), (4, 12))
    put((4, 52), (8, 20), (4, 12))
    return v


def main():
    sheet = []
    for folder, name, count, seed, make in ROLES:
        d = os.path.join(OUT, folder)
        os.makedirs(d, exist_ok=True)
        # quitar las de antes (por si había más)
        for f in os.listdir(d):
            if f.startswith(name + "_") and f.endswith(".png"):
                os.remove(os.path.join(d, f))
        imgs = people(name, count, seed, make)
        for i, im in enumerate(imgs):
            im.save(os.path.join(d, "%s_%d.png" % (name, i)))
        if folder in CAPTAINS:
            cap = person(seed * 100 + 99, **CAPTAINS[folder](random.Random(seed)))
            cap.save(os.path.join(d, "captain.png"))
            imgs = imgs + [cap]
        sheet.append((folder, imgs))
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        cols = max(len(i) for _, i in sheet)
        s = 6
        W = Image.new("RGBA", (cols * 18 * s, len(sheet) * 34 * s), (60, 64, 70, 255))
        for row, (_, imgs) in enumerate(sheet):
            for col, im in enumerate(imgs):
                W.alpha_composite(front(im).resize((16 * s, 32 * s), Image.NEAREST), (col * 18 * s, row * 34 * s))
        W.save(os.path.join(PREVIEW, "skins_sheet.png"))
    print("pieles:", ", ".join("%s %d" % (f, len(i)) for f, i in sheet))


if __name__ == "__main__":
    main()
