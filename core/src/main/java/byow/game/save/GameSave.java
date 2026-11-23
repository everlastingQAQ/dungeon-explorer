package byow.game.save;

import byow.game.monster.MonsterType;

import java.util.List;

public record GameSave(
    WorldData worldData,
    PlayerData playerData,
    List<ItemData> items,
    List<MonsterData> monsters,
    boolean[][] explored,
    int visionRadius,
    long randomState,
    long nextMonsterId,
    int frozenMonsterTurns,
    boolean radarActive,
    List<MonsterType> seenMonsterTypes,
    ExitData exit,
    List<SceneObjectData> sceneObjects,
    int version,
    RealtimeData realtime
) {
    public GameSave(WorldData worldData, PlayerData playerData, List<ItemData> items,
        List<MonsterData> monsters, boolean[][] explored, int visionRadius, long randomState,
        long nextMonsterId, int frozenMonsterTurns, boolean radarActive,
        List<MonsterType> seenMonsterTypes, ExitData exit, List<SceneObjectData> sceneObjects) {
        this(worldData, playerData, items, monsters, explored, visionRadius, randomState,
            nextMonsterId, frozenMonsterTurns, radarActive, seenMonsterTypes, exit, sceneObjects, 0, null);
    }
}
