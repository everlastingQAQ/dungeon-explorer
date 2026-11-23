package byow.game;

import byow.game.input.GameInputProcessor;
import byow.game.render.FontManager;
import byow.game.render.GameRenderer;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;


/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class ByowGame extends ApplicationAdapter {
    private SpriteBatch batch;
    private FontManager fonts;
    private ShapeRenderer shapeRenderer;

    private GameRenderer gameRenderer;
    private Engine engine;
    private GameInputProcessor input;

    @Override
    public void create() {
        batch = new SpriteBatch();
        fonts = new FontManager();
        shapeRenderer = new ShapeRenderer();
        engine = new Engine();
        gameRenderer = new GameRenderer(batch, fonts, shapeRenderer, GameConfig.getTileSize());

        input = new GameInputProcessor(engine);
        Gdx.input.setInputProcessor(input);
    }

    @Override
    public void render() {

        if (engine.shouldQuit()) {
            Gdx.app.exit();
            return;
        }

        engine.update(Gdx.graphics.getDeltaTime());
        ScreenUtils.clear(0, 0, 0, 1);
        gameRenderer.render(engine);
    }

    @Override public void pause() { focusLost(); }

    public void focusLost() {
        if (engine != null) engine.focusLost();
        if (input != null) input.clear();
    }

    @Override
    public void dispose() {
        gameRenderer.dispose();
        batch.dispose();
        fonts.dispose();
        shapeRenderer.dispose();
    }

    /**
     * 如果框架发现窗口resize, 自动调用 ByowGame.resize(newWidth, newHeight)
     * @param width the new width in pixels
     * @param height the new height in pixels
     */
    @Override
    public void resize(int width, int height) {
        gameRenderer.resize(width, height);
    }

}
