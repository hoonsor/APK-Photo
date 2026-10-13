import cairosvg, io, gen
from PIL import Image, ImageDraw
shapes = gen.concept_c_mono()
svg = gen.to_svg(shapes, "m")
png = cairosvg.svg2png(bytestring=svg.encode(), output_width=1200, output_height=1200)
im = Image.open(io.BytesIO(png)).convert("RGBA")
crop = int(1200*72/108); off=(1200-crop)//2
im = im.crop((off,off,off+crop,off+crop)).resize((260,260), Image.LANCZOS)
sheet = Image.new("RGB",(260*2+60,260+40),(28,30,36))
for i,(bgc,tint) in enumerate([((214,226,240),(41,58,92)), ((44,52,70),(198,216,240))]):
    plate = Image.new("RGBA",(260,260),(0,0,0,0))
    d=ImageDraw.Draw(plate); d.ellipse([0,0,259,259], fill=bgc+(255,))
    col = Image.new("RGBA",(260,260), tint+(255,))
    col.putalpha(im.split()[3])
    plate.alpha_composite(col)
    sheet.paste(plate.convert("RGB"), (20+i*(260+20), 20))
sheet.save("/home/claude/icons/mono.png"); print("ok")
