package com.qmftm.quantumdisasters;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * 재해가 처음 시작되거나 바뀔 때 울리는 사이렌.
 * 마인크래프트에는 사이렌 소리가 없으므로 음 높이를 올렸다 내렸다 하며 짧은 소리를 반복해 만듭니다.
 * (Java/Bedrock 모두 있는 기본 소리를 쓰므로 Geyser 플레이어에게도 들립니다.)
 */
public final class Siren {

    private final QuantumDisasters plugin;
    private BukkitTask task;

    public Siren(QuantumDisasters plugin) {
        this.plugin = plugin;
    }

    public void play() {
        stop();
        FileConfiguration config = plugin.getConfig();
        // sound 는 하나(문자열) 또는 여러 개(목록)로 쓸 수 있습니다. 여러 개면 겹쳐서 재생합니다.
        List<String> sounds = config.isList("siren.sound")
                ? config.getStringList("siren.sound")
                : List.of(config.getString("siren.sound", "minecraft:block.note_block.didgeridoo"));
        float volume = (float) config.getDouble("siren.volume", 1.0);
        int cycles = Math.max(1, config.getInt("siren.cycles", 3));
        int cycleTicks = Math.max(1, config.getInt("siren.cycle-ticks", 40));
        int intervalTicks = Math.max(1, config.getInt("siren.interval-ticks", 2));
        // 마인크래프트 소리의 음 높이는 0.5 ~ 2.0 까지만 됩니다.
        float low = clampPitch(config.getDouble("siren.min-pitch", 0.5));
        float high = clampPitch(config.getDouble("siren.max-pitch", 1.2));
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
            float pitch = (float) (low + (high - low) * t);
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (String sound : sounds) {
                    player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
                }
            }
            tick[0] += intervalTicks;
        }, 0L, intervalTicks);
    }

    private static float clampPitch(double pitch) {
        return (float) Math.max(0.5, Math.min(2.0, pitch));
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
