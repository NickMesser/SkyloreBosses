"""Static Deacon model specs: the Deacon (a Beryl caretaker in deacon's vestments: alb, purple dalmatic, the diagonal
stole, a faceted crystal head in a cowl, a halo of shards, one crystal-lattice arm and one arm swinging a static
censer) and its two projectiles. Everything faces north (-Z) like the other boss models; the entity's right side is +X."""
import boss_env  # noqa: F401  (shared pipeline + this boss)
import math
from lib import Model, particle

# Liturgical particle set (Snowstorm previews; in game these are skylore_bosses:<name> particle types).
PX = {
    "static_dust": particle("static_dust", rate=12, duration=1, lifetime=1.5, speed=0.4, radius=0.6, gravity=0.6, drag=1.2,
                            size=(0.08, 0.08), size_end=(0.03, 0.03), color=(0.8, 0.8, 0.84, 1), color_end=(0.35, 0.35, 0.4, 0),
                            uv=(0, 88)),
    "communion_mote": particle("communion_mote", rate=10, duration=1, lifetime=1.6, speed=0.6, direction=[0, 1, 0], radius=0.8,
                               gravity=-0.6, drag=0.8, size=(0.1, 0.1), size_end=(0.02, 0.02), color=(0.85, 0.6, 1, 1),
                               color_end=(1, 0.9, 0.55, 0), uv=(0, 88), blend="add"),
    "beryl_glint": particle("beryl_glint", burst=16, duration=0.1, lifetime=0.5, speed=3, radius=0.3, drag=2.5,
                            size=(0.1, 0.1), size_end=(0.02, 0.02), color=(0.7, 1, 0.9, 1), color_end=(0.3, 0.85, 0.7, 0),
                            uv=(8, 88), blend="add", facing="lookat_direction"),
    "litany_light": particle("litany_light", rate=30, duration=1, lifetime=0.4, speed=0.2, radius=0.3,
                             size=(0.35, 0.35), size_end=(0.05, 0.05), color=(1, 0.95, 0.85, 1), color_end=(0.75, 0.55, 1, 0),
                             uv=(0, 88), blend="add"),
}


def pfx(m, *names):
    for n in names:
        m.particles[n] = PX[n]


