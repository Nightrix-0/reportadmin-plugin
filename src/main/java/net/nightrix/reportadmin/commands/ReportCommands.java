package net.nightrix.reportadmin.commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.nightrix.reportadmin.gui.PlayerSelectMenu;
import net.nightrix.reportadmin.model.AdminRequest;
import net.nightrix.reportadmin.model.Report;
import net.nightrix.reportadmin.model.TicketLog;
import net.nightrix.reportadmin.storage.AdminRequestManager;
import net.nightrix.reportadmin.storage.ReportManager;
import net.nightrix.reportadmin.storage.TicketLogManager;
import net.nightrix.reportadmin.util.DialogUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Handles /report and /reports.
 *
 * /report [player]        - no player: opens the head-picker then the report form.
 *                            a player: skips the picker, goes straight to the form for them.
 * /report logs [player]   - staff only. no player: every archived ticket. a player: archived
 *                            tickets touching that player (as submitter or reported target).
 * /reports [player]       - no player: your own reports. a player: your own reports against
 *                            that specific player.
 * /reports view [player]  - staff only. no player: every report on file. a player: every
 *                            report against that specific player.
 *
 * Picking who to report (when no player is named) is its own screen (see
 * {@link PlayerSelectMenu}) - a browsable, searchable grid of player heads with an
 * Online/Everyone toggle - rather than a dropdown living inside the report form. Evidence is
 * always required before the form will actually create or update a report.
 */
public class ReportCommands implements CommandExecutor {

    private final ReportManager reportManager;
    private final PlayerSelectMenu playerSelectMenu;
    private final TicketLogManager ticketLogManager;
    private final AdminRequestManager adminRequestManager;

