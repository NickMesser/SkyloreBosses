"""Copy the Null Router Blockbench exports into the mod and generate the derived assets: geo/animations/textures,
RouterLocators.java, RouterSoundIds.java, particle descriptions, block/item textures (from the Codex-painted sources in
textures/src/, see codex_textures.py; console faces and lamp modes are derived here so a console always shows exactly
its lamp's glyph), synthesized sounds and this boss's sounds.json entries.
    python export_to_mod.py            # everything
    python export_to_mod.py --no-sounds"""
import json, os, shutil, sys
import numpy as np
from PIL import Image
import boss_env
import models, sounds
from sounds import (beep, bell, boom, choir, clank, clicks, crackle, electric, hiss, hum, klaxon, laser_charge, modem, servo,
                    swell, thump, zap)

ROOT = boss_env.BOSS_DIR
REPO = boss_env.REPO
MOD = os.path.join(REPO, "src", "main")
NS = "skylore_bosses"
BOSS = "null_router"
ASSETS = os.path.join(MOD, "resources", "assets", NS)
JAVA = os.path.join(MOD, "java", "net", "teamaof", "skylorebosses", "bosses", "nullrouter")
SRC = boss_env.TEXTURE_SRC
SOUND_IDS = os.path.join(ROOT, "tools", "sound_ids.txt")

ENTITY_MODELS = ["null_router", "retry_packet"]
FACTORIES = {f.__name__: f for f in models.ALL}

# sounds fired from Java rather than animation keyframes
EXTRA_SOUNDS = ["lockdown", "stall", "rotate.warn", "rotate", "request", "request.cleared", "console.set", "console.flipped", "arm",
                "ack", "ack.warn", "ack.close", "coolant.hiss", "coolant.vent", "drain", "short", "misroute", "misroute.windup",
                "gate.open", "terminal", "victory", "storm.alarm", "storm.port", "dock", "release", "chime.windup", "chime",
                "ping", "ping.console", "lance.charge", "lance.fire", "burst.windup", "burst.eject", "burst.fizzle", "flick.windup",
                "flick.refused", "ttl.windup", "ttl.pulse", "nak", "death", "retry.reroute", "retry.uplink", "retry.zap",
                "retry.drop", "retry.timeout", "retry.death"]

