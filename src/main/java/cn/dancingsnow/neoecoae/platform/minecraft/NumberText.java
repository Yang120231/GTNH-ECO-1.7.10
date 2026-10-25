package cn.dancingsnow.neoecoae.platform.minecraft;

public final class NumberText {

    private NumberText() {}

    public static String ofUnformattedNumber(long value) {
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.ROOT)
            .format(value);
    }
}
