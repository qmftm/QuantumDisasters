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

import java.util.List;

/** 토네이도가 플레이어를 쫓아다니며 근처의 생물과 블록을 빨아올립니다. */
public final class Tornado extends Disaster {

    private static final double RADIUS = 8;
    private static final int HEIGHT = 24;
    private static final double SPEED = 0.18;

    private Location center;

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

    @Override
    public void start() {
        setWeather(true, false, durationTicks() + 200);
        List<Player> targets = targets();
        if (targets.isEmpty()) {
            center = null;
            return;
        }
        Location base = targets.get(random().nextInt(targets.size())).getLocation();
        double angle = random().nextDouble(Math.PI * 2);
        center = base.clone().add(Math.cos(angle) * 25, 0, Math.sin(angle) * 25);
        snapToGround();
    }

    @Override
    public void tick(int elapsed) {
        if (center == null) {
            start();
            if (center == null) {
                return;
            }
        }
        move();
        drawFunnel(elapsed);
        pullEntities();
        if (blockDamage() && elapsed % 4 == 0) {
            ripBlock();
        }
        if (elapsed % 40 == 0) {
            center.getWorld().playSound(center, Sound.ITEM_ELYTRA_FLYING, 3f, 0.5f);
        }
    }

    @Override
    public void stop() {
        super.stop();
        center = null;
    }

    /** 같은 월드에서 가장 가까운 플레이어 쪽으로 천천히, 약간 비틀거리며 이동합니다. */
    private void move() {
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
        dir.normalize().add(new Vector(random().nextDouble(-0.6, 0.6), 0, random().nextDouble(-0.6, 0.6)));
        center.add(dir.normalize().multiply(SPEED));
        snapToGround();
    }

    private void snapToGround() {
        World world = center.getWorld();
        center.setY(world.getHighestBlockYAt(center.getBlockX(), center.getBlockZ()) + 1);
    }

    private void drawFunnel(int elapsed) {
        World world = center.getWorld();
        for (int h = 0; h < HEIGHT; h++) {
            double r = 0.8 + h * 0.3;
            for (int arm = 0; arm < 3; arm++) {
                double a = elapsed * 0.35 + h * 0.5 + arm * (Math.PI * 2 / 3);
                Location p = center.clone().add(Math.cos(a) * r, h, Math.sin(a) * r);
                world.spawnParticle(Particle.CLOUD, p, 1, 0.1, 0.1, 0.1, 0);
            }
        }
        world.spawnParticle(Particle.LARGE_SMOKE, center, 4, 1.5, 0.3, 1.5, 0.02);
    }

    private void pullEntities() {
        for (Entity entity : center.getWorld().getNearbyEntities(center, RADIUS, HEIGHT, RADIUS)) {
            if (entity instanceof Player player && !targets().contains(player)) {
                continue; // 크리에이티브/관전자는 영향 없음
            }
            Vector offset = entity.getLocation().toVector().subtract(center.toVector());
            double height = offset.getY();
            offset.setY(0);
            double dist = offset.length();
            if (dist > RADIUS || height < -2) {
                continue;
            }

            Vector inward = offset.lengthSquared() < 0.01 ? new Vector() : offset.clone().normalize().multiply(-0.15);
            Vector spin = new Vector(-offset.getZ(), 0, offset.getX());
            if (spin.lengthSquared() > 0.01) {
                spin.normalize().multiply(0.35);
            }
            Vector push = inward.add(spin).setY(height > HEIGHT - 6 ? 0.05 : 0.28);

            // 꼭대기까지 올라가면 바깥으로 날려버립니다.
            if (height > HEIGHT - 6 && random().nextInt(15) == 0) {
                push = offset.clone().normalize().multiply(1.5).setY(0.6);
            }
            entity.setVelocity(push);
            entity.setFallDistance(0);
        }
    }

    private void ripBlock() {
        World world = center.getWorld();
        int x = center.getBlockX() + random().nextInt(-3, 4);
        int z = center.getBlockZ() + random().nextInt(-3, 4);
        Block block = world.getHighestBlockAt(x, z);
        if (isBreakable(block)) {
            launchBlock(block, new Vector(random().nextDouble(-0.3, 0.3), 0.9, random().nextDouble(-0.3, 0.3)));
        }
    }
}
