package byow.game.render.ui;

import byow.game.item.Item;
import byow.game.feedback.GameFeedback;
import byow.game.monster.MonsterType;
import byow.game.render.FontManager;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;
import java.util.List;

import static byow.game.GameConfig.HUD_HEIGHT;

/** 绘制顶部玩家状态和左下角的单行情境日志。 */
public class HudRenderer {
    private static final float HORIZONTAL_PADDING = 24f;
    private static final float MESSAGE_X = 24f;
    private static final float MESSAGE_Y = 32f;

    private static final Color HUD_BACKGROUND =
        new Color(18 / 255f, 24 / 255f, 32 / 255f, 1f);
    private static final Color STATUS_COLOR =
        new Color(120 / 255f, 220 / 255f, 255 / 255f, 1f);
    private static final Color MONSTER_ACCENT =
        new Color(255 / 255f, 130 / 255f, 100 / 255f, 1f);
    private static final Color ITEM_ACCENT =
        new Color(100 / 255f, 220 / 255f, 190 / 255f, 1f);
    private static final Color TOOLTIP_BACKGROUND =
        new Color(8 / 255f, 12 / 255f, 20 / 255f, 0.94f);
    private static final float TOOLTIP_PADDING = 10f;
    private static final float TOOLTIP_OFFSET = 16f;

    private final SpriteBatch batch;
    private final BitmapFont bodyFont;
    private final BitmapFont messageFont;
    private final ShapeRenderer shapeRenderer;
    private final GlyphLayout layout = new GlyphLayout();

    public HudRenderer(SpriteBatch batch, FontManager fonts, ShapeRenderer shapeRenderer) {
        this.batch = batch;
        this.bodyFont = fonts.body();
        this.messageFont = fonts.message();
        this.shapeRenderer = shapeRenderer;
    }

    /**
     * 绘制完整 HUD。
     *
     * 顶部从左到右显示生命值、当前 Buff 状态和遗物进度。左下角以
     * 单行日志显示首次遇见的怪物；没有怪物提示时显示附近遗物。
     */
    public void render(
        int currentHp,
        int maxHp,
        int collectedCount,
        int targetCount,
        int shieldCharges,
        int frozenMonsterTurns,
        int protectionTicks,
        int visionBonus,
        boolean radarActive,
        String radarDirection,
        MonsterType monsterNoticeType,
        String hoverDescription,
        Item nearbyItem,
        int mouseX,
        int mouseY,
        GameFeedback feedback
    ) {
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        InfoContent info = createInfo(monsterNoticeType, nearbyItem);
        HoverLayout hover = hoverDescription == null
            ? null
            : createHoverLayout(
                width,
                height,
                mouseX,
                mouseY,
                new InfoContent(hoverDescription, ITEM_ACCENT)
            );

        drawBackground(width, height);
        drawFeedback(feedback, width, height);
        drawHealthPips(currentHp, maxHp, height);
        drawHoverBackground(hover);
        drawText(
            width,
            height,
            currentHp,
            maxHp,
            collectedCount,
            targetCount,
            shieldCharges,
            frozenMonsterTurns,
            protectionTicks,
            visionBonus,
            radarActive,
            radarDirection,
            info,
            hover,
            feedback
        );
    }

    /** Separate bounded channels: a pickup cannot erase the exit-unlocked notice. */
    private void drawFeedback(GameFeedback feedback, float width, float height) {
        float boxWidth = Math.max(48, Math.min(680, width - 48));
        float x = (width - boxWidth) / 2;
        // Reserve the first line below the HUD for the pause controls.
        float top = height - HUD_HEIGHT - 58;
        for (var notice : feedback.notices()) {
            Color accent = notice.channel() == GameFeedback.Channel.EXIT ? Color.GOLD : ITEM_ACCENT;
            layout.setText(messageFont, notice.text(), accent, boxWidth - 24,
                com.badlogic.gdx.utils.Align.left, true);
            float boxHeight = layout.height + 20;
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(HUD_BACKGROUND);
            shapeRenderer.rect(x, top - boxHeight, boxWidth, boxHeight);
            shapeRenderer.setColor(accent);
            shapeRenderer.rect(x, top - boxHeight, 3, boxHeight);
            shapeRenderer.end();
            batch.begin();
            messageFont.setColor(accent);
            messageFont.draw(batch, notice.text(), x + 12, top - 10, boxWidth - 24,
                com.badlogic.gdx.utils.Align.left, true);
            batch.end();
            top -= boxHeight + 8;
        }
    }

