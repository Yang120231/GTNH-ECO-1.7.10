package cn.dancingsnow.neoecoae.platform.jdk;

import java.util.zip.Checksum;

/** Castagnoli checksum for the imported legacy-domain reader, unavailable in Java 8's standard library. */
public final class CRC32C implements Checksum {

    private static final int[] TABLE = new int[256];
    static {
        for (int i = 0; i < 256; i++) {
            int crc = i;
            for (int bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) == 0 ? 0 : 0x82f63b78);
            TABLE[i] = crc;
        }
    }
    private int state = 0xffffffff;

    @Override
    public void update(int value) {
        state = TABLE[(state ^ value) & 255] ^ (state >>> 8);
    }

    @Override
    public void update(byte[] bytes, int offset, int length) {
        if (offset < 0 || length < 0 || offset > bytes.length - length) throw new ArrayIndexOutOfBoundsException();
        for (int i = offset; i < offset + length; i++) update(bytes[i]);
    }

    public void update(byte[] bytes) {
        update(bytes, 0, bytes.length);
    }

    @Override
    public long getValue() {
        return (~state) & 0xffffffffL;
    }

    @Override
    public void reset() {
        state = 0xffffffff;
    }
}
