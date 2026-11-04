package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 쓰나미. 거대한 물의 벽이 멀리서 밀려와 플레이어를 지나쳐 갑니다.
 *
 * <p>지형이 가장 낮은 쪽(바다일 가능성이 큰 쪽)에서 밀려오고, 지나가는 길의 나무·판자 같은 약한 블록을 쓸어가며
 * 사람과 물건을 휩쓸어 갑니다. 지나간 자리에는 얕은 물이 남았다가 재해가 끝나면 빠집니다.
 * 파도가 여러 번 옵니다. 남은 시간은 보여주지 않고, 마지막 파도가 지나가면 끝납니다.
 */
public final class Tsunami extends Disaster {

    private final class Wave {
        final World world;
        final double startX;
        final double startZ;
        final double dirX;
        final double dirZ;
        final int top;
        final int halfWidth;
        final int totalSteps;
        final Map<Integer, List<Block>> slabs = new HashMap<>();
        double distance;
        int lastStep = -1;
        boolean done;

        Wave(World world, double startX, double startZ, double dirX, double dirZ, int top, int halfWidth, int totalSteps) {
            this.world = world;
            this.startX = startX;
            this.startZ = startZ;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.top = top;
            this.halfWidth = halfWidth;
            this.totalSteps = totalSteps;
        }

        Location front() {
            return new Location(world, startX + dirX * distance, top - 4, startZ + dirZ * distance);
        }
    }

    private final TemporaryBlocks water = new TemporaryBlocks();
    private final List<Wave> waves = new ArrayList<>();
    private BlockData waterData;
    private int wavesLeft;
    private int nextWaveAt;
    private int finishedAt;

    public Tsunami(QuantumDisasters plugin) {
        super(plugin, "tsunami");
    }

    @Override
    public Component displayName() {
        return Component.text("쓰나미", NamedTextColor.DARK_AQUA);
    }

    @Override
    public Component description() {
        return Component.text("거대한 파도가 밀려옵니다! 높은 곳으로!", NamedTextColor.AQUA);
    }

    @Override
    public boolean hasFixedDuration() {
        return false;
    }

    @Override
    public void start() {
        waves.clear();
        waterData = Material.WATER.createBlockData();
        wavesLeft = scaled("waves", 2);
        nextWaveAt = 20 * config().getInt("first-wave-delay-seconds", 8);
        finishedAt = -1;
        setWeather(true, false, 20 * 300);
    }

