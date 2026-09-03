package net.nightrix.reportadmin.storage;

import net.nightrix.reportadmin.model.Report;
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
 * Loads, saves and queries {@link Report}s. Backed by a flat reports.yml file
 * in the plugin's data folder - fine for the scale a report queue runs at.
 */
public class ReportManager {

    private final JavaPlugin plugin;
    private final File file;
    private final List<Report> reports = new ArrayList<>();
    private int nextId = 1;

    public ReportManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reports.yml");
        load();
    }

    public synchronized Report create(UUID reporterId, String reporterName, String targetName,
                                       String reason, String evidence) {
        Report report = new Report(nextId++, reporterId, reporterName, targetName, reason, evidence,
                Report.Status.OPEN, System.currentTimeMillis());
        reports.add(report);
        save();
        return report;
    }

    public synchronized List<Report> getByReporter(UUID reporterId) {
        return reports.stream()
                .filter(r -> r.getReporterId().equals(reporterId))
                .sorted(Comparator.comparingLong(Report::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public synchronized List<Report> getAll() {
        return reports.stream()
                .sorted(Comparator.comparingLong(Report::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public synchronized Report getById(int id) {
        for (Report r : reports) {
            if (r.getId() == id) {
                return r;
            }
        }
        return null;
    }

    public synchronized void save(Report report) {
        save();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("reports");
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
                UUID reporterId = UUID.fromString(s.getString("reporterId"));
                String reporterName = s.getString("reporterName", "Unknown");
                String targetName = s.getString("targetName", "Unknown");
                String reason = s.getString("reason", "");
                String evidence = s.getString("evidence", "");
                Report.Status status = Report.Status.valueOf(s.getString("status", "OPEN"));
                long createdAt = s.getLong("createdAt", System.currentTimeMillis());
                Report report = new Report(id, reporterId, reporterName, targetName, reason,
                        evidence, status, createdAt);
                reports.add(report);
                nextId = Math.max(nextId, id + 1);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Skipping malformed report entry '" + key + "'", e);
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Report r : reports) {
            String path = "reports." + r.getId();
            yaml.set(path + ".reporterId", r.getReporterId().toString());
            yaml.set(path + ".reporterName", r.getReporterName());
            yaml.set(path + ".targetName", r.getTargetName());
            yaml.set(path + ".reason", r.getReason());
            yaml.set(path + ".evidence", r.getEvidence());
            yaml.set(path + ".status", r.getStatus().name());
            yaml.set(path + ".createdAt", r.getCreatedAt());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save reports.yml", e);
        }
    }
}
