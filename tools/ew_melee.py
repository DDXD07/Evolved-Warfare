"""Las armas cuerpo a cuerpo en 3D (daga, lanza, mandoble, alabarda, martillo de guerra, mangual y hacha de batalla) y
sus animaciones de ataque. Una sola fuente para:

- texturas por material (assets/evolvedwarfare/textures/entity/weapons/<material>.png): hierro, diamante, netherita
- la clase Java con los siete modelos (src/client/.../melee/MeleeModels.java)
- las animaciones (src/client/.../melee/MeleeAnimations.java): fotogramas clave en primera y tercera persona
- los modelos de objeto: <material>_<arma>.json (3D, lo dibuja MeleeWeaponRenderer) y <material>_<arma>_icon.json
  (el icono plano de siempre, para el inventario)
- vistas previas (opcional): python3 tools/ew_melee.py <raíz del proyecto> [carpeta de vistas]

Cada arma se construye en su propio marco, en píxeles: el origen en el centro de la mano, +Y hacia la punta, X a lo
ancho (el filo del hacha, la hoja de la alabarda y la cara del martillo hacia -X, que en la mano mira hacia delante y
abajo) y Z el grueso. En el objeto, ese marco va con el origen en GRIP y girado -45° sobre Z: la misma diagonal que los
iconos de las armas del juego, así se coge igual que una espada (en la mano, en el suelo, en un marco...).
"""
import json
import math
import os
import random
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None
NS = "evolvedwarfare"

WEAPONS = ("dagger", "spear", "longsword", "halberd", "warhammer", "flail", "battle_axe")
MATERIALS = ("iron", "diamond", "netherite")
# donde la mano de vanilla agarra un objeto "handheld" (píxeles del objeto) y el giro de la diagonal
GRIP = (3.0, 3.0, 8.0)
DIAGONAL = -45.0
# el mangual: la cadena cuelga de este punto del mango (marco del arma)
CHAIN_ANCHOR = 8.0

TEX_W, TEX_H = 256, 128
ZONES = {
    "wood": (0, 0, 48, 128),     # astas y mangos
    "metal": (48, 0, 80, 64),    # hojas y cabezas
    "edge": (48, 64, 80, 64),    # filos, caras de golpe y pinchos (más claro)
    "fit": (128, 0, 64, 64),     # guardas, pomos, virolas
    "grip": (128, 64, 64, 64),   # cuero de la empuñadura
    "chain": (192, 0, 64, 32),   # cadena del mangual
    "dark": (192, 32, 64, 32),   # acanaladura de la hoja
}


def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


# los mismos tonos que los iconos (tools/ew_weapons.py)
PALETTES = {
    "iron": {"metal": hexc("#C8C8C8"), "edge": hexc("#F2F2F2"), "fit": hexc("#7C7C80"), "dark": hexc("#8E8E8E"),
             "grip": hexc("#6E4A2A"), "wood": hexc("#7A5530"), "chain": hexc("#5C5E64")},
    "diamond": {"metal": hexc("#5FE6D6"), "edge": hexc("#D6FFF8"), "fit": hexc("#26A89A"), "dark": hexc("#2FB8A8"),
                "grip": hexc("#6E4A2A"), "wood": hexc("#7A5530"), "chain": hexc("#5C5E64")},
    "netherite": {"metal": hexc("#4D4249"), "edge": hexc("#8A7E88"), "fit": hexc("#E8B830"), "dark": hexc("#332B31"),
                  "grip": hexc("#3A2E3A"), "wood": hexc("#4A3A40"), "chain": hexc("#3A3438")},
}


def c(mat, x, y, z, w, h, d):
    return (mat, x, y, z, w, h, d)


# ------------------------------------------------------------------ las armas (marco propio, píxeles)
def dagger():
    return [
        c("fit", -1.0, -3.3, -1.0, 2.0, 1.0, 2.0),        # pomo
        c("grip", -0.6, -2.3, -0.6, 1.2, 3.3, 1.2),       # empuñadura
        c("fit", -2.2, 1.0, -0.7, 4.4, 0.8, 1.4),         # guarda
        c("metal", -0.95, 1.8, -0.3, 1.9, 6.2, 0.6),      # hoja
        c("edge", -1.05, 1.8, -0.18, 0.12, 6.2, 0.36),
        c("edge", 0.93, 1.8, -0.18, 0.12, 6.2, 0.36),
        c("dark", -0.2, 2.2, -0.33, 0.4, 4.8, 0.66),      # acanaladura
        c("metal", -0.65, 8.0, -0.25, 1.3, 1.1, 0.5),     # punta
        c("metal", -0.3, 9.1, -0.2, 0.6, 0.9, 0.4),
    ]


