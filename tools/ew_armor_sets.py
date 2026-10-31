"""Los diseños de las 12 armaduras de las facciones (4 por facción, de menos a más rango). Coordenadas del modelo de
jugador de Minecraft (en píxeles, y hacia abajo), cada parte respecto a su pivote:
  cabeza (-4,-8,-4) 8x8x8 · cuerpo (-4,0,-2) 8x12x4 · brazo derecho (-3,-2,-2) 4x12x4 · pierna derecha (-2,0,-2) 4x12x4
La parte izquierda se hace sola reflejando la derecha. Holgura (dil): casco, peto y botas 1.0 como en vanilla;
pantalones 0.5.
"""
from ew_armor import Box as B, Node as N

# ------------------------------------------------------------------ piezas que se repiten


def helm(mat, dil=1.0, **kw):
    return B(mat, -4, -8, -4, 8, 8, 8, dil, **kw)


def torso(mat, dil=1.0, **kw):
    return B(mat, -4, 0, -2, 8, 12, 4, dil, **kw)


def sleeve(mat, length=12, dil=1.0, **kw):
    return B(mat, -3, -2, -2, 4, length, 4, dil, **kw)


def ring(r, ry=0.1):
    """Holgura solo hacia los lados (para anillos, puños y cintos que no deben engordar hacia arriba y abajo)."""
    return (r, ry, r)


def cuff(mat, y, h=2, dil=1.3, **kw):
    """Puño o brazal alrededor del brazo (y: altura en el brazo, 0 = hombro)."""
    return B(mat, -3, y - 2, -2, 4, h, 4, ring(dil), **kw)


def pants(mat, length=12, dil=0.5, **kw):
    return B(mat, -2, 0, -2, 4, length, 4, dil, **kw)


def hips(mat, **kw):
    return B(mat, -4, 7, -2, 8, 5, 4, 0.5, **kw)


def boot(mat, h=5, dil=1.0, **kw):
    return B(mat, -2, 12 - h, -2, 4, h, 4, dil, **kw)


def belt(mat, y=9, h=2, dil=1.25, **kw):
    return B(mat, -4, y, -2, 8, h, 4, ring(dil, 0.15), **kw)


def mantle(mat, **kw):
    """Manto o cuello de piel sobre los hombros (en el cuerpo)."""
    return B(mat, -5, -1, -3, 10, 3, 6, 0.4, **kw)


def pauldron(mat, h=3, dil=0.35, **kw):
    """Hombrera (en el brazo derecho; la izquierda sale sola)."""
    return B(mat, -4, -3, -3, 5, h, 6, dil, **kw)


def cape(mat, length=14, tilt=6, **kw):
    """Capa a la espalda, algo separada y caída hacia atrás para que las piernas no la atraviesen al andar."""
    return N(B(mat, -4, 0, 0, 8, length, 1, 0.1, **kw), pivot=(0, 0.5, 3.2), rot=(tilt, 0, 0))


def horn(mat, tip):
    """Cuerno curvado (lado derecho): tres trozos, cada uno más fino y más hacia arriba y adelante."""
    return N(B(mat, -4, -1, -1, 4, 2, 2, 0.0),
             children=[N(B(mat, -3, -1, -1, 3, 2, 2, -0.2),
                         children=[N(B(tip, -3, -0.5, -0.5, 3, 1, 1, 0.0), pivot=(-2.8, 0, 0), rot=(0, -12, 40))],
                         pivot=(-3.8, 0, 0), rot=(0, -10, 32))],
             pivot=(-4.6, -6.2, 0.5), rot=(0, -12, 12))


def tricorn(mat, trim, front_point=-7.2, back=5.4, half=6.0, height=3, y=-8.6):
    """Sombrero de tres picos: la copa y tres alas levantadas que forman un triángulo (pico delante)."""
    import math
    nodes = [N(B(mat, -4, -12.2, -4, 8, 4, 8, 0.35, decals=[("band", "sides", 3, 4, trim)]))]
    # ala de atrás
    nodes.append(N(B(mat, -half, -height, 0, int(2 * half), height, 1, 0.0, decals=[("band", "all", 0, 1, trim)]),
                   pivot=(0, y, back), rot=(-16, 0, 0)))
    # alas de delante: del pico a cada esquina de atrás
    for side in (-1, 1):
        dx, dz = side * half, back - front_point
        length = math.hypot(dx, dz)
        yaw = math.degrees(math.atan2(-dz, dx))
        nodes.append(N(B(mat, 0, -height, -0.5, int(round(length)), height, 1, 0.0, decals=[("band", "all", 0, 1, trim)]),
                       pivot=(0, y, front_point), rot=(side * 16, yaw, 0)))
    return nodes


