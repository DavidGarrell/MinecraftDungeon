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

public final class ExecuteEnchantEffect extends BaseEnchantEffect {

    private static final double EXECUTE_DAMAGE_MULTIPLIER = 0.05D;

    public ExecuteEnchantEffect() {
        super("execute");
    }

    @Override
    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantDefinition definition,
                             EnchantService service,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        player.spawnParticle(Particle.SWEEP_ATTACK, mainTarget.getLocation().add(0, 1.0D, 0), 2, 0.2D, 0.2D, 0.2D, 0.0D);
        player.playSound(mainTarget.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.75F, 0.9F);
        dungeonService.executeDamage(player, mainTarget, EXECUTE_DAMAGE_MULTIPLIER, indicatorService, service.indicatorStyle(definition));
    }
}
