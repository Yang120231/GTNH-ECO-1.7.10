package cn.dancingsnow.neoecoae.crafting.upload;

/**
 * Optional machine-route knowledge for processing patterns. The core never sees the implementing mod's types; the
 * GregTech probe is only constructed after FML confirms GregTech is installed.
 */
public interface PatternRouteProbe {

    PatternRouteProbe NONE = new PatternRouteProbe() {

        @Override
        public MatchResult match(Object host, PatternContext context, boolean programmable) {
            return null;
        }

        @Override
        public PatternUploadTarget.Kind kind(Object host) {
            return null;
        }

        @Override
        public void invalidate() {}
    };

    /** Null when the host exposes no recipe route at all. */
    MatchResult match(Object host, PatternContext context, boolean programmable);

    /** A probe-specific target kind, or null for an ordinary interface. */
    PatternUploadTarget.Kind kind(Object host);

    /** Drop cached structure ownership; called once per discovery. */
    void invalidate();
}
