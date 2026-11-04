package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 혹한의 눈보라.
 * 바깥에 있는 플레이어가 얼어붙고 느려집니다. (가죽 갑옷을 입으면 동상 피해 면역)
 * 주변에 눈이 계속 쌓이고, 물이 얼어붙습니다.
 */
public final class Blizzard extends Disaster {

    public Blizzard(QuantumDisasters plugin) {
        super(plugin, "blizzard");
    }

    @Override
    public Component displayName() {
        return Component.text("눈보라", NamedTextColor.WHITE);
    }

    @Override
    public Component description() {
        return Component.text("혹한의 눈보라! 실내로 피하거나 가죽 옷을 입으세요!", NamedTextColor.AQUA);
    }

    private org.bukkit.util.Vector wind;

    @Override
    public void start() {
        setWeather(true, false, durationTicks() + 200);
        double angle = random().nextDouble(Math.PI * 2);
        wind = new org.bukkit.util.Vector(Math.cos(angle), -0.15, Math.sin(angle));
    }

    @Override
    public void tick(int elapsed) {
        int radius = config().getInt("radius", 30);
        for (Player player : targets()) {
            Location loc = player.getLocation();
            fx(Particle.SNOWFLAKE, loc.clone().add(0, 2, 0), 25, 8, 3, 8, 0.15);
            fx(Particle.WHITE_ASH, loc.clone().add(0, 1.5, 0), 30, 6, 2, 6, 0);
            // 바람 방향으로 옆으로 날리는 눈발
            for (int i = 0; i < 12; i++) {
                Location p = loc.clone().add(random().nextDouble(-10, 10), random().nextDouble(0, 5), random().nextDouble(-10, 10));
                fxMove(Particle.SNOWFLAKE, p, wind, 0.8, null);
            }

            if (blockDamage() && elapsed % 3 == 0) {
                snowfall(loc, radius);
            }
            if (!isExposed(player)) {
                continue;
            }
            // 바닐라에서 얼음 수치는 매 틱 2씩 줄어드므로 그보다 빠르게 올립니다.
            int freeze = Math.min(player.getMaxFreezeTicks() + 20, player.getFreezeTicks() + 5);
            player.setFreezeTicks(freeze);

            if (elapsed % 20 == 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
            }
            if (elapsed % 25 == 0) {
                // 하얀 입김
                Location mouth = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.4));
                fx(Particle.CLOUD, mouth, 3, 0.05, 0.05, 0.05, 0.01);
            }
            if (elapsed % 50 == 0) {
                player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.5f, 0.5f);
                player.playSound(player.getLocation(), Sound.WEATHER_RAIN_ABOVE, 1f, 0.5f);
            }
        }
    }

    /** 눈 블록이면 한 겹 더 쌓고 true. 다 차면 눈 블록으로 바꿉니다. */
    private static boolean addLayer(Block block) {
        if (block.getType() != Material.SNOW || !(block.getBlockData() instanceof Snow snow)) {
            return false;
        }
        if (snow.getLayers() < snow.getMaximumLayers()) {
            snow.setLayers(snow.getLayers() + 1);
            block.setBlockData(snow, false);
        } else {
            block.setType(Material.SNOW_BLOCK, false);
        }
        return true;
    }

    /** 주변에 눈을 쌓고 물을 얼립니다. */
    private void snowfall(Location center, int radius) {
        int count = scaled("snow-per-burst", 10);
        for (int i = 0; i < count; i++) {
            Block top = randomSurface(center, radius);
            if (top == null) {
                continue;
            }
            Material type = top.getType();
            if (type == Material.WATER) {
                top.setType(Material.ICE);
                continue;
            }
            // 지표면 찾기가 얇은 눈 아래 블록을 돌려줄 수 있어 바로 위도 확인합니다.
            Block above = top.getRelative(0, 1, 0);
            if (addLayer(top) || addLayer(above)) {
                continue;
            }
            if (above.getType().isAir() && type.isSolid() && type.isOccluding()) {
                above.setType(Material.SNOW, false);
            } else if (!above.getType().isAir() && !above.getType().isSolid() && !above.isLiquid()) {
                above.setType(Material.SNOW, false); // 풀·꽃이 눈에 덮임
            }
        }
    }
}
