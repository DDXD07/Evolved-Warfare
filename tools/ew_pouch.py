"""Fase 9: la Bolsa de Gloria (textura y modelo).

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


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ART):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), COLORS[ch])
    img.save(os.path.join(OUT, "textures/item/glory_pouch.png"))
    with open(os.path.join(OUT, "models/item/glory_pouch.json"), "w", encoding="utf-8") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "evolvedwarfare:item/glory_pouch"}}, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
