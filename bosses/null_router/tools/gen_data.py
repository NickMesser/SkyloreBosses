"""Generate Null Router's static data/assets JSON: blockstates, block/item models, loot, damage types, advancements and
lang (merged into the shared en_us.json). Run after export_to_mod.py.  python gen_data.py"""
import json, os
import boss_env

ROOT = boss_env.BOSS_DIR
REPO = boss_env.REPO
RES = os.path.join(REPO, "src", "main", "resources")
NS = "skylore_bosses"
BOSS = "null_router"
A = os.path.join(RES, "assets", NS)
D = os.path.join(RES, "data", NS)
GLYPHS = ["circle", "triangle", "square"]
STATUS = ["idle", "arming", "locked", "alert"]
FACING_Y = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)


def tex(name):
    return f"{NS}:block/{BOSS}/{name}"


def simple(b, model):
    w(f"{A}/blockstates/{b}.json", {"variants": {"": {"model": f"{NS}:block/{b}"}}})
    w(f"{A}/models/block/{b}.json", model)
    w(f"{A}/models/item/{b}.json", {"parent": f"{NS}:block/{b}"})


def y_of(f):
    return {"y": dict(FACING_Y)[f]} if dict(FACING_Y)[f] else {}


# ---------------- blocks ----------------
for b in ("vault_floor", "vault_wall", "vault_ceiling", "vault_light"):
    simple(b, {"parent": "minecraft:block/cube_all", "textures": {"all": tex(b)}})
simple("vault_grate", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout", "textures": {"all": tex("vault_grate")}})
simple("vault_pillar", {"parent": "minecraft:block/cube_column", "textures": {"end": tex("vault_pillar_top"), "side": tex("vault_pillar_side")}})
simple("chassis_pad", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": tex("chassis_pad_top"), "bottom": tex("chassis_pad_side"), "side": tex("chassis_pad_side")}})

# coolant vent: lit while the vents are open (P3+ ACK windows)
w(f"{A}/blockstates/coolant_vent.json", {"variants": {
    "lit=false": {"model": f"{NS}:block/coolant_vent"}, "lit=true": {"model": f"{NS}:block/coolant_vent_lit"}}})
for name, top in (("coolant_vent", "coolant_vent"), ("coolant_vent_lit", "coolant_vent_lit")):
    w(f"{A}/models/block/{name}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": tex(top), "bottom": tex("chassis_pad_side"), "side": tex("chassis_pad_side")}})
w(f"{A}/models/item/coolant_vent.json", {"parent": f"{NS}:block/coolant_vent"})

# channel console: facing x glyph x status; the front is the glyph screen in a status-coloured bezel
variants = {}
for g in GLYPHS:
    for s in STATUS:
        w(f"{A}/models/block/channel_console_{g}_{s}.json", {"parent": "minecraft:block/orientable", "textures": {
            "front": tex(f"console_{g}_{s}"), "side": tex("console_side"), "top": tex("console_top")}})
        for f, _ in FACING_Y:
            variants[f"facing={f},glyph={g},status={s}"] = {"model": f"{NS}:block/channel_console_{g}_{s}", **y_of(f)}
w(f"{A}/blockstates/channel_console.json", {"variants": variants})
w(f"{A}/models/item/channel_console.json", {"parent": f"{NS}:block/channel_console_circle_idle"})

