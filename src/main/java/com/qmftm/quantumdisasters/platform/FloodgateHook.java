package com.qmftm.quantumdisasters.platform;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.UUID;

/** Floodgate API 를 직접 쓰는 부분. Floodgate 가 설치된 경우에만 사용됩니다. */
final class FloodgateHook {

    private FloodgateHook() {
    }

    static boolean isBedrock(UUID uuid) {
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }

    static String device(UUID uuid) {
        FloodgatePlayer player = FloodgateApi.getInstance().getPlayer(uuid);
        if (player == null || player.getDeviceOs() == null) {
            return null;
        }
        return player.getDeviceOs().toString();
    }
}
