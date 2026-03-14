package de.fleaqx.minecraftDungeons.config;

import de.fleaqx.minecraftDungeons.currency.CurrencyBundle;
import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.model.AfkMobDefinition;
import de.fleaqx.minecraftDungeons.model.CuboidArea;
import de.fleaqx.minecraftDungeons.model.MobEntry;
import de.fleaqx.minecraftDungeons.model.MobRarity;
import de.fleaqx.minecraftDungeons.model.StageDefinition;
import de.fleaqx.minecraftDungeons.model.ZoneDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ZoneConfigService {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;

    public ZoneConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "zones.yml");
    }

    public void init() {
        if (!file.exists()) {
            plugin.saveResource("zones.yml", false);
        }
        reloadFile();
    }

    public void reloadFile() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public Map<String, ZoneDefinition> loadZones() {
        reloadFile();
        ConfigurationSection zonesSection = config.getConfigurationSection("zones");
        Map<String, ZoneDefinition> zones = new HashMap<>();
        if (zonesSection == null) {
            return zones;
        }

        for (String zoneId : zonesSection.getKeys(false)) {
            ConfigurationSection zoneSection = zonesSection.getConfigurationSection(zoneId);
            if (zoneSection == null) {
                continue;
            }

            String world = zoneSection.getString("area.world", "world");
            CuboidArea area = new CuboidArea(
                    world,
                    zoneSection.getDouble("area.min.x"),
                    zoneSection.getDouble("area.min.y"),
                    zoneSection.getDouble("area.min.z"),
                    zoneSection.getDouble("area.max.x"),
                    zoneSection.getDouble("area.max.y"),
                    zoneSection.getDouble("area.max.z")
            );

            Location spawn = null;
            if (zoneSection.isConfigurationSection("spawn")) {
                String spawnWorld = zoneSection.getString("spawn.world", world);
                spawn = new Location(
                        Bukkit.getWorld(spawnWorld),
                        zoneSection.getDouble("spawn.x"),
                        zoneSection.getDouble("spawn.y"),
                        zoneSection.getDouble("spawn.z"),
                        (float) zoneSection.getDouble("spawn.yaw", 0.0),
                        (float) zoneSection.getDouble("spawn.pitch", 0.0)
                );
            }

            int totalStages = zoneSection.getInt("total-stages", 10);
            int mobsPerStage = zoneSection.getInt("mobs-per-stage", 20);
            int order = zoneSection.getInt("order", 1);
            BigInteger unlockPrice = NumberFormat.parse(zoneSection.getString("unlock-price", "0"), BigInteger.ZERO);
            List<StageDefinition> stages = loadStages(zoneSection.getConfigurationSection("stages"));

            ZoneDefinition zone = new ZoneDefinition(
                    zoneId,
                    zoneSection.getString("display-name", zoneId),
                    order,
                    unlockPrice,
                    totalStages,
                    mobsPerStage,
                    area,
                    spawn,
                    stages
            );
            zones.put(zoneId.toLowerCase(Locale.ROOT), zone);
        }
        return zones;
    }

    public AfkMobDefinition loadAfkMob() {
        reloadFile();
        if (!config.getBoolean("afk-mob.enabled", false)) {
            return new AfkMobDefinition(false, null, null, 2.0D);
        }

        String world = config.getString("afk-mob.location.world", "world");
        Location location = new Location(
                Bukkit.getWorld(world),
                config.getDouble("afk-mob.location.x"),
                config.getDouble("afk-mob.location.y"),
                config.getDouble("afk-mob.location.z"),
                (float) config.getDouble("afk-mob.location.yaw", 0.0),
                (float) config.getDouble("afk-mob.location.pitch", 0.0)
        );

        EntityType type;
        MobRarity rarity;
        try {
            type = EntityType.valueOf(config.getString("afk-mob.entity", "IRON_GOLEM").toUpperCase(Locale.ROOT));
            rarity = MobRarity.valueOf(config.getString("afk-mob.rarity", "LEGENDARY").toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            return new AfkMobDefinition(false, null, null, 2.0D);
        }

        MobEntry entry = new MobEntry(
                "afk_mob",
                type,
                rarity,
                1,
                Math.max(0.1D, config.getDouble("afk-mob.scale", plugin.getConfig().getDouble("gameplay.default-mob-scale", 1.0D))),
                NumberFormat.parse(config.getString("afk-mob.health", "1000000"), BigInteger.valueOf(1_000_000)),
                CurrencyBundle.of(
                        NumberFormat.parse(config.getString("afk-mob.rewards.money", "100"), BigInteger.valueOf(100)),
                        NumberFormat.parse(config.getString("afk-mob.rewards.souls", "5"), BigInteger.valueOf(5)),
                        NumberFormat.parse(config.getString("afk-mob.rewards.essence", "5"), BigInteger.valueOf(5)),
                        NumberFormat.parse(config.getString("afk-mob.rewards.shards", "1"), BigInteger.ONE)
                )
        );

        double hps = Math.max(0.1D, config.getDouble("afk-mob.hits-per-second", 2.0D));
        return new AfkMobDefinition(true, location, entry, hps);
    }

    public boolean setAfkLocation(Location location) {
        config.set("afk-mob.enabled", true);
        config.set("afk-mob.location.world", location.getWorld() == null ? "world" : location.getWorld().getName());
        config.set("afk-mob.location.x", location.getX());
        config.set("afk-mob.location.y", location.getY());
        config.set("afk-mob.location.z", location.getZ());
        config.set("afk-mob.location.yaw", location.getYaw());
        config.set("afk-mob.location.pitch", location.getPitch());
        return save();
    }

    private List<StageDefinition> loadStages(ConfigurationSection stagesSection) {
        List<StageDefinition> list = new ArrayList<>();
        if (stagesSection == null) {
            return list;
        }

        for (String stageKey : stagesSection.getKeys(false)) {
            ConfigurationSection stageSection = stagesSection.getConfigurationSection(stageKey);
            if (stageSection == null) {
                continue;
            }

            int stage = Integer.parseInt(stageKey);
            BigInteger unlockPrice = NumberFormat.parse(stageSection.getString("unlock-price", "0"), BigInteger.ZERO);
            List<MobEntry> mobs = new ArrayList<>();
            ConfigurationSection mobsSection = stageSection.getConfigurationSection("mobs");
            if (mobsSection != null) {
                for (String mobId : mobsSection.getKeys(false)) {
                    ConfigurationSection mobSection = mobsSection.getConfigurationSection(mobId);
                    if (mobSection == null) {
                        continue;
                    }

                    EntityType entityType;
                    try {
                        entityType = EntityType.valueOf(mobSection.getString("entity", "ZOMBIE").toUpperCase(Locale.ROOT));
                    } catch (Exception ignored) {
                        continue;
                    }

                    int weight = Math.max(1, mobSection.getInt("weight", 1));
                    BigInteger health = NumberFormat.parse(mobSection.getString("health", "100"), BigInteger.valueOf(100));
                    CurrencyBundle rewards = CurrencyBundle.of(
                            NumberFormat.parse(mobSection.getString("rewards.money", "1"), BigInteger.ONE),
                            NumberFormat.parse(mobSection.getString("rewards.souls", "0"), BigInteger.ZERO),
                            NumberFormat.parse(mobSection.getString("rewards.essence", "0"), BigInteger.ZERO),
                            NumberFormat.parse(mobSection.getString("rewards.shards", "0"), BigInteger.ZERO)
                    );
                    double scale = Math.max(0.1D, mobSection.getDouble("scale", plugin.getConfig().getDouble("gameplay.default-mob-scale", 1.0D)));
                    String rarityInput = mobSection.getString("rarity", "");
                    if (rarityInput == null || rarityInput.isBlank()) {
                        mobs.add(new MobEntry(mobId + "_common", entityType, MobRarity.COMMON, weight, scale, health, rewards));
                        mobs.add(new MobEntry(mobId + "_rare", entityType, MobRarity.RARE, weight, scale, health, rewards));
                        mobs.add(new MobEntry(mobId + "_epic", entityType, MobRarity.EPIC, weight, scale, health, rewards));
                        mobs.add(new MobEntry(mobId + "_legendary", entityType, MobRarity.LEGENDARY, weight, scale, health, rewards));
                        continue;
                    }

                    MobRarity rarity;
                    try {
                        rarity = MobRarity.valueOf(rarityInput.toUpperCase(Locale.ROOT));
                    } catch (Exception ignored) {
                        continue;
                    }

                    mobs.add(new MobEntry(mobId, entityType, rarity, weight, scale, health, rewards));
                }
            }

            list.add(new StageDefinition(stage, unlockPrice, mobs));
        }

        list.sort(Comparator.comparingInt(StageDefinition::index));
        return list;
    }

    public boolean createZone(String id, String displayName, int order, BigInteger unlockPrice) {
        String base = "zones." + id.toLowerCase(Locale.ROOT);
        if (config.isConfigurationSection(base)) {
            return false;
        }

        config.set(base + ".display-name", displayName);
        config.set(base + ".order", order);
        config.set(base + ".unlock-price", unlockPrice.toString());
        config.set(base + ".total-stages", 10);
        config.set(base + ".mobs-per-stage", 20);
        config.set(base + ".area.world", "world");
        config.set(base + ".area.min.x", 0);
        config.set(base + ".area.min.y", 0);
        config.set(base + ".area.min.z", 0);
        config.set(base + ".area.max.x", 0);
        config.set(base + ".area.max.y", 0);
        config.set(base + ".area.max.z", 0);
        for (int i = 1; i <= 10; i++) {
            config.set(base + ".stages." + i + ".unlock-price", String.valueOf((long) i * 1000L));
        }

        return save();
    }

    public boolean deleteZone(String id) {
        String base = "zones." + id.toLowerCase(Locale.ROOT);
        if (!config.isConfigurationSection(base)) {
            return false;
        }
        config.set(base, null);
        return save();
    }

    public boolean setZoneAreaPos(String id, int pos, Location location) {
        String base = "zones." + id.toLowerCase(Locale.ROOT) + ".area";
        if (!config.isConfigurationSection("zones." + id.toLowerCase(Locale.ROOT))) {
            return false;
        }

        config.set(base + ".world", location.getWorld() == null ? "world" : location.getWorld().getName());
        String node = pos == 1 ? "min" : "max";
        config.set(base + "." + node + ".x", location.getX());
        config.set(base + "." + node + ".y", location.getY());
        config.set(base + "." + node + ".z", location.getZ());
        return save();
    }

    public boolean setSpawn(String id, Location location) {
        String base = "zones." + id.toLowerCase(Locale.ROOT);
        if (!config.isConfigurationSection(base)) {
            return false;
        }

        config.set(base + ".spawn.world", location.getWorld() == null ? "world" : location.getWorld().getName());
        config.set(base + ".spawn.x", location.getX());
        config.set(base + ".spawn.y", location.getY());
        config.set(base + ".spawn.z", location.getZ());
        config.set(base + ".spawn.yaw", location.getYaw());
        config.set(base + ".spawn.pitch", location.getPitch());
        return save();
    }

    public boolean setZoneMeta(String id, String key, Object value) {
        String base = "zones." + id.toLowerCase(Locale.ROOT);
        if (!config.isConfigurationSection(base)) {
            return false;
        }
        config.set(base + "." + key, value);
        return save();
    }

    public boolean setStagePrice(String id, int stage, BigInteger price) {
        String base = "zones." + id.toLowerCase(Locale.ROOT);
        if (!config.isConfigurationSection(base)) {
            return false;
        }
        config.set(base + ".stages." + stage + ".unlock-price", price.toString());
        return save();
    }

    public boolean addMob(String zoneId, int stage, MobEntry entry) {
        String base = "zones." + zoneId.toLowerCase(Locale.ROOT);
        if (!config.isConfigurationSection(base)) {
            return false;
        }

        String mobBase = base + ".stages." + stage + ".mobs." + entry.id();
        config.set(mobBase + ".entity", entry.entityType().name());
        config.set(mobBase + ".rarity", entry.rarity().name());
        config.set(mobBase + ".weight", entry.weight());
        config.set(mobBase + ".scale", entry.scale());
        config.set(mobBase + ".health", entry.health().toString());
        config.set(mobBase + ".rewards.money", entry.rewards().money().toString());
        config.set(mobBase + ".rewards.souls", entry.rewards().souls().toString());
        config.set(mobBase + ".rewards.essence", entry.rewards().essence().toString());
        config.set(mobBase + ".rewards.shards", entry.rewards().shards().toString());
        return save();
    }

    public boolean removeMob(String zoneId, int stage, String mobId) {
        String base = "zones." + zoneId.toLowerCase(Locale.ROOT) + ".stages." + stage + ".mobs." + mobId;
        if (!config.isConfigurationSection("zones." + zoneId.toLowerCase(Locale.ROOT))) {
            return false;
        }
        config.set(base, null);
        return save();
    }

    private boolean save() {
        try {
            config.save(file);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save zones.yml: " + exception.getMessage());
            return false;
        }
    }
}