# first matching key wins, so specific ids come before the words they contain
sounds.EXTRA_RULES = [
    ("idle.hum", hum, {"d": 3.0, "f": 55}, 1.0), ("boot", servo, {"d": 5.0, "f0": 80, "f1": 640}),
    ("retry.reroute", zap, {"d": 0.3}, 1.4), ("retry.uplink", modem, {"d": 0.5, "count": 5}), ("retry.zap", zap, {"d": 0.3}, 1.3),
    ("retry.drop", clicks, {"d": 0.2, "count": 2, "lo": 1500, "hi": 6000}), ("retry.timeout", beep, {"d": 0.3, "f": 660, "count": 1}),
    ("retry.death", crackle, {"d": 0.4, "density": 100}),
    ("lockdown", boom, {"d": 2.0, "f0": 70, "f1": 30, "crack": 0.3}), ("stall", beep, {"d": 0.8, "f": 440, "count": 2}),
    ("rotate.warn", beep, {"d": 1.0, "f": 880, "count": 4}), ("rotate", modem, {"d": 0.6, "count": 5}),
    ("request.cleared", beep, {"d": 0.3, "f": 1320, "count": 1}), ("request", modem, {"d": 1.0, "count": 8}),
    ("console.set", clicks, {"d": 0.12, "count": 1, "lo": 1500, "hi": 6000}), ("console.flipped", zap, {"d": 0.4}),
    ("arm", servo, {"d": 0.6, "f0": 400, "f1": 900}),
    ("ack.warn", beep, {"d": 0.8, "f": 1760, "count": 4}), ("ack.close", servo, {"d": 0.6, "f0": 900, "f1": 300}),
    ("ack", bell, {"d": 2.0, "f": 523}),
    ("coolant.hiss", hiss, {"d": 1.2, "lo": 1500, "hi": 8000, "a": 0.05}), ("coolant.vent", hiss, {"d": 1.8, "lo": 400, "hi": 5000, "a": 0.3}),
    ("drain", hiss, {"d": 1.2, "lo": 200, "hi": 2000, "a": 0.4}), ("short", zap, {"d": 0.5}),
    ("misroute.windup", klaxon, {"d": 0.5}), ("misroute", electric, {"d": 0.8}),
    ("gate.open", servo, {"d": 0.5, "f0": 300, "f1": 600}), ("terminal", beep, {"d": 0.4, "f": 1200, "count": 2}),
    ("victory", choir, {"d": 5.0}), ("storm.alarm", klaxon, {"d": 2.5}), ("storm.port", clank, {"d": 0.4}),
    ("dock", boom, {"d": 0.8, "f0": 90, "f1": 40, "crack": 0.2}), ("release", servo, {"d": 0.5, "f0": 200, "f1": 700}),
    ("chime.windup", swell, {"d": 2.0}), ("chime", bell, {"d": 3.0, "f": 392}),
    ("ping.console", beep, {"d": 0.25, "f": 1568, "count": 1}), ("ping", modem, {"d": 0.8, "count": 6}),
    ("lance.charge", laser_charge, {"d": 1.5}), ("lance.fire", zap, {"d": 0.5}),
    ("burst.windup", servo, {"d": 1.5, "f0": 150, "f1": 500}), ("burst.eject", thump, {"d": 0.25, "f0": 200, "f1": 90, "noise": 0.6}),
    ("burst.fizzle", clicks, {"d": 0.3, "count": 3}), ("flick.windup", electric, {"d": 1.5}),
    ("flick.refused", beep, {"d": 0.4, "f": 330, "count": 2}), ("ttl.windup", swell, {"d": 1.5}),
    ("ttl.pulse", boom, {"d": 0.6, "f0": 120, "f1": 60, "crack": 0.4}), ("nak", hum, {"d": 0.25, "f": 110}),
    ("death", servo, {"d": 6.0, "f0": 600, "f1": 40}),
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
    src = f"""package net.teamaof.skylorebosses.bosses.nullrouter;

import java.util.Map;
import net.teamaof.skylorebosses.core.fx.ModelLocators;

/** GENERATED by bosses/null_router/tools/export_to_mod.py from the Blockbench specs. Rest-pose locators in model pixels. */
public final class RouterLocators {{
    private RouterLocators() {{}}

    public static void register() {{
{chr(10).join(lines)}
    }}
}}
"""
    with open(os.path.join(mk(JAVA), "RouterLocators.java"), "w") as f:
        f.write(src)
    return sorted(sound_ids | set(EXTRA_SOUNDS))


# ---------------- particle descriptions (sprites are the shared glow/spark/puff sets) ----------------
PARTICLES = {"packet_spark": ("spark", 3), "coolant_mist": ("puff", 4), "ack_glint": ("glow", 3), "null_beam": ("glow", 3)}


def particles():
    pdir = mk(ASSETS, "particles")
    for name, (fam, n) in PARTICLES.items():
        with open(os.path.join(pdir, f"{name}.json"), "w") as f:
            json.dump({"textures": [f"{NS}:{fam}_{i}" for i in range(n)]}, f, indent=2)


# ---------------- block & item textures (Codex sources + derived states) ----------------
BLOCK_TEXTURES = ["vault_floor", "vault_wall", "vault_ceiling", "vault_light", "vault_pillar_side", "vault_pillar_top", "chassis_pad_top",
                  "chassis_pad_side", "coolant_vent", "routing_gate", "vault_grate", "service_terminal", "service_terminal_side",
                  "console_side", "console_top"]
GLYPHS = ["circle", "triangle", "square"]
# console bezel colour per status (DESIGN.md §6): settable, settling, locked in an ACK window, being flipped
STATUS = {"idle": (96, 100, 108), "arming": (255, 176, 40), "locked": (200, 255, 215), "alert": (255, 60, 50)}


def _load(name):
    return np.array(Image.open(os.path.join(SRC, f"{name}.png")).convert("RGBA")).copy()


def console_face(glyph, status):
    """The console screen is its lamp's glyph (same shape, same colour) inside a 2-pixel bezel in the status colour."""
    a = _load(f"lamp_{glyph}")
    c = np.array(STATUS[status], float)
    out = a.copy()
    rgb = out[..., :3].astype(float)
    ring1 = np.zeros((16, 16), bool)
    ring1[0, :] = ring1[-1, :] = ring1[:, 0] = ring1[:, -1] = True
    ring2 = np.zeros((16, 16), bool)
    ring2[1, 1:-1] = ring2[-2, 1:-1] = ring2[1:-1, 1] = ring2[1:-1, -2] = True
    rgb[ring1] = c
    rgb[ring2] = c * 0.55
    if status == "locked":
        rgb[~(ring1 | ring2)] = rgb[~(ring1 | ring2)] * 0.8 + np.array([40, 60, 45]) * 0.2
    out[..., :3] = rgb.clip(0, 255).astype(np.uint8)
    out[..., 3] = 255
    return out


def dim(a, k=0.33):
    """NEXT (decoy) lamp: the same glyph at a third of the brightness, half desaturated."""
    rgb = a[..., :3].astype(float)
    grey = rgb.mean(axis=-1, keepdims=True)
    rgb = (rgb * 0.5 + grey * 0.5) * k
    out = a.copy()
    out[..., :3] = rgb.clip(0, 255).astype(np.uint8)
    return out


def lamp_off():
    a = np.zeros((16, 16, 4), np.uint8)
    a[..., :3] = (14, 14, 16)
    a[..., 3] = 255
    a[0, :, :3] = a[-1, :, :3] = a[:, 0, :3] = a[:, -1, :3] = (70, 72, 78)
    return a


def lit_vent(a):
    rgb = a[..., :3].astype(float)
    rgb = rgb * 0.7 + np.array([90, 200, 255]) * 0.45
    out = a.copy()
    out[..., :3] = rgb.clip(0, 255).astype(np.uint8)
    return out


def blocks_and_items():
    bt = mk(ASSETS, "textures", "block", BOSS)
    it = mk(ASSETS, "textures", "item", BOSS)
    for n in BLOCK_TEXTURES:
        shutil.copy(os.path.join(SRC, f"{n}.png"), os.path.join(bt, f"{n}.png"))
    for g in GLYPHS:
        lamp = _load(f"lamp_{g}")
        lamp[..., 3] = 255
        Image.fromarray(lamp).save(os.path.join(bt, f"lamp_{g}.png"))
        Image.fromarray(dim(lamp)).save(os.path.join(bt, f"lamp_{g}_dim.png"))
        for s in STATUS:
            Image.fromarray(console_face(g, s)).save(os.path.join(bt, f"console_{g}_{s}.png"))
    Image.fromarray(lamp_off()).save(os.path.join(bt, "lamp_off.png"))
    Image.fromarray(lit_vent(_load("coolant_vent"))).save(os.path.join(bt, "coolant_vent_lit.png"))
    shutil.copy(os.path.join(SRC, "closed_ticket.png"), os.path.join(it, "closed_ticket.png"))


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
        if sid.split(".")[0] in ("lockdown", "storm", "victory", "death", "ack", "chime", "request", "boot", "coolant", "dock"):
            entry["sounds"][0]["attenuation_distance"] = 64
        j[f"{BOSS}.{sid}"] = entry
    with open(path, "w") as f:
        json.dump(dict(sorted(j.items())), f, indent=2)
    with open(SOUND_IDS, "w") as f:
        f.write("\n".join(ids))


def sound_ids_java(ids):
    body = ",\n            ".join(f'"{s}"' for s in ids)
    src = f"""package net.teamaof.skylorebosses.bosses.nullrouter;

/** GENERATED by bosses/null_router/tools/export_to_mod.py: Null Router sound ids (registered as skylore_bosses:null_router.<id>). */
public final class RouterSoundIds {{
    public static final String[] ALL = {{
            {body}
    }};

    private RouterSoundIds() {{}}
}}
"""
    with open(os.path.join(mk(JAVA), "RouterSoundIds.java"), "w") as f:
        f.write(src)


if __name__ == "__main__":
    copy_models()
    ids = locators_java()
    particles()
    blocks_and_items()
    sound_files(ids, synth="--no-sounds" not in sys.argv)
    sound_ids_java(ids)
    print(len(ids), "sounds;", len(ENTITY_MODELS), "models exported")
