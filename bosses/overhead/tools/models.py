"""Overhead model specs: the hovering artillery chassis, the generator pylon (block entity), and two ordnance
models. Everything faces north (-Z), like the Matris models; the renderer yaws the entity toward its target and
the 'turret' / 'howitzer' bones add fine aim."""
import boss_env  # noqa: F401  (shared pipeline + this boss)
import math
from lib import Model, particle

# Industrial particle set (Snowstorm previews; in game these are skylore_bosses:<name> particle types).
PX = {
    "smoke": particle("smoke", rate=8, duration=1, lifetime=2.2, speed=0.8, direction=[0, 1, 0], radius=0.6,
                      gravity=-0.5, drag=1.0, size=(0.3, 0.3), size_end=(1.0, 1.0), color=(0.3, 0.3, 0.32, 0.8),
                      color_end=(0.2, 0.2, 0.2, 0), uv=(0, 0), frames=8),
    "spark": particle("spark", burst=20, duration=0.1, lifetime=0.4, speed=6, radius=0.3, gravity=10, drag=1.5,
                      size=(0.08, 0.08), color=(1, 0.85, 0.4, 1), color_end=(1, 0.4, 0.1, 0.6), uv=(8, 88),
                      blend="add", facing="lookat_direction"),
    "muzzle_flash": particle("muzzle_flash", burst=12, duration=0.05, lifetime=0.15, speed=3, radius=0.2,
                             size=(0.6, 0.6), size_end=(0.1, 0.1), color=(1, 0.9, 0.6, 1), color_end=(1, 0.5, 0.1, 0),
                             uv=(0, 88), blend="add"),
    "grid_arc": particle("grid_arc", rate=30, duration=1, lifetime=0.25, speed=5, radius=0.8, drag=4,
                         size=(0.12, 0.12), size_end=(0.02, 0.02), color=(0.6, 0.95, 1, 1), color_end=(0.2, 0.6, 1, 0.5),
                         uv=(8, 88), blend="add", facing="lookat_direction"),
    "ember": particle("ember", rate=10, duration=1, lifetime=1.2, speed=1.2, radius=0.5, gravity=-1, drag=0.5,
                      size=(0.1, 0.1), size_end=(0.02, 0.02), color=(1, 0.6, 0.2, 1), color_end=(0.8, 0.2, 0.05, 0),
                      uv=(0, 88), blend="add"),
    "shield": particle("shield", burst=80, duration=0.1, lifetime=0.8, speed=6, radius=2.5, drag=3,
                       size=(0.3, 0.3), size_end=(0.05, 0.05), color=(0.4, 0.8, 1, 0.9), color_end=(0.2, 0.4, 1, 0),
                       uv=(0, 88), blend="add"),
}


def pfx(m, *names):
    for n in names:
        m.particles[n] = PX[n]


