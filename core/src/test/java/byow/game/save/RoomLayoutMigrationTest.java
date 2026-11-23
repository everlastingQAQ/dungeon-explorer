package byow.game.save;

import byow.game.Engine;
import byow.game.tile.TileConverter;
import byow.game.tile.TileType;
import byow.game.worldGenerator.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomLayoutMigrationTest {
    @TempDir Path directory;

    @Test void removesEveryRetiredTypeAndMarkButKeepsPotsBoxesAndNonFurnitureTerrain() {
        TileType[][] terrain = floor(20, 12);
        Room bounds = new Room(1, 1, 18, 10);
        List<Furniture> props = new ArrayList<>();
        int x = 1;
        for (FurnitureType type : FurnitureType.values()) {
            var p = new Furniture(x, 2, type, Furniture.Orientation.HORIZONTAL, 0, x);
            props.add(p);
            for (int dx = 0; dx < p.width(); dx++) terrain[x + dx][2] = type.blocksVision()
                ? TileType.TALL_FURNITURE : TileType.FURNITURE;
            x += type.cells() + 1;
        }
        terrain[1][7] = TileType.TALL_FURNITURE; // orphan
        terrain[2][7] = TileType.FURNITURE; // orphan
        terrain[3][7] = TileType.WALL;
        terrain[4][7] = TileType.PILLAR;
        terrain[5][7] = TileType.OPEN_DOOR;
        terrain[6][7] = TileType.CLOSED_DOOR;
        var marks = List.of(new RoomDesign.Decoration(1, 8, RoomDesign.Mark.PAPER, 0),
            new RoomDesign.Decoration(2, 8, RoomDesign.Mark.CLOTH, 0),
            new RoomDesign.Decoration(3, 8, RoomDesign.Mark.WEAR, 0),
            new RoomDesign.Decoration(4, 8, RoomDesign.Mark.SHARDS, 0));
        var original = new WorldData(terrain, List.of(new RoomDesign(bounds, RoomTheme.LIBRARY, 2, marks, props)));
        var result = RoomLayoutMigration.toRgs(original);
        var again = RoomLayoutMigration.toRgs(result);
        assertEquals(RoomTheme.STONE, result.rooms().getFirst().theme());
        assertEquals(props.stream().filter(p -> p.type().active()).toList(), result.rooms().getFirst().furniture());
        assertEquals(marks.subList(2, 4), result.rooms().getFirst().decorations());
        assertTrue(Arrays.deepEquals(result.world(), again.world()));
        assertEquals(result.rooms(), again.rooms());
        assertEquals(TileType.TALL_FURNITURE, terrain[1][7], "migration must not mutate its input");
        for (int px = 0; px < terrain.length; px++) for (int y = 0; y < terrain[px].length; y++) {
            var owner = result.rooms().getFirst().furnitureAt(px, y);
            TileType expected = (terrain[px][y] == TileType.FURNITURE || terrain[px][y] == TileType.TALL_FURNITURE)
                && owner == null ? TileType.FLOOR : terrain[px][y];
            assertEquals(expected, result.world()[px][y]);
        }
    }

    @Test void incompleteOrInvalidNewFurnitureIsRemovedWithoutOverwritingWallsOrDoors() {
        TileType[][] terrain = floor(10, 10);
        terrain[1][1] = TileType.FURNITURE;
        terrain[2][1] = TileType.CLOSED_DOOR;
        terrain[4][1] = TileType.FURNITURE;
        terrain[4][2] = TileType.FURNITURE;
        terrain[7][7] = TileType.FURNITURE;
        var room = new RoomDesign(new Room(1, 1, 8, 8), RoomTheme.BARRACKS, 0, List.of(), List.of(
            new Furniture(1, 1, FurnitureType.ABANDONED_BOX, Furniture.Orientation.HORIZONTAL, 0, 0),
            new Furniture(4, 1, FurnitureType.ABANDONED_BOX, Furniture.Orientation.VERTICAL, 0, 1),
            new Furniture(7, 7, FurnitureType.POT, Furniture.Orientation.VERTICAL, 2, 2)));
        var result = RoomLayoutMigration.toRgs(new WorldData(terrain, List.of(room)));
        assertEquals(RoomTheme.STONE, result.rooms().getFirst().theme());
        assertEquals(List.of(room.furniture().get(2)), result.rooms().getFirst().furniture());
        assertEquals(TileType.FLOOR, result.world()[1][1]);
        assertEquals(TileType.CLOSED_DOOR, result.world()[2][1]);
        assertEquals(TileType.FLOOR, result.world()[4][1]);
        assertEquals(TileType.FLOOR, result.world()[4][2]);
        assertEquals(TileType.FURNITURE, result.world()[7][7]);
    }

    @Test void engineMigratesOnlyInMemoryAndManualSavePreservesGameplayState() throws Exception {
        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", directory.toString());
            Engine original = new Engine(); input(original, "N42SPS1");
            Path path = directory.resolve("save/slot1/save.json");
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var rooms = json.getAsJsonObject("worldData").getAsJsonArray("rooms");
            int boxes = 0, pots = 0;
            for (var element : rooms) {
                var room = element.getAsJsonObject();
                for (var item : room.getAsJsonArray("furniture")) {
                    var prop = item.getAsJsonObject();
                    if (prop.get("type").getAsString().equals("ABANDONED_BOX")) {
                        prop.addProperty("type", "BED"); boxes++;
                    } else if (prop.get("type").getAsString().equals("POT")) pots++;
                }
            }
            assertTrue(boxes > 0 && pots > 0);
            rooms.get(0).getAsJsonObject().addProperty("theme", "LIBRARY");
            rooms.get(1).getAsJsonObject().addProperty("theme", "BARRACKS");
            rooms.get(0).getAsJsonObject().getAsJsonArray("decorations").add(JsonParser.parseString(
                "{\"x\":1,\"y\":1,\"mark\":\"PAPER\",\"variant\":0,\"part\":-1}"));
            String legacy = new Gson().toJson(json);
            Files.writeString(path, legacy);
            var expected = RoomLayoutMigration.toRgs(new SaveManager().load(1).worldData());
            Engine loaded = new Engine(); input(loaded, "L1");
            assertEquals(legacy, Files.readString(path), "loading must not overwrite old saves");
            assertEquals(expected.rooms(), loaded.roomDesigns());
            assertTrue(Arrays.deepEquals(expected.world(), TileConverter.toTileType(loaded.world())));
            assertEquals(pots, loaded.roomDesigns().stream().mapToLong(r -> r.furniture().size()).sum());
            Engine second = new Engine(); input(second, "L1");
            assertEquals(loaded.roomDesigns(), second.roomDesigns());
            assertTrue(Arrays.deepEquals(TileConverter.toTileType(loaded.world()), TileConverter.toTileType(second.world())));
            input(loaded, "S2");
            var saved = JsonParser.parseString(Files.readString(directory.resolve("save/slot2/save.json"))).getAsJsonObject();
            for (var entry : json.entrySet()) if (!Set.of("worldData", "explored").contains(entry.getKey()))
                assertEquals(entry.getValue(), saved.get(entry.getKey()), "gameplay field " + entry.getKey());
            // Normal post-load visibility may reveal newly cleared tiles; previously explored cells stay explored.
            var before = json.getAsJsonArray("explored"); var after = saved.getAsJsonArray("explored");
            for (int px = 0; px < before.size(); px++) for (int y = 0; y < before.get(px).getAsJsonArray().size(); y++)
                if (before.get(px).getAsJsonArray().get(y).getAsBoolean())
                    assertTrue(after.get(px).getAsJsonArray().get(y).getAsBoolean());
            Engine restored = new Engine(); input(restored, "L2");
            assertEquals(loaded.roomDesigns(), restored.roomDesigns());
            assertTrue(Arrays.deepEquals(expected.world(), TileConverter.toTileType(restored.world())));
        } finally { System.setProperty("user.dir", previous); }
    }

    private static TileType[][] floor(int width, int height) {
        TileType[][] world = new TileType[width][height];
        for (var column : world) Arrays.fill(column, TileType.FLOOR);
        return world;
    }
    private static void input(Engine e, String keys) { keys.chars().forEach(c -> e.handleKey((char) c)); }
}
