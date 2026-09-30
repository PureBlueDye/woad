"""Writes Woad's font definitions: one per text style and per GUI scale.

Minecraft samples TrueType glyphs with NEAREST filtering, so text is only sharp when the glyphs were
rasterised at exactly the on-screen pixel density. The oversample of a font definition is fixed in
its JSON, which is why each GUI scale gets its own definition: UiText picks the one matching the
current scale at draw time, so changing the GUI scale needs no resource reload.

Run from anywhere:  python tools/make_fonts.py
"""
import json
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "woad", "font", "ui")
os.makedirs(OUT, exist_ok=True)

# style -> (file, em size in GUI pixels). Keep in sync with UiText.Style.
STYLES = {
    "title":   ("woad:inter_bold.ttf", 11.0),
    "heading": ("woad:inter_semibold.ttf", 10.5),
    "label":   ("woad:inter_semibold.ttf", 7.5),
    "body":    ("woad:inter_regular.ttf", 7.0),
}
SCALES = range(1, 9)  # denser scales (and enlarged HUDs) reuse the 8x rasterisation

# At GUI scale 1 one interface pixel is one screen pixel, and 7 px Inter is too small to read.
# Small text grows a little there. Keep in sync with UiText.Style.
SCALE_1_SIZES = {"label": 8.0, "body": 8.0}

count = 0
for style, (file, size) in STYLES.items():
    for scale in SCALES:
        data = {"providers": [
            {
                "type": "ttf",
                "file": file,
                "size": SCALE_1_SIZES.get(style, size) if scale == 1 else size,
                "oversample": float(scale),
                "shift": [0.0, 0.0],
                "skip": "",
            },
            # Anything Inter lacks (card suits, SkyBlock's star, names in other scripts) falls
            # back to the game's own fonts, as vanilla's default font chain does, instead of
            # showing as a missing-glyph box.
            {"type": "reference", "id": "minecraft:include/default", "filter": {"uniform": False}},
            {"type": "reference", "id": "minecraft:include/unifont"},
        ]}
        with open(os.path.join(OUT, "%s_%d.json" % (style, scale)), "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        count += 1

print(count, "font definitions written to", os.path.normpath(OUT))
