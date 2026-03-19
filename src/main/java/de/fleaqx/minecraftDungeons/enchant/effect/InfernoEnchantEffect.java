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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InfernoEnchantEffect extends BaseEnchantEffect {

    private static final int STACKS_TO_ERUPT = 4;
    private static final double BLAST_RADIUS_SQUARED = 16.0D;
    private static final double BLAST_DAMAGE_MULTIPLIER = 2.25D;
    private static final double EMBER_TICK_MULTIPLIER = 1.0D;
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
                EMBER_TICK_MULTIPLIER, 12, 50, true);

        int stacks = STACKS.merge(mainTarget.getUniqueId(), 1, Integer::sum);
        player.spawnParticle(Particle.FLAME, mainTarget.getLocation().add(0.0D, 1.0D, 0.0D), 16, 0.28D, 0.35D, 0.28D, 0.02D);
        player.playSound(mainTarget.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.6F, 1.15F);

        if (stacks < STACKS_TO_ERUPT) {
            return;
        }

        STACKS.remove(mainTarget.getUniqueId());
        BigInteger blastDamage = service.scaleDamage(swordDamage, BLAST_DAMAGE_MULTIPLIER);
        player.spawnParticle(Particle.LAVA, mainTarget.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.35D, 0.3D, 0.35D, 0.02D);
        player.spawnParticle(Particle.EXPLOSION, mainTarget.getLocation().add(0.0D, 0.8D, 0.0D), 2, 0.2D, 0.2D, 0.2D, 0.0D);
        player.playSound(mainTarget.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8F, 0.8F);

        for (LivingEntity target : dungeonService.activeAttackableOwnedMobs(player)) {
            if (!target.isValid() || target.getLocation().distanceSquared(mainTarget.getLocation()) > BLAST_RADIUS_SQUARED) {
                continue;
            }
            target.setFireTicks(Math.max(target.getFireTicks(), 70));
            DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, target, blastDamage);
            if (result.accepted()) {
                indicatorService.spawnDamage(player, target, blastDamage, service.indicatorStyle(definition));
            }
        }
    }
}
