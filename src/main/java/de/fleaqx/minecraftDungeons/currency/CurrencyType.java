package de.fleaqx.minecraftDungeons.currency;

public enum CurrencyType {
    MONEY("money", "Money"),
    SOULS("souls", "Souls"),
    ESSENCE("essence", "Essence"),
    SHARDS("shards", "Shards");

    private final String key;
    private final String displayName;

    CurrencyType(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static CurrencyType fromInput(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        for (CurrencyType type : values()) {
            if (type.name().equalsIgnoreCase(input) || type.key().equalsIgnoreCase(input)) {
                return type;
            }
        }
        return null;
    }
}
