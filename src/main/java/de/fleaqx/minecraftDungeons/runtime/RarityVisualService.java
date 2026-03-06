package de.fleaqx.minecraftDungeons.runtime;

import de.fleaqx.minecraftDungeons.model.MobRarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.EnumMap;
import java.util.Map;

public class RarityVisualService {

    private final Scoreboard scoreboard;
    private final Map<MobRarity, Team> teams = new EnumMap<>(MobRarity.class);

    public RarityVisualService() {
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        createTeam(MobRarity.RARE, ChatColor.AQUA);
        createTeam(MobRarity.EPIC, ChatColor.LIGHT_PURPLE);
        createTeam(MobRarity.LEGENDARY, ChatColor.GOLD);
        createTeam(MobRarity.BOSS, ChatColor.DARK_RED);
    }

    public void apply(Entity entity, MobRarity rarity) {
        clear(entity);
        Team team = teams.get(rarity);
        if (team == null) {
            entity.setGlowing(false);
            return;
        }

        String key = entity.getUniqueId().toString();
        team.addEntry(key);
        entity.setGlowing(true);
    }

    public void clear(Entity entity) {
        String key = entity.getUniqueId().toString();
        for (Team team : teams.values()) {
            if (team.hasEntry(key)) {
                team.removeEntry(key);
            }
        }
        entity.setGlowing(false);
    }

    private void createTeam(MobRarity rarity, ChatColor color) {
        String name = "mdg_" + rarity.name().toLowerCase();
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }

        team.setColor(color);
        teams.put(rarity, team);
    }
}
