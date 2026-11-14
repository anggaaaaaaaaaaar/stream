"""Generates CineVault icon resources (vector XML + PNG previews) from one geometry source."""
import os, subprocess, sys
from logo import ring_paths, c_letter, V_PATH, TRI, GLOW_C, WHITE, RED, BG, f
from wordmark import wordmark

RES = sys.argv[1]
OUT = os.path.dirname(os.path.abspath(__file__))

def circle(cx, cy, r):
    return f"M{f(cx-r)},{f(cy)}a{f(r)},{f(r)} 0,1 0 {f(2*r)},0a{f(r)},{f(r)} 0,1 0 {f(-2*r)},0Z"

GLOW_R = 14.0
GLOW_STOPS = [(0, "#FF2B3A", 0.80), (0.4, "#FF2B3A", 0.30), (1, "#FF2B3A", 0.0)]
TRI_STOPS = [(0, "#FF7A84", 1), (1, "#FF2B3A", 1)]

def elements(glow=True, mono=None):
    w = mono or WHITE; r = mono or RED
    bands, hinges = ring_paths()
    els = []
    if glow and not mono:
        els.append(dict(name="glow", d=circle(*GLOW_C, GLOW_R), grad=("radial", GLOW_C, GLOW_R, GLOW_STOPS)))
    for i, b in enumerate(bands + hinges):
        els.append(dict(name=f"ring{i}", d=b, fill=w))
    els.append(dict(name="letter_c", d=c_letter(), stroke=w, sw=3.5))
    els.append(dict(name="letter_v", d=V_PATH, fill=w))
    if glow and not mono:
        els.append(dict(name="play", d=TRI, grad=("radial", (62.6, 54.0), 9.0, TRI_STOPS)))
    else:
        els.append(dict(name="play", d=TRI, fill=r))
    return els

def hexa(color, a):
    return "#%02X%s" % (round(a * 255), color[1:])

# ---------- Android vector writer ----------
def av_path(e, ind):
    p = ind
    attrs = [f'android:name="{e["name"]}"', f'android:pathData="{e["d"]}"']
    if "fill" in e: attrs.append(f'android:fillColor="{e["fill"]}"')
    if "stroke" in e:
        attrs += [f'android:strokeColor="{e["stroke"]}"', f'android:strokeWidth="{e["sw"]}"']
    if "grad" not in e:
        return f"{p}<path\n" + "".join(f"{p}    {a}\n" for a in attrs[:-1]) + f"{p}    {attrs[-1]} />\n"
    kind, (cx, cy), rad, stops = e["grad"]
    s = f"{p}<path\n" + "".join(f"{p}    {a}\n" for a in attrs[:-1]) + f"{p}    {attrs[-1]}>\n"
    s += f'{p}    <aapt:attr name="android:fillColor">\n'
    s += f'{p}        <gradient\n{p}            android:type="radial"\n{p}            android:centerX="{f(cx)}"\n{p}            android:centerY="{f(cy)}"\n{p}            android:gradientRadius="{f(rad)}">\n'
    for o, c, a in stops:
        s += f'{p}            <item android:offset="{o}" android:color="{hexa(c, a)}" />\n'
    s += f"{p}        </gradient>\n{p}    </aapt:attr>\n{p}</path>\n"
    return s

def vector(w, h, vw, vh, body, aapt=False, comment=None):
    head = '<?xml version="1.0" encoding="utf-8"?>\n'
    if comment: head += f"<!-- {comment} -->\n"
    ns = 'xmlns:android="http://schemas.android.com/apk/res/android"'
    if aapt: ns += '\n    xmlns:aapt="http://schemas.android.com/aapt"'
    return (head + f'<vector {ns}\n    android:width="{w}dp"\n    android:height="{h}dp"\n'
            f'    android:viewportWidth="{f(vw)}"\n    android:viewportHeight="{f(vh)}">\n' + body + "</vector>\n")

