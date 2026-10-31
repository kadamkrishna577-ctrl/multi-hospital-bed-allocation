package com.group15.hospital.model.bed;

import com.group15.hospital.model.enums.TriageLevel;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a patient waiting for an ICU bed to become available.
 * <p>
 * Entries are naturally ordered by <strong>triage priority</strong> (highest
 * acuity first — lowest {@link TriageLevel#getPriorityScore()} wins),
 * breaking ties by <strong>request time</strong> (earliest first — FIFO).
 * This ordering is used by the waitlist priority queue.
 * </p>
 *
 * @see com.group15.hospital.service.preemption.WaitlistManager
 */
public class WaitlistEntry implements Comparable<WaitlistEntry> {

    private final long entryId;
    private final long patientId;
    private final String patientName;
    private final TriageLevel triageLevel;
    private final String requestedHospitalName;
    private final String requestedWardName;
    private final LocalDateTime requestTime;
    private final String notes;

    // ──────────────────────────── Constructors ────────────────────────────

    /**
     * Full constructor — typically used when loading from the database.
     *
     * @param entryId               unique waitlist entry identifier
     * @param patientId             the patient's unique identifier
     * @param patientName           the patient's display name
     * @param triageLevel           the patient's triage acuity level
     * @param requestedHospitalName preferred hospital
     * @param requestedWardName     preferred ward
     * @param requestTime           when the patient was added to the waitlist
     * @param notes                 optional clinical notes
     */
    public WaitlistEntry(long entryId, long patientId, String patientName,
                         TriageLevel triageLevel, String requestedHospitalName,
                         String requestedWardName, LocalDateTime requestTime,
                         String notes) {
        this.entryId = entryId;
        this.patientId = patientId;
        this.patientName = Objects.requireNonNull(patientName, "patientName must not be null");
        this.triageLevel = Objects.requireNonNull(triageLevel, "triageLevel must not be null");
        this.requestedHospitalName = Objects.requireNonNull(requestedHospitalName,
                "requestedHospitalName must not be null");
        this.requestedWardName = Objects.requireNonNull(requestedWardName,
                "requestedWardName must not be null");
        this.requestTime = requestTime != null ? requestTime : LocalDateTime.now();
        this.notes = notes != null ? notes : "";
    }

    /**
     * Convenience constructor — sets {@code entryId} to 0 (auto-generated)
     * and {@code requestTime} to now.
     */
    public WaitlistEntry(long patientId, String patientName, TriageLevel triageLevel,
                         String requestedHospitalName, String requestedWardName, String notes) {
        this(0L, patientId, patientName, triageLevel,
             requestedHospitalName, requestedWardName, LocalDateTime.now(), notes);
    }

    // ──────────────────────────── Getters ─────────────────────────────

    public long getEntryId() {
        return entryId;
    }

    public long getPatientId() {
        return patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public TriageLevel getTriageLevel() {
        return triageLevel;
    }

    public String getRequestedHospitalName() {
        return requestedHospitalName;
    }

    public String getRequestedWardName() {
        return requestedWardName;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public String getNotes() {
        return notes;
    }

    // ──────────────────── Comparable (priority ordering) ──────────────

    /**
     * Orders entries by triage priority (ascending score = higher medical acuity),
     * then by request time (ascending = FIFO among same priority).
     */
    @Override
    public int compareTo(WaitlistEntry other) {
        // Lower priority score = more critical → comes first
        int priorityCompare = Integer.compare(
                this.triageLevel.getPriorityScore(),
                other.triageLevel.getPriorityScore());
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        // Same priority → earlier request time comes first (FIFO)
        return this.requestTime.compareTo(other.requestTime);
    }

    // ──────────────────────── Object overrides ────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WaitlistEntry other)) return false;
        // Two entries are equal if they refer to the same patient at the same hospital/ward
        return patientId == other.patientId
                && Objects.equals(requestedHospitalName, other.requestedHospitalName)
                && Objects.equals(requestedWardName, other.requestedWardName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patientId, requestedHospitalName, requestedWardName);
    }

    @Override
    public String toString() {
        return String.format("WaitlistEntry{id=%d, patient='%s'(#%d), triage=%s, " +
                             "hospital='%s', ward='%s', time=%s}",
                entryId, patientName, patientId,
                triageLevel.getDescription(),
                requestedHospitalName, requestedWardName, requestTime);
    }
}
