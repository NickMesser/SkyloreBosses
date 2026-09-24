"""Generate The Static Deacon's source textures with the Codex CLI's image generation tool, then post-process them into
game-sized pixel art. Raw generations are kept in textures/codex_raw/ (so a rerun only regenerates what is missing);
the processed sources land in textures/src/ and are consumed by export_to_mod.py (blocks, items) and texture.py
(entity material swatches).
    python codex_textures.py                 # generate everything missing, 4 at a time
    python codex_textures.py crypt_wall ...  # regenerate the named textures
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
               "of solid-colour square pixels filling the entire image edge to edge. No border, no frame, no text, no perspective, "
               "no drop shadow, even lighting. Subject: ")
STYLE_SWATCH = ("A seamless tileable material swatch for a low-resolution game model texture, pixel art: a flat square made of a 32 by 32 "
                "grid of solid-colour square pixels filling the entire image edge to edge. No border, no text, no objects, no perspective, "
                "even lighting, the pattern must tile. Material: ")
STYLE_ICON = ("A Minecraft inventory item icon in crisp 16 by 16 pixel-art style with a dark 1-pixel outline, centred, on a perfectly flat "
              "pure magenta (#FF00FF) background that fills the rest of the image. No text, no shadow on the background. Subject: ")

# name -> (kind, output size, prompt). kind: block (opaque 16), swatch (64 entity tile), icon (keyed 16), cutout (keyed 16)
TEXTURES = {
    # ---------------- blocks
    "consecrated_endstone": ("block", 16, "pale cream-yellow End Stone floor flagstone, porous speckled stone, with thin glowing violet "
                                          "engraved liturgical glyph lines (small crosses and diamond sigils) running across it"),
    "desecrated_endstone": ("block", 16, "the same pale End Stone but dead and profaned: ashen grey, cracked through with dark jagged fissures, "
                                         "dusted with grey static, no glow"),
    "altar_plinth_top": ("block", 16, "the top of a carved altar stone of polished pale End Stone with an inlaid violet and gold eye-of-ender "
                                      "sigil in the centre inside a square gold border"),
    "altar_plinth_side": ("block", 16, "the side of a carved altar dais: pale End Stone panel with a recessed gold-trimmed rectangular frame "
                                       "and a small violet diamond in the middle"),
    "crypt_tile": ("block", 16, "dark deepslate floor tiles in a 2 by 2 square pattern with thin dull purple grout lines, worn and cold"),
    "crypt_subfloor": ("block", 16, "rough dark charcoal deepslate bedrock, chipped and uneven, very dark"),
    "crypt_wall": ("block", 16, "a crypt wall of dark purple-black ashlar stone bricks, with a few thin pale sea-green crystal veins "
                                "running through the mortar"),
    "crypt_pillar_side": ("block", 16, "a fluted stone column side: dark purple stone with vertical grooves and a thin pale sea-green "
                                       "crystal inlay down the middle"),
    "crypt_pillar_top": ("block", 16, "the round top of a stone column seen from above: dark purple stone ring with a pale sea-green "
                                      "crystal disc in the centre"),
    "beryl_lamp": ("block", 16, "a glowing lamp made of pale aquamarine beryl crystal facets set in a thin dark iron frame, bright and luminous"),
    "crypt_pew": ("block", 16, "dark stained oak planks of a church pew, horizontal boards with a deep violet cloth strip"),
    "sacristy_bell": ("block", 16, "polished old brass metal with engraved rings and a dull gold sheen"),
    "crypt_grate": ("cutout", 16, "a black iron portcullis grate: thick vertical and horizontal iron bars forming a grid, on a pure magenta "
                                  "(#FF00FF) background visible between the bars"),
    # ---------------- items
    "static_thurible": ("icon", 32, "a small hanging church censer (thurible) made of brass with pale aquamarine crystal panes, "
                                    "hanging from a short chain, with a wisp of grey static smoke"),
    # ---------------- entity material swatches (texture.py 'liturgical' palette)
    "beryl": ("swatch", 64, "translucent pale aquamarine-green beryl crystal with sharp angular facets and bright highlights"),
    "beryl_dark": ("swatch", 64, "deep teal-green beryl crystal, darker faceted interior, a few pale edges"),
    "alb": ("swatch", 64, "off-white heavy linen vestment cloth with subtle vertical folds and weave"),
    "dalmatic": ("swatch", 64, "deep ender-purple brocade vestment fabric with a faint repeating diamond damask pattern"),
    "trim": ("swatch", 64, "gold embroidered braid ribbon with small repeating cross stitches"),
    "stole": ("swatch", 64, "pale end-stone-yellow cloth embroidered with small violet liturgical glyphs"),
    "static": ("swatch", 64, "grey swirling static dust, fine grainy noise like television static, dark and light grey motes"),
    "glyph_glow": ("swatch", 64, "glowing bright violet runic glyph lines on near-black, luminous magenta-violet"),
    "core_glow": ("swatch", 64, "bright white-cyan crackling static light, luminous, electric"),
    "chain": ("swatch", 64, "dark iron chain links interlocking, on a black background"),
    "brass": ("swatch", 64, "old polished brass metal with engraved rings, warm gold-brown"),
    "endstone": ("swatch", 64, "pale cream End Stone, porous speckled"),
    "obsidian": ("swatch", 64, "polished dark purple-black obsidian stone with faint violet sheen"),
    "shadow": ("swatch", 64, "almost black deep violet shadow cloth, the dark inside of a hood"),
    "halo": ("swatch", 64, "pale gold and aquamarine crystal shards, luminous, glowing"),
    "ender_flame": ("swatch", 64, "soft glowing purple ender flame wisps on dark violet, luminous"),
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
    style = {"block": STYLE_BLOCK, "cutout": STYLE_BLOCK, "swatch": STYLE_SWATCH, "icon": STYLE_ICON}[kind]
    prompt = ("Use your image generation tool to create exactly one image. " + style + subject +
              ". Do not try to edit, resize or save the file yourself. When done, reply with only the absolute path of the generated image file.")
    work = tempfile.mkdtemp(prefix="codex_tex_")
    last = os.path.join(work, "last.txt")
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
    print("FAILED", name, (r.stdout or "")[-400:], flush=True)
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
        # icons: crop to the subject (plus a small margin) so it fills the 16x16 slot
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
    if kind == "block":
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
