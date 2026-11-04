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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 화재(산불). 플레이어 주변 여러 곳에서 불이 나 바람을 따라 번집니다.
 * 불씨가 바람에 날려 멀리서도 새로 불이 붙고, 탄 자리는 나무·잎이 사라지고 땅이 그을립니다.
 * 짙은 연기 속에서는 앞이 잘 보이지 않습니다. 재해가 끝나면 남은 불은 꺼집니다.
 *
 * <p>바닐라 불 번짐(doFireTick)을 쓰지 않고 직접 번지게 해서 번지는 정도를 설정으로 조절할 수 있습니다.
 */
public final class Wildfire extends Disaster {

    private static final BlockFace[] SIDES = {BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP};

    private final TemporaryBlocks fires = new TemporaryBlocks();
    /** 지금 타고 있는 불 → 붙은 시각(틱) */
    private final Map<Block, Integer> burning = new LinkedHashMap<>();
    private Vector wind;
    private BlockData fireData;

    public Wildfire(QuantumDisasters plugin) {
        super(plugin, "wildfire");
    }

    @Override
    public Component displayName() {
        return Component.text("화재", NamedTextColor.RED);
    }

    @Override
    public Component description() {
        return Component.text("불길이 번집니다! 물가나 맨땅으로 피하세요!", NamedTextColor.GOLD);
    }

    @Override
    public void start() {
        setWeather(false, false, durationTicks() + 200);
        burning.clear();
        double angle = random().nextDouble(Math.PI * 2);
        wind = new Vector(Math.cos(angle), 0, Math.sin(angle));
        fireData = Material.FIRE.createBlockData();
    }

    @Override
    public void tick(int elapsed) {
        currentTick = elapsed;
        List<Player> targets = targets();
        if (targets.isEmpty()) {
            return;
        }
        // 새 발화점
        if (elapsed % 60 == 1) {
            int count = scaled("ignitions", 3);
            for (int i = 0; i < count; i++) {
                Player target = targets.get(random().nextInt(targets.size()));
                ignitePoint(target.getLocation(), random().nextInt(12, config().getInt("radius", 40)));
            }
        }
        if (elapsed % 3 == 0) {
            spread();
        }
        burnOut(elapsed);
        if (elapsed % 10 == 0) {
            effects(targets);
        }
    }

    // ───────────────────────── 불 붙이기 / 번지기 ─────────────────────────

