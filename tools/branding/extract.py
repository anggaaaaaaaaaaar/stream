"""Turn the flat-background logo into a transparent emblem (GIMP-style colour-to-alpha)."""
import sys
import numpy as np
from PIL import Image

BG = np.array([7, 66, 34], float)

def color_to_alpha(rgb):
    p = rgb.astype(float)
    a = np.zeros(p.shape[:2])
    for c in range(3):
        g = BG[c]
        up = (p[..., c] - g) / (255 - g)
        down = (g - p[..., c]) / g if g > 0 else np.zeros_like(up)
        a = np.maximum(a, np.where(p[..., c] >= g, up, down))
    a = np.clip(a, 0, 1)
    a = np.where(a < 0.12, 0, a)  # JPEG noise around the flat background
    safe = np.where(a > 0, a, 1)[..., None]
    f = (p - BG) / safe + BG
    f = np.clip(f, 0, 255)
    return np.dstack([f, a * 255]).astype(np.uint8)
