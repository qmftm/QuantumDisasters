package com.qmftm.quantumdisasters.disaster;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 재해가 잠시 놓은 블록(물, 불 등)을 기억했다가 원래대로 되돌립니다.
 * 되돌릴 때 그 자리가 아직 재해가 놓은 블록일 때만 되돌립니다. (그 사이 플레이어가 바꿨으면 그대로 둠)
 */
public final class TemporaryBlocks {

    /** 천천히 되돌리는 중인 작업들 (서버가 꺼질 때 남은 것을 바로 되돌리기 위해) */
    private static final List<Iterator<Map.Entry<Pos, Entry>>> PENDING = new ArrayList<>();

    /** 플러그인이 꺼질 때 호출: 되돌리던 중인 블록을 바로 전부 되돌립니다. */
    public static void flushPending() {
        for (Iterator<Map.Entry<Pos, Entry>> it : PENDING) {
            while (it.hasNext()) {
                Map.Entry<Pos, Entry> e = it.next();
                restore(e.getKey(), e.getValue());
            }
        }
        PENDING.clear();
    }

    private record Pos(World world, int x, int y, int z) {
        Block block() {
            return world.getBlockAt(x, y, z);
        }
    }

    private record Entry(BlockData original, Material placed) {
    }

    private final Map<Pos, Entry> placed = new LinkedHashMap<>();

    int size() {
        return placed.size();
    }

    boolean contains(Block block) {
        return placed.containsKey(new Pos(block.getWorld(), block.getX(), block.getY(), block.getZ()));
    }

    /** 블록을 바꾸고 원래 모습을 기억합니다. (주변 블록 업데이트 없이: 물이 흘러넘치지 않음) */
    void place(Block block, BlockData data) {
        Pos pos = new Pos(block.getWorld(), block.getX(), block.getY(), block.getZ());
        placed.putIfAbsent(pos, new Entry(block.getBlockData(), data.getMaterial()));
        block.setBlockData(data, false);
    }

    /** 블록 하나를 바로 되돌립니다. */
    void restore(Block block) {
        Pos pos = new Pos(block.getWorld(), block.getX(), block.getY(), block.getZ());
        Entry entry = placed.remove(pos);
        if (entry != null) {
            restore(pos, entry);
        }
    }

    /** 되돌리지 않고 잊습니다. (영구적으로 남길 때) */
    void forget(Block block) {
        placed.remove(new Pos(block.getWorld(), block.getX(), block.getY(), block.getZ()));
    }

    /** 전부 되돌리지 않고 잊습니다. (그대로 남김) */
    void forgetAll() {
        placed.clear();
    }

    /** 기억한 블록을 전부, 1틱에 perTick 개씩 천천히 되돌립니다. */
    void restoreGradually(Plugin plugin, int perTick) {
        if (placed.isEmpty()) {
            return;
        }
        if (!plugin.isEnabled()) {
            restoreAllNow(); // 플러그인이 꺼지는 중이면 예약 작업을 쓸 수 없으므로 바로
            return;
        }
        // 위에서부터 되돌려야 물이 위에서 아래로 빠지는 것처럼 보입니다.
        List<Map.Entry<Pos, Entry>> queue = new ArrayList<>(placed.entrySet());
        queue.sort((a, b) -> Integer.compare(b.getKey().y(), a.getKey().y()));
        placed.clear();
        Iterator<Map.Entry<Pos, Entry>> it = queue.iterator();
        PENDING.add(it);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            for (int i = 0; i < perTick && it.hasNext(); i++) {
                Map.Entry<Pos, Entry> e = it.next();
                restore(e.getKey(), e.getValue());
            }
            if (!it.hasNext()) {
                PENDING.remove(it);
                task.cancel();
            }
        }, 1L, 1L);
    }

    /** 서버가 꺼질 때처럼 지금 바로 전부 되돌립니다. */
    void restoreAllNow() {
        for (Map.Entry<Pos, Entry> e : placed.entrySet()) {
            restore(e.getKey(), e.getValue());
        }
        placed.clear();
    }

    private static void restore(Pos pos, Entry entry) {
        if (!pos.world().isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
            return;
        }
        Block block = pos.block();
        if (block.getType() == entry.placed()) {
            block.setBlockData(entry.original(), false);
        }
    }
}
