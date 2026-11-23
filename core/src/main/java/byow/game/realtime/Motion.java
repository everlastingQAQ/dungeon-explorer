package byow.game.realtime;

/** The logical origin stays occupied until the destination is committed. */
public record Motion(int fromX, int fromY, int toX, int toY,
                     int duration, int elapsed, boolean swap) {
    public Motion {
        if (duration <= 0 || elapsed < 0 || elapsed > duration
            || Math.abs(toX - fromX) + Math.abs(toY - fromY) != 1)
            throw new IllegalArgumentException("Invalid grid motion");
    }
    public Motion advance() {
        return new Motion(fromX, fromY, toX, toY, duration, Math.min(duration, elapsed + 1), swap);
    }
    public boolean complete() { return elapsed == duration; }
    public float x() { return x(0); }
    public float x(float alpha) { return fromX + (toX - fromX) * Math.min(duration, elapsed + alpha) / duration; }
    public float y() { return y(0); }
    public float y(float alpha) { return fromY + (toY - fromY) * Math.min(duration, elapsed + alpha) / duration; }
    public boolean targets(int x, int y) { return toX == x && toY == y; }
}
