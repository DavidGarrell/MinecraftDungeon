package de.fleaqx.minecraftDungeons.util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.UUID;

public final class EntityLookup {
    private EntityLookup() {
    }

    public static Entity find(UUID id) {
        if (id == null) {
            return null;
        }
        for (World world : Bukkit.getWorlds()) {
            Entity entity = findInWorld(world, id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    public static Entity findInWorld(World world, UUID id) {
        if (world == null || id == null) {
            return null;
        }
        for (Entity entity : world.getEntities()) {
            if (id.equals(entity.getUniqueId())) {
                return entity;
            }
        }
        return null;
    }
}
