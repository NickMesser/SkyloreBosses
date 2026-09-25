"""Generate Null Router's source textures with the Codex CLI's image generation tool, then post-process them into
game-sized pixel art. Raw generations are kept in textures/codex_raw/ (so a rerun only regenerates what is missing);
the processed sources land in textures/src/ and are consumed by export_to_mod.py (blocks, items) and texture.py
(entity material swatches).
    python codex_textures.py                 # generate everything missing, 4 at a time
    python codex_textures.py vault_wall ...  # regenerate the named textures
    python codex_textures.py --process-only  # only redo the downscale/quantise step
Needs the Codex CLI logged in. CODEX_EXE may point at a codex.exe; by default the app-bundled one is looked up."""
import os, re, shutil, subprocess, sys, tempfile
from concurrent.futures import ThreadPoolExecutor
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.abspath(os.path.join(HERE, "..", "textures"))
RAW = os.path.join(TEX, "codex_raw")
SRC = os.path.join(TEX, "src")

STYLE_BLOCK = ("A seamless tileable Minecraft block texture in crisp pixel-art style: a flat top-down square made of a 16 by 16 grid "
               "of solid-colour square pixels filling the entire image edge to edge. No border, no frame, no text, no letters, no "
               "perspective, no drop shadow, even lighting. Subject: ")
STYLE_FACE = ("A Minecraft block face texture in crisp pixel-art style: a flat front-on square made of a 16 by 16 grid of solid-colour "
              "square pixels filling the entire image edge to edge. No text, no letters, no numbers, no perspective, even lighting. Subject: ")
STYLE_SWATCH = ("A seamless tileable material swatch for a low-resolution game model texture, pixel art: a flat square made of a 32 by 32 "
                "grid of solid-colour square pixels filling the entire image edge to edge. No border, no text, no objects, no perspective, "
                "even lighting, the pattern must tile. Material: ")
STYLE_ICON = ("A Minecraft inventory item icon in crisp 16 by 16 pixel-art style with a dark 1-pixel outline, centred, on a perfectly flat "
              "pure magenta (#FF00FF) background that fills the rest of the image. No text, no shadow on the background. Subject: ")

# Shared look: an Automaton network vault. Clean-room raised floor in pale grey, dark sky-stone walls, violet-cyan fluix accents.
GLYPH = {"circle": "a single bold hollow ring (circle) glyph", "triangle": "a single bold upward-pointing triangle glyph",
         "square": "a single bold hollow square glyph"}
GLYPH_COL = {"circle": "bright cyan", "triangle": "bright amber-orange", "square": "bright magenta"}