def longsword():
    return [
        c("fit", -1.2, -7.0, -1.2, 2.4, 1.4, 2.4),        # pomo
        c("fit", -0.75, -7.4, -0.75, 1.5, 0.4, 1.5),
        c("grip", -0.7, -5.6, -0.7, 1.4, 5.8, 1.4),       # empuñadura a dos manos
        c("fit", -4.2, 0.2, -0.8, 8.4, 1.0, 1.6),         # guarda
        c("fit", -4.8, -0.1, -0.9, 0.8, 1.6, 1.8),        # gavilanes
        c("fit", 4.0, -0.1, -0.9, 0.8, 1.6, 1.8),
        c("fit", -1.4, 1.2, -0.5, 2.8, 0.9, 1.0),         # recazo
        c("metal", -1.2, 2.1, -0.35, 2.4, 15.4, 0.7),     # hoja
        c("edge", -1.32, 2.1, -0.2, 0.14, 15.4, 0.4),
        c("edge", 1.18, 2.1, -0.2, 0.14, 15.4, 0.4),
        c("dark", -0.3, 2.4, -0.38, 0.6, 12.0, 0.76),     # acanaladura
        c("metal", -0.9, 17.5, -0.3, 1.8, 1.4, 0.6),      # punta
        c("metal", -0.52, 18.9, -0.25, 1.04, 1.2, 0.5),
        c("metal", -0.2, 20.1, -0.2, 0.4, 0.8, 0.4),
    ]


def spear():
    return [
        c("wood", -0.65, -13.0, -0.65, 1.3, 31.0, 1.3),   # asta
        c("fit", -0.85, -14.1, -0.85, 1.7, 1.2, 1.7),     # regatón
        c("grip", -0.75, -2.5, -0.75, 1.5, 5.0, 1.5),     # forro donde va la mano
        c("grip", -0.75, -10.0, -0.75, 1.5, 3.0, 1.5),
        c("fit", -0.85, 17.6, -0.85, 1.7, 2.6, 1.7),      # cubo
        c("metal", -1.2, 20.0, -0.28, 2.4, 0.6, 0.56),    # punta en hoja de laurel
        c("metal", -1.55, 20.6, -0.3, 3.1, 3.6, 0.6),
        c("metal", -1.15, 24.2, -0.27, 2.3, 2.0, 0.54),
        c("metal", -0.7, 26.2, -0.24, 1.4, 1.6, 0.48),
        c("metal", -0.3, 27.8, -0.2, 0.6, 1.1, 0.4),
        c("edge", -0.22, 20.0, -0.36, 0.44, 7.4, 0.72),   # nervio
    ]


def halberd():
    return [
        c("wood", -0.7, -12.0, -0.7, 1.4, 34.5, 1.4),     # asta
        c("fit", -0.9, -13.2, -0.9, 1.8, 1.3, 1.8),       # regatón
        c("grip", -0.8, -3.0, -0.8, 1.6, 5.0, 1.6),
        c("fit", -0.9, 13.5, -0.9, 1.8, 0.9, 1.8),        # virolas
        c("fit", -0.9, 21.6, -0.9, 1.8, 0.9, 1.8),
        c("metal", -3.2, 15.4, -0.3, 2.5, 5.6, 0.6),      # la hoja de hacha (hacia -X)
        c("metal", -5.6, 13.8, -0.28, 2.4, 8.8, 0.56),
        c("metal", -6.9, 12.8, -0.25, 1.3, 10.8, 0.5),
        c("edge", -7.1, 13.0, -0.15, 0.2, 10.4, 0.3),
        c("metal", 0.7, 18.4, -0.3, 3.4, 1.1, 0.6),       # el pico de atrás, curvado hacia abajo
        c("metal", 3.7, 17.2, -0.26, 1.0, 1.4, 0.52),
        c("metal", 4.1, 16.4, -0.22, 0.6, 1.0, 0.44),
        c("metal", -0.6, 22.5, -0.35, 1.2, 5.6, 0.7),     # la pica de arriba
        c("metal", -0.35, 28.1, -0.25, 0.7, 1.8, 0.5),
    ]


