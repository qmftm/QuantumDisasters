package com.qmftm.quantumdisasters.disaster;

import com.qmftm.quantumdisasters.QuantumDisasters;
import com.qmftm.quantumdisasters.platform.Platforms;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Entity;
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

    /**
     * true 면 duration-seconds 가 지나면 끝나고 보스바에 남은 시간이 보입니다.
     * false 면 isOver() 가 true 가 될 때 끝나며 남은 시간을 보여주지 않습니다.
     */
    public boolean hasFixedDuration() {
        return true;
    }

    /** hasFixedDuration() 이 false 인 재해가 스스로 끝날 때를 알려줍니다. */
    public boolean isOver(int elapsed) {
        return false;
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

    /** 전체 재해 세기 배율 (config 의 intensity). 개수·빈도에 곱해집니다. */
    protected double intensity() {
        return Math.max(0.1, plugin.getConfig().getDouble("intensity", 1.0));
    }

    /** 재해별 설정값(정수)에 intensity 를 곱한 값. */
    protected int scaled(String key, int def) {
        return Math.max(1, (int) Math.round(config().getInt(key, def) * intensity()));
    }

    /** 확률(0~1)을 넘기면 그만큼의 확률로 true. */
    protected static boolean chance(double probability) {
        return random().nextDouble() < probability;
    }

    /**
     * center 주변 radius 칸 안의 무작위 지표면 블록. 불러와지지 않은 청크면 null.
     * (재해 범위가 넓어도 새 청크를 강제로 불러오지 않도록)
     */
    protected static Block randomSurface(org.bukkit.Location center, int radius) {
        World world = center.getWorld();
        int x = center.getBlockX() + random().nextInt(-radius, radius + 1);
        int z = center.getBlockZ() + random().nextInt(-radius, radius + 1);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        return world.getHighestBlockAt(x, z);
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

    /**
     * 엔티티를 밀어냅니다. Bedrock 플레이어는 밀어내기가 덜컹거려 보일 수 있어
     * config 의 bedrock.knockback-multiplier 만큼 약하게 밉니다.
     */
    protected void push(Entity entity, Vector velocity) {
        if (entity instanceof Player player && Platforms.isBedrock(player)) {
            velocity = velocity.clone().multiply(plugin.getConfig().getDouble("bedrock.knockback-multiplier", 0.7));
        }
        entity.setVelocity(velocity);
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
