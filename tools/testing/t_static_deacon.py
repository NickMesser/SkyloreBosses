"""Static Deacon end-to-end test (DESIGN.md bosses/static_deacon): builds the test undercroft in a fresh void world and
walks the whole fight through Marionette, asserting phase flow, the communion heal/damage gate, all three player verbs
that desecrate the floor (mining, covering, explosions) plus the catch-all scan, the Deacon's stance on live flagstones,
stumbles, the plinth vigil and its break, the reseed rite and denial, persistence, anti-soft-lock recovery, every
attack (with measured damage where it is deterministic), victory hooks and the abandon reset.
    set MARIONETTE_PORT=25590 (if your client is not on 25585), then: python t_static_deacon.py"""
import os, re, sys, time
from mar import call, cmd, wait

WORLD = "deacon_regress"
RESULTS = []
SHOTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok)))
    print(("PASS " if ok else "FAIL ") + name + (f"  [{detail}]" if detail != "" else ""), flush=True)


def status():
    return cmd("skyloredeacon status")["output"][0]


def field(pat, s=None, cast=str):
    m = re.search(pat, s or status())
    return cast(m.group(1)) if m else None


def phase():
    return field(r"phase=(\S+)")


def floor():
    return field(r"floor=(\d+)/", cast=int)


def commune():
    return field(r"commune=(\S+)")


def dr():
    return field(r"dr=(\d+)%", cast=int)


def hp():
    return field(r"hp=([0-9.]+)/", cast=float)


def action():
    m = re.search(r"action=(\S+) stage=(\S+)", status())
    return (m.group(1), m.group(2)) if m else (None, None)


def hit(amount=20):
    a = hp()
    cmd(f"damage @e[type=skylore_bosses:static_deacon,limit=1] {amount} minecraft:player_attack by @p")
    wait(2)
    return round(a - hp(), 1)