def warhammer():
    return [
        c("wood", -0.7, -5.5, -0.7, 1.4, 25.5, 1.4),      # asta
        c("fit", -0.9, -6.6, -0.9, 1.8, 1.2, 1.8),
        c("grip", -0.8, -4.5, -0.8, 1.6, 7.0, 1.6),       # empuñadura a dos manos
        c("fit", -0.85, 9.0, -0.85, 1.7, 0.8, 1.7),       # anilla
        c("fit", -1.0, 16.6, -1.0, 2.0, 5.0, 2.0),        # cubo
        c("metal", -4.6, 17.6, -1.3, 3.6, 3.4, 2.6),      # la maza (hacia -X)
        c("edge", -5.1, 17.4, -1.5, 0.5, 3.8, 3.0),       # la cara de golpe
        c("metal", 1.0, 18.3, -0.7, 3.0, 2.0, 1.4),       # el pico de atrás
        c("metal", 4.0, 18.7, -0.45, 1.6, 1.2, 0.9),
        c("metal", 5.6, 19.0, -0.25, 0.8, 0.6, 0.5),
        c("metal", -0.45, 21.6, -0.45, 0.9, 2.6, 0.9),    # la pica de arriba
    ]


def flail():
    return [
        c("wood", -0.8, -4.2, -0.8, 1.6, 10.6, 1.6),      # mango
        c("grip", -0.9, -3.2, -0.9, 1.8, 5.0, 1.8),
        c("fit", -1.1, -5.2, -1.1, 2.2, 1.1, 2.2),        # pomo
        c("fit", -1.1, 6.4, -1.1, 2.2, 1.2, 2.2),         # virola de arriba
        c("fit", -0.25, 7.6, -0.6, 0.5, 0.8, 1.2),        # argolla
    ]


def flail_chain():
    """La cadena y la bola, colgando hacia -Y desde el punto de enganche."""
    cubes = []
    for i in range(6):
        y0 = -1.3 * (i + 1)
        if i % 2 == 0:
            cubes.append(c("chain", -0.4, y0, -0.12, 0.8, 1.4, 0.24))
        else:
            cubes.append(c("chain", -0.12, y0, -0.4, 0.24, 1.4, 0.8))
    cy = -9.8
    cubes += [
        c("metal", -1.7, cy - 1.7, -1.7, 3.4, 3.4, 3.4),
        c("metal", -1.3, cy - 2.1, -1.3, 2.6, 4.2, 2.6),
        c("metal", -2.1, cy - 1.3, -1.3, 4.2, 2.6, 2.6),
        c("metal", -1.3, cy - 1.3, -2.1, 2.6, 2.6, 4.2),
        c("edge", 2.1, cy - 0.35, -0.35, 1.2, 0.7, 0.7),   # pinchos
        c("edge", -3.3, cy - 0.35, -0.35, 1.2, 0.7, 0.7),
        c("edge", -0.35, cy - 0.35, 2.1, 0.7, 0.7, 1.2),
        c("edge", -0.35, cy - 0.35, -3.3, 0.7, 0.7, 1.2),
        c("edge", -0.35, cy - 3.3, -0.35, 0.7, 1.2, 0.7),
        c("edge", 1.2, cy + 1.2, -0.3, 0.6, 0.6, 0.6),
        c("edge", -1.8, cy + 1.2, -0.3, 0.6, 0.6, 0.6),
        c("edge", 1.2, cy - 1.8, -0.3, 0.6, 0.6, 0.6),
        c("edge", -1.8, cy - 1.8, -0.3, 0.6, 0.6, 0.6),
    ]
    return cubes


