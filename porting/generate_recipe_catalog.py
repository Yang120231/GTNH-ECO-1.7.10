"""Index imported source recipes/tags; the native loader interprets their original JSON."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    resources = ROOT / "src/main/resources"
    recipes = sorted((resources / "data/neoecoae/recipe").rglob("*.json"))
    tags = sorted((resources / "data").glob("*/tags/item/**/*.json"))
    def entries(paths):
        return [{"path": path.relative_to(resources).as_posix(), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()} for path in paths]
    document = {"source_commit": "f6003889a1352ea0d7e2e23a3868eb69943789ca", "recipes": entries(recipes), "tags": entries(tags)}
    (resources / "neoecoae/recipe-catalog.json").write_text(json.dumps(document, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"recipes": len(recipes), "item_tags": len(tags)}))


if __name__ == "__main__":
    main()
