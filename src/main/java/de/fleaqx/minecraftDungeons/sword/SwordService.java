package de.fleaqx.minecraftDungeons.sword;

import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.enchant.EnchantDefinition;
import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import de.fleaqx.minecraftDungeons.profile.PlayerProfile;
import de.fleaqx.minecraftDungeons.profile.ProfileService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class SwordService {

    public static final int MAX_SWORDS = 100;
    public static final int MAX_TIERS = 5;

    private final JavaPlugin plugin;
    private final ProfileService profileService;
    private final EnchantService enchantService;
    private final SwordFormulaService formula;
    private final NamespacedKey swordKey;
    private final List<SwordDefinition> definitions = new ArrayList<>();

    public SwordService(JavaPlugin plugin, ProfileService profileService, EnchantService enchantService) {
        this.plugin = plugin;
        this.profileService = profileService;
        this.enchantService = enchantService;
        this.formula = new SwordFormulaService(
                plugin.getConfig().getDouble("sword.formula.base-damage", 1.0D),
                plugin.getConfig().getDouble("sword.formula.level-damage-growth", 2.916D),
                plugin.getConfig().getDouble("sword.formula.base-price", 200.0D),
                plugin.getConfig().getDouble("sword.formula.level-price-growth", 3.4D),
                MAX_TIERS
        );
        this.swordKey = new NamespacedKey(plugin, "md_sword");
        bootstrapDefinitions();
    }

    public void ensureSwordInSlot(Player player) {
        PlayerProfile profile = profile(player);
        int swordId = profile.selectedSword();
        int tier = Math.max(1, profile.swordLevel(swordId));
        ItemStack item = createSwordItem(player, swordId, tier, true);
        player.getInventory().setItem(0, item);
    }

    public boolean isManagedSword(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(swordKey, PersistentDataType.INTEGER);
    }

    public BigInteger currentDamage(Player player) {
        PlayerProfile profile = profile(player);
        int swordId = profile.selectedSword();
        int tier = Math.max(1, profile.swordLevel(swordId));
        return formula.damage(swordId, tier);
    }

    public int selectedSword(Player player) {
        return profile(player).selectedSword();
    }

    public int swordTier(Player player, int swordId) {
        return profile(player).swordLevel(swordId);
    }

    public BigInteger tierPrice(int swordId, int targetTier) {
        return formula.price(swordId, targetTier);
    }

    public BigInteger tierDamage(int swordId, int tier) {
        return formula.damage(swordId, tier);
    }

    public boolean unlockOrSelect(Player player, int swordId, int targetTier) {
        PlayerProfile profile = profile(player);
        int current = profile.swordLevel(swordId);
        if (targetTier <= current) {
            profile.selectedSword(swordId);
            ensureSwordInSlot(player);
            return true;
        }

        if (targetTier != current + 1) {
            return false;
        }

        BigInteger price = tierPrice(swordId, targetTier);
        if (!profile.remove(CurrencyType.MONEY, price)) {
            return false;
        }

        profile.swordLevel(swordId, targetTier);
        profile.selectedSword(swordId);
        ensureSwordInSlot(player);
        return true;
    }

    public int buyBest(Player player) {
        int upgrades = 0;
        PlayerProfile profile = profile(player);

        boolean changed;
        do {
            changed = false;
            for (int swordId = 1; swordId <= MAX_SWORDS; swordId++) {
                int current = profile.swordLevel(swordId);
                int target = current + 1;
                if (target > MAX_TIERS) {
                    continue;
                }

                BigInteger price = tierPrice(swordId, target);
                if (profile.balance(CurrencyType.MONEY).compareTo(price) >= 0) {
                    profile.remove(CurrencyType.MONEY, price);
                    profile.swordLevel(swordId, target);
                    profile.selectedSword(swordId);
                    upgrades++;
                    changed = true;
                }
            }
        } while (changed);

        ensureSwordInSlot(player);
        return upgrades;
    }

    public SwordDefinition definition(int id) {
        int safe = Math.max(1, Math.min(MAX_SWORDS, id));
        return definitions.get(safe - 1);
    }

    public List<SwordDefinition> definitions() {
        return definitions;
    }

    public ItemStack createSwordItem(Player player, int swordId, int tier, boolean equipped) {
        SwordDefinition def = definition(swordId);
        ItemStack item = new ItemStack(def.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Dungeon Sword");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Right click your sword in");
            lore.add(ChatColor.GRAY + "hand to access enchants");
            lore.add(" ");
            lore.add(ChatColor.AQUA + "Info");
            lore.add(ChatColor.DARK_AQUA + "| " + ChatColor.GRAY + "Buff: " + ChatColor.RED + "None");
            lore.add(ChatColor.DARK_AQUA + "| " + ChatColor.GRAY + "Essence Boost: " + ChatColor.AQUA + "0%");
            lore.add(ChatColor.DARK_AQUA + "| " + ChatColor.GRAY + "Tier: " + ChatColor.AQUA + tier);
            lore.add(ChatColor.DARK_AQUA + "| " + ChatColor.GRAY + "Level: " + ChatColor.AQUA + enchantService.toolLevel(player));
            lore.add(ChatColor.DARK_AQUA + "| " + ChatColor.GRAY + "Experience: " + ChatColor.GREEN + progressBar(player));
            lore.add(" ");
            lore.add(ChatColor.AQUA + "Enchants (" + countActiveEnchants(player) + ")");
            List<String> active = activeEnchantPreview(player);
            if (active.isEmpty()) {
                lore.add(ChatColor.DARK_AQUA + "| " + ChatColor.RED + "None");
            } else {
                for (String line : active) {
                    lore.add(ChatColor.DARK_AQUA + "| " + ChatColor.WHITE + line);
                }
            }
            lore.add(" ");
            lore.add(ChatColor.WHITE + "RIGHT CLICK TO VIEW SWORD");
            lore.add(ChatColor.DARK_GRAY + "When in Main Hand:");
            lore.add(ChatColor.GREEN + NumberFormat.compact(tierDamage(swordId, tier)) + " Attack Damage");
            meta.setLore(lore);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            if (def.customModelData() != null) {
                meta.setCustomModelData(def.customModelData());
            }
            meta.getPersistentDataContainer().set(swordKey, PersistentDataType.INTEGER, swordId);
            item.setItemMeta(meta);
        }
        return item;
    }

    private int countActiveEnchants(Player player) {
        int count = 0;
        for (String id : enchantService.enchantIds()) {
            if (enchantService.enchantLevel(player, id) > 0) {
                count++;
            }
        }
        return count;
    }

    private List<String> activeEnchantPreview(Player player) {
        List<String> out = new ArrayList<>();
        for (String id : enchantService.enchantIds()) {
            int level = enchantService.enchantLevel(player, id);
            if (level <= 0) {
                continue;
            }
            EnchantDefinition def = enchantService.definition(id).orElse(null);
            if (def != null) {
                out.add(def.displayName() + " " + ChatColor.RED + level);
            }
            if (out.size() >= 3) {
                break;
            }
        }
        return out;
    }

    private String progressBar(Player player) {
        BigInteger xp = enchantService.toolXp(player);
        BigInteger need = enchantService.toolXpRequiredNext(player);
        double progress = need.compareTo(BigInteger.ZERO) > 0
                ? Math.min(1.0D, xp.doubleValue() / Math.max(1.0D, need.doubleValue()))
                : 0.0D;
        int bars = 18;
        int filled = (int) Math.round(progress * bars);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            sb.append(i < filled ? ChatColor.RED + "|" : ChatColor.DARK_GRAY + "|");
        }
        sb.append(ChatColor.GREEN).append(' ').append(String.format("%.2f%%", progress * 100.0D));
        return sb.toString();
    }

    private PlayerProfile profile(Player player) {
        return profileService.profile(player.getUniqueId());
    }

    private void bootstrapDefinitions() {
        Material[] mats = new Material[]{
                Material.WOODEN_SWORD,
                Material.STONE_SWORD,
                Material.GOLDEN_SWORD,
                Material.IRON_SWORD,
                Material.DIAMOND_SWORD,
                Material.NETHERITE_SWORD
        };

        for (int i = 1; i <= MAX_SWORDS; i++) {
            Material mat = mats[(i - 1) % mats.length];
            String name = switch ((i - 1) / 20) {
                case 0 -> "Wooden Sword";
                case 1 -> "Stone Sword";
                case 2 -> "Golden Sword";
                case 3 -> "Iron Sword";
                case 4 -> "Diamond Sword";
                default -> "Netherite Sword";
            };
            definitions.add(new SwordDefinition(i, name + " #" + i, mat, null));
        }
    }
}