def battle_axe():
    return [
        c("wood", -0.75, -5.0, -0.75, 1.5, 25.0, 1.5),    # mango
        c("fit", -0.9, -6.1, -0.9, 1.8, 1.2, 1.8),        # remate
        c("grip", -0.85, -4.0, -0.85, 1.7, 5.5, 1.7),
        c("fit", -1.1, 15.0, -1.1, 2.2, 5.0, 2.2),        # ojo del hacha
        c("fit", -0.8, 20.0, -0.8, 1.6, 0.8, 1.6),
        c("metal", -3.1, 15.6, -0.42, 2.0, 3.6, 0.84),    # la hoja (hacia -X), con barba
        c("metal", -5.4, 13.4, -0.36, 2.3, 8.2, 0.72),
        c("metal", -7.0, 11.6, -0.3, 1.6, 11.6, 0.6),
        c("metal", -4.3, 12.2, -0.33, 1.0, 1.4, 0.66),
        c("edge", -7.35, 11.9, -0.16, 0.35, 11.0, 0.32),  # filo
        c("fit", 1.1, 16.0, -0.95, 1.6, 2.8, 1.9),        # el talón de atrás
    ]


SHAPES = {"dagger": dagger, "spear": spear, "longsword": longsword, "halberd": halberd, "warhammer": warhammer,
          "flail": flail, "battle_axe": battle_axe}

# ------------------------------------------------------------------ animaciones
# Ataque (el golpe de vanilla, con su progreso 0..1). Cada fotograma: (progreso, valores...); entre fotogramas se pasa
# con una curva suave. En primera persona: movimiento de la mano (bloques, ejes de la vista: X derecha, Y arriba, Z
# hacia la cámara) y giro alrededor de la empuñadura (grados: cabeceo X, guiñada Y, alabeo Z), y "cadena" (0 = cuelga,
# 1 = estirada: solo el mangual). En tercera persona: el brazo que golpea (cabeceo, guiñada, alabeo, en grados, como
# ModelPart: cabeceo -90 = brazo al frente), el otro brazo si el arma es de dos manos y está libre, el giro del cuerpo,
# la inclinación del arma en la mano (-90 = en la línea del brazo) y "cadena".
FP_FIELDS = ("dx", "dy", "dz", "pitch", "yaw", "roll", "chain")
TP_FIELDS = ("arm_pitch", "arm_yaw", "arm_roll", "off_pitch", "off_yaw", "off_roll", "body_yaw", "tilt", "chain")
REST_FP = (0, 0, 0, 0, 0, 0, 0)
REST_TP = (-18, 0, 0, 0, 0, 0, 0, 0, 0)

