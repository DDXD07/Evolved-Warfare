"""Fase 10: el mosquete. Una sola fuente para la geometría 3D:

- textura de las piezas (assets/evolvedwarfare/textures/entity/musket.png)
- clase Java con el modelo (src/client/.../MusketModel.java)
- vistas previas (opcional): python3 tools/ew_musket.py <raíz del proyecto> [carpeta de vistas]

Coordenadas en píxeles del objeto (16 = 1 bloque), Y hacia arriba, el cañón a lo largo de Z con la boca hacia -Z y la
llave a la derecha (+X). El centro del objeto es (8, 8, 8).
"""
import math
import os
import random
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

# zonas de la textura (64x64): u, v, ancho, alto
ZONES = {
    "wood": (0, 0, 64, 24),
    "iron": (0, 24, 64, 24),
    "brass": (0, 48, 64, 8),
    "dark": (0, 56, 32, 8),
    "lead": (32, 56, 32, 8),
}
BASE = {
    "wood": (112, 64, 32),
    "iron": (132, 136, 142),
    "brass": (198, 156, 62),
    "dark": (30, 30, 33),
    "lead": (98, 101, 108),
}

# piezas: nombre -> (pivote, rotación en grados (x, y, z), [(material, x, y, z, ancho, alto, fondo)])
PARTS = {
    "body": ((0, 0, 0), (0, 0, 0), [
        ("iron", 7.2, 9.0, -8.0, 1.6, 1.6, 22.0),     # cañón
        ("dark", 7.6, 9.4, -8.1, 0.8, 0.8, 0.2),      # boca
        ("brass", 7.85, 10.55, -7.2, 0.3, 0.35, 0.6),  # punto de mira
        ("wood", 7.05, 7.95, -4.5, 1.9, 1.5, 17.0),   # caja
        ("brass", 7.0, 7.9, -5.0, 2.0, 1.7, 0.6),     # embocadura
        ("iron", 6.95, 7.85, -1.0, 2.1, 2.85, 0.35),  # abrazaderas
        ("iron", 6.95, 7.85, 4.0, 2.1, 2.85, 0.35),
        ("iron", 6.95, 7.85, 9.0, 2.1, 2.85, 0.35),
        ("wood", 6.9, 7.3, 12.5, 2.2, 2.6, 5.0),      # garganta (donde va la llave)
        ("iron", 7.4, 9.8, 14.0, 1.2, 0.4, 2.0),      # cola de la recámara
        ("iron", 9.1, 8.2, 12.6, 0.2, 1.4, 4.2),      # pletina de la llave
        ("brass", 9.0, 9.4, 13.2, 0.6, 0.3, 1.0),     # cazoleta
        ("brass", 6.75, 8.0, 13.0, 0.2, 1.0, 3.5),    # contrapletina
        ("brass", 7.8, 6.7, 13.8, 0.4, 0.3, 3.2),     # guardamonte
        ("brass", 7.8, 6.7, 13.8, 0.4, 0.7, 0.3),
        ("brass", 7.8, 6.7, 16.7, 0.4, 0.7, 0.3),
        ("iron", 7.85, 6.9, 15.0, 0.3, 0.6, 0.3),     # gatillo
    ]),
    "stock": ((8.0, 9.7, 17.0), (10, 0, 0), [
        ("wood", -1.1, -2.6, 0.0, 2.2, 2.6, 4.0),     # culata
        ("wood", -1.2, -4.2, 3.5, 2.4, 4.2, 3.5),
        ("brass", -1.25, -4.25, 7.0, 2.5, 4.3, 0.3),  # cantonera
    ]),
    "ramrod": ((0, 0, 0), (0, 0, 0), [
        ("iron", 7.8, 7.45, -7.0, 0.4, 0.4, 19.0),    # baqueta
        ("brass", 7.75, 7.4, -7.3, 0.5, 0.5, 0.4),    # su cabeza
    ]),
    "hammer": ((9.3, 9.3, 15.6), (0, 0, 0), [
        ("iron", -0.2, 0.0, -0.3, 0.4, 1.6, 0.6),     # rastrillo (el pie)
        ("iron", -0.25, 1.3, -1.2, 0.5, 0.5, 1.2),    # las quijadas
        ("dark", -0.2, 0.9, -1.25, 0.4, 0.4, 0.5),    # el pedernal
    ]),
    "frizzen": ((9.25, 9.7, 13.4), (0, 0, 0), [
        ("iron", -0.2, 0.0, 0.0, 0.4, 1.4, 0.4),      # el acero (contra él golpea el pedernal)
        ("iron", -0.25, -0.05, 0.0, 0.5, 0.15, 1.0),  # la tapa de la cazoleta
    ]),
    "ball": ((0, 0, 0), (0, 0, 0), [
        ("lead", 7.55, 9.35, -8.9, 0.9, 0.9, 0.9),    # la bala, justo delante de la boca
    ]),
}

