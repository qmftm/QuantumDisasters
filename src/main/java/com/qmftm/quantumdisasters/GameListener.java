package com.qmftm.quantumdisasters;

import com.qmftm.quantumdisasters.radiation.RadiationService;
import com.qmftm.quantumdisasters.radio.Radio;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** 플레이어의 죽음/리스폰/접속을 재해 게임에 전달합니다. */
public final class GameListener implements Listener {

    private final DisasterManager manager;
    private final RadiationService radiation;
    private final Radio radio;

    public GameListener(DisasterManager manager, RadiationService radiation, Radio radio) {
        this.manager = manager;
        this.radiation = radiation;
        this.radio = radio;
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
        radio.item().discover(event.getPlayer());
    }
}