# name -> (kind, output size, prompt). kind: block (opaque 16), face (opaque 16, not tileable), swatch (64 entity tile),
# icon (keyed 16/32), cutout (keyed 16)
TEXTURES = {
    # ---------------- vault shell
    "vault_floor": ("block", 16, "a clean-room raised access floor panel: pale grey-white square tile with a thin darker grey seam around the edge "
                                 "and a neat grid of small perforation dots, spotless and dry"),
    "vault_wall": ("block", 16, "dark charcoal sky-stone wall block with a smooth polished surface and a thin glowing violet-cyan circuit line "
                                "running horizontally across the middle"),
    "vault_ceiling": ("block", 16, "dark grey metal ceiling panel with a square vent grille pattern"),
    "vault_light": ("block", 16, "a bright white-cyan clean-room ceiling light panel, a luminous frosted square inside a thin grey metal frame"),
    "vault_pillar_side": ("block", 16, "the side of a tall server-rack column: dark grey metal with vertical rows of tiny green and amber indicator "
                                       "lights and thin horizontal drive-bay slots"),
    "vault_pillar_top": ("block", 16, "the top of a dark metal column seen from above: a square dark grey cap with a violet fluix crystal square inlay"),
    "chassis_pad_top": ("block", 16, "the top of a docking pad: a dark metal square plate with a yellow-and-black hazard stripe border and a "
                                     "glowing cyan concentric ring target in the centre"),
    "chassis_pad_side": ("block", 16, "the side of a dark metal docking pad: dark steel with a yellow-and-black hazard stripe band along the top"),
    "coolant_vent": ("block", 16, "a round coolant vent grate seen from above: dark steel with a circular grille of slots and pale blue frost "
                                  "around the openings"),
    "routing_gate": ("cutout", 16, "a closed red holographic grid barrier: thin glowing red lines forming a square lattice, on a pure magenta "
                                   "(#FF00FF) background visible between the lines"),
    "vault_grate": ("cutout", 16, "a heavy black steel blast-door grate: thick vertical and horizontal dark metal bars forming a grid, on a pure "
                                  "magenta (#FF00FF) background visible between the bars"),
    "service_terminal": ("face", 16, "the front of a small computer service terminal: a dark screen showing a glowing green blinking cursor and "
                                     "three short green lines, inside a grey metal bezel with one amber button below"),
    "service_terminal_side": ("block", 16, "the side of a grey metal computer case with thin ventilation slots"),
    # ---------------- channel consoles and request lamps (the glyphs are the puzzle alphabet)
    "console_side": ("block", 16, "the side of a network channel console: dark grey steel with a bundle of violet and cyan network cables running "
                                  "into it at the bottom"),
    "console_top": ("block", 16, "the top of a network channel console: dark steel plate with a small grid of square keys, one glowing"),
    **{f"console_{g}": ("face", 16, f"the front screen of a network channel console: a dark near-black display inside a grey metal bezel showing "
                                    f"{GLYPH[g]} in {GLYPH_COL[g]} glowing pixels, centred, filling about half the screen") for g in GLYPH},
    **{f"lamp_{g}": ("block", 16, f"a glowing indicator lamp panel: a black square with {GLYPH[g]} in {GLYPH_COL[g]} light, centred, bold, "
                                  f"with a thin grey frame around the edge") for g in GLYPH},
    # ---------------- items
    "closed_ticket": ("icon", 32, "a small paper support ticket stub with a torn perforated edge, stamped with a green check mark, with a "
                                  "tiny glowing cyan circuit corner"),
    # ---------------- entity material swatches (texture.py 'automaton' palette)
    "chassis": ("swatch", 64, "brushed dark gunmetal steel armour plating with thin panel seams and small hex bolts"),
    "chassis_dark": ("swatch", 64, "near-black recessed steel panels with faint scratches"),
    "panel": ("swatch", 64, "clean pale grey-white ceramic composite plating with thin grey seams, like a clean-room machine casing"),
    "fluix": ("swatch", 64, "glowing violet and cyan faceted fluix crystal, luminous, sharp angular facets"),
    "cable": ("swatch", 64, "dense bundle of dark network cables with thin violet and cyan stripes"),
    "cable_glow": ("swatch", 64, "a glowing cyan data cable with bright pulses of light travelling along it, luminous"),
    "lens": ("swatch", 64, "a bright glowing white-cyan optical lens with concentric rings, luminous"),
    "led_amber": ("swatch", 64, "a strip of bright glowing amber indicator LEDs on black, luminous"),
    "led_red": ("swatch", 64, "a strip of bright glowing red warning LEDs on black, luminous"),
    "led_green": ("swatch", 64, "a strip of bright glowing green status LEDs on black, luminous"),
    "screen": ("swatch", 64, "a dark glass computer display with faint green scrolling text lines, dim"),
    "fins": ("swatch", 64, "aluminium heat-sink cooling fins, parallel thin silver-grey ridges"),
    "copper": ("swatch", 64, "polished copper bus bars and contacts, warm orange-brown metal"),
    "hazard": ("swatch", 64, "diagonal yellow and black hazard stripes, worn paint"),
    "rubber": ("swatch", 64, "black rubber gasket with fine texture"),
    "packet": ("swatch", 64, "a glowing orange-amber holographic data packet: bright translucent orange light with thin darker scanlines, luminous"),
}


def codex_exe():
    if os.environ.get("CODEX_EXE"):
        return os.environ["CODEX_EXE"]
    # The packaged app binary cannot be launched from WindowsApps; copy the exe with its code-mode host next to it.
    cache = os.path.join(tempfile.gettempdir(), "skylore_codexbin")
    exe = os.path.join(cache, "codex.exe")
    if not os.path.exists(exe):
        # WindowsApps cannot be listed, but the Codex config names the bundled CLI
        cfg = open(os.path.expanduser(r"~\.codex\config.toml"), encoding="utf-8").read()
        m = re.search(r"CODEX_CLI_PATH\s*=\s*'([^']+)'", cfg)
        if not m:
            raise SystemExit("Codex CLI not found; set CODEX_EXE")
        pkg = os.path.dirname(m.group(1))
        os.makedirs(cache, exist_ok=True)
        for f in ("codex.exe", "codex-code-mode-host.exe", "codex-command-runner.exe"):
            shutil.copy(os.path.join(pkg, f), cache)
    return exe