def group(name, body, sx=1, tx=0, ty=0, px=None, py=None):
    a = [f'android:name="{name}"']
    if px is not None: a += [f'android:pivotX="{f(px)}"', f'android:pivotY="{f(py)}"']
    if sx != 1: a += [f'android:scaleX="{f(sx)}"', f'android:scaleY="{f(sx)}"']
    if tx: a.append(f'android:translateX="{f(tx)}"')
    if ty: a.append(f'android:translateY="{f(ty)}"')
    ind = "    "
    return f"{ind}<group\n" + "".join(f"{ind}    {x}\n" for x in a[:-1]) + f"{ind}    {a[-1]}>\n" + \
        "".join("    " + l + "\n" for l in body.rstrip("\n").split("\n")) + f"{ind}</group>\n"

def logo_body(glow=True, mono=None, ind="    "):
    return "".join(av_path(e, ind) for e in elements(glow, mono))

GEN = "Generated from the CineVault logo. Keep in sync with the other ic_cinevault_* drawables."

def write(rel, text):
    path = os.path.join(RES, rel); os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w").write(text); print("wrote", rel)

WM_D, WM_W = wordmark()

def wordmark_paths(scale, tx, ty, color=WHITE, ind="    "):
    return group("wordmark", f'{ind}<path\n{ind}    android:name="wordmark_text"\n{ind}    android:fillColor="{color}"\n{ind}    android:pathData="{WM_D}" />\n', sx=scale, tx=tx, ty=ty)

def main():
    # adaptive icon foreground (108dp, content within the 66dp safe zone)
    write("drawable-v24/ic_launcher_foreground.xml", vector(108, 108, 108, 108, logo_body(True), aapt=True, comment=GEN))
    write("drawable/ic_launcher_foreground.xml", vector(108, 108, 108, 108, logo_body(False), comment=GEN))
    # themed-icon monochrome layer and the notification small icon
    write("drawable/ic_cloudstream_monochrome.xml", vector(108, 108, 108, 108, logo_body(False, "#FFFFFFFF"), comment=GEN))
    write("drawable/ic_cloudstream_monochrome_big.xml",
          vector(24, 24, 66, 66, group("logo", logo_body(False, "#FFFFFFFF"), tx=-21, ty=-21), comment=GEN))
    # logo without padding, used by the in-app intro and the setup screen
    write("drawable-v24/ic_cinevault_logo.xml",
          vector(200, 200, 66, 66, group("logo", logo_body(True), tx=-21, ty=-21), aapt=True, comment=GEN))
    write("drawable/ic_cinevault_logo.xml",
          vector(200, 200, 66, 66, group("logo", logo_body(False), tx=-21, ty=-21), comment=GEN))
    # wordmark (Inter Bold outlines), cap height 1 unit
    wm_h = 1.25  # room for descender-free text plus small margin
    write("drawable/ic_cinevault_wordmark.xml",
          vector(round(40 * (WM_W + 0.04) / wm_h), 40, WM_W + 0.04, wm_h,
                 wordmark_paths(1, 0.02, 0.12), comment=GEN + " Text outlines from Inter Bold (OFL)."))
    # TV banner 320x180: logo left, wordmark right
    s_logo = 1.5  # 66 -> 99
    wm_cap = 26
    total = 66 * s_logo + 14 + WM_W * wm_cap
    lx, ly = (320 - total) / 2, (180 - 66 * s_logo) / 2
    wm_x = lx + 66 * s_logo + 14
    body = f'    <path\n        android:name="background"\n        android:fillColor="{BG}"\n        android:pathData="M0,0h320v180h-320z" />\n'
    body += group("logo", logo_body(True, ind="        ").replace("\n        ", "\n        "), sx=s_logo,
                  tx=lx - 21 * s_logo, ty=ly - 21 * s_logo)
    body += wordmark_paths(wm_cap, wm_x, (180 - wm_cap) / 2)
    write("drawable-v24/ic_banner_background.xml", vector(320, 180, 320, 180, body, aapt=True, comment=GEN))
    return wm_x, wm_cap

if __name__ == "__main__":
    print(main())
