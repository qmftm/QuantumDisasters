package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

/** 뇌우: 천둥번개가 몰아치며 사방에 벼락이 떨어지고, 가끔 한곳에 벼락이 연달아 내리꽂힙니다. */
public final class ThunderStorm extends Disaster {

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
    }

    @Override
    public void tick(int elapsed) {
        List<Player> targets = targets();
        if (targets.isEmpty()) {
            return;
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

    private void strike(Location where, List<Player> targets) {
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
