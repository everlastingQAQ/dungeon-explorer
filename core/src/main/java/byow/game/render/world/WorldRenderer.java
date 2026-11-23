package byow.game.render.world;

import byow.game.GameConfig;
import byow.game.feedback.GameFeedback;
import byow.game.realtime.Timing;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import byow.game.GameExit;
import byow.game.door.DoorLayout;
import byow.game.item.Item;
import byow.game.monster.Monster;
import byow.game.monster.MonsterAppearance;
import byow.game.player.Player;
import byow.game.render.FontManager;
import byow.game.scene.SceneObject;
import byow.game.scene.SceneObjectAppearance;
import byow.game.item.Itemset;
import byow.game.tile.TETile;
import byow.game.tile.Tileset;
import byow.game.worldGenerator.RoomDesign;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import java.util.*;

import static byow.game.GameConfig.HUD_HEIGHT;

/**
 * 负责渲染一个世界
 * 1. 属性
 * - SpriteBatch 字符渲染器
 * - ShapeRenderer 背景渲染器
 * - TileRenderer 板块渲染器
 * 2. 行为
 * - render(TETile[][] world) 渲染世界
 */
public class WorldRenderer {

    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final TileRenderer tileRenderer;
    private final RoomDecorationRenderer roomRenderer = new RoomDecorationRenderer();

    private final OrthographicCamera camera;
    private final float tileSize;
    private final GlyphLayout statusLayout = new GlyphLayout();
    private final com.badlogic.gdx.graphics.g2d.BitmapFont statusFont;

    /**
     * 创建 WorldRenderer。
     *
     * @param shapeRenderer 用于绘制 tile 背景
     * @param batch 用于绘制 tile 字符
     * @param fonts 游戏使用的字体管理器
     * @param tileSize 一个 tile 的世界尺寸
     */
    public WorldRenderer(ShapeRenderer shapeRenderer,
                         SpriteBatch batch,
                         FontManager fonts,
                         float tileSize) {
        this.shapeRenderer = shapeRenderer;
        this.batch = batch;

        this.camera = new OrthographicCamera();

        this.tileSize = tileSize;
        this.statusFont = fonts.message();

        // 初始化 camera 能看到多大的世界区域。
        resize(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT);

        this.tileRenderer = new TileRenderer(batch, fonts.tile(), shapeRenderer, tileSize);
    }

    /**
     * 渲染世界
     * @param world 当前世界
     * @param player 当前玩家
     */
    public void render(
        TETile[][] world,
        Player player,
        List<Monster> monsters,
        List<Item> items,
        List<SceneObject> sceneObjects,
        GameExit exit,
        boolean[][] visible,
        List<RoomDesign> roomDesigns
    ) {

        render(world, player, monsters, items, sceneObjects, exit, visible, roomDesigns, null, 0, 0);
    }

    public void render(TETile[][] world, Player player, List<Monster> monsters, List<Item> items,
                       List<SceneObject> sceneObjects, GameExit exit, boolean[][] visible,
                       List<RoomDesign> roomDesigns, GameFeedback feedback, int protectionTicks, int freezeTicks) {
        // 镜头跟随玩家
        updateCamera(world, player);

        // 先画背景
        // 背景跟着 camera.combined 移动
        shapeRenderer.setProjectionMatrix(camera.combined);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int x = 0; x < world.length; x++) {
            for (int y = 0; y < world[0].length; y++) {
                tileRenderer.drawBackground(world[x][y], x, y);
            }
        }
        shapeRenderer.end();

        // 再画字符
        // 字符跟着 camera 移动，
        batch.setProjectionMatrix(camera.combined);

        batch.begin();
        for (int x = 0; x < world.length; x++) {
            for (int y = 0; y < world[0].length; y++) {
                // Grouped doors are drawn once, above the floor decoration layer.
                if (!DoorLayout.isDoor(world[x][y]) && (exit == null || !exit.contains(x, y)))
                    tileRenderer.drawTerrain(world, x, y);
            }
        }

        Set<Long> occupied = new HashSet<>();
        for (SceneObject object : sceneObjects) if (object.active())
            occupied.add(RoomDecorationRenderer.key(object.x(), object.y()));
        for (Item item : items) occupied.add(RoomDecorationRenderer.key(item.x(), item.y()));
        if (exit != null) for (int dx = 0; dx < exit.width(); dx++)
            occupied.add(RoomDecorationRenderer.key(exit.x() + dx, exit.y()));
        roomRenderer.draw(batch, world, roomDesigns, tileSize, occupied, null);

        // 出口是独立的玩法对象：即使尚未收集足够遗物，也要在进入
        // 玩家视野后显式绘制，不能依赖它恰好仍保存在底层地块中。
        // The existing fog pass clips every covered cell independently, including
        // the upper frame and the case where only the right half is visible.
        for (var door : DoorLayout.collect(world, roomDesigns, exit)) tileRenderer.drawDoor(door);

