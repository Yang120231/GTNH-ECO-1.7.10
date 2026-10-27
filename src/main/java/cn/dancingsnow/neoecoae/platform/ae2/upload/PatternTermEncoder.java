package cn.dancingsnow.neoecoae.platform.ae2.upload;

/** Implemented on AE2's pattern terminal container by {@code PatternTermEncodeMixin}. */
public interface PatternTermEncoder {

    /** Runs AE2's own encode; blank patterns are only consumed by AE2's normal rules. */
    void neoecoae$encode();
}
