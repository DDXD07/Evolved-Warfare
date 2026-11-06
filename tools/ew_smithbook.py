"""El Manual del Herrero (fase 30): su icono, su modelo, el grabado de la forja de la primera página (el yunque con el
lingote al rojo, hecho con los modelos 3D de ew_gear3d) y los textos del libro, en español y en inglés.

El libro lo dibuja SmithingManualScreen (Java): seis dobles páginas con ilustraciones (en marcha: el martillo que golpea, la
barra del minijuego, el humo del lingote, el nivel que sube) y los textos de aquí.

Uso: python3 tools/ew_smithbook.py [raíz del proyecto] [--preview carpeta]
"""
import collections
import glob
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from ew_model3d import Model, Palette, render  # noqa: E402

args = sys.argv[1:]
PREVIEW = None
if "--preview" in args:
    i = args.index("--preview")
    PREVIEW = args[i + 1]
    del args[i:i + 2]
ROOT = args[0] if args else os.path.join(os.path.dirname(__file__), "..")
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
K = "manual.evolvedwarfare.smithing."

ES = collections.OrderedDict([
    ("item.evolvedwarfare.smithing_manual", "Manual del Herrero"),
    ("item.evolvedwarfare.smithing_manual.desc", "Cómo forjar armas y herramientas, paso a paso. Clic derecho para leerlo."),
    (K + "title", "Manual del Herrero"),
    (K + "subtitle", "El arte de forjar armas y herramientas"),
    (K + "intro.step1", "Funde lingotes al rojo en un crisol sobre el fuego."),
    (K + "intro.step2", "Talla un molde de arcilla y vierte el metal."),
    (K + "intro.step3", "Afila la pieza en el yunque y móntala."),
    (K + "intro.a", "Las herramientas y armas de hierro, oro y diamante ya no se hacen en la mesa: se funden y se forjan. Su calidad sale de tus manos."),
    (K + "engraving", "Lingote al rojo sobre el yunque"),
    (K + "intro.b", "La madera, la piedra, el hacha de sílex, la netherita y las armaduras siguen como siempre."),
    (K + "heat.title", "1. Calentar el lingote"),
    (K + "heat.a", "Un lingote de hierro u oro sale al rojo en el alto horno (5 s) o en el horno (10 s)."),
    (K + "heat.b", "Luego, al crisol: cada pieza gasta de 1 a 4 lingotes."),
    (K + "cool.title", "¡Que no se enfríe!"),
    (K + "cool.a", "El lingote al rojo humea y al minuto se enfría: vuelve a ser un lingote normal."),
    (K + "cool.b", "Fórjalo antes. Si se enfría, vuelve a meterlo en el horno."),
    (K + "hammer.title", "2. El martillo"),
    (K + "hammer.a", "2 lingotes de hierro y 2 palos en la mesa. Basta con llevarlo encima; se gasta un poco con cada pieza."),
    (K + "anvil.title", "En el yunque"),
    (K + "anvil.click", "Clic derecho"),
    (K + "anvil.a", "Con la pieza en bruto en la mano y el martillo encima, clic derecho a un yunque."),
    (K + "anvil.b", "Si es de hierro y llevas diamantes, puedes darle filo de diamante: uno por lingote."),
    (K + "game.title", "3. El minijuego"),
    (K + "game.perfect", "¡Perfecto!"),
    (K + "game.good", "¡Bien!"),
    (K + "game.wait", "Espera..."),
    (K + "game.legend.perfect", "perfecto"),
    (K + "game.legend.good", "bien"),
    (K + "game.legend.poor", "flojo"),
    (K + "game.a", "El martillo va y viene por la barra. Golpea (clic o espacio) cuando pase por lo naranja; en el centro amarillo, perfecto."),
    (K + "strikes.title", "Cada golpe cuenta"),
    (K + "strikes.a", "Cada pieza lleva de 4 a 9 golpes. Tras cada uno, la zona cambia de sitio y el martillo va más rápido."),
    (K + "strikes.b", "El lingote se enfría (la barra roja): si se enfría del todo, los golpes que faltan son fallos."),
    (K + "strikes.c", "El oro es blando: más fácil de forjar."),
    (K + "strikes.d", "El filo de diamante es más difícil."),
    (K + "parts.title", "Las piezas"),
    (K + "parts.cost", "%s lingotes al rojo"),
    (K + "parts.handle.none", "Mango: %s palos"),
    (K + "parts.handle.grip", "Mango: %s palo y empuñadura (cuero o cuerda)"),
    (K + "parts.handle.chain", "Mango: %s palo y una cadena"),
    (K + "parts.strikes", "%s golpes"),
    (K + "parts.a", "Lo que cuesta cada una. Pasa el ratón por encima para ver su mango."),
    (K + "assembly.title", "4. Montar la pieza"),
    (K + "assembly.a", "En la mesa, la pieza y su mango, en cualquier sitio: palos y, las hojas, una empuñadura de cuero o cuerda; el mangual, una cadena."),
    (K + "assembly.b", "Las armas medievales no se hacen de oro."),
    (K + "quality.title", "5. La calidad"),
    (K + "quality.durability", "Durab."),
    (K + "quality.damage", "Daño"),
    (K + "quality.speed", "Picar"),
    (K + "quality.a", "Sale del molde, del vertido y del afilado. La herramienta la conserva, también al mejorarla a netherita."),
    (K + "level.title", "Nivel de herrería"),
    (K + "level.label", "Nivel %s"),
    (K + "level.a", "Sube forjando, del 0 al 10, y a más nivel, más grande la zona buena."),
    (K + "level.b", "Practica con piezas baratas (pala, daga, lanza) antes de las grandes."),
    (K + "level.c", "Este manual lo venden los armeros de las ciudades por 20 Glory Coins."),
    (K + "crucible.title", "El crisol"),
    (K + "crucible.a", "5 ladrillos. Ponlo sobre una hoguera, fuego, lava o magma y échale lingotes al rojo (hasta 4): se funden."),
    (K + "crucible.b", "Con la mano vacía lo coges, con el metal dentro. Fuera del fuego, a los 30 s se enfría."),
    (K + "mold.title", "El molde"),
    (K + "mold.a", "Un frasco de agua, una bola de arcilla y un palo. Ponlo en el suelo: se abre la rueda de piezas."),
    (K + "carve.title", "Tallar"),
    (K + "carve.a", "Elige la pieza y talla su silueta: clic izquierdo rasca, clic derecho tapa."),
    (K + "carve.b", "La arcilla se seca: date prisa. Lo que rasques de más resta."),
    (K + "pour.title", "Verter"),
    (K + "pour.a", "Con el crisol en la mano, mantén clic derecho sobre el molde. El chorro va a más: suelta en la raya."),
    (K + "pour.b", "Lo verde es perfecto; de menos, la pieza sale incompleta; de más, se derrama."),
    (K + "crack.title", "Romper el molde"),
    (K + "crack.a", "El metal cuaja y se enfría (no lo toques: quema). Luego, tres golpes con la mano."),
    (K + "crack.b", "Sale la pieza en bruto: aún hay que afilarla."),
])

