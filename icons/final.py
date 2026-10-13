from PIL import Image, ImageDraw
import os, cairosvg, io, gen
RES="/home/claude/photobox/res"
W,H=940,700
sheet=Image.new("RGB",(W,H),(26,28,34))

def rounded(im,r):
    m=Image.new("L",im.size,0); ImageDraw.Draw(m).rounded_rectangle([0,0,im.size[0]-1,im.size[1]-1],radius=r,fill=255)
    o=Image.new("RGBA",im.size,(0,0,0,0)); o.paste(im,(0,0),m); return o
def circled(im):
    m=Image.new("L",im.size,0); ImageDraw.Draw(m).ellipse([0,0,im.size[0]-1,im.size[1]-1],fill=255)
    o=Image.new("RGBA",im.size,(0,0,0,0)); o.paste(im,(0,0),m); return o

bg,fg=gen.CONCEPTS["C"]()
def compose(px):
    big=px*3
    im=Image.open(io.BytesIO(cairosvg.svg2png(bytestring=gen.to_svg(bg,"b").encode(),output_width=big,output_height=big))).convert("RGBA")
    im.alpha_composite(Image.open(io.BytesIO(cairosvg.svg2png(bytestring=gen.to_svg(fg,"f").encode(),output_width=big,output_height=big))).convert("RGBA"))
    c=int(big*72/108); o=(big-c)//2
    return im.crop((o,o,o+c,o+c)).resize((px,px),Image.LANCZOS)

# 第一排：啟動器會套用的三種遮罩
big=compose(600).resize((200,200),Image.LANCZOS)
sq=rounded(big,int(200*0.225)); ci=circled(big); sqr=rounded(big,int(200*0.46))
for i,img in enumerate([sq,ci,sqr]):
    sheet.paste(img,(40+i*(200+30),34),img)

# 第二排：實際點陣圖縮圖尺寸
x=40; y=290
for name,s in [("xxxhdpi",192),("xxhdpi",144),("xhdpi",96),("hdpi",72),("mdpi",48)]:
    im=Image.open(os.path.join(RES,"mipmap-"+name,"ic_launcher.png")).convert("RGBA")
    sheet.paste(im,(x, y+(192-s)//2),im); x+=s+26

# 第三排：Android 13+ 主題化圖示
mono=Image.open(io.BytesIO(cairosvg.svg2png(bytestring=gen.to_svg(gen.concept_c_mono(),"m").encode(),
        output_width=900,output_height=900))).convert("RGBA")
c=int(900*72/108); o=(900-c)//2
mono=mono.crop((o,o,o+c,o+c)).resize((180,180),Image.LANCZOS)
for i,(plate,tint) in enumerate([((214,226,240),(41,58,92)),((44,52,70),(198,216,240)),
                                 ((233,214,206),(92,58,41)),((60,48,44),(240,216,198))]):
    p=Image.new("RGBA",(180,180),(0,0,0,0)); ImageDraw.Draw(p).ellipse([0,0,179,179],fill=plate+(255,))
    col=Image.new("RGBA",(180,180),tint+(255,)); col.putalpha(mono.split()[3]); p.alpha_composite(col)
    sheet.paste(p.convert("RGB"),(40+i*(180+30),500))
sheet.save("/home/claude/icons/final.png"); print("ok")
