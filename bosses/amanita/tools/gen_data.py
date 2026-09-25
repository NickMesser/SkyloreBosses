"""Generate Amanita's static data/assets JSON: blockstates, block/item models, loot, tags, advancements and lang (merged
into the shared en_us.json). Run after export_to_mod.py.  python gen_data.py"""
import json, os
import boss_env

ROOT = boss_env.BOSS_DIR
REPO = boss_env.REPO
RES = os.path.join(REPO, "src", "main", "resources")
NS = "skylore_bosses"
BOSS = "amanita"
A = os.path.join(RES, "assets", NS)
D = os.path.join(RES, "data", NS)


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)


def tex(name):
    return f"{NS}:block/{BOSS}/{name}"


def simple(b, model):
    w(f"{A}/blockstates/{b}.json", {"variants": {"": {"model": f"{NS}:block/{b}"}}})
    w(f"{A}/models/block/{b}.json", model)
    w(f"{A}/models/item/{b}.json", {"parent": f"{NS}:block/{b}"})


# ---------------- blocks ----------------
for b in ("hollow_wall", "hollow_loam", "hollow_ceiling", "gill_shelf"):
    simple(b, {"parent": "minecraft:block/cube_all", "textures": {"all": tex(b)}})
simple("hollow_membrane", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout", "textures": {"all": tex("hollow_membrane")}})

# column: a rotated pillar (the hollow places it upright)
w(f"{A}/blockstates/hollow_column.json", {"variants": {
    "axis=y": {"model": f"{NS}:block/hollow_column"},
    "axis=z": {"model": f"{NS}:block/hollow_column", "x": 90},
    "axis=x": {"model": f"{NS}:block/hollow_column", "x": 90, "y": 90}}})
w(f"{A}/models/block/hollow_column.json", {"parent": "minecraft:block/cube_column",
                                           "textures": {"end": tex("hollow_column_top"), "side": tex("hollow_column_side")}})
w(f"{A}/models/item/hollow_column.json", {"parent": f"{NS}:block/hollow_column"})


def el(frm, to, faces):
    return {"from": frm, "to": to, "faces": {f: {"texture": t} for f, t in faces.items()}}


def brazier(lit):
    side, top = ("#side", "#top")
    sfx = "_lit" if lit else ""
    return {"parent": "minecraft:block/block",
            "textures": {"side": tex("brazier_side" + sfx), "top": tex("brazier_top" + sfx), "particle": tex("brazier_side")},
            "elements": [el([2, 0, 2], [14, 11, 14], {"north": side, "south": side, "east": side, "west": side, "up": top, "down": top}),
                         el([1, 10, 1], [15, 11, 15], {"north": side, "south": side, "east": side, "west": side, "down": top})]}


w(f"{A}/blockstates/hollow_brazier.json", {"variants": {"lit=false": {"model": f"{NS}:block/hollow_brazier"},
                                                        "lit=true": {"model": f"{NS}:block/hollow_brazier_lit"}}})
w(f"{A}/models/block/hollow_brazier.json", brazier(False))
w(f"{A}/models/block/hollow_brazier_lit.json", brazier(True))
w(f"{A}/models/item/hollow_brazier.json", {"parent": f"{NS}:block/hollow_brazier"})

# knocker: a pale shelf mushroom on a short stalk
w(f"{A}/models/block/hollow_knocker.json", {"parent": "minecraft:block/block",
                                            "textures": {"cap": tex("hollow_knocker"), "stalk": tex("hollow_column_side"), "particle": tex("hollow_knocker")},
                                            "elements": [el([6, 0, 6], [10, 9, 10], {f: "#stalk" for f in ("north", "south", "east", "west", "up", "down")}),
                                                         el([3, 9, 3], [13, 12, 13], {f: "#cap" for f in ("north", "south", "east", "west", "up", "down")}),
                                                         el([5, 12, 5], [11, 14, 11], {f: "#cap" for f in ("north", "south", "east", "west", "up", "down")})]})
w(f"{A}/blockstates/hollow_knocker.json", {"variants": {"": {"model": f"{NS}:block/hollow_knocker"}}})
w(f"{A}/models/item/hollow_knocker.json", {"parent": f"{NS}:block/hollow_knocker"})

w(f"{A}/models/item/hollow_bloom_cap.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{BOSS}/hollow_bloom_cap"}})
for egg in ("amanita_spawn_egg", "lamp_eater_spawn_egg", "hollow_spawn_spawn_egg"):
    w(f"{A}/models/item/{egg}.json", {"parent": "minecraft:item/template_spawn_egg"})


