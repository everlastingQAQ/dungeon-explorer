package byow.game.render.world;

import byow.game.*;
import byow.game.feedback.GameFeedback;
import byow.game.item.*;
import byow.game.monster.*;
import byow.game.realtime.EngineFixture;
import byow.game.render.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.*;
import java.util.*;
import static byow.game.realtime.EngineFixture.*;

/** Actual-renderer acceptance: cosmetic events, paused frames, glyphs and fog. */
public final class FeedbackRenderCheck extends ApplicationAdapter {
    private static Throwable failure;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private FontManager fonts;
    private GameRenderer renderer;
    private FrameBuffer buffer;

    public static void main(String[] args) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(960, 640); config.disableAudio(true);
        new Lwjgl3Application(new FeedbackRenderCheck(), config);
        if (failure != null) throw new AssertionError("Feedback renderer", failure);
    }

    @Override public void create() {
        try {
            fonts = new FontManager(); batch = new SpriteBatch(); shapes = new ShapeRenderer();
            renderer = new GameRenderer(batch, fonts, shapes, 16); renderer.resize(960, 640);
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 960, 640, false);
            String messages = "尖刺生命护盾抵挡拾取水晶金币钥匙宝珠符文激活猎手伏击者守卫巡逻胆小鬼遗物恢复生命已满出口已开启找到出口进入即可获胜视野怪物冻结秒雷达喘息";
            for (char c : messages.toCharArray()) if (fonts.message().getData().getGlyph(c) == null)
                throw new AssertionError("Missing Chinese glyph " + c);

            Engine hurt = open(); add(hurt, 1, 31, 20); ticks(hurt, 1);
            capture(hurt, "damage.png");
            Engine shield = open(); shield.player().grantShield(1);
            add(shield, 1, 31, 20); ticks(shield, 1);
            capture(shield, "shield.png");
            ticks(shield, 19); capture(shield, "protection.png");

            Engine pickup = open(); set(pickup, "collectedCount", 2);
            var exit = new GameExit(34, 22, false); set(pickup, "exit", exit); exit.syncTile(pickup.world());
            pickup.items().add(new Item(31, 20, ItemType.CRYSTAL)); tap(pickup, 'D');
            capture(pickup, "pickup-exit.png");
            keys(pickup, "P");
            Pixmap before = frame(pickup);
            pickup.update(600);
            Pixmap after = frame(pickup);
            same(before, after, "Paused feedback changed"); before.dispose(); after.dispose();
            capture(pickup, "paused-pickup.png");

            Engine states = open();
            var resting = add(states, 1, 28, 21); resting.activity.restTicks = 36;
            capture(states, "rest.png");
            set(states, "freezeTicks", 90); states.player().grantShield(1);
            set(states, "protectionTicks", 30); set(states, "visionRadius", 9); set(states, "radarActive", true);
            states.feedback().notice(GameFeedback.Channel.EFFECT, "祝福宝箱：怪物冻结 2 秒");
            capture(states, "freeze.png");
            Pixmap hud = frame(states);
            try {
                for (int x = 0; x < 960; x++) if (x < 190 || x > 770)
                    for (int y = 560; y < 640; y++)
                        if (hud.getPixel(x, y) == 0x78dcffff)
                            throw new AssertionError("Status text overlaps HP/relic columns");
            } finally { hud.dispose(); }

            // Hidden monster removed/added must produce identical pixels,
            // including its floating label and bar above the sprite.
            Engine hidden = open(); set(hidden, "freezeTicks", 100);
            var ghost = add(hidden, 1, 40, 20); ghost.activity.restTicks = 36;
            before = frame(hidden);
            EngineFixture.<Monster>list(hidden, "monsters").clear();
            after = frame(hidden);
            same(before, after, "Hidden monster/status leaked through fog");
            before.dispose(); after.dispose();

            // Cosmetic calls must not mutate real gameplay or consume an effect.
            var notices = pickup.feedback().notices(); int hp = pickup.player().currentHp();
            capture(pickup, "paused-repeat.png");
            if (!notices.equals(pickup.feedback().notices()) || hp != pickup.player().currentHp())
                throw new AssertionError("Rendering changed the simulation");
            Engine generated = new Engine(); keys(generated, "N0S");
            Item relic = generated.items().getFirst();
            boolean collected = false;
            for (int[] offset : new int[][] {{-1, 0, 'D'}, {1, 0, 'A'}, {0, -1, 'W'}, {0, 1, 'S'}}) {
                int x = relic.x() + offset[0], y = relic.y() + offset[1];
                if (!byow.game.tile.TileRules.isWalkable(generated.world()[x][y])) continue;
                generated.player().teleportTo(generated.world(), x, y); generated.updateVision();
                set(generated, "collectedCount", 2);
                tap(generated, (char) offset[2]);
                capture(generated, "generated-pickup.png"); collected = true; break;
            }
            if (!collected) throw new AssertionError("No approach to generated relic");
            System.out.println("PASS: Chinese glyphs, damage/shield/protection, pickup + exit, rest/freeze, paused pixels and hidden statuses.");
        } catch (Throwable error) { failure = error; error.printStackTrace(); }
        finally {
            if (buffer != null) buffer.dispose(); if (renderer != null) renderer.dispose();
            if (batch != null) batch.dispose(); if (shapes != null) shapes.dispose(); if (fonts != null) fonts.dispose();
            Gdx.app.exit();
        }
    }

    private Monster add(Engine engine, long id, int x, int y) {
        var monster = new Monster(id, x, y, MonsterType.HUNTER);
        EngineFixture.<Monster>list(engine, "monsters").add(monster);
        return monster;
    }
    private Pixmap frame(Engine engine) {
        buffer.begin();
        Gdx.gl.glClearColor(0, 0, 0, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        renderer.render(engine);
        Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, 960, 640);
        buffer.end();
        return pixels;
    }
    private void capture(Engine engine, String name) throws Exception {
        Pixmap pixels = frame(engine);
        var writer = new PixmapIO.PNG();
        try {
            writer.setFlipY(true);
            var file = Gdx.files.local("../build/wall-review/feedback/" + name);
            file.parent().mkdirs(); writer.write(file, pixels);
        } finally { writer.dispose(); pixels.dispose(); }
    }
    private void same(Pixmap a, Pixmap b, String message) {
        for (int x = 0; x < a.getWidth(); x++) for (int y = 0; y < a.getHeight(); y++)
            if (a.getPixel(x, y) != b.getPixel(x, y)) throw new AssertionError(message + ": " + x + "," + y);
    }
}
