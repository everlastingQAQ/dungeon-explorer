package byow.game.render.world;

import byow.game.tile.TETile;
import byow.game.tile.Tileset;
import byow.game.worldGenerator.*;
import byow.game.Engine;
import byow.game.render.FontManager;
import byow.game.render.GameRenderer;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.util.*;

/** Sample rooms and random worlds, using the same terrain and room layers as the game. */
public final class RoomRenderCheck extends ApplicationAdapter {
    private static Throwable failure;

    public static void main(String[] args) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(1280, 800);
        config.disableAudio(true);
        new Lwjgl3Application(new RoomRenderCheck(), config);
        if (failure != null) throw new AssertionError("Room render check failed", failure);
    }

    @Override public void create() {
        try {
            atlasCatalog(0, 0, 16, 5, "rgs-furniture-catalog.png");
            for (int[] dimensions : new int[][] {{5, 5}, {7, 7}, {7, 9}, {9, 9}, {15, 11}}) {
              for (int group = 0; group < 3; group++) {
                boolean furnished = group == 1;
                List<RoomTheme> themes = group == 2
                    ? List.of(RoomTheme.INLAY, RoomTheme.MIXED_STORAGE, RoomTheme.POTTERY)
                    : Arrays.stream(RoomTheme.values()).filter(RoomTheme::active)
                        .filter(t -> t.furnished() == furnished).toList();
                TETile[][] world = new TETile[themes.size() * 18 + 2][50];
                for (var column : world) Arrays.fill(column, Tileset.NOTHING);
                List<RoomDesign> designs = new ArrayList<>();
                for (RoomTheme theme : themes) {
                    for (int variant = 0; variant < 3; variant++) {
                        Room room = new Room(2 + themes.indexOf(theme) * 18, 3 + (2 - variant) * 16,
                            dimensions[0], dimensions[1]);
                        for (int x = room.x() - 1; x <= room.x() + room.width(); x++) {
                            for (int y = room.y() - 1; y <= room.y() + room.height(); y++) {
                                world[x][y] = x < room.x() || x == room.x() + room.width()
                                    || y < room.y() || y == room.y() + room.height() ? Tileset.WALL : Tileset.FLOOR;
                            }
                        }
                        world[room.x() + room.width() / 2][room.y() - 1] = Tileset.OPEN_DOOR;
                        designs.add(RoomStyler.styleRoom(world, room, theme, variant, 42));
                    }
                }
                WallRenderCheck.render(world, (group == 2 ? "rgs-expansion-" : furnished ? "furnished-samples-" : "room-samples-") + dimensions[0] + "x" + dimensions[1] + ".png",
                    16, designs, true);
              }
            }
            for (long seed : new long[] {0, 1, 42, 20260921, 77}) {
                GeneratedWorld world = WorldGenerator.generateDesigned(seed);
                WallRenderCheck.render(world.terrain(), "room-seed-" + seed + ".png", 16, world.rooms(), false);
                RoomDesign selected = world.rooms().stream().filter(r -> !r.furniture().isEmpty())
                    .max(Comparator.comparingInt(r -> r.furniture().size())).orElseThrow();
                Room b = selected.bounds();
                int x0 = b.x() - 1, y0 = b.y() - 1;
                TETile[][] detail = new TETile[b.width() + 2][b.height() + 2];
                for (int x = 0; x < detail.length; x++)
                    System.arraycopy(world.terrain()[x + x0], y0, detail[x], 0, detail[x].length);
                List<RoomDesign.Decoration> moved = selected.decorations().stream().map(d ->
                    new RoomDesign.Decoration(d.x() - x0, d.y() - y0, d.mark(), d.variant(), d.part())).toList();
                RoomDesign local = new RoomDesign(new Room(1, 1, b.width(), b.height()),
                    selected.theme(), selected.variant(), moved, selected.furniture().stream().map(p ->
                        new Furniture(p.x() - x0, p.y() - y0, p.type(), p.orientation(), p.variant(), p.group())).toList());
                WallRenderCheck.render(detail, "room-detail-" + seed + ".png", 32, List.of(local), false);
                renderGameplay(seed);
            }
            verifyRoomLayers();
            verifyFurnitureLayers();
            verifyPotteryFragments();
            verifyInlayPixels();
            System.out.println("PASS: nine themes, three variants at five sizes, five worlds, details and gameplay views; "
                + "RGS source pixels, occupied cells, fog, Chinese glyphs and complete gameplay renderer.");
        } catch (Throwable error) {
            failure = error;
            error.printStackTrace();
        } finally { Gdx.app.exit(); }
    }

    private static void atlasCatalog(int x0, int y0, int columns, int rows, String name) throws java.io.IOException {
        Texture atlas = new Texture(Gdx.files.internal("tilesets/rgs-cc0/tileset-gray.png"));
        atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        SpriteBatch batch = new SpriteBatch();
        var font = new com.badlogic.gdx.graphics.g2d.BitmapFont();
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, columns * 64, rows * 80, false);
        try {
            buffer.begin(); Gdx.gl.glClearColor(.15f, .18f, .24f, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, columns * 64, rows * 80));
            batch.begin();
            for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
                batch.draw(new com.badlogic.gdx.graphics.g2d.TextureRegion(atlas, (x0 + x) * 16, (y0 + y) * 16, 16, 16),
                    x * 64 + 8, (rows - y - 1) * 80 + 24, 48, 48);
                font.draw(batch, (x0 + x) + "," + (y0 + y), x * 64 + 8, (rows - y - 1) * 80 + 18);
            }
            batch.end(); Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, columns * 64, rows * 80); buffer.end();
            PixmapIO.PNG writer = new PixmapIO.PNG();
            try { writer.setFlipY(true); writer.write(Gdx.files.local("../build/wall-review/" + name), pixels); }
            finally { writer.dispose(); pixels.dispose(); }
        } finally { buffer.dispose(); batch.dispose(); font.dispose(); atlas.dispose(); }
    }

    private static void verifyRoomLayers() {
        TETile[][] world = new TETile[9][9];
        for (var column : world) Arrays.fill(column, Tileset.FLOOR);
        world[0][0] = Tileset.WALL;
        world[0][1] = Tileset.OPEN_DOOR;
        world[0][2] = Tileset.CLOSED_DOOR;
        world[0][3] = Tileset.PILLAR;
        RoomDesign design = RoomStyler.styleRoom(world, new Room(0, 0, 9, 9), RoomTheme.EMBLEM, 0, 42);
        RoomDecorationRenderer layer = new RoomDecorationRenderer();
        SpriteBatch batch = new SpriteBatch();
        ShapeRenderer shapes = new ShapeRenderer();
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 144, 144, false);
        try {
            Matrix4 matrix = new Matrix4().setToOrtho2D(0, 0, 144, 144);
            batch.setProjectionMatrix(matrix); shapes.setProjectionMatrix(matrix);
            for (int pass = 0; pass < 4; pass++) {
                buffer.begin();
                Gdx.gl.glClearColor(0, 0, 0, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                batch.begin();
                layer.draw(batch, world, List.of(design), 16,
                    pass == 1 ? Set.of(RoomDecorationRenderer.key(4, 4)) : Set.of(), null);
                batch.end();
                if (pass >= 2) {
                    boolean[][] explored = new boolean[9][9];
                    if (pass == 3) for (var column : explored) Arrays.fill(column, true);
                    new FogRenderer(shapes, 16).render(new boolean[9][9], explored);
                }
                Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, 144, 144);
                buffer.end();
                try {
                    int floor = Color.rgba8888(RoomDecorationRenderer.baseColor(RoomTheme.EMBLEM));
                    boolean foundInk = false;
                    for (int x = 64; x < 80; x++) for (int y = 64; y < 80; y++) {
                        int color = pixels.getPixel(x, y);
                        if (color != floor) foundInk = true;
                        if (pass == 1 && color != floor) throw new AssertionError("Emblem covers occupied cell");
                        if (pass == 2 && color != 0x000000ff) throw new AssertionError("Decoration leaks through fog");
                        if (pass == 3 && (color == floor || color == 0x000000ff))
                            throw new AssertionError("Explored floor must be darkened, not hidden");
                    }
                    if (pass == 0 && !foundInk) throw new AssertionError("Emblem missing");
                    if (pass <= 1) for (int y = 0; y < 4; y++)
                        if (pixels.getPixel(8, y * 16 + 8) != 0x000000ff)
                            throw new AssertionError("Room layer covers wall, pillar or door");
                } finally { pixels.dispose(); }
            }
        } finally { buffer.dispose(); shapes.dispose(); batch.dispose(); layer.dispose(); }
    }

    private static void verifyFurnitureLayers() {
        RoomDecorationRenderer layer = new RoomDecorationRenderer();
        SpriteBatch batch = new SpriteBatch();
        ShapeRenderer shapes = new ShapeRenderer();
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 80, 80, false);
        Pixmap source = new Pixmap(Gdx.files.internal("tilesets/rgs-cc0/tileset-gray.png"));
        try {
            Matrix4 matrix = new Matrix4().setToOrtho2D(0, 0, 80, 80);
            batch.setProjectionMatrix(matrix); shapes.setProjectionMatrix(matrix);
            int floor = Color.rgba8888(RoomDecorationRenderer.baseColor(RoomTheme.STORAGE));
            for (FurnitureType type : FurnitureType.values()) if (type.active())
              for (Furniture.Orientation direction : Furniture.Orientation.values())
                for (int variant = 0; variant < 3; variant++) {
                    if (type == FurnitureType.ABANDONED_BOX && direction != Furniture.Orientation.HORIZONTAL) continue;
                    TETile[][] world = new TETile[5][5];
                    for (var column : world) Arrays.fill(column, Tileset.FLOOR);
                    Furniture prop = new Furniture(2, 2, type, direction, variant, 0);
                    for (int x = 2; x < 2 + prop.width(); x++) for (int y = 2; y < 2 + prop.height(); y++) world[x][y] = type.tile();
                    RoomDesign room = new RoomDesign(new Room(0, 0, 5, 5), RoomTheme.STORAGE, 0, List.of(), List.of(prop));
                    for (boolean fog : new boolean[] {false, true}) {
                        buffer.begin();
                        Gdx.gl.glClearColor(0, 0, 0, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                        batch.begin(); layer.draw(batch, world, List.of(room), 16, Set.of(), null); batch.end();
                        int hiddenX = prop.x() + prop.width() - 1, hiddenY = prop.y() + prop.height() - 1;
                        if (fog) {
                            boolean[][] visible = new boolean[5][5];
                            for (var column : visible) Arrays.fill(column, true);
                            visible[hiddenX][hiddenY] = false;
                            new FogRenderer(shapes, 16).render(visible, new boolean[5][5]);
                        }
                        Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, 80, 80); buffer.end();
                        try {
                            for (int x = 0; x < 5; x++) for (int y = 0; y < 5; y++) {
                                boolean ink = false;
                                for (int px = 0; px < 16; px++) for (int py = 0; py < 16; py++) {
                                    int color = pixels.getPixel(x * 16 + px, y * 16 + py);
                                    if (color != floor) ink = true;
                                    if (!prop.contains(x, y) && color != floor)
                                        throw new AssertionError("Furniture escapes footprint: " + prop);
                                    if (!fog && prop.contains(x, y)) {
                                        int sourceX = (type == FurnitureType.POT ? 224 : 112 + (x - prop.x()) * 16) + px;
                                        int original = source.getPixel(sourceX, 15 - py);
                                        int expected = (original & 255) == 0 ? floor : original;
                                        if (color != expected) throw new AssertionError("RGS source pixels changed: " + prop);
                                    }
                                    if (fog && x == hiddenX && y == hiddenY && color != 0x000000ff)
                                        throw new AssertionError("Hidden furniture part leaks: " + prop);
                                }
                                if (!fog && prop.contains(x, y) && !ink)
                                    throw new AssertionError("Collision cell without visible sprite: " + prop);
                            }
                        } finally { pixels.dispose(); }
                    }
                }
        } finally { source.dispose(); buffer.dispose(); batch.dispose(); shapes.dispose(); layer.dispose(); }
    }

    private static void verifyPotteryFragments() {
        RoomDecorationRenderer layer = new RoomDecorationRenderer();
        SpriteBatch batch = new SpriteBatch();
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 48, 48, false);
        Pixmap source = new Pixmap(Gdx.files.internal("tilesets/rgs-cc0/tileset-gray.png"));
        try {
            TETile[][] world = new TETile[3][3];
            for (var column : world) Arrays.fill(column, Tileset.FLOOR);
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 48, 48));
            int floor = Color.rgba8888(RoomDecorationRenderer.baseColor(RoomTheme.POTTERY));
            for (int variant = 0; variant < 3; variant++) for (boolean occupied : new boolean[] {false, true}) {
                RoomDesign room = new RoomDesign(new Room(0, 0, 3, 3), RoomTheme.POTTERY, variant,
                    List.of(new RoomDesign.Decoration(1, 1, RoomDesign.Mark.SHARDS, variant)));
                buffer.begin(); Gdx.gl.glClearColor(0, 0, 0, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                batch.begin();
                layer.draw(batch, world, List.of(room), 16,
                    occupied ? Set.of(RoomDecorationRenderer.key(1, 1)) : Set.of(), null);
                batch.end();
                Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, 48, 48); buffer.end();
                try {
                    for (int x = 0; x < 48; x++) for (int y = 0; y < 48; y++) {
                        int expected = floor;
                        if (!occupied && x >= 16 && x < 32 && y >= 16 && y < 32) {
                            int original = source.getPixel(240 + x - 16, 31 - y);
                            if ((original & 255) != 0) expected = original;
                        }
                        if (pixels.getPixel(x, y) != expected)
                            throw new AssertionError("RGS fragments changed or covered a gameplay object");
                    }
                } finally { pixels.dispose(); }
            }
        } finally { source.dispose(); buffer.dispose(); batch.dispose(); layer.dispose(); }
    }

    private static void verifyInlayPixels() {
        RoomDecorationRenderer layer = new RoomDecorationRenderer();
        SpriteBatch batch = new SpriteBatch();
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 144, 144, false);
        Pixmap source = new Pixmap(Gdx.files.internal("tilesets/rgs-cc0/tileset-gray.png"));
        try {
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 144, 144));
            int floor = Color.rgba8888(RoomDecorationRenderer.baseColor(RoomTheme.INLAY));
            for (int variant = 0; variant < 3; variant++) {
                TETile[][] world = new TETile[9][9];
                for (var column : world) Arrays.fill(column, Tileset.FLOOR);
                RoomDesign room = RoomStyler.styleRoom(world, new Room(0, 0, 9, 9), RoomTheme.INLAY, variant, 42);
                for (int pass = 0; pass < 4; pass++) {
                    world[4][4] = pass == 3 ? Tileset.WALL : Tileset.FLOOR;
                    boolean[][] visible = new boolean[9][9];
                    for (var column : visible) Arrays.fill(column, true);
                    if (pass == 2) visible[4][4] = false;
                    buffer.begin(); Gdx.gl.glClearColor(0, 0, 0, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    batch.begin(); layer.draw(batch, world, List.of(room), 16,
                        pass == 1 ? Set.of(RoomDecorationRenderer.key(4, 4)) : Set.of(), visible); batch.end();
                    Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, 144, 144); buffer.end();
                    try {
                        for (int x = 0; x < 9; x++) for (int y = 0; y < 9; y++) {
                            var d = room.decorationAt(x, y);
                            for (int px = 0; px < 16; px++) for (int py = 0; py < 16; py++) {
                                int expected = floor;
                                if (x == 4 && y == 4 && pass >= 2) expected = 0x000000ff;
                                else if (d != null && !(x == 4 && y == 4 && pass == 1)) {
                                    int original = source.getPixel(160 + d.part() % 3 * 16 + px,
                                        (2 - d.part() / 3) * 16 + 15 - py);
                                    if ((original & 255) != 0) expected = original;
                                }
                                if (pixels.getPixel(x * 16 + px, y * 16 + py) != expected)
                                    throw new AssertionError("Inlay source pixels, visibility or gameplay exclusion failed");
                            }
                        }
                    } finally { pixels.dispose(); }
                }
            }
        } finally { source.dispose(); buffer.dispose(); batch.dispose(); layer.dispose(); }
    }

    private static void renderGameplay(long seed) throws java.io.IOException {
        Engine engine = new Engine();
        ("N" + seed + "S").chars().forEach(c -> engine.handleKey((char) c));
        FurnitureType wanted = seed % 2 == 0 ? FurnitureType.ABANDONED_BOX : FurnitureType.POT;
        RoomDesign room = engine.roomDesigns().stream().filter(r -> !r.furniture().isEmpty())
            .max(Comparator.<RoomDesign>comparingInt(r -> r.furniture().stream().anyMatch(p -> p.type() == wanted) ? 1 : 0)
                .thenComparingInt(r -> r.furniture().size())).orElseThrow();
        Furniture target = room.furniture().getFirst();
        int px = room.bounds().x() + room.bounds().width() / 2;
        int py = room.bounds().y() + room.bounds().height() / 2;
        // Diagnostic camera position: retain real fog, but put a complete prop in view.
        int best = Integer.MAX_VALUE;
        for (int x = room.bounds().x(); x < room.bounds().x() + room.bounds().width(); x++)
            for (int y = room.bounds().y(); y < room.bounds().y() + room.bounds().height(); y++) {
                int distance = Math.abs(x - target.x()) + Math.abs(y - target.y());
                if (distance < best && byow.game.tile.TileRules.isWalkable(engine.world()[x][y])) {
                    best = distance; px = x; py = y;
                }
            }
        engine.player().teleportTo(engine.world(), px, py);
        engine.updateVision();
        FontManager fonts = new FontManager();
        SpriteBatch batch = new SpriteBatch();
        ShapeRenderer shapes = new ShapeRenderer();
        GameRenderer renderer = new GameRenderer(batch, fonts, shapes, 16);
        int width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight();
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        try {
            String text = "石柱：不可通行，并会阻挡视野，可以绕行。可通行，无交互效果。";
            for (RoomTheme theme : RoomTheme.values()) text += theme.displayName();
            for (RoomDesign.Mark mark : RoomDesign.Mark.values()) text += mark.displayName();
            for (FurnitureType type : FurnitureType.values()) text += type.displayName();
            for (char c : text.toCharArray()) if (fonts.body().getData().getGlyph(c) == null)
                throw new AssertionError("Missing Chinese glyph " + c);
            renderer.resize(width, height);
            buffer.begin();
            Gdx.gl.glClearColor(0, 0, 0, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            renderer.render(engine);
            Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, width, height);
            buffer.end();
            PixmapIO.PNG writer = new PixmapIO.PNG();
            try {
                writer.setFlipY(true);
                writer.write(Gdx.files.local("../build/wall-review/room-gameplay-" + seed + ".png"), pixels);
            } finally { writer.dispose(); pixels.dispose(); }
        } finally { buffer.dispose(); renderer.dispose(); shapes.dispose(); batch.dispose(); fonts.dispose(); }
    }
}
