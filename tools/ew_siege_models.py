"""Máquinas de asedio en 3D (catapulta, balista, trebuchet y ariete). Una sola fuente para la geometría:

- textura de todas las piezas (assets/evolvedwarfare/textures/entity/siege_engines.png, 512x512)
- clase Java con los cuatro modelos (src/client/.../siege/SiegeModels.java)
- vistas previas (opcional): python3 tools/ew_siege_models.py <raíz del proyecto> [carpeta de vistas]

Coordenadas en píxeles (16 = 1 bloque) desde los pies de la máquina, Y hacia arriba y el frente (hacia donde
dispara) hacia +Z. Cada pieza tiene un pivote y una rotación (grados, X/Y/Z como las de ModelPart) y puede llevar
piezas hijas; las que se animan tienen nombre propio y el renderizador las mueve.
"""
import math
import os
import random
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

TEX_W, TEX_H = 512, 512
# zonas de la textura: u, v, ancho, alto
ZONES = {
    "dark": (0, 0, 512, 160),     # vigas de roble oscuro
    "wood": (0, 160, 512, 160),   # tablones claros
    "hide": (0, 320, 256, 128),   # cuero (tejado del ariete, honda)
    "iron": (256, 320, 256, 96),  # herrajes
    "rope": (256, 416, 128, 64),  # cuerda gruesa (madejas de torsión)
    "stone": (384, 416, 128, 64), # piedra
    "hay": (384, 480, 128, 32),   # paja (el tope de la catapulta)
    "cord": (256, 480, 128, 32),  # cuerda fina (cuerda de la balista)
}
BASE = {
    "dark": (78, 52, 30), "wood": (150, 108, 64), "hide": (128, 88, 56), "iron": (92, 94, 100),
    "rope": (150, 118, 78), "stone": (122, 122, 124), "hay": (196, 166, 72), "cord": (226, 218, 196),
}


class Part:
    def __init__(self, name, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=None, children=None):
        self.name = name
        self.pivot = pivot
        self.rot = rot
        self.cubes = cubes or []
        self.children = children or []

    def add(self, *parts):
        self.children.extend(parts)
        return self


def c(mat, x, y, z, w, h, d):
    return (mat, x, y, z, w, h, d)


_beams = [0]


def beam(mat, a, b, t, name=None):
    """Una viga de grosor t de 'a' a 'b' (puntos en píxeles), como pieza girada."""
    dx, dy, dz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    length = math.sqrt(dx * dx + dy * dy + dz * dz)
    ux, uy, uz = dx / length, dy / length, dz / length
    pitch = math.degrees(math.asin(max(-1.0, min(1.0, uz))))
    roll = math.degrees(math.atan2(-ux, uy))
    _beams[0] += 1
    return Part(name or "beam%d" % _beams[0], a, (pitch, 0, roll), [c(mat, -t / 2, 0, -t / 2, t, length, t)])


def wheel(name, x, y, z, radius, width):
    """Rueda de 12 lados (tres tablas giradas) con su buje de hierro."""
    w = Part(name, (x, y, z))
    side = 2 * radius * math.tan(math.pi / 12) * 1.9
    for i, a in enumerate((0, 60, 120)):
        w.add(Part("rim%d" % i, (0, 0, 0), (a, 0, 0), [c("wood", -width / 2, -radius, -side / 2, width, radius * 2, side)]))
    w.cubes.append(c("iron", -width / 2 - 0.6, -2, -2, width + 1.2, 4, 4))
    w.cubes.append(c("dark", -width / 2 - 0.3, -radius * 0.82, -0.8, width + 0.6, radius * 1.64, 1.6))
    w.cubes.append(c("dark", -width / 2 - 0.3, -0.8, -radius * 0.82, width + 0.6, 1.6, radius * 1.64))
    return w


