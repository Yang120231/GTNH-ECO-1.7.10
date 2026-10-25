package cn.dancingsnow.neoecoae.platform.ae2.storage;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import cn.dancingsnow.neoecoae.platform.ae2.key.AEKey;

public final class IPartitionList {

    private final Set<AEKey> keys;
    private final FuzzyMode fuzzy;

    private IPartitionList(Set<AEKey> keys, FuzzyMode fuzzy) {
        this.keys = Collections.unmodifiableSet(new HashSet<>(keys));
        this.fuzzy = fuzzy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public boolean matchesFilter(AEKey key, IncludeExclude mode) {
        if (keys.isEmpty()) return true;
        boolean match = keys.contains(key);
        if (!match && fuzzy != null && key != null)
            for (AEKey candidate : keys) if (candidate.getType() == key.getType() && candidate.toNative(1)
                .fuzzyComparison(key.toNative(1), fuzzy)) {
                    match = true;
                    break;
                }
        return mode == IncludeExclude.WHITELIST ? match : !match;
    }

    public static final class Builder {

        private final Set<AEKey> keys = new HashSet<>();
        private FuzzyMode fuzzy;

        public Builder fuzzyMode(FuzzyMode mode) {
            fuzzy = mode;
            return this;
        }

        public Builder addAll(Collection<AEKey> values) {
            keys.addAll(values);
            return this;
        }

        public IPartitionList build() {
            return new IPartitionList(keys, fuzzy);
        }
    }
}
