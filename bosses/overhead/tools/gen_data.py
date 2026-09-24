"""Generate Overhead's static data/assets JSON: blockstates, block/item models, loot, tags, advancements and lang
(merged into the shared en_us.json). Run after export_to_mod.py.  python gen_data.py"""
import json, os
import boss_env

ROOT = boss_env.BOSS_DIR
REPO = boss_env.REPO
RES = os.path.join(REPO, "src", "main", "resources")
NS = "skylore_bosses"
BOSS = "overhead"
A = os.path.join(RES, "assets", NS)
D = os.path.join(RES, "data", NS)


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)


CUBE_BLOCKS = ["yard_plating", "hazard_plating", "yard_wall", "yard_shutter", "yard_crate"]

for b in CUBE_BLOCKS:
    w(f"{A}/blockstates/{b}.json", {"variants": {"": {"model": f"{NS}:block/{b}"}}})
    w(f"{A}/models/block/{b}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{BOSS}/{b}"}})
    w(f"{A}/models/item/{b}.json", {"parent": f"{NS}:block/{b}"})

# console: horizontal facing, screen on the front
w(f"{A}/models/block/yard_console.json", {"parent": "minecraft:block/orientable", "textures": {
    "front": f"{NS}:block/{BOSS}/yard_console_front", "side": f"{NS}:block/{BOSS}/yard_console_side",
    "top": f"{NS}:block/{BOSS}/yard_console_side"}})
w(f"{A}/blockstates/yard_console.json", {"variants": {f"facing={f}": {"model": f"{NS}:block/yard_console", **({"y": y} if y else {})}
                                                      for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
w(f"{A}/models/item/yard_console.json", {"parent": f"{NS}:block/yard_console"})
# GeckoLib-rendered pylon and invisible casing: particle textures only
w(f"{A}/blockstates/generator_pylon.json", {"variants": {"": {"model": f"{NS}:block/generator_pylon"}}})
w(f"{A}/models/block/generator_pylon.json", {"textures": {"particle": f"{NS}:block/{BOSS}/pylon_casing"}})
w(f"{A}/models/item/generator_pylon.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{BOSS}/generator_pylon"}})
w(f"{A}/blockstates/pylon_casing.json", {"variants": {"": {"model": f"{NS}:block/pylon_casing"}}})
w(f"{A}/models/block/pylon_casing.json", {"textures": {"particle": f"{NS}:block/{BOSS}/pylon_casing"}})
w(f"{A}/models/item/targeting_core.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{BOSS}/targeting_core"}})
w(f"{A}/models/item/overhead_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})

# ---------------- loot: the chassis drops salvage; the pack may override this table ----------------
def pool(name, lo, hi):
    return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": name, "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}]}


w(f"{D}/loot_table/entities/overhead.json", {"type": "minecraft:entity", "pools": [
    pool("minecraft:iron_block", 2, 4), pool("minecraft:copper_block", 3, 6), pool("minecraft:redstone_block", 1, 3),
    pool("minecraft:gold_ingot", 4, 9)]})

# ---------------- tags ----------------
w(f"{D}/tags/block/{BOSS}/breakable_cover.json", {"values": [f"{NS}:yard_crate"]})
w(f"{D}/tags/entity_type/{BOSS}/flight_vehicles.json", {"values": [{"id": f"immersive_aircraft:{v}", "required": False}
                                                              for v in ("airship", "biplane", "gyrodyne", "quadrocopter", "cargo_airship", "warship", "bamboo_hopper")]})
axe = os.path.join(RES, "data", "minecraft", "tags", "block", "mineable", "axe.json")
w(axe, {"replace": False, "values": [f"{NS}:yard_crate"]})


# ---------------- advancements ----------------
def adv(name, parent, icon, frame="task", hidden=False, bg=None, criteria=None):
    crit = criteria or {"code": {"trigger": "minecraft:impossible"}}
    d = {"display": {"icon": {"id": icon}, "title": {"translate": f"advancements.{BOSS}.{name}.title"},
                     "description": {"translate": f"advancements.{BOSS}.{name}.description"}, "frame": frame,
                     "show_toast": True, "announce_to_chat": True, "hidden": hidden},
         "criteria": crit, "requirements": [list(crit.keys())]}
    if bg:
        d["display"]["background"] = bg
    if parent:
        d["parent"] = f"{NS}:{BOSS}/{parent}"
    w(f"{D}/advancement/{BOSS}/{name}.json", d)


adv("enter_yard", None, f"{NS}:yard_console", bg=f"{NS}:block/{BOSS}/yard_plating")
adv("first_pylon", "enter_yard", f"{NS}:generator_pylon")
adv("demolition_crew", "first_pylon", f"{NS}:hazard_plating", frame="goal")
adv("prototype_down", "first_pylon", f"{NS}:targeting_core", frame="challenge",
    criteria={"code": {"trigger": "minecraft:impossible"},
              "kill": {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [
                  {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"type": f"{NS}:overhead"}}]}}})
adv("no_rearm", "prototype_down", f"{NS}:yard_shutter", frame="challenge", hidden=True)

