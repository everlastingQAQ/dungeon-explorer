package byow.game.render.ui;

import byow.game.render.FontManager;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import static byow.game.render.RenderUtils.drawCentered;

/** Keep the frozen world and hover descriptions visible for inspection. */
public class PauseRenderer {
    private final SpriteBatch batch;
    private final BitmapFont font;
    public PauseRenderer(SpriteBatch batch, FontManager fonts) {
        this.batch = batch;
        this.font = fonts.body();
    }
    public void render() {
        float y = Gdx.graphics.getHeight() - byow.game.GameConfig.HUD_HEIGHT - 18;
        String text = "游戏暂停 · 继续 (P)   保存 (S)   返回主菜单 (Q)";
        batch.begin();
        font.setColor(Color.BLACK);
        drawCentered(batch, font, text, y - 2);
        font.setColor(Color.WHITE);
        drawCentered(batch, font, text, y);
        batch.end();
    }
}
