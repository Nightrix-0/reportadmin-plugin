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
import net.nightrix.reportadmin.model.AdminRequest;
import net.nightrix.reportadmin.model.TicketLog;
import net.nightrix.reportadmin.storage.AdminRequestManager;
import net.nightrix.reportadmin.storage.TicketLogManager;
import net.nightrix.reportadmin.util.DialogUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles /request (staff) and /requests ([player] | view [player]).
 *
 * "/requests view" (no player) only ever lists OPEN requests whose author is currently online,
 * per spec. "/requests view &lt;player&gt;" jumps straight to that online player's open request.
 */
public class AdminRequestCommands implements CommandExecutor {

    private final AdminRequestManager requestManager;
    private final TicketLogManager ticketLogManager;

    public AdminRequestCommands(AdminRequestManager requestManager, TicketLogManager ticketLogManager) {
        this.requestManager = requestManager;
        this.ticketLogManager = ticketLogManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "request" -> handleRequest(player, args);
            case "requests" -> handleRequests(player, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- routing

    /** "/request staff" - the only valid form. */
    private void handleRequest(Player player, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("staff")) {
            showForm(player, "Request Admin Assistance", null, null);
        } else {
            player.sendMessage(DialogUtil.error("Usage: /request staff"));
        }
    }

    /** "/requests" (menu), "/requests &lt;anything else&gt;" (own requests), or
     *  "/requests view [player]" (staff). */
    private void handleRequests(Player player, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("view")) {
            if (!player.hasPermission("reportadmin.staff")) {
                player.sendMessage(DialogUtil.error("You don't have permission to view other players' admin requests."));
                return;
            }
            if (args.length >= 2) {
                String typed = args[1];
                AdminRequest request = requestManager.getOpenByPlayerName(typed);
                if (request == null) {
                    player.sendMessage(DialogUtil.info(typed + " has no open admin request."));
                } else {
                    openStaffDetail(player, request);
                }
            } else {
                openStaffList(player);
            }
            return;
        }

        if (args.length == 0) {
            openRequestsMenu(player);
            return;
        }

        openOwnList(player);
    }

