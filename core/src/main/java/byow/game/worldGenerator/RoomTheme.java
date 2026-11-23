package byow.game.worldGenerator;

public enum RoomTheme {
    STONE("素石厅", 20), COLONNADE("柱廊厅", 15), RUIN("残破厅", 10),
    MOSS("苔痕厅", 10), EMBLEM("纹章厅", 5), STORAGE("旧箱储藏室", 12),
    // Retained only for deserializing old saves; never selected for new rooms.
    LIBRARY("废弃书库", 0), BARRACKS("旧营房", 0), POTTERY("陶罐厅", 10),
    INLAY("镶边大厅", 10), MIXED_STORAGE("混合库房", 8);

    private final String displayName;
    private final int weight;

    RoomTheme(String displayName, int weight) {
        this.displayName = displayName;
        this.weight = weight;
    }

    public String displayName() { return displayName; }
    public int weight() { return weight; }
    public boolean furnished() {
        return this == STORAGE || this == POTTERY || this == MIXED_STORAGE;
    }
    public boolean active() { return weight > 0; }
}
