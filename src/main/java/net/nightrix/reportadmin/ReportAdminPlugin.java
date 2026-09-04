package net.nightrix.reportadmin;

import net.nightrix.reportadmin.commands.AdminRequestCommands;
import net.nightrix.reportadmin.commands.LogsCommand;
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

        ReportCommands reportCommands = new ReportCommands(reportManager, playerSelectMenu, ticketLogManager);
        getCommand("flag").setExecutor(reportCommands);
        getCommand("myreports").setExecutor(reportCommands);
        getCommand("reportlist").setExecutor(reportCommands);

        AdminRequestCommands adminRequestCommands = new AdminRequestCommands(adminRequestManager, ticketLogManager);
        getCommand("requestadmin").setExecutor(adminRequestCommands);
        getCommand("myadminrequests").setExecutor(adminRequestCommands);
        getCommand("adminrequests").setExecutor(adminRequestCommands);

        LogsCommand logsCommand = new LogsCommand(ticketLogManager);
        getCommand("ralogs").setExecutor(logsCommand);

        getLogger().info("ReportAdmin enabled: /flag, /myreports, /reportlist (alias /reports), "
                + "/requestadmin, /myadminrequests, /adminrequests, /ralogs");
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
