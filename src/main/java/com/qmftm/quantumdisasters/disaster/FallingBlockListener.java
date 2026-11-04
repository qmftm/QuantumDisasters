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

/**
 * 재해가 만든 떨어지는 블록을 처리합니다.
 * <ul>
 *     <li>운석: 땅에 닿으면 블록으로 굳는 대신 폭발</li>
 *     <li>눈사태: block-damage 가 꺼져 있으면 쌓이지 않고 사라짐</li>
 * </ul>
 */
public final class FallingBlockListener implements Listener {

    /** 이 세기 이상이면 거대 운석으로 보고 파편을 흩뿌립니다. */
    private static final float GIANT_POWER = 6f;

    private final QuantumDisasters plugin;
    private final NamespacedKey key;
    private final NamespacedKey avalanche;

    public FallingBlockListener(QuantumDisasters plugin) {
        this.plugin = plugin;
        this.key = key(plugin);
        this.avalanche = avalancheKey(plugin);
    }

    static NamespacedKey key(QuantumDisasters plugin) {
        return new NamespacedKey(plugin, "meteor");
    }

    static NamespacedKey avalancheKey(QuantumDisasters plugin) {
        return new NamespacedKey(plugin, "avalanche");
    }

    @EventHandler(ignoreCancelled = true)
    public void onLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock meteor)) {
            return;
        }
        if (meteor.getPersistentDataContainer().has(avalanche)) {
            if (!plugin.getConfig().getBoolean("block-damage", true)) {
                event.setCancelled(true);
                meteor.remove();
            }
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
        impactEffects(loc, power);

        if (power >= GIANT_POWER) {
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3, 2, 1, 2, 0);
            loc.getWorld().spawnParticle(Particle.LAVA, loc, 60, 4, 2, 4, 0);
            if (blockDamage) {
                scatterDebris(loc);
            }
        }
    }

    /** 충돌 충격파 고리, 남는 연기 기둥, 근처 사람이 휘청이는 충격. */
    private void impactEffects(Location loc, float power) {
        var world = loc.getWorld();
        double amount = Math.max(0, plugin.getConfig().getDouble("particles", 1.0));
        double maxRadius = power * 2.2;
        int[] step = {0};
        // 바닥을 따라 퍼져 나가는 충격파
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            double r = 1 + step[0] * maxRadius / 8;
            int points = (int) (r * 6 * amount);
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                Location p = loc.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r);
                com.qmftm.quantumdisasters.platform.Fx.spawn(Particle.CLOUD, p, 1, 0.1, 0.05, 0.1, 0.01, null);
                if (i % 3 == 0) {
                    com.qmftm.quantumdisasters.platform.Fx.spawn(Particle.FLAME, p, 1, 0.1, 0.1, 0.1, 0.01, null);
                }
            }
            if (++step[0] >= 8) {
                task.cancel();
            }
        }, 0L, 1L);
        // 충돌 지점에 한동안 연기 기둥이 남음
        int[] smoke = {0};
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            com.qmftm.quantumdisasters.platform.Fx.spawn(Particle.CAMPFIRE_SIGNAL_SMOKE, loc, (int) Math.ceil(2 * amount), 0.6, 0.2, 0.6, 0.02, null);
            com.qmftm.quantumdisasters.platform.Fx.spawn(Particle.LARGE_SMOKE, loc, (int) Math.ceil(2 * amount), 0.8, 0.3, 0.8, 0.01, null);
            if (++smoke[0] >= 20) {
                task.cancel();
            }
        }, 5L, 10L);
        // 근처 사람이 충격에 휘청
        for (var player : loc.getNearbyPlayers(power * 4)) {
            Vector away = player.getLocation().toVector().subtract(loc.toVector()).setY(0);
            if (away.lengthSquared() > 0.01) {
                player.setVelocity(player.getVelocity().add(away.normalize().multiply(0.3).setY(0.2)));
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
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
