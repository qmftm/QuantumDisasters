package com.qmftm.quantumdisasters;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** 플레이어의 죽음/리스폰/접속을 재해 게임에 전달합니다. */
public final class GameListener implements Listener {

    private final DisasterManager manager;
    private final com.qmftm.quantumdisasters.radiation.RadiationService radiation;

    public GameListener(DisasterManager manager, com.qmftm.quantumdisasters.radiation.RadiationService radiation) {
        this.manager = manager;
        this.radiation = radiation;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        manager.onDeath(event.getPlayer());
        radiation.onDeath(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        manager.onRespawn(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.onJoin(event.getPlayer());
        radiation.onJoin(event.getPlayer());
    }
}
