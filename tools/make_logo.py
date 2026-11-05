from PIL import Image, ImageDraw
import math, sys
S=4; N=int(sys.argv[1]) if len(sys.argv)>1 else 1024; W=N*S
im=Image.new("RGBA",(W,W),(0,0,0,0)); d=ImageDraw.Draw(im)
d.rounded_rectangle([0,0,W-1,W-1],radius=int(W*0.19),fill=(7,193,96,255))
c=W/2; white=(255,255,255,255)
# ring
ro=W*0.155; ri=W*0.09
d.ellipse([c-ro,c-ro,c+ro,c+ro],fill=white); d.ellipse([c-ri,c-ri,c+ri,c+ri],fill=(7,193,96,255))
rays=32; w=W*0.036
for k in range(rays):
    a=2*math.pi*k/rays - math.pi/2
    r0=W*0.205; r1=W*(0.37 if k%2==0 else 0.31)
    x0,y0=c+r0*math.cos(a),c+r0*math.sin(a); x1,y1=c+r1*math.cos(a),c+r1*math.sin(a)
    d.line([x0,y0,x1,y1],fill=white,width=int(w))
    for (x,y) in ((x0,y0),(x1,y1)): d.ellipse([x-w/2,y-w/2,x+w/2,y+w/2],fill=white)
im.resize((N,N),Image.LANCZOS).save(sys.argv[2] if len(sys.argv)>2 else "wxlogo.png")
