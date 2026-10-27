import struct,zlib,gzip,io,glob,os,json
import nbtlib
root=r'run/client/saves/New World/region'; out=[]
for path in glob.glob(os.path.join(root,'r.*.*.mca')):
 with open(path,'rb') as f:data=f.read()
 for idx in range(1024):
  off=int.from_bytes(data[idx*4:idx*4+3],'big')*4096; sectors=data[idx*4+3]
  if not off or not sectors: continue
  length=int.from_bytes(data[off:off+4],'big'); ctype=data[off+4]; raw=data[off+5:off+4+length]
  try: payload=zlib.decompress(raw) if ctype==2 else gzip.decompress(raw); nbt=nbtlib.File.parse(io.BytesIO(payload)); level=nbt.get('Level',nbt); bes=level.get('TileEntities',level.get('block_entities',[]))
  except: continue
  for be in bes:
   if 'CPU0' in be:
    def conv(v):
     if hasattr(v,'items'): return {str(k):conv(x) for k,x in v.items()}
     if isinstance(v,(list,tuple)): return [conv(x) for x in v]
     return str(v)
    d=conv(be); print('BLOCK',d.get('id'),d.get('x'),d.get('y'),d.get('z')); print(json.dumps(d.get('CPU0'),ensure_ascii=False,indent=2)[:20000])
