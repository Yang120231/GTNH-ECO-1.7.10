package cn.dancingsnow.neoecoae.platform.ui.upload;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import me.towdium.pinin.PinIn;

/**
 * Search matching for the upload target list: plain substring, or pinyin (full spelling and initials, e.g. "jiyin" or
 * "jy" for 基因) through the shaded PinIn library.
 */
@SideOnly(Side.CLIENT)
final class UploadSearch {

    private static PinIn pinIn;
    /** PinIn failed to load once (e.g. its dictionary is missing); stay with plain matching. */
    private static boolean broken;

    private UploadSearch() {}

    /** Both arguments are already lower-cased. */
    static boolean contains(String text, String term) {
        if (text.contains(term)) return true;
        if (broken || !pinyinQuery(term) || !hasHan(text)) return false;
        try {
            if (pinIn == null) pinIn = new PinIn();
            return pinIn.contains(text, term);
        } catch (Throwable failure) {
            broken = true;
            return false;
        }
    }

    /** Pinyin queries are ASCII letters (and digits for tones); anything else cannot match by pinyin. */
    private static boolean pinyinQuery(String term) {
        for (int index = 0; index < term.length(); index++) {
            char c = term.charAt(index);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '\'')) return false;
        }
        return true;
    }

    private static boolean hasHan(String text) {
        for (int index = 0; index < text.length(); index++)
            if (Character.UnicodeScript.of(text.charAt(index)) == Character.UnicodeScript.HAN) return true;
        return false;
    }
}
