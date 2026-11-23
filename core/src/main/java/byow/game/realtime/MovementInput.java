package byow.game.realtime;

import java.util.LinkedHashSet;

/** Retains a tap across ticks and at most one pending change of direction. */
public final class MovementInput {
    private final LinkedHashSet<Character> held = new LinkedHashSet<>();
    private Character pending;
    public static boolean isDirection(char key) { return "WASD".indexOf(key) >= 0; }
    public void press(char key) {
        if (isDirection(key) && held.add(key)) pending = key;
    }
    public void release(char key) { held.remove(key); }
    public Character consumePending() { Character result = pending; pending = null; return result; }
    public Character preferred() {
        Character last = null;
        for (char key : held) last = key;
        return last;
    }
    public boolean held(char key) { return held.contains(key); }
    public void clear() { held.clear(); pending = null; }
}
