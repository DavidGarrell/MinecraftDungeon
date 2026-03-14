package de.fleaqx.minecraftDungeons.ui;

import de.fleaqx.minecraftDungeons.currency.CurrencyType;
import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.model.StageDefinition;
import de.fleaqx.minecraftDungeons.model.ZoneDefinition;
import de.fleaqx.minecraftDungeons.runtime.DungeonService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ZoneMenuService {

    private final DungeonService dungeonService;
    private final Map<UUID, MenuContext> openMenus = new HashMap<>();

    public ZoneMenuService(DungeonService dungeonService) {
        this.dungeonService = dungeonService;
    }

    public void openZoneMenu(Player player) {
        Inventory inventory = UiMenuUtils.createMenu(player, 54, dungeonService.formatMessage("ui.zone-menu-title", "&8Dungeon Zones", Map.of()));

        int slot = 19;
        for (ZoneDefinition zone : dungeonService.sortedZones()) {
            boolean unlocked = dungeonService.isZoneUnlocked(player, zone);
            Material material = unlocked ? Material.LIME_WOOL : Material.RED_WOOL;
            ItemStack item = UiMenuUtils.item(material,
                    (unlocked ? ChatColor.GREEN : ChatColor.RED) + zone.displayName() + (unlocked ? " [Unlocked]" : " [Locked]"),
                    List.of(
                            ChatColor.GRAY + "Order: " + zone.order(),
                            ChatColor.GRAY + "Price: " + NumberFormat.compact(zone.unlockPrice()) + " Money",
                            unlocked ? ChatColor.GREEN + "Click to open stages" : ChatColor.YELLOW + "Click to unlock zone"
                    ));
            inventory.setItem(slot, item);
            slot++;
            if ((slot + 1) % 9 == 0) {
                slot += 2;
            }
        }

        inventory.setItem(4, UiMenuUtils.item(Material.NETHER_STAR,
                dungeonService.formatMessage("ui.max-upgrade-title", "&6Max Upgrade", Map.of()),
                List.of(
                        ChatColor.GRAY + "Buy as many zones as possible",
                        ChatColor.GRAY + "with your current Money.",
                        ChatColor.YELLOW + "Click to run max upgrade"
                )));

        inventory.setItem(6, UiMenuUtils.item(Material.ENDER_PEARL,
                dungeonService.formatMessage("ui.goto-zone-title", "&bGo To Current Zone", Map.of()),
                List.of(
                        ChatColor.GRAY + "Teleport to your highest",
                        ChatColor.GRAY + "unlocked zone spawn."
                )));

        UiMenuUtils.fillEmptySlots(inventory);
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), new MenuContext(MenuType.ZONES, null));
    }

    public void openStageMenu(Player player, ZoneDefinition zone) {
        String stageTitle = dungeonService.formatMessage("ui.stage-menu-title", "&8{zone} Stages", Map.of("{zone}", zone.displayName()));
        Inventory inventory = UiMenuUtils.createMenu(player, 54, stageTitle);
        int unlockedStage = dungeonService.unlockedStage(player, zone.id());
        int selectedStage = dungeonService.selectedStage(player, zone.id());

        for (int stageIndex = 1; stageIndex <= zone.totalStages(); stageIndex++) {
            StageDefinition stage = zone.stageByIndex(stageIndex);
            if (stage == null) {
                continue;
            }

            boolean unlocked = stageIndex <= unlockedStage;
            boolean selected = stageIndex == selectedStage;
            Material material = selected ? Material.EMERALD_BLOCK : (unlocked ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Price: " + NumberFormat.compact(stage.unlockPrice()) + " Money");
            lore.add(ChatColor.GRAY + "Mob pool: " + stage.mobs().size());
            if (unlocked) {
                lore.add(ChatColor.GREEN + "Click to select and teleport");
            } else if (stageIndex == unlockedStage + 1) {
                lore.add(ChatColor.YELLOW + "Click to unlock and teleport");
            } else {
                lore.add(ChatColor.RED + "Unlock previous stage first");
            }

            inventory.setItem(stageSlot(stageIndex), UiMenuUtils.item(material,
                    (unlocked ? ChatColor.GREEN : ChatColor.RED) + "Stage " + stageIndex,
                    lore));
        }

        inventory.setItem(49, UiMenuUtils.item(Material.ARROW, ChatColor.YELLOW + "Back", List.of(ChatColor.GRAY + "Back to zones")));
        UiMenuUtils.fillEmptySlots(inventory);
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), new MenuContext(MenuType.STAGES, zone.id()));
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        MenuContext context = openMenus.get(player.getUniqueId());
        if (context == null) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        int slot = event.getSlot();
        if (context.type() == MenuType.ZONES) {
            handleZoneMenuClick(player, slot);
            return;
        }

        if (context.type() == MenuType.STAGES && context.zoneId() != null) {
            handleStageMenuClick(player, context.zoneId(), slot);
        }
    }

    public void handleClose(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    private void handleZoneMenuClick(Player player, int slot) {
        if (slot == 4) {
            int purchased = dungeonService.maxUpgradeStages(player);
            if (purchased <= 0) {
                player.sendMessage(dungeonService.formatMessage("messages.no-purchasable-zone", "&cNo purchasable stage progression found.", Map.of()));
            } else {
                player.sendMessage(dungeonService.formatMessage("messages.max-upgrade-bought", "&aMax upgrade bought {amount} progression step(s).", Map.of("{amount}", String.valueOf(purchased))));
            }
            openZoneMenu(player);
            return;
        }

        if (slot == 6) {
            dungeonService.zoneByOrder(dungeonService.profile(player).unlockedZoneOrder()).ifPresentOrElse(zone -> {
                if (zone.spawn() != null && zone.spawn().getWorld() != null) {
                    player.teleport(zone.spawn());
                } else {
                    player.sendMessage(dungeonService.formatMessage("messages.zone-spawn-not-set", "&cZone spawn is not set.", Map.of()));
                }
            }, () -> player.sendMessage(dungeonService.formatMessage("messages.no-unlocked-zone", "&cNo unlocked zone found.", Map.of())));
            return;
        }

        ZoneDefinition clickedZone = zoneBySlot(slot);
        if (clickedZone == null) {
            return;
        }

        if (!dungeonService.isZoneUnlocked(player, clickedZone)) {
            if (dungeonService.removeCurrency(player, CurrencyType.MONEY, clickedZone.unlockPrice())) {
                dungeonService.profile(player).unlockedZoneOrder(clickedZone.order());
                dungeonService.profile(player).unlockedStage(clickedZone.id(), 1);
                player.sendMessage(dungeonService.formatMessage("messages.zone-unlocked", "&aZone unlocked: {zone}", Map.of("{zone}", clickedZone.displayName())));
            } else {
                player.sendMessage(dungeonService.formatMessage("messages.not-enough-money", "&cNot enough Money.", Map.of()));
            }
            openZoneMenu(player);
            return;
        }

        openStageMenu(player, clickedZone);
    }

    private void handleStageMenuClick(Player player, String zoneId, int slot) {
        if (slot == 49) {
            openZoneMenu(player);
            return;
        }

        ZoneDefinition zone = dungeonService.zoneById(zoneId).orElse(null);
        if (zone == null) {
            return;
        }

        int stageIndex = stageBySlot(slot);
        if (stageIndex < 1 || stageIndex > zone.totalStages()) {
            return;
        }

        int unlockedStage = dungeonService.unlockedStage(player, zone.id());
        if (stageIndex <= unlockedStage) {
            selectAndTeleport(player, zone, stageIndex);
            return;
        }

        boolean unlocked = dungeonService.unlockStage(player, zone, stageIndex);
        if (unlocked) {
            player.sendMessage(dungeonService.formatMessage("messages.stage-unlocked", "&aStage unlocked: {stage}", Map.of("{stage}", String.valueOf(stageIndex))));
            selectAndTeleport(player, zone, stageIndex);
        } else {
            player.sendMessage(dungeonService.formatMessage("messages.stage-cannot-unlock", "&cStage cannot be unlocked.", Map.of()));
            openStageMenu(player, zone);
        }
    }

    private void selectAndTeleport(Player player, ZoneDefinition zone, int stageIndex) {
        dungeonService.selectStage(player, zone.id(), stageIndex);
        if (zone.spawn() != null && zone.spawn().getWorld() != null) {
            player.teleport(zone.spawn());
        }
        dungeonService.forceStart(player, zone, stageIndex);
        player.sendMessage(dungeonService.formatMessage("messages.stage-selected", "&aSelected Stage {stage} in {zone}", Map.of("{stage}", String.valueOf(stageIndex), "{zone}", zone.displayName())));
        player.closeInventory();
    }

    private ZoneDefinition zoneBySlot(int slot) {
        List<ZoneDefinition> zones = dungeonService.sortedZones();
        int currentSlot = 19;
        for (ZoneDefinition zone : zones) {
            if (slot == currentSlot) {
                return zone;
            }
            currentSlot++;
            if ((currentSlot + 1) % 9 == 0) {
                currentSlot += 2;
            }
        }
        return null;
    }

    private int stageSlot(int stage) {
        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
        return stage >= 1 && stage <= slots.length ? slots[stage - 1] : 10;
    }

    private int stageBySlot(int slot) {
        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i + 1;
            }
        }
        return -1;
    }

    private enum MenuType {
        ZONES,
        STAGES
    }

    private record MenuContext(MenuType type, String zoneId) {
    }
}
