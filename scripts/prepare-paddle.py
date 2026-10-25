#!/usr/bin/env python3
"""Build-time model preparation. No Python/converter/downloader is included in the APK."""
from pathlib import Path
import hashlib,json,subprocess,sys,struct,zipfile,os,shutil
ROOT=Path(__file__).resolve().parents[1];lock=json.loads((ROOT/'scripts/paddle-models.json').read_text());out=Path(os.environ.get('PADDLE_ASSET_OUTPUT',str(ROOT/'app/src/main/assets/paddle')));cache=ROOT/'.native-deps/paddle-build';cache.mkdir(parents=True,exist_ok=True)
locksha=hashlib.sha256((ROOT/'scripts/paddle-models.json').read_bytes()).hexdigest();manifest=out/'manifest.json'
if manifest.exists():
 try:
  old=json.loads(manifest.read_text())
  if old['source_lock_sha256']==locksha and all(hashlib.sha256((out/name).read_bytes()).hexdigest()==entry['sha256'] for name,entry in old['files'].items()):
   print('Verified cached Paddle assets');sys.exit(0)
 except (KeyError,OSError,ValueError):pass

def fetch(url,dest,expected):
 if dest.exists() and hashlib.sha256(dest.read_bytes()).hexdigest()==expected:return
 part=dest.with_suffix(dest.suffix+'.part');subprocess.run(['curl','--fail','--location','--retry','3','--silent','--show-error',url,'-o',str(part)],check=True)
 if hashlib.sha256(part.read_bytes()).hexdigest()!=expected:part.unlink();raise SystemExit('Paddle preparation hash mismatch')
 part.replace(dest)
# A Linux x86_64 CPython 3.12 build tool, independent of target Android ABI.
wheel=cache/'pnnx.whl';fetch(lock['pnnx_wheel']['url'],wheel,lock['pnnx_wheel']['sha256']);tool=cache/'pnnx'
with zipfile.ZipFile(wheel) as z:
 names=[n for n in z.namelist()if n=='pnnx/pnnx'];assert len(names)==1;tool.write_bytes(z.read(names[0]))
if hashlib.sha256(tool.read_bytes()).hexdigest()!=lock['pnnx_wheel']['executable_sha256']:raise SystemExit('PNNX executable hash mismatch')
tool.chmod(0o700)
venv=cache/'venv';python=venv/'bin/python'
if not python.exists():subprocess.run([sys.executable,'-m','venv',str(venv)],check=True)
subprocess.run([str(python),'-m','pip','install','--disable-pip-version-check','--only-binary=:all:','--require-hashes','-r',str(ROOT/'scripts/paddle-conversion-requirements.txt')],check=True)
out.mkdir(parents=True,exist_ok=True);env={**os.environ,'OMP_NUM_THREADS':'1','OPENBLAS_NUM_THREADS':'1'}
for name,spec in lock['models'].items():
 src=cache/name;src.mkdir(exist_ok=True)
 for f in spec['files']:fetch(f['url'],src/f['file'],f['sha256'])
 onnx=src/'model.onnx'
 subprocess.run([sys.executable,str(ROOT/'scripts/offline-build-exec.py'),str(venv/'bin/paddle2onnx'),'--model_dir',str(src),'--model_filename','inference.json','--params_filename','inference.pdiparams','--save_file',str(onnx),'--opset_version','13','--enable_onnx_checker','True','--optimize_tool','None'],env=env,check=True)
 stage='det' if name.endswith('_det') else 'rec';shape='[1,3,960,960]' if stage=='det' else '[1,3,48,960]'
 subprocess.run([sys.executable,str(ROOT/'scripts/offline-build-exec.py'),str(tool),str(onnx),f'inputshape={shape}','fp16=0',f'ncnnparam={out}/{stage}.param',f'ncnnbin={out}/{stage}.bin'],env=env,check=True,cwd=cache)
 if stage=='rec':
  # Only a YAML safe loader from the pinned conversion environment is used.
  source="import yaml,json,sys; print(json.dumps(['<blank>']+yaml.safe_load(open(sys.argv[1]))['PostProcess']['character_dict']+[' '],ensure_ascii=False))"
  keys=json.loads(subprocess.run([str(python),'-c',source,str(src/'inference.yml')],check=True,capture_output=True,text=True).stdout);assert len(keys)==18385
  with (out/'keys.bin').open('wb') as f:
   f.write(struct.pack('<I',len(keys)))
   for key in keys:b=key.encode('utf-8');assert len(b)<=64;f.write(struct.pack('<I',len(b))+b)
files={name:{'sha256':hashlib.sha256((out/name).read_bytes()).hexdigest(),'bytes':(out/name).stat().st_size} for name in ['det.param','det.bin','rec.param','rec.bin','keys.bin']}
manifest.write_text(json.dumps({'source_lock_sha256':locksha,'models':lock['models'],'converter':{'paddle':lock['paddle'],'paddle2onnx':lock['paddle2onnx'],'pnnx':lock['pnnx_version'],'executable_sha256':lock['pnnx_wheel']['executable_sha256']},'precision':'FP32','conversion_network_denied':True,'files':files},indent=2))
print('Prepared and hashed Paddle assets; runtime does not download models')
