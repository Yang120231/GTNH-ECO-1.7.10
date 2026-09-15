"""Mechanical Java-8 projection of the sibling planner; algorithm bodies remain upstream."""
from pathlib import Path
import re

source = Path('../NeoECOAEExtension-1.21.1/src/main/java/cn/dancingsnow/neoecoae/impl/crafting/planner')
target = Path('src/main/java/cn/dancingsnow/neoecoae/crafting/planner/ported')
package = 'cn.dancingsnow.neoecoae.crafting.planner.ported'

def records(text):
    pattern = r'(public|private) record (\w+)\s*\('
    while m := re.search(pattern, text):
        end = text.index(')', m.end())
        fields = []
        depth = 0
        start = m.end()
        for index in range(start, end):
            char = text[index]
            depth += (char == '<') - (char == '>')
            if char == ',' and depth == 0:
                fields.append(text[start:index].strip())
                start = index + 1
        fields.append(text[start:end].strip())
        fields = [f.rsplit(None, 1) for f in fields if f.strip()]
        brace = text.index('{', end)
        suffix = text[end+1:brace]
        name = m[2]
        params = ', '.join(t+' '+n for t,n in fields)
        assigns = '\n'.join('this.'+n+' = '+n+';' for _,n in fields)
        members = '\n'.join('private final '+t+' '+n+';\npublic '+t+' '+n+'() { return '+n+'; }' for t,n in fields)
        tail = text[brace+1:]
        compact = re.search(r'\b(public|private) '+name+r'\s*\{', tail)
        explicit = re.search(r'\b(public|private) '+name+r'\s*\('+re.escape(params)+r'\)', tail)
        # Canonical explicit constructor has whitespace-normalized parameters.
        explicit = explicit or re.search(r'\b(public|private) '+name+r'\s*\(\s*'+r'\s*,\s*'.join(re.escape(t)+r'\s+'+n for t,n in fields)+r'\s*\)', tail)
        if compact:
            start = compact.end(); depth=1; finish=start
            while depth:
                depth += (tail[finish]=='{') - (tail[finish]=='}'); finish+=1
            tail = tail[:compact.start()]+compact[1]+' '+name+'('+params+') {'+tail[start:finish-1]+'\n'+assigns+'\n}'+tail[finish:]
        elif not explicit:
            members += '\npublic '+name+'('+params+') {\n'+assigns+'\n}'
        # Record equality matters for condensation edge keys and public value objects.
        eq = ' && '.join('java.util.Objects.equals(this.'+n+', other.'+n+')' for _,n in fields) or 'true'
        members += '\n@Override public boolean equals(Object value) { if (this == value) return true; if (!(value instanceof '+name+')) return false; '+name+' other = ('+name+') value; return '+eq+'; }'
        members += '\n@Override public int hashCode() { return java.util.Objects.hash('+', '.join(n for _,n in fields)+'); }'
        text = text[:m.start()]+m[1]+(' static' if m[1]=='private' else '')+' final class '+name+suffix+' {\n'+members+'\n'+tail
    return text

files = list((source/'cycle').glob('*.java')) + list((source/'component').glob('*.java'))
files += [p for p in (source/'graph').glob('*.java') if p.stem != 'CraftingGraphBuilder']
files += [source/('result/'+name+'.java') for name in ['ReferenceOwnershipLedger', 'OwnershipEvent', 'PendingChoiceGroup', 'ExecutionMode', 'PlannedInputAllocation']]
files += [source/'solve/PlannerAmount.java', source/'ECOCancellation.java', source/'result/ExecutionCountKnowledge.java']
for path in files:
    text = path.read_text(encoding='utf-8')
    text = text.replace('cn.dancingsnow.neoecoae.impl.crafting.planner', package)
    text = text.replace('import appeng.api.stacks.AEKey;', '').replace('AEKey', 'Object')
    text = text.replace('import appeng.api.crafting.IPatternDetails;', '').replace('IPatternDetails', 'Object')
    text = text.replace('appeng.api.stacks.GenericStack', package+'.compile.GenericStack')
    text = text.replace('import org.jetbrains.annotations.Nullable;', '').replace('@Nullable', '')
    text = text.replace('org.slf4j.LoggerFactory', 'org.apache.logging.log4j.LogManager').replace('org.slf4j.Logger', 'org.apache.logging.log4j.Logger').replace('LoggerFactory.getLogger', 'LogManager.getLogger')
    text = re.sub(r'sealed interface (\w+) permits [^{]+', r'interface \1 ', text)
    text = text.replace('List.copyOf(', 'com.google.common.collect.ImmutableList.copyOf(').replace('Map.copyOf(', 'com.google.common.collect.ImmutableMap.copyOf(').replace('Set.copyOf(', 'com.google.common.collect.ImmutableSet.copyOf(')
    text = text.replace('List.of(', 'com.google.common.collect.ImmutableList.of(').replace('Map.of(', 'com.google.common.collect.ImmutableMap.of(').replace('Set.of(', 'com.google.common.collect.ImmutableSet.of(')
    text = text.replace('.toList()', '.collect(java.util.stream.Collectors.toList())')
    text = re.sub(r'(\w+)\.getLast\(\)', r'\1.get(\1.size() - 1)', text)
    text = re.sub(r'(\w+)\.getFirst\(\)', r'\1.get(0)', text)
    text = text.replace('outputs.toArray(OutputTarget[]::new)', 'outputs.toArray(new OutputTarget[0])').replace('unlocks.toArray(UnlockTarget[]::new)', 'unlocks.toArray(new UnlockTarget[0])')
    text = text.replace('.getFirst()', '.get(0)')
    text = re.sub(r'(\w+)\.removeLast\(\)', r'\1.remove(\1.size() - 1)', text)
    text = records(text)
    text = text.replace('public final class PlannerOptions', 'public static final class PlannerOptions').replace('public final class Run ', 'public static final class Run ')
    text = text.replace('return topologicalOrder.reversed();', 'List<PlanningComponent> result = new ArrayList<>(topologicalOrder); java.util.Collections.reverse(result); return com.google.common.collect.ImmutableList.copyOf(result);')
    out = target/path.relative_to(source)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding='utf-8')
