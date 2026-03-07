package de.fleaqx.minecraftDungeons.companion.ui;

import de.fleaqx.minecraftDungeons.companion.CompanionService;
import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import de.fleaqx.minecraftDungeons.ui.HeadItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigInteger;
import java.util.*;

public class CompanionMenuService {

    private static final int PAGE_SIZE = 27;

    private final CompanionService companionService;
    private final DungeonService dungeonService;
    private final Map<UUID, MenuContext> contexts = new HashMap<>();

    public CompanionMenuService(CompanionService companionService, DungeonService dungeonService) {
        this.companionService = companionService;
        this.dungeonService = dungeonService;
    }

    public void openCompanions(Player player) {
        MenuContext existing = contexts.get(player.getUniqueId());
        if (existing != null && "companions".equals(existing.menu())) {
            openCompanions(player, existing.page(), existing.selectionMode(), existing.selectedIds(), existing.awaitingDeleteConfirm(), existing.awaitingBulkConfirm(), existing.pendingBulkMode(), existing.pendingBulkValue());
            return;
        }

        dungeonService.currentZoneContext(player)
                .ifPresentOrElse(
                        context -> openEggMenu(player, context.zoneId(), context.stage()),
                        () -> openCompanions(player, 1, false, Set.of(), null, null, null, null)
                );
    }

