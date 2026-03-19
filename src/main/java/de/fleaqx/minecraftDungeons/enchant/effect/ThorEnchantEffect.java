package de.fleaqx.minecraftDungeons.enchant.effect;

import de.fleaqx.minecraftDungeons.enchant.EnchantDefinition;
import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import de.fleaqx.minecraftDungeons.runtime.DamageIndicatorService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.math.BigInteger;

public final class ThorEnchantEffect extends BaseEnchantEffect {

    private static final double LIGHTNING_HIT_MULTIPLIER = 7.0D;

    public ThorEnchantEffect() {
        super("thor");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        player.spawnParticle(Particle.ELECTRIC_SPARK, mainTarget.getLocation().add(0, 1.0D, 0), 18, 0.25D, 0.45D, 0.25D, 0.03D);
        player.playSound(mainTarget.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.65F, 1.3F);
        service.applyLightningStrike(player, swordDamage, LIGHTNING_HIT_MULTIPLIER, dungeonService, indicatorService);
    }
}
