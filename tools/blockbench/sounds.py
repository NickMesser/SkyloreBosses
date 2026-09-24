"""Procedural sound synthesis for Matris Calyx (mono 22.05 kHz OGG Vorbis).
Every sound id maps to a recipe chosen by keywords; seeds are per-id so rebuilds are stable."""
import hashlib
import numpy as np
import soundfile as sf

SR = 22050


def _rng(name):
    return np.random.default_rng(int(hashlib.md5(name.encode()).hexdigest()[:8], 16))


def t_(d):
    return np.arange(int(SR * d)) / SR


def env_exp(n, decay):
    return np.exp(-np.arange(n) / SR / decay)


def env_adsr(n, a=0.01, r=0.2):
    e = np.ones(n)
    na, nr = max(1, int(a * SR)), max(1, int(r * SR))
    e[:na] = np.linspace(0, 1, na)
    e[-nr:] *= np.linspace(1, 0, nr)
    return e


def band(x, lo, hi):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    m = ((f >= lo) & (f <= hi)).astype(float)
    # soft edges
    m = np.convolve(m, np.hanning(31) / np.hanning(31).sum(), mode="same")
    return np.fft.irfft(X * m, len(x))


def reverb(x, size=0.6, mix=0.35, rng=None):
    rng = rng or np.random.default_rng(1)
    n = int(SR * size)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / SR / (size / 4))
    ir = band(ir, 80, 5000)
    L = len(x) + n
    nf = 1 << (L - 1).bit_length()
    wet = np.fft.irfft(np.fft.rfft(x, nf) * np.fft.rfft(ir, nf), nf)[:L]
    out = np.concatenate([x, np.zeros(n)])
    wet /= (np.abs(wet).max() + 1e-9)
    return out * (1 - mix) + wet * mix * np.abs(x).max()


def sweep(f0, f1, d, shape="sine", curve=1.0):
    tt = t_(d)
    f = f0 + (f1 - f0) * (tt / d) ** curve
    ph = 2 * np.pi * np.cumsum(f) / SR
    if shape == "saw":
        return 2 * ((ph / (2 * np.pi)) % 1) - 1
    if shape == "square":
        return np.sign(np.sin(ph))
    return np.sin(ph)


def norm(x, peak=0.9):
    return x / (np.abs(x).max() + 1e-9) * peak


def fade(x, ms=8):
    n = int(SR * ms / 1000)
    x[:n] *= np.linspace(0, 1, n)
    x[-n:] *= np.linspace(1, 0, n)
    return x


# ---------------- recipes ----------------
def thump(r, d=0.35, f0=70, f1=32, noise=0.3):
    n = int(SR * d)
    x = sweep(f0, f1, d) * env_exp(n, d / 4)
    x += band(r.standard_normal(n), 30, 400) * env_exp(n, d / 10) * noise
    return x


def heartbeat(r):
    a = thump(r, 0.3)
    b = thump(r, 0.3, 60, 30) * 0.7
    out = np.zeros(int(SR * 1.0))
    out[: len(a)] += a
    k = int(SR * 0.28)
    out[k:k + len(b)] += b
    return reverb(out, 0.5, 0.25, r)


def squelch(r, d=0.6, lo=250, hi=1600, bubbles=14, body=0.5):
    n = int(SR * d)
    x = band(r.standard_normal(n), lo, hi)
    am = np.zeros(n)
    for _ in range(bubbles):
        c = r.integers(0, n)
        w = int(SR * r.uniform(0.01, 0.05))
        seg = np.hanning(2 * w)
        s, e = max(0, c - w), min(n, c + w)
        am[s:e] += seg[: e - s] * r.uniform(0.4, 1)
    x *= am
    x += sweep(r.uniform(90, 140), r.uniform(50, 70), d) * env_exp(n, d / 3) * body
    return x * env_adsr(n, 0.02, d * 0.4)


def roar(r, d=1.6, f0=90, f1=60, grit=0.6, formants=(420, 900, 2200), vib=5):
    n = int(SR * d)
    tt = t_(d)
    f = np.linspace(f0, f1, n) * (1 + 0.04 * np.sin(2 * np.pi * vib * tt)) * (1 + 0.02 * r.standard_normal(n).cumsum() / np.sqrt(n))
    ph = 2 * np.pi * np.cumsum(f) / SR
    src = 2 * ((ph / (2 * np.pi)) % 1) - 1
    src += r.standard_normal(n) * grit
    out = sum(band(src, fo * 0.8, fo * 1.25) * (1.0 / (k + 1)) for k, fo in enumerate(formants))
    out = np.tanh(out * 3)
    return reverb(out * env_adsr(n, 0.12, d * 0.45), 0.9, 0.3, r)


