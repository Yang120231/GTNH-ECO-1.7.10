"""Extract all core block identities; registration alone does not supply pending machine behavior."""
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
    basic_ids = {entry["id"] for entry in entries}
    storage_ids = {"storage_casing", "storage_interface", "eco_drive", "storage_vent"}
    storage_ids.update(prefix + level for prefix in ("storage_system_", "energy_cell_") for level in ("l4", "l6", "l9"))
    methods = {member["name"]: member["text"] for member in members if member["kind"] == "METHOD"}
    all_blocks = []
    for member in members:
        text = member["text"]
        if member["kind"] != "VARIABLE" or "BlockEntry<" not in text:
            continue
        direct = re.search(r'\.block\("([^"]+)"', text)
        block_id = member["name"].lower()
        factory_text = text
        if direct:
            if direct[1] != block_id:
                raise ValueError("Review changed source block identity: " + member["name"])
        else:
            call = re.search(r'=\s*(\w+)\(', text)
            if not call or call[1] not in methods:
                raise ValueError("Unclassified source block helper: " + member["name"])
            factory_text = methods[call[1]]
        base = re.search(r"\.initialProperties\(\(\) -> Blocks\.(\w+)\)", factory_text)
        if base is None:
            raise ValueError("Missing source block properties: " + block_id)
        state_path = ROOT / "src/main/resources/assets/neoecoae/blockstates" / (block_id + ".json")
        state_data = state_path.read_bytes()
        variants = json.loads(state_data)["variants"]
        cross = "CertusQuartzClusterBlock" in text
        defaults = {"facing": "up" if cross else "north", "module": "regular", "storage_mode": "input", "level": "0"}
        candidates = []
        for key, variant in variants.items():
            values = dict(pair.split("=", 1) for pair in key.split(",") if pair)
            if all(value == defaults.get(name, "false") for name, value in values.items()):
                candidates.append((key, variant))
        if len(candidates) != 1:
            raise ValueError("Ambiguous default source model: " + block_id)
        variant_key, variant = candidates[0]
        if variant.get("x", 0) or variant.get("y", 0):
            raise ValueError("Review default model rotation: " + block_id)
        entry = {"id": block_id, "source_field": member["name"], "source_line": member["line"],
                 "factory": "block", "base": base[1],
                 "registration": "basic" if block_id in basic_ids else "storage" if block_id in storage_ids else "pending_behavior",
                 "no_occlusion": cross or "noOcclusion" in factory_text,
                 "harvest_level": 3 if "NEEDS_DIAMOND_TOOL" in factory_text else 2 if "NEEDS_IRON_TOOL" in factory_text else 1 if "NEEDS_STONE_TOOL" in factory_text else 0,
                 "cross": cross, "default_variant": variant_key, "model": variant["model"],
                 "blockstate_sha256": hashlib.sha256(state_data).hexdigest()}
        strength = re.search(r"\.strength\(([\d.]+)[fF]?(?:,\s*([\d.]+)[fF]?)?\)", factory_text)
        if strength:
            entry["hardness"] = float(strength[1])
            entry["resistance"] = float(strength[2] or strength[1])
        rarity = re.search(r"Rarity\.(\w+)", text)
        entry["rarity"] = rarity[1] if rarity else "COMMON"
        if cross:
            dimensions = re.search(r"new CertusQuartzClusterBlock\((\d+), (\d+),", text)
            entry["height"], entry["inset"] = int(dimensions[1]), int(dimensions[2])
            entry["light"] = int(re.search(r"lightLevel\(s -> (\d+)\)", text)[1])
        all_blocks.append(entry)
    if len(all_blocks) != 70 or len({entry["id"] for entry in all_blocks}) != 70:
        raise ValueError("Review changed complete source block catalog")
    complete = dict(document, blocks=all_blocks)
    registration_output = ROOT / "src/main/resources/neoecoae/core-registration.json"
    registration_output.write_text(json.dumps(complete, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    # The source workstation uses three AE2 artwork files absent from the legacy AE2 JAR.
    # Keep the original pixels and already bundled LGPL license, only aliasing the atlas path.
    ae2_repo = ROOT.parent / "Applied-Energistics-2"
    ae2_commit = "db17504a86128fdf3dae31f5fb7a112a646e0b93"
    artwork = []
    for name in ("back", "bottom", "side"):
        original_path = "src/main/resources/assets/ae2/textures/block/generics/" + name + ".png"
        pixels = subprocess.check_output(["git", "-C", str(ae2_repo), "show", ae2_commit + ":" + original_path])
        alias = "src/main/resources/assets/ae2/textures/blocks/generics/" + name + ".png"
        destination = ROOT / alias
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists() and destination.read_bytes() != pixels:
            raise ValueError("Refusing to overwrite edited source artwork: " + alias)
        destination.write_bytes(pixels)
        artwork.append(dict(repository=str(ae2_repo), commit=ae2_commit, source=original_path,
                            source_sha256=hashlib.sha256(pixels).hexdigest(), target=alias,
                            target_sha256=hashlib.sha256(pixels).hexdigest(),
                            license="META-INF/licenses/ae2-modern-LGPL-3.0.txt"))
    (ROOT / "porting/registration-textures.json").write_text(json.dumps(artwork, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"all_core_blocks": len(all_blocks), "new_registrations": sum(entry["registration"] == "pending_behavior" for entry in all_blocks)}))
    print(json.dumps({"basic_blocks": len(entries), "bases": sorted({entry["base"] for entry in entries}), "factories": sorted({entry["factory"] for entry in entries})}))


if __name__ == "__main__":
    main()