    /** Bare "/requests" - shows what you can do next instead of jumping straight into a list. */
    private void openRequestsMenu(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.builder(Component.text("My Admin Requests", NamedTextColor.YELLOW))
                .tooltip(Component.text("/requests - view and manage the requests you've sent."))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        openOwnList(p);
                    }
                }, DialogUtil.singleUse()))
                .build());
        if (player.hasPermission("reportadmin.staff")) {
            buttons.add(ActionButton.builder(Component.text("View All Requests", NamedTextColor.AQUA))
                    .tooltip(Component.text("/requests view [player] - open requests from online players."))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openStaffList(p);
                        }
                    }, DialogUtil.singleUse()))
                    .build());
        }

        ActionButton close = ActionButton.builder(Component.text("Close")).action(null).build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Requests Commands"))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("Choose what you'd like to do.", NamedTextColor.GRAY))))
                        .build())
                .type(DialogType.multiAction(buttons, close, 1)));

        player.showDialog(dialog);
    }

    // ---------------------------------------------------------------- create / edit form

    private void showForm(Player player, String title, String prefillReason, AdminRequest editing) {
        ActionButton submit = ActionButton.builder(Component.text("Submit", NamedTextColor.GREEN))
                .tooltip(Component.text("Send this request to online staff."))
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) {
                        return;
                    }
                    String reason = view.getText("reason");
                    if (reason == null || reason.isBlank()) {
                        p.sendMessage(DialogUtil.error("You must enter a reason for your request."));
                        showForm(p, title, reason, editing);
                        return;
                    }

                    if (editing == null) {
                        AdminRequest request = requestManager.create(p.getUniqueId(), p.getName(), reason);
                        p.sendMessage(DialogUtil.success("Admin request #" + request.getId() + " sent."));
                        notifyStaff(Component.text(p.getName() + " requested admin assistance: " + reason,
                                NamedTextColor.YELLOW));
                    } else {
                        editing.setReason(reason);
                        requestManager.save();
                        p.sendMessage(DialogUtil.success("Admin request #" + editing.getId() + " updated."));
                    }
                }, DialogUtil.singleUse()))
                .build();

        ActionButton cancel = ActionButton.builder(Component.text("Cancel", NamedTextColor.RED))
                .action(null)
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text("A reason is required before this can be submitted.", NamedTextColor.GRAY))))
                        .inputs(List.of(
                                DialogInput.text("reason", Component.text("Reason of Request"))
                                        .initial(prefillReason == null ? "" : prefillReason)
                                        .maxLength(300)
                                        .width(300)
                                        .build()
                        ))
                        .build())
                .type(DialogType.confirmation(submit, cancel)));

        player.showDialog(dialog);
    }

    // ---------------------------------------------------------------- lists

    private void openOwnList(Player viewer) {
        List<AdminRequest> requests = requestManager.getByPlayer(viewer.getUniqueId());
        if (requests.isEmpty()) {
            viewer.sendMessage(DialogUtil.info("You have not sent any admin requests."));
            return;
        }

        List<ActionButton> buttons = new ArrayList<>();
        for (AdminRequest request : requests) {
            String labelText = "#" + request.getId() + " [" + request.getStatus() + "]";
            buttons.add(ActionButton.builder(Component.text(labelText))
                    .tooltip(Component.text(request.getReason()))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openOwnDetail(p, request);
                        }
                    }, DialogUtil.singleUse()))
                    .build());
        }

        ActionButton close = ActionButton.builder(Component.text("Close")).action(null).build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("My Admin Requests")).build())
                .type(DialogType.multiAction(buttons, close, 1)));

        viewer.showDialog(dialog);
    }

    private void openOwnDetail(Player viewer, AdminRequest request) {
        List<DialogBody> body = List.of(
                DialogBody.plainMessage(Component.text("Reason: " + request.getReason())),
                DialogBody.plainMessage(Component.text("Status: " + request.getStatus())),
                DialogBody.plainMessage(Component.text("Sent: " + DialogUtil.formatDate(request.getCreatedAt()), NamedTextColor.GRAY))
        );

        List<ActionButton> buttons = new ArrayList<>();
        if (request.getStatus() == AdminRequest.Status.OPEN) {
            buttons.add(ActionButton.builder(Component.text("Edit", NamedTextColor.AQUA))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            showForm(p, "Edit Admin Request #" + request.getId(), request.getReason(), request);
                        }
                    }, DialogUtil.singleUse()))
                    .build());
            buttons.add(ActionButton.builder(Component.text("Cancel Request", NamedTextColor.RED))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            closeRequest(request, "CANCELLED", p);
                            p.sendMessage(DialogUtil.success("Admin request #" + request.getId() + " cancelled and moved to the logs."));
                            openOwnList(p);
                        }
                    }, DialogUtil.singleUse()))
                    .build());
        }

        ActionButton back = ActionButton.builder(Component.text("Back"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        openOwnList(p);
                    }
                }, DialogUtil.singleUse()))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Admin Request #" + request.getId())).body(body).build())
                .type(DialogType.multiAction(buttons, back, 1)));

        viewer.showDialog(dialog);
    }

    private void openStaffList(Player staff) {
        List<AdminRequest> requests = requestManager.getOpenFromOnlinePlayers();
        if (requests.isEmpty()) {
            staff.sendMessage(DialogUtil.info("There are no open admin requests from online players."));
            return;
        }

        List<ActionButton> buttons = new ArrayList<>();
        for (AdminRequest request : requests) {
            buttons.add(ActionButton.builder(Component.text(request.getPlayerName() + " (#" + request.getId() + ")"))
                    .tooltip(Component.text(request.getReason()))
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p) {
                            openStaffDetail(p, request);
                        }
                    }, DialogUtil.singleUse()))
                    .build());
        }

        ActionButton close = ActionButton.builder(Component.text("Close")).action(null).build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Admin Requests (Online Players)")).build())
                .type(DialogType.multiAction(buttons, close, 1)));

        staff.showDialog(dialog);
    }

    private void openStaffDetail(Player staff, AdminRequest request) {
        List<DialogBody> body = List.of(
                DialogBody.plainMessage(Component.text("Player: " + request.getPlayerName())),
                DialogBody.plainMessage(Component.text("Reason: " + request.getReason())),
                DialogBody.plainMessage(Component.text("Sent: " + DialogUtil.formatDate(request.getCreatedAt()), NamedTextColor.GRAY))
        );

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.builder(Component.text("Teleport to Player", NamedTextColor.AQUA))
                .tooltip(Component.text("Teleport to " + request.getPlayerName() + "."))
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) {
                        return;
                    }
                    Player target = Bukkit.getPlayer(request.getPlayerId());
                    if (target == null) {
                        p.sendMessage(DialogUtil.error(request.getPlayerName() + " is no longer online."));
                        return;
                    }
                    Location loc = target.getLocation();
                    p.teleport(loc);
                    p.sendMessage(DialogUtil.success("Teleported to " + request.getPlayerName() + "."));
                }, DialogUtil.singleUse()))
                .build());
        buttons.add(ActionButton.builder(Component.text("Close Request", NamedTextColor.RED))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        closeRequest(request, "CLOSED", p);
                        p.sendMessage(DialogUtil.success("Admin request #" + request.getId() + " closed and moved to the logs."));
                        openStaffList(p);
                    }
                }, DialogUtil.singleUse()))
                .build());

        ActionButton back = ActionButton.builder(Component.text("Back"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        openStaffList(p);
                    }
                }, DialogUtil.singleUse()))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Admin Request #" + request.getId())).body(body).build())
                .type(DialogType.multiAction(buttons, back, 2)));

        staff.showDialog(dialog);
    }

    /** Logs the request to /ralogs and pulls it out of the active list. finalStatus is "CLOSED" or "CANCELLED". */
    private void closeRequest(AdminRequest request, String finalStatus, Player closedBy) {
        request.setStatus("CANCELLED".equals(finalStatus) ? AdminRequest.Status.CANCELLED : AdminRequest.Status.CLOSED);
        ticketLogManager.add(TicketLog.Type.ADMIN_REQUEST, request.getId(), request.getPlayerId(), request.getPlayerName(),
                null, request.getReason(), null, finalStatus, closedBy.getName(), request.getCreatedAt());
        requestManager.remove(request.getId());
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
