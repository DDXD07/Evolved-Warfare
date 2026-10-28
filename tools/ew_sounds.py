"""Fase 10: sonidos del mosquete, sintetizados (ruido filtrado y senos amortiguados), en .ogg mono.

Uso: python3 tools/ew_sounds.py src/main/resources/assets/evolvedwarfare   (necesita numpy, scipy y ffmpeg)
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np
from scipy.signal import butter, lfilter

OUT = sys.argv[1]
RATE = 44100
rng = np.random.default_rng(1776)


def t(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def noise(seconds):
    return rng.uniform(-1, 1, int(seconds * RATE))


def low(x, hz):
    b, a = butter(2, hz / (RATE / 2), "low")
    return lfilter(b, a, x)


def band(x, lo, hi):
    b, a = butter(2, [lo / (RATE / 2), hi / (RATE / 2)], "band")
    return lfilter(b, a, x)


def env(seconds, decay, attack=0.0005):
    tt = t(seconds)
    return np.minimum(1.0, tt / attack) * np.exp(-tt / decay)


def place(total, part, at):
    start = int(at * RATE)
    end = min(len(total), start + len(part))
    total[start:end] += part[: end - start]


def ping(seconds, freqs, decay):
    tt = t(seconds)
    return sum(np.sin(2 * np.pi * f * tt) * w for f, w in freqs) * np.exp(-tt / decay)


def finish(x, peak=0.89):
    x = np.tanh(x * 1.4)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    # sin chasquido al final
    fade = min(len(x), int(0.02 * RATE))
    x[-fade:] *= np.linspace(1, 0, fade)
    return x


def fire():
    n = 1.6
    x = np.zeros(int(n * RATE))
    place(x, noise(0.012) * env(0.012, 0.002) * 1.2, 0.0)                           # chasquido
    place(x, low(noise(0.4), 1600) * env(0.4, 0.06) * 2.2, 0.001)                     # estampido
    place(x, low(noise(0.9), 420) * env(0.9, 0.22) * 2.6, 0.002)                      # cuerpo
    place(x, low(noise(1.55), 140) * env(1.55, 0.55, 0.03) * 3.0, 0.01)               # retumbo
    place(x, band(noise(0.08), 2500, 7000) * env(0.08, 0.012) * 0.5, 0.0)             # chispa de la cazoleta
    return finish(x)


def click(at, x, strength=1.0, tone=1.0):
    place(x, band(noise(0.01), 2000, 9000) * env(0.01, 0.0015) * strength, at)
    place(x, ping(0.05, [(2900 * tone, 0.6), (5300 * tone, 0.4), (1700 * tone, 0.3)], 0.008) * strength, at)


def cock():
    x = np.zeros(int(0.25 * RATE))
    click(0.0, x, 0.5, 1.1)   # primer punto del rastrillo
    click(0.07, x, 1.0, 1.0)  # segundo: queda montado
    return finish(x, 0.8)


def empty():
    x = np.zeros(int(0.2 * RATE))
    click(0.0, x, 1.0, 0.8)
    place(x, low(noise(0.05), 600) * env(0.05, 0.01) * 0.6, 0.0)
    return finish(x, 0.7)


def ball_in():
    x = np.zeros(int(0.5 * RATE))
    hits = [(0.0, 1.0), (0.085, 0.7), (0.15, 0.5), (0.2, 0.35), (0.235, 0.25)]
    for i, (at, amp) in enumerate(hits):
        f = 3300 + 250 * i
        place(x, ping(0.08, [(f, 0.7), (f * 1.83, 0.3)], 0.012) * amp, at)
    place(x, ping(0.15, [(190, 1.0), (330, 0.4)], 0.03) * 0.9, 0.27)  # llega al fondo
    place(x, low(noise(0.1), 900) * env(0.1, 0.02) * 0.5, 0.27)
    return finish(x, 0.75)


def ramrod():
    n = 0.42
    x = np.zeros(int(n * RATE))
    tt = t(0.3)
    scrape = band(noise(0.3), 1400, 4200) * (np.sin(np.pi * tt / 0.3) ** 2) * (0.6 + 0.4 * np.sin(2 * np.pi * 23 * tt))
    place(x, scrape * 0.7, 0.0)
    place(x, ping(0.12, [(140, 1.0), (260, 0.5)], 0.025), 0.29)    # aprieta la carga
    click(0.29, x, 0.4, 0.7)
    return finish(x, 0.75)


def save(name, data):
    folder = os.path.join(OUT, "sounds")
    os.makedirs(folder, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes((data * 32767).astype("<i2").tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5",
                        os.path.join(folder, name + ".ogg")], check=True)


def main():
    save("musket_fire", fire())
    save("musket_cock", cock())
    save("musket_empty", empty())
    save("musket_ball_in", ball_in())
    save("musket_ramrod", ramrod())


if __name__ == "__main__":
    main()
