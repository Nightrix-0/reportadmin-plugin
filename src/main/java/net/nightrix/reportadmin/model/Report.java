package net.nightrix.reportadmin.model;

import java.util.UUID;

/**
 * A single player report: who filed it, who it is against, why, and the evidence link.
 */
public class Report {

    public enum Status {
        OPEN,
        CLOSED
    }

    private final int id;
    private final UUID reporterId;
    private final String reporterName;
    private String targetName;
    private String reason;
    private String evidence;
    private Status status;
    private final long createdAt;

    public Report(int id, UUID reporterId, String reporterName, String targetName,
                  String reason, String evidence, Status status, long createdAt) {
        this.id = id;
        this.reporterId = reporterId;
        this.reporterName = reporterName;
        this.targetName = targetName;
        this.reason = reason;
        this.evidence = evidence;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public String getReporterName() {
        return reporterName;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
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
