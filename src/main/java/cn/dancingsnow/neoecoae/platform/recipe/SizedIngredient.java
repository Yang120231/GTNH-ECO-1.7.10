package cn.dancingsnow.neoecoae.platform.recipe;

/** Native counterpart of the source's sized item predicate. */
@com.github.bsideup.jabel.Desugar
public record SizedIngredient(SourceIngredient ingredient, int count) {

    public SizedIngredient {
        java.util.Objects.requireNonNull(ingredient);
        if (count <= 0) throw new IllegalArgumentException("Non-positive item input count");
    }
}
