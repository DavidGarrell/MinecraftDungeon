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

public final class EssenceFinderEnchantEffect extends BaseEnchantEffect {

    private static final String ENCHANT_ID = "essence_finder";
    private static final String ESSENCE_MAGNET_ID = "essence_magnet";
    private static final BigInteger BASE_ESSENCE_PER_PROC = BigInteger.valueOf(80L);
    private static final double ESSENCE_MAGNET_BONUS_PER_LEVEL = 0.0005D;

    public EssenceFinderEnchantEffect() {
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
        BigInteger essenceGained = BASE_ESSENCE_PER_PROC;
        int essenceMagnetLevel = service.enchantLevel(player, ESSENCE_MAGNET_ID);
        if (essenceMagnetLevel > 0 && service.enchantEnabled(player, ESSENCE_MAGNET_ID)) {
            essenceGained = service.scaleDamage(essenceGained, 1.0D + (ESSENCE_MAGNET_BONUS_PER_LEVEL * essenceMagnetLevel));
        }
        if (essenceGained.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }
        player.spawnParticle(Particle.ENCHANT, mainTarget.getLocation().add(0, 1.0D, 0), 16, 0.25D, 0.35D, 0.25D, 0.5D);
        player.playSound(mainTarget.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7F, 1.25F);
        dungeonService.addCurrency(player, CurrencyType.ESSENCE, essenceGained);
    }
}
