"""Phase-flow test: kill arms in design order (Nerve first) via skipphase, watch the heart split and the Bloom."""
import json, re, time
from mar import call, cmd, wait


def status():
    return cmd("skylorecalyx status")["output"][0]


cmd("gamemode creative")
cmd("tp @s 0 125 60")
call("POST", "/look", {"x": 0, "y": 118, "z": 0})
print(status())
wait(40)
call("POST", "/screenshot", {"name": "flow_heart_split.png"})
wait(170)
print(status())
call("POST", "/screenshot", {"name": "flow_bloom.png"})
adv = cmd("execute if entity @s[advancements={skylore_bosses:matris_calyx/nerve_first=true}]")
print("nerve_first:", adv["output"])
print("all_arms:", cmd("execute if entity @s[advancements={skylore_bosses:matris_calyx/all_arms=true}]")["output"])
