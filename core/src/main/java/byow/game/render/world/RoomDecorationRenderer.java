package byow.game.render.world;

import byow.game.tile.TETile;
import byow.game.tile.TileRules;
import byow.game.tile.Tileset;
import byow.game.worldGenerator.RoomDesign;
import byow.game.worldGenerator.RoomTheme;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import java.util.List;
import java.util.Set;

/** Low-contrast room floor art, drawn below gameplay objects and the existing fog. */
public final class RoomDecorationRenderer implements Disposable {
    private final Texture atlas;
    private final RoomFurnitureRenderer furnitureRenderer = new RoomFurnitureRenderer();
    private final TextureRegion plain;
    private final TextureRegion[][] marks = new TextureRegion[RoomDesign.Mark.values().length][3];
    private final TextureRegion[][] emblems = new TextureRegion[3][9];

    public RoomDecorationRenderer() {
        int emblemX = RoomDesign.Mark.values().length * 16;
        int atlasWidth = com.badlogic.gdx.math.MathUtils.nextPowerOfTwo(emblemX + 144);
        Pixmap pixels = new Pixmap(atlasWidth, 64, Pixmap.Format.RGBA8888);
        pixels.setBlending(Pixmap.Blending.None);
        pixels.setColor(Color.WHITE);
        pixels.fillRectangle(atlasWidth - 16, 48, 16, 16);
        for (RoomDesign.Mark mark : RoomDesign.Mark.values()) {
            for (int v = 0; v < 3; v++) {
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        pixels.drawPixel(mark.ordinal() * 16 + x, v * 16 + y, ink(mark, v, x, y, 16));
                    }
                }
            }
        }
        for (int v = 0; v < 3; v++) {
            for (int y = 0; y < 48; y++) {
                for (int x = 0; x < 48; x++) {
                    pixels.drawPixel(emblemX + v * 48 + x, y, ink(RoomDesign.Mark.EMBLEM, v, x, y, 48));
                }
            }
        }
        try { atlas = new Texture(pixels); } finally { pixels.dispose(); }
        atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        plain = new TextureRegion(atlas, atlasWidth - 16, 48, 16, 16);
        for (RoomDesign.Mark mark : RoomDesign.Mark.values()) for (int v = 0; v < 3; v++)
            marks[mark.ordinal()][v] = new TextureRegion(atlas, mark.ordinal() * 16, v * 16, 16, 16);
        for (int v = 0; v < 3; v++) for (int part = 0; part < 9; part++)
            emblems[v][part] = new TextureRegion(atlas, emblemX + v * 48 + part % 3 * 16,
                (2 - part / 3) * 16, 16, 16);
    }

    /** Batch is already begun. A null visibility mask delegates visibility to the world's fog pass. */
    public void draw(SpriteBatch batch, TETile[][] world, List<RoomDesign> rooms,
                     float size, Set<Long> occupied, boolean[][] visible) {
        for (RoomDesign design : rooms) {
            var r = design.bounds();
            batch.setColor(baseColor(design.theme()));
            for (int x = r.x(); x < r.x() + r.width(); x++) {
                for (int y = r.y(); y < r.y() + r.height(); y++) {
                    if (ground(world, x, y, visible) || furnitureFloor(world, design, x, y, visible))
                        batch.draw(plain, x * size, y * size, size, size);
                }
            }
            batch.setColor(Color.WHITE);
            for (var decoration : design.decorations()) {
                int x = decoration.x(), y = decoration.y();
                if (!ground(world, x, y, visible) || occupied.contains(key(x, y))) continue;
                TextureRegion region = decoration.mark() == RoomDesign.Mark.EMBLEM && decoration.part() >= 0
                    ? emblems[decoration.variant()][decoration.part()]
                    : marks[decoration.mark().ordinal()][decoration.variant()];
                TextureRegion libraryArt = furnitureRenderer.decoration(decoration);
                if (libraryArt != null) region = libraryArt;
                boolean rotate = r.width() < r.height() && decoration.mark() == RoomDesign.Mark.STRIPE;
                batch.draw(region, x * size, y * size, size / 2, size / 2,
                    size, size, 1, 1, rotate ? 90 : 0);
            }
        }
        furnitureRenderer.draw(batch, world, rooms, size, visible);
        batch.setColor(Color.WHITE);
    }

    public static long key(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }

    public static Color baseColor(RoomTheme theme) {
        return new Color(switch (theme) {
            case STONE -> 0x5a6988ff;
            case COLONNADE -> 0x5c6c88ff;
            case RUIN -> 0x5c647dff;
            case MOSS -> 0x566b7aff;
            case EMBLEM -> 0x626984ff;
            case STORAGE, LIBRARY, BARRACKS, POTTERY, MIXED_STORAGE, INLAY -> 0x5a6988ff;
        });
    }

    private static boolean furnitureFloor(TETile[][] w, RoomDesign r, int x, int y, boolean[][] visible) {
        return x >= 0 && x < w.length && y >= 0 && y < w[x].length
            && (w[x][y].equals(Tileset.FURNITURE) || w[x][y].equals(Tileset.TALL_FURNITURE))
            && r.furnitureAt(x, y) != null && (visible == null || visible[x][y]);
    }

    private static boolean ground(TETile[][] world, int x, int y, boolean[][] visible) {
        return x >= 0 && x < world.length && y >= 0 && y < world[x].length
            && TileRules.canHostItem(world[x][y]) && (visible == null || visible[x][y]);
    }

    private static int ink(RoomDesign.Mark mark, int v, int x, int y, int size) {
        int hash = Math.floorMod(x * 31 + y * 47 + v * 73, 97);
        int cx = size / 2, cy = size / 2;
        int dx = Math.abs(x - cx), dy = Math.abs(y - cy);
        boolean on = switch (mark) {
            case WEAR -> y == 6 + v * 2 && x >= 4 && x <= 7 + v;
            case CRACK -> x == 5 + (y + v * 3) / 4 && y >= 3 && y <= 12
                || y == 8 && x >= 6 && x <= 11;
            case RUBBLE -> x >= 3 && x <= 12 && y >= 3 && y <= 12 && hash < 15;
            case MOSS -> x >= 2 && x <= 13 && y >= 2 && y <= 13
                && (x + v * 3) / 4 + y / 4 <= 4 && hash < 43;
            case STAIN -> dx * dx + dy * dy <= 20 + v * 5 && hash < 55;
            case STRIPE -> switch (v) {
                case 0 -> y == 4 || y == 11;
                case 1 -> y == 7 || y == 8;
                default -> (y == 4 || y == 11) && x % 4 < 2;
            };
            case PAPER, CLOTH, SHARDS, INLAY -> false; // Legacy marks are blank; new marks use the RGS atlas.
            case EMBLEM -> switch (v) {
                case 0 -> dx + dy == size / 3 || dx + dy == size / 3 - 1
                    || (dx == 0 || dy == 0) && dx + dy < size / 5;
                case 1 -> Math.max(dx, dy) == size / 3 || dx + dy == size / 5;
                default -> (dx == dy && dx <= size / 3)
                    || Math.abs(dx * dx + dy * dy - size * size / 10) < size / 2;
            };
        };
        if (!on) return 0;
        return switch (mark) {
            case MOSS -> hash % 2 == 0 ? 0x617c73ff : 0x456367ff;
            case STAIN -> 0x49606cff;
            case STRIPE -> 0x70809aff;
            case EMBLEM -> 0x8491a5ff;
            case RUBBLE -> hash % 2 == 0 ? 0x46526cff : 0x75829bff;
            default -> 0x46546eff;
        };
    }

    @Override public void dispose() { atlas.dispose(); furnitureRenderer.dispose(); }
}
