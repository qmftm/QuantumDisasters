package com.qmftm.quantumdisasters.radiation;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * 어떤 위치에 방사선이 얼마나 닿는지 (0 = 완전히 막힘, 1 = 하늘이 뚫림).
 *
 * <p>머리 위로 수직 1줄 + 비스듬한 4줄을 쏘아 올려, 지나가는 블록마다 방사선이 조금씩 막힙니다.
 * 그래서 벙커 깊숙한 곳은 거의 0 이고, 입구나 창문 근처는 비스듬한 줄이 새어 들어와 수치가 올라갑니다.
 * 높은 곳일수록 방사선이 강해집니다 (실제로도 고도가 높을수록 우주 방사선이 강함).
 */
public final class Shielding {

    private static final int MAX_STEPS = 24;
    private static final int[][] RAYS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private Shielding() {
    }

    public static double exposure(Location loc) {
        World world = loc.getWorld();
        int x0 = loc.getBlockX();
        int y0 = (int) Math.floor(loc.getY() + 1.6); // 머리 높이
        int z0 = loc.getBlockZ();

        double total = 0;
        for (int[] ray : RAYS) {
            double pass = 1;
            int x = x0;
            int y = y0;
            int z = z0;
            boolean reachedSky = false;
            for (int step = 1; step <= MAX_STEPS && pass > 0.001; step++) {
                x = x0 + ray[0] * step;
                y = y0 + step;
                z = z0 + ray[1] * step;
                if (y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
                    reachedSky = true;
                    break;
                }
                pass *= 1 - absorption(world.getBlockAt(x, y, z));
            }
            // 천장이 아주 높은 동굴처럼, 다 올라가도 위에 아직 땅이 있으면 크게 막힌 것으로 봅니다.
            if (!reachedSky && pass > 0.001 && y < world.getHighestBlockYAt(x, z)) {
                pass *= 0.02;
            }
            // 수직 줄을 더 중요하게
            total += pass * (ray[0] == 0 && ray[1] == 0 ? 2 : 1);
        }
        double shielded = total / 6.0;

        // 높이 64 기준으로 50칸 올라갈 때마다 2배
        double altitude = Math.pow(2, (loc.getY() - 64) / 50.0);
        return shielded * Math.max(0.5, Math.min(4, altitude));
    }

    /** 블록 하나가 방사선을 막는 비율. */
    static double absorption(Block block) {
        Material type = block.getType();
        if (type.isAir()) {
            return 0;
        }
        if (block.isLiquid()) {
            return type == Material.LAVA ? 0.4 : 0.3; // 물도 좋은 차폐재
        }
        if (Tag.LEAVES.isTagged(type) || type.name().contains("GLASS")) {
            return 0.05;
        }
        if (!type.isSolid()) {
            return 0.02;
        }
        if (Tag.LOGS.isTagged(type) || Tag.PLANKS.isTagged(type) || type.name().contains("WOOL")) {
            return 0.15;
        }
        if (Tag.DIRT.isTagged(type) || Tag.SAND.isTagged(type) || type == Material.GRAVEL
                || type == Material.SNOW_BLOCK || type == Material.CLAY) {
            return 0.25;
        }
        return switch (type) {
            case IRON_BLOCK, GOLD_BLOCK, COPPER_BLOCK, NETHERITE_BLOCK, ANVIL -> 0.6;
            case OBSIDIAN, CRYING_OBSIDIAN, REINFORCED_DEEPSLATE -> 0.5;
            default -> 0.35; // 돌, 벽돌, 콘크리트 등
        };
    }
}
