package com.qmftm.quantumdisasters;

import com.qmftm.quantumdisasters.disaster.AcidRain;
import com.qmftm.quantumdisasters.disaster.Blizzard;
import com.qmftm.quantumdisasters.disaster.Disaster;
import com.qmftm.quantumdisasters.disaster.Earthquake;
import com.qmftm.quantumdisasters.disaster.MeteorShower;
import com.qmftm.quantumdisasters.disaster.ThunderStorm;
import com.qmftm.quantumdisasters.disaster.Tornado;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * 재해 게임의 흐름을 관리합니다.
 *
 * <ul>
 *     <li>게임이 시작되면 DAY 1 부터 날짜를 셉니다. (액션바에 "DAY n" 표시)</li>
 *     <li>disaster-change-days 일마다 직전과 다른 재해로 바뀝니다.</li>
 *     <li>그 기간 동안은 같은 재해가 휴식(interval-seconds)을 사이에 두고 반복해서 찾아옵니다.</li>
 *     <li>survive-days 일을 버티면 성공, 모두 죽거나 탈락하면 실패입니다.</li>
 * </ul>
 */
public final class DisasterManager {

    public enum State { STOPPED, WAITING, ACTIVE }

    private enum LifeStatus { ALIVE, WAITING_RESPAWN, ELIMINATED }

    private static final class Participant {
        int lives;
        LifeStatus status = LifeStatus.ALIVE;
        final GameMode originalMode;

        Participant(int lives, GameMode originalMode) {
            this.lives = lives;
            this.originalMode = originalMode;
        }
    }

    /** 재해 중 보스바에 표시되는 이름 (어떤 재해인지는 숨김) */
    private static final Component HIDDEN_NAME = Component.text("재해 진행 중", NamedTextColor.RED);