EN = collections.OrderedDict([
    ("item.evolvedwarfare.smithing_manual", "Smith's Manual"),
    ("item.evolvedwarfare.smithing_manual.desc", "How to forge weapons and tools, step by step. Right-click to read."),
    (K + "title", "Smith's Manual"),
    (K + "subtitle", "The art of forging weapons and tools"),
    (K + "intro.step1", "Melt red-hot ingots in a crucible over a fire."),
    (K + "intro.step2", "Carve a clay mold and pour the metal."),
    (K + "intro.step3", "Sharpen the part on the anvil and fit it."),
    (K + "intro.a", "Iron, gold and diamond tools and weapons are no longer crafted at the table: they are cast and forged. Your hands set their quality."),
    (K + "engraving", "Red-hot ingot on the anvil"),
    (K + "intro.b", "Wood, stone, the flint axe, netherite and armor work as always."),
    (K + "heat.title", "1. Heat the ingot"),
    (K + "heat.a", "An iron or gold ingot comes out red-hot from the blast furnace (5 s) or the furnace (10 s)."),
    (K + "heat.b", "Then into the crucible: each part takes 1 to 4 ingots."),
    (K + "cool.title", "Don't let it cool!"),
    (K + "cool.a", "The red-hot ingot smokes and cools after a minute: it turns back into a normal ingot."),
    (K + "cool.b", "Forge it before then. If it cools, put it back in the furnace."),
    (K + "hammer.title", "2. The hammer"),
    (K + "hammer.a", "2 iron ingots and 2 sticks at the table. Just carry it; it wears a little with each part."),
    (K + "anvil.title", "At the anvil"),
    (K + "anvil.click", "Right-click"),
    (K + "anvil.a", "With the rough part in hand and the hammer on you, right-click an anvil."),
    (K + "anvil.b", "If it is iron and you carry diamonds, you can give it a diamond edge: one per ingot."),
    (K + "game.title", "3. The minigame"),
    (K + "game.perfect", "Perfect!"),
    (K + "game.good", "Good!"),
    (K + "game.wait", "Wait..."),
    (K + "game.legend.perfect", "perfect"),
    (K + "game.legend.good", "good"),
    (K + "game.legend.poor", "poor"),
    (K + "game.a", "The hammer slides back and forth. Strike (click or space) when it is over the orange; in the yellow center, perfect."),
    (K + "strikes.title", "Every strike counts"),
    (K + "strikes.a", "Each part takes 4 to 9 strikes. After each one the zone moves and the hammer speeds up."),
    (K + "strikes.b", "The ingot cools (the red bar): if it cools down, the strikes left are misses."),
    (K + "strikes.c", "Gold is soft: easier to forge."),
    (K + "strikes.d", "A diamond edge is harder."),
    (K + "parts.title", "The parts"),
    (K + "parts.cost", "%s red-hot ingots"),
    (K + "parts.handle.none", "Handle: %s sticks"),
    (K + "parts.handle.grip", "Handle: %s stick and a grip (leather or string)"),
    (K + "parts.handle.chain", "Handle: %s stick and a chain"),
    (K + "parts.strikes", "%s strikes"),
    (K + "parts.a", "What each one costs. Hover over it to see its handle."),
    (K + "assembly.title", "4. Fit the part"),
    (K + "assembly.a", "At the table, the part and its handle, anywhere: sticks and, for blades, a leather or string grip; for the flail, a chain."),
    (K + "assembly.b", "Medieval weapons are not made of gold."),
    (K + "quality.title", "5. Quality"),
    (K + "quality.durability", "Durab."),
    (K + "quality.damage", "Damage"),
    (K + "quality.speed", "Mining"),
    (K + "quality.a", "It comes from the mold, the pour and the sharpening. The tool keeps it, also when upgraded to netherite."),
    (K + "level.title", "Smithing level"),
    (K + "level.label", "Level %s"),
    (K + "level.a", "It rises as you forge, from 0 to 10; the higher, the bigger the good zone."),
    (K + "level.b", "Practice with cheap parts (shovel, dagger, spear) before the big ones."),
    (K + "level.c", "City armorers sell this manual for 20 Glory Coins."),
    (K + "crucible.title", "The crucible"),
    (K + "crucible.a", "5 bricks. Put it over a campfire, fire, lava or magma and add red-hot ingots (up to 4): they melt."),
    (K + "crucible.b", "Pick it up with an empty hand, metal and all. Away from the fire it cools in 30 s."),
    (K + "mold.title", "The mold"),
    (K + "mold.a", "A water bottle, a clay ball and a stick. Place it on the ground: the wheel of parts opens."),
    (K + "carve.title", "Carving"),
    (K + "carve.a", "Pick the part and carve its outline: left click scrapes, right click fills."),
    (K + "carve.b", "The clay dries: hurry. Scraping too much counts against you."),
    (K + "pour.title", "Pouring"),
    (K + "pour.a", "Crucible in hand, hold right click on the mold. The stream speeds up: let go at the line."),
    (K + "pour.b", "Green is perfect; too little leaves it incomplete; too much spills."),
    (K + "crack.title", "Breaking the mold"),
    (K + "crack.a", "The metal sets and cools (don't touch it: it burns). Then, three hits by hand."),
    (K + "crack.b", "Out comes the rough part: it still needs sharpening."),
])


