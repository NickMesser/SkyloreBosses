"""Copy the Amanita Blockbench exports into the mod and generate the derived assets: geo/animations/textures,
AmanitaLocators.java, AmanitaSoundIds.java, particle descriptions, block/item textures (from the Codex-painted sources in
textures/src/, see codex_textures.py), synthesized sounds and this boss's sounds.json entries.
    python export_to_mod.py            # everything
    python export_to_mod.py --no-sounds"""
import json, os, shutil, sys
import boss_env
import models, sounds
from sounds import (bell, boom, chant, choir, clicks, crackle, drips, hiss, hum, rumble, squelch, swell, thump, whip)

ROOT = boss_env.BOSS_DIR
REPO = boss_env.REPO
MOD = os.path.join(REPO, "src", "main")
NS = "skylore_bosses"
BOSS = "amanita"
ASSETS = os.path.join(MOD, "resources", "assets", NS)
JAVA = os.path.join(MOD, "java", "net", "teamaof", "skylorebosses", "bosses", "amanita")
SRC = boss_env.TEXTURE_SRC
SOUND_IDS = os.path.join(ROOT, "tools", "sound_ids.txt")

ENTITY_MODELS = ["amanita", "lamp_eater", "hollow_spawn", "hollow_bolt"]
FACTORIES = {f.__name__: f for f in models.ALL}

# sounds fired from Java rather than animation keyframes
EXTRA_SOUNDS = ["absorb", "bared", "bloom.open", "bolt.cast", "bolt.impact", "brazier.light", "call.root", "dash.go", "dash.smother",
                "death", "deep.bloom", "eater.burrow", "eater.chew", "eater.death", "eater.emerge", "eater.gulp", "howl", "knocker",
                "lash.swing", "lockdown", "recoil", "sink", "slam.wave", "snuff.full", "snuff.hiss", "snuff.pulse", "snuff.windup",
                "spawn.dissolve", "spawn.emerge", "veil.bloom", "victory"]

