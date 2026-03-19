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

public final class MiniWitherEnchantEffect extends BaseEnchantEffect {

    private static final int WITHER_COUNT = 4;
    private static final double WITHER_HIT_MULTIPLIER = 2.4D;

    public MiniWitherEnchantEffect() {
        super("mini_wither");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        player.spawnParticle(Particle.SMOKE, mainTarget.getLocation().add(0, 1.0D, 0), 18, 0.25D, 0.35D, 0.25D, 0.01D);
        player.playSound(mainTarget.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.35F, 1.8F);
        service.applyMiniWitherBarrage(player, swordDamage, WITHER_COUNT, WITHER_HIT_MULTIPLIER, dungeonService, indicatorService);
    }
}
