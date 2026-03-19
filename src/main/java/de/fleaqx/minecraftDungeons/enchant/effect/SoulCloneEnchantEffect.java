package de.fleaqx.minecraftDungeons.enchant.effect;

import de.fleaqx.minecraftDungeons.enchant.EnchantDefinition;
import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import de.fleaqx.minecraftDungeons.runtime.DamageIndicatorService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigInteger;

public final class SoulCloneEnchantEffect extends BaseEnchantEffect {

    private static final int CLONE_STRIKES = 3;
    private static final double CLONE_DAMAGE_MULTIPLIER = 0.9D;

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

        player.playSound(mainTarget.getLocation(), Sound.ENTITY_ALLAY_ITEM_TAKEN, 0.65F, 1.7F);
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        new BukkitRunnable() {
            private int strike = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !mainTarget.isValid() || strike >= CLONE_STRIKES) {
                    cancel();
                    return;
                }

                player.spawnParticle(Particle.SOUL_FIRE_FLAME, mainTarget.getLocation().add(0.0D, 1.0D, 0.0D), 10, 0.22D, 0.25D, 0.22D, 0.02D);
                player.spawnParticle(Particle.SWEEP_ATTACK, mainTarget.getLocation().add(0.0D, 0.9D, 0.0D), 1, 0.12D, 0.12D, 0.12D, 0.0D);
                player.playSound(mainTarget.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.45F, 1.55F + (strike * 0.1F));

                DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, mainTarget, strikeDamage);
                if (result.accepted()) {
                    indicatorService.spawnDamage(player, mainTarget, strikeDamage, service.indicatorStyle(definition));
                }

                strike++;
            }
        }.runTaskTimer(plugin, 1L, 3L);
    }
}
