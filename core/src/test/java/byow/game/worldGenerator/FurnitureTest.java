package byow.game.worldGenerator;

import byow.game.Engine;
import byow.game.pathfinder.Pathfinder;
import byow.game.player.Player;
import byow.game.render.ui.HoverDescriptions;
import byow.game.render.world.WallAppearance;
import byow.game.tile.*;
import byow.game.visioncauculate.Visioncauculate;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FurnitureTest {
    @Test void hundredSeedsPreserveBudgetsFootprintsEntrancesAndThemeCoverage() {
        Set<FurnitureType> seen = EnumSet.noneOf(FurnitureType.class);
        Set<Furniture.Orientation> directions = EnumSet.noneOf(Furniture.Orientation.class);
        for (int seed = 0; seed < 100; seed++) {
            Engine engine = new Engine();
            ("N" + seed + "S").chars().forEach(c -> engine.handleKey((char) c));
            if (engine.roomDesigns().size() >= 5) assertTrue(engine.roomDesigns().stream()
                .map(RoomDesign::theme).filter(RoomTheme::furnished).distinct().count() >= 2, "seed " + seed);
            for (RoomDesign design : engine.roomDesigns()) {
                assertLayout(engine.world(), design);
                for (Furniture p : design.furniture()) {
                    seen.add(p.type()); directions.add(p.orientation());
                    String description = HoverDescriptions.forTile(engine, p.x(), p.y());
                    assertTrue(description.contains(p.type().displayName()));
                    assertTrue(description.contains("不可通行") && description.contains("无交互效果"));
                    assertTrue(description.contains(p.type().blocksVision() ? "，阻挡视野" : "，不阻挡视野"));
                    for (int x = p.x(); x < p.x() + p.width(); x++) for (int y = p.y(); y < p.y() + p.height(); y++)
                        assertEquals(description, HoverDescriptions.forTile(engine, x, y), "both halves must hover identically");
                }
            }
        }
        assertEquals(EnumSet.of(FurnitureType.POT, FurnitureType.ABANDONED_BOX), seen);
        assertEquals(EnumSet.of(Furniture.Orientation.HORIZONTAL), directions);
    }

    @Test void allThemesVariantsSizesAndEntranceSidesAreSafe() {
        for (RoomTheme theme : RoomTheme.values()) if (theme.furnished())
            for (int variant = 0; variant < 3; variant++)
                for (int[] size : new int[][] {{5, 5}, {5, 9}, {7, 9}, {9, 7}, {9, 9}, {15, 11}, {11, 15}})
                    for (int entrances = 1; entrances <= 4; entrances++) {
                        Room r = new Room(1, 1, size[0], size[1]);
                        TETile[][] world = room(r);
                        world[r.x() + r.width() / 2][0] = Tileset.OPEN_DOOR;
                        if (entrances >= 2) world[0][r.y() + r.height() / 2] = Tileset.OPEN_DOOR;
                        if (entrances >= 3) world[r.x() + r.width() / 2][r.height() + 1] = Tileset.OPEN_DOOR;
                        if (entrances >= 4) world[r.width() + 1][r.y() + r.height() / 2] = Tileset.OPEN_DOOR;
                        RoomDesign design = RoomStyler.styleRoom(world, r, theme, variant, 42);
                        assertLayout(world, design);
                        assertTrue(RoomStyler.connected(world, r));
                        if (entrances == 1 && !(theme == RoomTheme.STORAGE && size[0] == 5 && size[1] > 5))
                            assertFalse(design.furniture().isEmpty(), "representative furniture " + design);
                    }
    }

    @Test void smallMapsGuaranteeThreeThemesAndTwoFurnishedThemes() {
        assertEquals(100, Arrays.stream(RoomTheme.values()).mapToInt(RoomTheme::weight).sum());
        assertEquals(9, Arrays.stream(RoomTheme.values()).filter(RoomTheme::active).count());
        for (int seed = 0; seed < 100; seed++) {
            TETile[][] world = new TETile[37][9];
            for (var column : world) Arrays.fill(column, Tileset.WALL);
            List<Room> rooms = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Room r = new Room(1 + i * 7, 1, 5, 5); rooms.add(r);
                for (int x = r.x(); x < r.x() + 5; x++) for (int y = 1; y < 6; y++) world[x][y] = Tileset.FLOOR;
            }
            var designs = RoomStyler.style(world, rooms, seed);
            assertTrue(designs.stream().map(RoomDesign::theme).distinct().count() >= 3);
            assertTrue(designs.stream().map(RoomDesign::theme).filter(RoomTheme::furnished).distinct().count() >= 2);
        }
    }

    @Test void furnitureBlocksMovementButOnlyBookshelvesBlockVision() {
        for (TETile tile : new TETile[] {Tileset.FURNITURE, Tileset.TALL_FURNITURE}) {
            TETile[][] world = room(new Room(1, 1, 9, 9));
            for (int y = 0; y < world[0].length; y++) world[5][y] = tile;
            Player player = new Player(world, 4, 5);
            assertFalse(player.moveRight(world));
            assertFalse(TileRules.canHostItem(tile));
            assertFalse(WallAppearance.isWall(tile));
            assertEquals(-1, Pathfinder.distancesFrom(world, 4, 5)[6][5]);
            boolean[][] visible = Visioncauculate.calculate(world, player, 20);
            assertTrue(visible[5][5]);
            assertEquals(tile == Tileset.FURNITURE, visible[6][5]);
            assertSame(tile, TileConverter.toTETile(TileConverter.toTileType(tile)));
        }
    }

    private static void assertLayout(TETile[][] world, RoomDesign design) {
        Room r = design.bounds();
        Set<String> occupied = new HashSet<>();
        for (Furniture p : design.furniture()) {
            assertTrue(p.type().active());
            assertEquals(Furniture.Orientation.HORIZONTAL, p.orientation());
            assertEquals(p.type().cells(), p.width() * p.height());
            assertTrue(p.variant() >= 0 && p.variant() < 3);
            for (int x = p.x(); x < p.x() + p.width(); x++) for (int y = p.y(); y < p.y() + p.height(); y++) {
                assertTrue(design.contains(x, y));
                assertTrue(occupied.add(x + ":" + y));
                assertSame(p.type().tile(), world[x][y]);
                assertSame(p, design.furnitureAt(x, y));
                int shortCoordinate = r.width() >= r.height() ? y - r.y() : x - r.x();
                assertTrue(Math.abs(shortCoordinate - Math.min(r.width(), r.height()) / 2) > 1);
                for (int u = r.x(); u < r.x() + r.width(); u++) if (Math.abs(x - u) <= 1) {
                    if (TileRules.isWalkable(world[u][r.y() - 1])) assertTrue(y >= r.y() + 2);
                    if (TileRules.isWalkable(world[u][r.y() + r.height()])) assertTrue(y < r.y() + r.height() - 2);
                }
                for (int v = r.y(); v < r.y() + r.height(); v++) if (Math.abs(y - v) <= 1) {
                    if (TileRules.isWalkable(world[r.x() - 1][v])) assertTrue(x >= r.x() + 2);
                    if (TileRules.isWalkable(world[r.x() + r.width()][v])) assertTrue(x < r.x() + r.width() - 2);
                }
                for (Furniture other : design.furniture()) if (other.group() != p.group())
                    assertFalse(x >= other.x() - 1 && x <= other.x() + other.width()
                        && y >= other.y() - 1 && y <= other.y() + other.height(), "group gap");
            }
        }
        assertTrue(occupied.size() <= Math.min(10, r.width() * r.height() / 10));
        if (design.theme().furnished()) {
            assertTrue((occupied.size() + design.decorations().size()) * 4 <= r.width() * r.height());
            assertTrue(RoomStyler.connected(world, r));
        }
        assertTrue(design.theme().active());
        for (var d : design.decorations()) {
            assertFalse(occupied.contains(d.x() + ":" + d.y()));
            assertNotEquals(RoomDesign.Mark.PAPER, d.mark());
            assertNotEquals(RoomDesign.Mark.CLOTH, d.mark());
        }
        for (int x = r.x(); x < r.x() + r.width(); x++) for (int y = r.y(); y < r.y() + r.height(); y++)
            if (world[x][y] == Tileset.FURNITURE || world[x][y] == Tileset.TALL_FURNITURE)
                assertTrue(occupied.contains(x + ":" + y), "no orphan collision cells");
    }

    private static TETile[][] room(Room r) {
        TETile[][] w = new TETile[r.width() + 2][r.height() + 2];
        for (int x = 0; x < w.length; x++) for (int y = 0; y < w[x].length; y++)
            w[x][y] = x == 0 || y == 0 || x == w.length - 1 || y == w[x].length - 1 ? Tileset.WALL : Tileset.FLOOR;
        return w;
    }
}
