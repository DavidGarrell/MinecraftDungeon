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

public final class MarkOfDeathEnchantEffect extends BaseEnchantEffect {

    private static final long MARK_DURATION_MS = 6_000L;
    private static final double MARK_BONUS_DAMAGE_MULTIPLIER = 0.55D;
    private static final Map<UUID, Long> MARKS = new ConcurrentHashMap<>();

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
        player.spawnParticle(Particle.SMOKE, mainTarget.getLocation().add(0.0D, 1.0D, 0.0D), 10, 0.22D, 0.35D, 0.22D, 0.0D);
        player.spawnParticle(Particle.SOUL, mainTarget.getLocation().add(0.0D, 1.2D, 0.0D), 12, 0.18D, 0.3D, 0.18D, 0.02D);
        player.playSound(mainTarget.getLocation(), Sound.ENTITY_WITHER_HURT, 0.45F, 1.7F);
    }

    private boolean isMarked(LivingEntity target) {
        Long expiresAt = MARKS.get(target.getUniqueId());
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis() || !target.isValid()) {
            MARKS.remove(target.getUniqueId());
            return false;
        }
        return true;
    }
}
