import struct,zlib,gzip,io,glob,os,json
import nbtlib
root=r'run/client/saves/New World/region'; count=0
for path in glob.glob(os.path.join(root,'r.*.*.mca')):
 with open(path,'rb') as f:data=f.read()
 for idx in range(1024):
  off=int.from_bytes(data[idx*4:idx*4+3],'big')*4096; sectors=data[idx*4+3]
  if not off or not sectors: continue
  length=int.from_bytes(data[off:off+4],'big'); ctype=data[off+4]; raw=data[off+5:off+4+length]
  try: payload=zlib.decompress(raw) if ctype==2 else gzip.decompress(raw); nbt=nbtlib.File.parse(io.BytesIO(payload)); level=nbt.get('Level',nbt); bes=level.get('TileEntities',level.get('block_entities',[]))
  except: continue
  for be in bes:
   if str(be.get('id',''))=='neoecoae.crafting_worker':
    ts=be.get('craftingThreads',[])
    print('worker',be.get('x'),be.get('y'),be.get('z'),'running',be.get('runningThreads'),'threads',len(ts), 'raw',str(ts)[:2500])
    count+=1
print('workers',count)
