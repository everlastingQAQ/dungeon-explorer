package byow.game.monster;

import byow.game.monster.ai.*;
import byow.game.pathfinder.Pathfinder;
import byow.game.player.Player;
import byow.game.tile.TETile;
import java.util.*;

/** AI decisions share a logical snapshot; Engine resolves reservations in stable ID order. */
public class MonsterController {
    public record Decision(Monster monster, Pathfinder.Position destination) {}
    public List<Decision> decisions(TETile[][] world, List<Monster> monsters,
                                    Player player, Set<Long> excluded) {
        List<Decision> result = new ArrayList<>();
        for (Monster m : monsters.stream().sorted(Comparator.comparingLong(Monster::id)).toList()) {
            var a = m.activity;
            if (excluded.contains(m.id()) || a.motion != null || a.waitTicks > 0 || a.restTicks > 0) continue;
            MonsterAI ai = switch (m.type()) {
                case HUNTER -> new HunterAI();
                case AMBUSHER -> new AmbusherAI();
                case GUARDIAN -> new GuardianAI();
                case PATROLLER -> new PatrollerAI();
                case COWARD -> new CowardAI();
            };
            result.add(new Decision(m, ai.decideNextStep(world, m, player)));
        }
        return result;
    }
}
