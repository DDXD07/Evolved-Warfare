"""Voces de los NPCs, sintetizadas: un timbre por facción (tono, tamaño de la garganta, voz rota, aliento) y, para cada
una, su voz de siempre (murmullos, risas, gritos de guerra) y su queja al recibir un golpe. La muerte es la queja más
grave (en sounds.json, con 'pitch'); el «sí» de los reclutas y los comerciantes, una de sus voces.

Síntesis por formantes: los armónicos de la voz (con su vibrato y su temblor) pasan por las resonancias de cada vocal,
que se mueven de una a otra; más el aliento (ruido con las mismas resonancias), la voz rota (subarmónicos) y la erre
vibrante de los piratas.

Uso: python3 tools/ew_voices.py [raíz del proyecto] [--preview carpeta]   (necesita numpy, scipy y ffmpeg; matplotlib
     para las vistas previas)
Escribe src/main/resources/assets/evolvedwarfare/sounds/npc/<voz>/*.ogg, sus entradas de sounds.json y los subtítulos.
"""
import collections
import glob
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np
from scipy.signal import butter, lfilter

ROOT = "."
PREVIEW = None
args = sys.argv[1:]
if "--preview" in args:
    i = args.index("--preview")
    PREVIEW = args[i + 1]
    del args[i:i + 2]
if args:
    ROOT = args[0]
ASSETS = os.path.join(ROOT, "src/main/resources/assets/evolvedwarfare")
RATE = 44100
TOP = 7500.0  # sin armónicos por encima (suena más cálido y no hay aliasing)

# formantes (Hz) y anchos de banda de cada sonido, voz de hombre adulto
VOWELS = {
    "a": ((730, 1090, 2440, 3400), (90, 110, 170, 250)),
    "e": ((530, 1840, 2480, 3500), (80, 110, 170, 250)),
    "i": ((300, 2250, 2950, 3600), (60, 100, 170, 250)),
    "o": ((520, 860, 2400, 3300), (80, 100, 170, 250)),
    "u": ((320, 800, 2240, 3300), (60, 100, 170, 250)),
    "U": ((440, 1020, 2240, 3300), (70, 100, 170, 250)),   # la u corta de «oof»
    "A": ((640, 1190, 2390, 3400), (90, 110, 170, 250)),   # la «uh» de los gruñidos
    "r": ((460, 1250, 1650, 3200), (90, 130, 160, 250)),   # erre (la tercera resonancia baja)
    "m": ((260, 1100, 2300, 3300), (60, 500, 800, 1100)),  # boca cerrada: casi solo la primera (y apagada, DAMP)
    "y": ((280, 2200, 2900, 3600), (60, 100, 170, 250)),
}


# por encima de esto se apaga (la nariz se come los agudos de la «m»)
DAMP = {"m": 750.0}
OPEN = 20000.0


class Voice:
    def __init__(self, f0, size, rasp, breath, jitter, seed):
        self.f0 = f0            # tono de base
        self.size = size        # >1 = garganta más grande (formantes más bajos)
        self.rasp = rasp        # voz rota: subarmónicos y aspereza
        self.breath = breath    # aliento
        self.jitter = jitter    # temblor del tono
        self.rng = np.random.default_rng(seed)


VOICES = {
    "bandit": Voice(f0=104, size=1.03, rasp=0.22, breath=0.30, jitter=0.020, seed=11),
    "viking": Voice(f0=86, size=1.09, rasp=0.38, breath=0.16, jitter=0.016, seed=22),
    "pirate": Voice(f0=114, size=1.02, rasp=0.30, breath=0.22, jitter=0.022, seed=33),
    "recruit": Voice(f0=124, size=1.00, rasp=0.05, breath=0.14, jitter=0.012, seed=44),
    "trader": Voice(f0=132, size=0.98, rasp=0.00, breath=0.12, jitter=0.010, seed=55),
}


