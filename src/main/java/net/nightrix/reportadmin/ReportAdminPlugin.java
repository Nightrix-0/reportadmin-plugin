package net.nightrix.reportadmin;

import net.nightrix.reportadmin.commands.AdminRequestCommands;
import net.nightrix.reportadmin.commands.ReportCommands;
import net.nightrix.reportadmin.gui.PlayerSelectMenu;
import net.nightrix.reportadmin.storage.AdminRequestManager;
import net.nightrix.reportadmin.storage.ReportManager;
import net.nightrix.reportadmin.storage.TicketLogManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class ReportAdminPlugin extends JavaPlugin {

    private ReportManager reportManager;
    private AdminRequestManager adminRequestManager;
    private TicketLogManager ticketLogManager;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        this.reportManager = new ReportManager(this);
        this.adminRequestManager = new AdminRequestManager(this);
        this.ticketLogManager = new TicketLogManager(this);

        PlayerSelectMenu playerSelectMenu = new PlayerSelectMenu(this);
        Bukkit.getPluginManager().registerEvents(playerSelectMenu, this);

        ReportCommands reportCommands = new ReportCommands(reportManager, playerSelectMenu, ticketLogManager, adminRequestManager);
        getCommand("report").setExecutor(reportCommands);
        getCommand("reports").setExecutor(reportCommands);

        AdminRequestCommands adminRequestCommands = new AdminRequestCommands(adminRequestManager, ticketLogManager);
        getCommand("request").setExecutor(adminRequestCommands);
        getCommand("requests").setExecutor(adminRequestCommands);

        getLogger().info("ReportAdmin enabled: /report [player], /report logs [player], "
                + "/reports [player], /reports view [player], /request staff, /requests, /requests view [player]");
    }

    @Override
    public void onDisable() {
        if (reportManager != null) {
            reportManager.save();
        }
        if (adminRequestManager != null) {
            adminRequestManager.save();
        }
        if (ticketLogManager != null) {
            ticketLogManager.save();
        }
    }
}
