"""The six rooted appendages of Matris Calyx. All share a rooted stump, a gated weak core behind two
chitin 'door' plates (the open window), and the same hurt / open / close / death / emerge language.
Front of every model faces north (-Z)."""
import boss_env  # noqa: F401  (shared pipeline + this boss)
import math
import lib
from lib import Model, P


# ------------------------------------------------------------------ shared base
def arm_base(m, core_y=18, trunk_r=10, stump_r=13, roots=7, core_z=None):
    R = m.rng
    root = m.group("arm", (0, 0, 0))
    stump = m.group("stump", (0, 0, 0), root)
    # root mass and ground-anchoring roots (never moves: the island holds it)
    m.blob(stump, (0, 6, 0), stump_r, "flesh_dark", n=6, jitter=0.3)
    m.blob(stump, (0, 3, 0), stump_r * 1.1, "root", n=3, jitter=0.2, rot_j=8)
    for i in range(roots):
        a = 2 * math.pi * i / roots + R.uniform(-0.2, 0.2)
        L = R.uniform(26, 38)
        grp = m.group(f"root_{i}", (math.cos(a) * stump_r * 0.8, 2, math.sin(a) * stump_r * 0.8), stump)
        for s in range(4):
            d0 = stump_r * 0.6 + L * s / 4
            d1 = stump_r * 0.6 + L * (s + 1) / 4
            dm = (d0 + d1) / 2
            th = 5.5 - s * 1.1
            c = (math.cos(a) * dm, max(th / 2 - 0.5, 3.5 - s * 1.2), math.sin(a) * dm)
            m.box(grp, c, [L / 4 + 1.5, th, th], "root" if s % 2 else "flesh_dark",
                  rot=[0, -math.degrees(a), R.uniform(-8, 8)])
        # a little knuckle of bone where the root breaks the surface
        m.box(grp, (math.cos(a) * (stump_r + 4), 4.5, math.sin(a) * (stump_r + 4)), [3, 3, 3], "bone",
              rot=[R.uniform(0, 40), R.uniform(0, 90), 20])
    m.pustules(stump, (0, 9, 4), (9, 3, 6), 6, "bile", (2, 3.5))
    m.veins(stump, (0, 12, 0), (0, -1, 0.3), 16, count=3, mat="vein", thick=1.0)

    # weak core + peel plates (open-window read)
    cz = core_z if core_z is not None else -trunk_r + 2
    core = m.group("weak_core", (0, core_y, cz), root)
    m.blob(core, (0, core_y, cz), 4.5, "core", n=3, jitter=0.25)
    m.ring(core, (0, core_y, cz - 1), 5.5, 6, [1.2, 4, 1.2], "glow_vein", axis="z", phase=0.3)
    m.locator(core, "core", (0, core_y, cz - 5))
    pl = m.group("plate_l", (-trunk_r + 1, core_y, cz - 2), root)
    pr = m.group("plate_r", (trunk_r - 1, core_y, cz - 2), root)
    for g, sx in ((pl, -1), (pr, 1)):
        x0 = sx * (trunk_r - 1)
        m.cube(g, (x0, core_y - 10, cz - 5), (0, core_y + 10, cz - 2), "chitin")
        for k in range(3):  # rib ridges
            y = core_y - 7 + k * 7
            m.cube(g, (x0 - sx * 0.5, y, cz - 6), (sx * 1, y + 2, cz - 4.5), "bone", rot=[0, 0, sx * 4])
        m.box(g, (x0 * 0.55, core_y + 11, cz - 3.5), [trunk_r * 0.7, 2.5, 4], "bone", rot=[0, 0, -sx * 12])
        m.box(g, (sx * 0.8, core_y, cz - 3.2), [1.4, 19, 2.4], "mucus")  # wet seam where they meet
    limb = m.group("limb", (0, core_y + 10, 0), root)
    return root, stump, limb


def register_common_particles(m, extra=()):
    for k in ("core_glow", "blood_burst", "flesh_chunks", "root_dust", "mucus_string", *extra):
        m.particles[k] = P[k]


