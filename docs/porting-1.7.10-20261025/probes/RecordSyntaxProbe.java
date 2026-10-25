import com.github.bsideup.jabel.Desugar;
import java.math.BigInteger;

public final class RecordSyntaxProbe {
    @Desugar
    public record Amount(BigInteger value, String name) {
        public Amount {
            if (value.signum() < 0) throw new IllegalArgumentException("negative amount");
        }
    }

    public static void main(String[] args) {
        BigInteger huge = BigInteger.TEN.pow(40).add(BigInteger.valueOf(17));
        Amount first = new Amount(huge, "fluid");
        Amount second = new Amount(huge, "fluid");
        if (!first.equals(second) || first.hashCode() != second.hashCode()) {
            throw new AssertionError("record value semantics");
        }
        Object object = first;
        if (!(object instanceof Amount amount) || !amount.value().equals(huge)) {
            throw new AssertionError("record accessors and instanceof matching");
        }
        String result = switch (first.name()) {
            case "fluid" -> first.value().toString();
            default -> throw new AssertionError("switch expression");
        };
        System.out.println("PASS: @Desugar record, compact constructor, equality, hash, instanceof, switch, BigInteger=" + result);
    }
}
