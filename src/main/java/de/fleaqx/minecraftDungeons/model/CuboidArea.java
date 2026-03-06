package de.fleaqx.minecraftDungeons.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.concurrent.ThreadLocalRandom;

public record CuboidArea(
        String worldName,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
) {
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public Location randomSpawnLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        double x = ThreadLocalRandom.current().nextDouble(minX, maxX);
        double z = ThreadLocalRandom.current().nextDouble(minZ, maxZ);
        int y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
        y = Math.max(y, (int) Math.floor(minY));
        y = Math.min(y, (int) Math.floor(maxY));
        return new Location(world, x + 0.5, y, z + 0.5);
    }
}
