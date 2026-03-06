package de.fleaqx.minecraftDungeons.model;

import org.bukkit.Location;

import java.math.BigInteger;
import java.util.List;

public record ZoneDefinition(
        String id,
        String displayName,
        int order,
        BigInteger unlockPrice,
        int totalStages,
        int mobsPerStage,
        CuboidArea area,
        Location spawn,
        List<StageDefinition> stages
) {
    public StageDefinition stageByIndex(int stage) {
        if (stage <= 0 || stage > stages.size()) {
            return null;
        }
        return stages.get(stage - 1);
    }
}
