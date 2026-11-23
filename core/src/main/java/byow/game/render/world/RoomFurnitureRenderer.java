package byow.game.render.world;

import byow.game.tile.TETile;
import byow.game.worldGenerator.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.utils.Disposable;
import java.util.List;

/** Original RGS CC0 pixels only. Box halves retain their source orientation and scale. */
public final class RoomFurnitureRenderer implements Disposable {
    private final Texture atlas;
    private final TextureRegion[] box;
    private final TextureRegion pot;
    private final TextureRegion shards;
    private final TextureRegion[] inlay = new TextureRegion[9];

    public RoomFurnitureRenderer() {
        atlas = new Texture(Gdx.files.internal("tilesets/rgs-cc0/tileset-gray.png"));
        atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        // Complete box: (112,0,32,16), including the lid in its second cell.
        box = new TextureRegion[] {tile(7, 0), tile(8, 0)};
        pot = tile(14, 0);
        shards = tile(15, 0);
        for (int part = 0; part < 9; part++) inlay[part] = tile(10 + part % 3, 2 - part / 3);
    }

    public TextureRegion decoration(RoomDesign.Mark mark, int variant) {
        return mark == RoomDesign.Mark.SHARDS ? shards : null;
    }

    public TextureRegion decoration(RoomDesign.Decoration mark) {
        if (mark.mark() == RoomDesign.Mark.INLAY && mark.part() >= 0 && mark.part() < 9)
            return inlay[mark.part()];
        return decoration(mark.mark(), mark.variant());
    }

    public void draw(SpriteBatch batch, TETile[][] world, List<RoomDesign> rooms, float size, boolean[][] visible) {
        batch.setColor(Color.WHITE);
        for (RoomDesign room : rooms) for (Furniture prop : room.furniture()) {
            if (!prop.type().active() || prop.type() == FurnitureType.ABANDONED_BOX
                && prop.orientation() != Furniture.Orientation.HORIZONTAL) continue;
            for (int part = 0; part < prop.type().cells(); part++) {
                int x = prop.x() + part, y = prop.y();
                if (x < 0 || y < 0 || x >= world.length || y >= world[x].length
                    || !world[x][y].equals(prop.type().tile()) || visible != null && !visible[x][y]) continue;
                batch.draw(prop.type() == FurnitureType.POT ? pot : box[part],
                    x * size, y * size, size, size);
            }
        }
    }

    private TextureRegion tile(int column, int row) {
        return new TextureRegion(atlas, column * 16, row * 16, 16, 16);
    }

    @Override public void dispose() { atlas.dispose(); }
}
