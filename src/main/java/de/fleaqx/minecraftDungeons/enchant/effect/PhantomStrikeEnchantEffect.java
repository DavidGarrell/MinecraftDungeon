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

public final class PhantomStrikeEnchantEffect extends BaseEnchantEffect {

    private static final int PHANTOM_COUNT = 3;
    private static final double PHANTOM_HIT_MULTIPLIER = 3.0D;

    public PhantomStrikeEnchantEffect() {
        super("phantom_strike");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        player.spawnParticle(Particle.SOUL_FIRE_FLAME, mainTarget.getLocation().add(0, 1.0D, 0), 14, 0.25D, 0.25D, 0.25D, 0.02D);
        player.playSound(mainTarget.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.45F, 1.6F);
        int phantomCount = definition.phantomCount() > 0 ? definition.phantomCount() : PHANTOM_COUNT;
        double phantomMultiplier = definition.phantomHitMultiplier() > 0.0D ? definition.phantomHitMultiplier() : PHANTOM_HIT_MULTIPLIER;
        service.applyPhantomStrike(player, swordDamage, phantomCount, phantomMultiplier, definition, dungeonService, indicatorService);
    }
}