# ------------------------------------------------------------------ catapulta
def catapult():
    root = Part("root")
    frame = Part("frame")
    frame.cubes += [
        c("dark", -16, 6, -24, 5, 5, 48), c("dark", 11, 6, -24, 5, 5, 48),          # largueros
        c("dark", -30, 6, -24, 46, 5, 5),                                          # travesaño de atrás (sostiene el asiento)
        c("dark", -16, 6, -7, 32, 5, 6), c("dark", -16, 6, 19, 32, 5, 5),           # travesaños
        c("iron", -20, 6, -15, 40, 2, 2), c("iron", -20, 6, 13, 40, 2, 2),          # ejes
        c("dark", -13, 11, 6, 4, 16, 4), c("dark", 9, 11, 6, 4, 16, 4),             # postes del tope
        c("dark", -13, 23, 6, 26, 4, 4),                                           # el tope
        c("hay", -6, 20, 4, 12, 8, 2),                                             # su cojín de paja
        c("dark", -12, 11, -8, 3, 8, 8), c("dark", 9, 11, -8, 3, 8, 8),             # soportes de la madeja
        c("rope", -9, 9, -7, 18, 8, 6),                                            # la madeja de torsión
        c("iron", -12.5, 11.5, -4.5, 25, 2, 2),                                    # su eje
        c("dark", -12, 11, -19, 3, 12, 4), c("dark", 9, 11, -19, 3, 12, 4),         # postes del torno
        c("dark", -9, 18, -18.5, 18, 4, 3),                                        # el torno
        c("iron", -13.5, 19, -17.5, 1.5, 2, 1), c("iron", -14.5, 17, -17.5, 1, 6, 1),
        c("iron", 12, 19, -17.5, 1.5, 2, 1), c("iron", 13.5, 17, -17.5, 1, 6, 1),
        c("wood", -30, 11, -28, 10, 2, 8),                                         # asiento (al lado, el brazo pasa por el centro)
        c("dark", -29.5, 0, -23.5, 3, 6, 3),                                       # su pata
    ]
    frame.add(beam("dark", (-14.5, 11, 20), (-11, 24, 9.5), 3), beam("dark", (14.5, 11, 20), (11, 24, 9.5), 3))
    for name, x, z in (("wheel_fl", -18, 14), ("wheel_fr", 18, 14), ("wheel_bl", -18, -14), ("wheel_br", 18, -14)):
        frame.add(wheel(name, x, 7, z, 6.8, 3))
    arm = Part("arm", (0, 12.5, -3.5), (0, 0, 0), [
        c("wood", -2, 0, -2, 4, 41, 4),
        c("iron", -2.5, 12, -2.5, 5, 1.5, 5), c("iron", -2.5, 30, -2.5, 5, 1.5, 5),
        c("wood", -6, 39, -6, 12, 2, 12),                                          # la cuchara
        c("wood", -6, 41, -6, 12, 3, 1.5), c("wood", -6, 41, 4.5, 12, 3, 1.5),
        c("wood", -6, 41, -4.5, 1.5, 3, 9), c("wood", 4.5, 41, -4.5, 1.5, 3, 9),
    ])
    arm.add(Part("stone", (0, 0, 0), (0, 0, 0), [c("stone", -4, 41, -4, 8, 7, 8)]))
    root.add(frame, arm)
    return root


# ------------------------------------------------------------------ balista
def ballista():
    root = Part("root")
    base = Part("base")
    base.cubes += [
        c("dark", -14, 0, -2, 28, 3, 4), c("dark", -2, 0, -14, 4, 3, 28),          # pies en cruz
        c("dark", -2.5, 3, -2.5, 5, 12, 5),                                        # poste
        c("wood", -5, 14, -5, 10, 2, 10),                                          # collar
        c("wood", -4, 8, -23, 8, 2, 7),                                            # taburete del que dispara
        c("dark", -3.5, 0, -22.5, 2, 8, 2), c("dark", 1.5, 0, -22.5, 2, 8, 2),
        c("dark", -3.5, 0, -18.5, 2, 8, 2), c("dark", 1.5, 0, -18.5, 2, 8, 2),
    ]
    base.add(beam("dark", (-12, 3, 0), (-2, 11, 0), 2.5), beam("dark", (12, 3, 0), (2, 11, 0), 2.5),
             beam("dark", (0, 3, 12), (0, 11, 2), 2.5), beam("dark", (0, 3, -12), (0, 11, -2), 2.5))
    turret = Part("turret", (0, 16, 0), (0, 0, 0), [
        c("dark", -2, 0, -18, 4, 4, 40),                                           # la caja
        c("wood", -9, 9, 13, 18, 2, 5), c("wood", -9, -6, 13, 18, 2, 5),            # marco de delante
        c("wood", -9, -4, 13, 2, 13, 5), c("wood", 7, -4, 13, 2, 13, 5),
        c("rope", -6.5, -5, 13.5, 3, 15, 4), c("rope", 3.5, -5, 13.5, 3, 15, 4),    # madejas de torsión
        c("iron", -7, 11, 13, 5, 1, 5), c("iron", 2, 11, 13, 5, 1, 5),
        c("iron", -7, -7, 13, 5, 1, 5), c("iron", 2, -7, 13, 5, 1, 5),
        c("dark", -5, -1, -17, 10, 3, 3),                                          # torno de atrás
        c("iron", -7, -1.5, -16.5, 2, 4, 2), c("iron", 5, -1.5, -16.5, 2, 4, 2),
        c("iron", -1.5, 4, 18, 3, 1, 4),                                           # guía del virote
    ])
    turret.add(Part("arm_l", (-5, 2, 15.5), (0, 0, 0), [c("wood", -16, -1, -1, 16, 2, 2), c("iron", -17, -1.5, -1.5, 2, 3, 3)]))
    turret.add(Part("arm_r", (5, 2, 15.5), (0, 0, 0), [c("wood", 0, -1, -1, 16, 2, 2), c("iron", 15, -1.5, -1.5, 2, 3, 3)]))
    turret.add(Part("nut", (0, 0, 0), (0, 0, 0), [c("iron", -2.5, 4, -1.5, 5, 2, 3)]))
    turret.add(Part("bolt", (0, 0, 0), (0, 0, 0), [
        c("wood", -0.6, 5, 0, 1.2, 1.2, 24), c("iron", -1.2, 4.6, 24, 2.4, 2, 4), c("cord", -1.8, 5.1, 0.5, 3.6, 0.4, 4),
    ]))
    root.add(base, turret, Part("cord", (0, 0, 0), (0, 0, 0), [c("cord", -0.4, -0.4, 0, 0.8, 0.8, 1)]))
    return root


