package byow.game.scene;

import byow.game.GameExit;
import byow.game.item.*;
import byow.game.itemGenerator.ItemGenerator;
import byow.game.player.Player;
import byow.game.random.GameRandom;
import byow.game.save.SceneObjectData;
import byow.game.tile.*;
import byow.game.worldGenerator.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomSpawnTest {
    private TETile[][] world() {
        TETile[][] world = new TETile[30][12];
        for (var column : world) Arrays.fill(column, Tileset.FLOOR);
        world[2][2] = Tileset.WALL;
        world[3][2] = Tileset.PILLAR;
        world[4][2] = Tileset.FURNITURE;
        world[5][2] = Tileset.OPEN_DOOR;
        world[6][2] = Tileset.CLOSED_DOOR;
        world[3][3] = Tileset.CRACKED_FLOOR;
        return world;
    }

    private List<RoomDesign> rooms() {
        return List.of(
            new RoomDesign(new Room(2, 2, 5, 5), RoomTheme.STONE, 0, List.of()),
            new RoomDesign(new Room(20, 3, 5, 5), RoomTheme.STONE, 0, List.of()));
    }

    @Test void allRelicsAndSceneObjectsStayInRoomsDespiteLargeCorridorFloorArea() {
        var world = world();
        var rooms = rooms();
        var player = new Player(world, 2, 3);
        var exit = new GameExit(6, 2, false);
        for (int seed = 0; seed < 100; seed++) {
            var items = ItemGenerator.generate(world, player, new GameRandom(seed), rooms);
            var again = ItemGenerator.generate(world, player, new GameRandom(seed), rooms);
            assertEquals(5, items.size());
            assertEquals(5, items.stream().map(Item::type).distinct().count());
            Set<String> occupied = new HashSet<>();
            occupied.add(player.x() + ":" + player.y());
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i), same = again.get(i);
                assertEquals(item.x(), same.x()); assertEquals(item.y(), same.y());
                assertEquals(item.type(), same.type());
                assertLegal(world, rooms, occupied, item.x(), item.y());
            }
            var objects = SceneObjectGenerator.generate(world, player, items, exit,
                new GameRandom(seed + 123), rooms);
            var objectsAgain = SceneObjectGenerator.generate(world, player, items, exit,
                new GameRandom(seed + 123), rooms);
            assertEquals(objects.stream().map(SceneObjectData::from).toList(),
                objectsAgain.stream().map(SceneObjectData::from).toList());
            assertEquals(7, objects.size());
            for (var object : objects) {
                assertLegal(world, rooms, occupied, object.x(), object.y());
                assertFalse(exit.contains(object.x(), object.y()));
                if (object.type() == SceneObjectType.PORTAL)
                    assertTrue(rooms.stream().anyMatch(r -> r.contains(object.targetX(), object.targetY())));
            }
        }
    }

    @Test void missingOrInsufficientRoomSpaceNeverFallsBackToCorridors() {
        var world = world();
        var player = new Player(world, 1, 1);
        var exit = new GameExit(6, 2, false);
        for (var rooms : List.of(List.<RoomDesign>of(),
            List.of(new RoomDesign(new Room(8, 2, 2, 2), RoomTheme.STONE, 0, List.of())))) {
            assertThrows(IllegalStateException.class,
                () -> ItemGenerator.generate(world, player, new GameRandom(1), rooms));
            assertThrows(IllegalStateException.class,
                () -> SceneObjectGenerator.generate(world, player, List.of(), exit, new GameRandom(1), rooms));
        }
    }

    private void assertLegal(TETile[][] world, List<RoomDesign> rooms, Set<String> occupied, int x, int y) {
        assertTrue(rooms.stream().anyMatch(r -> r.contains(x, y)), "outside rooms: " + x + "," + y);
        assertTrue(TileRules.canHostItem(world[x][y]));
        assertTrue(occupied.add(x + ":" + y), "overlapping spawn");
    }
}
