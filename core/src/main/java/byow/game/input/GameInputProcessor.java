package byow.game.input;

import byow.game.Engine;
import byow.game.GameState;
import byow.game.realtime.MovementInput;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import java.util.HashSet;
import java.util.Set;

public class GameInputProcessor extends InputAdapter {
    private final Engine engine;
    private final Set<Integer> down = new HashSet<>();
    public GameInputProcessor(Engine engine) { this.engine = engine; }

    @Override public boolean keyDown(int code) {
        if (!down.add(code)) return true;
        String name = Input.Keys.toString(code);
        if (name.length() != 1) return false;
        char key = Character.toUpperCase(name.charAt(0));
        if (engine.state() == GameState.PLAYING && MovementInput.isDirection(key))
            engine.pressDirection(key);
        else if (!(engine.state() == GameState.SEED && Character.isDigit(key)))
            engine.handleKey(key);
        return true;
    }
    @Override public boolean keyUp(int code) {
        down.remove(code);
        String name = Input.Keys.toString(code);
        if (name.length() == 1) engine.releaseDirection(Character.toUpperCase(name.charAt(0)));
        return true;
    }
    @Override public boolean keyTyped(char key) {
        if (engine.state() == GameState.SEED && Character.isDigit(key)) engine.handleKey(key);
        return true;
    }
    public void clear() { down.clear(); }
}