def generate(name):
    kind, _, subject = TEXTURES[name]
    style = {"block": STYLE_BLOCK, "face": STYLE_FACE, "cutout": STYLE_BLOCK, "swatch": STYLE_SWATCH, "icon": STYLE_ICON}[kind]
    prompt = ("Use your image generation tool to create exactly one image. " + style + subject +
              ". Do not try to edit, resize or save the file yourself. When done, reply with only the absolute path of the generated image file.")
    work = tempfile.mkdtemp(prefix="codex_tex_")
    last = os.path.join(work, "last.txt")
    r = None
    for attempt in range(2):
        r = subprocess.run([codex_exe(), "exec", "--skip-git-repo-check", "--ephemeral", "-s", "read-only", "-C", work, "-o", last, prompt],
                           stdin=subprocess.DEVNULL, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=900)
        text = open(last, encoding="utf-8").read() if os.path.exists(last) else r.stdout
        m = re.findall(r"[A-Za-z]:\\[^\s`'\"]+?\.png", text)
        if m and os.path.exists(m[-1]):
            # keep a 256px copy: processing only ever box-downsamples below that, and it keeps the repo small
            Image.open(m[-1]).convert("RGBA").resize((256, 256), Image.BOX).save(os.path.join(RAW, f"{name}.png"), optimize=True)
            print("generated", name, flush=True)
            return True
    print("FAILED", name, ((r.stdout or "") if r else "")[-400:], flush=True)
    return False


def _safe_generate(name):
    try:
        return generate(name)
    except Exception as e:  # one bad generation must not stop the batch
        print("FAILED", name, repr(e), flush=True)
        return False


def _background(rgb, black=False):
    """Key colour mask: the requested magenta, and (for cutouts) the near-black Codex sometimes paints instead."""
    rgb = rgb.astype(int)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    m = (r > 90) & (b > 90) & (g < 0.55 * np.minimum(r, b)) & (np.abs(r - b) < 70)
    if black:
        m |= rgb.max(axis=-1) < 28
    return m


def _key(a, black=False):
    a[..., 3] = np.where(_background(a[..., :3], black), 0, 255)
    return a


def process(name):
    kind, size, _ = TEXTURES[name]
    src = os.path.join(RAW, f"{name}.png")
    if not os.path.exists(src):
        return
    im = Image.open(src).convert("RGBA")
    w, h = im.size
    c = min(w, h)
    im = im.crop(((w - c) // 2, (h - c) // 2, (w - c) // 2 + c, (h - c) // 2 + c))
    if kind == "icon":
        # icons: crop to the subject (plus a small margin) so it fills the slot
        bg = _background(np.array(im)[..., :3])
        ys, xs = np.where(~bg)
        if len(xs):
            x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
            side = int(max(x1 - x0, y1 - y0) * 1.08) + 1
            cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
            box = (cx - side // 2, cy - side // 2, cx - side // 2 + side, cy - side // 2 + side)
            canvas = Image.new("RGBA", (side, side), (255, 0, 255, 255))
            canvas.paste(im.crop(box), (0, 0))
            im = canvas
    if kind in ("icon", "cutout"):
        # nearest sampling at cell centres keeps crisp pixel edges and pure key colours
        small = np.array(im.resize((size * 8, size * 8), Image.BOX))
        a = small[4::8, 4::8].copy()
        a = _key(a, black=kind == "cutout")
    else:
        a = np.array(im.resize((size, size), Image.BOX))
        a[..., 3] = 255
    out = Image.fromarray(a, "RGBA")
    if kind in ("block", "face"):
        # quantise to a small palette so the downscale reads as pixel art rather than a blur
        rgb = out.convert("RGB").quantize(colors=12, method=Image.Quantize.MEDIANCUT).convert("RGB")
        a2 = np.array(rgb.convert("RGBA"))
        a2[..., 3] = a[..., 3]
        out = Image.fromarray(a2, "RGBA")
    out.save(os.path.join(SRC, f"{name}.png"))


def main(argv):
    os.makedirs(RAW, exist_ok=True)
    os.makedirs(SRC, exist_ok=True)
    names = [a for a in argv if not a.startswith("--")] or list(TEXTURES)
    if "--process-only" not in argv:
        todo = [n for n in names if argv and n in argv or not os.path.exists(os.path.join(RAW, f"{n}.png"))]
        codex_exe()   # resolve (and copy) the binary once, before the workers start
        with ThreadPoolExecutor(4) as ex:
            list(ex.map(_safe_generate, todo))
    for n in names:
        process(n)
    print("processed", len(names))


if __name__ == "__main__":
    main(sys.argv[1:])
