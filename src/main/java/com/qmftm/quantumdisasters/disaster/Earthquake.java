package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** 땅이 흔들려 플레이어가 휘청이고 지면 블록이 튀어오릅니다. */
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
        for (Player player : targets()) {
            if (elapsed % 4 == 0 && player.isOnGround()) {
                Vector shake = new Vector(random().nextDouble(-0.25, 0.25), 0.12, random().nextDouble(-0.25, 0.25));
                push(player, player.getVelocity().add(shake));
            }
            if (elapsed % 40 == 1) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, false));
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.5f);
                player.playSound(player.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.5f);
            }
            if (elapsed % 10 == 0) {
                crackGround(player.getLocation());
            }
        }
    }

    private void crackGround(Location center) {
        World world = center.getWorld();
        for (int i = 0; i < 3; i++) {
            int x = center.getBlockX() + random().nextInt(-12, 13);
            int z = center.getBlockZ() + random().nextInt(-12, 13);
            Block block = world.getHighestBlockAt(x, z);
            if (Math.abs(block.getY() - center.getBlockY()) > 10) {
                continue;
            }
            world.spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 1, 0.5), 15, 0.4, 0.1, 0.4, block.getBlockData());
            if (blockDamage() && isBreakable(block) && random().nextInt(3) == 0) {
                launchBlock(block, new Vector(random().nextDouble(-0.1, 0.1), random().nextDouble(0.3, 0.6), random().nextDouble(-0.1, 0.1)));
            }
        }
    }
}
