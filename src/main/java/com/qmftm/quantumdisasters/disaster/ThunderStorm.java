package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

/** 뇌우: 천둥번개가 몰아치며 사방에 벼락이 떨어지고, 가끔 한곳에 벼락이 연달아 내리꽂힙니다. */
public final class ThunderStorm extends Disaster {

    /** 곧 떨어질 벼락: 떨어지기 전 그 자리에 전기 불꽃이 튑니다. */
    private static final class Pending {
        final Location where;
        int ticksLeft;

        Pending(Location where, int ticksLeft) {
            this.where = where;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final int WARNING_TICKS = 15;
    private final java.util.List<Pending> pending = new java.util.ArrayList<>();
    private Location barrageCenter;
    private int barrageLeft;

    public ThunderStorm(QuantumDisasters plugin) {
        super(plugin, "thunder_storm");
    }

    @Override
    public Component displayName() {
        return Component.text("뇌우", NamedTextColor.AQUA);
    }

    @Override
    public Component description() {
        return Component.text("벼락이 내리칩니다! 탁 트인 곳을 피하세요!", NamedTextColor.WHITE);
    }

    @Override
    public void start() {
        setWeather(true, true, durationTicks() + 200);
        barrageLeft = 0;
        pending.clear();
    }

    @Override
    public void stop() {
        super.stop();
        pending.clear();
    }

    @Override
    public void tick(int elapsed) {
        List<Player> targets = targets();
        tickPending(targets);
        if (targets.isEmpty()) {
            return;
        }
        // 먹구름 속 번쩍임
        if (elapsed % 6 == 0) {
            for (Player target : targets) {
                Location sky = target.getLocation().add(random().nextDouble(-30, 30), 0, random().nextDouble(-30, 30));
                sky.setY(Math.min(sky.getWorld().getMaxHeight() - 2, target.getLocation().getY() + 40));
                fx(Particle.ELECTRIC_SPARK, sky, 15, 4, 1, 4, 0.3);
                fx(Particle.CLOUD, sky, 6, 6, 1, 6, 0.01);
            }
        }
        int radius = config().getInt("radius", 35);

        // 모든 플레이어 주변에 계속 벼락
        if (elapsed % 5 == 0) {
            for (Player target : targets) {
                if (!chance(Math.min(1, 0.6 * intensity()))) {
                    continue;
                }
                if (chance(0.12) && isExposed(target)) {
                    strike(target.getLocation(), targets); // 가끔은 바로 머리 위로
                } else {
                    Block ground = randomSurface(target.getLocation(), radius);
                    if (ground != null) {
                        strike(ground.getLocation().add(0.5, 1, 0.5), targets);
                    }
                }
            }
        }

        // 집중 낙뢰: 한 지점에 벼락이 연달아 떨어집니다.
        if (barrageLeft <= 0 && elapsed % 80 == 40) {
            Player target = targets.get(random().nextInt(targets.size()));
            Block center = randomSurface(target.getLocation(), 18);
            if (center != null) {
                barrageCenter = center.getLocation();
                barrageLeft = scaled("barrage-strikes", 8);
            }
        }
        if (barrageLeft > 0 && elapsed % 2 == 0) {
            barrageLeft--;
            Block ground = randomSurface(barrageCenter, 7);
            if (ground != null) {
                strike(ground.getLocation().add(0.5, 1, 0.5), targets);
            }
        }
    }

    /** 벼락을 예약합니다. 떨어지기 전까지 그 자리에 불꽃이 튀고 지지직 소리가 납니다. */
    private void strike(Location where, List<Player> targets) {
        pending.add(new Pending(where, WARNING_TICKS));
        where.getWorld().playSound(where, Sound.BLOCK_BEEHIVE_WORK, 1.5f, 2f);
    }

    private void tickPending(List<Player> targets) {
        java.util.Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (--p.ticksLeft > 0) {
                fx(Particle.ELECTRIC_SPARK, p.where, 6, 0.4, 1.2, 0.4, 0.1);
                if (p.ticksLeft % 4 == 0) {
                    ring(p.where.clone().add(0, 0.1, 0), 1.2, Particle.ELECTRIC_SPARK, null, 10);
                    p.where.getWorld().playSound(p.where, Sound.BLOCK_COPPER_BULB_TURN_ON, 0.8f, 2f);
                }
                continue;
            }
            it.remove();
            strikeNow(p.where, targets);
            fx(Particle.FLASH, p.where.clone().add(0, 1, 0), 1, 0, 0, 0, 0, org.bukkit.Color.WHITE);
            fx(Particle.ELECTRIC_SPARK, p.where, 40, 1.5, 0.5, 1.5, 0.6);
            fx(Particle.LARGE_SMOKE, p.where, 6, 0.6, 0.3, 0.6, 0.02);
        }
    }

    private void strikeNow(Location where, List<Player> targets) {
        World world = where.getWorld();
        if (blockDamage()) {
            world.strikeLightning(where);
        } else {
            world.strikeLightningEffect(where);
            for (Player nearby : where.getNearbyPlayers(2.5)) {
                if (targets.contains(nearby)) {
                    nearby.damage(5.0);
                    nearby.setFireTicks(60);
                }
            }
        }
    }
}
