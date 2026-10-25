"""User-approved core-only import boundary, shared by import and package audit."""
import json
import re
from pathlib import PurePosixPath

PACKAGE = "cn/dancingsnow/neoecoae/"
OPTIONAL_CELL = re.compile(
    # Core AE energy buffer blocks are unrelated to optional energy storage cells.
    r"(?:^|[/._:])(?!energy_cell_l[469](?:$|[./:]))(?:eco_)?(?:mega_[a-z_]+|digital_singularity_[a-z_]+|quantum_[a-z_]+|"
    r"(?:air|chemical|complex_omni|data|energy|experience|fe|lightning|mana|matter|omni|"
    r"radioactive_chemical|soul|source|spent_nuclear_waste|stress)_(?:storage_)?cell[a-z0-9_]*)"
)
OPTIONAL_PATH_PARTS = {
    "ae2lt", "ae2omnicells", "ae2pattern", "appbot", "appex", "appflux", "appliedcreate",
    "appliedpneumatics", "appliedsoul", "appmek", "apprep", "arsenergistique", "bigger_ae2",
    "biggerae2", "dataenergistics", "megacells", "large_integrated_working_station_compat",
    "controller_formed_light_compat", "controller_formed_screen_compat",
    "integrated_working_station_modules",
}
CORE_MOD_IDS = {"minecraft", "ae2", "neoecoae", "forge", "neoforge"}
SHARED_CORE_ASSETS = {
    # NEItems.bulkItemCell is a core legacy identity; its source artwork shares this directory.
    "assets/neoecoae/textures/item/eco_cells/megacells/bulk_item_cell.png": "assets/neoecoae/textures/item/eco_cells/bulk_item_cell.png",
    "assets/neoecoae/textures/block/storage/drive_cells/complex_omni_cell_housing.png": "assets/neoecoae/textures/block/storage/drive_cells/bulk_item_cell_housing.png",
}


def optional_identity(value):
    return bool(OPTIONAL_CELL.search(value)) or "integrated_working_station_module_" in value


def source_exclusion(path):
    relative = path.split(PACKAGE, 1)[-1]
    if relative in {"api/me/ECOCraftingCPU.java", "api/me/ECOCraftingCPULogic.java", "api/me/ExecutingCraftingJob.java"}:
        return "legacy_optional_integration_abi"
    if relative in {"blocks/entity/storage/ECOStorageMegaController.java", "api/storage/IECOBulkMarkableCellItem.java", "recipe/LightningWorkstationTiming.java", "recipe/LightningWorkstationTimingTest.java"}:
        return "optional_mod_business_helper"
    if relative.startswith("compat/") and not relative.startswith("compat/ae2/"):
        return "optional_mod_compatibility"
    if relative.startswith("mixins/compat/"):
        return "optional_mod_mixin"
    if relative.startswith("integration/") and not relative.startswith(("integration/ldlib/", "integration/recipe/", "integration/xei/")):
        return "optional_mod_integration" if not relative.startswith(("integration/jei/", "integration/emi/", "integration/jade/")) else "modern_information_platform_replaced_by_native"
    if relative.startswith("api/integration/") or relative.endswith("ECOAdvancedAeCraftingOutputRouter.java"):
        return "optional_mod_integration_api"
    return None


def condition_matches(condition):
    kind = condition["type"]
    if kind == "neoforge:mod_loaded":
        return condition["modid"] in CORE_MOD_IDS
    if kind == "neoforge:not":
        return not condition_matches(condition["value"])
    raise ValueError("Unreviewed recipe condition: " + kind)


def resource_exclusion(path, data):
    relative = path.split("/resources/", 1)[-1]
    if relative in SHARED_CORE_ASSETS:
        return None
    if "/models/block/cell/" in relative and not re.fullmatch(
            r"storage_cell_(?:default|creative|l[469]_(?:item|fluid|bulk|bulk_item|infinite_resource))\.json", PurePosixPath(relative).name):
        return "optional_mod_drive_cell_model"
    if "/textures/block/storage/drive_cells/" in relative and not re.fullmatch(
            r"(?:bulk_housing|bulk_item_cell_housing|cell_housing|cell_level|cell_type|infinite_resource_housing)\.png(?:\.mcmeta)?", PurePosixPath(relative).name):
        return "optional_mod_drive_cell_texture"
    if set(PurePosixPath(relative).parts) & OPTIONAL_PATH_PARTS:
        return "optional_mod_asset_or_recipe"
    if "/recipe/compat/" in relative or "/recipes/compat/" in relative:
        return "optional_mod_recipe"
    if optional_identity(PurePosixPath(relative).stem):
        return "optional_mod_cell_or_excluded_workstation_card"
    if relative.endswith(".json"):
        document = json.loads(data)
        if isinstance(document, dict) and not all(condition_matches(c) for c in document.get("neoforge:conditions", [])):
            return "optional_mod_condition_unsatisfied"
    return None


