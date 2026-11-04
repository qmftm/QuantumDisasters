package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** 하늘에서 불타는 운석이 쏟아져 폭발합니다. 가끔 거대 운석이 떨어집니다. */
public final class MeteorShower extends Disaster {

    private final NamespacedKey meteorKey;
    private final List<FallingBlock> meteors = new ArrayList<>();
    private final List<FallingBlock> giants = new ArrayList<>();
    private double spawnBudget;

    public MeteorShower(QuantumDisasters plugin) {
        super(plugin, "meteor");
        this.meteorKey = FallingBlockListener.key(plugin);
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
    public void start() {
        spawnBudget = 0;
    }

    @Override
    public void tick(int elapsed) {
        drawTrails();
        if (elapsed % 3 == 0) {
            drawShadows();
        }

        List<Player> targets = targets();
        if (targets.isEmpty()) {
            return;
        }
        // 1초에 meteors-per-second 개 (intensity 배율 적용)
        spawnBudget += config().getDouble("meteors-per-second", 5) * intensity() / 20.0;
        while (spawnBudget >= 1) {
            spawnBudget--;
            Player target = targets.get(random().nextInt(targets.size()));
            boolean giant = chance(config().getDouble("giant-chance", 0.12));
            spawnMeteor(target.getLocation(), giant);
        }
    }

    private void drawTrails() {
        meteors.removeIf(m -> !m.isValid());
        giants.removeIf(m -> !m.isValid());
        for (FallingBlock meteor : meteors) {
            Location loc = meteor.getLocation();
            fx(Particle.FLAME, loc, 8, 0.3, 0.3, 0.3, 0.03);
            fx(Particle.LARGE_SMOKE, loc, 3, 0.2, 0.2, 0.2, 0.01);
            fx(Particle.SMALL_FLAME, loc, 4, 0.5, 0.5, 0.5, 0.01);
        }
        for (FallingBlock giant : giants) {
            Location loc = giant.getLocation();
            fx(Particle.FLAME, loc, 40, 1.2, 1.2, 1.2, 0.08);
            fx(Particle.LAVA, loc, 6, 1, 1, 1, 0);
            fx(Particle.LARGE_SMOKE, loc, 15, 1.5, 1.5, 1.5, 0.03);
            fx(Particle.SOUL_FIRE_FLAME, loc, 10, 0.8, 0.8, 0.8, 0.02);
            fx(Particle.CAMPFIRE_SIGNAL_SMOKE, loc, 2, 0.5, 0.5, 0.5, 0.01);
        }
    }

    /** 운석이 떨어질 땅에 그림자 원. 가까워질수록 좁고 진해집니다. */
    private void drawShadows() {
        Particle.DustOptions red = new Particle.DustOptions(org.bukkit.Color.fromRGB(0xFF, 0x40, 0x20), 1.6f);
        Particle.DustOptions dark = new Particle.DustOptions(org.bukkit.Color.fromRGB(0x60, 0x10, 0x00), 2.2f);
        List<FallingBlock> all = new ArrayList<>(meteors);
        all.addAll(giants);
        for (FallingBlock meteor : all) {
            Location loc = meteor.getLocation();
            int ground = loc.getWorld().getHighestBlockYAt(loc);
            double height = loc.getY() - ground;
            if (height < 1) {
                continue;
            }
            boolean giant = giants.contains(meteor);
            double radius = Math.max(0.8, Math.min(giant ? 8 : 4, height / 8)) * (giant ? 1.6 : 1);
            Location center = new Location(loc.getWorld(), loc.getX(), ground + 1.1, loc.getZ());
            ring(center, radius, Particle.DUST, height < 15 ? dark : red, giant ? 24 : 12);
        }
    }

    private void spawnMeteor(Location around, boolean giant) {
        int spread = config().getInt("spread", 45);
        double x = around.getX() + random().nextDouble(-spread, spread);
        double z = around.getZ() + random().nextDouble(-spread, spread);
        if (!around.getWorld().isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
            return;
        }
        double y = Math.min(around.getWorld().getMaxHeight() - 2, around.getY() + config().getInt("height", 70));
        Location spawn = new Location(around.getWorld(), x, y, z);

        Material look = giant ? Material.CRYING_OBSIDIAN : Material.MAGMA_BLOCK;
        FallingBlock meteor = around.getWorld().spawnFallingBlock(spawn, look.createBlockData());
        meteor.setDropItem(false);
        meteor.setHurtEntities(true);
        meteor.setVelocity(new Vector(random().nextDouble(-0.6, 0.6), giant ? -2.2 : -1.8, random().nextDouble(-0.6, 0.6)));

        float power = (float) (giant
                ? config().getDouble("giant-power", 9.0)
                : config().getDouble("explosion-power", 4.0));
        meteor.getPersistentDataContainer().set(meteorKey, PersistentDataType.FLOAT, power);

        if (giant) {
            giants.add(meteor);
            // 거대 운석은 떨어지기 전에 하늘이 울립니다.
            for (Player player : spawn.getNearbyPlayers(120)) {
                player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
                player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.6f, 0.6f);
            }
        } else {
            meteors.add(meteor);
        }
    }

    @Override
    public void stop() {
        super.stop();
        meteors.clear(); // 이미 떨어지는 중인 운석은 그대로 떨어지게 둡니다.
        giants.clear();
    }
}
