package de.fleaqx.minecraftDungeons.runtime;

import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;

public class DamageIndicatorService {

    private final JavaPlugin plugin;

    public DamageIndicatorService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnDamage(Player viewer, Entity target, BigInteger damage) {
        Location base = target.getLocation().clone().add(0, 0.6, 0);
        Location spawn = base.add(randomOffset(0.6), ThreadLocalRandom.current().nextDouble(0.0, 0.45), randomOffset(0.6));

        ArmorStand stand = spawn.getWorld().spawn(spawn, ArmorStand.class, as -> {
            as.setMarker(true);
            as.setInvisible(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setCollidable(false);
            as.setCustomNameVisible(true);
            as.setCustomName(ChatColor.RED + "\u2764" + NumberFormat.compact(damage));
        });

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(viewer.getUniqueId())) {
                online.hideEntity(plugin, stand);
            }
        }

        Vector velocity = randomDirection().multiply(0.07).setY(ThreadLocalRandom.current().nextDouble(0.03, 0.07));
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (!stand.isValid() || ticks > 30) {
                    stand.remove();
                    cancel();
                    return;
                }

                stand.teleport(stand.getLocation().add(velocity));
                velocity.multiply(0.92);
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private Vector randomDirection() {
        double x = ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
        double z = ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
        Vector vector = new Vector(x, 0.0, z);
        if (vector.lengthSquared() < 0.0001) {
            return new Vector(0.5, 0.0, 0.2);
        }
        return vector.normalize();
    }

    private double randomOffset(double max) {
        return ThreadLocalRandom.current().nextDouble(-max, max);
    }
}