def common_anims(m, kind, limb_bones, core_y=18, death_axis=(1, 0, 0)):
    # open window: plates peel, core pulses (3.0 s, matches the ~3 s baseline window)
    a = m.anim("open_window", 3.0, "once")
    m.keys(a, "plate_l", "rotation", [(0, [0, 0, 0]), (0.15, [0, 8, 0]), (0.4, [0, -112, 0]), (0.55, [0, -100, 0]),
                                      (2.6, [0, -104, 0]), (2.85, [0, -10, 0]), (3.0, [0, 0, 0])])
    m.keys(a, "plate_r", "rotation", [(0, [0, 0, 0]), (0.15, [0, -8, 0]), (0.4, [0, 112, 0]), (0.55, [0, 100, 0]),
                                      (2.6, [0, 104, 0]), (2.85, [0, 10, 0]), (3.0, [0, 0, 0])])
    for i in range(7):
        t = 0.4 + i * 0.32
        m.key(a, "weak_core", "scale", t, 1.0)
        m.key(a, "weak_core", "scale", t + 0.16, 1.22)
    m.key(a, "weak_core", "scale", 2.9, 1.0)
    m.key(a, "weak_core", "position", 0.4, [0, 0, 0])
    m.key(a, "weak_core", "position", 0.6, [0, 0, -1.5])
    m.key(a, "weak_core", "position", 2.7, [0, 0, -1.5])
    m.key(a, "weak_core", "position", 3.0, [0, 0, 0])
    # the limb sags while exposed: that's the "now" read
    if limb_bones:
        m.keys(a, limb_bones[0], "rotation", [(0, [0, 0, 0]), (0.5, [12, 0, 0]), (2.6, [10, 0, 0]), (3.0, [0, 0, 0])])
    for t in (0.35, 1.2, 2.0):
        m.fx(a, t, "core_glow", "core")
    m.fx(a, 0.3, "mucus_string", "core")
    m.sfx(a, 0.2, f"arm.open")
    m.sfx(a, 2.7, f"arm.close")

    # telegraph for the window (0.8 s shudder before it opens)
    a = m.anim("window_warn", 0.8, "once")
    for i in range(9):
        t = i * 0.1
        s = 1 if i % 2 else -1
        m.key(a, "plate_l", "rotation", t, [0, s * 4, 0])
        m.key(a, "plate_r", "rotation", t, [0, -s * 4, 0])
    m.keys(a, "weak_core", "scale", [(0, 1), (0.4, 1.1), (0.8, 1)])
    m.fx(a, 0.1, "core_glow", "core")
    m.sfx(a, 0.0, "arm.window_warn")

    # hurt (only plays inside the window)
    a = m.anim("hurt", 0.45, "once")
    m.keys(a, "weak_core", "scale", [(0, 1.2), (0.08, 0.8), (0.25, 1.25), (0.45, 1.15)])
    if limb_bones:
        m.keys(a, limb_bones[0], "rotation", [(0, [10, 0, 0]), (0.1, [22, 0, 6]), (0.45, [10, 0, 0])])
    m.fx(a, 0.02, "blood_burst", "core")
    m.sfx(a, 0.0, f"arm.{kind}.hurt")

    # death: limb spasms, plates tear off, collapse onto the stump, core bursts
    a = m.anim("death", 3.5, "hold")
    for i, b in enumerate(limb_bones):
        amp = 25 + i * 6
        m.keys(a, b, "rotation", [(0, [0, 0, 0]), (0.2, [-amp * 0.4, 0, amp * 0.2]), (0.5, [amp * 0.3, 0, -amp * 0.3]),
                                  (0.8, [-amp * 0.2, 0, amp * 0.25]), (1.6, [death_axis[0] * (30 + i * 8), 0,
                                                                          death_axis[2] * (30 + i * 8)]),
                                  (1.9, [death_axis[0] * (26 + i * 7), 0, death_axis[2] * (26 + i * 7)]),
                                  (3.5, [death_axis[0] * (28 + i * 7.5), 0, death_axis[2] * (28 + i * 7.5)])])
    if limb_bones:
        m.keys(a, limb_bones[0], "position", [(0, [0, 0, 0]), (1.6, [0, -6, 0]), (3.5, [0, -8, 0])])
    m.keys(a, "plate_l", "rotation", [(0, [0, 0, 0]), (0.9, [0, -60, -20]), (1.3, [-50, -150, -70]), (3.5, [-80, -160, -90])])
    m.keys(a, "plate_l", "position", [(0, [0, 0, 0]), (0.9, [-2, 0, -2]), (1.3, [-10, -6, -12]), (3.5, [-14, -16, -14])])
    m.keys(a, "plate_r", "rotation", [(0, [0, 0, 0]), (0.95, [0, 60, 20]), (1.35, [-40, 150, 70]), (3.5, [-70, 160, 90])])
    m.keys(a, "plate_r", "position", [(0, [0, 0, 0]), (0.95, [2, 0, -2]), (1.35, [10, -6, -12]), (3.5, [14, -16, -14])])
    m.keys(a, "weak_core", "scale", [(0, 1), (0.9, 1.4), (1.0, 1.6), (1.05, 0.2), (3.5, 0.0)])
    m.keys(a, "stump", "scale", [(0, 1), (1.6, [1.08, 0.9, 1.08]), (3.5, [1.1, 0.8, 1.1])])
    m.fx(a, 0.2, "blood_burst", "core")
    m.fx(a, 1.02, "flesh_chunks", "core")
    m.fx(a, 1.04, "blood_burst", "core")
    m.fx(a, 1.6, "root_dust", "base")
    m.fx(a, 2.0, "mucus_string", "core")
    m.sfx(a, 0.0, f"arm.{kind}.death")
    m.sfx(a, 1.0, "arm.core_burst")
    m.sfx(a, 1.6, "arm.collapse")

    # emerge: tears up out of the island at fight start / on reload re-sync
    a = m.anim("emerge", 2.5, "once")
    m.keys(a, "arm", "position", [(0, [0, -40, 0]), (0.6, [0, -30, 0]), (1.6, [0, 2, 0]), (2.0, [0, -1, 0]), (2.5, [0, 0, 0])])
    m.keys(a, "arm", "scale", [(0, [1, 0.6, 1]), (1.6, [0.95, 1.08, 0.95]), (2.5, [1, 1, 1])])
    for i, b in enumerate(limb_bones):
        m.keys(a, b, "rotation", [(0, [40, 0, 0]), (1.6, [-10 - i * 2, 0, 0]), (2.5, [0, 0, 0])])
    m.fx(a, 0.1, "root_dust", "base")
    m.fx(a, 1.5, "root_dust", "base")
    m.fx(a, 1.6, "blood_burst", "base")
    m.sfx(a, 0.0, "arm.emerge")

    # stump breathing sub-layer; every idle adds this
    return a


def base_breath(m, a, length):
    for s in range(5):
        t = length * s / 4
        v = 1 + 0.03 * math.sin(2 * math.pi * s / 4)
        m.key(a, "stump", "scale", t, [v, 1 / v, v])
        m.key(a, "weak_core", "scale", t, 1 + 0.06 * math.sin(2 * math.pi * s / 4 + 1))


def add_base_locator(m):
    m.locator("stump", "base", (0, 2, -16))


