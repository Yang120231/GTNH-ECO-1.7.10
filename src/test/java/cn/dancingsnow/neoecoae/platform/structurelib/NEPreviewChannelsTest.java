package cn.dancingsnow.neoecoae.platform.structurelib;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;

class NEPreviewChannelsTest {

    public static final class PreviewData {

        final Map<String, Integer> channels = new HashMap<>();

        public PreviewData() {}

        public boolean hasData() {
            return !channels.isEmpty();
        }

        public void setMaxTier(int maximum, Collection<String> names) {
            for (String name : names) channels.put(name, maximum);
        }
    }

    @Test
    void publishesLengthAndMirrorForTheExistingCopyButtonWithoutMutatingSharedEmptyData() {
        var definition = mock(MultiBlockDefinition.class);
        when(definition.getExpandMax()).thenReturn(11);
        var constructable = new NEPreviewConstructable(mock(ISurvivalConstructable.class), definition);
        var empty = new PreviewData();

        var result = (PreviewData) NEPreviewChannels.addChannels(constructable, empty);
        assertNotSame(empty, result);
        assertTrue(empty.channels.isEmpty());
        assertEquals(11, result.channels.get("length"));
        assertEquals(1, result.channels.get("mirror"));
        assertSame(result, NEPreviewChannels.addChannels(constructable, result));
    }

    @Test
    void leavesOtherModsPreviewDataAlone() {
        var empty = new PreviewData();
        assertSame(empty, NEPreviewChannels.addChannels(mock(IConstructable.class), empty));
        assertTrue(empty.channels.isEmpty());
    }
}