def smooth_interp(frac, points):
    """Curva suave por los puntos (fracción del tiempo, valor)."""
    xs = np.array([p[0] for p in points], dtype=float)
    ys = np.array([p[1] for p in points], dtype=float)
    out = np.interp(frac, xs, ys)
    k = max(1, int(0.012 * RATE))
    if len(out) > k * 2:
        kernel = np.hanning(k * 2 + 1)
        kernel /= kernel.sum()
        out = np.convolve(np.pad(out, k, mode="edge"), kernel, mode="valid")
    return out


def lowpass_noise(rng, n, hz):
    b, a = butter(2, hz / (RATE / 2), "low")
    x = lfilter(b, a, rng.standard_normal(n))
    return x / (np.std(x) + 1e-9)


def gain(f, formants, bandwidths):
    """Respuesta de las resonancias (en cascada, 1 en 0 Hz) a la frecuencia f (arrays del mismo largo)."""
    g = np.ones_like(f)
    for i in range(formants.shape[1]):
        ff = formants[:, i]
        b = bandwidths[:, i] / 2.0
        g *= (ff ** 2 + b ** 2) / np.sqrt(((f - ff) ** 2 + b ** 2) * ((f + ff) ** 2 + b ** 2))
    return g


def resonate(x, formants, bandwidths):
    """Ruido por las resonancias fijas de una vocal (para el aliento y la hache)."""
    y = x
    for ff, bw in zip(formants, bandwidths):
        r = np.exp(-np.pi * bw / RATE)
        c = 2 * r * np.cos(2 * np.pi * ff / RATE)
        y = lfilter([1 - r], [1, -c, r * r], y)
    return y


def tract(voice, vowel_points, frac):
    """Formantes y anchos a lo largo de la sílaba (n x 4), y dónde se apagan los agudos."""
    n = len(frac)
    formants = np.zeros((n, 4))
    widths = np.zeros((n, 4))
    for i in range(4):
        formants[:, i] = smooth_interp(frac, [(p, VOWELS[v][0][i]) for p, v in vowel_points]) / voice.size
        widths[:, i] = smooth_interp(frac, [(p, VOWELS[v][1][i]) for p, v in vowel_points])
    damp = np.exp(smooth_interp(frac, [(p, np.log(DAMP.get(v, OPEN))) for p, v in vowel_points]))
    return formants, widths, damp


