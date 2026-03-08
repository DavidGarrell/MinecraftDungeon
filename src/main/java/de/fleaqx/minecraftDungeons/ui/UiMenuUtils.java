package de.fleaqx.minecraftDungeons.ui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class UiMenuUtils {

    private UiMenuUtils() {
    }

    public static Inventory createMenu(HumanEntity owner, int size, String title) {
        return Bukkit.createInventory(owner, size, title);
    }

    public static ItemStack item(Material material, String name, List<String> lore) {
        return HeadItemFactory.named(new ItemStack(material), name, lore);
    }

    public static void fillEmptySlots(Inventory inventory) {
        fillEmptySlots(inventory, Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static void fillEmptySlots(Inventory inventory, Material fillerMaterial, String fillerName) {
        ItemStack filler = item(fillerMaterial, fillerName, List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }
}