    private final QuantumDisasters plugin;
    private final Map<String, Disaster> disasters = new LinkedHashMap<>();
    private final Map<UUID, Participant> participants = new HashMap<>();
    /** 게임이 끝났을 때 오프라인이라 관전 모드를 풀어주지 못한 플레이어. */
    private final Map<UUID, GameMode> pendingRestore = new HashMap<>();
    private final BossBar bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);

    private BukkitTask task;
    private State state = State.STOPPED;
    /** 지금 기간의 재해 (disaster-change-days 일마다 바뀜) */
    private Disaster periodDisaster;
    /** 지금 실제로 진행 중인 재해 (휴식 중이면 null) */
    private Disaster current;
    /** 다음에 재해가 시작될 때 사이렌을 울려야 하는지 (첫 재해이거나 재해가 바뀐 경우) */
    private boolean sirenPending;
    private final Siren siren;
    private int day;
    private int dayTicks;
    private int waitTicks;
    private int waitTotal;
    private int elapsed;
    private int duration;

    public DisasterManager(QuantumDisasters plugin) {
        this.plugin = plugin;
        this.siren = new Siren(plugin);
        register(new MeteorShower(plugin));
        register(new Earthquake(plugin));
        register(new ThunderStorm(plugin));
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

    public Disaster periodDisaster() {
        return periodDisaster;
    }

    public int day() {
        return day;
    }

    public Siren siren() {
        return siren;
    }

    public boolean isRunning() {
        return state != State.STOPPED;
    }

    // ───────────────────────── 설정 ─────────────────────────

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    private int dayLengthTicks() {
        return Math.max(20, config().getInt("day-length-seconds", 1200) * 20);
    }

    private int changeDays() {
        return Math.max(1, config().getInt("disaster-change-days", 1));
    }

    private int surviveDays() {
        return Math.max(0, config().getInt("survive-days", 10));
    }

    private boolean continuous() {
        return config().getInt("interval-seconds", 45) <= 0;
    }

    public RespawnMode respawnMode() {
        return RespawnMode.parse(config().getString("respawn.mode", "lives"));
    }

    private int maxLives() {
        return Math.max(1, config().getInt("respawn.lives", 3));
    }

    // ───────────────────────── 게임 시작/종료 ─────────────────────────

    /** 재해 게임을 시작합니다. 이미 진행 중이면 아무것도 하지 않습니다. */
    public boolean start() {
        if (isRunning()) {
            return false;
        }
        participants.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            addParticipant(player);
        }

        day = 1;
        dayTicks = 0;
        syncWorldTime();
        periodDisaster = pickNext(null);
        sirenPending = true;
        startWaiting(config().getInt("first-delay-seconds", 15));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);

        broadcastTitle(
                Component.text("자연재해 게임 시작!", NamedTextColor.GOLD),
                Component.text(goalText(), NamedTextColor.GRAY));
        Bukkit.broadcast(Component.text("[재해] 게임 시작! " + goalText() + " / " + respawnText(), NamedTextColor.GOLD));
        playSound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
        sendDayActionBar();
        return true;
    }

    /** 재해 게임을 멈추고 진행 중인 재해를 정리합니다. */
    public boolean stop() {
        if (!isRunning()) {
            return false;
        }
        shutdown();
        Bukkit.broadcast(Component.text("[재해] 자연재해 게임이 종료되었습니다.", NamedTextColor.YELLOW));
        return true;
    }

    private void shutdown() {
        endCurrent();
        siren.stop();
        if (task != null) {
            task.cancel();
            task = null;
        }
        state = State.STOPPED;
        periodDisaster = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bossBar);
            player.sendActionBar(Component.empty());
        }
        // 관전 중인 참가자들을 원래대로 되돌립니다.
        for (Map.Entry<UUID, Participant> entry : participants.entrySet()) {
            Participant p = entry.getValue();
            if (p.status == LifeStatus.ALIVE) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                revive(player, p.originalMode);
            } else {
                pendingRestore.put(entry.getKey(), p.originalMode);
            }
        }
        participants.clear();
    }

    private void win() {
        Title title = Title.title(
                Component.text("생존 성공!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(surviveDays() + "일을 버텨냈습니다!", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofSeconds(1)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
        Bukkit.broadcast(Component.text("[재해] 생존 성공! " + surviveDays() + "일을 모두 버텨냈습니다!", NamedTextColor.GOLD));
        playSound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        shutdown();
    }

    private void lose() {
        int survived = day - 1;
        Title title = Title.title(
                Component.text("게임 오버", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("DAY " + day + " 에 모두 쓰러졌습니다", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofSeconds(1)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
        Bukkit.broadcast(Component.text("[재해] 게임 오버! 버틴 날: " + survived + "일", NamedTextColor.RED));
        playSound(Sound.ENTITY_WITHER_DEATH, 0.6f, 1f);
        shutdown();
    }

    // ───────────────────────── 명령어용 ─────────────────────────

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

    /** 특정 재해를 즉시 발생시킵니다. 이번 기간의 재해도 이것으로 바뀝니다. 게임이 멈춰 있으면 게임도 시작합니다. */
    public void trigger(Disaster disaster) {
        if (!isRunning()) {
            start();
        }
        endCurrent();
        if (disaster != periodDisaster) {
            sirenPending = true;
        }
        periodDisaster = disaster;
        begin(disaster);
    }

    // ───────────────────────── 매 틱 ─────────────────────────

    private void tick() {
        updateBossBarViewers();

        dayTicks++;
        if (dayTicks >= dayLengthTicks()) {
            dayTicks = 0;
            day++;
            if (surviveDays() > 0 && day > surviveDays()) {
                win();
                return;
            }
            onNewDay();
            if (!isRunning()) {
                return;
            }
        }
        if (dayTicks % 20 == 0) {
            sendDayActionBar();
            if (checkGameOver()) {
                return;
            }
        }

        if (state == State.WAITING) {
            tickWaiting();
        } else if (state == State.ACTIVE) {
            tickActive();
        }
    }

    private void onNewDay() {
        syncWorldTime();
        playSound(Sound.BLOCK_BELL_USE, 1f, 1f);
        sendDayActionBar();

        if ((day - 1) % changeDays() == 0) {
            // 재해가 바뀌는 날
            Disaster previous = periodDisaster;
            if (state == State.ACTIVE) {
                endCurrent();
                reviveWaiting();
            }
            periodDisaster = pickNext(previous);
            sirenPending = true;
            if (periodDisaster == null) {
                noDisasters();
                return;
            }
            startWaiting(Math.max(1, config().getInt("warning-seconds", 5)));
        }
    }

    private void tickWaiting() {
        waitTicks--;
        int secondsLeft = (waitTicks + 19) / 20;

        if (waitTicks % 20 == 0) {
            bossBar.name(Component.text("다음 재해까지 " + secondsLeft + "초", NamedTextColor.GREEN));
            bossBar.color(BossBar.Color.GREEN);
            bossBar.progress(clamp(waitTicks / (float) Math.max(1, waitTotal)));

            int warning = config().getInt("warning-seconds", 5);
            if (secondsLeft > 0 && secondsLeft <= warning) {
                broadcastTitle(
                        Component.text(String.valueOf(secondsLeft), NamedTextColor.RED),
                        Component.text("무언가 다가오고 있습니다...", NamedTextColor.GRAY));
                playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 0.8f);
            }
        }

        if (waitTicks <= 0) {
            if (periodDisaster == null || !periodDisaster.enabled()) {
                periodDisaster = pickNext(periodDisaster);
                sirenPending = true;
            }
            if (periodDisaster == null) {
                noDisasters();
                return;
            }
            begin(periodDisaster);
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
            if (continuous()) {
                bossBar.name(HIDDEN_NAME);
                bossBar.progress(1f);
            } else {
                int secondsLeft = Math.max(0, (duration - elapsed) / 20);
                bossBar.name(HIDDEN_NAME.append(Component.text(" - " + secondsLeft + "초", NamedTextColor.WHITE)));
                bossBar.progress(clamp(1f - elapsed / (float) duration));
            }
        }

        if (!continuous() && elapsed >= duration) {
            finishDisaster();
        }
    }

    // ───────────────────────── 재해 진행 ─────────────────────────

    private void begin(Disaster disaster) {
        current = disaster;
        elapsed = 0;
        duration = Math.max(20, disaster.durationTicks());
        state = State.ACTIVE;

        // 플레이어에게는 어떤 재해인지 알려주지 않습니다.
        bossBar.name(HIDDEN_NAME);
        bossBar.color(BossBar.Color.RED);
        bossBar.progress(1f);

        broadcastTitle(
                Component.text("재해 발생!", NamedTextColor.RED),
                Component.text("살아남으세요...", NamedTextColor.GRAY));
        Bukkit.broadcast(Component.text("[재해] 재해가 찾아왔습니다! 살아남으세요!", NamedTextColor.RED));
        if (sirenPending) {
            sirenPending = false;
            siren.play();
        } else {
            playSound(Sound.ENTITY_WITHER_SPAWN, 0.5f, 1f);
        }

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
            Bukkit.broadcast(Component.text("[재해] 재해가 지나갔습니다. 잠시 숨을 돌리세요.", NamedTextColor.GREEN));
            playSound(Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1f);
        }
        reviveWaiting();
        if (isRunning()) {
            startWaiting(Math.max(1, config().getInt("interval-seconds", 45)));
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

    private void noDisasters() {
        Bukkit.broadcast(Component.text("[재해] 활성화된 재해가 없습니다. config.yml 을 확인하세요.", NamedTextColor.RED));
        stop();
    }

    /** 활성화된 재해 중 하나를 고릅니다. 가능하면 previous 와 다른 재해를 고릅니다. */
    private Disaster pickNext(Disaster previous) {
        List<Disaster> pool = new ArrayList<>();
        for (Disaster d : disasters.values()) {
            if (d.enabled()) {
                pool.add(d);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        if (pool.size() > 1 && previous != null) {
            pool.remove(previous);
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    // ───────────────────────── 목숨 / 리스폰 ─────────────────────────

    private void addParticipant(Player player) {
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return;
        }
        participants.put(player.getUniqueId(), new Participant(maxLives(), mode));
    }

    void onDeath(Player player) {
        if (!isRunning()) {
            return;
        }
        Participant p = participants.get(player.getUniqueId());
        if (p == null || p.status != LifeStatus.ALIVE) {
            return;
        }
        switch (respawnMode()) {
            case UNLIMITED -> {
            }
            case LIVES -> {
                p.lives--;
                if (p.lives <= 0) {
                    p.status = LifeStatus.ELIMINATED;
                    Bukkit.broadcast(Component.text("[재해] " + player.getName() + " 님이 탈락했습니다.", NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("[재해] 남은 목숨: " + p.lives, NamedTextColor.YELLOW));
                }
            }
            case AFTER_DISASTER -> {
                if (state == State.ACTIVE) {
                    p.status = LifeStatus.WAITING_RESPAWN;
                    player.sendMessage(Component.text("[재해] 이번 재해가 끝나면 부활합니다.", NamedTextColor.YELLOW));
                }
            }
        }
    }

    void onRespawn(Player player) {
        Participant p = participants.get(player.getUniqueId());
        if (!isRunning() || p == null || p.status == LifeStatus.ALIVE) {
            return;
        }
        // 리스폰 처리가 끝난 다음 틱에 관전 모드로 바꿉니다.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        });
    }

    void onJoin(Player player) {
        GameMode restore = pendingRestore.remove(player.getUniqueId());
        if (restore != null && !isRunning()) {
            revive(player, restore);
        }
        if (!isRunning()) {
            return;
        }
        player.showBossBar(bossBar);
        Participant p = participants.get(player.getUniqueId());
        if (p == null) {
            addParticipant(player);
        } else if (p.status != LifeStatus.ALIVE) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    /** 재해가 끝나서 부활 대기 중인 플레이어를 되살립니다. */
    private void reviveWaiting() {
        for (Map.Entry<UUID, Participant> entry : participants.entrySet()) {
            Participant p = entry.getValue();
            if (p.status != LifeStatus.WAITING_RESPAWN) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue; // 접속하면 그때 관전 상태로 들어오고 다음 재해 끝에 부활
            }
            p.status = LifeStatus.ALIVE;
            revive(player, p.originalMode);
            player.sendMessage(Component.text("[재해] 재해를 버텨내어 부활했습니다!", NamedTextColor.GREEN));
        }
    }

    private void revive(Player player, GameMode mode) {
        Location spawn = player.getRespawnLocation();
        if (spawn == null) {
            spawn = Bukkit.getWorlds().getFirst().getSpawnLocation();
        }
        player.teleport(spawn);
        player.setGameMode(mode);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
    }

    /** 모든 참가자가 죽거나 탈락했으면 게임 오버. */
    private boolean checkGameOver() {
        if (respawnMode() == RespawnMode.UNLIMITED || participants.isEmpty()) {
            return false;
        }
        boolean anyOnline = false;
        for (Map.Entry<UUID, Participant> entry : participants.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            anyOnline = true;
            if (entry.getValue().status == LifeStatus.ALIVE) {
                return false;
            }
        }
        if (!anyOnline) {
            return false; // 아무도 접속해 있지 않으면 판정하지 않습니다.
        }
        lose();
        return true;
    }

    // ───────────────────────── 대상 ─────────────────────────

    /** 재해의 영향을 받는 플레이어 (서바이벌/모험 모드, 대상 월드에 있는 살아있는 플레이어). */
    public List<Player> targets() {
        List<String> worlds = config().getStringList("worlds");
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
        Set<World> result = new HashSet<>();
        for (Player player : targets()) {
            result.add(player.getWorld());
        }
        return result;
    }

    private Set<World> gameWorlds() {
        List<String> names = config().getStringList("worlds");
        Set<World> result = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL && (names.isEmpty() || names.contains(world.getName()))) {
                result.add(world);
            }
        }
        return result;
    }

    // ───────────────────────── 표시 ─────────────────────────

    private void sendDayActionBar() {
        Component text = Component.text("DAY " + day, NamedTextColor.GOLD, TextDecoration.BOLD);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(text);
        }
    }

    private void syncWorldTime() {
        if (!config().getBoolean("sync-world-time", true)) {
            return;
        }
        for (World world : gameWorlds()) {
            world.setTime(0);
        }
    }

    private String goalText() {
        return surviveDays() > 0 ? surviveDays() + "일을 버티세요!" : "최대한 오래 버티세요!";
    }

    private String respawnText() {
        return switch (respawnMode()) {
            case UNLIMITED -> "무한 리스폰";
            case LIVES -> "목숨 " + maxLives() + "개";
            case AFTER_DISASTER -> "죽으면 재해가 끝날 때 부활";
        };
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
