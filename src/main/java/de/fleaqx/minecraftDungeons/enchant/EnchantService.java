package de.fleaqx.minecraftDungeons.enchant;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.enchant.effect.*;
import de.fleaqx.minecraftDungeons.profile.PlayerProfile;
import de.fleaqx.minecraftDungeons.profile.ProfileService;
import de.fleaqx.minecraftDungeons.runtime.DamageIndicatorService;
import de.fleaqx.minecraftDungeons.runtime.DamageIndicatorService.Style;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import de.fleaqx.minecraftDungeons.sword.SwordPerkService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final AtomicInteger packetEntityIds = new AtomicInteger(2_000_000);
    private final boolean protocolLibAvailable;
    private final ProtocolManager protocolManager;
    private final Map<UUID, BukkitRunnable> orbitingAuraTasks = new HashMap<>();

    public EnchantService(JavaPlugin plugin, EnchantConfigService configService, ProfileService profileService) {
        this.plugin = plugin;
        this.configService = configService;
        this.profileService = profileService;
        ProtocolManager manager = null;
        boolean protocolAvailable = false;
        try {
            protocolAvailable = plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib");
            if (protocolAvailable) {
                manager = ProtocolLibrary.getProtocolManager();
            }
        } catch (Throwable ignored) {
            protocolAvailable = false;
        }
        this.protocolLibAvailable = protocolAvailable && manager != null;
        this.protocolManager = manager;
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
        registerEffect(new ChainLightningEnchantEffect());
        registerEffect(new MarkOfDeathEnchantEffect());
        registerEffect(new InfernoEnchantEffect());
        registerEffect(new PhantomStrikeEnchantEffect());
        registerEffect(new MiniWitherEnchantEffect());
        registerEffect(new ThorEnchantEffect());
        registerEffect(new DragonBurstEnchantEffect());
        registerEffect(new ExecuteEnchantEffect());
        registerEffect(new SoulCloneEnchantEffect());
        registerEffect(new SoulFinderEnchantEffect());
        registerEffect(new SoulMagnetEnchantEffect());
        registerEffect(new SoulStormEnchantEffect());
        registerEffect(new EssenceFinderEnchantEffect());
        registerEffect(new EssenceMagnetEnchantEffect());
        registerEffect(new SpeedEnchantEffect());
        registerEffect(new SoulRingEnchantEffect());
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
                               EnchantDefinition definition,
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

                indicatorService.spawnDamage(player, target, perTickDamage, indicatorStyle(definition));
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
                                    EnchantDefinition definition,
                                    DungeonService dungeonService,
                                    DamageIndicatorService indicatorService) {
        List<LivingEntity> mobs = dungeonService.activeAttackableOwnedMobs(player);
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
            spawnPhantomStrikeMob(player, start, target, damage, definition, dungeonService, indicatorService);
        }
    }

    public void applyMiniWitherBarrage(Player player,
                                       BigInteger swordDamage,
                                       int count,
                                       double hitMultiplier,
                                       EnchantDefinition definition,
                                       DungeonService dungeonService,
                                       DamageIndicatorService indicatorService) {
        List<LivingEntity> mobs = dungeonService.activeAttackableOwnedMobs(player);
        if (mobs.isEmpty()) {
            return;
        }

        BigInteger damage = scaleDamage(swordDamage, hitMultiplier);
        if (damage.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        int shots = Math.max(1, count);
        for (int i = 0; i < shots; i++) {
            Location start = witherStart(player, i, shots);
            spawnMiniWitherMob(player, start, damage, definition, dungeonService, indicatorService, i, shots);
        }
    }

    public void startSoulRing(Player player,
                              BigInteger swordDamage,
                              double hitMultiplier,
                              EnchantDefinition definition,
                              DungeonService dungeonService,
                              DamageIndicatorService indicatorService) {
        cancelSoulRing(player.getUniqueId());

        BigInteger damage = scaleDamage(swordDamage, hitMultiplier);
        if (damage.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        BukkitRunnable task = new BukkitRunnable() {
            private static final int DURATION_TICKS = 200;
            private static final int ORBIT_POINTS = 4;
            private static final double RING_RADIUS = 2.15D;
            private static final double RING_THICKNESS = 1.05D;
            private static final double VERTICAL_RANGE = 1.45D;
            private static final int MOB_HIT_COOLDOWN = 4;

            private final Map<UUID, Integer> hitCooldowns = new HashMap<>();
            private int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelSoulRing(player.getUniqueId());
                    return;
                }

                if (tick >= DURATION_TICKS) {
                    player.spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.35D, 0.45D, 0.35D, 0.25D);
                    cancelSoulRing(player.getUniqueId());
                    return;
                }

                hitCooldowns.replaceAll((mobId, cooldown) -> cooldown - 1);
                hitCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);

                for (int orbitIndex = 0; orbitIndex < ORBIT_POINTS; orbitIndex++) {
                    Location orbitLocation = soulRingOrbit(player, orbitIndex, ORBIT_POINTS, tick);
                    player.spawnParticle(Particle.SOUL_FIRE_FLAME, orbitLocation, 4, 0.06D, 0.06D, 0.06D, 0.0D);
                    player.spawnParticle(Particle.ENCHANT, orbitLocation, 3, 0.02D, 0.02D, 0.02D, 0.0D);
                }

                Location ringCenter = player.getLocation().clone().add(0.0D, 1.18D, 0.0D);
                for (LivingEntity target : dungeonService.activeAttackableOwnedMobs(player)) {
                    if (!target.isValid() || hitCooldowns.containsKey(target.getUniqueId())) {
                        continue;
                    }
                    if (!isInsideSoulRing(target, ringCenter, RING_RADIUS, RING_THICKNESS, VERTICAL_RANGE)) {
                        continue;
                    }

                    DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, target, damage);
                    if (result.accepted()) {
                        indicatorService.spawnDamage(player, target, damage, indicatorStyle(definition));
                        player.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35F, 1.85F);
                        player.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0.0D, 0.9D, 0.0D), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                        hitCooldowns.put(target.getUniqueId(), MOB_HIT_COOLDOWN);
                    }
                }

                tick++;
            }
        };

        orbitingAuraTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 1L);
    }

    public void applyLightningStrike(Player player,
                                      BigInteger swordDamage,
                                      double hitMultiplier,
                                      EnchantDefinition definition,
                                      DungeonService dungeonService,
                                      DamageIndicatorService indicatorService) {
        BigInteger dmg = scaleDamage(swordDamage, hitMultiplier);
        for (LivingEntity mob : dungeonService.activeAttackableOwnedMobs(player)) {
            if (!mob.isValid()) {
                continue;
            }
            player.spawnParticle(Particle.ELECTRIC_SPARK, mob.getLocation().add(0, 1.0, 0), 24, 0.25, 0.45, 0.25, 0.03);
            player.playSound(mob.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6F, 1.4F);
            DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, mob, dmg);
            if (result.accepted()) {
                indicatorService.spawnDamage(player, mob, dmg, indicatorStyle(definition));
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

    public boolean protocolLibAvailable() {
        return protocolLibAvailable;
    }


    public Style primaryHitIndicatorStyle(EnchantHitResult hit) {
        if (hit == null || hit.triggered() == null) {
            return null;
        }

        for (EnchantDefinition definition : hit.triggered()) {
            if ("critical_enchant".equalsIgnoreCase(definition.id())) {
                return indicatorStyle(definition);
            }
        }
        return null;
    }

    public Style indicatorStyle(EnchantDefinition definition) {
        if (definition == null) {
            return null;
        }

        return switch (definition.id().toLowerCase(Locale.ROOT)) {
            case "critical_enchant" -> new Style(ChatColor.GOLD, "✦", true);
            case "blazed_enchant" -> new Style(ChatColor.GOLD, "✹", true);
            case "frostbite" -> new Style(ChatColor.AQUA, "❄", true);
            case "chain_lightning" -> new Style(ChatColor.YELLOW, "↯", true);
            case "mark_of_death" -> new Style(ChatColor.DARK_RED, "☬", true);
            case "inferno" -> new Style(ChatColor.RED, "✹", true);
            case "phantom_strike" -> new Style(ChatColor.DARK_AQUA, "✧", true);
            case "mini_wither" -> new Style(ChatColor.DARK_GRAY, "☠", true);
            case "thor" -> new Style(ChatColor.YELLOW, "⚡", true);
            case "dragon_burst" -> new Style(ChatColor.LIGHT_PURPLE, "☄", true);
            case "execute" -> new Style(ChatColor.DARK_RED, "✖", true);
            case "soul_clone" -> new Style(ChatColor.LIGHT_PURPLE, "✥", true);
            case "soul_ring" -> new Style(ChatColor.LIGHT_PURPLE, "◎", true);
            case "soul_greed" -> new Style(ChatColor.DARK_AQUA, "✦", false);
            case "soul_storm" -> new Style(ChatColor.DARK_AQUA, "✺", true);
            case "essence_finder" -> new Style(ChatColor.BLUE, "✧", false);
            default -> null;
        };
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

    private void spawnPhantomStrikeMob(Player player,
                                       Location start,
                                       LivingEntity target,
                                       BigInteger damage,
                                       EnchantDefinition definition,
                                       DungeonService dungeonService,
                                       DamageIndicatorService indicatorService) {
        if (start == null || start.getWorld() == null || damage.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        Entity spawned = start.getWorld().spawnEntity(start, EntityType.PHANTOM);
        if (!(spawned instanceof LivingEntity phantom)) {
            spawned.remove();
            return;
        }

        configureSummonedVisualMob(phantom, 0.6D);
        player.playSound(start, Sound.ENTITY_PHANTOM_FLAP, 0.5F, 1.5F);

        new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !phantom.isValid() || !target.isValid()) {
                    removeEntity(phantom);
                    cancel();
                    return;
                }

                Location current = phantom.getLocation();
                Vector direction = target.getLocation().add(0.0D, 0.8D, 0.0D).toVector().subtract(current.toVector());
                double distance = direction.length();
                if (distance <= 0.7D || tick >= 20) {
                    player.playSound(target.getLocation(), Sound.ENTITY_PHANTOM_BITE, 0.45F, 1.7F);
                    player.spawnParticle(Particle.CLOUD, target.getLocation().add(0, 0.8D, 0), 12, 0.18D, 0.18D, 0.18D, 0.01D);
                    DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, target, damage);
                    if (result.accepted()) {
                        indicatorService.spawnDamage(player, target, damage, indicatorStyle(definition));
                    }
                    removeEntity(phantom);
                    cancel();
                    return;
                }

                Vector movement = direction.normalize().multiply(Math.min(1.15D, distance));
                Location next = current.clone().add(movement);
                next.setDirection(movement);
                phantom.teleport(next);
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, next, 5, 0.08D, 0.08D, 0.08D, 0.0D);
                player.spawnParticle(Particle.CLOUD, next, 2, 0.03D, 0.03D, 0.03D, 0.0D);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnMiniWitherMob(Player player,
                                    Location start,
                                    BigInteger damage,
                                    EnchantDefinition definition,
                                    DungeonService dungeonService,
                                    DamageIndicatorService indicatorService,
                                    int index,
                                    int total) {
        if (start == null || start.getWorld() == null || damage.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }

        Entity spawned = start.getWorld().spawnEntity(start, EntityType.WITHER);
        if (!(spawned instanceof LivingEntity wither)) {
            spawned.remove();
            return;
        }

        configureSummonedVisualMob(wither, 0.35D);
        suppressWitherBossBar(wither);
        disableWitherSpawnState(wither);
        player.spawnParticle(Particle.SMOKE, start, 8, 0.12D, 0.12D, 0.12D, 0.0D);
        player.spawnParticle(Particle.ENCHANT, start, 4, 0.04D, 0.04D, 0.04D, 0.0D);

        int lifetimeTicks = 200;
        int fireTick = Math.min(180, 20 + (index * Math.max(1, 160 / Math.max(1, total))));
        new BukkitRunnable() {
            private int tick = 0;
            private boolean fired = false;

            @Override
            public void run() {
                if (!player.isOnline() || !wither.isValid()) {
                    removeEntity(wither);
                    cancel();
                    return;
                }

                if (tick >= lifetimeTicks) {
                    player.spawnParticle(Particle.SMOKE, wither.getLocation(), 12, 0.15D, 0.15D, 0.15D, 0.01D);
                    removeEntity(wither);
                    cancel();
                    return;
                }

                List<LivingEntity> activeTargets = dungeonService.activeAttackableOwnedMobs(player).stream()
                        .filter(LivingEntity::isValid)
                        .toList();

                Location orbit = witherOrbit(player, index, total, tick);
                if (!activeTargets.isEmpty()) {
                    LivingEntity lookTarget = activeTargets.get((index + (tick / 20)) % activeTargets.size());
                    Vector lookDirection = lookTarget.getLocation().add(0.0D, 0.8D, 0.0D).toVector().subtract(orbit.toVector());
                    if (lookDirection.lengthSquared() > 0.0001D) {
                        orbit.setDirection(lookDirection);
                    }
                }
                wither.teleport(orbit);
                player.spawnParticle(Particle.SMOKE, orbit, 2, 0.05D, 0.05D, 0.05D, 0.0D);

                if (!fired && tick >= fireTick && !activeTargets.isEmpty()) {
                    LivingEntity target = chooseWitherTarget(activeTargets, orbit, null);
                    launchPacketProjectile(player, wither.getLocation().clone().add(0.0D, 0.65D, 0.0D), target, damage,
                            dungeonService, indicatorService, Particle.SMOKE, Particle.ENCHANT, Sound.ENTITY_WITHER_SHOOT,
                            Sound.ENTITY_WITHER_HURT, 10, 0.25D, indicatorStyle(definition));
                    fired = true;
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnPacketMiniWitherMob(Player player,
                                          Location start,
                                          BigInteger damage,
                                          EnchantDefinition definition,
                                          DungeonService dungeonService,
                                          DamageIndicatorService indicatorService,
                                          int index,
                                          int total) {
        int entityId = packetEntityIds.incrementAndGet();
        UUID uuid = UUID.randomUUID();
        if (!sendSpawnEntityPacket(player, entityId, uuid, EntityType.WITHER, start)) {
            return;
        }

        player.spawnParticle(Particle.SMOKE, start, 8, 0.12D, 0.12D, 0.12D, 0.0D);
        player.spawnParticle(Particle.ENCHANT, start, 4, 0.04D, 0.04D, 0.04D, 0.0D);

        new BukkitRunnable() {
            private static final int LIFETIME_TICKS = 70;
            private static final int FIRE_INTERVAL = 14;

            private int tick = 0;
            private int lastFireTick = -FIRE_INTERVAL;
            private UUID currentTargetId;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    sendDestroyEntityPacket(player, entityId);
                    cancel();
                    return;
                }

                if (tick >= LIFETIME_TICKS) {
                    player.spawnParticle(Particle.SMOKE, witherOrbit(player, index, total, tick), 12, 0.15D, 0.15D, 0.15D, 0.01D);
                    sendDestroyEntityPacket(player, entityId);
                    cancel();
                    return;
                }

                Location orbit = witherOrbit(player, index, total, tick);
                List<LivingEntity> activeTargets = dungeonService.activeAttackableOwnedMobs(player).stream()
                        .filter(LivingEntity::isValid)
                        .toList();
                LivingEntity chosenTarget = chooseWitherTarget(activeTargets, orbit, currentTargetId);
                if (chosenTarget != null) {
                    currentTargetId = chosenTarget.getUniqueId();
                    Vector lookDirection = chosenTarget.getLocation().add(0.0D, 0.8D, 0.0D).toVector().subtract(orbit.toVector());
                    if (lookDirection.lengthSquared() > 0.0001D) {
                        orbit.setDirection(lookDirection);
                    }
                }

                if (!sendTeleportEntityPacket(player, entityId, orbit)) {
                    player.spawnParticle(Particle.SMOKE, orbit, 4, 0.08D, 0.08D, 0.08D, 0.0D);
                }
                player.spawnParticle(Particle.SMOKE, orbit, 2, 0.05D, 0.05D, 0.05D, 0.0D);

                if (chosenTarget != null && tick - lastFireTick >= FIRE_INTERVAL) {
                    lastFireTick = tick;
                    launchPacketProjectile(player, orbit.clone().add(0.0D, 0.65D, 0.0D), chosenTarget, damage,
                            dungeonService, indicatorService, Particle.SMOKE, Particle.ENCHANT, Sound.ENTITY_WITHER_SHOOT,
                            Sound.ENTITY_WITHER_HURT, 10, 0.25D, indicatorStyle(definition));
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private LivingEntity chooseWitherTarget(List<LivingEntity> activeTargets, Location origin, UUID preferredTargetId) {
        if (activeTargets == null || activeTargets.isEmpty() || origin == null) {
            return null;
        }

        if (preferredTargetId != null) {
            for (LivingEntity activeTarget : activeTargets) {
                if (preferredTargetId.equals(activeTarget.getUniqueId()) && activeTarget.isValid()) {
                    return activeTarget;
                }
            }
        }

        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (LivingEntity activeTarget : activeTargets) {
            double distance = activeTarget.getLocation().distanceSquared(origin);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = activeTarget;
            }
        }
        return closest;
    }

    private Location witherOrbit(Player player, int index, int total, int tick) {
        double baseAngle = (Math.PI * 2.0D * index) / Math.max(1, total);
        double angle = baseAngle + (tick * 0.08D);
        Location base = player.getEyeLocation().clone();
        return base.add(Math.cos(angle) * 1.8D, 0.7D + (Math.sin((tick + index * 7.0D) * 0.12D) * 0.18D), Math.sin(angle) * 1.8D);
    }

    private Location soulRingOrbit(Player player, int index, int total, int tick) {
        double baseAngle = (Math.PI * 2.0D * index) / Math.max(1, total);
        double angle = baseAngle + (tick * 0.34D);
        Location base = player.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        return base.add(Math.cos(angle) * 2.15D, 0.18D + (Math.sin((tick * 0.22D) + index) * 0.18D), Math.sin(angle) * 2.15D);
    }

    private boolean isInsideSoulRing(LivingEntity target,
                                     Location ringCenter,
                                     double ringRadius,
                                     double ringThickness,
                                     double verticalRange) {
        if (target == null || ringCenter == null || !target.getWorld().getUID().equals(ringCenter.getWorld().getUID())) {
            return false;
        }

        Location targetLocation = target.getLocation().clone().add(0.0D, 0.9D, 0.0D);
        double dx = targetLocation.getX() - ringCenter.getX();
        double dz = targetLocation.getZ() - ringCenter.getZ();
        double horizontalDistance = Math.sqrt((dx * dx) + (dz * dz));
        double verticalDistance = Math.abs(targetLocation.getY() - ringCenter.getY());
        return horizontalDistance >= Math.max(0.0D, ringRadius - ringThickness)
                && horizontalDistance <= ringRadius + ringThickness
                && verticalDistance <= verticalRange;
    }

    private void configureSummonedVisualMob(LivingEntity entity, double scale) {
        entity.setCanPickupItems(false);
        entity.setPersistent(false);
        entity.setRemoveWhenFarAway(false);
        entity.setCollidable(false);
        entity.setInvulnerable(true);
        entity.setAI(false);
        entity.setGravity(false);
        entity.setSilent(true);
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
        }
        if (entity.getAttribute(Attribute.GENERIC_SCALE) != null) {
            entity.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(Math.max(0.1D, scale));
        }
    }

    private void suppressWitherBossBar(LivingEntity entity) {
        try {
            Method getBossBar = entity.getClass().getMethod("getBossBar");
            Object bossBar = getBossBar.invoke(entity);
            if (bossBar != null) {
                Method setVisible = bossBar.getClass().getMethod("setVisible", boolean.class);
                setVisible.invoke(bossBar, false);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void disableWitherSpawnState(LivingEntity entity) {
        try {
            Method setInvulnerabilityTicks = entity.getClass().getMethod("setInvulnerabilityTicks", int.class);
            setInvulnerabilityTicks.invoke(entity, 0);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void removeEntity(Entity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private void cancelSoulRing(UUID playerId) {
        BukkitRunnable activeTask = orbitingAuraTasks.remove(playerId);
        if (activeTask != null) {
            activeTask.cancel();
        }
    }


    private void launchPacketMob(Player player,
                                 Location start,
                                 LivingEntity target,
                                 BigInteger damage,
                                 DungeonService dungeonService,
                                 DamageIndicatorService indicatorService,
                                 EntityType entityType,
                                 Sound launchSound,
                                 Sound impactSound,
                                 int steps,
                                 double arcHeight,
                                 Particle trailParticle,
                                 Particle impactParticle,
                                 Style style) {
        if (!protocolLibAvailable || protocolManager == null || start == null || start.getWorld() == null || damage.compareTo(BigInteger.ZERO) <= 0) {
            launchPacketProjectile(player, start, target, damage, dungeonService, indicatorService, trailParticle, impactParticle, launchSound, impactSound, steps, arcHeight, style);
            return;
        }

        int entityId = packetEntityIds.incrementAndGet();
        UUID uuid = UUID.randomUUID();
        if (!sendSpawnEntityPacket(player, entityId, uuid, entityType, start)) {
            launchPacketProjectile(player, start, target, damage, dungeonService, indicatorService, trailParticle, impactParticle, launchSound, impactSound, steps, arcHeight, style);
            return;
        }
        player.playSound(start, launchSound, 0.5F, 1.5F);

        Vector from = start.toVector();
        new BukkitRunnable() {
            private int step = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid()) {
                    sendDestroyEntityPacket(player, entityId);
                    cancel();
                    return;
                }

                Vector to = target.getLocation().add(0, 0.65D, 0).toVector();
                double progress = Math.min(1.0D, step / (double) Math.max(1, steps));
                Vector point = from.clone().multiply(1.0D - progress).add(to.clone().multiply(progress));
                point.setY(point.getY() + Math.sin(progress * Math.PI) * arcHeight);
                Location location = new Location(start.getWorld(), point.getX(), point.getY(), point.getZ());
                Vector direction = to.clone().subtract(point);
                if (direction.lengthSquared() > 0.0001D) {
                    location.setDirection(direction);
                }

                if (!sendTeleportEntityPacket(player, entityId, location)) {
                    sendDestroyEntityPacket(player, entityId);
                    launchPacketProjectile(player, start, target, damage, dungeonService, indicatorService, trailParticle, impactParticle, launchSound, impactSound, Math.max(1, steps - step), arcHeight, style);
                    cancel();
                    return;
                }
                player.spawnParticle(trailParticle, point.getX(), point.getY(), point.getZ(), 2, 0.05D, 0.05D, 0.05D, 0.0D);

                if (step++ >= steps) {
                    sendDestroyEntityPacket(player, entityId);
                    player.playSound(target.getLocation(), impactSound, 0.45F, 1.7F);
                    player.spawnParticle(impactParticle, target.getLocation().add(0, 0.8D, 0), 12, 0.18D, 0.18D, 0.18D, 0.01D);
                    DungeonService.AttackResult result = dungeonService.onPlayerDamageMob(player, target, damage);
                    if (result.accepted()) {
                        indicatorService.spawnDamage(player, target, damage, style);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean sendSpawnEntityPacket(Player player, int entityId, UUID uuid, EntityType entityType, Location location) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            packet.getIntegers().writeSafely(0, entityId);
            packet.getUUIDs().writeSafely(0, uuid);
            packet.getEntityTypeModifier().writeSafely(0, entityType);
            packet.getDoubles().writeSafely(0, location.getX());
            packet.getDoubles().writeSafely(1, location.getY());
            packet.getDoubles().writeSafely(2, location.getZ());
            packet.getBytes().writeSafely(0, angleToByte(location.getYaw()));
            packet.getBytes().writeSafely(1, angleToByte(location.getPitch()));
            packet.getBytes().writeSafely(2, angleToByte(location.getYaw()));
            if (packet.getIntegers().size() > 1) {
                packet.getIntegers().writeSafely(1, 0);
            }
            protocolManager.sendServerPacket(player, packet);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean sendTeleportEntityPacket(Player player, int entityId, Location location) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
            packet.getIntegers().writeSafely(0, entityId);
            packet.getDoubles().writeSafely(0, location.getX());
            packet.getDoubles().writeSafely(1, location.getY());
            packet.getDoubles().writeSafely(2, location.getZ());
            packet.getBytes().writeSafely(0, angleToByte(location.getYaw()));
            packet.getBytes().writeSafely(1, angleToByte(location.getPitch()));
            packet.getBooleans().writeSafely(0, false);
            protocolManager.sendServerPacket(player, packet);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void sendDestroyEntityPacket(Player player, int entityId) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            if (packet.getIntLists().size() > 0) {
                packet.getIntLists().write(0, Collections.singletonList(entityId));
            } else if (packet.getIntegerArrays().size() > 0) {
                packet.getIntegerArrays().write(0, new int[]{entityId});
            }
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception ignored) {
        }
    }

    private byte angleToByte(float angle) {
        return (byte) Math.floorMod((int) Math.round(angle * 256.0F / 360.0F), 256);
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
                                        double arcHeight,
                                        Style style) {
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
                        indicatorService.spawnDamage(player, target, damage, style);
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
