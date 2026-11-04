package com.qmftm.quantumdisasters.tectonics;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** 월드마다 그 월드 시드로 만든 판 지도를 하나씩 들고 있습니다. */
public final class PlateRegistry {

    private final Supplier<FileConfiguration> config;
    private final Map<UUID, TectonicPlates> byWorld = new HashMap<>();

    public PlateRegistry(Supplier<FileConfiguration> config) {
        this.config = config;
    }

    public TectonicPlates of(World world) {
        return byWorld.computeIfAbsent(world.getUID(), id -> new TectonicPlates(
                world.getSeed(),
                config.get().getLong("disasters.earthquake.plates.salt", 0),
                config.get().getDouble("disasters.earthquake.plates.size", 600)));
    }

    /** 설정을 다시 불러왔을 때 호출합니다. */
    public void clear() {
        byWorld.clear();
    }
}
