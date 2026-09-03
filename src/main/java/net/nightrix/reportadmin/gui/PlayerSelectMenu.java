package net.nightrix.reportadmin.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.nightrix.reportadmin.util.DialogUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A browsable "pick a player" menu: a grid of player heads with a search box and a toggle
 * between currently-online players and everyone who has ever played on the server.
 *
 * This replaces the old dropdown that lived inside the report form. Minecraft's dialog boxes
 * can't embed a scrollable/searchable sub-menu inside themselves, so picking a player is now
 * its own screen: it opens first, and whatever you click hands control back to the caller
 * (see {@link SelectCallback}) which then shows the rest of the form.
 */
public class PlayerSelectMenu implements Listener {

    public enum Mode {
        ONLINE("Online Players"),
        EVERYONE("All Players");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    @FunctionalInterface
    public interface SelectCallback {
        void onSelected(Player viewer, String selectedName);
    }

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int HEADS_PER_PAGE = 36; // slots 9-44
    private static final int SLOT_CANCEL = 0;
    private static final int SLOT_TOGGLE = 3;
    private static final int SLOT_SEARCH = 4;
    private static final int SLOT_CLEAR_SEARCH = 5;
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_NEXT_PAGE = 53;

    private final Plugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public PlayerSelectMenu(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Opens the menu fresh: online players, no search filter, first page. */
    public void open(Player viewer, SelectCallback onSelect, Runnable onCancel) {
        Session session = new Session(Mode.ONLINE, null, 0, onSelect, onCancel);
        sessions.put(viewer.getUniqueId(), session);
        render(viewer, session);
    }

    private static final class Session {
        Mode mode;
        String search;
        int page;
        final SelectCallback onSelect;
        final Runnable onCancel;
        // True for the brief window where WE are the ones replacing the current screen
        // (re-rendering the inventory for a new page/mode/search, or popping open the search
        // sub-dialog) - both implicitly close whatever's currently open on the client, and the
        // close handler below needs to tell that apart from the player backing out for real.
        boolean selfClose;
        // Which player name sits in which slot on the currently-rendered page, so a click can
        // be resolved without having to parse it back out of the item's display text.
        final Map<Integer, String> slotNames = new HashMap<>();

        Session(Mode mode, String search, int page, SelectCallback onSelect, Runnable onCancel) {
            this.mode = mode;
            this.search = search;
            this.page = page;
            this.onSelect = onSelect;
            this.onCancel = onCancel;
        }
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ---------------------------------------------------------------- rendering

    private List<OfflinePlayer> candidates(Player viewer, Session session) {
        List<OfflinePlayer> all = new ArrayList<>();
        if (session.mode == Mode.ONLINE) {
            all.addAll(Bukkit.getOnlinePlayers());
        } else {
            all.addAll(List.of(Bukkit.getOfflinePlayers()));
        }
        List<OfflinePlayer> filtered = new ArrayList<>();
        String needle = session.search == null ? null : session.search.toLowerCase();
        for (OfflinePlayer p : all) {
            String name = p.getName();
            if (name == null || name.equalsIgnoreCase(viewer.getName())) {
                continue;
            }
            if (needle != null && !name.toLowerCase().contains(needle)) {
                continue;
            }
            filtered.add(p);
        }
        filtered.sort(Comparator.comparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER));
        return filtered;
    }

    private void render(Player viewer, Session session) {
        List<OfflinePlayer> candidates = candidates(viewer, session);
        int totalPages = Math.max(1, (int) Math.ceil(candidates.size() / (double) HEADS_PER_PAGE));
        session.page = Math.max(0, Math.min(session.page, totalPages - 1));

        Holder holder = new Holder();
        String searchSuffix = (session.search != null && !session.search.isBlank()) ? " - \"" + session.search + "\"" : "";
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                Component.text("Select a Player (" + session.mode.label + ")" + searchSuffix));
        holder.inventory = inv;

        inv.setItem(SLOT_CANCEL, namedItem(Material.BARRIER, Component.text("Cancel", NamedTextColor.RED),
                List.of(Component.text("Close this menu without picking anyone.", NamedTextColor.GRAY))));

        inv.setItem(SLOT_TOGGLE, namedItem(Material.COMPASS,
                Component.text("Mode: " + session.mode.label, NamedTextColor.AQUA),
                List.of(Component.text("Click to switch between online players", NamedTextColor.GRAY),
                        Component.text("and everyone who has ever joined.", NamedTextColor.GRAY))));

        List<Component> searchLore = new ArrayList<>();
        searchLore.add(Component.text(session.search == null || session.search.isBlank()
                ? "Click to search by name." : "Current search: \"" + session.search + "\"", NamedTextColor.GRAY));
        inv.setItem(SLOT_SEARCH, namedItem(Material.OAK_SIGN, Component.text("Search", NamedTextColor.YELLOW), searchLore));

        if (session.search != null && !session.search.isBlank()) {
            inv.setItem(SLOT_CLEAR_SEARCH, namedItem(Material.REDSTONE, Component.text("Clear Search", NamedTextColor.RED),
                    List.of(Component.text("Show everyone again.", NamedTextColor.GRAY))));
        }

        session.slotNames.clear();
        int start = session.page * HEADS_PER_PAGE;
        int end = Math.min(candidates.size(), start + HEADS_PER_PAGE);
        int slot = 9;
        for (int i = start; i < end; i++) {
            OfflinePlayer target = candidates.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(target.getName(), NamedTextColor.YELLOW));
            meta.lore(List.of(Component.text(target.isOnline() ? "Online - click to select" : "Offline - click to select",
                    NamedTextColor.GRAY)));
            head.setItemMeta(meta);
            inv.setItem(slot, head);
            session.slotNames.put(slot, target.getName());
            slot++;
        }