def adapt_resource(path, data):
    relative = path.split("/resources/", 1)[-1]
    if not relative.endswith(".json"):
        return data
    document = json.loads(data)
    if "/lang/" in relative:
        document = {key: value for key, value in document.items()
                    if not optional_identity(key) and ".integration." not in key
                    and not any(part in key for part in [".advanced_ae.", ".extendedae.", ".aelt."])}
    elif "/tags/" in relative and isinstance(document, dict):
        document = dict(document)
        document["values"] = [value for value in document.get("values", [])
                              if not optional_identity(value if isinstance(value, str) else value.get("id", ""))]
    elif "/models/" in relative:
        text = data.decode("utf8")
        for original, alias in SHARED_CORE_ASSETS.items():
            old = original.removeprefix("assets/").replace("/textures/", "/").removesuffix(".png").replace("/", ":", 1)
            new = alias.removeprefix("assets/").replace("/textures/", "/").removesuffix(".png").replace("/", ":", 1)
            text = text.replace('"' + old + '"', '"' + new + '"')
        return text.encode("utf8")
    else:
        return data
    return (json.dumps(document, ensure_ascii=False, indent=2) + "\n").encode("utf8")


def replace_java_body(text, signature, replacement):
    match = re.search(signature, text)
    if match is None:
        return text
    start = text.index("{", match.start())
    cursor, depth = start + 1, 1
    while depth:
        if text[cursor] == "{": depth += 1
        elif text[cursor] == "}": depth -= 1
        cursor += 1
    return text[:start] + "{" + replacement + "}" + text[cursor:]


def adapt_core_business(text):
    """Remove only optional branches; core amounts, scheduling and storage policy are unchanged."""
    if "class ECOStorageHostStatistics" in text:
        text = re.sub(r"(?m)^import .*\.(?:BiggerAE2SingularityPowerService|OptionalCellPower);\n", "", text)
        text = re.sub(r"(?m)^\s*boolean hasSingularity = false;\n", "\n", text)
        text = re.sub(r"(?m)^\s*hasSingularity \|= (?:BiggerAE2SingularityPowerService|OptionalCellPower)\.isSingularity\(stack\);\n", "\n", text)
        text = replace_java_body(text, r"if \(hasSingularity\)", "")
        text = text.replace("if (hasSingularity) {}", "")
        text = replace_java_body(text, r"private int normalUiTypeId\(int cellTypeId\)", " return cellTypeId; ")
    if "class StoragePanelTextures" in text:
        text = replace_java_body(text, r"(?:public )?static int swatchIndex\(ResourceLocation id\)", '''
        if (id == null) return FALLBACK_SWATCH;
        return switch (id.toString()) {
            case "neoecoae:fluids", "ae2:f" -> 1;
            case "neoecoae:items", "neoecoae:bulk_item", "ae2:i" -> 2;
            default -> FALLBACK_SWATCH;
        };
    ''')
    if "class StoragePanelStateTest" in text:
        start = text.index("        String[] normal =")
        end = text.index("        var unknown =", start)
        text = text[:start] + '''        String[] normal = { "fluids", "items" };
        String[] infinite = { "ae2:f", "ae2:i" };
        for (int i = 0; i < normal.length; i++) {
            assertEquals(i + 1, StoragePanelTextures.swatchIndex(ResourceLocation.parse("neoecoae:" + normal[i])));
            assertEquals(i + 1, StoragePanelTextures.swatchIndex(ResourceLocation.parse(infinite[i])));
        }
        assertEquals(2, StoragePanelTextures.swatchIndex(ResourceLocation.parse("neoecoae:bulk_item")));
        for (String excluded : new String[] { "neoecoae:mega_mana", "mekanism:spent_nuclear_waste",
                "data_energistics:digitalization", "other:mana" }) {
            assertEquals(StoragePanelTextures.FALLBACK_SWATCH,
                    StoragePanelTextures.swatchIndex(ResourceLocation.parse(excluded)));
        }
''' + text[end:]
    return text
