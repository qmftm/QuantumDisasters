package com.qmftm.quantumdisasters.platform;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 파티클을 Java / Bedrock 플레이어에게 각각 맞게 보냅니다.
 *
 * <p>Bedrock 플레이어는 Geyser 가 Java 파티클을 Bedrock 파티클로 바꿔서 보는데, 일부는 제대로 안 보입니다.
 * (Geyser 소스의 particles.json / JavaLevelParticlesTranslator 기준)
 * <ul>
 *     <li>ASH, WHITE_ASH, FLASH: Geyser 선택 리소스팩이 없으면 아예 안 보임 → 떨어지는 가루 / 빛 알갱이로</li>
 *     <li>CLOUD, LARGE_SMOKE: 아주 작은 김으로 바뀜 → 흰 연기 뭉치 / 모닥불 연기로</li>
 *     <li>방향이 있는 파티클(count 0): 방향 없이 한 점에 찍힘 → 진행 방향으로 짧은 줄을 그려 움직임을 표현</li>
 * </ul>
 * Bedrock 플레이어에게만 바꿔 보내고 Java 플레이어는 원래 파티클을 봅니다.
 */
public final class Fx {

    /** 파티클을 보내는 거리 (멀리서도 보이도록) */
    private static final double RANGE = 160;

    private static final BlockData BLACK_DUST = Material.BLACK_CONCRETE_POWDER.createBlockData();
    private static final BlockData WHITE_DUST = Material.WHITE_CONCRETE_POWDER.createBlockData();

    private Fx() {
    }

    public static <T> void spawn(Particle particle, Location at, int count, double ox, double oy, double oz, double extra, T data) {
        if (count <= 0) {
            return;
        }
        for (Player viewer : at.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(at) > RANGE * RANGE) {
                continue;
            }
            if (Platforms.isBedrock(viewer)) {
                bedrock(viewer, particle, at, count, ox, oy, oz, extra, data);
            } else {
                viewer.spawnParticle(particle, at, count, ox, oy, oz, extra, data, true);
            }
        }
    }

    /** 한 방향으로 날아가는 파티클 하나. */
    public static <T> void spawnMoving(Particle particle, Location at, Vector direction, double speed, T data) {
        for (Player viewer : at.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(at) > RANGE * RANGE) {
                continue;
            }
            if (Platforms.isBedrock(viewer)) {
                // Bedrock 은 날아가는 방향을 표현 못 하므로 진행 방향으로 점 3개를 찍어 줄처럼 보이게
                Vector step = direction.clone();
                if (step.lengthSquared() > 1e-6) {
                    step.normalize().multiply(Math.max(0.3, speed * 0.8));
                }
                for (int i = 0; i < 3; i++) {
                    Location p = at.clone().add(step.clone().multiply(i));
                    bedrock(viewer, particle, p, 1, 0, 0, 0, 0, data);
                }
            } else {
                viewer.spawnParticle(particle, at, 0, direction.getX(), direction.getY(), direction.getZ(), speed, data, true);
            }
        }
    }

    private static <T> void bedrock(Player viewer, Particle particle, Location at, int count,
                                    double ox, double oy, double oz, double extra, T data) {
        if (particle == Particle.ASH) {
            viewer.spawnParticle(Particle.FALLING_DUST, at, count, ox, oy, oz, 0, BLACK_DUST);
        } else if (particle == Particle.WHITE_ASH) {
            viewer.spawnParticle(Particle.FALLING_DUST, at, count, ox, oy, oz, 0, WHITE_DUST);
        } else if (particle == Particle.FLASH) {
            viewer.spawnParticle(Particle.END_ROD, at, 12 * count, 0.6, 0.6, 0.6, 0);
        } else if (particle == Particle.CLOUD) {
            viewer.spawnParticle(Particle.POOF, at, count, ox, oy, oz, 0);
        } else if (particle == Particle.LARGE_SMOKE) {
            // 모닥불 연기는 오래 남으므로 절반만
            viewer.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, Math.max(1, count / 2), ox, oy, oz, 0.01);
        } else {
            viewer.spawnParticle(particle, at, count, ox, oy, oz, extra, data);
        }
    }
}
