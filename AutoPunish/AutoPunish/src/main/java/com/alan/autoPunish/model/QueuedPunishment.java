package com.alan.autoPunish.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a punishment awaiting staff approval
 *
 * @author AlanTheDev
 */
public class QueuedPunishment {

    private final long id;
    private final UUID playerUuid;
    private final String playerName;
    private final Punishment suggestedPunishment;
    private final String evidence;
    private final Instant queuedAt;
    private final String queuedBy;
    private QueueStatus status;
    private String reviewedBy;
    private Instant reviewedAt;
    private String denialReason;

    /**
     * Queue status enum
     */
    public enum QueueStatus {
        PENDING, APPROVED, DENIED, EXPIRED
    }

    /**
     * Constructor for new queued punishments
     */
    public QueuedPunishment(long id, UUID playerUuid, String playerName,
                            Punishment suggestedPunishment, String evidence,
                            Instant queuedAt, String queuedBy) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.suggestedPunishment = suggestedPunishment;
        this.evidence = evidence;
        this.queuedAt = queuedAt;
        this.queuedBy = queuedBy;
        this.status = QueueStatus.PENDING;
    }

    /**
     * Approve this queued punishment
     */
    public boolean approve(String reviewer) {
        if (status != QueueStatus.PENDING) {
            return false;
        }
        this.status = QueueStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        return true;
    }

    /**
     * Deny this queued punishment
     */
    public boolean deny(String reviewer, String reason) {
        if (status != QueueStatus.PENDING) {
            return false;
        }
        this.status = QueueStatus.DENIED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.denialReason = reason;
        return true;
    }

    /**
     * Check if this punishment has expired (auto-approval timeout)
     */
    public boolean isExpired(long autoApproveTimeMs) {
        if (status != QueueStatus.PENDING) {
            return false;
        }
        return Instant.now().isAfter(queuedAt.plusMillis(autoApproveTimeMs));
    }

    /**
     * Get time remaining until auto-approval (-1 if not pending or already expired)
     */
    public long getTimeUntilAutoApproval(long autoApproveTimeMs) {
        if (status != QueueStatus.PENDING) {
            return -1;
        }
        Instant expiry = queuedAt.plusMillis(autoApproveTimeMs);
        if (Instant.now().isAfter(expiry)) {
            return -1;
        }
        return expiry.toEpochMilli() - Instant.now().toEpochMilli();
    }

    // Getters
    public long getId() { return id; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public Punishment getSuggestedPunishment() { return suggestedPunishment; }
    public String getEvidence() { return evidence; }
    public Instant getQueuedAt() { return queuedAt; }
    public String getQueuedBy() { return queuedBy; }
    public QueueStatus getStatus() { return status; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getDenialReason() { return denialReason; }
}