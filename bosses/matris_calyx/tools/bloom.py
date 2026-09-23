"""Calyx Bloom: phase-3 organ. The split heart peels open (six lobes), a stalk lifts a building-scale eye
ringed by eight toothed petals and hanging tendrils. The eye faces north (-Z); the renderer yaws the whole
entity toward the laser target, the 'eye' bone adds fine tracking."""
import boss_env  # noqa: F401  (shared pipeline + this boss)
import math
from lib import Model, P


def bloom():
    m = Model("calyx_bloom", seed=77)
    R = m.rng
    root = m.group("bloom", (0, 0, 0))

    # ---- heart base: arteries into the island, six lobes that were the closed heart
    base = m.group("heart_base", (0, 0, 0), root)
    m.blob(base, (0, 10, 0), 30, "flesh_dark", n=8, jitter=0.3, rot_j=12)
    m.blob(base, (0, 4, 0), 36, "root", n=4, jitter=0.15, rot_j=6)
    for i in range(10):
        a = 2 * math.pi * i / 10 + R.uniform(-0.15, 0.15)
        grp = m.group(f"artery_{i}", (math.cos(a) * 30, 4, math.sin(a) * 30), base)
        for s in range(5):
            d = 30 + s * 11
            th = 9 - s * 1.4
            m.box(grp, (math.cos(a) * d, th / 2, math.sin(a) * d), [12, th, th], "vein" if s % 2 else "flesh_dark",
                  rot=[0, -math.degrees(a), R.uniform(-6, 6)])
        m.box(grp, (math.cos(a) * 44, 8, math.sin(a) * 44), [4, 4, 4], "bile", rot=[30, 20, 10])
    m.pustules(base, (0, 20, 0), (28, 6, 28), 18, "bile", (3, 6))
    m.pustules(base, (0, 16, 0), (30, 4, 30), 10, "spore", (3, 5))
    for i in range(6):
        a = 2 * math.pi * i / 6 + math.pi / 6
        m.locator(base, f"vent_{i}", (math.cos(a) * 34, 18, math.sin(a) * 34))
    lobes = []
    for i in range(6):
        a = 2 * math.pi * i / 6
        yaw = m.group(f"lobe_{i}_yaw", (0, 22, 0), base, rot=[0, -math.degrees(a), 0])
        # local frame: lobe hinges at +X 24 and rises along +Y; hinge rotates on Z (negative = open)
        lg = m.group(f"lobe_{i}", (24, 22, 0), yaw, rot=[0, 0, -55])
        for k in range(4):
            y = 28 + k * 12
            x = 24 - k * 4.5
            m.box(lg, (x, y, 0), [6, 13, 22 - k * 3], "flesh" if k % 2 == 0 else "flesh_dark", rot=[0, 0, 8])
            m.box(lg, (x + 3.2, y, 0), [2, 12, 4], "vein", rot=[0, 0, 8])
            m.box(lg, (x - 3.2, y, 5 - k), [1.5, 3, 1.5], "tooth")
        m.box(lg, (8, 76, 0), [5, 8, 6], "tooth", rot=[0, 0, 20])
        m.locator(lg, f"lobe_tip_{i}", (8, 80, 0))
        lobes.append(lg)

    # ---- stalk
    stalk = []
    parent = root
    y = 30
    for i in range(3):
        b = m.group(f"stalk_{i}", (0, y, 0), parent)
        r = 13 - i * 1.5
        m.box(b, (0, y + 10, 0), [r * 2, 22, r * 2], "flesh")
        m.box(b, (0, y + 4, 0), [r * 2 + 2, 3, r * 2 + 2], "flesh_dark")
        m.box(b, (0, y + 14, 0), [r * 2 + 1.5, 2, r * 2 + 1.5], "bone")
        m.veins(b, (0, y + 20, -r), (0, -1, 0), 20, count=2, mat="glow_vein", thick=1.2)
        stalk.append(b)
        parent = b
        y += 20

    # ---- crown: socket ring, petals, tendrils, eye
    crown = m.group("crown", (0, y, 0), stalk[-1])
    cy = y + 26
    m.ring(crown, (0, cy, 4), 27, 16, [8, 12, 12], "flesh_dark", axis="z")
    m.ring(crown, (0, cy, 1), 23, 16, [3, 5, 4], "mucus", axis="z", phase=0.2)
    m.box(crown, (0, cy, 16), [44, 44, 14], "flesh")
    m.box(crown, (0, cy - 20, 8), [30, 12, 24], "flesh_dark")
    petals = []
    for i in range(8):
        a = 2 * math.pi * i / 8 + math.pi / 8
        hx, hy = math.cos(a) * 26, cy + math.sin(a) * 26
        roll = m.group(f"petal_{i}_roll", (hx, hy, 2), crown, rot=[0, 0, math.degrees(a) - 90])
        # local frame: petal grows along +Y from its hinge; hinge rotates on X (negative = flare back)
        pg = m.group(f"petal_{i}", (hx, hy, 2), roll, rot=[-12, 0, 0])
        for k in range(3):
            y = hy + 6 + k * 13
            L = 14 - k * 2
            m.box(pg, (hx, y, 2 - k * 3), [16 - k * 4, L, 4], "flesh" if k != 1 else "flesh_dark", rot=[-8 * k, 0, 0])
            m.box(pg, (hx, y, 4.5 - k * 3), [2, L, 2], "vein", rot=[-8 * k, 0, 0])
            for side in (-1, 1):
                m.box(pg, (hx + side * (8 - k * 2), y, -1 - k * 3), [2, 2, 5], "tooth", rot=[0, 0, side * 20])
        m.box(pg, (hx, hy + 44, -7), [4, 6, 4], "bone", rot=[-25, 0, 0])
        petals.append(pg)

    eye = m.group("eye", (0, cy, -6), crown)
    m.box(eye, (0, cy, -6), [40, 40, 30], "sclera", inflate=0.5)
    m.box(eye, (0, cy, -6), [44, 30, 26], "sclera")
    m.box(eye, (0, cy, -6), [30, 44, 26], "sclera")
    # bloodshot veins on the sclera front
    for i in range(12):
        a = 2 * math.pi * i / 12 + R.uniform(-0.1, 0.1)
        for k in range(3):
            d = 13 + k * 3.5
            m.box(eye, (math.cos(a) * d, cy + math.sin(a) * d, -21.6), [3.5, 0.9, 0.4], "flesh",
                  rot=[0, 0, math.degrees(a) + R.uniform(-20, 20)])
    iris = m.group("iris", (0, cy, -21), eye)
    m.box(iris, (0, cy, -21.4), [22, 22, 1], "iris")
    m.box(iris, (0, cy, -21.5), [26, 14, 0.8], "iris")
    m.box(iris, (0, cy, -21.5), [14, 26, 0.8], "iris")
    pupil = m.group("pupil", (0, cy, -22), iris)
    m.box(pupil, (0, cy, -22.2), [5, 16, 1], "pupil")
    m.box(pupil, (0, cy, -22.2), [8, 9, 0.8], "pupil")
    m.box(iris, (5, cy + 5, -22.6), [2.5, 2.5, 0.5], "sclera")  # specular glint
    lid_t = m.group("lid_top", (0, cy + 20, -4), crown)
    m.box(lid_t, (0, cy + 22, -10), [46, 8, 26], "flesh_dark")
    m.box(lid_t, (0, cy + 18.5, -22.5), [42, 2, 2], "chitin")
    for k in range(9):
        m.box(lid_t, (-18 + k * 4.5, cy + 17, -23.5), [0.8, 4, 0.8], "chitin", rot=[-30, 0, 0])
    lid_b = m.group("lid_bottom", (0, cy - 20, -4), crown)
    m.box(lid_b, (0, cy - 22, -10), [46, 8, 26], "flesh_dark")
    m.box(lid_b, (0, cy - 18.5, -22.5), [42, 2, 2], "chitin")
    m.locator(pupil, "laser", (0, cy, -26))
    m.locator(eye, "glint", (5, cy + 5, -24))

    tendrils = []
    for t in range(6):
        a = 2 * math.pi * t / 6 + 0.3
        bx, bz = math.cos(a) * 20, 10 + math.sin(a) * 8
        chain = []
        parent = crown
        yy = cy - 22
        for s in range(5):
            b = m.group(f"tendril_{t}_{s}", (bx, yy, bz), parent)
            r = 3 - s * 0.45
            m.box(b, (bx, yy - 5, bz), [r * 2, 11, r * 2], "flesh" if s % 2 == 0 else "flesh_dark")
            m.box(b, (bx, yy - 2, bz), [r * 2 + 1, 1.2, r * 2 + 1], "vein")
            chain.append(b)
            parent = b
            yy -= 10
        m.box(chain[-1], (bx, yy - 1, bz), [1.6, 4, 1.6], "tooth")
        m.locator(chain[-1], f"drip_{t}", (bx, yy - 3, bz))
        tendrils.append(chain)

    for k in ("laser_charge", "laser_beam", "eye_glint", "blood_burst", "flesh_chunks", "spore_puff", "spore_haze",
              "bile_drip", "mucus_string", "root_dust", "core_glow"):
        m.particles[k] = P[k]

    def petals_open(a, t, deg):
        for i in range(8):
            m.key(a, f"petal_{i}", "rotation", t, [deg, 0, 0])

    # ---- idle: breathing, petals slowly flex, eye darts, tendrils sway
    a = m.anim("idle", 6.0)
    for s in range(7):
        t = s
        ph = 2 * math.pi * s / 6
        m.key(a, "crown", "scale", t, 1 + 0.02 * math.sin(ph))
        m.key(a, "heart_base", "scale", t, [1 + 0.015 * math.sin(ph + 1), 1 - 0.01 * math.sin(ph + 1), 1 + 0.015 * math.sin(ph + 1)])
        petals_open(a, t, -6 * math.sin(ph))
    m.keys(a, "eye", "rotation", [(0, [0, 0, 0]), (0.8, [0, 0, 0]), (0.9, [4, 10, 0]), (2.2, [4, 10, 0]), (2.3, [-6, -8, 0]),
                                  (3.6, [-6, -8, 0]), (3.7, [2, -2, 0]), (5.2, [2, -2, 0]), (5.4, [0, 0, 0]), (6, [0, 0, 0])], "linear")
    m.keys(a, "lid_top", "rotation", [(0, [0, 0, 0]), (4.4, [0, 0, 0]), (4.5, [-28, 0, 0]), (4.6, [0, 0, 0]), (6, [0, 0, 0])], "linear")
    m.keys(a, "lid_bottom", "rotation", [(0, [0, 0, 0]), (4.4, [0, 0, 0]), (4.5, [28, 0, 0]), (4.6, [0, 0, 0]), (6, [0, 0, 0])], "linear")
    for t, chain in enumerate(tendrils):
        for i, b in enumerate(chain):
            for s in range(7):
                v = 7 * math.sin(2 * math.pi * (s / 6 - i * 0.12 + t / 6))
                m.key(a, b, "rotation", s, [v, 0, v * 0.6])
    m.fx(a, 0.5, "eye_glint", "glint")
    m.fx(a, 1.5, "bile_drip", "drip_1")
    m.fx(a, 3.2, "bile_drip", "drip_4")
    m.fx(a, 2.0, "spore_haze", "vent_0")
    m.fx(a, 4.0, "spore_haze", "vent_3")
    m.sfx(a, 0.0, "bloom.heartbeat")
    m.sfx(a, 3.0, "bloom.heartbeat")

    # ---- emerge: the heart splits, lobes peel outward, stalk rises, petals unfurl, eye opens (8 s)
    a = m.anim("emerge", 8.0, "once")
    for i in range(6):
        m.keys(a, f"lobe_{i}", "rotation", [(0, [0, 0, 55]), (0.8, [0, 0, 55]), (1.0, [0, 0, 49]), (1.3, [0, 0, 57]),
                                            (2.2, [0, 0, 51]), (3.4, [0, 0, -6]), (3.8, [0, 0, 2]), (8, [0, 0, 0])])
    m.keys(a, "stalk_0", "position", [(0, [0, -60, 0]), (3.0, [0, -60, 0]), (5.2, [0, 3, 0]), (5.8, [0, -1, 0]), (8, [0, 0, 0])])
    m.keys(a, "stalk_0", "scale", [(0, [0.8, 0.5, 0.8]), (3.0, [0.8, 0.5, 0.8]), (5.2, [1, 1.05, 1]), (8, [1, 1, 1])])
    petals_open(a, 0, 70)
    petals_open(a, 5.0, 70)
    petals_open(a, 6.6, -18)
    petals_open(a, 7.1, -4)
    petals_open(a, 8.0, -6)
    m.keys(a, "lid_top", "rotation", [(0, [30, 0, 0]), (6.8, [30, 0, 0]), (7.2, [-8, 0, 0]), (8, [0, 0, 0])])
    m.keys(a, "lid_bottom", "rotation", [(0, [-30, 0, 0]), (6.8, [-30, 0, 0]), (7.2, [8, 0, 0]), (8, [0, 0, 0])])
    m.keys(a, "pupil", "scale", [(6.8, [1, 1, 1]), (7.2, [0.5, 1.2, 1]), (8, [1, 1, 1])])
    m.keys(a, "heart_base", "scale", [(0, 1), (0.9, [1.05, 0.95, 1.05]), (1.2, 1), (2.0, [1.06, 0.95, 1.06]), (2.3, 1)])
    m.fx(a, 1.0, "blood_burst", "vent_0")
    m.fx(a, 2.0, "blood_burst", "vent_3")
    m.fx(a, 3.3, "flesh_chunks", "vent_1")
    m.fx(a, 3.35, "flesh_chunks", "vent_4")
    m.fx(a, 3.4, "root_dust", "vent_2")
    m.fx(a, 3.45, "root_dust", "vent_5")
    m.fx(a, 5.2, "mucus_string", "glint")
    m.fx(a, 7.2, "eye_glint", "glint")
    m.sfx(a, 0.9, "bloom.heart_split")
    m.sfx(a, 3.3, "bloom.tear")
    m.sfx(a, 5.0, "bloom.rise")
    m.sfx(a, 7.1, "bloom.eye_open")

    # ---- laser_charge (2.0 s): petals flare back, pupil pinches to a slit, glow gathers
    a = m.anim("laser_charge", 2.0, "hold")
    petals_open(a, 0, -6)
    petals_open(a, 1.6, -32)
    petals_open(a, 2.0, -35)
    m.keys(a, "pupil", "scale", [(0, [1, 1, 1]), (1.2, [0.35, 1.3, 1]), (2.0, [0.25, 1.35, 1])])
    m.keys(a, "iris", "scale", [(0, 1), (2.0, 1.15)])
    m.keys(a, "lid_top", "rotation", [(0, [0, 0, 0]), (1.5, [-14, 0, 0])])
    m.keys(a, "lid_bottom", "rotation", [(0, [0, 0, 0]), (1.5, [14, 0, 0])])
    for i in range(8):
        m.key(a, "eye", "position", 1.2 + i * 0.1, [(-1) ** i * 0.5, (-1) ** (i // 2) * 0.4, 0])
    m.fx(a, 0.0, "laser_charge", "laser")
    m.sfx(a, 0.0, "bloom.laser_charge")

    # ---- laser_fire (3.0 s loop while beam is active): recoil, jitter, beam particles
    a = m.anim("laser_fire", 3.0)
    petals_open(a, 0, -35)
    petals_open(a, 3.0, -35)
    m.keys(a, "pupil", "scale", [(0, [0.25, 1.35, 1]), (3.0, [0.25, 1.35, 1])])
    m.keys(a, "iris", "scale", [(0, 1.15), (3.0, 1.15)])
    m.keys(a, "lid_top", "rotation", [(0, [-14, 0, 0]), (3.0, [-14, 0, 0])])
    m.keys(a, "lid_bottom", "rotation", [(0, [14, 0, 0]), (3.0, [14, 0, 0])])
    for i in range(16):
        m.key(a, "eye", "position", i * 0.2, [((-1) ** i) * 0.4, 0.3 * math.sin(i), 1.5 + 0.5 * ((-1) ** i)], "linear")
    m.key(a, "eye", "position", 3.0, [0, 0, 1.5], "linear")
    for s in range(7):
        m.key(a, "crown", "rotation", s * 0.5, [-3 + 1 * math.sin(s * 2.1), 0, 1.2 * math.sin(s * 3.3)])
    m.fx(a, 0.0, "laser_beam", "laser")
    m.fx(a, 0.05, "eye_glint", "glint")
    m.sfx(a, 0.0, "bloom.laser_loop")

    # ---- retina_flash (1.4 s): lids snap wide, iris blooms, flash; syncs with the blindness event
    a = m.anim("retina_flash", 1.4, "once")
    m.keys(a, "lid_top", "rotation", [(0, [0, 0, 0]), (0.5, [20, 0, 0]), (0.6, [-35, 0, 0]), (1.4, [0, 0, 0])])
    m.keys(a, "lid_bottom", "rotation", [(0, [0, 0, 0]), (0.5, [-20, 0, 0]), (0.6, [35, 0, 0]), (1.4, [0, 0, 0])])
    m.keys(a, "pupil", "scale", [(0, 1), (0.55, [0.2, 0.2, 1]), (0.62, [2.2, 1.6, 1]), (1.4, 1)])
    m.keys(a, "iris", "scale", [(0.55, 1), (0.62, 1.3), (1.4, 1)])
    m.fx(a, 0.6, "eye_glint", "laser")
    m.fx(a, 0.61, "core_glow", "laser")
    m.sfx(a, 0.0, "bloom.retina_inhale")
    m.sfx(a, 0.6, "bloom.retina_flash")

    # ---- stagger (hurt): recoil + petal clench
    a = m.anim("stagger", 1.0, "once")
    m.keys(a, "crown", "rotation", [(0, [0, 0, 0]), (0.12, [10, 6, -4]), (0.5, [-4, -2, 2]), (1.0, [0, 0, 0])])
    petals_open(a, 0, -6)
    petals_open(a, 0.15, 25)
    petals_open(a, 1.0, -6)
    m.keys(a, "lid_top", "rotation", [(0, [0, 0, 0]), (0.1, [25, 0, 0]), (0.6, [0, 0, 0])])
    m.keys(a, "lid_bottom", "rotation", [(0, [0, 0, 0]), (0.1, [-25, 0, 0]), (0.6, [0, 0, 0])])
    m.fx(a, 0.05, "blood_burst", "laser")
    m.sfx(a, 0.0, "bloom.hurt")

    # ---- death (8 s): convulses, petals wilt, eye ruptures, stalk collapses into the heart
    a = m.anim("death", 8.0, "hold")
    m.keys(a, "crown", "rotation", [(0, [0, 0, 0]), (0.3, [12, 10, 0]), (0.7, [-10, -12, 6]), (1.1, [14, 6, -8]),
                                    (1.6, [-8, 4, 6]), (3.0, [20, 0, 4]), (5.5, [70, 0, 10]), (6.2, [64, 0, 8]), (8, [66, 0, 9])])
    for i, b in enumerate(stalk):
        m.keys(a, b, "rotation", [(0, [0, 0, 0]), (3.0, [4, 0, 2]), (5.5, [18 + i * 8, 0, 4]), (8, [20 + i * 8, 0, 5])])
    m.keys(a, "stalk_0", "position", [(3.0, [0, 0, 0]), (5.5, [0, -28, 6]), (8, [0, -32, 8])])
    for i in range(8):
        m.keys(a, f"petal_{i}", "rotation", [(0, [-6, 0, 0]), (0.5, [30, 0, 0]), (1.2, [-40, 0, 0]),
                                             (3.5, [45 + (i % 3) * 10, 0, 0]), (8, [60 + (i % 3) * 12, 0, 0])])
    m.keys(a, "eye", "scale", [(0, 1), (2.0, 1.12), (2.6, 1.25), (2.75, 0.7), (3.2, [0.8, 0.55, 0.8]), (8, [0.75, 0.45, 0.75])])
    m.keys(a, "pupil", "scale", [(0, 1), (2.6, [2.6, 2.6, 1]), (2.75, 0.3), (8, 0.3)])
    m.keys(a, "lid_top", "rotation", [(2.7, [0, 0, 0]), (3.5, [40, 0, 0]), (8, [45, 0, 0])])
    m.keys(a, "lid_bottom", "rotation", [(2.7, [0, 0, 0]), (3.5, [-40, 0, 0]), (8, [-45, 0, 0])])
    for i in range(6):
        m.keys(a, f"lobe_{i}", "rotation", [(0, [0, 0, 0]), (5.5, [0, 0, 0]), (6.5, [0, 0, 38]), (7.0, [0, 0, 32]), (8, [0, 0, 34])])
    for t, chain in enumerate(tendrils):
        for i, b in enumerate(chain):
            m.keys(a, b, "rotation", [(0, [0, 0, 0]), (0.4 + t * 0.1, [30, 0, -20]), (1.0, [-25, 0, 20]), (4, [10, 0, 5]), (8, [5, 0, 0])])
    m.fx(a, 0.2, "blood_burst", "laser")
    m.fx(a, 1.1, "blood_burst", "vent_2")
    m.fx(a, 2.72, "flesh_chunks", "laser")
    m.fx(a, 2.74, "blood_burst", "glint")
    m.fx(a, 2.76, "core_glow", "laser")
    for i in range(6):
        m.fx(a, 5.5 + i * 0.15, "spore_puff", f"vent_{i}")
    m.fx(a, 6.4, "root_dust", "vent_0")
    m.fx(a, 6.45, "root_dust", "vent_3")
    m.sfx(a, 0.0, "bloom.death_scream")
    m.sfx(a, 2.7, "bloom.eye_rupture")
    m.sfx(a, 5.5, "bloom.collapse")
    m.sfx(a, 7.0, "bloom.last_heartbeat")
    return m


ALL = [bloom]
