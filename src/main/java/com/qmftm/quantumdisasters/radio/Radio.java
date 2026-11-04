package com.qmftm.quantumdisasters.radio;

import com.qmftm.quantumdisasters.QuantumDisasters;
import com.qmftm.quantumdisasters.disaster.Disaster;
import com.qmftm.quantumdisasters.items.CraftableItem;
import com.qmftm.quantumdisasters.platform.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 라디오: 다가오는(다음) 재해의 낌새를 어렴풋이 알려주는 예측 아이템.
 *
 * <p>손에 들고 있으면 지지직 잡음이 들리고, 그 사이로 다가오는 재해의 소리가 희미하게 섞이며
 * 라디오 주변에 관련 파티클이 피어오릅니다. 글자로는 알려주지 않습니다.
 * <ul>
 *     <li>재해가 바뀌는 날이 멀면 신호가 드물고 작으며, 다른 재해의 소리가 섞이는 헛신호가 잦습니다.</li>
 *     <li>바뀌는 날이 가까워질수록 신호가 잦고 크고 정확해집니다.</li>
 * </ul>
 * 나중에 지진계·기압계처럼 재해 종류별 도구를 따로 추가할 수 있도록 신호 표는 재해 ID 로 나눠 둡니다.
 */
public final class Radio {

    /** 재해 하나의 신호: 잡음 사이로 섞이는 소리 + 라디오 주변 파티클 */
    private record Signal(Sound sound, float pitch, Particle particle, Object data) {
    }

    private static final Map<String, Signal> SIGNALS = Map.ofEntries(
            Map.entry("meteor", new Signal(Sound.ENTITY_BLAZE_SHOOT, 0.6f, Particle.FLAME, null)),
            Map.entry("earthquake", new Signal(Sound.BLOCK_DEEPSLATE_BREAK, 0.4f, Particle.FALLING_DUST, Material.DIRT.createBlockData())),
            Map.entry("thunder_storm", new Signal(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.6f, Particle.ELECTRIC_SPARK, null)),
            Map.entry("acid_rain", new Signal(Sound.BLOCK_FIRE_EXTINGUISH, 1.6f, Particle.DUST, new Particle.DustOptions(Color.fromRGB(0x9C, 0xFF, 0x3C), 1f))),
            Map.entry("tornado", new Signal(Sound.ITEM_ELYTRA_FLYING, 0.6f, Particle.CLOUD, null)),
            Map.entry("blizzard", new Signal(Sound.BLOCK_POWDER_SNOW_STEP, 0.5f, Particle.SNOWFLAKE, null)),
            Map.entry("solar_storm", new Signal(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, Particle.END_ROD, null)),
            Map.entry("flash_flood", new Signal(Sound.WEATHER_RAIN, 1.2f, Particle.DRIPPING_WATER, null)),
            Map.entry("wildfire", new Signal(Sound.BLOCK_FIRE_AMBIENT, 1.0f, Particle.SMOKE, null)),
            Map.entry("tsunami", new Signal(Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.5f, Particle.SPLASH, null)),
            Map.entry("avalanche", new Signal(Sound.BLOCK_SNOW_BREAK, 0.5f, Particle.ITEM_SNOWBALL, null)),
            Map.entry("sandstorm", new Signal(Sound.BLOCK_SAND_BREAK, 0.6f, Particle.FALLING_DUST, Material.SAND.createBlockData()))
    );

    private final QuantumDisasters plugin;
    private final CraftableItem item;
    private BukkitTask task;

    public Radio(QuantumDisasters plugin) {
        this.plugin = plugin;
        this.item = new CraftableItem(plugin, "radio", "radio", Material.NOTE_BLOCK,
                Component.text("라디오", NamedTextColor.AQUA),
                List.of("손에 들고 있으면 전파를 잡습니다.", "잡음 속에서 무언가 들려올지도..."));
    }

    public CraftableItem item() {
        return item;
    }

    public void enable() {
        item.reload();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void disable() {
        if (task != null) {
            task.cancel();
        }
    }

    private ConfigurationSection config() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("radio");
        return section != null ? section : plugin.getConfig().createSection("radio");
    }

    private void tick() {
        List<Player> listeners = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (item.isHolding(player)) {
                listeners.add(player);
            }
        }
        if (listeners.isEmpty()) {
            return;
        }
        Disaster upcoming = plugin.disasterManager().upcoming();
        double proximity = plugin.disasterManager().changeProximity();
        for (Player player : listeners) {
            noise(player);
            if (upcoming != null) {
                maybeSignal(player, upcoming, proximity);
            }
        }
    }

    /** 지지직 잡음 */
    private void noise(Player player) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() >= config().getDouble("static-chance", 0.5)) {
            return;
        }
        String sound = config().getString("static-sound", "minecraft:block.note_block.snare");
        float volume = (float) config().getDouble("static-volume", 0.12);
        player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, (float) random.nextDouble(0.5, 2.0));
    }

    /** 다가오는 재해의 신호가 가끔 섞여 들립니다. 바뀌는 날이 가까울수록 잦고 크고 정확합니다. */
    private void maybeSignal(Player player, Disaster upcoming, double proximity) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double minRate = config().getDouble("signals-per-second-far", 0.15);
        double maxRate = config().getDouble("signals-per-second-near", 0.8);
        double perSecond = minRate + (maxRate - minRate) * proximity;
        if (random.nextDouble() >= perSecond / 20.0) {
            return;
        }
        // 헛신호: 다른 재해의 소리가 섞임 (멀수록 잦음)
        String id = upcoming.id();
        double decoy = config().getDouble("decoy-chance-far", 0.6) * (1 - proximity);
        if (random.nextDouble() < decoy) {
            List<String> others = new ArrayList<>(SIGNALS.keySet());
            others.remove(id);
            id = others.get(random.nextInt(others.size()));
        }
        Signal signal = SIGNALS.get(id);
        if (signal == null) {
            return;
        }
        float volume = (float) (0.12 + 0.6 * proximity);
        float pitch = (float) (signal.pitch() * random.nextDouble(0.9, 1.1));
        player.playSound(player.getLocation(), signal.sound(), SoundCategory.PLAYERS, volume, pitch);

        // 라디오(손) 근처에 파티클
        Location hand = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.6)).add(0, -0.4, 0);
        int count = 1 + (int) Math.round(5 * proximity);
        Fx.spawn(signal.particle(), hand, count, 0.15, 0.15, 0.15, 0.01, signal.data());
    }
}