# ------------------------------------------------------------------ trebuchet (casi a tamaño real)
def trebuchet():
    root = Part("root")
    frame = Part("frame")
    frame.cubes += [
        c("dark", -30, 6, -40, 7, 8, 80), c("dark", 23, 6, -40, 7, 8, 80),         # largueros
        c("dark", -30, 6, -40, 60, 8, 6), c("dark", -30, 6, -3, 60, 8, 6), c("dark", -30, 6, 34, 60, 8, 6),
        c("iron", -35, 7, -29, 70, 2, 2), c("iron", -35, 7, 27, 70, 2, 2),          # ejes
        c("dark", -30, 90, -5, 7, 10, 10), c("dark", 23, 90, -5, 7, 10, 10),        # cabezas de los caballetes
        c("iron", -31, 94, -2, 62, 4, 4),                                          # el eje del brazo
        c("dark", -29.5, 50, -17, 6, 6, 34), c("dark", 23.5, 50, -17, 6, 6, 34),    # riostras
        c("dark", -17, 14, -30, 3, 20, 7), c("dark", 14, 14, -30, 3, 20, 7),        # postes del torno
        c("dark", -14, 26, -30, 28, 7, 7),                                         # el torno (alto: las piernas pasan por debajo)
        c("iron", -15, 25, -27.5, 1, 9, 2), c("iron", -15, 28.5, -31, 1, 2, 9),
        c("iron", 14, 25, -27.5, 1, 9, 2), c("iron", 14, 28.5, -31, 1, 2, 9),
        c("wood", -6, 14, -40, 12, 2, 7),                                          # asiento de atrás
        c("wood", -10, 14, -46, 20, 1, 8),                                         # tablas donde cae la honda
    ]
    for x in (-26.5, 26.5):
        frame.add(beam("dark", (x, 14, -32), (x, 92, -1), 6), beam("dark", (x, 14, 32), (x, 92, 1), 6))
        frame.add(beam("dark", (x, 14, -39), (x, 52, -16), 4), beam("dark", (x, 14, 39), (x, 52, 16), 4))
    for name, x, z in (("wheel_fl", -32.5, 28), ("wheel_fr", 32.5, 28), ("wheel_bl", -32.5, -28), ("wheel_br", 32.5, -28)):
        frame.add(wheel(name, x, 8, z, 8, 4))
    arm = Part("arm", (0, 96, 0), (0, 0, 0), [
        c("dark", -3, -26, -3, 6, 130, 6),
        c("iron", -3.5, -26, -3.5, 7, 3, 7), c("iron", -3.5, -3, -3.5, 7, 6, 7), c("iron", -3.5, 100, -3.5, 7, 4, 7),
        c("iron", -3.5, 40, -3.5, 7, 2, 7),
    ])
    weight = Part("weight", (0, -26, 0), (0, 0, 0), [
        c("iron", -1.5, -12, -1.5, 3, 12, 3),
        c("dark", -14, -40, -14, 28, 28, 28),
        c("iron", -14.5, -31, -14.5, 29, 2, 29), c("iron", -14.5, -20, -14.5, 29, 2, 29),
        c("stone", -12, -12.5, -12, 24, 1, 24),
    ])
    sling = Part("sling", (0, 104, 0), (0, 0, 0), [
        c("cord", -2.5, -18, -0.5, 1, 18, 1), c("cord", 1.5, -18, -0.5, 1, 18, 1),
        c("hide", -3.5, -21, -3.5, 7, 3, 7),
    ])
    sling.add(Part("stone", (0, 0, 0), (0, 0, 0), [c("stone", -2.5, -24, -2.5, 5, 5, 5)]))
    arm.add(weight, sling)
    root.add(frame, arm)
    return root


