package byow.game.worldGenerator;

import byow.game.tile.TETile;
import byow.game.tile.Tileset;

/** Static scenery, never an interactable or a source of loot. */
public enum FurnitureType {
    // Legacy identifiers keep their original footprint for save compatibility.
    CRATE("储物木箱", 1, false), BARREL("旧木桶", 1, false), POT("陶罐", 1, false),
    BOOKCASE("废弃书架", 2, true), TABLE("旧木桌", 2, false), BED("破旧床铺", 2, false),
    ABANDONED_BOX("废弃木箱", 2, false);

    private final String displayName;
    private final int cells;
    private final boolean blocksVision;

    FurnitureType(String displayName, int cells, boolean blocksVision) {
        this.displayName = displayName;
        this.cells = cells;
        this.blocksVision = blocksVision;
    }
    public String displayName() { return displayName; }
    public int cells() { return cells; }
    public boolean blocksVision() { return blocksVision; }
    public boolean active() { return this == POT || this == ABANDONED_BOX; }
    public TETile tile() { return blocksVision ? Tileset.TALL_FURNITURE : Tileset.FURNITURE; }
}