# ------------------------------------------------------------------ vikingos

V_MATS = {
    "fur": {"kind": "fur", "c": (122, 92, 62)},
    "wolf": {"kind": "fur", "c": (132, 128, 120)},
    "bear": {"kind": "fur", "c": (92, 66, 44)},
    "wool_blue": {"kind": "cloth", "c": (52, 78, 122)},
    "wool_red": {"kind": "cloth", "c": (128, 42, 36)},
    "linen": {"kind": "cloth", "c": (176, 160, 128)},
    "leather": {"kind": "leather", "c": (112, 76, 46)},
    "dark_leather": {"kind": "leather", "c": (78, 52, 34)},
    "iron": {"kind": "plate", "c": (156, 158, 162)},
    "dark_iron": {"kind": "plate", "c": (100, 102, 108)},
    "chain": {"kind": "chain", "c": (132, 134, 140)},
    "scale": {"kind": "scale", "c": (128, 130, 136)},
    "lamellar": {"kind": "lamellar", "c": (118, 120, 126), "lace": (98, 66, 40)},
    "amber": {"kind": "flat", "c": (226, 168, 40)},
    "teeth": {"kind": "flat", "c": (236, 232, 220)},
    "wolf_dark": {"kind": "fur", "c": (92, 88, 82)},
    "gilded_scale": {"kind": "scale", "c": (150, 140, 118)},
    "bronze": {"kind": "gold", "c": (186, 140, 62)},
    "gold": {"kind": "gold", "c": (214, 170, 66)},
    "horn": {"kind": "horn", "c": (226, 214, 184)},
    "horn_tip": {"kind": "horn", "c": (150, 136, 110)},
    "rope": {"kind": "rope", "c": (150, 120, 80)},
    "black": {"kind": "flat", "c": (24, 22, 24)},
    "nose": {"kind": "flat", "c": (30, 26, 26)},
}

VIKING_HUNTER = {
    "id": "viking_hunter", "faction": "vikings", "tier": 1,
    "names": {"es": ["Capucha de lobo", "Chaleco de pieles", "Calzas de cazador", "Botas de pieles", "Pieles de cazador"],
              "en": ["Wolf Pelt Hood", "Fur Vest", "Hunter's Breeches", "Fur Boots", "Hunter's Furs"]},
    "materials": V_MATS,
    "recipe": {"X": "minecraft:leather", "A": "minecraft:rabbit_hide"},
    "parts": {
        # la cabeza de lobo encima, con las orejas y el hocico sobre la frente; la piel cae por la nuca
        "head": N(helm("wolf", 1.0, decals=[("hole", "N", 1, 3, 6, 5), ("fringe", "sides", 1)]),
                  B("wolf", -3, -10, -3, 6, 2, 5, 0.2),
                  B("wolf", -2.5, -10.4, -7.4, 5, 3, 4, 0.0, decals=[("px", "N", [(2, 1, "nose"), (0, 2, "teeth"), (4, 2, "teeth")]),
                                                                    ("rect", "N", 1, 0, 3, 1, "wolf_dark"),
                                                                    ("px", "U", [(0, 3, "amber"), (4, 3, "amber")])]),
                  B("wolf_dark", -4, -12, -2, 2, 3, 1, 0.0, decals=[("hole", "N", 0, 0, 1, 1), ("hole", "S", 1, 0, 1, 1)]),
                  B("wolf_dark", 2, -12, -2, 2, 3, 1, 0.0, decals=[("hole", "N", 1, 0, 1, 1), ("hole", "S", 0, 0, 1, 1)]),
                  B("wolf", -4, -4, 4, 8, 6, 1, 0.6, decals=[("fringe", "sides", 2)])),
        "body": N(torso("wool_blue", 0.8),
                  torso("fur", 1.1, decals=[("hole", "N", 3, 0, 2, 12), ("fringe", "sides", 1), ("band", "sides", 9, 10, "dark_leather")]),
                  mantle("wolf", decals=[("fringe", "sides", 1)])),
        "right_arm": N(sleeve("wool_blue", 12, 0.8), cuff("dark_leather", 7, 3, 1.05)),
        "waist": N(hips("leather")),
        "right_leg": N(pants("linen", decals=[("diag", "N", 0, 0.5, 1, "dark_leather"), ("diag", "S", 0, 0.5, 1, "dark_leather"),
                                              ("diag", "W", 0, 0.5, 1, "dark_leather"), ("diag", "E", 0, 0.5, 1, "dark_leather"),
                                              ("diag", "N", 3, -0.5, 1, "dark_leather"), ("diag", "S", 3, -0.5, 1, "dark_leather")])),
        "right_boot": N(boot("fur", 5, decals=[("fringe", "sides", 1)]), B("dark_leather", -2, 11, -2, 4, 1, 4, ring(1.05, 1.05))),
    },
}