# ---------------- lang ----------------
L = {
    "block.skylore_bosses.yard_plating": "Yard Plating", "block.skylore_bosses.hazard_plating": "Hazard Plating",
    "block.skylore_bosses.yard_wall": "Yard Wall", "block.skylore_bosses.yard_shutter": "Lockdown Shutter",
    "block.skylore_bosses.yard_crate": "Yard Crate", "block.skylore_bosses.yard_console": "Gate Console",
    "block.skylore_bosses.generator_pylon": "Generator Pylon", "block.skylore_bosses.pylon_casing": "Pylon Casing",
    "item.skylore_bosses.targeting_core": "Overhead Targeting Core",
    "item.skylore_bosses.overhead_spawn_egg": "Overhead Spawn Egg (bench test)",
    "entity.skylore_bosses.overhead": "Overhead",
    "entity.skylore_bosses.overhead_ordnance": "Overhead Ordnance",
    "overhead.bossbar.name": "Overhead, Noven's Decommissioned Prototype",
    "overhead.bossbar.title": "%s  |  %s",
    "overhead.bossbar.grid": "Yard Grid",
    "overhead.status.p0": "Powering on. Grid spinning up.",
    "overhead.status.shielded": "Grid %s/4. Shield absorbing %s%%",
    "overhead.status.window": "GRID FAULT. Shield down for %ss",
    "overhead.status.p3": "Chassis exposed. Re-arm in %ss",
    "overhead.status.p4": "Emergency re-arm: %s/%s nodes",
    "overhead.status.down": "Ticket closed",
    "overhead.pylon.0": "Node NW", "overhead.pylon.1": "Node NE", "overhead.pylon.2": "Node SW", "overhead.pylon.3": "Node SE",
    "overhead.title.lockdown": "OVERHEAD",
    "overhead.title.lockdown.sub": "Status: decommissioned. Power: on.",
    "overhead.title.exposed": "All nodes offline",
    "overhead.title.exposed.sub": "Chassis continuing under protest.",
    "overhead.title.rearm": "Emergency re-arm",
    "overhead.title.rearm.sub": "Authorized. Budget code: none.",
    "overhead.title.rearm_aborted": "Re-arm aborted",
    "overhead.title.rearm_aborted.sub": "Nodes failed inspection. Chassis exposed.",
    "overhead.title.victory": "Ticket closed",
    "overhead.title.victory.sub": "Asset destroyed. Noven has not been notified.",
    "overhead.log.p1": "[OVERHEAD] Yard grid nominal. Threat ticket opened. Assignee: artillery.",
    "overhead.log.pylon_down": "%s offline. Incident report filed. Nodes remaining: %s",
    "overhead.log.window_closed": "[OVERHEAD] Shielding restored from remaining nodes.",
    "overhead.log.pylon_online": "%s back online. Warranty status: void.",
    "overhead.log.rearm_done": "[OVERHEAD] Re-arm complete: %s nodes. Resuming normal operations.",
    "overhead.log.rescue": "Yard safety net engaged. Incident filed under 'weather'.",
    "overhead.gate.absorbed": "Grid shield absorbed %s%% of that. Break the pylons.",
    "overhead.gate.immune": "Shield at full power. Nothing gets through right now.",
    "overhead.gate.pylon_spinning": "Node spinning up. Wait for the grid to come online.",
    "overhead.warn.missile_lock": "MISSILE LOCK. Get behind cover.",
    "overhead.warn.laser": "Sensor lens charging. Watch the red line.",
    "overhead.warn.overcharge": "Node overcharging. Get off the pad!",
    "overhead.warn.carpet": "Carpet bombing. Get into an unmarked lane.",
    "overhead.warn.painted": "You are painted. Overhead is aiming at you.",
    "overhead.console.denied": "Yard access denied. Clearance pending.",
    "overhead.console.readmit": "Re-admitted to the yard. Keep your hands inside the perimeter.",
    "overhead.console.cleared": "Ticket closed. The yard is quiet.",
    "overhead.console.unregistered": "This console is not linked to any yard.",
    "advancements.overhead.enter_yard.title": "Unauthorized Personnel",
    "advancements.overhead.enter_yard.description": "Trip the lockdown in a Teknari yard",
    "advancements.overhead.first_pylon.title": "Grid Fault",
    "advancements.overhead.first_pylon.description": "Knock out one of Overhead's generator pylons",
    "advancements.overhead.demolition_crew.title": "Demolition Crew",
    "advancements.overhead.demolition_crew.description": "Take all four pylons offline before Overhead can re-arm",
    "advancements.overhead.prototype_down.title": "Decommissioned",
    "advancements.overhead.prototype_down.description": "Destroy Overhead, Noven's Decommissioned Prototype",
    "advancements.overhead.no_rearm.title": "Under Budget",
    "advancements.overhead.no_rearm.description": "Destroy Overhead without letting it re-arm once",
}
with open(os.path.join(ROOT, "tools", "sound_ids.txt")) as f:
    for sid in f.read().split():
        words = sid.replace("_", " ").split(".")
        subject = {"pylon": "Pylon", "shell": "Shell", "missile": "Missile", "flak": "Flak", "carpet": "Carpet bombing",
                   "lockdown": "Yard", "rearm": "Overhead", "victory": "Yard"}.get(words[0], "Overhead")
        rest = " ".join(words if subject == "Overhead" else words[1:])
        L[f"subtitles.{NS}.{BOSS}.{sid}"] = f"{subject} {rest}".strip().capitalize()
lang_path = f"{A}/lang/en_us.json"
merged = json.load(open(lang_path, encoding="utf-8")) if os.path.exists(lang_path) else {}
merged.update(L)
w(lang_path, dict(sorted(merged.items())))
print("data generated:", len(L), "lang keys")
