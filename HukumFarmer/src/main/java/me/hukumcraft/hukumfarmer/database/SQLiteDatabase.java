package me.hukumcraft.hukumfarmer.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.hukumcraft.hukumfarmer.HukumFarmer;
import me.hukumcraft.hukumfarmer.model.Farmer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * SQLite database backend using HikariCP.
 */
public class SQLiteDatabase implements DatabaseManager {

    private final HukumFarmer plugin;
    private HikariDataSource dataSource;

    public SQLiteDatabase(HukumFarmer plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), "database.db");
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("HukumFarmer-SQLite-Pool");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setConnectionTestQuery("SELECT 1");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(10000);

        this.dataSource = new HikariDataSource(config);

        createTables();
    }

    private void createTables() throws SQLException {
        try (Connection connection = getConnection();
             Statement stmt = connection.createStatement()) {

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS hukum_farmers (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "owner_uuid VARCHAR(36) NOT NULL UNIQUE, " +
                    "owner_name VARCHAR(64) NOT NULL, " +
                    "custom_name VARCHAR(64) NOT NULL, " +
                    "world VARCHAR(64), " +
                    "x DOUBLE, " +
                    "y DOUBLE, " +
                    "z DOUBLE, " +
                    "yaw REAL, " +
                    "pitch REAL, " +
                    "npc_id VARCHAR(64), " +
                    "level INT NOT NULL, " +
                    "xp BIGINT NOT NULL, " +
                    "earnings DOUBLE NOT NULL, " +
                    "auto_harvest BOOLEAN NOT NULL, " +
                    "auto_sell BOOLEAN NOT NULL, " +
                    "total_harvested BIGINT NOT NULL, " +
                    "total_earnings DOUBLE NOT NULL, " +
                    "created_at BIGINT NOT NULL, " +
                    "last_harvest_time BIGINT NOT NULL" +
                    ");");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS hukum_farmer_storage (" +
                    "farmer_id VARCHAR(36) NOT NULL, " +
                    "crop_id VARCHAR(32) NOT NULL, " +
                    "amount BIGINT NOT NULL, " +
                    "PRIMARY KEY (farmer_id, crop_id)" +
                    ");");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS hukum_player_preferences (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY, " +
                    "language VARCHAR(8) NOT NULL" +
                    ");");

            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_owner_uuid ON hukum_farmers(owner_uuid);");

            // Safe migration for npc_id if upgrading an existing database
            try {
                stmt.executeUpdate("ALTER TABLE hukum_farmers ADD COLUMN npc_id VARCHAR(64);");
            } catch (SQLException ignored) {}
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("HikariDataSource is not initialized or closed.");
        }
        return dataSource.getConnection();
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public void saveFarmer(Farmer farmer) throws SQLException {
        if (farmer == null) return;
        saveFarmersBatch(Collections.singletonList(farmer));
    }

    @Override
    public void saveFarmersBatch(Collection<Farmer> farmers) throws SQLException {
        if (farmers == null || farmers.isEmpty()) return;

        String farmerSql = "INSERT OR REPLACE INTO hukum_farmers " +
                "(id, owner_uuid, owner_name, custom_name, world, x, y, z, yaw, pitch, npc_id, level, xp, earnings, auto_harvest, auto_sell, total_harvested, total_earnings, created_at, last_harvest_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String deleteStorageSql = "DELETE FROM hukum_farmer_storage WHERE farmer_id = ?";
        String insertStorageSql = "INSERT OR REPLACE INTO hukum_farmer_storage (farmer_id, crop_id, amount) VALUES (?, ?, ?)";

        try (Connection connection = getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement farmerStmt = connection.prepareStatement(farmerSql);
                 PreparedStatement delStorageStmt = connection.prepareStatement(deleteStorageSql);
                 PreparedStatement insStorageStmt = connection.prepareStatement(insertStorageSql)) {

                for (Farmer farmer : farmers) {
                    farmerStmt.setString(1, farmer.getId().toString());
                    farmerStmt.setString(2, farmer.getOwnerUUID().toString());
                    farmerStmt.setString(3, farmer.getOwnerName());
                    farmerStmt.setString(4, farmer.getCustomName());

                    Location loc = farmer.getLocation();
                    if (loc != null && loc.getWorld() != null) {
                        farmerStmt.setString(5, loc.getWorld().getName());
                        farmerStmt.setDouble(6, loc.getX());
                        farmerStmt.setDouble(7, loc.getY());
                        farmerStmt.setDouble(8, loc.getZ());
                        farmerStmt.setFloat(9, loc.getYaw());
                        farmerStmt.setFloat(10, loc.getPitch());
                    } else {
                        farmerStmt.setNull(5, Types.VARCHAR);
                        farmerStmt.setNull(6, Types.DOUBLE);
                        farmerStmt.setNull(7, Types.DOUBLE);
                        farmerStmt.setNull(8, Types.DOUBLE);
                        farmerStmt.setNull(9, Types.FLOAT);
                        farmerStmt.setNull(10, Types.FLOAT);
                    }

                    farmerStmt.setString(11, farmer.getNpcId());
                    farmerStmt.setInt(12, farmer.getLevel());
                    farmerStmt.setLong(13, farmer.getXp());
                    farmerStmt.setDouble(14, farmer.getEarnings());
                    farmerStmt.setBoolean(15, farmer.isAutoHarvest());
                    farmerStmt.setBoolean(16, farmer.isAutoSell());
                    farmerStmt.setLong(17, farmer.getTotalHarvested());
                    farmerStmt.setDouble(18, farmer.getTotalEarnings());
                    farmerStmt.setLong(19, farmer.getCreatedAt());
                    farmerStmt.setLong(20, farmer.getLastHarvestTime());
                    farmerStmt.addBatch();

                    // Update storage
                    delStorageStmt.setString(1, farmer.getId().toString());
                    delStorageStmt.addBatch();

                    for (Map.Entry<String, Long> entry : farmer.getStoredCrops().entrySet()) {
                        if (entry.getValue() > 0) {
                            insStorageStmt.setString(1, farmer.getId().toString());
                            insStorageStmt.setString(2, entry.getKey());
                            insStorageStmt.setLong(3, entry.getValue());
                            insStorageStmt.addBatch();
                        }
                    }

                    farmer.resetDirty();
                }

                farmerStmt.executeBatch();
                delStorageStmt.executeBatch();
                insStorageStmt.executeBatch();

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public Optional<Farmer> loadFarmer(UUID ownerUUID) throws SQLException {
        if (ownerUUID == null) return Optional.empty();
        String sql = "SELECT * FROM hukum_farmers WHERE owner_uuid = ?";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, ownerUUID.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Farmer farmer = mapFarmer(rs);
                    loadStorage(connection, farmer);
                    farmer.resetDirty();
                    return Optional.of(farmer);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Collection<Farmer> loadAllFarmers() throws SQLException {
        List<Farmer> list = new ArrayList<>();
        String sql = "SELECT * FROM hukum_farmers";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Farmer farmer = mapFarmer(rs);
                loadStorage(connection, farmer);
                farmer.resetDirty();
                list.add(farmer);
            }
        }
        return list;
    }

    @Override
    public void deleteFarmer(UUID ownerUUID) throws SQLException {
        if (ownerUUID == null) return;
        String selectIdSql = "SELECT id FROM hukum_farmers WHERE owner_uuid = ?";
        String delFarmerSql = "DELETE FROM hukum_farmers WHERE owner_uuid = ?";
        String delStorageSql = "DELETE FROM hukum_farmer_storage WHERE farmer_id = ?";

        try (Connection connection = getConnection()) {
            String farmerId = null;
            try (PreparedStatement stmt = connection.prepareStatement(selectIdSql)) {
                stmt.setString(1, ownerUUID.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        farmerId = rs.getString("id");
                    }
                }
            }

            if (farmerId != null) {
                try (PreparedStatement stmt = connection.prepareStatement(delStorageSql)) {
                    stmt.setString(1, farmerId);
                    stmt.executeUpdate();
                }
            }

            try (PreparedStatement stmt = connection.prepareStatement(delFarmerSql)) {
                stmt.setString(1, ownerUUID.toString());
                stmt.executeUpdate();
            }
        }
    }

    @Override
    public void savePlayerLanguage(UUID playerUUID, String langCode) throws SQLException {
        if (playerUUID == null || langCode == null) return;
        String sql = "INSERT OR REPLACE INTO hukum_player_preferences (player_uuid, language) VALUES (?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, langCode);
            stmt.executeUpdate();
        }
    }

    @Override
    public String loadPlayerLanguage(UUID playerUUID) throws SQLException {
        if (playerUUID == null) return null;
        String sql = "SELECT language FROM hukum_player_preferences WHERE player_uuid = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("language");
                }
            }
        }
        return null;
    }

    @Override
    public Map<UUID, String> loadAllPlayerLanguages() throws SQLException {
        Map<UUID, String> result = new HashMap<>();
        String sql = "SELECT player_uuid, language FROM hukum_player_preferences";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    String lang = rs.getString("language");
                    result.put(uuid, lang);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return result;
    }

    private Farmer mapFarmer(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID ownerUUID = UUID.fromString(rs.getString("owner_uuid"));
        String ownerName = rs.getString("owner_name");
        String customName = rs.getString("custom_name");

        String worldName = rs.getString("world");
        Location loc = null;
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");
                loc = new Location(world, x, y, z, yaw, pitch);
            }
        }

        String npcId = rs.getString("npc_id");
        if (npcId == null || npcId.isEmpty()) {
            npcId = "farmer-" + ownerUUID.toString();
        }

        int level = Math.max(1, rs.getInt("level"));
        long xp = Math.max(0L, rs.getLong("xp"));
        double earnings = Math.max(0.0, rs.getDouble("earnings"));
        boolean autoHarvest = rs.getBoolean("auto_harvest");
        boolean autoSell = rs.getBoolean("auto_sell");
        long totalHarvested = Math.max(0L, rs.getLong("total_harvested"));
        double totalEarnings = Math.max(0.0, rs.getDouble("total_earnings"));
        long createdAt = rs.getLong("created_at");
        if (createdAt <= 0) createdAt = System.currentTimeMillis();
        long lastHarvestTime = Math.max(0L, rs.getLong("last_harvest_time"));

        return new Farmer(id, ownerUUID, ownerName, customName, loc, npcId, level, xp, earnings, autoHarvest, autoSell, totalHarvested, totalEarnings, createdAt, lastHarvestTime);
    }

    private void loadStorage(Connection connection, Farmer farmer) throws SQLException {
        String sql = "SELECT crop_id, amount FROM hukum_farmer_storage WHERE farmer_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, farmer.getId().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String cropId = rs.getString("crop_id");
                    long amount = rs.getLong("amount");
                    if (cropId != null && amount > 0) {
                        farmer.setCropAmount(cropId.toUpperCase(), amount);
                    }
                }
            }
        }
    }
}
