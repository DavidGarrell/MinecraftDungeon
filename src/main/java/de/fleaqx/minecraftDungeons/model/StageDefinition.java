package de.fleaqx.minecraftDungeons.model;

import java.math.BigInteger;
import java.util.List;

public record StageDefinition(int index, BigInteger unlockPrice, List<MobEntry> mobs) {
}
