package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** 모든 자연재해의 기본 클래스. */
public abstract class Disaster {

    protected final QuantumDisasters plugin;
    private final String id;
    private final Map<World, boolean[]> savedWeather = new HashMap<>();

    protected Disaster(QuantumDisasters plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    public final String id() {
        return id;
    }

    public abstract Component displayName();

    public abstract Component description();

    /** 재해가 시작될 때 한 번 호출됩니다. */
    public void start() {
    }

    /** 재해가 진행되는 동안 매 틱 호출됩니다. elapsed 는 1부터 시작합니다. */
    public abstract void tick(int elapsed);

    /** 재해가 끝날 때 한 번 호출됩니다. 하위 클래스는 super.stop() 을 호출해야 합니다. */
    public void stop() {
        restoreWeather();
    }

    public boolean enabled() {
        return config().getBoolean("enabled", true);
    }

    public int durationTicks() {
        return config().getInt("duration-seconds", 30) * 20;
    }

    protected ConfigurationSection config() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("disasters." + id);
        return section != null ? section : new MemoryConfiguration();
    }

    protected boolean blockDamage() {
        return plugin.getConfig().getBoolean("block-damage", true);
    }

    protected List<Player> targets() {
        return plugin.disasterManager().targets();
    }

    protected static Random random() {
        return ThreadLocalRandom.current();
    }

    /** 대상 월드의 날씨를 바꿉니다. 재해가 끝나면 원래대로 돌아옵니다. */
    protected void setWeather(boolean storm, boolean thunder, int ticks) {
        for (World world : plugin.disasterManager().targetWorlds()) {
            savedWeather.computeIfAbsent(world, w -> new boolean[]{w.hasStorm(), w.isThundering()});
            world.setStorm(storm);
            world.setThundering(thunder);
            world.setWeatherDuration(ticks);
            if (thunder) {
                world.setThunderDuration(ticks);
            }
        }
    }

    private void restoreWeather() {
        for (Map.Entry<World, boolean[]> entry : savedWeather.entrySet()) {
            entry.getKey().setStorm(entry.getValue()[0]);
            entry.getKey().setThundering(entry.getValue()[1]);
        }
        savedWeather.clear();
    }

    /** 플레이어 머리 위가 하늘로 뚫려 있는지 (지붕 아래가 아닌지). */
    protected static boolean isExposed(Player player) {
        return player.getWorld().getHighestBlockYAt(player.getLocation()) < player.getEyeLocation().getBlockY();
    }

    /** 재해가 부숴도 되는 블록인지. (상자 등 데이터가 있는 블록, 부술 수 없는 블록 제외) */
    protected static boolean isBreakable(Block block) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid() || !type.isSolid()) {
            return false;
        }
        if (type.getHardness() < 0 || type == Material.BEDROCK || type == Material.BARRIER) {
            return false;
        }
        return !(block.getState(false) instanceof TileState);
    }

    /** 블록을 떨어지는 블록 엔티티로 바꿔 날려 보냅니다. */
    protected static FallingBlock launchBlock(Block block, Vector velocity) {
        FallingBlock falling = block.getWorld().spawnFallingBlock(
                block.getLocation().add(0.5, 0, 0.5), block.getBlockData());
        block.setType(Material.AIR, false);
        falling.setDropItem(false);
        falling.setHurtEntities(true);
        falling.setVelocity(velocity);
        return falling;
    }
}
