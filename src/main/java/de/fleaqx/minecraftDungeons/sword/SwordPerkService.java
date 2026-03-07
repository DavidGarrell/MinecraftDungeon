package de.fleaqx.minecraftDungeons.sword;

import de.fleaqx.minecraftDungeons.profile.PlayerProfile;
import de.fleaqx.minecraftDungeons.profile.ProfileService;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class SwordPerkService {

    private static final int[] DEFAULT_LEVEL_WEIGHTS = new int[]{40, 28, 18, 10, 4};

    private final JavaPlugin plugin;
    private final ProfileService profileService;

    private int[] levelWeights = DEFAULT_LEVEL_WEIGHTS;
    private int pityRolls = 200;
    private int pityPlusRolls = 800;
    private final Map<String, PerkDefinition> perks = new LinkedHashMap<>();
    private List<ShopOffer> shopOffers = List.of(new ShopOffer(5, 50), new ShopOffer(15, 140), new ShopOffer(35, 300));

    public SwordPerkService(JavaPlugin plugin, ProfileService profileService) {
        this.plugin = plugin;
        this.profileService = profileService;
        reload();
    }

    public void reload() {
        this.levelWeights = readLevelWeights();
        this.pityRolls = Math.max(1, plugin.getConfig().getInt("sword.perks.pity-rolls", 200));
        this.pityPlusRolls = Math.max(pityRolls, plugin.getConfig().getInt("sword.perks.pity-plus-rolls", 800));

        perks.clear();
        shopOffers = loadShopOffers();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("sword.perks.definitions");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection perkSection = section.getConfigurationSection(id);
                if (perkSection == null) {
                    continue;
                }
                perks.put(id.toLowerCase(), loadPerk(id.toLowerCase(), perkSection));
            }
        }

        if (perks.isEmpty()) {
            perks.put("universal", fallbackUniversal());
        }
    }

    public List<PerkDefinition> availablePerks() {
        return List.copyOf(perks.values());
    }

    public List<ShopOffer> shopOffers() {
        return shopOffers;
    }

    public int pityRolls() {
        return pityRolls;
    }

    public int pityPlusRolls() {
        return pityPlusRolls;
    }

    public int perkPoints(Player player) {
        return profile(player).perkPoints();
    }

    public int perkRolls(Player player) {
        return profile(player).perkRolls();
    }

    public int rollsUntilPity(Player player) {
        int mod = perkRolls(player) % pityRolls;
        return mod == 0 ? pityRolls : pityRolls - mod;
    }

    public int rollsUntilPityPlus(Player player) {
        int mod = perkRolls(player) % pityPlusRolls;
        return mod == 0 ? pityPlusRolls : pityPlusRolls - mod;
    }

    public boolean buyPoints(Player player, int points, int shardCost) {
        PlayerProfile profile = profile(player);
        if (profile.balance(de.fleaqx.minecraftDungeons.currency.CurrencyType.SHARDS)
                .compareTo(java.math.BigInteger.valueOf(shardCost)) < 0) {
            return false;
        }
        profile.remove(de.fleaqx.minecraftDungeons.currency.CurrencyType.SHARDS, java.math.BigInteger.valueOf(shardCost));
        profile.addPerkPoints(points);
        return true;
    }

    public RollResult roll(Player player) {
        PlayerProfile profile = profile(player);
        if (!profile.removePerkPoints(1)) {
            return new RollResult(false, "none", "None", 0, "None", false, false);
        }

        int nextRoll = profile.perkRolls() + 1;
        boolean pity = nextRoll % pityRolls == 0;
        boolean pityPlus = nextRoll % pityPlusRolls == 0;

        int level = weightedLevel();
        if (pity) {
            level = Math.max(level, 4);
        }
        if (pityPlus) {
            level = Math.max(level, 5);
        }

        PerkDefinition perk = weightedPerk();

        profile.incrementPerkRolls();
        profile.currentPerkId(perk.id());
        profile.currentPerkLevel(level);
        return new RollResult(true, perk.id(), perk.displayName(), level, perk.rarity(), pity, pityPlus);
    }

    public String currentPerkName(Player player) {
        if (currentPerkLevel(player) <= 0) {
            return "None";
        }
        return currentPerk(player).displayName();
    }

    public int currentPerkLevel(Player player) {
        return profile(player).currentPerkLevel();
    }

    public String currentPerkRarity(Player player) {
        if (currentPerkLevel(player) <= 0) {
            return "None";
        }
        return currentPerk(player).rarity();
    }

    public double attackSpeedMultiplier(Player player) {
        return 1.0D + value(player, PerkDefinition::attackSpeed);
    }

    public double enchantProcMultiplier(Player player) {
        return 1.0D + value(player, PerkDefinition::enchantProc);
    }

    public double soulsMultiplier(Player player) {
        return 1.0D + value(player, PerkDefinition::souls);
    }

    public double swordXpMultiplier(Player player) {
        return 1.0D + value(player, PerkDefinition::swordXp);
    }

    public double moneyMultiplier(Player player) {
        return 1.0D + value(player, PerkDefinition::money);
    }

    public double essenceMultiplier(Player player) {
        return 1.0D + value(player, PerkDefinition::essence);
    }

    public String chanceText(Player player) {
        int level = currentPerkLevel(player);
        if (level <= 0 || level > levelWeights.length) {
            return ChatColor.RED + "0.000%";
        }

        int total = 0;
        for (int levelWeight : levelWeights) {
            total += Math.max(0, levelWeight);
        }
        if (total <= 0) {
            return ChatColor.RED + "0.000%";
        }

        double chance = Math.max(0, levelWeights[level - 1]) / (double) total;
        return ChatColor.RED + String.format("%.3f%%", chance * 100.0D);
    }

    public boolean setPerk(Player player, String perkId, int level) {
        PerkDefinition perk = perks.get(perkId.toLowerCase());
        if (perk == null) {
            return false;
        }
        PlayerProfile profile = profile(player);
        profile.currentPerkId(perk.id());
        profile.currentPerkLevel(Math.max(0, Math.min(5, level)));
        return true;
    }

    public void addPerkPoints(Player player, int amount) {
        profile(player).addPerkPoints(amount);
    }

    private PerkDefinition currentPerk(Player player) {
        String id = profile(player).currentPerkId();
        if (id == null) {
            return perks.values().stream().findFirst().orElse(fallbackUniversal());
        }
        return perks.getOrDefault(id.toLowerCase(), perks.values().stream().findFirst().orElse(fallbackUniversal()));
    }

    private PerkDefinition weightedPerk() {
        List<PerkDefinition> weighted = new ArrayList<>(perks.values());
        int total = 0;
        for (PerkDefinition perk : weighted) {
            total += Math.max(1, perk.weight());
        }
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        int cursor = 0;
        for (PerkDefinition perk : weighted) {
            cursor += Math.max(1, perk.weight());
            if (roll < cursor) {
                return perk;
            }
        }
        return weighted.get(0);
    }

    private int weightedLevel() {
        int total = 0;
        for (int weight : levelWeights) {
            total += Math.max(0, weight);
        }

        if (total <= 0) {
            return 1;
        }

        int roll = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        for (int i = 0; i < levelWeights.length; i++) {
            cursor += Math.max(0, levelWeights[i]);
            if (roll < cursor) {
                return i + 1;
            }
        }
        return 1;
    }

    private double value(Player player, StatGetter getter) {
        int level = currentPerkLevel(player);
        if (level <= 0) {
            return 0.0D;
        }
        double[] values = getter.values(currentPerk(player));
        int idx = Math.min(values.length, level) - 1;
        return values[idx];
    }


    private List<ShopOffer> loadShopOffers() {
        List<Map<?, ?>> rows = plugin.getConfig().getMapList("sword.perks.shop-offers");
        List<ShopOffer> offers = new ArrayList<>();
        for (Map<?, ?> row : rows) {
            Object pointsRaw = row.get("points");
            Object costRaw = row.get("cost");
            int points = pointsRaw instanceof Number number ? number.intValue() : 0;
            int cost = costRaw instanceof Number number ? number.intValue() : 0;
            if (points > 0 && cost > 0) {
                offers.add(new ShopOffer(points, cost));
            }
            if (offers.size() >= 3) {
                break;
            }
        }
        if (offers.isEmpty()) {
            offers.add(new ShopOffer(5, 50));
            offers.add(new ShopOffer(15, 140));
            offers.add(new ShopOffer(35, 300));
        }
        return List.copyOf(offers);
    }
    private int[] readLevelWeights() {
        List<Integer> list = plugin.getConfig().getIntegerList("sword.perks.level-weights");
        if (list.size() < 5) {
            return DEFAULT_LEVEL_WEIGHTS;
        }
        int[] weights = new int[5];
        for (int i = 0; i < 5; i++) {
            weights[i] = Math.max(0, list.get(i));
        }
        return weights;
    }

    private PerkDefinition loadPerk(String id, ConfigurationSection section) {
        return new PerkDefinition(
                id,
                section.getString("display-name", capitalize(id)),
                section.getString("rarity", "Common"),
                Math.max(1, section.getInt("weight", 10)),
                readValues(section, "attack-speed"),
                readValues(section, "enchant-proc"),
                readValues(section, "souls"),
                readValues(section, "sword-xp"),
                readValues(section, "money"),
                readValues(section, "essence"),
                section.getStringList("lore")
        );
    }

    private double[] readValues(ConfigurationSection section, String path) {
        List<Double> raw = section.getDoubleList(path);
        if (raw.size() < 5) {
            return new double[]{0.0D, 0.0D, 0.0D, 0.0D, 0.0D};
        }
        double[] values = new double[5];
        for (int i = 0; i < 5; i++) {
            values[i] = raw.get(i);
        }
        return values;
    }

    private String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return "Unknown";
        }
        return Character.toUpperCase(input.charAt(0)) + input.substring(1).toLowerCase();
    }

    private PerkDefinition fallbackUniversal() {
        return new PerkDefinition(
                "universal",
                "Universal",
                "Masterful",
                10,
                new double[]{0.00D, 0.00D, 0.15D, 0.20D, 0.25D},
                new double[]{0.08D, 0.16D, 0.24D, 0.32D, 0.40D},
                new double[]{0.30D, 0.60D, 0.90D, 1.20D, 1.50D},
                new double[]{0.12D, 0.24D, 0.36D, 0.48D, 0.60D},
                new double[]{0.10D, 0.20D, 0.30D, 0.40D, 0.50D},
                new double[]{0.30D, 0.60D, 0.90D, 1.20D, 1.50D},
                List.of("Default all-round perk.")
        );
    }

    private PlayerProfile profile(Player player) {
        return profileService.profile(player.getUniqueId());
    }

    public record RollResult(boolean success, String perkId, String perkName, int level, String rarity, boolean pity, boolean pityPlus) {
    }

    public record PerkDefinition(String id, String displayName, String rarity, int weight,
                                 double[] attackSpeed, double[] enchantProc, double[] souls, double[] swordXp, double[] money,
                                 double[] essence, List<String> lore) {
    }

    public record ShopOffer(int points, int cost) {
    }

    @FunctionalInterface
    private interface StatGetter {
        double[] values(PerkDefinition definition);
    }
}
