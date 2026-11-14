"""Render the generated vectors to PNG: legacy launcher icons, banner fallback, play store icon, previews."""
import os, sys, subprocess, tempfile
from av2svg import to_svg
RES = sys.argv[1]; PREV = sys.argv[2]; ROOT = sys.argv[3]
BG = "#1C1D21"
os.makedirs(PREV, exist_ok=True)
jobs = []
def job(svg, out, w, h): jobs.append((svg, out, w, h))
fg = os.path.join(RES, "drawable-v24/ic_launcher_foreground.xml")
# Legacy icons: crop the 108 viewport to 18..90 so the logo fills the tile like a pre-O launcher icon.
def legacy(shape):
    bg = (f'<rect x="18" y="18" width="72" height="72" rx="15" fill="{BG}"/>' if shape == "square"
          else f'<circle cx="54" cy="54" r="36" fill="{BG}"/>')
    s = to_svg(fg, 100, 100, under=bg)
    return s.replace('viewBox="0 0 108 108"', 'viewBox="18 18 72 72"')
for dpi, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    job(legacy("square"), f"{RES}/mipmap-{dpi}/ic_launcher.png", px, px)
    job(legacy("circle"), f"{RES}/mipmap-{dpi}/ic_launcher_round.png", px, px)
full = to_svg(fg, 100, 100, under=f'<rect width="108" height="108" fill="{BG}"/>')
job(full, f"{ROOT}/ic_launcher-playstore.png", 512, 512)
banner = to_svg(os.path.join(RES, "drawable-v24/ic_banner_background.xml"), 320, 180)
job(banner, f"{RES}/mipmap-xhdpi/ic_banner.png", 320, 180)
# previews
job(full, f"{PREV}/adaptive_full.png", 432, 432)
circ = to_svg(fg, 100, 100, under=f'<rect width="108" height="108" fill="{BG}"/>').replace("<svg ", "<svg style=\"clip-path:circle(33.4%)\" ")
job(circ, f"{PREV}/adaptive_circle.png", 432, 432)
job(to_svg(os.path.join(RES, "drawable/ic_cloudstream_monochrome.xml"), 100, 100, under='<rect width="108" height="108" fill="#3B4A5A"/>'), f"{PREV}/monochrome.png", 216, 216)
job(to_svg(os.path.join(RES, "drawable/ic_cloudstream_monochrome_big.xml"), 100, 100, under='<rect width="66" height="66" fill="#333"/>'), f"{PREV}/notification.png", 96, 96)
job(to_svg(os.path.join(RES, "drawable/ic_cinevault_wordmark.xml"), 100, 100, under='<rect width="100" height="100" fill="#333"/>'), f"{PREV}/wordmark.png", 600, 116)
job(banner, f"{PREV}/banner.png", 640, 360)
tmp = tempfile.mkdtemp()
js = ["const { chromium } = require('playwright');(async()=>{const b=await chromium.launch();"]
for i, (svg, out, w, h) in enumerate(jobs):
    p = os.path.join(tmp, f"{i}.html")
    open(p, "w").write(f'<html><body style="margin:0;background:transparent">{svg.replace("width=\"100\" height=\"100\"", f"width=\"{w}\" height=\"{h}\"").replace("width=\"320\" height=\"180\"", f"width=\"{w}\" height=\"{h}\"")}</body></html>')
    js.append(f"{{const p=await b.newPage({{viewport:{{width:{w},height:{h}}}}});await p.goto('file://{p}');await p.screenshot({{path:'{out}',omitBackground:true}});await p.close();}}")
js.append("await b.close();})();")
open(os.path.join(tmp, "r.js"), "w").write("".join(js))
subprocess.run(["node", os.path.join(tmp, "r.js")], check=True, env={**os.environ, "NODE_PATH": subprocess.check_output(["npm", "root", "-g"]).decode().strip()})
print("rendered", len(jobs))
