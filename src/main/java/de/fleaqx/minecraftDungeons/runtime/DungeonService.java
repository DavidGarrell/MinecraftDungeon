package de.fleaqx.minecraftDungeons.runtime;

import de.fleaqx.minecraftDungeons.config.ZoneConfigService;
import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import de.fleaqx.minecraftDungeons.currency.CurrencyBundle;
import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.model.AfkMobDefinition;
import de.fleaqx.minecraftDungeons.model.MobEntry;
import de.fleaqx.minecraftDungeons.model.StageDefinition;
import de.fleaqx.minecraftDungeons.model.ZoneDefinition;
import de.fleaqx.minecraftDungeons.profile.PlayerProfile;
import de.fleaqx.minecraftDungeons.profile.ProfileService;
import de.fleaqx.minecraftDungeons.sword.SwordPerkService;
import de.fleaqx.minecraftDungeons.rebirth.RebirthService;
import de.fleaqx.minecraftDungeons.companion.CompanionService;
import de.fleaqx.minecraftDungeons.util.EntityLookup;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class DungeonService {

    private final JavaPlugin plugin;
    private final ZoneConfigService configService;
    private final ProfileService profileService;
    private final VirtualHealthService virtualHealthService;
    private final VisibilityService visibilityService;
    private final RarityVisualService rarityVisualService;
    private final DamageIndicatorService damageIndicatorService;
    private EnchantService enchantService;
    private SwordPerkService swordPerkService;
    private CompanionService companionService;
    private RebirthService rebirthService;

    private final Map<String, ZoneDefinition> zones = new HashMap<>();
    private final Map<UUID, PlayerDungeonSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lockedMessageCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastCombatLocations = new ConcurrentHashMap<>();
    private final Map<UUID, MobEntry> lastCombatMobTemplates = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> afkMobByPlayer = new ConcurrentHashMap<>();

    private final double baseHitsPerSecond;
    private final long resetIntervalTicks;
    private final boolean onlyPlayerDamageManagedMobs;

    private AfkMobDefinition afkMobDefinition;
    private BukkitTask zoneTask;
    private BukkitTask saveTask;
    private BukkitTask resetTask;

    public DungeonService(JavaPlugin plugin,
                         ZoneConfigService configService,
                         ProfileService profileService,
                         VirtualHealthService virtualHealthService,
                         DamageIndicatorService damageIndicatorService) {
        this.plugin = plugin;
        this.configService = configService;
        this.profileService = profileService;
        this.virtualHealthService = virtualHealthService;
        this.damageIndicatorService = damageIndicatorService;
        this.visibilityService = createVisibilityService(plugin);
        this.rarityVisualService = new RarityVisualService();

        this.baseHitsPerSecond = Math.max(0.1D, plugin.getConfig().getDouble("combat.base-hits-per-second", 3.0D));
        this.resetIntervalTicks = Math.max(20L, plugin.getConfig().getLong("combat.reset-interval-seconds", 300L) * 20L);
        this.onlyPlayerDamageManagedMobs = plugin.getConfig().getBoolean("gameplay.only-player-damages-managed-mobs", true);
    }

    public void start() {
        reload();
        visibilityService.start();

        zoneTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                tickPlayerZone(player);
            }
        }, 20L, 20L);

        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, profileService::saveAll, 20L * 60L, 20L * 60L);
        resetTask = Bukkit.getScheduler().runTaskTimer(plugin, this::resetAllPlayerMobs, resetIntervalTicks, resetIntervalTicks);
    }

    public void shutdown() {
        if (zoneTask != null) {
            zoneTask.cancel();
        }
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (resetTask != null) {
            resetTask.cancel();
        }

        sessions.values().forEach(session -> clearSessionMobs(session, true));
        sessions.clear();
        visibilityService.shutdown();
        profileService.saveAll();
    }

    public void reload() {
        zones.clear();
        zones.putAll(configService.loadZones());
        afkMobDefinition = configService.loadAfkMob();
    }

    public void setEnchantService(EnchantService enchantService) {
        this.enchantService = enchantService;
    }

    public void setSwordPerkService(SwordPerkService swordPerkService) {
        this.swordPerkService = swordPerkService;
    }

    public void setCompanionService(CompanionService companionService) {
        this.companionService = companionService;
    }

    public void setRebirthService(RebirthService rebirthService) {
        this.rebirthService = rebirthService;
    }

    public Collection<ZoneDefinition> zones() {
        return zones.values();
    }

    public List<ZoneDefinition> sortedZones() {
        return zones.values().stream().sorted(Comparator.comparingInt(ZoneDefinition::order)).toList();
    }

    public Optional<ZoneDefinition> zoneById(String zoneId) {
        return Optional.ofNullable(zones.get(zoneId.toLowerCase(Locale.ROOT)));
    }

    public Optional<ZoneDefinition> zoneByOrder(int order) {
        return sortedZones().stream().filter(zone -> zone.order() == order).findFirst();
    }

    public Optional<ZoneDefinition> zoneAt(Location location) {
        return zones.values().stream().filter(zone -> zone.area().contains(location)).findFirst();
    }

    public Optional<PlayerZoneContext> currentZoneContext(Player player) {
        PlayerDungeonSession session = sessions.get(player.getUniqueId());
        if (session != null && session.zone() != null) {
            return Optional.of(new PlayerZoneContext(session.zone().id(), session.currentStage()));
        }

        Optional<ZoneDefinition> zone = zoneAt(player.getLocation());
        if (zone.isEmpty()) {
            return Optional.empty();
        }

        int stage = selectedStage(player, zone.get().id());
        return Optional.of(new PlayerZoneContext(zone.get().id(), stage));
    }

    public PlayerProfile profile(Player player) {
        return profileService.profile(player.getUniqueId());
    }

    public BigInteger balance(Player player, CurrencyType type) {
        return profile(player).balance(type);
    }

    public boolean removeCurrency(Player player, CurrencyType type, BigInteger amount) {
        return profile(player).remove(type, amount);
    }

    public void addCurrency(Player player, CurrencyType type, BigInteger amount) {
        profile(player).add(type, amount);
    }

    public boolean isZoneUnlocked(Player player, ZoneDefinition zone) {
        return profile(player).unlockedZoneOrder() >= zone.order();
    }

    public int unlockedStage(Player player, String zoneId) {
        return profile(player).unlockedStage(zoneId);
    }

    public int selectedStage(Player player, String zoneId) {
        PlayerProfile profile = profile(player);
        int unlocked = profile.unlockedStage(zoneId);
        int selected = profile.selectedStage(zoneId);
        return Math.min(unlocked, selected);
    }

    public void selectStage(Player player, String zoneId, int stage) {
        PlayerProfile profile = profile(player);
        int unlocked = profile.unlockedStage(zoneId);
        profile.selectedStage(zoneId, Math.min(unlocked, stage));
    }

    public boolean unlockNextZoneWithMoney(Player player) {
        PlayerProfile profile = profile(player);
        Optional<ZoneDefinition> next = zoneByOrder(profile.unlockedZoneOrder() + 1);
        if (next.isEmpty()) {
            return false;
        }

        ZoneDefinition zone = next.get();
        if (!profile.remove(CurrencyType.MONEY, zone.unlockPrice())) {
            return false;
        }

        profile.unlockedZoneOrder(zone.order());
        profile.unlockedStage(zone.id(), 1);
        profile.selectedStage(zone.id(), 1);
        return true;
    }

    public int maxUpgradeStages(Player player) {
        int purchased = 0;
        while (unlockNextProgressionStep(player)) {
            purchased++;
        }
        return purchased;
    }

    private boolean unlockNextProgressionStep(Player player) {
        PlayerProfile profile = profile(player);
        List<ZoneDefinition> ordered = sortedZones();
        if (ordered.isEmpty()) {
            return false;
        }

        int unlockedZoneOrder = profile.unlockedZoneOrder();
        for (ZoneDefinition zone : ordered) {
            if (zone.order() > unlockedZoneOrder + 1) {
                break;
            }

            if (zone.order() > unlockedZoneOrder) {
                if (!profile.remove(CurrencyType.MONEY, zone.unlockPrice())) {
                    return false;
                }
                profile.unlockedZoneOrder(zone.order());
                profile.unlockedStage(zone.id(), 1);
                profile.selectedStage(zone.id(), 1);
                return true;
            }

            int unlockedStage = Math.max(1, profile.unlockedStage(zone.id()));
            if (unlockedStage >= zone.totalStages()) {
                continue;
            }

            int nextStage = unlockedStage + 1;
            StageDefinition stageDefinition = zone.stageByIndex(nextStage);
            if (stageDefinition == null) {
                continue;
            }

            if (!profile.remove(CurrencyType.MONEY, stageDefinition.unlockPrice())) {
                return false;
            }

            profile.unlockedStage(zone.id(), nextStage);
            profile.selectedStage(zone.id(), nextStage);
            return true;
        }

        return false;
    }

    public boolean unlockStage(Player player, ZoneDefinition zone, int stageIndex) {
        PlayerProfile profile = profile(player);
        int unlocked = profile.unlockedStage(zone.id());
        if (stageIndex <= unlocked) {
            return true;
        }
        if (stageIndex != unlocked + 1) {
            return false;
        }

        StageDefinition stage = zone.stageByIndex(stageIndex);
        if (stage == null) {
            return false;
        }

        if (!profile.remove(CurrencyType.MONEY, stage.unlockPrice())) {
            return false;
        }

        profile.unlockedStage(zone.id(), stageIndex);
        profile.selectedStage(zone.id(), stageIndex);
        return true;
    }

    public void forceStart(Player player, ZoneDefinition zone, int stage) {
        PlayerDungeonSession session = sessions.computeIfAbsent(player.getUniqueId(), PlayerDungeonSession::new);
        clearSessionMobs(session, false);
        session.zone(zone);
        session.currentStage(stage);
        session.stageKills(0);
        spawnStage(session, player, lastCombatLocations.get(player.getUniqueId()));
    }

    public void removePlayer(UUID playerId) {
        PlayerDungeonSession session = sessions.remove(playerId);
        if (session != null) {
            clearSessionMobs(session, true);
        }
        afkMobByPlayer.remove(playerId);
        lastCombatLocations.remove(playerId);
        lastCombatMobTemplates.remove(playerId);
    }

    public AttackResult onPlayerDamageMob(Player player, LivingEntity entity, BigInteger damage) {
        VirtualHealthService.DamageResult result = virtualHealthService.applyDamage(entity, damage);
        if (result.owner() == null || result.mob() == null) {
            return AttackResult.ignored();
        }

        if (!player.getUniqueId().equals(result.owner())) {
            return AttackResult.ignored();
        }

        lastCombatLocations.put(player.getUniqueId(), entity.getLocation().clone());
        lastCombatMobTemplates.put(player.getUniqueId(), result.mob());

        entity.playHurtAnimation(player.getLocation().getYaw());

        if (!result.dead()) {
            return new AttackResult(true, false);
        }

        CurrencyBundle grantedRewards = giveRewards(player, result.mob().rewards());
        String rewardText = ChatColor.GREEN + "" + ChatColor.BOLD + "+" + NumberFormat.compact(grantedRewards.money()) + " $";
        damageIndicatorService.spawnReward(player, entity, rewardText);

        PlayerDungeonSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return new AttackResult(true, true);
        }

        session.activeMobs().remove(entity.getUniqueId());
        rarityVisualService.clear(entity);
        visibilityService.unregister(entity);
        entity.remove();

        if (isAfkMob(result.mob().id())) {
            spawnAfkMobForPlayer(player, session);
            return new AttackResult(true, true);
        }

        session.incrementStageKills();
        ZoneDefinition zone = session.zone();
        if (zone == null) {
            return new AttackResult(true, true);
        }

        if (session.stageKills() >= Math.max(1, zone.mobsPerStage())) {
            int nextStage = session.currentStage() + 1;
            if (nextStage > zone.totalStages()) {
                player.sendMessage(formatMessage("messages.zone-completed", "&6Zone {zone} completed.", Map.of("{zone}", zone.displayName())));
                session.currentStage(zone.totalStages());
                session.stageKills(0);
                spawnStage(session, player, lastCombatLocations.get(player.getUniqueId()));
                return new AttackResult(true, true);
            }

            int unlockedStage = unlockedStage(player, zone.id());
            if (nextStage > unlockedStage) {
                player.sendMessage(formatMessage("messages.stage-locked", "&eStage {stage} is locked. Use /zone to unlock it.", Map.of("{stage}", String.valueOf(nextStage))));
                session.currentStage(unlockedStage);
                session.stageKills(0);
                spawnStage(session, player, lastCombatLocations.get(player.getUniqueId()));
                return new AttackResult(true, true);
            }

            session.currentStage(nextStage);
            session.stageKills(0);
            player.sendMessage(formatMessage("messages.stage-started", "&aStage {stage} started.", Map.of("{stage}", String.valueOf(nextStage))));
            spawnStage(session, player, lastCombatLocations.get(player.getUniqueId()));
            return new AttackResult(true, true);
        }

        spawnReplacementMob(player, session);
        return new AttackResult(true, true);
    }


    public boolean onlyPlayerDamageManagedMobs() {
        return onlyPlayerDamageManagedMobs;
    }

    public String formatMessage(String path, String fallback, Map<String, String> replacements) {
        String raw = plugin.getConfig().getString(path, fallback);
        if (raw == null) {
            raw = fallback;
        }
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            raw = raw.replace(entry.getKey(), entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public boolean isManagedMob(UUID entityId) {
        return virtualHealthService.isManaged(entityId);
    }

    public boolean canPlayerAttackMob(Player player, UUID entityId) {
        UUID owner = mobOwner(entityId);
        return owner != null && owner.equals(player.getUniqueId());
    }

    public boolean isAfkMobFor(Player player, UUID entityId) {
        VirtualHealthService.VirtualHealth state = virtualHealthService.get(entityId);
        return state != null
                && state.owner().equals(player.getUniqueId())
                && state.mob() != null
                && state.mob().id().equalsIgnoreCase("afk_mob");
    }

    public UUID mobOwner(UUID entityId) {
        VirtualHealthService.VirtualHealth health = virtualHealthService.get(entityId);
        return health == null ? null : health.owner();
    }

    public VirtualHealthService.VirtualHealth health(UUID entityId) {
        return virtualHealthService.get(entityId);
    }

    public double attackSpeedMultiplier(Player player) {
        double base = profile(player).attackSpeedMultiplier();
        double enchant = enchantService == null ? 1.0D : enchantService.attackSpeedMultiplier(player);
        double perk = swordPerkService == null ? 1.0D : swordPerkService.attackSpeedMultiplier(player);
        return Math.max(0.1D, base * enchant * perk);
    }

    public double hitsPerSecondFor(Player player, UUID entityId) {
        VirtualHealthService.VirtualHealth health = virtualHealthService.get(entityId);
        if (health == null || health.mob() == null) {
            return baseHitsPerSecond;
        }

        double afkRate = afkMobDefinition != null ? afkMobDefinition.hitsPerSecond() : 2.0D;
        double base = isAfkMob(health.mob().id()) ? afkRate : baseHitsPerSecond;
        return Math.max(0.1D, base * attackSpeedMultiplier(player));
    }

    public List<LivingEntity> activeOwnedMobs(Player player) {
        PlayerDungeonSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return List.of();
        }

        List<LivingEntity> out = new java.util.ArrayList<>();
        for (UUID id : session.activeMobs()) {
            Entity entity = EntityLookup.find(id);
            if (entity instanceof LivingEntity living && living.isValid()) {
                out.add(living);
            }
        }
        return out;
    }


    public List<LivingEntity> activeCombatOwnedMobs(Player player) {
        return activeOwnedMobs(player).stream()
                .filter(mob -> !isAfkMobFor(player, mob.getUniqueId()))
                .toList();
    }

    public List<LivingEntity> activeAttackableOwnedMobs(Player player) {
        List<LivingEntity> combat = activeCombatOwnedMobs(player);
        return combat.isEmpty() ? activeOwnedMobs(player) : combat;
    }

    public void damageOwnedMobs(Player player, BigInteger damage, UUID excludeEntityId, DamageIndicatorService indicatorService) {
        damageOwnedMobs(player, damage, excludeEntityId, indicatorService, null);
    }

    public void damageOwnedMobs(Player player, BigInteger damage, UUID excludeEntityId, DamageIndicatorService indicatorService, DamageIndicatorService.Style style) {
        for (LivingEntity mob : activeAttackableOwnedMobs(player)) {
            if (excludeEntityId != null && excludeEntityId.equals(mob.getUniqueId())) {
                continue;
            }
            AttackResult result = onPlayerDamageMob(player, mob, damage);
            if (result.accepted()) {
                indicatorService.spawnDamage(player, mob, damage, style);
            }
        }
    }

    public void executeDamage(Player player, LivingEntity target, double multiplier, DamageIndicatorService indicatorService) {
        executeDamage(player, target, multiplier, indicatorService, null);
    }

    public void executeDamage(Player player, LivingEntity target, double multiplier, DamageIndicatorService indicatorService, DamageIndicatorService.Style style) {
        VirtualHealthService.VirtualHealth health = virtualHealthService.get(target.getUniqueId());
        if (health == null) {
            return;
        }
        BigDecimal scaled = new BigDecimal(health.max()).multiply(BigDecimal.valueOf(Math.max(0.01D, multiplier)));
        BigInteger executeDamage = scaled.setScale(0, RoundingMode.DOWN).toBigInteger().max(BigInteger.ONE);
        AttackResult result = onPlayerDamageMob(player, target, executeDamage);
        if (result.accepted()) {
            indicatorService.spawnDamage(player, target, executeDamage, style);
        }
    }
    private void tickPlayerZone(Player player) {
        Optional<ZoneDefinition> zoneOptional = zoneAt(player.getLocation());
        PlayerDungeonSession session = sessions.computeIfAbsent(player.getUniqueId(), PlayerDungeonSession::new);

        if (zoneOptional.isEmpty()) {
            return;
        }

        ZoneDefinition zone = zoneOptional.get();
        if (!isZoneUnlocked(player, zone)) {
            sendLockedZoneMessage(player, zone);
            return;
        }

        if (session.zone() == null || !session.zone().id().equalsIgnoreCase(zone.id())) {
            clearSessionMobs(session, false);
            session.zone(zone);
            int selected = selectedStage(player, zone.id());
            session.currentStage(selected);
            session.stageKills(0);
            player.sendMessage(formatMessage("messages.entered-zone", "&bEntered zone: {zone} | Stage {stage}", Map.of("{zone}", zone.displayName(), "{stage}", String.valueOf(selected))));
            spawnStage(session, player, lastCombatLocations.get(player.getUniqueId()));
        }
    }

    private void spawnStage(PlayerDungeonSession session, Player player, Location preferredSpawn) {
        ZoneDefinition zone = session.zone();
        if (zone == null) {
            return;
        }

        StageDefinition stage = zone.stageByIndex(session.currentStage());
        if (stage == null || stage.mobs().isEmpty()) {
            player.sendMessage(formatMessage("messages.stage-no-mobs", "&cStage {stage} has no mobs configured.", Map.of("{stage}", String.valueOf(session.currentStage()))));
            return;
        }

        List<MobEntry> candidateMobs = stage.mobs();

        clearSessionMobs(session, false);
        session.stageKills(0);

        int stageMobCount = Math.max(1, zone.mobsPerStage());

        for (int i = 0; i < stageMobCount; i++) {
            MobEntry weighted = pickWeighted(candidateMobs);
            MobEntry chosen = applyStageScaling(weighted, zone, session.currentStage());

            Location spawnLocation;
            if (i == 0 && preferredSpawn != null && zone.area().contains(preferredSpawn)) {
                spawnLocation = randomNear(preferredSpawn, 3.0D);
            } else {
                spawnLocation = zone.area().randomSpawnLocation();
            }
            if (spawnLocation == null || spawnLocation.getWorld() == null) {
                continue;
            }

            spawnManagedMob(player, session, session.currentStage(), chosen, spawnLocation);
        }

        spawnAfkMobForPlayer(player, session);
    }

    private MobEntry pickWeighted(List<MobEntry> mobs) {
        if (mobs.isEmpty()) {
            throw new IllegalArgumentException("Mob list cannot be empty");
        }

        Map<de.fleaqx.minecraftDungeons.model.MobRarity, List<MobEntry>> byRarity = new EnumMap<>(de.fleaqx.minecraftDungeons.model.MobRarity.class);
        for (MobEntry mob : mobs) {
            byRarity.computeIfAbsent(mob.rarity(), ignored -> new ArrayList<>()).add(mob);
        }

        List<MobEntry> nonBossPool = new ArrayList<>();
        for (Map.Entry<de.fleaqx.minecraftDungeons.model.MobRarity, List<MobEntry>> entry : byRarity.entrySet()) {
            if (entry.getKey() != de.fleaqx.minecraftDungeons.model.MobRarity.BOSS) {
                nonBossPool.addAll(entry.getValue());
            }
        }
        if (nonBossPool.isEmpty()) {
            return pickByConfiguredWeight(mobs);
        }

        double roll = ThreadLocalRandom.current().nextDouble(100.0D);
        List<de.fleaqx.minecraftDungeons.model.MobRarity> preferredOrder;
        if (roll < 10.0D) {
            preferredOrder = List.of(
                    de.fleaqx.minecraftDungeons.model.MobRarity.LEGENDARY,
                    de.fleaqx.minecraftDungeons.model.MobRarity.EPIC,
                    de.fleaqx.minecraftDungeons.model.MobRarity.RARE,
                    de.fleaqx.minecraftDungeons.model.MobRarity.COMMON
            );
        } else if (roll < 30.0D) {
            preferredOrder = List.of(
                    de.fleaqx.minecraftDungeons.model.MobRarity.EPIC,
                    de.fleaqx.minecraftDungeons.model.MobRarity.RARE,
                    de.fleaqx.minecraftDungeons.model.MobRarity.LEGENDARY,
                    de.fleaqx.minecraftDungeons.model.MobRarity.COMMON
            );
        } else if (roll < 60.0D) {
            preferredOrder = List.of(
                    de.fleaqx.minecraftDungeons.model.MobRarity.RARE,
                    de.fleaqx.minecraftDungeons.model.MobRarity.COMMON,
                    de.fleaqx.minecraftDungeons.model.MobRarity.EPIC,
                    de.fleaqx.minecraftDungeons.model.MobRarity.LEGENDARY
            );
        } else {
            preferredOrder = List.of(
                    de.fleaqx.minecraftDungeons.model.MobRarity.COMMON,
                    de.fleaqx.minecraftDungeons.model.MobRarity.RARE,
                    de.fleaqx.minecraftDungeons.model.MobRarity.EPIC,
                    de.fleaqx.minecraftDungeons.model.MobRarity.LEGENDARY
            );
        }

        for (de.fleaqx.minecraftDungeons.model.MobRarity rarity : preferredOrder) {
            List<MobEntry> pool = byRarity.getOrDefault(rarity, List.of());
            if (!pool.isEmpty()) {
                return pickByConfiguredWeight(pool);
            }
        }

        return pickByConfiguredWeight(nonBossPool);
    }

    private MobEntry pickByConfiguredWeight(List<MobEntry> mobs) {
        long total = 0L;
        for (MobEntry mob : mobs) {
            total += Math.max(1, mob.weight());
        }

        if (total <= 0L) {
            return mobs.get(0);
        }

        long roll = ThreadLocalRandom.current().nextLong(total);
        long cumulative = 0L;
        for (MobEntry mob : mobs) {
            cumulative += Math.max(1, mob.weight());
            if (roll < cumulative) {
                return mob;
            }
        }
        return mobs.get(mobs.size() - 1);
    }

    private MobEntry applyStageScaling(MobEntry mob, ZoneDefinition zone, int stageIndex) {
        int globalStage = globalStage(zone, stageIndex);
        int exponent = Math.max(0, globalStage - 1);

        BigInteger baseHealth = baseHealthForRarity(mob.rarity());
        BigInteger stageHealth = baseHealth.multiply(BigInteger.valueOf(30).pow(exponent));
        BigInteger moneyReward = stageHealth.multiply(BigInteger.valueOf(4));

        CurrencyBundle rewards = new CurrencyBundle(
                moneyReward,
                mob.rewards().souls(),
                mob.rewards().essence(),
                mob.rewards().shards()
        );

        return new MobEntry(
                mob.id(),
                mob.entityType(),
                mob.rarity(),
                mob.weight(),
                mob.scale(),
                stageHealth,
                rewards
        );
    }

    private int globalStage(ZoneDefinition zone, int stageIndex) {
        return ((zone.order() - 1) * 10) + stageIndex;
    }

    private BigInteger baseHealthForRarity(de.fleaqx.minecraftDungeons.model.MobRarity rarity) {
        return switch (rarity) {
            case COMMON -> BigInteger.valueOf(24);
            case RARE -> BigInteger.valueOf(48);
            case EPIC -> BigInteger.valueOf(72);
            case LEGENDARY -> BigInteger.valueOf(96);
            case BOSS -> BigInteger.valueOf(120);
        };
    }

    private void spawnAfkMobForPlayer(Player player, PlayerDungeonSession session) {
        if (afkMobDefinition == null || !afkMobDefinition.enabled() || afkMobDefinition.location() == null || afkMobDefinition.mob() == null) {
            return;
        }

        UUID existingId = afkMobByPlayer.get(player.getUniqueId());
        if (existingId != null) {
            Entity existing = EntityLookup.find(existingId);
            if (existing instanceof LivingEntity living
                    && living.isValid()
                    && canPlayerAttackMob(player, existingId)
                    && isAfkMobFor(player, existingId)) {
                session.activeMobs().add(existingId);
                return;
            }
            afkMobByPlayer.remove(player.getUniqueId());
        }

        Location location = afkMobDefinition.location().clone();
        if (location.getWorld() == null) {
            return;
        }

        LivingEntity entity = spawnManagedMob(player, session, 1, afkMobDefinition.mob(), location);
        if (entity != null) {
            afkMobByPlayer.put(player.getUniqueId(), entity.getUniqueId());
        }
    }

    private void spawnReplacementMob(Player player, PlayerDungeonSession session) {
        ZoneDefinition zone = session.zone();
        if (zone == null) {
            return;
        }

        StageDefinition stage = zone.stageByIndex(session.currentStage());
        if (stage == null || stage.mobs().isEmpty()) {
            return;
        }

        List<MobEntry> candidateMobs = stage.mobs();
        MobEntry weighted = pickWeighted(candidateMobs);
        MobEntry chosen = applyStageScaling(weighted, zone, session.currentStage());
        Location lastLocation = lastCombatLocations.get(player.getUniqueId());

        Location spawnLocation = zone.area().randomSpawnLocation();
        if (lastLocation != null && zone.area().contains(lastLocation)) {
            Location nearby = randomNear(lastLocation, 5.0D);
            if (nearby != null) {
                spawnLocation = nearby;
            }
        }

        if (spawnLocation == null || spawnLocation.getWorld() == null) {
            return;
        }

        spawnManagedMob(player, session, session.currentStage(), chosen, spawnLocation);
    }

    private LivingEntity spawnManagedMob(Player player, PlayerDungeonSession session, int level, MobEntry mob, Location spawnLocation) {
        Entity spawned = spawnLocation.getWorld().spawnEntity(spawnLocation, mob.entityType());
        if (!(spawned instanceof LivingEntity entity)) {
            spawned.remove();
            return null;
        }

        entity.setCanPickupItems(false);
        entity.setPersistent(false);
        entity.setRemoveWhenFarAway(false);
        entity.setCollidable(false);
        entity.setAI(false);
        disableDaylightBurn(entity);

        if (entity.getAttribute(Attribute.GENERIC_SCALE) != null) {
            entity.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(Math.max(0.1D, mob.scale()));
        }

        virtualHealthService.register(entity, player.getUniqueId(), level, mob);
        visibilityService.register(entity, player.getUniqueId());
        rarityVisualService.apply(entity, mob.rarity());
        hideFromOtherPlayers(player, entity);

        session.activeMobs().add(entity.getUniqueId());
        return entity;
    }

    private void resetAllPlayerMobs() {
        for (PlayerDungeonSession session : sessions.values()) {
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null || !player.isOnline() || session.zone() == null) {
                continue;
            }
            spawnStage(session, player, lastCombatLocations.get(player.getUniqueId()));
        }
    }

    private Location randomNear(Location base, double radius) {
        if (base.getWorld() == null) {
            return null;
        }
        double x = base.getX() + ThreadLocalRandom.current().nextDouble(-radius, radius);
        double z = base.getZ() + ThreadLocalRandom.current().nextDouble(-radius, radius);
        int y = base.getWorld().getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
        return new Location(base.getWorld(), x + 0.5, y, z + 0.5);
    }

    private boolean isAfkMob(String mobId) {
        return mobId != null && mobId.equalsIgnoreCase("afk_mob");
    }

    private void disableDaylightBurn(LivingEntity entity) {
        try {
            java.lang.reflect.Method method = entity.getClass().getMethod("setShouldBurnInDay", boolean.class);
            method.invoke(entity, false);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void sendLockedZoneMessage(Player player, ZoneDefinition zone) {
        long now = System.currentTimeMillis();
        long last = lockedMessageCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 3000L) {
            return;
        }

        lockedMessageCooldown.put(player.getUniqueId(), now);
        player.sendMessage(formatMessage("messages.zone-locked", "&cZone locked. Price: {price}", Map.of("{price}", zone.unlockPrice().toString())));
    }

    private CurrencyBundle giveRewards(Player player, CurrencyBundle rewards) {
        PlayerProfile profile = profile(player);
        java.math.BigInteger money = rewards.money();
        java.math.BigInteger souls = rewards.souls();
        java.math.BigInteger essence = rewards.essence();

        if (swordPerkService != null) {
            if (money.compareTo(java.math.BigInteger.ZERO) > 0) {
                money = scaleByMultiplier(money, swordPerkService.moneyMultiplier(player));
            }
            if (souls.compareTo(java.math.BigInteger.ZERO) > 0) {
                souls = scaleByMultiplier(souls, swordPerkService.soulsMultiplier(player));
            }
            if (essence.compareTo(java.math.BigInteger.ZERO) > 0) {
                essence = scaleByMultiplier(essence, swordPerkService.essenceMultiplier(player));
            }
        }

        if (companionService != null && money.compareTo(java.math.BigInteger.ZERO) > 0) {
            money = scaleByMultiplier(money, companionService.moneyMultiplier(player));
        }

        if (rebirthService != null && money.compareTo(java.math.BigInteger.ZERO) > 0) {
            money = scaleByMultiplier(money, rebirthService.moneyMultiplier(player));
        }

        CurrencyBundle grantedRewards = new CurrencyBundle(money, souls, essence, rewards.shards());

        profile.add(CurrencyType.MONEY, grantedRewards.money());
        profile.add(CurrencyType.SOULS, grantedRewards.souls());
        profile.add(CurrencyType.ESSENCE, grantedRewards.essence());
        profile.add(CurrencyType.SHARDS, grantedRewards.shards());

        return grantedRewards;
    }


    public void resetProgressAfterRebirth(Player player) {
        removePlayer(player.getUniqueId());

        Optional<ZoneDefinition> first = zoneByOrder(1);
        if (first.isPresent()) {
            ZoneDefinition zone = first.get();
            if (zone.spawn() != null && zone.spawn().getWorld() != null) {
                player.teleport(zone.spawn());
            }
            selectStage(player, zone.id(), 1);
            forceStart(player, zone, 1);
        }
    }

    private java.math.BigInteger scaleByMultiplier(java.math.BigInteger base, double multiplier) {
        if (multiplier <= 0.0D || base.compareTo(java.math.BigInteger.ZERO) <= 0) {
            return java.math.BigInteger.ZERO;
        }
        java.math.BigDecimal decimal = new java.math.BigDecimal(base);
        java.math.BigDecimal scaled = decimal.multiply(java.math.BigDecimal.valueOf(multiplier));
        return scaled.setScale(0, java.math.RoundingMode.DOWN).toBigInteger().max(java.math.BigInteger.ZERO);
    }

    private void hideFromOtherPlayers(Player owner, LivingEntity entity) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(owner.getUniqueId())) {
                online.hideEntity(plugin, entity);
            }
        }
    }

    private void clearSessionMobs(PlayerDungeonSession session, boolean includeAfk) {
        Set<UUID> active = Set.copyOf(session.activeMobs());
        for (UUID mobId : active) {
            if (!includeAfk && isAfkMobFor(session.playerId(), mobId)) {
                continue;
            }
            boolean afkMob = isAfkMobId(mobId);
            Entity entity = EntityLookup.find(mobId);
            if (entity != null) {
                rarityVisualService.clear(entity);
                visibilityService.unregister(entity);
                entity.remove();
            }
            virtualHealthService.remove(mobId);
            session.activeMobs().remove(mobId);
            if (includeAfk && afkMob) {
                afkMobByPlayer.remove(session.playerId(), mobId);
            }
        }
    }

    private boolean isAfkMobFor(UUID playerId, UUID entityId) {
        VirtualHealthService.VirtualHealth state = virtualHealthService.get(entityId);
        return state != null
                && state.owner().equals(playerId)
                && state.mob() != null
                && isAfkMob(state.mob().id());
    }

    private boolean isAfkMobId(UUID entityId) {
        VirtualHealthService.VirtualHealth state = virtualHealthService.get(entityId);
        return state != null && state.mob() != null && isAfkMob(state.mob().id());
    }

    private VisibilityService createVisibilityService(JavaPlugin plugin) {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("ProtocolLib") != null
                    && plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
                plugin.getLogger().info("Visibility mode: ProtocolLib packets");
                return new VisibilityPacketService(plugin);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("ProtocolLib packet visibility disabled: " + throwable.getClass().getSimpleName());
        }

        plugin.getLogger().warning("Visibility mode: Bukkit fallback (no packet filtering)");
        return new FallbackVisibilityService();
    }

    public record AttackResult(boolean accepted, boolean dead) {
        public static AttackResult ignored() {
            return new AttackResult(false, false);
        }
    }
    public record PlayerZoneContext(String zoneId, int stage) {
    }
}