VIKING_WARRIOR = {
    "id": "viking_warrior", "faction": "vikings", "tier": 2,
    "names": {"es": ["Yelmo de nasal", "Cota vikinga", "Calzas con vendas", "Botas de cuero vikingas", "Cota de guerrero vikingo"],
              "en": ["Nasal Helm", "Viking Byrnie", "Wrapped Trousers", "Viking Leather Boots", "Viking Warrior's Mail"]},
    "materials": V_MATS,
    "recipe": {"X": "minecraft:chain", "A": "minecraft:rabbit_hide"},
    "parts": {
        # yelmo cónico con nasal y banda de bronce
        "head": N(B("iron", -4, -8, -4, 8, 5, 8, 1.0, decals=[("band", "sides", 3, 4, "bronze")]),
                  B("iron", -3, -10, -3, 6, 2, 6, 0.6),
                  B("iron", -1.5, -11.2, -1.5, 3, 1, 3, 0.2),
                  B("iron", -0.5, -5, -5.6, 1, 4, 1, 0.0)),
        "body": N(torso("chain"), belt("leather", decals=[("sprite", "N", "buckle", "c", 0, "bronze")]),
                  B("wool_red", -4, 0, -2, 8, 3, 4, 1.15)),
        "right_arm": N(sleeve("chain", 8), sleeve("wool_red", 12, 0.8), cuff("leather", 8, 2, 1.1)),
        "right_skirt": N(B("chain", -2, 0, -2, 4, 3, 4, 1.0)),
        "waist": N(hips("wool_red")),
        "right_leg": N(pants("wool_red", decals=[("band", "sides", 4, 5, "linen"), ("band", "sides", 6, 7, "linen"),
                                                 ("band", "sides", 8, 9, "linen")])),
        "right_boot": N(boot("leather", 5, decals=[("band", "sides", 0, 1, "dark_leather")])),
    },
}

VIKING_HUSCARL = {
    "id": "viking_huscarl", "faction": "vikings", "tier": 3,
    "names": {"es": ["Yelmo de anteojos", "Coraza de láminas", "Calzas de malla", "Grebas de huscarle", "Armadura de huscarle"],
              "en": ["Spectacle Helm", "Lamellar Cuirass", "Mail Chausses", "Huscarl Greaves", "Huscarl Armor"]},
    "materials": V_MATS,
    "recipe": {"X": "minecraft:iron_ingot", "A": "minecraft:rabbit_hide"},
    "parts": {
        # yelmo redondo con la máscara de anteojos y la malla colgando por los lados y la nuca
        "head": N(B("iron", -4, -8, -4, 8, 5, 8, 1.0, decals=[("band", "sides", 4, 5, "dark_iron")]),
                  B("iron", -0.5, -9.4, -4.5, 1, 1, 9, 0.0),
                  B("dark_iron", -4, -5, -5.6, 8, 3, 1, 0.0, decals=[("rect", "N", 1, 1, 2, 1, "black"), ("rect", "N", 5, 1, 2, 1, "black")]),
                  B("dark_iron", -0.5, -2, -5.6, 1, 2, 1, 0.0),
                  B("chain", -4, -3, -4, 8, 5, 8, 1.1, decals=[("hole", "N", 1, 0, 6, 5), ("fringe", "sides", 1)])),
        "body": N(torso("chain"), B("lamellar", -4, 0, -2, 8, 9, 4, 1.15), belt("dark_leather", 9, 2, 1.3,
                  decals=[("sprite", "N", "buckle", "c", 0, "bronze")])),
        "right_arm": N(sleeve("chain"), N(pauldron("lamellar", 4), rot=(0, 0, 8)), cuff("iron", 8, 3, 1.15)),
        "right_skirt": N(B("lamellar", -2, -1, -2, 4, 5, 4, 1.15)),
        "waist": N(hips("chain")),
        "right_leg": N(pants("chain")),
        "right_boot": N(boot("dark_leather", 6), B("iron", -2, 6, -3.3, 4, 4, 1, 0.0)),
    },
}