def syllable(voice, dur, pitch, vowels, loud=None, attack=0.025, release=0.07, onset=None, coda=None, trill=None,
             rasp=1.0, breath=1.0):
    """Una sílaba con voz.

    pitch: [(fracción, tono relativo al de la voz)]; vowels: [(fracción, vocal)]; loud: [(fracción, volumen)].
    onset 'h' = aspiración antes; coda 'f' = soplo al final, 'h' = suspiro; trill = (desde, hasta, Hz) de erre vibrante.
    """
    rng = voice.rng
    n = int(dur * RATE)
    tt = np.arange(n) / RATE
    frac = tt / dur
    f0 = smooth_interp(frac, pitch) * voice.f0
    f0 *= 1.0 + 0.006 * np.sin(2 * np.pi * 5.3 * tt + rng.uniform(0, 6.28))          # vibrato
    f0 *= 1.0 + voice.jitter * lowpass_noise(rng, n, 18) * 0.5                       # temblor
    phase = 2 * np.pi * np.cumsum(f0) / RATE
    formants, widths, damp = tract(voice, vowels, frac)
    out = np.zeros(n)
    kmax = int(TOP / max(40.0, f0.min())) + 1
    r = voice.rasp * rasp
    for k in range(1, kmax + 1):
        fk = k * f0
        g = gain(fk, formants, widths) / k * (fk < TOP) / np.sqrt(1 + (fk / damp) ** 4)
        out += g * np.sin(k * phase + rng.uniform(0, 2 * np.pi))
        if r > 0:
            fh = (k - 0.5) * f0
            gh = gain(fh, formants, widths) / k * (fh < TOP) / np.sqrt(1 + (fh / damp) ** 4)
            out += r * 0.55 * gh * np.sin((2 * k - 1) * phase / 2 + rng.uniform(0, 2 * np.pi))
    if r > 0:  # aspereza: el volumen tiembla deprisa
        out *= 1.0 + r * 0.45 * lowpass_noise(rng, n, 55)
    level = np.sqrt(np.mean(out ** 2)) + 1e-9
    out /= level
    # aliento: ruido con las resonancias de la vocal del medio, latiendo con cada pulso de la voz
    mid = vowels[len(vowels) // 2][1]
    puff = resonate(rng.standard_normal(n), VOWELS[mid][0], [w * 1.6 for w in VOWELS[mid][1]])
    if mid in DAMP:
        b_, a_ = butter(2, DAMP[mid] / (RATE / 2), "low")
        puff = lfilter(b_, a_, puff)
    puff = puff / (np.sqrt(np.mean(puff ** 2)) + 1e-9) * (0.55 + 0.45 * np.cos(phase))
    out += voice.breath * breath * 0.6 * puff
    if trill:
        a, b, hz = trill
        inside = smooth_interp(frac, [(0, 0), (max(0, a - 0.02), 0), (a, 1), (b, 1), (min(1, b + 0.02), 0), (1, 0)])
        out *= 1.0 - inside * 0.65 * (0.5 + 0.5 * np.sin(2 * np.pi * hz * tt))
    env = np.ones(n)
    na, nr = int(attack * RATE), int(release * RATE)
    env[:na] = np.sin(np.linspace(0, np.pi / 2, na)) ** 2
    env[n - nr:] *= np.cos(np.linspace(0, np.pi / 2, nr)) ** 2
    if loud:
        env *= smooth_interp(frac, loud)
    out *= env
    parts = [out]
    if onset == "h":
        first = vowels[0][1]
        m = int(0.07 * RATE)
        h = resonate(rng.standard_normal(m), VOWELS[first][0], [w * 2.2 for w in VOWELS[first][1]])
        h = h / (np.sqrt(np.mean(h ** 2)) + 1e-9) * 0.5
        lap = int(0.025 * RATE)
        h *= np.sin(np.linspace(0, np.pi / 2, m)) ** 1.5
        h[m - lap:] *= np.cos(np.linspace(0, np.pi / 2, lap)) ** 2   # se funde con la vocal
        parts.insert(0, ("pre", h))
    if coda in ("f", "h"):
        m = int((0.12 if coda == "f" else 0.18) * RATE)
        noise = rng.standard_normal(m)
        if coda == "f":
            b_, a_ = butter(2, [1500 / (RATE / 2), 6500 / (RATE / 2)], "band")
            tail = lfilter(b_, a_, noise)
        else:
            last = vowels[-1][1]
            tail = resonate(noise, VOWELS[last][0], [w * 2.5 for w in VOWELS[last][1]])
        tail = tail / (np.sqrt(np.mean(tail ** 2)) + 1e-9) * (0.22 if coda == "f" else 0.3)
        tail *= np.cos(np.linspace(0, np.pi / 2, m)) ** 2
        rise = int(0.018 * RATE)
        tail[:rise] *= np.sin(np.linspace(0, np.pi / 2, rise)) ** 2   # entra sin chasquido
        parts.append(("post", tail))
    return parts


def phrase(*items):
    """Junta sílabas y silencios (números = segundos de silencio); la hache solapa un poco con la vocal."""
    chunks = []
    for item in items:
        if isinstance(item, (int, float)):
            chunks.append(np.zeros(int(item * RATE)))
            continue
        voiced = item[0] if not isinstance(item[0], tuple) else None
        pre = [p[1] for p in item if isinstance(p, tuple) and p[0] == "pre"]
        post = [p[1] for p in item if isinstance(p, tuple) and p[0] == "post"]
        voiced = [p for p in item if not isinstance(p, tuple)][0]
        seg = voiced
        if pre:
            h = pre[0]
            lap = int(0.025 * RATE)
            seg = np.concatenate([h, np.zeros(len(voiced) - lap)])
            seg[len(h) - lap:] += voiced
        if post:
            tail = post[0]
            lap = int(0.03 * RATE)
            seg = np.concatenate([seg, np.zeros(len(tail) - lap)])
            seg[len(seg) - len(tail):] += tail
        chunks.append(seg)
    return np.concatenate(chunks)


def room(x):
    """Un poco de sala (reflejos tempranos, apagados), para que no suene a máquina."""
    out = np.concatenate([x, np.zeros(int(0.12 * RATE))])
    b, a = butter(1, 2500 / (RATE / 2), "low")
    wet = lfilter(b, a, out)
    for delay, amp in ((0.019, 0.16), (0.031, 0.12), (0.047, 0.09), (0.071, 0.06), (0.097, 0.04)):
        d = int(delay * RATE)
        out[d:] += wet[:len(out) - d] * amp
    return out


def finish(x, loudness):
    b, a = butter(2, 75 / (RATE / 2), "high")
    x = room(lfilter(b, a, x))
    rms = np.sqrt(np.mean(x[np.abs(x) > np.max(np.abs(x)) * 0.05] ** 2))
    x = x / (rms + 1e-9) * loudness
    x = np.tanh(x * 1.1) / np.tanh(1.1)          # limita los picos sin cortar
    peak = np.max(np.abs(x))
    if peak > 0.92:
        x *= 0.92 / peak
    fade = int(0.015 * RATE)
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade:] *= np.linspace(1, 0, fade)
    return x


