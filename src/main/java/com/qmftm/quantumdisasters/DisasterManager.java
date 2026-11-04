package com.qmftm.quantumdisasters;

import com.qmftm.quantumdisasters.disaster.AcidRain;
import com.qmftm.quantumdisasters.disaster.Blizzard;
import com.qmftm.quantumdisasters.disaster.Disaster;
import com.qmftm.quantumdisasters.disaster.Earthquake;
import com.qmftm.quantumdisasters.disaster.LightningStorm;
import com.qmftm.quantumdisasters.disaster.MeteorShower;
import com.qmftm.quantumdisasters.disaster.Tornado;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * 재해 게임의 흐름을 관리합니다.
 * 휴식(카운트다운) → 무작위 재해 → 휴식 → 다른 재해 ... 를 게임이 멈출 때까지 반복합니다.
 */
public final class DisasterManager {

    public enum State { STOPPED, WAITING, ACTIVE }

    private final QuantumDisasters plugin;
    private final Map<String, Disaster> disasters = new LinkedHashMap<>();
    private final BossBar bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);

    private BukkitTask task;
    private State state = State.STOPPED;
    private Disaster current;
    private Disaster last;
    private int waitTicks;
    private int waitTotal;
    private int elapsed;
    private int duration;

    public DisasterManager(QuantumDisasters plugin) {
        this.plugin = plugin;
        register(new MeteorShower(plugin));
        register(new Earthquake(plugin));
        register(new LightningStorm(plugin));
        register(new AcidRain(plugin));
        register(new Tornado(plugin));
        register(new Blizzard(plugin));
    }

    private void register(Disaster disaster) {
        disasters.put(disaster.id(), disaster);
    }

    public Map<String, Disaster> disasters() {
        return Collections.unmodifiableMap(disasters);
    }

    public State state() {
        return state;
    }

    public Disaster current() {
        return current;
    }

    public boolean isRunning() {
        return state != State.STOPPED;
    }

    /** 재해 게임을 시작합니다. 이미 진행 중이면 아무것도 하지 않습니다. */
    public boolean start() {
        if (isRunning()) {
            return false;
        }
        startWaiting(plugin.getConfig().getInt("first-delay-seconds", 15));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);

        broadcastTitle(
                Component.text("자연재해 게임 시작!", NamedTextColor.GOLD),
                Component.text("살아남으세요...", NamedTextColor.GRAY));
        playSound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
        return true;
    }

    /** 재해 게임을 멈추고 진행 중인 재해를 정리합니다. */
    public boolean stop() {
        if (!isRunning()) {
            return false;
        }
        endCurrent();
        if (task != null) {
            task.cancel();
            task = null;
        }
        state = State.STOPPED;
        last = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bossBar);
        }
        Bukkit.broadcast(Component.text("[재해] 자연재해 게임이 종료되었습니다.", NamedTextColor.YELLOW));
        return true;
    }

    /** 지금 진행 중인 단계를 건너뜁니다. (재해 중이면 재해 종료, 휴식 중이면 바로 다음 재해) */
    public boolean skip() {
        if (state == State.ACTIVE) {
            finishDisaster();
            return true;
        }
        if (state == State.WAITING) {
            waitTicks = 1;
            return true;
        }
        return false;
    }

    /** 특정 재해를 즉시 발생시킵니다. 게임이 멈춰 있으면 게임도 함께 시작합니다. */
    public void trigger(Disaster disaster) {
        if (!isRunning()) {
            start();
        }
        endCurrent();
        begin(disaster);
    }

    private void tick() {
        updateBossBarViewers();
        if (state == State.WAITING) {
            tickWaiting();
        } else if (state == State.ACTIVE) {
            tickActive();
        }
    }

    private void tickWaiting() {
        waitTicks--;
        int secondsLeft = (waitTicks + 19) / 20;

        if (waitTicks % 20 == 0) {
            bossBar.name(Component.text("다음 재해까지 " + secondsLeft + "초", NamedTextColor.GREEN));
            bossBar.color(BossBar.Color.GREEN);
            bossBar.progress(clamp(waitTicks / (float) Math.max(1, waitTotal)));

            int warning = plugin.getConfig().getInt("warning-seconds", 5);
            if (secondsLeft > 0 && secondsLeft <= warning) {
                broadcastTitle(
                        Component.text(String.valueOf(secondsLeft), NamedTextColor.RED),
                        Component.text("무언가 다가오고 있습니다...", NamedTextColor.GRAY));
                playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 0.8f);
            }
        }

        if (waitTicks <= 0) {
            Disaster next = pickNext();
            if (next == null) {
                Bukkit.broadcast(Component.text("[재해] 활성화된 재해가 없습니다. config.yml 을 확인하세요.", NamedTextColor.RED));
                stop();
                return;
            }
            begin(next);
        }
    }

    private void tickActive() {
        elapsed++;
        try {
            current.tick(elapsed);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "재해 '" + current.id() + "' 실행 중 오류", e);
            finishDisaster();
            return;
        }

        if (elapsed % 20 == 0) {
            int secondsLeft = Math.max(0, (duration - elapsed) / 20);
            bossBar.name(current.displayName().append(Component.text(" - " + secondsLeft + "초", NamedTextColor.WHITE)));
            bossBar.progress(clamp(1f - elapsed / (float) duration));
        }

        if (elapsed >= duration) {
            finishDisaster();
        }
    }

    private void begin(Disaster disaster) {
        current = disaster;
        last = disaster;
        elapsed = 0;
        duration = Math.max(20, disaster.durationTicks());
        state = State.ACTIVE;

        bossBar.name(disaster.displayName());
        bossBar.color(BossBar.Color.RED);
        bossBar.progress(1f);

        broadcastTitle(disaster.displayName(), disaster.description());
        Bukkit.broadcast(Component.text("[재해] ", NamedTextColor.RED)
                .append(disaster.displayName())
                .append(Component.text(" 이(가) 찾아왔습니다! ", NamedTextColor.WHITE))
                .append(disaster.description()));
        playSound(Sound.ENTITY_WITHER_SPAWN, 0.5f, 1f);

        try {
            disaster.start();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "재해 '" + disaster.id() + "' 시작 중 오류", e);
            finishDisaster();
        }
    }

    private void finishDisaster() {
        Disaster ended = current;
        endCurrent();
        if (ended != null) {
            Bukkit.broadcast(Component.text("[재해] ", NamedTextColor.GREEN)
                    .append(ended.displayName())
                    .append(Component.text(" 이(가) 지나갔습니다. 잠시 숨을 돌리세요.", NamedTextColor.WHITE)));
            playSound(Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1f);
        }
        if (isRunning()) {
            startWaiting(plugin.getConfig().getInt("interval-seconds", 45));
        }
    }

    private void endCurrent() {
        if (current == null) {
            return;
        }
        try {
            current.stop();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "재해 '" + current.id() + "' 종료 중 오류", e);
        }
        current = null;
    }

    private void startWaiting(int seconds) {
        state = State.WAITING;
        waitTotal = Math.max(1, seconds) * 20;
        waitTicks = waitTotal;
    }

    /** 활성화된 재해 중 하나를 고릅니다. 가능하면 직전과 다른 재해를 고릅니다. */
    private Disaster pickNext() {
        List<Disaster> pool = new ArrayList<>();
        for (Disaster d : disasters.values()) {
            if (d.enabled()) {
                pool.add(d);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        if (pool.size() > 1 && last != null) {
            pool.remove(last);
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /** 재해의 영향을 받는 플레이어 (서바이벌/모험 모드, 대상 월드에 있는 살아있는 플레이어). */
    public List<Player> targets() {
        List<String> worlds = plugin.getConfig().getStringList("worlds");
        List<Player> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead()) {
                continue;
            }
            GameMode mode = player.getGameMode();
            if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
                continue;
            }
            if (!worlds.isEmpty() && !worlds.contains(player.getWorld().getName())) {
                continue;
            }
            result.add(player);
        }
        return result;
    }

    /** 대상 플레이어가 있는 월드 목록. */
    public Set<World> targetWorlds() {
        Set<World> result = new java.util.HashSet<>();
        for (Player player : targets()) {
            result.add(player.getWorld());
        }
        return result;
    }

    private void updateBossBarViewers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(bossBar);
        }
    }

    private void broadcastTitle(Component title, Component subtitle) {
        Title t = Title.title(title, subtitle,
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(t);
        }
    }

    private void playSound(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

}
