package com.qmftm.quantumdisasters.platform;

import org.geysermc.geyser.api.GeyserApi;

import java.util.UUID;

/** Geyser API 를 직접 쓰는 부분. Geyser 가 같은 서버에 설치된 경우에만 사용됩니다. */
final class GeyserHook {

    private GeyserHook() {
    }

    static boolean isBedrock(UUID uuid) {
        return GeyserApi.api().connectionByUuid(uuid) != null;
    }
}
