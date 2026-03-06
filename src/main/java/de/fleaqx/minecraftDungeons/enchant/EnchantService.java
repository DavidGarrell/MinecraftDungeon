package de.fleaqx.minecraftDungeons.enchant;

import de.fleaqx.minecraftDungeons.profile.PlayerProfile;
import de.fleaqx.minecraftDungeons.profile.ProfileService;
import de.fleaqx.minecraftDungeons.runtime.DamageIndicatorService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantService {

    private final JavaPlugin plugin;
    private final EnchantConfigService configService;
    private final ProfileService profileService;

    private final Map<String, EnchantDefinition> definitions = new HashMap<>();
    private BigInteger xpPerHit = BigInteger.ONE;
    private BigInteger xpBase = BigInteger.valueOf(100L);
    private double xpGrowth = 1.15D;
    private double maxActivationChance = 1.0D;

    public EnchantService(JavaPlugin plugin, EnchantConfigService configService, ProfileService profileService) {
        this.plugin = plugin;
        this.configService = configService;
        this.profileService = profileService;
    }

    public void reload() {
        definitions.clear();
        definitions.putAll(configService.load());
        xpPerHit = configService.toolXpPerHit();
        xpBase = configService.toolXpBaseRequirement();
        xpGrowth = configService.toolXpGrowth();
        maxActivationChance = configService.maxActivationChance();
    }

    public List<EnchantDefinition> byCategory(EnchantCategory category) {
        return definitions.values().stream()
                .filter(def -> def.category() == category)
                .sorted(Comparator.comparing(EnchantDefinition::id))
                .toList();
    }

    public Optional<EnchantDefinition> definition(String id) {
        return Optional.ofNullable(definitions.get(id.toLowerCase(Locale.ROOT)));
    }

    public int enchantLevel(Player player, String id) {
        return profile(player).enchantLevel(id);
    }

    public int toolLevel(Player player) {
        return profile(player).toolLevel();
    }

    public BigInteger toolXp(Player player) {
        return profile(player).toolXp();
    }

    public BigInteger toolXpRequiredNext(Player player) {
        int level = toolLevel(player);
        return scale(xpBase, Math.pow(xpGrowth, Math.max(0, level - 1))).max(BigInteger.ONE);
    }

    public boolean enchantEnabled(Player player, String id) {
        return profile(player).enchantEnabled(id);
    }

    public boolean enchantMessageEnabled(Player player, String id) {
        return profile(player).enchantMessageEnabled(id);
    }

    public boolean toggleEnabled(Player player, String id) {
        PlayerProfile profile = profile(player);
        boolean next = !profile.enchantEnabled(id);
        profile.enchantEnabled(id, next);
        return next;
    }

    public boolean toggleMessages(Player player, String id) {
        PlayerProfile profile = profile(player);
        boolean next = !profile.enchantMessageEnabled(id);
        profile.enchantMessageEnabled(id, next);
        return next;
    }

    public void grantHitXp(Player player) {
        PlayerProfile profile = profile(player);
        profile.addToolXp(xpPerHit);
        while (profile.toolXp().compareTo(toolXpRequiredNextRaw(profile.toolLevel())) >= 0) {
            BigInteger need = toolXpRequiredNextRaw(profile.toolLevel());
            profile.toolXp(profile.toolXp().subtract(need));
            profile.toolLevel(profile.toolLevel() + 1);
            player.sendMessage(ChatColor.AQUA + "Tool leveled up to " + profile.toolLevel() + ".");
        }
    }

    public boolean upgrade(Player player, String id) {
        return upgradeLevels(player, id, 1) > 0;
    }

    public int upgradeLevels(Player player, String id, int amount) {
        EnchantDefinition def = definitions.get(id.toLowerCase(Locale.ROOT));
        if (def == null || amount <= 0) {
            return 0;
        }

        PlayerProfile profile = profile(player);
        if (profile.toolLevel() < def.requiredToolLevel()) {
            return 0;
        }

        int current = profile.enchantLevel(def.id());
        int maxBuy = Math.min(amount, def.maxLevel() - current);
        if (maxBuy <= 0) {
            return 0;
        }

        int bought = 0;
        for (int i = 0; i < maxBuy; i++) {
            int next = current + bought + 1;
            BigInteger price = priceFor(def, next);
            if (!profile.remove(def.costCurrency(), price)) {
                break;
            }
            bought++;
        }

        if (bought > 0) {
            profile.enchantLevel(def.id(), current + bought);
        }
        return bought;
    }

    public int maxUpgrade(Player player, String id) {
        EnchantDefinition def = definitions.get(id.toLowerCase(Locale.ROOT));
        if (def == null) {
            return 0;
        }

        PlayerProfile profile = profile(player);
        if (profile.toolLevel() < def.requiredToolLevel()) {
            return 0;
        }

        int current = profile.enchantLevel(def.id());
        int bought = 0;
        while (current + bought < def.maxLevel()) {
            BigInteger price = priceFor(def, current + bought + 1);
            if (!profile.remove(def.costCurrency(), price)) {
                break;
            }
            bought++;
        }

        if (bought > 0) {
            profile.enchantLevel(def.id(), current + bought);
        }
        return bought;
    }

    public BigInteger totalPriceFor(Player player, String id, int levels) {
        EnchantDefinition def = definitions.get(id.toLowerCase(Locale.ROOT));
        if (def == null || levels <= 0) {
            return BigInteger.ZERO;
        }

        int current = enchantLevel(player, id);
        int max = Math.min(levels, Math.max(0, def.maxLevel() - current));
        BigInteger total = BigInteger.ZERO;
        for (int i = 1; i <= max; i++) {
            total = total.add(priceFor(def, current + i));
        }
        return total;
    }

    public int maxAffordableLevels(Player player, String id) {
        EnchantDefinition def = definitions.get(id.toLowerCase(Locale.ROOT));
        if (def == null) {
            return 0;
        }

        PlayerProfile profile = profile(player);
        if (profile.toolLevel() < def.requiredToolLevel()) {
            return 0;
        }

        BigInteger budget = profile.balance(def.costCurrency());
        int current = profile.enchantLevel(def.id());
        int count = 0;
        while (current + count < def.maxLevel()) {
            BigInteger nextPrice = priceFor(def, current + count + 1);
            if (budget.compareTo(nextPrice) < 0) {
                break;
            }
            budget = budget.subtract(nextPrice);
            count++;
        }
        return count;
    }

    public void setEnchantLevel(Player target, String id, int level) {
        EnchantDefinition def = definitions.get(id.toLowerCase(Locale.ROOT));
        if (def == null) {
            return;
        }
        profile(target).enchantLevel(def.id(), Math.max(0, Math.min(def.maxLevel(), level)));
    }

    public void setToolLevel(Player target, int level) {
        profile(target).toolLevel(Math.max(1, level));
    }

    public void addToolXp(Player target, BigInteger amount) {
        profile(target).addToolXp(amount.max(BigInteger.ZERO));
    }

    public BigInteger priceFor(EnchantDefinition def, int level) {
        return scale(def.basePrice(), Math.pow(def.priceGrowth(), Math.max(0, level - 1))).max(BigInteger.ONE);
    }

    public double activationChance(Player player, EnchantDefinition def) {
        int level = enchantLevel(player, def.id());
        if (level <= 0) {
            return 0.0D;
        }
        double chance = def.baseChance() + (def.chancePerLevel() * level);
        return Math.max(0.0D, Math.min(maxActivationChance, chance));
    }

    public double attackSpeedMultiplier(Player player) {
        EnchantDefinition speed = definitions.get("speed_enchant");
        if (speed == null) {
            return 1.0D;
        }
        int level = enchantLevel(player, speed.id());
        return 1.0D + (speed.passiveMultiplier() * level);
    }

    public EnchantHitResult computeHit(Player player, LivingEntity target, BigInteger swordDamage) {
        BigInteger extraDamage = BigInteger.ZERO;
        List<EnchantDefinition> triggered = new ArrayList<>();

        for (EnchantDefinition def : definitions.values()) {
            int level = enchantLevel(player, def.id());
            if (level <= 0 || !enchantEnabled(player, def.id())) {
                continue;
            }

            double chance = activationChance(player, def);
            if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble() > chance) {
                continue;
            }

            triggered.add(def);
            if (def.damageMultiplier() > 0.0D) {
                extraDamage = extraDamage.add(scale(swordDamage, def.damageMultiplier()));
            }
        }

        return new EnchantHitResult(swordDamage.add(extraDamage), triggered);
    }

    public void applyPostHit(Player player,
                             LivingEntity mainTarget,
                             BigInteger swordDamage,
                             EnchantHitResult hit,
                             DungeonService dungeonService,
                             DamageIndicatorService indicatorService) {
        if (hit.triggered().isEmpty()) {
            return;
        }

        for (EnchantDefinition def : hit.triggered()) {
            applyCurrencyBonus(player, def, dungeonService);

            if (def.hitsAllZoneMobs()) {
                BigInteger zoneDamage = scale(swordDamage, Math.max(0.1D, def.damageMultiplier() <= 0.0D ? 1.0D : def.damageMultiplier()));
                dungeonService.damageOwnedMobs(player, zoneDamage, mainTarget.getUniqueId(), indicatorService);
            }

            if (def.executeEffect()) {
                dungeonService.executeDamage(player, mainTarget, def.damageMultiplier(), indicatorService);
            }

            if (def.fireEffect()) {
                applyDotTicks(player, mainTarget, swordDamage, dungeonService, indicatorService, def.fireTickMultiplier(), def.dotTicks(), def.fireTicks(), true);
            }

            if (def.freezeEffect()) {
                applyDotTicks(player, mainTarget, swordDamage, dungeonService, indicatorService, def.freezeTickMultiplier(), def.dotTicks(), def.freezeTicks(), false);
            }

            if (def.phantomEffect()) {
                applyPhantomStrike(player, swordDamage, def.phantomCount(), def.phantomHitMultiplier(), dungeonService, indicatorService);
            }

            if (def.lightningEffect()) {
                applyLightningStrike(player, swordDamage, def.lightningHitMultiplier(), dungeonService, indicatorService);
            }

            if (enchantMessageEnabled(player, def.id())) {
                player.sendMessage(ChatColor.GOLD + "Enchants " + ChatColor.DARK_GRAY + "> " + ChatColor.AQUA + def.displayName() + ChatColor.GRAY + " activated!");
            }
        }
    }

    public List<String> enchantIds() {
        return definitions.keySet().stream().sorted().toList();
    }

    private void applyCurrencyBonus(Player player, EnchantDefinition def, DungeonService dungeonService) {
        if (def.bonusAmount().compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        BigInteger bonus = def.bonusAmount();
        for (EnchantDefinition enhancer : definitions.values()) {
            if (enhancer.bonusTargetEnchant() == null || enhancer.bonusTargetEnchant().isBlank()) {
                continue;
            }
            if (!enhancer.bonusTargetEnchant().equalsIgnoreCase(def.id())) {
                continue;
            }
            int lvl = enchantLevel(player, enhancer.id());
            if (lvl <= 0 || !enchantEnabled(player, enhancer.id())) {
                continue;
            }
            bonus = scale(bonus, 1.0D + (enhancer.passiveMultiplier() * lvl));
        }

        dungeonService.addCurrency(player, def.bonusCurrency(), bonus);
    }

    private void applyDotTicks(Player player,
                               LivingEntity target,
                               BigInteger swordDamage,
                               DungeonService dungeonService,
                               DamageIndicatorService indicatorService,
                               double tickMultiplier,
                               int ticks,
                               int statusTicks,
                               boolean fire) {
        if (!target.isValid()) {
            return;
        }

        if (fire) {
            target.setFireTicks(Math.max(target.getFireTicks(), statusTicks));
        } else {
            target.setFreezeTicks(Math.max(target.getFreezeTicks(), statusTicks));
        }

        BigInteger perTickDamage = scale(swordDamage, tickMultiplier);
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid() || tick >= ticks) {
                    cancel();
                    return;
                }

                DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, target, perTickDamage);
                if (!result.accepted()) {
                    cancel();
                    return;
                }

                indicatorService.spawnDamage(player, target, perTickDamage);
                if (result.dead()) {
                    cancel();
                }
                tick++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void applyPhantomStrike(Player player,
                                    BigInteger swordDamage,
                                    int count,
                                    double hitMultiplier,
                                    DungeonService dungeonService,
                                    DamageIndicatorService indicatorService) {
        List<LivingEntity> mobs = dungeonService.activeOwnedMobs(player);
        if (mobs.isEmpty()) {
            return;
        }

        BigInteger dmg = scale(swordDamage, hitMultiplier);
        int hits = Math.max(1, count);
        for (int i = 0; i < hits; i++) {
            LivingEntity target = mobs.get(ThreadLocalRandom.current().nextInt(mobs.size()));
            if (!target.isValid()) {
                continue;
            }

            spawnPhantomTrail(player, target);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || !target.isValid()) {
                        return;
                    }
                    DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, target, dmg);
                    if (result.accepted()) {
                        indicatorService.spawnDamage(player, target, dmg);
                    }
                }
            }.runTaskLater(plugin, 12L);
        }
    }

    private void applyLightningStrike(Player player,
                                      BigInteger swordDamage,
                                      double hitMultiplier,
                                      DungeonService dungeonService,
                                      DamageIndicatorService indicatorService) {
        BigInteger dmg = scale(swordDamage, hitMultiplier);
        for (LivingEntity mob : dungeonService.activeOwnedMobs(player)) {
            if (!mob.isValid()) {
                continue;
            }
            if (mob.getWorld() != null) {
                mob.getWorld().strikeLightningEffect(mob.getLocation());
            }
            DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, mob, dmg);
            if (result.accepted()) {
                indicatorService.spawnDamage(player, mob, dmg);
            }
        }
    }

    private void spawnPhantomTrail(Player player, LivingEntity target) {
        Vector from = player.getEyeLocation().toVector();
        Vector to = target.getLocation().add(0, 0.6, 0).toVector();
        Vector step = to.clone().subtract(from).multiply(1.0 / 10.0);
        for (int i = 0; i <= 10; i++) {
            Vector point = from.clone().add(step.clone().multiply(i));
            player.spawnParticle(Particle.SOUL_FIRE_FLAME, point.getX(), point.getY(), point.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private BigInteger toolXpRequiredNextRaw(int level) {
        return scale(xpBase, Math.pow(xpGrowth, Math.max(0, level - 1))).max(BigInteger.ONE);
    }

    private BigInteger scale(BigInteger value, double multiplier) {
        if (multiplier <= 0.0D || value.compareTo(BigInteger.ZERO) <= 0) {
            return BigInteger.ZERO;
        }
        BigDecimal decimal = new BigDecimal(value);
        BigDecimal scaled = decimal.multiply(BigDecimal.valueOf(multiplier));
        return scaled.setScale(0, RoundingMode.DOWN).toBigInteger().max(BigInteger.ZERO);
    }

    public record EnchantHitResult(BigInteger totalDamage, List<EnchantDefinition> triggered) {
    }

    private PlayerProfile profile(Player player) {
        return profileService.profile(player.getUniqueId());
    }
}
