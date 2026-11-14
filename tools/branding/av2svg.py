"""Minimal Android VectorDrawable -> SVG converter, used to preview and rasterize the generated XML."""
import sys, xml.etree.ElementTree as ET
A = "{http://schemas.android.com/apk/res/android}"
def color(c):
    if c is None: return "none", 1
    c = c.lstrip("#")
    if len(c) == 8: return "#" + c[2:], int(c[:2], 16) / 255
    return "#" + c, 1
gid = [0]
def conv(el, out, defs):
    if el.tag == "group":
        g = lambda k, d: float(el.get(A + k, d))
        px, py, s, sy = g("pivotX", 0), g("pivotY", 0), g("scaleX", 1), g("scaleY", 1)
        tx, ty = g("translateX", 0), g("translateY", 0)
        out.append(f'<g transform="translate({tx+px},{ty+py}) scale({s},{sy}) translate({-px},{-py})">')
        for c in el: conv(c, out, defs)
        out.append("</g>")
    elif el.tag == "path":
        d = el.get(A + "pathData"); fill, fo = color(el.get(A + "fillColor"))
        for at in el:
            if at.get("name") == "android:fillColor":
                gr = at[0]; gid[0] += 1; i = f"g{gid[0]}"
                stops = "".join(f'<stop offset="{it.get(A+"offset")}" stop-color="{color(it.get(A+"color"))[0]}" stop-opacity="{color(it.get(A+"color"))[1]:.3f}"/>' for it in gr)
                defs.append(f'<radialGradient id="{i}" gradientUnits="userSpaceOnUse" cx="{gr.get(A+"centerX")}" cy="{gr.get(A+"centerY")}" r="{gr.get(A+"gradientRadius")}">{stops}</radialGradient>')
                fill, fo = f"url(#{i})", 1
        stroke, so = color(el.get(A + "strokeColor"))
        sw = el.get(A + "strokeWidth", "0")
        out.append(f'<path d="{d}" fill="{fill}" fill-opacity="{fo}" stroke="{stroke}" stroke-opacity="{so}" stroke-width="{sw}"/>')
def to_svg(path, w, h, under="", over=""):
    root = ET.parse(path).getroot()
    vw, vh = root.get(A + "viewportWidth"), root.get(A + "viewportHeight")
    out, defs = [], []
    for c in root: conv(c, out, defs)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {vw} {vh}" preserveAspectRatio="xMidYMid meet">'
            f'<defs>{"".join(defs)}</defs>{under}{"".join(out)}{over}</svg>')
if __name__ == "__main__":
    print(to_svg(sys.argv[1], sys.argv[2], sys.argv[3]))
