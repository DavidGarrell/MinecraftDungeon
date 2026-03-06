package de.fleaqx.minecraftDungeons.runtime;

import de.fleaqx.minecraftDungeons.model.ZoneDefinition;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerDungeonSession {

    private final UUID playerId;
    private ZoneDefinition zone;
    private int currentStage;
    private int stageKills;
    private final Set<UUID> activeMobs = new HashSet<>();

    public PlayerDungeonSession(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public ZoneDefinition zone() {
        return zone;
    }

    public void zone(ZoneDefinition zone) {
        this.zone = zone;
    }

    public int currentStage() {
        return currentStage;
    }

    public void currentStage(int currentStage) {
        this.currentStage = currentStage;
    }

    public int stageKills() {
        return stageKills;
    }

    public void stageKills(int stageKills) {
        this.stageKills = Math.max(0, stageKills);
    }

    public void incrementStageKills() {
        this.stageKills++;
    }

    public Set<UUID> activeMobs() {
        return activeMobs;
    }
}