# ============================================================ the chassis
def overhead():
    m = Model("overhead", seed=417)
    root = m.group("overhead", (0, 0, 0))
    body = m.group("body", (0, 24, 0), root)

    # hull
    m.box(body, (0, 26, 0), [40, 14, 52], "noven")
    m.box(body, (0, 35, 2), [30, 6, 36], "steel")
    m.box(body, (0, 38.5, -6), [14, 1, 10], "hazard")
    m.box(body, (0, 20, 0), [34, 4, 44], "steel_dark")
    m.box(body, (0, 25, -29), [30, 11, 8], "steel_dark", rot=[12, 0, 0])
    for s in (-1, 1):
        m.box(body, (s * 20.6, 26, 0), [1, 3, 50], "hazard")
        m.box(body, (s * 21.2, 24, -2), [2, 10, 38], "plate")
        m.box(body, (s * 21.8, 30, 14), [1.5, 2, 2], "amber", name=f"side_light_{'lr'[s > 0]}")
    m.box(body, (0, 26, 26.6), [40, 3, 1], "hazard")
    m.box(body, (0, 29, 28), [24, 12, 8], "steel_dark")
    m.box(body, (0, 29, 32.2), [20, 8, 0.5], "grille")
    m.box(body, (0, 36, 20), [18, 2, 10], "grille")
    m.box(body, (0, 38.2, 8), [10, 1, 10], "energy")
    m.locator(body, "core", (0, 30, 0))

    # sensor lens (laser_sweep origin)
    lens = m.group("lens_mount", (0, 24, -33), body)
    m.box(lens, (0, 24, -34), [12, 8, 4], "steel_dark")
    m.box(lens, (0, 24, -36.3), [6, 6, 1.5], "lens")
    m.box(lens, (0, 28.5, -34), [14, 1, 5], "hazard")
    m.locator(lens, "lens", (0, 24, -38))

    # antenna + beacon
    mast = m.group("mast", (8, 38, 14), body)
    m.box(mast, (8, 46, 14), [1.5, 16, 1.5], "steel")
    m.box(mast, (8, 55, 14), [3, 3, 3], "amber")
    m.locator(mast, "beacon", (8, 55, 14))

    # flare rack (suppression_flare)
    rack = m.group("flare_rack", (-9, 38, 12), body)
    m.box(rack, (-9, 39, 12), [11, 2, 6], "steel_dark")
    for k in range(3):
        m.box(rack, (-12 + k * 3, 42, 11), [2.5, 6, 2.5], "rubber", rot=[-20, 0, 0])
    m.locator(rack, "flare", (-9, 46, 9))

    # four ducted-fan nacelles
    for i, (sx, sz) in enumerate(((-30, -22), (30, -22), (-30, 22), (30, 22))):
        nac = m.group(f"nacelle_{i}", (sx, 28, sz), body)
        sgn = 1 if sx > 0 else -1
        m.box(nac, ((sx + sgn * -20) / 2 + sgn * 2, 28, sz), [abs(sx) - 16, 4, 5], "steel_dark")
        m.ring(nac, (sx, 28, sz), 9.5, 12, [6, 7, 2.5], "noven")
        m.ring(nac, (sx, 31.8, sz), 10.5, 12, [6, 1, 1.5], "hazard", phase=0.26)
        m.box(nac, (sx, 24.2, sz), [9, 1, 9], "amber")
        rot = m.group(f"rotor_{i}", (sx, 28, sz), nac)
        m.box(rot, (sx, 28, sz), [4, 3, 4], "brass")
        for k in range(3):
            m.box(rot, (sx, 28.5, sz), [17, 0.8, 3], "steel", rot=[0, k * 60, 8])
        m.locator(nac, f"thruster_{i}", (sx, 23, sz))

    # missile pods with top bay doors
    for side, sx in (("l", -26), ("r", 26)):
        pod = m.group(f"pod_{side}", (sx, 20, -4), body)
        m.box(pod, (sx, 20, -4), [10, 10, 22], "noven")
        m.box(pod, (sx, 20, -15.5), [10.5, 10.5, 1.5], "hazard")
        m.box(pod, (sx, 15.5, -4), [8, 1, 18], "steel_dark")
        for r in range(2):
            for c in range(3):
                m.box(pod, (sx - 2 + r * 4, 24.2, -10 + c * 6), [2.5, 1.5, 2.5], "warning")
        door = m.group(f"door_{side}", (sx + (4.5 if sx > 0 else -4.5), 25.5, -4), pod)
        m.box(door, (sx, 25.5, -4), [9.5, 1, 20], "plate")
        m.box(door, (sx, 26.1, -4), [4, 0.5, 16], "hazard")
        m.locator(pod, f"bay_{side}", (sx, 27, -4))

    # underside turret: yaw ball + pitching howitzer
    turret = m.group("turret", (0, 16, -6), body)
    m.box(turret, (0, 13.5, -6), [14, 9, 14], "steel_dark")
    m.box(turret, (0, 18.5, -6), [18, 2, 18], "steel")
    m.box(turret, (6.5, 13, -6), [2, 6, 8], "hazard")
    m.box(turret, (-6.5, 13, -6), [2, 6, 8], "hazard")
    how = m.group("howitzer", (0, 13, -8), turret)
    m.box(how, (0, 13, -14), [7, 7, 10], "noven")
    m.box(how, (0, 13, -24), [4.5, 4.5, 22], "steel")
    m.box(how, (0, 13, -35), [7, 7, 4], "steel_dark")
    m.box(how, (0, 13, -21), [5.5, 5.5, 1], "steel_dark")
    m.locator(how, "muzzle", (0, 13, -37))

    # nose gatling (strafe_barrage / flak_burst)
    gat = m.group("gatling", (0, 18, -27), body)
    m.box(gat, (0, 17, -26), [8, 6, 8], "steel_dark")
    bar = m.group("gatling_barrels", (0, 16, -31), gat)
    m.ring(bar, (0, 16, -33), 2.0, 6, [1.3, 1.3, 11], "steel", axis="z")
    m.box(bar, (0, 16, -36), [5.5, 5.5, 1.2], "steel_dark")
    m.locator(gat, "gatling", (0, 16, -39))

    pfx(m, "smoke", "spark", "muzzle_flash", "grid_arc", "ember", "shield")
    rotors = [f"rotor_{i}" for i in range(4)]

    def spin(a, length, turns, bones=rotors, axis=1):
        for b in bones:
            v = [0, 0, 0]
            m.key(a, b, "rotation", 0, list(v), "linear")
            v[axis] = 360 * turns
            m.key(a, b, "rotation", length, list(v), "linear")

    def bob(a, length, amp=1.5):
        for s in range(5):
            t = length * s / 4
            m.key(a, "body", "position", t, [0, amp * math.sin(2 * math.pi * s / 4), 0])
            m.key(a, "body", "rotation", t, [1.5 * math.sin(2 * math.pi * s / 4 + 1), 0, 1.2 * math.cos(2 * math.pi * s / 4)])

    # ---- loops
    a = m.anim("idle", 2.0)
    spin(a, 2.0, 6)
    bob(a, 2.0)
    m.keys(a, "mast", "rotation", [(0, [0, 0, 0]), (1, [0, 0, 4]), (2, [0, 0, 0])])
    m.fx(a, 0.5, "smoke", "thruster_2")
    m.fx(a, 1.5, "smoke", "thruster_3")
    m.sfx(a, 0.0, "rotor.loop")

    a = m.anim("brownout", 2.0)      # a pylon just died: grid fault, chassis sags and sparks
    spin(a, 2.0, 2)
    for s in range(5):
        t = s * 0.5
        m.key(a, "body", "rotation", t, [8 + 3 * math.sin(s * 2.1), 0, -6 + 4 * math.cos(s * 1.7)])
        m.key(a, "body", "position", t, [0, -2 + 1.5 * math.sin(s * 2.4), 0])
    m.keys(a, "howitzer", "rotation", [(0, [-25, 0, 0]), (2, [-25, 0, 0])])
    for t in (0.1, 0.9, 1.6):
        m.fx(a, t, "spark", "core")
    m.fx(a, 0.4, "smoke", "core")
    m.fx(a, 1.2, "grid_arc", "lens")
    m.sfx(a, 0.0, "brownout")

    a = m.anim("laser_fire", 1.0)
    spin(a, 1.0, 3)
    bob(a, 1.0, 0.4)
    for t in (0.0, 0.25, 0.5, 0.75):
        m.key(a, "lens_mount", "scale", t, [1.05, 1.05, 1] if int(t * 4) % 2 == 0 else [1, 1, 1])
    m.fx(a, 0.0, "ember", "lens")
    m.fx(a, 0.5, "ember", "lens")

    a = m.anim("strafe", 1.0)
    spin(a, 1.0, 4)
    m.keys(a, "body", "rotation", [(0, [12, 0, 0]), (1, [12, 0, 0])])
    m.keys(a, "gatling_barrels", "rotation", [(0, [0, 0, 0]), (1, [0, 0, 1440])], "linear")
    for t in (0.0, 0.3, 0.6):
        m.fx(a, t, "muzzle_flash", "gatling")

    # ---- one-shots
    a = m.anim("power_on", 6.0, "once")
    for b in rotors:
        m.keys(a, b, "rotation", [(0, [0, 0, 0]), (2, [0, 180, 0]), (4, [0, 900, 0]), (6, [0, 2160, 0])], "linear")
    m.keys(a, "body", "rotation", [(0, [18, 0, -10]), (1.5, [18, 0, -10]), (3, [-6, 0, 3]), (4.5, [2, 0, 0]), (6, [0, 0, 0])])
    m.keys(a, "turret", "rotation", [(0, [0, 0, 0]), (3.5, [0, 0, 0]), (4.2, [0, 90, 0]), (5, [0, -90, 0]), (5.8, [0, 0, 0])])
    m.keys(a, "lens_mount", "scale", [(0, [1, 1, 0.2]), (2.5, [1, 1, 0.2]), (2.7, [1, 1, 1.2]), (3, [1, 1, 1])])
    m.keys(a, "mast", "rotation", [(0, [0, 0, -80]), (1, [0, 0, -80]), (2, [0, 0, 5]), (2.3, [0, 0, 0])])
    m.fx(a, 0.5, "spark", "core")
    m.fx(a, 1.0, "smoke", "thruster_0")
    m.fx(a, 1.1, "smoke", "thruster_1")
    m.fx(a, 1.2, "smoke", "thruster_2")
    m.fx(a, 1.3, "smoke", "thruster_3")
    m.fx(a, 2.7, "ember", "lens")
    m.fx(a, 5.9, "grid_arc", "core")
    m.sfx(a, 0.0, "power.charge")

    a = m.anim("power_pulse", 1.5, "once")
    m.keys(a, "body", "scale", [(0, 1), (0.1, [1.08, 0.92, 1.08]), (0.3, 1)])
    m.fx(a, 0.05, "shield", "core")
    m.fx(a, 0.06, "grid_arc", "core")
    m.sfx(a, 0.0, "power.pulse")

    a = m.anim("howitzer_load", 1.0, "once")
    m.keys(a, "howitzer", "rotation", [(0, [0, 0, 0]), (0.8, [-30, 0, 0]), (1.0, [-32, 0, 0])])
    m.keys(a, "howitzer", "position", [(0, [0, 0, 0]), (0.3, [0, 0, 3]), (0.6, [0, 0, 0])])
    m.sfx(a, 0.0, "howitzer.load")

    a = m.anim("howitzer_fire", 0.8, "once")
    m.keys(a, "howitzer", "rotation", [(0, [-32, 0, 0]), (0.8, [-10, 0, 0])])
    m.keys(a, "howitzer", "position", [(0, [0, 0, 0]), (0.05, [0, 0, 6]), (0.5, [0, 0, 0])])
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (0.08, [-5, 0, 0]), (0.6, [0, 0, 0])])
    m.fx(a, 0.0, "muzzle_flash", "muzzle")
    m.fx(a, 0.02, "smoke", "muzzle")
    m.sfx(a, 0.0, "howitzer.fire")

    a = m.anim("missile_open", 1.5, "hold")
    for side, sgn in (("l", -1), ("r", 1)):
        m.keys(a, f"door_{side}", "rotation", [(0, [0, 0, 0]), (0.6, [0, 0, sgn * 110]), (1.5, [0, 0, sgn * 110])])
        m.keys(a, f"pod_{side}", "position", [(0, [0, 0, 0]), (0.6, [0, 2, 0]), (1.5, [0, 2, 0])])
    m.fx(a, 0.5, "smoke", "bay_l")
    m.fx(a, 0.55, "smoke", "bay_r")
    m.sfx(a, 0.0, "missile.open")

    a = m.anim("missile_close", 0.8, "once")
    for side, sgn in (("l", -1), ("r", 1)):
        m.keys(a, f"door_{side}", "rotation", [(0, [0, 0, sgn * 110]), (0.8, [0, 0, 0])])
        m.keys(a, f"pod_{side}", "position", [(0, [0, 2, 0]), (0.8, [0, 0, 0])])

    a = m.anim("laser_charge", 2.0, "once")
    m.keys(a, "lens_mount", "scale", [(0, 1), (1.6, [1.3, 1.3, 1.1]), (2.0, [1.15, 1.15, 1])])
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (2.0, [10, 0, 0])])
    for t in (0.2, 0.7, 1.2, 1.7):
        m.fx(a, t, "ember", "lens")
    m.sfx(a, 0.0, "laser.charge")

    a = m.anim("laser_vent", 1.5, "once")   # recovery: cooling fins exposed (vulnerable)
    m.keys(a, "lens_mount", "scale", [(0, [1.15, 1.15, 1]), (0.4, [0.8, 0.8, 1]), (1.5, 1)])
    m.keys(a, "body", "rotation", [(0, [10, 0, 0]), (1.5, [0, 0, 0])])
    m.fx(a, 0.1, "smoke", "lens")
    m.fx(a, 0.5, "smoke", "core")
    m.sfx(a, 0.0, "laser.vent")

    a = m.anim("strafe_spinup", 1.5, "once")
    m.keys(a, "gatling_barrels", "rotation", [(0, [0, 0, 0]), (1.5, [0, 0, 1080])], "linear")
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (1.5, [12, 0, 0])])
    m.sfx(a, 0.0, "strafe.spinup")

    a = m.anim("flare_fire", 0.6, "once")
    m.keys(a, "flare_rack", "rotation", [(0, [0, 0, 0]), (0.05, [-8, 0, 0]), (0.4, [0, 0, 0])])
    m.fx(a, 0.0, "muzzle_flash", "flare")
    m.fx(a, 0.02, "smoke", "flare")
    m.sfx(a, 0.0, "flare.launch")

    a = m.anim("overcharge", 2.0, "once")
    m.keys(a, "lens_mount", "scale", [(0, 1), (1.8, [1.25, 1.25, 1]), (2, 1)])
    for t in (0.1, 0.6, 1.1, 1.6):
        m.fx(a, t, "grid_arc", "core")
    m.sfx(a, 0.0, "overcharge.charge")

    a = m.anim("carpet", 3.0, "once")
    for side, sgn in (("l", -1), ("r", 1)):
        m.keys(a, f"door_{side}", "rotation", [(0, [0, 0, 0]), (0.5, [0, 0, sgn * 150]), (2.6, [0, 0, sgn * 150]), (3, [0, 0, 0])])
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (0.8, [-6, 0, 0]), (2.6, [-6, 0, 0]), (3, [0, 0, 0])])
    m.fx(a, 0.2, "smoke", "bay_l")
    m.fx(a, 0.25, "smoke", "bay_r")
    m.sfx(a, 0.0, "carpet.klaxon")

    a = m.anim("shield_pulse", 1.5, "once")
    m.keys(a, "body", "scale", [(0, 1), (0.15, 1.12), (0.5, 1)])
    m.fx(a, 0.1, "shield", "core")
    m.fx(a, 0.12, "grid_arc", "core")
    m.sfx(a, 0.0, "shield.pulse")

    a = m.anim("hurt", 0.4, "once")
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (0.06, [4, 0, -3]), (0.4, [0, 0, 0])])
    m.fx(a, 0.0, "spark", "core")
    m.sfx(a, 0.0, "hurt")

    a = m.anim("death", 6.0, "hold")
    for b in rotors:
        m.keys(a, b, "rotation", [(0, [0, 0, 0]), (2, [0, 400, 0]), (4, [0, 560, 0]), (6, [0, 600, 0])], "linear")
    m.keys(a, "body", "rotation", [(0, [0, 0, 0]), (1, [15, 20, -20]), (3, [25, 140, -35]), (4.5, [30, 200, -40]), (6, [30, 200, -40])])
    m.keys(a, "mast", "rotation", [(0, [0, 0, 0]), (4.4, [0, 0, 0]), (4.6, [0, 0, 70]), (6, [0, 0, 80])])
    m.keys(a, "nacelle_1", "rotation", [(0, [0, 0, 0]), (2.5, [0, 0, 0]), (2.7, [0, 0, 35]), (6, [0, 0, 40])])
    m.keys(a, "lens_mount", "scale", [(0, 1), (4.5, 1), (4.6, [1, 1, 0.1]), (6, [1, 1, 0.1])])
    for t in (0.0, 0.8, 1.6, 2.5, 3.3, 4.1):
        m.fx(a, t, "spark", "core")
    for t in (0.3, 1.2, 2.2, 3.6):
        m.fx(a, t, "smoke", f"thruster_{int(t) % 4}")
    m.fx(a, 4.5, "muzzle_flash", "core")
    m.sfx(a, 0.0, "death")
    return m


