"""Overhead end-to-end test (DESIGN.md bosses/overhead): builds a yard in a fresh void world and walks the whole
fight through Marionette, asserting phase flow, the damage gate, pylon damage paths, re-arm (complete and aborted),
persistence, anti-soft-lock recovery, every attack, victory hooks and the abandon reset.
    set MARIONETTE_PORT=25590 (if your client is not on 25585), then: python t_overhead.py"""
import os, re, sys, time
from mar import call, cmd, wait

WORLD = "overhead_regress"
RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok)))
    print(("PASS " if ok else "FAIL ") + name + (f"  [{detail}]" if detail else ""), flush=True)


def status():
    return cmd("skyloreoverhead status")["output"][0]


def phase():
    return re.search(r"phase=(\S+)", status()).group(1)


def dr():
    return int(re.search(r"dr=(\d+)%", status()).group(1))


def pylons():
    return re.findall(r"(on|off|rb):(\d+)", re.search(r"pylons=\[(.*?)\]", status()).group(1))


def boss_hp():
    m = re.search(r"hp=([0-9.]+)/", status())
    return float(m.group(1)) if m else None


def hit(amount=20):
    a = boss_hp()
    cmd(f"damage @e[type=skylore_bosses:overhead,limit=1] {amount} minecraft:player_attack by @p")
    wait(12)
    return round(a - boss_hp(), 1)


def tp(x, y, z):
    call("POST", "/tp", {"x": x, "y": y, "z": z})


def serverconfig():
    run = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "run")
    # NeoForge 21.1 keeps server configs in config/ (older layouts: saves/<world>/serverconfig/)
    for p in (os.path.join(run, "config", "skylore_bosses-server.toml"),
              os.path.join(run, "saves", WORLD, "serverconfig", "skylore_bosses-server.toml")):
        if os.path.exists(p):
            return p
    return None


def set_config(key, value):
    p = serverconfig()
    s = open(p).read()
    s = re.sub(rf"(?m)^(\s*{key}\s*=\s*).*$", rf"\g<1>{value}", s)
    open(p, "w").write(s)
    time.sleep(3)   # NeoForge's config watcher reloads the file


