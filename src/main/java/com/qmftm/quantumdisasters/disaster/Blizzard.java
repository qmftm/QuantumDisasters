package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** 눈보라가 몰아쳐 바깥에 있는 플레이어가 얼어붙습니다. (가죽 갑옷을 입으면 동상 피해 면역) */
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

    @Override
    public void start() {
        setWeather(true, false, durationTicks() + 200);
    }

    @Override
    public void tick(int elapsed) {
        for (Player player : targets()) {
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1.5, 0), 6, 4, 2, 4, 0.05);
            if (!isExposed(player)) {
                continue;
            }
            // 바닐라에서 얼음 수치는 매 틱 2씩 줄어드므로 그보다 빠르게 올립니다.
            int freeze = Math.min(player.getMaxFreezeTicks() + 20, player.getFreezeTicks() + 4);
            player.setFreezeTicks(freeze);

            if (elapsed % 20 == 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
            }
            if (elapsed % 60 == 0) {
                player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.3f, 0.6f);
            }
        }
    }
}
