from PIL import Image,ImageDraw
from pathlib import Path
files=[]
for root in [Path('src/main/resources/assets/neoecoae/textures/block'), Path('src/main/resources/assets/neoecoae/textures/blocks'), Path('src/main/resources/assets/neoecoae/textures/item'), Path('src/main/resources/assets/neoecoae/textures/items')]:
 for p in root.rglob('*.png'):
  if any(k in p.name.lower() for k in ['light','status','cell_north','cell_side','housing']): files.append(p)
files=files[:200]
thumb=80; cols=8; rows=(len(files)+cols-1)//cols
out=Image.new('RGB',(cols*thumb,rows*110),'white'); d=ImageDraw.Draw(out)
for i,p in enumerate(files):
 im=Image.open(p).convert('RGBA'); im.thumbnail((70,70),Image.Resampling.NEAREST)
 x=(i%cols)*thumb; y=(i//cols)*110
 out.paste(Image.new('RGB',(70,70),(180,180,180)),(x,y)); out.paste(im,(x,y),im)
 d.text((x,y+72),p.name[:12],fill='black')
out.save('tmp_lights.png')
print(len(files))
