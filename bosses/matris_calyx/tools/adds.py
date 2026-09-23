"""Infected adds birthed by spore vents: spore thrall (walker), spore mite (swarm bomber),
infected drifter (flying bladder that tethers to aircraft). Front faces north (-Z)."""
import boss_env  # noqa: F401  (shared pipeline + this boss)
import math
from lib import Model, P


def spore_thrall():
    m = Model("spore_thrall", seed=101)
    root = m.group("thrall", (0, 0, 0))
    body = m.group("body", (0, 14, 0), root)
    # hunched torso
    m.box(body, (0, 19, 1), [9, 10, 5], "flesh", rot=[18, 0, 0])
    m.box(body, (0, 16, 0), [8, 3, 5], "flesh_dark", rot=[10, 0, 0])
    m.box(body, (0, 22, 3.5), [7, 5, 2], "root", rot=[18, 0, 0])  # ragged clothing strip
    for k in range(4):  # exposed ribs
        m.box(body, (-2.5 + k * 1.7, 19 + (k % 2), -1.8), [1, 6, 1], "bone", rot=[18, 0, (k - 1.5) * 8])
    # the fungal growth bursting out of the back
    growth = m.group("growth", (0, 22, 4), body)
    m.blob(growth, (0, 24, 5), 3.5, "spore", n=5, jitter=0.4, rot_j=35)
    m.pustules(growth, (0, 25, 5), (4, 3, 2), 7, "bile", (1.2, 2.2))
    for k in range(3):
        a = -0.6 + k * 0.6
        m.box(growth, (math.sin(a) * 3, 28 + k % 2, 6), [1.2, 5, 1.2], "spore", rot=[-20, 0, math.degrees(a) * 0.8])
        m.box(growth, (math.sin(a) * 3.8, 31 + k % 2, 5.5), [2.6, 1.2, 2.6], "flesh_dark", rot=[-20, 0, math.degrees(a) * 0.8])
    m.locator(growth, "back", (0, 27, 6))
    head = m.group("head", (0, 24, -1), body)
    m.box(head, (0, 27, -2), [6, 6, 6], "mucus")
    m.box(head, (0, 26, -5.2), [4, 2, 0.6], "pupil")  # hollow eye slit
    m.box(head, (-1.4, 26.3, -5.4), [1, 1, 0.4], "iris")
    m.box(head, (0, 24.6, -4.6), [3.5, 1.5, 1.4], "tooth")
    # the head is split by a bloom of petals
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        m.box(head, (math.cos(a) * 2.2, 31, math.sin(a) * 2.2 - 1.5), [3, 4, 1], "flesh",
              rot=[math.sin(a) * 30, 0, -math.cos(a) * 30])
    m.box(head, (0, 30.5, -1.5), [2, 2, 2], "core")
    m.locator(head, "mouth", (0, 25, -6))
    arms = []
    for sx, tag in ((-1, "l"), (1, "r")):
        up = m.group(f"arm_{tag}", (sx * 5, 23, 0), body)
        m.box(up, (sx * 5.8, 19, -0.5), [2.4, 8, 2.4], "flesh", rot=[0, 0, sx * 6])
        low = m.group(f"forearm_{tag}", (sx * 6, 15, -0.5), up)
        m.box(low, (sx * 6.3, 10.5, -0.8), [2, 9, 2], "flesh_dark")
        for k in range(3):
            m.box(low, (sx * 6.3 + (k - 1) * 0.8, 5, -1.5), [0.7, 3.5, 0.7], "bone", rot=[-20, 0, (k - 1) * 12])
        arms.append((up, low))
    legs = []
    for sx, tag in ((-1, "l"), (1, "r")):
        lg = m.group(f"leg_{tag}", (sx * 2.2, 14, 0), root)
        m.box(lg, (sx * 2.2, 10.5, 0), [3, 7, 3], "flesh_dark")
        knee = m.group(f"shin_{tag}", (sx * 2.2, 7, 0), lg)
        m.box(knee, (sx * 2.2, 3.5, 0.3), [2.6, 7, 2.6], "flesh")
        m.box(knee, (sx * 2.2, 0.5, -0.8), [3, 1, 4], "root")
        legs.append((lg, knee))
    m.locator(root, "feet", (0, 0.5, 0))
    for k in ("spore_puff", "blood_burst", "flesh_chunks", "mucus_string", "spore_haze"):
        m.particles[k] = P[k]

    a = m.anim("idle", 3.0)
    for s in range(5):
        ph = 2 * math.pi * s / 4
        m.key(a, "body", "rotation", s * 0.75, [2 * math.sin(ph), 0, 1.5 * math.sin(ph / 2)])
        m.key(a, "head", "rotation", s * 0.75, [4 * math.sin(ph + 1), 10 * math.sin(ph / 2), 6 * math.sin(ph)])
        m.key(a, "growth", "scale", s * 0.75, 1 + 0.06 * math.sin(ph))
        for (up, low), sgn in zip(arms, (1, -1)):
            m.key(a, up, "rotation", s * 0.75, [3 * math.sin(ph + sgn), 0, 0])
    m.fx(a, 1.0, "spore_haze", "back")
    m.sfx(a, 0.0, "thrall.idle")

    a = m.anim("walk", 1.2)
    for s in range(9):
        t = 1.2 * s / 8
        x = 2 * math.pi * s / 8
        for (lg, kn), ph in zip(legs, (0, math.pi)):
            m.key(a, lg, "rotation", t, [28 * math.sin(x + ph), 0, 0])
            m.key(a, kn, "rotation", t, [max(0, 30 * math.sin(x + ph + 1.2)), 0, 0])
        for (up, low), ph in zip(arms, (math.pi, 0)):
            m.key(a, up, "rotation", t, [-18 * math.sin(x + ph) - 10, 0, 0])
            m.key(a, low, "rotation", t, [-15, 0, 0])
        m.key(a, "body", "rotation", t, [6, 5 * math.sin(x), 4 * math.sin(x)])
        m.key(a, "body", "position", t, [0, -abs(math.sin(x)) * 0.8, 0])
        m.key(a, "head", "rotation", t, [0, -6 * math.sin(x), 10 * math.sin(x * 2)])
    m.sfx(a, 0.0, "thrall.step")
    m.sfx(a, 0.6, "thrall.step")

    a = m.anim("attack", 0.8, "once")
    for (up, low), d in zip(arms, (0, 0.08)):
        m.keys(a, up, "rotation", [(0, [0, 0, 0]), (0.25 + d, [-150, 0, 0]), (0.45 + d, [-30, 0, 0]), (0.8, [0, 0, 0])])
        m.keys(a, low, "rotation", [(0, [0, 0, 0]), (0.25 + d, [-30, 0, 0]), (0.45 + d, [0, 0, 0])])
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (0.25, [-10, 0, 0]), (0.45, [22, 0, 0]), (0.8, [0, 0, 0])])
    m.keys(a, "head", "rotation", [(0.2, [0, 0, 0]), (0.3, [-25, 0, 0]), (0.5, [5, 0, 0])])
    m.fx(a, 0.45, "mucus_string", "mouth")
    m.sfx(a, 0.2, "thrall.attack")

    a = m.anim("hurt", 0.4, "once")
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (0.08, [-14, 8, 0]), (0.4, [0, 0, 0])])
    m.fx(a, 0.02, "blood_burst", "back")
    m.sfx(a, 0.0, "thrall.hurt")

    a = m.anim("death", 1.6, "hold")
    m.keys(a, "thrall", "rotation", [(0, [0, 0, 0]), (0.3, [-10, 0, 5]), (1.0, [85, 0, 10]), (1.2, [80, 0, 8]), (1.6, [82, 0, 9])])
    m.keys(a, "thrall", "position", [(0, [0, 0, 0]), (1.0, [0, 0, 0]), (1.6, [0, 0, 0])])
    m.keys(a, "growth", "scale", [(0, 1), (0.9, 1.6), (1.0, 0.3), (1.6, 0.3)])
    m.keys(a, "head", "rotation", [(0, [0, 0, 0]), (0.6, [30, 30, 0]), (1.6, [20, 40, 20])])
    m.fx(a, 1.0, "spore_puff", "back")
    m.fx(a, 1.02, "flesh_chunks", "back")
    m.sfx(a, 0.0, "thrall.death")

    a = m.anim("emerge", 1.8, "once")
    m.keys(a, "thrall", "position", [(0, [0, -30, 0]), (0.9, [0, -8, 0]), (1.4, [0, 1, 0]), (1.8, [0, 0, 0])])
    m.keys(a, "body", "rotation", [(0, [60, 0, 0]), (1.4, [-10, 0, 0]), (1.8, [0, 0, 0])])
    m.fx(a, 0.9, "mucus_string", "mouth")
    m.sfx(a, 0.0, "thrall.emerge")
    return m


