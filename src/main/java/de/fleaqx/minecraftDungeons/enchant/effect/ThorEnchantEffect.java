package de.fleaqx.minecraftDungeons.enchant.effect;

import de.fleaqx.minecraftDungeons.enchant.EnchantDefinition;
import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import de.fleaqx.minecraftDungeons.runtime.DamageIndicatorService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.math.BigInteger;

public final class ThorEnchantEffect extends BaseEnchantEffect {

    public ThorEnchantEffect() {
        super("thor");
    }

    @Override
    public BigInteger extraDamage(Player player, LivingEntity target, BigInteger swordDamage, EnchantDefinition definition, EnchantService service) {
        return super.extraDamage(player, target, swordDamage, definition, service);
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        super.applyPostHit(player, mainTarget, swordDamage, definition, service, dungeonService, indicatorService);
    }
}
