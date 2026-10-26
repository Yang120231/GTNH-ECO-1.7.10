"""Audit current build artifacts and source evidence; historical runtime checks are opt-in."""
from collections import Counter
from datetime import datetime, timezone
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from import_scope import source_exclusion, resource_exclusion, condition_matches

ROOT=Path(__file__).resolve().parent.parent


def sha(data):
    return hashlib.sha256(data).hexdigest()


def git(repository,*arguments):
    return subprocess.check_output(["git","-C",str(repository),*arguments]).decode("utf-8").strip()


def read_text(path):
    raw=path.read_bytes()
    return raw.decode("utf-16" if raw[:2] in (b"\xff\xfe",b"\xfe\xff") else "utf-8-sig",errors="replace")


def gregtech_resources():
    catalog_path = ROOT / "src/main/resources/neoecoae/gt-1201/catalog.json"
    if not catalog_path.exists():
        return [], None
    override = json.loads((ROOT / "porting/content-overrides.json").read_text(encoding="utf8"))
    assert override["native_gregtech"]["enabled"], "GT resources require the explicit user scope override"
    catalog = json.loads(catalog_path.read_text(encoding="utf8"))
    commit = catalog["source_commit"]
    assert re.fullmatch("[0-9a-f]{40}", commit), commit
    repository = ROOT.parent / "NeoECOAEExtension-1.20.1"
    derived = []
    seen = set()
    resource_root = (ROOT / "src/main/resources").resolve()
    for entry in catalog["files"]:
        resource = entry["resource"]
        path = (resource_root / resource).resolve()
        assert path.is_relative_to(resource_root / "neoecoae/gt-1201"), resource
        assert resource not in seen, resource
        seen.add(resource)
        assert entry["profile"] in {"gtm", "gt_legacy"} and entry["kind"] in {"recipe", "tag"}, entry
        original = subprocess.check_output(["git", "-C", str(repository), "show", commit + ":" + entry["source"]])
        assert sha(original) == entry["sha256"] and path.read_bytes() == original, resource
        derived.append(dict(target=path.relative_to(ROOT).as_posix(), target_sha256=entry["sha256"],
                            repository=str(repository), commit=commit, source=entry["source"]))
    derived.append(dict(target=catalog_path.relative_to(ROOT).as_posix(), target_sha256=sha(catalog_path.read_bytes())))
    return derived, dict(reference_commit=commit, source_files=len(catalog["files"]),
                         profiles=dict(Counter(entry["profile"] for entry in catalog["files"])))


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--build-log", default="build/porting-build.log")
    parser.add_argument("--registration-log", help="Optional complete native content/creative acceptance log")
    parser.add_argument("--include-historical-runtime", action="store_true",
                        help="Recheck existing historical game logs; this does not run the game or prove the current build")
    args=parser.parse_args()
    lock_path=ROOT/"porting/source-lock.json"
    lock=json.loads(lock_path.read_text(encoding="utf8"))
    for entry in lock["imported"]:
        assert sha((ROOT/entry["target"]).read_bytes())==entry["target_sha256"],entry["target"]
        if entry["kind"] in ("business", "test"):
            assert source_exclusion(entry["source"]) is None, entry["source"]
        elif entry["kind"] == "resource":
            assert resource_exclusion(entry["target"], (ROOT/entry["target"]).read_bytes()) is None, entry["target"]
    assert all(source_exclusion(path) is None for path in lock["pending_business"])
    for entry in lock.get("platform_adaptations", []):
        assert sha((ROOT/entry["target"]).read_bytes()) == entry["target_sha256"], entry["target"]
        assert entry["source_commit"] == lock["primary_source"]["commit"], entry["target"]
        for source_input in entry["inputs"]:
            original = subprocess.check_output(["git", "-C", lock["primary_source"]["repository"], "show",
                                               entry["source_commit"] + ":" + source_input["source"]])
            assert sha(original) == source_input["source_sha256"], source_input["source"]
    foundation=json.loads((ROOT/"porting/foundation-state.json").read_text(encoding="utf8"))
    for model in foundation["models"]:
        assert sha((ROOT/model["path"]).read_bytes())==model["sha256"],model["path"]

    repositories=[]
    for name in ["NeoECOAEExtension-1.21.1","NeoECOAEExtension-1.20.1","Applied-Energistics-2-Unofficial",
                 "ModularUI2","LDLib2","GT5-Unofficial","jabel","GTNHLib","StructureLib","NotEnoughItems","GTNHGradle"]:
        repository=ROOT.parent/name
        branch=git(repository,"branch","--show-current")
        upstream_ref=git(repository,"for-each-ref","--format=%(upstream)","refs/heads/"+branch) if branch else ""
        repositories.append(dict(repository=name,head=git(repository,"rev-parse","HEAD"),
            upstream=git(repository,"rev-parse",upstream_ref) if upstream_ref else None,
            ahead_behind=git(repository,"rev-list","--left-right","--count","HEAD..."+upstream_ref) if upstream_ref else None,
            working_tree_status=git(repository,"status","--porcelain")))
    lock["repositories"]=repositories
    lock["reference_port"]["selected_patches"]=[]
    lock["reference_port"]["unselected_patch_sha256"]=sha(subprocess.check_output(["git","-C",lock["reference_port"]["repository"],"diff","--binary","HEAD"]))
    lock["native_adapter_inputs"]=[]
    for path in ["all/NEItems.java","all/NEBlocks.java","all/NEToolTier.java","all/NECellTypes.java","api/ECOTier.java","api/IECOTier.java",
                 "items/ECOStorageCellItem.java","config/NEConfig.java"]:
        source="src/main/java/cn/dancingsnow/neoecoae/"+path
        data=subprocess.check_output(["git","-C",lock["primary_source"]["repository"],"show",lock["primary_source"]["commit"]+":"+source])
        lock["native_adapter_inputs"].append(dict(source=source,source_sha256=sha(data),policy="Registration, item/world API and configuration adapters; business inventory and planning policy remain in imported sources"))
    lock["pinned_dependency_sources"]=[
        dict(repository="Applied-Energistics-2",tag="neoforge/v19.2.18",commit=git(ROOT.parent/"Applied-Energistics-2","rev-parse","neoforge/v19.2.18^{commit}")),
        dict(repository="ModularUI",tag="1.3.2",commit=git(ROOT.parent/"ModularUI","rev-parse","1.3.2^{commit}")),
        dict(repository="Applied-Energistics-2-Unofficial",tag="rv3-beta-990-GTNH",commit=git(ROOT.parent/"Applied-Energistics-2-Unofficial","rev-parse","rv3-beta-990-GTNH^{commit}")),
        dict(repository="ModularUI2",tag="2.3.49-1.7.10",commit=git(ROOT.parent/"ModularUI2","rev-parse","2.3.49-1.7.10^{commit}"))]
    lock_path.write_text(json.dumps(lock,ensure_ascii=False,indent=2)+"\n",encoding="utf8")

    dependency_adapters=json.loads((ROOT/"porting/dependency-adapters.json").read_text(encoding="utf8"))
    for entry in dependency_adapters:
        original=subprocess.check_output(["git","-C",entry["repository"],"show",entry["commit"]+":"+entry["source"]])
        assert sha(original)==entry["source_sha256"],entry["source"]
        assert sha((ROOT/entry["target"]).read_bytes())==entry["target_sha256"],entry["target"]
    derived=json.loads((ROOT/"porting/core-content-derived.json").read_text(encoding="utf8"))["outputs"]
    derived=list(derived)
    derived.extend(json.loads((ROOT/"porting/dependency-licenses.json").read_text(encoding="utf8")))
    artwork=json.loads((ROOT/"porting/registration-textures.json").read_text(encoding="utf8"))
    for entry in artwork:
        original=subprocess.check_output(["git","-C",entry["repository"],"show",entry["commit"]+":"+entry["source"]])
        assert sha(original)==entry["source_sha256"],entry["source"]
    derived.extend(artwork)
    gt_resources, gt_reference = gregtech_resources()
    derived.extend(gt_resources)
    for path in ["neoecoae/core-blocks.json","neoecoae/core-registration.json","neoecoae/recipe-catalog.json"]:
        target="src/main/resources/"+path
        derived.append(dict(target=target,target_sha256=sha((ROOT/target).read_bytes())))
    for entry in derived: assert sha((ROOT/entry["target"]).read_bytes())==entry["target_sha256"],entry["target"]
    recipe_catalog=json.loads((ROOT/"src/main/resources/neoecoae/recipe-catalog.json").read_text(encoding="utf8"))
    for entry in recipe_catalog["recipes"]+recipe_catalog["tags"]:
        assert sha((ROOT/"src/main/resources"/entry["path"]).read_bytes())==entry["sha256"],entry["path"]

    test_counts=Counter()
    suites=[]
    for path in sorted((ROOT/"build/test-results/test").glob("TEST-*.xml")):
        suite=ET.parse(path).getroot()
        for field in ["tests","failures","errors","skipped"]:test_counts[field]+=int(suite.get(field,"0"))
        suites.append(dict(name=suite.get("name"),tests=int(suite.get("tests","0"))))
    assert test_counts["tests"]>4 and test_counts["failures"]==test_counts["errors"]==test_counts["skipped"]==0,dict(test_counts)
    build_log=read_text(ROOT/args.build_log)
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
            for removed in lock.get("removed_excluded_imports", []):
                if (ROOT / removed["target"]).exists(): continue
                if removed["target"].startswith("src/main/resources/"):
                    assert removed["target"].removeprefix("src/main/resources/") not in names, removed["target"]
                elif removed["target"].startswith("src/main/java/"):
                    suffix = ".java" if path.name.endswith("-sources.jar") else ".class"
                    assert removed["target"].removeprefix("src/main/java/").removesuffix(".java") + suffix not in names, removed["target"]
            classes=[name for name in names if name.endswith(".class")]
            majors=Counter(int.from_bytes(jar.read(name)[6:8],"big") for name in classes)
            assert not majors or set(majors)=={52},(path,majors)
            if not path.name.endswith("-sources.jar"):
                for source in main_sources:
                    primary = source.relative_to(ROOT / "src/main/java").as_posix().removesuffix(".java") + ".class"
                    if not primary.endswith("/package-info.class"):
                        assert primary in names, primary
                for entry in dependency_adapters:
                    assert entry["target"].removeprefix("src/main/java/").removesuffix(".java")+".class" in names,entry["target"]
                for entry in derived:
                    resource=entry["target"].removeprefix("src/main/resources/")
                    assert resource in names and sha(jar.read(resource))==entry["target_sha256"],resource
                for entry in business:
                    primary=entry["target"].removeprefix("src/main/java/").removesuffix(".java")+".class"
                    if not primary.endswith("/package-info.class"):assert primary in names,primary
                for entry in lock.get("platform_adaptations", []):
                    if entry.get("kind") == "test":
                        continue
                    primary=entry["target"].removeprefix("src/main/java/").removesuffix(".java")+".class"
                    assert primary in names,primary
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
    client_log=ROOT/"build/client-material-recipes-acceptance.log"
    if args.include_historical_runtime:
        for label,source in [("initial-storage","build/porting-server-first.log"),("restart-reload","build/porting-server-reload.log"),("previous-storage-recipes-mui2","build/porting-server-current.log"),("material-execution","build/server-material-execution.log"),("core-planning","build/server-core-planning.log")]:
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
        latest_server=read_text(ROOT/"build/server-material-execution.log")+read_text(ROOT/"build/server-material-execution-minecraft.log")
        for marker in ["NEOECOAE_NATIVE_CONTENT_OK", "NEOECOAE_PORT_LEGACY_BULK_BOUNDARY", "NEOECOAE_PORT_STORAGE_NORMAL_OK", "NEOECOAE_PORT_STORAGE_INFINITE_HOST_OK", "NEOECOAE_PORT_FOUNDATION_OK"]:
            assert marker in latest_server,marker
        core_server=read_text(ROOT/"build/server-core-planning.log")+read_text(ROOT/"build/server-core-planning-minecraft.log")
        for marker in ["NEOECOAE_PORT_PLANNING_OK", "emitter_only=true", "provider_dispatch=1", "NEOECOAE_PORT_STORAGE_NORMAL_OK", "NEOECOAE_PORT_STORAGE_INFINITE_HOST_OK", "NEOECOAE_NATIVE_CONTENT_OK"]:
            assert marker in core_server,marker
        client_log=ROOT/"build/client-material-recipes-acceptance.log"
        client_text=read_text(client_log)
        for marker in ["NEOECOAE_NATIVE_CONTENT_OK", "NEOECOAE_CLIENT_STORAGE_OK", "NEOECOAE_CLIENT_ACCEPTANCE_FINISHED success=true"]: assert marker in client_text,marker

    registration_acceptance=None
    installed_recipes,native_crafting=None,None
    block_catalog=json.loads((ROOT/"src/main/resources/neoecoae/core-registration.json").read_text(encoding="utf8"))
    if args.registration_log:
        registration_path=ROOT/args.registration_log
        registration_text=read_text(registration_path)
        for marker in ["NEOECOAE_CORE_BLOCKS_REGISTERED total=70 pending_behavior=47", "NEOECOAE_CONTENT_PLACEMENT_OK blocks=47", "NEOECOAE_CONTENT_ACCEPTANCE_OK total_blocks=70 new_blocks=47 visible_items=132", "NEOECOAE_CONTENT_ORDER_OK visible=132 groups=storage,crafting,computation,workstation,materials,tools tier_order=ascending"]:
            assert marker in registration_text,marker
        counts=re.search(r"NEOECOAE_SOURCE_RECIPES_REGISTERED totals=\{installed=(\d+), pending=(\d+)\} native_crafting=(\d+)", registration_text)
        assert counts, "Missing current native recipe counts"
        installed_recipes,native_crafting=int(counts[1]),int(counts[3])
        assert installed_recipes+int(counts[2])==len(recipe_catalog["recipes"])
        registration_acceptance=dict(source=args.registration_log,sha256=sha(registration_path.read_bytes()),
            registered_blocks=len(block_catalog["blocks"]),new_blocks=47,visible_items=132,
            creative_order=["storage","crafting","computation","workstation","materials","tools"],
            scope="Real FML identities, complete creative-tab listing, stable functional grouping and ascending tier/capacity order, native placement of 47 new blocks, source artwork and four creative-scroll screenshots. Pending machine behavior remains unimplemented.")

    report=dict(schema_version=1,status="business_import_in_progress",verified_at=datetime.now(timezone.utc).isoformat(),
        source_lock="source-lock.json",branch=git(ROOT,"branch","--show-current"),target_base=git(ROOT,"rev-parse","HEAD"),
        primary_source_commit=lock["primary_source"]["commit"],selected_source_patches=[],
        imported=Counter(entry["kind"] for entry in lock["imported"]),pending_source_paths=len(lock["pending_business"]),
        main_java=len(main_sources),test_java=len(list((ROOT/"src/test/java").rglob("*.java"))),
        preserved_model_files=len(foundation["models"]),preserved_model_hash_changes=[],
        tests=dict(test_counts),test_suites=suites,build_log=args.build_log,artifacts=artifacts,server_acceptance=acceptance,
        test_profile=dict(planner_challenge_enabled=True,challenge_cases=next(s["tests"] for s in suites if s["name"].endswith("TechModPlannerChallengeTest"))),
        pending_test_members=lock.get("pending_test_members", []),
        dependency_adapters=dependency_adapters,derived_resources=derived,
        excluded_source_paths=len(lock.get("excluded_sources", [])),optional_mod_integrations=False,
        native_gregtech=gt_reference, acceptance_policy=dict(mode="compile_and_build", manual_mouse_required=False,
            current_gameplay_verified=False, historical_runtime_rechecked=args.include_historical_runtime),
        content=dict(core_items=65,material_blocks=13,storage_machine_blocks=10,registered_core_blocks=len(block_catalog["blocks"]),
            blocks_pending_behavior=sum(entry["registration"]=="pending_behavior" for entry in block_catalog["blocks"]),
            source_recipes=len(recipe_catalog["recipes"]),installed_source_recipes=installed_recipes,native_crafting_recipes=native_crafting,excluded_large_workstation_cards=7),
        registration_acceptance=registration_acceptance,
        client_acceptance=(dict(source=client_log.relative_to(ROOT).as_posix(),sha256=sha(client_log.read_bytes()),
            scope="Historical client evidence only; current acceptance is compilation") if args.include_historical_runtime else None),
        outstanding=["Remaining core source files and platform adaptations are listed in source-lock.json",
            "See the current implementation record for machine and page completion; file counts are not behavior coverage"],
        no_longer_required=["Manual mouse verification", "Final live game acceptance save"])
    (ROOT/"porting/business-import-state.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
    print(json.dumps({key:report[key] for key in ["main_java","test_java","imported","pending_source_paths","tests","preserved_model_files"]}))
    print(json.dumps(artifacts))


if __name__=="__main__":
    main()
