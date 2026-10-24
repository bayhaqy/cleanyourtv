"""Abstract wallpaper for the theme-crimson launcher theme: soft colour fields, luminous flowing ribbons and a faint
dot grid on near-black, darkened behind the dock and the clock.
Usage: python my-tv/make_wallpaper.py <out.jpg> [seed] [palette]   palette: redblack (default) or violet"""
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

# The launcher draws at 1920x1080 (the TV's largest override; the panel shows it downscaled to 1280x720).
W, H = 1920, 1080
out = sys.argv[1]
rng = np.random.default_rng(int(sys.argv[2]) if len(sys.argv) > 2 else 7)
PALETTE = sys.argv[3] if len(sys.argv) > 3 else "redblack"
RED = PALETTE == "redblack"

y, x = np.mgrid[0:H, 0:W].astype(np.float32)
nx, ny = x / W, y / H
# Domain warp, so the colour fields flow instead of sitting as round blobs.
wx = nx + 0.06 * np.sin(ny * 6.0 + 1.3) + 0.03 * np.sin(nx * 11.0)
wy = ny + 0.05 * np.cos(nx * 5.0 + 0.4)

def field(cx, cy, rx, ry, color, power):
    d = ((wx - cx) / rx) ** 2 + ((wy - cy) / ry) ** 2
    return np.exp(-d)[..., None] * np.array(color, np.float32)[None, None, :] * power

if RED:
    img = np.zeros((H, W, 3), np.float32) + np.array([6, 5, 6], np.float32)
    img += field(0.80, 0.20, 0.32, 0.22, (200, 12, 32), 0.80)    # crimson, top right behind the buttons
    img += field(1.00, 0.62, 0.20, 0.28, (120, 0, 18), 0.70)     # deep wine, right edge
    img += field(0.25, 0.70, 0.30, 0.12, (90, 6, 16), 0.35)      # low ember, centre left
    img += field(0.55, 0.05, 0.40, 0.10, (230, 230, 235), 0.06)  # faint white haze along the top
else:
    img = np.zeros((H, W, 3), np.float32) + np.array([5, 7, 15], np.float32)
    img += field(0.78, 0.22, 0.30, 0.22, (109, 40, 217), 0.85)
    img += field(0.98, 0.55, 0.22, 0.30, (6, 182, 212), 0.55)
    img += field(0.30, 0.62, 0.35, 0.16, (219, 39, 119), 0.30)
    img += field(0.10, 0.05, 0.25, 0.18, (37, 99, 235), 0.30)

base = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(60 * W / 1920))

# Ribbons: a family of thin sine curves sweeping across the upper middle, drawn bright, then a wide glow copy.
lines = Image.new("RGB", (W, H))
draw = ImageDraw.Draw(lines)
phase = rng.uniform(0, 6.28)
for i in range(26):
    t = i / 25.0
    pts = []
    for px in range(-40, W + 41, 8):
        u = px / W
        yy = (0.30 + 0.20 * t) * H + 120 * H / 1080 * np.sin(u * 4.2 + phase + t * 1.6) + 50 * H / 1080 * np.sin(u * 9.0 - t * 3.0)
        pts.append((px, yy))
    c = (235, int(225 - 90 * t), int(228 - 90 * t)) if RED else (int(90 + 110 * (1 - t)), int(100 + 70 * t), 235)
    draw.line(pts, fill=tuple(int(v * (0.18 + 0.22 * np.sin(t * 3.14))) for v in c), width=2)
glow = lines.filter(ImageFilter.GaussianBlur(14 * W / 1920))
lines = lines.filter(ImageFilter.GaussianBlur(2.6 * W / 1920 + 0.6))
art = np.asarray(base, np.float32) + np.asarray(lines, np.float32) * 0.8 + np.asarray(glow, np.float32) * 0.9

# Faint dot grid, fading in towards the right, for a technical feel.
grid = np.zeros((H, W), np.float32)
grid[::24, ::24] = 1.0
grid = np.asarray(Image.fromarray((grid * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8)), np.float32)
art += (grid / 255.0 * 38 * np.clip(nx * 1.4 - 0.2, 0, 1) * (1 - ny))[..., None]

# Keep the clock corner and the dock area dark enough for white text and the tiles.
shade = 1.0 - 0.55 * np.clip((ny - 0.45) / 0.5, 0, 1) ** 1.3
shade *= 1.0 - 0.35 * np.exp(-(((nx - 0.18) / 0.25) ** 2 + ((ny - 0.15) / 0.18) ** 2))
art *= shade[..., None]

# A little grain so dark gradients don't band on an 8-bit panel.
art += rng.normal(0, 1.6, art.shape)
Image.fromarray(np.clip(art, 0, 255).astype(np.uint8)).save(out, "JPEG", quality=95, subsampling=0, optimize=True)
print(out, round(float(np.clip(art, 0, 255).mean()), 1))
