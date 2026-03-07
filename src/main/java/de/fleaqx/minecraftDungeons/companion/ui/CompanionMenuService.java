package de.fleaqx.minecraftDungeons.companion.ui;

import de.fleaqx.minecraftDungeons.companion.CompanionService;
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

import java.util.*;

public class CompanionMenuService {

    private final CompanionService companionService;
    private final Map<UUID, MenuContext> contexts = new HashMap<>();

    public CompanionMenuService(CompanionService companionService) {
        this.companionService = companionService;
    }

    public void openCompanions(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54, "Companions");
        int maxSlots = companionService.maxEquipSlots(player);
        List<CompanionService.OwnedCompanion> equipped = companionService.equipped(player);

        for (int i = 0; i < 6; i++) {
            boolean unlocked = i < maxSlots;
            ItemStack slotItem = unlocked
                    ? item(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "Equip Slot " + (i + 1), List.of(ChatColor.GRAY + "Click a companion below"))
                    : item(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "Locked Slot", List.of(ChatColor.GRAY + "Permission required"));
            inv.setItem(2 + i, slotItem);
        }

        for (int i = 0; i < equipped.size() && i < 6; i++) {
            inv.setItem(2 + i, companionHead(equipped.get(i), true));
        }

        int slot = 18;
        for (CompanionService.OwnedCompanion companion : companionService.owned(player)) {
            if (slot >= 54) {
                break;
            }
            inv.setItem(slot++, companionHead(companion, false));
        }

        inv.setItem(48, item(Material.DIAMOND, ChatColor.AQUA + "Equip Best", List.of(ChatColor.GREEN + "Click to equip best companions")));
        inv.setItem(49, item(Material.BARRIER, ChatColor.RED + "Close", List.of(ChatColor.GRAY + "Close menu")));
        fill(inv);
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new MenuContext("companions", null, 1));
    }

    public void openEggMenu(Player player, String zoneId, int stage) {
        Inventory inv = Bukkit.createInventory(player, 27, "Companion Eggs");

        long price = companionService.costPerDraw(stage);
        inv.setItem(4, HeadItemFactory.head(
                "http://textures.minecraft.net/texture/b23c6f17cb43cc6c5cfcc8ef3f7480fcead0b5e1c574b95f2c98b5eb2d646e47",
                ChatColor.GREEN + "Zone Egg",
                List.of(
                        ChatColor.GRAY + "Zone: " + ChatColor.YELLOW + zoneId,
                        ChatColor.GRAY + "Stage: " + ChatColor.YELLOW + stage,
                        ChatColor.GRAY + "Price per draw: " + ChatColor.GREEN + price + " Money"
                )
        ));

        inv.setItem(10, rollItem(1, price));
        inv.setItem(12, rollItem(3, price));
        inv.setItem(14, rollItem(10, price));
        inv.setItem(16, rollItem(64, price));
        inv.setItem(22, item(Material.CHEST, ChatColor.AQUA + "My Companions", List.of(ChatColor.GRAY + "Open companion inventory")));

        fill(inv);
        player.openInventory(inv);
        contexts.put(player.getUniqueId(), new MenuContext("eggs", zoneId, stage));
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
        if (ctx.menu.equals("eggs")) {
            int amount = switch (slot) {
                case 10 -> 1;
                case 12 -> 3;
                case 14 -> 10;
                case 16 -> 64;
                default -> 0;
            };
            if (amount > 0) {
                CompanionService.RollBatchResult result = companionService.roll(player, ctx.zoneId, ctx.stage, amount);
                if (!result.success()) {
                    player.sendMessage(ChatColor.RED + "Not enough money. Cost: " + result.totalCost());
                } else {
                    player.sendMessage(ChatColor.GREEN + "You opened " + amount + " eggs for " + result.totalCost() + " Money.");
                    CompanionService.OwnedCompanion best = result.companions().stream().max(Comparator.comparingDouble(CompanionService.OwnedCompanion::multiplier)).orElse(null);
                    if (best != null) {
                        player.sendMessage(best.rarity().color() + "Best roll: " + best.name() + ChatColor.GRAY + " [" + best.mutation().name() + "] " + ChatColor.GREEN + best.multiplier() + "x");
                    }
                }
                openEggMenu(player, ctx.zoneId, ctx.stage);
                return true;
            }
            if (slot == 22) {
                openCompanions(player);
            }
            return true;
        }

        if (ctx.menu.equals("companions")) {
            if (slot == 49) {
                player.closeInventory();
                return true;
            }
            if (slot == 48) {
                companionService.equipBest(player);
                player.sendMessage(ChatColor.GREEN + "Equipped best companions.");
                openCompanions(player);
                return true;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) {
                return true;
            }
            String companionId = readCompanionId(clicked);
            if (companionId != null) {
                companionService.equip(player, companionId);
                openCompanions(player);
            }
        }

        return true;
    }

    public void handleClose(InventoryCloseEvent event) {
        contexts.remove(event.getPlayer().getUniqueId());
    }

    private ItemStack companionHead(CompanionService.OwnedCompanion companion, boolean equipped) {
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
                        equipped ? ChatColor.GREEN + "Equipped" : ChatColor.YELLOW + "Click to equip / unequip"
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
        return item(Material.INK_SAC, ChatColor.GREEN + "Open " + amount + "x",
                List.of(ChatColor.GRAY + "Cost: " + ChatColor.GOLD + total + " Money", ChatColor.YELLOW + "Click to draw"));
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

    private record MenuContext(String menu, String zoneId, int stage) {
    }
}