        // 画功能性场景物件；被使用的一次性物件不再显示。
        for (SceneObject object : sceneObjects) {
            if (object.active() && visible[object.x()][object.y()]) {
                tileRenderer.drawCharacter(
                    SceneObjectAppearance.get(object.type()),
                    object.x(),
                    object.y()
                );
            }
        }

        // 画物品的字符
        for (Item item : items) {
            if (!visible[item.x()][item.y()]) {
                continue;
            }

            TETile appearance = Itemset.get(item.type());
            tileRenderer.drawCharacter(appearance, item.x(), item.y());
        }

        // 画所有当前可见的怪物
        for (Monster monster : monsters) {
            if (visible[monster.x()][monster.y()]) {
                TETile appearance = MonsterAppearance.get(monster.type());
                tileRenderer.drawCharacter(appearance, monster.renderX(), monster.renderY(),
                    freezeTicks > 0 ? FROZEN_COLOR : Color.WHITE);
            }
        }

        // A hit takes priority over the gentler protection pulse; never hide the actor.
        Color playerTint = protectionTicks > 0 && (protectionTicks / 6) % 2 == 0 ? PROTECTED_COLOR : Color.WHITE;
        if (feedback != null && feedback.impactTicks() > 30)
            playerTint = impactColor(feedback.impact());
        tileRenderer.drawCharacter(Tileset.AVATAR, player.renderX(), player.renderY(), playerTint);

