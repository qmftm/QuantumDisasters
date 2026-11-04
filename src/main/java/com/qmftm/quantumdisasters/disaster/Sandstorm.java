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

    /** 작은 모래 회오리 (먼지 돌풍) */
    private static final class DustDevil {
        final Location at;
        int ticksLeft;

        DustDevil(Location at, int ticksLeft) {
            this.at = at;
            this.ticksLeft = ticksLeft;
        }
    }

    private final java.util.List<DustDevil> devils = new java.util.ArrayList<>();
    /** 바람에 굴러다니는 마른 덤불 (회전초) */
    private final java.util.List<Item> tumbleweeds = new java.util.ArrayList<>();
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
        if (elapsed % 50 == 0) {
            for (Player player : targets()) {
                Block ground = randomSurface(player.getLocation(), 22);
                if (ground != null && chance(0.6)) {
                    devils.add(new DustDevil(ground.getLocation().add(0.5, 1, 0.5), 20 * random().nextInt(4, 8)));
                }
                if (chance(0.7)) {
                    spawnTumbleweed(player.getLocation());
                }
            }
        }
        tickDustDevils(elapsed);
        tickTumbleweeds();
    }

    private void particles(Location loc) {
        // 바람 방향으로 날리는 모래
        for (int i = 0; i < 25; i++) {
            Location p = loc.clone().add(random().nextDouble(-10, 10), random().nextDouble(-1, 5), random().nextDouble(-10, 10));
            fxMove(Particle.BLOCK, p, new Vector(wind.getX(), -0.05, wind.getZ()), 1.5, sand);
        }
        fx(Particle.DUST, loc.clone().add(0, 1.5, 0), 40, 7, 3, 7, 0, dust);
        fx(Particle.FALLING_DUST, loc.clone().add(0, 4, 0), 15, 8, 2, 8, 0, sand);
    }

    private void tickDustDevils(int elapsed) {
        java.util.Iterator<DustDevil> it = devils.iterator();
        while (it.hasNext()) {
            DustDevil d = it.next();
            if (d.ticksLeft-- <= 0) {
                it.remove();
                continue;
            }
            d.at.add(wind.clone().multiply(0.15));
            if (d.at.getWorld().isChunkLoaded(d.at.getBlockX() >> 4, d.at.getBlockZ() >> 4)) {
                d.at.setY(d.at.getWorld().getHighestBlockYAt(d.at) + 1);
            }
            for (double h = 0; h < 7; h += 0.5) {
                double a = elapsed * 0.6 + h * 1.3;
                double r = 0.4 + h * 0.25;
                fx(Particle.BLOCK, d.at.clone().add(Math.cos(a) * r, h, Math.sin(a) * r), 1, 0.05, 0.05, 0.05, 0, sand);
            }
            // 휘말린 사람은 살짝 떠오르며 돌아감
            for (Player player : d.at.getNearbyPlayers(2.2)) {
                if (targets().contains(player)) {
                    Vector spin = player.getLocation().toVector().subtract(d.at.toVector()).setY(0);
                    spin = new Vector(-spin.getZ(), 0, spin.getX());
                    if (spin.lengthSquared() > 0.01) {
                        push(player, spin.normalize().multiply(0.3).setY(0.25));
                    }
                }
            }
        }
    }

    private void spawnTumbleweed(Location around) {
        Location at = around.clone().subtract(wind.clone().multiply(20)).add(random().nextDouble(-8, 8), 0, random().nextDouble(-8, 8));
        if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        at.setY(at.getWorld().getHighestBlockYAt(at) + 1.5);
        Item item = at.getWorld().dropItem(at, new ItemStack(Material.DEAD_BUSH));
        item.setCanPlayerPickup(false);
        item.setCanMobPickup(false);
        item.setPersistent(false);
        tumbleweeds.add(item);
    }

    private void tickTumbleweeds() {
        tumbleweeds.removeIf(item -> {
            if (!item.isValid() || item.getTicksLived() > 200) {
                item.remove();
                return true;
            }
            if (item.isOnGround()) {
                // 통통 튀며 굴러감
                item.setVelocity(wind.clone().multiply(0.45).setY(random().nextDouble(0.25, 0.45)));
                fx(Particle.BLOCK, item.getLocation(), 3, 0.2, 0.05, 0.2, 0, sand);
            }
            return false;
        });
    }

    @Override
    public void stop() {
        super.stop();
        devils.clear();
        for (Item item : tumbleweeds) {
            item.remove();
        }
        tumbleweeds.clear();
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
                if (e instanceof Item item && item.isOnGround() && !tumbleweeds.contains(item)) {
                    item.setVelocity(item.getVelocity().add(wind.clone().multiply(0.25)).setY(0.1));
                }
            }
        }
    }
}
