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
