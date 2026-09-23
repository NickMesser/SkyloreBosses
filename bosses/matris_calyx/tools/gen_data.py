"""Generate the mod's static data/assets JSON (lang, models, blockstates, loot, recipes, tags, advancements,
Proto-World dimension). Run after export_to_mod.py.  python gen_data.py"""
import json, os
import boss_env
from PIL import Image

ROOT = boss_env.BOSS_DIR
REPO = boss_env.REPO
RES = os.path.join(REPO, "src", "main", "resources")
NS = "skylore_bosses"
BOSS = "matris_calyx"
A = os.path.join(RES, "assets", NS)
D = os.path.join(RES, "data", NS)


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)


CUBE_BLOCKS = ["flesh", "hardened_flesh", "flesh_membrane", "glow_vein", "heart_core", "rib_bone", "syringe_cache", "ruptured_vent"]
DROPS_SELF = ["flesh", "flesh_membrane", "glow_vein", "ruptured_vent"]
ARMS = ["nerve_arm", "grasping_arm", "slam_arm", "charging_arm", "mouth_arm", "spitting_arm"]
ENTITIES = ARMS + ["calyx_bloom", "spore_thrall", "spore_mite", "infected_drifter"]

for b in CUBE_BLOCKS:
    w(f"{A}/blockstates/{b}.json", {"variants": {"": {"model": f"{NS}:block/{b}"}}})
    w(f"{A}/models/block/{b}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{BOSS}/{b}"}})
    w(f"{A}/models/item/{b}.json", {"parent": f"{NS}:block/{b}"})
w(f"{A}/blockstates/spore_vent.json", {"variants": {"": {"model": f"{NS}:block/spore_vent"}}})
w(f"{A}/models/block/spore_vent.json", {"textures": {"particle": f"{NS}:block/{BOSS}/flesh"}})
w(f"{A}/models/item/spore_vent.json", {"parent": f"{NS}:block/ruptured_vent"})
for i in ["purgative_syringe", "calyx_heart"]:
    w(f"{A}/models/item/{i}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{BOSS}/{i}"}})
for e in ENTITIES:
    w(f"{A}/models/item/{e}_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})

# ---------------- loot ----------------
for b in DROPS_SELF:
    w(f"{D}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{b}"}],
                                                                                "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def ent_loot(items):
    return {"type": "minecraft:entity", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": n, "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}]}
        for n, lo, hi in items]}


for a in ARMS:
    w(f"{D}/loot_table/entities/{a}.json", ent_loot([(f"{NS}:flesh", 8, 16), ("minecraft:bone", 4, 10), (f"{NS}:glow_vein", 2, 5)]))
w(f"{D}/loot_table/entities/mouth_arm.json", ent_loot([(f"{NS}:flesh", 8, 16), (f"{NS}:purgative_syringe", 2, 2), ("minecraft:bone", 4, 10)]))
w(f"{D}/loot_table/entities/calyx_bloom.json", ent_loot([(f"{NS}:flesh", 32, 64), (f"{NS}:heart_core", 1, 1), (f"{NS}:glow_vein", 8, 16)]))
w(f"{D}/loot_table/entities/spore_thrall.json", ent_loot([("minecraft:rotten_flesh", 0, 2), (f"{NS}:flesh", 0, 1)]))
w(f"{D}/loot_table/entities/spore_mite.json", {"type": "minecraft:entity", "pools": []})
w(f"{D}/loot_table/entities/infected_drifter.json", ent_loot([("minecraft:slime_ball", 0, 2)]))

# ---------------- recipe ----------------
w(f"{D}/recipe/purgative_syringe.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                        "pattern": [" I ", "GHG", " B "],
                                        "key": {"I": {"item": "minecraft:iron_nugget"}, "G": {"item": "minecraft:glass_pane"},
                                                "H": {"item": "minecraft:honey_bottle"}, "B": {"item": "minecraft:glow_berries"}},
                                        "result": {"id": f"{NS}:purgative_syringe", "count": 2}})

# ---------------- tags ----------------
w(f"{D}/tags/block/{BOSS}/laser_proof.json", {"values": [f"{NS}:{b}" for b in CUBE_BLOCKS + ["spore_vent"]] +
                                       ["minecraft:bedrock", "minecraft:barrier", "minecraft:obsidian", "minecraft:crying_obsidian"]})
w(f"{D}/tags/item/doctor_tools.json", {"values": []})
w(f"{D}/tags/item/{BOSS}/cures.json", {"values": [f"{NS}:purgative_syringe"]})
w(f"{D}/tags/entity_type/{BOSS}/flight_vehicles.json", {"values": [{"id": f"immersive_aircraft:{v}", "required": False}
                                                            for v in ("airship", "biplane", "gyrodyne", "quadrocopter", "cargo_airship", "warship", "bamboo_hopper")]})