# first matching key wins, so specific ids come before the words they contain
sounds.EXTRA_RULES = [
    ("idle.breathe", hiss, {"d": 3.0, "lo": 200, "hi": 1600, "a": 0.9}, 1.0), ("rise", rumble, {"d": 5.0, "lo": 20, "hi": 160}),
    ("bloom.windup", swell, {"d": 2.0}), ("bloom.open", squelch, {"d": 1.2, "bubbles": 24, "body": 0.9}, 0.7),
    ("lash.windup", hiss, {"d": 0.6, "lo": 800, "hi": 5000, "a": 0.1}), ("lash.swing", whip, {"d": 0.35}, 0.8),
    ("bolt.windup", swell, {"d": 1.0}), ("bolt.cast", hiss, {"d": 0.4, "lo": 300, "hi": 3000, "a": 0.02}),
    ("bolt.impact", squelch, {"d": 0.4, "bubbles": 8, "body": 0.4}),
    ("veil.windup", hiss, {"d": 1.4, "lo": 1500, "hi": 7000, "a": 0.5}), ("veil.bloom", hiss, {"d": 1.6, "lo": 400, "hi": 4000, "a": 0.2}),
    ("snuff.gather", swell, {"d": 2.0}), ("snuff.windup", chant, {"d": 3.0, "f": 62}), ("snuff.full", boom, {"d": 2.5, "f0": 60, "f1": 22, "crack": 0.2}),
    ("snuff.pulse", boom, {"d": 1.2, "f0": 90, "f1": 35, "crack": 0.3}), ("snuff.hiss", hiss, {"d": 0.8, "lo": 1200, "hi": 8000, "a": 0.05}),
    ("full.windup", chant, {"d": 3.0, "f": 55}), ("howl.inhale", hiss, {"d": 1.5, "lo": 150, "hi": 1500, "a": 1.0}),
    ("howl", chant, {"d": 2.2, "f": 82}), ("slam.windup", rumble, {"d": 1.5, "lo": 30, "hi": 200}),
    ("slam.wave", thump, {"d": 0.4, "f0": 90, "f1": 40, "noise": 0.6}), ("dash.windup", hiss, {"d": 1.0, "lo": 300, "hi": 3000, "a": 0.4}),
    ("dash.go", whip, {"d": 0.5}, 0.6), ("dash.smother", squelch, {"d": 0.7, "bubbles": 14, "body": 0.8}, 0.8),
    ("deep.windup", chant, {"d": 2.0, "f": 49}), ("deep.bloom", choir, {"d": 4.0}), ("recoil", squelch, {"d": 0.5, "bubbles": 10, "body": 0.5}, 1.2),
    ("bared", squelch, {"d": 1.5, "bubbles": 30, "body": 1.0}, 0.7), ("absorb", hiss, {"d": 0.3, "lo": 300, "hi": 2000, "a": 0.02}),
    ("hurt", squelch, {"d": 0.4, "bubbles": 8, "body": 0.5}), ("death", chant, {"d": 6.0, "f": 41}), ("victory", choir, {"d": 5.0}),
    ("lockdown", squelch, {"d": 2.0, "lo": 80, "hi": 600, "bubbles": 30, "body": 1.0}), ("knocker", thump, {"d": 0.3, "f0": 180, "f1": 90, "noise": 0.3}),
    ("brazier.light", crackle, {"d": 1.0, "density": 80}), ("call.root", hum, {"d": 1.0, "f": 49}), ("sink", squelch, {"d": 1.2, "bubbles": 20, "body": 1.0}, 0.7),
    ("eater.emerge", squelch, {"d": 0.8, "bubbles": 16, "body": 0.6}, 1.4), ("eater.chew", clicks, {"d": 0.6, "count": 8, "lo": 400, "hi": 3000}),
    ("eater.gulp", squelch, {"d": 0.5, "lo": 100, "hi": 600, "bubbles": 5, "body": 1.0}, 1.3), ("eater.burrow", drips, {"d": 1.0, "count": 6}),
    ("eater.death", squelch, {"d": 0.5, "bubbles": 12, "body": 0.5}, 1.5), ("spawn.emerge", squelch, {"d": 1.2, "bubbles": 24, "body": 0.9}),
    ("spawn.swing", whip, {"d": 0.3}, 1.1), ("spawn.dissolve", hiss, {"d": 1.0, "lo": 300, "hi": 3000, "a": 0.4}),
    ("bell", bell, {"d": 2.0, "f": 220}),
]


def mk(*p):
    d = os.path.join(*p)
    os.makedirs(d, exist_ok=True)
    return d


def copy_models():
    for n in ENTITY_MODELS:
        src = os.path.join(boss_env.MODELS, n)
        shutil.copy(os.path.join(src, f"{n}.geo.json"), os.path.join(mk(ASSETS, "geo", "entity", BOSS), f"{n}.geo.json"))
        shutil.copy(os.path.join(src, f"{n}.animation.json"), os.path.join(mk(ASSETS, "animations", "entity", BOSS), f"{n}.animation.json"))
        shutil.copy(os.path.join(src, f"{n}.png"), os.path.join(mk(ASSETS, "textures", "entity", BOSS), f"{n}.png"))
        shutil.copy(os.path.join(src, f"{n}_glowmask.png"), os.path.join(mk(ASSETS, "textures", "entity", BOSS), f"{n}_glowmask.png"))


def _pairs(locs):
    return [f'"{l["name"]}", new float[]{{{l["pos"][0]:.2f}f, {l["pos"][1]:.2f}f, {l["pos"][2]:.2f}f}}' for l in locs]


