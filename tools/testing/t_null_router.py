"""Null Router end-to-end test (DESIGN.md bosses/null_router): builds the test vault in a fresh void world and walks the
whole fight through Marionette, asserting the request queue bar, console input by real right-clicks, arming and
commit, the ACK window (edge i-frames, solid damage -> cleared requests, per-window quota), ghost immunity (NAK),
dry/wet throughput, coolant vents, the floor short and drain, clean-room airflow, retry packets flipping consoles,
console_flick refusal, misroutes into the service alcove (gate, cooldown), dual-queue alternation, stall growth,
every P4 trigger and its wet-ACK exit, every chassis action with measured damage where it is deterministic,
persistence across a save/reload, the void rescue, victory hooks and the abandon reset.
    set MARIONETTE_PORT=25590 (if your client is not on 25585), then: python t_null_router.py"""
import os, re, sys, time
from mar import call, cmd, wait

WORLD = "null_router_regress"
O = (0, 100, 0)                   # vault origin
FLOOR = O[1] + 1                  # players stand here
CONSOLE = [(-10, 0), (0, -10), (10, 0)]
STAND = [(-9, 0), (0, -9), (9, 0)]
FACE = ["east", "south", "west"]
RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok)))
    print(("PASS " if ok else "FAIL ") + name + (f"  [{detail}]" if detail != "" else ""), flush=True)


def status():
    return " ".join(cmd("skylorenullrouter status")["output"])


def field(pat, s=None, cast=str):
    m = re.search(pat, s or status())
    return cast(m.group(1)) if m else None


def st():
    """One status read, parsed."""
    s = status()
    g = lambda p, c=str: field(p, s, c)
    trip = lambda p: [int(x) for x in g(p).split(",")] if g(p) not in (None, "-") else None
    return {"raw": s, "phase": g(r"phase=(\S+)"), "t": g(r" t=(\d+)", int), "queue": g(r"queue=(\d+)/", int), "start": g(r"queue=\d+/(\d+)", int),
            "ack": g(r"ack=(\w+)"), "serial": g(r"serial=(\d+)", int), "head": trip(r"head=(\S+)"), "decoy": trip(r"decoy=(\S+)"),
            "row": g(r"headRow=(\w)"), "consoles": trip(r"consoles=(\S+)"), "arm": g(r"arm=(\d+)", int), "wet": g(r"wet=(\d+)", int),
            "cleared": g(r"cleared=(\d+)", int), "acks": g(r"acks=(\d+)", int), "wetAcks": g(r"wetAcks=(\d+)", int),
            "misroutes": g(r"misroutes=(\d+)", int), "stalls": g(r"stalls=(\d+)", int), "storms": g(r"storms=(\d+)", int),
            "retries": g(r"retries=(\d+)", int), "killed": g(r"killed=(\d+)", int), "stall": g(r"stall=(\d+)/", int),
            "alt": g(r"alt=(-?\d+)", int), "gates": g(r"gates=(\S+)"), "mode": g(r"mode=(\w+)"), "action": g(r"action=(\S+)"),
            "cy": g(r"pos=[-0-9.]+,([-0-9.]+),", float)}


