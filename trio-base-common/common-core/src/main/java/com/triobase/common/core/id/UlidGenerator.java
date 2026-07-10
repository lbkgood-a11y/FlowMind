package com.triobase.common.core.id;

import java.security.SecureRandom;

public final class UlidGenerator {

    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ULID_LENGTH = 26;
    private static final int TIME_LENGTH = 10;
    private static final int RANDOM_LENGTH_BYTES = 10;
    private static final int MASK = 0x1F;

    private UlidGenerator() {
    }

    public static String nextUlid() {
        byte[] randomBytes = new byte[RANDOM_LENGTH_BYTES];
        RANDOM.nextBytes(randomBytes);
        return encode(System.currentTimeMillis(), randomBytes);
    }

    static String encode(long timestamp, byte[] randomBytes) {
        if (timestamp < 0 || timestamp > 0xFFFFFFFFFFFFL) {
            throw new IllegalArgumentException("ULID timestamp must fit in 48 bits");
        }
        if (randomBytes == null || randomBytes.length != RANDOM_LENGTH_BYTES) {
            throw new IllegalArgumentException("ULID requires exactly 80 random bits");
        }

        char[] chars = new char[ULID_LENGTH];
        long time = timestamp;
        for (int i = TIME_LENGTH - 1; i >= 0; i--) {
            chars[i] = ENCODING[(int) (time & MASK)];
            time >>>= 5;
        }

        int bitBuffer = 0;
        int bitCount = 0;
        int charIndex = TIME_LENGTH;
        for (byte randomByte : randomBytes) {
            bitBuffer = (bitBuffer << 8) | (randomByte & 0xFF);
            bitCount += 8;
            while (bitCount >= 5) {
                chars[charIndex++] = ENCODING[(bitBuffer >> (bitCount - 5)) & MASK];
                bitCount -= 5;
            }
        }
        return new String(chars);
    }
}
