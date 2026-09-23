"""Create a void test world with a small platform south of where the arena anchor will be."""
import json, sys
from mar import call, cmd

name = sys.argv[1] if len(sys.argv) > 1 else "matris-test-1"
mode = sys.argv[2] if len(sys.argv) > 2 else "creative"
r = call("POST", "/world/create", {
    "type": "void", "name": name, "gamemode": mode, "delete_existing": True,
    "gamerules": {"doMobSpawning": False, "doDaylightCycle": False, "doImmediateRespawn": True},
    "setup_commands": ["fill -3 99 40 3 99 46 minecraft:stone", "tp @s 0 101 44", "spawnpoint @s 0 101 44", "time set midnight"]})
print(json.dumps(r)[:600])
