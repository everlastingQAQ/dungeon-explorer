package byow.game.worldGenerator;

import byow.game.random.GameRandom;
import byow.game.tile.TETile;
import byow.game.tile.TileRules;
import byow.game.tile.Tileset;
import java.util.*;
import static byow.game.worldGenerator.FurnitureType.*;

/** Places complete scenery groups against walls, with a protected central aisle. */
final class FurnitureStyler {
    private FurnitureStyler() { }

    static RoomDesign style(TETile[][] world, Room r, RoomTheme theme, int variant, GameRandom random) {
        List<Furniture> placed = new ArrayList<>();
        int budget = Math.min(10, r.width() * r.height() / 10);
        boolean horizontal = r.width() >= r.height();
        int length = Math.max(r.width(), r.height());
        boolean mixed = theme == RoomTheme.MIXED_STORAGE;
        boolean shattered = theme == RoomTheme.POTTERY && variant == 2;
        int groups = mixed || variant == 1 ? 2 : 1;
        for (int group = 0; group < groups; group++) {
            List<Piece> recipe = recipe(theme, variant, horizontal, group);
            int side = mixed ? group % 2 : variant == 2 ? 0 : group % 2;
            int preferred = group == 0 || mixed && variant == 0 ? 0 : length - 1;
            int groupBudget = mixed && group == 0 && budget >= 3 ? budget - 1 : budget;
            List<Integer> anchors = new ArrayList<>();
            for (int u = 0; u < length; u++) anchors.add(u);
            random.shuffle(anchors);
            anchors.sort(Comparator.comparingInt(u -> Math.abs(u - preferred)));
            // Shrink only by whole pieces before retrying a group; never split a two-cell prop.
            boolean accepted = false;
            // Prefer a smaller group on the intended wall over moving both groups to one side.
            for (int wallAttempt = 0; wallAttempt < 2 && !accepted; wallAttempt++) {
                int actualSide = wallAttempt == 0 ? side : 1 - side;
                for (int count = recipe.size(); count > 0 && !accepted; count--) {
                    int cells = recipe.subList(0, count).stream().mapToInt(p -> p.type().cells()).sum();
                    if (cells > groupBudget) continue;
                    for (int anchor : anchors) {
                        List<Furniture> attempt = new ArrayList<>();
                        for (Piece p : recipe.subList(0, count)) {
                            int u = anchor + p.u();
                            int depth = horizontal ? 1 : p.type().cells();
                            int v = actualSide == 0 ? p.v() : Math.min(r.width(), r.height()) - depth - p.v();
                            attempt.add(new Furniture(r.x() + (horizontal ? u : v), r.y() + (horizontal ? v : u),
                                p.type(), Furniture.Orientation.HORIZONTAL, 0, group));
                        }
                        if (!safe(world, r, attempt, placed)) continue;
                        for (Furniture prop : attempt) stamp(world, prop, prop.type().tile());
                        if (RoomStyler.connected(world, r)) {
                            placed.addAll(attempt); budget -= cells; accepted = true; break;
                        }
                        for (Furniture prop : attempt) stamp(world, prop, Tileset.FLOOR);
                    }
                }
            }
        }
        List<int[]> candidates = new ArrayList<>();
        int corner = shattered ? random.nextInt(4) : 0;
        int cornerX = (corner & 1) == 0 ? r.x() : r.x() + r.width() - 1;
        int cornerY = (corner & 2) == 0 ? r.y() : r.y() + r.height() - 1;
        for (int x = r.x(); x < r.x() + r.width(); x++) for (int y = r.y(); y < r.y() + r.height(); y++) {
            int edge = Math.min(Math.min(x - r.x(), r.x() + r.width() - 1 - x),
                Math.min(y - r.y(), r.y() + r.height() - 1 - y));
            int cornerDistance = Math.abs(x - cornerX) + Math.abs(y - cornerY);
            if (edge <= 1 && world[x][y] == Tileset.FLOOR && (!shattered || cornerDistance <= 4
                || cornerDistance >= r.width() + r.height() - 6)) candidates.add(new int[] {x, y});
        }
        random.shuffle(candidates);
        int occupied = placed.stream().mapToInt(p -> p.type().cells()).sum();
        int count = Math.min(candidates.size(), Math.min(r.width() * r.height() / (shattered ? 7 : 12),
            r.width() * r.height() / 4 - occupied));
        RoomDesign.Mark mark = switch (theme) {
            case STORAGE -> RoomDesign.Mark.WEAR;
            case POTTERY, MIXED_STORAGE -> RoomDesign.Mark.SHARDS;
            default -> throw new IllegalArgumentException("Not a furnished theme: " + theme);
        };
        List<RoomDesign.Decoration> decorations = new ArrayList<>();
        for (int i = 0; i < count; i++) decorations.add(new RoomDesign.Decoration(
            candidates.get(i)[0], candidates.get(i)[1], mark, random.nextInt(3)));
        return new RoomDesign(r, theme, variant, decorations, placed);
    }