    public ReportCommands(ReportManager reportManager, PlayerSelectMenu playerSelectMenu, TicketLogManager ticketLogManager,
                           AdminRequestManager adminRequestManager) {
        this.reportManager = reportManager;
        this.playerSelectMenu = playerSelectMenu;
        this.ticketLogManager = ticketLogManager;
        this.adminRequestManager = adminRequestManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "report" -> handleReport(player, args);
            case "reports" -> handleReports(player, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- command routing

    private void handleReport(Player player, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("logs")) {
            if (!player.hasPermission("reportadmin.logs")) {
                player.sendMessage(DialogUtil.error("You don't have permission to view the report logs."));
                return;
            }
            openLogsList(player, args.length >= 2 ? args[1] : null);
            return;
        }

        if (args.length >= 1) {
            String targetName = resolvePlayerName(player, args[0]);
            if (targetName == null) {
                return;
            }
            showForm(player, "Flag " + targetName, null, targetName, null, null);
            return;
        }

        openReportMenu(player);
    }

    /** Bare "/report" - shows what you can do next instead of jumping straight into the picker. */
    private void openReportMenu(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.builder(Component.text("Report a Player", NamedTextColor.YELLOW))
                .tooltip(Component.text("/report <player> - or pick one from a list."))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        openCreateFlow(p);
                    }
                }, DialogUtil.singleUse()))
                .build());
        if (player.hasPermission("reportadmin.logs")) {
            buttons.add(ActionButton.builder(Component.text("View Report Logs", NamedTextColor.AQUA))
                    .tooltip(Component.text("/report logs [player] - browse the closed-ticket archive."))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openLogsList(p, null);
                        }
                    }, DialogUtil.singleUse()))
                    .build());
        }

        ActionButton close = ActionButton.builder(Component.text("Close")).action(null).build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Report Commands"))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("Choose what you'd like to do.", NamedTextColor.GRAY))))
                        .build())
                .type(DialogType.multiAction(buttons, close, 1)));

        player.showDialog(dialog);
    }

    private void handleReports(Player player, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("view")) {
            if (!player.hasPermission("reportadmin.staff")) {
                player.sendMessage(DialogUtil.error("You don't have permission to view every report."));
                return;
            }
            if (args.length >= 2) {
                String targetName = args[1];
                openList(player, "Reports Against " + targetName,
                        () -> reportManager.getByTarget(targetName), false,
                        "There are no reports on file against " + targetName + ".");
            } else {
                openList(player, "All Reports", reportManager::getAll, false,
                        "There are no reports on file.");
            }
            return;
        }

        if (args.length >= 1) {
            String targetName = args[0];
            openList(player, "Your Reports Against " + targetName,
                    () -> reportManager.getByReporterAndTarget(player.getUniqueId(), targetName), true,
                    "You haven't filed any reports against " + targetName + ".");
            return;
        }

        openList(player, "My Reports", () -> reportManager.getByReporter(player.getUniqueId()), true,
                "You have not filed any reports.");
    }

    /**
     * Resolves a typed player name against the server's known players (online, or has played
     * before), case-insensitively, returning the canonical stored name - or null (after telling
     * the sender) if nobody by that name is known to this server.
     */
    @SuppressWarnings("deprecation")
    private String resolvePlayerName(Player sender, String typed) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(typed)) {
                return p.getName();
            }
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(typed);
        if (offline.hasPlayedBefore() && offline.getName() != null) {
            return offline.getName();
        }
        sender.sendMessage(DialogUtil.error("No player named '" + typed + "' is known to this server."));
        return null;
    }

    // ---------------------------------------------------------------- create / edit flow

    private void openCreateFlow(Player player) {
        playerSelectMenu.open(player,
                (viewer, target) -> showForm(viewer, "Flag " + target, null, target, null, null),
                () -> player.sendMessage(DialogUtil.info("Cancelled - no player selected.")));
    }

    private void openEditForm(Player player, Report report, Runnable onBack) {
        showForm(player, "Edit Report #" + report.getId(), report.getReason(),
                report.getTargetName(), report.getEvidence(), report, onBack);
    }

    private void openChangeTargetFlow(Player player, Report report, Runnable onBack) {
        playerSelectMenu.open(player,
                (viewer, target) -> {
                    report.setTargetName(target);
                    reportManager.save();
                    viewer.sendMessage(DialogUtil.success("Report #" + report.getId() + " now targets " + target + "."));
                    openDetail(viewer, report, true, onBack);
                },
                () -> openDetail(player, report, true, onBack));
    }

    private void showForm(Player player, String title, String prefillReason, String target,
                           String prefillEvidence, Report editing) {
        showForm(player, title, prefillReason, target, prefillEvidence, editing, null);
    }

    /**
     * Builds and shows the Reason/Evidence form for the given (already-chosen) target player.
     * When {@code editing} is null this creates a brand new report; otherwise it updates the
     * given report in place (and {@code onBack} - if given - is where "Submit" returns control
     * to, since only editing happens from within a detail screen). Evidence is required -
     * leaving it blank just reopens this same form with an error instead of letting the
     * submission through.
     */
    private void showForm(Player player, String title, String prefillReason, String target,
                           String prefillEvidence, Report editing, Runnable onBack) {
        ActionButton submit = ActionButton.builder(Component.text("Submit Report", NamedTextColor.GREEN))
                .tooltip(Component.text("Submit this report to staff."))
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) {
                        return;
                    }
                    String reason = view.getText("reason");
                    String evidence = view.getText("evidence");

                    if (evidence == null || evidence.isBlank()) {
                        p.sendMessage(DialogUtil.error("Evidence is required - the report cannot be submitted without it."));
                        showForm(p, title, reason, target, evidence, editing, onBack);
                        return;
                    }
                    if (reason == null || reason.isBlank()) {
                        p.sendMessage(DialogUtil.error("You must provide a reason."));
                        showForm(p, title, null, target, evidence, editing, onBack);
                        return;
                    }

                    if (editing == null) {
                        Report report = reportManager.create(p.getUniqueId(), p.getName(), target, reason, evidence);
                        p.sendMessage(DialogUtil.success("Report #" + report.getId() + " submitted. Staff have been notified."));
                        notifyStaff(Component.text(p.getName() + " filed a report against " + target + ".",
                                NamedTextColor.YELLOW));
                    } else {
                        editing.setReason(reason);
                        editing.setTargetName(target);
                        editing.setEvidence(evidence);
                        reportManager.save();
                        p.sendMessage(DialogUtil.success("Report #" + editing.getId() + " updated."));
                        if (onBack != null) {
                            onBack.run();
                        }
                    }
                }, DialogUtil.singleUse()))
                .build();

        ActionButton cancel = ActionButton.builder(Component.text("Cancel", NamedTextColor.RED))
                .tooltip(Component.text("Discard without submitting."))
                .action(null)
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title))
                        .body(List.of(
                                DialogBody.plainMessage(Component.text("Reporting: " + target, NamedTextColor.YELLOW)),
                                DialogBody.plainMessage(Component.text("Evidence (a link) is required.", NamedTextColor.GRAY))
                        ))
                        .inputs(List.of(
                                DialogInput.text("reason", Component.text("Reason"))
                                        .initial(prefillReason == null ? "" : prefillReason)
                                        .maxLength(256)
                                        .width(300)
                                        .build(),
                                DialogInput.text("evidence", Component.text("Evidence (link)"))
                                        .initial(prefillEvidence == null ? "" : prefillEvidence)
                                        .maxLength(256)
                                        .width(300)
                                        .build()
                        ))
                        .build())
                .type(DialogType.confirmation(submit, cancel)));

        player.showDialog(dialog);
    }

    // ---------------------------------------------------------------- lists

    /**
     * A generic report list: {@code supplier} is re-run every time this screen is (re)shown, so
     * "Back" from a detail screen always reflects the latest state (e.g. a just-closed report
     * disappearing) instead of a stale snapshot.
     */
    private void openList(Player viewer, String title, Supplier<List<Report>> supplier, boolean ownerMode, String emptyMessage) {
        List<Report> reports = supplier.get();
        if (reports.isEmpty()) {
            viewer.sendMessage(DialogUtil.info(emptyMessage));
            return;
        }

        List<ActionButton> buttons = new ArrayList<>();
        for (Report report : reports) {
            String labelText = "#" + report.getId() + " - " + report.getTargetName() + " [" + report.getStatus() + "]";
            buttons.add(ActionButton.builder(Component.text(labelText))
                    .tooltip(Component.text(report.getReason()))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openDetail(p, report, ownerMode, () -> openList(p, title, supplier, ownerMode, emptyMessage));
                        }
                    }, DialogUtil.singleUse()))
                    .build());
        }

        ActionButton close = ActionButton.builder(Component.text("Close")).action(null).build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title)).build())
                .type(DialogType.multiAction(buttons, close, 1)));

        viewer.showDialog(dialog);
    }

    private void openDetail(Player viewer, Report report, boolean ownerMode, Runnable onBack) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("Reported Player: " + report.getTargetName())));
        if (!ownerMode) {
            body.add(DialogBody.plainMessage(Component.text("Reported By: " + report.getReporterName())));
        }
        body.add(DialogBody.plainMessage(Component.text("Reason: " + report.getReason())));
        body.add(DialogBody.plainMessage(evidenceLine(report.getEvidence(), ownerMode)));
        body.add(DialogBody.plainMessage(Component.text("Status: " + report.getStatus())));
        body.add(DialogBody.plainMessage(Component.text("Filed: " + DialogUtil.formatDate(report.getCreatedAt()), NamedTextColor.GRAY)));

        List<ActionButton> buttons = new ArrayList<>();
        boolean open = report.getStatus() == Report.Status.OPEN;

        if (ownerMode) {
            if (open) {
                buttons.add(ActionButton.builder(Component.text("Edit", NamedTextColor.AQUA))
                        .action(DialogAction.customClick((view, audience) -> {
                            if (audience instanceof Player p) {
                                openEditForm(p, report, onBack);
                            }
                        }, DialogUtil.singleUse()))
                        .build());
                buttons.add(ActionButton.builder(Component.text("Change Player", NamedTextColor.AQUA))
                        .tooltip(Component.text("Pick a different reported player from the head menu."))
                        .action(DialogAction.customClick((view, audience) -> {
                            if (audience instanceof Player p) {
                                openChangeTargetFlow(p, report, onBack);
                            }
                        }, DialogUtil.singleUse()))
                        .build());
                buttons.add(ActionButton.builder(Component.text("Close Report", NamedTextColor.RED))
                        .action(DialogAction.customClick((view, audience) -> {
                            if (audience instanceof Player p) {
                                closeReport(report, p);
                                p.sendMessage(DialogUtil.success("Report #" + report.getId() + " closed and moved to the logs."));
                                onBack.run();
                            }
                        }, DialogUtil.singleUse()))
                        .build());
            }
        } else if (open) {
            buttons.add(ActionButton.builder(Component.text("Close Report", NamedTextColor.RED))
                    .tooltip(Component.text("Mark this report as resolved."))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            closeReport(report, p);
                            p.sendMessage(DialogUtil.success("Report #" + report.getId() + " closed and moved to the logs."));
                            onBack.run();
                        }
                    }, DialogUtil.singleUse()))
                    .build());
        }

        ActionButton back = ActionButton.builder(Component.text("Back"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        onBack.run();
                    }
                }, DialogUtil.singleUse()))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Report #" + report.getId())).body(body).build())
                .type(DialogType.multiAction(buttons, back, 1)));

        viewer.showDialog(dialog);
    }

    /** Logs the report to /report logs and pulls it out of the active list. */
    private void closeReport(Report report, Player closedBy) {
        report.setStatus(Report.Status.CLOSED);
        ticketLogManager.add(TicketLog.Type.REPORT, report.getId(), report.getReporterId(), report.getReporterName(),
                report.getTargetName(), report.getReason(), report.getEvidence(), "CLOSED", closedBy.getName(),
                report.getCreatedAt());
        reportManager.remove(report.getId());
    }

    // ---------------------------------------------------------------- logs ("/report logs")

    private void openLogsList(Player viewer, String filterName) {
        List<TicketLog> entries = filterName == null
                ? ticketLogManager.getRecent()
                : ticketLogManager.getRecentByPlayer(filterName);
        if (entries.isEmpty()) {
            viewer.sendMessage(DialogUtil.info(filterName == null
                    ? "The logs are empty - nothing has been closed yet."
                    : "There's no log history involving " + filterName + "."));
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
                            openLogsDetail(p, entry, filterName);
                        }
                    }, DialogUtil.singleUse()))
                    .build());
        }

        ActionButton close = ActionButton.builder(Component.text("Close")).action(null).build();
        String title = filterName == null
                ? "Ticket Logs (most recent " + entries.size() + ")"
                : "Ticket Logs: " + filterName + " (" + entries.size() + ")";

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title)).build())
                .type(DialogType.multiAction(buttons, close, 1)));

        viewer.showDialog(dialog);
    }

    private void openLogsDetail(Player viewer, TicketLog entry, String filterName) {
        List<DialogBody> body = new ArrayList<>();
        boolean isReport = entry.getType() == TicketLog.Type.REPORT;
        body.add(DialogBody.plainMessage(Component.text((isReport ? "Report" : "Admin Request") + " #" + entry.getOriginalId())));
        body.add(DialogBody.plainMessage(Component.text((isReport ? "Reported By: " : "Requested By: ") + entry.getSubmitterName())));
        if (isReport) {
            body.add(DialogBody.plainMessage(Component.text("Reported Player: " + entry.getTargetName())));
        }
        body.add(DialogBody.plainMessage(Component.text("Reason: " + entry.getReason())));
        if (isReport) {
            // /report logs is staff-only end to end, so the evidence link is always copyable here.
            body.add(DialogBody.plainMessage(evidenceLine(entry.getEvidence(), false)));
        }
        body.add(DialogBody.plainMessage(Component.text("Final Status: " + entry.getFinalStatus())));
        body.add(DialogBody.plainMessage(Component.text("Closed By: " + entry.getClosedByName())));
        body.add(DialogBody.plainMessage(Component.text("Filed: " + DialogUtil.formatDate(entry.getCreatedAt()), NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text("Closed: " + DialogUtil.formatDate(entry.getClosedAt()), NamedTextColor.GRAY)));

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.builder(Component.text("Reopen", NamedTextColor.GREEN))
                .tooltip(Component.text("Restore this back into the active queue."))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        reopenLogEntry(p, entry, filterName);
                    }
                }, DialogUtil.singleUse()))
                .build());
        buttons.add(ActionButton.builder(Component.text("Delete Permanently", NamedTextColor.RED))
                .tooltip(Component.text("Remove this log entry for good. This cannot be undone."))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        confirmDeleteLogEntry(p, entry, filterName);
                    }
                }, DialogUtil.singleUse()))
                .build());

        ActionButton back = ActionButton.builder(Component.text("Back"))
                .tooltip(Component.text("Just close this - keeps the entry in the logs."))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        openLogsList(p, filterName);
                    }
                }, DialogUtil.singleUse()))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Log Entry #" + entry.getId())).body(body).build())
                .type(DialogType.multiAction(buttons, back, 2)));

        viewer.showDialog(dialog);
    }

    /** Restores a log entry back into the active {@link ReportManager}/{@link AdminRequestManager}
     *  queue (as a brand new id) and removes it from the archive. */
    private void reopenLogEntry(Player staff, TicketLog entry, String filterName) {
        if (entry.getType() == TicketLog.Type.REPORT) {
            Report report = reportManager.create(entry.getSubmitterId(), entry.getSubmitterName(),
                    entry.getTargetName(), entry.getReason(), entry.getEvidence());
            staff.sendMessage(DialogUtil.success("Report #" + report.getId() + " reopened from the logs."));
        } else {
            AdminRequest request = adminRequestManager.create(entry.getSubmitterId(), entry.getSubmitterName(), entry.getReason());
            staff.sendMessage(DialogUtil.success("Admin request #" + request.getId() + " reopened from the logs."));
        }
        ticketLogManager.remove(entry.getId());
        openLogsList(staff, filterName);
    }

    /** A confirmation step before permanently deleting a log entry - this can't be undone, unlike
     *  everything else in this menu system, so it gets its own "are you sure" screen. */
    private void confirmDeleteLogEntry(Player staff, TicketLog entry, String filterName) {
        ActionButton confirm = ActionButton.builder(Component.text("Yes, Delete", NamedTextColor.RED))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        ticketLogManager.remove(entry.getId());
                        p.sendMessage(DialogUtil.success("Log entry #" + entry.getId() + " deleted permanently."));
                        openLogsList(p, filterName);
                    }
                }, DialogUtil.singleUse()))
                .build();

        ActionButton cancel = ActionButton.builder(Component.text("Cancel"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        openLogsDetail(p, entry, filterName);
                    }
                }, DialogUtil.singleUse()))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Delete Log Entry #" + entry.getId()))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("This permanently removes the entry from the logs. This cannot be undone.",
                                        NamedTextColor.RED))))
                        .build())
                .type(DialogType.confirmation(confirm, cancel)));

        staff.showDialog(dialog);
    }

    // ---------------------------------------------------------------- shared bits

    /**
     * Builds the "Evidence: <link>" line. For a staff viewer (ownerMode false) the link itself
     * is underlined and click-to-copy - handy for pasting it elsewhere without having to select
     * text inside a dialog. Report owners just see it as plain text.
     */
    private Component evidenceLine(String evidence, boolean ownerMode) {
        if (ownerMode) {
            return Component.text("Evidence: " + evidence);
        }
        return Component.text("Evidence: ")
                .append(Component.text(evidence, NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.copyToClipboard(evidence))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy", NamedTextColor.GRAY))));
    }

    private void notifyStaff(Component message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("reportadmin.staff")) {
                p.sendMessage(message);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.4f);
            }
        }
    }
}
