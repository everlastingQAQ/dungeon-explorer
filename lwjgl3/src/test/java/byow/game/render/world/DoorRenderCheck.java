package byow.game.render.world;

import byow.game.*;
import byow.game.door.DoorLayout;
import byow.game.realtime.EngineFixture;
import byow.game.render.*;
import byow.game.tile.*;
import byow.game.worldGenerator.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.math.Matrix4;
import java.util.*;

/** Native door source pixels, cell-wise fog, and the actual game rendering path. */
public final class DoorRenderCheck extends ApplicationAdapter {
    private static Throwable failure;
    private FontManager fonts;
    private SpriteBatch batch;
    private ShapeRenderer shapes;

    public static void main(String[] args) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(960, 640); config.disableAudio(true);
        new Lwjgl3Application(new DoorRenderCheck(), config);
        if (failure != null) throw new AssertionError("Door render check failed", failure);
    }

    @Override public void create() {
        try {
            fonts = new FontManager(); batch = new SpriteBatch(); shapes = new ShapeRenderer();
            for (char c : "双格木门出口已经打开尚未开启阻挡视野".toCharArray())
                if (fonts.body().getData().getGlyph(c) == null) throw new AssertionError("Missing glyph " + c);
            verifySourceAndFog();
            for (boolean opened : new boolean[] {false, true}) {
                Engine engine = EngineFixture.open();
                for (int x = 21; x < 40; x++) engine.world()[x][20] = Tileset.WALL;
                engine.world()[29][20] = engine.world()[30][20] = opened ? Tileset.OPEN_DOOR : Tileset.CLOSED_DOOR;
                var room = new RoomDesign(new Room(22, 21, 17, 12), RoomTheme.STONE, 0, List.of());
                EngineFixture.set(engine, "roomDesigns", List.of(room));
                engine.player().teleportTo(engine.world(), 29, 18); engine.updateVision();
                renderGame(engine, opened ? "wide-open.png" : "wide-closed.png");
                if (!opened) {
                    var exit = new GameExit(29, 20, false, 2);
                    EngineFixture.set(engine, "exit", exit);
                    // Only the right half is visible; drawing must not depend on the anchor cell.
                    for (var column : engine.visible()) Arrays.fill(column, false);
                    engine.visible()[30][20] = engine.visible()[30][21] = true;
                    for (var column : engine.explored()) Arrays.fill(column, false);
                    renderGame(engine, "wide-exit-right-visible.png");
                }
            }
            int examples = 0;
            for (int seed = 0; seed < 100 && examples < 2; seed++) {
                Engine engine = new Engine(); EngineFixture.keys(engine, "N" + seed + "S");
                var door = DoorLayout.collect(engine.world(), engine.roomDesigns(), engine.exit())
                    .stream().filter(d -> d.width() == 2).findFirst().orElse(null);
                if (door == null) continue;
                engine.player().teleportTo(engine.world(), door.x(), door.y() - 1);
                engine.updateVision();
                renderGame(engine, "wide-seed-" + seed + ".png");
                examples++;
            }
            if (examples < 2) throw new AssertionError("Missing generated wide door fixtures");
            System.out.println("PASS: full 32x32 source pixels, open/closed doors, independently fogged halves, right-visible exit, actual gameplay fixtures and two seeds.");
        } catch (Throwable error) { failure = error; error.printStackTrace(); }
        finally {
            if (shapes != null) shapes.dispose();
            if (batch != null) batch.dispose();
            if (fonts != null) fonts.dispose();
            Gdx.app.exit();
        }
    }

    private void verifySourceAndFog() throws Exception {
        var tileRenderer = new TileRenderer(batch, fonts.tile(), shapes, 16);
        var buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 96, 80, false);
        try {
            var matrix = new Matrix4().setToOrtho2D(0, 0, 96, 80);
            batch.setProjectionMatrix(matrix); shapes.setProjectionMatrix(matrix);
            for (boolean opened : new boolean[] {false, true}) {
                var source = new Pixmap(Gdx.files.internal("sprites/dungeon-ii/door-" + (opened ? "open" : "closed") + ".png"));
                try {
                    for (boolean fog : new boolean[] {false, true}) {
                        buffer.begin(); clear();
                        batch.begin();
                        tileRenderer.drawDoor(new DoorLayout.Door(2, 1, 2, opened, false));
                        batch.end();
                        if (fog) {
                            boolean[][] visible = new boolean[6][5];
                            for (var column : visible) Arrays.fill(column, true);
                            visible[2][1] = visible[2][2] = false;
                            new FogRenderer(shapes, 16).render(visible, new boolean[6][5]);
                        }
                        Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, 96, 80); buffer.end();
                        try {
                            for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) {
                                int expected = source.getPixel(x, 31 - y);
                                if ((expected & 255) == 0 || fog && x < 16) expected = 0x000000ff;
                                if (pixels.getPixel(32 + x, 16 + y) != expected)
                                    throw new AssertionError("Door source or fog mismatch: " + x + "," + y);
                            }
                        } finally { pixels.dispose(); }
                    }
                } finally { source.dispose(); }
            }
        } finally { tileRenderer.dispose(); buffer.dispose(); }
    }

    private void renderGame(Engine engine, String name) throws Exception {
        var renderer = new GameRenderer(batch, fonts, shapes, 16);
        var buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 960, 640, false);
        try {
            renderer.resize(960, 640);
            buffer.begin(); clear(); renderer.render(engine);
            Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, 960, 640); buffer.end();
            try {
                if (name.equals("wide-exit-right-visible.png")) {
                    // No sprite may be revealed in an unseen map cell; at least
                    // the explicitly visible right half must actually be drawn.
                    var worldField = GameRenderer.class.getDeclaredField("worldRenderer"); worldField.setAccessible(true);
                    var world = (WorldRenderer) worldField.get(renderer);
                    var cameraField = WorldRenderer.class.getDeclaredField("camera"); cameraField.setAccessible(true);
                    var camera = (OrthographicCamera) cameraField.get(world);
                    int lit = 0;
                    for (int cellY = 20; cellY <= 21; cellY++) {
                        var point = camera.project(new com.badlogic.gdx.math.Vector3(29.5f * 16,
                            (cellY + .5f) * 16, 0), 0, 0, 960, 560);
                        if (pixels.getPixel((int)point.x, (int)point.y) != 0x000000ff)
                            throw new AssertionError("Hidden left door half leaked");
                    }
                    for (int x = 0; x < 960; x++) for (int y = 0; y < 560; y++)
                        if (pixels.getPixel(x, y) != 0x000000ff) lit++;
                    if (lit == 0) throw new AssertionError("Right-only visible exit was omitted");
                }
                var writer = new PixmapIO.PNG();
                try {
                    writer.setFlipY(true);
                    var file = Gdx.files.local("../build/wall-review/doors/" + name);
                    file.parent().mkdirs(); writer.write(file, pixels);
                } finally { writer.dispose(); }
            } finally { pixels.dispose(); }
        } finally { renderer.dispose(); buffer.dispose(); }
    }

    private void clear() { Gdx.gl.glClearColor(0, 0, 0, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT); }
}
