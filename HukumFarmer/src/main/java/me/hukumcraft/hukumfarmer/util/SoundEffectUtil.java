package me.hukumcraft.hukumfarmer.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Safe sound and particle player with configuration support.
 */
public final class SoundEffectUtil {

    private SoundEffectUtil() {}

    /**
     * Safely resolves a Sound enum with version aliasing.
     */
    public static Sound matchSound(String soundName) {
        if (soundName == null || soundName.trim().isEmpty()) return null;
        String upper = soundName.toUpperCase().trim();
        try {
            return Sound.valueOf(upper);
        } catch (IllegalArgumentException ignored) {}

        // Fallbacks / version aliases
        if (upper.equals("ENTITY_EXPERIENCE_ORB_PICKUP")) {
            try { return Sound.valueOf("ENTITY_PLAYER_LEVELUP"); } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    /**
     * Safely resolves a Particle enum with version aliasing (1.21.11 / 26.x).
     */
    public static Particle matchParticle(String particleName) {
        if (particleName == null || particleName.trim().isEmpty()) return null;
        String upper = particleName.toUpperCase().trim();
        try {
            return Particle.valueOf(upper);
        } catch (IllegalArgumentException ignored) {}

        // Particle aliases across Paper versions (e.g. VILLAGER_HAPPY <-> HAPPY_VILLAGER)
        if (upper.equals("VILLAGER_HAPPY")) {
            try { return Particle.valueOf("HAPPY_VILLAGER"); } catch (IllegalArgumentException ignored) {}
        } else if (upper.equals("HAPPY_VILLAGER")) {
            try { return Particle.valueOf("VILLAGER_HAPPY"); } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    /**
     * Plays a sound to a player with specified volume and pitch.
     */
    public static void playSound(Player player, String soundName, float volume, float pitch) {
        if (player == null || soundName == null || soundName.isEmpty()) return;
        Sound sound = matchSound(soundName);
        if (sound != null) {
            try {
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Plays a sound at a world location.
     */
    public static void playSound(Location location, String soundName, float volume, float pitch) {
        if (location == null || location.getWorld() == null || soundName == null || soundName.isEmpty()) return;
        Sound sound = matchSound(soundName);
        if (sound != null) {
            try {
                location.getWorld().playSound(location, sound, volume, pitch);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Plays a configured effect from a ConfigurationSection (sound + particle).
     */
    public static void playEffect(Player player, ConfigurationSection section) {
        if (player == null || section == null) return;
        String soundName = section.getString("sound");
        if (soundName != null && !soundName.isEmpty()) {
            float volume = (float) section.getDouble("volume", 1.0);
            float pitch = (float) section.getDouble("pitch", 1.0);
            playSound(player, soundName, volume, pitch);
        }

        String particleName = section.getString("particle");
        if (particleName != null && !particleName.isEmpty()) {
            Particle particle = matchParticle(particleName);
            if (particle != null) {
                try {
                    player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Plays particle effect at block location.
     */
    public static void spawnParticle(Location location, String particleName, int count) {
        if (location == null || location.getWorld() == null || particleName == null || particleName.isEmpty()) return;
        Particle particle = matchParticle(particleName);
        if (particle != null) {
            try {
                location.getWorld().spawnParticle(particle, location.clone().add(0.5, 0.5, 0.5), count, 0.2, 0.2, 0.2, 0.02);
            } catch (Exception ignored) {}
        }
    }
}
