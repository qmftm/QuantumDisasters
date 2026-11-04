package com.qmftm.quantumdisasters;

import com.qmftm.quantumdisasters.disaster.MeteorListener;
import com.qmftm.quantumdisasters.platform.Platforms;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuantumDisasters extends JavaPlugin {

    private DisasterManager disasterManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Platforms.init();
        getLogger().info(Platforms.hasFloodgate()
                ? "Floodgate 연동됨: Bedrock 플레이어를 구분합니다."
                : "Floodgate 없음: 모든 플레이어를 Java 로 취급합니다.");

        disasterManager = new DisasterManager(this);
        getServer().getPluginManager().registerEvents(new MeteorListener(this), this);
        getServer().getPluginManager().registerEvents(new GameListener(disasterManager), this);

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
    }

    public DisasterManager disasterManager() {
        return disasterManager;
    }
}