def write_json(path, obj, indent=1):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=indent)
        f.write("\n")


def icon():
    """Un libro de tapas de cuero con un yunque de hierro en la portada."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cover, dark, light = (120, 62, 34, 255), (78, 38, 20, 255), (150, 84, 46, 255)
    pages = (232, 222, 196, 255)
    for y in range(2, 15):
        for x in range(3, 14):
            im.putpixel((x, y), cover)
    for y in range(2, 15):
        im.putpixel((3, y), dark)
        im.putpixel((4, y), dark)
    for x in range(5, 14):
        im.putpixel((x, 2), light)
        im.putpixel((x, 14), dark)
    for y in range(3, 15):
        im.putpixel((14, y), pages)
    for x in range(5, 14):
        im.putpixel((x, 15), pages)
    im.putpixel((14, 15), (200, 190, 166, 255))
    # el yunque
    iron, iron_l = (150, 152, 158, 255), (200, 202, 208, 255)
    for x in range(6, 13):
        im.putpixel((x, 6), iron_l)
    for x in range(6, 12):
        im.putpixel((x, 7), iron)
    im.putpixel((12, 6), iron)
    for x in range(8, 11):
        im.putpixel((x, 8), iron)
        im.putpixel((x, 9), iron)
    for x in range(7, 12):
        im.putpixel((x, 10), iron)
    # la chispa (el lingote al rojo)
    im.putpixel((9, 5), (255, 160, 40, 255))
    im.putpixel((10, 5), (255, 220, 90, 255))
    for x in range(6, 13):
        im.putpixel((x, 12), (214, 170, 70, 255))
    return im


def anvil_model():
    p = Palette(321)
    p.add("iron", (78, 80, 88), noise=0.06)
    p.add("top", (128, 130, 138), noise=0.04)
    p.add("dark", (50, 52, 58), noise=0.05)
    p.add("stump", (110, 78, 46), pattern="rings", accent=(86, 60, 34))
    p.add("bark", (78, 56, 34), pattern="stripes", accent=(60, 42, 26))
    m = Model(p, "%s:gui/anvil_scene" % NS, 3)
    # el tocón de debajo
    m.box(3.5, -6, 4.0, 12.5, 2.0, 12.0, "bark")
    m.box(3.5, 2.0, 4.0, 12.5, 2.2, 12.0, "stump")
    # el yunque: pie, cuello, cuerpo y bigornia
    m.box(4.0, 2.2, 5.0, 12.0, 4.0, 11.0, "dark")
    m.box(5.0, 4.0, 6.0, 11.0, 5.2, 10.0, "iron")
    m.box(6.2, 5.2, 6.8, 9.8, 8.4, 9.2, "iron")
    m.box(3.0, 8.4, 6.0, 13.0, 11.2, 10.0, "iron")
    m.box(3.0, 11.2, 6.0, 13.0, 11.6, 10.0, "top")
    m.box(13.0, 9.2, 6.6, 15.4, 11.6, 9.4, "iron")
    m.box(15.4, 10.0, 7.2, 17.2, 11.6, 8.8, "iron")
    m.box(17.2, 10.8, 7.7, 18.6, 11.6, 8.3, "iron")
    m.box(1.6, 9.8, 6.8, 3.0, 11.6, 9.2, "iron")
    return m, p


def forge_picture():
    """El grabado de la primera página: el yunque sobre su tocón con un lingote al rojo encima."""
    import ew_gear3d
    anvil, pal = anvil_model()
    ingot = ew_gear3d.ingot(False).build(fit=5.2)
    ingot_json = ingot.json()
    ingot_img = ingot.palette.im
    # el lingote, encima del yunque (y = 11,6)
    ys = [e["from"][1] for e in ingot_json["elements"]]
    off = (0.5, 11.6 - min(ys), 0.0)
    imgs = {anvil.texture: pal.im, ingot_json["textures"]["t"]: ingot_img}
    key = (255, 0, 255)
    big = render([anvil.json(), ingot_json], imgs, size=224, scale=7.6, yaw=-28, pitch=22, bg=key, offsets=[(0, -0.5, 0), (off[0], off[1] - 0.5, off[2])])
    # recortar a 112x84 (de 224x168) y dejar el fondo como pergamino
    big = big.crop((0, 18, 224, 186)).resize((112, 84), Image.NEAREST)
    out = Image.new("RGBA", big.size, (0, 0, 0, 0))
    px = big.load()
    paper = (226, 210, 172)
    for y in range(big.height):
        for x in range(big.width):
            c = px[x, y]
            if abs(c[0] - 255) < 8 and c[1] < 8 and abs(c[2] - 255) < 8:
                # un brillo cálido detrás, como de la fragua
                dx, dy = (x - 56) / 56.0, (y - 46) / 44.0
                d = min(1.0, (dx * dx + dy * dy) ** 0.5)
                glow = (1.0 - d) ** 2
                col = tuple(int(paper[i] + (255 - paper[i]) * glow * 0.4 - (paper[i] * 0.05 if i == 2 else 0) * glow) for i in range(3))
                out.putpixel((x, y), col + (255,))
            else:
                out.putpixel((x, y), c[:3] + (255,))
    return out


def main():
    tex = os.path.join(ASSETS, "textures/item")
    icon().save(os.path.join(tex, "smithing_manual.png"))
    write_json(os.path.join(ASSETS, "models/item/smithing_manual.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/smithing_manual" % NS}})
    gui = os.path.join(ASSETS, "textures/gui/smithing_manual")
    os.makedirs(gui, exist_ok=True)
    pic = forge_picture()
    pic.save(os.path.join(gui, "forge.png"))
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        lang.update(EN if english else ES)
        write_json(path, lang, indent=2)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        pic.resize((pic.width * 4, pic.height * 4), Image.NEAREST).save(os.path.join(PREVIEW, "manual_forge.png"))
        icon().resize((128, 128), Image.NEAREST).save(os.path.join(PREVIEW, "manual_icon.png"))
    print("manual del herrero: %d textos" % len(ES))


if __name__ == "__main__":
    main()
