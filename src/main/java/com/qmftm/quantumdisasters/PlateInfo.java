package com.qmftm.quantumdisasters;

import com.qmftm.quantumdisasters.tectonics.Boundary;
import com.qmftm.quantumdisasters.tectonics.BoundaryType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** /qd plate : 관리자용. 지금 위치의 지각판과 가장 가까운 경계를 알려줍니다. */
final class PlateInfo {

    private PlateInfo() {
    }

    static void show(QuantumDisasters plugin, Player player, boolean drawLine) {
        Location loc = player.getLocation();
        Boundary b = plugin.plates().of(player.getWorld()).boundaryAt(loc.getX(), loc.getZ());

        double dx = b.pointX() - loc.getX();
        double dz = b.pointZ() - loc.getZ();
        NamedTextColor color = colorOf(b.type());

        player.sendMessage(Component.text("[재해] 지각판 정보", NamedTextColor.GOLD));
        player.sendMessage(Component.text(" - 지금 있는 판: #" + shortId(b.own().id())
                + " (움직이는 방향: " + direction(b.own().vx(), b.own().vz()) + ")", NamedTextColor.GRAY));
        player.sendMessage(Component.text(" - 가장 가까운 경계: ", NamedTextColor.GRAY)
                .append(Component.text(b.type().koreanName(), color))
                .append(Component.text(" / " + Math.round(b.distance()) + "블록 " + direction(dx, dz) + "쪽", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" - 건너편 판: #" + shortId(b.other().id())
                + " (움직이는 방향: " + direction(b.other().vx(), b.other().vz()) + ")", NamedTextColor.GRAY));
        player.sendMessage(Component.text(String.format(" - 경계 위치: X %.0f, Z %.0f / 활동도 %.2f",
                b.pointX(), b.pointZ(), b.rate()), NamedTextColor.GRAY));

        if (drawLine) {
            drawBoundary(plugin, player.getWorld(), b, loc.getY());
            player.sendMessage(Component.text(" - 10초 동안 경계선을 보여줍니다.", NamedTextColor.GRAY));
        }
    }

    /** 경계를 따라 색깔 입자로 선을 그립니다 (10초). */
    private static void drawBoundary(QuantumDisasters plugin, World world, Boundary b, double y) {
        Color color = switch (b.type()) {
            case DIVERGENT -> Color.fromRGB(0xE0, 0x3A, 0x2F);
            case CONVERGENT -> Color.fromRGB(0x2B, 0x6C, 0xD9);
            case TRANSFORM -> Color.fromRGB(0x2E, 0xA0, 0x4F);
        };
        Particle.DustOptions dust = new Particle.DustOptions(color, 2f);
        int[] runs = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (runs[0]++ >= 20) {
                task.cancel();
                return;
            }
            for (int i = -60; i <= 60; i += 2) {
                double x = b.pointX() + b.tangentX() * i;
                double z = b.pointZ() + b.tangentZ() * i;
                int bx = (int) Math.floor(x);
                int bz = (int) Math.floor(z);
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                    continue;
                }
                double ground = world.getHighestBlockYAt(bx, bz) + 1.2;
                world.spawnParticle(Particle.DUST, x, ground, z, 2, 0.1, 0.3, 0.1, 0, dust);
            }
        }, 0L, 10L);
    }

    private static NamedTextColor colorOf(BoundaryType type) {
        return switch (type) {
            case DIVERGENT -> NamedTextColor.RED;
            case CONVERGENT -> NamedTextColor.BLUE;
            case TRANSFORM -> NamedTextColor.GREEN;
        };
    }

    /** 마인크래프트 방위 (북 = -Z, 동 = +X) */
    private static String direction(double dx, double dz) {
        if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) {
            return "제자리";
        }
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = 북, 90 = 동
        String[] names = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};
        int index = (int) Math.round(((angle % 360) + 360) % 360 / 45) % 8;
        return names[index];
    }

    private static String shortId(long id) {
        return String.format("%016X", id * 0x9E3779B97F4A7C15L).substring(0, 4);
    }
}
