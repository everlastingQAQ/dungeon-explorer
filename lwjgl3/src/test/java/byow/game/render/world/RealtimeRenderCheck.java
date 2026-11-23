package byow.game.render.world;

import byow.game.*;
import byow.game.monster.*;
import byow.game.realtime.*;
import byow.game.render.*;
import byow.game.tile.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.math.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.*;
import static byow.game.realtime.EngineFixture.*;

/** Generates an animated acceptance clip using GameRenderer, never user saves. */
public final class RealtimeRenderCheck extends ApplicationAdapter {
    private static Throwable failure;
    private static boolean pauseOnly;
    private FontManager fonts;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private GameRenderer renderer;
    private FrameBuffer buffer;
    private static final int WIDTH = 960, HEIGHT = 640;

    public static void main(String[] args) {
        pauseOnly = Arrays.asList(args).contains("--pause-only");
        var config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(WIDTH, HEIGHT); config.disableAudio(true);
        new Lwjgl3Application(new RealtimeRenderCheck(), config);
        if (failure != null) throw new AssertionError("Realtime render check failed", failure);
    }

    @Override public void create() {
        try {
            fonts = new FontManager(); batch = new SpriteBatch(); shapes = new ShapeRenderer();
            renderer = new GameRenderer(batch, fonts, shapes, 16); renderer.resize(WIDTH, HEIGHT);
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
            String glyphs = "喘息秒剩余再走无伤换位决策保护";
            for (char c : glyphs.toCharArray()) if (fonts.body().getData().getGlyph(c) == null)
                throw new AssertionError("Missing glyph " + c);
            verifyPauseOverlay();
            if (pauseOnly) {
                System.out.println("PASS: paused world, fixed-screen pause controls, camera independence, save/cancel navigation.");
                return;
            }
            verifyHoverAndFog();
            verifyTooltipWrapping();
            Path directory = Path.of("../build/wall-review/realtime");
            Files.createDirectories(directory);
            var writer = ImageIO.getImageWritersByFormatName("gif").next();
            try (var output = new FileImageOutputStream(directory.resolve("realtime-demo.gif").toFile())) {
                writer.setOutput(output); writer.prepareWriteSequence(null);
                for (int scenario = 0; scenario < 5; scenario++) {
                    Engine e = fixture(scenario);
                    for (int frame = 0; frame < 90; frame++) {
                        input(e, scenario, frame);
                        ticks(e, 2);
                        Pixmap pixels = render(e);
                        if (frame == 20 || frame == 50) writePng(directory.resolve("scene-" + scenario + "-" + frame + ".png"), pixels);
                        BufferedImage image = image(pixels); pixels.dispose();
                        var metadata = writer.getDefaultImageMetadata(
                            ImageTypeSpecifier.createFromRenderedImage(image), null);
                        String format = metadata.getNativeMetadataFormatName();
                        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                        IIOMetadataNode control = node(root, "GraphicControlExtension");
                        control.setAttribute("disposalMethod", "none");
                        control.setAttribute("userInputFlag", "FALSE");
                        control.setAttribute("transparentColorFlag", "FALSE");
                        control.setAttribute("delayTime", frame % 3 == 2 ? "4" : "3");
                        control.setAttribute("transparentColorIndex", "0");
                        if (scenario == 0 && frame == 0) {
                            IIOMetadataNode extensions = node(root, "ApplicationExtensions");
                            IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                            loop.setAttribute("applicationID", "NETSCAPE");
                            loop.setAttribute("authenticationCode", "2.0");
                            loop.setUserObject(new byte[] {1, 0, 0}); extensions.appendChild(loop);
                        }
                        metadata.setFromTree(format, root);
                        writer.writeToSequence(new IIOImage(image, null, metadata), null);
                    }
                }
                writer.endWriteSequence();
            } finally { writer.dispose(); }
            System.out.println("PASS: actual renderer GIF (straight, staggered AI, resting swap, freeze/thaw, turns), moving hover, hidden rest markers and Chinese glyphs.");
        } catch (Throwable error) { failure = error; error.printStackTrace(); }
        finally {
            if (buffer != null) buffer.dispose();
            if (renderer != null) renderer.dispose();
            if (shapes != null) shapes.dispose();
            if (batch != null) batch.dispose();
            if (fonts != null) fonts.dispose();
            Gdx.app.exit();
        }
    }

