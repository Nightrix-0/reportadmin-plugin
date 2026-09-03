package net.nightrix.reportadmin.commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.nightrix.reportadmin.gui.PlayerSelectMenu;
import net.nightrix.reportadmin.model.Report;
import net.nightrix.reportadmin.storage.ReportManager;
import net.nightrix.reportadmin.util.DialogUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles /flag, /myreports and /reportlist.
 *
 * Picking who to report is its own screen now (see {@link PlayerSelectMenu}) - a browsable,
 * searchable grid of player heads with an Online/Everyone toggle - rather than a dropdown
 * living inside the report form. /flag opens that menu first; once a player is picked, the
 * Reason + Evidence form opens for them. Evidence is still enforced as required before the
 * form will actually create or update a report.
 */
public class ReportCommands implements CommandExecutor {

    private final ReportManager reportManager;
    private final PlayerSelectMenu playerSelectMenu;

    public ReportCommands(ReportManager reportManager, PlayerSelectMenu playerSelectMenu) {
        this.reportManager = reportManager;
        this.playerSelectMenu = playerSelectMenu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "flag" -> openCreateFlow(player);
            case "myreports" -> openList(player, true);
            case "reportlist" -> openList(player, false);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- create / edit flow

    private void openCreateFlow(Player player) {
        playerSelectMenu.open(player,
                (viewer, target) -> showForm(viewer, "Flag " + target, null, target, null, null),
                () -> player.sendMessage(DialogUtil.info("Cancelled - no player selected.")));
    }

    private void openEditForm(Player player, Report report) {
        showForm(player, "Edit Report #" + report.getId(), report.getReason(),
                report.getTargetName(), report.getEvidence(), report);
    }

    private void openChangeTargetFlow(Player player, Report report) {
        playerSelectMenu.open(player,
                (viewer, target) -> {
                    report.setTargetName(target);
                    reportManager.save();
                    viewer.sendMessage(DialogUtil.success("Report #" + report.getId() + " now targets " + target + "."));
                    openDetail(viewer, report, true);
                },
                () -> openDetail(player, report, true));
    }

    /**
     * Builds and shows the Reason/Evidence form for the given (already-chosen) target player.
     * When {@code editing} is null this creates a brand new report; otherwise it updates the
     * given report in place. Evidence is required - leaving it blank just reopens this same
     * form with an error instead of letting the submission through.
     */
    private void showForm(Player player, String title, String prefillReason, String target,
                           String prefillEvidence, Report editing) {
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
                        showForm(p, title, reason, target, evidence, editing);
                        return;
                    }
                    if (reason == null || reason.isBlank()) {
                        p.sendMessage(DialogUtil.error("You must provide a reason."));
                        showForm(p, title, null, target, evidence, editing);
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
                    }
                }), DialogUtil.singleUse())
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

    private void openList(Player viewer, boolean ownReportsOnly) {
        List<Report> reports = ownReportsOnly ? reportManager.getByReporter(viewer.getUniqueId()) : reportManager.getAll();
        if (reports.isEmpty()) {
            viewer.sendMessage(DialogUtil.info(ownReportsOnly ? "You have not filed any reports." : "There are no reports on file."));
            return;
        }

        List<ActionButton> buttons = new ArrayList<>();
        for (Report report : reports) {
            String labelText = "#" + report.getId() + " - " + report.getTargetName() + " [" + report.getStatus() + "]";
            buttons.add(ActionButton.builder(Component.text(labelText))
                    .tooltip(Component.text(report.getReason()))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openDetail(p, report, ownReportsOnly);
                        }
                    }, DialogUtil.singleUse()))
                    .build());
        }

        ActionButton close = ActionButton.builder(Component.text("Close")).action(null).build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(ownReportsOnly ? "My Reports" : "All Reports")).build())
                .type(DialogType.multiAction(buttons, close, 1)));

        viewer.showDialog(dialog);
    }

    private void openDetail(Player viewer, Report report, boolean ownerMode) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("Reported Player: " + report.getTargetName())));
        if (!ownerMode) {
            body.add(DialogBody.plainMessage(Component.text("Reported By: " + report.getReporterName())));
        }
        body.add(DialogBody.plainMessage(Component.text("Reason: " + report.getReason())));
        body.add(DialogBody.plainMessage(Component.text("Evidence: " + report.getEvidence())));
        body.add(DialogBody.plainMessage(Component.text("Status: " + report.getStatus())));
        body.add(DialogBody.plainMessage(Component.text("Filed: " + DialogUtil.formatDate(report.getCreatedAt()), NamedTextColor.GRAY)));

        List<ActionButton> buttons = new ArrayList<>();
        boolean open = report.getStatus() == Report.Status.OPEN;

        if (ownerMode) {
            if (open) {
                buttons.add(ActionButton.builder(Component.text("Edit", NamedTextColor.AQUA))
                        .action(DialogAction.customClick((view, audience) -> {
                            if (audience instanceof Player p) {
                                openEditForm(p, report);
                            }
                        }, DialogUtil.singleUse()))
                        .build());
                buttons.add(ActionButton.builder(Component.text("Change Player", NamedTextColor.AQUA))
                        .tooltip(Component.text("Pick a different reported player from the head menu."))
                        .action(DialogAction.customClick((view, audience) -> {
                            if (audience instanceof Player p) {
                                openChangeTargetFlow(p, report);
                            }
                        }, DialogUtil.singleUse()))
                        .build());
                buttons.add(ActionButton.builder(Component.text("Close Report", NamedTextColor.RED))
                        .action(DialogAction.customClick((view, audience) -> {
                            if (audience instanceof Player p) {
                                report.setStatus(Report.Status.CLOSED);
                                reportManager.save();
                                p.sendMessage(DialogUtil.success("Report #" + report.getId() + " closed."));
                                openList(p, true);
                            }
                        }, DialogUtil.singleUse()))
                        .build());
            }
        } else if (open) {
            buttons.add(ActionButton.builder(Component.text("Close Report", NamedTextColor.RED))
                    .tooltip(Component.text("Mark this report as resolved."))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            report.setStatus(Report.Status.CLOSED);
                            reportManager.save();
                            p.sendMessage(DialogUtil.success("Report #" + report.getId() + " closed."));
                            openList(p, false);
                        }
                    }, DialogUtil.singleUse()))
                    .build());
        }

        ActionButton back = ActionButton.builder(Component.text("Back"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        openList(p, ownerMode);
                    }
                }, DialogUtil.singleUse()))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Report #" + report.getId())).body(body).build())
                .type(DialogType.multiAction(buttons, back, 1)));

        viewer.showDialog(dialog);
    }

    private void notifyStaff(Component message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("reportadmin.staff")) {
                p.sendMessage(message);
            }
        }
    }
}
