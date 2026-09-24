"""Generate the Static Deacon's static data/assets JSON: blockstates, block/item models, loot, tags, advancements and
lang (merged into the shared en_us.json). Run after export_to_mod.py.  python gen_data.py"""
import json, os
import boss_env

ROOT = boss_env.BOSS_DIR
REPO = boss_env.REPO
RES = os.path.join(REPO, "src", "main", "resources")
NS = "skylore_bosses"
BOSS = "static_deacon"
A = os.path.join(RES, "assets", NS)
D = os.path.join(RES, "data", NS)


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


def facing(b, model_name):
    w(f"{A}/blockstates/{b}.json", {"variants": {f"facing={f}": {"model": f"{NS}:block/{model_name}", **({"y": y} if y else {})}
                                                 for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})


# ---------------- blocks ----------------
for b in ("desecrated_endstone", "crypt_tile", "crypt_subfloor", "crypt_wall", "beryl_lamp"):
    simple(b, {"parent": "minecraft:block/cube_all", "textures": {"all": tex(b)}})
simple("crypt_grate", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout", "textures": {"all": tex("crypt_grate")}})
simple("altar_plinth", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": tex("altar_plinth_top"), "bottom": tex("altar_plinth_top"), "side": tex("altar_plinth_side")}})
simple("crypt_pillar", {"parent": "minecraft:block/cube_column", "textures": {"end": tex("crypt_pillar_top"), "side": tex("crypt_pillar_side")}})

# consecrated endstone: lit (communion) / unlit (reseeding)
w(f"{A}/blockstates/consecrated_endstone.json", {"variants": {
    "lit=true": {"model": f"{NS}:block/consecrated_endstone"}, "lit=false": {"model": f"{NS}:block/consecrated_endstone_unlit"}}})
w(f"{A}/models/block/consecrated_endstone.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex("consecrated_endstone")}})
w(f"{A}/models/block/consecrated_endstone_unlit.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex("consecrated_endstone_unlit")}})
w(f"{A}/models/item/consecrated_endstone.json", {"parent": f"{NS}:block/consecrated_endstone"})


def el(frm, to, texture, uv_all=None):
    faces = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        faces[f] = {"texture": texture}
    return {"from": frm, "to": to, "faces": faces}


# pew / choir stall: seat plus a back on the side opposite the facing (matches CryptPewBlock's shape)
w(f"{A}/models/block/crypt_pew.json", {"parent": "minecraft:block/block", "textures": {"wood": tex("crypt_pew"), "particle": tex("crypt_pew")},
                                         "elements": [el([0, 0, 0], [16, 8, 16], "#wood"), el([0, 8, 12], [16, 16, 16], "#wood"),
                                                      el([0, 15, 11], [16, 16, 16], "#wood")]})
facing("crypt_pew", "crypt_pew")
w(f"{A}/models/item/crypt_pew.json", {"parent": f"{NS}:block/crypt_pew"})

# sacristy bell: tile base, brass post, hanging bell
w(f"{A}/models/block/sacristy_bell.json", {"parent": "minecraft:block/block", "textures": {
    "brass": tex("sacristy_bell"), "base": tex("crypt_tile"), "particle": tex("sacristy_bell")},
    "elements": [el([3, 0, 3], [13, 2, 13], "#base"), el([7, 2, 7], [9, 14, 9], "#brass"), el([4, 12, 7], [12, 14, 9], "#brass"),
                 el([5, 6, 5], [11, 12, 11], "#brass"), el([4, 5, 4], [12, 6, 12], "#brass"), el([7.5, 4, 7.5], [8.5, 5, 8.5], "#brass")]})
facing("sacristy_bell", "sacristy_bell")
w(f"{A}/models/item/sacristy_bell.json", {"parent": f"{NS}:block/sacristy_bell"})

w(f"{A}/models/item/static_thurible.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{BOSS}/static_thurible"}})
w(f"{A}/models/item/static_deacon_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})


# ---------------- loot: the Deacon drops End reliquary materials; the pack may override this table ----------------
def pool(name, lo, hi):
    return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": name, "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}]}


w(f"{D}/loot_table/entities/static_deacon.json", {"type": "minecraft:entity", "pools": [
    pool("minecraft:ender_pearl", 4, 8), pool("minecraft:amethyst_shard", 6, 12), pool("minecraft:chorus_fruit", 4, 10),
    pool("minecraft:experience_bottle", 3, 6), pool("minecraft:end_stone_bricks", 16, 32)]})

# ---------------- tags ----------------
w(f"{D}/tags/block/{BOSS}/communion_substrate.json", {"values": [f"{NS}:consecrated_endstone"]})
w(f"{D}/tags/block/{BOSS}/breakable_cover.json", {"values": [f"{NS}:crypt_pew"]})


def merge_tag(path, values):
    j = json.load(open(path)) if os.path.exists(path) else {"replace": False, "values": []}
    for v in values:
        if v not in j["values"]:
            j["values"].append(v)
    w(path, j)


merge_tag(os.path.join(RES, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"), [f"{NS}:consecrated_endstone", f"{NS}:desecrated_endstone"])
merge_tag(os.path.join(RES, "data", "minecraft", "tags", "block", "mineable", "axe.json"), [f"{NS}:crypt_pew"])


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


adv("enter_crypt", None, f"{NS}:sacristy_bell", bg=f"{NS}:block/{BOSS}/crypt_wall")
adv("first_flagstone", "enter_crypt", f"{NS}:desecrated_endstone")
adv("stranded", "first_flagstone", f"{NS}:altar_plinth", frame="goal")
adv("vigil_broken", "first_flagstone", f"{NS}:consecrated_endstone")
adv("deacon_silenced", "vigil_broken", f"{NS}:static_thurible", frame="challenge",
    criteria={"code": {"trigger": "minecraft:impossible"},
              "kill": {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [
                  {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"type": f"{NS}:static_deacon"}}]}}})
adv("no_reseed", "deacon_silenced", f"{NS}:crypt_pew", frame="challenge", hidden=True)

# ---------------- lang ----------------
L = {
    "block.skylore_bosses.consecrated_endstone": "Consecrated Endstone",
    "block.skylore_bosses.desecrated_endstone": "Desecrated Endstone",
    "block.skylore_bosses.altar_plinth": "Altar Plinth",
    "block.skylore_bosses.crypt_tile": "Undercroft Tile",
    "block.skylore_bosses.crypt_subfloor": "Undercroft Subfloor",
    "block.skylore_bosses.crypt_wall": "Undercroft Wall",
    "block.skylore_bosses.crypt_pillar": "Undercroft Pillar",
    "block.skylore_bosses.beryl_lamp": "Beryl Lamp",
    "block.skylore_bosses.crypt_grate": "Undercroft Grate",
    "block.skylore_bosses.crypt_pew": "Choir Stall",
    "block.skylore_bosses.sacristy_bell": "Sacristy Bell",
    "item.skylore_bosses.static_thurible": "Static Thurible",
    "item.skylore_bosses.static_thurible.lore": "Still warm from a rite nobody finished.",
    "item.skylore_bosses.static_deacon_spawn_egg": "Static Deacon Spawn Egg (bench test)",
    "entity.skylore_bosses.static_deacon": "The Static Deacon",
    "entity.skylore_bosses.deacon_projectile": "Static Shard",
    "static_deacon.bossbar.name": "The Static Deacon",
    "static_deacon.bossbar.title": "%s  |  %s",
    "static_deacon.bossbar.floor": "Communion",
    "static_deacon.bossbar.floor_count": "Communion: %s of %s flagstones consecrated",
    "static_deacon.status.p0": "Vesting. Communion coming online.",
    "static_deacon.status.communion": "In communion. Absorbing %s%%, restoring %s HP/s",
    "static_deacon.status.plinth": "Vigil on the altar. Absorbing %s%%",
    "static_deacon.status.stranded": "Stranded off consecrated ground. Full damage",
    "static_deacon.status.p3_vigil": "Vigil on the altar. Reseed rite in %ss",
    "static_deacon.status.p3_stranded": "Stranded. Reseed rite in %ss",
    "static_deacon.status.p4": "Reseed rite: %s of %s flagstones",
    "static_deacon.status.down": "Post vacant",
    "static_deacon.title.vesting": "Vesting",
    "static_deacon.title.vesting.sub": "The Static Deacon takes its post.",
    "static_deacon.title.vigil": "Vigil",
    "static_deacon.title.vigil.sub": "Only the altar remains. Knock it off.",
    "static_deacon.title.reseed": "Reseed rite",
    "static_deacon.title.reseed.sub": "By standing order. No quorum required.",
    "static_deacon.title.rite_denied": "Rite unfulfilled",
    "static_deacon.title.rite_denied.sub": "No flagstone took. Vigil resumes.",
    "static_deacon.title.victory": "Rite concluded",
    "static_deacon.title.victory.sub": "Post vacant. No replacement requisitioned.",
    "static_deacon.log.p1": "[DEACON] Communion quorum: %s of %s flagstones. The floor is in good standing.",
    "static_deacon.log.p2": "[DEACON] Quorum lost: %s of %s flagstones. Proceeding on what remains.",
    "static_deacon.log.desecrated": "Flagstone desecrated. Communion %s/%s.",
    "static_deacon.log.denied": "Reseed denied. Communion %s/%s.",
    "static_deacon.log.reseed_done": "[DEACON] Reseed entered in the register: %s of %s flagstones in service.",
    "static_deacon.log.vigil_broken": "Vigil broken. The Deacon is off the altar.",
    "static_deacon.log.rite_broken": "Rite broken. The reseed stalls.",
    "static_deacon.log.plinth_refused": "The altar takes no offerings during a rite.",
    "static_deacon.log.unconsecrated": "Endstone laid without rite is only stone.",
    "static_deacon.log.rescue": "Returned to the nave. Your absence has been noted in the register.",
    "static_deacon.gate.communion": "Communion absorbed %s%% and the floor is healing it. Break the flagstones.",
    "static_deacon.gate.vigil": "Vigil on the altar: it heals slowly and shrugs off a quarter. Hit hard to knock it off.",
    "static_deacon.gate.rite": "The rite absorbs %s%%. Hit hard to break its focus.",
    "static_deacon.gate.stranded": "Off consecrated ground. Every hit lands.",
    "static_deacon.gate.desecrated": "Off the altar and exposed. It takes extra damage.",
    "static_deacon.gate.immune": "Vesting in progress. Nothing lands yet.",
    "static_deacon.warn.pulse": "Communion pulse. Get off the lit flagstones.",
    "static_deacon.warn.shatter": "Nave shatter. Jump the ring.",
    "static_deacon.warn.pull": "The censer chain is on you. Break line of sight.",
    "static_deacon.warn.pull_snapped": "The chain caught on stone.",
    "static_deacon.warn.litany": "Litany. Step out of the lit aisle.",
    "static_deacon.warn.litany_cross": "Litany of the cross. Clear the lit aisle and the lit row.",
    "static_deacon.warn.reseed": "Reseed rite. Break its focus, or break the new flagstones.",
    "static_deacon.warn.on_communion": "You are standing on communion. The floor heals the Deacon.",
    "static_deacon.bell.denied": "The bell does not ring. Admission pending.",
    "static_deacon.bell.readmit": "Admitted to the nave.",
    "static_deacon.bell.cleared": "The undercroft is quiet. The post is vacant.",
    "static_deacon.bell.unregistered": "This bell is not attached to any undercroft.",
    "advancements.static_deacon.enter_crypt.title": "Unscheduled Visitation",
    "advancements.static_deacon.enter_crypt.description": "Wake the Static Deacon in a Church of Ender undercroft",
    "advancements.static_deacon.first_flagstone.title": "Profane",
    "advancements.static_deacon.first_flagstone.description": "Desecrate a consecrated flagstone",
    "advancements.static_deacon.stranded.title": "Nothing Left to Kneel On",
    "advancements.static_deacon.stranded.description": "Strip every flagstone before the first reseed rite",
    "advancements.static_deacon.vigil_broken.title": "Kneel Elsewhere",
    "advancements.static_deacon.vigil_broken.description": "Knock the Static Deacon off the altar",
    "advancements.static_deacon.deacon_silenced.title": "Post Vacant",
    "advancements.static_deacon.deacon_silenced.description": "Defeat the Static Deacon",
    "advancements.static_deacon.no_reseed.title": "Lapsed",
    "advancements.static_deacon.no_reseed.description": "Defeat the Static Deacon before it completes a single reseed rite",
}
SUBTITLES = {
    "absorb": "Communion absorbs a blow", "beam.loop": "Litany reads", "beam.windup": "Deacon intones", "bell": "Sacristy bell rings",
    "bolt.cast": "Static bolt flies", "bolt.impact": "Static bolt crackles", "bolt.windup": "Static gathers", "chime": "Vesting chime",
    "chime.windup": "Censer rises", "death": "Deacon falls silent", "flagstone.desecrate": "Flagstone desecrated",
    "flagstone.live": "Flagstone consecrated", "flagstone.reseed": "Flagstone regrows", "hurt": "Deacon cracks",
    "idle.hum": "Deacon hums", "lash.swing": "Lattice lashes", "lash.windup": "Lattice draws back", "lockdown": "Undercroft seals",
    "pull.snap": "Censer chain snaps", "pull.windup": "Censer whirls", "pull.yank": "Censer chain yanks",
    "pulse.charge": "Communion gathers", "pulse.release": "Communion pulses", "reseed.begin": "Deacon kneels",
    "reseed.chant": "Reseed chant", "reseed.toll": "Reseed bell tolls", "shard.break": "Shard shatters", "shard.cast": "Shard sheds",
    "shard.windup": "Halo flares", "shatter.crack": "Nave shatters", "shatter.windup": "Deacon rises", "step": "Deacon dissolves",
    "vesting.rise": "Deacon vests", "victory": "Rite concludes", "vigil.begin": "Vigil begins", "vigil.break": "Vigil breaks",
}
with open(os.path.join(ROOT, "tools", "sound_ids.txt")) as f:
    for sid in f.read().split():
        L[f"subtitles.{NS}.{BOSS}.{sid}"] = SUBTITLES.get(sid, "Static Deacon " + sid.replace(".", " "))
lang_path = f"{A}/lang/en_us.json"
merged = json.load(open(lang_path, encoding="utf-8")) if os.path.exists(lang_path) else {}
merged.update(L)
w(lang_path, dict(sorted(merged.items())))
print("data generated:", len(L), "lang keys")
