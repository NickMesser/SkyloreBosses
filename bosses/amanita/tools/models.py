"""Amanita model specs: Amanita, the Hollow Bloom (a gaunt pale fungal woman, 3.2 blocks tall: a torn volva cup at the
floor, a ghost-white stalk robe with a ragged ring, long hyphae-fingered arms, a shadow hood, and a great bruised-violet
cap of eight petals over lilac bioluminescent gills; the petals flare open in the dark and curl shut when she snuffs),
her two servants (lamp-eater: a pale moth-grub with a lantern jaw and an ember belly; hollow-spawn: a spore-husk thrall
under a small cap) and her hollow_bolt. Everything faces north (-Z) like the other boss models; the entity's right is +X."""
import boss_env  # noqa: F401  (shared pipeline + this boss)
import math
from lib import Model, particle

# Hollow particle set (Snowstorm previews; in game these are skylore_bosses:<name> particle types).
PX = {
    "hollow_spore": particle("hollow_spore", rate=10, duration=1, lifetime=2.5, speed=0.3, radius=1.0, gravity=0.15, drag=1.5,
                             size=(0.08, 0.08), size_end=(0.04, 0.04), color=(0.72, 0.62, 0.82, 1), color_end=(0.38, 0.3, 0.45, 0),
                             uv=(0, 88)),
    "gill_glow": particle("gill_glow", rate=12, duration=1, lifetime=1.2, speed=0.3, radius=0.8, gravity=0.3, drag=1.0,
                          size=(0.12, 0.12), size_end=(0.02, 0.02), color=(0.78, 0.55, 1, 1), color_end=(0.45, 0.2, 0.85, 0),
                          uv=(0, 88), blend="add"),
    "snuff_smoke": particle("snuff_smoke", burst=14, duration=0.1, lifetime=1.7, speed=0.8, direction=[0, 1, 0], radius=0.2,
                            gravity=-0.6, drag=1.2, size=(0.2, 0.2), size_end=(0.5, 0.5), color=(0.16, 0.13, 0.18, 0.9),
                            color_end=(0.05, 0.04, 0.06, 0), uv=(0, 0), frames=8),
    "veil_mist": particle("veil_mist", rate=16, duration=1, lifetime=2.2, speed=0.3, radius=1.2, gravity=-0.1, drag=0.8,
                          size=(0.4, 0.4), size_end=(0.9, 0.9), color=(0.24, 0.17, 0.3, 0.7), color_end=(0.1, 0.06, 0.14, 0),
                          uv=(0, 0), frames=8),
    "ember": particle("ember", rate=8, duration=1, lifetime=1.2, speed=0.6, direction=[0, 1, 0], radius=0.3, gravity=-0.4,
                      size=(0.08, 0.08), size_end=(0.02, 0.02), color=(1, 0.6, 0.2, 1), color_end=(0.8, 0.2, 0.05, 0),
                      uv=(0, 88), blend="add"),
}


def pfx(m, *names):
    for n in names:
        m.particles[n] = PX[n]


PETALS = 8
CAP_Y = 45.4


