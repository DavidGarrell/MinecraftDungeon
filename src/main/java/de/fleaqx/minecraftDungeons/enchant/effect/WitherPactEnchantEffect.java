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

public final class WitherPactEnchantEffect extends BaseEnchantEffect {

    private static final double WITHER_PACT_DAMAGE_MULTIPLIER = 3.4D;

    public WitherPactEnchantEffect() {
        super("wither_pact");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        player.playSound(mainTarget.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.45F, 1.35F);
        player.spawnParticle(Particle.SMOKE, mainTarget.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.24D, 0.32D, 0.24D, 0.01D);
        player.spawnParticle(Particle.SOUL, mainTarget.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.2D, 0.22D, 0.2D, 0.01D);
        service.startWitherPact(player, swordDamage, WITHER_PACT_DAMAGE_MULTIPLIER, definition, dungeonService, indicatorService);
    }
}
