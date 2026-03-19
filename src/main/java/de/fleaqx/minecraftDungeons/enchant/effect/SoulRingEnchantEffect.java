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

public final class SoulRingEnchantEffect extends BaseEnchantEffect {

    private static final double SOUL_RING_DAMAGE_MULTIPLIER = 4.0D;

    public SoulRingEnchantEffect() {
        super("soul_ring");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_2, 0.4F, 1.7F);
        player.spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.3D, 0.45D, 0.3D, 0.25D);
        service.startSoulRing(player, swordDamage, SOUL_RING_DAMAGE_MULTIPLIER, definition, dungeonService, indicatorService);
    }
}
