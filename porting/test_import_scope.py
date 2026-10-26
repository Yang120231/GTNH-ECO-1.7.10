"""Regression checks for the core energy buffers removed by optional-cell filtering."""
import json
import unittest
from pathlib import Path

from import_scope import adapt_resource, optional_identity, resource_exclusion


class EnergyCellImportTest(unittest.TestCase):
    def test_native_ui_and_material_translations_survive_source_refresh(self):
        for language, name in (("en_us", "Blue Ice"), ("zh_cn", "蓝冰")):
            path = "src/main/resources/assets/neoecoae/lang/" + language + ".json"
            adapted = json.loads(adapt_resource(path, b"{}"))
            self.assertEqual(name, adapted["tile.neoecoae.platform_blue_ice.name"])
            self.assertIn("gui.neoecoae.storage_priority.insertion_hint", adapted)
            self.assertIn("gui.neoecoae.storage_priority.extraction_hint", adapted)

    def test_core_energy_resources_are_retained(self):
        for tier in (4, 6, 9):
            identity = f"energy_cell_l{tier}"
            for prefix in ("assets/neoecoae/blockstates/", "assets/neoecoae/models/item/",
                           "data/neoecoae/recipe/", "data/neoecoae/loot_table/blocks/",
                           "data/neoecoae/advancement/recipes/misc/"):
                path = "src/generated/resources/" + prefix + identity + ".json"
                with self.subTest(path=path):
                    self.assertIsNone(resource_exclusion(path, b"{}"))
            key = "block.neoecoae." + identity
            data = json.dumps({key: "Core energy buffer"}).encode("utf8")
            adapted = adapt_resource("src/main/resources/assets/neoecoae/lang/en_us.json", data)
            self.assertIn(key, json.loads(adapted))

    def test_optional_cells_still_excluded(self):
        for identity in ("eco_energy_storage_cell_l4", "energy_cell_l5", "energy_cell_l4_extra",
                         "eco_fe_storage_cell_l4", "eco_mega_item_cell_l4",
                         "eco_digital_singularity_item_cell_l4"):
            with self.subTest(identity=identity):
                self.assertTrue(optional_identity(identity))

    def test_energy_blockstates_and_models_exist(self):
        assets = Path(__file__).resolve().parent.parent / "src/main/resources/assets/neoecoae"
        for tier in (4, 6, 9):
            state = assets / f"blockstates/energy_cell_l{tier}.json"
            with self.subTest(state=state):
                variants = json.loads(state.read_text(encoding="utf8"))["variants"]
                self.assertEqual(20, len(variants))
                for variant in variants.values():
                    model = variant["model"].removeprefix("neoecoae:")
                    self.assertTrue((assets / ("models/" + model + ".json")).is_file(), model)


if __name__ == "__main__":
    unittest.main()
