package de.fleaqx.minecraftDungeons.rebirth.ui;

import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.rebirth.RebirthService;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import de.fleaqx.minecraftDungeons.ui.UiMenuUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class RebirthMenuService {

    private final RebirthService rebirthService;
    private final DungeonService dungeonService;
    private final Map<UUID, String> openMenus = new HashMap<>();

    public RebirthMenuService(RebirthService rebirthService, DungeonService dungeonService) {
        this.rebirthService = rebirthService;
        this.dungeonService = dungeonService;
    }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 27, "Rebirth");

        BigInteger cost = rebirthService.nextCost(player);
        int rebirths = rebirthService.rebirths(player);
        int points = rebirthService.rebirthPoints(player);
        double currentMultiplier = rebirthService.moneyMultiplier(player);
        double nextMultiplier = rebirthService.nextMoneyMultiplier(player);
        BigInteger money = dungeonService.balance(player, CurrencyType.MONEY);

        inventory.setItem(11, UiMenuUtils.item(Material.NETHER_STAR,
                ChatColor.GREEN + "Rebirth Information",
                List.of(
                        ChatColor.GRAY + "Rebirth resets your progress but grants a",
                        ChatColor.GREEN + "permanent money multiplier" + ChatColor.GRAY + " and",
                        ChatColor.GOLD + "+1 Rebirth Point" + ChatColor.GRAY + ".",
                        " ",
                        ChatColor.YELLOW + "Information",
                        ChatColor.GRAY + "Rebirths: " + ChatColor.GREEN + rebirths,
                        ChatColor.GRAY + "Cost: " + ChatColor.GREEN + NumberFormat.compact(cost) + " Money",
                        ChatColor.GRAY + "Multiplier: " + ChatColor.RED + format(currentMultiplier)
                                + ChatColor.GRAY + " -> " + ChatColor.GREEN + format(nextMultiplier),
                        " ",
                        ChatColor.YELLOW + "Rewards",
                        ChatColor.GRAY + "Points: " + ChatColor.GREEN + "+1 Rebirth Point",
                        " ",
                        ChatColor.RED + "NOTE: Rebirth resets zones, stages,",
                        ChatColor.RED + "money, souls, essence, shards & swords.",
                        " ",
                        canAfford(player)
                                ? ChatColor.GREEN + "Click to rebirth"
                                : ChatColor.RED + "Missing: " + NumberFormat.compact(cost.subtract(money).max(BigInteger.ZERO)) + " Money"
                )));

        inventory.setItem(15, UiMenuUtils.item(Material.BEACON,
                ChatColor.GOLD + "Rebirth Upgrades",
                List.of(
                        ChatColor.GRAY + "Spend Rebirth Points to unlock",
                        ChatColor.GRAY + "permanent upgrades.",
                        " ",
                        ChatColor.YELLOW + "Information",
                        ChatColor.GRAY + "Current Points: " + ChatColor.GREEN + points,
                        " ",
                        ChatColor.GOLD + "Click to view upgrades",
                        ChatColor.DARK_GRAY + "(coming soon)"
                )));

        UiMenuUtils.fillEmptySlots(inventory);
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), "rebirth");
    }

    public boolean handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }
        if (!"rebirth".equals(openMenus.get(player.getUniqueId()))) {
            return false;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return true;
        }

        if (event.getSlot() == 11) {
            if (rebirthService.rebirth(player)) {
                player.sendMessage(ChatColor.GREEN + "Rebirth successful! You now have "
                        + rebirthService.rebirths(player) + " rebirths.");
            } else {
                player.sendMessage(ChatColor.RED + "Not enough money for rebirth.");
            }
            openMain(player);
            return true;
        }

        if (event.getSlot() == 15) {
            player.sendMessage(ChatColor.YELLOW + "Rebirth upgrades are coming soon.");
            return true;
        }

        return true;
    }

    public void handleClose(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    private String format(double value) {
        return String.format(Locale.US, "%.3fx", value);
    }

    private boolean canAfford(Player player) {
        return dungeonService.balance(player, CurrencyType.MONEY).compareTo(rebirthService.nextCost(player)) >= 0;
    }

    public ItemStack commandItem() {
        return UiMenuUtils.item(Material.NETHER_STAR, ChatColor.GOLD + "Open Rebirth Menu", List.of(ChatColor.GRAY + "Use /rebirth"));
    }
}
