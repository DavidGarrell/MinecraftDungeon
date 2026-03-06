package de.fleaqx.minecraftDungeons.sword;

import org.bukkit.Material;

public record SwordDefinition(
        int id,
        String name,
        Material material,
        Integer customModelData
) {
}
