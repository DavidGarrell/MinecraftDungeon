package de.fleaqx.minecraftDungeons.profile;

import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfileService {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;

    private final Map<UUID, PlayerProfile> profiles = new HashMap<>();

    public ProfileService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public void init() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().severe("Could not create players.yml: " + exception.getMessage());
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        loadProfiles();
    }

    public PlayerProfile profile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, PlayerProfile::new);
    }

    public void saveAll() {
        config.set("players", null);
        for (PlayerProfile profile : profiles.values()) {
            String base = "players." + profile.uuid();
            config.set(base + ".unlocked-zone-order", profile.unlockedZoneOrder());
            config.set(base + ".combat.attack-speed-multiplier", profile.attackSpeedMultiplier());
            config.set(base + ".sword.selected", profile.selectedSword());
            config.set(base + ".tool.level", profile.toolLevel());
            config.set(base + ".tool.xp", profile.toolXp().toString());

            for (CurrencyType type : CurrencyType.values()) {
                config.set(base + ".currency." + type.key(), profile.balance(type).toString());
            }

            for (Map.Entry<String, Integer> entry : profile.unlockedStagesRaw().entrySet()) {
                config.set(base + ".unlocked-stages." + entry.getKey(), entry.getValue());
            }

            for (Map.Entry<String, Integer> entry : profile.selectedStagesRaw().entrySet()) {
                config.set(base + ".selected-stages." + entry.getKey(), entry.getValue());
            }

            for (Map.Entry<Integer, Integer> sword : profile.swordLevelsRaw().entrySet()) {
                config.set(base + ".sword.levels." + sword.getKey(), sword.getValue());
            }

            for (Map.Entry<String, Integer> enchant : profile.enchantLevelsRaw().entrySet()) {
                config.set(base + ".enchants.levels." + enchant.getKey(), enchant.getValue());
            }

            for (Map.Entry<String, Boolean> enabled : profile.enchantEnabledRaw().entrySet()) {
                config.set(base + ".enchants.enabled." + enabled.getKey(), enabled.getValue());
            }

            for (Map.Entry<String, Boolean> enabled : profile.enchantMessageEnabledRaw().entrySet()) {
                config.set(base + ".enchants.messages." + enabled.getKey(), enabled.getValue());
            }
        }

        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save players.yml: " + exception.getMessage());
        }
    }

    private void loadProfiles() {
        profiles.clear();
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) {
            return;
        }

        for (String key : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (Exception ignored) {
                continue;
            }

            PlayerProfile profile = new PlayerProfile(uuid);
            String base = "players." + key;
            profile.unlockedZoneOrder(config.getInt(base + ".unlocked-zone-order", 1));
            profile.attackSpeedMultiplier(config.getDouble(base + ".combat.attack-speed-multiplier", 1.0D));
            profile.selectedSword(config.getInt(base + ".sword.selected", 1));
            profile.toolLevel(config.getInt(base + ".tool.level", 1));
            profile.toolXp(NumberFormat.parse(config.getString(base + ".tool.xp", "0"), BigInteger.ZERO));

            for (CurrencyType type : CurrencyType.values()) {
                BigInteger amount = NumberFormat.parse(config.getString(base + ".currency." + type.key(), "0"), BigInteger.ZERO);
                profile.add(type, amount);
            }

            ConfigurationSection unlockedStages = config.getConfigurationSection(base + ".unlocked-stages");
            if (unlockedStages != null) {
                for (String zoneId : unlockedStages.getKeys(false)) {
                    profile.unlockedStage(zoneId, unlockedStages.getInt(zoneId, 1));
                }
            }

            ConfigurationSection selectedStages = config.getConfigurationSection(base + ".selected-stages");
            if (selectedStages != null) {
                for (String zoneId : selectedStages.getKeys(false)) {
                    profile.selectedStage(zoneId, selectedStages.getInt(zoneId, 1));
                }
            }

            ConfigurationSection swords = config.getConfigurationSection(base + ".sword.levels");
            if (swords != null) {
                for (String swordId : swords.getKeys(false)) {
                    try {
                        int id = Integer.parseInt(swordId);
                        profile.swordLevel(id, swords.getInt(swordId, 0));
                    } catch (Exception ignored) {
                    }
                }
            }

            ConfigurationSection enchants = config.getConfigurationSection(base + ".enchants.levels");
            if (enchants != null) {
                for (String enchantId : enchants.getKeys(false)) {
                    profile.enchantLevel(enchantId, enchants.getInt(enchantId, 0));
                }
            }

            ConfigurationSection enabled = config.getConfigurationSection(base + ".enchants.enabled");
            if (enabled != null) {
                for (String enchantId : enabled.getKeys(false)) {
                    profile.enchantEnabled(enchantId, enabled.getBoolean(enchantId, true));
                }
            }

            ConfigurationSection messages = config.getConfigurationSection(base + ".enchants.messages");
            if (messages != null) {
                for (String enchantId : messages.getKeys(false)) {
                    profile.enchantMessageEnabled(enchantId, messages.getBoolean(enchantId, true));
                }
            }

            profiles.put(uuid, profile);
        }
    }
}
