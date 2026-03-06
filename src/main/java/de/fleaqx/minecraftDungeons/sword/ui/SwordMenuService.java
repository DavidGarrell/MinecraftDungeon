package de.fleaqx.minecraftDungeons.sword.ui;

import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.enchant.EnchantCategory;
import de.fleaqx.minecraftDungeons.enchant.EnchantDefinition;
import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import de.fleaqx.minecraftDungeons.sword.SwordDefinition;
import de.fleaqx.minecraftDungeons.sword.SwordService;
import de.fleaqx.minecraftDungeons.sword.SwordPerkService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SwordMenuService {

    private static final int[] GRID_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final SwordService swordService;
    private final EnchantService enchantService;
    private final SwordPerkService swordPerkService;
    private final Map<UUID, Context> contexts = new HashMap<>();

    public SwordMenuService(SwordService swordService, EnchantService enchantService, SwordPerkService swordPerkService) {
        this.swordService = swordService;
        this.enchantService = enchantService;
        this.swordPerkService = swordPerkService;
    }

    public void openMain(Player player) {
        openMain(player, MainView.SOULS, 0);
    }

    public void openSkins(Player player, int page) {
        openMain(player, MainView.SKINS, page);
    }

    public void openEnchants(Player player, EnchantCategory category, int page) {
        openMain(player, category == EnchantCategory.ESSENCE ? MainView.ESSENCE : MainView.SOULS, page);
    }

    private void openMain(Player player, MainView view, int page) {
        Inventory inv = Bukkit.createInventory(player, 54, "Sword Enchants");

        int maxPage = maxPageForView(view);
        int safePage = Math.max(0, Math.min(page, maxPage));

        inv.setItem(2, item(Material.NETHER_STAR,
                (view == MainView.SKINS ? ChatColor.GREEN : ChatColor.LIGHT_PURPLE) + "Skins" + (view == MainView.SKINS ? ChatColor.GREEN + " (SELECTED)" : ""),
                List.of(ChatColor.GRAY + "Klicke um Skins anzuzeigen.")));
        inv.setItem(4, item(Material.BLAZE_POWDER,
                (view == MainView.SOULS ? ChatColor.GREEN : ChatColor.GOLD) + "Souls Enchants" + (view == MainView.SOULS ? ChatColor.GREEN + " (SELECTED)" : ""),
                List.of(ChatColor.GRAY + "Klicke um Soul Enchants anzuzeigen.")));
        inv.setItem(6, item(Material.DIAMOND,
                (view == MainView.ESSENCE ? ChatColor.GREEN : ChatColor.AQUA) + "Essence Enchants" + (view == MainView.ESSENCE ? ChatColor.GREEN + " (SELECTED)" : ""),
                List.of(ChatColor.GRAY + "Klicke um Essence Enchants anzuzeigen.")));
        inv.setItem(8, item(Material.BOOK, ChatColor.AQUA + "Perks",
                List.of(ChatColor.GRAY + "Perks act as permanent multipliers", ChatColor.GRAY + "for your sword.", ChatColor.AQUA + "Click to view perks")));

        inv.setItem(49, item(Material.BARRIER, ChatColor.RED + "Close", List.of(ChatColor.GRAY + "Close this menu.")));
        if (safePage > 0) {
            inv.setItem(48, item(Material.ARROW, ChatColor.YELLOW + "Previous Page", List.of(ChatColor.GRAY + "Go to page " + safePage)));
        }
        if (safePage < maxPage) {
            inv.setItem(50, item(Material.ARROW, ChatColor.YELLOW + "Next Page", List.of(ChatColor.GRAY + "Go to page " + (safePage + 2))));
        }

        inv.setItem(53, item(Material.ANVIL, ChatColor.YELLOW + "Tool Level",
                List.of(
                        ChatColor.GRAY + "Level: " + ChatColor.GREEN + enchantService.toolLevel(player),
                        ChatColor.GRAY + "XP: " + ChatColor.AQUA + NumberFormat.compact(enchantService.toolXp(player)) + ChatColor.DARK_GRAY + " / " + ChatColor.AQUA + NumberFormat.compact(enchantService.toolXpRequiredNext(player))
                )));

        if (view == MainView.SKINS) {
            renderSkins(player, inv, safePage);
        } else {
            EnchantCategory category = view == MainView.ESSENCE ? EnchantCategory.ESSENCE : EnchantCategory.SOULS;
            renderEnchants(player, inv, category, safePage);
        }

        fill(inv);
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new Context("main", safePage, 0, view.category(), null, view));
    }

    private int maxPageForView(MainView view) {
        int total = switch (view) {
            case SKINS -> SwordService.MAX_SWORDS;
            case SOULS -> enchantService.byCategory(EnchantCategory.SOULS).size();
            case ESSENCE -> enchantService.byCategory(EnchantCategory.ESSENCE).size();
        };
        return Math.max(0, (int) Math.ceil((double) total / GRID_SLOTS.length) - 1);
    }

    private void renderSkins(Player player, Inventory inv, int page) {
        int start = page * GRID_SLOTS.length;
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            int index = start + i;
            if (index >= SwordService.MAX_SWORDS) {
                break;
            }

            SwordDefinition def = swordService.definition(index + 1);
            int tier = swordService.swordTier(player, def.id());
            ItemStack item = new ItemStack(def.material());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName((tier > 0 ? ChatColor.GREEN : ChatColor.RED) + def.name());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "ID: " + ChatColor.WHITE + def.id());
                lore.add(ChatColor.GRAY + "Current Tier: " + ChatColor.WHITE + tier + "/5");
                lore.add(ChatColor.YELLOW + "Click to open upgrade view");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(GRID_SLOTS[i], item);
        }

        inv.setItem(51, item(Material.NETHER_STAR, ChatColor.GOLD + "Best Sword",
                List.of(ChatColor.GRAY + "Buys best affordable sword upgrades.", ChatColor.YELLOW + "Click to buy.")));
    }

    private void renderEnchants(Player player, Inventory inv, EnchantCategory category, int page) {
        List<EnchantDefinition> defs = enchantService.byCategory(category);
        int start = page * GRID_SLOTS.length;

        for (int i = 0; i < GRID_SLOTS.length; i++) {
            int index = start + i;
            if (index >= defs.size()) {
                break;
            }

            EnchantDefinition def = defs.get(index);
            int level = enchantService.enchantLevel(player, def.id());
            boolean maxed = level >= def.maxLevel();

            List<String> lore = new ArrayList<>();
            for (String line : def.description()) {
                lore.add(ChatColor.GRAY + line);
            }
            lore.add(" ");
            lore.add(ChatColor.AQUA + "Type: " + ChatColor.WHITE + capitalize(def.category().name()));
            if (def.damageMultiplier() > 0.0D) {
                lore.add(ChatColor.AQUA + "Damage: " + ChatColor.RED + String.format("%.1fx", def.damageMultiplier()));
            }
            lore.add(ChatColor.AQUA + "Information:");
            lore.add(ChatColor.DARK_AQUA + "| " + ChatColor.GRAY + "Level: " + ChatColor.AQUA + level + ChatColor.DARK_GRAY + " / " + ChatColor.AQUA + def.maxLevel());
            lore.add(ChatColor.DARK_AQUA + "| " + ChatColor.GRAY + "Base Activation Chance: " + ChatColor.RED + chanceWithOdds(def.baseChance()));
            lore.add(ChatColor.DARK_AQUA + "| " + ChatColor.GRAY + "Your Activation Chance: " + ChatColor.RED + chanceWithOdds(enchantService.activationChance(player, def)));
            lore.add(maxed ? ChatColor.GREEN + "ENCHANT MAXED" : ChatColor.YELLOW + "Click to open enchant menu");

            inv.setItem(GRID_SLOTS[i], item(def.icon(), (maxed ? ChatColor.GREEN : ChatColor.AQUA) + def.displayName(), lore));
        }
    }

    public void openPerks(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54, "Sword Perks");

        inv.setItem(0, item(Material.BLAZE_ROD, ChatColor.YELLOW + "Roll Perk", List.of(
                ChatColor.GRAY + "Cost: " + ChatColor.RED + "1 Perk Point",
                ChatColor.GRAY + "Available: " + ChatColor.AQUA + swordPerkService.perkPoints(player),
                ChatColor.GREEN + "Click to roll"
        )));

        inv.setItem(4, item(Material.ENCHANTED_BOOK, ChatColor.AQUA + "Perk Pity", List.of(
                ChatColor.GRAY + "After 200 rolls, next roll is",
                ChatColor.GOLD + "Legendary or higher",
                " ",
                ChatColor.GRAY + "After 800 rolls, next roll is",
                ChatColor.RED + "Masterful",
                " ",
                ChatColor.DARK_AQUA + "Rolls until Pity: " + ChatColor.WHITE + swordPerkService.rollsUntilPity(player),
                ChatColor.DARK_AQUA + "Rolls until Pity+: " + ChatColor.WHITE + swordPerkService.rollsUntilPityPlus(player)
        )));

        inv.setItem(8, item(Material.DIAMOND, ChatColor.AQUA + "Perk Points", List.of(
                ChatColor.GRAY + "Current points: " + ChatColor.GREEN + swordPerkService.perkPoints(player),
                ChatColor.GRAY + "Current rolls: " + ChatColor.YELLOW + swordPerkService.perkRolls(player)
        )));

        inv.setItem(20, item(Material.BOOKSHELF, ChatColor.GREEN + "Perk Codex", List.of(
                ChatColor.GRAY + "Click to see all possible perks"
        )));

        inv.setItem(22, item(Material.REDSTONE_BLOCK, ChatColor.RED + "Current Perk", List.of(
                ChatColor.GRAY + "Perk: " + ChatColor.WHITE + swordPerkService.currentPerkName(player),
                ChatColor.GRAY + "Level: " + ChatColor.WHITE + swordPerkService.currentPerkLevel(player),
                ChatColor.GRAY + "Rarity: " + ChatColor.LIGHT_PURPLE + swordPerkService.currentPerkRarity(player),
                " ",
                ChatColor.RED + "Boosts:",
                ChatColor.GRAY + "Attack Speed » " + percent(swordPerkService.attackSpeedMultiplier(player) - 1.0D),
                ChatColor.GRAY + "Enchant Proc » " + percent(swordPerkService.enchantProcMultiplier(player) - 1.0D),
                ChatColor.GRAY + "Souls » " + percent(swordPerkService.soulsMultiplier(player) - 1.0D),
                ChatColor.GRAY + "Sword XP » " + percent(swordPerkService.swordXpMultiplier(player) - 1.0D),
                ChatColor.GRAY + "Money » " + percent(swordPerkService.moneyMultiplier(player) - 1.0D),
                ChatColor.GRAY + "Essence » " + percent(swordPerkService.essenceMultiplier(player) - 1.0D),
                " ",
                ChatColor.GRAY + "Chance: " + swordPerkService.chanceText(player)
        )));

        inv.setItem(29, item(Material.GOLD_BLOCK, ChatColor.YELLOW + "Buy 5 Perk Points", List.of(
                ChatColor.GRAY + "Price: " + ChatColor.GOLD + "50 Shards",
                ChatColor.GREEN + "Click to purchase"
        )));
        inv.setItem(30, item(Material.GOLD_BLOCK, ChatColor.YELLOW + "Buy 15 Perk Points", List.of(
                ChatColor.GRAY + "Price: " + ChatColor.GOLD + "140 Shards",
                ChatColor.GREEN + "Click to purchase"
        )));
        inv.setItem(31, item(Material.GOLD_BLOCK, ChatColor.YELLOW + "Buy 35 Perk Points", List.of(
                ChatColor.GRAY + "Price: " + ChatColor.GOLD + "300 Shards",
                ChatColor.GREEN + "Click to purchase"
        )));

        inv.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(ChatColor.GRAY + "Back to sword menu")));
        fill(inv);
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new Context("perks", 0, 0, EnchantCategory.SOULS, null, MainView.SOULS));
    }

    public void openPerkCodex(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54, "Perk Codex");
        inv.setItem(10, item(Material.BLAZE_ROD, ChatColor.RED + "Universal", List.of(
                ChatColor.GRAY + "Rarity: " + ChatColor.LIGHT_PURPLE + "Masterful",
                " ",
                ChatColor.GRAY + "Permanent boosts for Attack Speed,",
                ChatColor.GRAY + "Enchant Proc, Souls, Sword XP,",
                ChatColor.GRAY + "Money and Essence.",
                " ",
                ChatColor.RED + "Level 1" + ChatColor.GRAY + " - AS 0%, Proc 8%, Souls 30%, XP 12%, Money 10%, Essence 30%",
                ChatColor.RED + "Level 2" + ChatColor.GRAY + " - AS 0%, Proc 16%, Souls 60%, XP 24%, Money 20%, Essence 60%",
                ChatColor.RED + "Level 3" + ChatColor.GRAY + " - AS 15%, Proc 24%, Souls 90%, XP 36%, Money 30%, Essence 90%",
                ChatColor.RED + "Level 4" + ChatColor.GRAY + " - AS 20%, Proc 32%, Souls 120%, XP 48%, Money 40%, Essence 120%",
                ChatColor.RED + "Level 5" + ChatColor.GRAY + " - AS 25%, Proc 40%, Souls 150%, XP 60%, Money 50%, Essence 150%"
        )));

        inv.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(ChatColor.GRAY + "Back to perks")));
        fill(inv);
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new Context("perk_codex", 0, 0, EnchantCategory.SOULS, null, MainView.SOULS));
    }

    public void openUpgrade(Player player, int swordId, int returnPage) {
        Inventory inv = Bukkit.createInventory(player, 54, "Upgrade Sword Skin");
        SwordDefinition def = swordService.definition(swordId);
        int currentTier = swordService.swordTier(player, swordId);

        for (int tier = 1; tier <= SwordService.MAX_TIERS; tier++) {
            ItemStack item = new ItemStack(def.material());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName((tier <= currentTier ? ChatColor.GREEN : ChatColor.DARK_PURPLE) + def.name() + " " + roman(tier));
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GREEN + "Information");
                lore.add(ChatColor.DARK_GREEN + "| " + ChatColor.GRAY + "Damage: " + ChatColor.RED + NumberFormat.compact(swordService.tierDamage(swordId, tier)));
                lore.add(ChatColor.DARK_GREEN + "| " + ChatColor.GRAY + "Price: " + ChatColor.GREEN + NumberFormat.compact(swordService.tierPrice(swordId, tier)));
                lore.add(ChatColor.DARK_GREEN + "| " + ChatColor.GRAY + "Tier: " + ChatColor.AQUA + tier + "/5");
                lore.add(" ");
                lore.add(tier <= currentTier ? ChatColor.GREEN + "UNLOCKED" : ChatColor.RED + "LOCKED");
                lore.add(ChatColor.GRAY + "Click to purchase this Sword");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            int[] slots = new int[]{20, 21, 22, 23, 24};
            inv.setItem(slots[tier - 1], item);
        }

        inv.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(ChatColor.GRAY + "Back to skins")));
        fill(inv);
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new Context("upgrade", returnPage, swordId, EnchantCategory.SOULS, null, MainView.SKINS));
    }

    public void openEnchantDetail(Player player, EnchantCategory category, int page, EnchantDefinition def) {
        Inventory inv = Bukkit.createInventory(player, 54, def.displayName());
        int level = enchantService.enchantLevel(player, def.id());
        double baseChance = def.baseChance();
        double yourChance = enchantService.activationChance(player, def);

        inv.setItem(10, item(def.icon(), ChatColor.AQUA + def.displayName(), List.of(
                ChatColor.GRAY + "Level: " + ChatColor.GREEN + level + ChatColor.DARK_GRAY + " / " + ChatColor.GREEN + def.maxLevel(),
                ChatColor.GRAY + "Base Activation Chance: " + ChatColor.RED + chanceWithOdds(baseChance),
                ChatColor.GRAY + "Your Activation Chance: " + ChatColor.RED + chanceWithOdds(yourChance)
        )));

        inv.setItem(12, item(Material.EMERALD, ChatColor.GREEN + "+1 Enchant Levels", List.of(
                ChatColor.GRAY + "Level: " + ChatColor.WHITE + "1",
                ChatColor.GRAY + "Price: " + ChatColor.RED + NumberFormat.compact(enchantService.totalPriceFor(player, def.id(), 1)) + " " + def.costCurrency().name(),
                ChatColor.GREEN + "CLICK HERE"
        )));
        inv.setItem(13, item(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN + "+10 Enchant Levels", List.of(
                ChatColor.GRAY + "Level: " + ChatColor.WHITE + "10",
                ChatColor.GRAY + "Price: " + ChatColor.RED + NumberFormat.compact(enchantService.totalPriceFor(player, def.id(), 10)) + " " + def.costCurrency().name(),
                ChatColor.GREEN + "CLICK HERE"
        )));
        inv.setItem(14, item(Material.END_CRYSTAL, ChatColor.GREEN + "+100 Enchant Levels", List.of(
                ChatColor.GRAY + "Level: " + ChatColor.WHITE + "100",
                ChatColor.GRAY + "Price: " + ChatColor.RED + NumberFormat.compact(enchantService.totalPriceFor(player, def.id(), 100)) + " " + def.costCurrency().name(),
                ChatColor.GREEN + "CLICK HERE"
        )));
        inv.setItem(15, item(Material.NETHER_STAR, ChatColor.GREEN + "+1000 Enchant Levels", List.of(
                ChatColor.GRAY + "Level: " + ChatColor.WHITE + "1000",
                ChatColor.GRAY + "Price: " + ChatColor.RED + NumberFormat.compact(enchantService.totalPriceFor(player, def.id(), 1000)) + " " + def.costCurrency().name(),
                ChatColor.GREEN + "CLICK HERE"
        )));
        inv.setItem(16, item(Material.HOPPER, ChatColor.GREEN + "Max Upgrade", List.of(
                ChatColor.GRAY + "Level: " + ChatColor.WHITE + enchantService.maxAffordableLevels(player, def.id()),
                ChatColor.GRAY + "Price: " + ChatColor.RED + NumberFormat.compact(enchantService.totalPriceFor(player, def.id(), enchantService.maxAffordableLevels(player, def.id()))) + " " + def.costCurrency().name(),
                ChatColor.GREEN + "CLICK HERE"
        )));

        boolean enabled = enchantService.enchantEnabled(player, def.id());
        boolean messages = enchantService.enchantMessageEnabled(player, def.id());
        inv.setItem(30, item(Material.LEVER, ChatColor.GREEN + "Enchant Toggle", List.of(
                ChatColor.GRAY + "Whether this enchant should be enabled while grinding",
                enabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED",
                ChatColor.GRAY + "Click to toggle"
        )));
        inv.setItem(32, item(Material.OAK_SIGN, ChatColor.GREEN + "Message Toggle", List.of(
                ChatColor.GRAY + "Whether this enchant should send an activation message",
                messages ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED",
                ChatColor.GRAY + "Click to toggle"
        )));

        inv.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(ChatColor.GRAY + "Back to enchant list")));
        fill(inv);
        player.openInventory(inv);
        MainView view = category == EnchantCategory.ESSENCE ? MainView.ESSENCE : MainView.SOULS;
        contexts.put(player.getUniqueId(), new Context("enchant_detail", page, 0, category, def.id(), view));
    }

    public boolean handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }

        Context ctx = contexts.get(player.getUniqueId());
        if (ctx == null) {
            return false;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return true;
        }

        int slot = event.getSlot();
        switch (ctx.menu()) {
            case "main" -> {
                if (slot == 2) {
                    openMain(player, MainView.SKINS, 0);
                    return true;
                }
                if (slot == 4) {
                    openMain(player, MainView.SOULS, 0);
                    return true;
                }
                if (slot == 6) {
                    openMain(player, MainView.ESSENCE, 0);
                    return true;
                }
                if (slot == 48) {
                    openMain(player, ctx.view(), ctx.page() - 1);
                    return true;
                }
                if (slot == 50) {
                    openMain(player, ctx.view(), ctx.page() + 1);
                    return true;
                }
                if (slot == 49) {
                    player.closeInventory();
                    return true;
                }
                if (slot == 8) {
                    openPerks(player);
                    return true;
                }

                if (ctx.view() == MainView.SKINS && slot == 51) {
                    SwordService.BuyBestResult result = swordService.buyBest(player);
                    if (result.upgrades() > 0) {
                        String swordName = swordService.definition(result.swordId()).name();
                        player.sendMessage(ChatColor.GREEN + "Bought best sword upgrades: " + result.upgrades() + " (" + swordName + " " + roman(result.tier()) + ")");
                    } else {
                        player.sendMessage(ChatColor.RED + "No affordable sword upgrade.");
                    }
                    swordService.ensureSwordInSlot(player);
                    openMain(player, MainView.SKINS, ctx.page());
                    return true;
                }

                int index = enchantIndexFromSlot(ctx.page(), slot);
                if (index < 0) {
                    return true;
                }

                if (ctx.view() == MainView.SKINS) {
                    int swordId = index + 1;
                    if (swordId <= SwordService.MAX_SWORDS) {
                        openUpgrade(player, swordId, ctx.page());
                    }
                    return true;
                }

                List<EnchantDefinition> defs = enchantService.byCategory(ctx.category());
                if (index < defs.size()) {
                    openEnchantDetail(player, ctx.category(), ctx.page(), defs.get(index));
                }
            }
            case "upgrade" -> {
                if (slot == 49) {
                    openMain(player, MainView.SKINS, ctx.page());
                    return true;
                }
                int tier = tierFromUpgradeSlot(slot);
                if (tier >= 1 && tier <= SwordService.MAX_TIERS) {
                    boolean ok = swordService.unlockOrSelect(player, ctx.swordId(), tier);
                    player.sendMessage(ok ? ChatColor.GREEN + "Sword updated." : ChatColor.RED + "Cannot unlock this tier.");
                    openUpgrade(player, ctx.swordId(), ctx.page());
                }
            }
            case "enchant_detail" -> {
                EnchantDefinition def = enchantService.definition(ctx.enchantId()).orElse(null);
                if (def == null) {
                    openMain(player, ctx.view(), ctx.page());
                    return true;
                }
                if (slot == 49) {
                    openMain(player, ctx.view(), ctx.page());
                    return true;
                }

                int bought = 0;
                if (slot == 12) {
                    bought = enchantService.upgradeLevels(player, def.id(), 1);
                } else if (slot == 13) {
                    bought = enchantService.upgradeLevels(player, def.id(), 10);
                } else if (slot == 14) {
                    bought = enchantService.upgradeLevels(player, def.id(), 100);
                } else if (slot == 15) {
                    bought = enchantService.upgradeLevels(player, def.id(), 1000);
                } else if (slot == 16) {
                    bought = enchantService.maxUpgrade(player, def.id());
                } else if (slot == 30) {
                    boolean enabled = enchantService.toggleEnabled(player, def.id());
                    player.sendMessage(enabled ? ChatColor.GREEN + "Enchant enabled." : ChatColor.RED + "Enchant disabled.");
                } else if (slot == 32) {
                    boolean enabled = enchantService.toggleMessages(player, def.id());
                    player.sendMessage(enabled ? ChatColor.GREEN + "Enchant messages enabled." : ChatColor.RED + "Enchant messages disabled.");
                }

                if (bought > 0) {
                    player.sendMessage(ChatColor.GREEN + "Upgraded " + def.displayName() + " by " + bought + " levels.");
                } else if (slot >= 12 && slot <= 16) {
                    player.sendMessage(ChatColor.RED + "Upgrade failed (locked/max/no currency).");
                }
                openEnchantDetail(player, ctx.category(), ctx.page(), def);
            }
            case "perks" -> {
                if (slot == 49) {
                    openMain(player, MainView.SOULS, 0);
                    return true;
                }
                if (slot == 20) {
                    openPerkCodex(player);
                    return true;
                }
                if (slot == 0) {
                    SwordPerkService.RollResult result = swordPerkService.roll(player);
                    if (!result.success()) {
                        player.sendMessage(ChatColor.RED + "You need perk points.");
                    } else {
                        player.sendMessage(ChatColor.GREEN + "Rolled perk: " + ChatColor.AQUA + "Universal " + roman(result.level()) + ChatColor.GRAY + " (" + result.rarity() + ")");
                        if (result.pityPlus()) {
                            player.sendMessage(ChatColor.LIGHT_PURPLE + "Pity+ triggered!");
                        } else if (result.pity()) {
                            player.sendMessage(ChatColor.GOLD + "Pity triggered!");
                        }
                    }
                    openPerks(player);
                    return true;
                }
                if (slot == 29 || slot == 30 || slot == 31) {
                    int points = slot == 29 ? 5 : (slot == 30 ? 15 : 35);
                    int cost = slot == 29 ? 50 : (slot == 30 ? 140 : 300);
                    boolean ok = swordPerkService.buyPoints(player, points, cost);
                    player.sendMessage(ok ? ChatColor.GREEN + "Bought " + points + " perk points." : ChatColor.RED + "Not enough shards.");
                    openPerks(player);
                    return true;
                }
            }
            case "perk_codex" -> {
                if (slot == 49) {
                    openPerks(player);
                    return true;
                }
            }
        }

        return true;
    }

    public void handleClose(InventoryCloseEvent event) {
        contexts.remove(event.getPlayer().getUniqueId());
    }

    private int tierFromUpgradeSlot(int slot) {
        return switch (slot) {
            case 20 -> 1;
            case 21 -> 2;
            case 22 -> 3;
            case 23 -> 4;
            case 24 -> 5;
            default -> -1;
        };
    }

    private int enchantIndexFromSlot(int page, int slot) {
        int indexInPage = gridIndex(slot);
        if (indexInPage < 0) {
            return -1;
        }
        return page * GRID_SLOTS.length + indexInPage;
    }

    private int gridIndex(int slot) {
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (GRID_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private void fill(Inventory inv) {
        ItemStack glass = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass);
            }
        }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }

    private String chanceWithOdds(double chance) {
        double safe = Math.max(0.0D, Math.min(1.0D, chance));
        if (safe <= 0.0D) {
            return "0.000% (0 in 0)";
        }
        long oneIn = Math.max(1L, Math.round(1.0D / safe));
        return String.format("%.3f%% (1 in %d)", safe * 100.0D, oneIn);
    }

    private String percent(double value) {
        return String.format("%.0f%%", Math.max(0.0D, value) * 100.0D);
    }

    private String capitalize(String value) {
        String lower = value.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private enum MainView {
        SKINS,
        SOULS,
        ESSENCE;

        private EnchantCategory category() {
            return this == ESSENCE ? EnchantCategory.ESSENCE : EnchantCategory.SOULS;
        }
    }

    private record Context(String menu, int page, int swordId, EnchantCategory category, String enchantId, MainView view) {
    }
}
