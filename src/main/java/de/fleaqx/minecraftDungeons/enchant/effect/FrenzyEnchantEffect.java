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

public final class FrenzyEnchantEffect extends BaseEnchantEffect {

    private static final double ATTACK_SPEED_BONUS = 4.0D;
    private static final int DURATION_TICKS = 100;

    public FrenzyEnchantEffect() {
        super("frenzy");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.45F, 1.85F);
        player.spawnParticle(Particle.CRIT, player.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.3D, 0.45D, 0.3D, 0.02D);
        player.spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 1.0D, 0.0D), 8, 0.2D, 0.35D, 0.2D, 0.01D);
        service.grantTemporaryAttackSpeed(player, ATTACK_SPEED_BONUS, DURATION_TICKS);
    }
}
