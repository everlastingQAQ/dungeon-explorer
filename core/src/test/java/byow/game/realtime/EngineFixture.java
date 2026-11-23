package byow.game.realtime;

import byow.game.*;
import byow.game.player.Player;
import byow.game.monster.Monster;
import byow.game.random.GameRandom;
import byow.game.tile.*;
import java.util.*;

/** Test-only handcrafted rooms: never touches user saves. */
public final class EngineFixture {
    public static Engine open() {
        TETile[][] world = new TETile[61][41];
        for (int x = 0; x < world.length; x++) for (int y = 0; y < world[0].length; y++)
            world[x][y] = x == 0 || y == 0 || x == 60 || y == 40 ? Tileset.WALL : Tileset.FLOOR;
        return create(world, new Player(world, 30, 20), List.of());
    }
    public static Engine create(TETile[][] world, Player player, List<Monster> monsters) {
        Engine e = new Engine();
        set(e, "world", world); set(e, "player", player);
        set(e, "items", new ArrayList<>()); set(e, "random", new GameRandom(42));
        set(e, "visionRadius", 7); set(e, "explored", new boolean[world.length][world[0].length]);
        set(e, "exit", new GameExit(world.length - 2, world[0].length - 2, false));
        list(e, "monsters").addAll(monsters);
        set(e, "state", GameState.PLAYING);
        e.updateVision();
        return e;
    }
    @SuppressWarnings("unchecked")
    public static <T> List<T> list(Engine e, String name) {
        try { var f = Engine.class.getDeclaredField(name); f.setAccessible(true); return (List<T>) f.get(e); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    public static void set(Engine e, String name, Object value) {
        try { var f = Engine.class.getDeclaredField(name); f.setAccessible(true); f.set(e, value); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    public static void ticks(Engine e, int count) { for (int i = 0; i < count; i++) e.update(1.0 / 60); }
    public static void tap(Engine e, char key) {
        e.pressDirection(key); e.releaseDirection(key); ticks(e, 13);
    }
    public static void keys(Engine e, String keys) { keys.chars().forEach(c -> e.handleKey((char)c)); }
}