# ------------------------------------------------------------------ 1. grasping arm
def grasping():
    m = Model("grasping_arm", seed=11)
    root, stump, limb = arm_base(m, trunk_r=9)
    add_base_locator(m)
    bones, tip = m.tentacle(limb, (0, 28, 0), 8, 120, 8, 2.6, "grasp", mat="flesh", mat2="flesh_dark",
                            plates="chitin", suckers="mucus", rest=[6, 4, 2, -2, -6, -10, -14, -18])
    # the base trunk collar around the plates
    m.blob(limb, (0, 30, 2), 8, "flesh_dark", n=4)
    claw = m.group("claw", tip, bones[-1])
    m.blob(claw, (tip[0], tip[1] + 2, tip[2]), 4.5, "flesh_dark", n=4)
    m.pustules(claw, (tip[0], tip[1] + 2, tip[2]), (3, 2, 3), 3, "bile", (1.5, 2.5))
    for i in range(3):
        a = 2 * math.pi * i / 3 + math.pi / 2
        fx, fz = math.cos(a) * 4, math.sin(a) * 4
        f = m.group(f"finger_{i}", (tip[0] + fx, tip[1] + 3, tip[2] + fz), claw, rot=[0, -math.degrees(a) + 90, 0])
        k = m.group(f"finger_{i}_tip", (tip[0] + fx, tip[1] + 11, tip[2] + fz), f)
        m.box(f, (tip[0] + fx, tip[1] + 7, tip[2] + fz), [3.4, 9, 3.4], "flesh")
        m.box(f, (tip[0] + fx, tip[1] + 5, tip[2] + fz), [4.2, 1.6, 4.2], "flesh_dark")
        m.box(f, (tip[0] + fx, tip[1] + 9, tip[2] + fz), [4.0, 1.4, 4.0], "chitin")
        m.box(k, (tip[0] + fx, tip[1] + 15, tip[2] + fz), [2.6, 9, 2.6], "bone")
        m.box(k, (tip[0] + fx, tip[1] + 12, tip[2] + fz), [3.2, 1.2, 3.2], "flesh_dark")
        m.box(k, (tip[0] + fx * 0.7, tip[1] + 21, tip[2] + fz * 0.7), [1.8, 5, 1.8], "tooth", rot=[-20, 0, 0])
    m.locator("claw", "grip", (tip[0], tip[1] + 8, tip[2]))
    register_common_particles(m)

    a = m.anim("idle", 4.0)
    m.wave(a, bones, "rotation", 6, 4.0, 4.0, phase_step=0.1, axis=(1, 0, 0.6))
    for i in range(3):
        m.keys(a, f"finger_{i}", "rotation", [(0, [-10, 0, 0]), (2, [-20, 0, 0]), (4, [-10, 0, 0])])
    base_breath(m, a, 4.0)

    # telegraph: rears back, fingers splay wide, a rattling hiss (1.2 s)
    a = m.anim("telegraph", 1.2, "hold")
    for i, b in enumerate(bones):
        m.keys(a, b, "rotation", [(0, [0, 0, 0]), (0.8, [14 + i * 0.5, 0, 0]), (1.0, [15 + i * 0.5, 0, 2]), (1.2, [14 + i * 0.5, 0, -2])])
    for i in range(3):
        m.keys(a, f"finger_{i}", "rotation", [(0, [-10, 0, 0]), (0.6, [-55, 0, 0]), (1.2, [-60, 0, 0])])
    m.fx(a, 0.3, "mucus_string", "grip")
    m.sfx(a, 0.0, "arm.grasping.telegraph")

    # grab: whips down-forward through the flight lane, fingers slam shut
    a = m.anim("grab", 1.0, "once")
    for i, b in enumerate(bones):
        m.keys(a, b, "rotation", [(0, [14 + i * 0.5, 0, 0]), (0.25, [-18 - i * 1.5, 0, 0]),
                                  (0.45, [-14 - i, 0, 0]), (1.0, [-8, 0, 0])])
    for i in range(3):
        m.keys(a, f"finger_{i}", "rotation", [(0, [-60, 0, 0]), (0.3, [-60, 0, 0]), (0.4, [25, 0, 0]), (1.0, [20, 0, 0])])
        m.keys(a, f"finger_{i}_tip", "rotation", [(0.3, [0, 0, 0]), (0.42, [45, 0, 0]), (1.0, [40, 0, 0])])
    m.fx(a, 0.4, "blood_burst", "grip")
    m.sfx(a, 0.2, "arm.grasping.whip")
    m.sfx(a, 0.4, "arm.grasping.snap")

    # yank: drags the caught flyer back to the island
    a = m.anim("yank", 1.4, "once")
    for i, b in enumerate(bones):
        m.keys(a, b, "rotation", [(0, [-8, 0, 0]), (0.5, [10 + i, 0, 0]), (0.9, [22 + i * 1.5, 0, 0]), (1.4, [0, 0, 0])])
    for i in range(3):
        m.keys(a, f"finger_{i}", "rotation", [(0, [20, 0, 0]), (1.0, [20, 0, 0]), (1.2, [-40, 0, 0]), (1.4, [-10, 0, 0])])
    m.fx(a, 1.1, "mucus_string", "grip")
    m.sfx(a, 0.0, "arm.grasping.yank")

    common_anims(m, "grasping", bones)
    return m


# ------------------------------------------------------------------ 2. slam arm
def slam():
    m = Model("slam_arm", seed=22)
    root, stump, limb = arm_base(m, trunk_r=12, stump_r=15)
    add_base_locator(m)
    bones, tip = m.tentacle(limb, (0, 28, 0), 4, 64, 11, 8, "slam", mat="flesh", mat2="bone", plates="chitin", rest=[6, 4, -8, -12])
    m.blob(limb, (0, 32, 3), 11, "flesh_dark", n=5)
    fist = m.group("fist", tip, bones[-1])
    m.blob(fist, (tip[0], tip[1] + 10, tip[2]), 12, "flesh_dark", n=6, jitter=0.25)
    # bone knuckle plate on the striking face (north, -z) and underside
    for i in range(4):
        x = -10.5 + i * 7
        m.box(fist, (x, tip[1] + 4, tip[2] - 11), [6, 9, 5], "bone", rot=[12, 0, (i - 1.5) * 4])
        m.box(fist, (x, tip[1] + 1, tip[2] - 14), [2.5, 4, 3], "tooth", rot=[40, 0, 0])
    m.box(fist, (tip[0], tip[1] - 1, tip[2]), [22, 4, 20], "bone")
    for i in range(6):  # dorsal spikes
        a = i / 5
        m.box(fist, (-9 + 18 * a, tip[1] + 20, tip[2] + 4 - 6 * math.sin(a * math.pi)), [2.5, 8, 2.5], "bone",
              rot=[-20, 0, (a - 0.5) * 50])
    m.veins(fist, (0, tip[1] + 14, tip[2] + 10), (0, -1, -0.5), 20, count=4, mat="vein", thick=1.3)
    m.pustules(fist, (0, tip[1] + 12, tip[2] + 6), (8, 5, 3), 5, "bile")
    m.locator("fist", "impact", (tip[0], tip[1] - 3, tip[2] - 8))
    register_common_particles(m)

    a = m.anim("idle", 5.0)
    m.wave(a, bones, "rotation", 3, 5.0, 5.0, phase_step=0.08, axis=(1, 0, 0.4))
    m.keys(a, "fist", "rotation", [(0, [0, 0, 0]), (2.5, [4, 3, 0]), (5, [0, 0, 0])])
    base_breath(m, a, 5.0)

    # long wind-up: lifts overhead and shakes (2.2 s, very readable)
    a = m.anim("telegraph", 2.2, "hold")
    for i, b in enumerate(bones):
        m.keys(a, b, "rotation", [(0, [0, 0, 0]), (1.4, [22 + i * 3, 0, 0]), (2.2, [26 + i * 3, 0, 0])])
    m.keys(a, "fist", "rotation", [(0, [0, 0, 0]), (1.4, [35, 0, 0]), (2.2, [40, 0, 0])])
    for i in range(10):
        t = 1.4 + i * 0.08
        m.key(a, "fist", "position", t, [(-1) ** i * 0.8, 0, 0])
    m.fx(a, 1.5, "mucus_string", "impact")
    m.sfx(a, 0.0, "arm.slam.windup")
    m.sfx(a, 1.4, "arm.slam.strain")

    # slam: fast drop, impact, hold a beat in the ground (that's the safe punish moment)
    a = m.anim("slam", 1.6, "once")
    for i, b in enumerate(bones):
        m.keys(a, b, "rotation", [(0, [26 + i * 3, 0, 0]), (0.18, [-30 - i * 6, 0, 0]), (0.26, [-26 - i * 5, 0, 0]),
                                  (1.2, [-26 - i * 5, 0, 0]), (1.6, [0, 0, 0])])
    m.keys(a, "fist", "rotation", [(0, [40, 0, 0]), (0.18, [-35, 0, 0]), (1.2, [-35, 0, 0]), (1.6, [0, 0, 0])])
    m.keys(a, "fist", "scale", [(0.18, [1, 1, 1]), (0.22, [1.12, 0.85, 1.12]), (0.4, [1, 1, 1])])
    m.keys(a, "stump", "scale", [(0.18, [1, 1, 1]), (0.24, [1.08, 0.9, 1.08]), (0.5, [1, 1, 1])])
    m.fx(a, 0.19, "root_dust", "impact")
    m.fx(a, 0.2, "flesh_chunks", "impact")
    m.fx(a, 0.21, "bile_splash", "impact")
    m.particles["bile_splash"] = P["bile_splash"]
    m.sfx(a, 0.18, "arm.slam.impact")

    common_anims(m, "slam", bones)
    return m


