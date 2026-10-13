# -*- coding: utf-8 -*-
"""產生系統各處會用到的點陣圖圖示（legacy mipmap）與主題化單色圖層。"""
import cairosvg, io, os, sys, gen
from PIL import Image, ImageDraw

RES = "/home/claude/photobox/res"
DENS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
key = (sys.argv[1] if len(sys.argv) > 1 else "C").upper()

bg, fg = gen.CONCEPTS[key]()

def render(shapes, prefix, px):
    svg = gen.to_svg(shapes, prefix)
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=px, output_height=px)
    return Image.open(io.BytesIO(png)).convert("RGBA")

def compose(px):
    """先以 4 倍解析度合成再縮圖，邊緣才乾淨。"""
    big = px * 4
    im = render(bg, "b", big)
    im.alpha_composite(render(fg, "f", big))
    crop = int(big * 72 / 108)          # Android 遮罩實際顯示的範圍
    off = (big - crop) // 2
    return im.crop((off, off, off + crop, off + crop)).resize((px, px), Image.LANCZOS)

def masked(im, circle):
    m = Image.new("L", im.size, 0)
    d = ImageDraw.Draw(m)
    w = im.size[0]
    if circle:
        d.ellipse([0, 0, w - 1, w - 1], fill=255)
    else:
        d.rounded_rectangle([0, 0, w - 1, w - 1], radius=int(w * 0.225), fill=255)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), m)
    return out

for name, px in DENS.items():
    d = os.path.join(RES, "mipmap-" + name)
    os.makedirs(d, exist_ok=True)
    src = compose(px * 4).resize((px, px), Image.LANCZOS)
    masked(src, False).save(os.path.join(d, "ic_launcher.png"))
    masked(src, True).save(os.path.join(d, "ic_launcher_round.png"))
    print(name, px, "ok")

# 主題化單色圖層
open(os.path.join(RES, "drawable", "ic_launcher_mono.xml"), "w").write(
    gen.to_vector(gen.concept_c_mono()))

ADAPTIVE = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_bg" />
    <foreground android:drawable="@drawable/ic_launcher_fg" />
    <monochrome android:drawable="@drawable/ic_launcher_mono" />
</adaptive-icon>
'''
os.makedirs(os.path.join(RES, "mipmap-anydpi-v26"), exist_ok=True)
open(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml"), "w").write(ADAPTIVE)
open(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher_round.xml"), "w").write(ADAPTIVE)
print("adaptive + monochrome written")
