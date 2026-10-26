"""Regression checks for source identity and partial-import boundaries across batches."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from batch_imports import merge_batch_imports, reviewed_batch_imports, reviewed_platform_adaptations


class BatchImportsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.path = "src/main/java/cn/dancingsnow/neoecoae/crafting/Example.java"
        self.blobs = {self.path: b"fixed source"}
        target = self.root / self.path
        target.parent.mkdir(parents=True)
        target.write_bytes(b"latest reviewed native adaptation")
        self.entry = dict(source=self.path, target=self.path, kind="business",
                          source_sha256=hashlib.sha256(self.blobs[self.path]).hexdigest(),
                          target_sha256="historical snapshot")

    def write_batch(self, entry=None, commit="fixed", name="example.json"):
        path = self.root / "porting/batches" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(dict(source_commit=commit, imports=[entry or self.entry])), encoding="utf8")

    def test_refresh_keeps_current_adaptation_and_partial_scope(self):
        self.write_batch()
        batches = reviewed_batch_imports(self.root, "fixed", self.blobs)
        merged = merge_batch_imports([dict(self.entry, partial_source_scope={"members": ["one"]})], batches)
        self.assertEqual({"members": ["one"]}, merged[0]["partial_source_scope"])
        self.assertEqual(hashlib.sha256((self.root / self.path).read_bytes()).hexdigest(), merged[0]["target_sha256"])

    def test_complete_requires_explicit_boolean(self):
        self.write_batch(dict(self.entry, complete=True))
        batches = reviewed_batch_imports(self.root, "fixed", self.blobs)
        self.assertNotIn("partial_source_scope", merge_batch_imports([dict(self.entry, partial_source_scope={})], batches)[0])
        self.write_batch(dict(self.entry, complete="true"))
        with self.assertRaisesRegex(ValueError, "boolean"):
            reviewed_batch_imports(self.root, "fixed", self.blobs)

    def test_unlocked_revision_and_wrong_source_hash_are_rejected(self):
        self.write_batch(commit="another revision")
        with self.assertRaisesRegex(ValueError, "different source revision"):
            reviewed_batch_imports(self.root, "fixed", self.blobs)
        self.write_batch(dict(self.entry, source_sha256="wrong"))
        with self.assertRaisesRegex(ValueError, "source hash mismatch"):
            reviewed_batch_imports(self.root, "fixed", self.blobs)

    def test_target_namespace_escape_is_rejected(self):
        self.write_batch(dict(self.entry, target="src/main/java/appeng/Replacement.java"))
        with self.assertRaisesRegex(ValueError, "outside the project"):
            reviewed_batch_imports(self.root, "fixed", self.blobs)
        self.write_batch(dict(self.entry, target="src/main/java/cn/dancingsnow/neoecoae/../../escape.java"))
        with self.assertRaisesRegex(ValueError, "Invalid batch"):
            reviewed_batch_imports(self.root, "fixed", self.blobs)

    def test_optional_integration_and_missing_target_are_rejected(self):
        excluded = "src/main/java/cn/dancingsnow/neoecoae/compat/appmek/Example.java"
        self.write_batch(dict(self.entry, source=excluded))
        with self.assertRaisesRegex(ValueError, "excluded source"):
            reviewed_batch_imports(self.root, "fixed", self.blobs)
        self.write_batch(dict(self.entry, target=self.path.replace("Example", "Missing")))
        with self.assertRaisesRegex(ValueError, "target missing"):
            reviewed_batch_imports(self.root, "fixed", self.blobs)

    def test_conflicting_batch_source_cannot_replace_existing_identity(self):
        self.write_batch()
        batches = reviewed_batch_imports(self.root, "fixed", self.blobs)
        with self.assertRaisesRegex(ValueError, "existing source identity"):
            merge_batch_imports([dict(self.entry, source="different source")], batches)

    def test_platform_adapter_validates_all_sources_without_claiming_business_import(self):
        path = self.root / "porting/batches/adapter.json"
        path.parent.mkdir(parents=True)
        source_input = dict(source=self.path, source_sha256=self.entry["source_sha256"])
        value = dict(source_commit="fixed", imports=[], platform_adaptations=[
            dict(target=self.path, inputs=[source_input], adaptation="Native widget boundary")])
        path.write_text(json.dumps(value), encoding="utf8")
        self.assertEqual({}, reviewed_batch_imports(self.root, "fixed", self.blobs))
        adapters = reviewed_platform_adaptations(self.root, "fixed", self.blobs)
        self.assertEqual([source_input], adapters[0]["inputs"])
        value["platform_adaptations"][0]["inputs"].append(dict(source=self.path, source_sha256="wrong"))
        path.write_text(json.dumps(value), encoding="utf8")
        with self.assertRaisesRegex(ValueError, "source hash mismatch"):
            reviewed_platform_adaptations(self.root, "fixed", self.blobs)


if __name__ == "__main__":
    unittest.main()