def boom(r, d=1.4, f0=80, f1=28, crack=0.7):
    n = int(SR * d)
    x = sweep(f0, f1, d, curve=0.5) * env_exp(n, d / 3)
    x += band(r.standard_normal(n), 60, 3500) * env_exp(n, 0.08) * crack
    x += band(r.standard_normal(n), 20, 200) * env_exp(n, d / 2.5) * 0.6
    return reverb(np.tanh(x * 1.5), 1.2, 0.35, r)


def whip(r, d=0.35):
    n = int(SR * d)
    x = band(r.standard_normal(n), 1500, 8000) * env_exp(n, 0.04)
    x += sweep(2400, 300, d) * env_exp(n, 0.05) * 0.3
    return x


def hiss(r, d=1.0, lo=2500, hi=9000, a=0.2):
    n = int(SR * d)
    return band(r.standard_normal(n), lo, hi) * env_adsr(n, a, d * 0.5) * (1 + 0.3 * np.sin(2 * np.pi * 7 * t_(d)))


def zap(r, d=0.8):
    n = int(SR * d)
    buzz = sweep(110, 90, d, "square") * 0.4
    crackle = np.zeros(n)
    for _ in range(60):
        c = r.integers(0, n - 200)
        crackle[c:c + 200] += r.standard_normal(200) * np.exp(-np.arange(200) / 30)
    x = band(buzz + crackle, 80, 9000)
    x += sweep(300, 2400, d, curve=2) * env_exp(n, d / 2) * 0.4
    return x * env_adsr(n, 0.01, d * 0.5)


def laser_charge(r, d=2.0):
    n = int(SR * d)
    x = sweep(160, 1400, d, curve=1.6) * 0.6 + sweep(161, 1410, d, "saw", curve=1.6) * 0.25
    x += band(r.standard_normal(n), 800, 6000) * np.linspace(0, 0.5, n)
    return x * np.linspace(0.2, 1, n)


def laser_loop(r, d=3.0):
    tt = t_(d)
    x = sum(np.sin(2 * np.pi * f * tt) for f in (180, 181.5, 362, 543)) / 4
    x += 0.25 * (2 * ((tt * 90) % 1) - 1)
    x += band(r.standard_normal(len(tt)), 1000, 7000) * 0.25
    return np.tanh(x * 1.8) * (1 + 0.15 * np.sin(2 * np.pi * 11 * tt))  # seamless: whole periods


def ping(r, d=1.2):
    n = int(SR * d)
    x = sum(np.sin(2 * np.pi * f * t_(d)) * a for f, a in ((1320, 1), (1980, 0.5), (2640, 0.3)))
    x += band(r.standard_normal(n), 3000, 10000) * env_exp(n, 0.05)
    return reverb(x * env_exp(n, 0.3), 1.0, 0.4, r)


def swell(r, d=1.2):
    n = int(SR * d)
    x = band(r.standard_normal(n), 200, 3000) * np.linspace(0, 1, n) ** 2
    x += sweep(60, 120, d) * np.linspace(0, 0.6, n)
    return x


def rumble(r, d=3.0, lo=20, hi=140):
    n = int(SR * d)
    x = band(r.standard_normal(n), lo, hi) * 3
    x *= 1 + 0.5 * np.sin(2 * np.pi * 0.7 * t_(d))
    return np.tanh(x) * env_adsr(n, d * 0.2, d * 0.3)


def drips(r, d=1.5, count=9):
    n = int(SR * d)
    out = np.zeros(n)
    for _ in range(count):
        c = r.integers(0, n - 4000)
        f = r.uniform(600, 1400)
        blip = sweep(f, f * 0.55, 0.12) * env_exp(int(SR * 0.12), 0.03)
        out[c:c + len(blip)] += blip * r.uniform(0.4, 1)
    return reverb(out, 0.6, 0.3, r)


