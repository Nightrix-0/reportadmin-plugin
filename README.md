# ReportAdmin

A serverside-only Paper plugin that adds an in-game player-report and admin-request system, built entirely on Minecraft's own [Dialog UI](https://docs.papermc.io/paper/dev/dialogs/) — no client mod or resource pack required. Players pick who they're reporting from a searchable grid of player heads, fill in a form, and staff work the queue (and the archive) from in-game menus.

## Requirements

- Paper **1.21.11** or newer (this plugin uses Paper's Dialog API, which only exists on recent Paper builds — it will not work on Spigot or older Paper versions)
- Java 21

## Features

- **Player reports** (`/report`) — with no player given, shows a menu of every report-related command you can run (report a player, view your own reports, and — for staff — the logs and every report on file); give a player name (`/report Steve`) to skip straight to the form. Submit a reason and an evidence link — evidence is required, the form won't submit without it.
- **Admin requests** (`/request`) — with no arguments, shows a menu of every request-related command (send a request, view your own requests, and — for staff — every open request); `/request staff` skips straight to the form.
- Players can review, edit, or close/cancel their own reports and requests: `/reports` (optionally `/reports <player>` to filter to reports you've filed against one player) and `/requests` (bare, this also shows a quick menu of what you can do next).
- Staff can browse the full report queue with `/reports view` (optionally `/reports view <player>` for every report against one player), see open admin requests from players who are currently online and teleport straight to them with `/requests view` (optionally `/requests view <player>` to jump straight to one), and close either out. When staff view a report, the evidence link is clickable and copies straight to their clipboard.
- **Ticket logging** — once a report or request is closed (or an admin request is cancelled by its own author), it's pulled out of the active queue and archived rather than deleted. `/report logs` browses that archive (most recent 50), showing who filed it, who closed it, and when — add a player name (`/report logs Steve`) to filter to just that player's history. From a log entry, staff can just close it and leave it archived, **reopen** it back into the active queue, or **permanently delete** it (with a confirmation step first, since that one can't be undone) — the evidence link stays copyable to clipboard throughout.
- **Staff notifications** — filing a report or an admin request pings every online staff member in chat and plays a notification sound.

## Commands & permissions

| Command | Description | Permission | Default |
|---|---|---|---|
| `/report [player]` | Report a player — opens the picker if no player is given; bare `/report` shows a menu of everything above/below instead | `reportadmin.report` | all players |
| `/report logs [player]` | Browse the closed-ticket archive, optionally filtered to a player (staff) | `reportadmin.logs` | op |
| `/reports [player]` | View/manage your own reports, optionally filtered to a target | `reportadmin.report` | all players |
| `/reports view [player]` | View/manage every report, optionally filtered to a target (staff) | `reportadmin.staff` | op |
| `/request staff` | Request staff assistance — bare `/request` shows a menu of everything above/below instead | `reportadmin.request` | all players |
| `/requests` | View/manage your own admin requests | `reportadmin.request` | all players |
| `/requests view [player]` | View open requests from online players, or jump straight to one (staff) | `reportadmin.staff` | op |

The `reportadmin.staff` and `reportadmin.logs` nodes gate the staff-only subcommands (`view`, `logs`) rather than standalone commands — the base `/reports` and `/requests` commands are open to everyone, and staff-only branches are checked in code. All permissions are standard Bukkit permission nodes, so any permissions plugin (LuckPerms, PermissionsEx, etc.) can grant or revoke them per player or group — for example, handing out `reportadmin.logs` on its own to a moderator who shouldn't otherwise have `reportadmin.staff`.

## Installation

1. Download the latest `ReportAdmin-<version>.jar` from this repo's [Actions](../../actions) tab (open the newest successful **Build Plugin** run and grab the artifact) or build it yourself (below).
2. Drop the jar into your server's `plugins/` folder.
3. Restart or `/reload` the server.

Data is stored in flat YAML files under `plugins/ReportAdmin/` (`reports.yml`, `adminrequests.yml`, `logs.yml`) — no database required.

## Building from source

```
mvn clean package
```

The built jar is written to `target/ReportAdmin-<version>.jar`. This repo also builds automatically on every push via GitHub Actions (see the Actions tab) since Paper's API isn't on Maven Central and needs the PaperMC repository, which most local/offline setups won't have cached.
