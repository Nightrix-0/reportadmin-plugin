package net.nightrix.reportadmin.storage;

import net.nightrix.reportadmin.model.AdminRequest;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Loads, saves and queries {@link AdminRequest}s. Backed by a flat adminrequests.yml
 * file in the plugin's data folder.
 */
public class AdminRequestManager {

    private final JavaPlugin plugin;
    private final File file;
    private final List<AdminRequest> requests = new ArrayList<>();
    private int nextId = 1;

    public AdminRequestManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "adminrequests.yml");
        load();
    }

    public synchronized AdminRequest create(UUID playerId, String playerName, String reason) {
        AdminRequest request = new AdminRequest(nextId++, playerId, playerName, reason,
                AdminRequest.Status.OPEN, System.currentTimeMillis());
        requests.add(request);
        save();
        return request;
    }

    public synchronized List<AdminRequest> getByPlayer(UUID playerId) {
        return requests.stream()
                .filter(r -> r.getPlayerId().equals(playerId))
                .sorted(Comparator.comparingLong(AdminRequest::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /** Open requests whose author is currently online - what /adminrequests should list. */
    public synchronized List<AdminRequest> getOpenFromOnlinePlayers() {
        return requests.stream()
                .filter(r -> r.getStatus() == AdminRequest.Status.OPEN)
                .filter(r -> Bukkit.getPlayer(r.getPlayerId()) != null)
                .sorted(Comparator.comparingLong(AdminRequest::getCreatedAt))
                .collect(Collectors.toList());
    }

    public synchronized AdminRequest getById(int id) {
        for (AdminRequest r : requests) {
            if (r.getId() == id) {
                return r;
            }
        }
        return null;
    }

    public synchronized void save(AdminRequest request) {
        save();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("requests");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                int id = Integer.parseInt(key);
                UUID playerId = UUID.fromString(s.getString("playerId"));
                String playerName = s.getString("playerName", "Unknown");
                String reason = s.getString("reason", "");
                AdminRequest.Status status = AdminRequest.Status.valueOf(s.getString("status", "OPEN"));
                long createdAt = s.getLong("createdAt", System.currentTimeMillis());
                AdminRequest request = new AdminRequest(id, playerId, playerName, reason, status, createdAt);
                requests.add(request);
                nextId = Math.max(nextId, id + 1);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Skipping malformed admin request entry '" + key + "'", e);
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (AdminRequest r : requests) {
            String path = "requests." + r.getId();
            yaml.set(path + ".playerId", r.getPlayerId().toString());
            yaml.set(path + ".playerName", r.getPlayerName());
            yaml.set(path + ".reason", r.getReason());
            yaml.set(path + ".status", r.getStatus().name());
            yaml.set(path + ".createdAt", r.getCreatedAt());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save adminrequests.yml", e);
        }
    }
}