def clicks(r, d=0.4, count=6, lo=1500, hi=7000):
    n = int(SR * d)
    out = np.zeros(n)
    for _ in range(count):
        c = r.integers(0, n - 300)
        out[c:c + 300] += r.standard_normal(300) * np.exp(-np.arange(300) / 40)
    return band(out, lo, hi)


def step(r, d=0.3):
    n = int(SR * d)
    x = band(r.standard_normal(n), 100, 900) * env_exp(n, 0.04)
    return x + squelch(r, d, 300, 1200, 3, 0.2) * 0.5


def gallop(r, d=0.8):
    out = np.zeros(int(SR * d))
    for k in range(4):
        th = thump(r, 0.18, 110, 55, 0.6)
        s = int(SR * (k * 0.15))
        out[s:s + len(th)] += th
    return out


def stinger(r, d=2.2):
    """body-attack warning: low rumble + dissonant horn."""
    x = rumble(r, d, 25, 160)
    tt = t_(d)
    horn = (np.sin(2 * np.pi * 55 * tt) + 0.6 * np.sin(2 * np.pi * 58.3 * tt) + 0.3 * np.sin(2 * np.pi * 110.5 * tt))
    horn = np.tanh(horn * 1.5) * env_adsr(len(tt), 0.4, 0.8)
    return reverb(x * 0.7 + horn * 0.6, 1.4, 0.4, r)


def choir(r, d=3.0):
    """victory / awaken swell: stacked detuned saw pad."""
    tt = t_(d)
    x = sum(sweep(f, f * 1.002, d, "saw") for f in (65.4, 98, 130.8, 155.6, 196))
    x = band(x, 60, 1600)
    return reverb(x * env_adsr(len(tt), d * 0.4, d * 0.4), 1.8, 0.5, r)


def ambient(r, d=8.0):
    n = int(SR * d)
    base = band(r.standard_normal(n), 25, 180) * 2
    beat = np.zeros(n)
    for k in range(int(d / 1.6)):
        h = heartbeat(r)[: int(SR * 0.8)] * 0.35
        s = int(SR * k * 1.6)
        beat[s:s + len(h)] += h[: max(0, min(len(h), n - s))]
    gurgle = squelch(r, d, 150, 700, 25, 0.1) * 0.3
    x = np.tanh(base) * 0.5 + beat + gurgle
    x[: int(SR * 0.5)] *= np.linspace(0, 1, int(SR * 0.5))
    x[-int(SR * 0.5):] *= np.linspace(1, 0, int(SR * 0.5))
    return x


# ---------------- industrial recipes (Overhead) ----------------
def servo(r, d=1.0, f0=220, f1=880):
    """rising electric whine with gear chatter"""
    n = int(SR * d)
    x = sweep(f0, f1, d, "saw", curve=0.8) * 0.4 + sweep(f0 * 2.01, f1 * 2.01, d, curve=0.8) * 0.2
    x += clicks(r, d, count=int(d * 30), lo=800, hi=4000) * 0.5
    return band(x, 100, 7000) * env_adsr(n, 0.05, d * 0.2)


def hum(r, d=2.0, f=60):
    tt = t_(d)
    x = sum(np.sin(2 * np.pi * f * k * tt) / k for k in (1, 2, 3, 5))
    x += band(r.standard_normal(len(tt)), 2000, 6000) * 0.08
    return x * (1 + 0.2 * np.sin(2 * np.pi * 3 * tt))


def klaxon(r, d=2.0):
    tt = t_(d)
    f = np.where((tt * 2.5) % 1 < 0.5, 440, 330)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.tanh(3 * np.sin(ph)) * 0.8
    return reverb(x * env_adsr(len(tt), 0.02, 0.1), 0.8, 0.3, r)


def siren(r, d=3.0):
    tt = t_(d)
    f = 400 + 250 * np.sin(2 * np.pi * 0.6 * tt)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return reverb(np.tanh(2 * np.sin(ph)) * env_adsr(len(tt), 0.2, 0.4), 1.0, 0.3, r)


def autocannon(r, d=0.25):
    n = int(SR * d)
    x = band(r.standard_normal(n), 200, 5000) * env_exp(n, 0.03)
    x += sweep(180, 60, d) * env_exp(n, 0.04) * 0.8
    return np.tanh(x * 2)


