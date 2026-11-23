package byow.game.render.world;

import byow.game.tile.TETile;

/** Continuous, code-drawn stone walls using the RGS gray palette. */
public final class WallAppearance {
    public static final int SIZE = 16;
    public static final int OUTLINE = 0x181425ff;
    public static final int FACE = 0x3a4466ff;
    public static final int CAP = 0x8b9bb4ff;
    public static final int LIGHT = 0xc0cbdcff;

    private WallAppearance() { }

    /** Bits follow image coordinates: NW, N, NE, W, E, SW, S, SE. */
    public static int mask(TETile[][] world, int x, int y) {
        int result = 0;
        int bit = 0;
        for (int dy = 1; dy >= -1; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < world.length && ny >= 0 && ny < world[nx].length
                    && isWall(world[nx][ny])) {
                    result |= 1 << bit;
                }
                bit++;
            }
        }
        return result;
    }

    public static boolean isWall(TETile tile) {
        return "wall".equals(tile.description()) || "pillar".equals(tile.description());
    }

    /**
     * Rasterizes the same continuous solid field on both sides of a tile seam.
     * The cap is inset from exposed boundaries; its south edge leaves a five
     * pixel vertical face. Lighting always comes from above, never from rotating
     * a wall-face texture. The diagonal samples close concave corners too.
     */
    public static int pixel(int mask, int x, int y) {
        if (!solid(mask, x - 1, y) || !solid(mask, x + 1, y)
            || !solid(mask, x, y - 1) || !solid(mask, x, y + 1)) {
            return OUTLINE;
        }
        if (!cap(mask, x, y)) {
            return FACE;
        }
        if (!cap(mask, x, y - 1)) {
            return LIGHT;
        }
        return CAP;
    }

    private static boolean cap(int mask, int x, int y) {
        for (int dy = -2; dy <= 5; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (!solid(mask, x + dx, y + dy)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean solid(int mask, int x, int y) {
        int column = Math.floorDiv(x, SIZE) + 1;
        int row = Math.floorDiv(y, SIZE) + 1;
        if (column == 1 && row == 1) {
            return true;
        }
        if (column < 0 || column > 2 || row < 0 || row > 2) {
            return false;
        }
        int bit = row * 3 + column;
        if (bit > 4) {
            bit--;
        }
        return (mask & (1 << bit)) != 0;
    }
}
