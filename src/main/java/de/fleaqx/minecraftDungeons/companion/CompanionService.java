package de.fleaqx.minecraftDungeons.companion;

import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.profile.PlayerProfile;
import de.fleaqx.minecraftDungeons.profile.ProfileService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import de.fleaqx.minecraftDungeons.ui.HeadItemFactory;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CompanionService {

    private static final double DEFAULT_STAGE_POWER_FACTOR = 1.852D;
    private static final double DEFAULT_COST_STAGE_FACTOR = 47.25D;
    private static final double DEFAULT_BASE_COST = 800.0D;

    private final JavaPlugin plugin;
    private final ProfileService profileService;
    private final DungeonService dungeonService;
    private final Map<UUID, List<OwnedCompanion>> owned = new HashMap<>();
    private final Map<UUID, List<String>> equipped = new HashMap<>();
    private final Map<String, Location> eggLocations = new HashMap<>();
    private final File file;
    private final File companionConfigFile;
    private FileConfiguration data;
    private FileConfiguration companionConfig;
    private final NamespacedKey controllerKey;
    private final NamespacedKey eggEntityKey;
    private final Map<String, EggVisual> eggVisuals = new HashMap<>();
    private CompanionPool defaultPool = CompanionPool.fallback();
    private final Map<String, CompanionPool> zonePools = new HashMap<>();
    private double stagePowerFactor = DEFAULT_STAGE_POWER_FACTOR;
    private double costStageFactor = DEFAULT_COST_STAGE_FACTOR;
    private double baseCost = DEFAULT_BASE_COST;
    private BukkitTask eggVisualTask;

    public CompanionService(JavaPlugin plugin, ProfileService profileService, DungeonService dungeonService) {
        this.plugin = plugin;
        this.profileService = profileService;
        this.dungeonService = dungeonService;
        this.file = new File(plugin.getDataFolder(), "companions.yml");
        this.companionConfigFile = new File(plugin.getDataFolder(), "companions-config.yml");
        this.controllerKey = new NamespacedKey(plugin, "md_companion_controller");
        this.eggEntityKey = new NamespacedKey(plugin, "md_companion_egg");
    }

    public void init() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        if (!companionConfigFile.exists()) {
            plugin.saveResource("companions-config.yml", false);
        }
        this.companionConfig = YamlConfiguration.loadConfiguration(companionConfigFile);
        loadCompanionConfig();

        data = YamlConfiguration.loadConfiguration(file);
        load();
        startEggVisuals();
    }

    public void save() {
        if (data == null) {
            return;
        }
        data.set("players", null);
        data.set("egg-locations", null);

        for (Map.Entry<UUID, List<OwnedCompanion>> entry : owned.entrySet()) {
            String base = "players." + entry.getKey();
            List<String> serialized = new ArrayList<>();
            for (OwnedCompanion companion : entry.getValue()) {
                serialized.add(companion.serialize());
            }
            data.set(base + ".owned", serialized);
            data.set(base + ".equipped", equipped.getOrDefault(entry.getKey(), List.of()));
        }

        for (Map.Entry<String, Location> entry : eggLocations.entrySet()) {
            Location loc = entry.getValue();
            if (loc.getWorld() == null) {
                continue;
            }
            String base = "egg-locations." + entry.getKey();
            data.set(base + ".world", loc.getWorld().getName());
            data.set(base + ".x", loc.getX());
            data.set(base + ".y", loc.getY());
            data.set(base + ".z", loc.getZ());
            data.set(base + ".yaw", loc.getYaw());
            data.set(base + ".pitch", loc.getPitch());
        }

        try {
            data.save(file);
        } catch (IOException ignored) {
        }
    }

    public void ensureControllerInSlot(Player player) {
        ItemStack current = player.getInventory().getItem(1);
        if (isController(current)) {
            return;
        }
        ItemStack item = HeadItemFactory.head(
                "http://textures.minecraft.net/texture/15ca7f9f8e95ea4481f34f21403ff78703864c8dd56a7dca0ce12f5688d2f952",
                ChatColor.LIGHT_PURPLE + "Companions",
                List.of(ChatColor.GRAY + "Right click to open companion UI")
        );
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(controllerKey, PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
        }
        player.getInventory().setItem(1, item);
    }

    public boolean isController(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(controllerKey, PersistentDataType.INTEGER);
    }

    public long costPerDraw(int stage) {
        double raw = baseCost * Math.pow(costStageFactor, Math.max(0, stage - 1));
        return Math.max(1L, (long) Math.floor(raw));
    }

    public List<CompanionDefinition> previewCompanions(String zoneId) {
        return pool(zoneId).previewCompanions();
    }

    public int maxEquipSlots(Player player) {
        int slots = 3;
        if (player.hasPermission("minecraftdungeons.companions.extra.1")) slots++;
        if (player.hasPermission("minecraftdungeons.companions.extra.2")) slots++;
        if (player.hasPermission("minecraftdungeons.companions.extra.3")) slots++;
        return slots;
    }

    public List<OwnedCompanion> owned(Player player) {
        return owned.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
    }

    public List<OwnedCompanion> equipped(Player player) {
        List<String> ids = equipped.getOrDefault(player.getUniqueId(), new ArrayList<>());
        List<OwnedCompanion> list = new ArrayList<>();
        for (String id : ids) {
            owned(player).stream().filter(companion -> companion.id().equals(id)).findFirst().ifPresent(list::add);
        }
        return list;
    }

    public double moneyMultiplier(Player player) {
        double value = 1.0D;
        for (OwnedCompanion companion : equipped(player)) {
            value += companion.multiplier();
        }
        return value;
    }

    public RollBatchResult roll(Player player, String zoneId, int stage, int amount) {
        amount = Math.max(1, amount);
        long singleCost = costPerDraw(stage);
        BigInteger totalCost = BigInteger.valueOf(singleCost).multiply(BigInteger.valueOf(amount));
        PlayerProfile profile = profileService.profile(player.getUniqueId());
        if (profile.balance(CurrencyType.MONEY).compareTo(totalCost) < 0) {
            return RollBatchResult.failed(totalCost);
        }
        profile.remove(CurrencyType.MONEY, totalCost);

        List<OwnedCompanion> result = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            OwnedCompanion companion = createCompanion(zoneId, stage);
            owned(player).add(companion);
            result.add(companion);
        }
        save();
        return RollBatchResult.success(totalCost, result);
    }

    public void equip(Player player, String companionId) {
        List<String> ids = equipped.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
        if (ids.contains(companionId)) {
            ids.remove(companionId);
            return;
        }
        if (ids.size() >= maxEquipSlots(player)) {
            return;
        }
        ids.add(companionId);
    }

    public void equipBest(Player player) {
        List<OwnedCompanion> sorted = new ArrayList<>(owned(player));
        sorted.sort(Comparator.comparingDouble(OwnedCompanion::multiplier).reversed());
        int max = maxEquipSlots(player);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < Math.min(max, sorted.size()); i++) {
            ids.add(sorted.get(i).id());
        }
        equipped.put(player.getUniqueId(), ids);
        save();
    }

    public int deleteCompanions(Player player, Collection<String> companionIds) {
        if (companionIds == null || companionIds.isEmpty()) {
            return 0;
        }

        Set<String> idSet = new HashSet<>(companionIds);
        List<OwnedCompanion> ownedCompanions = owned(player);
        int before = ownedCompanions.size();
        ownedCompanions.removeIf(companion -> idSet.contains(companion.id()));

        List<String> equippedIds = equipped.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
        equippedIds.removeIf(idSet::contains);

        int deleted = before - ownedCompanions.size();
        if (deleted > 0) {
            save();
        }
        return deleted;
    }

    public int deleteByRarity(Player player, CompanionRarity rarity) {
        List<String> ids = owned(player).stream()
                .filter(companion -> companion.rarity() == rarity)
                .map(OwnedCompanion::id)
                .toList();
        return deleteCompanions(player, ids);
    }

    public int deleteByZone(Player player, String zoneId) {
        String normalized = zoneId == null ? "" : zoneId.toLowerCase(Locale.ROOT);
        List<String> ids = owned(player).stream()
                .filter(companion -> companion.zoneId().equalsIgnoreCase(normalized))
                .map(OwnedCompanion::id)
                .toList();
        return deleteCompanions(player, ids);
    }

    public Optional<Location> eggLocation(String zoneId, int stage) {
        return Optional.ofNullable(eggLocations.get((zoneId + ":" + stage).toLowerCase()));
    }

    public void setEggLocation(String zoneId, int stage, Location location) {
        eggLocations.put((zoneId + ":" + stage).toLowerCase(), location.clone());
        save();
        refreshEggVisuals();
    }

    public Collection<EggPoint> eggPoints() {
        List<EggPoint> points = new ArrayList<>();
        for (Map.Entry<String, Location> entry : eggLocations.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                points.add(new EggPoint(parts[0], Integer.parseInt(parts[1]), entry.getValue().clone()));
            } catch (NumberFormatException ignored) {
            }
        }
        return points;
    }

    public Optional<EggPoint> eggPointByEntity(Entity entity) {
        if (entity == null || !entity.getPersistentDataContainer().has(eggEntityKey, PersistentDataType.STRING)) {
            return Optional.empty();
        }
        String key = entity.getPersistentDataContainer().get(eggEntityKey, PersistentDataType.STRING);
        if (key == null) {
            return Optional.empty();
        }
        String[] parts = key.split(":");
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new EggPoint(parts[0], Integer.parseInt(parts[1]), entity.getLocation()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public void shutdown() {
        if (eggVisualTask != null) {
            eggVisualTask.cancel();
            eggVisualTask = null;
        }
        for (EggVisual visual : eggVisuals.values()) {
            visual.remove();
        }
        eggVisuals.clear();
    }

    public Optional<EggPoint> nearbyEgg(Player player, double radius) {
        Location current = player.getLocation();
        EggPoint nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, Location> entry : eggLocations.entrySet()) {
            Location target = entry.getValue();
            if (target.getWorld() == null || current.getWorld() == null || !target.getWorld().getUID().equals(current.getWorld().getUID())) {
                continue;
            }
            double distanceSquared = target.distanceSquared(current);
            if (distanceSquared > radius * radius) {
                continue;
            }
            String[] parts = entry.getKey().split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                EggPoint candidate = new EggPoint(parts[0], Integer.parseInt(parts[1]), target);
                if (distanceSquared < nearestDistance) {
                    nearest = candidate;
                    nearestDistance = distanceSquared;
                }
            } catch (Exception ignored) {
            }
        }
        return Optional.ofNullable(nearest);
    }

    public Optional<EggPoint> eggPointAtBlock(Block block) {
        if (block == null) {
            return Optional.empty();
        }
        Location blockLocation = block.getLocation();
        for (Map.Entry<String, Location> entry : eggLocations.entrySet()) {
            Location configured = entry.getValue();
            if (configured.getWorld() == null || blockLocation.getWorld() == null) {
                continue;
            }
            if (!configured.getWorld().getUID().equals(blockLocation.getWorld().getUID())) {
                continue;
            }
            if (configured.getBlockX() != blockLocation.getBlockX()
                    || configured.getBlockY() != blockLocation.getBlockY()
                    || configured.getBlockZ() != blockLocation.getBlockZ()) {
                continue;
            }
            String[] parts = entry.getKey().split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                return Optional.of(new EggPoint(parts[0], Integer.parseInt(parts[1]), configured.clone()));
            } catch (NumberFormatException ignored) {
            }
        }
        return Optional.empty();
    }

    private void loadCompanionConfig() {
        stagePowerFactor = companionConfig.getDouble("stage-power-factor", DEFAULT_STAGE_POWER_FACTOR);
        costStageFactor = companionConfig.getDouble("cost.stage-factor", DEFAULT_COST_STAGE_FACTOR);
        baseCost = companionConfig.getDouble("cost.base", DEFAULT_BASE_COST);

        defaultPool = readPool(companionConfig, null);
        zonePools.clear();

        ConfigurationSection zonesSection = companionConfig.getConfigurationSection("zones");
        if (zonesSection == null) {
            return;
        }

        for (String zoneId : zonesSection.getKeys(false)) {
            ConfigurationSection zoneSection = zonesSection.getConfigurationSection(zoneId);
            if (zoneSection == null) {
                continue;
            }
            zonePools.put(zoneId.toLowerCase(Locale.ROOT), readPool(zoneSection, defaultPool));
        }
    }

    private CompanionPool readPool(ConfigurationSection section, CompanionPool fallback) {
        List<WeightedRarity> rarities = section.contains("rarity-chances")
                ? loadRarityWeights(section.getConfigurationSection("rarity-chances"))
                : (fallback == null ? loadRarityWeights(null) : fallback.rarityWeights());
        List<WeightedMutation> mutations = section.contains("mutation-chances")
                ? loadMutationWeights(section.getConfigurationSection("mutation-chances"))
                : (fallback == null ? loadMutationWeights(null) : fallback.mutationWeights());

        Map<CompanionRarity, List<CompanionDefinition>> definitions = new EnumMap<>(CompanionRarity.class);
        for (CompanionRarity rarity : CompanionRarity.values()) {
            definitions.put(rarity, new ArrayList<>());
        }

        List<CompanionDefinition> previews = new ArrayList<>();
        ConfigurationSection companionsSection = section.getConfigurationSection("companions");
        if (companionsSection != null && !companionsSection.getKeys(false).isEmpty()) {
            for (String key : companionsSection.getKeys(false)) {
                String base = "companions." + key;
                CompanionRarity rarity = parseRarity(section.getString(base + ".rarity", "COMMON"));
                String name = section.getString(base + ".name", key + " Companion");
                double baseMultiplier = section.getDouble(base + ".base-multiplier", rarity.defaultBaseMultiplier());
                Material previewMaterial = parseMaterial(section.getString(base + ".preview-material"), Material.DRAGON_EGG);
                CompanionDefinition definition = new CompanionDefinition(key.toLowerCase(Locale.ROOT), name, rarity, baseMultiplier, previewMaterial);
                definitions.get(rarity).add(definition);
                if (section.getBoolean(base + ".preview", true)) {
                    previews.add(definition);
                }
            }
        } else if (fallback != null) {
            for (CompanionRarity rarity : CompanionRarity.values()) {
                definitions.get(rarity).addAll(fallback.companionsByRarity().getOrDefault(rarity, List.of()));
            }
            previews.addAll(fallback.previewCompanions());
        }

        if (previews.isEmpty()) {
            for (CompanionRarity rarity : CompanionRarity.values()) {
                previews.add(defaultDefinition(rarity));
            }
        }

        for (CompanionRarity rarity : CompanionRarity.values()) {
            if (definitions.get(rarity).isEmpty()) {
                if (fallback != null && !fallback.companionsByRarity().getOrDefault(rarity, List.of()).isEmpty()) {
                    definitions.get(rarity).addAll(fallback.companionsByRarity().get(rarity));
                } else {
                    definitions.get(rarity).add(defaultDefinition(rarity));
                }
            }
        }

        return new CompanionPool(
                List.copyOf(rarities),
                List.copyOf(mutations),
                definitions.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue()),
                        (a, b) -> b,
                        () -> new EnumMap<>(CompanionRarity.class)
                )),
                List.copyOf(previews)
        );
    }

    private CompanionRarity parseRarity(String raw) {
        try {
            return CompanionRarity.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return CompanionRarity.COMMON;
        }
    }

    private List<WeightedRarity> loadRarityWeights(ConfigurationSection section) {
        List<WeightedRarity> list = new ArrayList<>();
        if (section == null) {
            list.add(new WeightedRarity(CompanionRarity.COMMON, 600));
            list.add(new WeightedRarity(CompanionRarity.RARE, 250));
            list.add(new WeightedRarity(CompanionRarity.EPIC, 100));
            list.add(new WeightedRarity(CompanionRarity.LEGENDARY, 40));
            list.add(new WeightedRarity(CompanionRarity.MYTHIC, 10));
            return list;
        }
        for (CompanionRarity rarity : CompanionRarity.values()) {
            double weight = section.getDouble(rarity.name(), 0.0D);
            if (weight > 0) {
                list.add(new WeightedRarity(rarity, weight));
            }
        }
        if (list.isEmpty()) {
            return loadRarityWeights(null);
        }
        return list;
    }

    private List<WeightedMutation> loadMutationWeights(ConfigurationSection section) {
        List<WeightedMutation> list = new ArrayList<>();
        if (section == null) {
            list.add(new WeightedMutation(Mutation.NORMAL, 850));
            list.add(new WeightedMutation(Mutation.GOLD, 100));
            list.add(new WeightedMutation(Mutation.RAINBOW, 40));
            list.add(new WeightedMutation(Mutation.DARKMATTER, 10));
            return list;
        }
        for (Mutation mutation : Mutation.values()) {
            double weight = section.getDouble(mutation.name(), 0.0D);
            if (weight > 0) {
                list.add(new WeightedMutation(mutation, weight));
            }
        }
        if (list.isEmpty()) {
            return loadMutationWeights(null);
        }
        return list;
    }

    private Material parseMaterial(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
        return parsed == null ? fallback : parsed;
    }

    private static CompanionDefinition defaultDefinitionStatic(CompanionRarity rarity) {
        return switch (rarity) {
            case COMMON -> new CompanionDefinition("chicken", "Chicken Companion", rarity, rarity.defaultBaseMultiplier(), Material.CHICKEN_SPAWN_EGG);
            case RARE -> new CompanionDefinition("pig", "Pig Companion", rarity, rarity.defaultBaseMultiplier(), Material.PIG_SPAWN_EGG);
            case EPIC -> new CompanionDefinition("cow", "Cow Companion", rarity, rarity.defaultBaseMultiplier(), Material.COW_SPAWN_EGG);
            case LEGENDARY -> new CompanionDefinition("wolf", "Wolf Companion", rarity, rarity.defaultBaseMultiplier(), Material.WOLF_SPAWN_EGG);
            case MYTHIC -> new CompanionDefinition("dragon", "Dragon Companion", rarity, rarity.defaultBaseMultiplier(), Material.DRAGON_EGG);
        };
    }

    private OwnedCompanion createCompanion(String zoneId, int stage) {
        CompanionPool pool = pool(zoneId);
        CompanionRarity rarity = rollRarity(pool.rarityWeights());
        Mutation mutation = rollMutation(pool.mutationWeights());
        CompanionDefinition definition = randomDefinition(pool.companionsByRarity(), rarity);

        double stageFactor = Math.pow(stagePowerFactor, Math.max(0, stage - 1));
        double multiplier = BigDecimal.valueOf(definition.baseMultiplier() * stageFactor * mutation.multiplier())
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        return new OwnedCompanion(UUID.randomUUID().toString(), zoneId.toLowerCase(Locale.ROOT), stage, definition.name(), rarity, mutation, multiplier);
    }

    private CompanionDefinition randomDefinition(Map<CompanionRarity, List<CompanionDefinition>> companionPools, CompanionRarity rarity) {
        List<CompanionDefinition> list = companionPools.getOrDefault(rarity, List.of());
        if (list.isEmpty()) {
            return defaultDefinition(rarity);
        }
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private CompanionRarity rollRarity(List<WeightedRarity> configuredWeights) {
        double total = configuredWeights.stream().mapToDouble(WeightedRarity::weight).sum();
        if (total <= 0) {
            return CompanionRarity.COMMON;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cursor = 0.0D;
        for (WeightedRarity weight : configuredWeights) {
            cursor += weight.weight();
            if (roll <= cursor) {
                return weight.rarity();
            }
        }
        return configuredWeights.getLast().rarity();
    }

    private Mutation rollMutation(List<WeightedMutation> configuredWeights) {
        double total = configuredWeights.stream().mapToDouble(WeightedMutation::weight).sum();
        if (total <= 0) {
            return Mutation.NORMAL;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cursor = 0.0D;
        for (WeightedMutation weight : configuredWeights) {
            cursor += weight.weight();
            if (roll <= cursor) {
                return weight.mutation();
            }
        }
        return configuredWeights.getLast().mutation();
    }

    private CompanionPool pool(String zoneId) {
        if (zoneId == null) {
            return defaultPool;
        }
        return zonePools.getOrDefault(zoneId.toLowerCase(Locale.ROOT), defaultPool);
    }

    private CompanionDefinition defaultDefinition(CompanionRarity rarity) {
        return defaultDefinitionStatic(rarity);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void load() {
        owned.clear();
        equipped.clear();
        eggLocations.clear();

        ConfigurationSection players = data.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    List<OwnedCompanion> list = new ArrayList<>();
                    for (String row : data.getStringList("players." + key + ".owned")) {
                        OwnedCompanion.deserialize(row).ifPresent(list::add);
                    }
                    owned.put(uuid, list);
                    equipped.put(uuid, new ArrayList<>(data.getStringList("players." + key + ".equipped")));
                } catch (Exception ignored) {
                }
            }
        }

        ConfigurationSection points = data.getConfigurationSection("egg-locations");
        if (points != null) {
            for (String key : points.getKeys(false)) {
                String base = "egg-locations." + key;
                String world = data.getString(base + ".world");
                if (world == null || plugin.getServer().getWorld(world) == null) {
                    continue;
                }
                eggLocations.put(key.toLowerCase(), new Location(
                        plugin.getServer().getWorld(world),
                        data.getDouble(base + ".x"),
                        data.getDouble(base + ".y"),
                        data.getDouble(base + ".z"),
                        (float) data.getDouble(base + ".yaw"),
                        (float) data.getDouble(base + ".pitch")
                ));
            }
        }

        refreshEggVisuals();
    }

    private void startEggVisuals() {
        if (eggVisualTask != null) {
            eggVisualTask.cancel();
        }
        refreshEggVisuals();
        eggVisualTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshEggVisuals, 40L, 100L);
    }

    private void refreshEggVisuals() {
        Map<String, Location> expected = new HashMap<>();
        for (EggPoint point : eggPoints()) {
            expected.put((point.zoneId() + ":" + point.stage()).toLowerCase(), point.location());
        }

        Iterator<Map.Entry<String, EggVisual>> iterator = eggVisuals.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, EggVisual> entry = iterator.next();
            Location expectedLocation = expected.get(entry.getKey());
            if (expectedLocation == null || !entry.getValue().isAt(expectedLocation) || !entry.getValue().isRenderable()) {
                entry.getValue().remove();
                iterator.remove();
            }
        }

        for (Map.Entry<String, Location> entry : expected.entrySet()) {
            eggVisuals.computeIfAbsent(entry.getKey(), key -> spawnEggVisual(key, entry.getValue()));
        }
    }

    private EggVisual spawnEggVisual(String key, Location baseLocation) {
        World world = baseLocation.getWorld();
        if (world == null) {
            return EggVisual.empty(baseLocation);
        }

        String[] parts = key.split(":");
        String zoneId = parts.length > 0 ? parts[0] : "unknown";
        int stage = 1;
        if (parts.length > 1) {
            try {
                stage = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                stage = 1;
            }
        }
        long price = costPerDraw(Math.max(1, stage));

        Location blockLocation = baseLocation.clone();
        clearTeleportedEggs(blockLocation);
        blockLocation.getBlock().setType(Material.DRAGON_EGG, false);

        List<UUID> textIds = new ArrayList<>();
        List<String> lines = List.of(
                ChatColor.GREEN + "Zone Egg",
                ChatColor.YELLOW + "[" + capitalize(zoneId) + " " + (stage > 0 ? "Stage " + stage : "Auto Stage") + "]",
                ChatColor.WHITE + "Purchase a Companion that boosts",
                ChatColor.WHITE + "the amount of money you gain!",
                ChatColor.GREEN + "| Price: " + price + " Money",
                ChatColor.GRAY + "" + ChatColor.ITALIC + "\u00ab Right Click to view \u00bb"
        );

        double startY = 2.95D;
        for (int i = 0; i < lines.size(); i++) {
            int index = i;
            double y = startY - (index * 0.27D);
            ArmorStand textLine = world.spawn(baseLocation.clone().add(0.5D, y, 0.5D), ArmorStand.class, stand -> {
                stand.setInvisible(true);
                stand.setInvulnerable(true);
                stand.setPersistent(false);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setSmall(true);
                stand.setCustomNameVisible(true);
                stand.setCustomName(lines.get(index));
                stand.getPersistentDataContainer().set(eggEntityKey, PersistentDataType.STRING, key);
            });
            textIds.add(textLine.getUniqueId());
        }

        return new EggVisual(baseLocation.clone(), zoneId, stage, List.copyOf(textIds));
    }

    private void clearTeleportedEggs(Location anchor) {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        int baseX = anchor.getBlockX();
        int baseY = anchor.getBlockY();
        int baseZ = anchor.getBlockZ();
        for (int x = baseX - 3; x <= baseX + 3; x++) {
            for (int y = baseY - 2; y <= baseY + 2; y++) {
                for (int z = baseZ - 3; z <= baseZ + 3; z++) {
                    if (x == baseX && y == baseY && z == baseZ) {
                        continue;
                    }
                    Location candidate = new Location(world, x, y, z);
                    if (candidate.getBlock().getType() == Material.DRAGON_EGG) {
                        candidate.getBlock().setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private record EggVisual(Location anchor, String zoneId, int stage, List<UUID> textIds) {
        static EggVisual empty(Location location) {
            return new EggVisual(location.clone(), "unknown", 1, List.of());
        }

        boolean isAt(Location location) {
            if (location.getWorld() == null || anchor.getWorld() == null) {
                return false;
            }
            return location.getWorld().getUID().equals(anchor.getWorld().getUID()) && location.distanceSquared(anchor) < 0.01D;
        }

        boolean isRenderable() {
            if (anchor.getWorld() == null) {
                return false;
            }
            for (UUID textId : textIds) {
                Entity entity = anchor.getWorld().getEntity(textId);
                if (entity == null || !entity.isValid()) {
                    return false;
                }
            }
            return true;
        }

        void remove() {
            for (UUID textId : textIds) {
                removeEntity(textId, anchor);
            }
            if (anchor.getWorld() != null) {
                Location block = anchor.clone();
                if (block.getBlock().getType() == Material.DRAGON_EGG) {
                    block.getBlock().setType(Material.AIR, false);
                }
            }
        }

        private static void removeEntity(UUID id, Location anchor) {
            if (id == null || anchor.getWorld() == null) {
                return;
            }
            Entity entity = anchor.getWorld().getEntity(id);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
    }

    private record WeightedRarity(CompanionRarity rarity, double weight) {
    }

    private record WeightedMutation(Mutation mutation, double weight) {
    }

    private record CompanionPool(List<WeightedRarity> rarityWeights,
                                 List<WeightedMutation> mutationWeights,
                                 Map<CompanionRarity, List<CompanionDefinition>> companionsByRarity,
                                 List<CompanionDefinition> previewCompanions) {
        static CompanionPool fallback() {
            EnumMap<CompanionRarity, List<CompanionDefinition>> byRarity = new EnumMap<>(CompanionRarity.class);
            List<CompanionDefinition> previews = new ArrayList<>();
            for (CompanionRarity rarity : CompanionRarity.values()) {
                CompanionDefinition definition = defaultDefinitionStatic(rarity);
                byRarity.put(rarity, List.of(definition));
                previews.add(definition);
            }
            return new CompanionPool(
                    List.of(
                            new WeightedRarity(CompanionRarity.COMMON, 600),
                            new WeightedRarity(CompanionRarity.RARE, 250),
                            new WeightedRarity(CompanionRarity.EPIC, 100),
                            new WeightedRarity(CompanionRarity.LEGENDARY, 40),
                            new WeightedRarity(CompanionRarity.MYTHIC, 10)
                    ),
                    List.of(
                            new WeightedMutation(Mutation.NORMAL, 850),
                            new WeightedMutation(Mutation.GOLD, 100),
                            new WeightedMutation(Mutation.RAINBOW, 40),
                            new WeightedMutation(Mutation.DARKMATTER, 10)
                    ),
                    byRarity,
                    List.copyOf(previews)
            );
        }
    }

    public record CompanionDefinition(String id, String name, CompanionRarity rarity, double baseMultiplier, Material previewMaterial) {
    }

    public enum CompanionRarity {
        COMMON(ChatColor.WHITE, 0.05D),
        RARE(ChatColor.AQUA, 0.075D),
        EPIC(ChatColor.LIGHT_PURPLE, 0.10D),
        LEGENDARY(ChatColor.GOLD, 0.15D),
        MYTHIC(ChatColor.RED, 0.25D);

        private final ChatColor color;
        private final double defaultBaseMultiplier;

        CompanionRarity(ChatColor color, double defaultBaseMultiplier) {
            this.color = color;
            this.defaultBaseMultiplier = defaultBaseMultiplier;
        }

        public ChatColor color() {
            return color;
        }

        public double defaultBaseMultiplier() {
            return defaultBaseMultiplier;
        }
    }

    public enum Mutation {
        NORMAL(ChatColor.GRAY, 1.0D),
        GOLD(ChatColor.GOLD, 1.5D),
        RAINBOW(ChatColor.LIGHT_PURPLE, 2.0D),
        DARKMATTER(ChatColor.DARK_PURPLE, 4.0D);

        private final ChatColor color;
        private final double multiplier;

        Mutation(ChatColor color, double multiplier) {
            this.color = color;
            this.multiplier = multiplier;
        }

        public ChatColor color() {
            return color;
        }

        public double multiplier() {
            return multiplier;
        }
    }

    public record OwnedCompanion(String id, String zoneId, int stage, String name, CompanionRarity rarity, Mutation mutation,
                                 double multiplier) {
        public String serialize() {
            return String.join(";", id, zoneId, String.valueOf(stage), name, rarity.name(), mutation.name(), String.valueOf(multiplier));
        }

        public static Optional<OwnedCompanion> deserialize(String row) {
            String[] split = row.split(";");
            if (split.length < 7) {
                return Optional.empty();
            }
            try {
                return Optional.of(new OwnedCompanion(
                        split[0], split[1], Integer.parseInt(split[2]), split[3],
                        CompanionRarity.valueOf(split[4]), Mutation.valueOf(split[5]), Double.parseDouble(split[6])
                ));
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
    }

    public record RollBatchResult(boolean success, BigInteger totalCost, List<OwnedCompanion> companions) {
        public static RollBatchResult failed(BigInteger cost) {
            return new RollBatchResult(false, cost, List.of());
        }

        public static RollBatchResult success(BigInteger cost, List<OwnedCompanion> companions) {
            return new RollBatchResult(true, cost, companions);
        }
    }

    public record EggPoint(String zoneId, int stage, Location location) {
    }
}

