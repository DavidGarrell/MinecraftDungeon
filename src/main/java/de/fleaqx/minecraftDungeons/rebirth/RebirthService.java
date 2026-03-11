package de.fleaqx.minecraftDungeons.rebirth;

import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.profile.PlayerProfile;
import de.fleaqx.minecraftDungeons.profile.ProfileService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class RebirthService {

    private final ProfileService profileService;
    private final DungeonService dungeonService;
    private final BigInteger baseCost;
    private final BigDecimal costGrowth;
    private final BigDecimal multiplierGrowth;

    public RebirthService(JavaPlugin plugin, ProfileService profileService, DungeonService dungeonService) {
        this.profileService = profileService;
        this.dungeonService = dungeonService;
        this.baseCost = new BigInteger(plugin.getConfig().getString("rebirth.base-cost", "100000000000000"));
        this.costGrowth = BigDecimal.valueOf(plugin.getConfig().getDouble("rebirth.cost-growth", 27.0D));
        this.multiplierGrowth = BigDecimal.valueOf(plugin.getConfig().getDouble("rebirth.multiplier-growth", 1.4D));
    }

    public int rebirths(Player player) {
        return profile(player).rebirths();
    }

    public int rebirthPoints(Player player) {
        return profile(player).rebirthPoints();
    }

    public BigInteger nextCost(Player player) {
        return nextCost(profile(player));
    }

    public double moneyMultiplier(Player player) {
        return moneyMultiplier(profile(player));
    }

    public double moneyMultiplier(PlayerProfile profile) {
        return Math.max(1.0D, scaleDouble(1.0D, multiplierGrowth, profile.rebirths()));
    }

    public double nextMoneyMultiplier(Player player) {
        PlayerProfile profile = profile(player);
        return Math.max(1.0D, scaleDouble(1.0D, multiplierGrowth, profile.rebirths() + 1));
    }

    public boolean rebirth(Player player) {
        PlayerProfile profile = profile(player);
        BigInteger cost = nextCost(profile);
        if (profile.balance(CurrencyType.MONEY).compareTo(cost) < 0) {
            return false;
        }

        profile.remove(CurrencyType.MONEY, cost);
        profile.rebirths(profile.rebirths() + 1);
        profile.addRebirthPoints(1);
        profile.resetProgressionForRebirth();
        dungeonService.resetProgressAfterRebirth(player);
        return true;
    }

    private BigInteger nextCost(PlayerProfile profile) {
        return scale(new BigDecimal(baseCost), costGrowth, profile.rebirths()).setScale(0, RoundingMode.DOWN).toBigInteger().max(BigInteger.ONE);
    }

    private PlayerProfile profile(Player player) {
        return profileService.profile(player.getUniqueId());
    }

    private BigDecimal scale(BigDecimal base, BigDecimal growth, int times) {
        BigDecimal value = base;
        for (int i = 0; i < times; i++) {
            value = value.multiply(growth);
        }
        return value;
    }

    private double scaleDouble(double base, BigDecimal growth, int times) {
        BigDecimal value = BigDecimal.valueOf(base);
        for (int i = 0; i < times; i++) {
            value = value.multiply(growth);
        }
        return value.doubleValue();
    }
}
