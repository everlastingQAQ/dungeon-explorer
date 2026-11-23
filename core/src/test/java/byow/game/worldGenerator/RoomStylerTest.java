package byow.game.worldGenerator;

import byow.game.Engine;
import byow.game.GameState;
import byow.game.pathfinder.Pathfinder;
import byow.game.render.ui.HoverDescriptions;
import byow.game.scene.SceneObjectType;
import byow.game.tile.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomStylerTest {
    @Test void oneHundredSeedsAreDeterministicConnectedAndSafe() {
        Set<RoomTheme> seen = EnumSet.noneOf(RoomTheme.class);
        int corridorFloors = 0, corridorWear = 0;
        for (long seed = 0; seed < 100; seed++) {
            GeneratedWorld a = WorldGenerator.generateDesigned(seed);
            GeneratedWorld b = WorldGenerator.generateDesigned(seed);
            assertEquals(a.rooms(), b.rooms(), "room seed " + seed);
            assertTrue(Arrays.deepEquals(TileConverter.toTileType(a.terrain()),
                TileConverter.toTileType(b.terrain())), "terrain seed " + seed);
            if (a.rooms().size() >= 3)
                assertTrue(a.rooms().stream().map(RoomDesign::theme).distinct().count() >= 3);
            for (RoomDesign room : a.rooms()) {
                seen.add(room.theme());
                assertRoom(a.terrain(), room);
            }
            for (int x = 0; x < a.terrain().length; x++) for (int y = 0; y < a.terrain()[x].length; y++) {
                int px = x, py = y;
                if (a.rooms().stream().anyMatch(r -> r.contains(px, py))) continue;
                if (TileRules.canHostItem(a.terrain()[x][y])) {
                    corridorFloors++;
                    assertTrue(a.terrain()[x][y] == Tileset.FLOOR || a.terrain()[x][y] == Tileset.WORN_FLOOR);
                    if (a.terrain()[x][y] == Tileset.WORN_FLOOR) corridorWear++;
                }
            }
            Engine engine = new Engine();
            ("N" + seed + "S").chars().forEach(c -> engine.handleKey((char) c));
            assertEquals(GameState.PLAYING, engine.state());
            assertEquals(a.rooms(), engine.roomDesigns());
            assertSame(Tileset.WALL, a.terrain()[engine.exit().x()][engine.exit().y()], "exit must not replace pillar");
            int[][] distances = Pathfinder.distancesFrom(engine.world(), engine.player().x(), engine.player().y());
            for (int x = 0; x < distances.length; x++) for (int y = 0; y < distances[x].length; y++)
                if (TileRules.isWalkable(engine.world()[x][y])) assertTrue(distances[x][y] >= 0, "disconnected seed " + seed);
            Set<String> occupied = new HashSet<>();
            // Player may also start on an open connector door; items may not.
            assertTrue(TileRules.isWalkable(engine.world()[engine.player().x()][engine.player().y()]));
            occupied.add(engine.player().x() + ":" + engine.player().y());
            assertEquals(5, engine.items().size());
            engine.items().forEach(i -> {
                assertPosition(engine.world(), occupied, i.x(), i.y());
                assertTrue(engine.roomDesigns().stream().anyMatch(r -> r.contains(i.x(), i.y())),
                    "relic in corridor, seed " + engine.seedString());
            });
            assertEquals(7, engine.sceneObjects().size());
            engine.sceneObjects().forEach(o -> {
                assertPosition(engine.world(), occupied, o.x(), o.y());
                assertTrue(engine.roomDesigns().stream().anyMatch(r -> r.contains(o.x(), o.y())),
                    "scene object in corridor, seed " + engine.seedString());
                if (o.type() == SceneObjectType.PORTAL) assertTrue(engine.sceneObjects().stream()
                    .anyMatch(t -> t.type() == SceneObjectType.PORTAL && t.x() == o.targetX()
                        && t.y() == o.targetY() && t.targetX() == o.x() && t.targetY() == o.y()));
            });
            engine.exit().activate(engine.world());
            assertTrue(Pathfinder.distancesFrom(engine.world(), engine.player().x(), engine.player().y())
                [engine.exit().x()][engine.exit().y()] >= 0);
        }
        assertEquals(EnumSet.of(RoomTheme.STONE, RoomTheme.COLONNADE, RoomTheme.RUIN, RoomTheme.MOSS,
            RoomTheme.EMBLEM, RoomTheme.STORAGE, RoomTheme.POTTERY, RoomTheme.INLAY, RoomTheme.MIXED_STORAGE), seen);
        double wearRatio = corridorWear / (double) corridorFloors;
        assertTrue(wearRatio > .01 && wearRatio < .03, "corridor wear " + wearRatio);
    }

    @Test void allThemesAndVariantsRespectSmallMediumAndLargeBounds() {
        for (int[] size : new int[][] {{5, 5}, {7, 7}, {7, 9}, {9, 7}, {9, 9}, {15, 11}, {11, 15}})
            for (RoomTheme theme : RoomTheme.values()) for (int variant = 0; variant < 3; variant++) {
                Room r = new Room(1, 1, size[0], size[1]);
                TETile[][] world = roomWorld(r);
                RoomDesign design = RoomStyler.styleRoom(world, r, theme, variant, 42);
                assertRoom(world, design);
                assertEquals(!theme.active() || theme == RoomTheme.COLONNADE && size[0] < 7
                    ? RoomTheme.STONE : theme, design.theme());
                long pillars = Arrays.stream(world).flatMap(Arrays::stream).filter(t -> t == Tileset.PILLAR).count();
                long expected = theme != RoomTheme.COLONNADE || Math.max(size[0], size[1]) < 9 ? 0
                    : Math.min(size[0], size[1]) >= 9 ? 4 : 2;
                assertEquals(expected, pillars);
            }
    }

    @Test void pillarBlocksMovementAndVisionAndHoverKeepsEntityPriority() {
        assertFalse(TileRules.isWalkable(Tileset.PILLAR));
        assertFalse(TileRules.isTransparent(Tileset.PILLAR));
        assertFalse(TileRules.canHostItem(Tileset.PILLAR));
        TETile[][] barrier = roomWorld(new Room(1, 1, 9, 9));
        for (int y = 0; y < barrier[0].length; y++) barrier[5][y] = Tileset.PILLAR;
        var player = new byow.game.player.Player(barrier, 4, 5);
        assertFalse(player.moveRight(barrier));
        boolean[][] vision = byow.game.visioncauculate.Visioncauculate.calculate(barrier, player, 20);
        assertTrue(vision[5][5]);
        assertFalse(vision[6][5]);
        Engine engine = new Engine();
        "N42S".chars().forEach(c -> engine.handleKey((char) c));
        assertTrue(HoverDescriptions.forTile(engine, engine.player().x(), engine.player().y()).startsWith("玩家"));
        engine.items().forEach(i -> assertEquals(HoverDescriptions.itemDescription(i), HoverDescriptions.forTile(engine, i.x(), i.y())));
        engine.sceneObjects().forEach(o -> assertEquals(HoverDescriptions.sceneObjectDescription(o.type()), HoverDescriptions.forTile(engine, o.x(), o.y())));
        int checked = 0, pillars = 0;
        for (RoomDesign room : engine.roomDesigns()) {
            for (var d : room.decorations()) {
                String text = HoverDescriptions.forTile(engine, d.x(), d.y());
                if (text.startsWith(room.theme().displayName())) {
                    assertTrue(text.contains("可通行，无交互效果")); checked++;
                }
            }
            Room r = room.bounds();
            for (int x = r.x(); x < r.x() + r.width(); x++) for (int y = r.y(); y < r.y() + r.height(); y++)
                if (engine.world()[x][y] == Tileset.PILLAR) {
                    assertTrue(HoverDescriptions.forTile(engine, x, y).contains("石柱：不可通行，并会阻挡视野")); pillars++;
                }
        }
        assertTrue(checked > 0 && pillars > 0);
    }

    @Test void unsafeFourPillarsFallBackToPairAndDisconnectedPairIsRolledBack() {
        Room r = new Room(1, 1, 9, 9);
        TETile[][] blocked = roomWorld(r);
        blocked[3][7] = Tileset.WALL;
        RoomStyler.styleRoom(blocked, r, RoomTheme.COLONNADE, 0, 42);
        assertEquals(2, Arrays.stream(blocked).flatMap(Arrays::stream).filter(t -> t == Tileset.PILLAR).count());
        assertSame(Tileset.WALL, blocked[3][7]);

        TETile[][] narrow = roomWorld(r);
        for (int x = 1; x <= 9; x++) if (x != 3) narrow[x][3] = Tileset.WALL;
        RoomStyler.styleRoom(narrow, r, RoomTheme.COLONNADE, 0, 42);
        assertEquals(0, Arrays.stream(narrow).flatMap(Arrays::stream).filter(t -> t == Tileset.PILLAR).count());
        assertSame(Tileset.FLOOR, narrow[3][3], "failed layout must restore the only connecting floor");
        assertSame(Tileset.FLOOR, narrow[7][7]);
    }

    private static void assertPosition(TETile[][] w, Set<String> positions, int x, int y) {
        assertTrue(TileRules.canHostItem(w[x][y]));
        assertTrue(positions.add(x + ":" + y));
    }

    private static void assertRoom(TETile[][] world, RoomDesign design) {
        Room r = design.bounds();
        Set<String> ground = new HashSet<>();
        List<int[]> pillars = new ArrayList<>();
        for (int x = r.x(); x < r.x() + r.width(); x++) for (int y = r.y(); y < r.y() + r.height(); y++) {
            if (TileRules.isWalkable(world[x][y])) ground.add(x + ":" + y);
            if (world[x][y] == Tileset.PILLAR) {
                assertTrue(x >= r.x() + 2 && x < r.x() + r.width() - 2);
                assertTrue(y >= r.y() + 2 && y < r.y() + r.height() - 2);
                for (int[] p : pillars) assertTrue(Math.max(Math.abs(x - p[0]), Math.abs(y - p[1])) >= 3);
                pillars.add(new int[] {x, y});
            }
        }
        assertTrue(design.variant() >= 0 && design.variant() < 3);
        assertTrue(design.decorations().size() * 4 <= ground.size(), "plain floor <75% " + design);
        Set<String> marks = new HashSet<>();
        for (var d : design.decorations()) {
            assertTrue(design.contains(d.x(), d.y()));
            assertTrue(TileRules.canHostItem(world[d.x()][d.y()]));
            assertTrue(marks.add(d.x() + ":" + d.y()));
        }
        // Restrict BFS to room bounds, so a route through external corridors cannot hide a split room.
        String first = ground.iterator().next();
        ArrayDeque<String> queue = new ArrayDeque<>(); queue.add(first); ground.remove(first);
        while (!queue.isEmpty()) {
            String[] p = queue.remove().split(":");
            int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]);
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                String next = (x + d[0]) + ":" + (y + d[1]);
                if (ground.remove(next)) queue.add(next);
            }
        }
        assertTrue(ground.isEmpty(), "split room " + design);
        // Every actual entrance retains both of its first two inward cells.
        for (int x = r.x(); x < r.x() + r.width(); x++) {
            if (TileRules.isWalkable(world[x][r.y() - 1])) for (int n = 0; n < 2; n++) assertTrue(TileRules.isWalkable(world[x][r.y() + n]));
            if (TileRules.isWalkable(world[x][r.y() + r.height()])) for (int n = 1; n <= 2; n++) assertTrue(TileRules.isWalkable(world[x][r.y() + r.height() - n]));
        }
        for (int y = r.y(); y < r.y() + r.height(); y++) {
            if (TileRules.isWalkable(world[r.x() - 1][y])) for (int n = 0; n < 2; n++) assertTrue(TileRules.isWalkable(world[r.x() + n][y]));
            if (TileRules.isWalkable(world[r.x() + r.width()][y])) for (int n = 1; n <= 2; n++) assertTrue(TileRules.isWalkable(world[r.x() + r.width() - n][y]));
        }
    }

    private static TETile[][] roomWorld(Room r) {
        TETile[][] world = new TETile[r.width() + 2][r.height() + 2];
        for (int x = 0; x < world.length; x++) for (int y = 0; y < world[x].length; y++)
            world[x][y] = x == 0 || y == 0 || x == world.length - 1 || y == world[x].length - 1 ? Tileset.WALL : Tileset.FLOOR;
        world[world.length / 2][0] = Tileset.OPEN_DOOR;
        world[0][world[0].length / 2] = Tileset.OPEN_DOOR;
        return world;
    }
}