JARL_HORN = horn("horn", "horn_tip")

VIKING_JARL = {
    "id": "viking_jarl", "faction": "vikings", "tier": 4,
    "names": {"es": ["Yelmo del jarl", "Cota escamada del jarl", "Calzas del jarl", "Botas del jarl", "Armadura del jarl"],
              "en": ["Jarl's Helm", "Jarl's Scale Hauberk", "Jarl's Chausses", "Jarl's Boots", "Jarl's Armor"]},
    "materials": V_MATS,
    "recipe": {"X": "minecraft:iron_ingot", "A": "minecraft:rabbit_hide", "D": "minecraft:diamond"},
    "parts": {
        # yelmo con bandas de oro, nasal dorado y cuernos curvados
        "head": N(B("iron", -4, -8, -4, 8, 5, 8, 1.0, decals=[("band", "sides", 0, 1, "gold"), ("band", "sides", 4, 5, "gold"),
                                                             ("vband", "N", 3, 5, "gold")]),
                  B("gold", -0.5, -9.4, -4.5, 1, 1, 9, 0.0),
                  B("gold", -0.5, -5, -5.6, 1, 4, 1, 0.0),
                  B("chain", -4, -3, -4, 8, 4, 8, 1.1, decals=[("hole", "N", 1, 0, 6, 4)]),
                  children=[JARL_HORN, JARL_HORN.mirrored()]),
        "body": N(torso("chain"), B("gilded_scale", -4, 0, -2, 8, 10, 4, 1.15, decals=[("trim", "N", "gold")]),
                  belt("dark_leather", 9, 2, 1.3, decals=[("sprite", "N", "buckle", "c", 0, "gold")]),
                  mantle("bear", decals=[("fringe", "sides", 1), ("sprite", "N", "brooch", 1, 0, "gold")]),
                  children=[cape("bear", 13, 7, decals=[("fringe", "sides", 2)])]),
        "right_arm": N(sleeve("wool_red"), N(pauldron("gilded_scale", 4), rot=(0, 0, 8)), cuff("gold", 8, 1, 1.15),
                       cuff("gold", 5, 1, 1.15)),
        "right_skirt": N(B("gilded_scale", -2, -1, -2, 4, 6, 4, 1.15, decals=[("band", "sides", 5, 6, "gold")])),
        "waist": N(hips("wool_red")),
        "right_leg": N(pants("chain")),
        "right_boot": N(boot("iron", 5), B("bear", -2, 6, -2, 4, 2, 4, ring(1.35, 0.3), decals=[("fringe", "sides", 1)])),
    },
}

# ------------------------------------------------------------------ bandidos

B_MATS = {
    "cloth": {"kind": "cloth", "c": (66, 60, 56)},
    "cloth_brown": {"kind": "cloth", "c": (94, 74, 54)},
    "linen": {"kind": "cloth", "c": (160, 144, 114)},
    "patch": {"kind": "cloth", "c": (122, 104, 74)},
    "red": {"kind": "cloth", "c": (126, 30, 28)},
    "black_cloth": {"kind": "cloth", "c": (34, 32, 34)},
    "leather": {"kind": "leather", "c": (88, 60, 40)},
    "dark_leather": {"kind": "leather", "c": (54, 40, 32)},
    "studded": {"kind": "studded", "c": (78, 54, 38), "stud": (156, 156, 160)},
    "brigandine": {"kind": "studded", "c": (104, 30, 28), "stud": (196, 168, 100)},
    "rust": {"kind": "plate", "c": (118, 112, 106), "rust": True},
    "chain": {"kind": "chain", "c": (108, 104, 100)},
    "black_plate": {"kind": "plate", "c": (58, 58, 64), "rivets": True},
    "steel": {"kind": "plate", "c": (146, 148, 154)},
    "bone": {"kind": "flat", "c": (214, 206, 184)},
    "crest": {"kind": "fur", "c": (146, 28, 26)},
    "rope": {"kind": "rope", "c": (132, 112, 80)},
    "brass": {"kind": "gold", "c": (178, 142, 74)},
    "black": {"kind": "flat", "c": (16, 16, 18)},
}

