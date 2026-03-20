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

public final class FrostNovaEnchantEffect extends BaseEnchantEffect {

    private static final double FROST_NOVA_DAMAGE_MULTIPLIER = 2.2D;
    private static final double NOVA_RADIUS = 4.6D;
    private static final int FREEZE_TICKS = 80;

    public FrostNovaEnchantEffect() {
        super("frost_nova");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.65F, 1.65F);
        player.spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0.0D, 1.0D, 0.0D), 28, 0.35D, 0.45D, 0.35D, 0.02D);
        service.applyFrostNova(player, swordDamage, FROST_NOVA_DAMAGE_MULTIPLIER, NOVA_RADIUS, FREEZE_TICKS, definition, dungeonService, indicatorService);
    }
}
