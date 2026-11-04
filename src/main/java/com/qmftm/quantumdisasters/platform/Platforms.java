package com.qmftm.quantumdisasters.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 플레이어가 Java 인지 Bedrock(Geyser) 인지 알려줍니다.
 * Floodgate 가 있으면 Floodgate 로, 없으면 같은 서버의 Geyser 로 확인합니다.
 * 둘 다 없으면 (예: Geyser 를 프록시에 설치) 모든 플레이어를 Java 로 취급합니다.
 */
public final class Platforms {

    private static boolean floodgate;
    private static boolean geyser;

    private Platforms() {
    }

    /** 플러그인이 켜질 때 한 번 호출합니다. */
    public static void init() {
        floodgate = Bukkit.getPluginManager().isPluginEnabled("floodgate");
        geyser = Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot");
    }

    public static boolean hasFloodgate() {
        return floodgate;
    }

    /** Bedrock 플레이어를 구분할 수 있는지 */
    public static boolean canDetect() {
        return floodgate || geyser;
    }

    public static boolean isBedrock(Player player) {
        // FloodgateHook 은 Floodgate 가 있을 때만 불러야 클래스 로딩 오류가 나지 않습니다.
        if (floodgate) {
            return FloodgateHook.isBedrock(player.getUniqueId());
        }
        return geyser && GeyserHook.isBedrock(player.getUniqueId());
    }

    /** 표시용 이름. 예: "Java", "Bedrock (Android)" */
    public static String describe(Player player) {
        if (!isBedrock(player)) {
            return "Java";
        }
        String device = floodgate ? FloodgateHook.device(player.getUniqueId()) : null;
        return device == null ? "Bedrock" : "Bedrock (" + device + ")";
    }
}
