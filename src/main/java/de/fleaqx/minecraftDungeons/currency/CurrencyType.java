package de.fleaqx.minecraftDungeons.currency;

public enum CurrencyType {
    MONEY("money"),
    SOULS("souls"),
    ESSENCE("essence"),
    SHARDS("shards");

    private final String key;

    CurrencyType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