AMBIENT, HURT = 0.16, 0.22  # volumen (RMS) de la voz y de las quejas


# ------------------------------------------------------------------ lo que dice cada uno

def bandit(v):
    return {
        # risita entre dientes: je, je, je
        "ambient1": phrase(syllable(v, 0.11, [(0, 1.18), (1, 1.08)], [(0, "e"), (1, "A")], onset="h", release=0.04, breath=1.6), 0.07,
                           syllable(v, 0.10, [(0, 1.12), (1, 1.02)], [(0, "e"), (1, "A")], onset="h", release=0.04, breath=1.6), 0.07,
                           syllable(v, 0.14, [(0, 1.05), (1, 0.9)], [(0, "e"), (1, "A")], onset="h", release=0.08, breath=1.8)),
        # «hmpf»: bufido por la nariz
        "ambient2": phrase(syllable(v, 0.26, [(0, 1.05), (0.5, 0.98), (1, 0.85)], [(0, "A"), (0.35, "m"), (1, "m")], onset="h",
                                    coda="f", loud=[(0, 1), (0.6, 0.8), (1, 0.5)])),
        # «eeeh...»: llamada desconfiada, que se apaga
        "ambient3": phrase(syllable(v, 0.42, [(0, 1.0), (0.3, 1.14), (1, 0.86)], [(0, "e"), (0.6, "e"), (1, "i")], onset="h",
                                    loud=[(0, 0.9), (0.4, 1.0), (1, 0.6)], release=0.12)),
        # «¡ugh!»
        "hurt1": phrase(syllable(v, 0.2, [(0, 1.55), (1, 1.1)], [(0, "A"), (1, "U")], attack=0.006, release=0.08, rasp=1.4,
                                 coda="h", loud=[(0, 1), (1, 0.7)])),
        # «¡agh!»
        "hurt2": phrase(syllable(v, 0.24, [(0, 1.45), (0.4, 1.35), (1, 1.0)], [(0, "a"), (1, "A")], attack=0.008, release=0.09,
                                 rasp=1.6, coda="h")),
    }


