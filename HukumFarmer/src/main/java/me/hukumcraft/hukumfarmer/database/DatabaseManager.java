package me.hukumcraft.hukumfarmer.database;

import me.hukumcraft.hukumfarmer.model.Farmer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface defining all database persistence operations.
 */
public interface DatabaseManager {

    void init() throws SQLException;

    void shutdown();

    Connection getConnection() throws SQLException;

    void saveFarmer(Farmer farmer) throws SQLException;

    void saveFarmersBatch(Collection<Farmer> farmers) throws SQLException;

    Optional<Farmer> loadFarmer(UUID ownerUUID) throws SQLException;

    Collection<Farmer> loadAllFarmers() throws SQLException;

    void deleteFarmer(UUID ownerUUID) throws SQLException;

    // Language Preference persistence
    void savePlayerLanguage(UUID playerUUID, String langCode) throws SQLException;

    String loadPlayerLanguage(UUID playerUUID) throws SQLException;

    Map<UUID, String> loadAllPlayerLanguages() throws SQLException;
}