# request lamp: glyph x mode (head bright, next dim, off dark)
variants = {}
w(f"{A}/models/block/request_lamp_off.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex("lamp_off")}})
for g in GLYPHS:
    w(f"{A}/models/block/request_lamp_{g}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex(f"lamp_{g}")}})
    w(f"{A}/models/block/request_lamp_{g}_dim.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex(f"lamp_{g}_dim")}})
    variants[f"glyph={g},mode=off"] = {"model": f"{NS}:block/request_lamp_off"}
    variants[f"glyph={g},mode=head"] = {"model": f"{NS}:block/request_lamp_{g}"}
    variants[f"glyph={g},mode=next"] = {"model": f"{NS}:block/request_lamp_{g}_dim"}
w(f"{A}/blockstates/request_lamp.json", {"variants": variants})
w(f"{A}/models/item/request_lamp.json", {"parent": f"{NS}:block/request_lamp_circle"})

# routing gate: closed = red lattice (cutout); open = only a thin floor strip
w(f"{A}/models/block/routing_gate_closed.json", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout",
                                                 "textures": {"all": tex("routing_gate")}})
w(f"{A}/models/block/routing_gate_open.json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                               "textures": {"gate": tex("routing_gate"), "particle": tex("routing_gate")},
                                               "elements": [{"from": [0, 0, 0], "to": [16, 0.5, 16], "faces": {
                                                   "up": {"texture": "#gate"}, "down": {"texture": "#gate"}}}]})
w(f"{A}/blockstates/routing_gate.json", {"variants": {
    "open=false": {"model": f"{NS}:block/routing_gate_closed"}, "open=true": {"model": f"{NS}:block/routing_gate_open"}}})
w(f"{A}/models/item/routing_gate.json", {"parent": f"{NS}:block/routing_gate_closed"})

# service terminal
w(f"{A}/models/block/service_terminal.json", {"parent": "minecraft:block/orientable", "textures": {
    "front": tex("service_terminal"), "side": tex("service_terminal_side"), "top": tex("vault_ceiling")}})
w(f"{A}/blockstates/service_terminal.json", {"variants": {f"facing={f}": {"model": f"{NS}:block/service_terminal", **y_of(f)} for f, _ in FACING_Y}})
w(f"{A}/models/item/service_terminal.json", {"parent": f"{NS}:block/service_terminal"})

w(f"{A}/models/item/closed_ticket.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{BOSS}/closed_ticket"}})
w(f"{A}/models/item/null_router_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})
w(f"{A}/models/item/retry_packet_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})


# ---------------- loot: vanilla network-ish materials; the pack replaces this table (AE2 / Cyberware parts) ----------------
def pool(name, lo, hi):
    return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": name, "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}]}


w(f"{D}/loot_table/entities/null_router.json", {"type": "minecraft:entity", "pools": [
    pool("minecraft:quartz", 16, 32), pool("minecraft:redstone", 16, 32), pool("minecraft:amethyst_shard", 6, 12),
    pool("minecraft:copper_ingot", 8, 16), pool("minecraft:diamond", 2, 4), pool("minecraft:experience_bottle", 3, 6)]})
w(f"{D}/loot_table/entities/retry_packet.json", {"type": "minecraft:entity", "pools": []})

# ---------------- damage types ----------------
w(f"{D}/damage_type/short_circuit.json", {"message_id": f"{NS}.short_circuit", "exhaustion": 0.0, "scaling": "never"})
w(f"{D}/damage_type/null_route.json", {"message_id": f"{NS}.null_route", "exhaustion": 0.1, "scaling": "when_caused_by_living_non_player"})


# ---------------- advancements ----------------
def adv(name, parent, icon, frame="task", hidden=False, bg=None):
    crit = {"code": {"trigger": "minecraft:impossible"}}
    d = {"display": {"icon": {"id": icon}, "title": {"translate": f"advancements.{BOSS}.{name}.title"},
                     "description": {"translate": f"advancements.{BOSS}.{name}.description"}, "frame": frame,
                     "show_toast": True, "announce_to_chat": True, "hidden": hidden},
         "criteria": crit, "requirements": [list(crit.keys())]}
    if bg:
        d["display"]["background"] = bg
    if parent:
        d["parent"] = f"{NS}:{BOSS}/{parent}"
    w(f"{D}/advancement/{BOSS}/{name}.json", d)


