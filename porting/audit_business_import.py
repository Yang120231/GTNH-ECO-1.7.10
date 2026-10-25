"""Audit the actual build, source provenance, retained models and Java-8 server acceptance evidence."""
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parent.parent


def sha(data):
    return hashlib.sha256(data).hexdigest()


def git(repository,*arguments):
    return subprocess.check_output(["git","-C",str(repository),*arguments]).decode("utf-8").strip()


def read_text(path):
    raw=path.read_bytes()
    return raw.decode("utf-16" if raw[:2] in (b"\xff\xfe",b"\xfe\xff") else "utf-8-sig",errors="replace")


def main():
    lock_path=ROOT/"porting/source-lock.json"
    lock=json.loads(lock_path.read_text(encoding="utf8"))
    for entry in lock["imported"]:
        assert sha((ROOT/entry["target"]).read_bytes())==entry["target_sha256"],entry["target"]
    foundation=json.loads((ROOT/"porting/foundation-state.json").read_text(encoding="utf8"))
    for model in foundation["models"]:
        assert sha((ROOT/model["path"]).read_bytes())==model["sha256"],model["path"]

    repositories=[]
    for name in ["NeoECOAEExtension-1.21.1","NeoECOAEExtension-1.20.1","Applied-Energistics-2-Unofficial",
                 "ModularUI2","LDLib2","GT5-Unofficial","jabel","GTNHLib","StructureLib","NotEnoughItems","GTNHGradle"]:
        repository=ROOT.parent/name
        repositories.append(dict(repository=name,head=git(repository,"rev-parse","HEAD"),
            upstream=git(repository,"rev-parse","@{upstream}"),
            ahead_behind=git(repository,"rev-list","--left-right","--count","HEAD...@{upstream}"),
            working_tree_status=git(repository,"status","--porcelain")))
    lock["repositories"]=repositories
    lock["reference_port"]["selected_patches"]=[]
    lock["reference_port"]["unselected_patch_sha256"]=sha(subprocess.check_output(["git","-C",lock["reference_port"]["repository"],"diff","--binary","HEAD"]))
    lock["native_adapter_inputs"]=[]
    for path in ["all/NEItems.java","all/NECellTypes.java","api/ECOTier.java","api/IECOTier.java",
                 "items/ECOStorageCellItem.java","config/NEConfig.java"]:
        source="src/main/java/cn/dancingsnow/neoecoae/"+path
        data=subprocess.check_output(["git","-C",lock["primary_source"]["repository"],"show",lock["primary_source"]["commit"]+":"+source])
        lock["native_adapter_inputs"].append(dict(source=source,source_sha256=sha(data),policy="Registration, item/world API and configuration adapters; business inventory and planning policy remain in imported sources"))
    lock["pinned_dependency_sources"]=[
        dict(repository="Applied-Energistics-2-Unofficial",tag="rv3-beta-990-GTNH",commit=git(ROOT.parent/"Applied-Energistics-2-Unofficial","rev-parse","rv3-beta-990-GTNH^{commit}")),
        dict(repository="ModularUI2",tag="2.3.49-1.7.10",commit=git(ROOT.parent/"ModularUI2","rev-parse","2.3.49-1.7.10^{commit}"))]
    lock_path.write_text(json.dumps(lock,ensure_ascii=False,indent=2)+"\n",encoding="utf8")

    test_counts=Counter()
    suites=[]
    for path in sorted((ROOT/"build/test-results/test").glob("TEST-*.xml")):
        suite=ET.parse(path).getroot()
        for field in ["tests","failures","errors","skipped"]:test_counts[field]+=int(suite.get(field,"0"))
        suites.append(dict(name=suite.get("name"),tests=int(suite.get("tests","0"))))
    assert test_counts["tests"]>4 and test_counts["failures"]==test_counts["errors"]==test_counts["skipped"]==0,dict(test_counts)
    build_log=read_text(ROOT/"build/porting-build.log")
    assert "BUILD SUCCESSFUL" in build_log and "> Task :verifyMigratedSourceCoverage" in build_log
    main_sources=sorted((ROOT/"src/main/java").rglob("*.java"))
    for path in main_sources:
        assert not re.search(r"(?m)^package (?:net\.minecraft|appeng|com\.lowdragmc)\.",path.read_text(encoding="utf8")),path

    artifacts=[]
    resources=[entry for entry in lock["imported"] if entry["kind"] in ("resource","platform_resource_alias")]
    business=[entry for entry in lock["imported"] if entry["kind"]=="business"]
    for path in sorted((ROOT/"build/libs").glob("neoecoae-0.1.0-porting*.jar")):
        with zipfile.ZipFile(path) as jar:
            names=set(jar.namelist())
            classes=[name for name in names if name.endswith(".class")]
            majors=Counter(int.from_bytes(jar.read(name)[6:8],"big") for name in classes)
            assert not majors or set(majors)=={52},(path,majors)
            if not path.name.endswith("-sources.jar"):
                for entry in business:
                    primary=entry["target"].removeprefix("src/main/java/").removesuffix(".java")+".class"
                    if not primary.endswith("/package-info.class"):assert primary in names,primary
                for entry in resources:
                    resource=entry["target"].removeprefix("src/main/resources/")
                    assert resource in names and sha(jar.read(resource))==entry["target_sha256"],resource
                metadata=json.loads(jar.read("mcmod.info"))
                info=metadata["modList"][0]
                assert info["version"]=="0.1.0-porting" and info["mcversion"]=="1.7.10",info
                assert {"gtnhlib","modularui2","appliedenergistics2"}.issubset(info["dependencies"]),info
                for name in classes:
                    assert b"java/lang/Record" not in jar.read(name),name
                    assert "/neoecoae/tile/" not in name and "/neoecoae/mixins/" not in name,name
            else:
                for source in main_sources:
                    name=source.relative_to(ROOT/"src/main/java").as_posix()
                    assert name in names and jar.read(name)==source.read_bytes(),name
            artifacts.append(dict(path=path.relative_to(ROOT).as_posix(),bytes=path.stat().st_size,sha256=sha(path.read_bytes()),
                classes=len(classes),classfile_major_counts=dict(majors),entries=len(names)))
    assert len(artifacts)==3

    acceptance=[]
    evidence_dir=ROOT/"porting/acceptance"
    evidence_dir.mkdir(exist_ok=True)
    for label,source in [("initial-storage","build/porting-server-first.log"),("restart-reload","build/porting-server-reload.log"),("final-storage-recipes-mui2","build/porting-server-current.log")]:
        path=ROOT/source
        # Forge routes its own mod/lifecycle log separately from the vanilla
        # server/command logger. Both original streams are required evidence.
        minecraft_source=source.removesuffix(".log")+"-minecraft.log"
        minecraft_path=ROOT/minecraft_source
        text=read_text(path)+"\n"+read_text(minecraft_path)
        assert "1.8.0_492" in text and "Done (" in text and "NEOECOAE_PORT_ACCEPTANCE_OK" in text,source
        assert "Encountered an unexpected exception" not in text,source
        selected=[line for line in text.splitlines() if any(token in line for token in ["Java is OpenJDK","successfully loaded","Done (","NEOECOAE_PORT_","Stopping server","Unloading dimension"])]
        target=evidence_dir/(label+".log")
        target.write_text("\n".join(selected)+"\n",encoding="utf8")
        versions={}
        for artifact in ["Applied-Energistics-2-Unofficial","ModularUI2","GTNHLib"]:
            matches=re.findall(re.escape(artifact)+r"/([^/;]+?)/[0-9a-f]{40}/"+re.escape(artifact)+r"-",text)
            versions[artifact]=sorted(set(matches))
        acceptance.append(dict(kind=label,source=source,source_sha256=sha(path.read_bytes()),minecraft_source=minecraft_source,
            minecraft_source_sha256=sha(minecraft_path.read_bytes()),excerpt=target.relative_to(ROOT).as_posix(),resolved_runtime_versions=versions))
    assert "mode=reload" in read_text(ROOT/"build/porting-server-reload-minecraft.log")
    assert "NEOECOAE_PORT_RECIPES_OK" in read_text(ROOT/"build/porting-server-current.log")
    assert "NEOECOAE_PORT_MUI2_BINDINGS_OK" in read_text(ROOT/"build/porting-server-current.log")

    report=dict(schema_version=1,status="business_import_in_progress",verified_at=datetime.now(timezone.utc).isoformat(),
        source_lock="source-lock.json",branch=git(ROOT,"branch","--show-current"),target_base=git(ROOT,"rev-parse","HEAD"),
        primary_source_commit=lock["primary_source"]["commit"],selected_source_patches=[],
        imported=Counter(entry["kind"] for entry in lock["imported"]),pending_source_paths=len(lock["pending_business"]),
        main_java=len(main_sources),test_java=len(list((ROOT/"src/test/java").rglob("*.java"))),
        preserved_model_files=len(foundation["models"]),preserved_model_hash_changes=[],
        tests=dict(test_counts),test_suites=suites,build_log="build/porting-build.log",artifacts=artifacts,server_acceptance=acceptance,
        outstanding=["ECO host/drive multiblock state and native grid lifecycle","Native planner request/confirmation and CPU execution hooks",
            "Computation and workstation business modules","Complete high-version MUI2 pages, native slot gestures and render-state binding",
            "Full recipe chain, addon mapping, NEI, client visuals and ordinary installation"])
    (ROOT/"porting/business-import-state.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
    print(json.dumps({key:report[key] for key in ["main_java","test_java","imported","pending_source_paths","tests","preserved_model_files"]}))
    print(json.dumps(artifacts))


if __name__=="__main__":
    main()
