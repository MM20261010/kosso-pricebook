"""Generate a fixed fictional fiscal layout, never accepting personal receipt input."""
from pathlib import Path
import hashlib,json,sys
from PIL import Image,ImageDraw,ImageFont
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
font_path=Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc')
font=ImageFont.truetype(str(font_path),32)
im=Image.new('RGB',(1200,1400),'white');d=ImageDraw.Draw(im)
for y,s in [(20,'FICTIVE MARKET'),(76,'2026/11/03 19:00'),(120,'全品税抜表示')]:d.text((120,y),s,font=font,fill='black')
names=['架空もやし 200g','架空パン 6枚入','架空コーヒー 500g','架空豆腐 300g','架空洗剤 CV-20','架空ビタミンC']
amounts=[6,158,1460,490,399,359];y=185
for n,name in enumerate(names):
 d.text((120,y),name,font=font,fill='black');d.text((990,y+6),str(amounts[n])+'C',font=font,fill='black')
 if n in (1,3):y+=42;d.text((145,y),'@'+('79' if n==1 else '245')+'×2',font=font,fill='black')
 y+=62
for s in ['小未読8点 2872','税率  点数  税抜対象額  消費税額','8%   5点     960       77','10%  3点    1912      191','税額合計 268','日計 3140','クレジット支払 2000','現金支払 1000','ポイント支払 140','預り金 1500','お釣り 500']:
 d.text((120,y),s,font=font,fill='black');y+=48
path=out/'v0810-fiscal-layout.png';im.save(path)
case={'id':'v0810-fiscal-layout','image':path.name,'items':names,'line_amounts':amounts,'date':'2026-11-03','fiscal_review':True,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'font_sha256':hashlib.sha256(font_path.read_bytes()).hexdigest()}
(out/'v0810-fixture.json').write_text(json.dumps(case,ensure_ascii=False,indent=2)+'\n')
print('Generated one fictional fiscal image; no personal data input supported.')
