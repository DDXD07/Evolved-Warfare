"""El Catálogo de los mercaderes: las tapas del libro 3D (una por oficio, con su color, su emblema y su título), el papel
de las páginas, las manos con guantes que lo sujetan, el icono, los modelos, el estado del bloque, la receta y los textos.

Las tapas van en una textura de 256x256 (se pinta a 128x128 y se dobla) con este reparto, en la escala de 128 (la usa
CatalogBookRenderer.java, que divide entre 128):
  (0,0)   48x36  tapa de fuera (título, emblema y marco dorado)
  (48,0)  48x36  tapa por dentro (papel de guardas jaspeado)
  (0,40)  48x36  la primera página (vista con el libro abierto)
  (48,40) 48x8   canto de las hojas
  (48,48) 16x16  cuero liso (cantos de las tapas y lomo)
  (0,80)  48x36  contratapa (cuero con marco)

Uso: python3 tools/ew_catalog.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import math
import os
import random
import sys

from PIL import Image

args = sys.argv[1:]
PREVIEW = None
if "--preview" in args:
    i = args.index("--preview")
    PREVIEW = args[i + 1]
    del args[i:i + 2]
ROOT = args[0] if args else "."
ASSETS = os.path.join(ROOT, "src/main/resources/assets/evolvedwarfare")
DATA = os.path.join(ROOT, "src/main/resources/data/evolvedwarfare")

# oficio -> (color del cuero, título de la tapa)
TRADES = collections.OrderedDict([
    ("grocer", ((78, 104, 46), "VIVERES")),
    ("armorer", ((110, 30, 30), "ARMERIA")),
    ("mason", ((72, 82, 94), "CANTERIA")),
    ("jeweler", ((74, 42, 94), "JOYERIA")),
    ("tailor", ((35, 52, 94), "SASTRERIA")),
    ("spice_merchant", ((138, 74, 28), "ESPECIAS")),
    ("furrier", ((91, 58, 34), "PELETERIA")),
])
GOLD = (214, 176, 84)
GOLD_D = (150, 116, 44)

# letras de 5x7 (mayúsculas que hacen falta)
FONT = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "C": ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "G": ["01111", "10000", "10000", "10011", "10001", "10001", "01111"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "J": ["00111", "00010", "00010", "00010", "00010", "10010", "01100"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
}


def text_width(text, scale=1):
    return (len(text) * 6 - 1) * scale


def draw_text(im, text, x, y, color, scale=1, shadow=None):
    for i, ch in enumerate(text):
        glyph = FONT.get(ch)
        if glyph is None:
            continue
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit == "1":
                    for sy in range(scale):
                        for sx in range(scale):
                            px, py = x + (i * 6 + gx) * scale + sx, y + gy * scale + sy
                            if shadow and 0 <= px + 1 < im.width and 0 <= py + 1 < im.height:
                                im.putpixel((px + 1, py + 1), shadow)
                            if 0 <= px < im.width and 0 <= py < im.height:
                                im.putpixel((px, py), color)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + ((c[3],) if len(c) > 3 else (255,))


def leather(im, x0, y0, w, h, base, rnd):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            n = rnd.randint(-10, 10) + (6 if (x * 7 + y * 3) % 11 == 0 else 0)
            im.putpixel((x, y), shade(base, 1.0 + n / 120.0))


def frame(im, x0, y0, w, h, inset, color, dark):
    for x in range(x0 + inset, x0 + w - inset):
        im.putpixel((x, y0 + inset), color)
        im.putpixel((x, y0 + h - 1 - inset), dark)
    for y in range(y0 + inset, y0 + h - inset):
        im.putpixel((x0 + inset, y), color)
        im.putpixel((x0 + w - 1 - inset, y), dark)


# emblemas de 16x16 (oro sobre el cuero)
def emblem(trade):
    g = [[0] * 16 for _ in range(16)]

    def px(x, y, v=1):
        if 0 <= x < 16 and 0 <= y < 16:
            g[y][x] = v

    if trade == "grocer":  # gavilla de trigo
        for i in range(10):
            px(7, 5 + i)
            px(8, 5 + i)
        for k in range(5):
            px(6 - k // 2, 4 - k)
            px(9 + k // 2, 4 - k)
            px(7, 4 - k)
            px(8, 4 - k)
        for x in range(5, 11):
            px(x, 10)
    elif trade == "armorer":  # espadas cruzadas
        for i in range(12):
            px(2 + i, 2 + i)
            px(13 - i, 2 + i)
        for k in (-1, 1):
            px(4 + k, 11 - k)
            px(11 + k, 11 + k)
        px(1, 14)
        px(14, 14)
    elif trade == "mason":  # martillo y escoplo
        for x in range(3, 11):
            px(x, 3)
            px(x, 4)
        for y in range(5, 14):
            px(6, y)
            px(7, y)
        for i in range(8):
            px(13 - i // 2, 6 + i)
    elif trade == "jeweler":  # gema
        rows = ["0000111111110000", "0001100000011000", "0011000000001100", "0111111111111110", "0011000110001100",
                "0001100110011000", "0000110110110000", "0000011111100000", "0000001111000000", "0000000110000000"]
        for y, row in enumerate(rows):
            for x, b in enumerate(row):
                if b == "1":
                    px(x, y + 3)
    elif trade == "tailor":  # tijeras
        for i in range(9):
            px(3 + i, 2 + i)
            px(12 - i, 2 + i)
        for a in range(0, 360, 30):
            px(int(4 + 2.2 * math.cos(math.radians(a))), int(12 + 2.2 * math.sin(math.radians(a))))
            px(int(11 + 2.2 * math.cos(math.radians(a))), int(12 + 2.2 * math.sin(math.radians(a))))
    elif trade == "spice_merchant":  # tarro de especias
        for y in range(5, 15):
            for x in range(4, 12):
                if x in (4, 11) or y in (5, 14):
                    px(x, y)
        for x in range(5, 11):
            px(x, 3)
            px(x, 4)
        for x in range(6, 10):
            px(x, 9)
            px(x, 10)
    else:  # zarpa de peletero
        for x in range(5, 11):
            for y in range(8, 13):
                if (x - 7.5) ** 2 / 9 + (y - 10.5) ** 2 / 6 <= 1:
                    px(x, y)
        for cx, cy in ((3, 6), (6, 3), (10, 3), (13, 6)):
            for dx in (0, 1):
                for dy in (0, 1):
                    px(cx + dx, cy + dy)
    return g


def cover(trade, base, title):
    rnd = random.Random(hash(trade) & 0xFFFF)
    im = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    # tapa de fuera
    leather(im, 0, 0, 48, 36, base, rnd)
    frame(im, 0, 0, 48, 36, 1, GOLD, GOLD_D)
    frame(im, 0, 0, 48, 36, 3, GOLD_D, GOLD_D)
    # tapa por dentro: papel de guardas jaspeado
    for y in range(36):
        for x in range(48, 96):
            v = math.sin(x * 0.4 + math.sin(y * 0.3) * 3) + math.sin(y * 0.5 + x * 0.1)
            c = shade(base, 1.25 + v * 0.12)
            im.putpixel((x, y), c)
    # primera página: papel con un titular y renglones
    for y in range(40, 76):
        for x in range(48):
            n = rnd.randint(-8, 8)
            im.putpixel((x, y), (226 + n, 222 + n, 210 + n, 255))
    ink = (40, 38, 36, 255)
    for x in range(6, 42):
        im.putpixel((x, 44), ink)
        im.putpixel((x, 52), ink)
    draw_text(im, "CATALOGO", (48 - text_width("CATALOGO")) // 2, 45, ink)
    for row in range(55, 74, 3):
        for x in range(5, 43):
            if rnd.random() < 0.75:
                im.putpixel((x, row), (110, 106, 100, 255))
    # canto de las hojas
    for y in range(40, 48):
        for x in range(48, 96):
            c = 214 if (y % 2 == 0) else 196
            im.putpixel((x, y), (c, c - 4, c - 14, 255))
    # cuero liso
    leather(im, 48, 48, 16, 16, base, rnd)
    # contratapa
    leather(im, 0, 80, 48, 36, shade(base, 0.92), rnd)
    frame(im, 0, 80, 48, 36, 2, GOLD_D, GOLD_D)
    # al doble de resolución, y encima, ya a esa resolución, «CATALOGO», el emblema (al doble) y el oficio
    im = im.resize((256, 256), Image.NEAREST)
    shadow = shade(base, 0.55)
    draw_text(im, "CATALOGO", (96 - text_width("CATALOGO")) // 2, 9, GOLD, shadow=shadow)
    for x in range(14, 82):
        im.putpixel((x, 18), GOLD_D)
    g = emblem(trade)
    for y in range(16):
        for x in range(16):
            if g[y][x]:
                for sy in range(2):
                    for sx in range(2):
                        im.putpixel((32 + x * 2 + sx, 21 + y * 2 + sy), GOLD if (x + y) % 5 else GOLD_D)
    for x in range(14, 82):
        im.putpixel((x, 55), GOLD_D)
    draw_text(im, title, (96 - text_width(title)) // 2, 58, GOLD, shadow=shadow)
    return im


# ------------------------------------------------------------------ interfaz

def paper(seed=5, w=256, h=256):
    """Papel de periódico viejo: gris claro con fibras, manchas y bordes un poco más oscuros (se ve en blanco y negro)."""
    rnd = random.Random(seed)
    im = Image.new("RGBA", (w, h))
    base = 222
    stains = [(rnd.randrange(w), rnd.randrange(h), rnd.randint(10, 40), rnd.uniform(4, 12)) for _ in range(10)]
    for y in range(h):
        for x in range(w):
            v = base + rnd.randint(-6, 6)
            # fibras horizontales
            if rnd.random() < 0.02:
                v -= 10
            for sx, sy, r, d in stains:
                dd = math.hypot(x - sx, y - sy)
                if dd < r:
                    v -= d * (1 - dd / r)
            edge = min(x, y, w - 1 - x, h - 1 - y)
            if edge < 18:
                v -= (18 - edge) * 1.1
            v = int(max(150, min(240, v)))
            im.putpixel((x, y), (v, v, v - 4, 255))
    return im


def hand(right):
    """Una mano con guante de cuero sin dedos sujetando el borde de abajo del libro (pulgar encima de la página)."""
    w, h = 64, 64
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    glove = (96, 62, 40, 255)
    glove_d = (66, 42, 28, 255)
    stitch = (150, 110, 70, 255)
    skin = (214, 160, 124, 255)
    skin_d = (176, 124, 92, 255)
    cuff = (168, 150, 120, 255)
    # palma y dorso (por debajo del libro: se ve el canto de la mano)
    for y in range(20, 64):
        for x in range(10, 54):
            dx = (x - 32) / 22.0
            dy = (y - 46) / 22.0
            if dx * dx + dy * dy <= 1.0:
                im.putpixel((x, y), glove if (x + y) % 7 else glove_d)
    # puño de la camisa abajo
    for y in range(54, 64):
        for x in range(12, 52):
            im.putpixel((x, y), cuff if (x // 3) % 2 else shade(cuff, 0.9))
    # costura
    for x in range(16, 48, 2):
        im.putpixel((x, 30), stitch)
    # pulgar: sale hacia el centro de la página y se apoya encima
    for t in range(0, 22):
        cx = 30 - t * 0.9
        cy = 22 - t * 0.55
        for oy in range(-4, 5):
            for ox in range(-4, 5):
                if ox * ox + oy * oy <= 16:
                    x, y = int(cx + ox), int(cy + oy)
                    if 0 <= x < w and 0 <= y < h:
                        c = glove if t < 10 else (skin if (ox + oy) < 3 else skin_d)
                        im.putpixel((x, y), c)
    # uña
    for oy in range(-2, 1):
        for ox in range(-2, 1):
            x, y = int(30 - 21 * 0.9 + ox), int(22 - 21 * 0.55 + oy)
            if 0 <= x < w and 0 <= y < h:
                im.putpixel((x, y), (236, 204, 184, 255))
    if right:
        im = im.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    return im


def icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base = (110, 72, 40, 255)
    for y in range(2, 14):
        for x in range(2, 14):
            im.putpixel((x, y), shade(base, 1.0 + ((x * 3 + y) % 5 - 2) / 30))
    for y in range(3, 13):
        im.putpixel((12, y), (226, 220, 204, 255))
        im.putpixel((13, y), (200, 194, 180, 255))
    for x in range(4, 11):
        im.putpixel((x, 4), GOLD + (255,))
        im.putpixel((x, 11), GOLD + (255,))
    for y in range(4, 12):
        im.putpixel((4, y), GOLD + (255,))
        im.putpixel((10, y), GOLD + (255,))
    for x, y in ((6, 6), (7, 7), (8, 8), (6, 8), (8, 6)):
        im.putpixel((x, y), GOLD + (255,))
    for y in range(2, 14):
        im.putpixel((2, y), shade(base, 0.6))
    return im


TEXTS = {
    "block.evolvedwarfare.trader_catalog": ("Catálogo del mercader", "Merchant's Catalog"),
    "block.evolvedwarfare.trader_catalog.tooltip": ("Ponlo en el mostrador: clic derecho para comprar y vender",
                                                    "Put it on the counter: right-click to buy and sell"),
    "message.evolvedwarfare.catalog.nobody": ("No hay nadie atendiendo esta tienda.", "Nobody is minding this shop."),
    "message.evolvedwarfare.catalog.closed": ("La tienda está cerrada: %s duerme. Vuelve cuando amanezca.",
                                              "The shop is closed: %s is asleep. Come back at dawn."),
    "gui.evolvedwarfare.catalog.slogan": ("LA MEJOR CASA DE VENTA POR CATÁLOGO DE LA TIERRA", "THE GREATEST MAIL ORDER HOUSE ON EARTH"),
    "gui.evolvedwarfare.catalog.house.grocer": ("Ultramarinos Del Valle e Hijos", "Del Valle & Sons Provisions"),
    "gui.evolvedwarfare.catalog.house.armorer": ("Armería Hierro Viejo", "Old Iron Armory"),
    "gui.evolvedwarfare.catalog.house.mason": ("Canteras y Labras Montenegro", "Montenegro Quarry & Stonework"),
    "gui.evolvedwarfare.catalog.house.jeweler": ("Joyería La Estrella del Norte", "North Star Jewelers"),
    "gui.evolvedwarfare.catalog.house.tailor": ("Sastrería Aguja de Plata", "Silver Needle Tailors"),
    "gui.evolvedwarfare.catalog.house.spice_merchant": ("Especias y Ultramar Al-Qasr", "Al-Qasr Spices & Overseas Goods"),
    "gui.evolvedwarfare.catalog.house.furrier": ("Peletería Lobo Blanco", "White Wolf Furriers"),
    "gui.evolvedwarfare.catalog.dept.grocer": ("DEPARTAMENTO DE VÍVERES", "PROVISIONS DEPARTMENT"),
    "gui.evolvedwarfare.catalog.dept.armorer": ("DEPARTAMENTO DE ARMAS Y ARMADURAS", "ARMS & ARMOR DEPARTMENT"),
    "gui.evolvedwarfare.catalog.dept.mason": ("DEPARTAMENTO DE PIEDRA Y CONSTRUCCIÓN", "STONE & BUILDING DEPARTMENT"),
    "gui.evolvedwarfare.catalog.dept.jeweler": ("DEPARTAMENTO DE JOYAS Y RAREZAS", "JEWELS & CURIOS DEPARTMENT"),
    "gui.evolvedwarfare.catalog.dept.tailor": ("DEPARTAMENTO DE TELAS Y ARREOS", "CLOTH & TACK DEPARTMENT"),
    "gui.evolvedwarfare.catalog.dept.spice_merchant": ("DEPARTAMENTO DE ESPECIAS Y ULTRAMAR", "SPICES & OVERSEAS DEPARTMENT"),
    "gui.evolvedwarfare.catalog.dept.furrier": ("DEPARTAMENTO DE PIELES Y ABRIGOS", "FURS & COATS DEPARTMENT"),
    "gui.evolvedwarfare.catalog.ask": ("¿QUÉ DESEA EL CLIENTE?", "WHAT WILL IT BE?"),
    "gui.evolvedwarfare.catalog.buy": ("COMPRA", "BUY"),
    "gui.evolvedwarfare.catalog.sell": ("VENTA", "SELL"),
    "gui.evolvedwarfare.catalog.buy.desc": ("Lo que vende esta casa: %s artículos", "What this house sells: %s items"),
    "gui.evolvedwarfare.catalog.sell.desc": ("Lo que esta casa le compra: %s artículos", "What this house buys from you: %s items"),
    "gui.evolvedwarfare.catalog.level": ("Ciudad de nivel %s · %s", "Level %s city · %s"),
    "gui.evolvedwarfare.catalog.markup": ("precios +%s %%", "prices +%s%%"),
    "gui.evolvedwarfare.catalog.no_markup": ("precios de origen", "base prices"),
    "gui.evolvedwarfare.catalog.coins": ("Sus monedas: %s", "Your coins: %s"),
    "gui.evolvedwarfare.catalog.lot": ("lote de %s", "lot of %s"),
    "gui.evolvedwarfare.catalog.stock": ("Quedan %s lotes hoy", "%s lots left today"),
    "gui.evolvedwarfare.catalog.sold_out": ("AGOTADO", "SOLD OUT"),
    "gui.evolvedwarfare.catalog.wants": ("Compra %s lotes más hoy", "Buys %s more lots today"),
    "gui.evolvedwarfare.catalog.have": ("Usted tiene %s", "You have %s"),
    "gui.evolvedwarfare.catalog.pays": ("LE PAGAMOS", "WE PAY"),
    "gui.evolvedwarfare.catalog.click_buy": ("Clic: comprar un lote · Mayús + clic: un montón", "Click: buy a lot · Shift + click: a stack"),
    "gui.evolvedwarfare.catalog.click_sell": ("Clic: vender un lote · Mayús + clic: todo lo que pueda", "Click: sell a lot · Shift + click: all it takes"),
    "gui.evolvedwarfare.catalog.index": ("« ÍNDICE", "« INDEX"),
    "gui.evolvedwarfare.catalog.prev": ("« ANTERIOR", "« PREVIOUS"),
    "gui.evolvedwarfare.catalog.next": ("SIGUIENTE »", "NEXT »"),
    "gui.evolvedwarfare.catalog.empty": ("Nada por aquí.", "Nothing here."),
    "gui.evolvedwarfare.catalog.keys": ("← → pasar página · Retroceso: índice · Esc: cerrar", "← → turn page · Backspace: index · Esc: close"),
    "gui.evolvedwarfare.catalog.blurb.0": ("De la mejor calidad. Garantizado por la casa.", "Of the finest quality. Guaranteed by the house."),
    "gui.evolvedwarfare.catalog.blurb.1": ("Recomendado por rancheros y tramperos de toda la frontera.", "Recommended by ranchers and trappers all over the frontier."),
    "gui.evolvedwarfare.catalog.blurb.2": ("¡Una ganga sin igual! Pregunte por él en cualquier ciudad.", "A bargain without equal! Ask for it in any town."),
    "gui.evolvedwarfare.catalog.blurb.3": ("Hecho para durar. Ninguna casa del país lo ofrece mejor.", "Made to last. No house in the land offers it better."),
    "gui.evolvedwarfare.catalog.blurb.4": ("Imprescindible en todo hogar decente y en toda caravana.", "A must in every decent home and every caravan."),
    "gui.evolvedwarfare.catalog.blurb.5": ("Se envía el mismo día. Satisfacción o le devolvemos su dinero.", "Shipped the same day. Satisfaction or your money back."),
    "gui.evolvedwarfare.catalog.blurb.sell": ("Esta casa paga al contado y sin regatear.", "This house pays cash, no haggling."),
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    folder = os.path.join(ASSETS, "textures/entity/catalog")
    os.makedirs(folder, exist_ok=True)
    covers = []
    for trade, (base, title) in TRADES.items():
        im = cover(trade, base + (255,), title)
        im.save(os.path.join(folder, "cover_%s.png" % trade))
        covers.append(im)
    gui = os.path.join(ASSETS, "textures/gui/catalog")
    os.makedirs(gui, exist_ok=True)
    paper().save(os.path.join(gui, "paper.png"))
    hand(False).save(os.path.join(gui, "hand_left.png"))
    hand(True).save(os.path.join(gui, "hand_right.png"))
    icon().save(os.path.join(ASSETS, "textures/item/trader_catalog.png"))
    write_json(os.path.join(ASSETS, "models/item/trader_catalog.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "evolvedwarfare:item/trader_catalog"}})
    write_json(os.path.join(ASSETS, "models/block/trader_catalog.json"),
               {"textures": {"particle": "minecraft:block/spruce_planks"}, "elements": []})
    variants = {}
    for facing in ("north", "south", "east", "west"):
        for t in range(len(TRADES)):
            variants["facing=%s,trade=%d" % (facing, t)] = {"model": "evolvedwarfare:block/trader_catalog"}
    write_json(os.path.join(ASSETS, "blockstates/trader_catalog.json"), {"variants": variants})
    write_json(os.path.join(DATA, "recipe/trader_catalog.json"),
               {"type": "minecraft:crafting_shapeless", "category": "misc",
                "ingredients": [{"item": "minecraft:book"}, {"item": "minecraft:ink_sac"}, {"item": "minecraft:paper"},
                                {"item": "evolvedwarfare:glory_coin"}],
                "result": {"id": "evolvedwarfare:trader_catalog", "count": 1}})
    write_json(os.path.join(DATA, "loot_table/blocks/trader_catalog.json"),
               {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
                                                      "entries": [{"type": "minecraft:item", "name": "evolvedwarfare:trader_catalog"}],
                                                      "conditions": [{"condition": "minecraft:survives_explosion"}]}],
                "random_sequence": "evolvedwarfare:blocks/trader_catalog"})
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        sheet = Image.new("RGBA", (256 * 4, 256 * 2), (40, 40, 40, 255))
        for i, im in enumerate(covers):
            sheet.alpha_composite(im, ((i % 4) * 256, (i // 4) * 256))
        sheet.save(os.path.join(PREVIEW, "catalog_covers.png"))
        hands = Image.new("RGBA", (64 * 2 + 8, 64), (200, 200, 200, 255))
        hands.alpha_composite(hand(False), (0, 0))
        hands.alpha_composite(hand(True), (72, 0))
        hands.resize((hands.width * 4, hands.height * 4), Image.NEAREST).save(os.path.join(PREVIEW, "catalog_hands.png"))
    print("catálogo: %d tapas, papel, manos, icono, modelos, receta y textos" % len(covers))


if __name__ == "__main__":
    main()