    private void verifyPauseOverlay() throws Exception {
        Path directory = Path.of("../build/wall-review/realtime");
        Files.createDirectories(directory);
        for (int x : new int[] {30, 44}) {
            Engine e = fixture(0);
            e.player().teleportTo(e.world(), x, 20);
            e.updateVision();
            Pixmap playing = render(e);
            keys(e, "P");
            Pixmap paused = render(e);
            try {
                writePng(directory.resolve("pause-" + x + ".png"), paused);
                int bannerChanges = 0;
                int top = HEIGHT - GameConfig.HUD_HEIGHT;
                for (int px = 0; px < WIDTH; px++) for (int py = 0; py < HEIGHT; py++) {
                    if (playing.getPixel(px, py) == paused.getPixel(px, py)) continue;
                    if (py >= top - 64 && py < top) bannerChanges++;
                    else throw new AssertionError("Pause controls escaped the UI banner at " + px + "," + py);
                }
                if (bannerChanges < 100)
                    throw new AssertionError("Pause controls are missing from screen coordinates; camera x=" + x);
                e.update(600);
                Pixmap later = render(e);
                try {
                    for (int px = 0; px < WIDTH; px++) for (int py = 0; py < HEIGHT; py++)
                        if (paused.getPixel(px, py) != later.getPixel(px, py))
                            throw new AssertionError("Paused scene changed");
                } finally { later.dispose(); }
                keys(e, "SB");
                Pixmap back = render(e);
                try {
                    for (int px = 0; px < WIDTH; px++) for (int py = 0; py < HEIGHT; py++)
                        if (paused.getPixel(px, py) != back.getPixel(px, py))
                            throw new AssertionError("Cancel save did not restore pause UI");
                } finally { back.dispose(); }
            } finally { playing.dispose(); paused.dispose(); }
        }
    }

    private void verifyTooltipWrapping() throws Exception {
        var hud = new byow.game.render.ui.HudRenderer(batch, fonts, shapes);
        Class<?> contentClass = Class.forName("byow.game.render.ui.HudRenderer$InfoContent");
        var constructor = contentClass.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        Object content = constructor.newInstance(
            "伏击者：冻结中，剩余 2.0 秒；追击中，已走 3/4 格；再走 1 格后喘息 1.2 秒。",
            Color.CYAN);
        var method = hud.getClass().getDeclaredMethod("createHoverLayout",
            float.class, float.class, int.class, int.class, contentClass);
        method.setAccessible(true);
        Object layout = method.invoke(hud, 640f, 480f, 630, 400, content);
        var width = layout.getClass().getDeclaredMethod("width"); width.setAccessible(true);
        var height = layout.getClass().getDeclaredMethod("height"); height.setAccessible(true);
        if ((float)width.invoke(layout) > 624 || (float)height.invoke(layout) < 48)
            throw new AssertionError("Realtime hover text must wrap inside narrow windows");
    }

    private Engine fixture(int scenario) {
        Engine e = open();
        // Actual existing floor/wall tiles provide camera-motion landmarks.
        for (int x = 19; x < 43; x++) {
            e.world()[x][16] = Tileset.WALL;
            e.world()[x][25] = Tileset.WALL;
        }
        for (int x = 20; x < 43; x += 3) e.world()[x][18] = Tileset.CRACKED_FLOOR;
        if (scenario == 1) {
            for (int i = 0; i < MonsterType.values().length; i++) {
                var m = add(e, i + 1, 24, 18 + i, MonsterType.values()[i]);
                m.activity.waitTicks = 3 * i;
                m.setHome(30, 20); m.setPatrolRoute(40, 21, 23, 21);
            }
        } else if (scenario == 2) {
            var m = add(e, 1, 31, 20, MonsterType.HUNTER);
            m.activity.walked = Timing.pace(m.type()).stepsBeforeRest();
            m.activity.restTicks = Timing.pace(m.type()).rest();
        } else if (scenario == 3) {
            add(e, 1, 25, 20, MonsterType.HUNTER);
            add(e, 2, 25, 22, MonsterType.GUARDIAN).setHome(30, 20);
        } else if (scenario == 4) e.world()[31][21] = Tileset.WALL;
        e.updateVision();
        return e;
    }

