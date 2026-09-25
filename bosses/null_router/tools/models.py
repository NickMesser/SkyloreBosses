"""Null Router model specs: the chassis (an orphaned Automaton router rack: a server-rack core with a white clean-room
front panel, a cyan status lens in a ring of amber LEDs, a three-glyph ticket screen, two side port pods that eject
retry packets, cable bundles, rear cooling fins, an antenna crown, four orbiting drive sleds, a hover skirt and four
docking clamps) and the retry packet add. Everything faces north (-Z) like the other boss models; the entity's right
side is +X. Rest pose is the docked (solid) pose; the ghost idle folds the clamps and retracts the pods."""
import boss_env  # noqa: F401  (shared pipeline + this boss)
import math
from lib import Model, particle

# Automaton particle set (Snowstorm previews; in game these are skylore_bosses:<name> particle types).
PX = {
    "packet_spark": particle("packet_spark", burst=16, duration=0.1, lifetime=0.6, speed=3, radius=0.3, gravity=2, drag=2,
                             size=(0.1, 0.1), size_end=(0.02, 0.02), color=(1, 0.72, 0.2, 1), color_end=(1, 0.4, 0.05, 0),
                             uv=(8, 88), blend="add", facing="lookat_direction"),
    "coolant_mist": particle("coolant_mist", rate=12, duration=1, lifetime=1.7, speed=0.5, direction=[0, 1, 0], radius=0.8,
                             gravity=-0.4, drag=1.0, size=(0.25, 0.25), size_end=(0.6, 0.6), color=(0.75, 0.92, 1, 0.8),
                             color_end=(0.45, 0.7, 1, 0), uv=(0, 0), frames=8),
    "ack_glint": particle("ack_glint", burst=14, duration=0.1, lifetime=0.7, speed=2, radius=0.4, drag=2.5,
                          size=(0.14, 0.14), size_end=(0.02, 0.02), color=(0.55, 1, 0.6, 1), color_end=(0.2, 0.85, 0.4, 0),
                          uv=(0, 88), blend="add"),
    "null_beam": particle("null_beam", rate=40, duration=1, lifetime=0.25, speed=0.2, radius=0.2,
                          size=(0.4, 0.4), size_end=(0.05, 0.05), color=(0.85, 1, 1, 1), color_end=(0.25, 0.85, 1, 0),
                          uv=(0, 88), blend="add"),
    "spark": particle("spark", burst=20, duration=0.1, lifetime=0.5, speed=4, radius=0.3, gravity=8, drag=1.5,
                      size=(0.08, 0.08), size_end=(0.02, 0.02), color=(1, 0.85, 0.4, 1), color_end=(1, 0.35, 0.08, 0),
                      uv=(8, 88), blend="add", facing="lookat_direction"),
    "smoke": particle("smoke", rate=10, duration=1, lifetime=1.8, speed=0.6, direction=[0, 1, 0], radius=0.5, gravity=-0.6,
                      drag=0.8, size=(0.3, 0.3), size_end=(0.8, 0.8), color=(0.32, 0.32, 0.34, 0.9), color_end=(0.18, 0.18, 0.19, 0),
                      uv=(0, 0), frames=8),
}


def pfx(m, *names):
    for n in names:
        m.particles[n] = PX[n]


CLAMPS = (("clamp_fl", -1, -1), ("clamp_fr", 1, -1), ("clamp_bl", -1, 1), ("clamp_br", 1, 1))