# ============================================================ Amanita
def amanita():
    m = Model("amanita", seed=4114)
    root = m.group("amanita", (0, 0, 0))

    # ---- volva: the torn cup she grows out of, and hyphae roots across the floor
    volva = m.group("volva", (0, 0, 0), root)
    for k, (y, w, d, h) in enumerate(((1.2, 18, 15, 2.4), (3.4, 16.4, 13.6, 2.4), (5.4, 14.2, 12, 2.0))):
        m.box(volva, (0, y, 0), [w, h, d], "volva", rot=[0, (k % 2) * 4 - 2, 0])
    for k in range(10):
        a = 2 * math.pi * k / 10 + 0.2
        m.box(volva, (math.cos(a) * 8.6, 6.4 + (k % 3) * 0.6, math.sin(a) * 7.0), [3.2, 2.4 + (k % 2), 0.8], "volva",
              rot=[0, -math.degrees(a) + 90, (k % 2) * 16 - 8], name=f"volva_tear_{k}")
    for k in range(7):
        a = 2 * math.pi * k / 7
        m.box(volva, (math.cos(a) * 11, 0.3, math.sin(a) * 9.5), [6.5, 0.6, 1.0], "hyphae", rot=[0, -math.degrees(a), 0])
        m.box(volva, (math.cos(a) * 14.5, 0.25, math.sin(a) * 12.5), [3.2, 0.5, 0.8], "hyphae", rot=[0, -math.degrees(a) + 20, 0])

    # ---- body: the stalk robe, tapering up, with the ragged ring (annulus) at the waist
    body = m.group("body", (0, 6, 0), root)
    for y, w, d, h in ((8.4, 13.4, 11.4, 5.0), (12.9, 11.2, 9.6, 4.4), (17.2, 9.4, 8.2, 4.4), (21.4, 8.0, 7.0, 4.2), (25.4, 7.0, 6.0, 4.0)):
        m.box(body, (0, y, 0), [w, h, d], "stipe")
    for x in (-2.8, -0.9, 0.9, 2.8):
        m.box(body, (x, 17.5 - abs(x) * 0.6, -4.3 + abs(x) * 0.25), [0.5, 16 - abs(x) * 1.2, 0.4], "volva", rot=[-6, 0, x * 1.5])
    ring = m.group("ring", (0, 27.2, 0), body)
    m.box(ring, (0, 27.2, 0), [9.6, 1.2, 8.4], "volva")
    for k in range(12):
        a = 2 * math.pi * k / 12
        m.box(ring, (math.cos(a) * 5.0, 25.4, math.sin(a) * 4.4), [2.4, 3.2 + (k % 3) * 0.9, 0.5], "volva",
              rot=[(k % 2) * 10 - 5, -math.degrees(a) + 90, 14])
    # hyphae veil down the back, tattered
    veil = m.group("veil", (0, 37, 3.4), body)
    for k, x in enumerate((-4.2, -1.4, 1.4, 4.2)):
        L = 27 - (k % 2) * 6
        m.box(veil, (x * 1.1, 37 - L / 2, 4.2 + abs(x) * 0.25 + (k % 2) * 0.3), [2.8, L, 0.6], "hyphae", rot=[6, 0, (k - 1.5) * 4])
        m.box(veil, (x * 1.25, 37 - L - 1.0, 5.6 + abs(x) * 0.3), [1.1, 3.0, 0.5], "shadow", rot=[8, 0, (k - 1.5) * 6])

    # ---- torso: pallid ribbed chest under a hyphae shawl, a spore core at the sternum
    torso = m.group("torso", (0, 29, 0), body)
    m.box(torso, (0, 30.4, 0), [6.2, 3.8, 4.8], "skin")                         # narrow waist
    m.box(torso, (0, 33.8, 0), [7.8, 3.6, 5.4], "skin")
    m.box(torso, (0, 36.9, 0), [9.2, 2.8, 5.6], "skin")                         # shoulders
    for k, y in enumerate((32.6, 34.0, 35.4)):
        for s in (-1, 1):
            m.box(torso, (s * 2.0, y, -2.75), [2.6 - k * 0.2, 0.5, 0.4], "hyphae", rot=[0, 0, s * -14])   # ribs
    core = m.group("core", (0, 33.2, -2.8), torso)
    m.box(core, (0, 33.2, -2.9), [1.8, 1.8, 0.8], "spore_glow", rot=[0, 0, 45])
    m.locator(core, "core", (0, 33.2, -3.2))
    for s in (-1, 1):
        m.box(torso, (s * 4.8, 37.9, 0.2), [4.2, 1.4, 6.6], "hyphae", rot=[0, 0, s * -24])    # shawl over each shoulder
        m.box(torso, (s * 5.4, 35.8, 0.6), [1.6, 4.4, 6.0], "shadow", rot=[0, 0, s * -8])
    m.box(torso, (0, 38.4, 1.6), [6.4, 1.6, 4.4], "hyphae")
    m.box(torso, (0, 39.6, 0), [2.2, 1.8, 2.2], "skin")                         # neck

    # ---- head: gaunt pallid face in a shadow hood, two cold eye slits
    head = m.group("head", (0, 40.2, 0), torso)
    m.box(head, (0, 42.6, -0.4), [4.0, 4.4, 4.2], "skin")
    m.box(head, (0, 40.8, -1.6), [2.6, 1.2, 1.6], "skin")                      # narrow jaw
    for s in (-1, 1):
        m.box(head, (s * 1.05, 43.1, -2.55), [1.2, 0.45, 0.3], "eye_glow", name=f"eye_{'l' if s < 0 else 'r'}")
        m.box(head, (s * 2.45, 42.8, 0.1), [0.8, 5.6, 5.2], "shadow", rot=[0, 0, s * 5])   # hood sides
        m.box(head, (s * 1.7, 41.6, -2.2), [0.9, 2.4, 0.4], "shadow", rot=[0, 0, s * -10])  # hollow cheeks
    m.box(head, (0, 43.0, 2.4), [5.2, 5.4, 0.9], "shadow")
    m.locator(head, "head", (0, 42.8, -2.6))

    # ---- cap: central dome + eight petals (each a yaw bone with a curl bone at the rim), gills glowing beneath
    cap = m.group("cap", (0, CAP_Y, 0), head)
    m.box(cap, (0, CAP_Y + 0.9, 0), [12.0, 2.4, 12.0], "cap")
    m.box(cap, (0, CAP_Y + 2.7, 0), [9.6, 1.8, 9.6], "cap")
    m.box(cap, (0, CAP_Y + 4.1, 0), [6.6, 1.4, 6.6], "cap")
    m.box(cap, (0, CAP_Y + 5.1, 0), [3.4, 1.0, 3.4], "cap_dark")
    m.box(cap, (0, CAP_Y - 0.5, 0), [4.6, 1.4, 4.6], "gill")                  # the collar where the cap meets the hood
    for (x, z, s) in ((0, 0, 1.6), (2.8, -1.5, 1.1), (-2.5, 2.2, 1.2), (-2.9, -2.4, 1.0), (2.2, 2.9, 0.9)):
        m.box(cap, (x, CAP_Y + 5.0 - 0.3 * (abs(x) + abs(z)), z), [s, s * 0.8, s], "wart", rot=[0, 45, 0])
    gills = m.group("gills", (0, CAP_Y - 0.5, 0), cap)
    m.ring(gills, (0, CAP_Y - 0.4, 0), 4.6, 18, [0.5, 1.2, 5.2], "gill_glow", axis="y")
    m.box(gills, (0, CAP_Y - 0.2, 0), [3.4, 1.0, 3.4], "gill")
    m.locator(gills, "gills", (0, CAP_Y - 1.5, 0))
    m.locator(cap, "cap", (0, CAP_Y + 3, 0))
    for k in range(PETALS):
        yaw = m.group(f"petal_yaw_{k}", (0, CAP_Y, 0), cap, rot=[0, 360 * k / PETALS, 0])
        p = m.group(f"petal_{k}", (0, CAP_Y + 0.6, -5.8), yaw, rot=[-14, 0, 0])   # rest: a gentle dome
        m.box(p, (0, CAP_Y + 0.8, -9.8), [9.6, 2.2, 8.4], "cap")
        m.box(p, (0, CAP_Y + 1.8, -8.8), [7.0, 0.8, 6.0], "cap_dark")
        m.box(p, (0, CAP_Y - 0.5, -9.6), [8.0, 0.5, 7.6], "gill")
        for j in range(3):
            m.box(p, ((j - 1) * 2.6, CAP_Y - 1.0, -9.6), [0.5, 0.8, 7.0], "gill_glow")
        m.box(p, (1.8 * (1 if k % 2 else -1), CAP_Y + 2.2, -10.4), [1.4, 1.1, 1.4], "wart", rot=[0, 30, 0])
        m.box(p, (0, CAP_Y + 0.2, -14.3), [7.0, 1.4, 1.2], "cap_dark")            # the curled rim

    # ---- arms: long pallid arms, forearms wrapped in hyphae, fingers become lash tendrils
    for side, s in (("l", -1), ("r", 1)):
        arm = m.group(f"arm_{side}", (s * 5.4, 37.4, 0), torso)
        m.box(arm, (s * 6.0, 33.2, 0), [1.9, 8.4, 1.9], "skin", rot=[0, 0, s * 5])
        fore = m.group(f"forearm_{side}", (s * 6.5, 29, 0), arm)
        m.box(fore, (s * 6.7, 24.6, 0), [1.8, 8.6, 1.8], "skin")
        m.box(fore, (s * 6.7, 25.6, 0), [2.4, 4.6, 2.4], "hyphae", rot=[0, 20, 0])
        hand = m.group(f"hand_{side}", (s * 6.7, 20.2, 0), fore)
        m.box(hand, (s * 6.7, 19.5, -0.2), [1.7, 1.4, 1.9], "skin")
        for j in range(4):
            a = math.radians(-54 + j * 36)
            L = 7.5 if side == "l" else 5.0
            m.box(hand, (s * 6.7 + math.sin(a) * 0.9, 18.6 - L / 2, -math.cos(a) * 0.5 - 0.4), [0.5, L, 0.5], "hyphae",
                  rot=[-8, 0, math.degrees(a) * 0.3])
        m.box(hand, (s * 6.7, 18.8, -1.2), [0.7, 0.7, 0.7], "gill_glow")
        m.locator(hand, f"hand_{side}", (s * 6.7, 14, -1))

    m.locator(root, "feet", (0, 0, 0))
    pfx(m, "hollow_spore", "gill_glow", "snuff_smoke", "veil_mist")

    # ================================================================ animations
    def petals(a, t, curl, spread=0.0):
        """curl > 0 closes the petals down around her (snuff); curl < 0 flares them up (bloom)."""
        for k in range(PETALS):
            m.key(a, f"petal_{k}", "rotation", t, [-(curl + spread * math.sin(k * 1.7)), 0, 0])

    def sway(a, length, amp=0.5, curl=-8, curl_amp=4, gill_amp=0.12, arms=True, head=True):
        n = 8
        for s in range(n + 1):
            t = length * s / n
            ph = 2 * math.pi * s / n
            m.key(a, "body", "position", t, [0, amp * math.sin(ph), 0])
            m.key(a, "veil", "rotation", t, [4 * math.sin(ph + 0.8), 0, 2 * math.cos(ph)])
            if head:
                m.key(a, "head", "rotation", t, [2 * math.sin(ph + 1.1), 4 * math.sin(ph * 0.5), 0])
            m.key(a, "gills", "scale", t, [1 + gill_amp * math.sin(ph), 1, 1 + gill_amp * math.sin(ph)])
            for k in range(PETALS):
                m.key(a, f"petal_{k}", "rotation", t, [-(curl + curl_amp * math.sin(ph + k * 0.8)), 0, 0])
            if arms:
                m.key(a, "arm_l", "rotation", t, [3 * math.sin(ph), 0, -4 + 2 * math.cos(ph)])
                m.key(a, "arm_r", "rotation", t, [-3 * math.sin(ph), 0, 4 - 2 * math.cos(ph)])

    # ---- loops
    a = m.anim("idle", 3.0)            # dark: the bloom is open, gills breathing
    sway(a, 3.0, curl=-14, curl_amp=5)
    m.fx(a, 0.5, "gill_glow", "gills")
    m.fx(a, 2.0, "hollow_spore", "cap")
    m.sfx(a, 0.0, "idle.breathe")

    a = m.anim("wilt", 1.5)            # exposed: petals droop and tremble, she cringes from the light
    sway(a, 1.5, amp=0.3, curl=34, curl_amp=6, gill_amp=0.04, arms=False, head=False)
    for t in (0, 1.5):
        m.key(a, "torso", "rotation", t, [14, 0, 0])
        m.key(a, "arm_l", "rotation", t, [-40, 0, -30])
        m.key(a, "arm_r", "rotation", t, [-55, 20, 25])
    m.keys(a, "head", "rotation", [(0, [18, -10, 0]), (0.75, [20, 10, 0]), (1.5, [18, -10, 0])])
    m.fx(a, 0.3, "snuff_smoke", "cap")
    m.fx(a, 1.0, "snuff_smoke", "gills")

    a = m.anim("walk", 1.6)            # she glides: the volva drags, the veil trails
    sway(a, 1.6, amp=0.8, curl=-6, curl_amp=6)
    for s in range(5):
        t = 1.6 * s / 4
        m.key(a, "volva", "rotation", t, [3 * math.sin(2 * math.pi * s / 4), 0, 2 * math.cos(2 * math.pi * s / 4)])
        m.key(a, "torso", "rotation", t, [8, 3 * math.sin(2 * math.pi * s / 4), 0])
    m.fx(a, 0.0, "hollow_spore", "feet")
    m.fx(a, 0.8, "hollow_spore", "feet")

    a = m.anim("deep_idle", 2.0)       # deep bloom: petals flung wide, gills blazing, rooted
    sway(a, 2.0, amp=0.2, curl=-38, curl_amp=3, gill_amp=0.3, arms=False)
    for t in (0, 2.0):
        m.key(a, "arm_l", "rotation", t, [-20, 0, -60])
        m.key(a, "arm_r", "rotation", t, [-20, 0, 60])
        m.key(a, "volva", "scale", t, [1.2, 1.1, 1.2])
    m.keys(a, "core", "scale", [(0, 1.2), (1, 1.8), (2, 1.2)])
    for t in (0.0, 0.7, 1.4):
        m.fx(a, t, "gill_glow", "gills")
    m.fx(a, 1.0, "hollow_spore", "cap")

    a = m.anim("dash", 0.5)            # low glide, arms swept back, cap folded like a closing bud
    petals(a, 0, 50)
    petals(a, 0.5, 50)
    for t in (0, 0.5):
        m.key(a, "torso", "rotation", t, [35, 0, 0])
        m.key(a, "arm_l", "rotation", t, [50, 0, -20])
        m.key(a, "arm_r", "rotation", t, [50, 0, 20])
        m.key(a, "veil", "rotation", t, [40, 0, 0])
    m.fx(a, 0.0, "veil_mist", "core")
    m.fx(a, 0.25, "veil_mist", "feet")

    a = m.anim("stagger", 1.0)
    for s in range(5):
        t = s / 4
        m.key(a, "torso", "rotation", t, [-14 + 5 * math.sin(s * 2.1), 0, 7 * math.cos(s * 1.9)])
        m.key(a, "head", "rotation", t, [-16, 10 * math.sin(s * 1.6), 0])
    petals(a, 0, 40, 10)
    petals(a, 1.0, 40, 10)
    m.keys(a, "arm_l", "rotation", [(0, [-70, 0, -40]), (1, [-70, 0, -40])])
    m.keys(a, "arm_r", "rotation", [(0, [-70, 0, 40]), (1, [-70, 0, 40])])
    m.fx(a, 0.0, "snuff_smoke", "cap")

    # ---- one-shots
    a = m.anim("rise", 6.0, "once")    # P0: she grows out of the loam, the cap still shut
    m.keys(a, "amanita", "position", [(0, [0, -40, 0]), (1.0, [0, -40, 0]), (4.0, [0, 0, 0])])
    m.keys(a, "amanita", "scale", [(0, [0.6, 0.6, 0.6]), (1.0, [0.6, 0.6, 0.6]), (4.0, [1, 1, 1])])
    petals(a, 0, 80)
    petals(a, 4.6, 80)
    petals(a, 6.0, -10)
    m.keys(a, "torso", "rotation", [(0, [45, 0, 0]), (3.5, [45, 0, 0]), (5.0, [-6, 0, 0]), (6.0, [0, 0, 0])])
    for t in (0.2, 1.0, 1.8, 2.6, 3.4):
        m.fx(a, t, "hollow_spore", "feet")
    m.fx(a, 5.0, "gill_glow", "gills")
    m.sfx(a, 0.0, "rise")

    a = m.anim("bloom_open", 2.0, "once")   # P0 teaching beat: the petals slam open, a spore burst
    petals(a, 0, 20)
    petals(a, 1.6, 50)
    petals(a, 1.9, -40, 8)
    petals(a, 2.0, -30)
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (1.6, [-40, 0, -20]), (1.9, [-30, 0, -80]), (2.0, [-20, 0, -70])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (1.6, [-40, 0, 20]), (1.9, [-30, 0, 80]), (2.0, [-20, 0, 70])])
    m.keys(a, "gills", "scale", [(0, 1), (1.9, 1.6), (2.0, 1.3)])
    m.fx(a, 1.92, "hollow_spore", "cap")
    m.fx(a, 1.95, "gill_glow", "gills")
    m.sfx(a, 0.0, "bloom.windup")

    a = m.anim("lash_windup", 0.7, "once")  # the long tendril hand draws back across her body
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (0.6, [-80, 50, -35]), (0.7, [-82, 55, -36])])
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (0.6, [0, 28, 0])])
    m.fx(a, 0.3, "gill_glow", "hand_l")
    m.sfx(a, 0.0, "lash.windup")

    a = m.anim("lash", 0.6, "once")
    m.keys(a, "arm_l", "rotation", [(0, [-82, 55, -36]), (0.12, [-85, -70, -20]), (0.6, [0, 0, -4])])
    m.keys(a, "torso", "rotation", [(0, [0, 28, 0]), (0.12, [0, -30, 0]), (0.6, [0, 0, 0])])
    m.fx(a, 0.05, "veil_mist", "hand_l")

    a = m.anim("bolt_cast", 1.0, "once")
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (0.85, [-90, -8, 0]), (1.0, [-96, -8, 0])])
    m.keys(a, "forearm_r", "rotation", [(0, [0, 0, 0]), (0.85, [-10, 0, 0]), (1.0, [6, 0, 0])])
    m.fx(a, 0.4, "gill_glow", "hand_r")
    m.fx(a, 0.95, "veil_mist", "hand_r")
    m.sfx(a, 0.0, "bolt.windup")

    a = m.anim("veil_cast", 1.5, "once")    # both hands up, spores shaken from the cap
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (1.3, [-150, 0, -25])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (1.3, [-150, 0, 25])])
    for s in range(7):
        m.key(a, "cap", "rotation", s * 0.2, [0, 0, 8 * math.sin(s * 2.4)])
    for t in (0.3, 0.8, 1.3):
        m.fx(a, t, "hollow_spore", "cap")
    m.sfx(a, 0.0, "veil.windup")

    a = m.anim("snuff_windup", 2.0, "once")  # the flower begins to close: petals fold, arms gather in
    petals(a, 0, -10)
    petals(a, 1.8, 60)
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (1.8, [-60, 0, 30])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (1.8, [-60, 0, -30])])
    m.keys(a, "gills", "scale", [(0, 1), (1.8, 1.6)])
    for t in (0.4, 1.0, 1.6):
        m.fx(a, t, "gill_glow", "gills")
    m.sfx(a, 0.0, "snuff.gather")

    a = m.anim("snuff", 1.2, "once")
    petals(a, 0, 60)
    petals(a, 0.1, 85)
    petals(a, 1.2, 0)
    m.keys(a, "arm_l", "rotation", [(0, [-60, 0, 30]), (0.1, [10, 0, -60]), (1.2, [0, 0, 0])])
    m.keys(a, "arm_r", "rotation", [(0, [-60, 0, -30]), (0.1, [10, 0, 60]), (1.2, [0, 0, 0])])
    m.keys(a, "body", "position", [(0, [0, 0, 0]), (0.1, [0, -2, 0]), (1.2, [0, 0, 0])])
    m.fx(a, 0.05, "veil_mist", "core")
    m.fx(a, 0.1, "snuff_smoke", "cap")

    a = m.anim("full_snuff_windup", 3.0, "once")   # closing the flower: the cap folds shut around her entirely
    petals(a, 0, -10)
    petals(a, 2.6, 88)
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (2.6, [30, 0, 0])])
    m.keys(a, "head", "rotation", [(0, [0, 0, 0]), (2.6, [25, 0, 0])])
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (2.6, [-95, 0, 35])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (2.6, [-95, 0, -35])])
    m.keys(a, "volva", "scale", [(0, 1), (2.6, [1.15, 1.4, 1.15])])
    for t in (0.5, 1.2, 1.9, 2.6):
        m.fx(a, t, "veil_mist", "core")
    m.sfx(a, 0.0, "full.windup")

    a = m.anim("full_snuff", 1.5, "once")
    petals(a, 0, 88)
    petals(a, 0.15, -45, 10)
    petals(a, 1.5, -12)
    m.keys(a, "torso", "rotation", [(0, [30, 0, 0]), (0.15, [-20, 0, 0]), (1.5, [0, 0, 0])])
    m.keys(a, "arm_l", "rotation", [(0, [-95, 0, 35]), (0.15, [-30, 0, -90]), (1.5, [0, 0, 0])])
    m.keys(a, "arm_r", "rotation", [(0, [-95, 0, -35]), (0.15, [-30, 0, 90]), (1.5, [0, 0, 0])])
    m.keys(a, "volva", "scale", [(0, [1.15, 1.4, 1.15]), (0.15, [1.3, 0.8, 1.3]), (1.5, 1)])
    m.fx(a, 0.1, "veil_mist", "feet")
    m.fx(a, 0.15, "snuff_smoke", "cap")

    a = m.anim("howl_windup", 1.5, "once")  # she inhales the hollow: head back, cap raised
    m.keys(a, "head", "rotation", [(0, [0, 0, 0]), (1.3, [-35, 0, 0])])
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (1.3, [-12, 0, 0])])
    petals(a, 0, -8)
    petals(a, 1.3, -30)
    m.keys(a, "core", "scale", [(0, 1), (1.3, 2.0)])
    m.fx(a, 0.6, "veil_mist", "head")
    m.sfx(a, 0.0, "howl.inhale")

    a = m.anim("howl", 1.0, "once")
    m.keys(a, "head", "rotation", [(0, [-35, 0, 0]), (0.1, [20, 0, 0]), (1, [0, 0, 0])])
    m.keys(a, "torso", "rotation", [(0, [-12, 0, 0]), (0.1, [18, 0, 0]), (1, [0, 0, 0])])
    m.keys(a, "core", "scale", [(0, 2.0), (0.2, 1), (1, 1)])
    m.fx(a, 0.05, "veil_mist", "head")

    a = m.anim("slam_windup", 1.5, "once")  # rises on the volva, both arms overhead
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (1.3, [-170, 0, -15])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (1.3, [-170, 0, 15])])
    m.keys(a, "body", "position", [(0, [0, 0, 0]), (1.3, [0, 4, 0])])
    petals(a, 0, -10)
    petals(a, 1.3, -35)
    m.fx(a, 0.5, "hollow_spore", "feet")
    m.fx(a, 1.0, "hollow_spore", "feet")
    m.sfx(a, 0.0, "slam.windup")

    a = m.anim("slam", 1.0, "once")
    m.keys(a, "arm_l", "rotation", [(0, [-170, 0, -15]), (0.1, [-20, 0, -30]), (1, [0, 0, 0])])
    m.keys(a, "arm_r", "rotation", [(0, [-170, 0, 15]), (0.1, [-20, 0, 30]), (1, [0, 0, 0])])
    m.keys(a, "body", "position", [(0, [0, 4, 0]), (0.1, [0, -5, 0]), (1, [0, 0, 0])])
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (0.1, [32, 0, 0]), (1, [0, 0, 0])])
    petals(a, 0, -35)
    petals(a, 0.1, 30)
    petals(a, 1.0, -8)
    m.fx(a, 0.1, "hollow_spore", "feet")
    m.fx(a, 0.12, "veil_mist", "feet")

    a = m.anim("dash_windup", 1.0, "once")  # crouch toward the light, cap folding like a hand closing
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (0.9, [30, 0, 0])])
    petals(a, 0, -8)
    petals(a, 0.9, 45)
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (0.9, [40, 0, -25])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (0.9, [40, 0, 25])])
    m.fx(a, 0.5, "veil_mist", "feet")
    m.sfx(a, 0.0, "dash.windup")

    a = m.anim("smother", 0.6, "once")      # arrives and closes her cap over the light
    petals(a, 0, 45)
    petals(a, 0.3, 85)
    petals(a, 0.6, 60)
    m.keys(a, "torso", "rotation", [(0, [30, 0, 0]), (0.3, [50, 0, 0]), (0.6, [40, 0, 0])])
    m.keys(a, "arm_l", "rotation", [(0, [40, 0, -25]), (0.3, [-100, 0, 20]), (0.6, [-100, 0, 20])])
    m.keys(a, "arm_r", "rotation", [(0, [40, 0, 25]), (0.3, [-100, 0, -20]), (0.6, [-100, 0, -20])])
    m.fx(a, 0.3, "snuff_smoke", "hand_r")

    a = m.anim("deep_bloom", 2.0, "once")   # P4: the petals are flung wide, she roots, the gills blaze
    petals(a, 0, 10)
    petals(a, 1.6, -45, 6)
    petals(a, 2.0, -38)
    m.keys(a, "volva", "scale", [(0, 1), (1.6, [1.3, 1.1, 1.3]), (2.0, [1.2, 1.1, 1.2])])
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (1.6, [-20, 0, -70]), (2.0, [-20, 0, -60])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (1.6, [-20, 0, 70]), (2.0, [-20, 0, 60])])
    m.keys(a, "gills", "scale", [(0, 1), (1.6, 1.8), (2.0, 1.4)])
    m.fx(a, 1.6, "gill_glow", "gills")
    m.fx(a, 1.65, "hollow_spore", "cap")
    m.sfx(a, 0.0, "deep.windup")

    a = m.anim("hurt", 0.4, "once")
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (0.06, [-7, 0, 5]), (0.4, [0, 0, 0])])
    m.keys(a, "cap", "position", [(0, [0, 0, 0]), (0.06, [0, 0.8, 0]), (0.4, [0, 0, 0])])
    m.fx(a, 0.0, "hollow_spore", "core")
    m.sfx(a, 0.0, "hurt")

    a = m.anim("recoil", 1.0, "once")       # sudden light: she flinches away, petals snapping shut
    petals(a, 0, 0)
    petals(a, 0.1, 70, 8)
    petals(a, 1.0, 30)
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (0.1, [-25, 0, 8]), (1.0, [10, 0, 0])])
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (0.1, [-120, 0, -30]), (1, [-40, 0, -20])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (0.1, [-120, 0, 30]), (1, [-40, 0, 20])])
    m.fx(a, 0.05, "snuff_smoke", "cap")

    a = m.anim("bared", 3.0, "once")        # forced open: petals torn back, she sags
    petals(a, 0, -38)
    petals(a, 0.2, -70, 14)
    petals(a, 3.0, 20)
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (0.2, [-20, 0, 0]), (1.5, [25, 0, 0]), (3, [10, 0, 0])])
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (0.2, [-40, 0, -80]), (3, [0, 0, -10])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (0.2, [-40, 0, 80]), (3, [0, 0, 10])])
    m.fx(a, 0.1, "gill_glow", "gills")
    m.fx(a, 0.3, "snuff_smoke", "cap")

    a = m.anim("death", 7.0, "hold")        # the bloom wilts: petals fall shut, she folds into the volva, spores
    petals(a, 0, 0)
    petals(a, 2.0, 70, 10)
    petals(a, 4.0, 95, 14)
    petals(a, 7.0, 95, 14)
    m.keys(a, "torso", "rotation", [(0, [0, 0, 0]), (1.5, [20, 0, 0]), (4.0, [45, 0, 10]), (7, [60, 0, 12])])
    m.keys(a, "head", "rotation", [(0, [0, 0, 0]), (2.0, [30, 0, 0]), (7, [40, 0, 10])])
    m.keys(a, "arm_l", "rotation", [(0, [0, 0, 0]), (2.0, [-20, 0, -30]), (5, [20, 0, -10]), (7, [20, 0, -10])])
    m.keys(a, "arm_r", "rotation", [(0, [0, 0, 0]), (2.0, [-20, 0, 30]), (5, [20, 0, 10]), (7, [20, 0, 10])])
    m.keys(a, "body", "scale", [(0, 1), (3.5, 1), (6.5, [1.05, 0.35, 1.05]), (7, [1.05, 0.3, 1.05])])
    m.keys(a, "gills", "scale", [(0, 1), (3.0, 1.6), (3.6, 0.01), (7, 0.01)])
    m.keys(a, "core", "scale", [(0, 1), (3.0, 2), (3.6, 0.01), (7, 0.01)])
    for t in (0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0):
        m.fx(a, t, "hollow_spore", "cap")
    m.fx(a, 3.6, "gill_glow", "gills")
    m.fx(a, 4.5, "veil_mist", "feet")
    return m