    private void openCompanions(Player player,
                                int page,
                                boolean selectionMode,
                                Set<String> selectedIds,
                                String awaitingDeleteConfirm,
                                String awaitingBulkConfirm,
                                BulkMode pendingBulkMode,
                                String pendingBulkValue) {
        Inventory inv = Bukkit.createInventory(player, 54, "Companions");
        int maxSlots = companionService.maxEquipSlots(player);
        List<CompanionService.OwnedCompanion> equipped = companionService.equipped(player);

        String activeZoneId = null;
        int activeStage = 1;
        Optional<DungeonService.PlayerZoneContext> zoneContext = dungeonService.currentZoneContext(player);
        if (zoneContext.isPresent()) {
            activeZoneId = zoneContext.get().zoneId();
            activeStage = Math.max(1, zoneContext.get().stage());
        } else if (contexts.containsKey(player.getUniqueId())) {
            MenuContext previous = contexts.get(player.getUniqueId());
            if (previous.zoneId() != null && !previous.zoneId().isBlank()) {
                activeZoneId = previous.zoneId();
                activeStage = Math.max(1, previous.stage());
            }
        }

        for (int i = 0; i < 6; i++) {
            boolean unlocked = i < maxSlots;
            ItemStack slotItem = unlocked
                    ? item(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "Equip Slot " + (i + 1), List.of(ChatColor.GRAY + "Click a companion below"))
                    : item(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "Locked Slot", List.of(ChatColor.GRAY + "Permission required"));
            inv.setItem(2 + i, slotItem);
        }

        for (int i = 0; i < equipped.size() && i < 6; i++) {
            inv.setItem(2 + i, companionHead(equipped.get(i), true, selectedIds.contains(equipped.get(i).id())));
        }

        List<CompanionService.OwnedCompanion> owned = companionService.owned(player);
        int pages = Math.max(1, (int) Math.ceil((double) owned.size() / PAGE_SIZE));
        int safePage = Math.max(1, Math.min(page, pages));
        int from = (safePage - 1) * PAGE_SIZE;
        int to = Math.min(owned.size(), from + PAGE_SIZE);

        int slot = 18;
        for (int i = from; i < to; i++) {
            CompanionService.OwnedCompanion companion = owned.get(i);
            inv.setItem(slot++, companionHead(companion, false, selectedIds.contains(companion.id())));
        }

        inv.setItem(45, deleteToggleItem(selectionMode, awaitingDeleteConfirm));
        inv.setItem(46, bulkDeleteItem(awaitingBulkConfirm));
        inv.setItem(48, item(Material.CLOCK, ChatColor.LIGHT_PURPLE + "Page " + safePage + "/" + pages, List.of(ChatColor.GRAY + "Browse companions")));
        inv.setItem(50, item(Material.BLAZE_ROD, ChatColor.RED + "Next", List.of(ChatColor.GRAY + "Go to next page")));
        inv.setItem(52, item(Material.BARRIER, ChatColor.RED + "Close", List.of(ChatColor.GRAY + "Close menu")));
        inv.setItem(53, item(Material.DIAMOND, ChatColor.AQUA + "Equip Best", List.of(ChatColor.GREEN + "Click to equip best!")));
        inv.setItem(47, dragonEggShortcutItem(activeZoneId, activeStage));
        inv.setItem(49, companionHudItem(activeZoneId, activeStage));

        fill(inv);
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new MenuContext("companions", activeZoneId, activeStage, safePage, selectionMode,
                new HashSet<>(selectedIds), awaitingDeleteConfirm, awaitingBulkConfirm, pendingBulkMode, pendingBulkValue));
    }

    private void openBulkDeleteMenu(Player player, int page, String awaitingBulkConfirm, BulkMode pendingMode, String pendingValue) {
        Inventory inv = Bukkit.createInventory(player, 54, "Bulk Delete Companions");

        List<String> options = new ArrayList<>();
        for (CompanionService.CompanionRarity rarity : CompanionService.CompanionRarity.values()) {
            options.add("RARITY:" + rarity.name());
        }
        Set<String> zones = new TreeSet<>();
        for (CompanionService.OwnedCompanion companion : companionService.owned(player)) {
            zones.add(companion.zoneId().toLowerCase(Locale.ROOT));
        }
        for (String zone : zones) {
            options.add("ZONE:" + zone);
        }

        int pages = Math.max(1, (int) Math.ceil((double) options.size() / PAGE_SIZE));
        int safePage = Math.max(1, Math.min(page, pages));
        int from = (safePage - 1) * PAGE_SIZE;
        int to = Math.min(options.size(), from + PAGE_SIZE);

        int slot = 18;
        for (int i = from; i < to; i++) {
            String raw = options.get(i);
            if (raw.startsWith("RARITY:")) {
                CompanionService.CompanionRarity rarity = CompanionService.CompanionRarity.valueOf(raw.substring("RARITY:".length()));
                inv.setItem(slot++, bulkRarityItem(rarity));
            } else {
                String zoneId = raw.substring("ZONE:".length());
                inv.setItem(slot++, bulkZoneItem(zoneId));
            }
        }

        inv.setItem(45, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(ChatColor.GRAY + "Return to companions")));
        inv.setItem(48, item(Material.CLOCK, ChatColor.LIGHT_PURPLE + "Page " + safePage + "/" + pages, List.of(ChatColor.GRAY + "Bulk filters")));
        inv.setItem(50, item(Material.BLAZE_ROD, ChatColor.RED + "Next", List.of(ChatColor.GRAY + "Go to next page")));
        inv.setItem(52, item(Material.BARRIER, ChatColor.RED + "Close", List.of(ChatColor.GRAY + "Close menu")));
        inv.setItem(53, bulkDeleteItem(awaitingBulkConfirm));

        fill(inv);
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new MenuContext("bulk-delete", null, 1, safePage, false,
                new HashSet<>(), null, awaitingBulkConfirm, pendingMode, pendingValue));
    }

    public void openEggMenu(Player player, String zoneId, int stage) {
        if (zoneId == null || zoneId.isBlank()) {
            return;
        }
        zoneId = zoneId.toLowerCase(Locale.ROOT);
        Inventory inv = Bukkit.createInventory(player, 54, "Companion Eggs");

        long price = companionService.costPerDraw(stage);
        inv.setItem(13, HeadItemFactory.head(
                "http://textures.minecraft.net/texture/b23c6f17cb43cc6c5cfcc8ef3f7480fcead0b5e1c574b95f2c98b5eb2d646e47",
                ChatColor.GREEN + "Zone Egg",
                List.of(
                        ChatColor.GRAY + "Purchase a Companion that boosts",
                        ChatColor.GRAY + "the amount of money you gain!",
                        " ",
                        ChatColor.GREEN + "| Price: " + NumberFormat.compact(BigInteger.valueOf(price)) + " Money",
                        " ",
                        ChatColor.WHITE + "Right Click to view »"
                )
        ));

        inv.setItem(4, item(Material.NETHER_STAR, ChatColor.AQUA + "Companion HUD", List.of(
                ChatColor.GRAY + "Zone: " + ChatColor.YELLOW + capitalize(zoneId),
                ChatColor.GRAY + "Stage: " + ChatColor.YELLOW + stage,
                ChatColor.GRAY + "Price / Egg: " + ChatColor.GREEN + NumberFormat.compact(BigInteger.valueOf(price)) + " Money"
        )));

        int[] previewSlots = new int[]{20, 22, 24, 30, 32};
        List<CompanionService.CompanionDefinition> previews = companionService.previewCompanions(zoneId);
        for (int i = 0; i < Math.min(previews.size(), previewSlots.length); i++) {
            inv.setItem(previewSlots[i], companionPreview(previews.get(i), zoneId, stage));
        }

        inv.setItem(36, toggleAnimationsItem());
        inv.setItem(38, rollItem(1, price));
        inv.setItem(40, rollItem(3, price));
        inv.setItem(42, rollItem(10, price));
        inv.setItem(44, rollItem(64, price));

        inv.setItem(49, item(Material.CHEST, ChatColor.AQUA + "My Companions", List.of(ChatColor.GRAY + "Open companion inventory")));
        fill(inv);
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new MenuContext("eggs", zoneId, stage, 1, false, new HashSet<>(), null, null, null, null));
    }

    public boolean handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }
        MenuContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) {
            return false;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return true;
        }

        int slot = event.getSlot();
        if (ctx.menu().equals("eggs")) {
            int amount = switch (slot) {
                case 38 -> 1;
                case 40 -> 3;
                case 42 -> 10;
                case 44 -> 64;
                default -> 0;
            };
            if (amount > 0) {
                CompanionService.RollBatchResult result = companionService.roll(player, ctx.zoneId(), ctx.stage(), amount);
                if (!result.success()) {
                    player.sendMessage(ChatColor.RED + "Not enough money. Cost: " + result.totalCost());
                } else {
                    player.sendMessage(ChatColor.GREEN + "You opened " + amount + " eggs for " + result.totalCost() + " Money.");
                    CompanionService.OwnedCompanion best = result.companions().stream().max(Comparator.comparingDouble(CompanionService.OwnedCompanion::multiplier)).orElse(null);
                    if (best != null) {
                        player.sendMessage(best.rarity().color() + "Best roll: " + best.name() + ChatColor.GRAY + " [" + best.mutation().name() + "] " + ChatColor.GREEN + best.multiplier() + "x");
                    }
                }
                openEggMenu(player, ctx.zoneId(), ctx.stage());
                return true;
            }
            if (slot == 49) {
                openCompanions(player);
                return true;
            }
            if (slot == 13) {
                player.sendMessage(ChatColor.YELLOW + "Use Open buttons below to hatch companions.");
                return true;
            }
            return true;
        }

        if (ctx.menu().equals("bulk-delete")) {
            if (slot == 45) {
                openCompanions(player);
                return true;
            }
            if (slot == 50) {
                openBulkDeleteMenu(player, ctx.page() + 1, null, null, null);
                return true;
            }
            if (slot == 52) {
                player.closeInventory();
                return true;
            }
            if (slot == 53) {
                if (ctx.awaitingBulkConfirm() != null && ctx.pendingBulkMode() != null && ctx.pendingBulkValue() != null) {
                    int deleted = runBulkDelete(player, ctx.pendingBulkMode(), ctx.pendingBulkValue());
                    player.sendMessage(ChatColor.RED + "Deleted " + deleted + " companions.");
                    openCompanions(player);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Select a rarity or zone first.");
                }
                return true;
            }

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) {
                return true;
            }
            String filter = readBulkFilter(clicked);
            if (filter == null) {
                return true;
            }

            BulkMode mode = filter.startsWith("RARITY:") ? BulkMode.RARITY : BulkMode.ZONE;
            String value = filter.substring(filter.indexOf(':') + 1);
            String human = mode == BulkMode.RARITY ? value : capitalize(value);
            player.sendMessage(ChatColor.YELLOW + "Selected " + human + ". Click Bulk Delete again to confirm.");
            openBulkDeleteMenu(player, ctx.page(), human, mode, value);
            return true;
        }

        if (!ctx.menu().equals("companions")) {
            return true;
        }

        if (slot == 52) {
            player.closeInventory();
            return true;
        }
        if (slot == 53) {
            companionService.equipBest(player);
            player.sendMessage(ChatColor.GREEN + "Equipped best companions.");
            openCompanions(player, ctx.page(), ctx.selectionMode(), ctx.selectedIds(), null, ctx.awaitingBulkConfirm(), ctx.pendingBulkMode(), ctx.pendingBulkValue());
            return true;
        }
        if (slot == 47) {
            if (ctx.zoneId() == null || ctx.zoneId().isBlank()) {
                player.sendMessage(ChatColor.RED + "No active zone found. Enter a zone first.");
            } else {
                openEggMenu(player, ctx.zoneId(), ctx.stage());
            }
            return true;
        }
        if (slot == 50) {
            openCompanions(player, ctx.page() + 1, ctx.selectionMode(), ctx.selectedIds(), null, ctx.awaitingBulkConfirm(), ctx.pendingBulkMode(), ctx.pendingBulkValue());
            return true;
        }
        if (slot == 46) {
            openBulkDeleteMenu(player, 1, null, null, null);
            return true;
        }
        if (slot == 45) {
            if (ctx.selectionMode()) {
                if (ctx.awaitingDeleteConfirm() != null) {
                    int deleted = companionService.deleteCompanions(player, ctx.selectedIds());
                    player.sendMessage(ChatColor.RED + "Deleted " + deleted + " companions.");
                    openCompanions(player, 1, false, Set.of(), null, null, null, null);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Click Delete Companions again to confirm.");
                    openCompanions(player, ctx.page(), true, ctx.selectedIds(), "confirm", ctx.awaitingBulkConfirm(), ctx.pendingBulkMode(), ctx.pendingBulkValue());
                }
            } else {
                player.sendMessage(ChatColor.YELLOW + "Select companions and click again to confirm deletion.");
                openCompanions(player, ctx.page(), true, new HashSet<>(), null, ctx.awaitingBulkConfirm(), ctx.pendingBulkMode(), ctx.pendingBulkValue());
            }
            return true;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return true;
        }
        String companionId = readCompanionId(clicked);
        if (companionId != null) {
            if (ctx.selectionMode()) {
                Set<String> selected = new HashSet<>(ctx.selectedIds());
                if (!selected.add(companionId)) {
                    selected.remove(companionId);
                }
                openCompanions(player, ctx.page(), true, selected, null, ctx.awaitingBulkConfirm(), ctx.pendingBulkMode(), ctx.pendingBulkValue());
            } else {
                companionService.equip(player, companionId);
                openCompanions(player, ctx.page(), false, Set.of(), null, null, null, null);
            }
        }

        return true;
    }

    public void handleClose(InventoryCloseEvent event) {
        contexts.remove(event.getPlayer().getUniqueId());
    }

    private int runBulkDelete(Player player, BulkMode mode, String value) {
        if (mode == BulkMode.RARITY) {
            return companionService.deleteByRarity(player, CompanionService.CompanionRarity.valueOf(value));
        }
        return companionService.deleteByZone(player, value);
    }

    private ItemStack companionHead(CompanionService.OwnedCompanion companion, boolean equipped, boolean selectedForDelete) {
        String texture = switch (companion.rarity()) {
            case COMMON -> "http://textures.minecraft.net/texture/1639c4e0c29260f4e1ef0af5b80f45bf9a572a9ad7d7568f94795f0f6c365a1";
            case RARE -> "http://textures.minecraft.net/texture/f4d8cdbf8776d4f31f0eb55f89ac43189af0f7f24f8206dfa8c32ece2b204361";
            case EPIC -> "http://textures.minecraft.net/texture/7b4f2f7f6ea585f32f7dfd59f3fbc46c110f10fca9f14f69533ef7559658fbf1";
            case LEGENDARY -> "http://textures.minecraft.net/texture/71aeb6c89e0ca9f33fbd583a14bcbb157ccd44fcbb40f19a1e0122972a97f6e8";
            case MYTHIC -> "http://textures.minecraft.net/texture/e48f8e72f5f8dcfc8fd7d7f9e77a4fbcff47aa8477f0348ee53d86fac261cb47";
        };
        ItemStack item = HeadItemFactory.head(texture,
                companion.rarity().color() + companion.name(),
                List.of(
                        ChatColor.GRAY + "Rarity: " + companion.rarity().color() + companion.rarity().name(),
                        ChatColor.GRAY + "Mutation: " + companion.mutation().color() + companion.mutation().name(),
                        ChatColor.GRAY + "Zone Stage: " + ChatColor.YELLOW + companion.zoneId() + " " + companion.stage(),
                        ChatColor.GRAY + "Multiplier: " + ChatColor.GREEN + companion.multiplier() + "x",
                        selectedForDelete ? ChatColor.RED + "Selected for deletion" : (equipped ? ChatColor.GREEN + "Equipped" : ChatColor.YELLOW + "Click to equip / unequip")
                ));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>(meta.getLore() == null ? List.of() : meta.getLore());
            lore.add(ChatColor.DARK_GRAY + "ID:" + companion.id());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack rollItem(int amount, long pricePerDraw) {
        long total = pricePerDraw * amount;
        return item(Material.BLACK_DYE, ChatColor.LIGHT_PURPLE + "Open " + amount + " Companions",
                List.of(
                        " ",
                        ChatColor.GRAY + "Click to open " + ChatColor.YELLOW + amount + ChatColor.GRAY + " companion(s)",
                        ChatColor.GRAY + "from the dragon egg!",
                        " ",
                        ChatColor.GRAY + "Cost: " + ChatColor.GREEN + NumberFormat.compact(BigInteger.valueOf(total)) + " Money",
                        " ",
                        ChatColor.LIGHT_PURPLE + "Companions will fall from the sky!"
                ));
    }

    private ItemStack toggleAnimationsItem() {
        return item(Material.BLAZE_ROD, ChatColor.GREEN + "Toggle Animations",
                List.of(
                        ChatColor.GRAY + "Toggle whether or not to play an animation",
                        ChatColor.GRAY + "when opening companion eggs.",
                        " ",
                        ChatColor.GREEN + "Click to toggle animations!"
                ));
    }

    private ItemStack companionPreview(CompanionService.CompanionDefinition definition, String zoneId, int stage) {
        String rarity = definition.rarity().color() + capitalize(definition.rarity().name());
        return item(definition.previewMaterial(), definition.rarity().color() + definition.name(),
                List.of(
                        ChatColor.GRAY + "Rarity: " + rarity,
                        ChatColor.GRAY + "Multiplier: " + ChatColor.GREEN + String.format(Locale.US, "%.3fx", definition.baseMultiplier()),
                        " ",
                        ChatColor.YELLOW + "[Zone " + capitalize(zoneId) + " Stage " + stage + "]"
                ));
    }

    private ItemStack dragonEggShortcutItem(String zoneId, int stage) {
        if (zoneId == null || zoneId.isBlank()) {
            return item(Material.DRAGON_EGG, ChatColor.DARK_GRAY + "Dragon Egg", List.of(
                    ChatColor.GRAY + "No active zone found.",
                    ChatColor.YELLOW + "Enter a zone to hatch companions."
            ));
        }

        return item(Material.DRAGON_EGG, ChatColor.LIGHT_PURPLE + "Dragon Egg", List.of(
                ChatColor.GRAY + "Zone: " + ChatColor.YELLOW + capitalize(zoneId),
                ChatColor.GRAY + "Stage: " + ChatColor.YELLOW + stage,
                ChatColor.GREEN + "Click to open companion eggs"
        ));
    }

    private ItemStack companionHudItem(String zoneId, int stage) {
        if (zoneId == null || zoneId.isBlank()) {
            return item(Material.COMPASS, ChatColor.GRAY + "Companion HUD", List.of(
                    ChatColor.GRAY + "Zone: " + ChatColor.RED + "Unknown",
                    ChatColor.GRAY + "Stage: " + ChatColor.RED + "-"
            ));
        }

        long price = companionService.costPerDraw(stage);
        return item(Material.COMPASS, ChatColor.AQUA + "Companion HUD", List.of(
                ChatColor.GRAY + "Zone: " + ChatColor.YELLOW + capitalize(zoneId),
                ChatColor.GRAY + "Stage: " + ChatColor.YELLOW + stage,
                ChatColor.GRAY + "Price / Egg: " + ChatColor.GREEN + NumberFormat.compact(BigInteger.valueOf(price)) + " Money"
        ));
    }

    private ItemStack deleteToggleItem(boolean enabled, String awaitingConfirm) {
        if (!enabled) {
            return item(Material.GLOWSTONE_DUST, ChatColor.GOLD + "Delete Companions", List.of(
                    ChatColor.GRAY + "Delete multiple companions at once by selecting them",
                    ChatColor.GRAY + "and clicking the button again to confirm deletion.",
                    " ",
                    ChatColor.GREEN + "Click to select companions for deletion!"
            ));
        }
        if (awaitingConfirm != null) {
            return item(Material.REDSTONE, ChatColor.RED + "Confirm Delete", List.of(
                    ChatColor.RED + "This action cannot be undone.",
                    ChatColor.YELLOW + "Click to delete selected companions now."
            ));
        }
        return item(Material.BLAZE_POWDER, ChatColor.YELLOW + "Delete Mode Enabled", List.of(
                ChatColor.GRAY + "Click companions to select them.",
                ChatColor.YELLOW + "Then click this button again to confirm."
        ));
    }

    private ItemStack bulkDeleteItem(String pendingLabel) {
        if (pendingLabel == null) {
            return item(Material.NETHER_STAR, ChatColor.RED + "Bulk Delete", List.of(
                    ChatColor.GRAY + "Delete all companions with a specific rarity or zone",
                    ChatColor.GRAY + "in a dedicated filter menu.",
                    " ",
                    ChatColor.GREEN + "Click to bulk delete companions!"
            ));
        }
        return item(Material.REDSTONE_BLOCK, ChatColor.DARK_RED + "Confirm Bulk Delete", List.of(
                ChatColor.GRAY + "Selected filter: " + ChatColor.YELLOW + pendingLabel,
                ChatColor.RED + "Click to permanently delete companions."
        ));
    }

    private ItemStack bulkRarityItem(CompanionService.CompanionRarity rarity) {
        ItemStack item = item(Material.PAPER, rarity.color() + "Rarity: " + rarity.name(), List.of(
                ChatColor.GRAY + "Delete all " + rarity.color() + rarity.name() + ChatColor.GRAY + " companions.",
                ChatColor.YELLOW + "Click to mark for confirm."
        ));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(org.bukkit.NamespacedKey.minecraft("bulk_filter"), org.bukkit.persistence.PersistentDataType.STRING, "RARITY:" + rarity.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack bulkZoneItem(String zoneId) {
        ItemStack item = item(Material.MAP, ChatColor.GREEN + "Zone: " + capitalize(zoneId), List.of(
                ChatColor.GRAY + "Delete all companions from this zone.",
                ChatColor.YELLOW + "Click to mark for confirm."
        ));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(org.bukkit.NamespacedKey.minecraft("bulk_filter"), org.bukkit.persistence.PersistentDataType.STRING, "ZONE:" + zoneId);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String readBulkFilter(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(org.bukkit.NamespacedKey.minecraft("bulk_filter"), org.bukkit.persistence.PersistentDataType.STRING);
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return "Unknown";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String readCompanionId(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return null;
        }
        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped != null && stripped.startsWith("ID:")) {
                return stripped.substring(3);
            }
        }
        return null;
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

    private void fill(Inventory inv) {
        ItemStack glass = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass);
            }
        }
    }

    private enum BulkMode {
        RARITY,
        ZONE
    }

    private record MenuContext(String menu,
                               String zoneId,
                               int stage,
                               int page,
                               boolean selectionMode,
                               Set<String> selectedIds,
                               String awaitingDeleteConfirm,
                               String awaitingBulkConfirm,
                               BulkMode pendingBulkMode,
                               String pendingBulkValue) {
        static MenuContext base() {
            return new MenuContext("companions", null, 1, 1, false, new HashSet<>(), null, null, null, null);
        }
    }
}
