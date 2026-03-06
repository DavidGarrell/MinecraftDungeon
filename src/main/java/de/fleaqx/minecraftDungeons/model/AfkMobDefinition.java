package de.fleaqx.minecraftDungeons.model;

import org.bukkit.Location;

public record AfkMobDefinition(
        boolean enabled,
        Location location,
        MobEntry mob,
        double hitsPerSecond
) {
}
