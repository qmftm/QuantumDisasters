package com.qmftm.quantumdisasters.items;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 제작할 수 있는 특수 아이템 (가이거 계수기, 라디오 등).
 *
 * <p>아이템 모양과 제작법은 config 의 [configPath].item / [configPath].recipe 에서 읽습니다.
 * (나중에 GUI 로 바꿀 수 있도록 전부 설정값으로 둡니다. 바꾼 뒤 reload() 를 부르면 다시 등록됩니다.)
 * 아이템은 플러그인 표식(PersistentData)으로 구분하므로 같은 재료의 일반 아이템과 섞이지 않습니다.
 */
public final class CraftableItem {

    private final QuantumDisasters plugin;
    private final String configPath;
    private final Material defaultMaterial;
    private final Component name;
    private final List<String> lore;
    private final NamespacedKey key;

    public CraftableItem(QuantumDisasters plugin, String id, String configPath, Material defaultMaterial,
                         Component name, List<String> lore) {
        this.plugin = plugin;
        this.configPath = configPath;
        this.defaultMaterial = defaultMaterial;
        this.name = name;
        this.lore = lore;
        this.key = new NamespacedKey(plugin, id);
    }

    public NamespacedKey recipeKey() {
        return key;
    }

    private ConfigurationSection config() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(configPath);
        return section != null ? section : plugin.getConfig().createSection(configPath);
    }

    public ItemStack create() {
        Material type = Material.matchMaterial(config().getString("item", defaultMaterial.getKey().toString()));
        ItemStack item = new ItemStack(type != null ? type : defaultMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean is(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** 주 손이나 보조 손에 들고 있는지 */
    public boolean isHolding(Player player) {
        return is(player.getInventory().getItemInMainHand()) || is(player.getInventory().getItemInOffHand());
    }

    /** 설정의 제작법을 (다시) 등록합니다. */
    public void reload() {
        Bukkit.removeRecipe(key);
        ConfigurationSection recipe = config().getConfigurationSection("recipe");
        if (recipe == null || !recipe.getBoolean("enabled", true)) {
            return;
        }
        List<String> shape = recipe.getStringList("shape");
        ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
        if (shape.isEmpty() || shape.size() > 3 || ingredients == null) {
            plugin.getLogger().warning(key.getKey() + " 제작법이 올바르지 않습니다 (shape 1~3줄, ingredients 필요).");
            return;
        }
        ShapedRecipe shaped = new ShapedRecipe(key, create());
        shaped.shape(shape.toArray(String[]::new));
        for (String symbol : ingredients.getKeys(false)) {
            Material material = Material.matchMaterial(ingredients.getString(symbol, ""));
            if (symbol.length() != 1 || material == null) {
                plugin.getLogger().warning(key.getKey() + " 제작법의 재료가 올바르지 않습니다: " + symbol);
                return;
            }
            shaped.setIngredient(symbol.charAt(0), material);
        }
        Bukkit.addRecipe(shaped);
        plugin.getLogger().info(key.getKey() + " 제작법 등록됨: " + describeRecipe());
        if (recipe.getBoolean("discover", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.discoverRecipe(key);
            }
        }
    }

    /** 접속한 플레이어의 제작법 책에 보이게 합니다. */
    public void discover(Player player) {
        if (config().getBoolean("recipe.discover", true)) {
            player.discoverRecipe(key);
        }
    }

    /** 제작법 표시용 (예: " R  / CGC / CIC  R=redstone ..."). */
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
