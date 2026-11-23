package byow.game.render.world;

import byow.game.tile.TETile;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

import java.util.HashMap;
import java.util.Map;

/** Loads and maps the 16x16 pixel-art used by the world renderer. */
final class WorldSprites implements Disposable {
    private static final String SPRITE_ROOT = "sprites/dungeon-ii/";
    private static final String TERRAIN_ATLAS = "tilesets/rgs-cc0/tileset-gray.png";

    private final Map<String, SpriteFrame> frames = new HashMap<>();
    private final Map<String, Texture> textures = new HashMap<>();
    private final SpriteFrame[] walls = new SpriteFrame[256];

    WorldSprites() {
        loadBundledSprites();
        loadTerrain();
        loadRelics();
    }

    SpriteFrame get(TETile tile) {
        return frames.get(tile.description());
    }

    /** Selects a direction-aware wall tile for terrain rendering. */
    SpriteFrame getTerrain(TETile[][] world, int x, int y) {
        TETile tile = world[x][y];
        if (!WallAppearance.isWall(tile)) {
            return get(tile);
        }

        return walls[WallAppearance.mask(world, x, y)];
    }

    private void loadBundledSprites() {
        putFile("avatar", "player.png");
        putFile("猎手", "hunter.png");
        putFile("伏击者", "ambusher.png");
        putFile("守卫", "guardian.png");
        putFile("巡逻者", "patroller.png");
        putFile("胆小鬼", "coward.png");

        putFile("尖刺陷阱", "spike-trap.png");
        putFile("治愈泉", "healing-fountain.png");
        putFile("传送门", "portal.png");
        putFile("祝福宝箱", "blessing-chest.png");
    }

    private void loadTerrain() {
        Texture terrain = loadTexture(TERRAIN_ATLAS);

        // Only interior floor details belong here. The framed floor cells
        // (1, 2) and (1, 3) are not wall corners or plain floor variants.
        putRegion("floor", terrain, 1, 1);
        putRegion("grass", terrain, 2, 1);
        putRegion("flower", terrain, 2, 2);
        putRegion("sand", terrain, 1, 1);
        putRegion("moss", terrain, 4, 2);
        putRegion("cracked floor", terrain, 2, 2);
        putRegion("mud", terrain, 4, 2);
        putRegion("pebbles", terrain, 5, 2);
        putRegion("worn floor", terrain, 2, 1);
        putRegion("stained floor", terrain, 4, 2);
        putRegion("chipped floor", terrain, 2, 1);

        loadWalls();
        putFile("locked door", "door-closed.png");
        putFile("unlocked door", "door-open.png");
    }

    private void loadRelics() {
        Texture items = loadTexture(SPRITE_ROOT + "items.png");
        putRegion("水晶", items, 19, 20);
        putRegion("金币", items, 18, 20);
        putRegion("钥匙", items, 21, 20);
        putRegion("宝珠", items, 21, 19);
        putRegion("符文", items, 24, 17);
    }

    private void putFile(String description, String filename) {
        Texture texture = loadTexture(SPRITE_ROOT + filename);
        frames.put(description, new SpriteFrame(
            new TextureRegion(texture),
            texture.getWidth(),
            texture.getHeight()
        ));
    }

    private void putRegion(String description, Texture texture, int column, int row) {
        frames.put(description, region(texture, column, row));
    }

    private SpriteFrame region(Texture texture, int column, int row) {
        return new SpriteFrame(
            new TextureRegion(texture, column * 16, row * 16, 16, 16),
            16,
            16
        );
    }

    private void loadWalls() {
        Pixmap pixels = new Pixmap(256, 256, Pixmap.Format.RGBA8888);
        Texture atlas;
        try {
            for (int mask = 0; mask < walls.length; mask++) {
                int ox = (mask % 16) * 16;
                int oy = (mask / 16) * 16;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        pixels.drawPixel(ox + x, oy + y, WallAppearance.pixel(mask, x, y));
                    }
                }
            }
            atlas = new Texture(pixels);
        } finally {
            pixels.dispose();
        }
        atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        textures.put("generated-wall-atlas", atlas);
        for (int mask = 0; mask < walls.length; mask++) {
            walls[mask] = region(atlas, mask % 16, mask / 16);
        }
    }

    private Texture loadTexture(String path) {
        return textures.computeIfAbsent(path, ignored -> {
            Texture texture = new Texture(Gdx.files.internal(path));
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            return texture;
        });
    }

    @Override
    public void dispose() {
        for (Texture texture : textures.values()) {
            texture.dispose();
        }
        textures.clear();
        frames.clear();
    }

    record SpriteFrame(TextureRegion region, int pixelWidth, int pixelHeight) {
        float aspectRatio() {
            return pixelHeight / (float) pixelWidth;
        }
    }
}
