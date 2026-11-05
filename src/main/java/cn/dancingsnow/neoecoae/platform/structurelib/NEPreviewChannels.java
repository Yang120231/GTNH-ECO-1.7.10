package cn.dancingsnow.neoecoae.platform.structurelib;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;

/** Optional BlockRenderer adapter; no BlockRenderer classes are linked on a server or when the mod is absent. */
public final class NEPreviewChannels {

    private NEPreviewChannels() {}

    public static Object addChannels(IConstructable constructable, Object data) {
        if (!(constructable instanceof NEPreviewConstructable preview) || data == null) return data;
        try {
            Class<?> type = data.getClass();
            // getTierData may return BlockRenderer's shared EMPTY singleton. Never modify that singleton.
            if (!((Boolean) type.getMethod("hasData")
                .invoke(data))) data = type.getConstructor()
                    .newInstance();
            Method setMaxTier = type.getMethod("setMaxTier", int.class, Collection.class);
            setMaxTier.invoke(data, preview.maximumLength, Collections.singleton(NEStructureLib.CHANNEL_LENGTH));
            setMaxTier.invoke(data, 1, Collections.singleton(NEStructureLib.CHANNEL_MIRROR));
            return data;
        } catch (ReflectiveOperationException failure) {
            org.apache.logging.log4j.LogManager.getLogger("neoecoae")
                .warn("Unable to publish ECO structure preview channels", failure);
            return data;
        }
    }
}
