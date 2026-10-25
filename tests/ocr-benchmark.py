#!/usr/bin/env python3
"""Optional local-only synthetic OCR comparison. Needs Pillow, numpy, tesseract, JDK17 and bundled models.
No real receipts; generated images/results stay in a temporary directory. No network calls.
"""
import json, os, pathlib, subprocess, tempfile
from PIL import Image, ImageDraw, ImageFont
import numpy as np
ROOT = pathlib.Path(__file__).resolve().parents[1]
font = ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc', 38)
lines = ['店名: 架空スーパー', '2026/10/25 18:30', '税込表示', '牛乳                 218',
         '食パン               158', '豆腐                  88', '小計                 464',
         '内消費税              34', '合計                 464', '現金                1000', 'お釣り               536', 'お買上点数             3']
base = Image.new('RGB', (1000, 1000), 'white'); draw = ImageDraw.Draw(base)
for i, line in enumerate(lines): draw.text((65, 40+i*70), line, font=font, fill='black')

def run(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=120).stdout.strip()

def ocr(image, psm, output):
    run('tesseract', str(image), str(output), '--tessdata-dir', str(ROOT/'app/src/main/assets/tessdata'),
        '-l', 'jpn+eng', '--psm', str(psm), '-c', 'preserve_interword_spaces=1')
    return output.with_suffix('.txt')

results=[]
with tempfile.TemporaryDirectory(prefix='kosso-synthetic-') as temp:
    folder=pathlib.Path(temp)
    run('javac','-encoding','UTF-8','-d',str(folder),str(ROOT/'app/src/main/java/org/privateprice/pricebook/ReceiptImage.java'),
        str(ROOT/'app/src/main/java/org/privateprice/pricebook/OcrDocument.java'),str(ROOT/'app/src/main/java/org/privateprice/pricebook/ReceiptLayout.java'),
        str(ROOT/'app/src/main/java/org/privateprice/pricebook/ReceiptParser.java'),str(ROOT/'tests/PrepareReceiptImage.java'),str(ROOT/'tests/ParseReceiptSummary.java'))
    shadow=np.asarray(base).astype(float)*(0.62+np.linspace(0,0.38,1000)[None,:,None])
    multiline=Image.new('RGB',(1000,1100),'white');md=ImageDraw.Draw(multiline)
    multi_lines=['店名: 架空スーパー','2026/10/25 18:30','税込表示','牛乳','@109x2                 218※','食パン                158 8%','豆腐                   88※','小計                  464','内消費税               34','合計                  464','現金                 1000','お釣り                536']
    for i,line in enumerate(multi_lines):md.text((65,40+i*75),line,font=font,fill='black')
    fixtures={'multiline':multiline,'clean':base,'shadow':Image.fromarray(shadow.astype('uint8')),
              'skew_shadow':Image.fromarray(shadow.astype('uint8')).rotate(3,expand=False,fillcolor='white')}
    for name,image in fixtures.items():
        source=folder/(name+'.png');image.save(source);corrected=folder/(name+'-corrected.png')
        angle=run('java','-cp',str(folder),'org.privateprice.pricebook.PrepareReceiptImage',str(source),str(corrected))
        for profile,img,psm in [('original-auto',source,3),('corrected-auto',corrected,3),('corrected-block',corrected,6)]:
            text=ocr(img,psm,folder/(name+'-'+profile))
            stats=run('java','-cp',str(folder),'org.privateprice.pricebook.ParseReceiptSummary',str(text)).split(',')
            results.append(dict(fixture=name,profile=profile,correct_items=int(stats[0]),false_candidates=int(stats[1]),date_correct=bool(int(stats[2])),quantity_unit_correct=bool(int(stats[3])) if name=='multiline' else None,angle=angle))
        # Compare separate horizontal text regions (PSM 7). This can lose row associations: benchmark only.
        im=Image.open(corrected).convert('L'); a=np.asarray(im);active=(a<120).sum(axis=1)>4
        spans=[];start=None
        for y,on in enumerate(list(active)+[False]):
            if on and start is None:start=y
            if not on and start is not None:
                if y-start>5:spans.append((start,y))
                start=None
        texts=[]
        for i,(top,bottom) in enumerate(spans):
            crop=folder/('line'+str(i)+'.png');im.crop((0,max(0,top-6),im.width,min(im.height,bottom+6))).save(crop)
            texts.append(ocr(crop,7,folder/('line'+str(i))).read_text())
        text=folder/'segmented.txt';text.write_text('\n'.join(texts))
        stats=run('java','-cp',str(folder),'org.privateprice.pricebook.ParseReceiptSummary',str(text)).split(',')
        results.append(dict(fixture=name,profile='line-regions-psm7',correct_items=int(stats[0]),false_candidates=int(stats[1]),date_correct=bool(int(stats[2])),quantity_unit_correct=bool(int(stats[3])) if name=='multiline' else None,angle=angle))
print(json.dumps(dict(engine=run('tesseract','--version').splitlines()[0],expected_items=3,synthetic_only=True,results=results),ensure_ascii=False,indent=2))
