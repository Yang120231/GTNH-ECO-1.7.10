package cn.dancingsnow.neoecoae.platform.ui.upload;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UploadSearchTest {

    @Test
    void matchesPlainText() {
        assertTrue(UploadSearch.contains("终极基因锻造者", "基因"));
        assertTrue(UploadSearch.contains("me 二合一接口", "me"));
    }

    @Test
    void matchesFullPinyinAndInitials() {
        assertTrue(UploadSearch.contains("终极基因锻造者", "jiyin"));
        assertTrue(UploadSearch.contains("终极基因锻造者", "zjjy"));
        assertTrue(UploadSearch.contains("精密自动组装机", "zuzhuang"));
        assertTrue(UploadSearch.contains("精密自动组装机", "jmzd"));
    }

    @Test
    void rejectsNonMatches() {
        assertFalse(UploadSearch.contains("终极基因锻造者", "zuzhuang"));
        assertFalse(UploadSearch.contains("electric blast furnace", "dian"));
    }
}
