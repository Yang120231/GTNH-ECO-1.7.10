package cn.dancingsnow.neoecoae.crafting.execution.batch;

import java.util.Objects;

import cn.dancingsnow.neoecoae.platform.ae2.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEItemKey;

/**
 * Stable identity of an installed pattern and its provider ownership.
 *
 * <p>
 * This type intentionally contains no scaled inputs, outputs, leases, or execution state. Those belong to
 * {@link ECOBatchMaterialized} and must never become task or allocation keys.
 * </p>
 */
@com.github.bsideup.jabel.Desugar
public record ECOPatternIdentity(IPatternDetails originalPattern, AEItemKey definition, Object providerIdentity) {

    public ECOPatternIdentity {
        Objects.requireNonNull(originalPattern, "originalPattern");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(providerIdentity, "providerIdentity");
    }

    public static ECOPatternIdentity of(IPatternDetails pattern, Object providerIdentity) {
        return new ECOPatternIdentity(
            pattern,
            Objects.requireNonNull(pattern, "pattern")
                .getDefinition(),
            providerIdentity);
    }
}
