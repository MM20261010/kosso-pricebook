#!/usr/bin/env python3
"""Build-time only. Pin/check native math sources; no downloads occur from the application."""
from pathlib import Path
import json,hashlib,subprocess,tarfile,shutil,tempfile
ROOT=Path(__file__).resolve().parents[1];out=ROOT/'.native-deps';out.mkdir(exist_ok=True)
lock=json.loads((ROOT/'scripts/native-sources.json').read_text())
for entry in lock['sources']:
 name=entry['name'];dest=out/name
 marker=dest/'.source-sha256' if entry['archive'] else dest
 if entry['archive'] and marker.is_file() and marker.read_text().strip()==entry['sha256']:continue
 if not entry['archive'] and dest.is_file() and hashlib.sha256(dest.read_bytes()).hexdigest()==entry['sha256']:continue
 archive=out/(name+'.download')
 subprocess.run(['curl','--fail','--location','--retry','3','--silent','--show-error',entry['url'],'-o',str(archive)],check=True)
 if hashlib.sha256(archive.read_bytes()).hexdigest()!=entry['sha256']:archive.unlink();raise SystemExit('Native source hash mismatch: '+name)
 if entry['archive']:
  with tempfile.TemporaryDirectory(dir=out) as tmp:
   with tarfile.open(archive) as t:
    if sum(m.size for m in t.getmembers())>1024*1024*1024:raise SystemExit('Native archive limit')
    t.extractall(tmp,filter='data')
   roots=list(Path(tmp).iterdir())
   if len(roots)!=1:raise SystemExit('Unexpected source root')
   if dest.exists():shutil.rmtree(dest)
   roots[0].rename(dest)
  marker.write_text(entry['sha256']+'\n')
 else:archive.replace(dest)
 if archive.exists():archive.unlink()
 print('Verified native source:',name)
licenses=ROOT/'app/src/main/assets/licenses';licenses.mkdir(parents=True,exist_ok=True)
for source,name in [(out/'ncnn/LICENSE.txt','ncnn-LICENSE.txt'),(out/'opencv/LICENSE','OpenCV-LICENSE.txt')]:shutil.copyfile(source,licenses/name)
(licenses/'Clipper-LICENSE.txt').write_text('Clipper 6.4.2 by Angus Johnson, Boost Software License 1.0.\nhttps://www.boost.org/LICENSE_1_0.txt\n\n'+(out/'clipper.hpp').read_text().split('#ifndef')[0])

# Preserve copyright/disclaimer blocks of the selected OpenCV modules and Android CPU feature helper.
notices = []
for folder in ('modules/core', 'modules/imgproc'):
 for source in sorted((out/'opencv'/folder).rglob('*')):
  if source.suffix not in ('.h', '.hpp', '.cpp', '.c', '.inl') or not source.is_file(): continue
  body = source.read_text(errors='replace')
  import re
  prefix = re.match(r'(?s)(?:\s+|//[^\n]*(?:\n|$)|/\*.*?\*/)*', body).group(0).strip()
  if prefix and ('opyright' in prefix or 'icense' in prefix):
   notices.append((str(source.relative_to(out/'opencv')),prefix))
text = 'OpenCV core/imgproc source copyright and license notices. Optional files may not be linked.\n'
seen = set()
for name,notice in notices:
 if notice not in seen: text+='\n--- '+name+' ---\n'+notice+'\n'; seen.add(notice)
for name in ('3rdparty/cpufeatures/LICENSE','modules/core/3rdparty/SoftFloat/COPYING.txt'):
 text+='\n--- '+name+' ---\n'+(out/'opencv'/name).read_text()+'\n'
(licenses/'OpenCV-THIRD-PARTY-NOTICES.txt').write_text(text)
