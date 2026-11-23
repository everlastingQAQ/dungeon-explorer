package byow.game.render.ui;

import byow.game.Engine;
import byow.game.item.Item;
import byow.game.item.ItemRules;
import byow.game.monster.Monster;
import byow.game.monster.MonsterType;
import byow.game.scene.SceneObject;
import byow.game.scene.SceneObjectType;

/** 为当前可见格生成鼠标悬停说明。 */
public final class HoverDescriptions {

    public static String forTile(Engine engine, int x, int y) {
        if (engine.player().motion == null && engine.player().x() == x && engine.player().y() == y) {
            return "玩家：当前生命 " + engine.player().currentHp()
                + " / " + engine.player().maxHp() + "。";
        }

        for (Monster monster : engine.monsters()) {
            if (monster.x() == x && monster.y() == y) {
                if (monster.activity.motion == null) return monsterDescription(engine, monster);
            }
        }

        for (Item item : engine.items()) {
            if (item.x() == x && item.y() == y) {
                return itemDescription(item);
            }
        }

        for (SceneObject object : engine.sceneObjects()) {
            if (object.active() && object.contains(x, y)) {
                return sceneObjectDescription(object.type());
            }
        }

        if (engine.exit().contains(x, y)) {
            String name = engine.exit().width() == 2 ? "双格出口：" : "出口：";
            return engine.exit().active()
                ? name + "已经激活，进入即可获胜。"
                : name + "收集 3 件遗物后开启。";
        }

        if (byow.game.door.DoorLayout.isDoor(engine.world()[x][y])
            || y > 0 && (byow.game.door.DoorLayout.isDoor(engine.world()[x][y - 1])
                || engine.exit().contains(x, y - 1))) {
            var door = byow.game.door.DoorLayout.drawnAt(engine.world(), engine.roomDesigns(), engine.exit(), x, y);
            if (door != null) {
                if (door.exit()) return door.open()
                    ? "双格出口：已经激活，进入即可获胜。"
                    : "双格出口：收集 3 件遗物后开启。";
                String name = door.width() == 2 ? "双格木门：" : "房门：";
                return name + (door.open() ? "已经打开，可以通行。" : "尚未开启，不可通行，阻挡视野。");
            }
        }

        String terrain = terrainDescription(engine.world()[x][y].description());
        for (var room : engine.roomDesigns()) {
            if (!room.contains(x, y)) continue;
            var furniture = room.furnitureAt(x, y);
            if (furniture != null) return room.theme().displayName() + " · " + furniture.type().displayName()
                + "：不可通行，" + (furniture.type().blocksVision() ? "阻挡视野" : "不阻挡视野") + "，无交互效果。";
            var decoration = room.decorationAt(x, y);
            return room.theme().displayName() + " · " + (decoration == null ? terrain
                : decoration.mark().displayName() + "：可通行，无交互效果。");
        }
        return terrain;
    }

    public static String itemDescription(Item item) {
        ItemRules.Rule rule = ItemRules.ruleFor(item.type());
        return item.type().displayName()
            + "：拾取会激活 " + rule.monsterType().displayName()
            + "；第 4、5 件会获得“" + rule.buffType().displayName() + "”增益。";
    }

    public static String monsterDescription(Engine engine, Monster monster) {
        var a = monster.activity;
        var pace = byow.game.realtime.Timing.pace(monster.type());
        String name = monster.type().displayName() + "：";
        String frozen = engine.freezeTicks() > 0 ? "冻结中，剩余 " + seconds(engine.freezeTicks()) + " 秒；" : "";
        if (a.restTicks > 0) return name + frozen + "喘息中，剩余 " + seconds(a.restTicks)
            + " 秒；可无伤换位通过。";
        String behavior = switch (monster.state()) {
            case CHASING -> "追击中";
            case FLEEING -> "逃跑中";
            case PATROLLING -> "巡逻中";
            default -> "守卫中";
        };
        return name + frozen + behavior + "，已走 " + a.walked + "/" + pace.stepsBeforeRest()
            + " 格；再走 " + Math.max(0, pace.stepsBeforeRest() - a.walked)
            + " 格后喘息 " + seconds(pace.rest()) + " 秒。";
    }
    public static String seconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 60.0);
    }

    public static String monsterDescription(MonsterType type) {
        return switch (type) {
            case HUNTER -> "猎手：锁定玩家位置 两次决策并通过广度优先寻路追踪。";
            case AMBUSHER -> "伏击者：预测玩家移动方向前方最多 3 格。";
            case GUARDIAN -> "守卫：玩家靠近守护点时开始追击。";
            case PATROLLER -> "巡逻者：在两个远端点之间巡逻。";
            case COWARD -> "胆小鬼：靠近玩家时逃跑，拉开距离后追击。";
        };
    }

    public static String sceneObjectDescription(SceneObjectType type) {
        return switch (type) {
            case SPIKE_TRAP -> "尖刺陷阱：踩上去会失去 1 点生命。";
            case HEALING_FOUNTAIN -> "治愈泉：生命未满时恢复 1 点生命，只能使用一次。";
            case PORTAL -> "传送门：进入后传送到另一座传送门。";
            case BLESSING_CHEST -> "祝福宝箱：打开后随机获得一种增益，只能使用一次。";
        };
    }

    private static String terrainDescription(String description) {
        return switch (description) {
            case "floor" -> "地面：可以通行。";
            case "wall" -> "墙壁：不可通行，并会阻挡视野。";
            case "pillar" -> "石柱：不可通行，并会阻挡视野，可以绕行。";
            case "furniture" -> "陈设：不可通行，不阻挡视野，无交互效果。";
            case "tall furniture" -> "陈设：不可通行，阻挡视野，无交互效果。";
            case "nothing" -> "虚空：不可通行。";
            case "grass" -> "草地：可以通行。";
            case "water" -> "水面：不可通行。";
            case "flower" -> "花地：可以通行。";
            case "locked door" -> "关闭的出口：尚未开启。";
            case "unlocked door" -> "开放的出口：可以通行。";
            case "sand" -> "沙地：可以通行。";
            case "moss" -> "苔藓地：可以通行的潮湿地面。";
            case "cracked floor" -> "裂纹地面：可以通行的破损地面。";
            case "mud" -> "泥地：可以通行的松软地面。";
            case "pebbles" -> "碎石地：可以通行的碎石地面。";
            case "worn floor" -> "磨损石板：可以通行，表面留下了细小磨痕。";
            case "stained floor" -> "污渍石板：可以通行，颜色比周围略深。";
            case "chipped floor" -> "缺角石板：可以通行，边缘已有破损。";
            case "mountain" -> "山地：不可通行。";
            case "tree" -> "树木：不可通行。";
            case "rock" -> "岩石：不可通行。";
            case "bridge" -> "桥梁：可以通行。";
            default -> "未知区域。";
        };
    }

    private HoverDescriptions() {}
}
