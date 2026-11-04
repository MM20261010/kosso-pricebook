"""Fixed fictional split fiscal images; no image, receipt text or user data input."""
from pathlib import Path
import hashlib,json,sys
from PIL import Image,ImageDraw,ImageFont
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
font_path=Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc')
font=ImageFont.truetype(str(font_path),32)
names=['架空もやし 200g','架空パン 6枚入','架空コーヒー 500g','架空豆腐 300g','架空洗剤 CV-20','架空ビタミンC'];amounts=[6,158,1460,490,399,359]
fixtures=[]
for mode in ('split','wrapped'):
 im=Image.new('RGB',(1200,1600),'white');d=ImageDraw.Draw(im)
 for y,s in [(20,'FICTIVE MARKET'),(76,'2026/11/04 19:00'),(120,'全品税抜表示')]:d.text((120,y),s,font=font,fill='black')
 y=185
 for n,name in enumerate(names):
  d.text((120,y),name,font=font,fill='black');d.text((990,y+6),str(amounts[n])+'C',font=font,fill='black')
  if n in (1,3):y+=42;d.text((145,y),'@'+('79' if n==1 else '245')+'×2',font=font,fill='black')
  y+=62
 d.text((120,y),'小未読8点',font=font,fill='black');d.text((990,y+6),'2872',font=font,fill='black');y+=48
 lines=['税抜対象額','8%対象5点 960','10%対象3点 1912','消費税額 8%77 10%191'] if mode=='split' else ['税率','対象点数','税抜対象額','消費税額','8%  5点    960      77','10% 3点   1912     191']
 for s in lines:d.text((120,y),s,font=font,fill='black');y+=44
 d.text((120,y),'日計(税込)' if mode=='split' else '目計',font=font,fill='black')
 if mode=='split':y+=44
 d.text((910 if mode=='split' else 990,y),'¥ 3,140' if mode=='split' else '3140',font=font,fill='black');y+=48
 for s in ['クレジット支払 2000','現金支払 1000','ポイント支払 140','預り金 1500','お釣り 500']:d.text((120,y),s,font=font,fill='black');y+=44
 path=out/('v0811-'+mode+'-fiscal.png');im.save(path)
 fixtures.append({'id':'v0811-'+mode+'-fiscal','image':path.name,'items':names,'line_amounts':amounts,'date':'2026-11-04','fiscal_stages':True,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'font_sha256':hashlib.sha256(font_path.read_bytes()).hexdigest()})
 im.save(out/(path.stem+'.ppm'))
(out/'v0811-fixtures.json').write_text(json.dumps({'synthetic_only':True,'fixtures':fixtures},ensure_ascii=False,indent=2)+'\n')
print('Generated two fictional fiscal ablations; no real source files are accepted.')
