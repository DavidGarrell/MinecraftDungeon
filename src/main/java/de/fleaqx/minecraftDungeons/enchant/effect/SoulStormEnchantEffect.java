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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class SoulStormEnchantEffect extends BaseEnchantEffect {

    private static final int STORM_SHOTS = 6;
    private static final double SHOT_DAMAGE_MULTIPLIER = 1.65D;

    public SoulStormEnchantEffect() {
        super("soul_storm");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        BigInteger shotDamage = service.scaleDamage(swordDamage, SHOT_DAMAGE_MULTIPLIER);
        if (shotDamage.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7F, 1.1F);
        new BukkitRunnable() {
            private int shots;

            @Override
            public void run() {
                if (!player.isOnline() || shots >= STORM_SHOTS) {
                    cancel();
                    return;
                }

                List<LivingEntity> targets = dungeonService.activeAttackableOwnedMobs(player).stream()
                        .filter(LivingEntity::isValid)
                        .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
                        .toList();
                if (targets.isEmpty()) {
                    cancel();
                    return;
                }

                LivingEntity target = targets.get(Math.min(shots % Math.max(1, targets.size()), targets.size() - 1));
                if (targets.size() > 2 && ThreadLocalRandom.current().nextBoolean()) {
                    target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
                }
                launchSoulProjectile(player, target, shotDamage, definition, service, dungeonService, indicatorService, shots);
                shots++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private void launchSoulProjectile(Player player,
                                      LivingEntity target,
                                      BigInteger damage,
                                      EnchantDefinition definition,
                                      EnchantService service,
                                      DungeonService dungeonService,
                                      DamageIndicatorService indicatorService,
                                      int shotIndex) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        Location start = player.getEyeLocation().clone().add(randomOffset(), -0.25D + ThreadLocalRandom.current().nextDouble(0.0D, 0.45D), randomOffset());
        spawnCastRing(player, start, shotIndex);
        new BukkitRunnable() {
            private Location current = start.clone();
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid() || current.getWorld() == null) {
                    cancel();
                    return;
                }

                Location targetLocation = target.getLocation().add(0.0D, Math.min(1.0D, target.getHeight() * 0.45D), 0.0D);
                Vector direction = targetLocation.toVector().subtract(current.toVector());
                double distance = direction.length();
                if (distance <= 0.75D || tick >= 22) {
                    player.spawnParticle(Particle.SOUL, targetLocation, 28, 0.25D, 0.28D, 0.25D, 0.03D);
                    player.spawnParticle(Particle.SOUL_FIRE_FLAME, targetLocation, 16, 0.18D, 0.22D, 0.18D, 0.02D);
                    player.spawnParticle(Particle.ENCHANT, targetLocation, 20, 0.16D, 0.18D, 0.16D, 0.18D);
                    player.playSound(target.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.45F, 1.9F);
                    player.playSound(target.getLocation(), Sound.ENTITY_ALLAY_HURT, 0.3F, 0.6F);
                    DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, target, damage);
                    if (result.accepted()) {
                        indicatorService.spawnDamage(player, target, damage, service.indicatorStyle(definition));
                    }
                    cancel();
                    return;
                }

                Vector spiralOffset = spiralOffset(direction, tick, shotIndex);
                Vector movement = direction.normalize().multiply(Math.min(1.05D, distance));
                current = current.clone().add(movement).add(spiralOffset);
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, current, 7, 0.05D, 0.05D, 0.05D, 0.0D);
                player.spawnParticle(Particle.SOUL, current, 4, 0.03D, 0.03D, 0.03D, 0.01D);
                player.spawnParticle(Particle.ENCHANT, current, 3, 0.02D, 0.02D, 0.02D, 0.0D);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnCastRing(Player player, Location start, int shotIndex) {
        double radius = 0.35D + ((shotIndex % 3) * 0.08D);
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0D * i) / 12.0D;
            Location point = start.clone().add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            player.spawnParticle(Particle.SOUL, point, 2, 0.02D, 0.02D, 0.02D, 0.0D);
            player.spawnParticle(Particle.ENCHANT, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private Vector spiralOffset(Vector direction, int tick, int shotIndex) {
        Vector axis = Math.abs(direction.getY()) > 0.9D ? new Vector(1.0D, 0.0D, 0.0D) : new Vector(0.0D, 1.0D, 0.0D);
        Vector side = direction.clone().crossProduct(axis);
        if (side.lengthSquared() <= 0.0001D) {
            side = new Vector(1.0D, 0.0D, 0.0D);
        }
        side.normalize();
        Vector up = side.clone().crossProduct(direction.clone().normalize()).normalize();
        double angle = (tick * 0.9D) + (shotIndex * 0.7D);
        double radius = 0.12D;
        return side.multiply(Math.cos(angle) * radius).add(up.multiply(Math.sin(angle) * radius));
    }

    private double randomOffset() {
        return ThreadLocalRandom.current().nextDouble(-0.9D, 0.9D);
    }
}
