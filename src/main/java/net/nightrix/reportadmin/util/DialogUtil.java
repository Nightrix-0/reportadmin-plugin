package net.nightrix.reportadmin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Small shared helpers used by every dialog-building command.
 */
public final class DialogUtil {

    private DialogUtil() {
    }

    /** A single-use click callback that never expires unexpectedly mid-menu-navigation. */
    public static ClickCallback.Options singleUse() {
        return ClickCallback.Options.builder()
                .uses(1)
                .lifetime(ClickCallback.DEFAULT_LIFETIME)
                .build();
    }

    /** A click callback usable more than once, for buttons that just navigate (e.g. "Back"). */
    public static ClickCallback.Options reusable() {
        return ClickCallback.Options.builder()
                .uses(ClickCallback.UNLIMITED_USES)
                .lifetime(ClickCallback.DEFAULT_LIFETIME)
                .build();
    }

    public static Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    public static Component info(String message) {
        return Component.text(message, NamedTextColor.GRAY);
    }

    public static Component success(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    public static String formatDate(long epochMillis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(epochMillis));
    }
}
