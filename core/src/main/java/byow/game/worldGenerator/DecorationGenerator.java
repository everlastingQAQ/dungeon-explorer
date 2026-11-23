package byow.game.worldGenerator;

import byow.game.random.GameRandom;
import byow.game.tile.TETile;
import byow.game.tile.Tileset;

/** 在不改变地图通行性的前提下生成成片的装饰地面。 */
public final class DecorationGenerator {
    /** Keep ordinary solid floor dominant and use variants as accents. */
    private static final int PATCH_COUNT = 10;
    private static final int CENTER_ATTEMPTS = 60;
    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 5;
    private static final int PATCH_DENSITY_PERCENT = 82;
    private static final int FLOWER_PERCENT = 12;
    private static final int SCATTERED_WEAR_PERCENT = 2;

    private static final TETile[] PATCH_TILES = {
        Tileset.GRASS,
        Tileset.GRASS,
        Tileset.SAND,
        Tileset.MOSS,
        Tileset.CRACKED_FLOOR,
        Tileset.MUD,
        Tileset.PEBBLES,
        Tileset.WORN_FLOOR,
        Tileset.WORN_FLOOR,
        Tileset.STAINED_FLOOR,
        Tileset.CHIPPED_FLOOR
    };

    private static final TETile[] SCATTERED_WEAR_TILES = {
        Tileset.WORN_FLOOR,
        Tileset.STAINED_FLOOR,
        Tileset.CHIPPED_FLOOR
    };

    public static void decorate(TETile[][] world, GameRandom random) {
        for (int patch = 0; patch < PATCH_COUNT; patch++) {
            Position center = findFloorCenter(world, random);
            if (center == null) {
                break;
            }

            int radiusX = random.nextInt(MIN_RADIUS, MAX_RADIUS + 1);
            int radiusY = random.nextInt(MIN_RADIUS, MAX_RADIUS + 1);
            TETile patchTile = PATCH_TILES[random.nextInt(PATCH_TILES.length)];
            paintPatch(world, random, center, radiusX, radiusY, patchTile);
        }
        scatterWear(world, random);
    }

    /** Adds sparse one-cell variations without turning the floor into noise. */
    private static void scatterWear(TETile[][] world, GameRandom random) {
        for (int x = 1; x < world.length - 1; x++) {
            for (int y = 1; y < world[0].length - 1; y++) {
                if (!world[x][y].equals(Tileset.FLOOR)
                    || random.nextInt(100) >= SCATTERED_WEAR_PERCENT) {
                    continue;
                }
                world[x][y] = SCATTERED_WEAR_TILES[
                    random.nextInt(SCATTERED_WEAR_TILES.length)
                ];
            }
        }
    }

    private static Position findFloorCenter(TETile[][] world, GameRandom random) {
        for (int attempt = 0; attempt < CENTER_ATTEMPTS; attempt++) {
            int x = random.nextInt(1, world.length - 1);
            int y = random.nextInt(1, world[0].length - 1);
            if (world[x][y].equals(Tileset.FLOOR)) {
                return new Position(x, y);
            }
        }
        return null;
    }

    private static void paintPatch(
        TETile[][] world,
        GameRandom random,
        Position center,
        int radiusX,
        int radiusY,
        TETile patchTile
    ) {
        for (int x = center.x() - radiusX; x <= center.x() + radiusX; x++) {
            for (int y = center.y() - radiusY; y <= center.y() + radiusY; y++) {
                if (!isInterior(world, x, y) || !world[x][y].equals(Tileset.FLOOR)) {
                    continue;
                }

                double normalizedDistance = square((x - center.x()) / (double) radiusX)
                    + square((y - center.y()) / (double) radiusY);
                if (normalizedDistance > 1.0
                    || random.nextInt(100) >= PATCH_DENSITY_PERCENT) {
                    continue;
                }

                if (patchTile.equals(Tileset.GRASS)) {
                    world[x][y] = random.nextInt(100) < FLOWER_PERCENT
                        ? Tileset.FLOWER
                        : Tileset.GRASS;
                } else {
                    world[x][y] = patchTile;
                }
            }
        }
    }

    private static boolean isInterior(TETile[][] world, int x, int y) {
        return x > 0 && x < world.length - 1 && y > 0 && y < world[0].length - 1;
    }

    private static double square(double value) {
        return value * value;
    }

    private record Position(int x, int y) {}

    private DecorationGenerator() {}
}
