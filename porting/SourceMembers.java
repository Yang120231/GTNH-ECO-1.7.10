import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.tools.ToolProvider;

/** Source-only parsing: selection boundaries come from javac, never brace-counting comments or strings. */
public final class SourceMembers {
    private static String json(String value) {
        StringBuilder result=new StringBuilder("\"");
        for(char c:value.toCharArray())switch(c) {
            case '\\':result.append("\\\\");break;case '"':result.append("\\\"");break;
            case '\n':result.append("\\n");break;case '\r':result.append("\\r");break;case '\t':result.append("\\t");break;
            default:if(c<32)result.append(String.format("\\u%04x",(int)c));else result.append(c);
        }
        return result.append('"').toString();
    }
    public static void main(String[] args) throws Exception {
        var compiler=ToolProvider.getSystemJavaCompiler();
        try(var manager=compiler.getStandardFileManager(null,null,StandardCharsets.UTF_8)) {
            String source=Files.readString(Paths.get(args[0]),StandardCharsets.UTF_8);
            JavacTask task=(JavacTask)compiler.getTask(null,manager,null,Arrays.asList("-proc:none"),null,
                manager.getJavaFileObjects(args[0]));
            CompilationUnitTree unit=task.parse().iterator().next();
            var positions=Trees.instance(task).getSourcePositions();
            ClassTree owner=(ClassTree)unit.getTypeDecls().get(0);
            System.out.print("[");boolean comma=false;
            for(Tree member:owner.getMembers()) {
                long start=positions.getStartPosition(unit,member),end=positions.getEndPosition(unit,member);
                if(start<0||end<0)continue;
                String name=member instanceof MethodTree?((MethodTree)member).getName().toString()
                    :member instanceof VariableTree?((VariableTree)member).getName().toString()
                    :member instanceof ClassTree?((ClassTree)member).getSimpleName().toString():member.getKind().name();
                if(comma)System.out.print(",");comma=true;
                System.out.print("{\"kind\":"+json(member.getKind().name())+",\"name\":"+json(name)
                    +",\"line\":"+unit.getLineMap().getLineNumber(start)+",\"text\":"+json(source.substring((int)start,(int)end))+"}");
            }
            System.out.println("]");
        }
    }
}
