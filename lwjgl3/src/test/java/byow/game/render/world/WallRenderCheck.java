package byow.game.render.world;

import byow.game.tile.TETile;
import byow.game.tile.Tileset;
import byow.game.worldGenerator.WorldGenerator;
import byow.game.worldGenerator.RoomDesign;
import byow.game.render.FontManager;
import java.util.List;
import java.util.Set;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;

/** Reproducible visual regression check using the game's actual TileRenderer. */
public final class WallRenderCheck extends ApplicationAdapter {
    private static Throwable failure;

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Wall rendering verification");
        config.setWindowedMode(320, 240);
        config.setInitialVisible(false);
        config.disableAudio(true);
        new Lwjgl3Application(new WallRenderCheck(), config);
        if (failure != null) {
            throw new AssertionError("Wall rendering verification failed", failure);
        }
    }

    @Override
    public void create() {
        try {
            TETile[][] masks = filled(80, 80);
            for (int mask = 0; mask < 256; mask++) {
                int cx = (mask % 16) * 5 + 2;
                int cy = (mask / 16) * 5 + 2;
                masks[cx][cy] = Tileset.WALL;
                int bit = 0;
                for (int dy = 1; dy >= -1; dy--) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        if ((mask & (1 << bit)) != 0) masks[cx + dx][cy + dy] = Tileset.WALL;
                        bit++;
                    }
                }
                if (WallAppearance.mask(masks, cx, cy) != mask) {
                    throw new AssertionError("Neighbour orientation differs for " + mask);
                }
            }
            render(masks, "wall-masks.png", 16);
            String[] fixture = {
                "                              ",
                "  #########    #   #   #####   ",
                "  #.......#    #   #   #...#   ",
                "  #.......#    #####   #...#   ",
                "  #.......#      #     #####   ",
                "  ####/####      #             ",
                "                 #    #       ",
                "  ########       #    #       ",
                "  #......#     #####  ####    ",
                "  #.####.#       #       #    ",
                "  #.#  #.#       #       #    ",
                "  #.####.#                    ",
                "  #......#     ##  ##   #     ",
                "  ########     ##  ##         ",
                "                              "
            };
            int fixtureWidth = java.util.Arrays.stream(fixture).mapToInt(String::length).max().orElseThrow();
            TETile[][] cases = filled(fixtureWidth, fixture.length);
            for (int row = 0; row < fixture.length; row++) {
                for (int x = 0; x < fixture[row].length(); x++) {
                    cases[x][fixture.length - row - 1] = switch (fixture[row].charAt(x)) {
                        case '#' -> Tileset.WALL;
                        case '/' -> Tileset.OPEN_DOOR;
                        default -> Tileset.FLOOR;
                    };
                }
            }
            render(cases, "wall-fixtures.png", 32);
            for (long seed : new long[] {0, 1, 42, 20260921, -7}) {
                TETile[][] world = WorldGenerator.generate(seed);
                render(world, "wall-seed-" + seed + ".png", 16);
                if (seed == 42) {
                    TETile[][] detail = new TETile[29][21];
                    for (int x = 0; x < 29; x++) {
                        System.arraycopy(world[x + 25], 20, detail[x], 0, 21);
                    }
                    render(detail, "wall-detail.png", 32);
                }
            }
            System.out.println("PASS: all 256 neighbour masks and five generated maps; "
                + "GPU wall pixels match the full-map reference, including seams and corners.");
        } catch (Throwable exception) {
            failure = exception;
            exception.printStackTrace();
        } finally {
            Gdx.app.exit();
        }
    }

    private static TETile[][] filled(int width, int height) {
        TETile[][] result = new TETile[width][height];
        for (TETile[] column : result) java.util.Arrays.fill(column, Tileset.FLOOR);
        return result;
    }

    private static void render(TETile[][] world, String filename, int size) {
        render(world, filename, size, List.of(), false);
    }

    static void render(TETile[][] world, String filename, int size, List<RoomDesign> rooms, boolean labels) {
        int width = world.length * size;
        int height = world[0].length * size;
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        SpriteBatch batch = new SpriteBatch();
        ShapeRenderer shapes = new ShapeRenderer();
        BitmapFont font = new BitmapFont();
        TileRenderer renderer = new TileRenderer(batch, font, shapes, size);
        RoomDecorationRenderer roomRenderer = new RoomDecorationRenderer();
        FontManager fonts = labels ? new FontManager() : null;
        Pixmap output = null;
        try {
            buffer.begin();
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            Matrix4 projection = new Matrix4().setToOrtho2D(0, 0, width, height);
            shapes.setProjectionMatrix(projection);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            for (int x = 0; x < world.length; x++) {
                for (int y = 0; y < world[0].length; y++) renderer.drawBackground(world[x][y], x, y);
            }
            shapes.end();
            batch.setProjectionMatrix(projection);
            batch.setColor(Color.WHITE);
            batch.begin();
            for (int x = 0; x < world.length; x++) {
                for (int y = 0; y < world[0].length; y++) renderer.drawTerrain(world, x, y);
            }
            roomRenderer.draw(batch, world, rooms, size, Set.of(), null);
            if (fonts != null) {
                for (RoomDesign room : rooms) {
                    fonts.body().draw(batch, room.theme().displayName() + " · " + (room.variant() + 1),
                        room.bounds().x() * size, (room.bounds().y() + room.bounds().height() + 2.7f) * size);
                }
            }
            batch.end();
            output = Pixmap.createFromFrameBuffer(0, 0, width, height);
            buffer.end();
            // Compare against a full-map geometric reference, independent of
            // the production 8-bit lookup, sprite coordinates and GPU drawing.
            for (int x = 0; x < world.length; x++) {
                for (int y = 0; y < world[0].length; y++) {
                    if (!WallAppearance.isWall(world[x][y])) continue;
                    for (int py = 0; py < 16; py++) {
                        for (int px = 0; px < 16; px++) {
                            int expected = reference(world, x * 16 + px, y * 16 + 15 - py);
                            int actual = output.getPixel(x * size + px * size / 16,
                                y * size + (15 - py) * size / 16);
                            if (expected != actual) {
                                throw new AssertionError(filename + " pixel mismatch at " + x + "," + y
                                    + " pixel " + px + "," + py);
                            }
                        }
                    }
                }
            }
            PixmapIO.PNG writer = new PixmapIO.PNG();
            try {
                writer.setFlipY(true);
                writer.write(Gdx.files.local("../build/wall-review/" + filename), output);
            } finally {
                writer.dispose();
            }
        } catch (java.io.IOException exception) {
            throw new RuntimeException(exception);
        } finally {
            if (output != null) output.dispose();
            renderer.dispose();
            roomRenderer.dispose();
            if (fonts != null) fonts.dispose();
            font.dispose();
            batch.dispose();
            shapes.dispose();
            buffer.dispose();
        }
    }

    private static int reference(TETile[][] world, int x, int y) {
        if (!solid(world, x - 1, y) || !solid(world, x + 1, y)
            || !solid(world, x, y - 1) || !solid(world, x, y + 1)) return WallAppearance.OUTLINE;
        if (!cap(world, x, y)) return WallAppearance.FACE;
        return cap(world, x, y + 1) ? WallAppearance.CAP : WallAppearance.LIGHT;
    }

    private static boolean cap(TETile[][] world, int x, int y) {
        for (int yy = y - 5; yy <= y + 2; yy++) {
            for (int xx = x - 2; xx <= x + 2; xx++) {
                if (!solid(world, xx, yy)) return false;
            }
        }
        return true;
    }

    private static boolean solid(TETile[][] world, int x, int y) {
        int tx = Math.floorDiv(x, 16);
        int ty = Math.floorDiv(y, 16);
        return tx >= 0 && tx < world.length && ty >= 0 && ty < world[tx].length
            && WallAppearance.isWall(world[tx][ty]);
    }
}