def beep(r, d=0.5, f=1760, count=3):
    out = np.zeros(int(SR * d))
    step = len(out) // count
    for k in range(count):
        s = k * step
        b = np.sin(2 * np.pi * f * t_(step * 0.5 / SR)) * 0.6
        out[s:s + len(b)] += b
    return out


def clank(r, d=0.6):
    n = int(SR * d)
    x = sum(np.sin(2 * np.pi * f * t_(d)) * a for f, a in ((310, 1), (523, 0.6), (1187, 0.4), (2300, 0.2)))
    x = x * env_exp(n, 0.12) + band(r.standard_normal(n), 500, 6000) * env_exp(n, 0.02)
    return reverb(x, 0.6, 0.25, r)


def electric(r, d=1.0):
    return zap(r, d) * 0.8 + hum(r, d, 120) * 0.3


# ---------------- liturgical recipes (The Static Deacon) ----------------
def bell(r, d=3.0, f=220):
    """church bell: inharmonic partials (hum, prime, tierce, quint, nominal...) with long decays"""
    tt = t_(d)
    n = len(tt)
    x = np.zeros(n)
    for ratio, amp, dec in ((0.5, 0.6, 1.0), (1.0, 1.0, 0.8), (1.183, 0.5, 0.6), (1.506, 0.4, 0.5), (2.0, 0.55, 0.45),
                            (2.514, 0.25, 0.3), (2.662, 0.2, 0.28), (3.011, 0.15, 0.2), (4.166, 0.1, 0.15)):
        x += amp * np.sin(2 * np.pi * f * ratio * tt + r.uniform(0, 6.28)) * env_exp(n, d * dec)
    x[: int(SR * 0.004)] *= np.linspace(0, 1, int(SR * 0.004))
    return reverb(x, 1.6, 0.35, r)


def crystal(r, d=0.8, f=1800):
    """glassy chime/shatter: detuned high partials plus a short bright noise burst"""
    tt = t_(d)
    n = len(tt)
    x = sum(np.sin(2 * np.pi * f * k * (1 + r.uniform(-0.01, 0.01)) * tt) / k for k in (1, 1.5, 2.3, 3.1))
    x = x * env_exp(n, d * 0.35) + band(r.standard_normal(n), 3000, 10000) * env_exp(n, 0.02) * 0.6
    return reverb(x, 0.8, 0.3, r)


def crackle(r, d=1.0, density=60):
    """static discharge: sparse broadband clicks over a hiss bed"""
    n = int(SR * d)
    x = band(r.standard_normal(n), 1500, 9000) * 0.25
    for _ in range(int(density * d)):
        s = r.integers(0, n - 200)
        x[s:s + 200] += r.standard_normal(200) * env_exp(200, 0.002) * r.uniform(0.5, 1.5)
    return x * env_adsr(n, 0.05, d * 0.3)


def chant(r, d=3.0, f=98):
    """low sung drone: saw voices through vowel formants, slow vibrato"""
    tt = t_(d)
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5 * tt)
    ph = 2 * np.pi * np.cumsum(f * vib) / SR
    src = sum(np.sin(ph * k) / k for k in range(1, 18))
    x = sum(band(src, fc * 0.85, fc * 1.15) * g for fc, g in ((500, 1.0), (900, 0.6), (2400, 0.25)))
    return reverb(x * env_adsr(len(tt), d * 0.25, d * 0.35), 1.8, 0.45, r)


# Checked before the organic rules; a boss's export script sets it (e.g. Overhead's industrial list).
EXTRA_RULES = []


