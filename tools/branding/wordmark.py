import uharfbuzz as hb
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
FONT = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"

def wordmark(text="CineVault", height=None):
    """Return (pathData, width, capHeight) in font units scaled so cap height == 1 unit * scale."""
    blob = hb.Blob.from_file_path(FONT); face = hb.Face(blob); font = hb.Font(face)
    buf = hb.Buffer(); buf.add_str(text); buf.guess_segment_properties()
    hb.shape(font, buf, {"kern": True, "liga": True})
    tt = TTFont(FONT); gs = tt.getGlyphSet(); order = tt.getGlyphOrder()
    cap = tt["OS/2"].sCapHeight
    s = 1.0 / cap
    pen = SVGPathPen(gs, ntos=lambda v: f"{v:.3f}".rstrip("0").rstrip("."))
    x = 0
    for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
        name = order[info.codepoint]
        # flip y, put baseline at y = cap (top of caps at 0)
        tp = TransformPen(pen, (s, 0, 0, -s, (x + pos.x_offset) * s, 1.0))
        gs[name].draw(tp)
        x += pos.x_advance
    return pen.getCommands(), x * s
if __name__ == "__main__":
    d, w = wordmark(); print(w, len(d))
