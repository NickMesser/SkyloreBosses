"""Proto-World entry test: /skylorecalyx tp builds the full arena (with dome) in skylore_bosses:proto_world."""
from mar import call, cmd, wait

r = call("POST", "/world/create", {"type": "void", "name": "matris-proto", "gamemode": "creative", "delete_existing": True,
                                   "gamerules": {"doMobSpawning": False, "doImmediateRespawn": True},
                                   "setup_commands": ["fill -2 -60 -2 2 -60 2 minecraft:stone", "tp @s 0 -59 0"]})
print("world", r.get("ok"))
print(cmd("skylorecalyx tp").get("output"))
o = "?"
for i in range(45):
    wait(20)
    out = cmd("execute in skylore_bosses:proto_world run skylorecalyx status").get("output") or ["?"]
    o = out[0].splitlines()[0]
    if "DORMANT" in o or "AWAKENING" in o or "LIMBS" in o:
        break
print(o)
st = call("GET", "/state")
print(st["dimension"], st["position"])
wait(260)
print(cmd("execute in skylore_bosses:proto_world run skylorecalyx status").get("output", ["?"])[0].splitlines()[0])
print("enter adv:", cmd("execute if entity @s[advancements={skylore_bosses:matris_calyx/enter_proto_world=true}]").get("output"))
cmd("gamemode spectator")
cmd("tp @s 150 260 260")
call("POST", "/look", {"x": 0, "y": 100, "z": 0})
wait(60)
call("POST", "/screenshot", {"name": "proto_overview.png"})
cmd("tp @s -40 135 -150")
call("POST", "/look", {"x": 0, "y": 125, "z": -120})
wait(40)
call("POST", "/screenshot", {"name": "proto_nerve.png"})
