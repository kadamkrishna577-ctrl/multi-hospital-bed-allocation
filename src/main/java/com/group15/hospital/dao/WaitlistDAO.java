package com.group15.hospital.dao;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.model.bed.WaitlistEntry;
import com.group15.hospital.model.enums.TriageLevel;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for the {@code waitlist_entries} table.
 * <p>
 * Provides CRUD operations for {@link WaitlistEntry} entities, with
 * priority-ordered queries that sort by triage level (highest acuity first)
 * and then by request time (FIFO).
 * </p>
 *
 * <h3>Table schema</h3>
 * <pre>{@code
 * CREATE TABLE waitlist_entries (
 *     entry_id                BIGINT       PRIMARY KEY AUTO_INCREMENT,
 *     patient_id              BIGINT       NOT NULL,
 *     patient_name            VARCHAR(255) NOT NULL,
 *     triage_level            VARCHAR(50)  NOT NULL,
 *     requested_hospital_name VARCHAR(255) NOT NULL,
 *     requested_ward_name     VARCHAR(255) NOT NULL,
 *     request_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *     notes                   TEXT         NULL
 * );
 * }</pre>
 *
 * @author Group 15
 * @see WaitlistEntry
 * @see DatabaseConnection
 */
public class WaitlistDAO {

    private static final Logger LOGGER = Logger.getLogger(WaitlistDAO.class.getName());

    /** Singleton database connection provider. */
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    // ──────────────────────────── SQL Constants ────────────────────────────

    private static final String TABLE = "waitlist_entries";

    private static final String SELECT_ALL_COLUMNS =
            "SELECT entry_id, patient_id, patient_name, triage_level, " +
            "requested_hospital_name, requested_ward_name, request_time, notes FROM " + TABLE;

    private static final String SQL_FIND_BY_ID =
            SELECT_ALL_COLUMNS + " WHERE entry_id = ?";

    private static final String SQL_FIND_ALL =
            SELECT_ALL_COLUMNS + " ORDER BY triage_level ASC, request_time ASC";

    private static final String SQL_FIND_BY_HOSPITAL =
            SELECT_ALL_COLUMNS +
            " WHERE requested_hospital_name = ? ORDER BY triage_level ASC, request_time ASC";

    private static final String SQL_INSERT =
            "INSERT INTO " + TABLE +
            " (patient_id, patient_name, triage_level, requested_hospital_name, " +
            "requested_ward_name, request_time, notes) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_DELETE_BY_ID =
            "DELETE FROM " + TABLE + " WHERE entry_id = ?";

    private static final String SQL_DELETE_BY_PATIENT_ID =
            "DELETE FROM " + TABLE + " WHERE patient_id = ?";

    // ──────────────────────────── Read Operations ─────────────────────────

    /**
     * Finds a single waitlist entry by its primary key.
     *
     * @param entryId the unique entry identifier
     * @return an {@link Optional} containing the entry, or empty if not found
     */
    public Optional<WaitlistEntry> findById(long entryId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {

            ps.setLong(1, entryId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find waitlist entry by id=" + entryId, e);
        }
        return Optional.empty();
    }

    /**
     * Returns all waitlist entries ordered by priority (highest acuity first, then FIFO).
     *
     * @return list of all entries (never {@code null})
     */
    public List<WaitlistEntry> findAll() {
        List<WaitlistEntry> entries = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                entries.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load all waitlist entries", e);
        }
        return entries;
    }

    /**
     * Returns waitlist entries for a specific hospital, ordered by priority.
     *
     * @param hospitalName the hospital to filter by
     * @return list of matching entries (never {@code null})
     */
    public List<WaitlistEntry> findByHospital(String hospitalName) {
        List<WaitlistEntry> entries = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_HOSPITAL)) {

            ps.setString(1, hospitalName);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find waitlist entries for hospital=" + hospitalName, e);
        }
        return entries;
    }

    // ──────────────────────────── Write Operations ────────────────────────

    /**
     * Inserts a new waitlist entry and returns the auto-generated primary key.
     *
     * @param entry the entry to persist (its {@code entryId} is ignored)
     * @return the generated {@code entry_id}
     * @throws SQLException if the insert fails
     */
    public long insert(WaitlistEntry entry) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, entry.getPatientId());
            ps.setString(2, entry.getPatientName());
            ps.setString(3, entry.getTriageLevel().name());
            ps.setString(4, entry.getRequestedHospitalName());
            ps.setString(5, entry.getRequestedWardName());
            ps.setTimestamp(6, Timestamp.valueOf(entry.getRequestTime()));
            ps.setString(7, entry.getNotes());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Inserting waitlist entry failed, no rows affected.");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    LOGGER.info("Inserted waitlist entry id=" + id +
                                " patient='" + entry.getPatientName() +
                                "' triage=" + entry.getTriageLevel());
                    return id;
                } else {
                    throw new SQLException("Inserting waitlist entry failed, no generated key obtained.");
                }
            }
        }
    }

    /**
     * Deletes a waitlist entry by its primary key.
     *
     * @param entryId the entry to delete
     */
    public void deleteById(long entryId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE_BY_ID)) {

            ps.setLong(1, entryId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                LOGGER.info("Deleted waitlist entry id=" + entryId);
            } else {
                LOGGER.warning("No waitlist entry found with id=" + entryId + " to delete");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete waitlist entry id=" + entryId, e);
        }
    }

    /**
     * Deletes all waitlist entries for a given patient.
     *
     * @param patientId the patient whose entries should be removed
     */
    public void deleteByPatientId(long patientId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE_BY_PATIENT_ID)) {

            ps.setLong(1, patientId);

            int affected = ps.executeUpdate();
            LOGGER.info("Deleted " + affected + " waitlist entries for patient id=" + patientId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete waitlist entries for patient id=" + patientId, e);
        }
    }

    // ──────────────────────────── Row Mapping ─────────────────────────────

    /**
     * Maps the current row of a {@link ResultSet} to a {@link WaitlistEntry}.
     *
     * @param rs the result set positioned at the row to map
     * @return a fully-hydrated {@link WaitlistEntry} instance
     * @throws SQLException if a column cannot be read
     */
    private WaitlistEntry mapRow(ResultSet rs) throws SQLException {
        long entryId              = rs.getLong("entry_id");
        long patientId            = rs.getLong("patient_id");
        String patientName        = rs.getString("patient_name");
        TriageLevel triageLevel   = TriageLevel.parseDatabase(rs.getString("triage_level"));
        String hospitalName       = rs.getString("requested_hospital_name");
        String wardName           = rs.getString("requested_ward_name");
        Timestamp ts              = rs.getTimestamp("request_time");
        LocalDateTime requestTime = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();
        String notes              = rs.getString("notes");

        return new WaitlistEntry(entryId, patientId, patientName, triageLevel,
                                 hospitalName, wardName, requestTime, notes);
    }
}