# ============================================================ generator pylon (block entity, 3x3 footprint, 5 tall)
def generator_pylon():
    m = Model("generator_pylon", seed=44)
    root = m.group("pylon", (0, 0, 0))
    base = m.group("base", (0, 0, 0), root)
    m.box(base, (0, 5, 0), [40, 10, 40], "steel_dark")
    m.box(base, (0, 10.5, 0), [41, 2, 41], "hazard")
    m.box(base, (0, 13, 0), [30, 4, 30], "steel")
    for i, (sx, sz) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1))):
        m.box(base, (sx * 18, 11, sz * 18), [5, 22, 5], "noven")
        m.box(base, (sx * 22, 1.5, sz * 12), [4, 3, 10], "rubber")
        lamp = m.group(f"lamp_{i}", (sx * 18, 23, sz * 18), base)
        m.box(lamp, (sx * 18, 23.5, sz * 18), [3.5, 3, 3.5], "amber")
    for sgn in (-1, 1):
        m.box(base, (sgn * 20.6, 5, 0), [1, 6, 14], "grille")
        m.box(base, (0, 5, sgn * 20.6), [14, 6, 1], "grille")
    column = m.group("column", (0, 15, 0), root)
    m.box(column, (0, 36, 0), [10, 42, 10], "steel")
    m.box(column, (0, 36, 0), [12, 3, 12], "steel_dark")
    coils = []
    for k in range(6):
        c = m.group(f"coil_{k}", (0, 20 + k * 7, 0), column)
        m.box(c, (0, 20 + k * 7, 0), [20 - k, 3, 20 - k], "copper")
        coils.append(c)
    cage = m.group("cage", (0, 58, 0), column)
    for sx, sz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        m.box(cage, (sx * 9, 67, sz * 9), [2, 18, 2], "brass")
    m.box(cage, (0, 77, 0), [22, 3, 22], "steel_dark")
    m.box(cage, (0, 79, 0), [8, 2, 8], "hazard")
    core = m.group("core", (0, 67, 0), cage)
    m.box(core, (0, 67, 0), [10, 10, 10], "energy", rot=[45, 0, 45])
    m.box(core, (0, 67, 0), [7, 7, 7], "energy")
    m.locator(core, "core", (0, 67, 0))
    m.locator(cage, "top", (0, 80, 0))
    m.locator(base, "pad", (0, 12, 0))
    pfx(m, "smoke", "spark", "grid_arc", "ember", "shield")
    lamps = [f"lamp_{i}" for i in range(4)]

    a = m.anim("online", 2.0)
    m.keys(a, "core", "rotation", [(0, [0, 0, 0]), (2, [0, 360, 0])], "linear")
    m.keys(a, "core", "position", [(0, [0, 0, 0]), (1, [0, 1.5, 0]), (2, [0, 0, 0])])
    for k, c in enumerate(coils):
        t = k * 0.25
        m.keys(a, c, "scale", [(0, 1), (t, 1), (t + 0.2, [1.12, 1, 1.12]), (min(2, t + 0.5), 1), (2, 1)])
    m.fx(a, 0.0, "grid_arc", "core")
    m.fx(a, 1.0, "grid_arc", "core")
    m.sfx(a, 0.0, "pylon.hum")

    a = m.anim("hurt", 0.35, "once")
    m.keys(a, "column", "rotation", [(0, [0, 0, 0]), (0.05, [3, 0, -2]), (0.35, [0, 0, 0])])
    m.fx(a, 0.0, "spark", "core")
    m.sfx(a, 0.0, "pylon.hit")

    a = m.anim("overcharge", 2.0, "once")
    m.keys(a, "core", "scale", [(0, 1), (1.8, 1.7), (2.0, 1)])
    for i, l in enumerate(lamps):
        for s in range(8):
            m.key(a, l, "scale", s * 0.25, 1.5 if (s + i) % 2 == 0 else 0.8)
    for t in (0.2, 0.7, 1.2, 1.7):
        m.fx(a, t, "grid_arc", "pad")
    m.sfx(a, 0.0, "overcharge.charge")

    a = m.anim("break", 1.5, "hold")
    m.keys(a, "core", "scale", [(0, 1), (0.2, 1.9), (0.35, 0.3), (1.5, 0.3)])
    m.keys(a, "core", "position", [(0, [0, 0, 0]), (0.35, [0, 0, 0]), (1.0, [0, -14, 0]), (1.5, [0, -14, 0])])
    m.keys(a, "cage", "rotation", [(0, [0, 0, 0]), (0.3, [0, 0, 0]), (0.9, [22, 0, -14]), (1.5, [24, 0, -16])])
    m.keys(a, "column", "rotation", [(0, [0, 0, 0]), (0.4, [0, 0, 0]), (1.2, [-7, 0, 5]), (1.5, [-7, 0, 5])])
    for l in lamps:
        m.keys(a, l, "scale", [(0, 1), (0.3, 1), (0.35, 0.01), (1.5, 0.01)])
    m.fx(a, 0.2, "shield", "core")
    m.fx(a, 0.25, "spark", "core")
    m.fx(a, 0.4, "smoke", "core")
    m.fx(a, 0.8, "smoke", "top")
    m.sfx(a, 0.2, "pylon.break")

    a = m.anim("offline", 3.0)
    m.keys(a, "core", "scale", [(0, 0.3), (3, 0.3)])
    m.keys(a, "core", "position", [(0, [0, -14, 0]), (3, [0, -14, 0])])
    m.keys(a, "cage", "rotation", [(0, [24, 0, -16]), (3, [24, 0, -16])])
    m.keys(a, "column", "rotation", [(0, [-7, 0, 5]), (3, [-7, 0, 5])])
    for l in lamps:
        m.keys(a, l, "scale", [(0, 0.01), (3, 0.01)])
    m.fx(a, 0.0, "smoke", "top")
    m.fx(a, 1.5, "smoke", "core")

    a = m.anim("rebuild", 1.0)      # scaffold lamps blink; the renderer scales 'core' by rebuild progress
    for i, l in enumerate(lamps):
        m.keys(a, l, "scale", [(0, 0.6 if i % 2 else 1.3), (0.5, 1.3 if i % 2 else 0.6), (1, 0.6 if i % 2 else 1.3)])
    m.keys(a, "core", "rotation", [(0, [0, 0, 0]), (1, [0, 180, 0])], "linear")
    m.fx(a, 0.0, "spark", "top")
    m.fx(a, 0.5, "ember", "core")
    m.sfx(a, 0.0, "pylon.rebuild")
    return m


