package byow.game;

import byow.game.monster.MonsterController;
import byow.game.feedback.GameFeedback;
import byow.game.monster.MonsterSpawner;
import byow.game.realtime.*;
import byow.game.item.BuffType;
import byow.game.item.Item;
import byow.game.item.ItemRules;
import byow.game.itemGenerator.ItemGenerator;
import byow.game.monster.Monster;
import byow.game.monster.MonsterType;
import byow.game.player.Player;
import byow.game.random.GameRandom;
import byow.game.scene.SceneObject;
import byow.game.scene.SceneObjectGenerator;
import byow.game.scene.SceneObjectType;
import byow.game.save.*;
import byow.game.tile.TETile;
import byow.game.visioncauculate.Visioncauculate;
import byow.game.worldGenerator.WorldGenerator;
import byow.game.worldGenerator.RoomDesign;

import java.util.*;

import static byow.game.GameConfig.ITEM_NUMBER;
import static byow.game.GameConfig.MONSTER_CONTACT_DAMAGE;
import static byow.game.GameConfig.PLAYER_MAX_HP;
import static byow.game.GameConfig.DEFAULT_VISION_RADIUS;
import static byow.game.GameConfig.REQUIRED_ITEM_COUNT;
import static byow.game.item.ItemRules.HEAL_AMOUNT;
import static byow.game.item.ItemRules.SHIELD_CHARGES;
import static byow.game.item.ItemRules.VISION_RADIUS_BONUS;
import static byow.game.tile.TileConverter.toTETile;
import static byow.game.tile.TileConverter.toTileType;

public class Engine {

    /** 首次发现怪物提示持续的游戏 tick 数。 */
    private static final int MONSTER_NOTICE_TICKS = 180;

    // =====================
    // Fields
    // =====================

    /** 当前游戏世界。 */
    private TETile[][] world = null;
    private List<RoomDesign> roomDesigns = List.of();

    public List<RoomDesign> roomDesigns() { return roomDesigns; }

    /** 当前游戏玩家。 */
    private Player player;

    /** 当前游戏物品。 */
    private List<Item> items;

    /** 当前世界唯一的出口。 */
    private GameExit exit;

    /** 地图中的功能性场景物件。 */
    private final List<SceneObject> sceneObjects = new ArrayList<>();

    /** 当前游戏中的全部怪物。空列表表示尚未激活怪物。 */
    private final List<Monster> monsters = new ArrayList<>();

    /** 分配给下一只怪物的稳定 ID。 */
    private long nextMonsterId = 1;

    /** 怪物管理。 */
    private final MonsterController monsterController = new MonsterController();

    /** 当前拥有的物品数。 */
    private int collectedCount;

    /** 当前玩家视野范围。 */
    private int visionRadius;

    /** Input and real-time counters; never advanced by menu input. */
    private final MovementInput movementInput = new MovementInput();
    private double accumulator;
    private int freezeTicks;
    private int protectionTicks;
    private final GameFeedback feedback = new GameFeedback();
    public GameFeedback feedback() { return feedback; }
    private boolean frozenThisTick;
    private char travelDirection;

    /** Radar Buff 是否已经激活。 */
    private boolean radarActive;

    /** 已经进入过玩家视野的怪物类型，用于保证首次提示只出现一次。 */
    private final EnumSet<MonsterType> seenMonsterTypes =
        EnumSet.noneOf(MonsterType.class);

    /** 当前正在左下角显示的首次怪物类型。 */
    private MonsterType monsterNoticeType;

    /** 当前首次怪物提示剩余的逻辑 tick。 */
    private int monsterNoticeTicks;

    /** 用于暂存用户输入的新世界随机种子。 */
    private final StringBuilder seedString = new StringBuilder();

    /** 当前世界随机种子 */
    private long seed;

    /** 随机种子生成的随机数生成器。 */
    private GameRandom random;

    /** 当前游戏所处的状态。 */
    private GameState state = GameState.MENU;

    /** 游戏存档管理器 */
    private final SaveManager saveManager;

    private final boolean[] occupiedSaveSlots = new boolean[5];
    private String uiMessage = "";
    private int pendingOverwriteSlot;

    public Engine() {
        this(new SaveManager());
    }

    public Engine(SaveManager saveManager) {
        this.saveManager = Objects.requireNonNull(saveManager);
    }

