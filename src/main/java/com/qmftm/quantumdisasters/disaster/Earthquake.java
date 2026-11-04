package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import com.qmftm.quantumdisasters.tectonics.Boundary;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 지진. 월드 시드로 정해진 지각판의 경계에서 일어납니다.
 *
 * <p>플레이어마다 가장 가까운 판 경계를 찾아 그곳이 진원이 되고, 경계에서 멀수록 약해집니다.
 * 경계 종류마다 일어나는 일이 다릅니다.
 * <ul>
 *     <li>발산형: 경계를 따라 땅이 벌어지며 깊게 갈라지고, 바닥에 용암이 비칩니다.</li>
 *     <li>수렴형: 경계를 따라 땅이 솟아올라 능선이 생기고, 바위가 굴러떨어집니다. 가장 세게 흔들립니다.</li>
 *     <li>보존형: 경계 건너편 땅이 경계를 따라 옆으로 어긋나고, 경계와 나란한 방향으로 흔들립니다.</li>
 * </ul>
 */
public final class Earthquake extends Disaster {

    private final Map<UUID, Boundary> boundaries = new HashMap<>();

    /** 경계에서 퍼져 나가는 땅의 파동 */
    private static final class Ripple {
        final Location center;
        final double maxRadius;
        double radius;

        Ripple(Location center, double maxRadius) {
            this.center = center;
            this.maxRadius = maxRadius;
        }
    }

    private final List<Ripple> ripples = new ArrayList<>();
    /** 발산형 균열이 시간이 갈수록 넓어지도록 */
    private int riftWidth;

    public Earthquake(QuantumDisasters plugin) {
        super(plugin, "earthquake");
    }

    @Override
    public Component displayName() {
        return Component.text("지진", NamedTextColor.DARK_RED);
    }

    @Override
    public Component description() {
        return Component.text("땅이 흔들립니다! 높은 곳을 조심하세요!", NamedTextColor.RED);
    }

    @Override
    public void start() {
        boundaries.clear();
        ripples.clear();
        riftWidth = 1;
    }

    @Override
    public void stop() {
        super.stop();
        boundaries.clear();
    }

    /** 플레이어 위치에서 가장 가까운 판 경계 (5초마다 다시 계산). */
    private Boundary boundaryFor(Player player, int elapsed) {
        Boundary b = boundaries.get(player.getUniqueId());
        if (b == null || elapsed % 100 == 0) {
            Location loc = player.getLocation();
            b = plugin.plates().of(player.getWorld()).boundaryAt(loc.getX(), loc.getZ());
            boundaries.put(player.getUniqueId(), b);
        }
        return b;
    }

    /** 경계에서 멀수록 약해지는 세기 (0.15 ~ 1). */
    private double strength(Boundary b, Player player) {
        double dx = player.getLocation().getX() - b.pointX();
        double dz = player.getLocation().getZ() - b.pointZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double falloff = config().getDouble("falloff", 200);
        return Math.max(0.15, 1 - distance / falloff);
    }

