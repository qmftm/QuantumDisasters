package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import com.qmftm.quantumdisasters.radiation.RadiationSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 태양 입자 폭풍. 태양에서 쏟아진 고에너지 입자로 방사선이 크게 오릅니다.
 *
 * <p>실제 태양 입자 사건처럼 방사선이 빠르게 치솟았다가 천천히 줄어듭니다.
 * 줄어드는 속도는 매번 다르고, 중간에 다시 치솟기도 해서 언제 끝날지 알 수 없습니다.
 * 남은 시간을 보여주지 않으며, 방사선이 안전한 수준까지 내려가야 끝납니다.
 * 플레이어는 지하나 두꺼운 지붕 아래로 숨고, 가이거 계수기로 나가도 될지 판단해야 합니다.
 */
public final class SolarStorm extends Disaster implements RadiationSource {

    private record Spike(double at, double amplitude) {
    }

    private final List<Spike> spikes = new ArrayList<>();
    private double peak;
    private double riseSeconds;
    private double halfLife;
    private double seconds;

    public SolarStorm(QuantumDisasters plugin) {
        super(plugin, "solar_storm");
    }

    @Override
    public Component displayName() {
        return Component.text("태양 입자 폭풍", NamedTextColor.YELLOW);
    }

    @Override
    public Component description() {
        return Component.text("방사선이 쏟아집니다! 지하로 숨으세요!", NamedTextColor.GOLD);
    }

    @Override
    public boolean hasFixedDuration() {
        return false;
    }

    @Override
    public void start() {
        spikes.clear();
        seconds = 0;
        peak = config().getDouble("peak-rate", 5.0) * intensity() * random().nextDouble(0.8, 1.2);
        riseSeconds = random().nextDouble(10, 25);
        double min = config().getDouble("half-life-min-seconds", 60);
        double max = Math.max(min + 1, config().getDouble("half-life-max-seconds", 180));
        halfLife = random().nextDouble(min, max);
    }

    @Override
    public void tick(int elapsed) {
        seconds = elapsed / 20.0;

        // 가끔 방사선이 다시 치솟습니다.
        if (elapsed % 200 == 0 && seconds > riseSeconds && chance(config().getDouble("spike-chance", 0.2))) {
            spikes.add(new Spike(seconds, peak * random().nextDouble(0.3, 0.8)));
        }

        // 하늘에 오로라 (낮에도 보이도록 높은 곳에 색깔 입자)
        if (elapsed % 4 == 0) {
            double strength = Math.min(1, ambientRate() / Math.max(0.01, peak));
            for (Player player : targets()) {
                aurora(player.getLocation(), strength, elapsed);
            }
        }
    }

    private void aurora(Location around, double strength, int elapsed) {
        int count = (int) Math.round(12 * strength);
        if (count <= 0) {
            return;
        }
        Particle.DustOptions green = new Particle.DustOptions(Color.fromRGB(0x5C, 0xFF, 0x9A), 3f);
        Particle.DustOptions purple = new Particle.DustOptions(Color.fromRGB(0xB0, 0x6C, 0xFF), 3f);
        double baseY = Math.min(around.getWorld().getMaxHeight() - 5, around.getY() + 35);
        for (int i = 0; i < count; i++) {
            double x = random().nextDouble(-40, 40);
            // 커튼처럼 일렁이는 띠
            double z = Math.sin((x + elapsed * 0.3) / 9.0) * 12 + random().nextDouble(-2, 2);
            double y = baseY + random().nextDouble(0, 8);
            Location p = around.clone().add(x, 0, z);
            p.setY(y);
            around.getWorld().spawnParticle(Particle.DUST, p, 1, 0.3, 1.5, 0.3, 0, y - baseY > 4 ? purple : green);
        }
    }

    @Override
    public double ambientRate() {
        double level;
        if (seconds < riseSeconds) {
            double x = seconds / riseSeconds;
            level = peak * x * x;
        } else {
            level = peak * Math.pow(0.5, (seconds - riseSeconds) / halfLife);
        }
        for (Spike spike : spikes) {
            double since = seconds - spike.at();
            if (since < 0) {
                continue;
            }
            // 짧게 치솟았다가 빠르게 줄어듦
            level += since < 5 ? spike.amplitude() * since / 5 : spike.amplitude() * Math.pow(0.5, (since - 5) / (halfLife / 3));
        }
        return level;
    }

    @Override
    public boolean isOver(int elapsed) {
        double s = elapsed / 20.0;
        double maxSeconds = config().getDouble("max-duration-seconds", 900);
        if (s >= maxSeconds) {
            return true;
        }
        return s > riseSeconds + 30 && ambientRate() < config().getDouble("safe-rate", 0.05);
    }
}
