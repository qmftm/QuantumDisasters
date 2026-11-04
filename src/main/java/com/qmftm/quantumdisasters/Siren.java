package com.qmftm.quantumdisasters;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * 재해가 처음 시작되거나 바뀔 때 울리는 사이렌.
 *
 * <p>토네이도 경보 사이렌처럼 천천히 올라가고(rise) → 꼭대기에서 버티고(hold) → 길게 내려갑니다(fall).
 * 마인크래프트에는 이어지는 사이렌 소리가 없으므로 짧은 소리를 촘촘히 반복하면서 음 높이를 바꿔 만듭니다.
 * 회전하는 경보기처럼 소리 크기가 흔들리고(wobble), 살짝 어긋난 음을 겹쳐(detune) 불길한 느낌을 냅니다.
 *
 * <p>Java/Bedrock 모두 있는 기본 소리를 쓰면 Geyser 플레이어에게도 들립니다.
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
        int cycles = Math.max(1, config.getInt("siren.cycles", 1));
        int rise = Math.max(1, config.getInt("siren.rise-ticks", 60));
        int hold = Math.max(0, config.getInt("siren.hold-ticks", 30));
        int fall = Math.max(1, config.getInt("siren.fall-ticks", 100));
        int intervalTicks = Math.max(1, config.getInt("siren.interval-ticks", 1));
        // 마인크래프트 소리의 음 높이는 0.5 ~ 2.0 까지만 됩니다.
        double low = clampPitch(config.getDouble("siren.min-pitch", 0.5));
        double high = clampPitch(config.getDouble("siren.max-pitch", 1.3));
        double wobble = Math.max(0, Math.min(1, config.getDouble("siren.wobble", 0.4)));
        int wobbleTicks = Math.max(2, config.getInt("siren.wobble-ticks", 24));
        double detune = config.getDouble("siren.detune", 1.06);

        int cycleTicks = rise + hold + fall;
        int totalTicks = cycles * cycleTicks;

        int[] tick = {0};
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (tick[0] >= totalTicks) {
                stop();
                return;
            }
            double t = level(tick[0] % cycleTicks, rise, hold, fall);
            double pitch = low + (high - low) * t;
            // 회전하는 경보기처럼 크기가 주기적으로 커졌다 작아집니다.
            double wave = 0.5 + 0.5 * Math.cos(2 * Math.PI * tick[0] / wobbleTicks);
            float vol = (float) (volume * (1 - wobble * wave));

            for (Player player : Bukkit.getOnlinePlayers()) {
                for (String sound : sounds) {
                    player.playSound(player.getLocation(), sound, SoundCategory.MASTER, vol, (float) pitch);
                    if (detune > 0 && detune != 1) {
                        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, vol * 0.8f,
                                (float) clampPitch(pitch * detune));
                    }
                }
            }
            tick[0] += intervalTicks;
        }, 0L, intervalTicks);
    }

    /**
     * 한 주기 안에서의 사이렌 높이 (0 = 가장 낮음, 1 = 가장 높음).
     * 올라갈 때는 처음에 빨리 오르다 점점 느려지고, 내려갈 때는 처음에 빨리 떨어지다 길게 끌립니다.
     */
    static double level(int tick, int rise, int hold, int fall) {
        if (tick < rise) {
            double x = tick / (double) rise;
            return 1 - (1 - x) * (1 - x);
        }
        if (tick < rise + hold) {
            return 1;
        }
        double x = (tick - rise - hold) / (double) fall;
        return (1 - x) * (1 - x);
    }

    private static double clampPitch(double pitch) {
        return Math.max(0.5, Math.min(2.0, pitch));
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
