package byow.game.door;

import byow.game.GameExit;
import byow.game.tile.*;
import byow.game.worldGenerator.RoomDesign;
import java.util.*;

/** Deterministic groups derived from saved terrain and room bounds; never carves new cells. */
public final class DoorLayout {
    public record Door(int x, int y, int width, boolean open, boolean exit) {
        public boolean contains(int px, int py) { return py == y && px >= x && px < x + width; }
        public TETile appearance() { return open ? Tileset.OPEN_DOOR : Tileset.CLOSED_DOOR; }
        public void sync(TETile[][] world, boolean opened) {
            for (int dx = 0; dx < width; dx++)
                world[x + dx][y] = opened ? Tileset.OPEN_DOOR : Tileset.CLOSED_DOOR;
        }
    }

    public static boolean isDoor(TETile tile) {
        return tile.equals(Tileset.OPEN_DOOR) || tile.equals(Tileset.CLOSED_DOOR);
    }

    public static List<Door> collect(TETile[][] world, List<RoomDesign> rooms, GameExit exit) {
        List<Door> result = new ArrayList<>();
        for (int y = 0; y < world[0].length; y++) {
            for (int x = 0; x < world.length; x++) {
                if (exit != null && exit.contains(x, y)) continue;
                if (!isDoor(world[x][y])) continue;
                int width = canPair(world, rooms, exit, x, y) ? 2 : 1;
                result.add(new Door(x, y, width, world[x][y].equals(Tileset.OPEN_DOOR), false));
                x += width - 1;
            }
        }
        // Keep the gameplay exit authoritative even if its terrain was stale.
        if (exit != null) result.add(new Door(exit.x(), exit.y(), exit.width(), exit.active(), true));
        return List.copyOf(result);
    }

    public static Door at(TETile[][] world, List<RoomDesign> rooms, GameExit exit, int x, int y) {
        for (Door door : collect(world, rooms, exit)) if (door.contains(x, y)) return door;
        return null;
    }

    /** Includes the non-colliding upper frame of the square two-cell-wide sprite. */
    public static Door drawnAt(TETile[][] world, List<RoomDesign> rooms, GameExit exit, int x, int y) {
        List<Door> doors = collect(world, rooms, exit);
        for (Door door : doors) if (door.contains(x, y)) return door;
        for (Door door : doors)
            if (door.width() == 2 && door.contains(x, y - 1)) return door;
        return null;
    }

    private static boolean canPair(TETile[][] world, List<RoomDesign> rooms, GameExit exit, int x, int y) {
        if (x < 1 || x + 2 >= world.length || y < 1 || y + 1 >= world[0].length) return false;
        if (!world[x][y].equals(world[x + 1][y]) || exit != null && exit.contains(x + 1, y)) return false;
        // Only a straight two-cell doorway: not a corner, T-junction, or part of a longer door run.
        if (!world[x - 1][y].equals(Tileset.WALL) || !world[x + 2][y].equals(Tileset.WALL)) return false;
        for (int dx = 0; dx < 2; dx++) {
            if (!TileRules.isWalkable(world[x + dx][y - 1])
                || !TileRules.isWalkable(world[x + dx][y + 1])
                || isDoor(world[x + dx][y - 1]) || isDoor(world[x + dx][y + 1])) return false;
        }
        for (RoomDesign room : rooms) {
            var b = room.bounds();
            if (x >= b.x() && x + 1 < b.x() + b.width()
                && (y == b.y() - 1 || y == b.y() + b.height())) return true;
        }
        return false;
    }

    private DoorLayout() {}
}
