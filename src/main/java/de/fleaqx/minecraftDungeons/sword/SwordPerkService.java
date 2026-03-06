package de.fleaqx.minecraftDungeons.sword;

import de.fleaqx.minecraftDungeons.profile.PlayerProfile;
import de.fleaqx.minecraftDungeons.profile.ProfileService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public class SwordPerkService {

    private static final int[] LEVEL_WEIGHTS = new int[]{40, 28, 18, 10, 4};

    private final ProfileService profileService;

    public SwordPerkService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public int perkPoints(Player player) {
        return profile(player).perkPoints();
    }

    public int perkRolls(Player player) {
        return profile(player).perkRolls();
    }

    public int rollsUntilPity(Player player) {
        int mod = perkRolls(player) % 200;
        return mod == 0 ? 200 : 200 - mod;
    }

    public int rollsUntilPityPlus(Player player) {
        int mod = perkRolls(player) % 800;
        return mod == 0 ? 800 : 800 - mod;
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
            return new RollResult(false, profile.currentPerkLevel(), rarityForLevel(profile.currentPerkLevel()), false, false);
        }

        int nextRoll = profile.perkRolls() + 1;
        boolean pity = nextRoll % 200 == 0;
        boolean pityPlus = nextRoll % 800 == 0;

        int level = weightedLevel();
        if (pity) {
            level = Math.max(level, 4);
        }
        if (pityPlus) {
            level = Math.max(level, 5);
        }

        profile.incrementPerkRolls();
        profile.currentPerkId("universal");
        profile.currentPerkLevel(level);
        return new RollResult(true, level, rarityForLevel(level), pity, pityPlus);
    }

    public String currentPerkName(Player player) {
        if (currentPerkLevel(player) <= 0) {
            return "None";
        }
        return "Universal";
    }

    public int currentPerkLevel(Player player) {
        return profile(player).currentPerkLevel();
    }

    public String currentPerkRarity(Player player) {
        return rarityForLevel(currentPerkLevel(player));
    }

    public double attackSpeedMultiplier(Player player) {
        int level = currentPerkLevel(player);
        double[] values = new double[]{0.00D, 0.00D, 0.15D, 0.20D, 0.25D};
        return 1.0D + value(values, level);
    }

    public double enchantProcMultiplier(Player player) {
        int level = currentPerkLevel(player);
        double[] values = new double[]{0.08D, 0.16D, 0.24D, 0.32D, 0.40D};
        return 1.0D + value(values, level);
    }

    public double soulsMultiplier(Player player) {
        int level = currentPerkLevel(player);
        double[] values = new double[]{0.30D, 0.60D, 0.90D, 1.20D, 1.50D};
        return 1.0D + value(values, level);
    }

    public double swordXpMultiplier(Player player) {
        int level = currentPerkLevel(player);
        double[] values = new double[]{0.12D, 0.24D, 0.36D, 0.48D, 0.60D};
        return 1.0D + value(values, level);
    }

    public double moneyMultiplier(Player player) {
        int level = currentPerkLevel(player);
        double[] values = new double[]{0.10D, 0.20D, 0.30D, 0.40D, 0.50D};
        return 1.0D + value(values, level);
    }

    public double essenceMultiplier(Player player) {
        int level = currentPerkLevel(player);
        double[] values = new double[]{0.30D, 0.60D, 0.90D, 1.20D, 1.50D};
        return 1.0D + value(values, level);
    }

    public String chanceText(Player player) {
        if (currentPerkLevel(player) <= 0) {
            return ChatColor.RED + "0.000%";
        }
        double chance = switch (currentPerkLevel(player)) {
            case 1 -> 0.40D;
            case 2 -> 0.28D;
            case 3 -> 0.18D;
            case 4 -> 0.10D;
            default -> 0.04D;
        };
        return ChatColor.RED + String.format("%.3f%%", chance * 100.0D);
    }

    private String rarityForLevel(int level) {
        return switch (level) {
            case 1 -> "Common";
            case 2 -> "Rare";
            case 3 -> "Epic";
            case 4 -> "Legendary";
            case 5 -> "Masterful";
            default -> "None";
        };
    }

    private int weightedLevel() {
        int total = 0;
        for (int weight : LEVEL_WEIGHTS) {
            total += weight;
        }

        int roll = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        for (int i = 0; i < LEVEL_WEIGHTS.length; i++) {
            cursor += LEVEL_WEIGHTS[i];
            if (roll < cursor) {
                return i + 1;
            }
        }
        return 1;
    }

    private double value(double[] values, int level) {
        if (level <= 0) {
            return 0.0D;
        }
        int idx = Math.min(values.length, level) - 1;
        return values[idx];
    }

    private PlayerProfile profile(Player player) {
        return profileService.profile(player.getUniqueId());
    }

    public record RollResult(boolean success, int level, String rarity, boolean pity, boolean pityPlus) {
    }
}
