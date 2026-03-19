package de.fleaqx.minecraftDungeons.enchant.effect;

import de.fleaqx.minecraftDungeons.enchant.EnchantDefinition;
import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import de.fleaqx.minecraftDungeons.runtime.DamageIndicatorService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.math.BigInteger;

public abstract class BaseEnchantEffect implements EnchantEffect {

    private final String enchantId;

    protected BaseEnchantEffect(String enchantId) {
        this.enchantId = enchantId;
    }

    @Override
    public String enchantId() {
        return enchantId;
    }

    @Override
    public BigInteger extraDamage(Player player,
                                  LivingEntity target,
                                  BigInteger swordDamage,
                                  EnchantDefinition definition,
                                  EnchantService service) {
        return BigInteger.ZERO;
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
    }
}