# ============================================================ ordnance
def howitzer_shell():
    m = Model("howitzer_shell", seed=5)
    root = m.group("shell", (0, 4, 0))
    m.box(root, (0, 4, -1), [4, 4, 8], "brass")
    m.box(root, (0, 4, -6), [3, 3, 2], "steel_dark")
    m.box(root, (0, 4, -7.5), [1.6, 1.6, 1.2], "warning")
    m.box(root, (0, 4, 3.5), [4.4, 4.4, 1], "copper")
    fins = m.group("fins", (0, 4, 4), root)
    for k in range(4):
        m.box(fins, (0, 4, 5), [0.6, 7, 3], "steel", rot=[0, 0, k * 45])
    m.box(root, (0, 4, 5.6), [2.4, 2.4, 0.6], "amber")
    m.locator(root, "trail", (0, 4, 6))
    pfx(m, "smoke", "ember")
    a = m.anim("fly", 0.5)
    m.keys(a, "shell", "rotation", [(0, [0, 0, 0]), (0.5, [0, 0, 360])], "linear")
    m.fx(a, 0.0, "smoke", "trail")
    m.fx(a, 0.25, "ember", "trail")
    return m


def seeker_missile():
    m = Model("seeker_missile", seed=6)
    root = m.group("missile", (0, 3, 0))
    m.box(root, (0, 3, 0), [3, 3, 12], "plate")
    m.box(root, (0, 3, -6.8), [2.2, 2.2, 1.8], "warning")
    m.box(root, (0, 3, -2), [3.4, 3.4, 1], "hazard")
    fins = m.group("fins", (0, 3, 5), root)
    for k in range(2):
        m.box(fins, (0, 3, 5), [0.5, 7, 2.5], "steel_dark", rot=[0, 0, 45 + k * 90])
    m.box(root, (0, 3, 6.5), [2, 2, 1], "amber")
    m.locator(root, "exhaust", (0, 3, 7.5))
    pfx(m, "smoke", "ember")
    a = m.anim("fly", 0.4)
    m.keys(a, "missile", "rotation", [(0, [0, 0, 0]), (0.4, [0, 0, 180])], "linear")
    m.fx(a, 0.0, "smoke", "exhaust")
    m.fx(a, 0.2, "ember", "exhaust")
    return m


ALL = [overhead, generator_pylon, howitzer_shell, seeker_missile]