BANDIT_OUTLAW = {
    "id": "bandit_outlaw", "faction": "bandits", "tier": 1,
    "names": {"es": ["Capucha de forajido", "Chaleco de forajido", "Calzas remendadas", "Botas vendadas", "Ropas de forajido"],
              "en": ["Outlaw's Hood", "Outlaw's Vest", "Patched Trousers", "Wrapped Boots", "Outlaw's Garb"]},
    "materials": B_MATS,
    "recipe": {"X": "minecraft:leather", "A": "minecraft:black_dye"},
    "parts": {
        # capucha con el pañuelo rojo tapando la cara y el nudo atrás
        "head": N(helm("cloth", 1.0, decals=[("hole", "N", 1, 2, 6, 6)]),
                  B("cloth", -2, -9.5, 2, 4, 3, 4, 0.4),
                  B("red", -4, -3, -4, 8, 3, 1, (1.1, 0.3, 1.15), decals=[("band", "N", 1, 2, (104, 24, 22))]),
                  B("red", -1, -5, 4.6, 2, 2, 1, 0.3),
                  N(B("red", -0.5, 0, -0.5, 1, 3, 1, 0.0), pivot=(-0.6, -4, 5.9), rot=(25, 0, 12)),
                  N(B("red", -0.5, 0, -0.5, 1, 3, 1, 0.0), pivot=(0.6, -4, 5.9), rot=(18, 0, -10))),
        "body": N(torso("linen", 0.8),
                  torso("leather", 1.05, decals=[("hole", "N", 3, 0, 2, 12), ("diag", "N", 0, 0.55, 2, "dark_leather")]),
                  belt("rope", 9, 1, 1.2)),
        "right_arm": N(sleeve("linen", 7, 0.8), cuff("cloth_brown", 5, 1, 0.95)),
        "waist": N(hips("cloth_brown")),
        "right_leg": N(pants("cloth_brown", decals=[("rect", "N", 1, 3, 2, 2, "patch"), ("rect", "W", 1, 7, 2, 2, "patch")])),
        "right_boot": N(boot("linen", 5, decals=[("diag", "sides", 0, 1, 1, "rope")]),
                        B("dark_leather", -2, 11, -2, 4, 1, 4, ring(1.05, 1.05))),
    },
}

BANDIT_HIGHWAYMAN = {
    "id": "bandit_highwayman", "faction": "bandits", "tier": 2,
    "names": {"es": ["Gorro de salteador", "Jubón tachonado", "Calzas de cuero", "Botas altas de salteador", "Cuero de salteador"],
              "en": ["Highwayman's Cap", "Studded Doublet", "Leather Trousers", "Highwayman's Tall Boots", "Highwayman's Leathers"]},
    "materials": B_MATS,
    "recipe": {"X": "minecraft:chain", "A": "minecraft:black_dye"},
    "parts": {
        # gorro de cuero con orejeras y el embozo negro
        "head": N(B("leather", -4, -8, -4, 8, 3, 8, 1.0, decals=[("band", "sides", 2, 3, "dark_leather")]),
                  B("leather", -4, -5, -3, 2, 5, 6, 1.1),
                  B("leather", 2, -5, -3, 2, 5, 6, 1.1),
                  B("black_cloth", -4, -3, -4, 8, 3, 1, (1.15, 0.3, 1.2)),
                  N(B("black_cloth", -0.5, 0, -0.5, 1, 4, 1, 0.0), pivot=(3.0, -3, 5.4), rot=(15, 0, -14))),
        "body": N(torso("studded"),
                  B("leather", -4, -1, -2, 8, 2, 4, (1.35, 0.4, 1.35)),
                  belt("red", 9, 2, 1.2),
                  N(B("red", 0, 0, -0.5, 1, 4, 1, 0.0), pivot=(4.9, 10.5, -1), rot=(0, 0, -12))),
        "right_arm": N(sleeve("leather"), pauldron("studded", 3), cuff("dark_leather", 8, 3, 1.15)),
        "waist": N(hips("leather")),
        "right_leg": N(pants("leather"), B("studded", -2, 4, -2.9, 4, 3, 1, 0.0)),
        "right_boot": N(boot("dark_leather", 7), B("leather", -2, 5, -2, 4, 2, 4, ring(1.3, 0.2))),
    },
}

