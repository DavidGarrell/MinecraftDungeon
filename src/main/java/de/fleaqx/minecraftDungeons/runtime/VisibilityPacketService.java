package de.fleaqx.minecraftDungeons.runtime;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VisibilityPacketService implements VisibilityService {

    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;
    private final Map<Integer, UUID> ownerByEntityId = new ConcurrentHashMap<>();

    public VisibilityPacketService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void start() {
        List<PacketType> types = List.of(
                PacketType.Play.Server.SPAWN_ENTITY,
                PacketType.Play.Server.ENTITY_METADATA,
                PacketType.Play.Server.ENTITY_TELEPORT,
                PacketType.Play.Server.REL_ENTITY_MOVE,
                PacketType.Play.Server.REL_ENTITY_MOVE_LOOK,
                PacketType.Play.Server.ENTITY_LOOK,
                PacketType.Play.Server.ENTITY_HEAD_ROTATION,
                PacketType.Play.Server.ENTITY_VELOCITY,
                PacketType.Play.Server.ENTITY_EQUIPMENT,
                PacketType.Play.Server.ENTITY_STATUS,
                PacketType.Play.Server.ANIMATION
        );

        protocolManager.addPacketListener(new PacketAdapter(plugin, types) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                if (packet.getIntegers().size() == 0) {
                    return;
                }

                int entityId = packet.getIntegers().read(0);
                UUID owner = ownerByEntityId.get(entityId);
                if (owner == null) {
                    return;
                }

                if (!event.getPlayer().getUniqueId().equals(owner)) {
                    event.setCancelled(true);
                }
            }
        });
    }

    public void shutdown() {
        protocolManager.removePacketListeners(plugin);
        ownerByEntityId.clear();
    }

    public void register(Entity entity, UUID owner) {
        ownerByEntityId.put(entity.getEntityId(), owner);
    }

    public void unregister(Entity entity) {
        ownerByEntityId.remove(entity.getEntityId());
    }
}