# ------------------------------------------------------------------ ariete
def ram():
    root = Part("root")
    frame = Part("frame")
    frame.cubes += [
        c("dark", -16, 6, -24, 4, 6, 48), c("dark", 12, 6, -24, 4, 6, 48),
        c("dark", -16, 6, -24, 32, 6, 4), c("dark", -16, 6, 20, 32, 6, 4),
        c("iron", -20, 6, -17, 40, 2, 2), c("iron", -20, 6, 15, 40, 2, 2),
        c("dark", -16, 32, -24, 4, 4, 48), c("dark", 12, 32, -24, 4, 4, 48),       # vigas de arriba
        c("dark", -2, 43, -26, 4, 4, 52),                                          # cumbrera (de ella cuelga el tronco)
        c("dark", -2, 36, -24, 4, 7, 3), c("dark", -2, 36, 21, 4, 7, 3),            # pendolones
        c("wood", -13, 12, -18, 8, 2, 8),                                          # asiento (a un lado del tronco)
    ]
    for x in (-16, 12):
        for z in (-24, -2, 20):
            frame.cubes.append(c("dark", x, 12, z, 4, 20, 4))
    rise = math.degrees(math.atan2(10, 17))
    frame.add(Part("roof_l", (-17.5, 35, -26), (0, 0, rise), [c("hide", 0, 0, 0, 20, 1.5, 52)]))
    frame.add(Part("roof_r", (17.5, 35, -26), (0, 0, 180 - rise), [c("hide", 0, -1.5, 0, 20, 1.5, 52)]))
    for name, x, z in (("wheel_fl", -18, 16), ("wheel_fr", 18, 16), ("wheel_bl", -18, -16), ("wheel_br", 18, -16)):
        frame.add(wheel(name, x, 7, z, 6.8, 3))
    log = Part("log", (0, 0, 0), (0, 0, 0), [
        c("dark", -4, 16, -20, 8, 8, 40),
        c("iron", -5, 15, 20, 10, 10, 6), c("iron", -3, 17, 26, 6, 6, 3),
        c("iron", -4.5, 15.5, -12, 9, 9, 2), c("iron", -4.5, 15.5, 8, 9, 9, 2),
    ])
    rope_a = Part("rope_a", (0, 43, -10), (0, 0, 0), [c("rope", -0.75, -19, -0.75, 1.5, 19, 1.5)])
    rope_b = Part("rope_b", (0, 43, 10), (0, 0, 0), [c("rope", -0.75, -19, -0.75, 1.5, 19, 1.5)])
    root.add(frame, log, rope_a, rope_b)
    return root


MODELS = {"catapult": catapult, "ballista": ballista, "trebuchet": trebuchet, "ram": ram}
# dónde se sienta el jugador (bloques, como getPassengerAttachmentPos) — el culo queda 0,1 por encima
SEATS = {"catapult": (-1.5625, 0.7125, -1.5), "ballista": (0.0, 0.525, -1.22), "trebuchet": (0.0, 0.9, -2.28),
         "ram": (-0.5625, 0.775, -0.875)}