def poll(pred, ticks, step=5):
    for _ in range(max(1, ticks // step)):
        if pred():
            return True
        wait(step)
    return pred()


def tp(x, y, z):
    """Server-side teleport, verified: the client-side /tp occasionally left the player on the crypt roof."""
    for _ in range(3):
        cmd(f"tp @p {x} {y} {z}")
        wait(1)
        p = call("GET", "/state")["position"]
        if abs(p["x"] - x) < 1 and abs(p["z"] - z) < 1 and abs(p["y"] - y) < 1.5:
            return
    print("  (tp to", x, y, z, "landed at", p, ")")


def block(x, y, z):
    return call("GET", f"/block?x={x}&y={y}&z={z}")


def player_hp():
    return call("GET", "/state")["health"]


def heal_player():
    cmd("effect give @p minecraft:instant_health 1 10 true")
    wait(3)


def adv(path):
    r = cmd(f"execute if entity @p[advancements={{skylore_bosses:static_deacon/{path}=true}}]")
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


def deacon_pos():
    e = [x for x in call("GET", "/entities?range=80&type=skylore_bosses:static_deacon")["entities"] if x["type"] == "skylore_bosses:static_deacon"]
    return e[0] if e else None


def serverconfig():
    run = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "run")
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
                                        "setup_commands": ["difficulty normal", "fill -3 99 60 3 99 66 minecraft:stone", "tp @s 0 100 63",
                                                           "spawnpoint @s 0 100 63", "time set noon",
                                                           "attribute @s minecraft:generic.max_health base set 200",
                                                           "effect give @s minecraft:saturation infinite 0 true"]})
    check("world created", r.get("ok"), r.get("error", ""))
    wait(10)
    cmd("clear @p")
    cmd("give @p minecraft:diamond_pickaxe")
    cmd("give @p minecraft:cobblestone 16")
    heal_player()
    # ---------------------------------------------------------------- build + dormant
    out = cmd("skyloredeacon build 0 100 0")["output"]
    wait(10)
    check("crypt dormant after build", phase() == "dormant", status())
    check("altar plinth placed (3x3x2)", block(0, 100, 0).get("id") == "skylore_bosses:altar_plinth" and block(1, 101, -1).get("id") == "skylore_bosses:altar_plinth")
    b = block(0, 100, 3)
    check("nave flagstone is lit consecrated endstone", b.get("id") == "skylore_bosses:consecrated_endstone" and b.get("properties", {}).get("lit") == "true", b.get("properties"))
    check("pillar and pew placed", block(6, 104, 6).get("id") == "skylore_bosses:crypt_pillar" and block(9, 101, 3).get("id") == "skylore_bosses:crypt_pew")
    check("bell placed outside the door", block(0, 101, 21).get("id") == "skylore_bosses:sacristy_bell")
    check("door open while dormant", block(0, 102, 17).get("id") == "minecraft:air")
    check("48 nave flagstones", floor() == 48, floor())
    # ---------------------------------------------------------------- P0 by walking in
    tp(0.5, 101, 14.5)
    wait(25)
    check("walking in starts P0 vesting", phase() == "p0_vesting", phase())
    check("door sealed with the grate", block(0, 102, 17).get("id") == "skylore_bosses:crypt_grate")
    check("Deacon spawned on the plinth", deacon_pos() is not None and abs(deacon_pos()["y"] - 102) < 0.6, deacon_pos())
    check("P0 Deacon immune", hit() == 0 and dr() == 100)
    check("enter_crypt advancement", adv("enter_crypt"))
    shot("deacon_p0_vesting")
    chime = poll(lambda: action()[0] == "vesting_chime", 160, 5)
    check("vesting chime scripted in P0", chime)
    check("P0 -> P1 at 200 ticks", poll(lambda: phase() == "p1_communion", 120, 5), phase())
    # ---------------------------------------------------------------- communion gate
    check("Deacon walks onto a live flagstone", poll(lambda: commune() == "nave", 200, 5), status())
    s = status()
    check("full-nave communion: 50% DR", field(r"dr=(\d+)%", s, int) == 50, s.splitlines()[0])
    reg = field(r"regen=([0-9.]+)/s", s, float)
    check("full-nave communion: ~32 HP/s regen (4% of 800)", reg is not None and 31 <= reg <= 33, reg)
    cmd("damage @e[type=skylore_bosses:static_deacon,limit=1] 100 minecraft:player_attack by @p")   # off full HP so regen cannot hide the hit
    wait(12)
    samples = []
    for _ in range(3):   # a 10-tick regen pulse can land inside the 2-tick read; keep the cleanest of three
        samples.append(hit(20))
        wait(12)
    d = max(samples)
    check("20 damage in communion lands ~half (then armour)", 8 <= d <= 11, d)
    cmd("damage @e[type=skylore_bosses:static_deacon,limit=1] 300 minecraft:player_attack by @p")
    wait(2)
    a = hp()
    wait(40)
    gained = hp() - a
    check("floor heals it: 64-80 HP over 40 ticks (16 per 10-tick pulse)", 48 <= gained <= 82 or (hp() >= 799), round(gained, 1))
    shot("deacon_p1_communion")
    # ---------------------------------------------------------------- the three verbs
    tp(0.5, 101, 14.5)
    call("POST", "/hotbar", {"slot": slot_of("minecraft:diamond_pickaxe")})
    wait(5)
    r = call("POST", "/interact", {"action": "break_block", "x": -2, "y": 100, "z": 15, "face": "up"})
    wait(3)
    check("mining one block desecrates the whole flagstone", r.get("broken") and block(-3, 100, 15).get("id") == "skylore_bosses:desecrated_endstone" and floor() == 47,
          f"{r.get('broken')} {block(-3, 100, 15).get('id')} {floor()}")
    check("first_flagstone advancement", adv("first_flagstone"))
    msgs = " ".join(m.get("text", "") for m in call("GET", "/messages?since=0").get("messages", [])[-15:])
    check("desecration feedback on the action bar", "Flagstone desecrated" in msgs, msgs[-200:])
    call("POST", "/hotbar", {"slot": slot_of("minecraft:cobblestone")})
    wait(3)
    tp(0.5, 101, 14.5)
    call("POST", "/look", {"x": -2, "y": 100.5, "z": 12})
    call("POST", "/interact", {"action": "use_block", "x": -2, "y": 100, "z": 12, "face": "up"})
    wait(3)
    check("covering a flagstone with a block desecrates it", block(-2, 101, 12).get("id") == "minecraft:cobblestone" and floor() == 46, floor())
    cmd("setblock 3 100 9 minecraft:air")
    wait(12)
    check("floor scan catches blocks removed any other way", floor() == 45, floor())
    cmd("summon minecraft:tnt 0 101 -12 {fuse:1}")
    wait(10)
    check("explosions desecrate flagstones", floor() <= 44, floor())
    tp(0.5, 101, 14.5)
    cmd("setblock -3 100 15 minecraft:end_stone")
    # ---------------------------------------------------------------- thresholds
    cmd("skyloredeacon setfloor 34")
    check("34/48 (70.8%) stays P1", phase() == "p1_communion", phase())
    cmd("skyloredeacon setfloor 33")
    check("33/48 (68.8%) -> P2 patchwork", phase() == "p2_patchwork", phase())
    cmd("skyloredeacon setfloor 8")
    check("patchwork: Deacon repositions onto a remaining flagstone", poll(lambda: commune() == "nave", 300, 10), status())
    s = status()
    check("DR/regen scale with coverage (8/48 -> 8%, 5.3 HP/s)", field(r"dr=(\d+)%", s, int) == 8 and 4.5 <= field(r"regen=([0-9.]+)/s", s, float) <= 6, s.splitlines()[0])
    p = deacon_pos()
    bx, bz = int(p["x"] // 1), int(p["z"] // 1)
    cmd(f"setblock {bx} 100 {bz} minecraft:air")
    stumbled = poll(lambda: commune() != "nave" and (int(field(r"stagger=(\d+)") or 0) > 0 or "stranded" in status()), 20, 2)
    check("stripping the flagstone under it strands it (stumble)", stumbled, status())
    d = hit(20)
    check("stranded: full damage", d >= 18, d)
    # ---------------------------------------------------------------- P3 vigil
    heal_player()
    cmd("skyloredeacon setfloor 0")
    check("no flagstones left -> P3 vigil", phase() == "p3_vigil", phase())
    check("stranded advancement (before any reseed)", adv("stranded"))
    check("Deacon returns to the plinth", poll(lambda: commune() == "plinth", 400, 10), status())
    check("vigil: 25% DR", dr() == 25)
    d = hit(20)
    check("vigil hit ~14 (25% DR, armour, minus regen tick)", 11 <= d <= 16, d)
    shot("deacon_p3_vigil")
    cmd("damage @e[type=skylore_bosses:static_deacon,limit=1] 60 minecraft:player_attack by @p")
    wait(12)   # past the 10-tick invulnerability window
    cmd("damage @e[type=skylore_bosses:static_deacon,limit=1] 60 minecraft:player_attack by @p")
    wait(5)
    s = status()
    check("burst on the altar breaks the vigil", "vigilBreaks=1" in s and int(field(r"plinthLock=(\d+)", s)) > 0, s)
    check("vigil_broken advancement", adv("vigil_broken"))
    wait(15)
    d = hit(20)
    check("desecrated after the break: +25% (~24)", 22 <= d <= 26, d)
    check("walks back after the plinth lock", poll(lambda: commune() == "plinth", 300, 10), status())
    # ---------------------------------------------------------------- P4 reseed
    cmd("skyloredeacon skipphase")
    wait(5)
    s = status()
    check("P3 clock ends -> P4 reseed (target 12)", phase() == "p4_reseed" and "reseeds=1" in s and "target=12" in s, s.splitlines()[0])
    check("rite channels", poll(lambda: action() == ("reseed_rite", "ACTIVE"), 120, 4), status())
    check("rite DR 60%", dr() == 60)
    grew = poll(lambda: "reseeding=" in status() and int(field(r"reseeding=(\d+)")) > 0, 60, 2)
    check("flagstones regrow from the plinth", grew)
    # deny one while it grows: find a reseeding flagstone (unlit consecrated endstone) and cover it
    denied = False
    for cx, cz in [(a, b) for a in range(-3, 4) for b in range(-5, 6) if max(abs(a), abs(b)) <= 2 and (a, b) != (0, 0)]:
        x, z = 3 * cx, 3 * cz
        bb = block(x, 100, z)
        if bb.get("id") == "skylore_bosses:consecrated_endstone" and bb.get("properties", {}).get("lit") == "false":
            cmd(f"setblock {x} 101 {z} minecraft:cobblestone")
            wait(12)
            denied = "denied=1" in status() or "denied=2" in status()
            break
    check("covering a reseeding flagstone denies it", denied, status())
    check("rite restores 12 flagstones -> P2", poll(lambda: phase() in ("p2_patchwork", "p1_communion"), 600, 10), status().splitlines()[0])
    check("12 live after the rite", (floor() or 0) >= 12, floor())
    # ---------------------------------------------------------------- persistence
    poll(lambda: "reseeding=0" in status(), 100, 5)
    snap = status().splitlines()[0]
    call("POST", "/world/leave", {}, timeout=120)
    call("POST", "/world/open", {"name": WORLD}, timeout=300)
    wait(40)
    now = status().splitlines()[0]
    same = re.search(r"phase=\S+", snap).group(0) == re.search(r"phase=\S+", now).group(0) and \
        re.search(r"floor=\S+", snap).group(0) == re.search(r"floor=\S+", now).group(0)
    check("fight state survives save/reload", same, now)
    check("Deacon present after reload", deacon_pos() is not None)
    heal_player()
    # ---------------------------------------------------------------- anti-soft-lock
    cmd("tp @e[type=skylore_bosses:static_deacon] 0 150 60")
    wait(10)
    p = deacon_pos()
    check("Deacon carried out of the nave returns to the altar", p is not None and abs(p["x"]) < 2 and abs(p["z"]) < 2 and abs(p["y"] - 102) < 1, p)
    cmd("setblock 1 101 1 minecraft:air")
    wait(45)
    check("damaged plinth is restored", block(1, 101, 1).get("id") == "skylore_bosses:altar_plinth")
    cmd("setblock 0 103 1 minecraft:stone")
    wait(12)
    check("blocks placed on the altar are cleared", block(0, 103, 1).get("id") == "minecraft:air")
    tp(5.5, 94, 5.5)
    wait(10)
    pos = call("GET", "/state")["position"]
    check("falling out of the crypt -> rescued to the entry pad", abs(pos["z"] - 14.5) < 1.5 and pos["y"] > 100, pos)
    # ---------------------------------------------------------------- every attack
    heal_player()
    cmd("skyloredeacon setfloor 48")
    tp(0.5, 101, 12.5)
    wait(10)
    measured = {}
    for a in ["vesting_chime", "communion_pulse", "lattice_lash", "static_bolt", "nave_shatter", "homing_shard", "plinth_pull",
              "litany_beam", "reseed_rite"]:
        heal_player()
        cmd("tp @e[type=skylore_bosses:static_deacon] 0 102 0")
        wait(2)
        where = {"lattice_lash": (0.5, 102, 2.2), "nave_shatter": (0.5, 101, 5.5), "plinth_pull": (0.5, 101, 10.5)}.get(a, (0.5, 101, 12.5))
        tp(*where)
        before = player_hp()
        z0 = call("GET", "/state")["position"]["z"]
        cmd(f"skyloredeacon attack {a}")
        seen = set()
        zmin = z0
        for _ in range(70):
            if a == "lattice_lash":
                tp(*where)
            wait(2)
            if a == "plinth_pull":
                zmin = min(zmin, call("GET", "/state")["position"]["z"])
            act, st = action()
            if act == a:
                seen.add(st)
            elif seen and act != a:
                break
        wait(20)
        measured[a] = round(before - player_hp(), 1)
        if a == "plinth_pull":
            check("censer pull yanks the player toward the altar (before the combo lash)", z0 - zmin >= 4, f"z {z0:.1f} -> {zmin:.1f}")
        ok = "TELEGRAPH" in seen and len(seen) >= 2
        if a == "reseed_rite":
            ok = "TELEGRAPH" in seen or "ACTIVE" in seen   # the rite may start with a static step to the altar
        check(f"attack {a} runs telegraph -> active", ok, f"{sorted(seen)} took {measured[a]}")
    check("lattice_lash hits for ~9", 7 <= measured["lattice_lash"] <= 11, measured["lattice_lash"])
    check("communion_pulse hits a player on a live flagstone (3)", 2 <= measured["communion_pulse"] <= 4, measured["communion_pulse"])
    effects = [e.get("id") for e in call("GET", "/state").get("effects", [])]
    shot("deacon_attacks")
    errs = call("GET", "/logs?level=ERROR&lines=40").get("lines", [])
    errs = [x for x in errs if "skylorebosses" in (x.get("message", "") + str(x.get("stack", ""))).lower()]
    check("no mod errors in the log", not errs, errs[:1])
    # ---------------------------------------------------------------- litany screenshot
    tp(0.5, 101, 12.5)
    cmd("skyloredeacon attack litany_beam")
    wait(50)
    shot("deacon_litany")
    wait(40)
    # ---------------------------------------------------------------- victory
    cmd("skyloredeacon setfloor 0")
    cmd("damage @e[type=skylore_bosses:static_deacon,limit=1] 5000 minecraft:player_attack by @p")
    wait(20)
    check("kill -> DEFEATED", phase() == "defeated", phase())
    inv = [s.get("id") for s in call("GET", "/inventory").get("main", []) if s.get("id")]
    check("trophy granted (static_thurible)", "skylore_bosses:static_thurible" in inv)
    check("deacon_silenced advancement", adv("deacon_silenced"))
    check("no_reseed withheld after a reseed", not adv("no_reseed"))
    wait(60)
    shot("deacon_death")
    wait(120)
    check("DEFEATED -> CLEARED, door open", phase() == "cleared" and block(0, 102, 17).get("id") == "minecraft:air", phase())
    # ---------------------------------------------------------------- abandon reset
    if serverconfig():
        set_config("dormantTimeoutTicks", 100)
        cmd("skyloredeacon reset")
        tp(0.5, 101, 14.5)
        wait(40)
        check("restart after reset", phase() == "p0_vesting", phase())
        tp(0.5, 100, 63.5)
        wait(160)
        check("empty crypt resets to dormant", phase() == "dormant", phase())
        check("reset relays the floor", floor() == 48, floor())
        set_config("dormantTimeoutTicks", 1200)
    else:
        check("server config found for the abandon test", False)
    print("screenshots:", *SHOTS, sep="\n  ")
    print(f"\n{sum(ok for _, ok in RESULTS)}/{len(RESULTS)} passed")
    return 0 if all(ok for _, ok in RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