ANIMATIONS = {
    # puñalada rápida al frente
    "dagger": dict(ticks=5, two_handed=False, fp=[
        (0.0, *REST_FP),
        (0.18, -0.10, 0.06, 0.06, -68, 10, 0, 0),
        (0.40, -0.20, 0.10, -0.40, -82, 12, 0, 0),
        (0.62, -0.14, 0.06, -0.18, -70, 8, 0, 0),
        (1.0, *REST_FP),
    ], tp=[
        (0.0, *REST_TP),
        (0.18, -40, 8, 0, 0, 0, 0, 12, -40, 0),
        (0.40, -95, -6, 0, 0, 0, 0, -18, -88, 0),
        (0.62, -82, -4, 0, 0, 0, 0, -10, -80, 0),
        (1.0, *REST_TP),
    ]),
    # estocada larga a dos manos
    "spear": dict(ticks=7, two_handed=True, fp=[
        (0.0, *REST_FP),
        (0.18, -0.18, 0.10, 0.18, -80, 6, 0, 0),
        (0.40, -0.30, 0.14, -0.62, -86, 8, 0, 0),
        (0.62, -0.26, 0.12, -0.36, -84, 6, 0, 0),
        (1.0, *REST_FP),
    ], tp=[
        (0.0, *REST_TP),
        (0.18, -48, 14, 0, -58, 38, 0, 18, -48, 0),
        (0.40, -88, -4, 0, -84, 30, 0, -22, -90, 0),
        (0.62, -80, 0, 0, -76, 30, 0, -12, -88, 0),
        (1.0, *REST_TP),
    ]),
    # tajo horizontal de derecha a izquierda, a dos manos
    "longsword": dict(ticks=9, two_handed=True, fp=[
        (0.0, *REST_FP),
        (0.12, 0.08, 0.14, 0.04, 0, -25, -80, 0),
        (0.42, -0.55, 0.04, -0.16, 0, 150, -95, 0),
        (0.58, -0.62, -0.02, -0.06, 0, 168, -100, 0),
        (1.0, *REST_FP),
    ], tp=[
        (0.0, *REST_TP),
        (0.12, -88, 55, 0, -80, 62, 0, 28, -80, 0),
        (0.42, -80, -55, 0, -78, -40, 0, -30, -82, 0),
        (0.58, -62, -48, 0, -60, -34, 0, -24, -78, 0),
        (1.0, *REST_TP),
    ]),
    # hachazo de arriba abajo, a dos manos
    "halberd": dict(ticks=10, two_handed=True, fp=[
        (0.0, *REST_FP),
        (0.16, 0.04, 0.30, 0.04, 5, 0, -15, 0),
        (0.40, -0.25, -0.08, -0.35, -115, 0, 5, 0),
        (0.56, -0.25, -0.12, -0.30, -118, 0, 4, 0),
        (1.0, *REST_FP),
    ], tp=[
        (0.0, *REST_TP),
        (0.16, -170, 8, 0, -160, -18, 0, 6, 30, 0),
        (0.40, -72, -6, 0, -66, 24, 0, -8, -95, 0),
        (0.56, -62, -6, 0, -56, 24, 0, -6, -90, 0),
        (1.0, *REST_TP),
    ]),
    # mazazo por encima de la cabeza, con rebote al dar
    "warhammer": dict(ticks=11, two_handed=True, fp=[
        (0.0, *REST_FP),
        (0.20, 0.05, 0.34, 0.06, 8, 0, -12, 0),
        (0.36, -0.28, -0.12, -0.28, -108, 0, 0, 0),
        (0.44, -0.28, -0.06, -0.28, -98, 0, 0, 0),
        (0.52, -0.28, -0.12, -0.28, -106, 0, 0, 0),
        (0.72, -0.20, -0.08, -0.18, -75, 0, 0, 0),
        (1.0, *REST_FP),
    ], tp=[
        (0.0, *REST_TP),
        (0.20, -178, 6, 0, -172, -14, 0, 0, 35, 0),
        (0.36, -70, -4, 0, -66, 22, 0, -6, -95, 0),
        (0.44, -78, -4, 0, -74, 22, 0, -6, -88, 0),
        (0.52, -70, -4, 0, -66, 22, 0, -6, -95, 0),
        (0.72, -50, -2, 0, -46, 16, 0, -4, -70, 0),
        (1.0, *REST_TP),
    ]),
    # volteo de la bola por encima y golpe cruzado; la cadena se estira al girar
    "flail": dict(ticks=10, two_handed=False, fp=[
        (0.0, *REST_FP),
        (0.15, 0.06, 0.25, 0.06, 10, 0, -25, 0.7),
        (0.40, -0.35, -0.10, -0.25, -90, 0, 35, 1.0),
        (0.60, -0.30, -0.12, -0.20, -80, 0, 30, 0.6),
        (1.0, *REST_FP),
    ], tp=[
        (0.0, *REST_TP),
        (0.15, -150, 18, -20, 0, 0, 0, 14, 0, 0.7),
        (0.40, -60, -32, 0, 0, 0, 0, -16, -60, 1.0),
        (0.60, -50, -28, 0, 0, 0, 0, -12, -55, 0.6),
        (1.0, *REST_TP),
    ]),
    # hachazo en diagonal, de arriba a la derecha hacia abajo a la izquierda
    "battle_axe": dict(ticks=10, two_handed=False, fp=[
        (0.0, *REST_FP),
        (0.18, 0.08, 0.28, 0.06, 10, 0, -35, 0),
        (0.40, -0.32, -0.08, -0.30, -105, 0, 30, 0),
        (0.56, -0.32, -0.10, -0.28, -110, 0, 32, 0),
        (1.0, *REST_FP),
    ], tp=[
        (0.0, *REST_TP),
        (0.18, -165, 28, 0, 0, 0, 0, 16, 25, 0),
        (0.40, -62, -36, 0, 0, 0, 0, -16, -85, 0),
        (0.56, -55, -32, 0, 0, 0, 0, -14, -80, 0),
        (1.0, *REST_TP),
    ]),
}


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def sample(keys, p):
    """Lo mismo que MeleeAnimations.sample (Java)."""
    p = max(0.0, min(1.0, p))
    for i in range(len(keys) - 1):
        a, b = keys[i], keys[i + 1]
        if p <= b[0]:
            t = smooth((p - a[0]) / max(1e-6, b[0] - a[0]))
            return [a[j] + (b[j] - a[j]) * t for j in range(1, len(a))]
    return list(keys[-1][1:])


