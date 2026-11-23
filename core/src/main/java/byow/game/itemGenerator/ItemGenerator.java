package byow.game.itemGenerator;

import byow.game.item.Item;
import byow.game.item.ItemType;
import byow.game.player.Player;
import byow.game.random.GameRandom;
import byow.game.tile.TETile;
import byow.game.tile.TileRules;
import byow.game.worldGenerator.RoomDesign;
import java.util.ArrayList;
import java.util.List;
import static byow.game.GameConfig.ITEM_NUMBER;

/** Relics are sampled without replacement from eligible room floors only. */
public class ItemGenerator {
    public static List<Item> generate(TETile[][] world, Player player,
                                      GameRandom random, List<RoomDesign> rooms) {
        List<Position> available = new ArrayList<>();
        for (int x = 1; x < world.length - 1; x++) {
            for (int y = 1; y < world[x].length - 1; y++) {
                if (player.x() == x && player.y() == y
                    || !TileRules.canHostItem(world[x][y])) continue;
                for (RoomDesign room : rooms) {
                    if (room.contains(x, y)) {
                        available.add(new Position(x, y));
                        break;
                    }
                }
            }
        }
        if (available.size() < ITEM_NUMBER)
            throw new IllegalStateException("Not enough room floor space for relics");

        List<ItemType> types = new ArrayList<>(List.of(ItemType.values()));
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < ITEM_NUMBER; i++) {
            Position position = available.remove(random.nextInt(available.size()));
            ItemType type = types.remove(random.nextInt(types.size()));
            items.add(new Item(position.x(), position.y(), type));
        }
        return items;
    }

    private record Position(int x, int y) {}
}
