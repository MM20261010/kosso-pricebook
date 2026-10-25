import hashlib,json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import sys
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)  # explicit scratch directory only
font_path=Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc')
font=ImageFont.truetype(str(font_path),32)
# Every store, receipt and timestamp is fictitious. No real source files are accepted.
cases=[
 ('simple',['店名: 架空スーパー青空店','2026/10/20 17:35','全品税込表示','牛乳 1L            218','食パン 6枚           158','豆腐 300g            88','小計                464','内消費税             34','合計                464','現金               1000','お釣り              536','お買上点数             3'],['牛乳 1L','食パン 6枚','豆腐 300g'],[218,158,88],None),
 ('multiline',['店名: 架空マート試験店','2026年10月21日 09:15','全品税込表示','りんご','@100x2             200※','にんじん','数量3点             300※','台所洗剤             330 10%','小計                830','内消費税             63','合計                830','カード支払           830','会員ポイント             8'],['りんご','にんじん','台所洗剤'],[200,300,330],{'りんじょ':None,'りんご':{'quantity':2,'unit':100},'にんじん':{'quantity':3,'unit':None}}),
 ('discount',['店名: 架空ストア比較店','2026-10-22 19:40','税抜表示','ヨーグルト 400g        198','納豆 3個             108','緑茶 500ml           128','小計                434','商品値引             -20','8%対象額             414','消費税額               33','お支払金額            447','PayPay              447','獲得ポイント             4'],['ヨーグルト 400g','納豆 3個','緑茶 500ml'],[198,108,128],None),
 ('mixed_tax',['店名: 架空テスト南店','2026/10/23 12:08','全品税込表示','おにぎり              129 8%','ミネラル水 2L          108※','ごみ袋 20枚           220 10%','小計                 457','8%税込対象額           237','10%税込対象額          220','内消費税               37','合 計                 457','お預り金額           1000','釣銭                 543','合計点数                 3'],['おにぎり','ミネラル水 2L','ごみ袋 20枚'],[129,108,220],None),
]
manifest=[]
for index,(name,lines,items,amounts,details) in enumerate(cases):
 variants=['clean'] if name!='simple' else ['clean','shadow_skew','faded']
 for variant in variants:
  height=80+len(lines)*57;im=Image.new('RGB',(880,height),'white');d=ImageDraw.Draw(im)
  line_entries=[]
  for i,line in enumerate(lines):
   y=28+i*57;d.text((45,y),line,font=font,fill=(0,0,0));line_entries.append({'text':line,'box':[24,y-3,860,y+48]})
  if variant=='shadow_skew':
   a=np.asarray(im).astype(np.float32);a*=np.linspace(.5,.96,880)[None,:,None];im=Image.fromarray(a.astype('uint8')).rotate(3,expand=False,fillcolor='white')
  if variant=='faded':
   im=Image.blend(Image.new('RGB',im.size,'white'),im,.45).filter(ImageFilter.GaussianBlur(.7))
  key=name+'-'+variant;p=out/(key+'.png');im.save(p)
  # Recognition-only crops must have the same geometry as each model input.
  # Rotated/shadow case is full-pipeline only: unrotated GT boxes are not exact crops.
  crops=[]
  if variant!='shadow_skew':
   for i,line in enumerate(line_entries):
    q=out/(key+'-line%02d.png'%i);im.crop(line['box']).save(q);crops.append({'path':q.name,'text':line['text']})
  date=['2026-10-20','2026-10-21','2026-10-22','2026-10-23'][index]
  manifest.append({'id':key,'image':p.name,'lines':[x['text'] for x in line_entries],'items':items,'line_amounts':amounts,'date':date,'explicit_quantity_unit':details or {},'crops':crops,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
(out/'manifest.json').write_text(json.dumps({'synthetic_only':True,'font':str(font_path),'font_sha256':hashlib.sha256(font_path.read_bytes()).hexdigest(),'fixtures':manifest},ensure_ascii=False,indent=2))
print('Generated',len(manifest),'synthetic receipts; real image input is not supported.')
