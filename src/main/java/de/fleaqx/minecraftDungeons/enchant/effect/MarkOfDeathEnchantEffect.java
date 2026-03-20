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

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MarkOfDeathEnchantEffect extends BaseEnchantEffect {

    private static final long MARK_DURATION_MS = 8_000L;
    private static final double MARK_BONUS_DAMAGE_MULTIPLIER = 0.9D;
    private static final Map<UUID, Long> MARKS = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitRunnable> AURAS = new ConcurrentHashMap<>();

    public MarkOfDeathEnchantEffect() {
        super("mark_of_death");
    }

    @Override
    public BigInteger extraDamage(Player player,
                                  LivingEntity target,
                                  BigInteger swordDamage,
                                  EnchantDefinition definition,
                                  EnchantService service) {
        if (!isMarked(target)) {
            return BigInteger.ZERO;
        }

        Location center = center(target);
        player.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 18, 0.22D, 0.32D, 0.22D, 0.02D);
        player.spawnParticle(Particle.CRIT, center, 8, 0.18D, 0.22D, 0.18D, 0.02D);
        player.playSound(center, Sound.ENTITY_WITHER_AMBIENT, 0.2F, 1.85F);
        return service.scaleDamage(swordDamage, MARK_BONUS_DAMAGE_MULTIPLIER);
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        MARKS.put(mainTarget.getUniqueId(), System.currentTimeMillis() + MARK_DURATION_MS);
        Location center = center(mainTarget);
        player.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, center, 12, 0.35D, 0.55D, 0.35D, 0.0D);
        player.spawnParticle(Particle.SOUL, center.clone().add(0.0D, 0.25D, 0.0D), 24, 0.22D, 0.45D, 0.22D, 0.03D);
        player.spawnParticle(Particle.SMOKE, center, 20, 0.28D, 0.45D, 0.28D, 0.02D);
        player.playSound(mainTarget.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.25F, 1.9F);
        startAura(player, mainTarget);
    }

    private void startAura(Player player, LivingEntity target) {
        BukkitRunnable previous = AURAS.remove(target.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        BukkitRunnable task = new BukkitRunnable() {
            private double angle;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid() || !isMarked(target)) {
                    AURAS.remove(target.getUniqueId());
                    cancel();
                    return;
                }

                Location center = center(target);
                for (int ring = 0; ring < 2; ring++) {
                    double ringRadius = 0.38D + (ring * 0.18D);
                    double ringHeight = 0.15D + (ring * 0.35D);
                    for (int i = 0; i < 4; i++) {
                        double current = angle + (i * (Math.PI / 2.0D));
                        Location orbit = center.clone().add(Math.cos(current) * ringRadius, ringHeight, Math.sin(current) * ringRadius);
                        player.spawnParticle(Particle.SOUL_FIRE_FLAME, orbit, 1, 0.02D, 0.02D, 0.02D, 0.0D);
                        player.spawnParticle(Particle.SMOKE, orbit, 1, 0.01D, 0.01D, 0.01D, 0.0D);
                    }
                }
                player.spawnParticle(Particle.ENCHANT, center, 4, 0.18D, 0.38D, 0.18D, 0.2D);
                angle += Math.PI / 8.0D;
            }
        };
        task.runTaskTimer(plugin, 0L, 4L);
        AURAS.put(target.getUniqueId(), task);
    }

    private boolean isMarked(LivingEntity target) {
        Long expiresAt = MARKS.get(target.getUniqueId());
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis() || !target.isValid()) {
            MARKS.remove(target.getUniqueId());
            BukkitRunnable task = AURAS.remove(target.getUniqueId());
            if (task != null) {
                task.cancel();
            }
            return false;
        }
        return true;
    }

    private Location center(LivingEntity target) {
        return target.getLocation().add(0.0D, Math.min(1.2D, target.getHeight() * 0.5D), 0.0D);
    }
}
