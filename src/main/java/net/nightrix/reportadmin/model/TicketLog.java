package net.nightrix.reportadmin.model;

import java.util.UUID;

/**
 * A closed-out record of a report or admin request. When a ticket is closed (or, for admin
 * requests, cancelled by its own author) it is removed from the active {@link Report}/
 * {@link AdminRequest} list and a TicketLog entry is written instead - this is what
 * "/ralogs" reads. Kept flat/immutable since a log entry is never edited after the fact -
 * except that staff can permanently delete one, or reopen it (which recreates the active
 * Report/AdminRequest and removes this entry).
 */
public class TicketLog {

    public enum Type {
        REPORT,
        ADMIN_REQUEST
    }

    private final int id;
    private final Type type;
    private final int originalId;
    private final UUID submitterId;
    private final String submitterName;
    private final String targetName; // reported player; null for admin requests
    private final String reason;
    private final String evidence; // null for admin requests
    private final String finalStatus; // "CLOSED" or "CANCELLED"
    private final String closedByName;
    private final long createdAt;
    private final long closedAt;

    public TicketLog(int id, Type type, int originalId, UUID submitterId, String submitterName, String targetName,
                      String reason, String evidence, String finalStatus, String closedByName,
                      long createdAt, long closedAt) {
        this.id = id;
        this.type = type;
        this.originalId = originalId;
        this.submitterId = submitterId;
        this.submitterName = submitterName;
        this.targetName = targetName;
        this.reason = reason;
        this.evidence = evidence;
        this.finalStatus = finalStatus;
        this.closedByName = closedByName;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
    }

    public int getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public int getOriginalId() {
        return originalId;
    }

    /** Who filed the original report/request - needed to recreate it if this entry is reopened. */
    public UUID getSubmitterId() {
        return submitterId;
    }

    public String getSubmitterName() {
        return submitterName;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getReason() {
        return reason;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getFinalStatus() {
        return finalStatus;
    }

    public String getClosedByName() {
        return closedByName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getClosedAt() {
        return closedAt;
    }
}
