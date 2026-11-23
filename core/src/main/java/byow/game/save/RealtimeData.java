package byow.game.save;

import byow.game.realtime.Motion;
import byow.game.realtime.MonsterActivity;
import java.util.Map;

public record RealtimeData(Motion playerMotion, Map<Long, MonsterActivity> monsters,
                           int protectionTicks, int freezeTicks) {}
