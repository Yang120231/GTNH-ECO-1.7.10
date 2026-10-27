import struct,zlib,gzip,io,glob,os,json
import nbtlib
root=r'run/client/saves/New World/region'
found=[]
for path in glob.glob(os.path.join(root,'r.*.*.mca')):
    try:
        with open(path,'rb') as f: data=f.read()
        if len(data)<8192: continue
        for idx in range(1024):
            off=int.from_bytes(data[idx*4:idx*4+3],'big')*4096
            sectors=data[idx*4+3]
            if off==0 or sectors==0 or off+5>len(data): continue
            length=int.from_bytes(data[off:off+4],'big')
            ctype=data[off+4]
            raw=data[off+5:off+4+length]
            try:
                payload=zlib.decompress(raw) if ctype==2 else gzip.decompress(raw) if ctype==1 else raw
                nbt=nbtlib.File.parse(io.BytesIO(payload))
            except Exception:
                continue
            rootv=nbt
            level=rootv.get('Level',rootv)
            bes=level.get('TileEntities', level.get('block_entities', []))
            for be in bes:
                bid=str(be.get('id',''))
                if 'neoecoae' in bid.lower() or 'crafting' in bid.lower() or 'f9' in bid.lower():
                    item={k:str(v)[:500] for k,v in be.items() if k in ('id','x','y','z','craftingThreads','threadCount','runningThreads','job','craftingJobId','outputsReady','recoveryState','progress','batchOutputItems','batchRemainingItems','isBusy','pendingFinalOutputs','currentJob')}
                    item['region']=os.path.basename(path); item['chunkIndex']=idx
                    item['keys']=list(be.keys())
                    found.append(item)
    except Exception as e:
        pass
print(json.dumps(found,ensure_ascii=False,indent=2))
