"""Voces de los NPCs, sintetizadas para que suenen a persona: un timbre por facción y, para cada una, sus ruidos de
siempre (murmullos, suspiros, carraspeos, risas por lo bajo, toses), sus quejas al recibir un golpe y su muerte.

Cómo suena humano (y no a máquina):
- **La fuente es una glotis, no una suma de senos**: un pulso de aire por cada vibración de las cuerdas (modelo de
  Rosenberg), cada uno un poco distinto: el periodo tiembla (jitter), el volumen también (shimmer) y la abertura cambia
  (voz soplada, normal o apretada, como al hacer fuerza).
- **Aliento de verdad**: ruido de aire que entra y sale con cada pulso, más en los suspiros.
- **El tono deriva** despacio (nadie mantiene una nota recta), cae al final de lo que se dice y, al apagarse, la voz
  **cruje** (fritura vocal: pulsos lentos e irregulares).
- **La boca se mueve**: las resonancias (formantes) pasan de un sonido a otro sin saltos, en un filtro que cambia cada 5
  ms; la «m» se cierra y se apaga en los agudos.
- **Ruidos que no son voz**: la hache, el soplo al final, la tos, el carraspeo, el resoplido por la nariz.
- Y un poco de sala, y nada de notas cantadas: casi todo son sonidos sin palabras, cortos y variados.

Uso: python3 tools/ew_voices.py [raíz del proyecto] [--preview carpeta]   (necesita numpy, scipy y ffmpeg; matplotlib
     para las vistas previas)
Escribe src/main/resources/assets/evolvedwarfare/sounds/npc/<voz>/*.ogg, sus entradas de sounds.json (a un 75 % del
volumen de antes) y los subtítulos.
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
BLOCK = 220  # el filtro de la boca cambia cada 5 ms
VOLUME = 0.75  # un 25 % más bajo que antes (en sounds.json)

# formantes (Hz) y anchos de banda, voz de hombre adulto
VOWELS = {
    "a": ((730, 1090, 2440, 3400, 4200), (80, 100, 140, 200, 280)),
    "e": ((530, 1840, 2480, 3500, 4300), (70, 100, 140, 200, 280)),
    "i": ((300, 2250, 2950, 3600, 4400), (50, 90, 140, 200, 280)),
    "o": ((520, 860, 2400, 3300, 4200), (70, 90, 140, 200, 280)),
    "u": ((320, 800, 2240, 3300, 4200), (50, 90, 140, 200, 280)),
    "U": ((440, 1020, 2240, 3300, 4200), (60, 90, 140, 200, 280)),  # la u corta de «uf»
    "A": ((620, 1180, 2390, 3400, 4200), (80, 100, 140, 200, 280)),  # la «uh» neutra de los gruñidos
    "E": ((500, 1500, 2500, 3500, 4300), (70, 100, 140, 200, 280)),  # schwa
    "r": ((460, 1250, 1650, 3200, 4200), (90, 130, 160, 250, 300)),
    "m": ((250, 1050, 2200, 3300, 4200), (50, 300, 500, 700, 900)),  # boca cerrada (y se apagan los agudos)
    "n": ((260, 1500, 2500, 3300, 4200), (50, 300, 500, 700, 900)),
}
NASAL = {"m": 900.0, "n": 1200.0}
OPEN = 9000.0


class Voice:
    def __init__(self, f0, size, breath, jitter, shimmer, press, seed):
        self.f0 = f0          # tono de base
        self.size = size      # >1: garganta más grande (formantes más bajos)
        self.breath = breath  # cuánto aire se oye
        self.jitter = jitter  # temblor del periodo (0.01 = 1 %)
        self.shimmer = shimmer
        self.press = press    # >0: voz más apretada, áspera
        self.rng = np.random.default_rng(seed)


VOICES = {
    "bandit": Voice(f0=108, size=1.03, breath=0.32, jitter=0.018, shimmer=0.10, press=0.35, seed=11),
    "viking": Voice(f0=88, size=1.10, breath=0.20, jitter=0.016, shimmer=0.10, press=0.55, seed=22),
    "pirate": Voice(f0=112, size=1.02, breath=0.26, jitter=0.020, shimmer=0.11, press=0.45, seed=33),
    "recruit": Voice(f0=122, size=1.00, breath=0.22, jitter=0.012, shimmer=0.07, press=0.15, seed=44),
    "trader": Voice(f0=130, size=0.98, breath=0.20, jitter=0.011, shimmer=0.06, press=0.05, seed=55),
}


# ------------------------------------------------------------------ curvas

def curve(frac, points):
    """Curva suave por los puntos (fracción, valor), sin esquinas."""
    xs = np.array([p[0] for p in points], dtype=float)
    ys = np.array([p[1] for p in points], dtype=float)
    out = np.interp(frac, xs, ys)
    k = max(1, int(0.015 * RATE))
    if len(out) > 2 * k:
        w = np.hanning(2 * k + 1)
        w /= w.sum()
        out = np.convolve(np.pad(out, k, mode="edge"), w, mode="valid")
    return out


def drift(rng, n, hz, amount):
    """Deriva lenta al azar (el tono y el volumen nunca están quietos)."""
    b, a = butter(1, hz / (RATE / 2), "low")
    x = lfilter(b, a, rng.standard_normal(n + 2000))[2000:]
    return 1.0 + amount * x / (np.std(x) + 1e-9)


# ------------------------------------------------------------------ la glotis

def glottis(voice, f0, oq, amp, fry=None):
    """Tren de pulsos de aire (la derivada del flujo, que es lo que radian los labios) y el flujo (para el aliento).

    f0, oq (abertura: 0.4 apretada .. 0.8 soplada) y amp por muestra; fry: por muestra, 0..1 de fritura vocal.
    """
    rng = voice.rng
    n = len(f0)
    flow = np.zeros(n + RATE // 20)
    t = 0.0
    while t < n:
        i = int(t)
        f = f0[i]
        q = oq[i]
        a = amp[i] * (1.0 + voice.shimmer * rng.standard_normal() * 0.5)
        jit = voice.jitter
        if fry is not None and fry[i] > 0.01:
            # fritura: pulsos lentos, irregulares, con pausas
            f = f * (1.0 - 0.6 * fry[i])
            jit = jit + 0.12 * fry[i]
            a *= 1.0 - 0.35 * fry[i] * rng.random()
        period = RATE / max(30.0, f) * (1.0 + jit * rng.standard_normal())
        period = max(20.0, period)
        tp = max(2, int(period * q * 0.72))   # sube
        tn = max(2, int(period * q * 0.28))   # cierra (más corto: el golpe que da el timbre)
        up = 0.5 * (1.0 - np.cos(np.pi * np.arange(tp) / tp))
        down = np.cos(0.5 * np.pi * np.arange(tn) / tn)
        pulse = np.concatenate([up, down]) * a
        j = min(len(flow), i + len(pulse))
        flow[i:j] += pulse[:j - i]
        t += period
    flow = flow[:n]
    src = np.diff(flow, prepend=0.0)
    return src, flow


def tract_filter(x, formants, widths, damp):
    """La boca: cinco resonancias en cascada que cambian cada 5 ms (y la nariz se come los agudos de la m)."""
    n = len(x)
    y = np.zeros(n)
    states = [np.zeros(2) for _ in range(formants.shape[1])]
    lp_state = np.zeros(2)
    for s in range(0, n, BLOCK):
        e = min(n, s + BLOCK)
        c = (s + e) // 2
        seg = x[s:e]
        for k in range(formants.shape[1]):
            f = formants[c, k]
            bw = widths[c, k]
            r = np.exp(-np.pi * bw / RATE)
            th = 2 * np.pi * f / RATE
            a1, a2 = -2 * r * np.cos(th), r * r
            b0 = 1 + a1 + a2   # ganancia 1 en 0 Hz
            seg, states[k] = lfilter([b0], [1, a1, a2], seg, zi=states[k])
        hz = min(damp[c], RATE * 0.45)
        b, a = butter(2, hz / (RATE / 2), "low")
        seg, lp_state = lfilter(b, a, seg, zi=lp_state)
        y[s:e] = seg
    return y


def mouth(voice, vowel_points, frac):
    n = len(frac)
    formants = np.zeros((n, 5))
    widths = np.zeros((n, 5))
    jitter_f = drift(voice.rng, n, 4.0, 0.015)
    for k in range(5):
        formants[:, k] = curve(frac, [(p, VOWELS[v][0][k]) for p, v in vowel_points]) / voice.size * jitter_f
        widths[:, k] = curve(frac, [(p, VOWELS[v][1][k]) for p, v in vowel_points])
    damp = np.exp(curve(frac, [(p, np.log(NASAL.get(v, OPEN))) for p, v in vowel_points]))
    return formants, widths, damp


# ------------------------------------------------------------------ sonidos

def voiced(voice, dur, pitch, vowels, loud=((0, 1), (1, 1)), oq=None, breath=1.0, fry_tail=0.0, attack=0.03, release=0.09,
           press=None, h_onset=0.0):
    """Algo dicho con voz: pitch y loud son [(fracción, valor)] (tono relativo al de la voz); vowels [(fracción, sonido)].

    oq: abertura de la glotis (más alta, más soplada); fry_tail: la fracción del final que cruje; h_onset: segundos de
    aire antes de que suene la voz (una hache).
    """
    rng = voice.rng
    n = int(dur * RATE)
    frac = np.arange(n) / max(1, n - 1)
    press = voice.press if press is None else press
    f0 = curve(frac, pitch) * voice.f0 * drift(rng, n, 3.0, 0.02)
    # el tono cae al final de lo que se dice
    f0 *= 1.0 - 0.06 * np.clip((frac - 0.7) / 0.3, 0, 1)
    open_q = curve(frac, oq) if oq else np.full(n, 0.62 - 0.18 * press)
    env = curve(frac, loud) * drift(rng, n, 6.0, 0.05)
    na, nr = int(attack * RATE), int(release * RATE)
    env[:na] *= np.sin(np.linspace(0, np.pi / 2, na)) ** 2
    env[n - nr:] *= np.cos(np.linspace(0, np.pi / 2, nr)) ** 1.5
    fry = None
    if fry_tail > 0:
        fry = np.clip((frac - (1.0 - fry_tail)) / fry_tail, 0, 1) ** 1.3
    src, flow = glottis(voice, f0, open_q, np.ones(n), fry)
    # aliento: ruido de aire, más fuerte con la glotis abierta
    hiss = rng.standard_normal(n)
    b, a = butter(2, 900 / (RATE / 2), "high")
    hiss = lfilter(b, a, hiss)
    fl = flow / (np.max(flow) + 1e-9)
    air = hiss * (0.25 + fl) * voice.breath * breath * 0.09 * (0.6 + open_q)
    # voz apretada: más golpe en cada cierre (más armónicos agudos)
    src = src * (1.0 + 0.8 * press)
    formants, widths, damp = mouth(voice, vowels, frac)
    out = tract_filter(src + air, formants, widths, damp) * env
    out = out / (np.sqrt(np.mean(out ** 2)) + 1e-9)
    if h_onset > 0:
        h = aspirate(voice, h_onset, vowels[0][1], 0.35)
        lap = min(len(h), int(0.03 * RATE))
        merged = np.concatenate([h, np.zeros(n - lap)])
        merged[len(h) - lap:] += out
        out = merged
    return out


def aspirate(voice, dur, vowel, level=0.3, fade_in=0.5):
    """Aire por la boca con la forma de una vocal: la hache, un suspiro sin voz."""
    rng = voice.rng
    n = int(dur * RATE)
    frac = np.arange(n) / max(1, n - 1)
    x = rng.standard_normal(n)
    formants, widths, damp = mouth(voice, [(0, vowel), (1, vowel)], frac)
    y = tract_filter(x, formants, widths * 2.2, damp)
    y = y / (np.sqrt(np.mean(y ** 2)) + 1e-9) * level
    env = np.ones(n)
    k = int(n * fade_in)
    env[:k] = np.sin(np.linspace(0, np.pi / 2, k)) ** 2
    m = int(n * 0.3)
    env[n - m:] *= np.cos(np.linspace(0, np.pi / 2, m)) ** 2
    return y * env


def puff(voice, dur, low=600, high=5000, level=0.2, shape="out"):
    """Un soplo (por la boca o la nariz): «fff» al final, resoplido, sorber."""
    rng = voice.rng
    n = int(dur * RATE)
    b, a = butter(2, [low / (RATE / 2), high / (RATE / 2)], "band")
    y = lfilter(b, a, rng.standard_normal(n))
    y = y / (np.sqrt(np.mean(y ** 2)) + 1e-9) * level
    t = np.linspace(0, 1, n)
    env = (np.sin(np.pi * t) ** 0.6) if shape == "mid" else (1 - t) ** 1.6 * np.minimum(1, t * 12)
    return y * env


def cough(voice, strength=1.0):
    """Una tos: golpe de aire con algo de voz, que se apaga en un soplo."""
    burst = puff(voice, 0.09, 300, 4500, 0.9 * strength)
    v = voiced(voice, 0.16, [(0, 1.25), (1, 0.95)], [(0, "A"), (1, "E")], loud=[(0, 1), (1, 0.4)], oq=[(0, 0.45), (1, 0.7)],
               breath=2.2, attack=0.004, release=0.08, press=0.6)
    tail = puff(voice, 0.12, 500, 3500, 0.25)
    return mix([(0, burst), (0.01, v * 0.7), (0.13, tail)])


def silence(sec):
    return np.zeros(int(sec * RATE))


def mix(parts):
    """Junta trozos en sus tiempos (segundos), sumando lo que se solapa."""
    end = max(int(t * RATE) + len(x) for t, x in parts)
    out = np.zeros(end)
    for t, x in parts:
        s = int(t * RATE)
        out[s:s + len(x)] += x
    return out


def seq(*items):
    """Uno detrás de otro (los números son silencios en segundos)."""
    return np.concatenate([silence(i) if isinstance(i, (int, float)) else i for i in items])


def room(x):
    """Un poco de sala (reflejos tempranos apagados y una cola corta), para que no suene metido en una caja."""
    out = np.concatenate([x, np.zeros(int(0.25 * RATE))])
    b, a = butter(1, 2200 / (RATE / 2), "low")
    wet = lfilter(b, a, out)
    for delay, amp in ((0.013, 0.13), (0.021, 0.10), (0.034, 0.08), (0.052, 0.06), (0.077, 0.045), (0.11, 0.03), (0.15, 0.02)):
        d = int(delay * RATE)
        out[d:] += wet[:len(out) - d] * amp
    return out


def finish(x, loudness):
    b, a = butter(2, 70 / (RATE / 2), "high")
    x = lfilter(b, a, x)
    b, a = butter(2, 7800 / (RATE / 2), "low")
    x = room(lfilter(b, a, x))
    loud_part = x[np.abs(x) > np.max(np.abs(x)) * 0.05]
    x = x / (np.sqrt(np.mean(loud_part ** 2)) + 1e-9) * loudness
    x = np.tanh(x * 1.05) / np.tanh(1.05)
    peak = np.max(np.abs(x))
    if peak > 0.9:
        x *= 0.9 / peak
    fade = int(0.012 * RATE)
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade:] *= np.linspace(1, 0, fade)
    # se recorta el silencio del final
    keep = np.nonzero(np.abs(x) > 0.002)[0]
    return x[:keep[-1] + 1] if len(keep) else x


AMBIENT, HURT, DEATH = 0.15, 0.21, 0.2


# ------------------------------------------------------------------ lo que hace cada uno

def hum(v, dur=0.45, rise=False, low=1.0):
    """«Mmm» pensativo (o «¿mm?» si sube)."""
    pitch = [(0, 1.02 * low), (0.4, 1.06 * low), (1, 1.18 * low if rise else 0.9 * low)]
    return voiced(v, dur, pitch, [(0, "m"), (1, "m")], loud=[(0, 0.7), (0.3, 1), (1, 0.65)], oq=[(0, 0.6), (1, 0.7)],
                  fry_tail=0.0 if rise else 0.25, attack=0.05, release=0.12)


def sigh(v, low=1.0):
    """Suspiro: se toma aire, sale con un «haaa» soplado que se apaga crujiendo."""
    inhale = puff(v, 0.3, 500, 2600, 0.06, "mid")
    out = voiced(v, 0.7, [(0, 1.08 * low), (0.3, 1.0 * low), (1, 0.82 * low)], [(0, "a"), (0.5, "A"), (1, "E")],
                 loud=[(0, 0.6), (0.25, 1.0), (1, 0.35)], oq=[(0, 0.8), (1, 0.85)], breath=3.0, fry_tail=0.3, attack=0.08,
                 release=0.22, press=0.0, h_onset=0.12)
    return seq(inhale, 0.08, out)


def huh(v, up=True):
    """«¿Eh?» / «¿hm?» corto."""
    return voiced(v, 0.22, [(0, 1.0), (1, 1.28 if up else 0.85)], [(0, "e"), (1, "E")], loud=[(0, 0.8), (0.4, 1), (1, 0.6)],
                  attack=0.02, release=0.07, h_onset=0.05)


def uh_huh(v, yes=True):
    """«Mm-hm» (sí) o «mm-mm» (no)."""
    a = voiced(v, 0.17, [(0, 1.05), (1, 1.02 if yes else 1.1)], [(0, "m"), (1, "m")], loud=[(0, 0.8), (1, 1)], attack=0.03,
               release=0.05)
    b = voiced(v, 0.24, [(0, 0.95 if yes else 1.05), (1, 1.18 if yes else 0.82)], [(0, "m"), (1, "m")],
               loud=[(0, 1), (1, 0.7)], attack=0.02, release=0.1, fry_tail=0.0 if yes else 0.3, h_onset=0.06 if yes else 0.0)
    return seq(a, 0.07, b)


def chuckle(v, beats=3, vowel="e", low=1.0, breath=2.5):
    """Risa por lo bajo: «je, je, je», soplada, cada golpe más bajo."""
    parts = []
    for k in range(beats):
        p = (1.25 - 0.08 * k) * low
        parts.append(voiced(v, 0.1 + 0.02 * (k == beats - 1), [(0, p), (1, p * 0.9)], [(0, vowel), (1, "E")],
                            loud=[(0, 1), (1, 0.5)], oq=[(0, 0.75), (1, 0.8)], breath=breath, attack=0.008, release=0.05,
                            h_onset=0.045))
        parts.append(0.06 + 0.02 * k)
    return seq(*parts[:-1])


def laugh(v, beats=3, vowel="a", low=1.0):
    """Carcajada: «¡ja, ja, ja!» con voz y pecho."""
    parts = []
    for k in range(beats):
        p = (1.38 - 0.1 * k) * low
        parts.append(voiced(v, 0.13, [(0, p), (1, p * 0.88)], [(0, vowel), (1, "A")], loud=[(0, 1), (1, 0.55)], breath=1.6,
                            attack=0.01, release=0.05, h_onset=0.05))
        parts.append(0.05)
    return seq(*parts[:-1])


def clear_throat(v):
    """Carraspeo: «ejem»."""
    rasp = voiced(v, 0.2, [(0, 0.9), (1, 0.85)], [(0, "A"), (1, "E")], loud=[(0, 0.8), (1, 0.6)], oq=[(0, 0.42), (1, 0.45)],
                  breath=1.5, attack=0.01, release=0.04, press=0.9, fry_tail=0.5)
    hm = voiced(v, 0.22, [(0, 1.1), (1, 0.95)], [(0, "E"), (0.4, "m"), (1, "m")], loud=[(0, 1), (1, 0.6)], attack=0.02,
                release=0.08)
    return seq(rasp, 0.05, hm)


def hmph(v):
    """Resoplido por la nariz: «hmpf»."""
    m = voiced(v, 0.16, [(0, 1.08), (1, 0.92)], [(0, "m"), (1, "m")], loud=[(0, 1), (1, 0.6)], attack=0.01, release=0.04,
               press=0.4)
    nose = puff(v, 0.18, 1200, 6000, 0.22)
    return mix([(0, m), (0.12, nose)])


def grunt(v, low=1.0, vowel="A", dur=0.3):
    """Gruñido grave: «hrm»."""
    return voiced(v, dur, [(0, 0.95 * low), (0.5, 0.98 * low), (1, 0.85 * low)], [(0, vowel), (0.5, "E"), (1, "m")],
                  loud=[(0, 0.8), (0.4, 1), (1, 0.6)], oq=[(0, 0.48), (1, 0.55)], press=0.8, fry_tail=0.45, attack=0.03,
                  release=0.1)


def growl(v):
    """«Arrr» de pirata: la erre con la lengua (el volumen late a 25 Hz) y voz apretada."""
    x = voiced(v, 0.62, [(0, 1.0), (0.25, 1.06), (1, 0.86)], [(0, "a"), (0.3, "a"), (0.5, "r"), (1, "r")],
               loud=[(0, 0.8), (0.3, 1), (1, 0.6)], press=0.75, fry_tail=0.3, release=0.12)
    t = np.arange(len(x)) / RATE
    frac = t / t[-1]
    trill = np.clip((frac - 0.42) / 0.08, 0, 1)
    return x * (1 - trill * 0.6 * (0.5 + 0.5 * np.sin(2 * np.pi * 25 * t)))


def ugh(v, vowel=("A", "U"), high=1.55, dur=0.2, press=0.9):
    """Queja de golpe: arranca de golpe y alto, apretada, y cae con un soplo."""
    x = voiced(v, dur, [(0, high), (0.25, high * 0.96), (1, high * 0.72)], [(0, vowel[0]), (1, vowel[1])],
               loud=[(0, 1), (0.3, 0.9), (1, 0.4)], oq=[(0, 0.42), (1, 0.6)], press=press, breath=1.4, attack=0.004,
               release=0.07, fry_tail=0.25)
    tail = aspirate(v, 0.16, vowel[1], 0.28, fade_in=0.05)
    return mix([(0, x), (len(x) / RATE - 0.04, tail)])


def oof(v, high=1.45):
    """«¡Uf!»: el aire sale de golpe (como al recibir un golpe en el estómago)."""
    burst = puff(v, 0.05, 400, 3000, 0.5)
    x = voiced(v, 0.13, [(0, high), (1, high * 0.8)], [(0, "U"), (1, "u")], loud=[(0, 1), (1, 0.45)], oq=[(0, 0.45), (1, 0.6)],
               press=0.7, breath=1.6, attack=0.003, release=0.05)
    f = puff(v, 0.14, 1500, 6000, 0.22)
    return mix([(0, burst), (0.01, x), (0.12, f)])


def death(v, vowel="a", high=1.45, dur=1.0):
    """Muerte: un «¡aagh…!» que arranca alto, se quiebra, baja y se apaga crujiendo, y el último aliento."""
    x = voiced(v, dur, [(0, high), (0.15, high * 1.03), (0.45, high * 0.82), (1, high * 0.5)], [(0, vowel), (0.5, "A"), (1, "E")],
               loud=[(0, 1), (0.2, 0.95), (0.6, 0.6), (1, 0.2)], oq=[(0, 0.42), (0.5, 0.6), (1, 0.85)], press=0.85, breath=1.8,
               attack=0.006, release=0.3, fry_tail=0.5)
    last = aspirate(v, 0.5, "E", 0.16, fade_in=0.2)
    return seq(x, 0.05, last)


def bandit(v):
    return {
        "ambient1": chuckle(v, 3, "e", low=1.0, breath=3.0),     # risita entre dientes
        "ambient2": hmph(v),                                     # bufido desconfiado
        "ambient3": sigh(v, low=0.95),                           # resoplido de aburrimiento
        "ambient4": cough(v),                                    # tos de mala vida
        "ambient5": huh(v, up=False),                            # «¿eh?» de pocos amigos
        "hurt1": ugh(v, ("A", "U"), 1.5),
        "hurt2": ugh(v, ("a", "A"), 1.42, 0.24),
        "hurt3": oof(v, 1.4),
        "death1": death(v, "a", 1.4),
        "death2": death(v, "A", 1.3, 0.85),
    }


def viking(v):
    return {
        "ambient1": laugh(v, 3, "a", low=0.95),                  # carcajada de pecho
        "ambient2": grunt(v, 1.0),                               # gruñido grave
        "ambient3": clear_throat(v),                             # carraspeo
        "ambient4": voiced(v, 0.5, [(0, 0.95), (0.3, 1.2), (1, 1.0)], [(0, "o"), (0.5, "o"), (1, "u")],  # «¡hoo!» de llamada
                           loud=[(0, 0.7), (0.35, 1), (1, 0.6)], press=0.6, h_onset=0.08, release=0.16, fry_tail=0.2),
        "ambient5": sigh(v, low=0.9),
        "hurt1": ugh(v, ("a", "A"), 1.4, 0.28, press=1.0),
        "hurt2": ugh(v, ("o", "U"), 1.45, 0.24, press=1.0),
        "hurt3": oof(v, 1.35),
        "death1": death(v, "a", 1.35, 1.1),
        "death2": death(v, "o", 1.3, 0.95),
    }


def pirate(v):
    return {
        "ambient1": growl(v),                                    # «¡arrr!»
        "ambient2": laugh(v, 4, "a", low=1.0),                   # risotada
        "ambient3": chuckle(v, 2, "a", low=0.95, breath=2.0),
        "ambient4": cough(v, 1.1),                               # demasiado ron
        "ambient5": hmph(v),
        "hurt1": ugh(v, ("a", "r"), 1.5, 0.26),
        "hurt2": ugh(v, ("a", "U"), 1.55),
        "hurt3": oof(v, 1.42),
        "death1": death(v, "a", 1.45),
        "death2": death(v, "A", 1.35, 0.9),
    }


def recruit(v):
    return {
        "ambient1": hum(v, 0.4, rise=True),                      # «¿mm?»
        "ambient2": uh_huh(v, yes=True),                         # «mm-hm» (también es su «sí»)
        "ambient3": sigh(v),                                     # suspiro de cansancio
        "ambient4": clear_throat(v),
        "ambient5": hum(v, 0.55, rise=False),                    # «mmm» pensativo
        "hurt1": oof(v, 1.5),
        "hurt2": ugh(v, ("a", "A"), 1.55, 0.2, press=0.7),
        "hurt3": ugh(v, ("A", "U"), 1.6, 0.18, press=0.7),
        "death1": death(v, "a", 1.5),
        "death2": death(v, "A", 1.4, 0.85),
    }


def trader(v):
    return {
        "ambient1": hum(v, 0.6, rise=False, low=1.02),           # «hmmm…» pensando un precio
        "ambient2": voiced(v, 0.13, [(0, 1.08), (1, 1.12)], [(0, "a"), (1, "a")], release=0.03, h_onset=0.04),  # «¡a-já!»
        "ambient3": uh_huh(v, yes=True),                         # «mm-hm», contento
        "ambient4": chuckle(v, 2, "e", low=1.05, breath=1.8),    # risita amable
        "ambient5": huh(v, up=True),
        "no": uh_huh(v, yes=False),                              # «mm-mm» (no)
        "hurt1": ugh(v, ("o", "U"), 1.6, 0.2, press=0.5),
        "hurt2": ugh(v, ("a", "e"), 1.6, 0.22, press=0.5),
        "hurt3": oof(v, 1.5),
        "death1": death(v, "o", 1.5),
        "death2": death(v, "a", 1.45, 0.85),
    }


# el «¡a-já!» del comerciante se alarga un poco (también es su «sí»)
def trader_yes(v):
    a = voiced(v, 0.13, [(0, 1.08), (1, 1.12)], [(0, "a"), (1, "a")], release=0.03, h_onset=0.04)
    b = voiced(v, 0.26, [(0, 1.3), (0.4, 1.34), (1, 1.05)], [(0, "a"), (1, "A")], release=0.1, h_onset=0.06)
    return seq(a, 0.05, b)


LINES = {"bandit": bandit, "viking": viking, "pirate": pirate, "recruit": recruit, "trader": trader}

SUBTITLES = {
    "bandit": ("Bandido", "Bandit", "murmura", "mutters"),
    "viking": ("Vikingo", "Viking", "gruñe", "grunts"),
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
    cols = 5
    rows = (len(clips) + cols - 1) // cols
    fig, axes = plt.subplots(rows, cols, figsize=(3.0 * cols, 2.6 * rows))
    for ax in axes.flat:
        ax.axis("off")
    for ax, (clip, data) in zip(axes.flat, clips.items()):
        ax.axis("on")
        ax.specgram(data, NFFT=1024, Fs=RATE, noverlap=900, cmap="magma", vmin=-110)
        ax.set_ylim(0, 5000)
        ax.set_title(f"{name}/{clip}", fontsize=8)
        ax.tick_params(labelsize=6)
    fig.tight_layout()
    fig.savefig(os.path.join(PREVIEW, name + ".png"), dpi=80)
    plt.close(fig)
    with tempfile.TemporaryDirectory() as tmp:
        # todo seguido, para escucharlo de una vez
        allx = np.concatenate([np.concatenate([c, np.zeros(int(0.35 * RATE))]) for c in clips.values()])
        save(os.path.join(PREVIEW, name + "_all.ogg"), allx)


def entry(name):
    return {"name": name, "volume": VOLUME}


def main():
    sounds_path = os.path.join(ASSETS, "sounds.json")
    with open(sounds_path, encoding="utf-8") as f:
        sounds = json.load(f, object_pairs_hook=collections.OrderedDict)
    for key in [k for k in sounds if k.startswith("npc.")]:
        del sounds[key]
    for old in glob.glob(os.path.join(ASSETS, "sounds/npc/*/*.ogg")):
        os.remove(old)
    for name, lines in LINES.items():
        v = VOICES[name]
        clips = lines(v)
        if name == "trader":
            clips["yes"] = trader_yes(v)
        done = {}
        for clip, data in clips.items():
            level = HURT if clip.startswith("hurt") else DEATH if clip.startswith("death") else AMBIENT
            data = finish(data, level)
            done[clip] = data
            save(os.path.join(ASSETS, "sounds/npc", name, clip + ".ogg"), data)
        if PREVIEW:
            preview(name, done)

        def group(prefix):
            return [entry(f"evolvedwarfare:npc/{name}/{c}") for c in clips if c.startswith(prefix)]

        sounds[f"npc.{name}.ambient"] = {"subtitle": f"subtitles.evolvedwarfare.npc.{name}.ambient", "sounds": group("ambient")}
        sounds[f"npc.{name}.hurt"] = {"subtitle": f"subtitles.evolvedwarfare.npc.{name}.hurt", "sounds": group("hurt")}
        sounds[f"npc.{name}.death"] = {"subtitle": f"subtitles.evolvedwarfare.npc.{name}.death", "sounds": group("death")}
        if name == "recruit":
            sounds["npc.recruit.yes"] = {"subtitle": "subtitles.evolvedwarfare.npc.recruit.yes",
                                         "sounds": [entry("evolvedwarfare:npc/recruit/ambient2")]}
        if name == "trader":
            sounds["npc.trader.yes"] = {"subtitle": "subtitles.evolvedwarfare.npc.trader.yes", "sounds": [entry("evolvedwarfare:npc/trader/yes")]}
            sounds["npc.trader.no"] = {"subtitle": "subtitles.evolvedwarfare.npc.trader.no", "sounds": [entry("evolvedwarfare:npc/trader/no")]}
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
