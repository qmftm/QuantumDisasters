package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** 운석이 땅에 닿으면 블록으로 굳는 대신 폭발하게 합니다. */
public final class MeteorListener implements Listener {

    /** 이 세기 이상이면 거대 운석으로 보고 파편을 흩뿌립니다. */
    private static final float GIANT_POWER = 6f;

    private final QuantumDisasters plugin;
    private final NamespacedKey key;

    public MeteorListener(QuantumDisasters plugin) {
        this.plugin = plugin;
        this.key = key(plugin);
    }

    static NamespacedKey key(QuantumDisasters plugin) {
        return new NamespacedKey(plugin, "meteor");
    }

    @EventHandler(ignoreCancelled = true)
    public void onLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock meteor)) {
            return;
        }
        Float power = meteor.getPersistentDataContainer().get(key, PersistentDataType.FLOAT);
        if (power == null) {
            return;
        }
        event.setCancelled(true);
        meteor.remove();
        Location loc = meteor.getLocation();
        boolean blockDamage = plugin.getConfig().getBoolean("block-damage", true);
        loc.getWorld().createExplosion(loc, power, blockDamage, blockDamage);

        if (power >= GIANT_POWER) {
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3, 2, 1, 2, 0);
            loc.getWorld().spawnParticle(Particle.LAVA, loc, 60, 4, 2, 4, 0);
            if (blockDamage) {
                scatterDebris(loc);
            }
        }
    }

    /** 거대 운석 충돌 지점에서 불타는 파편이 사방으로 튑니다. */
    private void scatterDebris(Location loc) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Material[] debris = {Material.MAGMA_BLOCK, Material.NETHERRACK, Material.BLACKSTONE, Material.OBSIDIAN};
        for (int i = 0; i < 14; i++) {
            FallingBlock piece = loc.getWorld().spawnFallingBlock(loc.clone().add(0, 1.5, 0),
                    debris[random.nextInt(debris.length)].createBlockData());
            piece.setDropItem(false);
            piece.setHurtEntities(true);
            double angle = random.nextDouble(Math.PI * 2);
            double speed = random.nextDouble(0.5, 1.2);
            piece.setVelocity(new Vector(Math.cos(angle) * speed, random.nextDouble(0.6, 1.2), Math.sin(angle) * speed));
        }
        // 충돌 지점 주변에 불이 번집니다.
        for (int i = 0; i < 20; i++) {
            Location fire = loc.clone().add(random.nextInt(-6, 7), 0, random.nextInt(-6, 7));
            var block = loc.getWorld().getHighestBlockAt(fire).getRelative(0, 1, 0);
            if (block.getType().isAir()) {
                block.setType(Material.FIRE);
            }
        }
    }
}
