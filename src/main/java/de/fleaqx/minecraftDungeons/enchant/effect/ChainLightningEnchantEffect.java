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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ChainLightningEnchantEffect extends BaseEnchantEffect {

    private static final int MAX_BOUNCES = 6;
    private static final double CHAIN_RADIUS_SQUARED = 64.0D;
    private static final double CHAIN_DAMAGE_MULTIPLIER = 1.1D;

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

        List<LivingEntity> chainTargets = new ArrayList<>(nearby.size() + 1);
        chainTargets.add(mainTarget);
        chainTargets.addAll(nearby);

        Location origin = center(mainTarget);
        player.spawnParticle(Particle.FLASH, origin, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        player.spawnParticle(Particle.ELECTRIC_SPARK, origin, 26, 0.35D, 0.45D, 0.35D, 0.08D);
        player.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9F, 1.55F);

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        new BukkitRunnable() {
            private int bounce = 0;

            @Override
            public void run() {
                if (!player.isOnline() || bounce >= chainTargets.size() - 1) {
                    cancel();
                    return;
                }

                LivingEntity fromTarget = chainTargets.get(bounce);
                LivingEntity chainedTarget = chainTargets.get(bounce + 1);
                if (!fromTarget.isValid() || !chainedTarget.isValid()) {
                    bounce++;
                    return;
                }

                Location from = center(fromTarget);
                Location to = center(chainedTarget);
                drawStormArc(player, from, to);
                player.playSound(to, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.35F, 1.9F + (bounce * 0.05F));
                player.spawnParticle(Particle.FLASH, to, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                player.spawnParticle(Particle.WAX_ON, to, 10, 0.22D, 0.22D, 0.22D, 0.0D);

                DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, chainedTarget, chainDamage);
                if (result.accepted()) {
                    indicatorService.spawnDamage(player, chainedTarget, chainDamage, service.indicatorStyle(definition));
                }

                bounce++;
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    private Location center(LivingEntity target) {
        return target.getLocation().add(0.0D, Math.min(1.3D, target.getHeight() * 0.55D), 0.0D);
    }

    private void drawStormArc(Player player, Location from, Location to) {
        Vector delta = to.toVector().subtract(from.toVector());
        double distance = delta.length();
        if (distance <= 0.001D) {
            return;
        }

        Vector direction = delta.clone().normalize();
        Vector side = perpendicular(direction);
        int steps = Math.max(12, (int) Math.ceil(distance * 7.5D));
        double swaySeed = ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
        for (int i = 0; i <= steps; i++) {
            double progress = i / (double) steps;
            Location point = from.clone().add(delta.clone().multiply(progress));
            double sway = Math.sin((progress * Math.PI * 4.0D) + swaySeed) * 0.22D;
            double vertical = Math.cos((progress * Math.PI * 3.0D) + swaySeed) * 0.11D;
            point.add(side.clone().multiply(sway)).add(0.0D, vertical, 0.0D);

            player.spawnParticle(Particle.ELECTRIC_SPARK, point, 4, 0.05D, 0.05D, 0.05D, 0.01D);
            player.spawnParticle(Particle.END_ROD, point, 2, 0.02D, 0.02D, 0.02D, 0.0D);
            if (i % 2 == 0) {
                player.spawnParticle(Particle.WAX_OFF, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private Vector perpendicular(Vector direction) {
        Vector axis = Math.abs(direction.getY()) > 0.9D ? new Vector(1.0D, 0.0D, 0.0D) : new Vector(0.0D, 1.0D, 0.0D);
        Vector perpendicular = direction.clone().crossProduct(axis);
        if (perpendicular.lengthSquared() <= 0.0001D) {
            perpendicular = new Vector(1.0D, 0.0D, 0.0D);
        }
        return perpendicular.normalize();
    }
}