        float oldScaleX = statusFont.getData().scaleX, oldScaleY = statusFont.getData().scaleY;
        statusFont.getData().setScale(.5f);
        Color oldColor = new Color(statusFont.getColor());
        for (Monster m : monsters) if ((freezeTicks > 0 || m.activity.restTicks > 0) && visible[m.x()][m.y()]) {
            // One tag at a time: freezing takes priority; resting resumes unchanged afterwards.
            label(freezeTicks > 0 ? "冻结" : "喘息",
                freezeTicks > 0 ? FROZEN_COLOR : REST_COLOR,
                (m.renderX() + .5f) * tileSize,
                (m.renderY() + tileRenderer.characterHeight(MonsterAppearance.get(m.type())) + .9f) * tileSize);
        }
        if (feedback != null && feedback.impactTicks() > 0) {
            float rise = (48 - feedback.impactTicks()) / 96f;
            label(feedback.impactText(), impactColor(feedback.impact()), (player.renderX() + .5f) * tileSize,
                (player.renderY() + tileRenderer.characterHeight(Tileset.AVATAR) + .65f + rise) * tileSize);
        }
        statusFont.getData().setScale(oldScaleX, oldScaleY);
        statusFont.setColor(oldColor);
        batch.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Monster m : monsters) if ((freezeTicks > 0 || m.activity.restTicks > 0) && visible[m.x()][m.y()]) {
            float px = m.renderX() * tileSize, py = (m.renderY()
                + tileRenderer.characterHeight(MonsterAppearance.get(m.type())) + .1f) * tileSize;
            shapeRenderer.setColor(com.badlogic.gdx.graphics.Color.DARK_GRAY);
            shapeRenderer.rect(px, py, tileSize, 2);
            shapeRenderer.setColor(freezeTicks > 0 ? FROZEN_COLOR : REST_COLOR);
            float progress = freezeTicks > 0 ? freezeTicks / (float) Timing.FREEZE
                : m.activity.restTicks / (float) Timing.pace(m.type()).rest();
            shapeRenderer.rect(px, py, tileSize * MathUtils.clamp(progress, 0, 1), 2);
        }
        // An active exit has a gold threshold, but only in cells currently visible.
        if (exit != null && exit.active()) {
            shapeRenderer.setColor(EXIT_COLOR);
            for (int dx = 0; dx < exit.width(); dx++) if (visible[exit.x() + dx][exit.y()])
                shapeRenderer.rect((exit.x() + dx) * tileSize + 1, exit.y() * tileSize + 1, tileSize - 2, 2);
        }
        shapeRenderer.end();
    }

    private static final Color DAMAGE_COLOR = new Color(1f, .32f, .32f, 1f);
    private static final Color SHIELD_COLOR = new Color(.5f, .82f, 1f, 1f);
    private static final Color HEAL_COLOR = new Color(.5f, 1f, .68f, 1f);
    private static final Color PROTECTED_COLOR = new Color(.72f, .83f, 1f, 1f);
    private static final Color FROZEN_COLOR = new Color(.58f, .82f, 1f, 1f);
    private static final Color REST_COLOR = new Color(.48f, .9f, .7f, 1f);
    private static final Color EXIT_COLOR = new Color(1f, .82f, .4f, 1f);

    private Color impactColor(GameFeedback.Impact impact) {
        if (impact == null) return Color.WHITE;
        return switch (impact) {
            case DAMAGE -> DAMAGE_COLOR;
            case SHIELD -> SHIELD_COLOR;
            case HEAL -> HEAL_COLOR;
        };
    }

    private void label(String text, Color color, float center, float y) {
        statusLayout.setText(statusFont, text);
        float x = center - statusLayout.width / 2;
        statusFont.setColor(Color.BLACK);
        statusFont.draw(batch, text, x + .7f, y - .7f);
        statusFont.setColor(color);
        statusFont.draw(batch, text, x, y);
    }

    /**
     * 设置 Camera 可视范围
     * @param width 窗口尺寸
     * @param height 窗口尺寸
     */
    public void resize(int width, int height) {
        float aspectRatio = (float)width / (float) height;

        // 设置 camera 纵向能够看到多少世界单位。
        camera.viewportHeight = GameConfig.CAMERA_VISIBLE_TILES_Y * tileSize;

        // 根据窗口宽高比计算横向视野。
        camera.viewportWidth = camera.viewportHeight * aspectRatio;

        // 更新 camera
        camera.update();
    }

    /**
     * 把鼠标屏幕坐标转换为当前可见的世界格。
     * 顶部 HUD 和迷雾中的格子不会触发悬停。
     */
    public GridPosition tileAtScreen(
        boolean[][] visible,
        int screenX,
        int screenY
    ) {
        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();
        int worldViewportHeight = screenHeight - HUD_HEIGHT;

        if (screenX < 0 || screenX >= screenWidth
            || screenY < HUD_HEIGHT || screenY >= screenHeight) {
            return null;
        }

        float mouseYFromBottom = screenHeight - screenY;
        float projectedTileSize = worldViewportHeight
            / (float) GameConfig.CAMERA_VISIBLE_TILES_Y;
        float halfTile = projectedTileSize / 2f;
        Vector3 tileCenter = new Vector3();

        // 只投影当前可见格。与实际渲染使用同一个 camera，可以避开
        // Wayland/高 DPI 环境中输入坐标反投影不一致的问题。
        for (int x = 0; x < visible.length; x++) {
            for (int y = 0; y < visible[0].length; y++) {
                if (!visible[x][y]) {
                    continue;
                }
                tileCenter.set(
                    (x + 0.5f) * tileSize,
                    (y + 0.5f) * tileSize,
                    0f
                );
                camera.project(
                    tileCenter,
                    0,
                    0,
                    screenWidth,
                    worldViewportHeight
                );
                if (Math.abs(screenX - tileCenter.x) <= halfTile
                    && Math.abs(mouseYFromBottom - tileCenter.y) <= halfTile) {
                    return new GridPosition(x, y);
                }
            }
        }
        return null;
    }

    /** Match drawn sprite bounds before looking up the underlying tile. */
    public String actorAtScreen(byow.game.Engine engine, int sx, int sy) {
        // Reject fog-covered fragments of a sprite as well as the HUD.
        if (tileAtScreen(engine.visible(), sx, sy) == null) return null;
        var p = engine.player();
        if (hit(p.renderX(), p.renderY(), tileRenderer.characterHeight(Tileset.AVATAR), sx, sy))
            return "玩家：当前生命 " + p.currentHp() + " / " + p.maxHp() + "。";
        for (Monster m : engine.monsters()) {
            if (engine.visible()[m.x()][m.y()] && hit(m.renderX(), m.renderY(),
                tileRenderer.characterHeight(MonsterAppearance.get(m.type())), sx, sy))
                return byow.game.render.ui.HoverDescriptions.monsterDescription(engine, m);
        }
        return null;
    }
    private boolean hit(float x, float y, float height, int sx, int sy) {
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        Vector3 bottom = camera.project(new Vector3(x * tileSize, y * tileSize, 0), 0, 0, w, h - HUD_HEIGHT);
        Vector3 top = camera.project(new Vector3((x + 1) * tileSize, (y + height) * tileSize, 0), 0, 0, w, h - HUD_HEIGHT);
        return sx >= bottom.x && sx <= top.x && h - sy >= bottom.y && h - sy <= top.y;
    }

    public record GridPosition(int x, int y) {}

    /** Releases pixel-art textures loaded for world rendering. */
    public void dispose() {
        tileRenderer.dispose();
        roomRenderer.dispose();
    }

    /**
     * 根据玩家当前位置更新 camera 视觉中心
     */
    private void updateCamera(TETile[][] world, Player player) {
        // 计算player所在中心位置
        float playerCenterX = (player.renderX() + 0.5f) * tileSize;
        float playerCenterY = (player.renderY() + 0.5f) * tileSize;

        // 计算camera半宽
        float halfWidth = camera.viewportWidth / 2;
        float halfHeight = camera.viewportHeight / 2;

        // 计算camera范围
        float worldWidth = world.length * tileSize;
        float worldHeight = world[0].length * tileSize;

        // 限定camera真正的位置
        float centerX = MathUtils.clamp(playerCenterX,halfWidth, worldWidth - halfWidth);
        float centerY = MathUtils.clamp(playerCenterY, halfHeight, worldHeight - halfHeight);

        // 把 camera 的中心位置设置为玩家中心位置。
        camera.position.set(centerX, centerY, 0);

        // 更新 camera
        camera.update();
    }
}