# ------------------------------------------------------------------ 3. charging arm
def charging():
    """A rooted crawler: carapace body on four hooked legs, battering skull, and an umbilical cord that
    trails back into its island (the cord is the visual reason it can never leave the island)."""
    m = Model("charging_arm", seed=33)
    R = m.rng
    root = m.group("arm", (0, 0, 0))
    body = m.group("body", (0, 20, 4), root)
    m.blob(body, (0, 22, 4), 13, "flesh_dark", n=6, jitter=0.25)
    m.box(body, (0, 20, 4), [22, 16, 34], "flesh")
    # dorsal carapace ridge
    for i in range(6):
        z = -10 + i * 6
        m.box(body, (0, 30 + math.sin(i / 5 * math.pi) * 3, z), [20 - abs(i - 2.5) * 2, 4, 6], "chitin", rot=[-10, 0, 0])
        m.box(body, (0, 34 + math.sin(i / 5 * math.pi) * 3, z), [2, 5, 2], "bone", rot=[-30, 0, 0])
    m.pustules(body, (0, 24, 12), (9, 4, 6), 6, "bile")
    m.veins(body, (-11, 24, -8), (0, -0.3, 1), 26, count=2, mat="vein")
    m.veins(body, (11, 24, -8), (0, -0.3, 1), 26, count=2, mat="vein")
    # weak core is on the back, under two dorsal plates
    core = m.group("weak_core", (0, 30, 10), body)
    m.blob(core, (0, 30, 10), 4.5, "core", n=3)
    m.ring(core, (0, 31, 10), 5.5, 6, [1.2, 1.2, 4], "glow_vein", axis="y")
    m.locator(core, "core", (0, 36, 10))
    pl = m.group("plate_l", (-10, 32, 10), body)
    pr = m.group("plate_r", (10, 32, 10), body)
    for g, sx in ((pl, -1), (pr, 1)):
        m.cube(g, (sx * 10, 32, 1), (0, 36, 19), "chitin", rot=[0, 0, sx * 10], pivot=(sx * 10, 32, 10))
        for k in range(3):
            m.cube(g, (sx * 9, 35.5, 3 + k * 6), (sx * 1, 37, 5 + k * 6), "bone", rot=[0, 0, sx * 10], pivot=(sx * 10, 32, 10))
    # neck + ram skull
    neck = m.group("neck", (0, 22, -12), body)
    m.box(neck, (0, 22, -16), [14, 12, 10], "flesh")
    m.box(neck, (0, 22, -16), [15.2, 2, 11], "flesh_dark")
    head = m.group("head", (0, 22, -20), neck)
    m.box(head, (0, 22, -26), [16, 13, 12], "bone")
    m.box(head, (0, 19, -33), [12, 8, 4], "bone")
    m.box(head, (0, 27, -30), [18, 4, 8], "chitin", rot=[-15, 0, 0])
    for sx in (-1, 1):
        horn = m.group(f"horn_{'l' if sx < 0 else 'r'}", (sx * 8, 26, -26), head)
        for k in range(4):
            m.box(horn, (sx * (10 + k * 3.2), 26 + k * 1.2 - (k * k) * 0.4, -26 - k * 3), [4.5 - k * 0.8, 4.5 - k * 0.8, 5],
                  "bone" if k < 3 else "tooth", rot=[0, sx * 25, sx * 10 * k])
        m.box(head, (sx * 5, 22, -32.2), [3, 3, 0.8], "iris")  # small dumb eyes
        m.box(head, (sx * 5, 22, -32.5), [1, 2, 0.6], "pupil")
    jaw = m.group("jaw", (0, 17, -22), head)
    m.box(jaw, (0, 15, -28), [12, 3, 11], "flesh_dark")
    for k in range(5):
        m.box(jaw, (-5 + k * 2.5, 17, -33), [1.2, 2.5, 1.2], "tooth")
    m.locator(head, "ram", (0, 22, -36))
    # legs
    legs = []
    for sx in (-1, 1):
        for sz, tag in ((-6, "f"), (14, "b")):
            name = f"leg_{tag}{'l' if sx < 0 else 'r'}"
            up = m.group(name, (sx * 11, 20, sz), body)
            m.box(up, (sx * 15, 20, sz), [9, 5, 5], "flesh", rot=[0, 0, sx * -30])
            m.box(up, (sx * 15, 22.5, sz), [7, 1.5, 5.5], "chitin", rot=[0, 0, sx * -30])
            low = m.group(name + "_low", (sx * 19, 17, sz), up)
            m.box(low, (sx * 20, 9, sz), [4, 16, 4], "flesh_dark", rot=[0, 0, sx * 8])
            m.box(low, (sx * 21, 1.5, sz - 1.5), [3, 3, 6], "bone", rot=[20, 0, 0])
            m.box(low, (sx * 21, 0.5, sz - 5), [1.5, 1.5, 3], "tooth", rot=[40, 0, 0])
            legs.append((up, low))
    # umbilical cord tail into the ground
    cord_bones = []
    parent = body
    pos = [0, 18, 20]
    for i in range(6):
        b = m.group(f"cord_{i}", pos, parent)
        nz = pos[2] + 7
        ny = pos[1] - 2.8
        m.box(b, (0, (pos[1] + ny) / 2, (pos[2] + nz) / 2), [5 - i * 0.3, 5 - i * 0.3, 8], "root" if i % 2 else "flesh_dark",
              rot=[-20, 0, 0])
        m.box(b, (0, (pos[1] + ny) / 2 + 0.3, (pos[2] + nz) / 2), [1.2, 5.6 - i * 0.3, 4], "vein", rot=[-20, 0, 0])
        cord_bones.append(b)
        parent = b
        pos = [0, ny, nz]
    m.blob(cord_bones[-1], (0, 1, pos[2] + 2), 5, "root", n=3)
    m.locator(root, "base", (0, 1, 0))
    m.locator(body, "back", (0, 20, 22))
    register_common_particles(m)

    def legcycle(a, length, lift, stride):
        for idx, (up, low) in enumerate(legs):
            ph = [0, 0.5, 0.5, 0][idx]
            for s in range(9):
                t = length * s / 8
                x = 2 * math.pi * (s / 8 + ph)
                m.key(a, up, "rotation", t, [stride * math.sin(x), 0, 0])
                m.key(a, low, "rotation", t, [max(0, lift * math.cos(x)) * -1, 0, 0])

    a = m.anim("idle", 3.0)
    for s in range(5):
        t = 3.0 * s / 4
        m.key(a, "body", "position", t, [0, 0.6 * math.sin(2 * math.pi * s / 4), 0])
        m.key(a, "head", "rotation", t, [3 * math.sin(2 * math.pi * s / 4 + 1), 4 * math.sin(2 * math.pi * s / 4), 0])
        m.key(a, "jaw", "rotation", t, [4 + 4 * math.sin(2 * math.pi * s / 4), 0, 0])
    m.wave(a, cord_bones, "rotation", 3, 3.0, 3.0, phase_step=0.12, axis=(0, 1, 0))

    a = m.anim("walk", 1.2)
    legcycle(a, 1.2, 20, 22)
    m.wave(a, ["body"], "rotation", 3, 0.6, 1.2, axis=(0, 0, 1))
    m.wave(a, cord_bones, "rotation", 6, 1.2, 1.2, phase_step=0.15, axis=(0, 1, 0))

    # telegraph: lowers skull, paws twice, dust kicks (1.5 s)
    a = m.anim("telegraph", 1.5, "hold")
    m.keys(a, "neck", "rotation", [(0, [0, 0, 0]), (0.5, [18, 0, 0]), (1.5, [20, 0, 0])])
    m.keys(a, "head", "rotation", [(0, [0, 0, 0]), (0.5, [10, 0, 0]), (1.5, [12, 0, 0])])
    m.keys(a, "body", "position", [(0, [0, 0, 0]), (0.5, [0, -3, 2]), (1.5, [0, -3, 3])])
    for k, t in enumerate((0.4, 0.9)):
        m.keys(a, "leg_fr", "rotation", [(t, [0, 0, 0]), (t + 0.15, [-40, 0, 0]), (t + 0.35, [25, 0, 0]), (t + 0.45, [0, 0, 0])])
        m.fx(a, t + 0.35, "root_dust", "ram")
    m.sfx(a, 0.0, "arm.charging.snort")

    # charge: fast gallop, head down (loop while the controller moves it along the island lane)
    a = m.anim("charge", 0.6)
    legcycle(a, 0.6, 30, 34)
    m.keys(a, "neck", "rotation", [(0, [20, 0, 0]), (0.6, [20, 0, 0])])
    m.wave(a, ["body"], "position", 1.5, 0.3, 0.6, axis=(0, 1, 0))
    m.wave(a, cord_bones, "rotation", 10, 0.6, 0.6, phase_step=0.18, axis=(0, 1, 0))
    m.fx(a, 0.0, "root_dust", "base")
    m.sfx(a, 0.0, "arm.charging.gallop")

    # impact: hits the island wall/edge limit, stunned (the long opening window lives here)
    a = m.anim("impact", 1.8, "once")
    m.keys(a, "body", "position", [(0, [0, -3, 3]), (0.1, [0, 0, 6]), (0.3, [0, -1, 4]), (1.8, [0, 0, 0])])
    m.keys(a, "head", "rotation", [(0, [12, 0, 0]), (0.1, [-25, 0, 0]), (0.5, [5, 14, 0]), (0.9, [5, -14, 0]), (1.3, [5, 10, 0]), (1.8, [0, 0, 0])])
    m.fx(a, 0.05, "flesh_chunks", "ram")
    m.fx(a, 0.06, "root_dust", "ram")
    m.sfx(a, 0.0, "arm.charging.impact")

    # own open/hurt/death (dorsal plates hinge on Z)
    a = m.anim("open_window", 3.0, "once")
    m.keys(a, "plate_l", "rotation", [(0, [0, 0, 0]), (0.4, [0, 0, 105]), (0.55, [0, 0, 95]), (2.6, [0, 0, 98]), (3.0, [0, 0, 0])])
    m.keys(a, "plate_r", "rotation", [(0, [0, 0, 0]), (0.4, [0, 0, -105]), (0.55, [0, 0, -95]), (2.6, [0, 0, -98]), (3.0, [0, 0, 0])])
    for i in range(7):
        t = 0.4 + i * 0.32
        m.key(a, "weak_core", "scale", t, 1.0)
        m.key(a, "weak_core", "scale", t + 0.16, 1.22)
    m.keys(a, "body", "position", [(0, [0, 0, 0]), (0.5, [0, -4, 0]), (2.6, [0, -4, 0]), (3.0, [0, 0, 0])])
    for t in (0.35, 1.2, 2.0):
        m.fx(a, t, "core_glow", "core")
    m.sfx(a, 0.2, "arm.open")
    m.sfx(a, 2.7, "arm.close")
    a = m.anim("window_warn", 0.8, "once")
    for i in range(9):
        s = 1 if i % 2 else -1
        m.key(a, "plate_l", "rotation", i * 0.1, [0, 0, s * 5])
        m.key(a, "plate_r", "rotation", i * 0.1, [0, 0, -s * 5])
    m.sfx(a, 0.0, "arm.window_warn")
    a = m.anim("hurt", 0.45, "once")
    m.keys(a, "weak_core", "scale", [(0, 1.2), (0.08, 0.8), (0.25, 1.25), (0.45, 1.15)])
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (0.1, [0, 0, 6]), (0.45, [0, 0, 0])])
    m.fx(a, 0.02, "blood_burst", "core")
    m.sfx(a, 0.0, "arm.charging.hurt")
    a = m.anim("death", 3.5, "hold")
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (0.4, [0, 0, -12]), (0.8, [0, 0, 10]), (1.6, [8, 0, 70]), (3.5, [10, 0, 82])])
    m.keys(a, "body", "position", [(0, [0, 0, 0]), (1.6, [6, -8, 0]), (3.5, [8, -12, 0])])
    for up, low in legs:
        m.keys(a, low, "rotation", [(0, [0, 0, 0]), (0.5, [-50, 0, 0]), (1.0, [-20, 0, 0]), (1.4, [-60, 0, 0]), (3.5, [-70, 0, 0])])
    m.keys(a, "jaw", "rotation", [(0, [0, 0, 0]), (0.4, [35, 0, 0]), (3.5, [25, 0, 0])])
    m.keys(a, "plate_l", "rotation", [(0.8, [0, 0, 0]), (1.1, [-40, 0, 140]), (3.5, [-60, 0, 170])])
    m.keys(a, "plate_r", "rotation", [(0.8, [0, 0, 0]), (1.1, [40, 0, -140]), (3.5, [60, 0, -170])])
    m.keys(a, "weak_core", "scale", [(0, 1), (0.9, 1.4), (1.0, 1.6), (1.05, 0.2), (3.5, 0.0)])
    m.fx(a, 1.02, "flesh_chunks", "core")
    m.fx(a, 1.04, "blood_burst", "core")
    m.fx(a, 1.6, "root_dust", "base")
    m.sfx(a, 0.0, "arm.charging.death")
    m.sfx(a, 1.0, "arm.core_burst")
    a = m.anim("emerge", 2.5, "once")
    m.keys(a, "arm", "position", [(0, [0, -40, 0]), (1.6, [0, 2, 0]), (2.5, [0, 0, 0])])
    m.keys(a, "neck", "rotation", [(0, [40, 0, 0]), (1.6, [-20, 0, 0]), (2.5, [0, 0, 0])])
    m.fx(a, 0.1, "root_dust", "base")
    m.fx(a, 1.5, "root_dust", "base")
    m.sfx(a, 0.0, "arm.emerge")
    return m


