package byow.game.save;

import byow.game.tile.TileType;
import byow.game.worldGenerator.*;
import java.util.ArrayList;
import java.util.List;

/** In-memory cleanup of retired room art. Never touches disk or reruns world generation. */
public final class RoomLayoutMigration {
    private RoomLayoutMigration() { }

    public static WorldData toRgs(WorldData source) {
        TileType[][] world = new TileType[source.world().length][];
        boolean[][] owned = new boolean[world.length][];
        for (int x = 0; x < world.length; x++) {
            world[x] = source.world()[x].clone();
            owned[x] = new boolean[world[x].length];
        }
        List<RoomDesign> rooms = new ArrayList<>();
        for (RoomDesign room : source.rooms()) {
            List<Furniture> kept = new ArrayList<>();
            for (Furniture prop : room.furniture()) {
                if (!valid(world, owned, room, prop)) continue;
                kept.add(prop);
                for (int x = prop.x(); x < prop.x() + prop.width(); x++)
                    for (int y = prop.y(); y < prop.y() + prop.height(); y++) owned[x][y] = true;
            }
            var marks = room.decorations().stream().filter(d -> d.mark() != RoomDesign.Mark.PAPER
                && d.mark() != RoomDesign.Mark.CLOTH).toList();
            RoomTheme theme = room.theme().active() ? room.theme() : RoomTheme.STONE;
            rooms.add(new RoomDesign(room.bounds(), theme, room.variant(), marks, kept));
        }
        // Also remove orphans from saves with missing furniture metadata. Other terrain is untouched.
        for (int x = 0; x < world.length; x++) for (int y = 0; y < world[x].length; y++)
            if (occupancy(world[x][y]) && !owned[x][y]) world[x][y] = TileType.FLOOR;
        return new WorldData(world, rooms);
    }

    private static boolean valid(TileType[][] world, boolean[][] owned, RoomDesign room, Furniture prop) {
        if (prop.type() == null || !prop.type().active() || prop.orientation() == null
            || prop.type() == FurnitureType.ABANDONED_BOX
                && prop.orientation() != Furniture.Orientation.HORIZONTAL) return false;
        for (int x = prop.x(); x < prop.x() + prop.width(); x++)
            for (int y = prop.y(); y < prop.y() + prop.height(); y++)
                if (x < 0 || x >= world.length || y < 0 || y >= world[x].length
                    || !room.contains(x, y) || owned[x][y] || world[x][y] != TileType.FURNITURE) return false;
        return true;
    }

    private static boolean occupancy(TileType tile) {
        return tile == TileType.FURNITURE || tile == TileType.TALL_FURNITURE;
    }
}
