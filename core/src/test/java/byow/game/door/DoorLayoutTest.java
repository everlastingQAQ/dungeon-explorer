package byow.game.door;

import byow.game.*;
import byow.game.player.Player;
import byow.game.realtime.EngineFixture;
import byow.game.render.ui.HoverDescriptions;
import byow.game.save.*;
import byow.game.tile.*;
import byow.game.worldGenerator.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class DoorLayoutTest {
    @TempDir Path directory;

    public static TETile[][] sample(boolean open) {
        TETile[][] world = new TETile[16][14];
        for (var column : world) Arrays.fill(column, Tileset.FLOOR);
        for (int x = 0; x < world.length; x++) { world[x][0] = Tileset.WALL; world[x][13] = Tileset.WALL; }
        for (int y = 0; y < 14; y++) { world[0][y] = Tileset.WALL; world[15][y] = Tileset.WALL; }
        for (int x = 1; x <= 10; x++) world[x][4] = Tileset.WALL;
        world[4][4] = world[5][4] = open ? Tileset.OPEN_DOOR : Tileset.CLOSED_DOOR;
        return world;
    }

    public static List<RoomDesign> rooms() {
        return List.of(new RoomDesign(new Room(2, 5, 8, 6), RoomTheme.STONE, 0, List.of()));
    }

    @Test void adjacentMatchingDoorsMergeWithoutChangingAnyTerrain() {
        for (boolean open : new boolean[] {false, true}) {
            var world = sample(open); var before = TileConverter.toTileType(world);
            var doors = DoorLayout.collect(world, rooms(), null);
            assertEquals(List.of(new DoorLayout.Door(4, 4, 2, open, false)), doors);
            assertEquals(doors.getFirst(), DoorLayout.at(world, rooms(), null, 5, 4));
            assertTrue(Arrays.deepEquals(before, TileConverter.toTileType(world)));
            assertTrue(DoorLayout.collect(world, List.of(rooms().getFirst(), rooms().getFirst()), null).size() == 1);
        }
    }

    @Test void mismatchedStatesCornersVerticalRunsAndDifferentRoomsStaySeparate() {
        var world = sample(true);
        world[5][4] = Tileset.CLOSED_DOOR;
        assertEquals(2, DoorLayout.collect(world, rooms(), null).size());
        world = sample(true); world[4][5] = Tileset.OPEN_DOOR;
        assertTrue(DoorLayout.collect(world, rooms(), null).stream().allMatch(d -> d.width() == 1));
        world = sample(true); world[3][4] = Tileset.FLOOR;
        assertTrue(DoorLayout.collect(world, rooms(), null).stream().allMatch(d -> d.width() == 1));
        world = sample(true); world[6][4] = Tileset.OPEN_DOOR;
        assertEquals(3, DoorLayout.collect(world, rooms(), null).size());
        var separate = List.of(new RoomDesign(new Room(2, 5, 3, 6), RoomTheme.STONE, 0, List.of()),
            new RoomDesign(new Room(5, 5, 5, 6), RoomTheme.STONE, 0, List.of()));
        assertEquals(2, DoorLayout.collect(sample(true), separate, null).size());
        assertEquals(2, DoorLayout.collect(sample(true), List.of(), null).size());
    }

    @Test void normalDoorNeverAbsorbsAnAdjacentGameplayExit() {
        var world = sample(true);
        var exit = new GameExit(5, 4, true);
        var doors = DoorLayout.collect(world, rooms(), exit);
        assertEquals(2, doors.size());
        assertTrue(doors.stream().allMatch(d -> d.width() == 1));
        assertEquals(1, doors.stream().filter(DoorLayout.Door::exit).count());
    }

    @Test void bothCellsShareCollisionVisionStateAndHoverText() {
        var world = sample(false);
        var door = DoorLayout.collect(world, rooms(), null).getFirst();
        var engine = EngineFixture.create(world, new Player(world, 4, 3), List.of());
        EngineFixture.set(engine, "roomDesigns", rooms());
        assertFalse(engine.player().moveUp(world));
        assertFalse(TileRules.isTransparent(world[4][4]));
        assertFalse(TileRules.isTransparent(world[5][4]));
        assertEquals(HoverDescriptions.forTile(engine, 4, 4), HoverDescriptions.forTile(engine, 5, 4));
        assertEquals(HoverDescriptions.forTile(engine, 4, 4), HoverDescriptions.forTile(engine, 5, 5));
        assertTrue(HoverDescriptions.forTile(engine, 5, 4).startsWith("双格木门"));
        door.sync(world, true);
        assertTrue(engine.player().moveUp(world));
        assertTrue(TileRules.isWalkable(world[5][4]));
        assertTrue(TileRules.isTransparent(world[5][4]));
    }

    @Test void wideExitOpensBothCellsAndEitherCellWins() {
        for (int x : new int[] {4, 5}) {
            var world = sample(false);
            var exit = new GameExit(4, 4, false, 2);
            exit.syncTile(world);
            var engine = EngineFixture.create(world, new Player(world, x, 3), List.of());
            EngineFixture.set(engine, "exit", exit);
            assertTrue(exit.contains(x, 4));
            assertEquals("双格出口：收集 3 件遗物后开启。", HoverDescriptions.forTile(engine, x, 4));
            assertFalse(engine.player().moveUp(world));
            exit.activate(world);
            assertSame(Tileset.OPEN_DOOR, world[4][4]); assertSame(Tileset.OPEN_DOOR, world[5][4]);
            EngineFixture.tap(engine, 'W');
            assertEquals(GameState.WIN, engine.state());
        }
    }

    @Test void saveLoadRetainsDerivedGroupsAndWideExitWhileLegacyExitDefaultsToOneCell() throws Exception {
        var gson = new Gson();
        assertEquals(1, gson.fromJson("{\"x\":4,\"y\":4,\"active\":false}", ExitData.class).toExit().width());
        var data = ExitData.from(new GameExit(4, 4, true, 2));
        assertEquals(data, gson.fromJson(gson.toJson(data), ExitData.class));
        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", directory.toString());
            var world = sample(true);
            var engine = EngineFixture.create(world, new Player(world, 4, 3), List.of());
            EngineFixture.set(engine, "roomDesigns", rooms());
            EngineFixture.set(engine, "exit", new GameExit(9, 10, false, 2));
            EngineFixture.unlockExit(engine);
            EngineFixture.keys(engine, "PS1");
            Path path = directory.resolve("save/slot1/save.json");
            String saved = Files.readString(path);
            var restored = new Engine(); EngineFixture.keys(restored, "L1");
            assertEquals(GameState.PAUSE, restored.state());
            assertEquals(2, restored.exit().width());
            assertEquals(DoorLayout.collect(world, rooms(), engine.exit()),
                DoorLayout.collect(restored.world(), restored.roomDesigns(), restored.exit()));
            assertEquals(saved, Files.readString(path));
        } finally { System.setProperty("user.dir", previous); }
    }

    @Test void hundredSeedsHaveDeterministicGroupsAndNoDuplicateDoorFootprints() {
        int paired = 0;
        for (int seed = 0; seed < 100; seed++) {
            var generated = WorldGenerator.generateDesigned(seed);
            var world = generated.terrain(); var before = TileConverter.toTileType(world);
            var doors = DoorLayout.collect(world, generated.rooms(), null);
            assertEquals(doors, DoorLayout.collect(world, generated.rooms(), null));
            Set<String> cells = new HashSet<>();
            for (var door : doors) {
                if (door.width() == 2) paired++;
                for (int dx = 0; dx < door.width(); dx++) {
                    assertTrue(cells.add((door.x() + dx) + ":" + door.y()));
                    assertTrue(DoorLayout.isDoor(world[door.x() + dx][door.y()]));
                }
            }
            int total = 0;
            for (var column : world) for (var tile : column) if (DoorLayout.isDoor(tile)) total++;
            assertEquals(total, cells.size());
            assertTrue(Arrays.deepEquals(before, TileConverter.toTileType(world)));
        }
        assertTrue(paired > 0, "Some actual generated adjacent doors should form wide doors");
        System.out.println("Wide door pairs across 100 seeds: " + paired);
    }
}