    private static Monster add(Engine e, long id, int x, int y, MonsterType type) {
        var m = new Monster(id, x, y, type);
        EngineFixture.<Monster>list(e, "monsters").add(m); return m;
    }

    private void input(Engine e, int scenario, int frame) {
        if (scenario == 0) {
            if (frame == 0) e.pressDirection('D');
            if (frame == 30) { e.releaseDirection('D'); e.pressDirection('W'); }
            if (frame == 50) e.releaseDirection('W');
        }
        if (scenario == 2 && frame == 5) { e.pressDirection('D'); e.releaseDirection('D'); }
        if (scenario == 2 && frame == 12 && e.player().currentHp() != 3)
            throw new AssertionError("Resting swap caused damage");
        if (scenario == 3 && frame == 15) set(e, "freezeTicks", 120);
        if (scenario == 4) {
            if (frame == 0) e.pressDirection('D');
            if (frame == 3) { e.pressDirection('W'); e.releaseDirection('W'); }
            if (frame == 13) e.pressDirection('W');
            if (frame == 26) { e.releaseDirection('W'); e.releaseDirection('D'); }
        }
    }

    private Pixmap render(Engine e) {
        buffer.begin();
        Gdx.gl.glClearColor(0, 0, 0, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        renderer.render(e);
        Pixmap result = Pixmap.createFromFrameBuffer(0, 0, WIDTH, HEIGHT);
        buffer.end(); return result;
    }

    private void verifyHoverAndFog() throws Exception {
        Engine e = open(); var m = add(e, 1, 31, 20, MonsterType.HUNTER);
        m.activity.motion = new Motion(31, 20, 32, 20, 12, 6, false);
        render(e).dispose();
        var field = GameRenderer.class.getDeclaredField("worldRenderer"); field.setAccessible(true);
        WorldRenderer world = (WorldRenderer) field.get(renderer);
        var cameraField = WorldRenderer.class.getDeclaredField("camera"); cameraField.setAccessible(true);
        OrthographicCamera camera = (OrthographicCamera) cameraField.get(world);
        Vector3 point = camera.project(new Vector3((m.renderX() + .75f) * 16,
            (m.renderY() + .5f) * 16, 0), 0, 0, WIDTH, HEIGHT - GameConfig.HUD_HEIGHT);
        String text = world.actorAtScreen(e, (int)point.x, HEIGHT - (int)point.y);
        if (text == null || !text.contains("猎手")) throw new AssertionError("Moving sprite hover missed");
        e.visible()[m.x()][m.y()] = false;
        if (world.actorAtScreen(e, (int)point.x, HEIGHT - (int)point.y) != null)
            throw new AssertionError("Hidden sprite hover leaked");
        m.activity.motion = null; m.activity.restTicks = 30;
        Pixmap resting = render(e);
        m.activity.restTicks = 0; Pixmap idle = render(e);
        try {
            for (int x = 0; x < WIDTH; x++) for (int y = 0; y < HEIGHT; y++)
                if (resting.getPixel(x, y) != idle.getPixel(x, y))
                    throw new AssertionError("Rest label/bar leaked through fog");
        } finally { resting.dispose(); idle.dispose(); }
    }

    private static BufferedImage image(Pixmap pixels) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
            image.setRGB(x, HEIGHT - 1 - y, pixels.getPixel(x, y) >>> 8);
        return image;
    }
    private static void writePng(Path path, Pixmap pixels) throws Exception {
        PixmapIO.PNG png = new PixmapIO.PNG();
        try { png.setFlipY(true); png.write(new com.badlogic.gdx.files.FileHandle(path.toFile()), pixels); }
        finally { png.dispose(); }
    }
    private static IIOMetadataNode node(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++)
            if (root.item(i).getNodeName().equals(name)) return (IIOMetadataNode)root.item(i);
        var result = new IIOMetadataNode(name); root.appendChild(result); return result;
    }
}
