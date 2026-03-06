package de.fleaqx.minecraftDungeons.currency;

import java.math.BigInteger;

public record CurrencyBundle(
        BigInteger money,
        BigInteger souls,
        BigInteger essence,
        BigInteger shards
) {
    public static CurrencyBundle zero() {
        return new CurrencyBundle(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO);
    }

    public static CurrencyBundle of(BigInteger money, BigInteger souls, BigInteger essence, BigInteger shards) {
        return new CurrencyBundle(money, souls, essence, shards);
    }

    public BigInteger byType(CurrencyType type) {
        return switch (type) {
            case MONEY -> money;
            case SOULS -> souls;
            case ESSENCE -> essence;
            case SHARDS -> shards;
        };
    }
}
