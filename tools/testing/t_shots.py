"""Spectator camera tour of the arena: one screenshot per arm + overview."""
import sys
from mar import call, cmd

ORIGIN = (0, 100, 0)
SAT = {"nerve": (0, 118, -120), "grasping": (104, 130, -60), "spitting": (104, 90, 60),
       "slam": (0, 76, 120), "charging": (-104, 100, 60), "mouth": (-104, 112, -60)}
prefix = sys.argv[1] if len(sys.argv) > 1 else "tour"
only = sys.argv[2:] or list(SAT) + ["overview"]

cmd("gamemode spectator")
cmd("time set noon")
cmd("effect give @s minecraft:night_vision infinite 0 true")
for name in only:
    if name == "overview":
        cmd(f"tp @s 60 200 170")
        call("POST", "/look", {"x": 0, "y": 100, "z": 0})
    else:
        x, y, z = SAT[name]
        # stand off toward the heart, slightly above, looking at the arm's mid-height
        dx, dz = -x, -z
        n = max(1.0, (dx * dx + dz * dz) ** 0.5)
        cx, cz = x + dx / n * 38, z + dz / n * 38
        cmd(f"tp @s {cx:.1f} {y + 16} {cz:.1f}")
        call("POST", "/look", {"x": x, "y": y + 10, "z": z})
    call("POST", "/wait", {"ticks": 30})
    r = call("POST", "/screenshot", {"name": f"{prefix}_{name}.png"})
    print(name, r.get("exists"))