# ---------------- loot: mundane Act I drops; the shadow artifacts are the pack's table (DESIGN.md §11) ----------------
def pool(name, lo, hi, chance=None):
    p = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": name, "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}]}
    if chance is not None:
        p["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return p


w(f"{D}/loot_table/entities/amanita.json", {"type": "minecraft:entity", "pools": [
    pool("minecraft:red_mushroom", 4, 8), pool("minecraft:brown_mushroom", 4, 8), pool("minecraft:ink_sac", 4, 8),
    pool("minecraft:phantom_membrane", 1, 3), pool("minecraft:experience_bottle", 3, 6)]})
w(f"{D}/loot_table/entities/lamp_eater.json", {"type": "minecraft:entity", "pools": [pool("minecraft:glowstone_dust", 0, 2)]})
w(f"{D}/loot_table/entities/hollow_spawn.json", {"type": "minecraft:entity", "pools": [pool("minecraft:bone_meal", 0, 2)]})
# placeholder the pack overrides with its shadow artifacts (same id in the pack datapack replaces this file)
w(f"{D}/loot_table/gameplay/amanita/shadow_artifacts.json", {"type": "minecraft:gift", "pools": [
    pool("minecraft:ink_sac", 2, 4), pool("minecraft:ender_pearl", 1, 1, 0.5)]})


# ---------------- tags ----------------
w(f"{D}/tags/item/{BOSS}/igniters.json", {"values": ["minecraft:flint_and_steel", "minecraft:fire_charge", "minecraft:torch",
                                                     "minecraft:soul_torch", "minecraft:lantern", "minecraft:soul_lantern", "minecraft:blaze_rod"]})
w(f"{D}/tags/block/{BOSS}/snuff_immune.json", {"values": ["minecraft:nether_portal", "minecraft:end_portal", "minecraft:end_gateway"]})


def merge_tag(path, values):
    j = json.load(open(path)) if os.path.exists(path) else {"replace": False, "values": []}
    for v in values:
        if v not in j["values"]:
            j["values"].append(v)
    w(path, j)


merge_tag(os.path.join(RES, "data", "minecraft", "tags", "block", "mineable", "axe.json"), [f"{NS}:gill_shelf"])


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


adv("enter_hollow", None, f"{NS}:hollow_knocker", bg=f"{NS}:block/{BOSS}/hollow_wall")
adv("first_light", "enter_hollow", "minecraft:torch")
adv("relit", "first_light", f"{NS}:hollow_brazier", frame="goal")
adv("deep_bloom", "first_light", f"{NS}:gill_shelf")
adv("forced_open", "deep_bloom", "minecraft:lantern", frame="goal")
adv("bloom_wilted", "relit", f"{NS}:hollow_bloom_cap", frame="challenge",
    criteria={"code": {"trigger": "minecraft:impossible"},
              "kill": {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [
                  {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"type": f"{NS}:amanita"}}]}}})
adv("no_deep_bloom", "bloom_wilted", "minecraft:soul_lantern", frame="challenge", hidden=True)