def locators_java():
    lines, sound_ids = [], set()
    for n in ENTITY_MODELS:
        m = FACTORIES[n]()
        for a in m.anims:
            for s in a["sounds"]:
                sound_ids.add(s["effect"].split(":", 1)[1])
        lines.append(f'        ModelLocators.register("{n}", Map.ofEntries({", ".join("Map.entry(" + e + ")" for e in _pairs(m.locators))}));')
    src = f"""package net.teamaof.skylorebosses.bosses.amanita;

import java.util.Map;
import net.teamaof.skylorebosses.core.fx.ModelLocators;

/** GENERATED by bosses/amanita/tools/export_to_mod.py from the Blockbench specs. Rest-pose locators in model pixels. */
public final class AmanitaLocators {{
    private AmanitaLocators() {{}}

    public static void register() {{
{chr(10).join(lines)}
    }}
}}
"""
    with open(os.path.join(mk(JAVA), "AmanitaLocators.java"), "w", newline="\n") as f:
        f.write(src)
    return sorted(sound_ids | set(EXTRA_SOUNDS))


# ---------------- particle descriptions (sprites are the shared glow/spark/puff sets) ----------------
PARTICLES = {"hollow_spore": ("spark", 3), "gill_glow": ("glow", 3), "snuff_smoke": ("puff", 4), "veil_mist": ("puff", 4)}


def particles():
    pdir = mk(ASSETS, "particles")
    for name, (fam, n) in PARTICLES.items():
        with open(os.path.join(pdir, f"{name}.json"), "w", newline="\n") as f:
            json.dump({"textures": [f"{NS}:{fam}_{i}" for i in range(n)]}, f, indent=2)


# ---------------- block & item textures (Codex sources) ----------------
BLOCK_TEXTURES = ["hollow_wall", "hollow_loam", "hollow_ceiling", "hollow_column_side", "hollow_column_top", "gill_shelf",
                  "brazier_side", "brazier_side_lit", "brazier_top", "brazier_top_lit", "hollow_membrane", "hollow_knocker"]


def blocks_and_items():
    bt = mk(ASSETS, "textures", "block", BOSS)
    it = mk(ASSETS, "textures", "item", BOSS)
    for n in BLOCK_TEXTURES:
        shutil.copy(os.path.join(SRC, f"{n}.png"), os.path.join(bt, f"{n}.png"))
    shutil.copy(os.path.join(SRC, "hollow_bloom_cap.png"), os.path.join(it, "hollow_bloom_cap.png"))


# ---------------- sounds ----------------
def sound_files(ids, synth=True):
    sdir = mk(ASSETS, "sounds", BOSS)
    path = os.path.join(ASSETS, "sounds.json")
    j = json.load(open(path)) if os.path.exists(path) else {}
    j = {k: v for k, v in j.items() if not k.startswith(BOSS + ".")}
    for sid in ids:
        fname = sid.replace(".", "_")
        if synth:
            sounds.write(sid, os.path.join(sdir, f"{fname}.ogg"))
        entry = {"subtitle": f"subtitles.{NS}.{BOSS}.{sid}", "sounds": [{"name": f"{NS}:{BOSS}/{fname}"}]}
        if sid.split(".")[0] in ("lockdown", "snuff", "howl", "deep", "death", "victory", "rise", "full"):
            entry["sounds"][0]["attenuation_distance"] = 64
        j[f"{BOSS}.{sid}"] = entry
    with open(path, "w", newline="\n") as f:
        json.dump(dict(sorted(j.items())), f, indent=2)
    with open(SOUND_IDS, "w", newline="\n") as f:
        f.write("\n".join(ids))


def sound_ids_java(ids):
    body = ",\n            ".join(f'"{s}"' for s in ids)
    src = f"""package net.teamaof.skylorebosses.bosses.amanita;

/** GENERATED by bosses/amanita/tools/export_to_mod.py: Amanita sound ids (registered as skylore_bosses:amanita.<id>). */
public final class AmanitaSoundIds {{
    public static final String[] ALL = {{
            {body}
    }};

    private AmanitaSoundIds() {{}}
}}
"""
    with open(os.path.join(mk(JAVA), "AmanitaSoundIds.java"), "w", newline="\n") as f:
        f.write(src)


if __name__ == "__main__":
    copy_models()
    ids = locators_java()
    particles()
    blocks_and_items()
    sound_files(ids, synth="--no-sounds" not in sys.argv)
    sound_ids_java(ids)
    print(len(ids), "sounds;", len(ENTITY_MODELS), "models exported")
