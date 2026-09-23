"""Poll arm debug state while a survival player stands in one arm's zone.  python t_debug_arm.py slam"""
import sys
from mar import call, cmd, wait
from t_arms_common import TESTS

name = sys.argv[1]
pos, look, extra, ticks = TESTS[name]
cmd("gamemode survival")
cmd("effect give @s minecraft:resistance 30 4 true")
cmd(f"tp @s {pos[0]} {pos[1]} {pos[2]}")
call("POST", "/look", {"x": look[0], "y": look[1], "z": look[2]})
for c in extra:
    cmd(c)
for i in range(ticks // 20):
    wait(20)
    out = cmd("skylorecalyx status")["output"][0].splitlines()
    line = [l for l in out if l.strip().startswith(name.upper())]
    print(i, call("GET", "/state").get("health"), line)
cmd("gamemode creative")