# ============================================================ lamp-eater
def lamp_eater():
    m = Model("lamp_eater", seed=77)
    root = m.group("lamp_eater", (0, 0, 0))
    segs = []
    parent = root
    for i in range(5):
        z = -4 + i * 3.6
        w = [9, 10.4, 10.8, 9.6, 7.4][i]
        h = [6.6, 7.6, 7.8, 7.0, 5.4][i]
        g = m.group(f"seg_{i}", (0, 3.6, z - 1.8), parent)
        m.box(g, (0, h / 2 + 0.2, z), [w, h, 3.8], "moth")
        m.box(g, (0, 0.9, z), [w * 0.72, 1.6, 3.2], "ember")                     # the belly glows with what it ate
        m.box(g, (0, h + 0.1, z), [w * 0.6, 0.8, 2.6], "wart")                   # fuzzy dorsal ridge
        for s in (-1, 1):
            m.box(g, (s * (w / 2 + 0.2), 1.0, z), [1.4, 1.6, 0.9], "husk", rot=[0, 0, s * 25])   # stubby legs
        segs.append(g)
        parent = g
    head = m.group("head", (0, 4, -6), root)
    m.box(head, (0, 4.2, -8.4), [7.6, 6.2, 5.0], "moth")
    m.box(head, (0, 7.4, -8.2), [6.4, 1.0, 4.2], "wart")
    for s in (-1, 1):
        m.box(head, (s * 2.4, 5.8, -10.95), [1.6, 1.2, 0.4], "eye_glow")
        jaw = m.group(f"jaw_{'l' if s < 0 else 'r'}", (s * 2.2, 2.4, -10.4), head)
        m.box(jaw, (s * 2.4, 2.2, -12.4), [1.4, 1.6, 4.2], "husk", rot=[0, s * -18, 0])
        m.box(jaw, (s * 1.4, 2.0, -14.1), [1.6, 1.2, 1.0], "husk", rot=[0, s * -40, 0])
        ant = m.group(f"antenna_{'l' if s < 0 else 'r'}", (s * 1.6, 7.6, -9.4), head)
        m.box(ant, (s * 2.6, 10.4, -11.6), [0.5, 5.4, 0.5], "moth", rot=[-35, 0, s * 25])
        for j in range(3):
            m.box(ant, (s * (2.3 + j * 0.5), 9.2 + j * 1.3, -10.8 - j * 0.9), [2.2, 0.3, 0.8], "wart", rot=[-35, 0, s * 25])
    m.box(head, (0, 3.2, -11.6), [3.2, 2.4, 1.2], "ember")                        # the lantern mouth
    m.locator(head, "mouth", (0, 3.4, -12.5))
    for s in (-1, 1):
        w = m.group(f"wing_{'l' if s < 0 else 'r'}", (s * 3.4, 8.2, -1), segs[1])
        m.box(w, (s * 5.2, 8.8, 2.6), [5.2, 0.5, 8.6], "moth", rot=[0, s * 10, s * -20])
        m.box(w, (s * 6.0, 8.95, 3.2), [2.6, 0.3, 4.2], "wart", rot=[0, s * 10, s * -20])
    m.locator(root, "feet", (0, 0, 0))
    pfx(m, "ember", "hollow_spore", "snuff_smoke")

    a = m.anim("idle", 2.0)
    for s in range(5):
        t = 2.0 * s / 4
        ph = 2 * math.pi * s / 4
        m.key(a, "head", "rotation", t, [3 * math.sin(ph), 6 * math.sin(ph * 0.5), 0])
        m.key(a, "antenna_l", "rotation", t, [8 * math.sin(ph), 0, 0])
        m.key(a, "antenna_r", "rotation", t, [8 * math.sin(ph + 1), 0, 0])
        m.key(a, "wing_l", "rotation", t, [0, 0, 4 * math.sin(ph)])
        m.key(a, "wing_r", "rotation", t, [0, 0, -4 * math.sin(ph)])
    m.wave(a, [f"seg_{i}" for i in range(5)], "rotation", 2.0, 2.0, 2.0, axis=(0, 1, 0))

    a = m.anim("walk", 0.8)
    m.wave(a, [f"seg_{i}" for i in range(5)], "rotation", 7.0, 0.8, 0.8, phase_step=0.2, axis=(0, 1, 0))
    m.wave(a, [f"seg_{i}" for i in range(5)], "position", 0.5, 0.8, 0.8, phase_step=0.25, axis=(0, 1, 0))
    m.keys(a, "head", "rotation", [(0, [0, -6, 0]), (0.4, [0, 6, 0]), (0.8, [0, -6, 0])])
    m.fx(a, 0.0, "hollow_spore", "feet")

    a = m.anim("chew_loop", 0.5)
    m.keys(a, "jaw_l", "rotation", [(0, [0, 0, 0]), (0.25, [0, 28, 0]), (0.5, [0, 0, 0])])
    m.keys(a, "jaw_r", "rotation", [(0, [0, 0, 0]), (0.25, [0, -28, 0]), (0.5, [0, 0, 0])])
    m.keys(a, "head", "rotation", [(0, [-20, 0, 0]), (0.25, [-26, 0, 0]), (0.5, [-20, 0, 0])])
    m.keys(a, "seg_0", "scale", [(0, 1), (0.25, [1.08, 1.08, 1]), (0.5, 1)])
    m.fx(a, 0.25, "ember", "mouth")

    a = m.anim("chew", 0.4, "once")
    m.keys(a, "head", "rotation", [(0, [0, 0, 0]), (0.4, [-20, 0, 0])])
    m.sfx(a, 0.0, "eater.chew")

    a = m.anim("nip", 0.4, "once")
    m.keys(a, "head", "rotation", [(0, [0, 0, 0]), (0.1, [15, 0, 0]), (0.4, [0, 0, 0])])
    m.keys(a, "jaw_l", "rotation", [(0, [0, 30, 0]), (0.1, [0, -5, 0]), (0.4, [0, 0, 0])])
    m.keys(a, "jaw_r", "rotation", [(0, [0, -30, 0]), (0.1, [0, 5, 0]), (0.4, [0, 0, 0])])

    a = m.anim("burrow", 1.0, "once")
    m.keys(a, "lamp_eater", "position", [(0, [0, 0, 0]), (1.0, [0, -10, 0])])
    m.keys(a, "lamp_eater", "rotation", [(0, [0, 0, 0]), (1.0, [30, 0, 0])])
    m.fx(a, 0.0, "hollow_spore", "feet")
    m.fx(a, 0.5, "hollow_spore", "feet")

    a = m.anim("rise", 1.0, "once")
    m.keys(a, "lamp_eater", "position", [(0, [0, -10, 0]), (1.0, [0, 0, 0])])
    m.keys(a, "lamp_eater", "rotation", [(0, [-30, 0, 0]), (1.0, [0, 0, 0])])
    m.fx(a, 0.2, "hollow_spore", "feet")
    return m


