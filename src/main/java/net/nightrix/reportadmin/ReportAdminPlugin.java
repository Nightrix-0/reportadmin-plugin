package net.nightrix.reportadmin;

import net.nightrix.reportadmin.commands.AdminRequestCommands;
import net.nightrix.reportadmin.commands.ReportCommands;
import net.nightrix.reportadmin.gui.PlayerSelectMenu;
import net.nightrix.reportadmin.storage.AdminRequestManager;
import net.nightrix.reportadmin.storage.ReportManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class ReportAdminPlugin extends JavaPlugin {

    private ReportManager reportManager;
    private AdminRequestManager adminRequestManager;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        this.reportManager = new ReportManager(this);
        this.adminRequestManager = new AdminRequestManager(this);

        PlayerSelectMenu playerSelectMenu = new PlayerSelectMenu(this);
        Bukkit.getPluginManager().registerEvents(playerSelectMenu, this);

        ReportCommands reportCommands = new ReportCommands(reportManager, playerSelectMenu);
        getCommand("flag").setExecutor(reportCommands);
        getCommand("myreports").setExecutor(reportCommands);
        getCommand("reportlist").setExecutor(reportCommands);

        AdminRequestCommands adminRequestCommands = new AdminRequestCommands(adminRequestManager);
        getCommand("requestadmin").setExecutor(adminRequestCommands);
        getCommand("myadminrequests").setExecutor(adminRequestCommands);
        getCommand("adminrequests").setExecutor(adminRequestCommands);

        getLogger().info("ReportAdmin enabled: /flag, /myreports, /reportlist (alias /reports), /requestadmin, /myadminrequests, /adminrequests");
    }

    @Override
    public void onDisable() {
        if (reportManager != null) {
            reportManager.save();
        }
        if (adminRequestManager != null) {
            adminRequestManager.save();
        }
    }
}