# ---------------- lang ----------------
L = {
    "block.skylore_bosses.hollow_wall": "Hollow Wall",
    "block.skylore_bosses.hollow_loam": "Hollow Loam",
    "block.skylore_bosses.hollow_ceiling": "Hollow Ceiling",
    "block.skylore_bosses.hollow_column": "Stalk Column",
    "block.skylore_bosses.gill_shelf": "Gill Shelf",
    "block.skylore_bosses.hollow_brazier": "Hollow Brazier",
    "block.skylore_bosses.hollow_membrane": "Hollow Membrane",
    "block.skylore_bosses.hollow_knocker": "Knocking Cap",
    "item.skylore_bosses.hollow_bloom_cap": "Hollow Bloom Cap",
    "item.skylore_bosses.hollow_bloom_cap.lore": "Folded shut. It opens a little in the dark.",
    "item.skylore_bosses.amanita_spawn_egg": "Amanita Spawn Egg (bench test)",
    "item.skylore_bosses.lamp_eater_spawn_egg": "Lamp-eater Spawn Egg",
    "item.skylore_bosses.hollow_spawn_spawn_egg": "Hollow-spawn Spawn Egg",
    "entity.skylore_bosses.amanita": "Amanita, the Hollow Bloom",
    "entity.skylore_bosses.lamp_eater": "Lamp-eater",
    "entity.skylore_bosses.hollow_spawn": "Hollow-spawn",
    "entity.skylore_bosses.hollow_bolt": "Hollow Bolt",
    "amanita.bossbar.name": "Amanita, the Hollow Bloom",
    "amanita.bossbar.title": "%s  |  %s",
    "amanita.bossbar.light": "Hollow light",
    "amanita.bossbar.light_count": "Hollow light: %s sources burning",
    "amanita.bossbar.relight": "Relight the hollow: %s of %s sources",
    "amanita.bossbar.deep": "Deep bloom: forced open %s%% (needs light %s at her)",
    "amanita.status.p0": "The bloom opens. Nothing lands yet",
    "amanita.status.closed": "Closed in the dark: light %s of %s at her. Immune",
    "amanita.status.exposed": "Exposed: light %s of %s. Taking %s%%",
    "amanita.status.bared": "Bared: light %s of %s. Taking %s%%",
    "amanita.status.p2": "Snuffed: light %s of %s at her. Relight the hollow",
    "amanita.status.p4": "Deep bloom: light %s of %s at her. Immune, regrowing",
    "amanita.status.down": "Wilted",
    "amanita.title.bloom": "The Hollow Bloom",
    "amanita.title.bloom.sub": "Case file: Amanita. Last seen retreating from light.",
    "amanita.title.p1": "Dark immunity",
    "amanita.title.p1.sub": "Light her, or you cannot hurt her.",
    "amanita.title.snuff": "Full snuff",
    "amanita.title.snuff.sub": "The flower closes. Every light goes with it.",
    "amanita.title.lit_duel": "Lit duel",
    "amanita.title.lit_duel.sub": "The hollow burns again. Keep it burning.",
    "amanita.title.lit_duel_dark.sub": "She waited out the dark. Light her anyway.",
    "amanita.title.deep": "Deep bloom",
    "amanita.title.deep.sub": "Left in the dark too long. She is rooting.",
    "amanita.title.forced": "Forced open",
    "amanita.title.forced.sub": "The bloom is torn back. Hit her now.",
    "amanita.title.victory": "Wilted",
    "amanita.title.victory.sub": "Case file closed. The hollow keeps its dark, and nothing else.",
    "amanita.log.p1": "[CASE FILE] Subject is untouchable in darkness. The hollow holds %s braziers, stocked, unlit.",
    "amanita.log.light_placed": "A light in the hollow. Bring it to her.",
    "amanita.log.snuffed": "[CASE FILE] Full snuff: %s lights out. Relight under pressure.",
    "amanita.log.closing": "[CASE FILE] Closing snuff: %s lights out. Deep bloom ends.",
    "amanita.log.snuff_pulse": "Snuffed: %s lights out around her.",
    "amanita.log.snuff_broken": "The snuff breaks. She reels.",
    "amanita.log.staggered": "She reels from the blow.",
    "amanita.log.recoil": "The bloom recoils from the light.",
    "amanita.log.hushed": "Hushed. The hollow will not take a light yet.",
    "amanita.log.hushed_all": "Hushed. No light will catch for a moment.",
    "amanita.log.rescue": "Returned to the hollow mouth. The drop was noted.",
    "amanita.gate.p0": "The bloom is still opening. Nothing lands yet.",
    "amanita.gate.sealed": "She has closed around herself. Wait for the flower to open.",
    "amanita.gate.closed": "Nothing lands. She is closed in the dark (light %s of %s at her). Bring light to her.",
    "amanita.gate.exposed": "Lit (light %s at her). Hits land at %s%%.",
    "amanita.gate.bared": "Bared (light %s at her). Hits land at %s%%.",
    "amanita.warn.bloom_open": "The bloom opens. Your blows do nothing to her in the dark.",
    "amanita.warn.snuff": "Snuff pulse. Lights near her will go out.",
    "amanita.warn.full_snuff": "She is closing the flower. Every light in the hollow will go out.",
    "amanita.warn.closing": "Closing snuff. Force her open before it lands.",
    "amanita.warn.howl": "Darkness howl. Place your light before it lands.",
    "amanita.warn.slam": "Bloom slam. Jump the ring; floor lights will scatter.",
    "amanita.warn.dash_carrier": "She is coming for the light in your hand.",
    "amanita.brazier.needs_flame": "It holds coal. It needs a flame: flint and steel, a fire charge or a torch.",
    "amanita.origin.call": "The hollow calls you (%s of %s). Stand in the light.",
    "amanita.origin.rooted": "Rooted in the hollow. The dark you keep is hers too.",
    "amanita.origin.tenebris_chill": "The snuff takes your dark with it. Chilled.",
    "amanita.origin.laevis_ignite": "The coal takes from your hand.",
    "amanita.knocker.denied": "The cap does not answer. Not yet.",
    "amanita.knocker.readmit": "The membrane parts.",
    "amanita.knocker.cleared": "The hollow is quiet. Nothing blooms here now.",
    "amanita.knocker.unregistered": "This cap is not attached to any hollow.",
    "advancements.amanita.enter_hollow.title": "Unlit Premises",
    "advancements.amanita.enter_hollow.description": "Wake Amanita, the Hollow Bloom, in her hollow",
    "advancements.amanita.first_light.title": "Point Made",
    "advancements.amanita.first_light.description": "Land a hit on Amanita while she stands in light",
    "advancements.amanita.relit.title": "Relit",
    "advancements.amanita.relit.description": "Relight the hollow after her full snuff",
    "advancements.amanita.deep_bloom.title": "Left in the Dark",
    "advancements.amanita.deep_bloom.description": "Let Amanita go into deep bloom",
    "advancements.amanita.forced_open.title": "Forced Open",
    "advancements.amanita.forced_open.description": "Force Amanita out of deep bloom with light",
    "advancements.amanita.bloom_wilted.title": "Wilted",
    "advancements.amanita.bloom_wilted.description": "Defeat Amanita, the Hollow Bloom",
    "advancements.amanita.no_deep_bloom.title": "No Room for Roots",
    "advancements.amanita.no_deep_bloom.description": "Defeat Amanita without ever letting her deep bloom",
}
SUBTITLES = {
    "absorb": "Blow sinks into the cap", "bared": "Bloom tears open", "bloom.open": "Bloom opens", "bloom.windup": "Petals gather",
    "bolt.cast": "Hollow bolt flies", "bolt.impact": "Hollow bolt bursts", "bolt.windup": "Shadow gathers", "brazier.light": "Brazier catches",
    "call.root": "The hollow takes hold", "dash.go": "Amanita lunges", "dash.smother": "A light is smothered", "dash.windup": "Amanita crouches",
    "death": "Amanita wilts", "deep.bloom": "Deep bloom", "deep.windup": "Petals fling open", "eater.burrow": "Lamp-eater burrows",
    "eater.chew": "Lamp-eater chews", "eater.death": "Lamp-eater bursts", "eater.emerge": "Lamp-eater climbs out", "eater.gulp": "A light is eaten",
    "full.windup": "The flower closes", "howl": "Darkness howl", "howl.inhale": "Amanita inhales", "hurt": "Amanita tears",
    "idle.breathe": "Gills breathe", "knocker": "Knocking cap", "lash.swing": "Tendrils lash", "lash.windup": "Tendrils draw back",
    "lockdown": "Hollow seals", "recoil": "Amanita recoils", "rise": "Amanita rises", "sink": "Amanita sinks into the loam",
    "slam.wave": "Loam ripples", "slam.windup": "Amanita rises up", "snuff.full": "Full snuff", "snuff.gather": "Petals fold",
    "snuff.hiss": "Lights hiss out", "snuff.pulse": "Snuff pulse", "snuff.windup": "The hollow draws in", "spawn.dissolve": "Hollow-spawn sinks",
    "spawn.emerge": "Hollow-spawn climbs out", "spawn.swing": "Hollow-spawn swings", "veil.bloom": "Spore veil blooms",
    "veil.windup": "Spores shaken loose", "victory": "The hollow falls quiet",
}
with open(os.path.join(ROOT, "tools", "sound_ids.txt")) as f:
    for sid in f.read().split():
        L[f"subtitles.{NS}.{BOSS}.{sid}"] = SUBTITLES.get(sid, "Amanita " + sid.replace(".", " "))
lang_path = f"{A}/lang/en_us.json"
merged = json.load(open(lang_path, encoding="utf-8")) if os.path.exists(lang_path) else {}
merged.update(L)
w(lang_path, dict(sorted(merged.items())))
print("data generated:", len(L), "lang keys")