def pick(name):
    """Map a sound id (without namespace) to (recipe, kwargs, pitch_scale)."""
    for key, fn, kw, *ps in EXTRA_RULES:
        if key in name:
            return fn, kw, (ps[0] if ps else 1.0)
    big = "bloom" in name or "slam" in name
    small = name.startswith("mite") or name.startswith("thrall") or name.startswith("drifter") or name.startswith("bile")
    rules = [
        ("heartbeat", heartbeat, {}), ("last_heartbeat", heartbeat, {}),
        ("laser_charge", laser_charge, {}), ("laser_loop", laser_loop, {}),
        ("retina_flash", ping, {}), ("retina_inhale", swell, {"d": 1.0}), ("eye_open", swell, {"d": 1.4}),
        ("ambient", ambient, {}), ("awaken", choir, {"d": 4.0}), ("victory", choir, {"d": 5.0}),
        (".warn", stinger, {}),
        ("nerve", zap, {"d": 1.0}), ("window_warn", zap, {"d": 0.7}),
        ("impact", boom, {}), ("collapse", boom, {"d": 2.2, "f0": 60}), ("core_burst", boom, {"d": 1.0, "f0": 140, "f1": 40}),
        ("eye_rupture", boom, {"d": 1.8, "f0": 120}), ("heart_split", boom, {"d": 2.0, "f0": 50, "f1": 22}),
        ("tear", squelch, {"d": 1.6, "bubbles": 40, "body": 0.8}), ("rupture", squelch, {"d": 1.2, "bubbles": 30, "body": 0.9}),
        ("pop", squelch, {"d": 0.35, "bubbles": 6, "lo": 500, "hi": 4000}), ("splat", squelch, {"d": 0.4, "bubbles": 8}),
        ("death_scream", roar, {"d": 4.0, "f0": 70, "f1": 30}), ("shriek", roar, {"d": 1.2, "f0": 260, "f1": 380, "formants": (900, 2000, 3600)}),
        ("death", roar, {"d": 2.2, "f0": 110, "f1": 45}), ("windup", roar, {"d": 2.2, "f0": 55, "f1": 75}),
        ("strain", roar, {"d": 0.9, "f0": 70, "f1": 90, "grit": 1.0}), ("snort", hiss, {"d": 0.6, "lo": 200, "hi": 2500, "a": 0.05}),
        ("hurt", roar, {"d": 0.5, "f0": 180, "f1": 120}), ("rise", roar, {"d": 2.5, "f0": 40, "f1": 70, "grit": 0.3}),
        ("whip", whip, {}), ("snap", clicks, {"d": 0.25, "count": 3, "lo": 800, "hi": 5000}), ("yank", squelch, {"d": 0.8, "bubbles": 18}),
        ("telegraph", hiss, {"d": 1.2}), ("gurgle", squelch, {"d": 1.2, "lo": 120, "hi": 700, "bubbles": 30}),
        ("spit", squelch, {"d": 0.35, "lo": 400, "hi": 3000, "bubbles": 5, "body": 0.2}),
        ("chomp", clicks, {"d": 0.3, "count": 4, "lo": 300, "hi": 3000}), ("gulp", squelch, {"d": 0.9, "lo": 100, "hi": 600, "bubbles": 6, "body": 1.0}),
        ("gallop", gallop, {}), ("step", step, {}), ("skitter", clicks, {"d": 0.4, "count": 10}),
        ("swell", swell, {"d": 1.0}), ("breathe", hiss, {"d": 2.5, "lo": 300, "hi": 2500, "a": 0.8}),
        ("puff", hiss, {"d": 0.8, "lo": 400, "hi": 5000, "a": 0.02}), ("birth", squelch, {"d": 1.5, "bubbles": 35, "body": 0.8}),
        ("tether", whip, {"d": 0.5}), ("emerge", squelch, {"d": 2.2, "lo": 80, "hi": 900, "bubbles": 40, "body": 1.0}),
        ("open", squelch, {"d": 0.7, "bubbles": 16}), ("close", squelch, {"d": 0.5, "bubbles": 10, "body": 0.6}),
        ("thunk", thump, {"d": 0.25, "f0": 160, "f1": 90, "noise": 0.8}), ("inject", clicks, {"d": 0.3, "count": 2, "lo": 2000, "hi": 9000}),
        ("taken", roar, {"d": 2.0, "f0": 50, "f1": 35, "grit": 0.2}), ("infection", swell, {"d": 0.8}),
        ("idle", squelch, {"d": 1.2, "bubbles": 10, "body": 0.3}), ("attack", whip, {"d": 0.4}),
    ]
    for key, fn, kw in rules:
        if key in name:
            return fn, kw, (0.7 if big else 1.35 if small else 1.0)
    return squelch, {}, 1.0


def synth(name):
    r = _rng(name)
    fn, kw, pscale = pick(name)
    x = fn(r, **kw)
    if pscale != 1.0 and fn not in (laser_loop, ambient):
        # resample to shift pitch (and length)
        idx = np.arange(0, len(x), pscale)
        x = np.interp(idx, np.arange(len(x)), x)
    return fade(norm(x))


def write(name, path):
    sf.write(path, synth(name).astype(np.float32), SR, format="OGG", subtype="VORBIS")