# ============================================================ Null Router (chassis)
def null_router():
    m = Model("null_router", seed=4040)
    root = m.group("null_router", (0, 0, 0))
    body = m.group("body", (0, 24, 0), root)

    # ---- the rack: dark steel core, rounded by chamfer strips, white clean-room front panel
    m.box(body, (0, 24, 0), [18, 26, 14], "chassis")
    for s in (-1, 1):
        m.box(body, (s * 8.6, 24, -6.6), [1.4, 26.4, 1.4], "chassis_dark", rot=[0, 45, 0])
        m.box(body, (s * 8.6, 24, 6.6), [1.4, 26.4, 1.4], "chassis_dark", rot=[0, 45, 0])
    m.box(body, (0, 24.5, -7.25), [14, 22, 0.8], "panel")
    m.box(body, (0, 12.2, -7.4), [15, 1.2, 0.8], "hazard")
    m.box(body, (0, 36.6, -7.4), [15, 1.2, 0.8], "chassis_dark")
    # drive bays down both flanks: slots with blinking status LEDs
    for s in (-1, 1):
        for k in range(5):
            y = 15 + k * 4.4
            m.box(body, (s * 9.1, y, 0), [0.6, 2.6, 10.5], "chassis_dark")
            m.box(body, (s * 9.45, y, -4.2), [0.3, 0.9, 0.9], "led_green" if k % 2 else "led_amber")
        # AE2-style fluix window behind the pods
        m.box(body, (s * 9.2, 33.6, 2.5), [0.4, 3.2, 4.5], "fluix")

    # ---- status lens: an octagonal cyan eye in a ring of twelve amber LEDs (the "is it listening" read)
    lens = m.group("lens", (0, 28.5, -8.0), body)
    m.box(lens, (0, 28.5, -7.9), [7.4, 7.4, 0.9], "chassis_dark")
    m.box(lens, (0, 28.5, -8.3), [5.4, 5.4, 0.8], "lens")
    m.box(lens, (0, 28.5, -8.3), [5.4, 5.4, 0.8], "lens", rot=[0, 0, 45])
    m.box(lens, (0, 28.5, -8.8), [2.0, 2.0, 0.4], "panel", rot=[0, 0, 45])
    ring = m.group("status_ring", (0, 28.5, -8.0), lens)
    m.ring(ring, (0, 28.5, -8.1), 5.2, 12, [1.0, 1.0, 0.8], "led_amber", axis="z")
    m.locator(lens, "lens", (0, 28.5, -9.0))
    # ticket screen under the lens: the current request as three coloured glyph blocks (cyan, amber, violet)
    m.box(body, (0, 18.6, -7.75), [11, 4.4, 0.4], "screen")
    for k, mat in enumerate(("lens", "led_amber", "fluix")):
        m.box(body, (-3.4 + k * 3.4, 18.6, -8.05), [1.8, 1.8, 0.3], mat)
    m.box(body, (0, 15.2, -7.75), [9, 0.8, 0.4], "led_green")

    # ---- side port pods: they slide out when docked and eject retry packets from their front ports
    for s, name, port in ((-1, "pod_l", "port_l"), (1, "pod_r", "port_r")):
        pod = m.group(name, (s * 9, 28, 0), body)
        m.box(pod, (s * 12.5, 27, 0), [6, 11, 10], "chassis")
        m.box(pod, (s * 12.5, 32.9, 0), [6.4, 0.8, 10.4], "hazard")
        m.box(pod, (s * 15.6, 27, 0), [0.4, 8, 7], "fins")
        m.box(pod, (s * 12.5, 26, -5.2), [4, 4, 0.6], "chassis_dark")
        m.box(pod, (s * 12.5, 26, -5.35), [2.6, 2.6, 0.4], "packet")
        for k in range(3):
            m.box(pod, (s * 12.5 + (k - 1) * 1.4, 22.2, 5.1), [0.8, 1.2, 0.4], "copper")
        m.locator(pod, port, (s * 12.5, 26, -5.8))
        # cable bundle hanging from under the pod, swaying
        parent = pod
        for k in range(3):
            b = m.group(f"cable_{'l' if s < 0 else 'r'}_{k}", (s * 12.5, 21.5 - k * 4, 1.5), parent)
            m.box(b, (s * 12.5, 19.5 - k * 4, 1.5), [2.2, 4.2, 2.2], "cable")
            m.box(b, (s * 12.5 - s * 0.9, 19.5 - k * 4, 0.6), [0.5, 4.2, 0.5], "cable_glow")
            parent = b
        m.box(parent, (s * 12.5, 9.2, 1.5), [2.8, 1.4, 2.8], "copper")

    # ---- cooling fins across the back
    fins = m.group("fins", (0, 24, 7), body)
    for k in range(7):
        x = -6 + k * 2
        m.box(fins, (x, 24, 9), [0.7, 20, 4], "fins")
    m.box(fins, (0, 24, 7.4), [14, 22, 0.6], "chassis_dark")

    # ---- crown: the ticket display and two antennas (one red, one amber tip)
    crown = m.group("crown", (0, 37, 0), body)
    m.box(crown, (0, 38.5, 0.5), [16, 3, 12], "chassis_dark")
    m.box(crown, (0, 43, -3.2), [12, 5.6, 1.0], "chassis", rot=[-15, 0, 0])
    m.box(crown, (0, 43.1, -3.8), [10.4, 4.2, 0.4], "screen", rot=[-15, 0, 0])
    for k in range(3):
        m.box(crown, (-2.5 + k * 1.2, 44.6 - k * 1.3, -4.25), [4.5 - k, 0.5, 0.3], "led_green", rot=[-15, 0, 0])
    m.locator(crown, "crown", (0, 44, -4.5))
    for s, name, h, tip in ((-1, "antenna_l", 12, "led_red"), (1, "antenna_r", 9, "led_amber")):
        ant = m.group(name, (s * 5.5, 40, 3.5), crown)
        m.box(ant, (s * 5.5, 40 + h / 2, 3.5), [1.0, h, 1.0], "chassis")
        m.box(ant, (s * 5.5, 41 + h * 0.6, 3.5), [2.4, 0.5, 2.4], "chassis_dark")
        m.box(ant, (s * 5.5, 40 + h + 0.8, 3.5), [1.8, 1.8, 1.8], tip, rot=[45, 45, 0])
        if s < 0:
            m.locator(ant, "antenna", (s * 5.5, 40 + h + 0.8, 3.5))

    # ---- four drive sleds on an orbit around the rack (spinning while it idles as a ghost)
    orbit = m.group("orbit", (0, 26, 0), body)
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        x, z = math.cos(a) * 15.5, math.sin(a) * 15.5
        d = m.group(f"drive_{k}", (x, 26, z), orbit)
        rot = [0, -math.degrees(a), 0]
        m.box(d, (x, 26, z), [3, 5, 7], "panel", rot=rot, pivot=(x, 26, z))
        m.box(d, (x, 26, z), [3.4, 0.8, 7.4], "chassis_dark", rot=rot, pivot=(x, 26, z))
        m.box(d, (x, 28.2, z), [3.2, 0.5, 5], "led_green" if k % 2 else "cable_glow", rot=rot, pivot=(x, 26, z))

    # ---- hover skirt with a ring of eight thrusters, the bottom port, and four docking clamps (extended at rest)
    skirt = m.group("skirt", (0, 10, 0), body)
    m.box(skirt, (0, 10, 0), [16, 2.4, 12], "chassis_dark")
    for k in range(8):
        a = 2 * math.pi * k / 8
        x, z = math.cos(a) * 7.2, math.sin(a) * 5.4
        m.box(skirt, (x, 8.4, z), [2.6, 1.6, 2.6], "chassis", rot=[0, -math.degrees(a), 0], pivot=(x, 8.4, z))
        m.box(skirt, (x, 7.5, z), [1.8, 0.4, 1.8], "cable_glow", rot=[0, -math.degrees(a), 0], pivot=(x, 7.5, z))
    port_b = m.group("port_b", (0, 8.6, 0), skirt)
    m.box(port_b, (0, 8.6, 0), [6, 2, 6], "chassis_dark")
    m.box(port_b, (0, 7.5, 0), [3.6, 0.4, 3.6], "packet")
    m.locator(port_b, "port_b", (0, 7.0, 0))
    for name, sx, sz in CLAMPS:
        c = m.group(name, (sx * 7, 10, sz * 5), skirt)
        m.box(c, (sx * 7.6, 6, sz * 5.6), [2, 8, 2], "chassis", rot=[sz * -8, 0, sx * 8])
        m.box(c, (sx * 8.2, 1.2, sz * 6.2), [3.6, 1.4, 3.6], "rubber")
        m.box(c, (sx * 7.6, 7.2, sz * 5.6), [2.4, 1.0, 2.4], "hazard", rot=[sz * -8, 0, sx * 8])
    m.locator(body, "core", (0, 24, 0))
    m.locator(root, "base", (0, 0, 0))
    pfx(m, "packet_spark", "coolant_mist", "ack_glint", "null_beam", "spark", "smoke")

    # ================================================================ animations
    def fold(a, t, amount=1.0):
        """Ghost stance: clamps folded up against the skirt, pods retracted, fins closed."""
        for name, sx, sz in CLAMPS:
            m.key(a, name, "rotation", t, [sz * 70 * amount, 0, sx * -70 * amount])
        m.key(a, "pod_l", "position", t, [1.6 * amount, 0, 0])
        m.key(a, "pod_r", "position", t, [-1.6 * amount, 0, 0])

    def open_(a, t):
        """Docked stance: clamps down on the pad, pods out, fins spread."""
        for name, _, _ in CLAMPS:
            m.key(a, name, "rotation", t, [0, 0, 0])
        m.key(a, "pod_l", "position", t, [-1.4, 0, 0])
        m.key(a, "pod_r", "position", t, [1.4, 0, 0])

    def sway(a, length, amp=6.0):
        n = 8
        for s in range(n + 1):
            t = length * s / n
            ph = 2 * math.pi * s / n
            for side in ("l", "r"):
                for k in range(3):
                    m.key(a, f"cable_{side}_{k}", "rotation", t, [amp * math.sin(ph - k * 0.7), 0, amp * 0.5 * math.cos(ph - k * 0.7)])
            m.key(a, "antenna_l", "rotation", t, [2 * math.sin(ph + 0.5), 0, 2 * math.cos(ph)])
            m.key(a, "antenna_r", "rotation", t, [2 * math.sin(ph + 1.8), 0, -2 * math.cos(ph)])

    # ---- loops
    a = m.anim("idle", 4.0)             # ghost: drives orbit, clamps folded, a slow scan with the lens
    fold(a, 0)
    fold(a, 4.0)
    m.keys(a, "orbit", "rotation", [(0, [0, 0, 0]), (4.0, [0, 360, 0])], "linear")
    m.keys(a, "status_ring", "rotation", [(0, [0, 0, 0]), (4.0, [0, 0, -180])], "linear")
    m.keys(a, "lens", "scale", [(0, 1), (1, 1.12), (2, 0.95), (3, 1.1), (4, 1)])
    m.keys(a, "body", "rotation", [(0, [0, -6, 0]), (2, [0, 6, 0]), (4, [0, -6, 0])])
    sway(a, 4.0)
    m.fx(a, 0.5, "null_beam", "lens")
    m.sfx(a, 0.0, "idle.hum")

    a = m.anim("solid", 2.0)            # docked and damageable: clamps planted, pods out, lens wide open
    open_(a, 0)
    open_(a, 2.0)
    m.keys(a, "lens", "scale", [(0, 1.3), (1, 1.36), (2, 1.3)])
    m.keys(a, "status_ring", "rotation", [(0, [0, 0, 0]), (2.0, [0, 0, -360])], "linear")
    m.keys(a, "orbit", "rotation", [(0, [0, 0, 0]), (2.0, [0, 30, 0])], "linear")
    m.keys(a, "fins", "scale", [(0, [1.15, 1, 1.2]), (2, [1.15, 1, 1.2])])
    sway(a, 2.0, amp=2.0)
    m.fx(a, 0.0, "ack_glint", "base")
    m.fx(a, 1.0, "ack_glint", "lens")

    a = m.anim("solid_warn", 0.5)       # closing warning: lens and ring stutter
    open_(a, 0)
    open_(a, 0.5)
    m.keys(a, "lens", "scale", [(0, 1.35), (0.12, 0.8), (0.25, 1.35), (0.37, 0.8), (0.5, 1.35)], "linear")
    m.keys(a, "status_ring", "scale", [(0, 1.1), (0.25, 0.9), (0.5, 1.1)])
    m.keys(a, "fins", "scale", [(0, [1.15, 1, 1.2]), (0.5, [1.15, 1, 1.2])])
    m.fx(a, 0.0, "spark", "lens")

    # ---- one-shots
    a = m.anim("dock", 0.5, "once")
    fold(a, 0)
    m.keys(a, "body", "position", [(0, [0, 0, 0]), (0.4, [0, -1.2, 0]), (0.5, [0, 0, 0])])
    open_(a, 0.35)
    m.fx(a, 0.4, "ack_glint", "base")

    a = m.anim("release", 0.5, "once")
    open_(a, 0)
    fold(a, 0.4)
    m.keys(a, "body", "position", [(0, [0, 0, 0]), (0.2, [0, 1.0, 0]), (0.5, [0, 0, 0])])

    a = m.anim("boot", 5.0, "once")     # P0: the rack assembles out of static, drives slot into orbit, the lens powers up
    fold(a, 0)
    fold(a, 5.0)
    m.keys(a, "null_router", "scale", [(0, [0.3, 0.05, 0.3]), (1.5, [0.8, 0.6, 0.8]), (3.0, 1)])
    m.keys(a, "orbit", "scale", [(0, 0.01), (2.8, 0.01), (3.6, 1.2), (4.0, 1)])
    m.keys(a, "orbit", "rotation", [(0, [0, 0, 0]), (5.0, [0, 720, 0])], "linear")
    m.keys(a, "lens", "scale", [(0, 0.01), (3.8, 0.01), (4.2, 1.8), (5.0, 1)])
    m.keys(a, "crown", "position", [(0, [0, -6, 0]), (2.5, [0, -6, 0]), (3.3, [0, 0.8, 0]), (3.6, [0, 0, 0])])
    m.keys(a, "antenna_l", "scale", [(0, [1, 0.01, 1]), (3.2, [1, 0.01, 1]), (3.8, 1)])
    m.keys(a, "antenna_r", "scale", [(0, [1, 0.01, 1]), (3.4, [1, 0.01, 1]), (4.0, 1)])
    for t in (0.2, 1.0, 1.8, 2.6):
        m.fx(a, t, "null_beam", "core")
    m.fx(a, 4.2, "ack_glint", "lens")
    m.sfx(a, 0.0, "boot")

    a = m.anim("chime", 2.0, "once")    # boot chime: lens flares, ring spins up, antennas ring
    fold(a, 0)
    fold(a, 2.0)
    m.keys(a, "status_ring", "rotation", [(0, [0, 0, 0]), (2.0, [0, 0, -720])], "linear")
    m.keys(a, "lens", "scale", [(0, 1), (1.8, 1.6), (2.0, 2.2)])
    for s in range(9):
        t = 1.2 + s * 0.1
        m.key(a, "antenna_l", "rotation", t, [0, 0, 6 * (-1) ** s])
        m.key(a, "antenna_r", "rotation", t, [0, 0, -6 * (-1) ** s])
    m.fx(a, 1.95, "ack_glint", "lens")

    a = m.anim("ping", 1.5, "once")     # request_ping: crown tilts to the consoles, lens blinks twice
    fold(a, 0)
    fold(a, 1.5)
    m.keys(a, "crown", "rotation", [(0, [0, 0, 0]), (0.4, [-12, 0, 0]), (1.2, [-12, 0, 0]), (1.5, [0, 0, 0])])
    m.keys(a, "lens", "scale", [(0, 1), (0.3, 1.5), (0.45, 0.8), (0.6, 1.5), (0.75, 1), (1.5, 1)])
    m.fx(a, 0.3, "ack_glint", "crown")

    a = m.anim("lance_windup", 1.5, "once")   # ghost_lance: leans in, lens contracts to a bright point
    fold(a, 0)
    fold(a, 1.5)
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (1.2, [-10, 0, 0])])
    m.keys(a, "lens", "scale", [(0, 1), (1.3, 0.6), (1.5, 0.5)])
    m.keys(a, "status_ring", "rotation", [(0, [0, 0, 0]), (1.5, [0, 0, -540])], "linear")
    m.keys(a, "pod_l", "rotation", [(0, [0, 0, 0]), (1.2, [0, -20, 0])])
    m.keys(a, "pod_r", "rotation", [(0, [0, 0, 0]), (1.2, [0, 20, 0])])
    for t in (0.4, 0.8, 1.2):
        m.fx(a, t, "null_beam", "lens")

    a = m.anim("lance", 0.6, "once")
    fold(a, 0)
    fold(a, 0.6)
    m.keys(a, "body", "rotation", [(0, [-10, 0, 0]), (0.08, [8, 0, 0]), (0.6, [0, 0, 0])])
    m.keys(a, "lens", "scale", [(0, 0.5), (0.05, 2.0), (0.6, 1)])
    m.keys(a, "pod_l", "rotation", [(0, [0, -20, 0]), (0.6, [0, 0, 0])])
    m.keys(a, "pod_r", "rotation", [(0, [0, 20, 0]), (0.6, [0, 0, 0])])
    m.fx(a, 0.02, "null_beam", "lens")

    a = m.anim("burst_windup", 1.5, "once")   # packet_burst: pods swing out, ports glow
    fold(a, 0)
    m.keys(a, "pod_l", "rotation", [(0, [0, 0, 0]), (1.2, [0, 35, -10])])
    m.keys(a, "pod_r", "rotation", [(0, [0, 0, 0]), (1.2, [0, -35, 10])])
    m.keys(a, "pod_l", "position", [(0, [1.6, 0, 0]), (1.0, [-1.5, 0, 0])])
    m.keys(a, "pod_r", "position", [(0, [-1.6, 0, 0]), (1.0, [1.5, 0, 0])])
    for t in (0.5, 1.0):
        m.fx(a, t, "packet_spark", "port_l")
        m.fx(a, t + 0.05, "packet_spark", "port_r")

    a = m.anim("burst", 1.0, "once")
    for side, sgn in (("pod_l", 1), ("pod_r", -1)):
        m.keys(a, side, "rotation", [(0, [0, 35 * sgn, -10 * sgn]), (0.1, [0, 20 * sgn, -4 * sgn]), (0.3, [0, 35 * sgn, -10 * sgn]),
                                     (0.4, [0, 20 * sgn, -4 * sgn]), (1.0, [0, 0, 0])])
        m.keys(a, side, "position", [(0, [-1.5 * sgn, 0, 0]), (1.0, [1.6 * sgn, 0, 0])])
    m.fx(a, 0.0, "packet_spark", "port_l")
    m.fx(a, 0.25, "packet_spark", "port_r")
    m.fx(a, 0.5, "packet_spark", "port_b")

    a = m.anim("flick", 1.5, "once")    # console_flick: the red antenna points, a beam crawls to the console
    fold(a, 0)
    fold(a, 1.5)
    m.keys(a, "antenna_l", "rotation", [(0, [0, 0, 0]), (0.5, [-55, 0, 0]), (1.3, [-55, 0, 0]), (1.5, [0, 0, 0])])
    m.keys(a, "crown", "rotation", [(0, [0, 0, 0]), (0.5, [-8, 0, 0]), (1.5, [0, 0, 0])])
    for t in (0.6, 1.0, 1.4):
        m.fx(a, t, "packet_spark", "antenna")

    a = m.anim("ttl_windup", 1.5, "once")     # ttl_expiry: rises, clamps splay, the skirt glows
    m.keys(a, "body", "position", [(0, [0, 0, 0]), (1.3, [0, 3, 0])])
    for name, sx, sz in CLAMPS:
        m.keys(a, name, "rotation", [(0, [sz * 70, 0, sx * -70]), (1.3, [sz * -40, 0, sx * 40])])
    m.keys(a, "orbit", "rotation", [(0, [0, 0, 0]), (1.5, [0, 540, 0])], "linear")
    for t in (0.5, 1.0):
        m.fx(a, t, "null_beam", "port_b")

    a = m.anim("ttl", 0.6, "once")
    m.keys(a, "body", "position", [(0, [0, 3, 0]), (0.08, [0, -2, 0]), (0.6, [0, 0, 0])])
    for name, sx, sz in CLAMPS:
        m.keys(a, name, "rotation", [(0, [sz * -40, 0, sx * 40]), (0.6, [sz * 70, 0, sx * -70])])
    m.fx(a, 0.08, "null_beam", "base")

    a = m.anim("misroute", 0.5, "once")
    fold(a, 0)
    fold(a, 0.5)
    m.keys(a, "lens", "scale", [(0, 1), (0.05, 2.2), (0.5, 1)])
    m.keys(a, "crown", "rotation", [(0, [0, 0, 0]), (0.05, [0, 0, 12]), (0.2, [0, 0, -8]), (0.5, [0, 0, 0])])
    m.fx(a, 0.05, "null_beam", "lens")

    a = m.anim("storm_windup", 2.0, "once")   # retry_storm: the whole rack shudders, drives spin up
    fold(a, 0)
    fold(a, 2.0)
    for s in range(17):
        t = s * 0.125
        m.key(a, "body", "rotation", t, [3 * math.sin(s * 2.3), 4 * math.sin(s * 1.7), 3 * math.cos(s * 2.9)])
    m.keys(a, "orbit", "rotation", [(0, [0, 0, 0]), (2.0, [0, 1080, 0])], "linear")
    m.keys(a, "status_ring", "scale", [(0, 1), (1.0, 1.3), (2.0, 1)])
    for t in (0.5, 1.0, 1.5):
        m.fx(a, t, "packet_spark", "core")

    a = m.anim("storm", 2.0, "once")
    fold(a, 0)
    fold(a, 2.0)
    m.keys(a, "orbit", "rotation", [(0, [0, 0, 0]), (2.0, [0, 1440, 0])], "linear")
    for side, sgn in (("pod_l", 1), ("pod_r", -1)):
        for s in range(5):
            m.key(a, side, "rotation", s * 0.5, [0, (25 if s % 2 else 5) * sgn, 0])
    m.fx(a, 0.0, "packet_spark", "port_l")
    m.fx(a, 0.5, "packet_spark", "port_r")
    m.fx(a, 1.0, "packet_spark", "port_b")

    a = m.anim("hurt", 0.3, "once")
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (0.05, [4, 0, -3]), (0.3, [0, 0, 0])])
    m.keys(a, "lens", "scale", [(0, 1.3), (0.05, 0.9), (0.3, 1.3)])
    m.fx(a, 0.0, "spark", "core")

    a = m.anim("death", 7.0, "hold")    # queue empty: the lens goes out, the drives drop, the rack settles and powers down
    open_(a, 0)
    m.keys(a, "lens", "scale", [(0, 1.3), (1.0, 1.8), (1.4, 0.4), (2.0, 1.0), (2.4, 0.2), (3.0, 0.01), (7, 0.01)])
    m.keys(a, "status_ring", "scale", [(0, 1), (2.8, 1), (3.4, 0.01), (7, 0.01)])
    m.keys(a, "orbit", "position", [(0, [0, 0, 0]), (2.5, [0, 0, 0]), (3.6, [0, -24, 0]), (7, [0, -24, 0])])
    m.keys(a, "orbit", "rotation", [(0, [0, 0, 0]), (2.5, [0, 90, 0]), (3.6, [18, 100, 9]), (7, [18, 100, 9])])
    m.keys(a, "antenna_l", "rotation", [(0, [0, 0, 0]), (3.5, [0, 0, 0]), (4.5, [30, 0, -70]), (7, [30, 0, -70])])
    m.keys(a, "antenna_r", "rotation", [(0, [0, 0, 0]), (3.8, [0, 0, 0]), (4.7, [-20, 0, 60]), (7, [-20, 0, 60])])
    m.keys(a, "body", "position", [(0, [0, 0, 0]), (4.0, [0, 0, 0]), (5.5, [0, -3, 0]), (7, [0, -3.5, 0])])
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (4.0, [0, 0, 0]), (5.5, [6, 0, -4]), (7, [6, 0, -4])])
    m.keys(a, "pod_l", "rotation", [(0, [0, 0, 0]), (4.8, [0, 0, 0]), (5.6, [0, 0, 25]), (7, [0, 0, 25])])
    m.keys(a, "pod_r", "rotation", [(0, [0, 0, 0]), (5.0, [0, 0, 0]), (5.8, [0, 0, -20]), (7, [0, 0, -20])])
    for side in ("l", "r"):
        m.keys(a, f"cable_{side}_0", "rotation", [(0, [0, 0, 0]), (5, [0, 0, 0]), (6, [25, 0, 0]), (7, [25, 0, 0])])
    for t in (0.0, 1.0, 2.0, 3.0):
        m.fx(a, t, "spark", "core")
    for t in (3.5, 4.5, 5.5, 6.5):
        m.fx(a, t, "smoke", "crown")
    m.fx(a, 3.0, "ack_glint", "lens")
    return m