# ------------------------------------------------------------------ 4. mouth arm
def mouth():
    m = Model("mouth_arm", seed=44)
    root, stump, limb = arm_base(m, trunk_r=10)
    add_base_locator(m)
    bones, tip = m.tentacle(limb, (0, 28, 0), 5, 70, 8, 9.5, "neck", mat="flesh", mat2="flesh_dark", plates="chitin", rest=[7, 5, -4, -9, -12])
    m.blob(limb, (0, 32, 2), 9, "flesh_dark", n=4)
    head = m.group("head", tip, bones[-1])
    hy = tip[1] + 10
    # barrel of the lamprey head, mouth opening facing north (-z)
    m.box(head, (0, hy, 2), [22, 22, 18], "flesh")
    m.box(head, (0, hy, 2), [23.2, 4, 16], "flesh_dark")
    m.box(head, (0, hy + 10, 4), [18, 4, 14], "chitin", rot=[-6, 0, 0])
    m.box(head, (0, hy, -7.2), [18, 18, 1], "pupil")                 # throat darkness
    m.box(head, (0, hy, -7.8), [9, 9, 1], "flesh_dark", rot=[0, 0, 45])  # gullet
    m.ring(head, (0, hy, -8.2), 7.5, 14, [1.4, 3.5, 1.4], "tooth", axis="z", tilt=25)
    m.ring(head, (0, hy, -6.8), 4.6, 10, [1.1, 2.6, 1.1], "tooth", axis="z", phase=0.3, tilt=35)
    m.ring(head, (0, hy, -5.5), 2.2, 6, [0.9, 1.8, 0.9], "tooth", axis="z", phase=0.1, tilt=45)
    m.pustules(head, (0, hy + 4, 8), (9, 6, 3), 6, "bile")
    m.veins(head, (0, hy + 11, 10), (0, -0.4, -1), 18, count=3, mat="vein", thick=1.1)
    # four jaw petals that close over the mouth
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        px, py = math.cos(a) * 9, math.sin(a) * 9
        p = m.group(f"lip_{i}", (px, hy + py, -8), head, rot=[0, 0, 0])
        cx, cy = math.cos(a) * 12, math.sin(a) * 12
        m.box(p, (cx, hy + cy, -10), [11, 11, 3], "flesh_dark", rot=[0, 0, math.degrees(a) - 45])
        for k in range(3):
            aa = a + (k - 1) * 0.35
            m.box(p, (math.cos(aa) * 7, hy + math.sin(aa) * 7, -11.5), [1.3, 4, 1.3], "tooth",
                  rot=[0, 0, math.degrees(aa) - 90])
    tongue = m.group("tongue", (0, hy - 3, -4), head)
    for k in range(4):
        m.box(tongue, (0, hy - 3 - k * 0.3, -6 - k * 3.5), [4 - k * 0.6, 2, 4], "core" if k == 3 else "flesh")
    m.locator(head, "maw", (0, hy, -14))
    register_common_particles(m)
    m.particles["bile_drip"] = P["bile_drip"]

    def lips(a, t, openness):
        for i in range(4):
            aa = i * math.pi / 2 + math.pi / 4
            # hinge swings outward along its own radial direction
            m.key(a, f"lip_{i}", "rotation", t, [-math.sin(aa) * openness * -1, math.cos(aa) * openness, 0])

    a = m.anim("idle", 4.0)
    m.wave(a, bones, "rotation", 5, 4.0, 4.0, phase_step=0.1, axis=(1, 0, 0.8))
    for s in range(5):
        lips(a, 4.0 * s / 4, 12 + 10 * math.sin(2 * math.pi * s / 4))
    m.keys(a, "tongue", "rotation", [(0, [0, 0, 0]), (1, [-8, 6, 0]), (2, [0, 0, 0]), (3, [-6, -6, 0]), (4, [0, 0, 0])])
    m.fx(a, 0.5, "bile_drip", "maw")
    m.fx(a, 2.5, "mucus_string", "maw")
    base_breath(m, a, 4.0)

    a = m.anim("telegraph", 1.0, "hold")
    for i, b in enumerate(bones):
        m.keys(a, b, "rotation", [(0, [0, 0, 0]), (0.8, [10 + i * 2, 0, 0]), (1.0, [11 + i * 2, 0, 0])])
    lips(a, 0, 12)
    lips(a, 0.7, 70)
    lips(a, 1.0, 75)
    m.keys(a, "head", "scale", [(0, 1), (0.8, [1.08, 1.08, 0.95]), (1.0, [1.1, 1.1, 0.94])])
    m.fx(a, 0.4, "mucus_string", "maw")
    m.sfx(a, 0.0, "arm.mouth.shriek")

    a = m.anim("bite", 0.9, "once")
    for i, b in enumerate(bones):
        m.keys(a, b, "rotation", [(0, [11 + i * 2, 0, 0]), (0.15, [-16 - i * 3, 0, 0]), (0.5, [-10 - i * 2, 0, 0]), (0.9, [0, 0, 0])])
    lips(a, 0, 75)
    lips(a, 0.15, 80)
    lips(a, 0.22, -5)
    lips(a, 0.9, 10)
    m.fx(a, 0.22, "blood_burst", "maw")
    m.sfx(a, 0.2, "arm.mouth.chomp")

    # feed: lifesteal swallow, a bulge travels down the neck to the core
    a = m.anim("feed", 1.6, "once")
    m.keys(a, "head", "scale", [(0, 1), (0.2, [0.9, 1.1, 1]), (0.4, 1)])
    for i, b in enumerate(reversed(bones)):
        t = 0.3 + i * 0.2
        m.keys(a, b, "scale", [(t - 0.15, 1), (t, [1.25, 1, 1.25]), (t + 0.2, 1)])
    m.keys(a, "weak_core", "scale", [(1.2, 1), (1.4, 1.35), (1.6, 1)])
    m.fx(a, 1.35, "core_glow", "core")
    m.sfx(a, 0.1, "arm.mouth.gulp")

    common_anims(m, "mouth", bones)
    return m


