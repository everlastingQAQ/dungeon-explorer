package byow.game.realtime;

/** Orthogonal to AI state; serialized only while gameplay is paused. */
public class MonsterActivity {
    public Motion motion;
    public int waitTicks;
    public int attackTicks;
    public int walked;
    public int restTicks;
}
