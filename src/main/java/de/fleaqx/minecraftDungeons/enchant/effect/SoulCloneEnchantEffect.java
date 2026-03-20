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

public final class SoulCloneEnchantEffect extends BaseEnchantEffect {

    private static final int CLONE_STRIKES = 5;
    private static final double CLONE_DAMAGE_MULTIPLIER = 1.0D;

    public SoulCloneEnchantEffect() {
        super("soul_clone");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        BigInteger strikeDamage = service.scaleDamage(swordDamage, CLONE_DAMAGE_MULTIPLIER);
        if (strikeDamage.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        Location center = center(mainTarget);
        player.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, center, 10, 0.25D, 0.35D, 0.25D, 0.0D);
        player.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 18, 0.28D, 0.3D, 0.28D, 0.02D);
        player.playSound(center, Sound.ENTITY_ALLAY_ITEM_TAKEN, 0.8F, 1.55F);
        player.playSound(center, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.35F, 1.45F);

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        new BukkitRunnable() {
            private int strike = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !mainTarget.isValid() || strike >= CLONE_STRIKES) {
                    cancel();
                    return;
                }

                Location targetCenter = center(mainTarget);
                Location from = flank(targetCenter, strike);
                drawCloneDash(player, from, targetCenter, strike);
                player.spawnParticle(Particle.SWEEP_ATTACK, targetCenter, 2, 0.18D, 0.18D, 0.18D, 0.0D);
                player.playSound(targetCenter, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.55F, 1.35F + (strike * 0.08F));
                player.playSound(targetCenter, Sound.ENTITY_VEX_CHARGE, 0.25F, 1.65F);

                DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, mainTarget, strikeDamage);
                if (result.accepted()) {
                    indicatorService.spawnDamage(player, mainTarget, strikeDamage, service.indicatorStyle(definition));
                }

                strike++;
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    private void drawCloneDash(Player player, Location from, Location to, int strike) {
        Vector delta = to.toVector().subtract(from.toVector());
        int steps = Math.max(10, (int) Math.ceil(delta.length() * 7.0D));
        for (int i = 0; i <= steps; i++) {
            double progress = i / (double) steps;
            Location point = from.clone().add(delta.clone().multiply(progress));
            point.add(0.0D, Math.sin(progress * Math.PI) * 0.18D, 0.0D);
            player.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 3, 0.03D, 0.03D, 0.03D, 0.0D);
            player.spawnParticle(Particle.ENCHANT, point, 2, 0.02D, 0.02D, 0.02D, 0.12D);
            if ((i + strike) % 3 == 0) {
                player.spawnParticle(Particle.WITCH, point, 1, 0.01D, 0.01D, 0.01D, 0.0D);
            }
        }
    }

    private Location flank(Location center, int strike) {
        double angle = (Math.PI * 2.0D * strike) / CLONE_STRIKES;
        return center.clone().add(Math.cos(angle) * 1.45D, 0.15D + ((strike % 2) * 0.25D), Math.sin(angle) * 1.45D);
    }

    private Location center(LivingEntity target) {
        return target.getLocation().add(0.0D, Math.min(1.05D, target.getHeight() * 0.48D), 0.0D);
    }
}
