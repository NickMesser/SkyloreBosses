"""Generate Amanita's source textures with the Codex CLI's image generation tool, then post-process them into game-sized
pixel art. Raw generations are kept in textures/codex_raw/ (so a rerun only regenerates what is missing); the processed
sources land in textures/src/ and are consumed by export_to_mod.py (blocks, items) and texture.py (entity swatches).
    python codex_textures.py                 # generate everything missing, 4 at a time
    python codex_textures.py hollow_wall ... # regenerate the named textures
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

# Shared look: a sealed shadow hollow under a sky island. Near-black violet fungal matter, pale ghost-white stalks,
# lilac gills. Nothing in the hollow itself glows; only the braziers (when lit) and the creatures' gills and eyes.
# name -> (kind, output size, prompt). kind: block (opaque 16), face (opaque 16, not tileable), swatch (64 entity tile),
# icon (keyed 32), cutout (keyed 16)
TEXTURES = {
    # ---------------- hollow shell
    "hollow_wall": ("block", 16, "a dark cave wall of packed black fungal hyphae and old roots, near-black violet-grey with a few faint pale "
                                 "grey thread lines, dull, no glow"),
    "hollow_loam": ("block", 16, "dark purple-black soil floor matted with thin pale grey fungal threads and a few tiny white spore specks, dull"),
    "hollow_ceiling": ("block", 16, "a cave ceiling of dark overlapping bracket fungus undersides: black-violet gill ridges in rows, dull, no glow"),
    "hollow_column_side": ("block", 16, "pale off-white mushroom stalk flesh filling the whole square edge to edge: fine vertical grey "
                                        "fibre streaks running top to bottom, seamless, no background, no outline, no object shape"),
    "hollow_column_top": ("block", 16, "the cut top of a thick mushroom stalk seen from above: an off-white fibrous ring around a darker "
                                       "grey hollow centre"),
    "gill_shelf": ("block", 16, "the side of a bracket fungus shelf: stacked horizontal dusky lilac-grey gill plates with thin dark gaps between them"),
    "brazier_side": ("face", 16, "the side of a small black iron brazier cage: dark iron bars with a heap of black coal lumps visible "
                                 "between them, unlit, cold, no fire"),
    "brazier_side_lit": ("face", 16, "the side of a small black iron brazier cage: dark iron bars with a heap of glowing orange-red embers "
                                     "and small yellow flames visible between them, bright"),
    "brazier_top": ("block", 16, "looking straight down into an iron brazier bowl heaped with black coal lumps, dark iron rim, unlit, cold"),
    "brazier_top_lit": ("block", 16, "looking straight down into an iron brazier bowl heaped with glowing orange-red embers and small yellow "
                                     "flames, dark iron rim, bright"),
    "hollow_membrane": ("cutout", 16, "an irregular net of thick dark violet fungal strands stretched across an opening, on a pure magenta "
                                      "(#FF00FF) background visible between the strands"),
    "hollow_knocker": ("block", 16, "the knobbly surface of a pale shelf mushroom cap: ivory with small brown-grey warts"),
    # ---------------- items
    "hollow_bloom_cap": ("icon", 32, "a small dark violet-black mushroom cap with pale ivory warts on top and faintly glowing lilac gills "
                                     "underneath, a torn pale stalk stub"),
    # ---------------- entity material swatches (texture.py 'hollow' palette)
    "cap": ("swatch", 64, "bruised violet-black mushroom cap flesh, velvety, with faint darker blotches"),
    "cap_dark": ("swatch", 64, "near-black deep violet mushroom cap underside, velvety shadow"),
    "wart": ("swatch", 64, "pale ivory mushroom warts: knobbly raised cream-white bumps on a grey base"),
    "gill": ("swatch", 64, "pale lilac-grey mushroom gill plates, fine parallel thin ridges"),
    "gill_glow": ("swatch", 64, "glowing soft violet bioluminescent mushroom gills, fine parallel ridges of light on dark, luminous"),
    "stipe": ("swatch", 64, "pale ghost-white mushroom stalk flesh with fine vertical fibres"),
    "volva": ("swatch", 64, "torn cream-grey papery mushroom volva skin, wrinkled and layered"),
    "hyphae": ("swatch", 64, "dense tangled dark grey-violet fungal threads, fibrous strands"),
    "shadow": ("swatch", 64, "almost black deep violet shadow, the darkness inside a hood"),
    "skin": ("swatch", 64, "pallid grey-lilac skin, smooth, with faint darker veins"),
    "eye_glow": ("swatch", 64, "glowing pale cold green-white light, luminous"),
    "spore_glow": ("swatch", 64, "glowing violet-pink spore motes scattered on dark violet, luminous"),
    "moth": ("swatch", 64, "dusty pale grey-brown moth wing scales with faint darker banding"),
    "ember": ("swatch", 64, "glowing warm amber-orange embers, luminous"),
    "husk": ("swatch", 64, "dried brown-grey fungal husk, cracked and papery"),
    "loam": ("swatch", 64, "dark purple-black soil with pale grey thread specks"),
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
