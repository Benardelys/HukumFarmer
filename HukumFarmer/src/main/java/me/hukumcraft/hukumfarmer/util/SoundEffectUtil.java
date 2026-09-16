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
     * Plays a sound to a player with specified volume and pitch.
     */
    public static void playSound(Player player, String soundName, float volume, float pitch) {
        if (player == null || soundName == null || soundName.isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            // Unknown sound for this MC version, gracefully ignore
        }
    }

    /**
     * Plays a sound at a world location.
     */
    public static void playSound(Location location, String soundName, float volume, float pitch) {
        if (location == null || location.getWorld() == null || soundName == null || soundName.isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            location.getWorld().playSound(location, sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {}
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
            try {
                Particle particle = Particle.valueOf(particleName.toUpperCase());
                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    /**
     * Plays particle effect at block location.
     */
    public static void spawnParticle(Location location, String particleName, int count) {
        if (location == null || location.getWorld() == null || particleName == null || particleName.isEmpty()) return;
        try {
            Particle particle = Particle.valueOf(particleName.toUpperCase());
            location.getWorld().spawnParticle(particle, location.clone().add(0.5, 0.5, 0.5), count, 0.2, 0.2, 0.2, 0.02);
        } catch (IllegalArgumentException ignored) {}
    }
}
