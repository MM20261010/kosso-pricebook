"""Deterministic fictional fiscal layouts. Accepts an output folder, no receipt input."""
from pathlib import Path
import hashlib,json,sys
from PIL import Image,ImageDraw,ImageFont
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
font_path=Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc')
font=ImageFont.truetype(str(font_path),32)
names=['架空もやし 200g','架空パン 6枚入','架空コーヒー 500g','架空豆腐 300g','架空洗剤 CV-20','架空ビタミンC'];amounts=[6,158,1460,490,399,359]
fixtures=[]
for mode in ('columns','stacked','stacked-tall-stress'):
 im=Image.new('RGB',(1200,1800 if mode=='stacked-tall-stress' else 1600),'white');d=ImageDraw.Draw(im)
 for y,s in [(20,'FICTIVE MARKET'),(76,'2026/11/04 19:00'),(120,'全品税抜表示')]:d.text((120,y),s,font=font,fill='black')
 y=185
 for n,name in enumerate(names):
  d.text((120,y),name,font=font,fill='black');d.text((990,y+6),str(amounts[n])+'C',font=font,fill='black')
  if n in (1,3):y+=42;d.text((145,y),'@'+('79' if n==1 else '245')+'×2',font=font,fill='black')
  y+=62
 d.text((120,y),'小未読8点',font=font,fill='black');d.text((990,y+6),'2872',font=font,fill='black');y+=48
 d.text((120,y),'税率区分 点数 税抜対象額 消費税額(内訳)',font=font,fill='black');y+=48
 if mode=='columns':
  for s in ['---','8% 5点 960 77','10% 3点 1912 191']:d.text((120,y),s,font=font,fill='black');y+=45
 else:
  for s in ['8%','5点','960','77','10%','3点','1912','191']:d.text((120,y),s,font=font,fill='black');y+=42
 d.text((120,y),'日計',font=font,fill='black');d.text((990,y+6),'3140',font=font,fill='black');y+=52
 for s in ['クレジット支払 2000','現金支払 1000','ポイント支払 140','預り金 1500','お釣り 500']:d.text((120,y),s,font=font,fill='black');y+=44
 path=out/('v0812-'+mode+'-fiscal.png');im.save(path);im.save(out/(path.stem+'.ppm'))
 fixtures.append({'id':'v0812-'+mode+'-fiscal','image':path.name,'items':names,'line_amounts':amounts,'date':'2026-11-04','fiscal_recovery':mode!='stacked-tall-stress','known_ocr_limit':mode=='stacked-tall-stress','sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'font_sha256':hashlib.sha256(font_path.read_bytes()).hexdigest()})
(out/'v0812-fixtures.json').write_text(json.dumps({'synthetic_only':True,'fixtures':fixtures},ensure_ascii=False,indent=2)+'\n')
print('Generated two fiscal reconstruction images and one known-limit stress image; fictional only.')
