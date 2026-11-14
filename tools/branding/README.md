# CineVault branding

`build.py` generates every icon resource from `cinevault-logo.jpg` (the logo on a flat
`#074222` background). Replace that file and rerun it instead of editing the PNGs.

Requirements: Python 3 with Pillow, numpy, `fonttools` and `uharfbuzz`, and the Inter Bold font
(`/usr/share/fonts/opentype/inter/Inter-Bold.otf`, OFL) for the wordmark.

```sh
cd tools/branding
python3 build.py ../../app/src/main
```

`extract.py` removes the flat background (colour-to-alpha), so the emblem can sit on any colour.

| Output | Used for |
|---|---|
| `drawable-nodpi/ic_cinevault_logo.png` | adaptive foreground (via `drawable/ic_launcher_foreground.xml`), splash, intro, setup screen |
| `drawable-nodpi/ic_cinevault_logo_mono.png` | themed icon layer (via `drawable/ic_cloudstream_monochrome.xml`) |
| `drawable-*dpi/ic_cloudstream_monochrome_big.png` | notification small icon |
| `mipmap-*dpi/ic_launcher(_round).png` | launchers before Android 8 |
| `mipmap-xhdpi`, `mipmap-xxxhdpi/ic_banner.png` | Android TV banner |
| `ic_launcher-playstore.png` | store listing |
| `drawable/ic_cinevault_wordmark.xml` | "CineVault" in the intro, outlines from Inter Bold |

Colours live in `res/values/cinevault.xml` and `ic_launcher_background.xml`: green `#074222`,
mint `#CEEFDE`, red `#F22140`.
