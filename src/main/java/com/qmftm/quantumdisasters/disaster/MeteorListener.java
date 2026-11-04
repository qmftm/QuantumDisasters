package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.persistence.PersistentDataType;

/** 운석이 땅에 닿으면 블록으로 굳는 대신 폭발하게 합니다. */
public final class MeteorListener implements Listener {

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
    }
}
