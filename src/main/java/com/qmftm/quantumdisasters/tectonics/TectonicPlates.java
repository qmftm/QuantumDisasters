package com.qmftm.quantumdisasters.tectonics;

/**
 * 월드 시드로 정해지는 지각판 지도.
 *
 * <p>월드를 plateSize 크기의 격자로 나누고, 칸마다 시드로 정해진 위치에 판 중심을 하나씩 둡니다.
 * 각 지점은 가장 가까운 중심의 판에 속합니다(보로노이). 판마다 시드로 정해진 이동 방향이 있고,
 * 맞닿은 두 판의 상대 운동으로 경계 종류가 정해집니다.
 * <ul>
 *     <li>서로 멀어지면 발산형</li>
 *     <li>서로 다가가면 수렴형</li>
 *     <li>주로 옆으로 엇갈리면 보존형</li>
 * </ul>
 * 같은 시드와 설정이면 언제나 같은 결과가 나옵니다. (마인크래프트 코드에 의존하지 않음)
 */
public final class TectonicPlates {

    /** 상대 운동에서 "다가가거나 멀어지는" 성분이 "옆으로 엇갈리는" 성분의 이 배수 이상이면 발산/수렴형. */
    private static final double HEAD_ON_RATIO = 0.7;

    private final long seed;
    private final double plateSize;

    public TectonicPlates(long worldSeed, long salt, double plateSize) {
        this.seed = mix(worldSeed ^ (salt * 0x9E3779B97F4A7C15L));
        this.plateSize = Math.max(64, plateSize);
    }

    /** 격자 칸 (cx, cz) 의 판. */
    public Plate plateInCell(int cx, int cz) {
        long h = hash(cx, cz);
        double jx = 0.15 + 0.7 * unit(h);
        double jz = 0.15 + 0.7 * unit(mix(h + 1));
        double angle = unit(mix(h + 2)) * Math.PI * 2;
        double speed = 0.3 + 0.7 * unit(mix(h + 3));
        long id = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        return new Plate(id, (cx + jx) * plateSize, (cz + jz) * plateSize,
                Math.cos(angle) * speed, Math.sin(angle) * speed);
    }

    /** (x, z) 가 속한 판. */
    public Plate plateAt(double x, double z) {
        return nearestTwo(x, z)[0];
    }

    /** (x, z) 에서 가장 가까운 판 경계. */
    public Boundary boundaryAt(double x, double z) {
        Plate[] two = nearestTwo(x, z);
        Plate a = two[0];
        Plate b = two[1];

        // 두 판 중심을 잇는 방향 = 경계에 수직인 방향
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double len = Math.sqrt(dx * dx + dz * dz);
        double nx = dx / len;
        double nz = dz / len;
        double tx = -nz;
        double tz = nx;

        // 경계(수직이등분선)까지의 거리와 경계 위 가장 가까운 점
        double mx = (a.x() + b.x()) / 2;
        double mz = (a.z() + b.z()) / 2;
        double signed = (mx - x) * nx + (mz - z) * nz;
        double px = x + nx * signed;
        double pz = z + nz * signed;

        // 상대 운동: a 가 b 쪽으로 다가가는 정도(closing)와 옆으로 엇갈리는 정도(shear)
        double rvx = a.vx() - b.vx();
        double rvz = a.vz() - b.vz();
        double closing = rvx * nx + rvz * nz;
        double shear = rvx * tx + rvz * tz;

        BoundaryType type;
        if (Math.abs(closing) >= HEAD_ON_RATIO * Math.abs(shear)) {
            type = closing > 0 ? BoundaryType.CONVERGENT : BoundaryType.DIVERGENT;
        } else {
            type = BoundaryType.TRANSFORM;
        }
        double rate = Math.sqrt(rvx * rvx + rvz * rvz);
        return new Boundary(a, b, type, Math.abs(signed), px, pz, nx, nz, tx, tz, rate);
    }

    private Plate[] nearestTwo(double x, double z) {
        int cx = (int) Math.floor(x / plateSize);
        int cz = (int) Math.floor(z / plateSize);
        Plate best = null;
        Plate second = null;
        double bestD = Double.MAX_VALUE;
        double secondD = Double.MAX_VALUE;
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                Plate p = plateInCell(cx + ox, cz + oz);
                double ddx = p.x() - x;
                double ddz = p.z() - z;
                double d = ddx * ddx + ddz * ddz;
                if (d < bestD) {
                    second = best;
                    secondD = bestD;
                    best = p;
                    bestD = d;
                } else if (d < secondD) {
                    second = p;
                    secondD = d;
                }
            }
        }
        return new Plate[]{best, second};
    }

    private long hash(int cx, int cz) {
        return mix(seed ^ mix(cx * 0x632BE59BD9B4E019L ^ mix(cz * 0x85157AF5L + 0x5851F42D4C957F2DL)));
    }

    /** SplitMix64 */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 0 이상 1 미만의 수 */
    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }
}