# ------------------------------------------------------------------ 5. spitting arm
def spitting():
    m = Model("spitting_arm", seed=55)
    root, stump, limb = arm_base(m, trunk_r=10)
    add_base_locator(m)
    bones, tip = m.tentacle(limb, (0, 28, 0), 4, 56, 7, 6, "stalk", mat="flesh", mat2="flesh_dark", plates="chitin", rest=[5, 3, -6, -10])
    m.blob(limb, (0, 32, 2), 9, "flesh_dark", n=4)
    sac = m.group("sac", (tip[0], tip[1] + 6, tip[2] + 2), bones[-1])
    sy = tip[1] + 12
    m.blob(sac, (0, sy, 3), 13, "bile", n=7, jitter=0.25, rot_j=25)
    m.box(sac, (0, sy - 9, 3), [16, 4, 16], "flesh")
    m.box(sac, (0, sy + 1, 3), [27, 2, 6], "flesh_dark", rot=[0, 20, 0])
    for i in range(5):
        m.box(sac, (-10 + i * 5, sy + 2 + (i % 2) * 3, 3 + (i - 2) * 2), [1.2, 14, 3], "vein", rot=[(i - 2) * 12, 0, (i - 2) * 6])
    m.pustules(sac, (0, sy + 2, 3), (12, 10, 11), 10, "bile", (3, 5))
    m.pustules(sac, (0, sy + 4, 12), (10, 8, 2), 4, "spore", (2, 3))
    nozzle = m.group("nozzle", (0, sy - 2, -8), sac)
    for k in range(4):
        r = 7 - k * 1.1
        m.box(nozzle, (0, sy - 2 - k * 0.8, -10 - k * 4), [r * 2, r * 2, 4.5], "flesh" if k % 2 == 0 else "flesh_dark",
              rot=[8, 0, 0])
    m.ring(nozzle, (0, sy - 5, -26), 3.4, 8, [1.4, 2.6, 2], "mucus", axis="z", tilt=20)
    m.box(nozzle, (0, sy - 5, -25.5), [4, 4, 1], "bile")
    m.locator(nozzle, "muzzle", (0, sy - 5, -30))
    register_common_particles(m, ("bile_splash", "bile_drip"))

    a = m.anim("idle", 3.0)
    m.wave(a, bones, "rotation", 4, 3.0, 3.0, phase_step=0.1, axis=(1, 0, 0.7))
    for s in range(7):
        t = 3.0 * s / 6
        v = 1 + 0.05 * math.sin(2 * math.pi * s / 6)
        m.key(a, "sac", "scale", t, [v, v, v])
        m.key(a, "nozzle", "rotation", t, [3 * math.sin(2 * math.pi * s / 6 + 1), 5 * math.sin(2 * math.pi * s / 3), 0])
    m.fx(a, 0.4, "bile_drip", "muzzle")
    base_breath(m, a, 3.0)

    a = m.anim("telegraph", 1.2, "hold")
    m.keys(a, "sac", "scale", [(0, 1), (0.9, [1.3, 1.25, 1.3]), (1.2, [1.35, 1.3, 1.35])])
    for i, b in enumerate(bones):
        m.keys(a, b, "rotation", [(0, [0, 0, 0]), (1.0, [8 + i, 0, 0])])
    m.keys(a, "nozzle", "rotation", [(0, [0, 0, 0]), (1.0, [-15, 0, 0])])
    m.fx(a, 0.2, "bile_drip", "muzzle")
    m.fx(a, 0.8, "bile_drip", "muzzle")
    m.sfx(a, 0.0, "arm.spitting.gurgle")

    # volley: three recoiling spits (projectiles spawned by the controller on the particle ticks)
    a = m.anim("volley", 1.5, "once")
    for k in range(3):
        t = 0.1 + k * 0.45
        sc = 1.3 - k * 0.1
        m.keys(a, "sac", "scale", [(t, [sc, sc, sc]), (t + 0.08, [sc - 0.18, sc - 0.1, sc - 0.18]), (t + 0.3, [sc - 0.1] * 3)])
        m.keys(a, "nozzle", "scale", [(t, 1), (t + 0.05, [1.3, 1.3, 0.8]), (t + 0.2, 1)])
        m.keys(a, "nozzle", "rotation", [(t, [-15, 0, 0]), (t + 0.06, [-5, 0, 0]), (t + 0.3, [-14, (k - 1) * 12, 0])])
        m.fx(a, t + 0.05, "bile_splash", "muzzle")
        m.sfx(a, t, "arm.spitting.spit")
    m.keys(a, "sac", "scale", [(1.5, 1)])
    m.keys(a, "nozzle", "rotation", [(1.5, [0, 0, 0])])

    common_anims(m, "spitting", bones)
    return m