# ============================================================ The Static Deacon
def static_deacon():
    m = Model("static_deacon", seed=1113)
    root = m.group("static_deacon", (0, 0, 0))

    # ---- hem: the alb's floor-length skirt, a tiered bell that frays into static at the floor (sways on its own)
    hem = m.group("hem", (0, 14, 0), root)
    for k, (y, w, d, h) in enumerate(((1.8, 17.4, 13.4, 3.0), (4.6, 16.2, 12.4, 2.8), (7.4, 15.0, 11.6, 3.0))):
        m.box(hem, (0, y, 0), [w, h, d], "alb", rot=[0, (k % 2) * 3 - 1.5, 0])
    for k in range(14):
        a = 2 * math.pi * k / 14
        m.box(hem, (math.cos(a) * 8.2, 0.5, math.sin(a) * 6.2), [2.2 + (k % 3) * 0.6, 1.2, 1.1], "static",
              rot=[0, -math.degrees(a), 10 + (k % 4) * 6])
    for k in range(6):
        a = 2 * math.pi * (k + 0.5) / 6
        m.box(hem, (math.cos(a) * 6.5, -0.4, math.sin(a) * 4.8), [1.2, 1.0, 1.2], "static", rot=[20, 45, 0])

    body = m.group("body", (0, 14, 0), root)
    # dalmatic: knee-length purple tunic over the alb, flaring slightly, gold hem band and clavi
    for y, w, d, h in ((11.4, 15.4, 11.8, 5.2), (16.4, 14.6, 11.2, 5), (21.2, 13.8, 10.6, 5), (25.6, 13.2, 10, 4)):
        m.box(body, (0, y, 0), [w, h, d], "dalmatic")
    m.box(body, (0, 9.1, 0), [15.8, 1.3, 12.2], "trim")
    for s in (-1, 1):
        m.box(body, (s * 3.2, 18.2, -6.0), [1.4, 18, 0.5], "trim", rot=[3, 0, 0])
        m.box(body, (s * 3.2, 18.2, 6.0), [1.4, 18, 0.5], "trim", rot=[-3, 0, 0])
    m.box(body, (0, 14.5, -6.15), [4.2, 7.5, 0.4], "stole")   # the stole's ends below the cincture
    m.box(body, (0, 10.4, -6.2), [4.6, 1.2, 0.5], "static")
    m.box(body, (0, 28.2, 0), [11.6, 1.6, 8.8], "shadow")    # cincture
    m.box(body, (-4.5, 26.4, -4.6), [1, 4, 1], "shadow", rot=[0, 0, 10])

    torso = m.group("torso", (0, 29, 0), body)
    m.box(torso, (0, 32.5, 0), [12.2, 6, 8.6], "dalmatic")
    m.box(torso, (0, 37.6, 0), [13.4, 5.2, 9.4], "dalmatic")
    m.box(torso, (0, 35, 4.9), [9.5, 9, 0.6], "dalmatic")
    # open collar: the Beryl body shows through, a static core at the sternum
    m.box(torso, (0, 38.2, -4.75), [5, 4.4, 0.6], "beryl_dark")
    for x, y, r in ((-1.5, 38.8, 25), (1.4, 38.2, -30), (0, 36.8, 0), (-0.5, 40.2, 50)):
        m.box(torso, (x, y, -5.2), [1.2, 2.8, 1.2], "beryl", rot=[20, 0, r])
    core = m.group("core", (0, 36.2, -5.3), torso)
    m.box(core, (0, 36.2, -5.4), [2.4, 2.4, 1.1], "core_glow", rot=[0, 0, 45])
    m.locator(core, "core", (0, 36.2, -5.5))
    # shoulder mantle (sloped) and a high collar
    for s in (-1, 1):
        m.box(torso, (s * 5.2, 40.4, 0), [6.2, 1.5, 10.4], "dalmatic", rot=[0, 0, s * -24])
        m.box(torso, (s * 5.4, 40.0, 0), [6.2, 0.5, 10.6], "trim", rot=[0, 0, s * -24])
    m.box(torso, (0, 41.4, 1.4), [7.4, 2.2, 6.4], "dalmatic")
    # the deacon's stole: over the left shoulder, crossing the chest to the right hip
    stole = m.group("stole", (0, 34, 0), torso)
    m.box(stole, (0.6, 34.4, -5.0), [2.4, 15, 0.6], "stole", rot=[0, 0, -34])
    m.box(stole, (0.6, 34.4, 5.25), [2.4, 15, 0.6], "stole", rot=[0, 0, 34])
    m.box(stole, (4.6, 28.2, -4.9), [2.4, 3.6, 0.7], "stole", rot=[0, 0, -12])
    for k in range(3):
        m.box(stole, (-2.6 + k * 2.8, 39 - k * 3.6, -5.45), [1, 1, 0.3], "glyph_glow", rot=[0, 0, 45])
    # Beryl spines breaking out through the back of the vestment
    for k, (x, y, rx, rz) in enumerate(((-3.2, 39, -38, 22), (3.0, 38.5, -42, -18), (0, 35, -52, 0), (-4.6, 33.5, -32, 35), (4.6, 33.5, -32, -35))):
        m.box(torso, (x, y, 5.8), [1.6, 6.5 - k * 0.6, 1.6], "beryl", rot=[rx, 0, rz])

    # ---- head: a faceted crystal skull under a peaked cowl, a vertical slit of static light
    head = m.group("head", (0, 42, 0), torso)
    m.box(head, (0, 42.8, 0), [4, 1.8, 4], "beryl_dark")
    m.box(head, (0, 46.6, -0.4), [5.2, 6.4, 5.2], "beryl", rot=[0, 45, 0])
    m.box(head, (0, 50.2, -0.4), [3, 2.2, 3], "beryl", rot=[0, 45, 0])
    m.box(head, (0, 46.4, -3.95), [0.8, 4.6, 0.4], "core_glow", name="face_slit")
    cowl = m.group("cowl", (0, 44, 0), head)
    m.box(cowl, (0, 48.5, 5.0), [9.4, 11, 1.2], "dalmatic")
    for s in (-1, 1):
        m.box(cowl, (s * 4.2, 48.2, 0.6), [1.2, 10.8, 9.2], "dalmatic", rot=[0, 0, s * 8])
        m.box(cowl, (s * 3.7, 48.2, 0.4), [0.4, 9.8, 8.4], "shadow", rot=[0, 0, s * 8])
    m.box(cowl, (0, 53.4, 0.8), [7.2, 1.4, 9.4], "dalmatic")
    m.box(cowl, (0, 55.2, 2.8), [5.6, 3.4, 5.6], "dalmatic", rot=[-35, 0, 0])      # the peak, swept back
    m.box(cowl, (0, 52.6, 0.9), [7.6, 0.4, 8.2], "shadow")
    m.box(cowl, (0, 43.4, -4.1), [10.4, 1.2, 1.2], "trim")
    m.locator(head, "head", (0, 46.5, -3))

    # halo: eight Beryl shards on a ring behind the head (the ring spins in its own plane)
    halo = m.group("halo", (0, 50, 6.8), head)
    m.ring(halo, (0, 50, 6.8), 8.5, 8, [1.4, 4.4, 1.2], "halo", axis="z")
    m.ring(halo, (0, 50, 7.1), 8.5, 16, [0.6, 1.4, 0.6], "glyph_glow", axis="z", phase=0.2)
    m.locator(halo, "halo", (0, 50, 6.8))

    # ---- left arm: a bell sleeve, then forearm and hand as an open crystal lattice (lash, bolts)
    arm_l = m.group("arm_l", (-6.6, 39.5, 0), torso)
    m.box(arm_l, (-8.2, 36.6, 0), [3.8, 6, 5], "dalmatic", rot=[0, 0, 6])
    m.box(arm_l, (-8.9, 31.4, 0), [5.6, 5, 6.6], "dalmatic", rot=[0, 0, 4])
    m.box(arm_l, (-9.1, 28.6, 0), [6, 0.8, 7], "trim", rot=[0, 0, 4])
    m.box(arm_l, (-9.1, 29.4, 0), [4.4, 0.6, 5.4], "shadow")
    fore_l = m.group("forearm_l", (-9.2, 29, 0), arm_l)
    for dx, dz, rz, rx in ((-1, -1, 8, -6), (1, -1, -8, -6), (0, 1.2, 0, 8)):
        m.box(fore_l, (-9.2 + dx * 0.9, 23.4, dz * 0.9), [0.9, 11, 0.9], "beryl", rot=[rx, 0, rz])
    for y in (26.6, 22.4, 18.6):
        m.box(fore_l, (-9.2, y, 0), [2.4, 0.8, 2.4], "beryl_dark", rot=[0, 45, 0])
    hand_l = m.group("hand_l", (-9.2, 17.6, 0), fore_l)
    m.box(hand_l, (-9.2, 17.0, 0), [2.2, 1.6, 2.2], "beryl", rot=[0, 45, 0])
    for k in range(4):
        a = math.radians(-60 + k * 40)
        m.box(hand_l, (-9.2 + math.sin(a) * 1.3, 14.4, -math.cos(a) * 0.6), [0.7, 4.4, 0.7], "beryl", rot=[-12, 0, math.degrees(a) * 0.4])
    m.box(hand_l, (-9.2, 16.2, -1.4), [0.8, 0.8, 0.8], "glyph_glow")
    m.locator(hand_l, "hand_l", (-9.2, 14, -1))

    # ---- right arm: bell sleeve, a Beryl hand holding the censer's chain
    arm_r = m.group("arm_r", (6.6, 39.5, 0), torso)
    m.box(arm_r, (8.2, 36.6, 0), [3.8, 6, 5], "dalmatic", rot=[0, 0, -6])
    m.box(arm_r, (8.9, 31.4, 0), [5.6, 5, 6.6], "dalmatic", rot=[0, 0, -4])
    m.box(arm_r, (9.1, 28.6, 0), [6, 0.8, 7], "trim", rot=[0, 0, -4])
    m.box(arm_r, (9.1, 29.4, 0), [4.4, 0.6, 5.4], "shadow")
    fore_r = m.group("forearm_r", (9.2, 29, 0), arm_r)
    m.box(fore_r, (9.2, 24.8, 0), [2, 8, 2], "beryl", rot=[0, 45, 0])
    m.box(fore_r, (9.2, 27.6, 0), [2.8, 1, 2.8], "beryl_dark")
    hand_r = m.group("hand_r", (9.2, 21, 0), fore_r)
    m.box(hand_r, (9.2, 20, -0.4), [2.6, 2.4, 2.6], "beryl", rot=[0, 45, 0])
    m.locator(hand_r, "hand_r", (9.2, 19, -0.5))
    chain0 = m.group("chain_0", (9.2, 19, -0.5), hand_r)
    for k in range(4):
        m.box(chain0, (9.2, 17.6 - k * 1.5, -0.5), [0.6 if k % 2 else 1.2, 1.4, 1.2 if k % 2 else 0.6], "chain")
    chain1 = m.group("chain_1", (9.2, 12, -0.5), chain0)
    for k in range(3):
        m.box(chain1, (9.2, 10.8 - k * 1.5, -0.5), [0.6 if k % 2 else 1.2, 1.4, 1.2 if k % 2 else 0.6], "chain")
    censer = m.group("censer", (9.2, 7.6, -0.5), chain1)
    m.box(censer, (9.2, 7.0, -0.5), [1.8, 1.2, 1.8], "brass", rot=[0, 45, 0])
    m.box(censer, (9.2, 5.9, -0.5), [3.0, 1.0, 3.0], "brass", rot=[0, 45, 0])
    m.box(censer, (9.2, 4.0, -0.5), [3.8, 3.0, 3.8], "brass", rot=[0, 45, 0])
    for s in (-1, 1):
        m.box(censer, (9.2 + s * 1.95, 4.0, -0.5), [0.3, 2.0, 1.6], "beryl")
        m.box(censer, (9.2, 4.0, -0.5 + s * 1.95), [1.6, 2.0, 0.3], "beryl")
    m.box(censer, (9.2, 4.0, -0.5), [2.4, 1.8, 2.4], "ender_flame")
    m.box(censer, (9.2, 2.2, -0.5), [2.6, 0.8, 2.6], "brass", rot=[0, 45, 0])
    m.locator(censer, "censer", (9.2, 4.0, -0.5))

    m.locator(root, "feet", (0, 0, 0))
    pfx(m, "static_dust", "communion_mote", "beryl_glint", "litany_light")

    # ================================================================ animations
    def breathe(a, length, amp=0.5, halo_turns=1, censer_amp=10, hem_amp=2.5):
        n = 8
        for s in range(n + 1):
            t = length * s / n
            ph = 2 * math.pi * s / n
            m.key(a, "body", "position", t, [0, amp * math.sin(ph), 0])
            m.key(a, "hem", "rotation", t, [hem_amp * math.sin(ph + 0.6), 0, hem_amp * 0.6 * math.cos(ph)])
            m.key(a, "chain_0", "rotation", t, [censer_amp * 0.4 * math.cos(ph), 0, censer_amp * math.sin(ph)])
            m.key(a, "chain_1", "rotation", t, [0, 0, censer_amp * 0.5 * math.sin(ph - 0.8)])
            m.key(a, "head", "rotation", t, [2 * math.sin(ph + 1.3), 3 * math.sin(ph * 0.5), 0])
        if halo_turns:
            m.keys(a, "halo", "rotation", [(0, [0, 0, 0]), (length, [0, 0, 360 * halo_turns])], "linear")

    def kneel(a, t0, t1, depth=-6, hands=(-60, 18)):
        m.keys(a, "body", "position", [(t0, [0, 0, 0]), (t1, [0, depth, 0])])
        m.keys(a, "hem", "scale", [(t0, [1, 1, 1]), (t1, [1.15, 0.45, 1.15])])
        m.keys(a, "hem", "position", [(t0, [0, 0, 0]), (t1, [0, depth * 0.55, 0])])
        m.keys(a, "torso", "rotation", [(t0, [0, 0, 0]), (t1, [12, 0, 0])])
        m.keys(a, "arm_l", "rotation", [(t0, [0, 0, 0]), (t1, [hands[0], 0, hands[1]])])
        m.keys(a, "arm_r", "rotation", [(t0, [0, 0, 0]), (t1, [hands[0], 0, -hands[1]])])

    def held_kneel(a, length, depth=-6, hands=(-60, 18)):
        for t in (0, length):
            m.key(a, "body", "position", t, [0, depth, 0])
            m.key(a, "hem", "scale", t, [1.15, 0.45, 1.15])
            m.key(a, "hem", "position", t, [0, depth * 0.55, 0])
            m.key(a, "torso", "rotation", t, [12, 0, 0])
            m.key(a, "arm_l", "rotation", t, [hands[0], 0, hands[1]])
            m.key(a, "arm_r", "rotation", t, [hands[0], 0, -hands[1]])

    # ---- loops
    a = m.anim("idle", 3.0)
    breathe(a, 3.0)
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 4]), (1.5, [3, 0, 6]), (3, [0, 0, 4])])
    m.fx(a, 0.8, "static_dust", "feet")
    m.fx(a, 2.2, "static_dust", "feet")
    m.sfx(a, 0.0, "idle.hum")

    a = m.anim("walk", 1.6)
    breathe(a, 1.6, amp=0.8, halo_turns=1, censer_amp=18, hem_amp=6)
    m.keys(a, "torso", "rotation", [(0, [6, 0, 0]), (1.6, [6, 0, 0])])
    for s in range(5):
        t = 1.6 * s / 4
        m.key(a, "arm_l", "rotation", t, [12 * math.sin(2 * math.pi * s / 4), 0, 5])
        m.key(a, "arm_r", "rotation", t, [-12 * math.sin(2 * math.pi * s / 4), 0, -5])
    m.fx(a, 0.0, "static_dust", "feet")
    m.fx(a, 0.8, "static_dust", "feet")

    a = m.anim("commune", 2.0)       # on a live flagstone: palms open to the floor, the core breathes with the regen
    breathe(a, 2.0, amp=0.3, halo_turns=1, censer_amp=6, hem_amp=1.5)
    m.keys(a, "arm_l", "rotation", [(0, [-10, 0, 28]), (1, [-14, 0, 32]), (2, [-10, 0, 28])])
    m.keys(a, "arm_r", "rotation", [(0, [-10, 0, -28]), (1, [-14, 0, -32]), (2, [-10, 0, -28])])
    m.keys(a, "core", "scale", [(0, 1), (1, 1.5), (2, 1)])
    m.fx(a, 0.0, "communion_mote", "feet")
    m.fx(a, 1.0, "communion_mote", "feet")

    a = m.anim("vigil", 3.0)         # on the plinth: kneeling, head bowed, hands folded
    held_kneel(a, 3.0, hands=(-62, 22))
    m.keys(a, "head", "rotation", [(0, [16, 0, 0]), (1.5, [20, 0, 0]), (3, [16, 0, 0])])
    m.keys(a, "halo", "rotation", [(0, [0, 0, 0]), (3, [0, 0, 180])], "linear")
    m.keys(a, "chain_0", "rotation", [(0, [0, 0, 4]), (1.5, [0, 0, -4]), (3, [0, 0, 4])])
    m.fx(a, 0.5, "communion_mote", "core")
    m.fx(a, 2.0, "communion_mote", "core")

    a = m.anim("reseed", 2.0)        # channelling the rite: kneeling, palms flat on the altar, halo spinning fast
    held_kneel(a, 2.0, depth=-7, hands=(-78, 10))
    m.keys(a, "halo", "rotation", [(0, [0, 0, 0]), (2, [0, 0, 720])], "linear")
    m.keys(a, "core", "scale", [(0, 1.3), (0.5, 1.8), (1, 1.3), (1.5, 1.8), (2, 1.3)])
    for t in (0.0, 0.5, 1.0, 1.5):
        m.fx(a, t, "communion_mote", "hand_l")
        m.fx(a, t + 0.25, "communion_mote", "hand_r")

    a = m.anim("stagger", 1.0)
    for s in range(5):
        t = s / 4
        m.key(a, "body", "rotation", t, [-10 + 4 * math.sin(s * 2.2), 0, 6 * math.cos(s * 1.9)])
        m.key(a, "head", "rotation", t, [-12, 8 * math.sin(s * 1.7), 0])
    m.keys(a, "arm_l", "rotation", [(0, [20, 0, 40]), (1, [20, 0, 40])])
    m.keys(a, "arm_r", "rotation", [(0, [20, 0, -40]), (1, [20, 0, -40])])
    m.fx(a, 0.0, "static_dust", "core")
    m.fx(a, 0.5, "static_dust", "head")

    a = m.anim("beam", 1.0)          # litany: both hands raised, the core blazing
    for t in (0, 1.0):
        m.key(a, "arm_l", "rotation", t, [-165, 0, 12])
        m.key(a, "arm_r", "rotation", t, [-165, 0, -12])
        m.key(a, "head", "rotation", t, [-20, 0, 0])
    m.keys(a, "core", "scale", [(0, 1.8), (0.5, 2.2), (1, 1.8)])
    m.keys(a, "halo", "rotation", [(0, [0, 0, 0]), (1, [0, 0, 360])], "linear")
    m.fx(a, 0.0, "litany_light", "hand_l")
    m.fx(a, 0.5, "litany_light", "hand_l")

    a = m.anim("static_step", 0.5)   # dissolving into static between two places
    m.keys(a, "static_deacon", "scale", [(0, [0.95, 1.05, 0.95]), (0.25, [1.05, 0.9, 1.05]), (0.5, [0.95, 1.05, 0.95])])
    m.fx(a, 0.0, "static_dust", "core")
    m.fx(a, 0.25, "static_dust", "head")

    # ---- one-shots
    a = m.anim("vesting", 6.0, "once")   # P0: rises out of the altar, vestments settle, the halo assembles
    m.keys(a, "static_deacon", "position", [(0, [0, -34, 0]), (1.0, [0, -34, 0]), (4.0, [0, 0, 0])])
    m.keys(a, "static_deacon", "scale", [(0, [0.7, 0.7, 0.7]), (1.0, [0.7, 0.7, 0.7]), (4.0, [1, 1, 1])])
    m.keys(a, "halo", "scale", [(0, 0.01), (3.8, 0.01), (4.6, 1.25), (5.0, 1)])
    m.keys(a, "halo", "rotation", [(0, [0, 0, 0]), (6, [0, 0, 540])], "linear")
    m.keys(a, "torso", "rotation", [(0, [40, 0, 0]), (3.0, [40, 0, 0]), (4.5, [-8, 0, 0]), (5.5, [0, 0, 0])])
    m.keys(a, "arm_l", "rotation", [(0, [30, 0, 10]), (4.4, [30, 0, 10]), (5.2, [-30, 0, 45]), (6, [0, 0, 4])])
    m.keys(a, "arm_r", "rotation", [(0, [30, 0, -10]), (4.4, [30, 0, -10]), (5.2, [-30, 0, -45]), (6, [0, 0, 0])])
    m.keys(a, "core", "scale", [(0, 0.2), (4.2, 0.2), (4.6, 2.2), (6, 1)])
    for t in (0.2, 1.0, 1.8, 2.6, 3.4):
        m.fx(a, t, "static_dust", "feet")
    m.fx(a, 4.5, "beryl_glint", "halo")
    m.fx(a, 4.6, "communion_mote", "core")
    m.sfx(a, 0.0, "vesting.rise")

    a = m.anim("chime", 2.0, "once")     # P0 vesting chime: censer raised on high, struck at 2.0
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (1.4, [-160, 0, -10]), (1.9, [-165, 0, -10]), (2.0, [-120, 0, -10])])
    m.keys(a, "chain_0", "rotation", [(0, [0, 0, 0]), (1.4, [0, 0, 0]), (1.7, [40, 0, 0]), (2.0, [-60, 0, 0])])
    m.keys(a, "head", "rotation", [(0, [0, 0, 0]), (1.4, [-15, 0, 0]), (2.0, [-10, 0, 0])])
    m.fx(a, 1.95, "beryl_glint", "censer")
    m.sfx(a, 0.0, "chime.windup")

    a = m.anim("pulse_windup", 2.0, "once")
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (1.8, [-30, 0, 55])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (1.8, [-30, 0, -55])])
    m.keys(a, "core", "scale", [(0, 1), (1.8, 2.4)])
    m.keys(a, "body", "position", [(0, [0, 0, 0]), (1.8, [0, 1.5, 0])])
    for t in (0.3, 0.9, 1.5):
        m.fx(a, t, "communion_mote", "core")
    m.sfx(a, 0.0, "pulse.charge")

    a = m.anim("pulse", 1.0, "once")
    m.keys(a, "arm_l", "rotation", [(0, [-30, 0, 55]), (0.12, [30, 0, 25]), (1, [0, 0, 4])])
    m.keys(a, "arm_r", "rotation", [(0, [-30, 0, -55]), (0.12, [30, 0, -25]), (1, [0, 0, 0])])
    m.keys(a, "body", "position", [(0, [0, 1.5, 0]), (0.12, [0, -1.5, 0]), (1, [0, 0, 0])])
    m.keys(a, "core", "scale", [(0, 2.4), (0.2, 1), (1, 1)])
    m.fx(a, 0.05, "communion_mote", "feet")

    a = m.anim("lash_windup", 0.8, "once")   # the lattice arm draws back across the body
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (0.7, [-70, -55, 30]), (0.8, [-72, -60, 32])])
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (0.7, [0, -25, 0])])
    m.fx(a, 0.3, "beryl_glint", "hand_l")
    m.sfx(a, 0.0, "lash.windup")

    a = m.anim("lash", 0.6, "once")
    m.keys(a, "arm_l", "rotation", [(0, [-72, -60, 32]), (0.12, [-80, 70, 20]), (0.6, [0, 0, 4])])
    m.keys(a, "torso", "rotation", [(0, [0, -25, 0]), (0.12, [0, 30, 0]), (0.6, [0, 0, 0])])
    m.fx(a, 0.05, "beryl_glint", "hand_l")

    a = m.anim("bolt_cast", 1.0, "once")
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (0.85, [-88, 8, 0]), (1.0, [-95, 8, 0])])
    m.keys(a, "forearm_l", "rotation", [(0, [0, 0, 0]), (0.85, [-10, 0, 0]), (1.0, [5, 0, 0])])
    m.fx(a, 0.4, "beryl_glint", "hand_l")
    m.fx(a, 0.95, "beryl_glint", "hand_l")
    m.sfx(a, 0.0, "bolt.windup")

    a = m.anim("shatter_windup", 1.5, "once")   # both arms up, rising on the static
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (1.3, [-170, 0, 20])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (1.3, [-170, 0, -20])])
    m.keys(a, "body", "position", [(0, [0, 0, 0]), (1.3, [0, 3, 0])])
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (1.3, [-10, 0, 0])])
    m.fx(a, 0.5, "static_dust", "feet")
    m.fx(a, 1.0, "static_dust", "feet")
    m.sfx(a, 0.0, "shatter.windup")

    a = m.anim("shatter", 1.0, "once")
    m.keys(a, "arm_l", "rotation", [(0, [-170, 0, 20]), (0.1, [-20, 0, 30]), (1, [0, 0, 4])])
    m.keys(a, "arm_r", "rotation", [(0, [-170, 0, -20]), (0.1, [-20, 0, -30]), (1, [0, 0, 0])])
    m.keys(a, "body", "position", [(0, [0, 3, 0]), (0.1, [0, -4, 0]), (1, [0, 0, 0])])
    m.keys(a, "torso", "rotation", [(0, [-10, 0, 0]), (0.1, [30, 0, 0]), (1, [0, 0, 0])])
    m.fx(a, 0.1, "static_dust", "feet")
    m.fx(a, 0.12, "beryl_glint", "feet")

    a = m.anim("shard_cast", 1.5, "once")   # the halo flares and sheds shards
    m.keys(a, "halo", "scale", [(0, 1), (1.2, 1.45), (1.5, 1)])
    m.keys(a, "halo", "rotation", [(0, [0, 0, 0]), (1.5, [0, 0, 540])], "linear")
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (1.2, [-40, 0, 50])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (1.2, [-40, 0, -50])])
    m.keys(a, "head", "rotation", [(0, [0, 0, 0]), (1.2, [-18, 0, 0]), (1.5, [0, 0, 0])])
    for t in (0.3, 0.8, 1.3):
        m.fx(a, t, "beryl_glint", "halo")
    m.sfx(a, 0.0, "shard.windup")

    a = m.anim("pull_windup", 1.25, "once")  # the censer is whirled on its chain, then flung
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (0.4, [-100, 0, -20]), (1.25, [-110, 0, -20])])
    for s in range(6):
        t = 0.3 + s * 0.19
        m.key(a, "chain_0", "rotation", t, [70 * math.cos(s * 2.1), 0, 70 * math.sin(s * 2.1)])
    m.fx(a, 0.6, "litany_light", "censer")
    m.sfx(a, 0.0, "pull.windup")

    a = m.anim("pull", 0.5, "once")
    m.keys(a, "arm_r", "rotation", [(0, [-110, 0, -20]), (0.12, [20, 0, -15]), (0.5, [0, 0, 0])])
    m.keys(a, "chain_0", "rotation", [(0, [-90, 0, 0]), (0.2, [30, 0, 0]), (0.5, [0, 0, 0])])
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (0.12, [-12, 0, 0]), (0.5, [0, 0, 0])])

    a = m.anim("beam_windup", 2.0, "once")   # hands together, lifted slowly: the litany is read
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (0.8, [-70, 0, -25]), (2.0, [-165, 0, 12])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (0.8, [-70, 0, 25]), (2.0, [-165, 0, -12])])
    m.keys(a, "head", "rotation", [(0, [0, 0, 0]), (2.0, [-20, 0, 0])])
    m.keys(a, "core", "scale", [(0, 1), (2.0, 1.8)])
    for t in (0.5, 1.2, 1.8):
        m.fx(a, t, "litany_light", "core")
    m.sfx(a, 0.0, "beam.windup")

    a = m.anim("reseed_begin", 2.0, "once")
    kneel(a, 0, 1.6, depth=-7, hands=(-78, 10))
    m.keys(a, "halo", "scale", [(0, 1), (1.8, 1.3), (2, 1)])
    m.fx(a, 1.6, "communion_mote", "hand_l")
    m.fx(a, 1.65, "communion_mote", "hand_r")
    m.sfx(a, 0.0, "reseed.begin")

    a = m.anim("hurt", 0.4, "once")
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (0.06, [-6, 0, 4]), (0.4, [0, 0, 0])])
    m.keys(a, "halo", "position", [(0, [0, 0, 0]), (0.06, [0, 0.8, 0]), (0.4, [0, 0, 0])])
    m.fx(a, 0.0, "beryl_glint", "core")
    m.sfx(a, 0.0, "hurt")

    a = m.anim("knockoff", 1.5, "once")      # vigil broken: thrown off the altar
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (0.15, [-35, 0, 10]), (0.8, [-20, 0, 6]), (1.5, [0, 0, 0])])
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (0.15, [-60, 0, 70]), (1.5, [0, 0, 4])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (0.15, [-60, 0, -70]), (1.5, [0, 0, 0])])
    m.keys(a, "halo", "rotation", [(0, [0, 0, 0]), (0.2, [25, 0, 90]), (1.5, [0, 0, 0])])
    m.fx(a, 0.05, "static_dust", "core")
    m.fx(a, 0.1, "beryl_glint", "halo")

    a = m.anim("step_out", 1.0, "once")
    m.keys(a, "static_deacon", "scale", [(0, 1), (0.8, [0.2, 1.2, 0.2]), (1.0, [0.05, 1.3, 0.05])])
    m.fx(a, 0.0, "static_dust", "core")
    m.fx(a, 0.5, "static_dust", "head")

    a = m.anim("step_in", 0.6, "once")
    m.keys(a, "static_deacon", "scale", [(0, [0.05, 1.3, 0.05]), (0.4, [1.1, 0.95, 1.1]), (0.6, 1)])
    m.fx(a, 0.0, "static_dust", "core")

    a = m.anim("death", 7.0, "hold")         # the static lets go: kneels, the halo falls, the vestments empty
    kneel(a, 0.3, 1.5, depth=-8, hands=(-20, 30))
    m.keys(a, "halo", "position", [(0, [0, 0, 0]), (2.5, [0, 0, 0]), (3.2, [0, -30, -2]), (7, [0, -30, -2])])
    m.keys(a, "halo", "rotation", [(0, [0, 0, 0]), (2.5, [0, 0, 90]), (3.2, [80, 0, 110]), (7, [80, 0, 110])])
    m.keys(a, "head", "rotation", [(0, [0, 0, 0]), (1.5, [25, 0, 0]), (4.5, [30, 0, 8]), (5.2, [70, 0, 30]), (7, [70, 0, 30])])
    m.keys(a, "head", "position", [(0, [0, 0, 0]), (4.5, [0, 0, 0]), (5.4, [2, -14, -6]), (7, [2, -16, -6])])
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (1.5, [12, 0, 0]), (4.5, [25, 0, 6]), (6, [45, 0, 10]), (7, [45, 0, 10])])
    m.keys(a, "body", "scale", [(0, 1), (4.0, 1), (6.5, [1.1, 0.25, 1.1]), (7, [1.1, 0.2, 1.1])])
    m.keys(a, "chain_0", "rotation", [(0, [0, 0, 0]), (2, [0, 0, 60]), (7, [0, 0, 80])])
    m.keys(a, "core", "scale", [(0, 1), (3.5, 2), (4.0, 0.01), (7, 0.01)])
    for t in (0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0):
        m.fx(a, t, "static_dust", "core")
    m.fx(a, 3.2, "beryl_glint", "halo")
    m.fx(a, 4.0, "communion_mote", "core")
    return m


