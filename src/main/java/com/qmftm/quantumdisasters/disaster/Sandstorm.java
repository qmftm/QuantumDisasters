package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * 모래 폭풍. 강한 바람이 모래를 몰고 와 바깥에서는 앞이 거의 보이지 않고 바람에 떠밀립니다.
 * 투구 없이 밖에 있으면 모래에 긁혀 피해를 입습니다. 땅은 모래로 덮이고 풀이 말라 죽습니다.
 */
public final class Sandstorm extends Disaster {

    private Vector wind;
    private BlockData sand;
    private Particle.DustOptions dust;

    public Sandstorm(QuantumDisasters plugin) {
        super(plugin, "sandstorm");
    }

    @Override
    public Component displayName() {
        return Component.text("모래 폭풍", NamedTextColor.GOLD);
    }

    @Override
    public Component description() {
        return Component.text("모래 폭풍이 몰아칩니다! 실내로 피하세요!", NamedTextColor.YELLOW);
    }

    @Override
    public void start() {
        setWeather(false, false, durationTicks() + 200);
        double angle = random().nextDouble(Math.PI * 2);
        wind = new Vector(Math.cos(angle), 0, Math.sin(angle));
        sand = Material.SAND.createBlockData();
        dust = new Particle.DustOptions(Color.fromRGB(0xD9, 0xB9, 0x7A), 2.5f);
    }

    @Override
    public void tick(int elapsed) {
        int radius = config().getInt("radius", 30);
        double windStrength = config().getDouble("wind-strength", 0.09) * Math.min(2, intensity());
        for (Player player : targets()) {
            Location loc = player.getLocation();
            if (elapsed % 2 == 0) {
                particles(loc);
            }
            if (blockDamage() && elapsed % 3 == 0) {
                desertify(loc, radius);
            }
            if (!isExposed(player)) {
                continue;
            }
            if (elapsed % 2 == 0) {
                push(player, player.getVelocity().add(wind.clone().multiply(windStrength)));
            }
            if (elapsed % 20 == 0) {
                if (config().getBoolean("blind", true)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, true, false));
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, false));
            }
            if (elapsed % 60 == 0) {
                ItemStack helmet = player.getInventory().getHelmet();
                if (helmet != null && !helmet.isEmpty()) {
                    player.damageItemStack(EquipmentSlot.HEAD, 1);
                } else {
                    player.damage(config().getDouble("damage", 1.0));
                }
            }
            if (elapsed % 40 == 0) {
                player.playSound(loc, Sound.ITEM_ELYTRA_FLYING, 0.8f, 0.6f);
                player.playSound(loc, Sound.BLOCK_SAND_BREAK, 1f, 0.5f);
            }
        }
        if (elapsed % 4 == 0) {
            blowItems();
        }
    }

    private void particles(Location loc) {
        // 바람 방향으로 날리는 모래
        for (int i = 0; i < 25; i++) {
            Location p = loc.clone().add(random().nextDouble(-10, 10), random().nextDouble(-1, 5), random().nextDouble(-10, 10));
            loc.getWorld().spawnParticle(Particle.BLOCK, p, 0, wind.getX(), -0.05, wind.getZ(), 1.5, sand);
        }
        loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 1.5, 0), 40, 7, 3, 7, 0, dust);
        loc.getWorld().spawnParticle(Particle.FALLING_DUST, loc.clone().add(0, 4, 0), 15, 8, 2, 8, 0, sand);
    }

    /** 땅이 모래로 덮이고 풀이 말라 죽습니다. */
    private void desertify(Location center, int radius) {
        int count = scaled("blocks-per-burst", 8);
        for (int i = 0; i < count; i++) {
            Block top = randomSurface(center, radius);
            if (top == null) {
                continue;
            }
            Block above = top.getRelative(0, 1, 0);
            Material aboveType = above.getType();
            if (aboveType == Material.SHORT_GRASS || aboveType == Material.TALL_GRASS || Tag.FLOWERS.isTagged(aboveType)) {
                above.setType(chance(0.3) ? Material.DEAD_BUSH : Material.AIR, false);
                continue;
            }
            if (aboveType == Material.SNOW) {
                above.setType(Material.AIR, false);
                continue;
            }
            Material type = top.getType();
            if ((type == Material.GRASS_BLOCK || type == Material.DIRT || type == Material.COARSE_DIRT || type == Material.PODZOL)
                    && chance(0.5)) {
                top.setType(Material.SAND, false);
            } else if (aboveType.isAir() && type.isSolid() && type.isOccluding() && chance(0.15)) {
                above.setType(Material.SAND); // 모래가 쌓여 언덕이 생김 (모래는 떨어지므로 업데이트 허용)
            }
        }
    }

    /** 땅에 떨어진 아이템이 바람에 날려갑니다. */
    private void blowItems() {
        for (Player player : targets()) {
            for (Entity e : player.getNearbyEntities(24, 8, 24)) {
                if (e instanceof Item item && item.isOnGround()) {
                    item.setVelocity(item.getVelocity().add(wind.clone().multiply(0.25)).setY(0.1));
                }
            }
        }
    }
}