# ------------------------------------------------------------------ 6. nerve arm
def nerve():
    """No attack. A braided nerve trunk with a glowing ganglion that pulses buffs to the other five arms.
    Killing it first strips the buff (teaches arm order)."""
    m = Model("nerve_arm", seed=66)
    root, stump, limb = arm_base(m, trunk_r=9)
    add_base_locator(m)
    # three twisted fibre strands as one bone chain
    bones = []
    parent = limb
    y = 28
    for i in range(7):
        b = m.group(f"trunk_{i}", (0, y, 0), parent)
        for s in range(3):
            a = 2 * math.pi * s / 3 + i * 0.55
            r = 3.2 - i * 0.2
            m.box(b, (math.cos(a) * r, y + 6, math.sin(a) * r), [2.6, 13, 2.6], "nerve" if s == 0 else "vein",
                  rot=[math.sin(a) * 14, 0, -math.cos(a) * 14])
        m.box(b, (0, y + 6, 0), [3, 12, 3], "flesh_dark")
        if i % 2 == 0:
            m.box(b, (0, y + 1, 0), [7.5 - i * 0.3, 1.6, 7.5 - i * 0.3], "glow_vein")
        bones.append(b)
        parent = b
        y += 12
    m.blob(limb, (0, 32, 1), 7, "flesh_dark", n=3)
    gang = m.group("ganglion", (0, y, 0), bones[-1])
    m.blob(gang, (0, y + 8, 0), 9, "vein", n=6, jitter=0.3)
    m.box(gang, (0, y + 8, 0), [12, 12, 12], "nerve", inflate=0.3)
    # brain folds
    for k in range(7):
        m.box(gang, (-9 + k * 3, y + 13, 0), [1.6, 3, 14], "flesh_dark", rot=[0, 0, (k - 3) * 6])
    m.box(gang, (0, y + 8, -7.5), [4, 4, 1], "core")
    m.locator(gang, "ganglion", (0, y + 9, 0))
    dend = []
    for d in range(6):
        a = 2 * math.pi * d / 6
        dg = m.group(f"dendrite_{d}", (math.cos(a) * 6, y + 8, math.sin(a) * 6), gang, rot=[0, -math.degrees(a), 0])
        for k in range(3):
            L = 7 - k * 1.5
            x0 = 6 + k * 6.5
            m.box(dg, (math.cos(a) * (x0 + L / 2), y + 8 + k * 2.5 + (d % 2) * 2, math.sin(a) * (x0 + L / 2)),
                  [L + 1, 1.6 - k * 0.3, 1.6 - k * 0.3], "nerve", rot=[0, -math.degrees(a), 12 + k * 10])
        m.box(dg, (math.cos(a) * 25, y + 17 + (d % 2) * 2, math.sin(a) * 25), [2, 2, 2], "glow_vein")
        m.locator(dg, f"tip_{d}", (math.cos(a) * 25, y + 18 + (d % 2) * 2, math.sin(a) * 25))
        dend.append(dg)
    register_common_particles(m, ("nerve_spark", "nerve_pulse"))

    a = m.anim("idle", 4.0)
    m.wave(a, bones, "rotation", 3, 4.0, 4.0, phase_step=0.1, axis=(0.6, 0, 1))
    m.keys(a, "ganglion", "rotation", [(0, [0, 0, 0]), (4, [0, 360, 0])], "linear")
    for d, dg in enumerate(dend):
        for s in range(5):
            m.key(a, dg, "rotation", 4 * s / 4, [0, 0, 6 * math.sin(2 * math.pi * (s / 4 + d / 6))])
    for d in range(0, 6, 2):
        m.fx(a, 0.6 + d * 0.5, "nerve_spark", f"tip_{d}")
    base_breath(m, a, 4.0)

    # buff pulse: every ~5 s (TUNE) the ganglion flashes and a wave runs up the trunk
    a = m.anim("pulse", 1.2, "once")
    for i, b in enumerate(bones):
        t = i * 0.08
        m.keys(a, b, "scale", [(t, 1), (t + 0.08, [1.3, 1, 1.3]), (t + 0.2, 1)])
    m.keys(a, "ganglion", "scale", [(0.55, 1), (0.65, 1.3), (0.8, 0.95), (1.2, 1)])
    for d, dg in enumerate(dend):
        m.keys(a, dg, "rotation", [(0.55, [0, 0, 0]), (0.65, [0, 0, 25]), (1.2, [0, 0, 0])])
    m.fx(a, 0.64, "nerve_pulse", "ganglion")
    m.sfx(a, 0.6, "arm.nerve.pulse")

    common_anims(m, "nerve", bones)
    # on death the ganglion goes dark and limp
    death = next(x for x in m.anims if x["name"].endswith(".death"))
    m.keys(death, "ganglion", "scale", [(0, 1), (0.5, 1.2), (0.6, 0.9), (3.5, 0.85)])
    for dg in dend:
        m.keys(death, dg, "rotation", [(0, [0, 0, 0]), (1.0, [0, 0, -40]), (3.5, [0, 0, -55])])
    m.fx(death, 0.5, "nerve_pulse", "ganglion")
    return m


ALL = [grasping, slam, charging, mouth, spitting, nerve]
