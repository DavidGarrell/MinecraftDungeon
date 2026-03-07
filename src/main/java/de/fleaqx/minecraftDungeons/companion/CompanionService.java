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

    private static final double DEFAULT_STAGE_POWER_FACTOR = 1.85D;
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
    private final Map<CompanionRarity, List<CompanionDefinition>> companionsByRarity = new EnumMap<>(CompanionRarity.class);
    private final List<CompanionDefinition> previewCompanions = new ArrayList<>();
    private List<WeightedRarity> rarityWeights = new ArrayList<>();
    private List<WeightedMutation> mutationWeights = new ArrayList<>();
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

    public List<CompanionDefinition> previewCompanions() {
        return List.copyOf(previewCompanions);
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
        for (Map.Entry<String, Location> entry : eggLocations.entrySet()) {
            Location target = entry.getValue();
            if (target.getWorld() == null || current.getWorld() == null || !target.getWorld().getUID().equals(current.getWorld().getUID())) {
                continue;
            }
            if (target.distanceSquared(current) <= radius * radius) {
                String[] parts = entry.getKey().split(":");
                if (parts.length == 2) {
                    try {
                        return Optional.of(new EggPoint(parts[0], Integer.parseInt(parts[1]), target));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return Optional.empty();
    }

    private void loadCompanionConfig() {
        stagePowerFactor = companionConfig.getDouble("stage-power-factor", DEFAULT_STAGE_POWER_FACTOR);
        costStageFactor = companionConfig.getDouble("cost.stage-factor", DEFAULT_COST_STAGE_FACTOR);
        baseCost = companionConfig.getDouble("cost.base", DEFAULT_BASE_COST);

        rarityWeights = loadRarityWeights(companionConfig.getConfigurationSection("rarity-chances"));
        mutationWeights = loadMutationWeights(companionConfig.getConfigurationSection("mutation-chances"));

        companionsByRarity.clear();
        for (CompanionRarity rarity : CompanionRarity.values()) {
            companionsByRarity.put(rarity, new ArrayList<>());
        }

        previewCompanions.clear();
        ConfigurationSection companionsSection = companionConfig.getConfigurationSection("companions");
        if (companionsSection != null) {
            for (String key : companionsSection.getKeys(false)) {
                String base = "companions." + key;
                CompanionRarity rarity;
                try {
                    rarity = CompanionRarity.valueOf(companionConfig.getString(base + ".rarity", "COMMON").toUpperCase(Locale.ROOT));
                } catch (Exception exception) {
                    rarity = CompanionRarity.COMMON;
                }

                String name = companionConfig.getString(base + ".name", key + " Companion");
                double baseMultiplier = companionConfig.getDouble(base + ".base-multiplier", rarity.defaultBaseMultiplier());
                Material previewMaterial = parseMaterial(companionConfig.getString(base + ".preview-material"), Material.DRAGON_EGG);
                CompanionDefinition definition = new CompanionDefinition(key.toLowerCase(Locale.ROOT), name, rarity, baseMultiplier, previewMaterial);

                companionsByRarity.get(rarity).add(definition);
                if (companionConfig.getBoolean(base + ".preview", true)) {
                    previewCompanions.add(definition);
                }
            }
        }

        if (previewCompanions.isEmpty()) {
            for (CompanionRarity rarity : CompanionRarity.values()) {
                previewCompanions.add(defaultDefinition(rarity));
            }
        }

        for (CompanionRarity rarity : CompanionRarity.values()) {
            if (companionsByRarity.getOrDefault(rarity, List.of()).isEmpty()) {
                companionsByRarity.get(rarity).add(defaultDefinition(rarity));
            }
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

    private OwnedCompanion createCompanion(String zoneId, int stage) {
        CompanionRarity rarity = rollRarity();
        Mutation mutation = rollMutation();
        CompanionDefinition definition = randomDefinition(rarity);

        double stageFactor = Math.pow(stagePowerFactor, Math.max(0, stage - 1));
        double multiplier = BigDecimal.valueOf(definition.baseMultiplier() * stageFactor * mutation.multiplier())
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        return new OwnedCompanion(UUID.randomUUID().toString(), zoneId.toLowerCase(Locale.ROOT), stage, definition.name(), rarity, mutation, multiplier);
    }

    private CompanionDefinition randomDefinition(CompanionRarity rarity) {
        List<CompanionDefinition> list = companionsByRarity.getOrDefault(rarity, List.of());
        if (list.isEmpty()) {
            return defaultDefinition(rarity);
        }
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private CompanionRarity rollRarity() {
        double total = rarityWeights.stream().mapToDouble(WeightedRarity::weight).sum();
        if (total <= 0) {
            return CompanionRarity.COMMON;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cursor = 0.0D;
        for (WeightedRarity weight : rarityWeights) {
            cursor += weight.weight();
            if (roll <= cursor) {
                return weight.rarity();
            }
        }
        return rarityWeights.getLast().rarity();
    }

    private Mutation rollMutation() {
        double total = mutationWeights.stream().mapToDouble(WeightedMutation::weight).sum();
        if (total <= 0) {
            return Mutation.NORMAL;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cursor = 0.0D;
        for (WeightedMutation weight : mutationWeights) {
            cursor += weight.weight();
            if (roll <= cursor) {
                return weight.mutation();
            }
        }
        return mutationWeights.getLast().mutation();
    }

    private CompanionDefinition defaultDefinition(CompanionRarity rarity) {
        return switch (rarity) {
            case COMMON -> new CompanionDefinition("chicken", "Chicken Companion", rarity, rarity.defaultBaseMultiplier(), Material.CHICKEN_SPAWN_EGG);
            case RARE -> new CompanionDefinition("pig", "Pig Companion", rarity, rarity.defaultBaseMultiplier(), Material.PIG_SPAWN_EGG);
            case EPIC -> new CompanionDefinition("cow", "Cow Companion", rarity, rarity.defaultBaseMultiplier(), Material.COW_SPAWN_EGG);
            case LEGENDARY -> new CompanionDefinition("wolf", "Wolf Companion", rarity, rarity.defaultBaseMultiplier(), Material.WOLF_SPAWN_EGG);
            case MYTHIC -> new CompanionDefinition("dragon", "Dragon Companion", rarity, rarity.defaultBaseMultiplier(), Material.DRAGON_EGG);
        };
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
            if (expectedLocation == null || !entry.getValue().isAt(expectedLocation)) {
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
        int stage = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        long price = costPerDraw(stage);

        Location eggLocation = baseLocation.clone().add(0.5D, 0.1D, 0.5D);
        ArmorStand egg = world.spawn(eggLocation, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSmall(true);
            stand.setCustomNameVisible(false);
            stand.getEquipment().setHelmet(new ItemStack(Material.DRAGON_EGG));
            stand.getPersistentDataContainer().set(eggEntityKey, PersistentDataType.STRING, key);
        });

        ArmorStand text = world.spawn(baseLocation.clone().add(0.5D, 2.1D, 0.5D), ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setCustomNameVisible(true);
            stand.setCustomName(
                    ChatColor.GREEN + "Zone Egg\n" +
                            ChatColor.GRAY + "Purchase a Companion that boosts\n" +
                            ChatColor.GRAY + "the amount of money you gain!\n" +
                            ChatColor.GREEN + "| Price: " + price + " Money\n" +
                            ChatColor.WHITE + "Right Click to view »"
            );
            stand.getPersistentDataContainer().set(eggEntityKey, PersistentDataType.STRING, key);
        });

        return new EggVisual(baseLocation.clone(), zoneId, stage, egg.getUniqueId(), text.getUniqueId());
    }

    private record EggVisual(Location anchor, String zoneId, int stage, UUID eggId, UUID textId) {
        static EggVisual empty(Location location) {
            return new EggVisual(location.clone(), "unknown", 1, null, null);
        }

        boolean isAt(Location location) {
            if (location.getWorld() == null || anchor.getWorld() == null) {
                return false;
            }
            return location.getWorld().getUID().equals(anchor.getWorld().getUID()) && location.distanceSquared(anchor) < 0.01D;
        }

        void remove() {
            removeEntity(eggId, anchor);
            removeEntity(textId, anchor);
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

    public record CompanionDefinition(String id, String name, CompanionRarity rarity, double baseMultiplier, Material previewMaterial) {
    }

    public enum CompanionRarity {
        COMMON(ChatColor.WHITE, 0.05D),
        RARE(ChatColor.AQUA, 0.10D),
        EPIC(ChatColor.LIGHT_PURPLE, 0.20D),
        LEGENDARY(ChatColor.GOLD, 0.40D),
        MYTHIC(ChatColor.RED, 0.80D);

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
