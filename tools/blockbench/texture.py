"""Procedural 256x256 material atlas: 16 tiles of 64x64. Also emits a glowmask."""
import numpy as np, base64, io
from PIL import Image

T = 64
MATS = [  # name, base, dark, light, glow
    ("flesh",      (150, 62, 70),  (84, 26, 38),  (206, 118, 112), False),
    ("flesh_dark", (92, 34, 48),   (44, 14, 26),  (140, 64, 72),   False),
    ("bone",       (214, 198, 164),(142, 122, 96),(240, 232, 206), False),
    ("vein",       (104, 42, 120), (52, 18, 70),  (170, 90, 190),  False),
    ("sclera",     (226, 214, 196),(170, 120, 110),(250, 244, 232),False),
    ("iris",       (230, 170, 40), (150, 70, 10), (255, 236, 120), True),
    ("pupil",      (18, 6, 10),    (4, 0, 2),     (60, 14, 20),    False),
    ("bile",       (150, 200, 40), (70, 110, 10), (220, 250, 110), True),
    ("tooth",      (232, 222, 190),(170, 150, 110),(252, 248, 230),False),
    ("nerve",      (120, 200, 230),(40, 90, 150), (210, 250, 255), True),
    ("core",       (255, 110, 150),(180, 30, 80), (255, 210, 225), True),
    ("spore",      (170, 150, 110),(96, 80, 56),  (210, 196, 150), False),
    ("chitin",     (70, 52, 60),   (30, 20, 28),  (120, 96, 104),  False),
    ("mucus",      (190, 150, 140),(130, 90, 90), (240, 210, 200), False),
    ("root",       (110, 72, 58),  (56, 34, 28),  (150, 110, 86),  False),
    ("glow_vein",  (255, 140, 60), (180, 60, 20), (255, 220, 150), True),
]
IDX = {m[0]: i for i, m in enumerate(MATS)}

# Teknari industry (Overhead): painted steel, hazard paint, emissive lenses and grid energy.
INDUSTRIAL = [  # name, base, dark, light, glow
    ("steel",       (128, 134, 140), (72, 76, 84),   (182, 188, 192), False),
    ("steel_dark",  (62, 66, 74),    (30, 32, 38),   (98, 104, 112),  False),
    ("noven",       (46, 110, 112),  (20, 60, 64),   (90, 160, 158),  False),
    ("plate",       (150, 146, 132), (92, 88, 78),   (196, 192, 176), False),
    ("hazard",      (226, 180, 30),  (30, 26, 22),   (250, 214, 80),  False),
    ("rust",        (132, 70, 40),   (70, 34, 20),   (182, 108, 60),  False),
    ("copper",      (190, 110, 60),  (110, 56, 30),  (236, 160, 104), False),
    ("brass",       (182, 150, 70),  (110, 86, 34),  (230, 204, 120), False),
    ("rubber",      (36, 36, 40),    (14, 14, 16),   (64, 64, 70),    False),
    ("grille",      (54, 58, 62),    (10, 10, 12),   (92, 96, 100),   False),
    ("lens",        (255, 60, 40),   (150, 10, 10),  (255, 200, 150), True),
    ("amber",       (255, 170, 40),  (170, 90, 10),  (255, 230, 150), True),
    ("energy",      (90, 220, 255),  (20, 110, 200), (220, 250, 255), True),
    ("concrete",    (150, 150, 146), (104, 104, 100),(180, 180, 176), False),
    ("warning",     (176, 40, 34),   (100, 16, 14),  (220, 90, 80),   False),
    ("glass",       (40, 46, 56),    (14, 16, 22),   (110, 130, 150), False),
]
_PALETTES = {"organic": MATS, "industrial": INDUSTRIAL}


def use(palette):
    """Select the material palette for this process (a boss's boss_env calls this before building)."""
    global MATS, IDX
    MATS = _PALETTES[palette]
    IDX = {m[0]: i for i, m in enumerate(MATS)}