def spore_mite():
    m = Model("spore_mite", seed=202)
    root = m.group("mite", (0, 0, 0))
    body = m.group("body", (0, 4, 0), root)
    m.box(body, (0, 4, -2), [5, 3, 4], "chitin")
    m.box(body, (0, 5.2, -2), [4, 1, 3.6], "flesh_dark")
    sac = m.group("sac", (0, 4.5, 1), body)
    m.blob(sac, (0, 5.5, 3), 2.8, "spore", n=4, jitter=0.25, rot_j=25)
    m.pustules(sac, (0, 6, 3), (2.5, 2, 2.5), 6, "bile", (0.8, 1.4))
    m.box(sac, (0, 6.5, 3), [3, 1, 3], "vein", rot=[0, 45, 0])
    m.locator(sac, "sac", (0, 6, 3))
    head = m.group("head", (0, 4, -4), body)
    m.box(head, (0, 3.8, -5), [3, 2.2, 2], "chitin")
    for sx in (-1, 1):
        m.box(head, (sx * 0.8, 4.3, -6.1), [0.8, 0.8, 0.4], "iris")
        m.box(head, (sx * 0.9, 3, -6.6), [0.5, 0.5, 1.8], "tooth", rot=[20, sx * 15, 0])
    legs = []
    for i in range(3):
        for sx, tag in ((-1, "l"), (1, "r")):
            z = -3.5 + i * 1.8
            lg = m.group(f"leg_{tag}{i}", (sx * 2.2, 4, z), body, rot=[0, sx * (i - 1) * -25, 0])
            m.box(lg, (sx * 3.6, 4.8, z), [3, 0.7, 0.7], "chitin", rot=[0, 0, sx * -25])
            m.box(lg, (sx * 5.4, 2.4, z), [0.6, 4.5, 0.6], "chitin", rot=[0, 0, sx * 15])
            legs.append(lg)
    for k in ("spore_puff", "spore_haze", "bile_splash"):
        m.particles[k] = P[k]

    a = m.anim("idle", 1.0)
    m.keys(a, "sac", "scale", [(0, 1), (0.5, 1.08), (1.0, 1)])
    m.keys(a, "head", "rotation", [(0, [0, 0, 0]), (0.3, [0, 12, 0]), (0.6, [0, -10, 0]), (1.0, [0, 0, 0])])

    a = m.anim("walk", 0.4)
    for idx, lg in enumerate(legs):
        ph = (idx % 2) * math.pi + (idx // 2) * 0.4
        for s in range(5):
            t = 0.4 * s / 4
            x = 2 * math.pi * s / 4 + ph
            m.key(a, lg, "rotation", t, [0, 20 * math.sin(x), 12 * max(0, math.cos(x)) * (1 if idx % 2 else -1)])
    m.keys(a, "body", "position", [(0, [0, 0, 0]), (0.1, [0, 0.4, 0]), (0.2, [0, 0, 0]), (0.3, [0, 0.4, 0]), (0.4, [0, 0, 0])])
    m.sfx(a, 0.0, "mite.skitter")

    a = m.anim("swell", 1.0, "hold")
    m.keys(a, "sac", "scale", [(0, 1), (0.6, 1.5), (0.7, 1.4), (0.8, 1.7), (0.9, 1.6), (1.0, 1.9)])
    for lg in legs:
        m.keys(a, lg, "rotation", [(0, [0, 0, 0]), (1.0, [0, 0, 0])])
    m.keys(a, "body", "position", [(0, [0, 0, 0]), (1.0, [0, -1.5, 0])])
    m.fx(a, 0.5, "spore_haze", "sac")
    m.sfx(a, 0.0, "mite.swell")

    a = m.anim("death", 0.5, "hold")
    m.keys(a, "sac", "scale", [(0, 1.9), (0.08, 2.4), (0.12, 0.01), (0.5, 0.01)])
    m.keys(a, "body", "scale", [(0.1, 1), (0.3, [1.2, 0.3, 1.2]), (0.5, [1.2, 0.2, 1.2])])
    m.fx(a, 0.1, "spore_puff", "sac")
    m.fx(a, 0.11, "bile_splash", "sac")
    m.sfx(a, 0.08, "mite.pop")
    return m


def infected_drifter():
    m = Model("infected_drifter", seed=303)
    root = m.group("drifter", (0, 0, 0))
    bladder = m.group("bladder", (0, 20, 0), root)
    m.blob(bladder, (0, 22, 0), 8, "mucus", n=7, jitter=0.25, rot_j=25)
    m.box(bladder, (0, 22, 0), [13, 13, 13], "flesh", inflate=0.2)
    for i in range(6):  # vein straps
        a = i * math.pi / 3
        m.box(bladder, (math.cos(a) * 6.8, 22, math.sin(a) * 6.8), [1.4, 13, 1.4], "vein", rot=[0, -math.degrees(a), 0])
    m.pustules(bladder, (0, 27, 0), (6, 2, 6), 8, "bile", (1.2, 2.4))
    m.box(bladder, (0, 29, 0), [6, 2, 6], "flesh_dark")
    m.box(bladder, (0, 30, 0), [3, 2, 3], "core")  # gas vent
    # cluster of eyes on the front
    for i, (x, y) in enumerate([(-2.5, 24), (2, 25), (0, 21), (3.5, 20.5), (-3.5, 20)]):
        s = 2.4 if i == 2 else 1.6
        m.box(bladder, (x, y, -7.1), [s, s, 0.6], "sclera")
        m.box(bladder, (x, y, -7.5), [s * 0.5, s * 0.6, 0.4], "pupil")
    m.locator(bladder, "top", (0, 31, 0))
    maw = m.group("maw", (0, 14, 0), bladder)
    m.box(maw, (0, 14, 0), [7, 3, 7], "flesh_dark")
    m.ring(maw, (0, 13, 0), 2.6, 8, [1, 2.2, 1], "tooth", axis="y", tilt=20)
    m.locator(maw, "maw", (0, 12, 0))
    tendrils = []
    for t in range(5):
        a = 2 * math.pi * t / 5 + 0.4
        bx, bz = math.cos(a) * 4.5, math.sin(a) * 4.5
        chain = []
        parent = maw
        yy = 14
        for s in range(4):
            b = m.group(f"tendril_{t}_{s}", (bx, yy, bz), parent)
            r = 0.9 - s * 0.15
            m.box(b, (bx, yy - 3, bz), [r * 2, 6.5, r * 2], "flesh" if s % 2 == 0 else "flesh_dark")
            chain.append(b)
            parent = b
            yy -= 6
        m.box(chain[-1], (bx, yy - 0.5, bz), [1.4, 1.4, 1.4], "bile")
        tendrils.append(chain)
    m.locator(tendrils[0][-1], "hook", (math.cos(0.4) * 4.5, -10, math.sin(0.4) * 4.5))
    for k in ("bile_drip", "spore_puff", "blood_burst", "flesh_chunks", "mucus_string"):
        m.particles[k] = P[k]

    a = m.anim("idle", 4.0)
    for s in range(9):
        t = s * 0.5
        ph = 2 * math.pi * s / 8
        m.key(a, "drifter", "position", t, [0, 1.5 * math.sin(ph), 0])
        m.key(a, "bladder", "scale", t, [1 + 0.05 * math.sin(ph * 2), 1 - 0.04 * math.sin(ph * 2), 1 + 0.05 * math.sin(ph * 2)])
        m.key(a, "drifter", "rotation", t, [3 * math.sin(ph), 0, 3 * math.cos(ph)])
    for i, chain in enumerate(tendrils):
        for j, b in enumerate(chain):
            for s in range(9):
                v = 12 * math.sin(2 * math.pi * (s / 8 - j * 0.12 + i / 5))
                m.key(a, b, "rotation", s * 0.5, [v, 0, v * 0.5])
    m.fx(a, 1.0, "bile_drip", "maw")
    m.sfx(a, 0.0, "drifter.idle")

    # tether: tendrils lash downward and forward to hook an aircraft
    a = m.anim("tether", 1.2, "hold")
    for i, chain in enumerate(tendrils):
        for j, b in enumerate(chain):
            m.keys(a, b, "rotation", [(0, [0, 0, 0]), (0.3, [-25 - j * 6, 0, 0]), (0.5, [-40 - j * 8, 0, 0]), (1.2, [-35 - j * 7, 0, 4])])
    m.keys(a, "bladder", "scale", [(0, 1), (0.3, [1.15, 0.9, 1.15]), (0.5, 1)])
    m.fx(a, 0.5, "mucus_string", "hook")
    m.sfx(a, 0.3, "drifter.tether")

    a = m.anim("hurt", 0.4, "once")
    m.keys(a, "bladder", "scale", [(0, 1), (0.08, [0.85, 1.15, 0.85]), (0.4, 1)])
    m.fx(a, 0.02, "blood_burst", "top")
    m.sfx(a, 0.0, "drifter.hurt")

    a = m.anim("death", 1.0, "hold")
    m.keys(a, "bladder", "scale", [(0, 1), (0.25, 1.35), (0.3, 0.05), (1.0, 0.05)])
    m.keys(a, "drifter", "position", [(0.3, [0, 0, 0]), (1.0, [0, -24, 0])])
    for chain in tendrils:
        m.keys(a, chain[0], "rotation", [(0.3, [0, 0, 0]), (1.0, [30, 0, 20])])
    m.fx(a, 0.3, "spore_puff", "top")
    m.fx(a, 0.31, "flesh_chunks", "top")
    m.sfx(a, 0.28, "drifter.pop")
    return m


ALL = [spore_thrall, spore_mite, infected_drifter]
