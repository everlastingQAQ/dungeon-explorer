package byow.game.worldGenerator;

import java.util.List;

/** Resolved visual layout: stored in saves, never rerolled during rendering. */
public record RoomDesign(Room bounds, RoomTheme theme, int variant, List<Decoration> decorations,
                         List<Furniture> furniture) {
    public RoomDesign {
        decorations = decorations == null ? List.of() : List.copyOf(decorations);
        furniture = furniture == null ? List.of() : List.copyOf(furniture);
    }

    public RoomDesign(Room bounds, RoomTheme theme, int variant, List<Decoration> decorations) {
        this(bounds, theme, variant, decorations, List.of());
    }

    public Furniture furnitureAt(int x, int y) {
        for (Furniture prop : furniture) if (prop.contains(x, y)) return prop;
        return null;
    }

    public boolean contains(int x, int y) {
        return x >= bounds.x() && x < bounds.x() + bounds.width()
            && y >= bounds.y() && y < bounds.y() + bounds.height();
    }

    public Decoration decorationAt(int x, int y) {
        for (Decoration decoration : decorations) {
            if (decoration.x() == x && decoration.y() == y) return decoration;
        }
        return null;
    }

    public enum Mark {
        WEAR("磨损石板"), CRACK("裂纹石板"), RUBBLE("散落碎石"),
        MOSS("墙脚苔痕"), STAIN("石板污渍"), STRIPE("廊道饰带"), EMBLEM("装饰纹章"),
        // PAPER and CLOTH are read-only legacy save identifiers.
        PAPER("散落纸页"), CLOTH("旧地垫"), SHARDS("散落陶片"), INLAY("镶边石板");
        private final String displayName;
        Mark(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
    }

    public record Decoration(int x, int y, Mark mark, int variant, int part) {
        public Decoration(int x, int y, Mark mark, int variant) { this(x, y, mark, variant, -1); }
    }
}
