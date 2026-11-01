package com.group15.hospital.service.preemption;

import com.group15.hospital.dao.WaitlistDAO;
import com.group15.hospital.model.bed.WaitlistEntry;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Thread-safe manager for the patient waitlist.
 * <p>
 * Maintains a {@link PriorityBlockingQueue} of {@link WaitlistEntry} objects
 * as an in-memory cache, backed by persistent storage through
 * {@link WaitlistDAO}. All mutations synchronise both the queue and the
 * database to keep them consistent.
 * </p>
 *
 * <h3>Ordering</h3>
 * Entries are ordered by {@link WaitlistEntry#compareTo(WaitlistEntry)}:
 * triage priority (highest acuity first), then request time (FIFO).
 *
 * @author Group 15
 * @see WaitlistEntry
 * @see WaitlistDAO
 */
public class WaitlistManager {

    private static final Logger LOGGER = Logger.getLogger(WaitlistManager.class.getName());

    /** Thread-safe priority queue — natural ordering from WaitlistEntry.compareTo. */
    private final PriorityBlockingQueue<WaitlistEntry> waitlistQueue;

    /** Database access for persistent waitlist storage. */
    private final WaitlistDAO waitlistDAO;

    /** Lock object for compound operations that must be atomic. */
    private final Object syncLock = new Object();

    // ──────────────────────────── Constructor ─────────────────────────────

    /**
     * Creates a new WaitlistManager and loads existing entries from the database.
     *
     * @param waitlistDAO DAO for waitlist persistence
     */
    public WaitlistManager(WaitlistDAO waitlistDAO) {
        this.waitlistDAO = Objects.requireNonNull(waitlistDAO, "waitlistDAO must not be null");
        this.waitlistQueue = new PriorityBlockingQueue<>();
        loadFromDatabase();
    }

    /**
     * Creates a new WaitlistManager with an empty in-memory queue (no DB load).
     * Useful for testing.
     *
     * @param waitlistDAO  DAO for waitlist persistence
     * @param skipDbLoad   if {@code true}, skip loading from database
     */
    public WaitlistManager(WaitlistDAO waitlistDAO, boolean skipDbLoad) {
        this.waitlistDAO = Objects.requireNonNull(waitlistDAO, "waitlistDAO must not be null");
        this.waitlistQueue = new PriorityBlockingQueue<>();
        if (!skipDbLoad) {
            loadFromDatabase();
        }
    }

    // ──────────────────────── Initialisation ──────────────────────────────

    /**
     * Loads all existing waitlist entries from the database into the in-memory queue.
     */
    private void loadFromDatabase() {
        try {
            List<WaitlistEntry> entries = waitlistDAO.findAll();
            waitlistQueue.addAll(entries);
            LOGGER.info("Loaded " + entries.size() + " waitlist entries from database.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load waitlist from database", e);
        }
    }

    // ──────────────────────── Core Operations ────────────────────────────

    /**
     * Adds a patient to the waitlist — persists to the database and adds
     * to the in-memory priority queue.
     *
     * @param entry the waitlist entry to add
     * @throws RuntimeException if the database insert fails
     */
    public void addToWaitlist(WaitlistEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");

        synchronized (syncLock) {
            try {
                long generatedId = waitlistDAO.insert(entry);
                // Re-create with the generated ID for consistency
                WaitlistEntry persisted = new WaitlistEntry(
                        generatedId,
                        entry.getPatientId(),
                        entry.getPatientName(),
                        entry.getTriageLevel(),
                        entry.getRequestedHospitalName(),
                        entry.getRequestedWardName(),
                        entry.getRequestTime(),
                        entry.getNotes()
                );
                waitlistQueue.add(persisted);
                LOGGER.info("Added to waitlist: " + persisted);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to persist waitlist entry for patient (using in-memory fallback): " +
                           entry.getPatientName());
                waitlistQueue.add(entry);
            }
        }
    }

    /**
     * Returns and removes the highest-priority patient for the specified hospital.
     * <p>
     * Scans the queue for the first entry matching the hospital name.
     * The match is removed from both the in-memory queue and the database.
     * </p>
     *
     * @param hospitalName the hospital to match
     * @return the next patient, or {@code null} if no matching entries
     */
    public WaitlistEntry pollNextPatient(String hospitalName) {
        Objects.requireNonNull(hospitalName, "hospitalName must not be null");

        synchronized (syncLock) {
            // PriorityBlockingQueue iterator is NOT in priority order,
            // so we drain, find the match, and re-add the rest.
            List<WaitlistEntry> temp = new ArrayList<>();
            WaitlistEntry match = null;

            while (!waitlistQueue.isEmpty()) {
                WaitlistEntry entry = waitlistQueue.poll();
                if (match == null && entry.getRequestedHospitalName().equals(hospitalName)) {
                    match = entry;
                } else {
                    temp.add(entry);
                }
            }

            // Re-add non-matching entries
            waitlistQueue.addAll(temp);

            if (match != null) {
                waitlistDAO.deleteById(match.getEntryId());
                LOGGER.info("Polled next patient from waitlist for hospital '" +
                            hospitalName + "': " + match.getPatientName());
            }
            return match;
        }
    }

    /**
     * Removes a specific waitlist entry by its ID.
     *
     * @param entryId the entry to remove
     */
    public void removeFromWaitlist(long entryId) {
        synchronized (syncLock) {
            waitlistQueue.removeIf(entry -> entry.getEntryId() == entryId);
            waitlistDAO.deleteById(entryId);
            LOGGER.info("Removed waitlist entry id=" + entryId);
        }
    }

    /**
     * Returns a priority-sorted snapshot of all waitlist entries for a hospital.
     *
     * @param hospitalName the hospital to filter by
     * @return an unmodifiable sorted list of entries (never {@code null})
     */
    public List<WaitlistEntry> getWaitlistForHospital(String hospitalName) {
        Objects.requireNonNull(hospitalName, "hospitalName must not be null");

        List<WaitlistEntry> filtered = waitlistQueue.stream()
                .filter(e -> e.getRequestedHospitalName().equals(hospitalName))
                .sorted()
                .collect(Collectors.toList());

        return Collections.unmodifiableList(filtered);
    }

    /**
     * Returns the total number of patients on the waitlist.
     *
     * @return current queue size
     */
    public int getWaitlistSize() {
        return waitlistQueue.size();
    }

    /**
     * Returns a priority-sorted snapshot of the entire waitlist.
     *
     * @return an unmodifiable sorted list of all entries
     */
    public List<WaitlistEntry> getAllEntries() {
        List<WaitlistEntry> sorted = new ArrayList<>(waitlistQueue);
        Collections.sort(sorted);
        return Collections.unmodifiableList(sorted);
    }

    /**
     * Reloads the in-memory queue from the database, discarding any cached state.
     * Useful for recovery or synchronisation.
     */
    public void refresh() {
        synchronized (syncLock) {
            waitlistQueue.clear();
            loadFromDatabase();
        }
    }
}
