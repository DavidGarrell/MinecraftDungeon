package de.fleaqx.minecraftDungeons.runtime;

import de.fleaqx.minecraftDungeons.currency.NumberFormat;
import de.fleaqx.minecraftDungeons.model.MobRarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatBossBarService {

    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public void update(Player player, VirtualHealthService.VirtualHealth state) {
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID));
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        String rarity = capitalize(state.mob().rarity().name());
        String name = capitalizeWords(state.mob().entityType().name());
        String hp = NumberFormat.compact(state.current());

        bar.setTitle(color(state.mob().rarity()) + "" + ChatColor.BOLD + "[" + rarity + "] "
                + ChatColor.GRAY + name + " " + ChatColor.RED + "\u2764" + hp);
        bar.setColor(barColor(state.mob().rarity()));
        bar.setProgress(progress(state));
        bar.setVisible(true);
    }

    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
    }

    public void shutdown() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
            bar.setVisible(false);
        }
        bars.clear();
    }

    private double progress(VirtualHealthService.VirtualHealth state) {
        try {
            BigDecimal current = new BigDecimal(state.current());
            BigDecimal max = new BigDecimal(state.max());
            if (max.signum() <= 0) {
                return 1.0D;
            }
            double ratio = current.divide(max, MathContext.DECIMAL64).doubleValue();
            if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
                return 1.0D;
            }
            return Math.max(0.0D, Math.min(1.0D, ratio));
        } catch (Exception ignored) {
            return 1.0D;
        }
    }

    private ChatColor color(MobRarity rarity) {
        return switch (rarity) {
            case COMMON -> ChatColor.WHITE;
            case RARE -> ChatColor.AQUA;
            case EPIC -> ChatColor.LIGHT_PURPLE;
            case LEGENDARY -> ChatColor.GOLD;
            case BOSS -> ChatColor.DARK_RED;
        };
    }

    private BarColor barColor(MobRarity rarity) {
        return switch (rarity) {
            case COMMON -> BarColor.WHITE;
            case RARE -> BarColor.BLUE;
            case EPIC -> BarColor.PURPLE;
            case LEGENDARY -> BarColor.YELLOW;
            case BOSS -> BarColor.RED;
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
}
