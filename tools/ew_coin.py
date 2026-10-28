"""Fase 9: la Glory Coin, moneda propia del mod (textura y modelo).

Uso: python3 tools/ew_coin.py src/main/resources/assets/evolvedwarfare
"""
import json
import os
import sys

from PIL import Image

OUT = sys.argv[1]

OUTLINE = (74, 42, 6, 255)
RIM_DARK = (150, 96, 14, 255)
RIM = (205, 144, 28, 255)
FACE = (240, 190, 52, 255)
FACE_LIGHT = (255, 224, 110, 255)
SHINE = (255, 248, 200, 255)
LAUREL = (166, 108, 16, 255)
GEM = (178, 34, 52, 255)
GEM_LIGHT = (232, 86, 96, 255)

# 16x16: moneda redonda de oro con borde, una corona de laurel grabada y un rubí en el centro
ART = [
    "................",
    ".....oooooo.....",
    "...ooRRRRRRoo...",
    "..oRRFFFFFFRDo..",
    "..oRFSFFFFFFDo..",
    ".oRFSLFFFFLFFDo.",
    ".oRFLFFggFFLFDo.",
    ".oRFLFgGGgFLFDo.",
    ".oRFLFgGgg.LFDo.",
    ".oRFFLFggFLFFDo.",
    ".oRFFFLFFLFFFDo.",
    "..oRFFFLLFFFDo..",
    "..oDRFFFFFFDDo..",
    "...ooDDDDDDoo...",
    ".....oooooo.....",
    "................",
]
COLORS = {"o": OUTLINE, "R": RIM, "D": RIM_DARK, "F": FACE, "S": SHINE, "L": LAUREL, "g": GEM, "G": GEM_LIGHT}


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ART):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch == ".":
                if 2 <= y <= 13 and ART[y][x - 1] not in "." and ART[y][x + 1 if x < 15 else x] not in ".":
                    img.putpixel((x, y), FACE)  # hueco interior
                continue
            img.putpixel((x, y), COLORS[ch])
    # brillo suave arriba a la izquierda de la cara
    for x, y in ((5, 3), (6, 3), (4, 5)):
        img.putpixel((x, y), FACE_LIGHT)
    path = os.path.join(OUT, "textures/item/glory_coin.png")
    img.save(path)
    with open(os.path.join(OUT, "models/item/glory_coin.json"), "w", encoding="utf-8") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "evolvedwarfare:item/glory_coin"}}, f, indent=2)
        f.write("\n")
    img.resize((256, 256), Image.NEAREST).save(sys.argv[2]) if len(sys.argv) > 2 else None
    print("ok", path)


if __name__ == "__main__":
    main()
