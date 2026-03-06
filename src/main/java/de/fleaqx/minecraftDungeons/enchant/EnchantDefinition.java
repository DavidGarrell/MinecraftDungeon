package de.fleaqx.minecraftDungeons.enchant;

import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import org.bukkit.Material;

import java.math.BigInteger;
import java.util.List;

public record EnchantDefinition(
        String id,
        String displayName,
        EnchantCategory category,
        Material icon,
        List<String> description,
        int maxLevel,
        int requiredToolLevel,
        CurrencyType costCurrency,
        BigInteger basePrice,
        double priceGrowth,
        double baseChance,
        double chancePerLevel,
        double damageMultiplier,
        CurrencyType bonusCurrency,
        BigInteger bonusAmount,
        String bonusTargetEnchant,
        double passiveMultiplier,
        boolean hitsAllZoneMobs,
        boolean phantomEffect,
        boolean fireEffect,
        boolean lightningEffect,
        boolean freezeEffect,
        boolean executeEffect,
        int dotTicks,
        int fireTicks,
        int freezeTicks,
        int phantomCount,
        double fireTickMultiplier,
        double freezeTickMultiplier,
        double phantomHitMultiplier,
        double lightningHitMultiplier,
        boolean packetBased
) {
}
