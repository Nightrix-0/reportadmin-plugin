package net.nightrix.reportadmin.storage;

import net.nightrix.reportadmin.model.TicketLog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Backs "/ralogs": a flat, append-only history of every report and admin request once it's
 * been closed (or cancelled) and pulled out of the active {@link ReportManager}/
 * {@link AdminRequestManager} lists. Stored in logs.yml in the plugin's data folder.
 */
public class TicketLogManager {

    /** How many of the most recent entries /ralogs shows in one dialog - kept bounded so the
     *  list stays usable no matter how many tickets have piled up over the life of the server. */
    public static final int MAX_DISPLAYED = 50;

    private final JavaPlugin plugin;
    private final File file;
    private final List<TicketLog> logs = new ArrayList<>();
    private int nextId = 1;

    public TicketLogManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "logs.yml");
        load();
    }

    public synchronized TicketLog add(TicketLog.Type type, int originalId, String submitterName, String targetName,
                                       String reason, String evidence, String finalStatus, String closedByName,
                                       long createdAt) {
        TicketLog log = new TicketLog(nextId++, type, originalId, submitterName, targetName, reason, evidence,
                finalStatus, closedByName, createdAt, System.currentTimeMillis());
        logs.add(log);
        save();
        return log;
    }

    /** Most recent first, capped at {@link #MAX_DISPLAYED}. */
    public synchronized List<TicketLog> getRecent() {
        return logs.stream()
                .sorted(Comparator.comparingLong(TicketLog::getClosedAt).reversed())
                .limit(MAX_DISPLAYED)
                .collect(Collectors.toList());
    }

    public synchronized TicketLog getById(int id) {
        for (TicketLog log : logs) {
            if (log.getId() == id) {
                return log;
            }
        }
        return null;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("logs");
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
                TicketLog.Type type = TicketLog.Type.valueOf(s.getString("type"));
                int originalId = s.getInt("originalId");
                String submitterName = s.getString("submitterName", "Unknown");
                String targetName = s.getString("targetName", null);
                String reason = s.getString("reason", "");
                String evidence = s.getString("evidence", null);
                String finalStatus = s.getString("finalStatus", "CLOSED");
                String closedByName = s.getString("closedByName", "Unknown");
                long createdAt = s.getLong("createdAt", System.currentTimeMillis());
                long closedAt = s.getLong("closedAt", System.currentTimeMillis());
                TicketLog log = new TicketLog(id, type, originalId, submitterName, targetName, reason,
                        evidence, finalStatus, closedByName, createdAt, closedAt);
                logs.add(log);
                nextId = Math.max(nextId, id + 1);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Skipping malformed log entry '" + key + "'", e);
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (TicketLog log : logs) {
            String path = "logs." + log.getId();
            yaml.set(path + ".type", log.getType().name());
            yaml.set(path + ".originalId", log.getOriginalId());
            yaml.set(path + ".submitterName", log.getSubmitterName());
            yaml.set(path + ".targetName", log.getTargetName());
            yaml.set(path + ".reason", log.getReason());
            yaml.set(path + ".evidence", log.getEvidence());
            yaml.set(path + ".finalStatus", log.getFinalStatus());
            yaml.set(path + ".closedByName", log.getClosedByName());
            yaml.set(path + ".createdAt", log.getCreatedAt());
            yaml.set(path + ".closedAt", log.getClosedAt());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save logs.yml", e);
        }
    }
}
