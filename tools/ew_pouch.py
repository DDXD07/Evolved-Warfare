"""Fase 9 y 10: la Bolsa de Gloria y la bolsa de munición (texturas y modelos).

Uso: python3 tools/ew_pouch.py src/main/resources/assets/evolvedwarfare
"""
import json
import os
import sys

from PIL import Image

OUT = sys.argv[1]

COLORS = {
    "o": (48, 26, 10, 255),      # contorno
    "D": (104, 60, 28, 255),     # cuero en sombra
    "L": (142, 86, 42, 255),     # cuero
    "H": (186, 124, 66, 255),    # cuero con luz
    "W": (214, 160, 98, 255),    # brillo
    "G": (170, 112, 20, 255),    # cordón de oro (sombra)
    "Y": (236, 184, 50, 255),    # cordón de oro
    "C": (240, 190, 52, 255),    # moneda
    "c": (255, 230, 120, 255),   # moneda con luz
    "R": (178, 34, 52, 255),     # rubí de la moneda
}

# 16x16: bolsa de cuero atada con un cordón de oro y una Glory Coin cosida delante
ART = [
    "................",
    "......oooo......",
    ".....oHWHLo.....",
    "......oLLo......",
    "....ooGYYGoo....",
    "...oYGooooGYo...",
    "....oDLLLLDo....",
    "...oDLHLLLLDo...",
    "..oDLHLLLLLLDo..",
    ".oDLHLLoooLLLDo.",
    ".oDLHLoCcCoLLDo.",
    ".oDLLLoCRCoLLDo.",
    ".oDDLLoCCCoLDDo.",
    "..oDDLLoooLDDo..",
    "...ooDDDDDDoo...",
    ".....oooooo.....",
]


# 16x16: cartuchera de cuero negro con su correa, chapa de latón en la tapa y remaches
AMMO_COLORS = {
    "o": (20, 18, 16, 255),      # contorno
    "S": (122, 84, 46, 255),     # correa
    "s": (88, 58, 30, 255),      # correa en sombra
    "F": (54, 48, 44, 255),      # tapa
    "H": (92, 84, 78, 255),      # tapa con luz
    "D": (40, 36, 33, 255),      # caja
    "B": (156, 114, 38, 255),    # latón en sombra
    "Y": (226, 182, 72, 255),    # latón
    "K": (168, 158, 136, 255),   # remaches
}
AMMO_ART = [
    "................",
    "....oooooooo....",
    "...oSso..osSo...",
    "..oSso....osSo..",
    ".oooooooooooooo.",
    ".oHHFFFFFFFFFFo.",
    ".oHFFFFBBFFFFFo.",
    ".oFFFFBYYBFFFFo.",
    ".oFFFFBYYBFFFFo.",
    ".oFFFFFBBFFFFFo.",
    ".oooooooooooooo.",
    ".oDKDDDDDDDDKDo.",
    ".oDDDDDDDDDDDDo.",
    ".oDDDDDDDDDDDDo.",
    "..oooooooooooo..",
    "................",
]


def draw(art, colors, name):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(art):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), colors[ch])
    img.save(os.path.join(OUT, "textures/item/%s.png" % name))
    with open(os.path.join(OUT, "models/item/%s.json" % name), "w", encoding="utf-8") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "evolvedwarfare:item/" + name}}, f, indent=2)
        f.write("\n")


def main():
    draw(ART, COLORS, "glory_pouch")
    draw(AMMO_ART, AMMO_COLORS, "ammo_pouch")


if __name__ == "__main__":
    main()
