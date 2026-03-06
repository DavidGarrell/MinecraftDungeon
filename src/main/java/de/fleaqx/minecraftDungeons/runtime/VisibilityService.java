package de.fleaqx.minecraftDungeons.runtime;

import org.bukkit.entity.Entity;

import java.util.UUID;

public interface VisibilityService {
    void start();
    void shutdown();
    void register(Entity entity, UUID owner);
    void unregister(Entity entity);
}
