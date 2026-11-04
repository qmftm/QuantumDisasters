package com.qmftm.quantumdisasters.tectonics;

/**
 * 어떤 지점에서 가장 가까운 판 경계 정보.
 *
 * @param own       그 지점이 속한 판
 * @param other     경계 건너편 판
 * @param type      경계 종류
 * @param distance  그 지점에서 경계까지의 거리(블록)
 * @param pointX    경계 위에서 그 지점과 가장 가까운 점 X
 * @param pointZ    경계 위에서 그 지점과 가장 가까운 점 Z
 * @param normalX   경계에 수직인 방향 X (own → other)
 * @param normalZ   경계에 수직인 방향 Z
 * @param tangentX  경계를 따라가는 방향 X
 * @param tangentZ  경계를 따라가는 방향 Z
 * @param rate      두 판의 상대 속도 크기 (클수록 활발한 경계)
 */
public record Boundary(Plate own, Plate other, BoundaryType type, double distance,
                       double pointX, double pointZ,
                       double normalX, double normalZ,
                       double tangentX, double tangentZ,
                       double rate) {
}