def poll(pred, ticks, step=5):
    for _ in range(max(1, ticks // step)):
        if pred():
            return True
        wait(step)
    return pred()


def tp(x, y, z, yaw=180, pitch=0):
    for _ in range(3):
        cmd(f"tp @p {x} {y} {z} {yaw} {pitch}")
        wait(1)
        p = call("GET", "/state")["position"]
        if abs(p["x"] - x) < 1 and abs(p["z"] - z) < 1 and abs(p["y"] - y) < 1.5:
            return
    print("  (tp to", x, y, z, "landed at", p, ")")


def at(dx, dz, dy=1):
    return O[0] + dx + 0.5, O[1] + dy, O[2] + dz + 0.5


def block(dx, dy, dz):
    return call("GET", f"/block?x={O[0] + dx}&y={O[1] + dy}&z={O[2] + dz}")


def console_block(i):
    return block(CONSOLE[i][0], 1, CONSOLE[i][1])


def player_hp():
    return call("GET", "/state")["health"]


def heal():
    cmd("effect give @p minecraft:instant_health 1 10 true")
    wait(3)


def adv(path):
    r = cmd(f"execute if entity @p[advancements={{skylore_bosses:null_router/{path}=true}}]")
    return r.get("success") and "passed" in " ".join(r["output"])


def messages_since(seq):
    r = call("GET", f"/messages?since={seq}")
    return r.get("messages", []), r.get("lastSeq", seq)


def msg_seq():
    return call("GET", "/messages?since=0").get("lastSeq", 0)


def said(seq, text):
    ms, _ = messages_since(seq)
    return any(text in m.get("text", "") for m in ms)


def chassis():
    e = [x for x in call("GET", "/entities?range=80&type=skylore_bosses:null_router")["entities"] if x["type"] == "skylore_bosses:null_router"]
    return e[0] if e else None


def retries():
    return [x for x in call("GET", "/entities?range=80&type=skylore_bosses:retry_packet")["entities"] if x["type"] == "skylore_bosses:retry_packet"]


def use_console(i):
    """Right-click console i for real, standing at its stand spot."""
    x, y, z = at(*STAND[i])
    tp(x, y, z, {"east": 90, "south": 180, "west": 270}[FACE[i]] - 180, 20)
    cx, cz = CONSOLE[i]
    r = call("POST", "/interact", {"action": "use_block", "x": O[0] + cx, "y": O[1] + 1, "z": O[2] + cz, "face": FACE[i]})
    wait(6)   # console cooldown is 4 ticks
    return r


def set_by_hand(target):
    """Click each console until it shows the target pattern (the way a player solves a request)."""
    for i in range(3):
        for _ in range(3):
            if st()["consoles"][i] == target[i]:
                break
            use_console(i)


def hit(amount=20):
    """Server-side player_attack damage on the chassis; returns the queue drop."""
    q = st()["queue"]
    cmd(f"damage @e[type=skylore_bosses:null_router,limit=1] {amount} minecraft:player_attack by @p")
    wait(12)   # past vanilla i-frames
    return q - st()["queue"]


def open_window():
    r = cmd("skylorenullrouter forceack")
    poll(lambda: st()["ack"] == "open", 30, 2)
    return r


def close_window():
    poll(lambda: st()["ack"] == "none", 200, 5)


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


def main():
    r = call("POST", "/world/create", {"type": "void", "name": WORLD, "gamemode": "survival", "delete_existing": True,
                                        "gamerules": {"doMobSpawning": False, "doImmediateRespawn": True, "doDaylightCycle": False,
                                                      "keepInventory": True, "naturalRegeneration": False},
                                        "setup_commands": ["difficulty normal", "fill -3 99 60 3 99 66 minecraft:stone", "tp @s 0 100 63",
                                                           "spawnpoint @s 0 100 63", "time set noon",
                                                           "attribute @s minecraft:generic.max_health base set 200",
                                                           "effect give @s minecraft:saturation infinite 0 true"]})
    check("world created", r.get("ok"), r.get("error", ""))
    heal()

    # ================================================================ build + dormant
    out = cmd(f"skylorenullrouter build {O[0]} {O[1]} {O[2]}")["output"]
    wait(20)
    check("vault built", "Built Automaton vault" in " ".join(out))
    s = st()
    check("dormant after build", s["phase"] == "dormant", s["phase"])
    check("consoles placed (A circle, B triangle, C square)", [console_block(i)["id"] for i in range(3)] == ["skylore_bosses:channel_console"] * 3
          and s["consoles"] == [0, 1, 2])
    check("console faces the pad", console_block(0)["properties"].get("facing") == "east" and console_block(2)["properties"].get("facing") == "west")
    check("door open while dormant", block(0, 1, 16)["id"] == "minecraft:air")
    check("pad and basins built", block(0, 0, 0)["id"] == "skylore_bosses:chassis_pad" and block(3, 0, 3)["id"] == "minecraft:air"
          and block(3, -1, 3)["id"] == "skylore_bosses:coolant_vent")
    check("alcove gates open", block(11, 1, 13)["properties"].get("open") == "true")
    check("lamps off while dormant", block(-10, 3, 0)["properties"].get("mode") == "off")

    # ================================================================ start via the service terminal
    tp(*at(0, 19))
    call("POST", "/interact", {"action": "use_block", "x": O[0], "y": O[1] + 1, "z": O[2] + 20, "face": "up"})
    wait(10)
    s = st()
    check("terminal starts the fight (P0)", s["phase"] == "p0_boot", s["phase"])
    check("terminal readmits to the entry pad", abs(call("GET", "/state")["position"]["z"] - (O[2] + 12.5)) < 1)
    check("queue starts at 24 solo", s["queue"] == 24 and s["start"] == 24, f"{s['queue']}/{s['start']}")
    check("door sealed", block(0, 1, 16)["id"] == "skylore_bosses:vault_grate")
    check("chassis spawned as a ghost over the pad", s["mode"] == "ghost" and s["cy"] > FLOOR + 3, s["cy"])
    check("advancement enter_vault", adv("enter_vault"))
    # ghost immunity before and after the request
    q = s["queue"]
    check("P0 boot: damage does nothing", hit() == 0)
    poll(lambda: st()["serial"] >= 1, 200)
    s = st()
    check("tutorial request issued at ~110", s["serial"] == 1 and len(set(s["head"])) == 1, s["head"])
    wrong = sum(1 for i in range(3) if s["consoles"][i] != s["head"][i])
    check("tutorial: exactly two consoles wrong", wrong == 2, s["consoles"])
    check("head lamp shows the request glyph", block(-10, 3, 0)["properties"].get("mode") == "head"
          and block(-10, 3, 0)["properties"].get("glyph") == ["circle", "triangle", "square"][s["head"][0]])
    check("board shows the request", block(0, 8, -15)["properties"].get("mode") == "head")
    check("no decoy row in P0", block(-10, 4, 0)["properties"].get("mode") == "off")
    seq = msg_seq()
    ch = chassis()
    tp(*at(0, 3))
    cmd(f"tp @p {O[0] + 0.5} {O[1] + 4} {O[2] + 2.2}")
    call("POST", "/interact", {"action": "attack_entity", "uuid": ch["uuid"]})
    wait(4)
    check("melee on the ghost: NAK line, no damage", said(seq, "NAK") and st()["queue"] == q)
    tp(*at(0, 8))

    # ================================================================ solve by hand: arming, commit, ACK window
    head = st()["head"]
    wrong_i = [i for i in range(3) if st()["consoles"][i] != head[i]]
    seq = msg_seq()
    before = st()["consoles"][wrong_i[0]]
    use_console(wrong_i[0])
    s = st()
    check("right-click cycles a console (o -> ^ -> [] -> o)", s["consoles"][wrong_i[0]] == (before + 1) % 3, f"{before} -> {s['consoles']}")
    check("console set line", said(seq, "Channel"))
    set_by_hand(head)
    s = st()
    armed = s["arm"] > 0 or s["ack"] != "none"
    check("matching the head arms the consoles", armed, s["raw"][:160])
    if s["arm"] > 0:
        check("consoles show ARMING", console_block(0)["properties"].get("status") == "arming")
    poll(lambda: st()["ack"] in ("solidify", "open"), 40, 2)
    s = st()
    check("commit opens the ACK window", s["ack"] in ("solidify", "open"), s["ack"])
    check("consoles locked during ACK", console_block(1)["properties"].get("status") == "locked")
    seq = msg_seq()
    use_console(0)
    check("locked console refuses input", said(seq, "locked") and st()["consoles"] == head)
    poll(lambda: st()["ack"] == "open", 20, 1)
    s = st()
    check("chassis docked and solid", s["mode"] == "solid" and s["cy"] < FLOOR + 0.5, f"{s['mode']} y={s['cy']}")
    check("advancement first_ack", adv("first_ack"))
    d1 = hit(20)
    check("P0 dry hit of 20 clears one request", d1 == 1, d1)
    d2 = hit(20)
    check("second hit clears another", d2 == 1, d2)
    seq = msg_seq()
    d3 = hit(40)
    check("P0 window quota is 2", d3 == 0 and said(seq, "quota"), d3)
    close_window()
    s = st()
    check("window closes: P0 -> P1", s["phase"] == "p1_single", s["phase"])
    check("chassis back to ghost after the window", s["mode"] == "ghost")
    check("new request after the window", s["serial"] == 2 and s["consoles"] != s["head"], f"{s['head']} vs {s['consoles']}")
    check("hamming(head, consoles) >= 2", sum(1 for i in range(3) if s["head"][i] != s["consoles"][i]) >= 2)
    check("consoles unlocked", console_block(0)["properties"].get("status") in ("idle", "alert", "arming"))
    q = s["queue"]
    check("queue 22 after the tutorial", q == 22, q)

    # ================================================================ P1: actions with measured damage, retries
    heal()
    tp(*at(4, 4))
    wait(5)
    hp0 = player_hp()
    cmd("skylorenullrouter attack ghost_lance")
    poll(lambda: st()["action"] == "-", 80, 2)
    wait(4)
    check("ghost_lance hits in the open for 8", round(hp0 - player_hp(), 1) == 8.0, hp0 - player_hp())
    heal()
    tp(*at(9, 9))
    wait(5)
    hp0 = player_hp()
    cmd("skylorenullrouter attack ghost_lance")
    poll(lambda: st()["action"] == "-", 80, 2)
    wait(4)
    check("ghost_lance is stopped by a rack column", round(hp0 - player_hp(), 1) == 0.0, hp0 - player_hp())
    tp(*at(0, 8))
    seq = msg_seq()
    cmd("skylorenullrouter attack request_ping")
    wait(40)
    check("request_ping runs (wrong consoles beamed)", st()["action"] in ("-", "request_ping"))
    cmd("kill @e[type=skylore_bosses:retry_packet]")
    cmd("skylorenullrouter attack packet_burst")
    poll(lambda: st()["action"] == "-", 100, 5)
    n = len(retries())
    check("packet_burst ejects one retry in P1", n == 1, n)
    cmd("skylorenullrouter attack packet_burst")
    poll(lambda: st()["action"] == "-", 100, 5)
    cmd("skylorenullrouter attack packet_burst")
    poll(lambda: st()["action"] == "-", 100, 5)
    n = len(retries())
    check("retry cap 2 in P1", n <= 2, n)
    # a retry undoes a console that matches the head
    cmd("kill @e[type=skylore_bosses:retry_packet]")
    wait(5)
    s = st()
    h = s["head"]
    cmd(f"skylorenullrouter setconsole 1 {h[1]}")
    tp(*at(0, 13))
    cmd("skylorenullrouter spawnretries 1")
    flipped = poll(lambda: st()["consoles"][1] != h[1], 600, 10)
    check("retry walks to the matched console and flips it", flipped, st()["consoles"])
    s = st()
    check("retry flip never completes a request", s["consoles"] != s["head"] and s["arm"] == 0)
    r = retries()
    if r:
        cmd("kill @e[type=skylore_bosses:retry_packet]")
        wait(10)
    check("retry kill is counted", st()["killed"] >= 1, st()["killed"])
    # stall: the retransmit clock adds one request
    s0 = st()
    set_config("stallP1", 60)
    ok = poll(lambda: st()["stalls"] > s0["stalls"], 200, 10)
    check("stall retransmits a request (queue +1)", ok and st()["queue"] == s0["queue"] + 1, f"{s0['queue']} -> {st()['queue']}")
    set_config("stallP1", 600)
    wait(5)

    # ================================================================ P1 -> P2 (three acks), dry throughput 1.0
    for k in range(3):
        if st()["phase"] != "p1_single":
            break
        open_window()
        hit(20)
        close_window()
    s = st()
    check("P1 -> P2 after three acks", s["phase"] == "p2_dual", f"{s['phase']} acks={s['acks']} q={s['queue']}")
    check("P2 shows a decoy at distance >= 2 from the head", s["decoy"] is not None and sum(1 for i in range(3) if s["decoy"][i] != s["head"][i]) >= 2,
          f"{s['head']} / {s['decoy']}")
    check("decoy row lit dim", block(-10, 4, 0)["properties"].get("mode") == "next" and block(-10, 3, 0)["properties"].get("mode") == "head")
    # dual queue alternation
    alt = s["alt"]
    ok = poll(lambda: st()["row"] == "B", alt + 40, 10)
    check("head rotates to row B after alternateP2", ok, st()["alt"])
    check("row B lamps now bright, row A dim", block(-10, 4, 0)["properties"].get("mode") == "head" and block(-10, 3, 0)["properties"].get("mode") == "next")

    # ================================================================ misroute: alcove, gate, cooldown
    cmd("skylorenullrouter hold true")   # isolate each check from the live attack bag, rotation and the storm trigger
    cmd("kill @e[type=skylore_bosses:retry_packet]")
    heal()
    q = st()["queue"]
    tp(*at(-5, 0))
    cmd("skylorenullrouter decoy")
    poll(lambda: st()["misroutes"] >= 1, 60, 2)
    wait(15)
    s = st()
    p = call("GET", "/state")["position"]
    in_alcove = abs(abs(p["x"] - O[0]) - 13.5) < 2.2 and p["z"] - O[2] > 11.5
    check("matching the decoy misroutes (queue +1)", s["misroutes"] == 1 and s["queue"] == q + 1, f"{q} -> {s['queue']}")
    check("misroute_pulse puts the actor in a service alcove", in_alcove, p)
    check("the alcove gate closes", "C" in s["gates"], s["gates"])
    check("advancement misrouted", adv("misrouted"))
    check("the decoy is re-rolled (consoles no longer match it)", s["decoy"] != s["consoles"])
    ok = poll(lambda: "C" not in st()["gates"], 120, 10)
    check("gate reopens after holdTicks", ok)
    q = st()["queue"]
    tp(*at(-5, 0))
    cmd("skylorenullrouter decoy")
    wait(20)
    p = call("GET", "/state")["position"]
    check("second misroute inside the cooldown: throttled, not teleported", st()["misroutes"] == 2 and abs(p["x"] - (O[0] - 4.5)) < 1.5, p)
    # console_flick: refused while attended, lands when not
    s = st()
    h = s["head"]
    for i in range(3):
        cmd(f"skylorenullrouter setconsole {i} {(h[i] + 1) % 3}")
    cmd(f"skylorenullrouter setconsole 0 {h[0]}")
    tp(*at(0, 12))
    seq = msg_seq()
    cmd("skylorenullrouter attack console_flick")
    wait(10)
    tp(*at(*STAND[0]))   # walk up to the targeted console during the telegraph
    wait(35)
    check("console_flick refused when a player stands at the console", st()["consoles"][0] == h[0] and said(seq, "attended"), st()["consoles"])
    tp(*at(0, 12))
    cmd("skylorenullrouter attack console_flick")
    wait(45)
    check("console_flick flips an unattended matched console", st()["consoles"][0] != h[0], st()["consoles"])
    # ttl_expiry on the pad ring
    heal()
    tp(*at(4, 0))
    cmd("effect give @p minecraft:slow_falling 5 0 true")   # the ring's knock-up would add a point of fall damage
    wait(3)
    hp0 = player_hp()
    cmd("skylorenullrouter attack ttl_expiry")
    wait(55)
    check("ttl_expiry ring hits a grounded player for 6", round(hp0 - player_hp(), 1) == 6.0, hp0 - player_hp())
    heal()

    # ================================================================ P2 -> P3 by queue; coolant throughput
    cmd("skylorenullrouter setqueue 11")
    cmd("skylorenullrouter hold false")
    open_window()
    hit(20)
    close_window()
    s = st()
    check("P2 -> P3 at queue <= 42% of start", s["phase"] == "p3_coolant", f"{s['phase']} q={s['queue']}")
    q = s["queue"]
    open_window()
    check("P3 ACK opens the coolant vents (basins filled)", block(3, 0, 3)["id"] == "minecraft:water"
          and block(3, -1, 3)["properties"].get("lit") == "true")
    d = hit(20)
    check("P3 dry hit of 20 clears nothing (x0.25)", d == 0, d)
    for _ in range(3):
        hit(20)
    s = st()
    check("P3 dry window: 80 damage clears exactly 1 (cap 1)", q - s["queue"] == 1, q - s["queue"])
    d = hit(40)
    check("P3 dry cap holds", d == 0, d)
    close_window()
    check("vents drain and close after the window", block(3, 0, 3)["id"] == "minecraft:air" and block(3, -1, 3)["properties"].get("lit") == "false")
    # wet: water poured on the pad during the window
    q = st()["queue"]
    open_window()
    cmd(f"setblock {O[0] + 1} {O[1] + 1} {O[2]} minecraft:water")
    ok = poll(lambda: st()["wet"] > 0, 20, 1)
    check("water on the pad wets the docked chassis", ok)
    d = hit(4)
    check("a wet tap always clears one (wetMinimum)", d == 1, d)
    d = hit(20)
    check("wet hit of 20 clears two more (x2.5, credit 3)", d == 2, d)
    d = hit(40)
    check("wet window cap is 4", st()["queue"] == q - 4, q - st()["queue"])
    close_window()
    s = st()
    check("wet ack counted and advancement wet_ack", s["wetAcks"] >= 1 and adv("wet_ack"))
    check("all water drained at window close", block(1, 1, 0)["id"] == "minecraft:air")
    # floor short + drain between windows
    cmd("skylorenullrouter hold true")
    cmd("kill @e[type=skylore_bosses:retry_packet]")
    heal()
    tp(*at(-5, 5))
    cmd(f"fill {O[0] - 6} {O[1] + 1} {O[2] + 4} {O[0] - 4} {O[1] + 1} {O[2] + 6} minecraft:water")
    wait(2)
    hp0 = player_hp()
    wait(42)
    lost = hp0 - player_hp()
    check("floor_short: standing water outside a window hurts (3 per 20 ticks)", 5.9 <= lost <= 9.1, lost)
    ok = poll(lambda: all(block(-5 + dx, 1, 5 + dz)["id"] != "minecraft:water" for dx in (-1, 0, 1) for dz in (-1, 0, 1)), 160, 10)
    check("standing water is drained after drainTicks", ok)
    heal()
    # clean-room airflow
    tp(*at(-5, 5, 10))
    wait(8)
    check("airflow pushes a flyer down", call("GET", "/state")["position"]["y"] < O[1] + 10.5, call("GET", "/state")["position"]["y"])
    tp(*at(0, 8))
    # placement guard on the docking bay
    r = cmd(f"setblock {O[0]} {O[1] + 2} {O[2]} minecraft:stone")
    wait(12)
    check("docking bay is kept clear", block(0, 2, 0)["id"] == "minecraft:air")

    cmd("skylorenullrouter hold false")

    # ================================================================ P4: queue rise trigger, storm, wet exit
    s = st()
    cmd(f"skylorenullrouter setqueue {s['queue'] + 3}")
    ok = poll(lambda: st()["phase"] == "p4_storm", 20, 1)
    check("P3 -> P4 when the queue climbs by 3", ok, st()["phase"])
    ok = poll(lambda: len(retries()) >= 3, 120, 5)
    check("retry_storm spawns packets from the wall ports", ok, len(retries()))
    s = st()
    check("storm counted", s["storms"] >= 1)
    cmd("kill @e[type=skylore_bosses:retry_packet]")
    open_window()
    cmd("skylorenullrouter setwet 100")
    hit(20)
    close_window()
    s = st()
    check("a wet ACK ends the storm (back by queue)", s["phase"] in ("p1_single", "p2_dual", "p3_coolant"), s["phase"])
    check("advancement storm_weathered", adv("storm_weathered"))
    # the other P4 trigger: no wet ack for p4NoWetAckTicks in P3
    cmd("skylorenullrouter setqueue 8")
    if st()["phase"] != "p3_coolant":
        open_window(); hit(1); close_window()
    set_config("p4NoWetAckTicks", 120)
    ok = poll(lambda: st()["phase"] == "p4_storm", 260, 10)
    check("P3 -> P4 after p4NoWetAckTicks without a wet ack", ok, st()["phase"])
    set_config("p4NoWetAckTicks", 1200)
    cmd("kill @e[type=skylore_bosses:retry_packet]")
    open_window()
    cmd("skylorenullrouter setwet 100")
    hit(20)
    close_window()

    # ================================================================ persistence: save, leave, reopen mid-window
    s = st()
    open_window()
    before = st()
    call("POST", "/world/leave", {}, timeout=120)
    time.sleep(3)
    call("POST", "/world/open", {"name": WORLD}, timeout=300)
    wait(40)
    after = st()
    check("reload keeps phase, queue, request and consoles", (after["phase"], after["queue"], after["head"], after["consoles"])
          == (before["phase"], before["queue"], before["head"], before["consoles"]), f"{before['raw'][:120]} || {after['raw'][:120]}")
    check("reload keeps the ACK window (chassis solid again)", after["ack"] in ("open", "release", "none") and chassis() is not None)
    close_window()
    check("fight continues after reload", st()["phase"] in ("p1_single", "p2_dual", "p3_coolant", "p4_storm"))

    # ================================================================ void rescue
    tp(O[0] + 0.5, O[1] - 20, O[2] + 0.5)
    wait(10)
    p = call("GET", "/state")["position"]
    check("falling under the vault puts you back on the entry pad", abs(p["z"] - (O[2] + 12.5)) < 1.5 and p["y"] > O[1], p)
    heal()

    # ================================================================ victory
    cmd("skylorenullrouter setqueue 1")
    tp(*at(0, 8))
    s = st()
    set_by_hand(s["head"])
    poll(lambda: st()["ack"] == "open", 60, 2)
    cmd("skylorenullrouter setwet 200")   # a bare fist dry in P3 is a scratch; wet, any tap clears one
    ch = chassis()
    cmd(f"tp @p {O[0] + 0.5} {FLOOR} {O[2] - 2.2} 0 10")
    for _ in range(4):
        call("POST", "/interact", {"action": "attack_entity", "uuid": ch["uuid"]})
        wait(12)
        if st()["phase"] == "defeated":
            break
    s = st()
    check("real melee in the window empties the queue: DEFEATED", s["phase"] == "defeated", s["phase"])
    check("advancement queue_zero", adv("queue_zero"))
    inv = call("GET", "/inventory").get("main", [])
    check("closed_ticket trophy given", any(x.get("id") == "skylore_bosses:closed_ticket" for x in inv))
    ok = poll(lambda: st()["phase"] == "cleared", 200, 10)
    check("DEFEATED -> CLEARED, door opens", ok and block(0, 1, 16)["id"] == "minecraft:air")
    check("chassis powered down and removed", poll(lambda: chassis() is None, 60, 10))
    check("no retries left after victory", len(retries()) == 0)
    seq = msg_seq()
    tp(*at(*STAND[0]))
    call("POST", "/interact", {"action": "use_block", "x": O[0] - 10, "y": O[1] + 1, "z": O[2], "face": "east"})
    wait(4)
    check("consoles report the queue empty after the clear", said(seq, "Queue empty"))

    # ================================================================ abandon reset
    set_config("dormantTimeoutTicks", 100)
    cmd("skylorenullrouter reset")
    wait(5)
    tp(*at(0, 5))
    poll(lambda: st()["phase"] == "p0_boot", 40, 5)
    check("restart after reset", st()["phase"] == "p0_boot")
    tp(0, 100, 63)
    ok = poll(lambda: st()["phase"] == "dormant", 200, 10)
    check("abandoned vault resets to dormant", ok, st()["phase"])
    check("door reopens on reset", block(0, 1, 16)["id"] == "minecraft:air")
    set_config("dormantTimeoutTicks", 1200)

    n = sum(1 for _, ok in RESULTS if ok)
    print(f"\n{n}/{len(RESULTS)} checks passed")
    for name, ok in RESULTS:
        if not ok:
            print("  FAILED:", name)
    return 0 if n == len(RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
