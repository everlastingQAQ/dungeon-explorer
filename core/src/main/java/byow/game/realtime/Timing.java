package byow.game.realtime;

import byow.game.monster.MonsterType;

/** All gameplay durations are integer 60 Hz ticks. */
public final class Timing {
    public static final int HZ = 60, PLAYER_STEP = 12, CONTACT_COOLDOWN = 60,
        CONTACT_PROTECTION = 48, FREEZE = 120, SPAWN_DELAY = 36;
    public record Pace(int step, int stepsBeforeRest, int rest) {}
    public static Pace pace(MonsterType type) {
        return switch (type) {
            case HUNTER -> new Pace(12, 8, 48);
            case AMBUSHER -> new Pace(9, 5, 72);
            case GUARDIAN -> new Pace(18, 10, 60);
            case PATROLLER -> new Pace(15, 13, 36);
            case COWARD -> new Pace(10, 6, 60);
        };
    }
    private Timing() {}
}
