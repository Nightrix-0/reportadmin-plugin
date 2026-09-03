package net.nightrix.reportadmin.model;

import java.util.UUID;

/**
 * A single "/requestadmin" call from a player asking for staff attention.
 */
public class AdminRequest {

    public enum Status {
        OPEN,
        CLOSED,
        CANCELLED
    }

    private final int id;
    private final UUID playerId;
    private final String playerName;
    private String reason;
    private Status status;
    private final long createdAt;

    public AdminRequest(int id, UUID playerId, String playerName, String reason,
                         Status status, long createdAt) {
        this.id = id;
        this.playerId = playerId;
        this.playerName = playerName;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