    @Override
    public void tick(int elapsed) {
        // 파도가 오기 전 바다 쪽에서 우르릉
        if (wavesLeft > 0 && elapsed == nextWaveAt - 80) {
            for (Player player : targets()) {
                player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 0.4f);
                player.playSound(player.getLocation(), Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, 2f, 0.5f);
            }
        }
        if (wavesLeft > 0 && elapsed >= nextWaveAt) {
            spawnWave();
            wavesLeft--;
            nextWaveAt = elapsed + 20 * config().getInt("seconds-between-waves", 25);
        }
        for (Wave wave : waves) {
            if (!wave.done) {
                advance(wave, elapsed);
            }
        }
        if (finishedAt < 0 && wavesLeft <= 0 && !waves.isEmpty() && waves.stream().allMatch(w -> w.done)) {
            finishedAt = elapsed;
        }
    }

    @Override
    public boolean isOver(int elapsed) {
        if (finishedAt >= 0 && elapsed - finishedAt > 100) {
            return true;
        }
        // 대상이 없어 파도를 하나도 못 만들었으면 끝냄
        return wavesLeft <= 0 && waves.isEmpty();
    }

    // ───────────────────────── 파도 만들기 ─────────────────────────

    private boolean spawnWave() {
        List<Player> targets = targets();
        if (targets.isEmpty()) {
            return false;
        }
        Player target = targets.get(random().nextInt(targets.size()));
        Location center = target.getLocation();
        World world = center.getWorld();

        // 8방향 중 지형이 가장 낮은 쪽에서 밀려옴 (바다 쪽)
        double bestAngle = 0;
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4 + random().nextDouble(-0.2, 0.2);
            int x = (int) (center.getX() + Math.cos(angle) * 60);
            int z = (int) (center.getZ() + Math.sin(angle) * 60);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int h = world.getHighestBlockYAt(x, z);
            if (h < lowest) {
                lowest = h;
                bestAngle = angle;
            }
        }
        double dirX = -Math.cos(bestAngle); // 낮은 쪽에서 플레이어 쪽으로
        double dirZ = -Math.sin(bestAngle);
        int startDistance = config().getInt("start-distance", 70);
        double startX = center.getX() - dirX * startDistance;
        double startZ = center.getZ() - dirZ * startDistance;
        int height = (int) Math.round(config().getInt("height", 12) * Math.min(2, intensity()));
        int top = Math.max(world.getSeaLevel(), center.getBlockY()) + height;
        int halfWidth = config().getInt("width", 90) / 2;
        int steps = startDistance + config().getInt("travel-beyond", 70);

        waves.add(new Wave(world, startX, startZ, dirX, dirZ, top, halfWidth, steps));
        for (Player player : targets) {
            player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.5f, 0.4f);
        }
        return true;
    }

    // ───────────────────────── 파도 움직이기 ─────────────────────────

    private void advance(Wave wave, int elapsed) {
        wave.distance += config().getDouble("speed", 0.55);
        int step = (int) Math.floor(wave.distance);
        int thickness = 6;
        while (wave.lastStep < step) {
            wave.lastStep++;
            if (wave.lastStep <= wave.totalSteps) {
                buildSlab(wave, wave.lastStep);
            }
            lowerSlab(wave, wave.lastStep - thickness);
        }
        if (wave.lastStep > wave.totalSteps + thickness) {
            wave.done = true;
            return;
        }
        sweep(wave);
        if (elapsed % 10 == 0) {
            foam(wave);
        }
        if (elapsed % 20 == 0) {
            Location front = wave.front();
            wave.world.playSound(front, Sound.WEATHER_RAIN_ABOVE, 8f, 0.4f);
            wave.world.playSound(front, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 8f, 0.5f);
            wave.world.playSound(front, Sound.BLOCK_WATER_AMBIENT, 8f, 0.5f);
        }
    }

    /** 파도 앞부분 한 줄(파도 진행 방향에 수직)을 물로 채웁니다. */
    private void buildSlab(Wave wave, int step) {
        List<Block> placed = new ArrayList<>();
        double px = -wave.dirZ;
        double pz = wave.dirX;
        int height = wave.top - wave.world.getSeaLevel();
        for (int l = -wave.halfWidth; l <= wave.halfWidth; l++) {
            int x = (int) Math.floor(wave.startX + wave.dirX * step + px * l);
            int z = (int) Math.floor(wave.startZ + wave.dirZ * step + pz * l);
            if (!wave.world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            // 양 끝으로 갈수록 낮게
            double edge = Math.pow(Math.abs(l) / (double) wave.halfWidth, 4);
            int top = (int) Math.round(wave.top - edge * Math.max(4, height));

            int y = wave.world.getHighestBlockYAt(x, z);
            // 약한 블록(나무, 판자, 유리 등)은 쓸어갑니다.
            int guard = 0;
            while (blockDamage() && guard++ < 24 && y > wave.world.getMinHeight()) {
                Block b = wave.world.getBlockAt(x, y, z);
                if (!isWeak(b)) {
                    break;
                }
                b.setType(Material.AIR, false);
                y--;
            }
            for (int yy = y + 1; yy <= top; yy++) {
                Block b = wave.world.getBlockAt(x, yy, z);
                Material type = b.getType();
                if (type.isAir() || (!type.isSolid() && !b.isLiquid())) {
                    water.place(b, waterData);
                    placed.add(b);
                } else if (type != Material.WATER) {
                    break;
                }
            }
        }
        wave.slabs.put(step, placed);
    }

    /** 파도가 지나간 줄은 얕은 물(바닥 2칸)만 남기고 빠집니다. */
    private void lowerSlab(Wave wave, int step) {
        List<Block> placed = wave.slabs.remove(step);
        if (placed == null) {
            return;
        }
        Map<Long, Integer> bottom = new HashMap<>();
        for (Block b : placed) {
            bottom.merge(((long) b.getX() << 32) ^ (b.getZ() & 0xFFFFFFFFL), b.getY(), Math::min);
        }
        for (Block b : placed) {
            int floor = bottom.get(((long) b.getX() << 32) ^ (b.getZ() & 0xFFFFFFFFL));
            if (b.getY() > floor + 1) {
                water.restore(b);
            }
        }
    }

    private static boolean isWeak(Block block) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) {
            return false;
        }
        return Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type) || Tag.PLANKS.isTagged(type)
                || Tag.WOODEN_FENCES.isTagged(type) || Tag.WOODEN_DOORS.isTagged(type) || Tag.WOODEN_SLABS.isTagged(type)
                || Tag.WOODEN_STAIRS.isTagged(type) || Tag.WOOL.isTagged(type) || Tag.FLOWERS.isTagged(type)
                || type.name().contains("GLASS") || !type.isSolid();
    }

    /** 파도 앞쪽에 있는 생물과 물건을 휩쓸어 갑니다. */
    private void sweep(Wave wave) {
        Location front = wave.front();
        double reach = wave.halfWidth + 4;
        for (Entity entity : wave.world.getNearbyEntities(front, reach, 20, reach)) {
            if (entity instanceof Player p && !targets().contains(p)) {
                continue;
            }
            double rx = entity.getLocation().getX() - wave.startX;
            double rz = entity.getLocation().getZ() - wave.startZ;
            double along = rx * wave.dirX + rz * wave.dirZ;
            double side = Math.abs(rx * -wave.dirZ + rz * wave.dirX);
            if (side > wave.halfWidth || along > wave.distance + 1.5 || along < wave.distance - 8
                    || entity.getLocation().getY() > wave.top + 2) {
                continue;
            }
            Vector push = new Vector(wave.dirX, 0, wave.dirZ).multiply(config().getDouble("push", 0.9)).setY(0.15);
            push(entity, push);
            entity.setFallDistance(0);
        }
    }

    private void foam(Wave wave) {
        double px = -wave.dirZ;
        double pz = wave.dirX;
        for (int l = -wave.halfWidth; l <= wave.halfWidth; l += 3) {
            Location p = new Location(wave.world,
                    wave.startX + wave.dirX * wave.distance + px * l,
                    wave.top + 0.5,
                    wave.startZ + wave.dirZ * wave.distance + pz * l);
            wave.world.spawnParticle(Particle.SPLASH, p, 6, 0.6, 0.3, 0.6, 0);
            wave.world.spawnParticle(Particle.CLOUD, p, 1, 0.4, 0.2, 0.4, 0.02);
        }
    }

    @Override
    public void stop() {
        super.stop();
        waves.clear();
        water.restoreGradually(plugin, 1500);
    }
}
