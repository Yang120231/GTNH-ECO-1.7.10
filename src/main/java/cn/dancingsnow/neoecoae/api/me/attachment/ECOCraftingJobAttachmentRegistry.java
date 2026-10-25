package cn.dancingsnow.neoecoae.api.me.attachment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.platform.minecraft.ResourceLocation;

/** Registration point for factories that add state to every matching ECO crafting job. */
public final class ECOCraftingJobAttachmentRegistry {

    private static final Logger LOGGER = LogManager.getLogger("neoecoae.api");
    private static final Map<ResourceLocation, Function<ECOCraftingJobContext, ? extends ECOCraftingJobAttachment>> FACTORIES = new LinkedHashMap<>();

    private ECOCraftingJobAttachmentRegistry() {}

    public static synchronized void register(ResourceLocation id,
        Function<ECOCraftingJobContext, ? extends ECOCraftingJobAttachment> factory) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(factory, "factory");
        if (FACTORIES.containsKey(id)) {
            throw new IllegalArgumentException("An ECO job attachment is already registered for " + id);
        }
        FACTORIES.put(id, factory);
    }

    public static synchronized boolean unregister(ResourceLocation id) {
        return id != null && FACTORIES.remove(id) != null;
    }

    static synchronized List<RegisteredFactory> snapshot() {
        List<RegisteredFactory> result = new ArrayList<>(FACTORIES.size());
        FACTORIES.forEach((id, factory) -> result.add(new RegisteredFactory(id, factory)));
        return result;
    }

    @ApiStatus.Internal
    public static List<ECOCraftingJobAttachment> createAll(ECOCraftingJobContext context) {
        List<ECOCraftingJobAttachment> result = new ArrayList<>();
        for (var registered : snapshot()) {
            try {
                ECOCraftingJobAttachment attachment = registered.factory()
                    .apply(context);
                if (attachment == null || !registered.id()
                    .equals(attachment.id())) {
                    LOGGER.warn("Ignoring ECO job attachment factory {} with mismatched result", registered.id());
                    continue;
                }
                result.add(attachment);
            } catch (RuntimeException failure) {
                LOGGER.error("ECO job attachment factory {} failed", registered.id(), failure);
            }
        }
        return result;
    }

    @Nullable
    @ApiStatus.Internal
    public static ECOCraftingJobAttachment create(ResourceLocation id, ECOCraftingJobContext context) {
        Function<ECOCraftingJobContext, ? extends ECOCraftingJobAttachment> factory;
        synchronized (ECOCraftingJobAttachmentRegistry.class) {
            factory = FACTORIES.get(id);
        }
        if (factory == null) return null;
        try {
            ECOCraftingJobAttachment attachment = factory.apply(context);
            return attachment != null && id.equals(attachment.id()) ? attachment : null;
        } catch (RuntimeException failure) {
            LOGGER.error("ECO job attachment factory {} failed while resolving persisted state", id, failure);
            return null;
        }
    }

    @com.github.bsideup.jabel.Desugar
    private record RegisteredFactory(ResourceLocation id,
        Function<ECOCraftingJobContext, ? extends ECOCraftingJobAttachment> factory) {}
}
