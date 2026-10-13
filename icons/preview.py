import cairosvg, io, gen
from PIL import Image, ImageDraw

SZ = 300
sheet_w = SZ*3 + 40*4
sheet_h = SZ*2 + 40*3
sheet = Image.new("RGB", (sheet_w, sheet_h), (32, 34, 40))

def render(shapes, prefix):
    svg = gen.to_svg(shapes, prefix)
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=SZ, output_height=SZ)
    return Image.open(io.BytesIO(png)).convert("RGBA")

def mask_round(img, radius):
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, img.size[0]-1, img.size[1]-1], radius=radius, fill=255)
    out = Image.new("RGBA", img.size, (0,0,0,0)); out.paste(img, (0,0), m); return out

def mask_circle(img):
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).ellipse([0, 0, img.size[0]-1, img.size[1]-1], fill=255)
    out = Image.new("RGBA", img.size, (0,0,0,0)); out.paste(img, (0,0), m); return out

for i, key in enumerate(["A", "B", "C"]):
    bg, fg = gen.CONCEPTS[key]()
    base = render(bg, key+"b")
    base.alpha_composite(render(fg, key+"f"))
    # Android 遮罩只顯示中央 72/108 的範圍
    crop = int(SZ * 72 / 108)
    off = (SZ - crop)//2
    view = base.crop((off, off, off+crop, off+crop)).resize((SZ, SZ), Image.LANCZOS)
    x = 40 + i*(SZ+40)
    sheet.paste(mask_round(view, int(SZ*0.24)), (x, 40), mask_round(view, int(SZ*0.24)))
    sheet.paste(mask_circle(view), (x, 40+SZ+40), mask_circle(view))

sheet.save("/home/claude/icons/preview.png")
print("ok")
