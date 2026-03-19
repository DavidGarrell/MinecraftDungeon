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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class SoulStormEnchantEffect extends BaseEnchantEffect {

    private static final int STORM_SHOTS = 4;
    private static final double SHOT_DAMAGE_MULTIPLIER = 1.35D;

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
        player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.7F, 0.8F);
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
                        .toList();
                if (targets.isEmpty()) {
                    cancel();
                    return;
                }

                LivingEntity target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
                launchSoulProjectile(player, target, shotDamage, definition, service, dungeonService, indicatorService);
                shots++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void launchSoulProjectile(Player player,
                                      LivingEntity target,
                                      BigInteger damage,
                                      EnchantDefinition definition,
                                      EnchantService service,
                                      DungeonService dungeonService,
                                      DamageIndicatorService indicatorService) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        Location start = player.getEyeLocation().clone().add(randomOffset(), -0.3D + ThreadLocalRandom.current().nextDouble(0.0D, 0.5D), randomOffset());
        new BukkitRunnable() {
            private Location current = start.clone();
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid() || current.getWorld() == null) {
                    cancel();
                    return;
                }

                Location targetLocation = target.getLocation().add(0.0D, 0.9D, 0.0D);
                Vector direction = targetLocation.toVector().subtract(current.toVector());
                double distance = direction.length();
                if (distance <= 0.65D || tick >= 18) {
                    player.spawnParticle(Particle.SOUL, targetLocation, 16, 0.18D, 0.22D, 0.18D, 0.02D);
                    player.spawnParticle(Particle.ENCHANT, targetLocation, 12, 0.15D, 0.18D, 0.15D, 0.15D);
                    player.playSound(target.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.35F, 1.85F);
                    DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, target, damage);
                    if (result.accepted()) {
                        indicatorService.spawnDamage(player, target, damage, service.indicatorStyle(definition));
                    }
                    cancel();
                    return;
                }

                Vector movement = direction.normalize().multiply(Math.min(0.95D, distance));
                current = current.clone().add(movement);
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, current, 5, 0.04D, 0.04D, 0.04D, 0.0D);
                player.spawnParticle(Particle.ENCHANT, current, 2, 0.02D, 0.02D, 0.02D, 0.0D);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private double randomOffset() {
        return ThreadLocalRandom.current().nextDouble(-0.8D, 0.8D);
    }
}
