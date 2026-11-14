import math
CX, CY = 54.0, 54.0
R, RW = 29.6, 4.6            # ring centerline radius, stroke width
WHITE, RED, BG = "#EDEEF0", "#FF2B3A", "#1C1D21"

def f(v): return f"{v:.2f}".rstrip("0").rstrip(".")

def pt(a, r, cx=CX, cy=CY):
    t = math.radians(a)
    return cx + r * math.cos(t), cy + r * math.sin(t)

def arc(a0, a1, r, cx=CX, cy=CY):
    x0, y0 = pt(a0, r, cx, cy); x1, y1 = pt(a1, r, cx, cy)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return f"M{f(x0)},{f(y0)}A{f(r)},{f(r)} 0,{large} 1 {f(x1)},{f(y1)}"

def ring_band(a0, a1, r0, r1):
    """Filled annular sector from angle a0 to a1 (clockwise in screen coords)."""
    p0 = pt(a0, r1); p1 = pt(a1, r1); p2 = pt(a1, r0); p3 = pt(a0, r0)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return (f"M{f(p0[0])},{f(p0[1])}A{f(r1)},{f(r1)} 0,{large} 1 {f(p1[0])},{f(p1[1])}"
            f"L{f(p2[0])},{f(p2[1])}A{f(r0)},{f(r0)} 0,{large} 0 {f(p3[0])},{f(p3[1])}Z")

HINGES = (144.0, 216.0)   # angles of the two vault hinges (left side)
GAP = 1.5                 # degrees of dark gap either side of a hinge
HW = 11.0                 # hinge angular width

def ring_paths():
    """Ring split into bands so the hinges read as separate pieces."""
    ri, ro = R - RW / 2, R + RW / 2
    h1, h2 = HINGES
    bands = []
    # main ring: from hinge2 end round the right side to hinge1 start
    bands.append(ring_band(h2 + HW / 2 + GAP, h1 - HW / 2 - GAP + 360, ri, ro))
    # left segment between the hinges, slightly thinner
    bands.append(ring_band(h1 + HW / 2 + GAP, h2 - HW / 2 - GAP, ri, ro))
    hinges = [ring_band(h - HW / 2, h + HW / 2, ri - 1.8, ro) for h in HINGES]
    return bands, hinges

def c_letter():
    return arc(42, 318, 6.1, 41.6, 54.0)  # stroked, width 3.5

V_PATH = "M45.7,46.1L49.3,46.1L51.1,55.6L52.9,46.1L56.5,46.1L52.9,61.9L49.3,61.9Z"
# play triangle with rounded corners
TRI = "M60.4,46.9Q58.9,46.1 58.9,47.8L58.9,60.2Q58.9,61.9 60.4,61.1L71.2,55.0Q72.6,54.0 71.2,53.0Z"
GLOW_C = (63.8, 54.0)

def svg(size, bg=None, shape="none", glow=True, mono=None):
    w = WHITE if mono is None else mono
    r = RED if mono is None else mono
    bands, hinges = ring_paths()
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 108 108">',
           '<defs><radialGradient id="g"><stop offset="0" stop-color="#FF2B3A" stop-opacity="0.75"/>'
           '<stop offset="0.45" stop-color="#FF2B3A" stop-opacity="0.28"/>'
           '<stop offset="1" stop-color="#FF2B3A" stop-opacity="0"/></radialGradient></defs>']
    if bg:
        if shape == "square":
            out.append(f'<rect x="0" y="0" width="108" height="108" rx="24" fill="{bg}"/>')
        elif shape == "circle":
            out.append(f'<circle cx="54" cy="54" r="54" fill="{bg}"/>')
        else:
            out.append(f'<rect width="108" height="108" fill="{bg}"/>')
    if glow and mono is None:
        out.append(f'<circle cx="{GLOW_C[0]}" cy="{GLOW_C[1]}" r="16" fill="url(#g)"/>')
    for b in bands + hinges:
        out.append(f'<path d="{b}" fill="{w}"/>')
    out.append(f'<path d="{c_letter()}" fill="none" stroke="{w}" stroke-width="3.5"/>')
    out.append(f'<path d="{V_PATH}" fill="{w}"/>')
    out.append(f'<path d="{TRI}" fill="{r}"/>')
    out.append('</svg>')
    return "\n".join(out)

