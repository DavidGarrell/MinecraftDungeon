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

public final class FrostbiteEnchantEffect extends BaseEnchantEffect {

    private static final double FREEZE_TICK_MULTIPLIER = 1.5D;
    private static final int DOT_TICKS = 20;
    private static final int FREEZE_TICKS = 60;

    public FrostbiteEnchantEffect() {
        super("frostbite");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        player.spawnParticle(Particle.SNOWFLAKE, mainTarget.getLocation().add(0, 1.0D, 0), 18, 0.35D, 0.45D, 0.35D, 0.01D);
        player.playSound(mainTarget.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.55F, 1.8F);
        service.applyDotTicks(player, mainTarget, swordDamage, definition, dungeonService, indicatorService,
                FREEZE_TICK_MULTIPLIER, DOT_TICKS, FREEZE_TICKS, false);
    }
}
