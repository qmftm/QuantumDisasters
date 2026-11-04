package com.qmftm.quantumdisasters.tectonics;

/**
 * 지각판 하나.
 *
 * @param id 판 번호 (격자 칸 좌표로 만든 값)
 * @param x  판 중심 X
 * @param z  판 중심 Z
 * @param vx 판이 움직이는 방향·속도 X
 * @param vz 판이 움직이는 방향·속도 Z
 */
public record Plate(long id, double x, double z, double vx, double vz) {
}