    private static List<Piece> recipe(RoomTheme theme, int variant, boolean horizontal, int group) {
        return switch (theme) {
            case MIXED_STORAGE -> group == 0
                ? List.of(new Piece(ABANDONED_BOX, 0, 0), new Piece(ABANDONED_BOX, horizontal ? 2 : 1, 0))
                : variant == 2
                    ? List.of(new Piece(POT, 0, 0), new Piece(POT, 1, 0), new Piece(POT, 0, 1))
                    : List.of(new Piece(POT, 0, 0), new Piece(POT, 1, 0));
            // Layout coordinates follow the room axis; the original box art never rotates.
            case STORAGE -> variant == 2
                ? List.of(new Piece(ABANDONED_BOX, 0, 0),
                    new Piece(ABANDONED_BOX, horizontal ? 2 : 1, 0),
                    new Piece(ABANDONED_BOX, horizontal ? 4 : 2, 0))
                : List.of(new Piece(ABANDONED_BOX, 0, 0),
                    new Piece(ABANDONED_BOX, horizontal ? 2 : 1, 0));
            case POTTERY -> variant == 2 ? List.of(new Piece(POT, 0, 0)) : variant == 0
                ? List.of(new Piece(POT, 0, 0), new Piece(POT, 1, 0), new Piece(POT, 0, 1))
                : List.of(new Piece(POT, 0, 0), new Piece(POT, 1, 0), new Piece(POT, 2, 0));
            default -> throw new IllegalArgumentException("Not a furnished theme: " + theme);
        };
    }

    private static boolean safe(TETile[][] world, Room r, List<Furniture> group, List<Furniture> previous) {
        Set<Long> cells = new HashSet<>();
        for (Furniture p : group) for (int x = p.x(); x < p.x() + p.width(); x++)
            for (int y = p.y(); y < p.y() + p.height(); y++) {
                if (x < r.x() || x >= r.x() + r.width() || y < r.y() || y >= r.y() + r.height()
                    || world[x][y] != Tileset.FLOOR || protectedCell(world, r, x, y)
                    || !cells.add(((long) x << 32) ^ y)) return false;
                for (Furniture old : previous)
                    if (x >= old.x() - 1 && x <= old.x() + old.width()
                        && y >= old.y() - 1 && y <= old.y() + old.height()) return false;
            }
        return true;
    }

    private static boolean protectedCell(TETile[][] world, Room r, int x, int y) {
        int shortCoordinate = r.width() >= r.height() ? y - r.y() : x - r.x();
        if (Math.abs(shortCoordinate - Math.min(r.width(), r.height()) / 2) <= 1) return true;
        for (int u = r.x(); u < r.x() + r.width(); u++) {
            if (Math.abs(x - u) > 1) continue;
            if (open(world, u, r.y() - 1) && y < r.y() + 2) return true;
            if (open(world, u, r.y() + r.height()) && y >= r.y() + r.height() - 2) return true;
        }
        for (int v = r.y(); v < r.y() + r.height(); v++) {
            if (Math.abs(y - v) > 1) continue;
            if (open(world, r.x() - 1, v) && x < r.x() + 2) return true;
            if (open(world, r.x() + r.width(), v) && x >= r.x() + r.width() - 2) return true;
        }
        return false;
    }

    private static boolean open(TETile[][] world, int x, int y) {
        return x >= 0 && x < world.length && y >= 0 && y < world[x].length && TileRules.isWalkable(world[x][y]);
    }
    private static void stamp(TETile[][] world, Furniture prop, TETile tile) {
        for (int x = prop.x(); x < prop.x() + prop.width(); x++)
            for (int y = prop.y(); y < prop.y() + prop.height(); y++) world[x][y] = tile;
    }
    private record Piece(FurnitureType type, int u, int v) { }
}
