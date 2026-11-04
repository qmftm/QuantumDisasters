package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** 하늘에서 불타는 운석이 떨어져 폭발합니다. */
public final class MeteorShower extends Disaster {

    private final NamespacedKey meteorKey;
    private final List<FallingBlock> meteors = new ArrayList<>();

    public MeteorShower(QuantumDisasters plugin) {
        super(plugin, "meteor");
        this.meteorKey = MeteorListener.key(plugin);
    }

    @Override
    public Component displayName() {
        return Component.text("유성우", NamedTextColor.GOLD);
    }

    @Override
    public Component description() {
        return Component.text("하늘에서 운석이 떨어집니다! 몸을 피하세요!", NamedTextColor.YELLOW);
    }

    @Override
    public void tick(int elapsed) {
        meteors.removeIf(m -> !m.isValid());
        for (FallingBlock meteor : meteors) {
            Location loc = meteor.getLocation();
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 6, 0.3, 0.3, 0.3, 0.02);
            loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 2, 0.2, 0.2, 0.2, 0.01);
        }

        int perSecond = Math.max(1, config().getInt("meteors-per-second", 2));
        int every = Math.max(1, 20 / perSecond);
        if (elapsed % every != 0) {
            return;
        }
        List<Player> targets = targets();
        if (targets.isEmpty()) {
            return;
        }
        Player target = targets.get(random().nextInt(targets.size()));
        spawnMeteor(target.getLocation());
    }

    private void spawnMeteor(Location around) {
        double x = around.getX() + random().nextDouble(-20, 20);
        double z = around.getZ() + random().nextDouble(-20, 20);
        double y = Math.min(around.getWorld().getMaxHeight() - 2, around.getY() + 40);
        Location spawn = new Location(around.getWorld(), x, y, z);

        FallingBlock meteor = around.getWorld().spawnFallingBlock(spawn, Material.MAGMA_BLOCK.createBlockData());
        meteor.setDropItem(false);
        meteor.setHurtEntities(true);
        meteor.setVelocity(new Vector(random().nextDouble(-0.4, 0.4), -1.5, random().nextDouble(-0.4, 0.4)));
        float power = (float) config().getDouble("explosion-power", 3.0);
        meteor.getPersistentDataContainer().set(meteorKey, PersistentDataType.FLOAT, power);
        meteors.add(meteor);
    }

    @Override
    public void stop() {
        super.stop();
        meteors.clear(); // 이미 떨어지는 중인 운석은 그대로 떨어지게 둡니다.
    }
}
