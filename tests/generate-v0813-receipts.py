"""Fixed synthetic alternative amounts and damaged notation; no personal receipt input."""
from pathlib import Path
import hashlib,json,sys
from PIL import Image,ImageDraw,ImageFont
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
font_path=Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc');font=ImageFont.truetype(str(font_path),32)
names=['架空もやし 200g','架空パン 6枚入','架空コーヒー 500g','架空豆腐 300g','架空洗剤 CV-20','架空ビタミンC'];amounts=[126,182,1300,630,421,341]
fixtures=[]
for mode in ('damaged','split'):
 im=Image.new('RGB',(1200,1600),'white');d=ImageDraw.Draw(im)
 for y,s in [(20,'FICTIVE MARKET'),(76,'2026/11/04 19:00')]:d.text((120,y),s,font=font,fill='black')
 y=185
 for n,name in enumerate(names):
  d.text((120,y),name,font=font,fill='black');d.text((990,y+6),str(amounts[n])+'C',font=font,fill='black')
  if n in (1,3):y+=42;d.text((145,y),'@'+('91' if n==1 else '315')+'×2',font=font,fill='black')
  y+=62
 d.text((120,y),'小未読8点',font=font,fill='black');d.text((990,y+6),'3000',font=font,fill='black');y+=48
 d.text((120,y),'点計',font=font,fill='black');y+=44
 lines=['8%5点 1800','10%3点 1200'] if mode=='damaged' else ['8%','5点','1800','10%','3点','1200']
 for s in lines:d.text((120,y),s,font=font,fill='black');y+=42
 for s in ['消費税額(内訳)','8% 144','10% 120']:d.text((120,y),s,font=font,fill='black');y+=44
 # Printed corruption is intentional simulation; distinguish it from naturally emitted OCR corruption.
 d.text((120,y),'白言十' if mode=='damaged' else '日計',font=font,fill='black');d.text((960,y+6),'3.264' if mode=='damaged' else '3,264',font=font,fill='black');y+=52
 for s in ['カード支払 2000','現金支払 1000','ポイント支払 264','預り金 1500','お釣り 500']:d.text((120,y),s,font=font,fill='black');y+=44
 path=out/('v0813-'+mode+'-fiscal.png');im.save(path);im.save(out/(path.stem+'.ppm'))
 fixtures.append({'id':'v0813-'+mode+'-fiscal','image':path.name,'items':names,'line_amounts':amounts,'date':'2026-11-04','fiscal_rescue':True,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'font_sha256':hashlib.sha256(font_path.read_bytes()).hexdigest()})
(out/'v0813-fixtures.json').write_text(json.dumps({'synthetic_only':True,'fixtures':fixtures},ensure_ascii=False,indent=2)+'\n')
print('Generated two fictional fiscal rescue images with different amounts; no receipt input accepted.')
