import re,sys
from PIL import Image,ImageDraw
s=open(sys.argv[1]).read();S=2
im=Image.new('L',(600*S,600*S),255);d=ImageDraw.Draw(im)
def col(a):
    m=re.search(r'fill="(\w+)"',a)
    if m: return 0 if m.group(1)=='black' else 255
    m=re.search(r'rgb\((\d+)',a); return int(m.group(1)) if m else 0
for t,a in re.findall(r'<(circle|line)([^>]*)/>',s):
    f=lambda k: float(re.search(k+r'="([-\d.]+)"',a).group(1))*S
    if t=='circle':
        cx,cy,r=f('cx'),f('cy'),f('r');
        sw=re.search(r'stroke-width:([\d.]+)',a)
        if sw and 'fill="none"' in a:
            w=float(sw.group(1))*S;d.ellipse([cx-r,cy-r,cx+r,cy+r],outline=col(a),width=int(w))
        else: d.ellipse([cx-r,cy-r,cx+r,cy+r],fill=col(a))
    else:
        w=float(re.search(r'stroke-width:([\d.]+)',a).group(1))*S
        x1,y1,x2,y2=f('x1'),f('y1'),f('x2'),f('y2');c=col(a)
        d.line([x1,y1,x2,y2],fill=c,width=int(w))
        for x,y in((x1,y1),(x2,y2)): d.ellipse([x-w/2,y-w/2,x+w/2,y+w/2],fill=c)
im.resize((600,600)).save(sys.argv[2])
