"""Spore vent (breakable add spawner) and bile glob (Spitting Arm / Bile Rain projectile)."""
import boss_env  # noqa: F401  (shared pipeline + this boss)
import math
from lib import Model, P


def spore_vent():
    m = Model("spore_vent", seed=88)
    R = m.rng
    root = m.group("vent", (0, 0, 0))
    base = m.group("base", (0, 0, 0), root)
    m.blob(base, (0, 5, 0), 9, "flesh_dark", n=6, rot_j=25)
    m.blob(base, (0, 1.5, 0), 9, "root", n=4, rot_j=20)
    for i in range(5):
        a = 2 * math.pi * i / 5 + R.uniform(-0.2, 0.2)
        for s in range(3):
            d = 14 + s * 7
            m.box(base, (math.cos(a) * d, 2 - s * 0.4, math.sin(a) * d), [8, 4 - s, 4 - s], "root",
                  rot=[0, -math.degrees(a), 0])
    m.pustules(base, (0, 7, 0), (10, 2, 10), 8, "spore", (2.5, 4))
    chim = []
    parent = base
    y = 8
    for i in range(3):
        b = m.group(f"chimney_{i}", (0, y, 0), parent)
        r = 8 - i * 1.2
        m.box(b, (0, y + 5, 0), [r * 2, 11, r * 2], "flesh")
        m.box(b, (0, y + 1, 0), [r * 2 + 1.5, 2, r * 2 + 1.5], "bone" if i == 1 else "flesh_dark")
        m.pustules(b, (0, y + 5, 0), (r, 4, r), 3, "spore", (2, 3))
        m.veins(b, (0, y + 10, -r), (0, -1, 0), 10, count=1, mat="vein", thick=0.9)
        chim.append(b)
        parent = b
        y += 10
    mouth = m.group("sphincter", (0, y, 0), chim[-1])
    m.box(mouth, (0, y + 1, 0), [11, 2, 11], "flesh_dark")
    m.box(mouth, (0, y + 0.5, 0), [5, 2.2, 5], "pupil")
    petals = []
    for i in range(5):
        a = 2 * math.pi * i / 5
        pg = m.group(f"flap_{i}", (math.cos(a) * 5, y + 1, math.sin(a) * 5), mouth, rot=[0, -math.degrees(a) - 90, 0])
        m.box(pg, (math.cos(a) * 6, y + 4, math.sin(a) * 6), [6, 7, 1.6], "flesh", rot=[0, -math.degrees(a) - 90, -15])
        m.box(pg, (math.cos(a) * 6.6, y + 7.5, math.sin(a) * 6.6), [1, 2.5, 1], "tooth", rot=[0, -math.degrees(a) - 90, -15])
        petals.append(pg)
    m.locator(mouth, "spout", (0, y + 3, 0))
    m.locator(base, "base", (0, 2, 0))
    for k in ("spore_puff", "spore_haze", "flesh_chunks", "blood_burst", "mucus_string", "root_dust"):
        m.particles[k] = P[k]

    def flaps(a, t, deg):
        for i in range(5):
            m.key(a, f"flap_{i}", "rotation", t, [0, 0, deg])

    a = m.anim("idle", 4.0)
    for s in range(5):
        ph = 2 * math.pi * s / 4
        m.key(a, "chimney_0", "scale", s, [1 + 0.05 * math.sin(ph), 1, 1 + 0.05 * math.sin(ph)])
        m.key(a, "chimney_2", "scale", s, [1 + 0.07 * math.sin(ph - 1), 1, 1 + 0.07 * math.sin(ph - 1)])
        flaps(a, s, 8 * math.sin(ph))
    m.fx(a, 0.2, "spore_haze", "spout")
    m.sfx(a, 0.0, "vent.breathe")

    a = m.anim("puff", 1.2, "once")
    for i, b in enumerate(chim):
        t = i * 0.12
        m.keys(a, b, "scale", [(t, 1), (t + 0.15, [1.25, 0.95, 1.25]), (t + 0.35, 1)])
    flaps(a, 0.35, 0)
    flaps(a, 0.45, 45)
    flaps(a, 1.2, 0)
    m.fx(a, 0.45, "spore_puff", "spout")
    m.sfx(a, 0.4, "vent.puff")

    # spawn_add: sphincter dilates wide and births an infected add (2.0 s)
    a = m.anim("spawn_add", 2.0, "once")
    m.keys(a, "sphincter", "scale", [(0, 1), (0.8, [1.6, 1, 1.6]), (1.4, [1.7, 1, 1.7]), (2.0, 1)])
    flaps(a, 0, 0)
    flaps(a, 0.8, 70)
    flaps(a, 1.5, 65)
    flaps(a, 2.0, 0)
    for i, b in enumerate(chim):
        t = 0.2 + (2 - i) * 0.2
        m.keys(a, b, "scale", [(t, 1), (t + 0.2, [1.35, 1, 1.35]), (t + 0.45, 1)])
    m.fx(a, 0.8, "mucus_string", "spout")
    m.fx(a, 1.1, "blood_burst", "spout")
    m.fx(a, 1.12, "spore_puff", "spout")
    m.sfx(a, 0.2, "vent.strain")
    m.sfx(a, 1.1, "vent.birth")

    a = m.anim("hurt", 0.4, "once")
    m.keys(a, "vent", "scale", [(0, 1), (0.08, [1.1, 0.9, 1.1]), (0.4, 1)])
    m.fx(a, 0.02, "spore_puff", "spout")
    m.sfx(a, 0.0, "vent.hurt")

    a = m.anim("broken", 2.5, "hold")
    m.keys(a, "chimney_2", "rotation", [(0, [0, 0, 0]), (0.3, [-10, 0, 8]), (1.0, [35, 0, -20]), (2.5, [60, 0, -30])])
    m.keys(a, "chimney_1", "rotation", [(0, [0, 0, 0]), (0.5, [8, 0, 10]), (1.2, [25, 0, 5]), (2.5, [30, 0, 8])])
    m.keys(a, "chimney_0", "scale", [(0, 1), (0.6, [1.3, 0.7, 1.3]), (2.5, [1.35, 0.5, 1.35])])
    m.keys(a, "sphincter", "scale", [(0, 1), (0.2, 1.4), (0.35, 0.6), (2.5, 0.5)])
    flaps(a, 0.3, 90)
    flaps(a, 2.5, 110)
    m.fx(a, 0.2, "spore_puff", "spout")
    m.fx(a, 0.25, "flesh_chunks", "spout")
    m.fx(a, 0.3, "blood_burst", "spout")
    m.fx(a, 0.9, "root_dust", "base")
    m.sfx(a, 0.0, "vent.rupture")
    return m


