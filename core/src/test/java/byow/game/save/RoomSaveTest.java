package byow.game.save;

import byow.game.Engine;
import byow.game.GameState;
import byow.game.tile.TileConverter;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class RoomSaveTest {
    @TempDir Path directory;

    @Test void engineSaveLoadPreservesFinalLayoutAndOldSavesStayUndecorated() throws Exception {
        String previous = System.getProperty("user.dir");
        try {
            // Isolate all disk writes from the user's real save slots.
            System.setProperty("user.dir", directory.toString());
            Engine original = new Engine();
            input(original, "N42SPS1Q");
            assertEquals(GameState.MENU, original.state());
            var terrain = TileConverter.toTileType(original.world());
            Engine loaded = new Engine();
            input(loaded, "L1");
            assertEquals(GameState.PAUSE, loaded.state());
            assertEquals(original.roomDesigns(), loaded.roomDesigns());
            assertFalse(loaded.roomDesigns().isEmpty());
            assertTrue(loaded.roomDesigns().stream().anyMatch(r -> !r.furniture().isEmpty()));
            assertTrue(Arrays.deepEquals(terrain, TileConverter.toTileType(loaded.world())));
            assertEquals(original.player().x(), loaded.player().x());
            assertEquals(original.player().y(), loaded.player().y());

            // Missing room metadata must clear orphan collision cells, without restyling other terrain.
            Path save = directory.resolve("save/slot1/save.json");
            var json = JsonParser.parseString(Files.readString(save)).getAsJsonObject();
            json.getAsJsonObject("worldData").remove("rooms");
            Files.writeString(save, new Gson().toJson(json));
            for (var column : terrain) for (int y = 0; y < column.length; y++)
                if (column[y] == byow.game.tile.TileType.FURNITURE || column[y] == byow.game.tile.TileType.TALL_FURNITURE)
                    column[y] = byow.game.tile.TileType.FLOOR;
            Engine old = new Engine();
            input(old, "L1");
            assertEquals(GameState.PAUSE, old.state());
            assertTrue(old.roomDesigns().isEmpty());
            assertTrue(Arrays.deepEquals(terrain, TileConverter.toTileType(old.world())));
            input(old, "S2");
            assertTrue(new SaveManager().load(2).worldData().rooms().isEmpty());
        } finally {
            System.setProperty("user.dir", previous);
        }
    }

    @Test void missingAndNullOptionalRoomFieldAreEmpty() {
        Gson gson = new Gson();
        assertTrue(gson.fromJson("{\"world\":[[\"FLOOR\"]]}", WorldData.class).rooms().isEmpty());
        assertTrue(gson.fromJson("{\"world\":[[\"FLOOR\"]],\"rooms\":null}", WorldData.class).rooms().isEmpty());
    }

    @Test void previousRoomSaveWithoutFurnitureLoadsWithoutAddingProps() throws Exception {
        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", directory.toString());
            Engine engine = new Engine(); input(engine, "N42SPS1");
            Path path = directory.resolve("save/slot1/save.json");
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var world = json.getAsJsonObject("worldData");
            for (var room : world.getAsJsonArray("rooms")) room.getAsJsonObject().remove("furniture");
            // An actual pre-furniture save has only legacy terrain, not orphan furniture occupancy.
            for (var column : world.getAsJsonArray("world")) {
                var cells = column.getAsJsonArray();
                for (int y = 0; y < cells.size(); y++) if (cells.get(y).getAsString().endsWith("FURNITURE"))
                    cells.set(y, new com.google.gson.JsonPrimitive("FLOOR"));
            }
            Files.writeString(path, new Gson().toJson(json));
            GameSave expected = new SaveManager().load(1);
            Engine loaded = new Engine(); input(loaded, "L1");
            assertEquals(expected.worldData().rooms(), loaded.roomDesigns());
            assertTrue(loaded.roomDesigns().stream().allMatch(r -> r.furniture().isEmpty()));
            assertTrue(Arrays.deepEquals(expected.worldData().world(), TileConverter.toTileType(loaded.world())));
            input(loaded, "S2");
            assertEquals(expected.worldData().rooms(), new SaveManager().load(2).worldData().rooms());
        } finally { System.setProperty("user.dir", previous); }
    }

    private static void input(Engine engine, String keys) {
        keys.chars().forEach(c -> engine.handleKey((char) c));
    }
}