def main():
    r = call("POST", "/world/create", {"type": "void", "name": WORLD, "gamemode": "survival", "delete_existing": True,
                                        "gamerules": {"doMobSpawning": False, "doImmediateRespawn": True, "doDaylightCycle": False,
                                                      "keepInventory": True, "naturalRegeneration": False},
                                        "setup_commands": ["fill -3 99 60 3 99 66 minecraft:stone", "tp @s 0 100 63",
                                                           "spawnpoint @s 0 100 63", "time set noon",
                                                           "effect give @s minecraft:resistance infinite 4 true",
                                                           "effect give @s minecraft:fire_resistance infinite 0 true",
                                                           "give @s minecraft:iron_sword"]})
    check("world created", r.get("ok"), r.get("error", ""))
    # ---------------------------------------------------------------- build + dormant
    cmd("skyloreoverhead build 0 100 0")
    wait(10)
    check("yard dormant after build", phase() == "dormant", phase())
    b = call("GET", "/block?x=-22&y=101&z=-22")
    check("pylon core placed + mirrored", b.get("id") == "skylore_bosses:generator_pylon" and b["blockEntity"]["Index"] == 0)
    check("console placed", call("GET", "/block?x=0&y=101&z=37").get("id") == "skylore_bosses:yard_console")
    check("gates open while dormant", call("GET", "/block?x=0&y=102&z=31").get("id") == "minecraft:air")
    # ---------------------------------------------------------------- P0 via walking in
    tp(0.5, 101, 26.5)
    wait(25)
    check("walking in starts P0", phase() == "p0_lockdown", phase())
    check("gates shut in P0", call("GET", "/block?x=0&y=102&z=31").get("id") == "skylore_bosses:yard_shutter")
    check("P0 chassis immune", hit() == 0 and dr() == 100)
    wait(160)
    check("P0 -> P1 after 160 ticks", phase() == "p1_shielded", phase())
    check("P1: all four pylons online", [p[0] for p in pylons()] == ["on"] * 4, str(pylons()))
    # ---------------------------------------------------------------- gate
    d = hit()
    check("P1 gate 90% DR (20 dmg -> ~2)", 1 <= d <= 3, d)
    # melee + projectile on pylon 0
    call("POST", "/hotbar", {"slot": 0})
    wait(30)
    before = int(pylons()[0][1])
    tp(-17.5, 101, -21.5)
    call("POST", "/look", {"x": -21, "y": 101.5, "z": -21.5})
    wait(30)
    tp(-17.5, 101, -21.5)
    call("POST", "/interact", {"action": "attack_block", "x": -22, "y": 101, "z": -22, "face": "east"})
    wait(2)
    after = int(pylons()[0][1])
    check("charged sword hit drains pylon integrity", 4 <= before - after <= 7, f"{before}->{after}")
    u = re.search(r"\[I; ?([-0-9, ]+)\]", cmd("data get entity @p UUID")["output"][0]).group(1)
    before = int(pylons()[0][1])
    cmd(f"summon minecraft:arrow -17.5 102.5 -21.5 {{Motion:[-1.5d,0.0d,0.0d],Owner:[I;{u}]}}")
    wait(10)
    after = int(pylons()[0][1])
    check("player arrow drains pylon integrity", before - after >= 5 or after - before >= 10, f"{before}->{after} (overcharge repair may interleave)")
    # ---------------------------------------------------------------- break -> P2 + window
    cmd("skyloreoverhead breakpylon 0")
    wait(3)
    check("break -> P2", phase() == "p2_degraded", phase())
    check("window opens (DR 0)", dr() == 0)
    d = hit()
    check("window: full damage", d >= 18, d)
    wait(200)
    check("window closes -> 3 pylons 85%", dr() == 85, dr())
    # ---------------------------------------------------------------- P3 exposed
    cmd("skyloreoverhead setpylons 0")
    wait(3)
    check("0 pylons -> P3", phase() == "p3_exposed", phase())
    d = hit()
    check("P3 full damage", d >= 18, d)
    check("demolition_crew awarded", "passed" in cmd("execute if entity @p[advancements={skylore_bosses:overhead/demolition_crew=true}]")["output"][0])
    # ---------------------------------------------------------------- P4 re-arm
    cmd("skyloreoverhead skipphase")
    wait(25)
    check("expose timeout -> P4", phase() == "p4_rearm", phase())
    check("P4 shield pulse immune", hit() == 0)
    wait(70)
    d = hit()
    check("P4 after pulse 50% DR", 8 <= d <= 11, d)
    rb = [p[0] for p in pylons()]
    check("first re-arm rebuilds two pylons, first-broken first", rb[:2] == ["rb", "rb"] and rb[2:] == ["off", "off"], str(rb))
    for _ in range(25):
        wait(20)
        if phase() != "p4_rearm":
            break
    check("rebuild completes -> P2 with 2 online", phase() == "p2_degraded" and dr() == 80, status().splitlines()[0])
    # ---------------------------------------------------------------- persistence
    snap = status().splitlines()[0]
    call("POST", "/world/leave", {}, timeout=120)
    call("POST", "/world/open", {"name": WORLD}, timeout=300)
    wait(40)
    now = status().splitlines()[0]
    same = re.search(r"phase=\S+", snap).group(0) == re.search(r"phase=\S+", now).group(0) and \
        re.search(r"pylons=\[.*?\]", snap).group(0) == re.search(r"pylons=\[.*?\]", now).group(0)
    check("state survives save/reload", same, now)
    cmd("effect give @p minecraft:resistance infinite 4 true")
    check("chassis present after reload", len([x for x in call("GET", "/entities?range=120&type=skylore_bosses:overhead")["entities"] if x["type"] == "skylore_bosses:overhead"]) >= 1)
    # ---------------------------------------------------------------- anti-soft-lock
    cmd("tp @e[type=skylore_bosses:overhead] 0 -200 0")
    wait(60)
    e = [x for x in call("GET", "/entities?range=120&type=skylore_bosses:overhead")["entities"] if x["type"] == "skylore_bosses:overhead"]
    check("chassis pushed out of the yard comes back", e and e[0]["y"] > 100, e[0]["y"] if e else None)
    cmd("setblock 22 101 -22 minecraft:air")
    wait(45)
    check("removed pylon column is re-placed", call("GET", "/block?x=22&y=101&z=-22").get("id") == "skylore_bosses:generator_pylon")
    tp(5.5, 80, 5.5)
    wait(30)
    pos = call("GET", "/state")["position"]
    check("void fall -> rescued to entry pad", abs(pos["z"] - 26.5) < 1.5 and pos["y"] > 100, pos)
    # ---------------------------------------------------------------- every attack
    for a in ["howitzer_lob", "missile_salvo", "laser_sweep", "strafe_barrage", "suppression_flare", "pylon_overcharge",
              "desperation_carpet", "flak_burst", "power_on_pulse", "rearm_shield_pulse"]:
        if a == "pylon_overcharge":
            tp(-20.5, 101, -17.5)
        elif a == "flak_burst":
            tp(0.5, 101, 20.5)
            cmd("effect give @p minecraft:levitation 3 5 true")
            wait(45)
        else:
            tp(0.5, 101, 20.5)
        out = cmd(f"skyloreoverhead attack {a}")["output"][0]
        seen = set()
        for _ in range(50):
            if a == "pylon_overcharge":
                tp(-20.5, 101, -17.5)
            wait(2)
            m = re.search(r"action=(\S+) stage=(\S+)", status())
            if m and m.group(1) == a:
                seen.add(m.group(2))
        # one-tick actives (pulses, overcharge) may fall between polls; a later stage proves the telegraph completed
        check(f"attack {a} runs telegraph->active", "TELEGRAPH" in seen and len(seen) >= 2, sorted(seen))
        wait(20)
    errs = call("GET", "/logs?level=ERROR&lines=20").get("lines", [])
    errs = [x for x in errs if "skylorebosses" in (x.get("message", "") + str(x.get("stack", ""))).lower()]
    check("no mod errors in the log", not errs, errs[:1])
    # ---------------------------------------------------------------- re-arm abort (config hot reload)
    if serverconfig():
        set_config("abortTicks", 150)
        cmd("skyloreoverhead setpylons 0")
        wait(3)
        cmd("skyloreoverhead skipphase")
        for _ in range(12):
            wait(20)
            if phase() == "p3_exposed":
                break
        check("re-arm aborts back to P3 when not finished in time", phase() == "p3_exposed", status().splitlines()[0])
        set_config("abortTicks", 1200)
    else:
        check("server config found for abort test", False)
    # ---------------------------------------------------------------- victory
    tp(0.5, 101, 20.5)
    cmd("skyloreoverhead setpylons 0")
    cmd("damage @e[type=skylore_bosses:overhead,limit=1] 5000 minecraft:player_attack by @p")
    wait(20)
    check("kill -> DEFEATED", phase() == "defeated", phase())
    inv = [s.get("id") for s in call("GET", "/inventory").get("main", []) if s.get("id")]
    check("trophy granted", "skylore_bosses:targeting_core" in inv)
    check("prototype_down advancement", "passed" in cmd("execute if entity @p[advancements={skylore_bosses:overhead/prototype_down=true}]")["output"][0])
    nr = cmd("execute if entity @p[advancements={skylore_bosses:overhead/no_rearm=true}]")
    check("no_rearm withheld after a re-arm", not nr["success"] or "passed" not in " ".join(nr["output"]), nr["output"])
    wait(170)
    check("DEFEATED -> CLEARED, gates open", phase() == "cleared" and call("GET", "/block?x=0&y=102&z=31").get("id") == "minecraft:air")
    # ---------------------------------------------------------------- abandon reset
    if serverconfig():
        set_config("dormantTimeoutTicks", 100)
        cmd("skyloreoverhead reset")
        tp(0.5, 101, 26.5)
        wait(40)
        tp(0.5, 100, 63.5)
        wait(160)
        check("empty yard resets to dormant", phase() == "dormant", phase())
        set_config("dormantTimeoutTicks", 1200)
    print(f"\n{sum(ok for _, ok in RESULTS)}/{len(RESULTS)} passed")
    return 0 if all(ok for _, ok in RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
