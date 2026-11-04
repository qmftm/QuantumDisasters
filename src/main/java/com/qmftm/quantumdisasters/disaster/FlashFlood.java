package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 홍수. 폭우와 함께 플레이어 주변의 수위가 점점 차오르고, 물살이 사람과 물건을 떠밀어 갑니다.
 * 재해가 끝나면 물이 천천히 빠집니다. (drain: false 면 물이 남음)
 */
public final class FlashFlood extends Disaster {

    private final TemporaryBlocks water = new TemporaryBlocks();
    /** 플레이어마다 홍수가 시작된 바닥 높이 */
    private final Map<UUID, Integer> baseLevel = new HashMap<>();
    private Vector current;
    private BlockData waterData;

    public FlashFlood(QuantumDisasters plugin) {
        super(plugin, "flash_flood");
    }

    @Override
    public Component displayName() {
        return Component.text("홍수", NamedTextColor.BLUE);
    }

    @Override
    public Component description() {
        return Component.text("물이 차오릅니다! 높은 곳으로 피하세요!", NamedTextColor.AQUA);
    }

    @Override
    public void start() {
        setWeather(true, true, durationTicks() + 200);
        baseLevel.clear();
        double angle = random().nextDouble(Math.PI * 2);
        current = new Vector(Math.cos(angle), 0, Math.sin(angle));
        waterData = Material.WATER.createBlockData();
    }

    /** 지금 이 플레이어 주변의 수위 (높이). 시간이 지날수록 올라갑니다. */
    private int level(Player player, int elapsed) {
        int base = baseLevel.computeIfAbsent(player.getUniqueId(), id -> {
            Location loc = player.getLocation();
            // 주변에서 가장 낮은 땅 근처부터 차오르기 시작
            int lowest = loc.getBlockY();
            for (int i = 0; i < 24; i++) {
                Block b = randomSurface(loc, 20);
                if (b != null) {
                    lowest = Math.min(lowest, b.getY());
                }
            }
            return lowest;
        });
        double progress = Math.min(1, elapsed / (durationTicks() * 0.7));
        int maxRise = (int) Math.round(config().getInt("max-rise", 7) * Math.min(2, intensity()));
        return base + (int) Math.round(maxRise * progress);
    }

    @Override
    public void tick(int elapsed) {
        int radius = config().getInt("radius", 26);
        for (Player player : targets()) {
            int level = level(player, elapsed);
            if (elapsed % 2 == 0) {
                fill(player.getLocation(), radius, level);
            }
            if (elapsed % 10 == 0) {
                rainEffects(player);
            }
        }
        if (elapsed % 2 == 0) {
            sweep();
        }
    }

    /** 주변 땅 위를 수위까지 물로 채웁니다. (지붕 아래 실내는 채우지 않음) */
    private void fill(Location center, int radius, int level) {
        World world = center.getWorld();
        int columns = scaled("columns-per-tick", 140);
        for (int i = 0; i < columns; i++) {
            int x = center.getBlockX() + random().nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + random().nextInt(-radius, radius + 1);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int top = world.getHighestBlockYAt(x, z);
            for (int y = top + 1; y <= level; y++) {
                Block block = world.getBlockAt(x, y, z);
                Material type = block.getType();
                if (type.isAir() || (!type.isSolid() && !block.isLiquid())) {
                    water.place(block, waterData);
                } else if (type != Material.WATER) {
                    break;
                }
            }
            // 지표면이 물에 잠기면 잔디가 진흙이 됩니다.
            if (blockDamage() && top < level && chance(0.02)) {
                Block ground = world.getBlockAt(x, top, z);
                if (ground.getType() == Material.GRASS_BLOCK || ground.getType() == Material.DIRT) {
                    ground.setType(Material.MUD, false);
                }
            }
        }
    }

    /** 물살이 물에 있는 생물과 아이템을 한 방향으로 떠밀어 갑니다. */
    private void sweep() {
        double strength = config().getDouble("current-strength", 0.12) * Math.min(2, intensity());
        for (Player player : targets()) {
            for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 32, 16, 32)) {
                if (!entity.isInWater()) {
                    continue;
                }
                if (entity instanceof Player p && !targets().contains(p)) {
                    continue;
                }
                Vector drift = current.clone().multiply(strength)
                        .add(new Vector(random().nextDouble(-0.03, 0.03), 0, random().nextDouble(-0.03, 0.03)));
                push(entity, entity.getVelocity().add(drift));
            }
        }
    }

    private void rainEffects(Player player) {
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.FALLING_WATER, loc.clone().add(0, 4, 0), 60, 8, 2, 8, 0);
        player.getWorld().spawnParticle(Particle.SPLASH, loc, 30, 6, 0.5, 6, 0);
        if (random().nextInt(4) == 0) {
            player.playSound(loc, Sound.WEATHER_RAIN, 1.5f, 0.7f);
            player.playSound(loc, Sound.BLOCK_WATER_AMBIENT, 1.5f, 0.6f);
        }
    }

    @Override
    public void stop() {
        super.stop();
        baseLevel.clear();
        if (config().getBoolean("drain", true)) {
            water.restoreGradually(plugin, 1500);
        } else {
            water.forgetAll(); // 물이 그대로 남음
        }
    }
}