BANDIT_BRIGAND = {
    "id": "bandit_brigand", "faction": "bandits", "tier": 3,
    "names": {"es": ["Capacete oxidado", "Brigantina", "Calzas de malla oxidada", "Botas herradas", "Brigantina de bandolero"],
              "en": ["Rusted Kettle Hat", "Brigandine", "Rusted Mail Leggings", "Hobnailed Boots", "Brigand's Brigandine"]},
    "materials": B_MATS,
    "recipe": {"X": "minecraft:iron_ingot", "A": "minecraft:black_dye"},
    "parts": {
        # capacete de ala ancha sobre una almófar de malla
        "head": N(B("rust", -4, -8, -4, 8, 4, 8, 1.0),
                  B("rust", -7, -4.6, -7, 14, 1, 14, 0.0),
                  B("chain", -4, -4, -4, 8, 4, 8, 1.08, decals=[("hole", "N", 1, 0, 6, 4)])),
        "body": N(torso("chain"), B("brigandine", -4, 0, -2, 8, 10, 4, 1.15),
                  belt("dark_leather", 9, 2, 1.3, decals=[("sprite", "N", "buckle", "c", 0, "brass")])),
        "right_arm": N(sleeve("chain"), pauldron("rust", 3), cuff("rust", 8, 3, 1.12)),
        "right_skirt": N(B("chain", -2, 0, -2, 4, 3, 4, 1.0)),
        "waist": N(hips("chain")),
        "right_leg": N(pants("chain")),
        "right_boot": N(boot("dark_leather", 6), B("rust", -2, 10, -3.3, 4, 2, 1, 0.0)),
    },
}

BANDIT_WARLORD = {
    "id": "bandit_warlord", "faction": "bandits", "tier": 4,
    "names": {"es": ["Yelmo del caudillo", "Coraza del caudillo", "Quijotes negros", "Escarpes negros", "Armadura del caudillo"],
              "en": ["Warlord's Great Helm", "Warlord's Cuirass", "Black Cuisses", "Black Sabatons", "Warlord's Armor"]},
    "materials": B_MATS,
    "recipe": {"X": "minecraft:iron_ingot", "A": "minecraft:black_dye", "D": "minecraft:diamond"},
    "parts": {
        # yelmo cerrado negro con la rendija, una calavera y la cresta roja
        "head": N(helm("black_plate", 1.1, decals=[("band", "N", 3, 4, "black"), ("sprite", "N", "skull_small", "c", 0, "bone"),
                                                   ("px", "N", [(2, 6, "black"), (5, 6, "black"), (3, 7, "black"), (4, 7, "black")])]),
                  B("crest", -0.5, -12.2, -4, 1, 3, 9, 0.0, decals=[("fringe", "sides", 1)])),
        "body": N(torso("chain"), B("black_plate", -4, 0, -2, 8, 10, 4, 1.15, decals=[("sprite", "N", "skull", "c", 2, "bone")]),
                  belt("dark_leather", 9, 2, 1.3, decals=[("sprite", "N", "buckle", "c", 0, "steel")]),
                  cape("red", 14, 6, decals=[("fringe", "sides", 3)])),
        "right_arm": N(sleeve("chain"), pauldron("black_plate", 4), cuff("black_plate", 8, 4, 1.12),
                       B("bone", -3, -5.3, -1, 1, 2, 1, 0.0), B("bone", -1, -5.3, -1, 1, 2, 1, 0.0)),
        "right_skirt": N(B("black_plate", -2, -1, -2, 4, 4, 4, 1.15)),
        "waist": N(hips("chain")),
        "right_leg": N(pants("black_plate"), B("black_plate", -2, 4, -3.1, 4, 3, 1, 0.0)),
        "right_boot": N(boot("black_plate", 5), B("black_plate", -2, 10, -3.8, 4, 2, 2, 0.0)),
    },
}

# ------------------------------------------------------------------ piratas

P_MATS = {
    "red": {"kind": "cloth", "c": (152, 32, 32)},
    "red_dark": {"kind": "cloth", "c": (106, 22, 24)},
    "white": {"kind": "cloth", "c": (228, 222, 208)},
    "stripes": {"kind": "stripes", "c": (228, 222, 208), "c2": (40, 52, 96), "period": 2},
    "navy": {"kind": "cloth", "c": (38, 50, 88)},
    "black": {"kind": "cloth", "c": (36, 34, 38)},
    "black_felt": {"kind": "cloth", "c": (30, 28, 30)},
    "leather": {"kind": "leather", "c": (108, 70, 42)},
    "dark_leather": {"kind": "leather", "c": (66, 44, 30)},
    "boot": {"kind": "leather", "c": (42, 34, 30)},
    "canvas": {"kind": "cloth", "c": (152, 130, 92)},
    "gold": {"kind": "gold", "c": (218, 174, 66)},
    "steel": {"kind": "plate", "c": (150, 154, 162), "rivets": True},
    "feather_white": {"kind": "feather", "c": (236, 232, 224)},
    "feather_red": {"kind": "feather", "c": (178, 40, 36)},
    "skull": {"kind": "flat", "c": (232, 228, 216)},
    "dot": {"kind": "flat", "c": (236, 230, 214)},
}

