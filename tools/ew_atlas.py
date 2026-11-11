"""El Atlas (fase 53): su icono (un libro de tapas de cuero con una rosa de los vientos dorada), su modelo, la receta y los
textos (del Atlas, sus mensajes, la configuración y los nombres de las estructuras que salen en el mapa).

Uso: python3 tools/ew_atlas.py [raíz del proyecto]
"""
import collections
import glob
import json
import os
import sys

from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..")
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    LEATHER, DARK, PAGES, GOLD, GOLD2 = (110, 62, 34), (70, 38, 20), (236, 226, 198), (220, 180, 70), (150, 110, 40)
    for y in range(1, 15):
        for x in range(2, 14):
            px[x, y] = LEATHER + (255,)
    for y in range(1, 15):
        px[2, y] = DARK + (255,)
        px[3, y] = DARK + (255,)
    for x in range(2, 14):
        px[x, 1] = DARK + (255,)
        px[x, 14] = DARK + (255,)
    for y in range(2, 14):
        px[14, y] = PAGES + (255,)
    px[13, 1] = px[13, 14] = DARK + (255,)
    # la rosa de los vientos
    for (x, y) in ((8, 4), (8, 5), (8, 6), (8, 8), (8, 9), (8, 10), (5, 7), (6, 7), (7, 7), (9, 7), (10, 7), (11, 7)):
        px[x, y] = GOLD + (255,)
    px[8, 7] = (255, 240, 170, 255)
    for (x, y) in ((7, 6), (9, 6), (7, 8), (9, 8)):
        px[x, y] = GOLD2 + (255,)
    for x in range(4, 13):
        px[x, 2] = GOLD2 + (255,)
        px[x, 13] = GOLD2 + (255,)
    return img