    /** center 에서 distance 쯤 떨어진, 탈 것이 있는 곳에 불을 붙입니다. */
    private void ignitePoint(Location center, int distance) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random().nextDouble(Math.PI * 2);
            Location at = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            Block surface = randomSurface(at, 3);
            if (surface == null) {
                continue;
            }
            Block air = surface.getRelative(BlockFace.UP);
            if (canIgnite(air)) {
                ignite(air);
                return;
            }
        }
    }

    private void spread() {
        int max = config().getInt("max-fires", 600);
        if (burning.isEmpty()) {
            return;
        }
        double chance = Math.min(1, config().getDouble("spread-chance", 0.35) * intensity());
        List<Block> snapshot = new ArrayList<>(burning.keySet());
        int budget = Math.min(snapshot.size(), 150);
        for (int i = 0; i < budget && burning.size() < max; i++) {
            Block fire = snapshot.get(random().nextInt(snapshot.size()));
            if (!chance(chance)) {
                continue;
            }
            // 바람 부는 쪽으로 더 잘 번짐
            double bias = chance(0.6) ? 1.5 : 0;
            int dx = (int) Math.round(random().nextInt(-2, 3) + wind.getX() * bias);
            int dz = (int) Math.round(random().nextInt(-2, 3) + wind.getZ() * bias);
            int dy = random().nextInt(-1, 3);
            Block target = fire.getRelative(dx, dy, dz);
            if (canIgnite(target)) {
                ignite(target);
            }
            // 가끔 불씨가 바람에 날려 멀리 떨어진 곳에 불이 붙습니다.
            if (chance(0.03)) {
                double far = random().nextDouble(5, 12);
                Location spot = fire.getLocation().add(wind.clone().multiply(far));
                Block surface = randomSurface(spot, 2);
                if (surface != null && canIgnite(surface.getRelative(BlockFace.UP))) {
                    ignite(surface.getRelative(BlockFace.UP));
                    fire.getWorld().spawnParticle(Particle.LAVA, fire.getLocation().add(0.5, 1, 0.5), 3, 0.2, 0.2, 0.2, 0);
                }
            }
        }
    }

    /** 공기 자리이고, 옆이나 아래에 탈 것이 있으면 불이 붙을 수 있습니다. */
    private boolean canIgnite(Block block) {
        if (!block.getType().isAir() || burning.containsKey(block)) {
            return false;
        }
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
            return false;
        }
        Block below = block.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid() && !isFuel(below)) {
            return false; // 공중에는 안 붙음
        }
        for (BlockFace face : SIDES) {
            if (isFuel(block.getRelative(face))) {
                return true;
            }
        }
        return below.getType() == Material.GRASS_BLOCK; // 풀밭
    }

    private static boolean isFuel(Block block) {
        Material type = block.getType();
        return type.isBurnable() || type.isFlammable() || type == Material.SHORT_GRASS || type == Material.TALL_GRASS;
    }

    private void ignite(Block block) {
        fires.place(block, fireData);
        burning.put(block, currentTick);
    }

    private int currentTick;

    // ───────────────────────── 타서 없어짐 ─────────────────────────

    private void burnOut(int elapsed) {
        int burnTicks = config().getInt("burn-ticks", 100);
        List<Block> done = new ArrayList<>();
        for (Map.Entry<Block, Integer> e : burning.entrySet()) {
            if (elapsed - e.getValue() >= burnTicks + (e.getKey().hashCode() & 31)) {
                done.add(e.getKey());
            }
        }
        for (Block fire : done) {
            burning.remove(fire);
            if (blockDamage()) {
                scorch(fire);
            }
            fires.restore(fire); // 불 끄기
        }
    }

    /** 불 주변의 탈 것을 태웁니다. */
    private void scorch(Block fire) {
        for (BlockFace face : SIDES) {
            Block b = fire.getRelative(face);
            Material type = b.getType();
            if (Tag.LEAVES.isTagged(type) || type == Material.SHORT_GRASS || type == Material.TALL_GRASS
                    || Tag.FLOWERS.isTagged(type) || Tag.WOOL.isTagged(type) || Tag.PLANKS.isTagged(type)
                    || Tag.WOODEN_STAIRS.isTagged(type) || Tag.WOODEN_SLABS.isTagged(type) || Tag.WOODEN_FENCES.isTagged(type)) {
                b.setType(Material.AIR, false);
            } else if (Tag.LOGS.isTagged(type)) {
                // 통나무는 절반쯤 타 없어지고 나머지는 숯처럼 검게
                b.setType(chance(0.5) ? Material.AIR : Material.BASALT, false);
            } else if (face == BlockFace.DOWN && (type == Material.GRASS_BLOCK || type == Material.PODZOL)) {
                b.setType(Material.COARSE_DIRT, false);
            }
        }
    }

    // ───────────────────────── 효과 ─────────────────────────

    private void effects(List<Player> targets) {
        for (Block fire : burning.keySet()) {
            if (chance(0.15)) {
                Location p = fire.getLocation().add(0.5, 0.5, 0.5);
                fire.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, p, 1, 0.2, 0.5, 0.2, 0.02);
                fire.getWorld().spawnParticle(Particle.LARGE_SMOKE, p, 2, 0.4, 0.4, 0.4, 0.02);
            }
        }
        for (Player player : targets) {
            Location loc = player.getLocation();
            int near = 0;
            for (Block fire : burning.keySet()) {
                if (fire.getWorld() != loc.getWorld()) {
                    continue;
                }
                double d = fire.getLocation().distanceSquared(loc);
                if (d < 9) {
                    player.setFireTicks(Math.max(player.getFireTicks(), 60)); // 불길 바로 옆은 뜨거움
                }
                if (d < 144) {
                    near++;
                }
            }
            // 연기가 짙으면 앞이 잘 안 보입니다.
            if (near >= 12) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, true, false));
            }
            if (near > 0 && random().nextInt(3) == 0) {
                player.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 2f, 0.6f);
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        burning.clear();
        fires.restoreGradually(plugin, 500); // 남은 불 끄기
    }
}
