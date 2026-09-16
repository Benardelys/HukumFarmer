package me.hukumcraft.hukumfarmer.manager;

import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.compatibility.CompatibilityUtil;
import me.hukumcraft.hukumfarmer.model.CropConfig;
import me.hukumcraft.hukumfarmer.model.CropType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Registry and manager for all supported crops.
 */
public class CropManager {

    private final HukumFarmer plugin;
    private final Map<String, CropConfig> cropsById = new LinkedHashMap<>();
    private final Map<Material, CropConfig> cropsByBlockMaterial = new HashMap<>();
    private final Map<Material, CropConfig> cropsByItemMaterial = new HashMap<>();

    public CropManager(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    public void loadCrops() {
        cropsById.clear();
        cropsByBlockMaterial.clear();
        cropsByItemMaterial.clear();

        FileConfiguration config = plugin.getConfigManager().getCropsConfig();
        ConfigurationSection section = config.getConfigurationSection("crops");
        if (section == null) {
            plugin.getLogger().warning("No crops section found in crops.yml!");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection cropSection = section.getConfigurationSection(key);
            if (cropSection == null) continue;

            boolean enabled = cropSection.getBoolean("enabled", true);
            String itemMatName = cropSection.getString("material", key);
            String blockMatName = cropSection.getString("block-material", key);

            Material itemMat = CompatibilityUtil.matchMaterial(itemMatName).orElse(null);
            Material blockMat = CompatibilityUtil.matchMaterial(blockMatName).orElse(null);

            // Fallback to CropType defaults if material matching failed
            if (itemMat == null || blockMat == null) {
                try {
                    CropType type = CropType.valueOf(key.toUpperCase());
                    if (itemMat == null) itemMat = type.getItemMaterial();
                    if (blockMat == null) blockMat = type.getBlockMaterial();
                } catch (IllegalArgumentException ignored) {}
            }

            if (itemMat == null || blockMat == null) {
                plugin.getLogger().warning("Skipping crop '" + key + "' due to invalid material configuration.");
                continue;
            }

            String displayName = cropSection.getString("display-name", key);
            double sellPrice = cropSection.getDouble("sell-price", 1.0);
            int xpReward = cropSection.getInt("xp-reward", 1);
            boolean autoHarvest = cropSection.getBoolean("auto-harvest", true);
            boolean autoSell = cropSection.getBoolean("auto-sell", true);
            int guiSlot = cropSection.getInt("gui-slot", 0);

            CropConfig cropConfig = new CropConfig(
                    key.toUpperCase(),
                    enabled,
                    itemMat,
                    blockMat,
                    displayName,
                    sellPrice,
                    xpReward,
                    autoHarvest,
                    autoSell,
                    guiSlot
            );

            cropsById.put(key.toUpperCase(), cropConfig);
            if (enabled) {
                cropsByBlockMaterial.put(blockMat, cropConfig);
                cropsByItemMaterial.put(itemMat, cropConfig);
            }
        }

        plugin.getLogger().info("Loaded " + cropsById.size() + " crops (" + cropsByBlockMaterial.size() + " enabled).");
    }

    public Optional<CropConfig> getCrop(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(cropsById.get(id.toUpperCase()));
    }

    public Optional<CropConfig> getCropByBlock(Block block) {
        if (block == null) return Optional.empty();
        return Optional.ofNullable(cropsByBlockMaterial.get(block.getType()));
    }

    public Optional<CropConfig> getCropByItem(Material material) {
        if (material == null) return Optional.empty();
        return Optional.ofNullable(cropsByItemMaterial.get(material));
    }

    public Collection<CropConfig> getAllCrops() {
        return Collections.unmodifiableCollection(cropsById.values());
    }

    public Collection<CropConfig> getEnabledCrops() {
        List<CropConfig> list = new ArrayList<>();
        for (CropConfig config : cropsById.values()) {
            if (config.isEnabled()) {
                list.add(config);
            }
        }
        return list;
    }
}