def tile_origin(name):
    i = IDX[name]
    return (i % 4) * T, (i // 4) * T

def _noise(rng, n=T, octaves=4):
    out = np.zeros((n, n))
    for o in range(octaves):
        s = 2 ** (o + 2)
        g = rng.random((s + 1, s + 1))
        g[-1, :] = g[0, :]; g[:, -1] = g[:, 0]
        x = np.linspace(0, s, n, endpoint=False)
        xi = x.astype(int); xf = x - xi
        xf = xf * xf * (3 - 2 * xf)
        a = g[np.ix_(xi, xi)]; b = g[np.ix_(xi, xi + 1)]
        c = g[np.ix_(xi + 1, xi)]; d = g[np.ix_(xi + 1, xi + 1)]
        fx = xf[None, :]; fy = xf[:, None]
        out += ((a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy) / (2 ** o)
    out -= out.min(); out /= out.max()
    return out

def build(seed=7):
    rng = np.random.default_rng(seed)
    img = np.zeros((256, 256, 4), np.uint8)
    glow = np.zeros((256, 256, 4), np.uint8)
    for i, (name, base, dark, light, is_glow) in enumerate(MATS):
        n = _noise(rng)
        base, dark, light = map(np.array, (base, dark, light))
        col = np.where(n[..., None] < 0.5,
                       dark + (base - dark) * (n[..., None] / 0.5),
                       base + (light - base) * ((n[..., None] - 0.5) / 0.5))
        # material-specific detail
        yy, xx = np.mgrid[0:T, 0:T]
        if name in ("flesh", "flesh_dark", "mucus"):
            # capillaries
            for _ in range(10):
                x, y = rng.integers(0, T, 2)
                for _ in range(40):
                    col[y % T, x % T] = col[y % T, x % T] * 0.55 + np.array([120, 20, 60]) * 0.45
                    x += rng.integers(-1, 2); y += rng.integers(0, 2)
            # pores
            for _ in range(30):
                x, y = rng.integers(0, T, 2); col[y, x] = dark * 0.7
        if name in ("vein", "nerve", "glow_vein"):
            stripes = (np.sin(yy * 0.9 + n * 6) > 0.6)[..., None]
            col = np.where(stripes, light, col)
        if name == "bone":
            cracks = (np.abs(np.sin(xx * 0.3 + n * 9)) < 0.05)[..., None]
            col = np.where(cracks, dark, col)
        if name == "chitin":
            plates = ((yy % 8) == 0)[..., None]
            col = np.where(plates, dark * 0.6, col)
        if name == "iris":
            r = np.hypot(xx - 31.5, yy - 31.5); ang = np.arctan2(yy - 31.5, xx - 31.5)
            rays = (np.sin(ang * 22 + r * 0.2) * 0.5 + 0.5)[..., None]
            col = col * (0.7 + 0.3 * rays)
        if name == "core":
            r = np.hypot(xx - 31.5, yy - 31.5)[..., None]
            col = col + (light - col) * np.clip(1 - r / 30, 0, 1)
        if name == "spore":
            for _ in range(60):
                x, y = rng.integers(1, T - 1, 2)
                col[y - 1:y + 2, x - 1:x + 2] = light
                col[y, x] = dark
        if name == "tooth":
            col = col * (0.85 + 0.15 * (yy[..., None] / T))
        # industrial details
        if name in ("steel", "steel_dark", "noven", "plate", "warning"):
            seams = (((xx % 16) == 0) | ((yy % 16) == 0))[..., None]
            col = np.where(seams, dark * 0.8, col)
            rivets = ((((xx % 16) == 3) | ((xx % 16) == 13)) & (((yy % 16) == 3) | ((yy % 16) == 13)))[..., None]
            col = np.where(rivets, light, col)
            wear = (n > 0.82)[..., None]
            col = np.where(wear, col * 0.5 + np.array((150, 150, 150)) * 0.5, col)
        if name == "hazard":
            stripes = (((xx + yy) // 8) % 2 == 0)[..., None]
            col = np.where(stripes, base * (0.85 + 0.3 * n[..., None]), dark + 10 * n[..., None])
        if name == "rust":
            pits = (n > 0.7)[..., None]
            col = np.where(pits, dark, col)
        if name in ("copper", "brass"):
            coil = ((yy % 4) == 0)[..., None]
            col = np.where(coil, dark, col)
        if name == "grille":
            slots = ((yy % 6) < 3)[..., None] & ((xx % 32) > 2)[..., None]
            col = np.where(slots, dark, col)
        if name in ("lens", "amber", "energy"):
            r = np.hypot(xx - 31.5, yy - 31.5)[..., None]
            col = col + (light - col) * np.clip(1 - r / 34, 0, 1)
            if name == "energy":
                col = np.where((np.sin(yy * 0.6 + n * 8) > 0.7)[..., None], light, col)
        if name == "concrete":
            for _ in range(80):
                x, y = rng.integers(0, T, 2)
                col[y, x] = dark
        if name == "glass":
            col = col + (light - col) * np.clip(1 - np.abs(xx - yy)[..., None] / 6, 0, 1) * 0.5
        # pixel-art quantise
        col = (np.round(np.clip(col, 0, 255) / 8) * 8).clip(0, 255)
        ox, oy = tile_origin(name)
        img[oy:oy + T, ox:ox + T, :3] = col; img[oy:oy + T, ox:ox + T, 3] = 255
        if is_glow:
            glow[oy:oy + T, ox:ox + T, :3] = col; glow[oy:oy + T, ox:ox + T, 3] = 255
    return Image.fromarray(img, "RGBA"), Image.fromarray(glow, "RGBA")

def data_url(im):
    b = io.BytesIO(); im.save(b, "PNG")
    return "data:image/png;base64," + base64.b64encode(b.getvalue()).decode()

if __name__ == "__main__":
    a, g = build(); a.save("atlas_preview.png"); print("ok")
