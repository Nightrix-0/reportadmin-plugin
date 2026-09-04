package net.nightrix.reportadmin.commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.nightrix.reportadmin.model.TicketLog;
import net.nightrix.reportadmin.storage.TicketLogManager;
import net.nightrix.reportadmin.util.DialogUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles /ralogs - the staff-only history of every report and admin request that has been
 * closed (or, for admin requests, cancelled by its own author) and pulled out of the active
 * lists. Gated by the "reportadmin.staff" permission in plugin.yml, same as /reportlist and
 * /adminrequests - default op, but grantable through LuckPerms or any other permission plugin
 * like any other node. Read-only: nothing here can be reopened or edited, it's just history.
 */
public class LogsCommand implements CommandExecutor {

    private final TicketLogManager ticketLogManager;

    public LogsCommand(TicketLogManager ticketLogManager) {
        this.ticketLogManager = ticketLogManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }
        openList(player);
        return true;
    }

    private void openList(Player viewer) {
        List<TicketLog> entries = ticketLogManager.getRecent();
        if (entries.isEmpty()) {
            viewer.sendMessage(DialogUtil.info("The logs are empty - nothing has been closed yet."));
            return;
        }

        List<ActionButton> buttons = new ArrayList<>();
        for (TicketLog entry : entries) {
            String kind = entry.getType() == TicketLog.Type.REPORT ? "Report" : "Admin Request";
            String labelText = "[" + kind + " #" + entry.getOriginalId() + "] " + entry.getSubmitterName()
                    + " [" + entry.getFinalStatus() + "]";
            buttons.add(ActionButton.builder(Component.text(labelText))
                    .tooltip(Component.text(entry.getReason()))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openDetail(p, entry);
                        }
                    }, DialogUtil.singleUse()))
                    .build());
        }

        ActionButton close = ActionButton.builder(Component.text("Close")).action(null).build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Ticket Logs (most recent " + entries.size() + ")")).build())
                .type(DialogType.multiAction(buttons, close, 1)));

        viewer.showDialog(dialog);
    }

    private void openDetail(Player viewer, TicketLog entry) {
        List<DialogBody> body = new ArrayList<>();
        boolean isReport = entry.getType() == TicketLog.Type.REPORT;
        body.add(DialogBody.plainMessage(Component.text((isReport ? "Report" : "Admin Request") + " #" + entry.getOriginalId())));
        body.add(DialogBody.plainMessage(Component.text((isReport ? "Reported By: " : "Requested By: ") + entry.getSubmitterName())));
        if (isReport) {
            body.add(DialogBody.plainMessage(Component.text("Reported Player: " + entry.getTargetName())));
        }
        body.add(DialogBody.plainMessage(Component.text("Reason: " + entry.getReason())));
        if (isReport) {
            body.add(DialogBody.plainMessage(Component.text("Evidence: " + entry.getEvidence())));
        }
        body.add(DialogBody.plainMessage(Component.text("Final Status: " + entry.getFinalStatus())));
        body.add(DialogBody.plainMessage(Component.text("Closed By: " + entry.getClosedByName())));
        body.add(DialogBody.plainMessage(Component.text("Filed: " + DialogUtil.formatDate(entry.getCreatedAt()), NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("Closed: " + DialogUtil.formatDate(entry.getClosedAt()), NamedTextColor.GRAY)));

        ActionButton back = ActionButton.builder(Component.text("Back"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        openList(p);
                    }
                }, DialogUtil.singleUse()))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Log Entry #" + entry.getId())).body(body).build())
                .type(DialogType.multiAction(List.of(), back, 1)));

        viewer.showDialog(dialog);
    }
}
