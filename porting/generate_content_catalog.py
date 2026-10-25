"""Extract the core item registration contract from the pinned source, without reading dirty files.

Factories are explicitly classified; an unknown factory fails generation instead of becoming a plain item.
Recipes remain in the imported data files and are installed by the recipe adapters.
"""
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REVISION = "f6003889a1352ea0d7e2e23a3868eb69943789ca"
REPO = ROOT.parent / "NeoECOAEExtension-1.21.1"
PACKAGE = "src/main/java/cn/dancingsnow/neoecoae/"


def source(path):
    return subprocess.check_output(["git", "-C", str(REPO), "show", REVISION + ":" + PACKAGE + path])


def main():
    overrides_path = ROOT / "porting/content-overrides.json"
    overrides = json.loads(overrides_path.read_text(encoding="utf8"))
    tool_dir = ROOT / "build/porting-tools"
    tool_dir.mkdir(parents=True, exist_ok=True)
    subprocess.check_call(["javac", "-encoding", "UTF-8", "-d", str(tool_dir), str(ROOT / "porting/SourceMembers.java")])
    item_bytes = source("all/NEItems.java")
    item_path = ROOT / "build/porting-parse/NEItems.java"
    item_path.parent.mkdir(parents=True, exist_ok=True)
    item_path.write_bytes(item_bytes)
    members = json.loads(subprocess.check_output(["java", "-Dfile.encoding=UTF-8", "-cp", str(tool_dir), "SourceMembers", str(item_path)], encoding="utf8"))
    entries = []
    for member in members:
        if member["kind"] != "VARIABLE" or "ItemEntry<" not in member["text"]:
            continue
        text = member["text"]
        factory = re.search(r"ItemEntry<(\w+)>", text)[1]
        direct = re.search(r"\.(?:<Item>)?item\(\s*\"([^\"]+)\"", text)
        entry = {"field": member["name"], "line": member["line"], "factory": factory}
        if direct:
            entry["id"] = direct[1]
        elif "workstationModule(" in text:
            entry["id"] = "integrated_working_station_module_" + re.search(r'workstationModule\(\s*"([^"]+)"', text)[1]
        elif "bulkItemCell(" in text:
            entry["id"] = re.search(r'bulkItemCell\(\s*"([^"]+)"', text)[1]
            entry["component"] = re.search(r"ECOTier\.\w+,\s*\"[^\"]+\",\s*(\w+),", text)[1].lower()
        elif "createComputationCell(" in text:
            entry["id"] = "eco_computation_cell_" + re.search(r'createComputationCell\(\s*"([^"]+)"', text)[1]
        else:
            raise ValueError("Unclassified item registration: " + member["name"])
        if factory == "MaterialItem":
            entry["kind"] = "material"
        elif factory in {"AxeItem", "HoeItem", "ShovelItem", "PickaxeItem", "SwordItem"}:
            entry["kind"] = "tool"
            entry["tool"] = factory.removesuffix("Item").lower()
            entry["tier"] = re.search(r"NEToolTier\.(\w+)", text)[1]
        elif factory == "SmithingTemplateItem":
            entry["kind"] = "smithing_template"
            entry["tier"] = member["name"].removesuffix("_UPGRADE_SMITHING_TEMPLATE")
        elif factory == "Item" and "workstationModule(" in text:
            entry["kind"] = "workstation_module"
        elif factory == "Item" and entry["id"] == "eco_infinite_cell_component":
            entry["kind"] = "infinite_component"
        elif factory in {"ECOStorageCellItem", "ECOInfiniteResourceCellItem", "ECOComputationCellItem"}:
            entry["kind"] = factory
        else:
            raise ValueError("Unclassified item factory: " + factory)
        entry["creative"] = "bulkItemCell(" not in text
        entry["enabled"] = entry["kind"] != "workstation_module" or overrides["large_integrated_workstation"]["register_upgrade_cards"]
        if not entry["enabled"]:
            entry["exclusion"] = "User excludes upgrade card registration for the large integrated workstation; porting/content-overrides.json"
        entries.append(entry)
    if len({entry["id"] for entry in entries}) != len(entries):
        raise ValueError("Duplicate source item ID")
    tier_bytes = source("all/NEToolTier.java")
    tiers = {}
    for match in re.finditer(r"^    (\w+)\(BlockTags\.(\w+), (\d+), ([\d.]+)[fF], ([\d.]+)[fF], (\d+),", tier_bytes.decode("utf8"), re.M):
        tiers[match[1]] = {"harvest_level": 2 if "IRON" in match[2] else 3 if "DIAMOND" in match[2] else 4,
                           "uses": int(match[3]), "speed": float(match[4]), "attack": float(match[5]), "enchantability": int(match[6])}
    catalog = {"schema_version": 1, "source_commit": REVISION, "inputs": [
        {"path": PACKAGE + "all/NEItems.java", "sha256": hashlib.sha256(item_bytes).hexdigest()},
        {"path": PACKAGE + "all/NEToolTier.java", "sha256": hashlib.sha256(tier_bytes).hexdigest()}],
        "items": entries, "tool_tiers": tiers,
        "target_overrides": {"path": "porting/content-overrides.json", "sha256": hashlib.sha256(overrides_path.read_bytes()).hexdigest()}}
    target = ROOT / "src/main/resources/neoecoae/core-content.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    generated = []
    for entry in entries:
        if "component" not in entry:
            continue
        name = entry["id"]
        housing = "neoecoae:eco_bulk_item_cell_housing"
        component = "neoecoae:" + entry["component"]
        values = {
            name: {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [{"item": housing}, {"item": component}], "result": {"id": "neoecoae:" + name, "count": 1}},
            "disassembly/" + name: {"type": "ae2:storage_cell_disassembly", "cell": "neoecoae:" + name, "cell_disassembly_items": [{"id": housing, "count": 1}, {"id": component, "count": 1}]}
        }
        for recipe_id, value in values.items():
            output = ROOT / "src/main/resources/data/neoecoae/recipe" / (recipe_id + ".json")
            output.parent.mkdir(parents=True, exist_ok=True)
            encoded = (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf8")
            if output.exists() and output.read_bytes() != encoded:
                raise ValueError("Refusing to overwrite an existing derived recipe: " + str(output))
            output.write_bytes(encoded)
            generated.append({"target": output.relative_to(ROOT).as_posix(), "target_sha256": hashlib.sha256(encoded).hexdigest(),
                              "source": PACKAGE + "all/NEItems.java", "source_sha256": hashlib.sha256(item_bytes).hexdigest(), "members": [entry["field"], "bulkItemCell"]})
    generated.append({"target": target.relative_to(ROOT).as_posix(), "target_sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
                      "source": PACKAGE + "all/NEItems.java", "source_sha256": hashlib.sha256(item_bytes).hexdigest(), "members": [entry["field"] for entry in entries]})
    (ROOT / "porting/core-content-derived.json").write_text(json.dumps({"source_commit": REVISION, "outputs": generated}, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"core_items": len(entries), "factories": sorted({entry["kind"] for entry in entries})}))


if __name__ == "__main__":
    main()