def viking(v):
    return {
        # «¡Huu!»: grito de guerra corto, de pecho
        "ambient1": phrase(syllable(v, 0.5, [(0, 0.95), (0.25, 1.18), (0.8, 1.12), (1, 0.95)], [(0, "u"), (0.5, "o"), (1, "o")],
                                    onset="h", loud=[(0, 0.8), (0.3, 1), (1, 0.7)], release=0.14)),
        # «¡Ja-joo!»
        "ambient2": phrase(syllable(v, 0.17, [(0, 1.1), (1, 1.15)], [(0, "a"), (1, "a")], onset="h", release=0.04), 0.05,
                           syllable(v, 0.4, [(0, 1.25), (0.4, 1.3), (1, 1.0)], [(0, "o"), (1, "u")], onset="h", release=0.13,
                                    loud=[(0, 1), (1, 0.75)])),
        # «hrrrm»: gruñido grave
        "ambient3": phrase(syllable(v, 0.55, [(0, 0.92), (1, 0.8)], [(0, "A"), (0.45, "r"), (0.7, "m"), (1, "m")], onset="h",
                                    rasp=1.6, trill=(0.3, 0.6, 24), loud=[(0, 0.9), (0.5, 1), (1, 0.6)], release=0.12)),
        # «¡raagh!»
        "hurt1": phrase(syllable(v, 0.32, [(0, 1.4), (0.3, 1.45), (1, 1.0)], [(0, "r"), (0.15, "a"), (1, "A")], attack=0.01,
                                 rasp=1.7, trill=(0.0, 0.14, 26), coda="h")),
        # «¡ohh!»
        "hurt2": phrase(syllable(v, 0.3, [(0, 1.5), (1, 1.05)], [(0, "o"), (1, "U")], attack=0.008, rasp=1.3, coda="h",
                                 loud=[(0, 1), (1, 0.7)])),
    }


def pirate(v):
    return {
        # «¡Arrr!»
        "ambient1": phrase(syllable(v, 0.6, [(0, 1.05), (0.25, 1.12), (1, 0.88)], [(0, "a"), (0.3, "a"), (0.5, "r"), (1, "r")],
                                    trill=(0.42, 0.95, 27), rasp=1.3, loud=[(0, 0.9), (0.3, 1), (1, 0.7)], release=0.1)),
        # «¡Yo-jo!»
        "ambient2": phrase(syllable(v, 0.22, [(0, 1.15), (1, 1.2)], [(0, "y"), (0.35, "o"), (1, "o")], release=0.04), 0.05,
                           syllable(v, 0.36, [(0, 1.3), (0.35, 1.38), (1, 1.05)], [(0, "o"), (1, "o")], onset="h", release=0.12)),
        # «¡jar, jar!»: risotada
        "ambient3": phrase(syllable(v, 0.2, [(0, 1.3), (1, 1.18)], [(0, "a"), (0.6, "a"), (1, "r")], onset="h", trill=(0.6, 1.0, 26),
                                    release=0.05), 0.06,
                           syllable(v, 0.26, [(0, 1.22), (1, 1.0)], [(0, "a"), (0.6, "a"), (1, "r")], onset="h", trill=(0.6, 1.0, 26),
                                    release=0.09)),
        # «¡argh!»
        "hurt1": phrase(syllable(v, 0.28, [(0, 1.5), (0.35, 1.4), (1, 1.0)], [(0, "a"), (0.5, "a"), (1, "r")], attack=0.007,
                                 trill=(0.5, 1.0, 28), rasp=1.6, coda="h")),
        # «¡au!»
        "hurt2": phrase(syllable(v, 0.24, [(0, 1.55), (1, 1.12)], [(0, "a"), (1, "u")], attack=0.007, rasp=1.2, coda="h",
                                 loud=[(0, 1), (1, 0.75)])),
    }


