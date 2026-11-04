package com.qmftm.quantumdisasters;

import java.util.Locale;

/** 플레이어가 죽었을 때 어떻게 할지. */
public enum RespawnMode {
    /** 죽어도 계속 리스폰 */
    UNLIMITED,
    /** 목숨 개수만큼만 리스폰, 다 쓰면 탈락 */
    LIVES,
    /** 재해 중에 죽으면 관전하다가 그 재해가 끝나면 부활 */
    AFTER_DISASTER;

    public static RespawnMode parse(String value) {
        if (value == null) {
            return LIVES;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return LIVES;
        }
    }
}