# ============================================================ retry packet (add)
def retry_packet():
    m = Model("retry_packet", seed=4041)
    root = m.group("retry_packet", (0, 0, 0))
    pk = m.group("packet", (0, 6.5, 0), root)
    # a glowing amber data packet in a dark frame, with a header tab and a routing arrow on each side
    m.box(pk, (0, 6.5, 0), [6.4, 4.6, 6.4], "packet")
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.box(pk, (sx * 3.2, 6.5, sz * 3.2), [0.8, 5.0, 0.8], "chassis_dark")
    m.box(pk, (0, 9.0, 0), [6.8, 0.6, 6.8], "chassis_dark")
    m.box(pk, (0, 4.0, 0), [6.8, 0.6, 6.8], "chassis_dark")
    m.box(pk, (0, 9.6, -2.0), [4.0, 1.0, 2.0], "chassis")
    m.box(pk, (0, 9.7, -2.0), [2.4, 0.4, 1.0], "led_red")
    for s in (-1, 1):
        m.box(pk, (s * 3.7, 6.5, 0), [0.6, 2.0, 3.6], "led_amber", rot=[45, 0, 0])
    m.box(pk, (0, 6.5, -3.4), [2.4, 2.4, 0.4], "chassis_dark", rot=[0, 0, 45])
    m.box(pk, (0, 6.5, -3.6), [1.2, 1.2, 0.4], "lens", rot=[0, 0, 45])
    m.locator(pk, "core", (0, 6.5, 0))
    # the retry loop: a ring of LED segments circling the packet
    ring = m.group("ring", (0, 6.5, 0), pk)
    m.ring(ring, (0, 6.5, 0), 5.4, 8, [1.2, 0.6, 0.6], "led_amber", axis="y")
    m.box(root, (0, 0.4, 0), [3.0, 0.4, 3.0], "packet")
    pfx(m, "packet_spark")

    a = m.anim("idle", 1.0)
    m.keys(a, "packet", "position", [(0, [0, 0, 0]), (0.5, [0, 0.8, 0]), (1.0, [0, 0, 0])])
    m.keys(a, "ring", "rotation", [(0, [0, 0, 0]), (1.0, [0, 360, 0])], "linear")

    a = m.anim("move", 0.6)
    m.keys(a, "packet", "rotation", [(0, [15, 0, 0]), (0.3, [15, 0, 4]), (0.6, [15, 0, 0])])
    m.keys(a, "packet", "position", [(0, [0, 0, 0]), (0.3, [0, 1.0, 0]), (0.6, [0, 0, 0])])
    m.keys(a, "ring", "rotation", [(0, [0, 0, 0]), (0.6, [0, 540, 0])], "linear")

    a = m.anim("uplink", 0.5)           # flipping a console: shaking, swelling, ring spinning hard
    m.keys(a, "packet", "scale", [(0, 1), (0.25, 1.15), (0.5, 1)])
    for s in range(6):
        m.key(a, "packet", "rotation", s * 0.1, [0, 0, 6 * (-1) ** s])
    m.keys(a, "ring", "rotation", [(0, [0, 0, 0]), (0.5, [0, 720, 0])], "linear")
    m.fx(a, 0.0, "packet_spark", "core")
    return m


ALL = [null_router, retry_packet]
