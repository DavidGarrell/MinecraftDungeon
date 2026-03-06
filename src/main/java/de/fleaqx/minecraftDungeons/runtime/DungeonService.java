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
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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

    private final Map<String, ZoneDefinition> zones = new HashMap<>();
    private final Map<UUID, PlayerDungeonSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lockedMessageCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastCombatLocations = new ConcurrentHashMap<>();
    private final Map<UUID, MobEntry> lastCombatMobTemplates = new ConcurrentHashMap<>();

    private final double baseHitsPerSecond;
    private final long resetIntervalTicks;

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

        sessions.values().forEach(this::clearSessionMobs);
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

    public int maxUpgradeZones(Player player) {
        int purchased = 0;
        while (unlockNextZoneWithMoney(player)) {
            purchased++;
        }
        return purchased;
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
        clearSessionMobs(session);
        session.zone(zone);
        session.currentStage(stage);
        session.stageKills(0);
        spawnStage(session, player, lastCombatLocations.get(player.getUniqueId()));
    }

    public void removePlayer(UUID playerId) {
        PlayerDungeonSession session = sessions.remove(playerId);
        if (session != null) {
            clearSessionMobs(session);
        }
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

        if (!result.dead()) {
            return new AttackResult(true, false);
        }

        giveRewards(player, result.mob().rewards());
        String rewardText = ChatColor.GREEN + "+" + NumberFormat.compact(result.mob().rewards().money()) + " $";
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
                player.sendMessage(ChatColor.GOLD + "Zone " + zone.displayName() + " completed.");
                session.currentStage(zone.totalStages());
                session.stageKills(0);
                spawnStage(session, player, lastCombatLocations.get(player.getUniqueId()));
                return new AttackResult(true, true);
            }

            int unlockedStage = unlockedStage(player, zone.id());
            if (nextStage > unlockedStage) {
                player.sendMessage(ChatColor.YELLOW + "Stage " + nextStage + " is locked. Use /zone to unlock it.");
                session.currentStage(unlockedStage);
                session.stageKills(0);
                spawnStage(session, player, lastCombatLocations.get(player.getUniqueId()));
                return new AttackResult(true, true);
            }

            session.currentStage(nextStage);
            session.stageKills(0);
            player.sendMessage(ChatColor.GREEN + "Stage " + nextStage + " started.");
            spawnStage(session, player, lastCombatLocations.get(player.getUniqueId()));
            return new AttackResult(true, true);
        }

        spawnReplacementMob(player, session);
        return new AttackResult(true, true);
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
        return Math.max(0.1D, base * enchant);
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
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof LivingEntity living && living.isValid()) {
                out.add(living);
            }
        }
        return out;
    }

    public void damageOwnedMobs(Player player, BigInteger damage, UUID excludeEntityId, DamageIndicatorService indicatorService) {
        for (LivingEntity mob : activeOwnedMobs(player)) {
            if (excludeEntityId != null && excludeEntityId.equals(mob.getUniqueId())) {
                continue;
            }
            AttackResult result = onPlayerDamageMob(player, mob, damage);
            if (result.accepted()) {
                indicatorService.spawnDamage(player, mob, damage);
            }
        }
    }

    public void executeDamage(Player player, LivingEntity target, double multiplier, DamageIndicatorService indicatorService) {
        VirtualHealthService.VirtualHealth health = virtualHealthService.get(target.getUniqueId());
        if (health == null) {
            return;
        }
        BigDecimal scaled = new BigDecimal(health.max()).multiply(BigDecimal.valueOf(Math.max(0.01D, multiplier)));
        BigInteger executeDamage = scaled.setScale(0, RoundingMode.DOWN).toBigInteger().max(BigInteger.ONE);
        AttackResult result = onPlayerDamageMob(player, target, executeDamage);
        if (result.accepted()) {
            indicatorService.spawnDamage(player, target, executeDamage);
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
            clearSessionMobs(session);
            session.zone(zone);
            int selected = selectedStage(player, zone.id());
            session.currentStage(selected);
            session.stageKills(0);
            player.sendMessage(ChatColor.AQUA + "Entered zone: " + zone.displayName() + " | Stage " + selected);
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
            player.sendMessage(ChatColor.RED + "Stage " + session.currentStage() + " has no mobs configured.");
            return;
        }

        List<MobEntry> candidateMobs = stage.mobs();

        clearSessionMobs(session);
        session.stageKills(0);

        MobEntry preferredMob = lastCombatMobTemplates.get(player.getUniqueId());
        boolean canUsePreferredMob = preferredMob != null && candidateMobs.stream().anyMatch(m -> m.id().equalsIgnoreCase(preferredMob.id()));
        int stageMobCount = Math.max(1, zone.mobsPerStage());

        for (int i = 0; i < stageMobCount; i++) {
            MobEntry chosen;
            if (i == 0 && canUsePreferredMob) {
                chosen = applyStageScaling(preferredMob, zone, session.currentStage());
            } else {
                MobEntry weighted = pickWeighted(candidateMobs, zone, session.currentStage());
                chosen = applyStageScaling(weighted, zone, session.currentStage());
            }

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

    private MobEntry pickWeighted(List<MobEntry> mobs, ZoneDefinition zone, int stageIndex) {
        int globalStage = globalStage(zone, stageIndex);
        long total = 0L;
        List<Long> effectiveWeights = new java.util.ArrayList<>(mobs.size());

        for (MobEntry mob : mobs) {
            long baseWeight = Math.max(1, mob.weight());
            double boost = rarityWeightBoost(mob, globalStage);
            long effective = Math.max(1L, (long) Math.floor(baseWeight * boost));
            effectiveWeights.add(effective);
            total += effective;
        }

        if (total <= 0L) {
            return mobs.get(0);
        }

        long roll = ThreadLocalRandom.current().nextLong(total);
        long cumulative = 0L;
        for (int i = 0; i < mobs.size(); i++) {
            cumulative += effectiveWeights.get(i);
            if (roll < cumulative) {
                return mobs.get(i);
            }
        }
        return mobs.get(mobs.size() - 1);
    }

    private double rarityWeightBoost(MobEntry mob, int globalStage) {
        int progress = Math.max(0, globalStage - 1);
        return switch (mob.rarity()) {
            case COMMON -> Math.max(0.10D, 1.0D - (progress * 0.03D));
            case RARE -> 1.0D + (progress * 0.05D);
            case EPIC -> 1.0D + (progress * 0.08D);
            case LEGENDARY -> 1.0D + (progress * 0.12D);
            case BOSS -> 1.0D + (progress * 0.16D);
        };
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

        Location location = afkMobDefinition.location().clone();
        if (location.getWorld() == null) {
            return;
        }

        spawnManagedMob(player, session, 1, afkMobDefinition.mob(), location);
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
        MobEntry weighted = pickWeighted(candidateMobs, zone, session.currentStage());
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

    private void spawnManagedMob(Player player, PlayerDungeonSession session, int level, MobEntry mob, Location spawnLocation) {
        Entity spawned = spawnLocation.getWorld().spawnEntity(spawnLocation, mob.entityType());
        if (!(spawned instanceof LivingEntity entity)) {
            spawned.remove();
            return;
        }

        entity.setCanPickupItems(false);
        entity.setPersistent(false);
        entity.setRemoveWhenFarAway(false);
        entity.setCollidable(false);
        entity.setAI(false);

        virtualHealthService.register(entity, player.getUniqueId(), level, mob);
        visibilityService.register(entity, player.getUniqueId());
        rarityVisualService.apply(entity, mob.rarity());
        hideFromOtherPlayers(player, entity);

        session.activeMobs().add(entity.getUniqueId());
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

    private void sendLockedZoneMessage(Player player, ZoneDefinition zone) {
        long now = System.currentTimeMillis();
        long last = lockedMessageCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 3000L) {
            return;
        }

        lockedMessageCooldown.put(player.getUniqueId(), now);
        player.sendMessage(ChatColor.RED + "Zone locked. Price: " + zone.unlockPrice());
    }

    private void giveRewards(Player player, CurrencyBundle rewards) {
        PlayerProfile profile = profile(player);
        profile.add(CurrencyType.MONEY, rewards.money());
        profile.add(CurrencyType.SOULS, rewards.souls());
        profile.add(CurrencyType.ESSENCE, rewards.essence());
        profile.add(CurrencyType.SHARDS, rewards.shards());
    }

    private void hideFromOtherPlayers(Player owner, LivingEntity entity) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(owner.getUniqueId())) {
                online.hideEntity(plugin, entity);
            }
        }
    }

    private void clearSessionMobs(PlayerDungeonSession session) {
        Set<UUID> active = Set.copyOf(session.activeMobs());
        for (UUID mobId : active) {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity != null) {
                rarityVisualService.clear(entity);
                visibilityService.unregister(entity);
                entity.remove();
            }
            virtualHealthService.remove(mobId);
            session.activeMobs().remove(mobId);
        }
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
}




