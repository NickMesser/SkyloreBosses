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
