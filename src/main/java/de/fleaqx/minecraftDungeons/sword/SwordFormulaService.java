package de.fleaqx.minecraftDungeons.sword;

import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.profile.PlayerProfile;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

public class SwordFormulaService {

    private final BigDecimal baseDamage;
    private final BigDecimal levelDamageGrowth;
    private final BigDecimal basePrice;
    private final BigDecimal levelPriceGrowth;
    private final int maxTiers;

    public SwordFormulaService(double baseDamage,
                               double levelDamageGrowth,
                               double basePrice,
                               double levelPriceGrowth,
                               int maxTiers) {
        this.baseDamage = BigDecimal.valueOf(baseDamage);
        this.levelDamageGrowth = BigDecimal.valueOf(levelDamageGrowth);
        this.basePrice = BigDecimal.valueOf(basePrice);
        this.levelPriceGrowth = BigDecimal.valueOf(levelPriceGrowth);
        this.maxTiers = Math.max(1, maxTiers);
    }

    public BigInteger damage(int swordId, int tier) {
        int level = globalLevel(swordId, tier);
        BigDecimal value = baseDamage.multiply(pow(levelDamageGrowth, level));
        return value.toBigInteger().max(BigInteger.ONE);
    }

    public BigInteger price(int swordId, int targetTier) {
        if (swordId == 1 && targetTier == 1) {
            return BigInteger.ZERO;
        }
        int level = globalLevel(swordId, targetTier);
        BigDecimal value = basePrice.multiply(pow(levelPriceGrowth, level));
        return value.toBigInteger().max(BigInteger.ONE);
    }

    public int bestAffordableSword(PlayerProfile profile, int maxSwords) {
        int best = 1;
        BigInteger money = profile.balance(CurrencyType.MONEY);
        for (int swordId = 1; swordId <= maxSwords; swordId++) {
            int current = profile.swordLevel(swordId);
            int nextTier = Math.min(maxTiers, Math.max(1, current + 1));
            BigInteger price = price(swordId, nextTier);
            if (money.compareTo(price) >= 0) {
                best = swordId;
            }
        }
        return best;
    }

    private int globalLevel(int swordId, int tier) {
        int swordOffset = Math.max(0, swordId - 1) * maxTiers;
        int tierOffset = Math.max(0, tier - 1);
        return swordOffset + tierOffset;
    }

    private BigDecimal pow(BigDecimal base, int exponent) {
        if (exponent <= 0) {
            return BigDecimal.ONE;
        }
        return base.pow(exponent, MathContext.DECIMAL128);
    }
}
