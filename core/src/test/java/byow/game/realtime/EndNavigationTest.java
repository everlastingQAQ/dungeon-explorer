package byow.game.realtime;

import byow.game.GameState;
import byow.game.input.GameInputProcessor;
import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import static byow.game.realtime.EngineFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class EndNavigationTest {
    @Test void qReturnsToMenuWithoutQuittingAndHeldQCannotQuitAgain() {
        for (GameState end : new GameState[] {GameState.GAME_OVER, GameState.WIN}) {
            var engine = open();
            set(engine, "state", end);
            set(engine, "hasUnsavedChanges", true);
            var input = new GameInputProcessor(engine);
            input.keyDown(Input.Keys.M);
            input.keyUp(Input.Keys.M);
            assertEquals(end, engine.state(), "M is no longer an end-screen shortcut");
            input.keyDown(Input.Keys.Q);
            assertEquals(GameState.MENU, engine.state());
            assertFalse(engine.shouldQuit());
            input.keyDown(Input.Keys.Q);
            input.keyTyped('q');
            assertFalse(engine.shouldQuit(), "Holding Q must not also exit from the menu");
            input.keyUp(Input.Keys.Q);
            engine.update(10);
            assertEquals(GameState.MENU, engine.state());
            assertFalse(engine.shouldQuit());
        }
    }
}
