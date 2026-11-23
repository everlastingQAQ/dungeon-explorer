package byow.game.worldGenerator;

import byow.game.tile.TETile;
import byow.game.tile.Tileset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WallGeneratorTest {

    @Test
    void rebuildRemovesOldWallsAndCreatesOneFinalRing() {
        TETile[][] world = filledWorld(7, 7, Tileset.WALL);
        world[3][3] = Tileset.FLOOR;
        world[4][3] = Tileset.OPEN_DOOR;

        WallGenerator.rebuild(world);

        for (int x = 0; x < world.length; x++) {
            for (int y = 0; y < world[0].length; y++) {
                if ((x == 3 && y == 3) || (x == 4 && y == 3)) {
                    continue;
                }
                boolean bordersWalkable = x >= 2 && x <= 5 && y >= 2 && y <= 4;
                assertEquals(bordersWalkable ? Tileset.WALL : Tileset.NOTHING, world[x][y]);
            }
        }
        assertEquals(Tileset.FLOOR, world[3][3]);
        assertEquals(Tileset.OPEN_DOOR, world[4][3]);
    }

    private static TETile[][] filledWorld(int width, int height, TETile tile) {
        TETile[][] world = new TETile[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                world[x][y] = tile;
            }
        }
        return world;
    }
}
