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
    public BigInteger extraDamage(Player player, LivingEntity target, BigInteger swordDamage, EnchantDefinition definition, EnchantService service) {
        if (definition.damageMultiplier() <= 0.0D) {
            return BigInteger.ZERO;
        }
        return service.scaleDamage(swordDamage, definition.damageMultiplier());
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        service.applyCurrencyBonus(player, definition, dungeonService);

        if (definition.hitsAllZoneMobs()) {
            BigInteger zoneDamage = service.scaleDamage(swordDamage,
                    Math.max(0.1D, definition.damageMultiplier() <= 0.0D ? 1.0D : definition.damageMultiplier()));
            dungeonService.damageOwnedMobs(player, zoneDamage, mainTarget.getUniqueId(), indicatorService);
        }

        if (definition.executeEffect()) {
            dungeonService.executeDamage(player, mainTarget, definition.damageMultiplier(), indicatorService);
        }

        if (definition.fireEffect()) {
            service.applyDotTicks(player, mainTarget, swordDamage, dungeonService, indicatorService,
                    definition.fireTickMultiplier(), definition.dotTicks(), definition.fireTicks(), true);
        }

        if (definition.freezeEffect()) {
            service.applyDotTicks(player, mainTarget, swordDamage, dungeonService, indicatorService,
                    definition.freezeTickMultiplier(), definition.dotTicks(), definition.freezeTicks(), false);
        }

        if (definition.phantomEffect()) {
            service.applyPhantomStrike(player, swordDamage, definition.phantomCount(), definition.phantomHitMultiplier(), dungeonService, indicatorService);
        }

        if (definition.lightningEffect()) {
            service.applyLightningStrike(player, swordDamage, definition.lightningHitMultiplier(), dungeonService, indicatorService);
        }
    }
}
