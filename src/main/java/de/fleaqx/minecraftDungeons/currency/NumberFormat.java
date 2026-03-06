package de.fleaqx.minecraftDungeons.currency;

import java.math.BigInteger;

public final class NumberFormat {

    private NumberFormat() {
    }

    public static String compact(BigInteger value) {
        String raw = value.toString();
        if (raw.length() <= 3) {
            return raw;
        }

        String[] suffixes = new String[]{"", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"};
        int group = (raw.length() - 1) / 3;
        if (group >= suffixes.length) {
            return raw.substring(0, 4) + "e" + (group * 3);
        }

        int first = raw.length() - group * 3;
        String major = raw.substring(0, first);
        String decimal = raw.substring(first, Math.min(first + 2, raw.length()));
        return major + (decimal.isBlank() ? "" : "." + decimal) + suffixes[group];
    }

    public static BigInteger parse(String input, BigInteger fallback) {
        try {
            return new BigInteger(input);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
