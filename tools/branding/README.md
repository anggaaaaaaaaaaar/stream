# CineVault branding

Scripts that generate the launcher icon, TV banner, monochrome/notification icons, the
splash logo and the wordmark used by `ui/intro/CineVaultIntro.kt`. All of them come from
the geometry in `logo.py`, so edit that file and regenerate instead of hand-editing the XML.

Requirements: Python 3 with `fonttools` and `uharfbuzz`, the Inter Bold font
(`/usr/share/fonts/opentype/inter/Inter-Bold.otf`, OFL), and Node with Playwright + Chromium
for the PNG fallbacks.

```sh
cd tools/branding
python3 build.py ../../app/src/main/res                    # vector drawables
python3 raster.py ../../app/src/main/res /tmp/cv-preview ../../app/src/main  # PNG mipmaps, banner, play store icon, previews
```

| File | Output |
|---|---|
| `drawable-v24/ic_launcher_foreground.xml` | adaptive icon foreground (with glow) |
| `drawable/ic_launcher_foreground.xml` | API 23 fallback without gradients |
| `drawable/ic_cloudstream_monochrome*.xml` | themed icon layer, notification icon |
| `drawable(-v24)/ic_cinevault_logo.xml` | logo without padding (splash, intro, setup screen) |
| `drawable/ic_cinevault_wordmark.xml` | "CineVault" outlines from Inter Bold |
| `drawable-v24/ic_banner_background.xml` | Android TV banner |
