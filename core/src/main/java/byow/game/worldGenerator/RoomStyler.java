package byow.game.worldGenerator;

import byow.game.random.GameRandom;
import byow.game.tile.TETile;
import byow.game.tile.TileRules;
import byow.game.tile.Tileset;
import java.util.*;

/** Applies room-local design only after the walkable map and outer walls exist. */
public final class RoomStyler {
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private RoomStyler() { }

    public static List<RoomDesign> style(TETile[][] world, List<Room> rooms, long seed) {
        List<RoomTheme> themes = new ArrayList<>();
        for (Room room : rooms) {
            GameRandom random = randomFor(seed, room);
            long furnished = themes.stream().filter(RoomTheme::furnished).distinct().count();
            boolean needsNew = rooms.size() >= 5 && rooms.size() - themes.size() <= 2 - furnished;
            List<RoomTheme> choices = Arrays.stream(RoomTheme.values())
                .filter(RoomTheme::active)
                .filter(t -> !needsNew || t.furnished() && !themes.contains(t)).toList();
            int roll = random.nextInt(choices.stream().mapToInt(RoomTheme::weight).sum());
            RoomTheme selected = RoomTheme.STONE;
            for (RoomTheme theme : choices) {
                roll -= theme.weight();
                if (roll < 0) { selected = theme; break; }
            }
            themes.add(eligible(room, selected));
        }
        // Replace only duplicate themes; keep the guarantee possible even if all rooms are small.
        if (rooms.size() >= 3) {
            for (RoomTheme candidate : new RoomTheme[] {RoomTheme.STONE, RoomTheme.RUIN,
                RoomTheme.MOSS, RoomTheme.EMBLEM}) {
                if (new HashSet<>(themes).size() >= 3) break;
                if (themes.contains(candidate)) continue;
                for (int i = 0; i < themes.size(); i++) {
                    if (Collections.frequency(themes, themes.get(i)) > 1) {
                        themes.set(i, candidate);
                        break;
                    }
                }
            }
        }
        boolean[][] inRoom = new boolean[world.length][world[0].length];
        List<RoomDesign> result = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            Room room = rooms.get(i);
            GameRandom random = randomFor(seed ^ 0x524f4f4dL, room);
            result.add(styleRoom(world, room, themes.get(i), random.nextInt(3), random.state()));
            for (int x = room.x(); x < room.x() + room.width(); x++) {
                for (int y = room.y(); y < room.y() + room.height(); y++) inRoom[x][y] = true;
            }
        }
        GameRandom corridorRandom = new GameRandom(seed ^ 0x434f525249444f52L);
        for (int x = 0; x < world.length; x++) {
            for (int y = 0; y < world[x].length; y++) {
                if (!inRoom[x][y] && world[x][y] == Tileset.FLOOR && corridorRandom.nextInt(100) < 2) {
                    world[x][y] = Tileset.WORN_FLOOR;
                }
            }
        }
        return List.copyOf(result);
    }

    public static RoomDesign styleRoom(TETile[][] world, Room room, RoomTheme requested,
                                       int variant, long seed) {
        RoomTheme theme = eligible(room, requested);
        GameRandom random = randomFor(seed, room);
        if (theme.furnished()) return FurnitureStyler.style(world, room, theme, variant, random);
        if (theme == RoomTheme.INLAY) return InlayStyler.style(world, room, variant);
        if (theme == RoomTheme.COLONNADE) placePillars(world, room, variant);
        List<RoomDesign.Decoration> marks = new ArrayList<>();
        List<Point> candidates = new ArrayList<>();
        int floorCount = 0;
        for (int x = 0; x < room.width(); x++) {
            for (int y = 0; y < room.height(); y++) {
                if (!TileRules.canHostItem(world[x + room.x()][y + room.y()])) continue;
                floorCount++;
                int edge = Math.min(Math.min(x, room.width() - 1 - x), Math.min(y, room.height() - 1 - y));
                int cx = room.width() / 2, cy = room.height() / 2;
                int cornerX = variant == 1 ? room.width() - 1 : 0;
                int cornerY = variant == 2 ? room.height() - 1 : 0;
                boolean select = switch (theme) {
                    case STONE -> edge == 0 && (variant == 0 || (variant == 1 ? x % 2 == 0 : y % 2 == 0));
                    case RUIN -> Math.abs(x - cornerX) + Math.abs(y - cornerY) <= 4
                        || (variant == 2 && x + y >= room.width() + room.height() - 5);
                    case MOSS -> edge <= 1 && switch (variant) {
                        case 0 -> x <= 1 || y >= room.height() - 2;
                        case 1 -> y <= 1 || x >= room.width() - 2;
                        default -> Math.min(x, room.width() - 1 - x) + Math.min(y, room.height() - 1 - y) <= 2;
                    };
                    case COLONNADE -> room.width() >= room.height() ? y == cy : x == cx;
                    case EMBLEM -> x == cx && y == cy;
                    default -> false; // Furnished themes are handled above.
                };
                if (select) candidates.add(new Point(x + room.x(), y + room.y()));
            }
        }
        if (theme == RoomTheme.EMBLEM) {
            int radius = floorCount / 4 >= 9 ? 1 : 0;
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    marks.add(new RoomDesign.Decoration(room.x() + room.width() / 2 + dx,
                        room.y() + room.height() / 2 + dy, RoomDesign.Mark.EMBLEM, variant,
                        radius == 0 ? -1 : (dy + 1) * 3 + dx + 1));
                }
            }
            return new RoomDesign(room, theme, variant, marks);
        }
        random.shuffle(candidates);
        int target = switch (theme) {
            case STONE -> Math.max(1, floorCount / 20);
            case RUIN -> Math.max(2, floorCount / 6);
            case MOSS -> Math.max(2, floorCount / 7);
            case COLONNADE, EMBLEM -> candidates.size();
            default -> 0;
        };
        int count = Math.min(Math.min(target, floorCount / 4), candidates.size());
        for (int i = 0; i < count; i++) {
            Point p = candidates.get(i);
            RoomDesign.Mark mark = switch (theme) {
                case STONE -> RoomDesign.Mark.WEAR;
                case RUIN -> random.nextInt(3) == 0 ? RoomDesign.Mark.CRACK : RoomDesign.Mark.RUBBLE;
                case MOSS -> random.nextInt(4) == 0 ? RoomDesign.Mark.STAIN : RoomDesign.Mark.MOSS;
                case COLONNADE -> RoomDesign.Mark.STRIPE;
                case EMBLEM -> RoomDesign.Mark.EMBLEM;
                default -> throw new IllegalStateException("Furnished theme handled separately");
            };
            marks.add(new RoomDesign.Decoration(p.x(), p.y(), mark,
                mark == RoomDesign.Mark.STRIPE || mark == RoomDesign.Mark.EMBLEM ? variant : random.nextInt(3)));
        }
        return new RoomDesign(room, theme, variant, marks);
    }

    private static void placePillars(TETile[][] world, Room r, int variant) {
        int cx = r.x() + r.width() / 2, cy = r.y() + r.height() / 2;
        List<Point> proposed = new ArrayList<>();
        if (r.width() >= 9 && r.height() >= 9) {
            int dx = variant == 1 ? (r.width() - 5) / 2 : 2;
            int dy = variant == 2 ? (r.height() - 5) / 2 : 2;
            for (int sx : new int[] {-1, 1}) for (int sy : new int[] {-1, 1})
                proposed.add(new Point(cx + sx * dx, cy + sy * dy));
        } else if (Math.max(r.width(), r.height()) >= 9) {
            boolean horizontal = r.width() >= r.height();
            int offset = variant == 1 ? (Math.max(r.width(), r.height()) - 5) / 2 : 2;
            proposed.add(new Point(cx - (horizontal ? offset : 0), cy - (horizontal ? 0 : offset)));
            proposed.add(new Point(cx + (horizontal ? offset : 0), cy + (horizontal ? 0 : offset)));
        }
        // A 7x7 room cannot fit a pair with two clear tiles on every side; leave it open.
        for (int count = proposed.size(); count >= 2; count -= 2) {
            List<Point> attempt = count == 4 ? proposed : proposed.size() == 4
                ? List.of(proposed.get(0), proposed.get(3)) : proposed;
            if (attempt.stream().anyMatch(p -> !safePillar(world, r, p))) continue;
            for (Point p : attempt) world[p.x()][p.y()] = Tileset.PILLAR;
            if (connected(world, r)) return;
            for (Point p : attempt) world[p.x()][p.y()] = Tileset.FLOOR;
        }
    }

    private static boolean safePillar(TETile[][] world, Room r, Point p) {
        if (p.x() < r.x() + 2 || p.x() >= r.x() + r.width() - 2
            || p.y() < r.y() + 2 || p.y() >= r.y() + r.height() - 2
            || world[p.x()][p.y()] != Tileset.FLOOR) return false;
        // Protect each boundary entrance's first two inward cells.
        for (int x = r.x(); x < r.x() + r.width(); x++) {
            if (open(world, x, r.y() - 1) && p.x() == x && p.y() < r.y() + 2) return false;
            if (open(world, x, r.y() + r.height()) && p.x() == x && p.y() >= r.y() + r.height() - 2) return false;
        }
        for (int y = r.y(); y < r.y() + r.height(); y++) {
            if (open(world, r.x() - 1, y) && p.y() == y && p.x() < r.x() + 2) return false;
            if (open(world, r.x() + r.width(), y) && p.y() == y && p.x() >= r.x() + r.width() - 2) return false;
        }
        return true;
    }

    static boolean connected(TETile[][] world, Room r) {
        Set<Point> remaining = new HashSet<>();
        for (int x = r.x(); x < r.x() + r.width(); x++)
            for (int y = r.y(); y < r.y() + r.height(); y++) if (open(world, x, y)) remaining.add(new Point(x, y));
        if (remaining.isEmpty()) return false;
        ArrayDeque<Point> queue = new ArrayDeque<>();
        Point start = remaining.iterator().next(); remaining.remove(start); queue.add(start);
        while (!queue.isEmpty()) {
            Point p = queue.remove();
            for (int[] d : DIRECTIONS) {
                Point next = new Point(p.x() + d[0], p.y() + d[1]);
                if (remaining.remove(next)) queue.add(next);
            }
        }
        return remaining.isEmpty();
    }

    private static boolean open(TETile[][] w, int x, int y) {
        return x >= 0 && x < w.length && y >= 0 && y < w[x].length && TileRules.isWalkable(w[x][y]);
    }

    private static RoomTheme eligible(Room r, RoomTheme theme) {
        if (!theme.active()) return RoomTheme.STONE;
        return theme == RoomTheme.COLONNADE && (r.width() < 7 || r.height() < 7) ? RoomTheme.STONE : theme;
    }

    private static GameRandom randomFor(long seed, Room r) {
        long value = seed ^ ((long) r.x() << 32) ^ r.y() ^ ((long) r.width() << 16) ^ r.height();
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return new GameRandom(value ^ (value >>> 31));
    }

    private record Point(int x, int y) { }
}
