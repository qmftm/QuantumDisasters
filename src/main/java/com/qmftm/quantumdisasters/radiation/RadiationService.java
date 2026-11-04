package com.qmftm.quantumdisasters.radiation;

import com.qmftm.quantumdisasters.QuantumDisasters;
import com.qmftm.quantumdisasters.disaster.Disaster;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 방사선 처리.
 * <ul>
 *     <li>가이거 계수기를 든 플레이어에게 주변 선량률에 맞춰 딸깍 소리를 들려줍니다. (숫자는 보여주지 않음)</li>
 *     <li>게임 중에는 맞은 방사선이 피폭량으로 쌓이고, 아주 천천히 줄어듭니다.</li>
 *     <li>피폭량이 쌓일수록 허기 → 나약함 → 메스꺼움 → 피해 순으로 증상이 나타납니다.</li>
 * </ul>
 * 피폭량은 플레이어 데이터에 저장되어 재접속해도 유지되고, 새 게임을 시작하면 0 이 됩니다.
 */
public final class RadiationService {

    private final QuantumDisasters plugin;
    private final GeigerCounter geiger;
    private final NamespacedKey doseKey;
    private final NamespacedKey sessionKey;
    private final Map<UUID, Double> exposureCache = new HashMap<>();
    private final Map<UUID, Integer> stageCache = new HashMap<>();
    private BukkitTask task;
    private long session;
    private int tick;

    public RadiationService(QuantumDisasters plugin) {
        this.plugin = plugin;
        this.geiger = new GeigerCounter(plugin);
        this.doseKey = new NamespacedKey(plugin, "radiation_dose");
        this.sessionKey = new NamespacedKey(plugin, "radiation_session");
    }

    public GeigerCounter geiger() {
        return geiger;
    }

    public void enable() {
        geiger.reload();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void disable() {
        if (task != null) {
            task.cancel();
        }
    }

    private ConfigurationSection config() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("radiation");
        return section != null ? section : plugin.getConfig().createSection("radiation");
    }

    /** 새 게임이 시작될 때: 모든 플레이어의 피폭량을 0 으로. (오프라인 플레이어는 접속할 때) */
    public void newGame() {
        session = System.currentTimeMillis();
        stageCache.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            setDose(player, 0);
        }
    }

    public void onJoin(Player player) {
        if (config().getConfigurationSection("geiger-counter") == null
                || config().getBoolean("geiger-counter.recipe.discover", true)) {
            player.discoverRecipe(geiger.recipeKey());
        }
    }

    public void onDeath(Player player) {
        if (config().getBoolean("reset-on-death", true)) {
            setDose(player, 0);
            stageCache.remove(player.getUniqueId());
        }
    }

    /** 지금 재해가 방사선 재해면 그 세기, 아니면 0. */
    private double ambientRate() {
        Disaster current = plugin.disasterManager().current();
        return current instanceof RadiationSource source ? source.ambientRate() : 0;
    }

    private double exposure(Player player) {
        return exposureCache.getOrDefault(player.getUniqueId(), 1.0);
    }

    private void tick() {
        tick++;
        double ambient = ambientRate();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (tick % 5 == 0) {
                exposureCache.put(player.getUniqueId(), Shielding.exposure(player.getLocation()));
            }
            if (geiger.isHolding(player)) {
                click(player, ambient * exposure(player));
            }
        }

        if (tick % 20 == 0 && plugin.disasterManager().isRunning()) {
            for (Player player : plugin.disasterManager().targets()) {
                updateDose(player, ambient * exposure(player));
            }
        }
    }

    // ───────────────────────── 가이거 계수기 소리 ─────────────────────────

    private void click(Player player, double rate) {
        ConfigurationSection c = config().getConfigurationSection("geiger-counter");
        double background = c != null ? c.getDouble("background-clicks", 0.5) : 0.5;
        double perRate = c != null ? c.getDouble("clicks-per-msv", 8) : 8;
        double cps = Math.min(40, background + rate * perRate);

        // 방사선 입자는 무작위로 들어오므로 딸깍 소리도 불규칙하게 (포아송 분포)
        int clicks = poisson(cps / 20.0);
        if (clicks == 0) {
            return;
        }
        String sound = c != null ? c.getString("click-sound", "minecraft:block.note_block.hat") : "minecraft:block.note_block.hat";
        float volume = c != null ? (float) c.getDouble("click-volume", 0.6) : 0.6f;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < Math.min(3, clicks); i++) {
            player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, (float) random.nextDouble(1.7, 2.0));
        }
    }

    private static int poisson(double lambda) {
        double l = Math.exp(-lambda);
        double p = 1;
        int k = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        do {
            k++;
            p *= random.nextDouble();
        } while (p > l && k < 10);
        return k - 1;
    }

    // ───────────────────────── 피폭량 ─────────────────────────

    public double dose(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Long stored = pdc.get(sessionKey, PersistentDataType.LONG);
        if (stored == null || stored != session) {
            return 0; // 이전 게임의 피폭량
        }
        return pdc.getOrDefault(doseKey, PersistentDataType.DOUBLE, 0.0);
    }

    private void setDose(Player player, double dose) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(sessionKey, PersistentDataType.LONG, session);
        pdc.set(doseKey, PersistentDataType.DOUBLE, Math.max(0, dose));
    }

    private void updateDose(Player player, double rate) {
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }
        // 1초 동안 맞은 양만큼 쌓이고, 아주 천천히 회복 (기본: 1분에 1)
        double recovery = config().getDouble("recovery-per-minute", 1.0) / 60.0;
        double dose = dose(player) + rate - recovery;
        setDose(player, dose);
        applySymptoms(player, Math.max(0, dose));
    }

    private void applySymptoms(Player player, double dose) {
        ConfigurationSection s = config().getConfigurationSection("stages");
        double hunger = s != null ? s.getDouble("hunger", 100) : 100;
        double weakness = s != null ? s.getDouble("weakness", 250) : 250;
        double sickness = s != null ? s.getDouble("sickness", 500) : 500;
        double damage = s != null ? s.getDouble("damage", 800) : 800;
        double lethal = s != null ? s.getDouble("lethal", 1200) : 1200;

        int stage = dose >= lethal ? 5 : dose >= damage ? 4 : dose >= sickness ? 3 : dose >= weakness ? 2 : dose >= hunger ? 1 : 0;
        int before = stageCache.getOrDefault(player.getUniqueId(), 0);
        stageCache.put(player.getUniqueId(), stage);
        if (stage > before) {
            // 무엇 때문인지는 알려주지 않고 몸 상태만
            String feeling = switch (stage) {
                case 1 -> "속이 허하다...";
                case 2 -> "몸에 힘이 빠진다...";
                case 3 -> "속이 메스껍고 머리가 어지럽다...";
                case 4 -> "피부가 따끔거리고 몸이 타들어 가는 것 같다...";
                default -> "몸이 버티지 못하고 있다...!";
            };
            player.sendMessage(Component.text(feeling, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
        }

        if (stage >= 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 60, stage >= 2 ? 1 : 0, true, false));
        }
        if (stage >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, stage >= 4 ? 1 : 0, true, false));
        }
        if (stage >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 0, true, false));
            if (tick % 600 < 20) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, true, false));
            }
        }
        if (stage == 4 && tick % 100 < 20) {
            player.damage(1.0);
        }
        if (stage >= 5 && tick % 60 < 20) {
            player.damage(2.0);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, true, false));
        }
    }
}
