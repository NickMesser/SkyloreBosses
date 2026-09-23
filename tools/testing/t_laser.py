"""Laser + victory test: survival player in the open on the heart island gets swept; then kill the Bloom."""
import json
from mar import call, cmd, wait


def health():
    return call("GET", "/state").get("health")


cmd("gamemode survival")
cmd("effect give @s minecraft:resistance 60 2 true")
cmd("tp @s 0 101 34")
call("POST", "/look", {"x": 0, "y": 150, "z": 0})
h0 = health()
hs = []
for i in range(12):
    wait(20)
    hs.append(health())
    if i == 5:
        call("POST", "/screenshot", {"name": "laser_1.png"})
print("health over 12 s:", h0, hs)
print(cmd("skylorecalyx status")["output"])
cmd("gamemode creative")
cmd("effect clear @s")
cmd("skylorecalyx skipphase")
wait(60)
call("POST", "/screenshot", {"name": "bloom_death.png"})
wait(120)
print(cmd("skylorecalyx status")["output"])
print("bloom_slain:", cmd("execute if entity @s[advancements={skylore_bosses:matris_calyx/bloom_slain=true}]")["output"])
print("calyx heart:", cmd("clear @s skylore_bosses:calyx_heart 0")["output"])
print(call("GET", "/messages").get("messages", [])[-4:])
