package byow.game.worldGenerator;

import byow.game.random.GameRandom;
import byow.game.tile.TETile;
import byow.game.tile.Tileset;

import static byow.game.GameConfig.WORLD_HEIGHT;
import static byow.game.GameConfig.WORLD_WIDTH;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责生成完整的游戏世界。
 *
 * <p>地图生成依次经过房间生成、迷宫生成、区域连接、
 * 死胡同移除、外墙重建、房间主题与室内布局这几个阶段。
 * 对于相同的随机种子，生成的地图应当保持一致。</p>
 *
 * <p>通过 {@link #generate(long)} 方法生成地图。</p>
 *
 * @author everlasting icovo
 */
public class WorldGenerator {

    public static TETile[][] generate(long seed) {
        return generateDesigned(seed).terrain();
    }

    public static GeneratedWorld generateDesigned(long seed) {

        GameRandom random = new GameRandom(seed);
        TETile[][] world = new TETile[WORLD_WIDTH][WORLD_HEIGHT];
        int[][] regions = new int[WORLD_WIDTH][WORLD_HEIGHT];
        List<Room> rooms = new ArrayList<>();

        initWorld(world);

        // 生成房间
        RoomGenerator roomGenerator = new RoomGenerator();
        roomGenerator.generate(world, regions, rooms, random);

        // 生成迷宫
        MazeGenerator mazeGenerator =
            new MazeGenerator(world, random, regions, getRegionSize(regions));
        mazeGenerator.generateMaze();

        // 生成树
        RegionConnector regionConnector = new RegionConnector();
        regionConnector.connect(world, regions, random);

        // 移走死胡同
        DeadEndRemover deadEndRemover = new DeadEndRemover(world, rooms);
        deadEndRemover.removeDeadEnd();

        // 地图拓扑完成后丢弃生成过程中的旧墙，按最终地面统一重建。
        WallGenerator.rebuild(world);

        return new GeneratedWorld(world, RoomStyler.style(world, rooms, seed));
    }

    /**
     * 初始化地图，将所有格子设置为墙壁。
     *
     * @param world 要初始化的地图
     */
    private static void initWorld(TETile[][] world) {
        for (int i = 0; i < world.length; i++) {
            for (int j = 0; j < world[0].length; j++) {
                world[i][j] = Tileset.WALL;
            }
        }
    }

    /**
     * 获取当前地图中最大的区域编号。
     *
     * <p>区域编号从 1 开始，0 表示该格子尚未属于任何区域，
     * 因此最大的区域编号可以作为当前已经生成的区域数量上界。</p>
     *
     * @param regions 每个格子所属的区域编号
     * @return 当前最大的区域编号
     */
    public static int getRegionSize(int[][] regions) {
        int regionSize = 0;
        for (int[] region : regions) {
            for (int j = 0; j < regions[0].length; j++) {
                regionSize = Math.max(regionSize, region[j]);
            }
        }
        return regionSize;
    }
}
