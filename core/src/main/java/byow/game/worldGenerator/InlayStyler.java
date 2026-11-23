package byow.game.worldGenerator;

import byow.game.tile.TETile;
import byow.game.tile.Tileset;
import java.util.ArrayList;
import java.util.List;

/** Complete nine-slice floor patches. Never modifies terrain or clips a patch around obstacles. */
final class InlayStyler {
    private InlayStyler() { }

    static RoomDesign style(TETile[][] world, Room room, int variant) {
        int floorCount = 0;
        for (int x = room.x(); x < room.x() + room.width(); x++)
            for (int y = room.y(); y < room.y() + room.height(); y++)
                if (world[x][y] == Tileset.FLOOR) floorCount++;
        boolean horizontal = room.width() >= room.height();
        int width = variant == 0 ? 3 : (variant == 1) == horizontal ? 5 : 3;
        int height = variant == 0 ? 3 : (variant == 1) == horizontal ? 3 : 5;
        for (int[] size : new int[][] {{width, height}, {3, 3}, {2, 2}}) {
            int w = size[0], h = size[1];
            if (w * h > floorCount / 4 || w > room.width() - 2 || h > room.height() - 2) continue;
            int x0 = room.x() + (room.width() - w) / 2;
            int y0 = room.y() + (room.height() - h) / 2;
            boolean clear = true;
            for (int x = x0; x < x0 + w; x++) for (int y = y0; y < y0 + h; y++)
                if (world[x][y] != Tileset.FLOOR) clear = false;
            if (!clear) continue;
            List<RoomDesign.Decoration> marks = new ArrayList<>();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int column = x == 0 ? 0 : x == w - 1 ? 2 : 1;
                int row = y == 0 ? 0 : y == h - 1 ? 2 : 1;
                marks.add(new RoomDesign.Decoration(x0 + x, y0 + y, RoomDesign.Mark.INLAY,
                    variant, row * 3 + column));
            }
            return new RoomDesign(room, RoomTheme.INLAY, variant, marks);
        }
        return new RoomDesign(room, RoomTheme.INLAY, variant, List.of());
    }
}
