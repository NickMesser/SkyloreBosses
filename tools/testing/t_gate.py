"""Damage gating test: an arm ignores damage while closed and takes it while its window is open."""
import json, re
from mar import call, cmd


def hp(sel):
    r = cmd(f"data get entity {sel} Health")
    m = re.search(r"([0-9.]+)f?$", (r.get("output") or [""])[0])
    return float(m.group(1)) if m else None


sel = "@e[type=skylore_bosses:nerve_arm,limit=1]"
print("nerve hp before", hp(sel))
print("window state", cmd(f"data get entity {sel} Window")["output"])
r = cmd(f"damage {sel} 20 minecraft:player_attack by @p")
print("damage while (probably) closed:", r["success"], r["output"], "hp", hp(sel))
print(cmd("skylorecalyx windows")["output"])
call("POST", "/wait", {"ticks": 30})
print("window state", cmd(f"data get entity {sel} Window")["output"])
r = cmd(f"damage {sel} 20 minecraft:player_attack by @p")
print("damage while open:", r["success"], r["output"], "hp", hp(sel))
print(cmd("skylorecalyx status")["output"])
