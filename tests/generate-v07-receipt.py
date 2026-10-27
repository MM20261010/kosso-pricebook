"""Generate one hardcoded fictional receipt. No real image/text input is accepted."""
from pathlib import Path
import hashlib,json,sys
from PIL import Image,ImageDraw,ImageFont
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
font_path=Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc')
font=ImageFont.truetype(str(font_path),32);code_font=ImageFont.truetype(str(font_path),25)
im=Image.new('RGB',(1200,1360),'white');draw=ImageDraw.Draw(im)
draw.text((190,20),'FICTIVE MARKET',font=ImageFont.truetype(str(font_path),42),fill='black')
for y,s in [(86,'2026/10/27 19:00'),(130,'全品税抜表示')]:draw.text((190,y),s,font=font,fill='black')
names=['架空もやし 200g','架空パン 6枚入','架空コーヒー 500g','架空豆腐 300g','架空洗剤 CV-20','架空ビタミンC']
prices=[6,158,1460,490,399,359];units=[6,79,1460,245,399,359];quantities=[1,2,1,2,1,1]
draw.text((20,178),'商品コード',font=ImageFont.truetype(str(font_path),22),fill='black')
draw.text((210,178),'商品名',font=code_font,fill='black')
draw.text((990,178),'行金額',font=code_font,fill='black')
y=230
for i,name in enumerate(names):
 draw.text((20,y+4),'00000'+str(i+1),font=code_font,fill='black')
 draw.text((210,y),name,font=font,fill='black')
 draw.text((990,y+8),str(prices[i])+('C' if i<3 else 'V'),font=font,fill='black')
 draw.text((235,y+42),'@'+str(units[i])+'×'+str(quantities[i]),font=font,fill='black');y+=84
for s in ['小訐 2872','8%消費税 77','10%消費税 191','消費税額 268','台計 3140','購入点数 8点','カード支払 3140','取引番号 00000000','レジ番号 00','担当者番号 00']:
 draw.text((190,y),s,font=font,fill='black');y+=42
path=out/'v07-unit-layout.png';im.save(path)
case={'id':'v07-unit-layout','image':path.name,'items':names,'line_amounts':prices,'quantities':quantities,'units':units,'date':'2026-10-27','basis':'EXCLUSIVE','subtotal':2872,'tax':268,'total':3140,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'font_sha256':hashlib.sha256(font_path.read_bytes()).hexdigest()}
(out/'v07-fixture.json').write_text(json.dumps(case,ensure_ascii=False,indent=2)+'\n')
print('Generated one fictional receipt; tax bands have no per-product assignment')
