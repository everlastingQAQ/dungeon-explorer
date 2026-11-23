package byow.game.worldGenerator;

import byow.game.save.RoomLayoutMigration;
import byow.game.save.WorldData;
import byow.game.tile.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RgsRoomExpansionTest {
    @Test void inlayUsesCompleteSourceSlicesWithinDensityBudgetAndNeverChangesTerrain() {
        for (int[] size : new int[][] {{5, 5}, {7, 9}, {9, 7}, {9, 9}, {15, 11}, {11, 15}})
            for (int variant = 0; variant < 3; variant++) {
                Room r = new Room(1, 1, size[0], size[1]);
                TETile[][] world = room(r);
                var before = TileConverter.toTileType(world);
                RoomDesign design = RoomStyler.styleRoom(world, r, RoomTheme.INLAY, variant, 42);
                assertTrue(Arrays.deepEquals(before, TileConverter.toTileType(world)));
                assertFalse(design.decorations().isEmpty());
                assertTrue(design.decorations().size() * 4 <= r.width() * r.height());
                int x0 = design.decorations().stream().mapToInt(RoomDesign.Decoration::x).min().orElseThrow();
                int y0 = design.decorations().stream().mapToInt(RoomDesign.Decoration::y).min().orElseThrow();
                int x1 = design.decorations().stream().mapToInt(RoomDesign.Decoration::x).max().orElseThrow();
                int y1 = design.decorations().stream().mapToInt(RoomDesign.Decoration::y).max().orElseThrow();
                assertEquals((x1 - x0 + 1) * (y1 - y0 + 1), design.decorations().size());
                assertTrue(x0 > r.x() && y0 > r.y() && x1 < r.x() + r.width() - 1 && y1 < r.y() + r.height() - 1);
                for (var d : design.decorations()) {
                    assertEquals(RoomDesign.Mark.INLAY, d.mark());
                    int col = d.x() == x0 ? 0 : d.x() == x1 ? 2 : 1;
                    int row = d.y() == y0 ? 0 : d.y() == y1 ? 2 : 1;
                    assertEquals(row * 3 + col, d.part());
                }
                if (size[0] == 9 && size[1] == 9)
                    assertEquals(variant == 0 ? 9 : 15, design.decorations().size());
            }
    }

    @Test void inlayDoesNotCutPartialPatchesAroundObstacles() {
        Room r = new Room(1, 1, 9, 9);
        TETile[][] world = room(r);
        world[5][5] = Tileset.PILLAR;
        var design = RoomStyler.styleRoom(world, r, RoomTheme.INLAY, 1, 42);
        assertTrue(design.decorations().isEmpty());
        assertSame(Tileset.PILLAR, world[5][5]);
    }

    @Test void mixedRoomsKeepBothKindsWhenSpacePermitsAndNarrowRoomsFallBackToPots() {
        for (int variant = 0; variant < 3; variant++) {
            Room r = new Room(1, 1, 9, 9);
            TETile[][] world = room(r);
            var design = RoomStyler.styleRoom(world, r, RoomTheme.MIXED_STORAGE, variant, 42);
            assertEquals(Set.of(FurnitureType.ABANDONED_BOX, FurnitureType.POT),
                new HashSet<>(design.furniture().stream().map(Furniture::type).toList()));
            assertTrue(RoomStyler.connected(world, r));
            assertTrue(design.furniture().stream().mapToInt(p -> p.type().cells()).sum() <= 8);
            for (var p : design.furniture()) assertEquals(Furniture.Orientation.HORIZONTAL, p.orientation());
            Room narrow = new Room(1, 1, 5, 9);
            var small = RoomStyler.styleRoom(room(narrow), narrow, RoomTheme.MIXED_STORAGE, variant, 42);
            assertFalse(small.furniture().isEmpty());
            assertTrue(small.furniture().stream().allMatch(p -> p.type() == FurnitureType.POT));
        }
    }

    @Test void shatteredPotteryClustersFragmentsAndResolvedDesignsSurviveSaveCompatibility() {
        Gson gson = new Gson();
        for (RoomTheme theme : new RoomTheme[] {RoomTheme.INLAY, RoomTheme.MIXED_STORAGE, RoomTheme.POTTERY})
            for (int variant = 0; variant < 3; variant++) {
                Room r = new Room(1, 1, 9, 9);
                TETile[][] world = room(r);
                var design = RoomStyler.styleRoom(world, r, theme, variant, 42);
                if (theme == RoomTheme.POTTERY && variant == 2) {
                    assertEquals(1, design.furniture().size());
                    assertTrue(design.decorations().size() >= 10);
                    for (var d : design.decorations()) {
                        assertEquals(RoomDesign.Mark.SHARDS, d.mark());
                        assertTrue(Math.min(d.x() - r.x(), r.x() + r.width() - 1 - d.x())
                            + Math.min(d.y() - r.y(), r.y() + r.height() - 1 - d.y()) <= 4);
                    }
                }
                var saved = new WorldData(TileConverter.toTileType(world), List.of(design));
                var restored = RoomLayoutMigration.toRgs(gson.fromJson(gson.toJson(saved), WorldData.class));
                assertEquals(saved.rooms(), restored.rooms());
                assertTrue(Arrays.deepEquals(saved.world(), restored.world()));
            }
    }

    @Test void previousPotteryThirdVariantKeepsItsSavedRowInsteadOfBeingRestyled() {
        Room r = new Room(1, 1, 9, 9);
        TETile[][] world = room(r);
        List<Furniture> oldPots = new ArrayList<>();
        for (int x = 1; x <= 3; x++) {
            oldPots.add(new Furniture(x, 9, FurnitureType.POT, Furniture.Orientation.HORIZONTAL, 0, 0));
            world[x][9] = Tileset.FURNITURE;
        }
        RoomDesign legacy = new RoomDesign(r, RoomTheme.POTTERY, 2,
            List.of(new RoomDesign.Decoration(5, 8, RoomDesign.Mark.SHARDS, 1)), oldPots);
        var saved = new WorldData(TileConverter.toTileType(world), List.of(legacy));
        var gson = new Gson();
        var restored = RoomLayoutMigration.toRgs(gson.fromJson(gson.toJson(saved), WorldData.class));
        assertEquals(saved.rooms(), restored.rooms());
        assertTrue(Arrays.deepEquals(saved.world(), restored.world()));
        assertEquals(3, restored.rooms().getFirst().furniture().size());
    }

    private static TETile[][] room(Room r) {
        TETile[][] w = new TETile[r.width() + 2][r.height() + 2];
        for (int x = 0; x < w.length; x++) for (int y = 0; y < w[x].length; y++)
            w[x][y] = x == 0 || y == 0 || x == w.length - 1 || y == w[x].length - 1 ? Tileset.WALL : Tileset.FLOOR;
        w[r.x() + r.width() / 2][0] = Tileset.OPEN_DOOR;
        return w;
    }
}
