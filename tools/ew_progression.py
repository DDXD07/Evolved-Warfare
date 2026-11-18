#!/usr/bin/env python3
"""
Fase 116: el ritmo del nivel de superviviente (estimado). No se ha jugado aún: son supuestos de lo que se gana por hora
en cada etapa (ver RATES) con los valores del juego (Survivor.java y SkillData.levelXpToNext), para ver que la curva
tiene sentido: los primeros niveles salen enseguida y el 100 lleva decenas de horas.

    python3 tools/ew_progression.py
"""


def to_next(level):
    return 20.0 + 6.0 * level + 0.12 * level * level


# experiencia por hora de juego (aprox.), por etapas: (hasta la hora, lo que se gana por hora y de dónde)
RATES = [
    (2, 450, "primeras cosas fabricadas (4 cada una, 0,5 los bloques), talar, carbón y cobre, zombis y esqueletos de noche"),
    (10, 700, "hierro y oro, más monstruos, biomas nuevos, amaneceres y las primeras subidas de habilidad"),
    (30, 1000, "diamantes, élites (x3), el Nether, habilidades por encima de 30"),
    (200, 1400, "legendarios y jefes (150-500), escombros ancestrales, habilidades altas"),
]


def xp_at(hours):
    xp, t = 0.0, 0.0
    for until, rate, _ in RATES:
        span = min(hours, until) - t
        if span <= 0:
            break
        xp += span * rate
        t += span
    return xp


def level_for(xp):
    level = 1
    while level < 100 and xp >= to_next(level):
        xp -= to_next(level)
        level += 1
    return level


def hours_for(level):
    h = 0.0
    while level_for(xp_at(h)) < level:
        h += 0.05
    return h


def main():
    print("| Horas de juego | Nivel (aprox.) | Puntos de engrama |")
    print("|---|---|---|")
    for h in (0.5, 1, 2, 5, 10, 20, 40, 60, 80):
        lv = level_for(xp_at(h))
        pts = 10 + sum(6 + l // 10 for l in range(2, lv + 1))
        print("| %s | %d | %d |" % (("%g" % h).replace(".", ","), lv, pts))
    print()
    for lv in (5, 10, 14, 18, 30, 42, 50, 70, 100):
        print("nivel %3d: %5.1f h" % (lv, hours_for(lv)))


if __name__ == "__main__":
    main()