TEXTS = {
    "item.evolvedwarfare.atlas": ("Atlas", "Atlas"),
    "item.evolvedwarfare.atlas.desc": ("El mapa de lo que has explorado, en tiempo real, con las estructuras, los jugadores y los territorios. Clic derecho para abrirlo.",
                                       "A live map of what you've explored, with structures, players and territories. Right click to open it."),
    "gui.evolvedwarfare.atlas.center": ("Centrar", "Center"),
    "gui.evolvedwarfare.atlas.claim_mode": ("Reclamar", "Claim"),
    "gui.evolvedwarfare.atlas.view_mode": ("Ver mapa", "View map"),
    "gui.evolvedwarfare.atlas.name": ("Nombre del territorio", "Territory name"),
    "gui.evolvedwarfare.atlas.default_name": ("Tierras de %s", "%s's Lands"),
    "gui.evolvedwarfare.atlas.claim": ("Reclamar", "Claim"),
    "gui.evolvedwarfare.atlas.release": ("Liberar", "Release"),
    "gui.evolvedwarfare.atlas.clear": ("Limpiar", "Clear"),
    "gui.evolvedwarfare.atlas.cost": ("%s chunks · %s Glory Coins (tienes %s)", "%s chunks · %s Glory Coins (you have %s)"),
    "gui.evolvedwarfare.atlas.hint": ("Arrastra para moverte, rueda para acercar", "Drag to move, scroll to zoom"),
    "gui.evolvedwarfare.atlas.scale": ("Escala %s", "Scale %s"),
    "gui.evolvedwarfare.atlas.territory": ("%s (de %s)", "%s (%s)"),
    "gui.evolvedwarfare.atlas.other_world": ("El Atlas solo dibuja el mundo normal", "The Atlas only maps the Overworld"),
    "message.evolvedwarfare.atlas.bad_name": ("Ponle un nombre al territorio (hasta 32 letras)", "Give the territory a name (up to 32 letters)"),
    "message.evolvedwarfare.atlas.taken": ("Alguno de esos chunks ya es de otro territorio", "Some of those chunks already belong to another territory"),
    "message.evolvedwarfare.atlas.unexplored": ("Solo puedes reclamar lo que has explorado", "You can only claim what you've explored"),
    "message.evolvedwarfare.atlas.nothing": ("No hay nada nuevo que reclamar", "Nothing new to claim"),
    "message.evolvedwarfare.atlas.not_connected": ("Los chunks del territorio tienen que estar pegados entre sí", "The territory's chunks must touch each other"),
    "message.evolvedwarfare.atlas.too_many": ("Te pasas del máximo de chunks por jugador (%2$s)", "That's over the per-player chunk limit (%2$s)"),
    "message.evolvedwarfare.atlas.no_coins": ("No te llegan las Glory Coins (%s por chunk)", "Not enough Glory Coins (%s per chunk)"),
    "message.evolvedwarfare.atlas.claimed": ("Territorio «%s» reclamado (%s Glory Coins)", "Territory \"%s\" claimed (%s Glory Coins)"),
    "message.evolvedwarfare.atlas.released": ("Chunks liberados", "Chunks released"),
    "config.evolvedwarfare.category.atlas": ("Atlas", "Atlas"),
    "config.evolvedwarfare.atlasClaimCost": ("Coste por chunk", "Cost per chunk"),
    "config.evolvedwarfare.atlasClaimCost.tip": ("Glory Coins que cuesta reclamar cada chunk de territorio en el Atlas.",
                                                 "Glory Coins it costs to claim each territory chunk in the Atlas."),
    "config.evolvedwarfare.atlasMaxChunks": ("Chunks por jugador", "Chunks per player"),
    "config.evolvedwarfare.atlasMaxChunks.tip": ("Cuántos chunks de territorio puede reclamar cada jugador en total.",
                                                 "How many territory chunks each player can claim in total."),
    "structure.evolvedwarfare.mercenary_camp": ("Campamento de mercenarios", "Mercenary Camp"),
    "structure.evolvedwarfare.watchtower": ("Atalaya", "Watchtower"),
    "structure.evolvedwarfare.sodom_house": ("Casa de Sodoma", "House of Sodom"),
}
VANILLA = {
    "village_plains": ("Aldea", "Village"), "village_desert": ("Aldea del desierto", "Desert Village"),
    "village_savanna": ("Aldea de la sabana", "Savanna Village"), "village_snowy": ("Aldea nevada", "Snowy Village"),
    "village_taiga": ("Aldea de la taiga", "Taiga Village"), "desert_pyramid": ("Templo del desierto", "Desert Temple"),
    "jungle_pyramid": ("Templo de la jungla", "Jungle Temple"), "swamp_hut": ("Cabaña de la bruja", "Witch Hut"), "igloo": ("Iglú", "Igloo"),
    "mansion": ("Mansión del bosque", "Woodland Mansion"), "monument": ("Monumento oceánico", "Ocean Monument"),
    "pillager_outpost": ("Puesto de saqueadores", "Pillager Outpost"), "ancient_city": ("Ciudad antigua", "Ancient City"),
    "trail_ruins": ("Ruinas de senderos", "Trail Ruins"), "ocean_ruin_cold": ("Ruinas submarinas", "Ocean Ruins"),
    "ocean_ruin_warm": ("Ruinas submarinas", "Ocean Ruins"), "shipwreck": ("Naufragio", "Shipwreck"),
    "shipwreck_beached": ("Naufragio en la playa", "Beached Shipwreck"), "trial_chambers": ("Cámaras de desafío", "Trial Chambers"),
}
for k, v in VANILLA.items():
    TEXTS["structure.minecraft." + k] = v


def main():
    icon().save(os.path.join(ASSETS, "textures/item/atlas.png"))
    write_json(os.path.join(ASSETS, "models/item/atlas.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/atlas"}})
    write_json(os.path.join(DATA, "recipe/atlas.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:book"}, {"item": "minecraft:compass"}, {"item": "minecraft:paper"}, {"item": "minecraft:paper"}],
        "result": {"id": NS + ":atlas", "count": 1}})
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    print("el Atlas: icono, modelo, receta y %d textos" % len(TEXTS))


if __name__ == "__main__":
    main()
