"""Genera imágenes de vista previa: galería de edificios, una ciudad completa, campamento y torre."""
import sys

from PIL import Image

import ew_jigsaw_sim as SIM
import ew_structures as E
from ew_render import render, template_blocks, template_entities

OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp"

NAMES = {
    "city/houses/cottage": "Casita",
    "city/houses/townhouse": "Casa de dos plantas",
    "city/houses/barracks": "Cuartel",
    "city/houses/tavern": "Taberna",
    "city/houses/blacksmith": "Herrería",
    "city/houses/guard_post": "Torre de guardia",
    "city/houses/market_stalls": "Puestos de mercado",
    "city/houses/garden": "Huerto",
    "city/town_centers/plaza_fountain": "Plaza de la fuente",
    "city/town_centers/training_yard": "Patio de armas",
    "watchtower/tower": "Torre de vigilancia",
}


def grid(images, cols, pad=10, bg=(242, 238, 228)):
    rows = [images[i:i + cols] for i in range(0, len(images), cols)]
    W = max(sum(im.width for im in r) + pad * (len(r) + 1) for r in rows)
    H = sum(max(im.height for im in r) for r in rows) + pad * (len(rows) + 1)
    out = Image.new("RGB", (W, H), bg)
    y = pad
    for r in rows:
        x = pad
        for im in r:
            out.paste(im, (x, y + max(i.height for i in r) - im.height))
            x += im.width + pad
        y += max(im.height for im in r) + pad
    return out


def gallery():
    t = E.build_all()
    ims = []
    for key, title in NAMES.items():
        tpl = t[key]
        ground = -1 if "/houses/" in key else 0
        blocks = template_blocks(tpl)
        ents = template_entities(tpl)
        # dónde pueden aparecer reclutas (marcados en azul)
        for (x, y, z), orient, be in tpl.jigsaws():
            if be["name"] == f"{E.NS}:npc":
                ents.append((f"{E.NS}:recruit", (x, y + 1, z)))
        if "/houses/" in key or key.startswith("watchtower"):
            # girar 180º para que la fachada (que mira al oeste) quede de cara a la cámara
            blocks = {(-x, y, -z): st for (x, y, z), st in blocks.items()}
            ents = [(i, (-x, y, -z)) for i, (x, y, z) in ents]
        ims.append(render(blocks, ents, scale=11, ground=ground, margin=2, title=title))
    return grid(ims, 4)


def whole(structure, seed, scale):
    pcs = SIM.generate(structure, seed)
    blocks, ents = SIM.world_blocks(pcs)
    return render(blocks, ents, scale=scale, ground=0, margin=4), pcs


if __name__ == "__main__":
    gallery().save(f"{OUT}/ew_edificios.png")
    seed = int(sys.argv[2]) if len(sys.argv) > 2 else 7
    img, pcs = whole("city", seed, 4)
    img.save(f"{OUT}/ew_ciudad.png")
    c, ext = SIM.summary(pcs)
    print("ciudad", seed, ext, sum(v for k, v in c.items() if "/houses/" in k), "edificios", c["npc/recruit"], "NPCs")
    # detalle de la ciudad: recorte alrededor del centro
    pcs = SIM.generate("city", seed)
    blocks, ents = SIM.world_blocks(pcs)
    near = {p: st for p, st in blocks.items() if -10 <= p[0] <= 45 and -10 <= p[2] <= 45}
    near_e = [(i, p) for i, p in ents if -10 <= p[0] <= 45 and -10 <= p[2] <= 45]
    render(near, near_e, scale=10, ground=0, margin=0).save(f"{OUT}/ew_ciudad_detalle.png")
    camps = [whole("mercenary_camp", s, 12)[0] for s in (3, 11)]
    grid(camps, 2).save(f"{OUT}/ew_campamentos.png")
    print("ok")
