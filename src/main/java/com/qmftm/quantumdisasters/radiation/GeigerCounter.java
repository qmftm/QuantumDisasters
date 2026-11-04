package com.qmftm.quantumdisasters.radiation;

import com.qmftm.quantumdisasters.QuantumDisasters;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/**
 * 가이거 계수기 아이템과 제작법.
 *
 * <p>제작법은 config.yml 의 radiation.geiger-counter.recipe 에서 읽습니다.
 * (나중에 GUI 로 바꿀 수 있도록 제작법은 전부 설정값으로 둡니다. 바꾼 뒤 reload() 를 부르면 다시 등록됩니다.)
 */
public final class GeigerCounter {

    private final QuantumDisasters plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey recipeKey;

    public GeigerCounter(QuantumDisasters plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "geiger_counter");
        this.recipeKey = new NamespacedKey(plugin, "geiger_counter");
    }

    public NamespacedKey recipeKey() {
        return recipeKey;
    }

    private ConfigurationSection config() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("radiation.geiger-counter");
        return section != null ? section : plugin.getConfig().createSection("radiation.geiger-counter");
    }

    /** 가이거 계수기 아이템 하나. */
    public ItemStack create() {
        Material type = Material.matchMaterial(config().getString("item", "minecraft:clock"));
        ItemStack item = new ItemStack(type != null ? type : Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("가이거 계수기", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("손에 들고 있으면 주변 방사선을 측정합니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("딸깍이는 소리가 빠를수록 위험합니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGeiger(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public boolean isHolding(Player player) {
        return isGeiger(player.getInventory().getItemInMainHand()) || isGeiger(player.getInventory().getItemInOffHand());
    }

    /** 설정의 제작법을 (다시) 등록합니다. */
    public void reload() {
        Bukkit.removeRecipe(recipeKey);
        ConfigurationSection recipe = config().getConfigurationSection("recipe");
        if (recipe == null || !recipe.getBoolean("enabled", true)) {
            return;
        }
        List<String> shape = recipe.getStringList("shape");
        ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
        if (shape.isEmpty() || shape.size() > 3 || ingredients == null) {
            plugin.getLogger().warning("가이거 계수기 제작법이 올바르지 않습니다 (shape 1~3줄, ingredients 필요).");
            return;
        }
        ShapedRecipe shaped = new ShapedRecipe(recipeKey, create());
        shaped.shape(shape.toArray(String[]::new));
        for (String symbol : ingredients.getKeys(false)) {
            Material material = Material.matchMaterial(ingredients.getString(symbol, ""));
            if (symbol.length() != 1 || material == null) {
                plugin.getLogger().warning("가이거 계수기 제작법의 재료가 올바르지 않습니다: " + symbol);
                return;
            }
            shaped.setIngredient(symbol.charAt(0), material);
        }
        Bukkit.addRecipe(shaped);
        plugin.getLogger().info("가이거 계수기 제작법 등록됨: " + describeRecipe());
        if (recipe.getBoolean("discover", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.discoverRecipe(recipeKey);
            }
        }
    }

    /** 제작법 재료 표시용 (예: "C=copper_ingot"). */
    public String describeRecipe() {
        ConfigurationSection recipe = config().getConfigurationSection("recipe");
        if (recipe == null) {
            return "없음";
        }
        StringBuilder sb = new StringBuilder(String.join(" / ", recipe.getStringList("shape")));
        ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
        if (ingredients != null) {
            for (String symbol : ingredients.getKeys(false)) {
                sb.append("  ").append(symbol).append('=')
                        .append(ingredients.getString(symbol, "").replace("minecraft:", "").toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }
}
