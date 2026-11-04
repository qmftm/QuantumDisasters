package com.qmftm.quantumdisasters;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * 재해가 처음 시작되거나 바뀔 때 울리는 사이렌.
 * 마인크래프트에는 사이렌 소리가 없으므로 음 높이를 올렸다 내렸다 하며 짧은 소리를 반복해 만듭니다.
 * (Java/Bedrock 모두 있는 기본 소리를 쓰므로 Geyser 플레이어에게도 들립니다.)
 */
public final class Siren {

    private static final float LOW = 0.5f;
    private static final float HIGH = 2.0f;

    private final QuantumDisasters plugin;
    private BukkitTask task;

    public Siren(QuantumDisasters plugin) {
        this.plugin = plugin;
    }

    public void play() {
        stop();
        String sound = plugin.getConfig().getString("siren.sound", "minecraft:block.note_block.flute");
        float volume = (float) plugin.getConfig().getDouble("siren.volume", 1.0);
        int cycles = Math.max(1, plugin.getConfig().getInt("siren.cycles", 3));
        int cycleTicks = Math.max(4, plugin.getConfig().getInt("siren.cycle-ticks", 40));
        int totalTicks = cycles * cycleTicks;

        int[] tick = {0};
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (tick[0] >= totalTicks) {
                stop();
                return;
            }
            // 한 주기 안에서 앞 절반은 올라가고 뒤 절반은 내려갑니다.
            double phase = (tick[0] % cycleTicks) / (double) cycleTicks;
            double t = phase < 0.5 ? phase * 2 : (1 - phase) * 2;
            float pitch = (float) (LOW + (HIGH - LOW) * t);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
            }
            tick[0] += 2;
        }, 0L, 2L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
