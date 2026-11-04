package com.qmftm.quantumdisasters;

import com.qmftm.quantumdisasters.disaster.FallingBlockListener;
import com.qmftm.quantumdisasters.platform.Platforms;
import com.qmftm.quantumdisasters.radiation.RadiationService;
import com.qmftm.quantumdisasters.tectonics.PlateRegistry;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuantumDisasters extends JavaPlugin {

    private DisasterManager disasterManager;
    private RadiationService radiation;
    private final PlateRegistry plates = new PlateRegistry(this::getConfig);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Platforms.init();
        getLogger().info(Platforms.hasFloodgate() ? "Floodgate 연동됨: Bedrock 플레이어를 구분합니다."
                : Platforms.canDetect() ? "Geyser 연동됨: Bedrock 플레이어를 구분합니다."
                : "Floodgate/Geyser 없음: 모든 플레이어를 Java 로 취급합니다.");

        disasterManager = new DisasterManager(this);
        radiation = new RadiationService(this);
        radiation.enable();
        getServer().getPluginManager().registerEvents(new FallingBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new GameListener(disasterManager, radiation), this);

        PluginCommand command = getCommand("quantumdisasters");
        if (command != null) {
            DisasterCommand executor = new DisasterCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        if (getConfig().getBoolean("auto-start", false)) {
            // 월드가 모두 로드된 뒤 시작
            getServer().getScheduler().runTask(this, () -> disasterManager.start());
        }
    }

    @Override
    public void onDisable() {
        if (disasterManager != null) {
            disasterManager.stop();
        }
        if (radiation != null) {
            radiation.disable();
        }
        // 물·불을 되돌리던 중이었다면 바로 마무리
        com.qmftm.quantumdisasters.disaster.TemporaryBlocks.flushPending();
    }

    /** 방사선 (가이거 계수기, 피폭량) */
    public RadiationService radiation() {
        return radiation;
    }

    /** 월드 시드로 만든 지각판 지도 */
    public PlateRegistry plates() {
        return plates;
    }

    public DisasterManager disasterManager() {
        return disasterManager;
    }
}