    private void drawHealthPips(int currentHp, int maxHp, float height) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < maxHp; i++) {
            shapeRenderer.setColor(i < currentHp ? MONSTER_ACCENT : Color.DARK_GRAY);
            shapeRenderer.rect(HORIZONTAL_PADDING + i * 22, height - HUD_HEIGHT + 8, 18, 4);
        }
        shapeRenderer.end();
    }

    private HoverLayout createHoverLayout(
        float screenWidth,
        float screenHeight,
        int mouseX,
        int mouseY,
        InfoContent content
    ) {
        float availableWidth = Math.max(48, screenWidth - TOOLTIP_PADDING * 2f - 16f);
        layout.setText(messageFont, content.text(), content.accent(),
            availableWidth, com.badlogic.gdx.utils.Align.left, true);
        float boxWidth = layout.width + TOOLTIP_PADDING * 2f;
        float boxHeight = layout.height + TOOLTIP_PADDING * 2f;
        float worldTop = screenHeight - HUD_HEIGHT;
        float x = MathUtils.clamp(
            mouseX + TOOLTIP_OFFSET,
            8f,
            Math.max(8f, screenWidth - boxWidth - 8f)
        );
        float mouseYFromBottom = screenHeight - mouseY;
        float y = MathUtils.clamp(
            mouseYFromBottom + TOOLTIP_OFFSET,
            8f,
            Math.max(8f, worldTop - boxHeight - 8f)
        );
        return new HoverLayout(content, x, y, boxWidth, boxHeight, layout.height);
    }

    private void drawHoverBackground(HoverLayout hover) {
        if (hover == null) {
            return;
        }
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(TOOLTIP_BACKGROUND);
        shapeRenderer.rect(hover.x(), hover.y(), hover.width(), hover.height());
        shapeRenderer.end();
    }

    private void drawBackground(float width, float height) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(HUD_BACKGROUND);
        shapeRenderer.rect(0, height - HUD_HEIGHT, width, HUD_HEIGHT);
        shapeRenderer.end();
    }

    private void drawText(
        float width,
        float height,
        int currentHp,
        int maxHp,
        int collectedCount,
        int targetCount,
        int shieldCharges,
        int frozenMonsterTurns,
        int protectionTicks,
        int visionBonus,
        boolean radarActive,
        String radarDirection,
        InfoContent info,
        HoverLayout hover,
        GameFeedback feedback
    ) {
        String hpText = "生命  " + currentHp + " / " + maxHp;
        String itemText = "遗物  " + collectedCount + " / " + targetCount;
        String statusText = createStatusText(
            shieldCharges,
            frozenMonsterTurns,
            protectionTicks,
            visionBonus,
            radarActive,
            radarDirection
        );

        batch.begin();
        bodyFont.setColor(feedback.impact() == GameFeedback.Impact.DAMAGE ? MONSTER_ACCENT : Color.WHITE);
        layout.setText(bodyFont, hpText);
        float textY = height - HUD_HEIGHT + (HUD_HEIGHT + layout.height) / 2;
        bodyFont.draw(batch, hpText, HORIZONTAL_PADDING, textY);

        bodyFont.setColor(STATUS_COLOR);
        // Keep the status block between HP and relic counters even with all buffs.
        float statusWidth = Math.max(96, width - 400);
        float scaleX = bodyFont.getData().scaleX, scaleY = bodyFont.getData().scaleY;
        layout.setText(bodyFont, statusText, STATUS_COLOR, statusWidth,
            com.badlogic.gdx.utils.Align.center, true);
        if (layout.height > HUD_HEIGHT - 16) {
            // Native 12px fallback, never a fractional pixel-font size.
            bodyFont.getData().setScale(.5f);
            layout.setText(bodyFont, statusText, STATUS_COLOR, statusWidth,
                com.badlogic.gdx.utils.Align.center, true);
        }
        bodyFont.draw(batch, statusText, (width - statusWidth) / 2f,
            height - HUD_HEIGHT + (HUD_HEIGHT + layout.height) / 2,
            statusWidth, com.badlogic.gdx.utils.Align.center, true);
        bodyFont.getData().setScale(scaleX, scaleY);

        bodyFont.setColor(collectedCount >= targetCount ? Color.GOLD : Color.WHITE);
        layout.setText(bodyFont, itemText);
        bodyFont.draw(batch, itemText, width - HORIZONTAL_PADDING - layout.width, textY);

        if (info != null) {
            drawInfoText(info);
        }
        if (hover != null) {
            drawHoverText(hover);
        }
        batch.end();
    }

    /** 像素地牢式左下角单行日志，用阴影保证文字在地图上可读。 */
    private void drawInfoText(InfoContent info) {
        messageFont.setColor(Color.BLACK);
        messageFont.draw(batch, info.text(), MESSAGE_X + 2f, MESSAGE_Y - 2f);
        messageFont.setColor(info.accent());
        messageFont.draw(batch, info.text(), MESSAGE_X, MESSAGE_Y);
    }

    private void drawHoverText(HoverLayout hover) {
        messageFont.setColor(hover.content().accent());
        messageFont.draw(
            batch,
            hover.content().text(),
            hover.x() + TOOLTIP_PADDING,
            hover.y() + TOOLTIP_PADDING + hover.textHeight(),
            hover.width() - TOOLTIP_PADDING * 2f,
            com.badlogic.gdx.utils.Align.left,
            true
        );
    }

    private static String createStatusText(
        int shieldCharges,
        int frozenMonsterTurns,
        int protectionTicks,
        int visionBonus,
        boolean radarActive,
        String radarDirection
    ) {
        List<String> states = new ArrayList<>();
        if (shieldCharges > 0) {
            states.add("护盾 x" + shieldCharges);
        }
        if (frozenMonsterTurns > 0) {
            states.add("冻结 " + HoverDescriptions.seconds(frozenMonsterTurns) + " 秒");
        }
        if (protectionTicks > 0) states.add("保护 " + HoverDescriptions.seconds(protectionTicks) + " 秒");
        if (visionBonus > 0) {
            states.add("视野 +" + visionBonus);
        }
        if (radarActive) {
            states.add("雷达 " + radarDirection);
        }
        return states.isEmpty() ? "状态  无" : String.join("   ", states);
    }

    /** 左下角保持怪物首次提示优先于附近遗物；悬停使用独立浮层。 */
    private static InfoContent createInfo(
        MonsterType monsterNoticeType,
        Item nearbyItem
    ) {
        if (monsterNoticeType != null) {
            return new InfoContent(
                monsterDescription(monsterNoticeType),
                MONSTER_ACCENT
            );
        }
        if (nearbyItem != null) {
            return itemInfo(nearbyItem, true);
        }
        return null;
    }

    private static InfoContent itemInfo(Item item, boolean nearby) {
        String prefix = nearby ? "附近有 " : "";
        return new InfoContent(
            prefix + HoverDescriptions.itemDescription(item),
            ITEM_ACCENT
        );
    }

    private static String monsterDescription(MonsterType type) {
        return switch (type) {
            case HUNTER ->
                "发现猎手：会锁定你的位置 两次决策并通过广度优先寻路追踪。";
            case AMBUSHER ->
                "发现伏击者：会预测你移动方向前方最多 3 格。";
            case GUARDIAN ->
                "发现守卫：靠近守护点时会开始追击。";
            case PATROLLER ->
                "发现巡逻者：会在两个远端点之间巡逻。";
            case COWARD ->
                "发现胆小鬼：靠近时逃跑，拉开距离后追击。";
        };
    }

    /** 保存左下角单行日志的文字和颜色。 */
    private record InfoContent(String text, Color accent) {}

    /** 保存已经换算到 UI 坐标系的悬停浮层布局。 */
    private record HoverLayout(
        InfoContent content,
        float x,
        float y,
        float width,
        float height,
        float textHeight
    ) {}
}