# ------------------------------------------------------------------ textura


def texture():
    rnd = random.Random(1612)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 255))
    for name, (u, v, w, h) in ZONES.items():
        r0, g0, b0 = BASE[name]
        for y in range(v, v + h):
            grain = math.sin(y * 1.7) * 6 if name == "wood" else 0
            for x in range(u, u + w):
                n = rnd.randint(-7, 7) + grain
                if name == "wood" and rnd.random() < 0.06:
                    n -= 14  # vetas
                if name in ("iron", "brass") and rnd.random() < 0.04:
                    n += 18  # brillos
                img.putpixel((x, y), (clamp(r0 + n), clamp(g0 + n * 0.8), clamp(b0 + n * 0.6), 255))
    return img


def clamp(c):
    return max(0, min(255, int(c)))


# ------------------------------------------------------------------ clase Java


def java():
    out = []
    w = out.append
    w("package com.evolvedwarfare.client.render.musket;")
    w("")
    w("import com.evolvedwarfare.EvolvedWarfare;")
    w("import net.minecraft.client.model.ModelData;")
    w("import net.minecraft.client.model.ModelPartBuilder;")
    w("import net.minecraft.client.model.ModelPartData;")
    w("import net.minecraft.client.model.ModelTransform;")
    w("import net.minecraft.client.model.TexturedModelData;")
    w("import net.minecraft.client.render.entity.model.EntityModelLayer;")
    w("import net.minecraft.util.Identifier;")
    w("")
    w("/**")
    w(" * El mosquete en 3D (generado por tools/ew_musket.py: no lo edites a mano). Coordenadas en píxeles del objeto, con")
    w(" * Y hacia arriba, la boca del cañón hacia -Z y la llave a la derecha.")
    w(" */")
    w("public final class MusketModel {")
    w('\tpublic static final EntityModelLayer LAYER = new EntityModelLayer(EvolvedWarfare.id("musket"), "main");')
    w('\tpublic static final Identifier TEXTURE = EvolvedWarfare.id("textures/entity/musket.png");')
    w("")
    w("\tprivate MusketModel() {")
    w("\t}")
    w("")
    w("\tpublic static TexturedModelData getTexturedModelData() {")
    w("\t\tModelData data = new ModelData();")
    w("\t\tModelPartData root = data.getRoot();")
    for name, (pivot, rot, cubes) in PARTS.items():
        w("\t\troot.addChild(\"%s\", ModelPartBuilder.create()" % name)
        for mat, x, y, z, sx, sy, sz in cubes:
            u, v = ZONES[mat][0], ZONES[mat][1]
            w("\t\t\t\t.uv(%d, %d).cuboid(%sF, %sF, %sF, %sF, %sF, %sF)" % (u, v, x, y, z, sx, sy, sz))
        px, py, pz = pivot
        rx, ry, rz = (math.radians(a) for a in rot)
        w("\t\t\t\t, ModelTransform.of(%sF, %sF, %sF, %sF, %sF, %sF));" % (px, py, pz, round(rx, 6), round(ry, 6), round(rz, 6)))
    w("\t\treturn TexturedModelData.of(data, 64, 64);")
    w("\t}")
    w("}")
    return "\n".join(out) + "\n"


def main():
    tex = texture()
    tex.save(os.path.join(ROOT, "src/main/resources/assets/evolvedwarfare/textures/entity/musket.png"))
    path = os.path.join(ROOT, "src/client/java/com/evolvedwarfare/client/render/musket/MusketModel.java")
    with open(path, "w", encoding="utf-8") as f:
        f.write(java())
    # la bala de mosquete (objeto)
    ball = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    art = [
        "................",
        "................",
        "................",
        "................",
        "......oooo......",
        ".....oHHLLo.....",
        "....oHWLLLDo....",
        "....oHLLLLDo....",
        "....oLLLLDDo....",
        "....oLLLDDDo....",
        ".....oDDDDo.....",
        "......oooo......",
        "................",
        "................",
        "................",
        "................",
    ]
    cols = {"o": (34, 35, 40, 255), "D": (70, 73, 80, 255), "L": (104, 108, 116, 255), "H": (142, 146, 154, 255),
            "W": (196, 200, 206, 255)}
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch != ".":
                ball.putpixel((x, y), cols[ch])
    ball.save(os.path.join(ROOT, "src/main/resources/assets/evolvedwarfare/textures/item/musket_ball.png"))
    if PREVIEW:
        import ew_musket_preview
        ew_musket_preview.render_all(PARTS, ZONES, tex, PREVIEW)


if __name__ == "__main__":
    main()
