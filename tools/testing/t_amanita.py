"""Amanita end-to-end test (DESIGN.md bosses/amanita): builds the test hollow in a fresh void world and walks the whole
fight through Marionette, asserting the dark-immunity gate against real block light, light placement and ownership,
braziers (igniters, Laevis bare hand), every snuff source, lamp-eaters and hollow-spawn, the P1 -> P2 snuff at half HP,
the P2 relight, the hush, P4 deep bloom (threshold, regen, force-open), Tenebris hollow call, persistence, every
attack, victory hooks and the abandon reset.
    set MARIONETTE_PORT=25590 (if your client is not on 25585), then: python t_amanita.py"""
import os, re, sys, time
from mar import call, cmd, wait

WORLD = "amanita_regress"
RESULTS = []
SHOTS = []
O = (0, 100, 0)            # hollow origin (floor centre); players stand at y 101


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok)))
    print(("PASS " if ok else "FAIL ") + name + (f"  [{detail}]" if detail != "" else ""), flush=True)


def status():
    out = cmd("skyloreamanita status").get("output") or [""]
    return out[0]


def field(pat, s=None, cast=str):
    m = re.search(pat, s if s is not None else status())
    return cast(m.group(1)) if m else None


def phase():
    return field(r"phase=(\S+)")


def lux():
    return field(r"lux=(\d+)", cast=int)


def exposed():
    return field(r"exposed=(\S+)") == "true"


def lights():
    return field(r"lights=(\d+)", cast=int)


def hp():
    return field(r"hp=([0-9.]+)/", cast=float)


def action():
    m = re.search(r"action=(\S+) stage=(\S+)", status())
    return (m.group(1), m.group(2)) if m else (None, None)


def hit(amount=20):
    a = hp()
    cmd(f"damage @e[type=skylore_bosses:amanita,limit=1] {amount} minecraft:player_attack by @p")
    wait(2)
    return round(a - hp(), 1)