    @Override
    public void tick(int elapsed) {
        int radius = config().getInt("radius", 30);
        if (elapsed % 200 == 0) {
            riftWidth = Math.min(4, riftWidth + 1);
        }
        tickRipples();
        for (Player player : targets()) {
            Boundary b = boundaryFor(player, elapsed);
            double s = strength(b, player);

            if (elapsed % 3 == 0 && player.isOnGround()) {
                push(player, player.getVelocity().add(shake(b, s, elapsed)));
            }
            if (elapsed % 30 == 1) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, false));
                float volume = (float) (0.6 + s);
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, volume, 0.5f);
                player.playSound(player.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, volume, 0.4f);
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, (float) (0.4 * s), 0.4f);
            }
            if (elapsed % 5 == 0) {
                crackGround(player.getLocation(), radius, s);
            }
            if (elapsed % 8 == 0) {
                boundaryDust(player, b);
            }
            if (elapsed % 30 == 1) {
                // 경계 위 가까운 점에서 파동이 퍼져 나감
                Location from = new Location(player.getWorld(), b.pointX(), player.getLocation().getY(), b.pointZ());
                ripples.add(new Ripple(from, Math.min(60, b.distance() + 15)));
            }
            if (!isExposed(player) && elapsed % 4 == 0) {
                // 실내·동굴: 천장에서 부스러기가 떨어짐
                fx(Particle.FALLING_DUST, player.getLocation().add(0, 2.5, 0), 6, 3, 0.2, 3, 0, Material.STONE.createBlockData());
            }
            if (blockDamage() && elapsed % 40 == 20 && nearBoundary(b, player)) {
                switch (b.type()) {
                    case DIVERGENT -> rift(player.getWorld(), b, player);
                    case CONVERGENT -> uplift(player.getWorld(), b, player);
                    case TRANSFORM -> shear(player.getWorld(), b, player);
                }
            }
        }
    }

    /** 판 경계를 따라 흙먼지가 피어오릅니다. */
    private void boundaryDust(Player player, Boundary b) {
        World world = player.getWorld();
        for (int i = 0; i < 6; i++) {
            double t = random().nextDouble(-30, 30);
            int x = (int) Math.floor(b.pointX() + b.tangentX() * t);
            int z = (int) Math.floor(b.pointZ() + b.tangentZ() * t);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block ground = world.getHighestBlockAt(x, z);
            Location at = ground.getLocation().add(0.5, 1.2, 0.5);
            fx(Particle.BLOCK, at, 12, 0.6, 0.4, 0.6, 0, ground.getBlockData());
            fx(Particle.CAMPFIRE_COSY_SMOKE, at, 1, 0.4, 0.2, 0.4, 0.02);
        }
    }

    /** 파동 고리를 키워 나갑니다. 땅 표면을 따라 흙이 튀어 보입니다. */
    private void tickRipples() {
        java.util.Iterator<Ripple> it = ripples.iterator();
        while (it.hasNext()) {
            Ripple r = it.next();
            r.radius += 1.2;
            if (r.radius > r.maxRadius) {
                it.remove();
                continue;
            }
            int points = (int) (r.radius * 2.5);
            World world = r.center.getWorld();
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                int x = (int) Math.floor(r.center.getX() + Math.cos(a) * r.radius);
                int z = (int) Math.floor(r.center.getZ() + Math.sin(a) * r.radius);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                Block ground = world.getHighestBlockAt(x, z);
                fx(Particle.BLOCK, ground.getLocation().add(0.5, 1.1, 0.5), 2, 0.3, 0.1, 0.3, 0, ground.getBlockData());
            }
        }
    }

    private boolean nearBoundary(Boundary b, Player player) {
        double dx = player.getLocation().getX() - b.pointX();
        double dz = player.getLocation().getZ() - b.pointZ();
        double range = config().getDouble("effect-range", 120);
        return dx * dx + dz * dz <= range * range;
    }

    /** 경계 종류에 따라 다르게 흔들립니다. */
    private Vector shake(Boundary b, double s, int elapsed) {
        double m = 0.35 * s * Math.min(2, intensity());
        Vector random = new Vector(random().nextDouble(-m, m), 0.15 * s, random().nextDouble(-m, m));
        return switch (b.type()) {
            // 경계와 나란한 방향으로 앞뒤로 흔들림
            case TRANSFORM -> {
                double side = Math.sin(elapsed * 0.9) * m * 1.4;
                yield new Vector(b.tangentX() * side, 0.1 * s, b.tangentZ() * side).add(random.multiply(0.3));
            }
            // 위아래로 쿵쿵 치받음
            case CONVERGENT -> random.multiply(1.3).setY(chance(0.3) ? 0.35 * s : 0.15 * s);
            case DIVERGENT -> random;
        };
    }

    /** 주변 지면에 먼지가 튀고 일부 블록이 튀어오릅니다. */
    private void crackGround(Location center, int radius, double s) {
        int count = Math.max(1, (int) Math.round(scaled("blocks-per-burst", 6) * s));
        for (int i = 0; i < count; i++) {
            Block block = randomSurface(center, radius);
            if (block == null || Math.abs(block.getY() - center.getBlockY()) > 15) {
                continue;
            }
            center.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 1, 0.5), 20, 0.5, 0.1, 0.5, block.getBlockData());
            if (blockDamage() && isBreakable(block) && chance(0.4 * s)) {
                launchBlock(block, new Vector(random().nextDouble(-0.15, 0.15), random().nextDouble(0.4, 0.8), random().nextDouble(-0.15, 0.15)));
            }
        }
    }

    /** 경계 위에서 플레이어 근처의 한 구간 [시작점, 길이]. */
    private double[] segment(Boundary b, Player player) {
        // 경계 위에서 플레이어와 가장 가까운 점 기준으로 앞뒤 무작위 위치
        double offset = random().nextDouble(-25, 25);
        double x = b.pointX() + b.tangentX() * offset;
        double z = b.pointZ() + b.tangentZ() * offset;
        int length = (int) Math.round(random().nextInt(20, 36) * Math.min(2, intensity()));
        return new double[]{x - b.tangentX() * length / 2.0, z - b.tangentZ() * length / 2.0, length};
    }

    // ───────────────────────── 발산형: 땅이 벌어짐 ─────────────────────────

    private void rift(World world, Boundary b, Player player) {
        double[] seg = segment(b, player);
        int length = (int) seg[2];
        int depth = random().nextInt(6, 11);
        boolean lava = config().getBoolean("rift-lava", true);
        for (int i = 0; i < length; i++) {
            double cx = seg[0] + b.tangentX() * i + random().nextDouble(-0.4, 0.4);
            double cz = seg[1] + b.tangentZ() * i + random().nextDouble(-0.4, 0.4);
            // 양끝은 좁게, 가운데는 넓게
            int width = (i < 3 || i > length - 4) ? 1 : riftWidth;
            for (int w = 0; w < width; w++) {
                double off = w - (width - 1) / 2.0; // 경계를 가운데 두고 양쪽으로
                int bx = (int) Math.floor(cx + b.normalX() * off);
                int bz = (int) Math.floor(cz + b.normalZ() * off);
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                    continue;
                }
                int top = world.getHighestBlockYAt(bx, bz);
                int bottom = Math.max(world.getMinHeight() + 2, top - depth);
                for (int y = top; y > bottom; y--) {
                    Block block = world.getBlockAt(bx, y, bz);
                    if (isBreakable(block) || isPlant(block)) {
                        block.setType(Material.AIR, false);
                    }
                }
                Block floor = world.getBlockAt(bx, bottom, bz);
                if (lava && isBreakable(floor) && chance(0.15)) {
                    floor.setType(chance(0.5) ? Material.LAVA : Material.MAGMA_BLOCK);
                }
                Location dust = new Location(world, bx + 0.5, top + 1, bz + 0.5);
                world.spawnParticle(Particle.BLOCK, dust, 10, 0.4, 0.3, 0.4, Material.DIRT.createBlockData());
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, dust, 1, 0.2, 0.2, 0.2, 0.02);
            }
        }
        Location mid = new Location(world, seg[0], player.getLocation().getY(), seg[1]);
        world.playSound(mid, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.3f);
        world.playSound(mid, Sound.BLOCK_ANCIENT_DEBRIS_BREAK, 2f, 0.4f);
    }

    // ───────────────────────── 수렴형: 땅이 솟아오름 ─────────────────────────

    private void uplift(World world, Boundary b, Player player) {
        double[] seg = segment(b, player);
        int length = (int) seg[2];
        int halfWidth = 4;
        int peak = random().nextInt(1, 4);
        List<Location> ridgeTops = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            double cx = seg[0] + b.tangentX() * i;
            double cz = seg[1] + b.tangentZ() * i;
            // 양끝으로 갈수록 낮게
            double along = 1 - Math.abs(i - length / 2.0) / (length / 2.0);
            for (int w = -halfWidth; w <= halfWidth; w++) {
                int raise = (int) Math.round(peak * along * (1 - Math.abs(w) / (halfWidth + 1.0)) + random().nextDouble(-0.3, 0.3));
                if (raise <= 0) {
                    continue;
                }
                int bx = (int) Math.floor(cx + b.normalX() * w);
                int bz = (int) Math.floor(cz + b.normalZ() * w);
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                    continue;
                }
                Location top = raiseColumn(world, bx, bz, raise);
                if (top != null && w == 0 && i % 4 == 0) {
                    ridgeTops.add(top);
                }
            }
        }
        // 솟은 능선에서 바위가 굴러떨어집니다.
        for (Location top : ridgeTops) {
            if (!chance(0.5)) {
                continue;
            }
            FallingBlock rock = world.spawnFallingBlock(top.clone().add(0.5, 1, 0.5),
                    (chance(0.5) ? Material.COBBLESTONE : Material.STONE).createBlockData());
            rock.setDropItem(false);
            rock.setHurtEntities(true);
            double side = chance(0.5) ? 1 : -1;
            rock.setVelocity(new Vector(b.normalX() * side * 0.4, 0.3, b.normalZ() * side * 0.4));
            world.spawnParticle(Particle.BLOCK, top, 15, 0.5, 0.3, 0.5, Material.STONE.createBlockData());
        }
        Location mid = new Location(world, seg[0], player.getLocation().getY(), seg[1]);
        world.playSound(mid, Sound.BLOCK_DEEPSLATE_BREAK, 3f, 0.3f);
        world.playSound(mid, Sound.ENTITY_IRON_GOLEM_DEATH, 1.5f, 0.3f);
    }

    /** (x, z) 기둥의 지표면을 raise 칸 들어올립니다. 새 꼭대기 위치를 돌려줍니다. */
    private Location raiseColumn(World world, int x, int z, int raise) {
        Block top = world.getHighestBlockAt(x, z);
        if (!isBreakable(top)) {
            return null;
        }
        for (int i = 1; i <= raise; i++) {
            Block above = top.getRelative(0, i, 0);
            if (!above.getType().isAir() && !isPlant(above)) {
                return null; // 위에 나무나 건물이 있으면 건드리지 않음
            }
        }
        BlockData surface = top.getBlockData();
        Material filler = fillerFor(top.getType());
        top.setType(filler, false);
        for (int i = 1; i < raise; i++) {
            top.getRelative(0, i, 0).setType(filler, false);
        }
        Block newTop = top.getRelative(0, raise, 0);
        newTop.setBlockData(surface, false);
        return newTop.getLocation();
    }

    private static Material fillerFor(Material surface) {
        return switch (surface) {
            case GRASS_BLOCK, PODZOL, MYCELIUM, DIRT_PATH, COARSE_DIRT -> Material.DIRT;
            case SAND -> Material.SANDSTONE;
            case RED_SAND -> Material.RED_SANDSTONE;
            case SNOW_BLOCK -> Material.STONE;
            default -> surface.isSolid() && surface.isOccluding() ? surface : Material.STONE;
        };
    }

    // ───────────────────────── 보존형: 땅이 옆으로 어긋남 ─────────────────────────

    private void shear(World world, Boundary b, Player player) {
        double[] seg = segment(b, player);
        int length = (int) seg[2];
        int stripWidth = 6;
        int slip = random().nextInt(2, 4);
        int layers = 4;

        // 경계 건너편(other 판 쪽) 띠를 경계 방향으로 slip 칸 밀어냅니다.
        // 덮어쓰기 전에 전부 읽어둡니다.
        record Column(int x, int z, BlockData[] data) {
        }
        List<Column> sources = new ArrayList<>();
        List<int[]> targets = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            for (int w = 1; w <= stripWidth; w++) {
                double fx = seg[0] + b.tangentX() * i + b.normalX() * w;
                double fz = seg[1] + b.tangentZ() * i + b.normalZ() * w;
                int sx = (int) Math.floor(fx);
                int sz = (int) Math.floor(fz);
                int tx = (int) Math.floor(fx + b.tangentX() * slip);
                int tz = (int) Math.floor(fz + b.tangentZ() * slip);
                if (!world.isChunkLoaded(sx >> 4, sz >> 4) || !world.isChunkLoaded(tx >> 4, tz >> 4)) {
                    continue;
                }
                int top = world.getHighestBlockYAt(sx, sz);
                BlockData[] data = new BlockData[layers];
                boolean ok = true;
                for (int l = 0; l < layers; l++) {
                    Block block = world.getBlockAt(sx, top - l, sz);
                    if (!isBreakable(block)) {
                        ok = false;
                        break;
                    }
                    data[l] = block.getBlockData();
                }
                if (ok) {
                    sources.add(new Column(sx, sz, data));
                    targets.add(new int[]{tx, tz});
                }
            }
        }
        for (int i = 0; i < sources.size(); i++) {
            Column col = sources.get(i);
            int tx = targets.get(i)[0];
            int tz = targets.get(i)[1];
            int top = world.getHighestBlockYAt(tx, tz);
            boolean ok = true;
            for (int l = 0; l < layers; l++) {
                if (!isBreakable(world.getBlockAt(tx, top - l, tz)) && !world.getBlockAt(tx, top - l, tz).getType().isAir()) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            for (int l = 0; l < layers; l++) {
                world.getBlockAt(tx, top - l, tz).setBlockData(col.data()[l], false);
            }
            Block plant = world.getBlockAt(tx, top + 1, tz);
            if (isPlant(plant)) {
                plant.setType(Material.AIR, false);
            }
        }

        // 경계선을 따라 얕은 단층 균열
        for (int i = 0; i < length; i++) {
            int bx = (int) Math.floor(seg[0] + b.tangentX() * i);
            int bz = (int) Math.floor(seg[1] + b.tangentZ() * i);
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                continue;
            }
            int top = world.getHighestBlockYAt(bx, bz);
            for (int y = top; y > top - 3; y--) {
                Block block = world.getBlockAt(bx, y, bz);
                if (isBreakable(block)) {
                    block.setType(Material.AIR, false);
                }
            }
            world.spawnParticle(Particle.BLOCK, new Location(world, bx + 0.5, top + 1, bz + 0.5), 8, 0.4, 0.2, 0.4,
                    Material.STONE.createBlockData());
        }
        Location mid = new Location(world, seg[0], player.getLocation().getY(), seg[1]);
        world.playSound(mid, Sound.BLOCK_GRINDSTONE_USE, 3f, 0.3f);
        world.playSound(mid, Sound.BLOCK_DEEPSLATE_BREAK, 3f, 0.3f);
    }

    private static boolean isPlant(Block block) {
        Material type = block.getType();
        return !type.isAir() && !type.isSolid() && !block.isLiquid();
    }
}
