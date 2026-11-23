package byow.game.save;

import byow.game.tile.TileType;
import byow.game.worldGenerator.RoomDesign;
import java.util.List;

/**
 * 保存 World 的相关数据
 *
 * @author everlasting
 *
 */
public record WorldData(TileType[][] world, List<RoomDesign> rooms) {
    public WorldData {
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
    }

    public WorldData(TileType[][] world) { this(world, List.of()); }
}
