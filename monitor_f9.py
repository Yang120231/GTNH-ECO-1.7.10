import struct,zlib,gzip,io,os,time
import nbtlib
path=r'run/client/saves/New World/region/r.-3.-2.mca'
def read():
 with open(path,'rb') as f:data=f.read()
 bes=[]
 for idx in range(1024):
  off=int.from_bytes(data[idx*4:idx*4+3],'big')*4096; sectors=data[idx*4+3]
  if not off or not sectors: continue
  length=int.from_bytes(data[off:off+4],'big'); ctype=data[off+4]; raw=data[off+5:off+4+length]
  try: payload=zlib.decompress(raw) if ctype==2 else gzip.decompress(raw); nbt=nbtlib.File.parse(io.BytesIO(payload)); level=nbt.get('Level',nbt); bes.extend(level.get('TileEntities',level.get('block_entities',[])))
  except: pass
 f9=[]; workers=[]; cpus=[]
 for be in bes:
  bid=str(be.get('id',''))
  if bid=='neoecoae.crafting_worker':
   ts=be.get('craftingThreads',[]); busy=sum(1 for t in ts if bool(t.get('isBusy',0))); ready=sum(1 for t in ts if bool(t.get('outputsReady',0)))
   workers.append((str(be.get('x')),str(be.get('y')),str(be.get('z')),str(be.get('runningThreads')),len(ts),busy,ready))
  if bid=='neoecoae.computation_threading_core_l9' and 'CPU0' in be:
   cpu=be['CPU0']; job=cpu.get('job');
   if job:
    cpus.append((str(be.get('x')),str(be.get('y')),str(be.get('z')),str(job.get('suspended')),str(job.get('permanentExecutionError','')),str(job.get('remainingAmount')),str(job.get('waitingFor')),str(cpu.get('inventory'))[:120]))
 return os.path.getmtime(path),f9,cpus,workers
for i in range(6):
 try:
  mt,_,cpus,workers=read(); print(time.strftime('%H:%M:%S'), 'mtime='+time.strftime('%H:%M:%S',time.localtime(mt)), 'cpus=',cpus, 'busyWorkers=',[w for w in workers if w[5] or w[6]])
 except Exception as e: print('ERR',e)
 if i<5: time.sleep(5)
