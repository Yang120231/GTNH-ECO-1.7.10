"""Targeted runtime imports from raw locked Git blobs. Existing reviewed targets are never overwritten."""
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

from import_business import adapt

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT.parent / "NeoECOAEExtension-1.21.1"
COMMIT = "f6003889a1352ea0d7e2e23a3868eb69943789ca"
PREFIX = "src/main/java/cn/dancingsnow/neoecoae/"
PATHS = [
    "crafting/execution/ExecutingCraftingJob.java",
    "crafting/execution/ECOExecutionPlanNbtCodec.java",
]


def remove_body(text, signature):
    match = re.search(signature, text)
    if match is None:
        raise ValueError("Missing reviewed member: " + signature)
    start = text.index("{", match.start())
    cursor, depth = start + 1, 1
    while depth:
        if text[cursor] == "{": depth += 1
        elif text[cursor] == "}": depth -= 1
        cursor += 1
    return text[:match.start()] + text[cursor:]


def runtime_adapt(relative, raw):
    text = raw.decode("utf8")
    if relative.endswith("ExecutingCraftingJob.java"):
        text = text.replace("public class ExecutingCraftingJob extends cn.dancingsnow.neoecoae.api.me.ExecutingCraftingJob", "public class ExecutingCraftingJob")
        text = re.sub(r"    \{\s*@SuppressWarnings\(\{\"rawtypes\", \"unchecked\"\}\).*?super.tasks = legacyTasks;\s*\}", "", text, flags=re.S)
        text = remove_body(text, r"    static class TaskProgress extends[^\n]+")
        text = remove_body(text, r"        for \(var entry : cn\.dancingsnow\.neoecoae\.compat\.extendedaeplus\.EAEPForcedCrafting\.manualMissing\(plan\)\)")
        text = remove_body(text, r"        if \(executionPlan == null && !exactOrder\)")
        text = re.sub(r"\bTaskProgress\b", "ECOExecutionTaskProgress", text)
        text = text.replace("e.getValue().exactTotal", "e.getValue().totalExact()")
        text = text.replace("e.getValue().exactRemaining", "e.getValue().remainingExact()")
    text = text.replace("ECOCraftingCPULogic cpu", "CraftingJobPersistenceContext cpu")
    text = text.replace("cpu.cpu.getLevel()", "cpu.getLevel()")
    text = text.replace("cpu.cpu)", "cpu.nativeCPU())")
    text = text.replace("PatternDetailsHelper.decodePattern(pattern, cpu.getLevel())", "cpu.decodePattern(pattern)")
    text = text.replace("PatternDetailsHelper.decodePattern(definition, cpu.getLevel())", "cpu.decodePattern(definition)")
    text = text.replace("import appeng.crafting.CraftingLink;", "import cn.dancingsnow.neoecoae.platform.ae2.crafting.CraftingLink;")
    text = text.replace("import appeng.api.crafting.PatternDetailsHelper;\n", "")
    return adapt(text.encode("utf8"))


def main():
    imports = []
    for relative in PATHS:
        source_path = PREFIX + relative
        raw = subprocess.check_output(["git", "-C", str(SOURCE), "show", COMMIT + ":" + source_path])
        target = ROOT / source_path
        if not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(runtime_adapt(relative, raw))
        imports.append(dict(source=source_path, target=source_path,
            source_sha256=hashlib.sha256(raw).hexdigest(),
            target_sha256=hashlib.sha256(target.read_bytes()).hexdigest(), kind="business", complete=True,
            adaptation="Full core job and execution-contract persistence; native pattern/link context replaces CPU coupling. Optional legacy ABI and optional integration-only branches excluded."))
    batch = dict(source_commit=COMMIT, title="完整合成任务与执行契约持久化", imports=imports)
    (ROOT / "porting/batches/execution-03-job-persistence.json").write_text(
        json.dumps(batch, ensure_ascii=False, indent=2) + "\n", encoding="utf8")


if __name__ == "__main__":
    if "--snapshot" in sys.argv:
        paths = subprocess.check_output(["git", "-C", str(SOURCE), "ls-tree", "-r", "--name-only", COMMIT, "src/main/java"]).decode().splitlines()
        prefixes = [PREFIX + path for path in ["crafting/execution/", "blocks/entity/crafting/", "blocks/entity/computation/", "multiblock/cluster/"]]
        for path in paths:
            if any(path.startswith(prefix) for prefix in prefixes):
                target = ROOT / "build/execution-source" / path.removeprefix(PREFIX)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(subprocess.check_output(["git", "-C", str(SOURCE), "show", COMMIT + ":" + path]))
    else:
        main()