def bile_glob():
    m = Model("bile_glob", seed=99)
    root = m.group("glob", (0, 4, 0))
    m.blob(root, (0, 4, 0), 3, "bile", n=4, jitter=0.3, rot_j=30)
    m.box(root, (0, 4, 0), [3, 3, 3], "glow_vein", inflate=0.2)
    m.pustules(root, (0, 4, 0), (2.5, 2.5, 2.5), 4, "bile", (1, 1.8))
    tail = m.group("tail", (0, 4, 3), root)
    for k in range(3):
        m.box(tail, (0, 4, 4 + k * 2.2), [2.4 - k * 0.6, 2.4 - k * 0.6, 2.4], "bile")
    m.locator(root, "trail", (0, 4, 5))
    m.particles["bile_drip"] = P["bile_drip"]
    m.particles["bile_splash"] = P["bile_splash"]
    a = m.anim("fly", 0.5)
    m.keys(a, "glob", "rotation", [(0, [0, 0, 0]), (0.5, [0, 0, 360])], "linear")
    m.keys(a, "glob", "scale", [(0, [1, 1, 1]), (0.25, [1.12, 0.9, 1.12]), (0.5, [1, 1, 1])])
    m.keys(a, "tail", "rotation", [(0, [0, -10, 0]), (0.25, [0, 10, 0]), (0.5, [0, -10, 0])])
    m.fx(a, 0.0, "bile_drip", "trail")
    a = m.anim("burst", 0.3, "hold")
    m.keys(a, "glob", "scale", [(0, 1), (0.08, 1.6), (0.15, 0)])
    m.fx(a, 0.05, "bile_splash", "trail")
    m.sfx(a, 0.0, "bile.splat")
    return m


ALL = [spore_vent, bile_glob]
