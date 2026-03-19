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

public final class BlazedEnchantEffect extends BaseEnchantEffect {

    private static final double FIRE_TICK_MULTIPLIER = 4.0D;
    private static final int DOT_TICKS = 20;
    private static final int FIRE_TICKS = 60;

    public BlazedEnchantEffect() {
        super("blazed_enchant");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        player.spawnParticle(Particle.FLAME, mainTarget.getLocation().add(0, 1.0D, 0), 20, 0.3D, 0.5D, 0.3D, 0.03D);
        player.playSound(mainTarget.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.7F, 1.0F);
        service.applyDotTicks(player, mainTarget, swordDamage, definition, dungeonService, indicatorService,
                FIRE_TICK_MULTIPLIER, DOT_TICKS, FIRE_TICKS, true);
    }
}
