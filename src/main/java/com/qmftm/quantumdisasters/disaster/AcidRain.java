package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 산성비가 내립니다.
 * 지붕 밖에 있는 플레이어에게 피해를 주고(투구를 쓰면 절반), 주변 지형을 녹입니다.
 * (나뭇잎·풀·농작물이 사라지고, 잔디는 흙으로, 돌은 조약돌로, 조약돌은 자갈로)
 */
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
        int radius = config().getInt("radius", 30);
        for (Player player : targets()) {
            if (blockDamage() && elapsed % 4 == 0) {
                corrode(player.getLocation(), radius);
            }
            if (elapsed % 3 == 0) {
                acidDrops(player.getLocation());
            }
            if (!isExposed(player)) {
                continue;
            }
            if (elapsed % 5 == 0) {
                Location above = player.getLocation().add(0, 3, 0);
                fx(Particle.FALLING_WATER, above, 20, 4, 1, 4, 0);
                fx(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 2, 0), 3, 0.5, 0.3, 0.5, 0);
                // 몸에서 피어오르는 연기 (산에 녹는 중)
                fx(Particle.CAMPFIRE_COSY_SMOKE, player.getLocation().add(0, 1, 0), 1, 0.3, 0.5, 0.3, 0.01);
            }
            if (elapsed % 20 == 0) {
                double damage = config().getDouble("damage-per-second", 1.5) * intensity();
                ItemStack helmet = player.getInventory().getHelmet();
                if (helmet != null && !helmet.isEmpty()) {
                    damage /= 2;
                    player.damageItemStack(EquipmentSlot.HEAD, 2);
                }
                player.damage(damage);
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0, false, false));
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.5f);
            }
        }
    }

    private static final Particle.DustOptions ACID = new Particle.DustOptions(org.bukkit.Color.fromRGB(0x9C, 0xFF, 0x3C), 1.1f);

    /** 초록빛 산성 빗방울과, 땅에 닿은 곳에서 피어오르는 연기. */
    private void acidDrops(Location center) {
        fx(Particle.DUST, center.clone().add(0, 5, 0), 30, 9, 4, 9, 0, ACID);
        for (int i = 0; i < 3; i++) {
            Block ground = randomSurface(center, 12);
            if (ground != null) {
                Location at = ground.getLocation().add(0.5, 1.05, 0.5);
                fx(Particle.CAMPFIRE_COSY_SMOKE, at, 1, 0.2, 0, 0.2, 0.005);
                fx(Particle.DUST, at, 3, 0.3, 0.05, 0.3, 0, ACID);
            }
        }
    }

    /** 주변 지표면 블록을 녹입니다. */
    private void corrode(Location center, int radius) {
        int count = scaled("corrode-per-burst", 8);
        for (int i = 0; i < count; i++) {
            Block top = randomSurface(center, radius);
            if (top == null) {
                continue;
            }
            // 지표면 바로 위의 풀·꽃 같은 식물
            Block above = top.getRelative(0, 1, 0);
            if (!above.getType().isAir() && !above.getType().isSolid() && !above.isLiquid()) {
                above.setType(Material.AIR, false);
                fizz(above);
                continue;
            }
            Material result = corrodeInto(top);
            if (result != null) {
                top.setType(result, result == Material.GRAVEL);
                fizz(top.getRelative(0, 1, 0));
            }
        }
    }

    private static Material corrodeInto(Block block) {
        Material type = block.getType();
        if (Tag.LEAVES.isTagged(type) || Tag.CROPS.isTagged(type) || Tag.FLOWERS.isTagged(type)) {
            return Material.AIR;
        }
        return switch (type) {
            case GRASS_BLOCK, PODZOL, MYCELIUM -> Material.COARSE_DIRT;
            case DIRT, FARMLAND -> chance(0.3) ? Material.COARSE_DIRT : null;
            case STONE, ANDESITE, DIORITE, GRANITE -> Material.COBBLESTONE;
            case COBBLESTONE -> chance(0.4) ? Material.GRAVEL : null;
            default -> null;
        };
    }

    private static void fizz(Block at) {
        Location loc = at.getLocation().add(0.5, 0.2, 0.5);
        at.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 1, 0.2, 0, 0.2, 0.01);
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 3, 0.3, 0.1, 0.3, 0);
        at.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 1.8f);
    }
}
