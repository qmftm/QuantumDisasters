package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/** 뇌우: 천둥번개가 몰아치며 주변에 벼락이 떨어집니다. */
public final class ThunderStorm extends Disaster {

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
    }

    @Override
    public void tick(int elapsed) {
        if (elapsed % 12 != 0) {
            return;
        }
        List<Player> targets = targets();
        if (targets.isEmpty()) {
            return;
        }
        Player target = targets.get(random().nextInt(targets.size()));
        World world = target.getWorld();

        Location strike;
        if (random().nextInt(5) == 0 && isExposed(target)) {
            strike = target.getLocation(); // 가끔은 바로 머리 위로
        } else {
            int x = target.getLocation().getBlockX() + random().nextInt(-15, 16);
            int z = target.getLocation().getBlockZ() + random().nextInt(-15, 16);
            strike = world.getHighestBlockAt(x, z).getLocation().add(0.5, 1, 0.5);
        }

        if (blockDamage()) {
            world.strikeLightning(strike);
        } else {
            world.strikeLightningEffect(strike);
            for (Player nearby : strike.getNearbyPlayers(2.5)) {
                if (targets.contains(nearby)) {
                    nearby.damage(5.0);
                    nearby.setFireTicks(60);
                }
            }
        }
    }
}