PIRATE_DECKHAND = {
    "id": "pirate_deckhand", "faction": "pirates", "tier": 1,
    "names": {"es": ["Pañuelo de grumete", "Camisa a rayas", "Calzones de lona", "Zapatos de cubierta", "Ropa de grumete"],
              "en": ["Deckhand's Bandana", "Striped Shirt", "Canvas Breeches", "Deck Shoes", "Deckhand's Clothes"]},
    "materials": P_MATS,
    "recipe": {"X": "minecraft:leather", "A": "minecraft:gold_nugget"},
    "parts": {
        "head": N(B("red", -4, -8, -4, 8, 4, 8, 1.05, decals=[("px", "N", [(1, 1, "dot"), (4, 2, "dot"), (7, 0, "dot")]),
                                                            ("px", "W", [(2, 1, "dot"), (5, 3, "dot")]),
                                                            ("px", "E", [(1, 2, "dot"), (6, 1, "dot")]),
                                                            ("px", "U", [(2, 2, "dot"), (5, 5, "dot")])]),
                  B("red", -1, -6, 4.4, 2, 2, 1, 0.3),
                  N(B("red", -0.5, 0, -0.5, 1, 4, 1, 0.0), pivot=(-0.8, -5, 5.8), rot=(25, 0, 15)),
                  N(B("red", -0.5, 0, -0.5, 1, 3, 1, 0.0), pivot=(0.8, -5, 5.8), rot=(20, 0, -12))),
        "body": N(torso("stripes"), belt("red", 9, 2, 1.2),
                  N(B("red", 0, 0, -0.5, 1, 4, 1, 0.0), pivot=(4.9, 10.5, -1), rot=(0, 0, -10))),
        "right_arm": N(sleeve("stripes", 7)),
        "waist": N(hips("canvas")),
        "right_leg": N(pants("canvas", 9), B("canvas", -2, 8, -2, 4, 1, 4, ring(0.8, 0.2))),
        "right_boot": N(boot("dark_leather", 3, decals=[("sprite", "N", "buckle", "c", 0, "gold")])),
    },
}

PIRATE_BUCCANEER = {
    "id": "pirate_buccaneer", "faction": "pirates", "tier": 2,
    "names": {"es": ["Sombrero de bucanero", "Casaca de cuero", "Calzones de bucanero", "Botas de vuelta", "Atuendo de bucanero"],
              "en": ["Buccaneer's Hat", "Leather Coat", "Buccaneer's Breeches", "Cuffed Boots", "Buccaneer's Outfit"]},
    "materials": P_MATS,
    "recipe": {"X": "minecraft:chain", "A": "minecraft:gold_nugget"},
    "parts": {
        # sombrero de ala ancha
        "head": N(B("leather", -4, -11, -4, 8, 3, 8, 0.4, decals=[("band", "sides", 2, 3, "red_dark")]),
                  B("leather", -7, -8.3, -7, 14, 1, 14, 0.0)),
        "body": N(torso("white", 0.8),
                  torso("leather", 1.05, decals=[("hole", "N", 3, 0, 2, 9), ("diag", "N", 6, -0.5, 2, "dark_leather")]),
                  belt("dark_leather", 9, 2, 1.25, decals=[("sprite", "N", "buckle", "c", 0, "gold")])),
        "right_arm": N(sleeve("leather"), cuff("leather", 8, 3, 1.35, decals=[("band", "sides", 0, 1, "gold")])),
        "right_skirt": N(B("leather", -2, 0, -2, 4, 4, 4, 1.05)),
        "waist": N(hips("navy")),
        "right_leg": N(pants("navy")),
        "right_boot": N(boot("boot", 7), B("boot", -2, 5, -2, 4, 2, 4, ring(1.4, 0.3))),
    },
}

