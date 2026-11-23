package byow.game.worldGenerator;

import byow.game.tile.TETile;
import java.util.List;

public record GeneratedWorld(TETile[][] terrain, List<RoomDesign> rooms) {
    public GeneratedWorld { rooms = List.copyOf(rooms); }
}
