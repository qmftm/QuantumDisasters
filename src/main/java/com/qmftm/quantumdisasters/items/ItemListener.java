package com.qmftm.quantumdisasters.items;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.List;

/** 특수 아이템(라디오 등)이 블록 모양이어도 설치되지 않게 합니다. */
public final class ItemListener implements Listener {

    private final List<CraftableItem> items;

    public ItemListener(List<CraftableItem> items) {
        this.items = items;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        for (CraftableItem item : items) {
            if (item.is(event.getItemInHand())) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
