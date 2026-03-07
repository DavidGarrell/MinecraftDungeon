package de.fleaqx.minecraftDungeons.companion;

import de.fleaqx.minecraftDungeons.config.ZoneConfigService;
import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.profile.PlayerProfile;
import de.fleaqx.minecraftDungeons.profile.ProfileService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import de.fleaqx.minecraftDungeons.ui.HeadItemFactory;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CompanionService {

    private static final double STAGE_POWER_FACTOR = 1.85D;
    private static final double COST_STAGE_FACTOR = 47.25D;
    private static final double BASE_COST = 800.0D;

    private final JavaPlugin plugin;
    private final ProfileService profileService;
    private final DungeonService dungeonService;
    private final Map<UUID, List<OwnedCompanion>> owned = new HashMap<>();
    private final Map<UUID, List<String>> equipped = new HashMap<>();
    private final Map<String, Location> eggLocations = new HashMap<>();
    private final File file;
    private FileConfiguration data;
    private final NamespacedKey controllerKey;

    public CompanionService(JavaPlugin plugin, ProfileService profileService, DungeonService dungeonService) {
        this.plugin = plugin;
        this.profileService = profileService;
        this.dungeonService = dungeonService;
        this.file = new File(plugin.getDataFolder(), "companions.yml");
        this.controllerKey = new NamespacedKey(plugin, "md_companion_controller");
    }

    public void init() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
        load();
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
        double raw = BASE_COST * Math.pow(COST_STAGE_FACTOR, Math.max(0, stage - 1));
        return Math.max(1L, (long) Math.floor(raw));
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

    private OwnedCompanion createCompanion(String zoneId, int stage) {
        CompanionRarity rarity = rollRarity();
        Mutation mutation = rollMutation();
        String name = switch (rarity) {
            case COMMON -> "Chicken Companion";
            case RARE -> "Pig Companion";
            case EPIC -> "Cow Companion";
            case LEGENDARY -> "Wolf Companion";
            case MYTHIC -> "Dragon Companion";
        };

        double base = rarity.baseMultiplier();
        double stageFactor = Math.pow(STAGE_POWER_FACTOR, Math.max(0, stage - 1));
        double multiplier = BigDecimal.valueOf(base * stageFactor * mutation.multiplier())
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        return new OwnedCompanion(UUID.randomUUID().toString(), zoneId.toLowerCase(), stage, name, rarity, mutation, multiplier);
    }

    private CompanionRarity rollRarity() {
        int roll = ThreadLocalRandom.current().nextInt(1000);
        if (roll < 600) return CompanionRarity.COMMON;
        if (roll < 850) return CompanionRarity.RARE;
        if (roll < 950) return CompanionRarity.EPIC;
        if (roll < 990) return CompanionRarity.LEGENDARY;
        return CompanionRarity.MYTHIC;
    }

    private Mutation rollMutation() {
        int roll = ThreadLocalRandom.current().nextInt(1000);
        if (roll < 850) return Mutation.NORMAL;
        if (roll < 950) return Mutation.GOLD;
        if (roll < 990) return Mutation.RAINBOW;
        return Mutation.DARKMATTER;
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
    }

    public enum CompanionRarity {
        COMMON(ChatColor.WHITE, 0.05D),
        RARE(ChatColor.AQUA, 0.10D),
        EPIC(ChatColor.LIGHT_PURPLE, 0.20D),
        LEGENDARY(ChatColor.GOLD, 0.40D),
        MYTHIC(ChatColor.RED, 0.80D);

        private final ChatColor color;
        private final double baseMultiplier;

        CompanionRarity(ChatColor color, double baseMultiplier) {
            this.color = color;
            this.baseMultiplier = baseMultiplier;
        }

        public ChatColor color() {
            return color;
        }

        public double baseMultiplier() {
            return baseMultiplier;
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