w(f"{D}/tags/entity_type/{BOSS}/parts.json", {"values": [f"{NS}:{e}" for e in ARMS + ["calyx_bloom"]]})
w(os.path.join(RES, "data", "minecraft", "tags", "block", "mineable", "hoe.json"),
  {"replace": False, "values": [f"{NS}:{b}" for b in DROPS_SELF]})

# ---------------- advancements ----------------
def adv(name, parent, icon, frame="task", criteria=None, hidden=False, bg=None):
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


adv("enter_proto_world", None, f"{NS}:flesh_membrane", bg=f"{NS}:block/{BOSS}/flesh",
    criteria={"code": {"trigger": "minecraft:impossible"},
              "dim": {"trigger": "minecraft:changed_dimension", "conditions": {"to": f"{NS}:proto_world"}}})
adv("first_arm", "enter_proto_world", f"{NS}:glow_vein")
adv("nerve_first", "first_arm", f"{NS}:nerve_arm_spawn_egg", frame="goal", hidden=True)
adv("all_arms", "first_arm", f"{NS}:rib_bone", frame="goal")
adv("bloom_slain", "all_arms", f"{NS}:calyx_heart", frame="challenge",
    criteria={"code": {"trigger": "minecraft:impossible"},
              "kill": {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [
                  {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"type": f"{NS}:calyx_bloom"}}]}}})