    /** 保存完成后是否返回主菜单。 */
    private boolean returnToMenuAfterSave;

    /** 当前进度是否比最近一次存档更新。 */
    private boolean hasUnsavedChanges;

    /** 是否请求退出游戏 */
    private boolean shouldQuit = false;

    /** 视觉可视范围。 */
    private boolean[][] visible;

    /** 已经探索过的范围。 */
    private boolean[][] explored;


    public void pressDirection(char key) {
        if (state == GameState.PLAYING) movementInput.press(Character.toUpperCase(key));
    }
    public void releaseDirection(char key) { movementInput.release(Character.toUpperCase(key)); }
    public void focusLost() {
        movementInput.clear();
        accumulator = 0;
        if (state == GameState.PLAYING) state = GameState.PAUSE;
    }
    public int freezeTicks() { return freezeTicks; }
    public int protectionTicks() { return protectionTicks; }

    /** No wall clock, and never catch up more than six ticks after a stalled frame. */
    public void update(double deltaSeconds) {
        if (state != GameState.PLAYING) { accumulator = 0; movementInput.clear(); return; }
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0) return;
        accumulator += Math.min(.1, deltaSeconds);
        int count = 0;
        while (accumulator + 1e-10 >= 1.0 / Timing.HZ && count++ < 6 && state == GameState.PLAYING) {
            accumulator -= 1.0 / Timing.HZ;
            tick();
        }
        if (state != GameState.PLAYING) { movementInput.clear(); accumulator = 0; }
        float alpha = (float) Math.max(0, accumulator * Timing.HZ);
        player.renderAlpha = alpha;
        for (Monster m : monsters) m.renderAlpha = alpha;
    }

    private void tick() {
        feedback.tick();
        boolean frozen = freezeTicks > 0;
        frozenThisTick = frozen;
        boolean changed = false;
        if (freezeTicks > 0) { freezeTicks--; changed = true; }
        if (protectionTicks > 0) { protectionTicks--; changed = true; }
        Set<Long> excluded = new HashSet<>();
        // Timer advancement precedes arrivals: a newly started rest gets its full duration.
        for (Monster m : monsters) {
            var a = m.activity;
            if (!frozen) {
                if (a.attackTicks > 0) { a.attackTicks--; changed = true; }
                if (a.waitTicks > 0) { a.waitTicks--; changed = true; }
                if (a.restTicks > 0) {
                    changed = true;
                    if (--a.restTicks == 0) { a.walked = 0; m.clearCommittedTarget(); }
                }
            }
        }
        for (Monster m : monsters) {
            var a = m.activity;
            if (a.motion == null) continue;
            changed = true;
            a.motion = a.motion.advance();
            if (a.motion.complete()) {
                Motion completed = a.motion;
                m.moveTo(completed.toX(), completed.toY());
                a.motion = null;
                if (completed.swap()) excluded.add(m.id());
                else if (++a.walked >= Timing.pace(m.type()).stepsBeforeRest())
                    a.restTicks = Timing.pace(m.type()).rest();
            }
        }
        boolean arrived = false;
        if (player.motion != null) {
            changed = true;
            player.motion = player.motion.advance();
            if (player.motion.complete()) {
                player.arrive(player.motion);
                arrived = true;
                resolvePlayerInteractions();
            }
        }
        hasUnsavedChanges |= changed;
        if (state != GameState.PLAYING) return;
        if (arrived) updateVision();
        if (player.motion == null) {
            Character pending = movementInput.consumePending();
            Character direction = pending != null ? pending : movementInput.preferred();
            if (direction != null && !startPlayerMove(direction, excluded)
                && state == GameState.PLAYING
                && movementInput.held(travelDirection) && direction != travelDirection)
                startPlayerMove(travelDirection, excluded);
        }
        if (state != GameState.PLAYING) return;
        // A freeze picked up on this arrival also prevents decisions immediately.
        if (!frozen && freezeTicks == 0) {
            for (var decision : monsterController.decisions(world, monsters, player, excluded)) {
                Monster m = decision.monster();
                var a = m.activity;
                var next = decision.destination();
                int duration = Timing.pace(m.type()).step();
                a.waitTicks = duration; // Failed attempts do not busy-loop AI every tick.
                hasUnsavedChanges = true;
                if (next == null) continue;
                if (next.x() == player.x() && next.y() == player.y()) {
                    resolveContact(m);
                    if (state != GameState.PLAYING) return;
                } else if (walkable(next.x(), next.y()) && !occupiedOrReserved(next.x(), next.y())) {
                    a.waitTicks = 0; // Animation itself is the movement interval.
                    a.motion = new Motion(m.x(), m.y(), next.x(), next.y(), duration, 0, false);
                }
            }
        }
        updateHudContext();
    }

    private boolean walkable(int x, int y) {
        return x > 0 && y > 0 && x < world.length - 1 && y < world[0].length - 1
            && byow.game.tile.TileRules.isWalkable(world[x][y]);
    }

    public boolean occupiedOrReserved(int x, int y) {
        if (player.x() == x && player.y() == y
            || player.motion != null && player.motion.targets(x, y)) return true;
        for (Monster m : monsters)
            if (m.x() == x && m.y() == y || m.activity.motion != null && m.activity.motion.targets(x, y))
                return true;
        return false;
    }

    private boolean startPlayerMove(char direction, Set<Long> excluded) {
        int dx = direction == 'D' ? 1 : direction == 'A' ? -1 : 0;
        int dy = direction == 'W' ? 1 : direction == 'S' ? -1 : 0;
        int x = player.x() + dx, y = player.y() + dy;
        if (!walkable(x, y)) return false;
        Monster target = monsterAt(x, y);
        if (target != null) {
            if (target.activity.motion != null) return false;
            for (Monster m : monsters)
                if (m.activity.motion != null && m.activity.motion.targets(x, y)) return false;
            resolveContact(target);
            if (state != GameState.PLAYING) return false;
            target.activity.motion = new Motion(x, y, player.x(), player.y(), Timing.PLAYER_STEP, 0, true);
            excluded.add(target.id());
        } else if (occupiedOrReserved(x, y)) return false;
        player.motion = new Motion(player.x(), player.y(), x, y, Timing.PLAYER_STEP, 0, target != null);
        travelDirection = direction;
        hasUnsavedChanges = true;
        return true;
    }


    // =====================
    // Public input entry
    // =====================

    /**
     * 根据不同的游戏状态分别处理输入
     * */
    public void handleKey(char key) {

        char c = Character.toUpperCase(key);
        uiMessage = "";

        switch (state) {
            case MENU -> handleMenuInput(c);
            case SEED -> handleSeedInput(c);
            case PLAYING -> { if (c == 'P') focusLost(); }
            case LOAD -> handleLoadInput(c);
            case PAUSE -> handlePauseInput(c);
            case SAVE -> handleSaveInput(c);
            case CONFIRM_QUIT -> handleConfirmQuitInput(c);
            case WIN, GAME_OVER -> handleEndInput(c);
        }
    }

    // =====================
    // State input handlers
    // =====================

    /**
     * 处理主菜单状态下的输入。
     *
     * N：开始新游戏并进入种子输入状态。
     * L：加载已有游戏。
     * Q：退出游戏。
     *
     * @param c 用户输入的字符
     */
    private void handleMenuInput(char c) {
        switch (c) {
            case 'N' -> {
                seedString.setLength(0);
                state = GameState.SEED;
            }
            case 'L' -> {
                refreshSaveSlots();
                state = GameState.LOAD;
            }
            case 'Q' -> quit();
        }
    }

    /**
     * 处理随机种子的输入
     *
     * 在读取到 S 之前，将输入的字符依次加入 seedString
     * 在读取到 S 之后，将根据种子生成世界，并进入 PLAYING 状态
     *
     * @param c 用户输入的字符
     */
    private void handleSeedInput(char c) {
        if (c == 'S') {
            initNewWorld();
            return;
        }

        if (c == 'B') {
            state = GameState.MENU;
            return;
        }

        if (c == '\b' || c == '\u007f') {
            if (!seedString.isEmpty()) {
                seedString.deleteCharAt(seedString.length() - 1);
            }
        } else if (c >= '0' && c <= '9') {
            if (seedString.length() < 19) {
                seedString.append(c);
            } else {
                uiMessage = "种子最多 19 位。";
            }
        }
    }

    /**
     * 处理加载存档时的输入
     * */
    private void handleLoadInput(char c) {
        if (c == 'B') {
            state = GameState.MENU;
            return;
        }
        if (c < '1' || c > '5') {
            return;
        }
        try {
            loadGame(c - '0');
        } catch (SaveException exception) {
            uiMessage = exception.getMessage();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            uiMessage = "存档数据无效，无法读取。";
        }
    }

    private void handleSaveInput(char c) {
        if (pendingOverwriteSlot != 0) {
            if (c == 'Y') {
                int slot = pendingOverwriteSlot;
                pendingOverwriteSlot = 0;
                finishSave(slot);
            } else if (c == 'N' || c == 'B') {
                pendingOverwriteSlot = 0;
            }
            return;
        }
        if (c == 'B') {
            state = GameState.PAUSE;
            returnToMenuAfterSave = false;
            return;
        }
        if (c < '1' || c > '5') {
            return;
        }
        int slot = c - '0';
        try {
            if (saveManager.exists(slot)) {
                occupiedSaveSlots[slot - 1] = true;
                pendingOverwriteSlot = slot;
                return;
            }
            finishSave(slot);
        } catch (SaveException exception) {
            uiMessage = exception.getMessage();
        }
    }

    private void finishSave(int slot) {
        try {
            saveGame(slot);
            occupiedSaveSlots[slot - 1] = true;
            uiMessage = "保存成功。";
            state = returnToMenuAfterSave ? GameState.MENU : GameState.PAUSE;
            returnToMenuAfterSave = false;
        } catch (SaveException exception) {
            uiMessage = exception.getMessage();
        }
    }

    private void refreshSaveSlots() {
        try {
            for (int slot = 1; slot <= occupiedSaveSlots.length; slot++) {
                occupiedSaveSlots[slot - 1] = saveManager.exists(slot);
            }
        } catch (SaveException exception) {
            uiMessage = exception.getMessage();
        }
    }

    /**
     * 处理暂停时的输入
     * */
    private void handlePauseInput(char c) {
        switch(c) {
            case 'P' -> state = GameState.PLAYING;
            case 'S' -> {
                returnToMenuAfterSave = false;
                pendingOverwriteSlot = 0;
                refreshSaveSlots();
                state = GameState.SAVE;
            }
            case 'Q' -> state = hasUnsavedChanges
                ? GameState.CONFIRM_QUIT
                : GameState.MENU;
        }
    }

    /**
     * 处理确认是否退出时的输入
     * */
    private void handleConfirmQuitInput(char c) {
        switch(c) {
            case 'Y' -> {
                returnToMenuAfterSave = true;
                pendingOverwriteSlot = 0;
                refreshSaveSlots();
                state = GameState.SAVE;
            }
            case 'N' -> state = GameState.MENU;
            case 'B' -> state = GameState.PAUSE;
        }
    }

    /** 结束画面只允许开始新游戏或返回主菜单。 */
    private void handleEndInput(char c) {
        switch (c) {
            case 'N' -> {
                seedString.setLength(0);
                state = GameState.SEED;
            }
            case 'Q' -> state = GameState.MENU;
        }
    }

    /**
     * 处理游戏进行状态下的输入。
     * 移动由独立按键状态与固定 tick 更新驱动。
     *
     * @param c 用户输入的字符
     */
    private Monster monsterAt(int x, int y) {
        for (Monster monster : monsters) {
            if (monster.x() == x && monster.y() == y) {
                return monster;
            }
        }
        return null;
    }

    // =====================
    // handle game interact
    // =====================

    // 处理人物交互
    private void resolvePlayerInteractions() {
        checkItemCollection();
        if (state == GameState.PLAYING) {
            resolveSceneObjectInteraction();
        }
        if (state == GameState.PLAYING
            && exit.active() && exit.contains(player.x(), player.y())) {
            state = GameState.WIN;
        }
    }

    /** 结算玩家脚下的场景物件；传送不会连锁触发目标位置。 */
    private void resolveSceneObjectInteraction() {
        SceneObject object = sceneObjectAt(player.x(), player.y());
        if (object == null) {
            return;
        }

        switch (object.type()) {
            case SPIKE_TRAP -> {
                player.takeDamage(1);
                feedback.impact(GameFeedback.Impact.DAMAGE, "尖刺 -1 生命");
                if (!player.isAlive()) {
                    state = GameState.GAME_OVER;
                }
            }
            case HEALING_FOUNTAIN -> {
                if (player.currentHp() < player.maxHp()) {
                    player.heal(1);
                    feedback.impact(GameFeedback.Impact.HEAL, "+1 生命");
                    object.consume();
                }
            }
            case PORTAL -> {
                if (!occupiedOrReserved(object.targetX(), object.targetY())) {
                    player.teleportTo(world, object.targetX(), object.targetY());
                }
            }
            case BLESSING_CHEST -> {
                BuffType[] buffs = BuffType.values();
                String effect = applyBuff(buffs[random.nextInt(buffs.length)]);
                feedback.notice(GameFeedback.Channel.EFFECT, "祝福宝箱：" + effect);
                object.consume();
            }
        }
    }

    private SceneObject sceneObjectAt(int x, int y) {
        for (SceneObject object : sceneObjects) {
            if (object.contains(x, y)) {
                return object;
            }
        }
        return null;
    }

    // 处理是否收集到物品
    private void checkItemCollection() {
        int x = player.x();
        int y = player.y();

        Item collectItem = null;
        for (Item item : items) {
            if (item.x() == x && item.y() == y) {
                collectItem = item;
                break;
            }
        }

        if (collectItem != null) {
            collectItem(collectItem);
        }
    }

    /** 拾取遗物，激活对应怪物，并在超过目标数量时给予固定 Buff。 */
    private void collectItem(Item item) {
        ItemRules.Rule rule = ItemRules.ruleFor(item.type());

        // 先完成拾取，再根据拾取后的总数判断是否应当给予 Buff。
        items.remove(item);
        collectedCount++;

        if (collectedCount >= REQUIRED_ITEM_COUNT && !exit.active()) {
            exit.activate(world);
            feedback.notice(GameFeedback.Channel.EXIT, "出口已开启！找到出口，进入即可获胜。");
        }

        // 每种遗物都会激活一只固定类型的怪物。
        spawnMonster(rule.monsterType(), item.x(), item.y());

        // 只有第 4 和第 5 个遗物会额外给予 Buff。
        String effect = ItemRules.shouldGrantBuff(collectedCount) ? "；" + applyBuff(rule.buffType()) : "";
        feedback.notice(GameFeedback.Channel.PICKUP,
            "拾取" + item.type().displayName() + " · 遗物 " + collectedCount + "/" + REQUIRED_ITEM_COUNT
                + "\n激活" + rule.monsterType().displayName() + effect + "。");
    }

    /**
     * 创建遗物对应的怪物；Guardian 使用触发遗物的位置作为守护点。
     */
    private void spawnMonster(MonsterType type, int itemX, int itemY) {
        Monster monster = MonsterSpawner.spawn(
            nextMonsterId,
            world,
            random,
            player,
            monsters,
            items,
            sceneObjects,
            type
        );

        if (type == MonsterType.GUARDIAN) {
            monster.setHome(itemX, itemY);
        }

        monster.activity.waitTicks = Timing.SPAWN_DELAY + (int) Math.floorMod(monster.id(), 4) * 3;
        monsters.add(monster);
        nextMonsterId++;
    }

    /** 应用不依赖出口系统的遗物 Buff。 */
    private String applyBuff(BuffType type) {
        int hpBefore = player.currentHp();
        switch (type) {
            case VISION -> visionRadius += VISION_RADIUS_BONUS;
            case HEAL -> player.heal(HEAL_AMOUNT);
            case SHIELD -> player.grantShield(SHIELD_CHARGES);
            case STUN -> freezeTicks = Math.max(freezeTicks, Timing.FREEZE);
            case RADAR -> radarActive = true;
        }
        return switch (type) {
            case VISION -> "视野 +" + VISION_RADIUS_BONUS;
            case HEAL -> player.currentHp() == hpBefore ? "生命已满" : "恢复 " + (player.currentHp() - hpBefore) + " 点生命";
            case SHIELD -> "护盾 +" + SHIELD_CHARGES;
            case STUN -> "怪物冻结 2 秒";
            case RADAR -> "出口雷达已开启";
        };
    }

    /** Attack attempts consume only the attacker's cooldown; protection is global. */
    private void resolveContact(Monster monster) {
        var a = monster.activity;
        if (freezeTicks > 0 || frozenThisTick || a.restTicks > 0 || a.motion != null && a.motion.swap() || a.attackTicks > 0) return;
        a.attackTicks = Timing.CONTACT_COOLDOWN;
        hasUnsavedChanges = true;
        if (protectionTicks > 0) return;
        if (player.consumeShield()) {
            feedback.impact(GameFeedback.Impact.SHIELD, "护盾抵挡");
        } else {
            player.takeDamage(MONSTER_CONTACT_DAMAGE);
            feedback.impact(GameFeedback.Impact.DAMAGE, "-" + MONSTER_CONTACT_DAMAGE + " 生命");
        }
        protectionTicks = Timing.CONTACT_PROTECTION;
        if (!player.isAlive()) state = GameState.GAME_OVER;
    }

    // =====================
    // Update Vision
    // ====================

    public void updateVision() {
        visible = Visioncauculate.calculate(world, player, visionRadius);

        for (int x = 0; x < world.length; x++) {
            for (int y = 0; y < world[0].length; y++) {
                if (visible[x][y]) {
                    explored[x][y] = true;
                }
            }
        }
    }

    /**
     * 更新左下角的首次怪物提示。
     *
     * 已有怪物提示优先显示；提示结束后，再选择一只当前可见且尚未
     * 介绍过的怪物。遗物靠近提示由渲染时动态查询，不需要保存计时。
     */
    private void updateHudContext() {
        if (monsterNoticeTicks > 0) {
            monsterNoticeTicks--;
            if (monsterNoticeTicks > 0) {
                return;
            }
            monsterNoticeType = null;
        }

        for (Monster monster : monsters) {
            if (visible[monster.x()][monster.y()]
                && !seenMonsterTypes.contains(monster.type())) {
                seenMonsterTypes.add(monster.type());
                monsterNoticeType = monster.type();
                monsterNoticeTicks = MONSTER_NOTICE_TICKS;
                return;
            }
        }
    }

    // =====================
    // Game lifecycle
    // =====================

    /**
     * 在输入完种子后初始化世界
     * */
    private void initNewWorld() {

        try {
            seed = Long.parseLong(seedString.toString());
        } catch (NumberFormatException e) {
            uiMessage = "请输入 0 到 9223372036854775807 的种子。";
            return;
        }

        feedback.clear();
        // 创建随机数
        random = new GameRandom(seed);

        // 创建世界
        var generated = WorldGenerator.generateDesigned(seed);
        world = generated.terrain();
        roomDesigns = generated.rooms();

        // 创建人物
        player = new Player(world, random);

        // 出口位于距离出生点最远的可达位置，激活前不可进入。
        exit = GameExit.place(world, player);

        // 创建物品
        items = ItemGenerator.generate(world, player, random, roomDesigns);

        // 独立随机流避免新增场景物件改变遗物和怪物的既有随机序列。
        sceneObjects.clear();
        sceneObjects.addAll(SceneObjectGenerator.generate(
            world,
            player,
            items,
            exit,
            new GameRandom(seed ^ 0x5CE0B1EC7L),
            roomDesigns
        ));

        // 新游戏开始时没有怪物，拾取遗物后再逐个激活。
        monsters.clear();
        nextMonsterId = 1;
        frozenThisTick = false;
        freezeTicks = 0;
        protectionTicks = 0;
        accumulator = 0;
        movementInput.clear();
        radarActive = false;
        seenMonsterTypes.clear();
        monsterNoticeType = null;
        monsterNoticeTicks = 0;

        // 初始化物品数量
        collectedCount = 0;

        // 初始化视野范围
        visionRadius = DEFAULT_VISION_RADIUS;

        // 初始化可视范围
        visible = new boolean[world.length][world[0].length];

        // 初始化探索范围
        explored = new boolean[world.length][world[0].length];

        updateVision();

        // 新世界尚未写入任何存档。
        hasUnsavedChanges = true;

        // 更改游戏状态
        state = GameState.PLAYING;
    }

    /**
     * 退出游戏
     * */
    private void quit() {
        shouldQuit = true;
    }


    // =====================
    // Save / Load
    // =====================

    // 复制boolean数组
    private static boolean[][] copyGrid(boolean[][] source) {
        boolean[][] copy = new boolean[source.length][];

        for (int x = 0; x < source.length; x++) {
            copy[x] = source[x].clone();
        }

        return copy;
    }

    /**
     * 根据存档加载游戏
     **/
    private void loadGame(int slot) {
        GameSave saved = saveManager.load(slot);

        // 先构造完整候选状态，全部成功后才替换当前游戏。
        WorldData layout = RoomLayoutMigration.toRgs(saved.worldData());
        TETile[][] loadedWorld = toTETile(layout.world());
        PlayerData data = saved.playerData();
        int maxHp = data.maxHp() > 0 ? data.maxHp() : PLAYER_MAX_HP;
        int hp = data.maxHp() > 0 ? data.currentHp() : maxHp;
        Player loadedPlayer = new Player(loadedWorld, data.x(), data.y(),
            data.lastMoveDx(), data.lastMoveDy(), hp, maxHp, data.shieldCharges());
        List<Item> loadedItems = new ArrayList<>();
        for (ItemData item : saved.items()) {
            loadedItems.add(new Item(item.x(), item.y(), item.type()));
        }
        List<Monster> loadedMonsters = new ArrayList<>();
        if (saved.monsters() != null) {
            for (MonsterData monster : saved.monsters()) {
                loadedMonsters.add(monster.toMonster());
            }
        }
        int loadedCount = ITEM_NUMBER - loadedItems.size();
        GameExit loadedExit;
        if (saved.exit() == null) {
            loadedExit = GameExit.place(loadedWorld, loadedPlayer);
            if (loadedCount >= REQUIRED_ITEM_COUNT) {
                loadedExit.activate(loadedWorld);
            }
        } else {
            loadedExit = saved.exit().toExit();
            loadedExit.syncTile(loadedWorld);
        }
        List<SceneObject> loadedObjects = new ArrayList<>();
        if (saved.sceneObjects() != null) {
            for (SceneObjectData object : saved.sceneObjects()) {
                loadedObjects.add(object.toSceneObject());
            }
        }
        int loadedRadius = saved.visionRadius() > 0
            ? saved.visionRadius() : DEFAULT_VISION_RADIUS;
        boolean[][] loadedExplored = saved.explored() == null
            ? new boolean[loadedWorld.length][loadedWorld[0].length] : copyGrid(saved.explored());
        boolean[][] loadedVisible = Visioncauculate.calculate(loadedWorld, loadedPlayer, loadedRadius);
        for (int x = 0; x < loadedWorld.length; x++) {
            for (int y = 0; y < loadedWorld[0].length; y++) {
                loadedExplored[x][y] |= loadedVisible[x][y];
            }
        }

        RealtimeData realtime = saved.realtime();
        if (realtime != null) {
            loadedPlayer.motion = realtime.playerMotion();
            if (realtime.monsters() != null) {
                for (Monster monster : loadedMonsters) {
                    MonsterActivity activity = realtime.monsters().get(monster.id());
                    if (activity != null) {
                        monster.activity = activity;
                    }
                }
            }
        }

        world = loadedWorld;
        roomDesigns = layout.rooms();
        player = loadedPlayer;
        items = loadedItems;
        monsters.clear();
        monsters.addAll(loadedMonsters);
        sceneObjects.clear();
        sceneObjects.addAll(loadedObjects);
        exit = loadedExit;
        collectedCount = loadedCount;
        random = new GameRandom(saved.randomState());
        long minimumNextId = monsters.stream().mapToLong(Monster::id).max().orElse(0L) + 1;
        nextMonsterId = Math.max(saved.nextMonsterId(), minimumNextId);
        freezeTicks = realtime == null
            ? saved.frozenMonsterTurns() * Timing.HZ : Math.max(0, realtime.freezeTicks());
        protectionTicks = realtime == null ? 0 : Math.max(0, realtime.protectionTicks());
        frozenThisTick = false;
        accumulator = 0;
        travelDirection = 0;
        movementInput.clear();
        feedback.clear();
        radarActive = saved.radarActive();
        seenMonsterTypes.clear();
        if (saved.seenMonsterTypes() != null) {
            seenMonsterTypes.addAll(saved.seenMonsterTypes());
        }
        monsterNoticeType = null;
        monsterNoticeTicks = 0;
        visionRadius = loadedRadius;
        explored = loadedExplored;
        visible = loadedVisible;
        hasUnsavedChanges = false;
        state = !player.isAlive() ? GameState.GAME_OVER
            : exit.active() && exit.contains(player.x(), player.y()) ? GameState.WIN
            : GameState.PAUSE;
    }

    /**
     * 保存游戏于对应存档位中
     * */
    private void saveGame(int slot) {
        // 求物品的存档数组
        List<ItemData> itemDataList = new ArrayList<>();

        for (Item item : items) {
            itemDataList.add(
                new ItemData(
                    item.x(),
                    item.y(),
                    item.type()
                )
            );
        }

        List<MonsterData> monsterDataList = new ArrayList<>();
        for (Monster monster : monsters) {
            monsterDataList.add(MonsterData.from(monster));
        }

        List<SceneObjectData> sceneObjectDataList = new ArrayList<>();
        for (SceneObject object : sceneObjects) {
            sceneObjectDataList.add(SceneObjectData.from(object));
        }

        GameSave gameSave = new GameSave(
            new WorldData(toTileType(world), roomDesigns),
            new PlayerData(
                player.x(),
                player.y(),
                player.lastMoveDx(),
                player.lastMoveDy(),
                player.currentHp(),
                player.maxHp(),
                player.shieldCharges()
            ),
            itemDataList,
            monsterDataList,
            copyGrid(explored),
            visionRadius,
            random.state(),
            nextMonsterId,
            0,
            radarActive,
            new ArrayList<>(seenMonsterTypes),
            ExitData.from(exit),
            sceneObjectDataList,
            SaveManager.FORMAT_VERSION,
            new RealtimeData(player.motion, monsters.stream().collect(
                java.util.stream.Collectors.toMap(Monster::id, m -> m.activity)),
                protectionTicks, freezeTicks)
            );
        saveManager.save(slot, gameSave);
        hasUnsavedChanges = false;
    }

    // =====================
    // Getters
    // =====================

    public String uiMessage() {
        return uiMessage;
    }

    public int pendingOverwriteSlot() {
        return pendingOverwriteSlot;
    }

    public boolean saveSlotExists(int slot) {
        saveManager.validateSlot(slot);
        return occupiedSaveSlots[slot - 1];
    }

    public GameState state() {
        return state;
    }

    public TETile[][] world() {
        return world;
    }

    public Player player() {
        return player;
    }

    public List<Item> items() {
        return items;
    }

    public List<Monster> monsters() {
        return Collections.unmodifiableList(monsters);
    }

    public List<SceneObject> sceneObjects() {
        return Collections.unmodifiableList(sceneObjects);
    }

    public String seedString() {
        return seedString.toString();
    }

    public boolean shouldQuit() {
        return shouldQuit;
    }

    public int collectedCount() {
        return collectedCount;
    }

    public int visionRadius() {
        return visionRadius;
    }


    public boolean radarActive() {
        return radarActive;
    }

    /** Radar 激活时返回出口相对玩家的八方向提示。 */
    public String radarDirection() {
        if (!radarActive || exit == null) {
            return "";
        }

        int dx = Integer.compare(exit.x(), player.x());
        int dy = Integer.compare(exit.y(), player.y());
        if (dx == 0 && dy == 0) {
            return "当前位置";
        }

        String vertical = dy > 0 ? "北" : dy < 0 ? "南" : "";
        String horizontal = dx > 0 ? "东" : dx < 0 ? "西" : "";
        return vertical + horizontal;
    }

    public GameExit exit() {
        return exit;
    }

    /** 返回当前首次进入视野、需要在 HUD 中介绍的怪物类型。 */
    public MonsterType monsterNoticeType() {
        return monsterNoticeType;
    }

    /**
     * 返回当前视野中离玩家最近的遗物；视野外遗物不属于“附近”。
     */
    public Item nearbyItem() {
        Item nearest = null;
        int nearestDistance = Integer.MAX_VALUE;

        for (Item item : items) {
            if (!visible[item.x()][item.y()]) {
                continue;
            }
            int distance = Math.abs(item.x() - player.x())
                + Math.abs(item.y() - player.y());
            if (distance < nearestDistance) {
                nearest = item;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public boolean[][] visible() {
        return visible;
    }

    public boolean[][] explored() {
        return explored;
    }
}
