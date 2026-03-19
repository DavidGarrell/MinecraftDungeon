package de.fleaqx.minecraftDungeons.enchant.effect;

import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.enchant.EnchantDefinition;
import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import de.fleaqx.minecraftDungeons.runtime.DamageIndicatorService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.math.BigInteger;

public final class SoulFinderEnchantEffect extends BaseEnchantEffect {

    private static final String ENCHANT_ID = "soul_greed";
    private static final String SOUL_MAGNET_ID = "soul_magnet";
    private static final BigInteger BASE_SOULS_PER_PROC = BigInteger.valueOf(120L);
    private static final double SOUL_MAGNET_BONUS_PER_LEVEL = 0.0002D;

    public SoulFinderEnchantEffect() {
        super(ENCHANT_ID);
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        BigInteger soulsGained = BASE_SOULS_PER_PROC;
        int soulMagnetLevel = service.enchantLevel(player, SOUL_MAGNET_ID);
        if (soulMagnetLevel > 0 && service.enchantEnabled(player, SOUL_MAGNET_ID)) {
            soulsGained = service.scaleDamage(soulsGained, 1.0D + (SOUL_MAGNET_BONUS_PER_LEVEL * soulMagnetLevel));
        }
        if (soulsGained.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }
        player.spawnParticle(Particle.SOUL, mainTarget.getLocation().add(0, 1.0D, 0), 12, 0.25D, 0.35D, 0.25D, 0.02D);
        player.playSound(mainTarget.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 0.8F);
        dungeonService.addCurrency(player, CurrencyType.SOULS, soulsGained);
    }
}
