package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 눈사태. 플레이어 근처의 높은 곳에서 눈과 가루눈 덩어리가 쏟아져 내려옵니다.
 * 휩쓸리면 밀려나고 얼어붙으며, 쌓인 눈과 가루눈에 파묻힐 수 있습니다.
 * 근처에 높은 곳이 없으면 멀리 높은 하늘에서 비스듬히 쏟아집니다.
 */
public final class Avalanche extends Disaster {

    /** 진행 중인 눈사태 하나 */
    private static final class Slide {
        final Location source;
        final Vector dir;
        final Vector side;
        int ticksLeft;

        Slide(Location source, Vector dir, int ticks) {
            this.source = source;
            this.dir = dir;
            this.side = new Vector(-dir.getZ(), 0, dir.getX());
            this.ticksLeft = ticks;
        }
    }

    private final NamespacedKey key;
    private final List<Slide> slides = new ArrayList<>();
    private final List<FallingBlock> snow = new ArrayList<>();

    public Avalanche(QuantumDisasters plugin) {
        super(plugin, "avalanche");
        this.key = FallingBlockListener.avalancheKey(plugin);
    }

    @Override
    public Component displayName() {
        return Component.text("눈사태", NamedTextColor.WHITE);
    }

    @Override
    public Component description() {
        return Component.text("산에서 눈이 쏟아져 내려옵니다! 옆으로 피하세요!", NamedTextColor.AQUA);
    }

    @Override
    public void start() {
        slides.clear();
        snow.clear();
    }

    @Override
    public void tick(int elapsed) {
        int interval = 20 * config().getInt("seconds-between-slides", 9);
        if (elapsed % interval == 40) {
            for (Player player : targets()) {
                startSlide(player);
            }
        }
        Iterator<Slide> it = slides.iterator();
        while (it.hasNext()) {
            Slide slide = it.next();
            if (slide.ticksLeft-- <= 0) {
                it.remove();
                continue;
            }
            pour(slide, elapsed);
        }
        snow.removeIf(f -> !f.isValid());
        if (elapsed % 2 == 0) {
            // 쏟아지는 눈 덩어리를 따라오는 눈가루 구름
            for (int i = 0; i < snow.size(); i += 3) {
                Location at = snow.get(i).getLocation();
                fx(Particle.CLOUD, at, 2, 0.6, 0.4, 0.6, 0.02);
                fx(Particle.SNOWFLAKE, at, 3, 0.5, 0.5, 0.5, 0.05);
            }
        }
        if (elapsed % 2 == 0) {
            hitPlayers();
        }
    }

    private void startSlide(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        int radius = config().getInt("search-radius", 50);

        // 근처에서 가장 높은 곳을 찾습니다.
        Block highest = null;
        for (int i = 0; i < 48; i++) {
            Block b = randomSurface(loc, radius);
            if (b != null && b.getLocation().distanceSquared(loc) > 15 * 15 && (highest == null || b.getY() > highest.getY())) {
                highest = b;
            }
        }
        Location source;
        if (highest != null && highest.getY() - loc.getY() >= 8) {
            source = highest.getLocation().add(0.5, 1.5, 0.5);
        } else {
            double angle = random().nextDouble(Math.PI * 2);
            source = loc.clone().add(Math.cos(angle) * 35, 0, Math.sin(angle) * 35);
            source.setY(Math.min(world.getMaxHeight() - 4, loc.getY() + 28));
        }
        Vector dir = loc.toVector().subtract(source.toVector()).setY(0);
        if (dir.lengthSquared() < 1) {
            dir = new Vector(1, 0, 0);
        }
        dir.normalize();
        slides.add(new Slide(source, dir, 20 * config().getInt("slide-seconds", 5)));

        world.playSound(source, Sound.ENTITY_GENERIC_EXPLODE, 6f, 0.3f);
        world.playSound(source, Sound.BLOCK_SNOW_BREAK, 6f, 0.5f);
        player.playSound(loc, Sound.BLOCK_POWDER_SNOW_BREAK, 1.5f, 0.4f);
    }

    /** 눈사태 출발점에서 눈 덩어리를 쏟아냅니다. */
    private void pour(Slide slide, int elapsed) {
        World world = slide.source.getWorld();
        int count = scaled("blocks-per-tick", 6);
        int width = config().getInt("width", 14);
        for (int i = 0; i < count; i++) {
            Location at = slide.source.clone()
                    .add(slide.side.clone().multiply(random().nextDouble(-width / 2.0, width / 2.0)))
                    .add(0, random().nextDouble(0, 3), 0);
            if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                continue;
            }
            Material type = chance(config().getDouble("powder-snow-chance", 0.3)) ? Material.POWDER_SNOW : Material.SNOW_BLOCK;
            FallingBlock block = world.spawnFallingBlock(at, type.createBlockData());
            block.setDropItem(false);
            block.setHurtEntities(true);
            double speed = random().nextDouble(0.7, 1.2);
            block.setVelocity(slide.dir.clone().multiply(speed).setY(random().nextDouble(0.05, 0.3)));
            block.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            snow.add(block);
        }
        if (elapsed % 3 == 0) {
            fx(Particle.SNOWFLAKE, slide.source, 30, width / 2.0, 2, width / 2.0, 0.2);
            fx(Particle.ITEM_SNOWBALL, slide.source, 20, width / 2.0, 1, width / 2.0, 0.1);
            fx(Particle.CLOUD, slide.source, 15, width / 2.0, 1.5, width / 2.0, 0.05);
        }
        if (elapsed % 5 == 0) {
            world.playSound(slide.source, Sound.BLOCK_POWDER_SNOW_STEP, 6f, 0.4f);
            world.playSound(slide.source, Sound.BLOCK_SNOW_FALL, 6f, 0.4f);
        }
    }

    /** 쏟아지는 눈에 맞은 플레이어는 밀려나고 얼어붙습니다. */
    private void hitPlayers() {
        for (Player player : targets()) {
            for (Entity nearby : player.getNearbyEntities(1.8, 1.8, 1.8)) {
                if (!(nearby instanceof FallingBlock falling) || !falling.getPersistentDataContainer().has(key)) {
                    continue;
                }
                Vector v = falling.getVelocity().setY(0.1).multiply(1.3);
                push(player, player.getVelocity().add(v));
                player.setFreezeTicks(Math.min(player.getMaxFreezeTicks() + 40, player.getFreezeTicks() + 40));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, true, false));
                break;
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        slides.clear();
        snow.clear(); // 이미 쏟아진 눈은 그대로 떨어져 쌓입니다.
    }
}
