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

public final class SoulOrbitEnchantEffect extends BaseEnchantEffect {

    private static final double SOUL_ORBIT_DAMAGE_MULTIPLIER = 1.85D;

    public SoulOrbitEnchantEffect() {
        super("soul_orbit");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.45F, 1.45F);
        player.spawnParticle(Particle.SOUL, player.getLocation().add(0.0D, 1.0D, 0.0D), 16, 0.28D, 0.4D, 0.28D, 0.02D);
        player.spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0D, 1.0D, 0.0D), 14, 0.22D, 0.35D, 0.22D, 0.15D);
        service.startSoulOrbit(player, swordDamage, SOUL_ORBIT_DAMAGE_MULTIPLIER, definition, dungeonService, indicatorService);
    }
}