# ---------------- dimension ----------------
w(f"{D}/dimension_type/proto_world.json", {
    "ultrawarm": False, "natural": False, "piglin_safe": False, "respawn_anchor_works": False, "bed_works": False,
    "has_raids": False, "has_skylight": False, "has_ceiling": False, "coordinate_scale": 1.0, "ambient_light": 0.08,
    "fixed_time": 18000, "logical_height": 384, "effects": "minecraft:the_end", "infiniburn": "#minecraft:infiniburn_end",
    "min_y": -64, "height": 384, "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0})
w(f"{D}/dimension/proto_world.json", {"type": f"{NS}:proto_world", "generator": {"type": "minecraft:flat", "settings": {
    "biome": f"{NS}:proto_stomach", "lakes": False, "features": False, "structure_overrides": [],
    "layers": [{"block": "minecraft:air", "height": 1}]}}})
w(f"{D}/worldgen/biome/proto_stomach.json", {
    "has_precipitation": False, "temperature": 0.9, "downfall": 0.0,
    "effects": {"fog_color": 0x3A0F18, "sky_color": 0x1A0508, "water_color": 0x6E8A18, "water_fog_color": 0x2A3808,
                "grass_color": 0x7A2A30, "foliage_color": 0x7A2A30,
                "particle": {"options": {"type": "minecraft:crimson_spore"}, "probability": 0.012},
                "ambient_sound": f"{NS}:{BOSS}.ambient.stomach",
                "mood_sound": {"sound": f"{NS}:{BOSS}.ambient.stomach", "tick_delay": 3000, "block_search_extent": 8, "offset": 2.0}},
    "spawners": {}, "spawn_costs": {}, "carvers": {}, "features": []})

# ---------------- lang ----------------
L = {
    "itemGroup.skylore_bosses": "Skylore Bosses",
    "item.skylore_bosses.purgative_syringe": "Purgative Syringe",
    "item.skylore_bosses.purgative_syringe.desc": "Use: -40 infection. Use on another player to cure them.",
    "item.skylore_bosses.purgative_syringe.canon": "It works. It always works.",
    "item.skylore_bosses.calyx_heart": "Heart of the Calyx",
    "block.skylore_bosses.flesh": "Flesh", "block.skylore_bosses.hardened_flesh": "Hardened Flesh",
    "block.skylore_bosses.flesh_membrane": "Digestive Membrane", "block.skylore_bosses.glow_vein": "Glow Vein",
    "block.skylore_bosses.heart_core": "Heart Core", "block.skylore_bosses.rib_bone": "Rib Bone",
    "block.skylore_bosses.spore_vent": "Spore Vent", "block.skylore_bosses.ruptured_vent": "Ruptured Vent",
    "block.skylore_bosses.syringe_cache": "Syringe Cache",
    "entity.skylore_bosses.nerve_arm": "Nerve Arm", "entity.skylore_bosses.grasping_arm": "Grasping Arm",
    "entity.skylore_bosses.slam_arm": "Slam Arm", "entity.skylore_bosses.charging_arm": "Charging Arm",
    "entity.skylore_bosses.mouth_arm": "Mouth Arm", "entity.skylore_bosses.spitting_arm": "Spitting Arm",
    "entity.skylore_bosses.calyx_bloom": "Calyx Bloom", "entity.skylore_bosses.bile_glob": "Bile Glob",
    "entity.skylore_bosses.spore_thrall": "Spore Thrall", "entity.skylore_bosses.spore_mite": "Spore Mite",
    "entity.skylore_bosses.infected_drifter": "Infected Drifter",
    "matris_calyx.bossbar.matris": "Matris Calyx, the Parasite Mother",
    "matris_calyx.bossbar.bloom": "Calyx Bloom",
    "matris_calyx.building": "The body is forming... %s%%",
    "matris_calyx.title.awaken": "Matris Calyx",
    "matris_calyx.title.awaken.sub": "The islands were never a place. They were a stomach.",
    "matris_calyx.title.heart": "The heart opens",
    "matris_calyx.title.heart.sub": "Something that was always watching is finally looking.",
    "matris_calyx.title.victory": "The stomach is quiet",
    "matris_calyx.title.victory.sub": "The farm has no farmer.",
    "matris_calyx.arm_severed": "%s severed (%s/6)",
    "matris_calyx.nerve_dead": "She flinches.",
    "matris_calyx.infection.taken": "The spores take you. You wake at the anchor.",
    "matris_calyx.cache.empty": "The cache is empty for you this fight.",
    "matris_calyx.cache.took": "You take %s purgative syringes.",
    "matris_calyx.hud.infection": "Infection %s",
    "matris_calyx.attack.peristalsis.warn": "The ground swallows...",
    "matris_calyx.attack.bile_rain.warn": "Bile gathers above the flight lanes...",
    "matris_calyx.attack.retina_flash.warn": "She notices you. Look away.",
    "matris_calyx.attack.root_grip.warn": "Roots stir beneath your feet...",
    "matris_calyx.attack.swallow.warn": "The stomach inhales...",
    "matris_calyx.attack.spore_exhale.warn": "The vents draw breath...",
    "advancements.matris_calyx.enter_proto_world.title": "Proto-World",
    "advancements.matris_calyx.enter_proto_world.description": "Step inside the body the islands grew from",
    "advancements.matris_calyx.first_arm.title": "First Incision",
    "advancements.matris_calyx.first_arm.description": "Sever one of Matris's appendages",
    "advancements.matris_calyx.nerve_first.title": "Cut the Nerve",
    "advancements.matris_calyx.nerve_first.description": "Sever the Nerve Arm before any other",
    "advancements.matris_calyx.all_arms.title": "Six Wounds",
    "advancements.matris_calyx.all_arms.description": "Sever all six appendages",
    "advancements.matris_calyx.bloom_slain.title": "Savior of Atmos",
    "advancements.matris_calyx.bloom_slain.description": "Destroy the Calyx Bloom",
}
for e in ENTITIES:
    L[f"item.skylore_bosses.{e}_spawn_egg"] = L[f"entity.skylore_bosses.{e}"] + " Spawn Egg"
for b in CUBE_BLOCKS + ["spore_vent"]:
    pass
with open(os.path.join(ROOT, "tools", "sound_ids.txt")) as f:
    for sid in f.read().split():
        words = sid.replace("_", " ").split(".")
        subject = {"arm": "Arm", "bloom": "Calyx Bloom", "vent": "Spore vent", "thrall": "Spore Thrall", "mite": "Spore Mite",
                   "drifter": "Drifter", "bile": "Bile", "attack": "Matris", "ambient": "Stomach", "encounter": "Matris",
                   "infection": "Infection", "syringe": "Syringe"}.get(words[0], words[0].title())
        L[f"subtitles.{NS}.{BOSS}.{sid}"] = f"{subject} {' '.join(words[1:])}".strip().capitalize()
# en_us.json is shared by every boss: merge this boss's keys into it
lang_path = f"{A}/lang/en_us.json"
merged = json.load(open(lang_path, encoding="utf-8")) if os.path.exists(lang_path) else {}
merged.update(L)
w(lang_path, dict(sorted(merged.items())))

# logo from the Bloom render
src = os.path.join(boss_env.MODELS, "calyx_bloom", "renders", "front34.png")
if os.path.exists(src):
    im = Image.open(src).convert("RGBA")
    bbox = im.getbbox()
    im = im.crop(bbox)
    s = max(im.size)
    sq = Image.new("RGBA", (s, s), (26, 5, 8, 255))
    sq.alpha_composite(im, ((s - im.size[0]) // 2, (s - im.size[1]) // 2))
    sq.resize((256, 256)).save(os.path.join(RES, "logo.png"))
    os.makedirs(os.path.join(RES, "pack.mcmeta").rsplit(os.sep, 1)[0], exist_ok=True)
print("data generated")
