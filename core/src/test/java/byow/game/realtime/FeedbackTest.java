package byow.game.realtime;

import byow.game.*;
import byow.game.feedback.GameFeedback;
import byow.game.item.*;
import byow.game.monster.*;
import byow.game.scene.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static byow.game.realtime.EngineFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackTest {
    @TempDir Path directory;

    private Monster adjacent(Engine e, long id, int x) {
        var m = new Monster(id, x, 20, MonsterType.HUNTER);
        EngineFixture.<Monster>list(e, "monsters").add(m);
        return m;
    }

    @Test void damageAndShieldAreDistinctAndProtectedContactDoesNotReplayImpact() {
        Engine e = open();
        adjacent(e, 1, 29); adjacent(e, 2, 31);
        ticks(e, 1);
        assertEquals(2, e.player().currentHp());
        assertEquals(GameFeedback.Impact.DAMAGE, e.feedback().impact());
        assertEquals("-1 生命", e.feedback().impactText());
        ticks(e, 1); assertEquals(47, e.feedback().impactTicks());

        Engine shielded = open(); shielded.player().grantShield(2);
        adjacent(shielded, 1, 29); adjacent(shielded, 2, 31);
        ticks(shielded, 1);
        assertEquals(3, shielded.player().currentHp());
        assertEquals(1, shielded.player().shieldCharges());
        assertEquals(GameFeedback.Impact.SHIELD, shielded.feedback().impact());
        assertEquals("护盾抵挡", shielded.feedback().impactText());
        // An attacker whose cooldown allows a fresh attempt still cannot
        // create another hit effect during global contact protection.
        shielded.monsters().getFirst().activity.attackTicks = 0;
        shielded.monsters().getFirst().activity.waitTicks = 0;
        ticks(shielded, 1);
        assertEquals(47, shielded.feedback().impactTicks());
        assertEquals(1, shielded.player().shieldCharges());
    }

    @Test void harmlessRestAndFreezeSwapDoNotClaimDamageOrShieldUse() {
        for (boolean frozen : new boolean[] {false, true}) {
            Engine e = open(); var m = adjacent(e, 1, 31);
            e.player().grantShield(1);
            if (frozen) set(e, "freezeTicks", 120); else m.activity.restTicks = 48;
            tap(e, 'D');
            assertNull(e.feedback().impact());
            assertEquals(1, e.player().shieldCharges());
        }
    }

    @Test void trapFeedbackOnlyOnArrivalAndDoesNotConsumeShield() {
        Engine e = open(); e.player().grantShield(1);
        EngineFixture.<SceneObject>list(e, "sceneObjects").add(new SceneObject(31, 20, SceneObjectType.SPIKE_TRAP));
        set(e, "protectionTicks", 48);
        e.pressDirection('D'); e.releaseDirection('D'); ticks(e, 12);
        assertNull(e.feedback().impact());
        ticks(e, 1);
        assertEquals("尖刺 -1 生命", e.feedback().impactText());
        assertEquals(1, e.player().shieldCharges());
        ticks(e, 48); assertNull(e.feedback().impact());
        assertEquals(2, e.player().currentHp());
    }

    @Test void thirdRelicShowsPickupAndExitOnceAndFourthShowsActualBuff() {
        Engine e = open(); set(e, "collectedCount", 2);
        e.items().add(new Item(31, 20, ItemType.CRYSTAL));
        tap(e, 'D');
        assertTrue(e.exit().active());
        assertEquals(2, e.feedback().notices().size());
        String pickup = e.feedback().notices().stream()
            .filter(n -> n.channel() == GameFeedback.Channel.PICKUP).findFirst().orElseThrow().text();
        assertTrue(pickup.contains("水晶")); assertTrue(pickup.contains("3/3"));
        assertTrue(pickup.contains("激活猎手")); assertFalse(pickup.contains("视野 +"));
        e.items().add(new Item(32, 20, ItemType.ORB));
        tap(e, 'D');
        assertEquals(1, e.player().shieldCharges());
        assertEquals(2, e.feedback().notices().size());
        assertTrue(e.feedback().notices().stream().anyMatch(n -> n.text().contains("护盾 +1")));
        assertEquals(287, e.feedback().notices().stream()
            .filter(n -> n.channel() == GameFeedback.Channel.EXIT).findFirst().orElseThrow().ticks());
    }

    @Test void healingReportsActualResultIncludingFullHealth() {
        Engine e = open(); set(e, "collectedCount", 3);
        e.items().add(new Item(31, 20, ItemType.COIN)); tap(e, 'D');
        assertTrue(e.feedback().notices().stream().anyMatch(n -> n.text().contains("生命已满")));
        Engine wounded = open(); set(wounded, "collectedCount", 3);
        wounded.player().takeDamage(1);
        wounded.items().add(new Item(31, 20, ItemType.COIN)); tap(wounded, 'D');
        assertTrue(wounded.feedback().notices().stream().anyMatch(n -> n.text().contains("恢复 1 点生命")));
    }

    @Test void transientFeedbackPausesExpiresAndDoesNotMakeAnIdleWorldDirty() {
        Engine e = open();
        e.feedback().impact(GameFeedback.Impact.SHIELD, "护盾抵挡");
        e.feedback().notice(GameFeedback.Channel.EFFECT, "怪物冻结 2 秒");
        set(e, "hasUnsavedChanges", false);
        keys(e, "PS");
        var before = e.feedback().notices();
        e.update(600);
        assertEquals(48, e.feedback().impactTicks()); assertEquals(before, e.feedback().notices());
        keys(e, "BP"); ticks(e, 240);
        assertNull(e.feedback().impact()); assertTrue(e.feedback().notices().isEmpty());
        keys(e, "PQ"); assertEquals(GameState.MENU, e.state());
    }

    @Test void savingAndLoadingDoNotReplayOldHitsOrPickupMessages() throws Exception {
        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", directory.toString());
            Engine e = open(); adjacent(e, 1, 31); ticks(e, 1);
            e.feedback().notice(GameFeedback.Channel.PICKUP, "拾取水晶");
            keys(e, "PS1");
            String json = Files.readString(directory.resolve("save/slot1/save.json"));
            assertFalse(json.contains("impactText")); assertFalse(json.contains("拾取水晶"));
            keys(e, "QL1");
            assertEquals(GameState.PAUSE, e.state());
            assertNull(e.feedback().impact()); assertTrue(e.feedback().notices().isEmpty());
            assertEquals(48, e.protectionTicks());
            assertEquals(json, Files.readString(directory.resolve("save/slot1/save.json")));
        } finally { System.setProperty("user.dir", previous); }
    }

    @Test void newGameClearsFeedbackAndChannelsRemainBounded() {
        Engine e = open();
        for (int i = 0; i < 20; i++) e.feedback().notice(GameFeedback.Channel.PICKUP, "拾取" + i);
        assertEquals(1, e.feedback().notices().size());
        e.feedback().impact(GameFeedback.Impact.DAMAGE, "-1 生命");
        set(e, "state", GameState.MENU); keys(e, "N42S");
        assertNull(e.feedback().impact()); assertTrue(e.feedback().notices().isEmpty());
    }
}
