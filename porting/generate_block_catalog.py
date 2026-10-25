"""Extract source basic-block factories/properties. Machine declarations remain with their imported tile factories."""
import hashlib
import json
import re
import subprocess
from pathlib import Path
from generate_content_catalog import ROOT, REPO, REVISION, PACKAGE, source


def main():
    data = source("all/NEBlocks.java")
    target = ROOT / "build/porting-parse/NEBlocks.java"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    classes = ROOT / "build/porting-tools"
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.check_call(["javac", "-encoding", "UTF-8", "-d", str(classes), str(ROOT / "porting/SourceMembers.java")])
    members = json.loads(subprocess.check_output(["java", "-Dfile.encoding=UTF-8", "-cp", str(classes), "SourceMembers", str(target)], encoding="utf8"))
    entries = []
    for member in members:
        text = member["text"]
        if member["kind"] != "VARIABLE" or not re.search(r"BlockEntry<(Block|CasingBlock)>\s", text):
            continue
        block_id = re.search(r'\.block\("([^"]+)"', text)[1]
        base = re.search(r"\.initialProperties\(\(\) -> Blocks\.(\w+)\)", text)[1]
        entry = {"id": block_id, "source_field": member["name"], "source_line": member["line"], "base": base,
                 "factory": "frame" if "BlockEntry<CasingBlock>" in text else "block",
                 "no_occlusion": ".noOcclusion()" in text,
                 "harvest_level": 3 if "NEEDS_DIAMOND_TOOL" in text else 2 if "NEEDS_IRON_TOOL" in text else 0}
        strength = re.search(r"\.strength\(([\d.]+)[fF]?(?:,\s*([\d.]+)[fF]?)?\)", text)
        if strength:
            entry["hardness"] = float(strength[1])
            entry["resistance"] = float(strength[2] or strength[1])
        drop = re.search(r"createOreDrop\(block, NEItems\.(\w+)\.get\(\)\)", text)
        if drop:
            entry["ore_drop"] = drop[1].lower()
        entries.append(entry)
    if len(entries) != 13:
        raise ValueError("Review changed source basic block factories: " + str(len(entries)))
    document = {"source_commit": REVISION, "source": PACKAGE + "all/NEBlocks.java", "source_sha256": hashlib.sha256(data).hexdigest(), "blocks": entries}
    output = ROOT / "src/main/resources/neoecoae/core-blocks.json"
    output.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"basic_blocks": len(entries), "bases": sorted({entry["base"] for entry in entries}), "factories": sorted({entry["factory"] for entry in entries})}))


if __name__ == "__main__":
    main()
