package de.fleaqx.minecraftDungeons.sword.ui;

import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.enchant.EnchantCategory;
import de.fleaqx.minecraftDungeons.enchant.EnchantDefinition;
import de.fleaqx.minecraftDungeons.enchant.EnchantService;
import de.fleaqx.minecraftDungeons.sword.SwordDefinition;
import de.fleaqx.minecraftDungeons.sword.SwordService;
import de.fleaqx.minecraftDungeons.sword.SwordPerkService;
import de.fleaqx.minecraftDungeons.ui.HeadItemFactory;
import de.fleaqx.minecraftDungeons.ui.UiMenuUtils;
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

import static de.fleaqx.minecraftDungeons.ui.UiMenuUtils.item;

public class SwordMenuService {

    private static final int[] GRID_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final String VIEW_SKINS = "skins";
    private static final String VIEW_SOULS = "souls";
    private static final String VIEW_ESSENCE = "essence";

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
        openMain(player, VIEW_SOULS, 0);
    }

    public void openSkins(Player player, int page) {
        openMain(player, VIEW_SKINS, page);
    }

    public void openEnchants(Player player, EnchantCategory category, int page) {
        openMain(player, category == EnchantCategory.ESSENCE ? VIEW_ESSENCE : VIEW_SOULS, page);
    }

    private void openMain(Player player, String view, int page) {
        Inventory inv = UiMenuUtils.createMenu(player, 54, "Sword Enchants");

        int maxPage = maxPageForView(view);
        int safePage = Math.max(0, Math.min(page, maxPage));

        inv.setItem(3, item(Material.BLAZE_POWDER,
                (VIEW_SOULS.equals(view) ? ChatColor.GREEN : ChatColor.GOLD) + "Souls Enchants" + (VIEW_SOULS.equals(view) ? ChatColor.GREEN + " (SELECTED)" : ""),
                List.of(ChatColor.GRAY + "Use Souls to upgrade your enchants.", "", ChatColor.YELLOW + "Click to open")));
        inv.setItem(4, item(Material.DIAMOND,
                (VIEW_ESSENCE.equals(view) ? ChatColor.GREEN : ChatColor.AQUA) + "Essence Enchants" + (VIEW_ESSENCE.equals(view) ? ChatColor.GREEN + " (SELECTED)" : ""),
                List.of(ChatColor.GRAY + "Use Essence for special upgrades.", "", ChatColor.YELLOW + "Click to open")));
        inv.setItem(5, item(Material.NETHER_STAR,
                (VIEW_SKINS.equals(view) ? ChatColor.GREEN : ChatColor.LIGHT_PURPLE) + "Sword Skins" + (VIEW_SKINS.equals(view) ? ChatColor.GREEN + " (SELECTED)" : ""),
                List.of(ChatColor.GRAY + "Choose and upgrade your sword.", "", ChatColor.YELLOW + "Click to open")));

        inv.setItem(46, item(Material.BOOK, ChatColor.AQUA + "Perks",
                List.of(ChatColor.GRAY + "Perks act as permanent multipliers", ChatColor.GRAY + "for your sword.", ChatColor.AQUA + "Click to view perks")));
        inv.setItem(47, item(Material.NETHERITE_SWORD, ChatColor.GOLD + "Sword Button",
                List.of(ChatColor.GRAY + "Open sword skins & upgrades.", ChatColor.YELLOW + "Click to open.")));

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

        if (VIEW_SKINS.equals(view)) {
            renderSkins(player, inv, safePage);
        } else {
            EnchantCategory category = VIEW_ESSENCE.equals(view) ? EnchantCategory.ESSENCE : EnchantCategory.SOULS;
            renderEnchants(player, inv, category, safePage);
        }

        UiMenuUtils.fillEmptySlots(inv);
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new Context("main", safePage, 0, categoryForView(view), null, view));
    }

    private int maxPageForView(String view) {
        int total = VIEW_SKINS.equals(view)
                ? SwordService.MAX_SWORDS
                : enchantService.byCategory(VIEW_ESSENCE.equals(view) ? EnchantCategory.ESSENCE : EnchantCategory.SOULS).size();
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
            boolean unlocked = enchantService.toolLevel(player) >= def.requiredToolLevel();
            double chance = enchantService.activationChance(player, def);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "Activation Chance: " + ChatColor.GRAY + chanceWithOdds(chance));
            lore.add(" ");
            lore.add(ChatColor.GREEN + "Description");
            for (String line : def.description()) {
                lore.add(ChatColor.DARK_GREEN + "| " + ChatColor.GRAY + line);
            }
            lore.add(" ");
            lore.add(ChatColor.GOLD + "Statistics");
            lore.add(ChatColor.GOLD + "| " + ChatColor.GRAY + "Type: " + ChatColor.WHITE + capitalize(def.category().name()));
            lore.add(ChatColor.GOLD + "| " + ChatColor.GRAY + "Level: " + ChatColor.YELLOW + level + ChatColor.GRAY + "/" + ChatColor.RED + def.maxLevel());
            lore.add(ChatColor.GOLD + "| " + ChatColor.GRAY + "Tool Unlock: " + ChatColor.YELLOW + "Level " + def.requiredToolLevel());
            lore.add(ChatColor.GOLD + "| " + ChatColor.GRAY + "Cost: " + ChatColor.YELLOW + NumberFormat.compact(enchantService.totalPriceFor(player, def.id(), 1)) + " " + def.costCurrency().name());
            if (def.damageMultiplier() > 0.0D) {
                lore.add(ChatColor.GOLD + "| " + ChatColor.GRAY + "Damage: " + ChatColor.RED + String.format("%.1fx", def.damageMultiplier()));
            }
            lore.add(" ");
            lore.add(ChatColor.YELLOW + "Upgrade");
            if (maxed) {
                lore.add(ChatColor.YELLOW + "| " + ChatColor.GREEN + "This enchant is maxed.");
            } else if (unlocked) {
                lore.add(ChatColor.YELLOW + "| " + ChatColor.GRAY + "Click to open upgrade menu.");
            } else {
                lore.add(ChatColor.YELLOW + "| " + ChatColor.RED + "Requires Tool Level " + def.requiredToolLevel() + ".");
            }

            inv.setItem(GRID_SLOTS[i], item(def.icon(), (maxed ? ChatColor.GREEN : ChatColor.AQUA) + def.displayName() + ChatColor.WHITE + " Enchant", lore));
        }
    }

    public void openPerks(Player player) {
        Inventory inv = UiMenuUtils.createMenu(player, 54, "Sword Perks");

        inv.setItem(0, HeadItemFactory.head("http://textures.minecraft.net/texture/3772db39ccf2a33f71f71a889f06e1492122ab0e8625995fe9ad49fdb02f4f24", ChatColor.GREEN + "Roll Sword Perk", List.of(
                ChatColor.GRAY + "Cost: " + ChatColor.RED + "1 Perk Point",
                ChatColor.GRAY + "Available: " + ChatColor.AQUA + swordPerkService.perkPoints(player),
                ChatColor.GREEN + "Click to roll"
        )));

        inv.setItem(4, item(Material.ENCHANTED_BOOK, ChatColor.AQUA + "Perk Pity", List.of(
                ChatColor.GRAY + "After " + swordPerkService.pityRolls() + " rolls, next roll is",
                ChatColor.GOLD + "Legendary or higher",
                " ",
                ChatColor.GRAY + "After " + swordPerkService.pityPlusRolls() + " rolls, next roll is",
                ChatColor.RED + "Masterful",
                " ",
                ChatColor.DARK_AQUA + "Rolls until Pity: " + ChatColor.WHITE + swordPerkService.rollsUntilPity(player),
                ChatColor.DARK_AQUA + "Rolls until Pity+: " + ChatColor.WHITE + swordPerkService.rollsUntilPityPlus(player)
        )));

        inv.setItem(8, item(Material.DIAMOND, ChatColor.AQUA + "Perk Points", List.of(
                ChatColor.GRAY + "Current points: " + ChatColor.GREEN + swordPerkService.perkPoints(player),
                ChatColor.GRAY + "Current rolls: " + ChatColor.YELLOW + swordPerkService.perkRolls(player)
        )));

        inv.setItem(20, HeadItemFactory.head("http://textures.minecraft.net/texture/e9e123405f7534ff95f966f235f5f60365aa2184141f24a4f7ab95687f850ca2", ChatColor.GREEN + "Perk Codex", List.of(
                ChatColor.GRAY + "Click to see all possible perks"
        )));

        inv.setItem(22, HeadItemFactory.head("http://textures.minecraft.net/texture/d01afe973c60323f6da20eb664004fbfbeb94e4fc3418f7a9ff313dd7f5f46f", ChatColor.RED + "Current Perk", List.of(
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

        int[] offerSlots = new int[]{29, 30, 31};
        List<SwordPerkService.ShopOffer> offers = swordPerkService.shopOffers();
        for (int i = 0; i < Math.min(offerSlots.length, offers.size()); i++) {
            SwordPerkService.ShopOffer offer = offers.get(i);
            inv.setItem(offerSlots[i], item(Material.GOLD_BLOCK, ChatColor.YELLOW + "Buy " + offer.points() + " Perk Points", List.of(
                    ChatColor.GRAY + "Price: " + ChatColor.GOLD + offer.cost() + " Shards",
                    ChatColor.GREEN + "Click to purchase"
            )));
        }

        inv.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(ChatColor.GRAY + "Back to sword menu")));
        UiMenuUtils.fillEmptySlots(inv);
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new Context("perks", 0, 0, EnchantCategory.SOULS, null, VIEW_SOULS));
    }

    public void openPerkCodex(Player player) {
        Inventory inv = UiMenuUtils.createMenu(player, 54, "Perk Codex");
        int slot = 10;
        for (SwordPerkService.PerkDefinition perk : swordPerkService.availablePerks()) {
            if (slot >= 44) {
                break;
            }
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Rarity: " + ChatColor.LIGHT_PURPLE + perk.rarity());
            lore.add(ChatColor.GRAY + "Weight: " + ChatColor.YELLOW + perk.weight());
            lore.add(" ");
            for (String line : perk.lore()) {
                lore.add(ChatColor.GRAY + line);
            }
            lore.add(" ");
            for (int level = 1; level <= 5; level++) {
                lore.add(ChatColor.RED + "Level " + level + ChatColor.GRAY
                        + " - AS " + percent(perk.attackSpeed()[level - 1])
                        + ", Proc " + percent(perk.enchantProc()[level - 1])
                        + ", Souls " + percent(perk.souls()[level - 1])
                        + ", XP " + percent(perk.swordXp()[level - 1])
                        + ", Money " + percent(perk.money()[level - 1])
                        + ", Essence " + percent(perk.essence()[level - 1]));
            }
            inv.setItem(slot, HeadItemFactory.head("http://textures.minecraft.net/texture/3474fbcf8f2840fc2d5cb35a30258f671fc5f0644f05f8ed3ef31f8f5da4db2", ChatColor.RED + perk.displayName(), lore));
            slot++;
        }

        inv.setItem(49, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(ChatColor.GRAY + "Back to perks")));
        UiMenuUtils.fillEmptySlots(inv);
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new Context("perk_codex", 0, 0, EnchantCategory.SOULS, null, VIEW_SOULS));
    }

    public void openUpgrade(Player player, int swordId, int returnPage) {
        Inventory inv = UiMenuUtils.createMenu(player, 54, "Upgrade Sword Skin");
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
        UiMenuUtils.fillEmptySlots(inv);
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new Context("upgrade", returnPage, swordId, EnchantCategory.SOULS, null, VIEW_SKINS));
    }

    public void openEnchantDetail(Player player, EnchantCategory category, int page, EnchantDefinition def) {
        Inventory inv = UiMenuUtils.createMenu(player, 54, def.displayName());
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
        UiMenuUtils.fillEmptySlots(inv);
        player.openInventory(inv);
        String view = category == EnchantCategory.ESSENCE ? VIEW_ESSENCE : VIEW_SOULS;
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
                if (slot == 3) {
                    openMain(player, VIEW_SOULS, 0);
                    return true;
                }
                if (slot == 4) {
                    openMain(player, VIEW_ESSENCE, 0);
                    return true;
                }
                if (slot == 5 || slot == 47) {
                    openMain(player, VIEW_SKINS, 0);
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
                if (slot == 46) {
                    openPerks(player);
                    return true;
                }

                if (VIEW_SKINS.equals(ctx.view()) && slot == 51) {
                    SwordService.BuyBestResult result = swordService.buyBest(player);
                    if (result.upgrades() > 0) {
                        String swordName = swordService.definition(result.swordId()).name();
                        player.sendMessage(ChatColor.GREEN + "Bought best sword upgrades: " + result.upgrades() + " (" + swordName + " " + roman(result.tier()) + ")");
                    } else {
                        player.sendMessage(ChatColor.RED + "No affordable sword upgrade.");
                    }
                    swordService.ensureSwordInSlot(player);
                    openMain(player, VIEW_SKINS, ctx.page());
                    return true;
                }

                int index = enchantIndexFromSlot(ctx.page(), slot);
                if (index < 0) {
                    return true;
                }

                if (VIEW_SKINS.equals(ctx.view())) {
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
                    openMain(player, VIEW_SKINS, ctx.page());
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
                    openMain(player, VIEW_SOULS, 0);
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
                        player.sendMessage(ChatColor.GREEN + "Rolled perk: " + ChatColor.AQUA + result.perkName() + " " + roman(result.level()) + ChatColor.GRAY + " (" + result.rarity() + ")");
                        swordService.ensureSwordInSlot(player);
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
                    int idx = slot == 29 ? 0 : (slot == 30 ? 1 : 2);
                    List<SwordPerkService.ShopOffer> offers = swordPerkService.shopOffers();
                    if (idx < offers.size()) {
                        SwordPerkService.ShopOffer offer = offers.get(idx);
                        boolean ok = swordPerkService.buyPoints(player, offer.points(), offer.cost());
                        player.sendMessage(ok ? ChatColor.GREEN + "Bought " + offer.points() + " perk points." : ChatColor.RED + "Not enough shards.");
                    }
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


    private EnchantCategory categoryForView(String view) {
        return VIEW_ESSENCE.equals(view) ? EnchantCategory.ESSENCE : EnchantCategory.SOULS;
    }

    private record Context(String menu, int page, int swordId, EnchantCategory category, String enchantId, String view) {
    }
}
