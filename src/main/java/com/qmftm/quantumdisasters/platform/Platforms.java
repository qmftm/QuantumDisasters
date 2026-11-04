package com.qmftm.quantumdisasters.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 플레이어가 Java 인지 Bedrock(Geyser + Floodgate) 인지 알려줍니다.
 * 서버에 Floodgate 가 없으면 모든 플레이어를 Java 로 취급합니다.
 */
public final class Platforms {

    private static boolean floodgate;

    private Platforms() {
    }

    /** 플러그인이 켜질 때 한 번 호출합니다. */
    public static void init() {
        floodgate = Bukkit.getPluginManager().isPluginEnabled("floodgate");
    }

    public static boolean hasFloodgate() {
        return floodgate;
    }

    public static boolean isBedrock(Player player) {
        // FloodgateHook 은 Floodgate 가 있을 때만 불러야 클래스 로딩 오류가 나지 않습니다.
        return floodgate && FloodgateHook.isBedrock(player.getUniqueId());
    }

    /** 표시용 이름. 예: "Java", "Bedrock (Android)" */
    public static String describe(Player player) {
        if (!isBedrock(player)) {
            return "Java";
        }
        String device = FloodgateHook.device(player.getUniqueId());
        return device == null ? "Bedrock" : "Bedrock (" + device + ")";
    }
}
