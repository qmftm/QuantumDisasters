package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * 땅이 크게 흔들립니다.
 * 플레이어가 휘청이고, 넓은 범위의 지면 블록이 튀어오르며, 땅이 길게 갈라집니다(균열).
 */
public final class Earthquake extends Disaster {

    public Earthquake(QuantumDisasters plugin) {
        super(plugin, "earthquake");
    }

    @Override
    public Component displayName() {
        return Component.text("지진", NamedTextColor.DARK_RED);
    }

    @Override
    public Component description() {
        return Component.text("땅이 흔들립니다! 높은 곳을 조심하세요!", NamedTextColor.RED);
    }

    @Override
    public void tick(int elapsed) {
        int radius = config().getInt("radius", 30);
        for (Player player : targets()) {
            if (elapsed % 3 == 0 && player.isOnGround()) {
                double s = 0.35 * Math.min(2, intensity());
                Vector shake = new Vector(random().nextDouble(-s, s), 0.15, random().nextDouble(-s, s));
                push(player, player.getVelocity().add(shake));
            }
            if (elapsed % 30 == 1) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, false));
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.5f);
                player.playSound(player.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 1.5f, 0.4f);
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 0.4f);
            }
            if (elapsed % 5 == 0) {
                crackGround(player.getLocation(), radius);
            }
            if (blockDamage() && elapsed % 40 == 20 && chance(config().getDouble("fissure-chance", 0.6))) {
                fissure(player.getLocation(), radius);
            }
        }
    }

    /** 주변 지면에 먼지가 튀고 일부 블록이 튀어오릅니다. */
    private void crackGround(Location center, int radius) {
        int count = scaled("blocks-per-burst", 6);
        for (int i = 0; i < count; i++) {
            Block block = randomSurface(center, radius);
            if (block == null || Math.abs(block.getY() - center.getBlockY()) > 15) {
                continue;
            }
            center.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 1, 0.5), 20, 0.5, 0.1, 0.5, block.getBlockData());
            if (blockDamage() && isBreakable(block) && chance(0.4)) {
                launchBlock(block, new Vector(random().nextDouble(-0.15, 0.15), random().nextDouble(0.4, 0.8), random().nextDouble(-0.15, 0.15)));
            }
        }
    }

    /** 땅이 한 줄로 길고 깊게 갈라집니다. */
    private void fissure(Location center, int radius) {
        World world = center.getWorld();
        Block start = randomSurface(center, radius - 5);
        if (start == null) {
            return;
        }
        double angle = random().nextDouble(Math.PI * 2);
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        int length = (int) Math.round(random().nextInt(14, 30) * Math.min(2, intensity()));
        int depth = random().nextInt(4, 9);

        double x = start.getX() + 0.5;
        double z = start.getZ() + 0.5;
        for (int i = 0; i < length; i++) {
            // 균열이 살짝 구불구불하게
            angle += random().nextDouble(-0.25, 0.25);
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            x += dx;
            z += dz;
            int width = (i < 2 || i > length - 3) ? 1 : random().nextInt(1, 3);
            for (int w = 0; w < width; w++) {
                int bx = (int) Math.floor(x - dz * w);
                int bz = (int) Math.floor(z + dx * w);
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                    continue;
                }
                int top = world.getHighestBlockYAt(bx, bz);
                for (int y = top; y > top - depth && y > world.getMinHeight() + 1; y--) {
                    Block b = world.getBlockAt(bx, y, bz);
                    if (isBreakable(b) || b.getType() == Material.SHORT_GRASS) {
                        b.setType(Material.AIR, false);
                    }
                }
                Location top1 = new Location(world, bx + 0.5, top + 1, bz + 0.5);
                world.spawnParticle(Particle.BLOCK, top1, 12, 0.4, 0.3, 0.4, Material.DIRT.createBlockData());
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, top1, 1, 0.2, 0.2, 0.2, 0.02);
            }
        }
        Location mid = start.getLocation();
        world.playSound(mid, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.3f);
        world.playSound(mid, Sound.BLOCK_ANCIENT_DEBRIS_BREAK, 2f, 0.4f);
    }
}