def recruit(v):
    return {
        # «¿mm?»
        "ambient1": phrase(syllable(v, 0.26, [(0, 0.98), (0.4, 1.0), (1, 1.22)], [(0, "m"), (1, "m")], loud=[(0, 0.8), (1, 1)],
                                    release=0.06)),
        # «mm-jm» (sí)
        "ambient2": phrase(syllable(v, 0.16, [(0, 1.08), (1, 1.0)], [(0, "m"), (1, "m")], release=0.03), 0.06,
                           syllable(v, 0.22, [(0, 0.98), (1, 1.14)], [(0, "m"), (1, "m")], onset="h", release=0.07)),
        # «ja»: resoplido de cansancio
        "ambient3": phrase(syllable(v, 0.2, [(0, 1.05), (1, 0.9)], [(0, "A"), (1, "A")], onset="h", coda="h", breath=1.8,
                                    loud=[(0, 1), (1, 0.6)])),
        # «¡uf!»
        "hurt1": phrase(syllable(v, 0.15, [(0, 1.45), (1, 1.15)], [(0, "U"), (1, "u")], attack=0.006, release=0.05, coda="f")),
        # «¡ah!»
        "hurt2": phrase(syllable(v, 0.18, [(0, 1.55), (1, 1.12)], [(0, "a"), (1, "A")], attack=0.006, release=0.08, coda="h")),
    }


def trader(v):
    return {
        # «hmmm~»: pensando un precio
        "ambient1": phrase(syllable(v, 0.62, [(0, 1.0), (0.35, 1.13), (1, 0.94)], [(0, "m"), (1, "m")], onset="h",
                                    loud=[(0, 0.8), (0.4, 1), (1, 0.75)], release=0.14)),
        # «¡a-já!» (también es su «sí»)
        "ambient2": phrase(syllable(v, 0.14, [(0, 1.08), (1, 1.12)], [(0, "a"), (1, "a")], release=0.03), 0.05,
                           syllable(v, 0.24, [(0, 1.3), (0.4, 1.34), (1, 1.08)], [(0, "a"), (1, "A")], onset="h", release=0.09)),
        # «mm-mm»: contento
        "ambient3": phrase(syllable(v, 0.18, [(0, 1.1), (1, 1.15)], [(0, "m"), (1, "m")], release=0.04), 0.04,
                           syllable(v, 0.26, [(0, 1.15), (1, 0.98)], [(0, "m"), (1, "m")], release=0.1)),
        # «¡oh!»
        "hurt1": phrase(syllable(v, 0.2, [(0, 1.6), (1, 1.2)], [(0, "o"), (1, "U")], attack=0.006, release=0.08, coda="h")),
        # «¡ay!»
        "hurt2": phrase(syllable(v, 0.24, [(0, 1.55), (1, 1.25)], [(0, "a"), (0.5, "a"), (1, "i")], attack=0.007, release=0.08)),
        # «mm-mm» de no: bajando (no se vende / no le alcanza)
        "no": phrase(syllable(v, 0.15, [(0, 1.12), (1, 1.08)], [(0, "m"), (1, "m")], release=0.03), 0.05,
                     syllable(v, 0.22, [(0, 1.02), (1, 0.84)], [(0, "m"), (1, "m")], release=0.09)),
    }


LINES = {"bandit": bandit, "viking": viking, "pirate": pirate, "recruit": recruit, "trader": trader}

# subtítulos: (español, inglés)
SUBTITLES = {
    "bandit": ("Bandido", "Bandit", "murmura", "murmurs"),
    "viking": ("Vikingo", "Viking", "grita", "shouts"),
    "pirate": ("Pirata", "Pirate", "gruñe", "growls"),
    "recruit": ("NPC", "NPC", "murmura", "mutters"),
    "trader": ("Comerciante", "Trader", "murmura", "hums"),
}


def save(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "x.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes((np.clip(data, -1, 1) * 32767).astype("<i2").tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", path], check=True)


