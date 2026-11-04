package com.qmftm.quantumdisasters.tectonics;

/** 판 경계의 종류. */
public enum BoundaryType {
    /** 발산형 경계: 두 판이 서로 멀어짐 → 땅이 벌어지며 갈라짐 */
    DIVERGENT("발산형"),
    /** 수렴형 경계: 두 판이 서로 부딪힘 → 땅이 솟아오름 */
    CONVERGENT("수렴형"),
    /** 보존형 경계: 두 판이 옆으로 엇갈림 → 땅이 옆으로 어긋남 */
    TRANSFORM("보존형");

    private final String koreanName;

    BoundaryType(String koreanName) {
        this.koreanName = koreanName;
    }

    public String koreanName() {
        return koreanName;
    }
}
