package de.fleaqx.minecraftDungeons.profile;

import de.fleaqx.minecraftDungeons.currency.CurrencyType;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProfile {

    private final UUID uuid;
    private final EnumMap<CurrencyType, BigInteger> balances = new EnumMap<>(CurrencyType.class);
    private int unlockedZoneOrder = 1;
    private final Map<String, Integer> unlockedStages = new HashMap<>();
    private final Map<String, Integer> selectedStages = new HashMap<>();
    private double attackSpeedMultiplier = 1.0D;

    private int selectedSword = 1;
    private final Map<Integer, Integer> swordLevels = new HashMap<>();

    private int toolLevel = 1;
    private BigInteger toolXp = BigInteger.ZERO;
    private final Map<String, Integer> enchantLevels = new HashMap<>();
    private final Map<String, Boolean> enchantEnabled = new HashMap<>();
    private final Map<String, Boolean> enchantMessageEnabled = new HashMap<>();

    private int perkPoints = 0;
    private int rebirths = 0;
    private int rebirthPoints = 0;
    private int perkRolls = 0;
    private String currentPerkId = "none";
    private int currentPerkLevel = 0;

    public PlayerProfile(UUID uuid) {
        this.uuid = uuid;
        for (CurrencyType type : CurrencyType.values()) {
            balances.put(type, BigInteger.ZERO);
        }
        swordLevels.put(1, 1);
    }

    public UUID uuid() {
        return uuid;
    }

    public BigInteger balance(CurrencyType type) {
        return balances.getOrDefault(type, BigInteger.ZERO);
    }

    public void add(CurrencyType type, BigInteger amount) {
        balances.put(type, balance(type).add(amount));
    }

    public boolean remove(CurrencyType type, BigInteger amount) {
        if (balance(type).compareTo(amount) < 0) {
            return false;
        }
        balances.put(type, balance(type).subtract(amount));
        return true;
    }

    public int unlockedZoneOrder() {
        return unlockedZoneOrder;
    }

    public void unlockedZoneOrder(int unlockedZoneOrder) {
        this.unlockedZoneOrder = Math.max(1, unlockedZoneOrder);
    }

    public int unlockedStage(String zoneId) {
        return Math.max(1, unlockedStages.getOrDefault(zoneId.toLowerCase(), 1));
    }

    public void unlockedStage(String zoneId, int stage) {
        unlockedStages.put(zoneId.toLowerCase(), Math.max(1, stage));
    }

    public int selectedStage(String zoneId) {
        return Math.max(1, selectedStages.getOrDefault(zoneId.toLowerCase(), 1));
    }

    public void selectedStage(String zoneId, int stage) {
        selectedStages.put(zoneId.toLowerCase(), Math.max(1, stage));
    }

    public double attackSpeedMultiplier() {
        return attackSpeedMultiplier;
    }

    public void attackSpeedMultiplier(double attackSpeedMultiplier) {
        this.attackSpeedMultiplier = Math.max(0.1D, attackSpeedMultiplier);
    }

    public int selectedSword() {
        return selectedSword;
    }

    public void selectedSword(int selectedSword) {
        this.selectedSword = Math.max(1, selectedSword);
    }

    public int swordLevel(int swordId) {
        return Math.max(0, swordLevels.getOrDefault(swordId, swordId == 1 ? 1 : 0));
    }

    public void swordLevel(int swordId, int level) {
        swordLevels.put(swordId, Math.max(0, level));
    }

    public int toolLevel() {
        return toolLevel;
    }

    public void toolLevel(int toolLevel) {
        this.toolLevel = Math.max(1, toolLevel);
    }

    public BigInteger toolXp() {
        return toolXp;
    }

    public void toolXp(BigInteger toolXp) {
        this.toolXp = toolXp == null ? BigInteger.ZERO : toolXp.max(BigInteger.ZERO);
    }

    public void addToolXp(BigInteger amount) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
            return;
        }
        toolXp = toolXp.add(amount);
    }

    public int enchantLevel(String enchantId) {
        return Math.max(0, enchantLevels.getOrDefault(enchantId.toLowerCase(), 0));
    }

    public void enchantLevel(String enchantId, int level) {
        enchantLevels.put(enchantId.toLowerCase(), Math.max(0, level));
    }

    public boolean enchantEnabled(String enchantId) {
        return enchantEnabled.getOrDefault(enchantId.toLowerCase(), true);
    }

    public void enchantEnabled(String enchantId, boolean enabled) {
        enchantEnabled.put(enchantId.toLowerCase(), enabled);
    }

    public boolean enchantMessageEnabled(String enchantId) {
        return enchantMessageEnabled.getOrDefault(enchantId.toLowerCase(), true);
    }

    public void enchantMessageEnabled(String enchantId, boolean enabled) {
        enchantMessageEnabled.put(enchantId.toLowerCase(), enabled);
    }

    public Map<String, Integer> enchantLevelsRaw() {
        return enchantLevels;
    }

    public Map<String, Boolean> enchantEnabledRaw() {
        return enchantEnabled;
    }

    public Map<String, Boolean> enchantMessageEnabledRaw() {
        return enchantMessageEnabled;
    }

    public int perkPoints() {
        return perkPoints;
    }

    public void perkPoints(int perkPoints) {
        this.perkPoints = Math.max(0, perkPoints);
    }

    public void addPerkPoints(int amount) {
        if (amount <= 0) {
            return;
        }
        perkPoints = Math.max(0, perkPoints + amount);
    }

    public boolean removePerkPoints(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (perkPoints < amount) {
            return false;
        }
        perkPoints -= amount;
        return true;
    }

    public int perkRolls() {
        return perkRolls;
    }

    public void perkRolls(int perkRolls) {
        this.perkRolls = Math.max(0, perkRolls);
    }

    public void incrementPerkRolls() {
        perkRolls++;
    }

    public String currentPerkId() {
        return currentPerkId;
    }

    public void currentPerkId(String currentPerkId) {
        this.currentPerkId = currentPerkId == null || currentPerkId.isBlank() ? "none" : currentPerkId.toLowerCase();
    }

    public int currentPerkLevel() {
        return currentPerkLevel;
    }

    public void currentPerkLevel(int currentPerkLevel) {
        this.currentPerkLevel = Math.max(0, currentPerkLevel);
    }


    public int rebirths() {
        return rebirths;
    }

    public void rebirths(int rebirths) {
        this.rebirths = Math.max(0, rebirths);
    }

    public int rebirthPoints() {
        return rebirthPoints;
    }

    public void rebirthPoints(int rebirthPoints) {
        this.rebirthPoints = Math.max(0, rebirthPoints);
    }

    public void addRebirthPoints(int amount) {
        if (amount <= 0) {
            return;
        }
        rebirthPoints += amount;
    }

    public void resetProgressionForRebirth() {
        for (CurrencyType type : CurrencyType.values()) {
            balances.put(type, BigInteger.ZERO);
        }

        unlockedZoneOrder = 1;
        unlockedStages.clear();
        selectedStages.clear();

        selectedSword = 1;
        swordLevels.clear();
        swordLevels.put(1, 1);
    }

    public Map<Integer, Integer> swordLevelsRaw() {
        return swordLevels;
    }

    public Map<String, Integer> unlockedStagesRaw() {
        return unlockedStages;
    }

    public Map<String, Integer> selectedStagesRaw() {
        return selectedStages;
    }

    public EnumMap<CurrencyType, BigInteger> balancesRaw() {
        return balances;
    }
}