PIRATE_CORSAIR = {
    "id": "pirate_corsair", "faction": "pirates", "tier": 3,
    "names": {"es": ["Tricornio de corsario", "Coraza de corsario", "Calzones de corsario", "Botas de corsario", "Equipo de corsario"],
              "en": ["Corsair's Tricorn", "Corsair's Cuirass", "Corsair's Breeches", "Corsair's Boots", "Corsair's Gear"]},
    "materials": P_MATS,
    "recipe": {"X": "minecraft:iron_ingot", "A": "minecraft:gold_nugget"},
    "parts": {
        "head": N(*tricorn("black_felt", "gold")),
        "body": N(torso("navy", 0.9), B("steel", -4, 0, -2, 8, 8, 4, 1.2, decals=[("vband", "N", 3, 5, (120, 124, 132)),
                                                                                ("band", "sides", 7, 8, "gold")]),
                  belt("red", 9, 2, 1.25)),
        "right_arm": N(sleeve("navy", 12, 0.9), cuff("navy", 8, 3, 1.3, decals=[("band", "sides", 0, 1, "gold")])),
        "right_skirt": N(B("navy", -2, 0, -2, 4, 5, 4, 1.05, decals=[("band", "sides", 4, 5, "gold")])),
        "waist": N(hips("navy")),
        "right_leg": N(pants("navy", decals=[("vband", "W", 1, 2, "gold")])),
        "right_boot": N(boot("boot", 7), B("boot", -2, 5, -2, 4, 2, 4, ring(1.4, 0.3))),
    },
}

PIRATE_CAPTAIN = {
    "id": "pirate_captain", "faction": "pirates", "tier": 4,
    "names": {"es": ["Tricornio del capitán", "Casaca del capitán", "Calzones del capitán", "Botas del capitán", "Galas del capitán pirata"],
              "en": ["Captain's Tricorn", "Captain's Coat", "Captain's Breeches", "Captain's Boots", "Pirate Captain's Finery"]},
    "materials": P_MATS,
    "recipe": {"X": "minecraft:iron_ingot", "A": "minecraft:gold_nugget", "D": "minecraft:diamond"},
    "parts": {
        # tricornio grande con la calavera y las plumas
        "head": N(*tricorn("black_felt", "gold", front_point=-7.8, back=5.8, half=6.5, height=4),
                  B("skull", -1.5, -10.6, -4.55, 3, 3, 0, 0.0) if False else B("black_felt", -1.5, -10.8, -4.7, 3, 3, 1, 0.0,
                                                                                decals=[("sprite", "N", "skull_small", 0, 0, "skull")]),
                  N(B("feather_red", -0.5, -9, -1, 1, 9, 2, 0.0), pivot=(3.9, -10.6, 1.5), rot=(-28, 0, 22)),
                  N(B("feather_white", -0.5, -7, -1, 1, 7, 2, 0.0), pivot=(3.6, -10.6, 2.5), rot=(-40, 0, 14))),
        "body": N(torso("black", 0.8, decals=[("buttons", "N", "c", 1, 11, 2, "gold")]),
                  torso("red", 1.1, decals=[("hole", "N", 3, 0, 2, 12), ("vband", "N", 2, 3, "gold"), ("vband", "N", 5, 6, "gold")]),
                  belt("dark_leather", 9, 2, 1.3, decals=[("sprite", "N", "buckle", "c", 0, "gold")])),
        "right_arm": N(sleeve("red", 12, 1.1),
                       B("gold", -4, -3, -3, 5, 1, 6, 0.3),
                       B("gold", -4, -2.2, -3, 5, 1, 6, (0.35, 0.0, 0.35), decals=[("fringe", "sides", 1)]),
                       cuff("red_dark", 8, 3, 1.45, decals=[("band", "sides", 0, 1, "gold")])),
        "right_skirt": N(B("red", -2, 0, -2, 4, 8, 4, 1.1, decals=[("band", "sides", 7, 8, "gold")])),
        "waist": N(hips("black")),
        "right_leg": N(pants("black")),
        "right_boot": N(boot("boot", 7, decals=[("sprite", "N", "buckle", "c", 5, "gold")]),
                        B("boot", -2, 5, -2, 4, 2, 4, ring(1.45, 0.3))),
    },
}

ARMORS = [BANDIT_OUTLAW, BANDIT_HIGHWAYMAN, BANDIT_BRIGAND, BANDIT_WARLORD,
          VIKING_HUNTER, VIKING_WARRIOR, VIKING_HUSCARL, VIKING_JARL,
          PIRATE_DECKHAND, PIRATE_BUCCANEER, PIRATE_CORSAIR, PIRATE_CAPTAIN]
