package byow.game.realtime;

import byow.game.*;
import byow.game.input.GameInputProcessor;
import byow.game.monster.*;
import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static byow.game.realtime.EngineFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise actual key-down/up menu routing, not only Engine.handleKey. */
class PauseNavigationTest {
    @TempDir Path directory;

    private void key(GameInputProcessor input, int code) {
        input.keyDown(code);
        input.keyUp(code);
    }

    @Test void saveLeavesPausedThenQuitReturnsDirectlyToMenuAndLoadStaysPaused() {
        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", directory.toString());
            Engine e = open();
            unlockExit(e);
            GameInputProcessor input = new GameInputProcessor(e);
            input.keyDown(Input.Keys.D);
            ticks(e, 7);
            key(input, Input.Keys.P);
            Motion paused = e.player().motion;
            key(input, Input.Keys.S);
            assertEquals(GameState.SAVE, e.state());
            e.update(600);
            assertEquals(paused, e.player().motion);
            key(input, Input.Keys.NUM_1);
            assertEquals(GameState.PAUSE, e.state());
            assertTrue(Files.exists(directory.resolve("save/slot1/save.json")));
            key(input, Input.Keys.Q);
            assertEquals(GameState.MENU, e.state());
            key(input, Input.Keys.L); key(input, Input.Keys.NUM_1);
            assertEquals(GameState.PAUSE, e.state());
            assertEquals(paused, e.player().motion);
            key(input, Input.Keys.P);
            input.keyDown(Input.Keys.D); // The held pre-pause key must not restart movement.
            ticks(e, 60);
            assertEquals(31, e.player().x());
            assertNull(e.player().motion);
            input.keyUp(Input.Keys.D);
        } finally { System.setProperty("user.dir", previous); }
    }

    @Test void cancelSaveAndQuitReturnToPauseWithoutAdvancingAnyGameplayTimer() {
        Engine e = open();
        var m = new Monster(1, 25, 20, MonsterType.HUNTER);
        m.activity.restTicks = 40; m.activity.attackTicks = 25; m.activity.waitTicks = 10;
        EngineFixture.<Monster>list(e, "monsters").add(m);
        set(e, "protectionTicks", 30); set(e, "freezeTicks", 80);
        set(e, "hasUnsavedChanges", true);
        GameInputProcessor input = new GameInputProcessor(e);
        key(input, Input.Keys.P);
        key(input, Input.Keys.S); e.update(600);
        key(input, Input.Keys.B);
        assertEquals(GameState.PAUSE, e.state());
        key(input, Input.Keys.Q);
        assertEquals(GameState.CONFIRM_QUIT, e.state());
        e.update(600);
        key(input, Input.Keys.B);
        assertEquals(GameState.PAUSE, e.state());
        assertEquals(40, m.activity.restTicks); assertEquals(25, m.activity.attackTicks);
        assertEquals(10, m.activity.waitTicks); assertEquals(30, e.protectionTicks());
        assertEquals(80, e.freezeTicks());
        key(input, Input.Keys.Q); key(input, Input.Keys.N);
        assertEquals(GameState.MENU, e.state()); assertFalse(e.shouldQuit());
    }

    @Test void saveAndReturnChoiceGoesToMenuOnlyAfterChoosingSaveSlot() {
        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", directory.toString());
            Engine e = open();
            unlockExit(e);
            set(e, "hasUnsavedChanges", true);
            GameInputProcessor input = new GameInputProcessor(e);
            key(input, Input.Keys.P); key(input, Input.Keys.Q); key(input, Input.Keys.Y);
            assertEquals(GameState.SAVE, e.state());
            key(input, Input.Keys.B);
            assertEquals(GameState.PAUSE, e.state());
            key(input, Input.Keys.Q); key(input, Input.Keys.Y);
            key(input, Input.Keys.NUM_2);
            assertEquals(GameState.MENU, e.state());
            assertTrue(Files.exists(directory.resolve("save/slot2/save.json")));
            assertFalse(e.shouldQuit());
        } finally { System.setProperty("user.dir", previous); }
    }
}