        if (candidates.isEmpty()) {
            inv.setItem(27, namedItem(Material.PAPER, Component.text("No players found", NamedTextColor.GRAY),
                    List.of(Component.text("Try clearing the search or switching mode.", NamedTextColor.GRAY))));
        }

        if (session.page > 0) {
            inv.setItem(SLOT_PREV_PAGE, namedItem(Material.ARROW, Component.text("Previous Page", NamedTextColor.GREEN), List.of()));
        }
        if (session.page < totalPages - 1) {
            inv.setItem(SLOT_NEXT_PAGE, namedItem(Material.ARROW, Component.text("Next Page", NamedTextColor.GREEN), List.of()));
        }
        inv.setItem(SLOT_PAGE_INFO, namedItem(Material.PAPER,
                Component.text("Page " + (session.page + 1) + " / " + totalPages, NamedTextColor.GRAY), List.of()));

        session.selfClose = true;
        viewer.openInventory(inv);
        // If that just closed a previously-open inventory of ours, the close handler already
        // consumed the flag above; if nothing was open, clear it now so a real close later
        // (Esc / E) isn't mistaken for one we caused ourselves.
        session.selfClose = false;
    }

    private ItemStack namedItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    // ---------------------------------------------------------------- search sub-dialog

    private void openSearchDialog(Player viewer, Session session) {
        ActionButton submit = ActionButton.builder(Component.text("Search", NamedTextColor.GREEN))
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) {
                        return;
                    }
                    String query = view.getText("query");
                    session.search = (query == null || query.isBlank()) ? null : query.trim();
                    session.page = 0;
                    render(p, session);
                }, DialogUtil.singleUse()))
                .build();

        ActionButton cancel = ActionButton.builder(Component.text("Back", NamedTextColor.RED))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        render(p, session);
                    }
                }, DialogUtil.singleUse()))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Search Players"))
                        .inputs(List.of(DialogInput.text("query", Component.text("Name contains"))
                                .initial(session.search == null ? "" : session.search)
                                .maxLength(32)
                                .width(250)
                                .build()))
                        .build())
                .type(DialogType.confirmation(submit, cancel)));

        session.selfClose = true;
        viewer.showDialog(dialog);
        session.selfClose = false;
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        Session session = sessions.get(viewer.getUniqueId());
        if (session == null) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) {
            return;
        }

        if (slot == SLOT_CANCEL) {
            sessions.remove(viewer.getUniqueId());
            viewer.closeInventory();
            Runnable onCancel = session.onCancel;
            if (onCancel != null) {
                onCancel.run();
            }
            return;
        }

        if (slot == SLOT_TOGGLE) {
            session.mode = session.mode == Mode.ONLINE ? Mode.EVERYONE : Mode.ONLINE;
            session.page = 0;
            render(viewer, session);
            return;
        }

        if (slot == SLOT_SEARCH) {
            openSearchDialog(viewer, session);
            return;
        }

        if (slot == SLOT_CLEAR_SEARCH) {
            session.search = null;
            session.page = 0;
            render(viewer, session);
            return;
        }

        if (slot == SLOT_PREV_PAGE) {
            session.page = Math.max(0, session.page - 1);
            render(viewer, session);
            return;
        }

        if (slot == SLOT_NEXT_PAGE) {
            session.page++;
            render(viewer, session);
            return;
        }

        if (slot >= 9 && slot < 45) {
            String name = session.slotNames.get(slot);
            if (name == null) {
                return;
            }
            sessions.remove(viewer.getUniqueId());
            viewer.closeInventory();
            session.onSelect.onSelected(viewer, name);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player viewer)) {
            return;
        }
        Session session = sessions.get(viewer.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.selfClose) {
            // We closed this screen ourselves (re-render or the search dialog) - not a real cancel.
            session.selfClose = false;
            return;
        }
        // Otherwise the player closed the menu themselves (Esc / E) rather than clicking one
        // of our own buttons - treat that as a cancel.
        sessions.remove(viewer.getUniqueId());
        if (session.onCancel != null) {
            session.onCancel.run();
        }
    }
}
