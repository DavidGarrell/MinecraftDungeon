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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InfernoEnchantEffect extends BaseEnchantEffect {

    private static final int STACKS_TO_ERUPT = 3;
    private static final double BLAST_RADIUS_SQUARED = 36.0D;
    private static final double BLAST_DAMAGE_MULTIPLIER = 3.25D;
    private static final double EMBER_TICK_MULTIPLIER = 1.35D;
    private static final Map<UUID, Integer> STACKS = new ConcurrentHashMap<>();

    public InfernoEnchantEffect() {
        super("inferno");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        service.applyDotTicks(player, mainTarget, swordDamage, definition, dungeonService, indicatorService,
                EMBER_TICK_MULTIPLIER, 14, 55, true);

        int stacks = STACKS.merge(mainTarget.getUniqueId(), 1, Integer::sum);
        Location center = center(mainTarget);
        spawnHeatSpiral(player, center, stacks);
        player.playSound(center, Sound.ITEM_FIRECHARGE_USE, 0.75F, 1.0F + (stacks * 0.08F));

        if (stacks < STACKS_TO_ERUPT) {
            return;
        }

        STACKS.remove(mainTarget.getUniqueId());
        BigInteger blastDamage = service.scaleDamage(swordDamage, BLAST_DAMAGE_MULTIPLIER);
        erupt(player, mainTarget, center, blastDamage, definition, service, dungeonService, indicatorService);
    }

    private void erupt(Player player,
                       LivingEntity mainTarget,
                       Location center,
                       BigInteger blastDamage,
                       EnchantDefinition definition,
                       EnchantService service,
                       DungeonService dungeonService,
                       DamageIndicatorService indicatorService) {
        player.spawnParticle(Particle.FLASH, center, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        player.spawnParticle(Particle.LAVA, center, 42, 0.45D, 0.38D, 0.45D, 0.03D);
        player.spawnParticle(Particle.FLAME, center, 75, 0.75D, 0.55D, 0.75D, 0.04D);
        player.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0.0D, 0.15D, 0.0D), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        player.playSound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.9F, 0.85F);
        player.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 0.85F, 0.55F);
        spawnBlastRing(player, center);

        for (LivingEntity target : dungeonService.activeAttackableOwnedMobs(player)) {
            if (!target.isValid() || target.getLocation().distanceSquared(mainTarget.getLocation()) > BLAST_RADIUS_SQUARED) {
                continue;
            }
            drawInfernoTrail(player, center, center(target));
            target.setFireTicks(Math.max(target.getFireTicks(), 110));
            DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, target, blastDamage);
            if (result.accepted()) {
                indicatorService.spawnDamage(player, target, blastDamage, service.indicatorStyle(definition));
            }
        }
    }

    private void spawnHeatSpiral(Player player, Location center, int stacks) {
        int points = 8 + (stacks * 4);
        for (int i = 0; i < points; i++) {
            double progress = i / (double) points;
            double angle = (progress * Math.PI * 2.0D) + (stacks * 0.35D);
            double radius = 0.18D + (progress * 0.32D);
            Location point = center.clone().add(Math.cos(angle) * radius, progress * 0.7D, Math.sin(angle) * radius);
            player.spawnParticle(Particle.FLAME, point, 2, 0.02D, 0.03D, 0.02D, 0.01D);
            player.spawnParticle(Particle.SMALL_FLAME, point, 1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }

    private void spawnBlastRing(Player player, Location center) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick >= 4) {
                    cancel();
                    return;
                }

                double radius = 0.9D + (tick * 0.75D);
                for (int i = 0; i < 18; i++) {
                    double angle = (Math.PI * 2.0D * i) / 18.0D;
                    Location ringPoint = center.clone().add(Math.cos(angle) * radius, 0.12D + (tick * 0.08D), Math.sin(angle) * radius);
                    player.spawnParticle(Particle.FLAME, ringPoint, 3, 0.04D, 0.02D, 0.04D, 0.01D);
                    player.spawnParticle(Particle.SMOKE, ringPoint, 1, 0.02D, 0.01D, 0.02D, 0.0D);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawInfernoTrail(Player player, Location from, Location to) {
        Vector delta = to.toVector().subtract(from.toVector());
        int steps = Math.max(8, (int) Math.ceil(delta.length() * 5.5D));
        for (int i = 0; i <= steps; i++) {
            double progress = i / (double) steps;
            Location point = from.clone().add(delta.clone().multiply(progress));
            point.add(0.0D, Math.sin(progress * Math.PI) * 0.45D, 0.0D);
            player.spawnParticle(Particle.FLAME, point, 4, 0.04D, 0.04D, 0.04D, 0.01D);
            if (i % 2 == 0) {
                player.spawnParticle(Particle.LAVA, point, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
        }
    }

    private Location center(LivingEntity target) {
        return target.getLocation().add(0.0D, Math.min(1.1D, target.getHeight() * 0.5D), 0.0D);
    }
}
