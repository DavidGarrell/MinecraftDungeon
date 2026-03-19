package de.fleaqx.minecraftDungeons.enchant;

import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.enchant.effect.*;
import de.fleaqx.minecraftDungeons.profile.PlayerProfile;
import de.fleaqx.minecraftDungeons.profile.ProfileService;
import de.fleaqx.minecraftDungeons.runtime.DamageIndicatorService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import de.fleaqx.minecraftDungeons.sword.SwordPerkService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
    private final Map<String, EnchantEffect> effects = new HashMap<>();
    private BigInteger xpPerHit = BigInteger.ONE;
    private BigInteger xpBase = BigInteger.valueOf(100L);
    private double xpGrowth = 1.15D;
    private double maxActivationChance = 1.0D;
    private SwordPerkService swordPerkService;

    public EnchantService(JavaPlugin plugin, EnchantConfigService configService, ProfileService profileService) {
        this.plugin = plugin;
        this.configService = configService;
        this.profileService = profileService;
        bootstrapEffects();
    }

    public void reload() {
        definitions.clear();
        definitions.putAll(configService.load());
        xpPerHit = configService.toolXpPerHit();
        xpBase = configService.toolXpBaseRequirement();
        xpGrowth = configService.toolXpGrowth();
        maxActivationChance = configService.maxActivationChance();
    }

    public void setSwordPerkService(SwordPerkService swordPerkService) {
        this.swordPerkService = swordPerkService;
    }

    public List<EnchantDefinition> byCategory(EnchantCategory category) {
        return definitions.values().stream()
                .filter(def -> def.category() == category)
                .sorted(Comparator.comparingInt(EnchantDefinition::requiredToolLevel)
                        .thenComparing(EnchantDefinition::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(EnchantDefinition::id))
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
        return scaleDamage(xpBase, Math.pow(xpGrowth, Math.max(0, level - 1))).max(BigInteger.ONE);
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
        BigInteger gain = xpPerHit;
        if (swordPerkService != null) {
            gain = scaleDamage(gain, swordPerkService.swordXpMultiplier(player));
        }
        profile.addToolXp(gain);
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
        return scaleDamage(def.basePrice(), Math.pow(def.priceGrowth(), Math.max(0, level - 1))).max(BigInteger.ONE);
    }

    public double activationChance(Player player, EnchantDefinition def) {
        int level = enchantLevel(player, def.id());
        if (level <= 0) {
            return 0.0D;
        }
        double chance = def.baseChance() + (def.chancePerLevel() * level);
        if (swordPerkService != null) {
            chance *= swordPerkService.enchantProcMultiplier(player);
        }
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
            EnchantEffect effect = effectFor(def.id());
            extraDamage = extraDamage.add(effect.extraDamage(player, target, swordDamage, def, this));
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
            EnchantEffect effect = effectFor(def.id());
            effect.applyPostHit(player, mainTarget, swordDamage, def, this, dungeonService, indicatorService);
            grantBonusSoulsFromSoulGreed(player, mainTarget, def, dungeonService);

            if (enchantMessageEnabled(player, def.id())) {
                player.sendMessage(ChatColor.GOLD + "Enchants " + ChatColor.DARK_GRAY + "> " + ChatColor.AQUA + def.displayName() + ChatColor.GRAY + " activated!");
            }
        }
    }

    public List<String> enchantIds() {
        return definitions.keySet().stream().sorted().toList();
    }

    private EnchantEffect effectFor(String enchantId) {
        return effects.getOrDefault(enchantId.toLowerCase(Locale.ROOT), new BaseEnchantEffect(enchantId.toLowerCase(Locale.ROOT)) {});
    }

    private void bootstrapEffects() {
        registerEffect(new CriticalEnchantEffect());
        registerEffect(new BlazedEnchantEffect());
        registerEffect(new FrostbiteEnchantEffect());
        registerEffect(new PhantomStrikeEnchantEffect());
        registerEffect(new MiniWitherEnchantEffect());
        registerEffect(new ThorEnchantEffect());
        registerEffect(new DragonBurstEnchantEffect());
        registerEffect(new ExecuteEnchantEffect());
        registerEffect(new SoulFinderEnchantEffect());
        registerEffect(new SoulMagnetEnchantEffect());
        registerEffect(new EssenceFinderEnchantEffect());
        registerEffect(new EssenceMagnetEnchantEffect());
        registerEffect(new SpeedEnchantEffect());
    }

    private void registerEffect(EnchantEffect effect) {
        effects.put(effect.enchantId().toLowerCase(Locale.ROOT), effect);
    }

    public void applyCurrencyBonus(Player player, EnchantDefinition def, DungeonService dungeonService) {
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
            bonus = scaleDamage(bonus, 1.0D + (enhancer.passiveMultiplier() * lvl));
        }
        dungeonService.addCurrency(player, def.bonusCurrency(), bonus);
    }

    public void applyDotTicks(Player player,
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

        BigInteger perTickDamage = scaleDamage(swordDamage, tickMultiplier);
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

    public void applyPhantomStrike(Player player,
                                    BigInteger swordDamage,
                                    int count,
                                    double hitMultiplier,
                                    DungeonService dungeonService,
                                    DamageIndicatorService indicatorService) {
        List<LivingEntity> mobs = dungeonService.activeCombatOwnedMobs(player);
        if (mobs.isEmpty()) {
            return;
        }

        BigInteger damage = scaleDamage(swordDamage, hitMultiplier);
        int hits = Math.max(1, count);
        for (int i = 0; i < hits; i++) {
            LivingEntity target = mobs.get(ThreadLocalRandom.current().nextInt(mobs.size()));
            if (!target.isValid()) {
                continue;
            }

            Location start = phantomStart(player, i, hits);
            launchPacketProjectile(player, start, target, damage, dungeonService, indicatorService,
                    Particle.SOUL_FIRE_FLAME, Particle.CLOUD, Sound.ENTITY_PHANTOM_FLAP, Sound.ENTITY_PHANTOM_BITE, 12, 0.85D);
        }
    }

    public void applyMiniWitherBarrage(Player player,
                                       BigInteger swordDamage,
                                       int count,
                                       double hitMultiplier,
                                       DungeonService dungeonService,
                                       DamageIndicatorService indicatorService) {
        List<LivingEntity> mobs = dungeonService.activeCombatOwnedMobs(player);
        if (mobs.isEmpty()) {
            return;
        }

        BigInteger damage = scaleDamage(swordDamage, hitMultiplier);
        int shots = Math.max(1, count);
        for (int i = 0; i < shots; i++) {
            LivingEntity target = mobs.get(i % mobs.size());
            if (!target.isValid()) {
                continue;
            }

            Location start = witherStart(player, i, shots);
            player.spawnParticle(Particle.SMOKE, start, 8, 0.12D, 0.12D, 0.12D, 0.0D);
            player.spawnParticle(Particle.ENCHANT, start, 4, 0.04D, 0.04D, 0.04D, 0.0D);
            launchPacketProjectile(player, start, target, damage, dungeonService, indicatorService,
                    Particle.SMOKE, Particle.ENCHANT, Sound.ENTITY_WITHER_SHOOT, Sound.ENTITY_WITHER_HURT, 10, 0.25D);
        }
    }

    public void applyLightningStrike(Player player,
                                      BigInteger swordDamage,
                                      double hitMultiplier,
                                      DungeonService dungeonService,
                                      DamageIndicatorService indicatorService) {
        BigInteger dmg = scaleDamage(swordDamage, hitMultiplier);
        for (LivingEntity mob : dungeonService.activeCombatOwnedMobs(player)) {
            if (!mob.isValid()) {
                continue;
            }
            player.spawnParticle(Particle.ELECTRIC_SPARK, mob.getLocation().add(0, 1.0, 0), 24, 0.25, 0.45, 0.25, 0.03);
            player.playSound(mob.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6F, 1.4F);
            DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, mob, dmg);
            if (result.accepted()) {
                indicatorService.spawnDamage(player, mob, dmg);
            }
        }
    }

    public BigInteger soulGreedReward(Player player) {
        BigInteger reward = BigInteger.valueOf(120L);
        int soulGreedLevel = enchantLevel(player, "soul_greed");
        if (soulGreedLevel <= 0 || !enchantEnabled(player, "soul_greed")) {
            return BigInteger.ZERO;
        }

        int soulMagnetLevel = enchantLevel(player, "soul_magnet");
        if (soulMagnetLevel > 0 && enchantEnabled(player, "soul_magnet")) {
            reward = scaleDamage(reward, 1.0D + (0.0002D * soulMagnetLevel));
        }
        return reward;
    }

    private void grantBonusSoulsFromSoulGreed(Player player, LivingEntity target, EnchantDefinition triggeredDef, DungeonService dungeonService) {
        if ("soul_greed".equalsIgnoreCase(triggeredDef.id())) {
            return;
        }

        BigInteger soulGreedReward = soulGreedReward(player);
        if (soulGreedReward.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        BigInteger bonusSouls = scaleDamage(soulGreedReward, 0.10D).max(BigInteger.ONE);
        dungeonService.addCurrency(player, CurrencyType.SOULS, bonusSouls);
        player.spawnParticle(Particle.ENCHANT, target.getLocation().add(0, 1.1D, 0), 8, 0.18D, 0.18D, 0.18D, 0.2D);
    }

    private Location phantomStart(Player player, int index, int total) {
        double angle = (Math.PI * 2.0D * index) / Math.max(1, total);
        Location base = player.getEyeLocation().clone();
        return base.add(Math.cos(angle) * 0.75D, 0.2D, Math.sin(angle) * 0.75D);
    }

    private Location witherStart(Player player, int index, int total) {
        double angle = (Math.PI * 2.0D * index) / Math.max(1, total);
        Location base = player.getEyeLocation().clone();
        return base.add(Math.cos(angle) * 1.25D, 0.45D, Math.sin(angle) * 1.25D);
    }

    private void launchPacketProjectile(Player player,
                                        Location start,
                                        LivingEntity target,
                                        BigInteger damage,
                                        DungeonService dungeonService,
                                        DamageIndicatorService indicatorService,
                                        Particle coreParticle,
                                        Particle trailParticle,
                                        Sound launchSound,
                                        Sound impactSound,
                                        int steps,
                                        double arcHeight) {
        if (start == null || start.getWorld() == null || damage.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        Vector from = start.toVector();
        player.playSound(start, launchSound, 0.5F, 1.5F);
        new BukkitRunnable() {
            private int step = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid()) {
                    cancel();
                    return;
                }

                Vector to = target.getLocation().add(0, 0.65D, 0).toVector();
                double progress = Math.min(1.0D, step / (double) Math.max(1, steps));
                Vector point = from.clone().multiply(1.0D - progress).add(to.clone().multiply(progress));
                point.setY(point.getY() + Math.sin(progress * Math.PI) * arcHeight);

                player.spawnParticle(coreParticle, point.getX(), point.getY(), point.getZ(), 5, 0.08D, 0.08D, 0.08D, 0.0D);
                player.spawnParticle(trailParticle, point.getX(), point.getY(), point.getZ(), 2, 0.03D, 0.03D, 0.03D, 0.0D);

                if (step++ >= steps) {
                    player.playSound(target.getLocation(), impactSound, 0.45F, 1.7F);
                    DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, target, damage);
                    if (result.accepted()) {
                        indicatorService.spawnDamage(player, target, damage);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private BigInteger toolXpRequiredNextRaw(int level) {
        return scaleDamage(xpBase, Math.pow(xpGrowth, Math.max(0, level - 1))).max(BigInteger.ONE);
    }

    public BigInteger scaleDamage(BigInteger value, double multiplier) {
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