# ------------------------------------------------------------------ textura
def texture():
    rnd = random.Random(1453)
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 255))
    px = img.load()
    for name, (u, v, w, h) in ZONES.items():
        r0, g0, b0 = BASE[name]
        for y in range(v, v + h):
            for x in range(u, u + w):
                n = rnd.randint(-6, 6)
                if name in ("dark", "wood"):
                    n += math.sin(y * 0.9 + math.sin(x * 0.07) * 2.0) * 7       # vetas
                    if (y - v) % 16 == 0:
                        n -= 22                                                # junta entre tablas
                    if rnd.random() < 0.012:
                        n -= 18                                                # nudos
                elif name == "iron":
                    if rnd.random() < 0.05:
                        n += 22
                    if (x - u) % 12 == 3 and (y - v) % 12 == 3:
                        n += 40                                                # remaches
                elif name in ("rope", "cord"):
                    n += 14 if (x + y) % 4 < 2 else -10                        # trenzado
                elif name == "hide":
                    n += math.sin(x * 0.21) * math.cos(y * 0.17) * 10
                    if (x - u) % 32 == 0 or (y - v) % 32 == 0:
                        n -= 26                                                # costuras de las pieles
                elif name == "stone":
                    n += rnd.choice((0, 0, 0, -18, 14))
                elif name == "hay":
                    n += 16 if (x * 3 + y) % 7 < 2 else -6
                px[x, y] = (clamp(r0 + n), clamp(g0 + n * 0.85), clamp(b0 + n * 0.7), 255)
    return img


def clamp(v):
    return max(0, min(255, int(v)))


# ------------------------------------------------------------------ clase Java
def check(part, kind):
    for mat, x, y, z, w, h, d in part.cubes:
        zu, zv, zw, zh = ZONES[mat]
        assert 2 * (w + d) <= zw and d + h <= zh, (kind, part.name, mat, w, h, d)
    for child in part.children:
        check(child, kind)


def fmt(v):
    return ("%.4f" % v).rstrip("0").rstrip(".") + "F" if isinstance(v, float) or True else str(v)


def emit(out, var, part, depth):
    for child in part.children:
        cvar = var + "_" + child.name
        lines = ["ModelPartBuilder.create()"]
        for mat, x, y, z, w, h, d in child.cubes:
            u, v = ZONES[mat][0], ZONES[mat][1]
            lines.append(".uv(%d, %d).cuboid(%s, %s, %s, %s, %s, %s)" % (u, v, fmt(x), fmt(y), fmt(z), fmt(w), fmt(h), fmt(d)))
        px, py, pz = child.pivot
        rx, ry, rz = (math.radians(a) for a in child.rot)
        out.append("\t\tModelPartData %s = %s.addChild(\"%s\", %s," % (cvar, var, child.name, "\n\t\t\t\t".join(lines)))
        out.append("\t\t\t\tModelTransform.of(%s, %s, %s, %s, %s, %s));" % (fmt(px), fmt(py), fmt(pz), fmt(rx), fmt(ry), fmt(rz)))
        emit(out, cvar, child, depth + 1)


def java(models):
    out = []
    w = out.append
    w("package com.evolvedwarfare.client.render.siege;")
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
    w(" * Los modelos 3D de las máquinas de asedio (generado por tools/ew_siege_models.py: no lo edites a mano). En píxeles")
    w(" * desde los pies de la máquina, Y hacia arriba y el frente hacia +Z.")
    w(" */")
    w("@SuppressWarnings(\"unused\")")
    w("public final class SiegeModels {")
    w('\tpublic static final Identifier TEXTURE = EvolvedWarfare.id("textures/entity/siege_engines.png");')
    for name in models:
        w('\tpublic static final EntityModelLayer %s = new EntityModelLayer(EvolvedWarfare.id("%s"), "main");' % (name.upper(), name))
    w("")
    w("\tprivate SiegeModels() {")
    w("\t}")
    for name, root in models.items():
        w("")
        w("\tpublic static TexturedModelData %s() {" % name)
        w("\t\tModelData data = new ModelData();")
        w("\t\tModelPartData root = data.getRoot();")
        emit(out, "root", root, 0)
        w("\t\treturn TexturedModelData.of(data, %d, %d);" % (TEX_W, TEX_H))
        w("\t}")
    w("}")
    return "\n".join(out) + "\n"


def main():
    models = {name: build() for name, build in MODELS.items()}
    for name, root in models.items():
        check(root, name)
    texture().save(os.path.join(ROOT, "src/main/resources/assets/evolvedwarfare/textures/entity/siege_engines.png"))
    folder = os.path.join(ROOT, "src/client/java/com/evolvedwarfare/client/render/siege")
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, "SiegeModels.java"), "w", encoding="utf-8") as f:
        f.write(java(models))
    if PREVIEW:
        import ew_siege_preview
        ew_siege_preview.render_all(models, SEATS, PREVIEW)


if __name__ == "__main__":
    main()
