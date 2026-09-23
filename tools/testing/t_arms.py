"""Per-arm behaviour test on a fresh arena: put a survival player in each arm's threat zone and watch
health, effects and passenger state. Screenshots mid-attack go to run/screenshots/arm_<name>.png."""
import json, sys, time
from mar import call, cmd, wait

from t_arms_common import TESTS


def state():
    return call("GET", "/state")


def main():
    call("POST", "/world/create", {"type": "void", "name": "matris-arms", "gamemode": "creative", "delete_existing": True,
                                   "gamerules": {"doMobSpawning": False, "doDaylightCycle": False, "doImmediateRespawn": True},
                                   "setup_commands": ["fill -3 99 40 3 99 46 minecraft:stone", "tp @s 0 101 44", "time set noon",
                                                      "effect give @s minecraft:night_vision infinite 0 true"]})
    print(cmd("skylorecalyx start 0 100 0 nodome")["output"])
    for _ in range(40):
        wait(20)
        s = cmd("skylorecalyx status")["output"][0]
        if "LIMBS" in s:
            break
    print(s)
    names = sys.argv[1:] or list(TESTS)
    for name in names:
        pos, look, extra, ticks = TESTS[name]
        cmd("gamemode survival")
        cmd("effect clear @s")
        cmd("effect give @s minecraft:night_vision infinite 0 true")
        cmd("effect give @s minecraft:resistance 30 2 true")
        cmd("effect give @s minecraft:regeneration 30 1 true")
        cmd("effect give @s minecraft:saturation 30 1 true")
        cmd(f"tp @s {pos[0]} {pos[1]} {pos[2]}")
        call("POST", "/look", {"x": look[0], "y": look[1], "z": look[2]})
        for c in extra:
            cmd(c)
        h0 = state().get("health")
        low, riding, shot = h0, False, False
        for t in range(0, ticks, 10):
            wait(10)
            st = state()
            low = min(low, st.get("health", low))
            ent = call("GET", "/entity?uuid=self") if False else None
            if st.get("health", h0) < h0 - 0.5 and not shot:
                call("POST", "/screenshot", {"name": f"arm_{name}.png"})
                shot = True
        if not shot:
            call("POST", "/screenshot", {"name": f"arm_{name}.png"})
        st = state()
        print(f"{name}: health {h0} -> min {low}, effects {[e['id'] for e in st.get('effects', [])]}, pos {st['position']}")
        cmd("gamemode creative")
        cmd("tp @s 0 101 44")
        wait(20)
    print(call("GET", "/messages").get("messages", [])[-8:])


main()
