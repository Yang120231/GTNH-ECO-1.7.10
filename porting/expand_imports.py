"""Expand non-static wildcard imports without changing compilation or checkstyle policy."""
from pathlib import Path
import re
import zipfile

ROOT=Path(__file__).resolve().parent.parent
SOURCES=[ROOT/"src/main/java",ROOT/"src/test/java"]
MODS=ROOT.parent
JMOD=Path(r"C:\Program Files\Microsoft\jdk-17.0.10.7-hotspot\jmods\java.base.jmod")


def names(package):
    relative=package.replace(".","/")
    if package.startswith("java."):
        prefix="classes/"+relative+"/"
        with zipfile.ZipFile(JMOD) as module:
            return {value[len(prefix):-6] for value in module.namelist() if value.startswith(prefix) and value.endswith(".class") and "/" not in value[len(prefix):] and "$" not in value}
    if package=="net.minecraft.command":return {"CommandBase","ICommandSender","CommandException","WrongUsageException"}
    roots=SOURCES
    if package.startswith("appeng."):roots=[MODS/"Applied-Energistics-2-Unofficial/src/main/java"]
    if package.startswith("com.cleanroommc.modularui."):roots=[MODS/"ModularUI2/src/main/java"]
    if package=="com.google.gson":return {"JsonArray","JsonObject","JsonElement","JsonParser","JsonPrimitive","JsonParseException"}
    result={p.stem for root in roots for p in (root/relative).glob("*.java")}
    if not result:raise ValueError("Cannot resolve wildcard package: "+package)
    return result


count=0
for root in SOURCES:
    for path in root.rglob("*.java"):
        source=path.read_text(encoding="utf8")
        imports=re.findall(r"(?m)^import ([\w.]+)\.\*;\s*$",source)
        if not imports:continue
        body=re.sub(r"(?m)^import [^;\n]+;\s*$","",source)
        words=set(re.findall(r"\b[A-Za-z_$][\w$]*\b",body))
        own={p.stem for p in path.parent.glob("*.java")}
        explicit={value.rsplit(".",1)[1]:value for value in re.findall(r"(?m)^import ([\w.]+);",source)}
        for package in imports:
            selected=sorted(name for name in names(package) if name in words and name not in own and (name not in explicit or explicit[name]==package+"."+name))
            replacement="\n".join("import "+package+"."+name+";" for name in selected)
            source=source.replace("import "+package+".*;",replacement)
        path.write_text(source,encoding="utf8",newline="\n")
        count+=1
print("Expanded wildcard imports in",count,"source files")
