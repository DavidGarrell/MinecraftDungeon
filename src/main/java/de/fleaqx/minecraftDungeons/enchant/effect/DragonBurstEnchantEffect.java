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

public final class DragonBurstEnchantEffect extends BaseEnchantEffect {

    private static final double ZONE_DAMAGE_MULTIPLIER = 1.0D;

    public DragonBurstEnchantEffect() {
        super("dragon_burst");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        BigInteger zoneDamage = service.scaleDamage(swordDamage, ZONE_DAMAGE_MULTIPLIER);
        if (zoneDamage.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }
        player.spawnParticle(Particle.DRAGON_BREATH, mainTarget.getLocation().add(0, 1.0D, 0), 24, 0.45D, 0.45D, 0.45D, 0.02D);
        player.playSound(mainTarget.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5F, 1.6F);
        dungeonService.damageOwnedMobs(player, zoneDamage, mainTarget.getUniqueId(), indicatorService);
    }
}
