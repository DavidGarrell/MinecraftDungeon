package de.fleaqx.minecraftDungeons.currency;

import java.math.BigInteger;

public final class NumberFormat {

    private static final String[] BASE_SUFFIXES = new String[]{"", "k", "m", "b", "t"};

    private NumberFormat() {
    }

    public static String compact(BigInteger value) {
        String sign = value.signum() < 0 ? "-" : "";
        String raw = value.abs().toString();
        if (raw.length() <= 3) {
            return sign + raw;
        }

        int group = (raw.length() - 1) / 3;

        int first = raw.length() - group * 3;
        String major = raw.substring(0, first);
        String decimal = raw.substring(first, Math.min(first + 2, raw.length()));
        return sign + major + (decimal.isBlank() ? "" : "." + decimal) + suffix(group);
    }

    private static String suffix(int group) {
        if (group < BASE_SUFFIXES.length) {
            return BASE_SUFFIXES[group];
        }

        int suffixIndex = group - BASE_SUFFIXES.length;
        int width = 2;
        int combinations = 26 * 26;
        while (suffixIndex >= combinations) {
            suffixIndex -= combinations;
            width++;
            combinations *= 26;
        }

        char[] chars = new char[width];
        for (int i = width - 1; i >= 0; i--) {
            chars[i] = (char) ('a' + (suffixIndex % 26));
            suffixIndex /= 26;
        }

        return new String(chars);
    }

    public static BigInteger parse(String input, BigInteger fallback) {
        try {
            return new BigInteger(input);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
