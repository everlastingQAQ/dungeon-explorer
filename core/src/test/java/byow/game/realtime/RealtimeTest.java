package byow.game.realtime;

import byow.game.*;
import byow.game.monster.*;
import byow.game.scene.*;
import byow.game.item.*;
import byow.game.save.*;
import byow.game.tile.*;
import byow.game.render.ui.HoverDescriptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static byow.game.realtime.EngineFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class RealtimeTest {
    @TempDir Path directory;
    private Monster add(Engine e, long id, int x, int y, MonsterType type) {
        Monster m = new Monster(id, x, y, type);
        EngineFixture.<Monster>list(e, "monsters").add(m);
        return m;
    }

    @Test void keyboardRepeatsDontDuplicateCommandsAndTypedLettersDontMove() {
        Engine e = open();
        var input = new byow.game.input.GameInputProcessor(e);
        input.keyDown(com.badlogic.gdx.Input.Keys.D);
        input.keyDown(com.badlogic.gdx.Input.Keys.D);
        input.keyTyped('d');
        input.keyUp(com.badlogic.gdx.Input.Keys.D);
        ticks(e, 13);
        assertEquals(31, e.player().x()); assertNull(e.player().motion);
        input.keyDown(com.badlogic.gdx.Input.Keys.P);
        input.keyDown(com.badlogic.gdx.Input.Keys.P);
        input.keyTyped('p');
        assertEquals(GameState.PAUSE, e.state());
        input.keyUp(com.badlogic.gdx.Input.Keys.P);
        input.keyDown(com.badlogic.gdx.Input.Keys.P);
        assertEquals(GameState.PLAYING, e.state());
        ticks(e, 30); assertEquals(31, e.player().x());
    }

    @Test void oneHundredGeneratedWorldsKeepEveryOriginAndReservationLegal() {
        for (int seed = 0; seed < 100; seed++) {
            Engine e = new Engine(); keys(e, "N" + seed + "S");
            e.player().grantShield(100);
            var random = new byow.game.random.GameRandom(seed);
            int id = 1;
            for (MonsterType type : MonsterType.values()) {
                var monster = MonsterSpawner.spawn(id++, e.world(), random, e.player(),
                    e.monsters(), e.items(), e.sceneObjects(), type);
                EngineFixture.<Monster>list(e, "monsters").add(monster);
            }
            set(e, "nextMonsterId", (long) id);
            for (int tick = 0; tick < 120; tick++) {
                char direction = "WASD".charAt((tick / 15) % 4);
                if (tick % 15 == 0) { e.pressDirection(direction); e.releaseDirection(direction); }
                ticks(e, 1);
                Set<String> origins = new HashSet<>(), targets = new HashSet<>();
                assertTrue(origins.add(e.player().x() + ":" + e.player().y()));
                if (e.player().motion != null) assertTrue(targets.add(
                    e.player().motion.toX() + ":" + e.player().motion.toY()));
                for (Monster m : e.monsters()) {
                    assertTrue(origins.add(m.x() + ":" + m.y()), "overlap seed " + seed);
                    assertTrue(TileRules.isWalkable(e.world()[m.x()][m.y()]));
                    if (m.activity.motion != null) {
                        var motion = m.activity.motion;
                        assertTrue(targets.add(motion.toX() + ":" + motion.toY()), "reservation seed " + seed);
                        assertTrue(TileRules.isWalkable(e.world()[motion.toX()][motion.toY()]));
                    }
                }
            }
        }
    }

    @Test void tapIsRetainedAndOriginStaysOccupiedUntilArrival() {
        Engine e = open();
        e.pressDirection('D'); e.releaseDirection('D');
        ticks(e, 1);
        assertEquals(30, e.player().x());
        assertTrue(e.occupiedOrReserved(30, 20));
        assertTrue(e.occupiedOrReserved(31, 20));
        ticks(e, 6);
        assertEquals(30.5f, e.player().renderX());
        assertEquals(30, e.player().x());
        ticks(e, 6);
        assertEquals(31, e.player().x());
        assertNull(e.player().motion);
        ticks(e, 120);
        assertEquals(31, e.player().x());
    }

    @Test void heldMovementAndTurnOnlyChangeAtCellCenters() {
        Engine e = open();
        e.pressDirection('D'); ticks(e, 7);
        e.pressDirection('W'); e.releaseDirection('W');
        ticks(e, 6);
        assertEquals(31, e.player().x());
        assertEquals(20, e.player().y());
        assertEquals(21, e.player().motion.toY());
        ticks(e, 12);
        assertEquals(21, e.player().y());
        assertEquals(32, e.player().motion.toX());
        e.releaseDirection('D'); ticks(e, 12);
        assertNull(e.player().motion);
    }

    @Test void blockedBufferedTurnFallsBackToHeldTravelAndDoesNotCutCorner() {
        Engine e = open(); e.world()[31][21] = Tileset.WALL;
        e.pressDirection('D'); ticks(e, 5);
        e.pressDirection('W'); e.releaseDirection('W');
        ticks(e, 8);
        assertEquals(32, e.player().motion.toX());
        assertEquals(20, e.player().motion.toY());
        e.releaseDirection('D'); ticks(e, 12);
        assertEquals(32, e.player().x());
    }

    @Test void thirtySixtyAnd144FpsProduceIdenticalLogicalState() {
        for (int fps : new int[] {30, 60, 144}) {
            Engine e = open(); e.pressDirection('D');
            for (int frame = 0; frame < fps * 2; frame++) e.update(1.0 / fps);
            assertEquals(39, e.player().x(), "FPS " + fps);
            assertEquals(11, e.player().motion.elapsed());
        }
    }

    @Test void pauseFocusAndLongFramesNeverCatchUpOrRetainInput() {
        Engine e = open(); e.pressDirection('D'); ticks(e, 7);
        e.focusLost(); Motion before = e.player().motion;
        e.update(600);
        assertEquals(before, e.player().motion);
        assertEquals(GameState.PAUSE, e.state());
        keys(e, "P"); ticks(e, 100);
        assertEquals(31, e.player().x()); assertNull(e.player().motion);
        e.pressDirection('D'); e.update(600);
        assertEquals(5, e.player().motion.elapsed());
    }

    @Test void playerKeysNoLongerAdvanceWorldAndWallsDontStopMonsterClock() {
        Engine e = open(); var m = add(e, 1, 20, 20, MonsterType.HUNTER);
        e.world()[31][20] = Tileset.WALL;
        e.handleKey('D'); assertNull(m.activity.motion);
        e.pressDirection('D'); ticks(e, 13);
        assertEquals(30, e.player().x());
        assertEquals(21, m.x());
        assertEquals(1, m.activity.walked);
    }

    @Test void allFiveTypesRestAfterExactAutonomousArrivals() {
        for (MonsterType type : MonsterType.values()) {
            Engine e = open(); var m = add(e, 1, 20, 18, type);
            m.setHome(30, 20); m.setPatrolRoute(40, 18, 20, 18);
            var pace = Timing.pace(type);
            ticks(e, 1 + pace.step() * pace.stepsBeforeRest());
            assertEquals(pace.stepsBeforeRest(), m.activity.walked, type.toString());
            assertEquals(pace.rest(), m.activity.restTicks, type.toString());
            assertNull(m.activity.motion);
            int x = m.x(), y = m.y(), lock = m.commitmentTurns();
            ticks(e, pace.rest() - 1);
            assertEquals(x, m.x()); assertEquals(y, m.y());
            assertEquals(lock, m.commitmentTurns());
            assertEquals(1, m.activity.restTicks);
            ticks(e, 1);
            assertEquals(0, m.activity.walked);
            assertEquals(0, m.activity.restTicks);
        }
    }

    @Test void restingMonsterCanBeSwappedWithoutDamageAndKeepsRestCountdown() {
        Engine e = open(); var m = add(e, 1, 31, 20, MonsterType.HUNTER);
        m.activity.restTicks = 48; m.activity.walked = 6;
        tap(e, 'D');
        assertEquals(3, e.player().currentHp());
        assertEquals(31, e.player().x()); assertEquals(30, m.x());
        assertEquals(6, m.activity.walked); assertEquals(35, m.activity.restTicks);
        assertNull(m.activity.motion); assertEquals(0, m.activity.attackTicks);
    }

    @Test void blockedByMovingMonsterDoesNotHurtOrStartSwap() {
        Engine e = open(); var m = add(e, 1, 31, 20, MonsterType.HUNTER);
        m.activity.motion = new Motion(31, 20, 32, 20, 12, 0, false);
        e.pressDirection('D'); e.releaseDirection('D'); ticks(e, 1);
        assertNull(e.player().motion); assertEquals(3, e.player().currentHp());
    }

    @Test void stableContactOrderConsumesOnlyOneShieldAndOneGlobalProtection() {
        Engine e = open(); e.player().grantShield(2);
        var second = add(e, 2, 31, 20, MonsterType.HUNTER);
        var first = add(e, 1, 29, 20, MonsterType.HUNTER);
        ticks(e, 1);
        assertEquals(1, e.player().shieldCharges());
        assertEquals(3, e.player().currentHp());
        assertEquals(48, e.protectionTicks());
        assertEquals(60, first.activity.attackTicks); assertEquals(60, second.activity.attackTicks);
        ticks(e, 59); assertEquals(1, e.player().shieldCharges());
        ticks(e, 1); assertEquals(0, e.player().shieldCharges());
    }

    @Test void fatalBufferedTurnCannotFallBackToHeldDirection() {
        Engine e = open(); e.player().takeDamage(2);
        var m = add(e, 1, 31, 21, MonsterType.HUNTER);
        m.activity.waitTicks = 100;
        e.pressDirection('D'); ticks(e, 3);
        e.pressDirection('W'); e.releaseDirection('W');
        ticks(e, 10);
        assertEquals(GameState.GAME_OVER, e.state());
        assertNull(e.player().motion);
        assertEquals(31, e.player().x()); assertEquals(20, e.player().y());
    }

    @Test void fatalBumpDoesNotSwapAndStopsOtherMonsterDecisions() {
        Engine e = open(); e.player().takeDamage(2);
        var m = add(e, 1, 31, 20, MonsterType.HUNTER);
        var other = add(e, 2, 20, 20, MonsterType.HUNTER);
        tap(e, 'D');
        assertEquals(GameState.GAME_OVER, e.state());
        assertNull(e.player().motion); assertNull(m.activity.motion); assertNull(other.activity.motion);
        assertEquals(30, e.player().x());
        keys(e, "Q"); assertEquals(GameState.MENU, e.state());
    }

    @Test void swapDoesNotCountAsAutonomousStepOrAllowSameTickAction() {
        Engine e = open(); var m = add(e, 1, 31, 20, MonsterType.HUNTER);
        m.activity.walked = 3;
        tap(e, 'D');
        assertEquals(2, e.player().currentHp());
        assertEquals(3, m.activity.walked); assertNull(m.activity.motion);
        assertEquals(30, m.x()); assertEquals(31, e.player().x());
    }

    @Test void freezeCompletesInflightStepButPausesRestWaitAndContact() {
        Engine e = open();
        var moving = add(e, 1, 20, 20, MonsterType.GUARDIAN);
        moving.activity.motion = new Motion(20, 20, 21, 20, 18, 0, false);
        var resting = add(e, 2, 31, 20, MonsterType.HUNTER);
        resting.activity.restTicks = 30; resting.activity.waitTicks = 7;
        set(e, "freezeTicks", 120);
        ticks(e, 119);
        assertEquals(21, moving.x()); assertEquals(1, moving.activity.walked);
        assertNull(moving.activity.motion);
        assertEquals(30, resting.activity.restTicks); assertEquals(7, resting.activity.waitTicks);
        assertEquals(3, e.player().currentHp()); assertEquals(1, e.freezeTicks());
        ticks(e, 1); assertEquals(30, resting.activity.restTicks);
        ticks(e, 1); assertEquals(29, resting.activity.restTicks);
    }

    @Test void repeatedFreezeRefreshesToTwoSecondsWithoutStacking() throws Exception {
        Engine e = open();
        var apply = Engine.class.getDeclaredMethod("applyBuff", BuffType.class); apply.setAccessible(true);
        apply.invoke(e, BuffType.STUN); ticks(e, 30); assertEquals(90, e.freezeTicks());
        apply.invoke(e, BuffType.STUN); assertEquals(120, e.freezeTicks());
        apply.invoke(e, BuffType.STUN); assertEquals(120, e.freezeTicks());
    }

    @Test void trapsTriggerOnlyOnArrivalAndIgnoreContactProtection() {
        Engine e = open();
        EngineFixture.<SceneObject>list(e, "sceneObjects").add(new SceneObject(31, 20, SceneObjectType.SPIKE_TRAP));
        set(e, "protectionTicks", 48);
        tap(e, 'D'); assertEquals(2, e.player().currentHp());
        ticks(e, 120); assertEquals(2, e.player().currentHp());
        tap(e, 'A'); tap(e, 'D'); assertEquals(1, e.player().currentHp());
    }

    @Test void portalSnapsOnceAndRejectsReservedDestination() {
        Engine e = open();
        var objects = EngineFixture.<SceneObject>list(e, "sceneObjects");
        objects.add(SceneObject.portal(31, 20, 40, 20));
        objects.add(SceneObject.portal(40, 20, 31, 20));
        tap(e, 'D'); assertEquals(40, e.player().x()); assertNull(e.player().motion);
        ticks(e, 20); assertEquals(40, e.player().x());
        e.player().teleportTo(e.world(), 30, 20);
        var m = add(e, 1, 41, 20, MonsterType.GUARDIAN);
        m.activity.motion = new Motion(41, 20, 40, 20, 18, 0, false);
        set(e, "freezeTicks", 120);
        tap(e, 'D'); assertEquals(31, e.player().x());
    }

    @Test void pickupOnlyOnArrivalAndSpawnsWithIndependentGracePeriod() {
        Engine e = open(); e.items().add(new Item(31, 20, ItemType.CRYSTAL));
        e.pressDirection('D'); e.releaseDirection('D'); ticks(e, 12);
        assertEquals(0, e.monsters().size());
        ticks(e, 1); assertEquals(1, e.monsters().size());
        var m = e.monsters().getFirst();
        assertEquals(39, m.activity.waitTicks); assertNull(m.activity.motion);
        ticks(e, 38); assertNull(m.activity.motion);
        ticks(e, 1); assertNotNull(m.activity.motion);
    }

    @Test void restingFrozenAndWaitingActorsStillBlockOccupancy() {
        Engine e = open();
        var follower = add(e, 1, 28, 20, MonsterType.HUNTER);
        var resting = add(e, 2, 29, 20, MonsterType.HUNTER);
        resting.activity.restTicks = 100; follower.activity.walked = 2;
        ticks(e, 36);
        assertEquals(28, follower.x()); assertNull(follower.activity.motion);
        assertEquals(2, follower.activity.walked);
    }

    @Test void finalFrozenTickIsStillHarmlessForPlayerSwap() {
        Engine e = open(); var m = add(e, 1, 31, 20, MonsterType.HUNTER);
        set(e, "freezeTicks", 1);
        e.pressDirection('D'); e.releaseDirection('D'); ticks(e, 1);
        assertEquals(3, e.player().currentHp());
        assertTrue(m.activity.motion.swap());
    }

    @Test void frozenNormalMonsterIsHarmlessAndProtectionPreservesShield() {
        Engine e = open(); var m = add(e, 1, 31, 20, MonsterType.HUNTER);
        set(e, "protectionTicks", 48); e.player().grantShield(2);
        tap(e, 'D');
        assertEquals(3, e.player().currentHp()); assertEquals(2, e.player().shieldCharges());
        assertEquals(48, m.activity.attackTicks);
    }

    @Test void waitingMonsterDestinationReservationBlocksAllActors() {
        Engine e = open();
        var first = add(e, 1, 28, 20, MonsterType.HUNTER);
        var second = add(e, 2, 29, 19, MonsterType.HUNTER);
        e.world()[30][19] = Tileset.WALL;
        first.activity.motion = new Motion(28, 20, 29, 20, 12, 0, false);
        ticks(e, 1);
        assertNull(second.activity.motion);
        e.player().teleportTo(e.world(), 29, 21);
        e.pressDirection('S'); e.releaseDirection('S'); ticks(e, 1);
        assertNull(e.player().motion); assertEquals(3, e.player().currentHp());
    }

    @Test void activeExitWinsAtArrivalBeforeAdjacentMonsterCanAttack() {
        Engine e = open(); var exit = new GameExit(31, 20, true);
        set(e, "exit", exit); exit.syncTile(e.world());
        var m = add(e, 1, 32, 20, MonsterType.HUNTER);
        m.activity.waitTicks = 13;
        tap(e, 'D');
        assertEquals(GameState.WIN, e.state()); assertEquals(3, e.player().currentHp());
        assertNull(m.activity.motion);
        keys(e, "Q"); assertEquals(GameState.MENU, e.state());
    }

    @Test void movementInputIsNotSavedAndIdleTimersMakeProgressDirty() throws Exception {
        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", directory.toString());
            Engine e = open(); var m = add(e, 1, 20, 20, MonsterType.HUNTER);
            m.activity.restTicks = 40;
            e.pressDirection('D'); ticks(e, 4); keys(e, "PS1Q");
            Engine restored = new Engine(); keys(restored, "L1P"); ticks(restored, 50);
            assertEquals(31, restored.player().x()); assertNull(restored.player().motion);
            keys(restored, "PQ"); assertEquals(GameState.CONFIRM_QUIT, restored.state());
        } finally { System.setProperty("user.dir", previous); }
    }

    @Test void subTickRenderingIsSmoothWithoutAdvancingLogic() {
        Engine e = open(); e.pressDirection('D'); ticks(e, 1);
        e.update(1.0 / 120);
        assertEquals(30, e.player().x());
        assertEquals(0, e.player().motion.elapsed());
        assertEquals(30 + .5f / 12, e.player().renderX(), 0.00001);
    }

    @Test void hoverUsesRuntimeRestAndSeconds() {
        Engine e = open(); var m = add(e, 1, 31, 20, MonsterType.HUNTER);
        m.activity.walked = 4;
        assertTrue(HoverDescriptions.monsterDescription(e, m).contains("4/8"));
        m.activity.restTicks = 42;
        String text = HoverDescriptions.monsterDescription(e, m);
        assertTrue(text.contains("0.7")); assertTrue(text.contains("无伤换位"));
    }

    @Test void realtimeSaveRoundTripsHalfStepSwapPreRestRestAndFreeze() throws Exception {
        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", directory.toString());
            for (int scenario = 0; scenario < 5; scenario++) {
                Engine original = open();
                var m = add(original, 1, scenario == 1 ? 31 : 20, 20, MonsterType.HUNTER);
                if (scenario == 0) { original.pressDirection('D'); ticks(original, 7); original.releaseDirection('D'); }
                if (scenario == 1) { original.pressDirection('D'); original.releaseDirection('D'); ticks(original, 7); }
                if (scenario == 2) { m.activity.walked = Timing.pace(m.type()).stepsBeforeRest() - 1; ticks(original, 12); }
                if (scenario == 3) { m.activity.walked = Timing.pace(m.type()).stepsBeforeRest(); m.activity.restTicks = 30; }
                if (scenario == 4) { set(original, "freezeTicks", 80); m.activity.restTicks = 20; }
                keys(original, "PS1");
                Path path = directory.resolve("save/slot1/save.json");
                String saved = Files.readString(path);
                Engine restored = new Engine(); keys(restored, "L1");
                assertEquals(GameState.PAUSE, restored.state());
                restored.update(1000);
                assertEquals(saved, Files.readString(path));
                assertEquals(original.player().motion, restored.player().motion);
                assertEquals(original.freezeTicks(), restored.freezeTicks());
                keys(original, "P"); keys(restored, "P");
                ticks(original, 90); ticks(restored, 90);
                assertEquals(original.player().x(), restored.player().x());
                assertEquals(original.player().currentHp(), restored.player().currentHp());
                assertEquals(original.player().motion, restored.player().motion);
                assertEquals(MonsterData.from(original.monsters().getFirst()), MonsterData.from(restored.monsters().getFirst()));
                assertEquals(new com.google.gson.Gson().toJson(original.monsters().getFirst().activity),
                    new com.google.gson.Gson().toJson(restored.monsters().getFirst().activity));
            }
        } finally { System.setProperty("user.dir", previous); }
    }

    @Test void legacyFreezeConvertsButPresentZeroRealtimeTakesPrecedence() throws Exception {
        String previous = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", directory.toString());
            Engine original = open(); keys(original, "PS1");
            Path path = directory.resolve("save/slot1/save.json");
            var json = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            json.addProperty("frozenMonsterTurns", 2);
            Files.writeString(path, json.toString());
            Engine modern = new Engine(); keys(modern, "L1");
            assertEquals(0, modern.freezeTicks());
            json.remove("realtime"); json.remove("version");
            String legacy = json.toString(); Files.writeString(path, legacy);
            Engine old = new Engine(); keys(old, "L1");
            assertEquals(120, old.freezeTicks()); assertEquals(GameState.PAUSE, old.state());
            assertEquals(legacy, Files.readString(path));
            keys(old, "S2Q"); assertEquals(GameState.MENU, old.state());
            Engine running = new Engine(); keys(running, "L2P"); ticks(running, 1);
            keys(running, "PQ"); assertEquals(GameState.CONFIRM_QUIT, running.state());
        } finally { System.setProperty("user.dir", previous); }
    }
}
