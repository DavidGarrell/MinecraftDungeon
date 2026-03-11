package de.fleaqx.minecraftDungeons.runtime;

import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.model.MobEntry;
import de.fleaqx.minecraftDungeons.model.MobRarity;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualHealthService {

    public record VirtualHealth(UUID owner, int level, BigInteger max, BigInteger current, MobEntry mob) {
    }

    private final Map<UUID, VirtualHealth> healthByEntity = new ConcurrentHashMap<>();

    public void register(LivingEntity entity, UUID owner, int level, MobEntry mob) {
        VirtualHealth health = new VirtualHealth(owner, level, mob.health(), mob.health(), mob);
        healthByEntity.put(entity.getUniqueId(), health);
        updateName(entity, health);
    }

    public boolean isManaged(UUID entityId) {
        return healthByEntity.containsKey(entityId);
    }

    public VirtualHealth get(UUID entityId) {
        return healthByEntity.get(entityId);
    }

    public void remove(UUID entityId) {
        healthByEntity.remove(entityId);
    }

    public DamageResult applyDamage(LivingEntity entity, BigInteger damage) {
        if (damage.compareTo(BigInteger.ZERO) <= 0) {
            return DamageResult.ignored();
        }

        VirtualHealth state = healthByEntity.get(entity.getUniqueId());
        if (state == null) {
            return DamageResult.ignored();
        }

        if (state.mob() != null && "afk_mob".equalsIgnoreCase(state.mob().id())) {
            updateName(entity, state);
            return new DamageResult(false, state.owner(), state.mob());
        }

        BigInteger next = state.current().subtract(damage);
        if (next.compareTo(BigInteger.ZERO) <= 0) {
            healthByEntity.remove(entity.getUniqueId());
            return new DamageResult(true, state.owner(), state.mob());
        }

        VirtualHealth updated = new VirtualHealth(state.owner(), state.level(), state.max(), next, state.mob());
        healthByEntity.put(entity.getUniqueId(), updated);
        updateName(entity, updated);
        return new DamageResult(false, state.owner(), state.mob());
    }

    private void updateName(LivingEntity entity, VirtualHealth health) {
        String hp = NumberFormat.compact(health.current());

        if (health.mob().id().equalsIgnoreCase("afk_mob")) {
            entity.setCustomName(ChatColor.RED + "[AFK] " + ChatColor.GRAY + "[Level " + health.level() + "] "
                    + ChatColor.WHITE + capitalizeWords(health.mob().entityType().name()) + " "
                    + ChatColor.RED + "\u2764∞");
            entity.setCustomNameVisible(true);
            return;
        }

        ChatColor rarityColor = rarityColor(health.mob().rarity());
        String rarity = capitalize(health.mob().rarity().name());
        String mobName = capitalizeWords(health.mob().entityType().name());

        String name = rarityColor + "" + ChatColor.BOLD + "[" + rarity + "] "
                + ChatColor.GRAY + "[Level: " + health.level() + "] "
                + ChatColor.WHITE + mobName + " "
                + ChatColor.RED + "\u2764" + hp;

        entity.setCustomName(name);
        entity.setCustomNameVisible(true);
    }

    private ChatColor rarityColor(MobRarity rarity) {
        return switch (rarity) {
            case COMMON -> ChatColor.WHITE;
            case RARE -> ChatColor.AQUA;
            case EPIC -> ChatColor.LIGHT_PURPLE;
            case LEGENDARY -> ChatColor.GOLD;
            case BOSS -> ChatColor.DARK_RED;
        };
    }

    private String capitalize(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String capitalizeWords(String enumName) {
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            if (i + 1 < parts.length) {
                out.append(' ');
            }
        }
        return out.toString();
    }

    public record DamageResult(boolean dead, UUID owner, MobEntry mob) {
        public static DamageResult ignored() {
            return new DamageResult(false, null, null);
        }
    }
}

