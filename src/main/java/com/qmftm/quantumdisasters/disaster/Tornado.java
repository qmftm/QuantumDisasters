package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 거대한 토네이도가 플레이어를 쫓아다니며 근처의 생물과 블록을 빨아올립니다.
 * 여러 개가 동시에 생길 수 있습니다. (config 의 count)
 */
public final class Tornado extends Disaster {

    private final List<Location> centers = new ArrayList<>();

    public Tornado(QuantumDisasters plugin) {
        super(plugin, "tornado");
    }

    @Override
    public Component displayName() {
        return Component.text("토네이도", NamedTextColor.GRAY);
    }

    @Override
    public Component description() {
        return Component.text("거대한 회오리바람이 다가옵니다! 도망치세요!", NamedTextColor.WHITE);
    }

    private double radius() {
        return config().getDouble("radius", 14);
    }

    private int height() {
        return config().getInt("height", 48);
    }

    @Override
    public void start() {
        setWeather(true, false, durationTicks() + 200);
        centers.clear();
        spawnTornadoes();
    }

    private void spawnTornadoes() {
        List<Player> targets = targets();
        if (targets.isEmpty()) {
            return;
        }
        int count = scaled("count", 2);
        for (int i = 0; i < count; i++) {
            Location base = targets.get(random().nextInt(targets.size())).getLocation();
            double angle = random().nextDouble(Math.PI * 2);
            double distance = random().nextDouble(35, 55);
            Location center = base.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            snapToGround(center);
            centers.add(center);
        }
    }

    @Override
    public void tick(int elapsed) {
        if (centers.isEmpty()) {
            spawnTornadoes();
            if (centers.isEmpty()) {
                return;
            }
        }
        for (Location center : centers) {
            move(center);
            drawFunnel(center, elapsed);
            pullEntities(center);
            if (blockDamage() && elapsed % 2 == 0) {
                ripBlocks(center);
            }
            if (elapsed % 30 == 0) {
                center.getWorld().playSound(center, Sound.ITEM_ELYTRA_FLYING, 6f, 0.4f);
                center.getWorld().playSound(center, Sound.WEATHER_RAIN_ABOVE, 6f, 0.5f);
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        centers.clear();
    }

    /** 같은 월드에서 가장 가까운 플레이어 쪽으로, 약간 비틀거리며 이동합니다. */
    private void move(Location center) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : targets()) {
            if (player.getWorld() != center.getWorld()) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(center);
            if (dist < best) {
                best = dist;
                nearest = player;
            }
        }

        Vector dir;
        if (nearest != null) {
            dir = nearest.getLocation().toVector().subtract(center.toVector()).setY(0);
        } else {
            dir = new Vector(random().nextDouble(-1, 1), 0, random().nextDouble(-1, 1));
        }
        if (dir.lengthSquared() < 0.01) {
            dir = new Vector(1, 0, 0);
        }
        dir.normalize().add(new Vector(random().nextDouble(-0.7, 0.7), 0, random().nextDouble(-0.7, 0.7)));
        center.add(dir.normalize().multiply(config().getDouble("speed", 0.3)));
        snapToGround(center);
    }

    private static void snapToGround(Location center) {
        World world = center.getWorld();
        if (world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) {
            center.setY(world.getHighestBlockYAt(center.getBlockX(), center.getBlockZ()) + 1);
        }
    }

    private void drawFunnel(Location center, int elapsed) {
        World world = center.getWorld();
        int height = height();
        double topRadius = radius() * 1.1;
        for (double h = 0; h < height; h += 1.5) {
            double r = 1 + (topRadius - 1) * (h / height);
            int arms = h < height / 2.0 ? 3 : 5;
            for (int arm = 0; arm < arms; arm++) {
                double a = elapsed * 0.4 + h * 0.35 + arm * (Math.PI * 2 / arms);
                Location p = center.clone().add(Math.cos(a) * r, h, Math.sin(a) * r);
                fx(Particle.CLOUD, p, 1, 0.2, 0.2, 0.2, 0);
                if (arm == 0 && h % 6 == 0) {
                    fx(Particle.LARGE_SMOKE, p, 1, 0.3, 0.3, 0.3, 0);
                }
            }
        }
        // 바닥에서 흙먼지가 소용돌이칩니다.
        Block ground = center.clone().add(0, -1, 0).getBlock();
        if (!ground.getType().isAir()) {
            fx(Particle.BLOCK, center, 25, 3, 0.5, 3, 0, ground.getBlockData());
            // 깔때기를 따라 빙빙 도는 흙·돌 파편
            for (int i = 0; i < 14; i++) {
                double h = random().nextDouble(0, height * 0.8);
                double r = 1 + (topRadius - 1) * (h / height) * random().nextDouble(0.5, 1);
                double a = elapsed * 0.5 + random().nextDouble(Math.PI * 2);
                fx(Particle.BLOCK, center.clone().add(Math.cos(a) * r, h, Math.sin(a) * r), 2, 0.2, 0.2, 0.2, 0, ground.getBlockData());
            }
        }
        fx(Particle.CAMPFIRE_COSY_SMOKE, center, 3, 2.5, 0.5, 2.5, 0.05);
        // 소용돌이 안에서 번쩍이는 번개
        if (random().nextInt(25) == 0) {
            Location flash = center.clone().add(0, random().nextDouble(height * 0.3, height * 0.9), 0);
            fx(Particle.ELECTRIC_SPARK, flash, 40, 2, 3, 2, 0.4);
            fx(Particle.FLASH, flash, 1, 0, 0, 0, 0, org.bukkit.Color.WHITE);
            world.playSound(flash, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 1.4f);
        }
    }

    private void pullEntities(Location center) {
        double radius = radius();
        int height = height();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, height, radius)) {
            if (entity instanceof Player player && !targets().contains(player)) {
                continue; // 크리에이티브/관전자는 영향 없음
            }
            Vector offset = entity.getLocation().toVector().subtract(center.toVector());
            double h = offset.getY();
            offset.setY(0);
            double dist = offset.length();
            if (dist > radius || h < -3) {
                continue;
            }

            // 가까울수록 강하게
            double strength = 1 - dist / radius * 0.6;
            Vector inward = offset.lengthSquared() < 0.01 ? new Vector() : offset.clone().normalize().multiply(-0.2 * strength);
            Vector spin = new Vector(-offset.getZ(), 0, offset.getX());
            if (spin.lengthSquared() > 0.01) {
                spin.normalize().multiply(0.5 * strength);
            }
            Vector velocity = inward.add(spin).setY(h > height - 8 ? 0.05 : 0.35 * strength + 0.05);

            // 꼭대기까지 올라가면 멀리 날려버립니다.
            if (h > height - 8 && random().nextInt(12) == 0) {
                velocity = offset.clone().normalize().multiply(2.2).setY(0.8);
            }
            push(entity, velocity);
            entity.setFallDistance(0);
        }
    }

    private void ripBlocks(Location center) {
        int count = scaled("blocks-per-rip", 3);
        int ripRadius = (int) Math.max(3, radius() / 2);
        for (int i = 0; i < count; i++) {
            Block block = randomSurface(center, ripRadius);
            if (block != null && isBreakable(block)) {
                launchBlock(block, new Vector(random().nextDouble(-0.4, 0.4), random().nextDouble(0.9, 1.4), random().nextDouble(-0.4, 0.4)));
            }
        }
    }
}