# ============================================================ projectiles
def static_bolt():
    m = Model("static_bolt", seed=21)
    root = m.group("bolt", (0, 2, 0))
    m.box(root, (0, 2, 0), [1.6, 1.6, 7], "beryl", rot=[0, 0, 45])
    m.box(root, (0, 2, -3.6), [1, 1, 1.6], "beryl", rot=[0, 0, 45])
    m.box(root, (0, 2, 0), [0.8, 0.8, 5], "core_glow")
    m.locator(root, "trail", (0, 2, 3.5))
    pfx(m, "beryl_glint")
    a = m.anim("fly", 0.5)
    m.keys(a, "bolt", "rotation", [(0, [0, 0, 0]), (0.5, [0, 0, 360])], "linear")
    return m


def homing_shard():
    m = Model("homing_shard", seed=22)
    root = m.group("shard", (0, 2.5, 0))
    m.box(root, (0, 2.5, 0), [2.2, 2.2, 6], "beryl", rot=[0, 0, 45])
    m.box(root, (0.6, 3, 1.5), [1.4, 1.4, 4], "beryl_dark", rot=[10, 20, 45])
    m.box(root, (-0.5, 2, -2.8), [1, 1, 2.2], "halo", rot=[0, -15, 45])
    m.box(root, (0, 2.5, 0), [0.8, 0.8, 3], "glyph_glow")
    m.locator(root, "trail", (0, 2.5, 3))
    pfx(m, "static_dust", "beryl_glint")
    a = m.anim("fly", 1.0)
    m.keys(a, "shard", "rotation", [(0, [0, 0, 0]), (1.0, [0, 0, 360])], "linear")
    m.fx(a, 0.0, "static_dust", "trail")
    return m


ALL = [static_deacon, static_bolt, homing_shard]
