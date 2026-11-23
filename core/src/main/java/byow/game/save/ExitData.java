package byow.game.save;

import byow.game.GameExit;

/** 存档中的出口坐标和激活状态。 */
public record ExitData(int x, int y, boolean active, int width) {
    public ExitData {
        // Older saves omit width; Gson restores the missing int as zero.
        if (width == 0) width = 1;
        if (width != 1 && width != 2) throw new IllegalArgumentException("Invalid exit width");
    }

    public ExitData(int x, int y, boolean active) { this(x, y, active, 1); }
    public static ExitData from(GameExit exit) {
        return new ExitData(exit.x(), exit.y(), exit.active(), exit.width());
    }

    public GameExit toExit() {
        return new GameExit(x, y, active, width);
    }
}
