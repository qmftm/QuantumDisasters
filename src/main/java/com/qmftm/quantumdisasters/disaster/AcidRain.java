package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** 산성비가 내려 지붕 밖에 있는 플레이어에게 피해를 줍니다. 투구를 쓰면 피해가 절반입니다. */
public final class AcidRain extends Disaster {

    public AcidRain(QuantumDisasters plugin) {
        super(plugin, "acid_rain");
    }

    @Override
    public Component displayName() {
        return Component.text("산성비", NamedTextColor.GREEN);
    }

    @Override
    public Component description() {
        return Component.text("산성비가 내립니다! 지붕 아래로 숨으세요!", NamedTextColor.DARK_GREEN);
    }

    @Override
    public void start() {
        setWeather(true, false, durationTicks() + 200);
    }

    @Override
    public void tick(int elapsed) {
        for (Player player : targets()) {
            if (!isExposed(player)) {
                continue;
            }
            if (elapsed % 5 == 0) {
                Location above = player.getLocation().add(0, 3, 0);
                player.getWorld().spawnParticle(Particle.FALLING_WATER, above, 12, 3, 1, 3, 0);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 2, 0), 2, 0.5, 0.3, 0.5, 0);
            }
            if (elapsed % 20 == 0) {
                double damage = config().getDouble("damage-per-second", 1.0);
                ItemStack helmet = player.getInventory().getHelmet();
                if (helmet != null && !helmet.isEmpty()) {
                    damage /= 2;
                    player.damageItemStack(EquipmentSlot.HEAD, 1);
                }
                player.damage(damage);
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0, false, false));
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 1.5f);
            }
        }
    }
}
