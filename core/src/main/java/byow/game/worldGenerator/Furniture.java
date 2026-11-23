package byow.game.worldGenerator;

/** Anchor is the lower-left occupied cell. Group IDs are local to a room. */
public record Furniture(int x, int y, FurnitureType type, Orientation orientation, int variant, int group) {
    public enum Orientation { HORIZONTAL, VERTICAL }
    public int width() { return orientation == Orientation.HORIZONTAL ? type.cells() : 1; }
    public int height() { return orientation == Orientation.VERTICAL ? type.cells() : 1; }
    public boolean contains(int px, int py) {
        return px >= x && px < x + width() && py >= y && py < y + height();
    }
}
