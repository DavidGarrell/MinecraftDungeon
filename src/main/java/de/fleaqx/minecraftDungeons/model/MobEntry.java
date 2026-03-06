package de.fleaqx.minecraftDungeons.model;

import de.fleaqx.minecraftDungeons.currency.CurrencyBundle;
import org.bukkit.entity.EntityType;

import java.math.BigInteger;

public record MobEntry(
        String id,
        EntityType entityType,
        MobRarity rarity,
        int weight,
        BigInteger health,
        CurrencyBundle rewards
) {
}
