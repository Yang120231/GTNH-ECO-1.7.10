"""Import the exact modern AE2 API primitives used by source business into our platform namespace."""
import hashlib
import argparse
import json
import subprocess
import re
from pathlib import Path
from import_business import adapt

ROOT = Path(__file__).resolve().parent.parent
REPO = ROOT.parent / "Applied-Energistics-2"
COMMIT = "db17504a86128fdf3dae31f5fb7a112a646e0b93"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--refresh-manifest", action="store_true", help="Record reviewed formatting/adaptation without overwriting the target")
    parser.add_argument("--import-missing", action="store_true", help="Import only new selected adapters during manifest refresh")
    args = parser.parse_args()
    manifest = ROOT / "porting/dependency-adapters.json"
    previous = json.loads(manifest.read_text(encoding="utf8")) if manifest.exists() else []
    known = {entry["target"]: entry["target_sha256"] for entry in previous}
    entries = []
    for name, package in [("ICraftingInventory", "crafting/inv"), ("ListCraftingInventory", "crafting/inv"), ("CalculationStrategy", "api/networking/crafting"),
                          ("InputTemplate", "crafting/execution"), ("CraftingCpuHelper", "crafting/execution"),
                          ("ICraftingCPU", "api/networking/crafting"), ("CraftingJobStatus", "api/networking/crafting"), ("CpuSelectionMode", "api/config"),
                          ("AEKeyFilter", "api/storage"), ("NoOpKeyFilter", "api/storage"),
                          ("CraftingPlanSummary", "menu/me/crafting"), ("CraftingPlanSummaryEntry", "menu/me/crafting")]:
        source = "src/main/java/appeng/" + package + "/" + name + ".java"
        original = subprocess.check_output(["git", "-C", str(REPO), "show", COMMIT + ":" + source])
        selected_members = None
        data = original
        if name == "CraftingCpuHelper":
            selected_members = ["reinjectPatternInputs", "getValidItemTemplates", "extractTemplates", "calculatePatternPower", "generateLinkData", "tryExtractInitialItems", "<init>"]
            classes = ROOT / "build/porting-tools"
            classes.mkdir(parents=True, exist_ok=True)
            subprocess.check_call(["javac", "-encoding", "UTF-8", "-d", str(classes), str(ROOT/"porting/SourceMembers.java")])
            temporary = ROOT / "build/porting-parse/CraftingCpuHelper.java"
            temporary.parent.mkdir(parents=True, exist_ok=True)
            temporary.write_bytes(original)
            members = json.loads(subprocess.check_output(["java", "-Dfile.encoding=UTF-8", "-cp", str(classes), "SourceMembers", str(temporary)], encoding="utf8"))
            content = "public class CraftingCpuHelper {\n" + "\n".join(m["text"] for m in members if m["name"] in selected_members) + "\n}\n"
            text = original.decode("utf8").replace("\r\n", "\n")
            header = text[:text.index("package ")]
            imports = [line for line in re.findall(r"(?m)^import [^;]+;", text)
                       if re.search(r"\b"+re.escape(line.removesuffix(";").rsplit(".", 1)[-1])+r"\b", content)]
            data = (header + "package appeng.crafting.execution;\n" + "\n".join(imports) + "\n" + content).encode("utf8")
        data = data.replace(b"package appeng.crafting.inv;", b"package cn.dancingsnow.neoecoae.platform.ae2.crafting.inventory;")
        data = data.replace(b"package appeng.api.networking.crafting;", b"package cn.dancingsnow.neoecoae.platform.ae2.crafting;")
        data = data.replace(b"package appeng.crafting.execution;", b"package cn.dancingsnow.neoecoae.platform.ae2.crafting;")
        data = data.replace(b"package appeng.api.config;", b"package cn.dancingsnow.neoecoae.platform.ae2.crafting;")
        data = data.replace(b"package appeng.api.storage;", b"package cn.dancingsnow.neoecoae.platform.ae2.crafting;")
        data = data.replace(b"package appeng.menu.me.crafting;", b"package cn.dancingsnow.neoecoae.platform.ae2.crafting;")
        data = adapt(data)
        # The target key counter removes zero entries immediately. Publish clear notifications from a snapshot.
        data = data.replace(b"for (var stack : list) {\n            // First clear", b"for (var stack : list.copy()) {\n            // First clear")
        target = "src/main/java/cn/dancingsnow/neoecoae/platform/ae2/crafting/" + ("inventory/" if package == "crafting/inv" else "") + name + ".java"
        path = ROOT / target
        write = not args.refresh_manifest or (args.import_missing and not path.exists())
        if args.refresh_manifest and not write:
            if not path.exists(): raise SystemExit("Missing dependency adapter: " + target)
            data = path.read_bytes()
        elif path.exists() and hashlib.sha256(path.read_bytes()).hexdigest() != known.get(target) and path.read_bytes() != data:
            raise SystemExit("Refusing to overwrite adapter edits: " + target)
        if write:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        license_name = "MIT" if b"The MIT License" in original else "LGPL-3.0-or-later"
        entries.append({"repository": str(REPO), "commit": COMMIT, "tag": "neoforge/v19.2.18", "source": source,
                        "source_sha256": hashlib.sha256(original).hexdigest(), "target": target,
                        "target_sha256": hashlib.sha256(data).hexdigest(), "license": license_name + "; original source header preserved where present"})
        if selected_members: entries[-1]["selected_members"] = selected_members
    for batch_path in sorted((ROOT/"porting/batches").glob("*.json")):
        batch = json.loads(batch_path.read_text(encoding="utf8"))
        for original_entry in batch.get("dependency_imports", []):
            entry = dict(original_entry)
            repository = Path(entry["repository"])
            if not repository.is_absolute(): repository = ROOT.parent/repository
            repository = repository.resolve()
            original = subprocess.check_output(["git", "-C", str(repository), "show", entry["commit"]+":"+entry["source"]])
            if hashlib.sha256(original).hexdigest() != entry["source_sha256"]:
                raise SystemExit("Dependency batch source hash mismatch: " + entry["source"])
            target = (ROOT/entry["target"]).resolve()
            if not target.is_relative_to((ROOT/"src/main/java/cn/dancingsnow/neoecoae").resolve()) or not target.is_file():
                raise SystemExit("Dependency batch target outside the compiled project namespace: " + entry["target"])
            if not entry.get("license"):
                raise SystemExit("Dependency batch has no license evidence: " + entry["source"])
            entry["repository"] = str(repository)
            entry["target_sha256"] = hashlib.sha256(target.read_bytes()).hexdigest()
            entry["batch_manifest"] = batch_path.relative_to(ROOT).as_posix()
            entries.append(entry)
    manifest.write_text(json.dumps(entries, indent=2) + "\n", encoding="utf8")
    license_state_path = ROOT / "porting/dependency-licenses.json"
    previous_licenses = json.loads(license_state_path.read_text(encoding="utf8")) if license_state_path.exists() else []
    license_hashes = {entry["target"]: entry["target_sha256"] for entry in previous_licenses}
    licenses = []
    for source, name in [("LICENSE", "ae2-modern-LGPL-3.0.txt"), ("src/main/java/appeng/api/networking/crafting/ICraftingCPU.java", "ae2-modern-api-MIT.txt")]:
        original = subprocess.check_output(["git", "-C", str(REPO), "show", COMMIT + ":" + source])
        content = original if source == "LICENSE" else original[:original.index(b"package ")].strip() + b"\n"
        target = "src/main/resources/META-INF/licenses/" + name
        path = ROOT / target
        if path.exists() and path.read_bytes() != content and hashlib.sha256(path.read_bytes()).hexdigest() != license_hashes.get(target):
            raise SystemExit("Refusing to overwrite license edits: " + target)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        licenses.append(dict(repository=str(REPO), commit=COMMIT, source=source, source_sha256=hashlib.sha256(original).hexdigest(),
            target=target, target_sha256=hashlib.sha256(content).hexdigest()))
    license_state_path.write_text(json.dumps(licenses, indent=2)+"\n", encoding="utf8")
    print(json.dumps({"dependency_adapters": len(entries)}))


if __name__ == "__main__":
    main()