# ============================================================ hollow-spawn
def hollow_spawn():
    m = Model("hollow_spawn", seed=91)
    root = m.group("hollow_spawn", (0, 0, 0))
    for side, s in (("l", -1), ("r", 1)):
        leg = m.group(f"leg_{side}", (s * 2, 12, 0), root)
        m.box(leg, (s * 2, 6, 0), [3.6, 12, 3.6], "husk")
        m.box(leg, (s * 2.2, 3, -0.3), [4.0, 2.4, 4.0], "hyphae", rot=[0, 15, 0])
        m.box(leg, (s * 2, 0.6, -0.6), [3.8, 1.2, 4.6], "loam")
    body = m.group("body", (0, 12, 0), root)
    m.box(body, (0, 18, 0), [8, 12, 4.4], "husk")
    m.box(body, (0, 21.5, -2.3), [6.2, 4.6, 0.4], "hyphae")
    m.box(body, (1.6, 16.5, -2.35), [2.2, 2.2, 0.5], "spore_glow", rot=[0, 0, 30])     # a spore wound in the chest
    for (x, y, z, sz) in ((3.8, 23.4, 1.0, 2.2), (-3.4, 20.2, 2.4, 1.6), (0.8, 13.6, 2.4, 1.8)):
        m.box(body, (x, y, z), [sz, sz * 0.7, sz], "cap", rot=[0, 30, 10])             # shelf fungi growing out of it
        m.box(body, (x, y - sz * 0.35, z), [sz * 0.8, 0.3, sz * 0.8], "gill_glow", rot=[0, 30, 10])
    for side, s in (("l", -1), ("r", 1)):
        arm = m.group(f"arm_{side}", (s * 5, 23, 0), body)
        m.box(arm, (s * 5.6, 17.6, 0), [3.0, 11, 3.0], "husk")
        m.box(arm, (s * 5.6, 11.6, -0.3), [2.4, 2.6, 2.4], "hyphae")
        for j in range(3):
            m.box(arm, (s * 5.6 + (j - 1) * 0.8, 9.4, -0.6), [0.5, 2.6, 0.5], "hyphae")
    head = m.group("head", (0, 24, 0), body)
    m.box(head, (0, 27.4, 0), [6.4, 6.6, 6.2], "husk")
    for s in (-1, 1):
        m.box(head, (s * 1.5, 28, -3.15), [1.2, 1.0, 0.3], "eye_glow")
    m.box(head, (0, 25.6, -3.1), [3.2, 1.4, 0.3], "shadow")
    hc = m.group("head_cap", (0, 30.6, 0), head)
    m.box(hc, (0, 31.6, 0), [10.6, 2.0, 10.6], "cap")
    m.box(hc, (0, 33.0, 0), [7.4, 1.4, 7.4], "cap")
    m.box(hc, (0, 30.4, 0), [9.4, 0.4, 9.4], "gill_glow")
    for (x, z) in ((2.2, -1.8), (-2.6, 1.0), (0.6, 2.8)):
        m.box(hc, (x, 34.0, z), [1.2, 0.9, 1.2], "wart", rot=[0, 45, 0])
    m.locator(root, "feet", (0, 0, 0))
    m.locator(head, "head", (0, 28, -3))
    pfx(m, "hollow_spore", "gill_glow")

    a = m.anim("idle", 2.4)
    for s in range(5):
        t = 2.4 * s / 4
        ph = 2 * math.pi * s / 4
        m.key(a, "head", "rotation", t, [4 * math.sin(ph), 8 * math.sin(ph * 0.5), 3 * math.cos(ph)])
        m.key(a, "arm_l", "rotation", t, [4 * math.sin(ph), 0, -3])
        m.key(a, "arm_r", "rotation", t, [-4 * math.sin(ph), 0, 3])
        m.key(a, "body", "rotation", t, [6 + 2 * math.sin(ph), 0, 0])

    a = m.anim("walk", 1.0)
    for s in range(5):
        t = s / 4
        ph = 2 * math.pi * s / 4
        m.key(a, "leg_l", "rotation", t, [30 * math.sin(ph), 0, 0])
        m.key(a, "leg_r", "rotation", t, [-30 * math.sin(ph), 0, 0])
        m.key(a, "arm_l", "rotation", t, [-70 - 10 * math.sin(ph), 0, -4])     # arms out ahead, shambling
        m.key(a, "arm_r", "rotation", t, [-70 + 10 * math.sin(ph), 0, 4])
        m.key(a, "body", "rotation", t, [10, 4 * math.sin(ph), 0])
    m.fx(a, 0.0, "hollow_spore", "feet")

    a = m.anim("attack", 0.5, "once")
    m.keys(a, "arm_l", "rotation", [(0, [-70, 0, 0]), (0.15, [-130, 0, 10]), (0.3, [-40, 0, 0]), (0.5, [-70, 0, 0])])
    m.keys(a, "arm_r", "rotation", [(0, [-70, 0, 0]), (0.15, [-130, 0, -10]), (0.3, [-40, 0, 0]), (0.5, [-70, 0, 0])])
    m.keys(a, "body", "rotation", [(0, [10, 0, 0]), (0.3, [25, 0, 0]), (0.5, [10, 0, 0])])
    m.sfx(a, 0.0, "spawn.swing")

    a = m.anim("rise", 1.0, "once")
    m.keys(a, "hollow_spawn", "position", [(0, [0, -30, 0]), (1.0, [0, 0, 0])])
    m.keys(a, "body", "rotation", [(0, [50, 0, 0]), (1.0, [6, 0, 0])])
    m.fx(a, 0.1, "hollow_spore", "feet")
    m.fx(a, 0.6, "hollow_spore", "feet")

    a = m.anim("dissolve", 1.0, "once")
    m.keys(a, "hollow_spawn", "position", [(0, [0, 0, 0]), (1.0, [0, -30, 0])])
    m.keys(a, "hollow_spawn", "scale", [(0, 1), (1.0, [1.2, 0.6, 1.2])])
    m.fx(a, 0.0, "hollow_spore", "head")
    return m


# ============================================================ hollow_bolt
def hollow_bolt():
    m = Model("hollow_bolt", seed=21)
    root = m.group("bolt", (0, 2, 0))
    m.box(root, (0, 2, 0), [2.0, 2.0, 7.0], "shadow", rot=[0, 0, 45])
    m.box(root, (0, 2, -3.8), [1.2, 1.2, 1.8], "cap_dark", rot=[0, 0, 45])
    m.box(root, (0, 2, 0), [0.9, 0.9, 5.4], "gill_glow")
    for k in range(4):
        a = k * math.pi / 2
        m.box(root, (math.cos(a) * 1.1, 2 + math.sin(a) * 1.1, 2.8), [0.5, 0.5, 2.6], "spore_glow")
    m.locator(root, "trail", (0, 2, 3.5))
    pfx(m, "veil_mist", "gill_glow")
    a = m.anim("fly", 0.5)
    m.keys(a, "bolt", "rotation", [(0, [0, 0, 0]), (0.5, [0, 0, 360])], "linear")
    return m


ALL = [amanita, lamp_eater, hollow_spawn, hollow_bolt]