adv("enter_vault", None, f"{NS}:service_terminal", bg=f"{NS}:block/{BOSS}/vault_wall")
adv("first_ack", "enter_vault", f"{NS}:channel_console")
adv("misrouted", "first_ack", f"{NS}:routing_gate", hidden=True)
adv("wet_ack", "first_ack", "minecraft:water_bucket", frame="goal")
adv("storm_weathered", "wet_ack", f"{NS}:request_lamp", frame="goal")
adv("queue_zero", "wet_ack", f"{NS}:closed_ticket", frame="challenge")
adv("no_misroute", "queue_zero", f"{NS}:chassis_pad", frame="challenge", hidden=True)

# ---------------- lang ----------------
L = {
    "block.skylore_bosses.vault_floor": "Clean-Room Floor",
    "block.skylore_bosses.vault_wall": "Vault Wall",
    "block.skylore_bosses.vault_ceiling": "Vault Ceiling Panel",
    "block.skylore_bosses.vault_light": "Clean-Room Light",
    "block.skylore_bosses.vault_pillar": "Server Rack Column",
    "block.skylore_bosses.chassis_pad": "Chassis Docking Pad",
    "block.skylore_bosses.coolant_vent": "Coolant Vent",
    "block.skylore_bosses.channel_console": "Channel Console",
    "block.skylore_bosses.request_lamp": "Request Lamp",
    "block.skylore_bosses.routing_gate": "Service Alcove Gate",
    "block.skylore_bosses.vault_grate": "Vault Blast Grate",
    "block.skylore_bosses.service_terminal": "Service Terminal",
    "item.skylore_bosses.closed_ticket": "Closed Ticket",
    "item.skylore_bosses.closed_ticket.lore": "Resolved. No requester on file.",
    "item.skylore_bosses.null_router_spawn_egg": "Null Router Spawn Egg (bench test)",
    "item.skylore_bosses.retry_packet_spawn_egg": "Retry Packet Spawn Egg",
    "entity.skylore_bosses.null_router": "Null Router",
    "entity.skylore_bosses.retry_packet": "Retry Packet",
    "death.attack.skylore_bosses.short_circuit": "%1$s stood in standing water outside an ACK window",
    "death.attack.skylore_bosses.short_circuit.player": "%1$s shorted out while fleeing %2$s",
    "death.attack.skylore_bosses.null_route": "%1$s was null-routed by %2$s",
    "death.attack.skylore_bosses.null_route.player": "%1$s was null-routed by %2$s",
    # boss bars
    "null_router.bossbar.name": "Null Router, the Unacked",
    "null_router.bossbar.title": "%s  |  Queue %s  |  %s",
    "null_router.bossbar.booting": "Booting. Please hold.",
    "null_router.bossbar.request": "Request %s: %s",
    "null_router.bossbar.request_dual": "Request %s: %s  (head on row %s; the dim row is a stale request)",
    "null_router.bossbar.ack": "ACK window: %s of %s requests cleared",
    "null_router.bossbar.ack_wet": "ACK window, coolant contact: %s of %s requests cleared",
    "null_router.bossbar.cleared": "Queue empty",
    "null_router.status.boot": "Booting",
    "null_router.status.p0": "Awaiting configuration",
    "null_router.status.p1": "Unacknowledged",
    "null_router.status.p2": "Dual queue",
    "null_router.status.p3": "Coolant rated",
    "null_router.status.p4": "Retry storm",
    "null_router.status.arming": "Pattern settling",
    "null_router.status.ack": "ACK: chassis solid, throughput %s%%",
    "null_router.status.ack_wet": "ACK, wet: throughput %s%%",
    "null_router.status.down": "Process terminated",
    # titles
    "null_router.title.boot": "Null Router",
    "null_router.title.boot.sub": "the Unacked. Still serving. Please hold.",
    "null_router.title.p2": "Dual queue",
    "null_router.title.p2.sub": "Two requests on the board. Only the bright row is at the head.",
    "null_router.title.p3": "Coolant rated",
    "null_router.title.p3.sub": "Dry acknowledgements now barely register. The vents open with each ACK.",
    "null_router.title.p4": "Retry storm",
    "null_router.title.p4.sub": "Queue unresolved. Retrying all requests.",
    "null_router.title.misroute": "Misrouted",
    "null_router.title.misroute.sub.alcove": "Your request has been routed to the service alcove. Please wait.",
    "null_router.title.misroute.sub.swap": "Your request has been exchanged with a retry. We apologise for nothing.",
    "null_router.title.misroute.sub.shock": "No alcove available. Your request has been dropped.",
    "null_router.title.victory": "Queue empty",
    "null_router.title.victory.sub": "All requests acknowledged. Nobody was waiting.",
    # log lines (action bar)
    "null_router.log.request": "[NR] Request %s queued: %s. Please configure channels A, B, C.",
    "null_router.log.p0_nudge": "[NR] Request pending. Set each console to the glyph on the lamp above it.",
    "null_router.log.p1": "[NR] Tutorial acknowledged. Queue: %s. Retries enabled.",
    "null_router.log.p2": "[NR] Dual queue. Queue: %s. Read the bright row.",
    "null_router.log.p3": "[NR] Coolant rated. Queue: %s. Wet the chassis during the ACK.",
    "null_router.log.stall": "[NR] Request retransmitted. Queue: %s. Thank you for your patience.",
    "null_router.log.rotate_warn": "[NR] Queue head rotating in 2 seconds.",
    "null_router.log.rotated": "[NR] Head moved to row %s: %s",
    "null_router.log.ack": "ACK. Chassis solid. Processing window open.",
    "null_router.log.ack_coolant": "ACK. Coolant vents open. Wet the chassis.",
    "null_router.log.wet": "Coolant contact. Throughput %s%%.",
    "null_router.log.dry": "Dry ACK: throughput %s%%. The chassis is coolant rated.",
    "null_router.log.cleared": "Request acknowledged: %s of %s this window. Queue: %s",
    "null_router.log.cleared_wet": "Wet ACK: %s of %s this window. Queue: %s",
    "null_router.log.quota": "Window quota reached (%s). Further damage is logged and ignored.",
    "null_router.log.misroute": "Request misrouted by %s. Queue: %s",
    "null_router.log.misroute_cooldown": "Misrouted again. Rerouting is rate limited; you are throttled instead. Queue: %s",
    "null_router.log.short": "Short circuit. Standing water outside an ACK window.",
    "null_router.log.airflow": "Clean-room airflow. Please remain at floor level.",
    "null_router.log.dock_clear": "The docking bay must remain clear.",
    "null_router.log.console_clear": "Console access must remain clear.",
    "null_router.log.rescue": "Returned to the vault. Your absence has been logged.",
    # consoles and terminal
    "null_router.console.set": "Channel %s set to %s. Consoles: %s  Head: %s",
    "null_router.console.flipped": "Channel %s was flipped to %s.",
    "null_router.console.locked": "Channels locked during ACK.",
    "null_router.console.booting": "Channel console booting.",
    "null_router.console.idle": "Channel console idle. No open ticket.",
    "null_router.console.cleared": "Queue empty. Nothing to configure.",
    "null_router.console.unregistered": "This console is not attached to any vault.",
    "null_router.terminal.denied": "Ticket refused. Your access level is pending.",
    "null_router.terminal.readmit": "Ticket reopened. Admitted to the vault. Queue: %s",
    "null_router.terminal.cleared": "Queue empty. The process has been terminated.",
    "null_router.terminal.unregistered": "This terminal is not attached to any vault.",
    # gate feedback (hitting the chassis)
    "null_router.gate.ghost": "NAK. Chassis unacknowledged. Match the request on the consoles first.",
    "null_router.gate.booting": "NAK. Chassis booting. Nothing lands yet.",
    "null_router.gate.docking": "Docking. The window opens in a moment.",
    "null_router.gate.releasing": "Window closed. Undocking.",
    # telegraph warnings
    "null_router.warn.boot": "Request 404. Still serving. Channels A, B, C online.",
    "null_router.warn.ping": "Channel %s expects %s.",
    "null_router.warn.lance": "Ghost lance. Get behind a column.",
    "null_router.warn.burst": "Packet burst. Retries incoming.",
    "null_router.warn.flick": "Console flick on channel %s. Stand at it to refuse.",
    "null_router.warn.flick_refused": "Channel %s is attended. Flick refused.",
    "null_router.warn.ttl": "TTL expiry. Jump the ring or leave the pad.",
    "null_router.warn.storm": "Retry storm. All consoles at risk.",
    # advancements
    "advancements.null_router.enter_vault.title": "Ticket Opened",
    "advancements.null_router.enter_vault.description": "Wake Null Router in an Automaton vault",
    "advancements.null_router.first_ack.title": "ACK",
    "advancements.null_router.first_ack.description": "Match a request and open an ACK window",
    "advancements.null_router.misrouted.title": "Please Hold",
    "advancements.null_router.misrouted.description": "Get misrouted to a service alcove",
    "advancements.null_router.wet_ack.title": "Not Rated for Immersion",
    "advancements.null_router.wet_ack.description": "Clear requests while the chassis is wet",
    "advancements.null_router.storm_weathered.title": "Retry Budget Exceeded",
    "advancements.null_router.storm_weathered.description": "End a retry storm with a wet ACK",
    "advancements.null_router.queue_zero.title": "Queue Empty",
    "advancements.null_router.queue_zero.description": "Defeat Null Router, the Unacked",
    "advancements.null_router.no_misroute.title": "Zero Packet Loss",
    "advancements.null_router.no_misroute.description": "Defeat Null Router without a single misroute",
}
SUBTITLES = {
    "ack": "Request acknowledged", "ack.close": "ACK window closes", "ack.warn": "ACK window closing", "arm": "Consoles settle",
    "boot": "Router boots", "burst.eject": "Packet ejected", "burst.fizzle": "Packet dropped", "burst.windup": "Ports open",
    "chime": "Boot chime", "chime.windup": "Lens spins up", "console.flipped": "Console flipped", "console.set": "Console set",
    "coolant.hiss": "Coolant hisses", "coolant.vent": "Coolant vents open", "death": "Router powers down", "dock": "Chassis docks",
    "drain": "Floor drains", "flick.refused": "Flick refused", "flick.windup": "Router targets a console", "gate.open": "Alcove gate opens",
    "idle.hum": "Router hums", "lance.charge": "Lance charges", "lance.fire": "Ghost lance fires", "lockdown": "Vault seals",
    "misroute": "Request misrouted", "misroute.windup": "Misroute alarm", "nak": "NAK", "ping": "Router pings", "ping.console": "Console pinged",
    "release": "Chassis undocks", "request": "Request queued", "request.cleared": "Request cleared", "retry.death": "Retry destroyed",
    "retry.drop": "Retry uplink dropped", "retry.reroute": "Retry reroutes", "retry.timeout": "Retry times out", "retry.uplink": "Retry uplinks",
    "retry.zap": "Retry zaps", "rotate": "Queue head rotates", "rotate.warn": "Queue head rotating", "short": "Short circuit",
    "stall": "Request retransmitted", "storm.alarm": "Retry storm alarm", "storm.port": "Port bursts", "terminal": "Terminal beeps",
    "ttl.pulse": "TTL expires", "ttl.windup": "TTL counting down", "victory": "Queue empty",
}
with open(os.path.join(ROOT, "tools", "sound_ids.txt")) as f:
    for sid in f.read().split():
        L[f"subtitles.{NS}.{BOSS}.{sid}"] = SUBTITLES.get(sid, "Null Router " + sid.replace(".", " "))
lang_path = f"{A}/lang/en_us.json"
merged = json.load(open(lang_path, encoding="utf-8")) if os.path.exists(lang_path) else {}
merged.update(L)
w(lang_path, dict(sorted(merged.items())))
print("data generated:", len(L), "lang keys")
