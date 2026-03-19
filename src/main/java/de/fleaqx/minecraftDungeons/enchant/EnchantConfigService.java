package de.fleaqx.minecraftDungeons.enchant;

import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EnchantConfigService {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;

    public EnchantConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "enchants.yml");
    }

    public void init() {
        if (!file.exists()) {
            plugin.saveResource("enchants.yml", false);
        }
        reloadFile();
        mergeMissingDefaults();
    }

    public void reloadFile() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    private void mergeMissingDefaults() {
        if (plugin.getResource("enchants.yml") == null) {
            return;
        }

        FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(plugin.getResource("enchants.yml"), StandardCharsets.UTF_8)
        );

        boolean changed = false;
        ConfigurationSection liveEnchants = config.getConfigurationSection("enchants");
        ConfigurationSection defaultEnchants = defaults.getConfigurationSection("enchants");
        if (liveEnchants == null && defaultEnchants != null) {
            config.set("enchants", defaults.getConfigurationSection("enchants"));
            changed = true;
        } else if (liveEnchants != null && defaultEnchants != null) {
            for (String enchantId : defaultEnchants.getKeys(false)) {
                if (liveEnchants.contains(enchantId)) {
                    continue;
                }
                config.set("enchants." + enchantId, defaultEnchants.get(enchantId));
                changed = true;
            }
        }

        if (!config.contains("tool")) {
            config.set("tool", defaults.getConfigurationSection("tool"));
            changed = true;
        }
        if (!config.contains("limits")) {
            config.set("limits", defaults.getConfigurationSection("limits"));
            changed = true;
        }

        if (changed) {
            save();
            reloadFile();
        }
    }

    public Map<String, EnchantDefinition> load() {
        reloadFile();
        Map<String, EnchantDefinition> enchants = new HashMap<>();

        ConfigurationSection section = config.getConfigurationSection("enchants");
        if (section == null) {
            return enchants;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(id);
            if (node == null) {
                continue;
            }

            EnchantCategory category = parseCategory(node.getString("category", "SOULS"));
            Material icon = parseMaterial(node.getString("icon", "NETHER_STAR"));
            CurrencyType costCurrency = parseCurrency(node.getString("cost.currency", "SOULS"), CurrencyType.SOULS);
            CurrencyType bonusCurrency = parseCurrency(node.getString("bonus.currency", "MONEY"), CurrencyType.MONEY);

            List<String> description = node.getStringList("description");
            if (description.isEmpty()) {
                description = new ArrayList<>();
                description.add("Configurable enchant.");
            }

            EnchantDefinition def = new EnchantDefinition(
                    id.toLowerCase(Locale.ROOT),
                    node.getString("display-name", id),
                    category,
                    icon,
                    description,
                    Math.max(1, node.getInt("max-level", 1000)),
                    Math.max(1, node.getInt("required-tool-level", 1)),
                    costCurrency,
                    NumberFormat.parse(node.getString("cost.base", "1000"), BigInteger.valueOf(1000L)).max(BigInteger.ONE),
                    Math.max(1.0D, node.getDouble("cost.growth", 1.15D)),
                    Math.max(0.0D, node.getDouble("chance.base", 0.0D)),
                    Math.max(0.0D, node.getDouble("chance.per-level", 0.0001D)),
                    Math.max(0.0D, node.getDouble("damage.multiplier", 0.0D)),
                    bonusCurrency,
                    NumberFormat.parse(node.getString("bonus.amount", "0"), BigInteger.ZERO).max(BigInteger.ZERO),
                    node.getString("bonus.target-enchant", ""),
                    Math.max(0.0D, node.getDouble("passive.multiplier-per-level", 0.0D)),
                    node.getBoolean("effects.zone-burst", false),
                    node.getBoolean("effects.phantom", false),
                    node.getBoolean("effects.fire", false),
                    node.getBoolean("effects.lightning", false),
                    node.getBoolean("effects.freeze", false),
                    node.getBoolean("effects.execute", false),
                    Math.max(1, node.getInt("effects.dot-ticks", 20)),
                    Math.max(1, node.getInt("effects.fire-ticks", 40)),
                    Math.max(1, node.getInt("effects.freeze-ticks", 40)),
                    Math.max(1, node.getInt("effects.phantom-count", 3)),
                    Math.max(0.0D, node.getDouble("effects.fire-tick-multiplier", 4.0D)),
                    Math.max(0.0D, node.getDouble("effects.freeze-tick-multiplier", 1.5D)),
                    Math.max(0.0D, node.getDouble("effects.phantom-hit-multiplier", 3.0D)),
                    Math.max(0.0D, node.getDouble("effects.lightning-hit-multiplier", 7.0D)),
                    node.getBoolean("packet-based", true)
            );

            enchants.put(def.id(), def);
        }

        return enchants;
    }

    public BigInteger toolXpPerHit() {
        return NumberFormat.parse(config.getString("tool.xp-per-hit", "1"), BigInteger.ONE).max(BigInteger.ONE);
    }

    public BigInteger toolXpBaseRequirement() {
        return NumberFormat.parse(config.getString("tool.xp-base", "100"), BigInteger.valueOf(100L)).max(BigInteger.ONE);
    }

    public double toolXpGrowth() {
        return Math.max(1.0D, config.getDouble("tool.xp-growth", 1.15D));
    }

    public double maxActivationChance() {
        return Math.max(0.0D, Math.min(1.0D, config.getDouble("limits.max-activation-chance", 1.0D)));
    }

    public boolean save() {
        try {
            config.save(file);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save enchants.yml: " + exception.getMessage());
            return false;
        }
    }

    private EnchantCategory parseCategory(String value) {
        try {
            return EnchantCategory.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return EnchantCategory.SOULS;
        }
    }

    private CurrencyType parseCurrency(String value, CurrencyType fallback) {
        try {
            return CurrencyType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Material parseMaterial(String value) {
        try {
            return Material.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return Material.NETHER_STAR;
        }
    }
}