# ------------------------------------------------------------------ textura
def clamp(v):
    return max(0, min(255, int(v)))


def texture(material):
    rnd = random.Random(1066 + MATERIALS.index(material))
    pal = PALETTES[material]
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 255))
    px = img.load()
    for name, (u, v, w, h) in ZONES.items():
        r0, g0, b0 = pal[name]
        for y in range(v, v + h):
            for x in range(u, u + w):
                n = rnd.randint(-5, 5)
                if name == "wood":
                    n += math.sin(x * 1.3 + math.sin(y * 0.11) * 2.0) * 7          # vetas a lo largo
                    if rnd.random() < 0.01:
                        n -= 16
                elif name in ("metal", "edge"):
                    n = rnd.randint(-3, 3) + (6 if (x * 7 + y * 3) % 11 == 0 else 0)  # metal pulido, algún brillo
                    if name == "metal":
                        n += math.sin(y * 0.35) * 3
                elif name == "grip":
                    n += 16 if (y + x // 3) % 3 == 0 else -6                       # tiras de cuero enrolladas
                elif name == "fit":
                    n += rnd.choice((0, 0, 0, 8, -8))
                elif name == "chain":
                    n += 14 if (x + y) % 3 == 0 else -6
                px[x, y] = (clamp(r0 + n), clamp(g0 + n), clamp(b0 + n), 255)
    return img


def check(cubes, name):
    for mat, x, y, z, w, h, d in cubes:
        zu, zv, zw, zh = ZONES[mat]
        assert 2 * (w + d) <= zw and d + h <= zh, (name, mat, w, h, d)


# ------------------------------------------------------------------ Java
def fmt(v):
    s = ("%.4f" % v).rstrip("0").rstrip(".")
    return (s if s not in ("-0", "") else "0") + "F"


def builder(cubes):
    lines = ["ModelPartBuilder.create()"]
    for mat, x, y, z, w, h, d in cubes:
        u, v = ZONES[mat][0], ZONES[mat][1]
        lines.append(".uv(%d, %d).cuboid(%s, %s, %s, %s, %s, %s)" % (u, v, fmt(x), fmt(y), fmt(z), fmt(w), fmt(h), fmt(d)))
    return "\n\t\t\t\t".join(lines)


def java_models():
    out = []
    w = out.append
    w("package com.evolvedwarfare.client.render.melee;")
    w("")
    w("import com.evolvedwarfare.EvolvedWarfare;")
    w("import com.evolvedwarfare.weapon.WeaponType;")
    w("import net.minecraft.client.model.ModelData;")
    w("import net.minecraft.client.model.ModelPartBuilder;")
    w("import net.minecraft.client.model.ModelPartData;")
    w("import net.minecraft.client.model.ModelTransform;")
    w("import net.minecraft.client.model.TexturedModelData;")
    w("import net.minecraft.client.render.entity.model.EntityModelLayer;")
    w("import net.minecraft.util.Identifier;")
    w("")
    w("/**")
    w(" * Los modelos 3D de las armas cuerpo a cuerpo (generado por tools/ew_melee.py: no lo edites a mano). Cada uno tiene")
    w(" * la pieza \"weapon\" (el arma en su marco: origen en la mano, +Y hacia la punta) puesta en la diagonal del objeto;")
    w(" * el mangual tiene además \"chain\" (cadena y bola, colgando hacia -Y), que el renderizador coloca en su sitio.")
    w(" */")
    w("public final class MeleeModels {")
    w("\t/** Dónde agarra la mano (píxeles del objeto) y giro de la diagonal (grados, sobre Z). */")
    w("\tpublic static final float GRIP_X = %s, GRIP_Y = %s, GRIP_Z = %s, DIAGONAL = %s;" % (fmt(GRIP[0]), fmt(GRIP[1]), fmt(GRIP[2]), fmt(DIAGONAL)))
    w("\t/** Altura (marco del arma, píxeles) de la que cuelga la cadena del mangual. */")
    w("\tpublic static final float CHAIN_ANCHOR = %s;" % fmt(CHAIN_ANCHOR))
    for name in WEAPONS:
        w('\tpublic static final EntityModelLayer %s = new EntityModelLayer(EvolvedWarfare.id("weapon_%s"), "main");' % (name.upper(), name))
    w("")
    w("\tprivate MeleeModels() {")
    w("\t}")
    w("")
    w("\tpublic static Identifier texture(String material) {")
    w('\t\treturn EvolvedWarfare.id("textures/entity/weapons/" + material + ".png");')
    w("\t}")
    w("")
    w("\tpublic static EntityModelLayer layer(WeaponType type) {")
    w("\t\treturn switch (type) {")
    for name in WEAPONS:
        w("\t\t\tcase %s -> %s;" % (name.upper(), name.upper()))
    w("\t\t};")
    w("\t}")
    w("")
    w("\tpublic static TexturedModelData of(WeaponType type) {")
    w("\t\treturn switch (type) {")
    for name in WEAPONS:
        w("\t\t\tcase %s -> %s();" % (name.upper(), camel(name)))
    w("\t\t};")
    w("\t}")
    for name in WEAPONS:
        w("")
        w("\tprivate static TexturedModelData %s() {" % camel(name))
        w("\t\tModelData data = new ModelData();")
        w("\t\tModelPartData root = data.getRoot();")
        w("\t\troot.addChild(\"weapon\", %s," % builder(SHAPES[name]()))
        w("\t\t\t\tModelTransform.of(GRIP_X, GRIP_Y, GRIP_Z, 0F, 0F, DIAGONAL * (float) (Math.PI / 180.0)));")
        if name == "flail":
            w("\t\troot.addChild(\"chain\", %s," % builder(flail_chain()))
            w("\t\t\t\tModelTransform.NONE);")
        w("\t\treturn TexturedModelData.of(data, %d, %d);" % (TEX_W, TEX_H))
        w("\t}")
    w("}")
    return "\n".join(out) + "\n"


def java_swing():
    out = []
    w = out.append
    w("package com.evolvedwarfare.weapon;")
    w("")
    w("/**")
    w(" * Cómo es el golpe de cada arma cuerpo a cuerpo (generado por tools/ew_melee.py: no lo edites a mano): lo que dura")
    w(" * la animación (en el servidor y en el cliente, para que vayan a la par) y si se coge con las dos manos.")
    w(" */")
    w("public final class MeleeSwing {")
    w("\tprivate MeleeSwing() {")
    w("\t}")
    w("")
    w("\t/** Lo que dura el golpe (tics; el de vanilla son 6). */")
    w("\tpublic static int ticks(WeaponType type) {")
    w("\t\treturn switch (type) {")
    for name in WEAPONS:
        w("\t\t\tcase %s -> %d;" % (name.upper(), ANIMATIONS[name]["ticks"]))
    w("\t\t};")
    w("\t}")
    w("")
    w("\t/** Si se coge con las dos manos al golpear (cuando la otra mano está libre). */")
    w("\tpublic static boolean twoHanded(WeaponType type) {")
    w("\t\treturn switch (type) {")
    for name in WEAPONS:
        w("\t\t\tcase %s -> %s;" % (name.upper(), "true" if ANIMATIONS[name]["two_handed"] else "false"))
    w("\t\t};")
    w("\t}")
    w("}")
    return "\n".join(out) + "\n"


def camel(name):
    parts = name.split("_")
    return parts[0] + "".join(p.title() for p in parts[1:])


def java_animations():
    out = []
    w = out.append
    w("package com.evolvedwarfare.client.render.melee;")
    w("")
    w("import com.evolvedwarfare.weapon.WeaponType;")
    w("import net.minecraft.util.math.MathHelper;")
    w("")
    w("/**")
    w(" * Las animaciones de ataque de las armas cuerpo a cuerpo (generado por tools/ew_melee.py: no lo edites a mano).")
    w(" * Fotogramas clave {progreso, valores...} sobre el progreso del golpe (0..1), con una curva suave entre ellos.")
    w(" * <ul>")
    w(" *   <li>Primera persona: " + ", ".join(FP_FIELDS) + " (movimiento en bloques en los ejes de la vista, giros en")
    w(" *   grados alrededor de la empuñadura; chain: 0 = la cadena cuelga, 1 = estirada).</li>")
    w(" *   <li>Tercera persona: " + ", ".join(TP_FIELDS) + " (grados, como ModelPart; off_* es el otro brazo,")
    w(" *   solo en las de dos manos y con esa mano libre; tilt: inclinación del arma en la mano, -90 = en la línea del")
    w(" *   brazo).</li>")
    w(" * </ul>")
    w(" */")
    w("public final class MeleeAnimations {")
    for i, f in enumerate(FP_FIELDS):
        w("\tpublic static final int FP_%s = %d;" % (f.upper(), i))
    for i, f in enumerate(TP_FIELDS):
        w("\tpublic static final int TP_%s = %d;" % (f.upper(), i))
    w("")
    for name in WEAPONS:
        a = ANIMATIONS[name]
        for kind in ("fp", "tp"):
            w("\tprivate static final float[][] %s_%s = {" % (name.upper(), kind.upper()))
            for key in a[kind]:
                w("\t\t\t{" + ", ".join(fmt(float(v)) for v in key) + "},")
            w("\t};")
    w("")
    w("\tprivate MeleeAnimations() {")
    w("\t}")
    w("")
    for kind in ("fp", "tp"):
        w("\tpublic static float[] %s(WeaponType type, float progress) {" % kind)
        w("\t\treturn sample(switch (type) {")
        for name in WEAPONS:
            w("\t\t\tcase %s -> %s_%s;" % (name.upper(), name.upper(), kind.upper()))
        w("\t\t}, progress);")
        w("\t}")
        w("")
    w("\tprivate static float[] sample(float[][] keys, float progress) {")
    w("\t\tfloat p = MathHelper.clamp(progress, 0.0F, 1.0F);")
    w("\t\tint n = keys[0].length - 1;")
    w("\t\tfloat[] out = new float[n];")
    w("\t\tfor (int i = 0; i < keys.length - 1; i++) {")
    w("\t\t\tfloat[] a = keys[i];")
    w("\t\t\tfloat[] b = keys[i + 1];")
    w("\t\t\tif (p <= b[0] || i == keys.length - 2) {")
    w("\t\t\t\tfloat t = MathHelper.clamp((p - a[0]) / Math.max(1.0E-6F, b[0] - a[0]), 0.0F, 1.0F);")
    w("\t\t\t\tt = t * t * (3.0F - 2.0F * t);")
    w("\t\t\t\tfor (int j = 0; j < n; j++) {")
    w("\t\t\t\t\tout[j] = MathHelper.lerp(t, a[j + 1], b[j + 1]);")
    w("\t\t\t\t}")
    w("\t\t\t\treturn out;")
    w("\t\t\t}")
    w("\t\t}")
    w("\t\treturn out;")
    w("\t}")
    w("}")
    return "\n".join(out) + "\n"


# ------------------------------------------------------------------ modelos de objeto
# lo mismo que minecraft:item/handheld (y item/generated para el suelo, la cabeza y los marcos)
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    for name in WEAPONS:
        check(SHAPES[name](), name)
    check(flail_chain(), "flail_chain")
    assets = os.path.join(ROOT, "src/main/resources/assets", NS)
    os.makedirs(os.path.join(assets, "textures/entity/weapons"), exist_ok=True)
    for material in MATERIALS:
        texture(material).save(os.path.join(assets, "textures/entity/weapons", material + ".png"))
        for name in WEAPONS:
            item = "%s_%s" % (material, name)
            write_json(os.path.join(assets, "models/item", item + ".json"), {
                "parent": "builtin/entity", "gui_light": "front",
                "textures": {"particle": "%s:item/%s" % (NS, item)}, "display": DISPLAY})
            write_json(os.path.join(assets, "models/item", item + "_icon.json"), {
                "parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/%s" % (NS, item)}})
    folder = os.path.join(ROOT, "src/client/java/com/evolvedwarfare/client/render/melee")
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, "MeleeModels.java"), "w", encoding="utf-8") as f:
        f.write(java_models())
    with open(os.path.join(folder, "MeleeAnimations.java"), "w", encoding="utf-8") as f:
        f.write(java_animations())
    with open(os.path.join(ROOT, "src/main/java/com/evolvedwarfare/weapon/MeleeSwing.java"), "w", encoding="utf-8") as f:
        f.write(java_swing())
    if PREVIEW:
        import ew_melee_preview
        ew_melee_preview.render_all(PREVIEW)


if __name__ == "__main__":
    main()
