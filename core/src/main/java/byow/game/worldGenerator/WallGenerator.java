package byow.game.worldGenerator;

import byow.game.tile.TETile;
import byow.game.tile.TileRules;
import byow.game.tile.Tileset;

/** Rebuilds walls from the finished walkable layout. */
public final class WallGenerator {

    /**
     * Discards every wall left by room and maze carving, then creates exactly
     * one wall ring around the final walkable map. Diagonal walls are included
     * so room corners are closed and can be autotiled by the renderer.
     */
    public static void rebuild(TETile[][] world) {
        boolean[][] walkable = snapshotWalkable(world);

        for (int x = 0; x < world.length; x++) {
            for (int y = 0; y < world[0].length; y++) {
                if (!walkable[x][y]) {
                    world[x][y] = Tileset.NOTHING;
                }
            }
        }

        for (int x = 0; x < world.length; x++) {
            for (int y = 0; y < world[0].length; y++) {
                if (walkable[x][y]) {
                    surroundWithWalls(world, walkable, x, y);
                }
            }
        }
    }

    private static boolean[][] snapshotWalkable(TETile[][] world) {
        boolean[][] result = new boolean[world.length][world[0].length];
        for (int x = 0; x < world.length; x++) {
            for (int y = 0; y < world[0].length; y++) {
                result[x][y] = TileRules.isWalkable(world[x][y]);
            }
        }
        return result;
    }

    private static void surroundWithWalls(
        TETile[][] world,
        boolean[][] walkable,
        int centerX,
        int centerY
    ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int x = centerX + dx;
                int y = centerY + dy;
                if (isInside(world, x, y) && !walkable[x][y]) {
                    world[x][y] = Tileset.WALL;
                }
            }
        }
    }

    private static boolean isInside(TETile[][] world, int x, int y) {
        return x >= 0 && x < world.length && y >= 0 && y < world[0].length;
    }

    private WallGenerator() {
    }
}