def preview(name, clips):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    os.makedirs(PREVIEW, exist_ok=True)
    fig, axes = plt.subplots(1, len(clips), figsize=(3.2 * len(clips), 3.2))
    for ax, (clip, data) in zip(axes, clips.items()):
        ax.specgram(data, NFFT=1024, Fs=RATE, noverlap=900, cmap="magma", vmin=-110)
        ax.set_ylim(0, 5000)
        ax.set_title(f"{name}/{clip}", fontsize=9)
        ax.tick_params(labelsize=7)
    fig.tight_layout()
    fig.savefig(os.path.join(PREVIEW, name + ".png"), dpi=90)
    plt.close(fig)


def main():
    sounds_path = os.path.join(ASSETS, "sounds.json")
    with open(sounds_path, encoding="utf-8") as f:
        sounds = json.load(f, object_pairs_hook=collections.OrderedDict)
    for key in [k for k in sounds if k.startswith("npc.")]:
        del sounds[key]
    for name, lines in LINES.items():
        clips = lines(VOICES[name])
        done = {}
        for clip, data in clips.items():
            data = finish(data, HURT if clip.startswith("hurt") else AMBIENT)
            done[clip] = data
            save(os.path.join(ASSETS, "sounds/npc", name, clip + ".ogg"), data)
        if PREVIEW:
            preview(name, done)
        ambient = [f"evolvedwarfare:npc/{name}/{c}" for c in clips if c.startswith("ambient")]
        hurt = [f"evolvedwarfare:npc/{name}/{c}" for c in clips if c.startswith("hurt")]
        sounds[f"npc.{name}.ambient"] = {"subtitle": f"subtitles.evolvedwarfare.npc.{name}.ambient", "sounds": ambient}
        sounds[f"npc.{name}.hurt"] = {"subtitle": f"subtitles.evolvedwarfare.npc.{name}.hurt", "sounds": hurt}
        sounds[f"npc.{name}.death"] = {"subtitle": f"subtitles.evolvedwarfare.npc.{name}.death",
                                       "sounds": [{"name": h, "pitch": 0.78} for h in hurt]}
        if name in ("recruit", "trader"):
            sounds[f"npc.{name}.yes"] = {"subtitle": f"subtitles.evolvedwarfare.npc.{name}.yes",
                                         "sounds": [f"evolvedwarfare:npc/{name}/ambient2"]}
        if name == "trader":
            sounds["npc.trader.no"] = {"subtitle": "subtitles.evolvedwarfare.npc.trader.no",
                                       "sounds": ["evolvedwarfare:npc/trader/no"]}
        print(f"{name}: {', '.join(f'{c} {len(d) / RATE:.2f} s' for c, d in done.items())}")
    with open(sounds_path, "w", encoding="utf-8") as f:
        json.dump(sounds, f, ensure_ascii=False, indent=2)
        f.write("\n")
    for path in sorted(glob.glob(os.path.join(ASSETS, "lang", "*.json"))):
        english = path.endswith("en_us.json")
        with open(path, encoding="utf-8") as f:
            lang = json.load(f, object_pairs_hook=collections.OrderedDict)
        for name, (es, en, talk_es, talk_en) in SUBTITLES.items():
            who = en if english else es
            base = f"subtitles.evolvedwarfare.npc.{name}."
            lang[base + "ambient"] = f"{who} {talk_en if english else talk_es}"
            lang[base + "hurt"] = f"{who} {'hurts' if english else 'herido'}"
            lang[base + "death"] = f"{who} {'dies' if english else 'muere'}"
        lang["subtitles.evolvedwarfare.npc.recruit.yes"] = "NPC agrees" if english else "NPC asiente"
        lang["subtitles.evolvedwarfare.npc.trader.yes"] = "Trader agrees" if english else "Comerciante asiente"
        lang["subtitles.evolvedwarfare.npc.trader.no"] = "Trader disagrees" if english else "Comerciante niega"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(lang, f, ensure_ascii=False, indent=2)
            f.write("\n")


if __name__ == "__main__":
    main()
