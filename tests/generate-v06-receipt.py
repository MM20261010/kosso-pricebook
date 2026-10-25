"""Generate only hardcoded fictional data; no real image or receipt input is accepted."""
from pathlib import Path
import hashlib,json,sys
from PIL import Image,ImageDraw,ImageFont
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
font_path=Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc')
font=ImageFont.truetype(str(font_path),32)
im=Image.new('RGB',(1080,1280),'white');draw=ImageDraw.Draw(im)
draw.text((80,20),'FICTIVE MARKET',font=ImageFont.truetype(str(font_path),42),fill='black')
for y,s in [(86,'2026/10/25 18:30'),(130,'全品税抜表示')]:draw.text((50,y),s,font=font,fill='black')
names=['架空りんご ※','架空食パン C','架空洗濯洗剤','架空台所洗剤','20枚入','架空スポンジ']
prices=[480,480,700,398,428,386];units=[240,480,350,398,428,386];quantities=[2,1,2,1,1,1]
y=190
for i,name in enumerate(names):
 if i==4:draw.text((50,y),'架空ごみ袋',font=font,fill='black');y+=42
 draw.text((50,y),name,font=font,fill='black')
 draw.text((850,y+8),str(prices[i])+('C' if i<2 else 'V'),font=font,fill='black')
 draw.text((75,y+42),'@'+str(units[i])+'×'+str(quantities[i]),font=font,fill='black');y+=84
for s in ['小訐 2872','8%対象額 960 税77','10%対象額 1912 税191','税額合計 268','台計 3140','購入点数 8点','カード支払 3140','店番号 0000 レジ番号 00']:
 draw.text((50,y),s,font=font,fill='black');y+=42
path=out/'v06-mixed-layout.png';im.save(path)
case={'id':'v06-mixed-layout','image':path.name,'items':['架空りんご','架空食パン','架空洗濯洗剤','架空台所洗剤','架空ごみ袋20枚入','架空スポンジ'],'line_amounts':prices,'ambiguous_suffix_items':[1],'quantities':quantities,'units':units,'date':'2026-10-25','basis':'EXCLUSIVE','subtotal':2872,'tax':268,'total':3140,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'font_sha256':hashlib.sha256(font_path.read_bytes()).hexdigest()}
(out/'v06-fixture.json').write_text(json.dumps(case,ensure_ascii=False,indent=2)+'\n')
print('Generated one fictional six-product receipt (no real data input)')
