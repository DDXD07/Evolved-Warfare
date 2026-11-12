"""La gente de los pueblos (fases 58 en adelante): los textos de los oficios, los caracteres, contratar y (luego) la
rutina, las charlas y el diálogo.

Uso: python3 tools/ew_town.py [raíz del proyecto]
"""
import collections
import glob
import json
import os
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..")
NS = "evolvedwarfare"
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)

# oficio: (masculino, femenino, inglés)
JOBS = {
    "laborer": ("peón", "peona", "laborer"),
    "farmer": ("campesino", "campesina", "farmer"),
    "baker": ("panadero", "panadera", "baker"),
    "blacksmith": ("herrero", "herrera", "blacksmith"),
    "carpenter": ("carpintero", "carpintera", "carpenter"),
    "lumberjack": ("leñador", "leñadora", "lumberjack"),
    "miner": ("minero", "minera", "miner"),
    "hunter": ("cazador", "cazadora", "hunter"),
    "fisher": ("pescador", "pescadora", "fisher"),
    "shepherd": ("pastor", "pastora", "shepherd"),
    "tailor": ("sastre", "sastra", "tailor"),
    "mason": ("cantero", "cantera", "mason"),
    "innkeeper": ("tabernero", "tabernera", "innkeeper"),
    "merchant": ("mercader", "mercader", "merchant"),
    "guard": ("guardia", "guardia", "guard"),
    "mercenary": ("mercenario", "mercenaria", "mercenary"),
}
PERSONALITIES = {
    "cheerful": ("alegre", "alegre", "cheerful"),
    "grumpy": ("gruñón", "gruñona", "grumpy"),
    "fearful": ("miedoso", "miedosa", "fearful"),
    "brave": ("valiente", "valiente", "brave"),
    "lazy": ("perezoso", "perezosa", "lazy"),
    "kind": ("amable", "amable", "kind"),
    "curious": ("curioso", "curiosa", "curious"),
}

TEXTS = {
    "npc.evolvedwarfare.profile": ("%s, %s", "%s, %s"),
    "message.evolvedwarfare.for_hire": ("%s (%s) busca trabajo. Contrátalo con un Contrato de reclutamiento.",
                                        "%s (%s) is looking for work. Hire them with a Recruitment Contract."),
    "message.evolvedwarfare.hire.coins": ("Para contratarlo hacen falta %s Glory Coins, además del contrato.",
                                          "Hiring them takes %s Glory Coins as well as the contract."),
    "message.evolvedwarfare.city_guard.hello": ("«Paz y orden en la ciudad, forastero.» (Con un contrato y 15 Glory Coins dejaría su puesto por ti.)",
                                                "\"Keep the peace in our city, stranger.\" (With a contract and 15 Glory Coins they'd leave their post for you.)"),
}
for table, prefix in ((JOBS, "npc.evolvedwarfare.job."), (PERSONALITIES, "npc.evolvedwarfare.personality.")):
    for key, (m, f, en) in table.items():
        TEXTS[prefix + key] = (m, en)
        TEXTS[prefix + key + ".f"] = (f, en)


def write_json(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for key, (es, en) in TEXTS.items():
            lang[key] = en if english else es
        write_json(path, lang)
    print("la gente de los pueblos: %d textos" % len(TEXTS))


if __name__ == "__main__":
    main()
