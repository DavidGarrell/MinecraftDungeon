package de.fleaqx.minecraftDungeons.enchant.effect;

import de.fleaqx.minecraftDungeons.enchant.EnchantDefinition;
import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import de.fleaqx.minecraftDungeons.runtime.DamageIndicatorService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;

public final class ChainLightningEnchantEffect extends BaseEnchantEffect {

    private static final int MAX_BOUNCES = 4;
    private static final double CHAIN_RADIUS_SQUARED = 36.0D;
    private static final double CHAIN_DAMAGE_MULTIPLIER = 0.8D;

    public ChainLightningEnchantEffect() {
        super("chain_lightning");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        BigInteger chainDamage = service.scaleDamage(swordDamage, CHAIN_DAMAGE_MULTIPLIER);
        if (chainDamage.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        List<LivingEntity> nearby = dungeonService.activeAttackableOwnedMobs(player).stream()
                .filter(LivingEntity::isValid)
                .filter(target -> !target.getUniqueId().equals(mainTarget.getUniqueId()))
                .filter(target -> target.getLocation().distanceSquared(mainTarget.getLocation()) <= CHAIN_RADIUS_SQUARED)
                .sorted(Comparator.comparingDouble(target -> target.getLocation().distanceSquared(mainTarget.getLocation())))
                .limit(MAX_BOUNCES)
                .toList();

        if (nearby.isEmpty()) {
            return;
        }

        Location origin = mainTarget.getLocation().add(0.0D, 1.0D, 0.0D);
        player.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7F, 1.6F);
        for (LivingEntity chainedTarget : nearby) {
            Location targetLocation = chainedTarget.getLocation().add(0.0D, 1.0D, 0.0D);
            drawArc(player, origin, targetLocation);
            DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, chainedTarget, chainDamage);
            if (result.accepted()) {
                indicatorService.spawnDamage(player, chainedTarget, chainDamage, service.indicatorStyle(definition));
            }
        }
    }

    private void drawArc(Player player, Location from, Location to) {
        Location current = from.clone();
        double distance = from.distance(to);
        int steps = Math.max(6, (int) Math.ceil(distance * 4.0D));
        for (int i = 1; i <= steps; i++) {
            double progress = i / (double) steps;
            current = from.clone().add(to.clone().subtract(from).toVector().multiply(progress));
            player.spawnParticle(Particle.ELECTRIC_SPARK, current, 2, 0.03D, 0.03D, 0.03D, 0.0D);
            player.spawnParticle(Particle.END_ROD, current, 1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }
}