def poll(pred, ticks, step=5):
    for _ in range(max(1, ticks // step)):
        if pred():
            return True
        wait(step)
    return pred()


def tp(x, y, z):
    for _ in range(3):
        cmd(f"tp @p {x} {y} {z}")
        wait(1)
        p = call("GET", "/state")["position"]
        if abs(p["x"] - x) < 1 and abs(p["z"] - z) < 1 and abs(p["y"] - y) < 1.5:
            return
    print("  (tp to", x, y, z, "landed at", p, ")")


def block(x, y, z):
    return call("GET", f"/block?x={x}&y={y}&z={z}")


def bid(x, y, z):
    return block(x, y, z).get("id")


def player_hp():
    return call("GET", "/state")["health"]


def heal_player():
    cmd("effect give @p minecraft:instant_health 1 10 true")
    wait(3)


def effects():
    return [e.get("id") for e in call("GET", "/state").get("effects", [])]


def adv(path):
    r = cmd(f"execute if entity @p[advancements={{skylore_bosses:amanita/{path}=true}}]")
    return r.get("success") and "passed" in " ".join(r["output"])


def slot_of(item):
    for s in call("GET", "/inventory").get("main", []):
        if s.get("id") == item:
            return s["slot"]
    return 0


def shot(name):
    r = call("POST", "/screenshot", {"name": name})
    if r.get("path"):
        SHOTS.append(r["path"])


def amanita_pos():
    e = [x for x in call("GET", "/entities?range=80&type=skylore_bosses:amanita")["entities"] if x["type"] == "skylore_bosses:amanita"]
    return e[0] if e else None


def count(type_):
    return len([x for x in call("GET", f"/entities?range=80&type={type_}")["entities"] if x["type"] == type_])


def ent_hp(uuid):
    e = call("GET", f"/entity?uuid={uuid}")
    return (e.get("nbt") or {}).get("Health", e.get("health"))


def msgs(n=20):
    return " ".join(m.get("text", "") for m in call("GET", "/messages?since=0").get("messages", [])[-n:])


def serverconfig():
    run = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "run")
    for p in (os.path.join(run, "saves", WORLD, "serverconfig", "skylore_bosses-server.toml"),
              os.path.join(run, "config", "skylore_bosses-server.toml")):
        if os.path.exists(p):
            return p
    return None


def set_config(key, value):
    p = serverconfig()
    s = open(p).read()
    s = re.sub(rf"(?m)^(\s*{key}\s*=\s*).*$", rf"\g<1>{value}", s)
    open(p, "w").write(s)
    time.sleep(3)   # NeoForge's config watcher reloads the file


def light_at_her(block_id="minecraft:glowstone"):
    """Put a light block right next to Amanita (east of her feet) and return where."""
    p = amanita_pos()
    x, y, z = int(p["x"] // 1) + 1, int(p["y"] // 1), int(p["z"] // 1)
    cmd(f"setblock {x} {y} {z} {block_id}")
    return x, y, z


def main():
    r = call("POST", "/world/create", {"type": "void", "name": WORLD, "gamemode": "survival", "delete_existing": True,
                                        "gamerules": {"doMobSpawning": False, "doImmediateRespawn": True, "doDaylightCycle": False,
                                                      "keepInventory": True, "naturalRegeneration": False},
                                        "setup_commands": ["difficulty normal", "fill -3 99 60 3 99 66 minecraft:stone", "tp @s 0 100 63",
                                                           "spawnpoint @s 0 100 63", "time set noon",
                                                           "attribute @s minecraft:generic.max_health base set 200",
                                                           "effect give @s minecraft:saturation infinite 0 true"]})
    check("world created", r.get("ok"), r.get("error", ""))
    wait(10)
    cmd("clear @p")
    cmd("give @p minecraft:torch 32")
    cmd("give @p minecraft:flint_and_steel")
    heal_player()
    # ---------------------------------------------------------------- build + dormant
    cmd("skyloreamanita build 0 100 0")
    wait(10)
    check("hollow dormant after build", phase() == "dormant", status())
    check("loam floor, hollow wall, stalk column", bid(0, 100, 0) == "skylore_bosses:hollow_loam" and bid(14, 104, 0) == "skylore_bosses:hollow_wall"
          and bid(8, 105, 8) == "skylore_bosses:hollow_column", (bid(0, 100, 0), bid(14, 104, 0), bid(8, 105, 8)))
    b = block(-12, 101, -6)
    check("brazier placed unlit", b.get("id") == "skylore_bosses:hollow_brazier" and b.get("properties", {}).get("lit") == "false", b)
    check("gill shelf cover placed", bid(4, 101, 5) == "skylore_bosses:gill_shelf")
    check("knocker outside the mouth", bid(0, 101, 18) == "skylore_bosses:hollow_knocker")
    check("mouth open while dormant", bid(0, 102, 14) == "minecraft:air")
    # ---------------------------------------------------------------- P0 by walking in
    tp(0.5, 101, 10.5)
    wait(25)
    check("walking in starts P0 bloom opens", phase() == "p0_bloom_opens", phase())
    check("mouth sealed with the membrane", bid(0, 102, 14) == "skylore_bosses:hollow_membrane")
    check("Amanita spawned on the bloom bed", amanita_pos() is not None and abs(amanita_pos()["y"] - 101) < 0.6, amanita_pos())
    check("the hollow starts dark: no light sources", lights() == 0, lights())
    check("P0 immune", hit() == 0)
    check("enter_hollow advancement", adv("enter_hollow"))
    check("P0 gate line on the action bar", "Nothing lands yet" in msgs(), msgs()[-160:])
    shot("amanita_p0")
    check("bloom_open scripted in P0", poll(lambda: action()[0] == "bloom_open", 140, 4))
    check("P0 -> P1 at 160 ticks", poll(lambda: phase() == "p1_dark_immunity", 120, 5), phase())
    check("P1 case-file line names the braziers", "braziers" in msgs(30), msgs(30)[-200:])
    # ---------------------------------------------------------------- the light gate (real light)
    cmd("skyloreamanita hold true")
    wait(5)
    s = status()
    check("P1 in the dark: closed, multiplier 0", field(r"exposed=(\S+)", s) == "false" and field(r"mult=([0-9.]+)", s) == "0.00" and (field(r"lux=(\d+)", s, int) or 0) < 7, s.splitlines()[0])
    d = hit(20)
    check("20 damage in the dark lands 0", d == 0, d)
    check("dark gate explains itself (light N of 7)", "Bring light to her" in msgs(), msgs()[-200:])
    lx = light_at_her()
    wait(4)
    s = status()
    check("glowstone beside her raises the light at her to 7+", (field(r"lux=(\d+)", s, int) or 0) >= 7, s.splitlines()[0])
    check("lit: exposed, multiplier >= 1", field(r"exposed=(\S+)", s) == "true" and float(field(r"mult=([0-9.]+)", s)) >= 1.0, s.splitlines()[0])
    d = hit(20)
    check("20 damage in light lands (~17-25 after armour and the bright bonus)", 14 <= d <= 26, d)
    check("first_light advancement", adv("first_light"))
    cmd(f"setblock {lx[0]} {lx[1]} {lx[2]} minecraft:air")
    check("light removed -> closed again (after the afterglow)", poll(lambda: not exposed(), 30, 2), status().splitlines()[0])
    cmd("skyloreamanita setlight 12")
    check("setlight override exposes her", exposed() and lux() == 12)
    cmd("skyloreamanita setlight -1")
    check("setlight -1 restores real light", not poll(exposed, 20, 2) or lux() < 7, status().splitlines()[0])
    # ---------------------------------------------------------------- placement + ownership
    tp(-9.5, 101, 3.5)
    call("POST", "/hotbar", {"slot": slot_of("minecraft:torch")})
    wait(3)
    call("POST", "/look", {"x": -10.5, "y": 100.5, "z": 3.5})
    call("POST", "/interact", {"action": "use_block", "x": -11, "y": 100, "z": 3, "face": "up"})
    wait(25)
    check("placed torch is counted in the census", (lights() or 0) >= 1 and bid(-11, 101, 3) == "minecraft:torch", (lights(), bid(-11, 101, 3)))
    lc = cmd("skyloreamanita lights")["output"][0]
    check("the census records who placed it", "@" in lc, lc)
    # ---------------------------------------------------------------- braziers
    tp(-10.5, 101, -5.5)
    call("POST", "/hotbar", {"slot": 8})   # empty slot
    wait(3)
    call("POST", "/look", {"x": -11.5, "y": 101.5, "z": -5.5})
    call("POST", "/interact", {"action": "use_block", "x": -12, "y": 101, "z": -6, "face": "east"})
    wait(4)
    check("bare hand does not light a brazier (not Laevis)", block(-12, 101, -6).get("properties", {}).get("lit") == "false")
    check("brazier says what it needs", "needs a flame" in msgs(), msgs()[-160:])
    cmd("tag @p add skylore_origin_laevis")
    call("POST", "/interact", {"action": "use_block", "x": -12, "y": 101, "z": -6, "face": "east"})
    wait(4)
    check("Laevis lights a brazier bare-handed", block(-12, 101, -6).get("properties", {}).get("lit") == "true")
    cmd("tag @p remove skylore_origin_laevis")
    call("POST", "/hotbar", {"slot": slot_of("minecraft:flint_and_steel")})
    wait(3)
    tp(-10.5, 101, 5.5)
    call("POST", "/look", {"x": -11.5, "y": 101.5, "z": 6.5})
    call("POST", "/interact", {"action": "use_block", "x": -12, "y": 101, "z": 6, "face": "east"})
    wait(4)
    check("flint and steel lights a brazier", block(-12, 101, 6).get("properties", {}).get("lit") == "true")
    wait(25)
    check("lit braziers are light sources (census 3)", (lights() or 0) >= 3, lights())
    # ---------------------------------------------------------------- snuff_pulse
    tp(-5.5, 101, 0.5)
    cmd("tp @e[type=skylore_bosses:amanita] -11 101 0")
    wait(3)
    cmd("skyloreamanita attack snuff_pulse")
    ok = poll(lambda: action()[0] != "snuff_pulse", 90, 5)
    wait(25)
    check("snuff_pulse puts out braziers and breaks torches within 8", block(-12, 101, -6).get("properties", {}).get("lit") == "false"
          and block(-12, 101, 6).get("properties", {}).get("lit") == "false" and bid(-11, 101, 3) == "minecraft:air", (ok, lights()))
    check("snuffed torch drops as an item (torch economy)", count("minecraft:item") >= 1)
    check("snuff gives Darkness", "minecraft:darkness" in effects(), effects())
    # ---------------------------------------------------------------- lamp-eater
    cmd("kill @e[type=minecraft:item]")
    cmd("setblock 0 101 -9 minecraft:torch")
    cmd("skyloreamanita spawnservants eater")
    wait(5)
    check("lamp-eater spawns at a vent", count("skylore_bosses:lamp_eater") == 1)
    eaten = poll(lambda: bid(0, 101, -9) != "minecraft:torch", 400, 10)
    check("lamp-eater walks to the torch and eats it", eaten and "eaten=1" in status(), status().splitlines()[0])
    cmd("setblock 5 106 -9 minecraft:glowstone")
    wait(120)
    check("lights hung high are safe from lamp-eaters", bid(5, 106, -9) == "minecraft:glowstone")
    cmd("setblock 5 106 -9 minecraft:air")
    cmd("kill @e[type=skylore_bosses:lamp_eater]")
    # ---------------------------------------------------------------- hollow-spawn burns in light
    cmd("skyloreamanita spawnservants spawn")
    wait(30)
    sp = [x for x in call("GET", "/entities?range=80&type=skylore_bosses:hollow_spawn")["entities"] if x["type"] == "skylore_bosses:hollow_spawn"]
    check("hollow-spawn spawns", len(sp) == 1)
    if sp:
        cmd("tp @e[type=skylore_bosses:hollow_spawn] 0 101 6")
        cmd("setblock 1 101 6 minecraft:glowstone")
        cmd("effect give @e[type=skylore_bosses:hollow_spawn] minecraft:slowness 10 10 true")
        a0 = ent_hp(sp[0]["uuid"])
        wait(45)
        a1 = ent_hp(sp[0]["uuid"]) if count("skylore_bosses:hollow_spawn") else 0
        check("hollow-spawn burns in bright light", a1 is not None and a0 is not None and a1 < a0, (a0, a1))
        cmd("setblock 1 101 6 minecraft:air")
    cmd("kill @e[type=skylore_bosses:hollow_spawn]")
    heal_player()
    # ---------------------------------------------------------------- P1 -> P2 full snuff at half HP
    cmd("skyloreamanita hold false")
    for (x, z) in ((-12, -6), (12, -6), (12, 6)):
        cmd(f"setblock {x} 101 {z} skylore_bosses:hollow_brazier[lit=true]")
    cmd("skyloreamanita setlight 15")
    cmd("damage @e[type=skylore_bosses:amanita,limit=1] 5000 minecraft:player_attack by @p")
    wait(3)
    s = status()
    check("a huge lit hit is clamped at exactly 50% HP and triggers P2", phase() == "p2_full_snuff" and abs(hp() - 300) < 0.5, s)
    cmd("skyloreamanita setlight -1")
    check("full snuff is cast (sealed: immune while closing)", action()[0] == "full_snuff" and hit(20) == 0, status())
    done = poll(lambda: action()[0] != "full_snuff", 120, 5)
    wait(25)
    check("full snuff lands: every brazier out, census 0", done and lights() == 0 and block(12, 101, 6).get("properties", {}).get("lit") == "false", status().splitlines()[0])
    check("full snuff: Darkness on the player", "minecraft:darkness" in effects(), effects())
    check("relight pressure: hollow-spawn and a lamp-eater climb out", count("skylore_bosses:hollow_spawn") >= 1 and count("skylore_bosses:lamp_eater") >= 1,
          (count("skylore_bosses:hollow_spawn"), count("skylore_bosses:lamp_eater")))
    shot("amanita_p2_snuffed")
    cmd("kill @e[type=skylore_bosses:lamp_eater]")
    cmd("kill @e[type=skylore_bosses:hollow_spawn]")
    # ---------------------------------------------------------------- hush
    cmd("skyloreamanita attack darkness_howl")
    poll(lambda: action() == ("darkness_howl", "RECOVERY") or int(field(r"hush=(\d+)") or 0) > 0, 60, 2)
    tp(0.5, 101, 10.5)
    call("POST", "/hotbar", {"slot": slot_of("minecraft:torch")})
    wait(1)
    call("POST", "/look", {"x": 0.5, "y": 100.5, "z": 9.5})
    call("POST", "/interact", {"action": "use_block", "x": 0, "y": 100, "z": 9, "face": "up"})
    wait(3)
    check("darkness_howl hushes: a torch cannot be placed", bid(0, 101, 9) != "minecraft:torch", status().splitlines()[0])
    # ---------------------------------------------------------------- P2 -> P3 relight
    wait(60)
    for (x, z) in ((-12, -6), (-12, 6), (12, -6)):
        cmd(f"setblock {x} 101 {z} skylore_bosses:hollow_brazier[lit=true]")
    check("three lights burning again -> P3 lit duel", poll(lambda: phase() == "p3_lit_duel", 60, 5), status().splitlines()[0])
    check("relit advancement", adv("relit"))
    # ---------------------------------------------------------------- persistence
    snap = status().splitlines()[0]
    call("POST", "/world/leave", {}, timeout=120)
    call("POST", "/world/open", {"name": WORLD}, timeout=300)
    wait(40)
    now = status().splitlines()[0]
    same = re.search(r"phase=\S+", snap).group(0) == re.search(r"phase=\S+", now).group(0) and "snuffed=true" in now
    check("fight state survives save/reload", same, now)
    check("Amanita present after reload with her HP", amanita_pos() is not None and abs((hp() or 0) - 300) < 1, hp())
    heal_player()
    # ---------------------------------------------------------------- P4 deep bloom
    cmd("skyloreamanita snuff")
    cmd("skyloreamanita skipphase")
    wait(5)
    s = status()
    check("P3 -> P4 deep bloom, threshold 10", phase() == "p4_deep_bloom" and "threshold=10" in s, s.splitlines()[0])
    check("deep_bloom advancement", adv("deep_bloom"))
    poll(lambda: action()[0] != "deep_bloom", 80, 5)
    cmd("skyloreamanita setlight 8")
    wait(3)
    check("light 8 no longer exposes her in deep bloom", not exposed(), status().splitlines()[0])
    cmd("skyloreamanita setlight 0")
    cmd("damage @e[type=skylore_bosses:amanita,limit=1] 60 minecraft:out_of_world")
    wait(2)
    h0 = hp()
    wait(60)
    check("closed in deep bloom she regrows (capped below the snuff threshold)", hp() > h0 and hp() <= 300.5, (h0, hp()))
    cmd("skyloreamanita setlight 12")
    forced = poll(lambda: phase() == "p3_lit_duel", 140, 5)
    check("80 ticks of light 10+ force the bloom open -> P3", forced, status().splitlines()[0])
    check("forced_open advancement", adv("forced_open"))
    d = hit(20)
    check("bared: extra damage (~25+)", d >= 23, d)
    cmd("skyloreamanita setlight -1")
    heal_player()
    # ---------------------------------------------------------------- Tenebris hollow call
    cmd("skyloreamanita snuff")
    cmd("skyloreamanita hold true")
    cmd("tag @p add skylore_origin_tenebris")
    p = amanita_pos()
    tp(round(p["x"]) + 0.5, 101, round(p["z"]) + 4.5)
    rooted = poll(lambda: "minecraft:slowness" in effects(), 160, 10)
    check("Tenebris in the dark near her: the hollow calls, then roots", rooted and "The hollow calls you" in msgs(40), effects())
    cmd("tag @p remove skylore_origin_tenebris")
    cmd("skyloreamanita hold false")
    # ---------------------------------------------------------------- anti-soft-lock
    cmd("tp @e[type=skylore_bosses:amanita] 0 150 60")
    wait(10)
    p = amanita_pos()
    check("Amanita carried out of the hollow rises on the bloom bed", p is not None and abs(p["x"]) < 2 and abs(p["z"] + 2) < 2, p)
    tp(5.5, 94, 5.5)
    wait(10)
    pos = call("GET", "/state")["position"]
    check("falling out of the hollow -> rescued to the entry pad", abs(pos["z"] - 12.5) < 1.5 and pos["y"] > 100, pos)
    cmd("setblock 4 101 5 minecraft:air")
    # ---------------------------------------------------------------- every attack
    heal_player()
    cmd("skyloreamanita hold true")
    cmd("kill @e[type=skylore_bosses:lamp_eater]")
    cmd("kill @e[type=skylore_bosses:hollow_spawn]")
    cmd("setblock 0 101 -9 minecraft:torch")
    cmd("setblock -12 101 -6 skylore_bosses:hollow_brazier[lit=true]")
    measured = {}
    for a in ["bloom_open", "shadow_lash", "hollow_bolt", "spore_veil", "snuff_pulse", "darkness_howl", "bloom_slam",
              "light_seeker_dash", "deep_bloom", "full_snuff"]:
        heal_player()
        cmd("tp @e[type=skylore_bosses:amanita] 0 101 0")
        cmd("setblock 0 101 -9 minecraft:torch")
        wait(2)
        where = {"shadow_lash": (0.5, 101, 2.5), "bloom_slam": (0.5, 101, 5.0), "bloom_open": (0.5, 101, 5.0)}.get(a, (0.5, 101, 10.5))
        tp(*where)
        before = player_hp()
        cmd("kill @e[type=skylore_bosses:lamp_eater]")
        cmd("kill @e[type=skylore_bosses:hollow_spawn]")
        wait(20)
        before = player_hp()
        cmd(f"skyloreamanita attack {a}")
        seen = set()
        for _ in range(80):
            if a == "shadow_lash":
                tp(*where)
            wait(2)
            act, st = action()
            if act == a:
                seen.add(st)
            elif seen and act != a:
                break
        wait(10)
        measured[a] = round(before - player_hp(), 1)
        check(f"attack {a} runs telegraph -> active", "TELEGRAPH" in seen and len(seen) >= 2, f"{sorted(seen)} took {measured[a]}")
    check("shadow_lash in P3 lashes twice (~7 + ~7)", 11 <= measured["shadow_lash"] <= 15, measured["shadow_lash"])
    check("bloom_open deals no damage", measured["bloom_open"] <= 0.5, measured["bloom_open"])
    shot("amanita_attacks")
    errs = call("GET", "/logs?level=ERROR&lines=60").get("lines", [])
    errs = [x for x in errs if "skylorebosses" in (x.get("message", "") + str(x.get("stack", ""))).lower()]
    check("no mod errors in the log", not errs, errs[:1])
    cmd("skyloreamanita hold false")
    # ---------------------------------------------------------------- victory
    cmd("skyloreamanita setlight 15")
    wait(3)
    cmd("damage @e[type=skylore_bosses:amanita,limit=1] 5000 minecraft:player_attack by @p")
    wait(20)
    check("kill while lit -> DEFEATED", phase() == "defeated", phase())
    inv = [s.get("id") for s in call("GET", "/inventory").get("main", []) if s.get("id")]
    check("trophy granted (hollow_bloom_cap)", "skylore_bosses:hollow_bloom_cap" in inv)
    check("artifact loot table rolled (placeholder ink sacs)", "minecraft:ink_sac" in inv, inv)
    check("bloom_wilted advancement", adv("bloom_wilted"))
    check("no_deep_bloom withheld after a deep bloom", not adv("no_deep_bloom"))
    check("adds sink away on victory", poll(lambda: count("skylore_bosses:lamp_eater") + count("skylore_bosses:hollow_spawn") == 0, 60, 10))
    wait(40)
    shot("amanita_death")
    wait(120)
    check("DEFEATED -> CLEARED, mouth open", phase() == "cleared" and bid(0, 102, 14) == "minecraft:air", phase())
    cmd("skyloreamanita setlight -1")
    # ---------------------------------------------------------------- abandon reset
    if serverconfig():
        set_config("dormantTimeoutTicks", 100)
        cmd("skyloreamanita reset")
        tp(0.5, 101, 10.5)
        wait(40)
        check("restart after reset", phase() == "p0_bloom_opens", phase())
        tp(0.5, 100, 63.5)
        wait(160)
        check("empty hollow resets to dormant", phase() == "dormant", phase())
        check("reset restores braziers unlit", block(-12, 101, -6).get("properties", {}).get("lit") == "false")
        set_config("dormantTimeoutTicks", 1200)
    else:
        check("server config found for the abandon test", False)
    print("screenshots:", *SHOTS, sep="\n  ")
    print(f"\n{sum(ok for _, ok in RESULTS)}/{len(RESULTS)} passed")
    return 0 if all(ok for _, ok in RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
