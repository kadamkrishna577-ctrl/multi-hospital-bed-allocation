package com.group15.hospital.dao;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.exception.BedAlreadyBookedException;
import com.group15.hospital.model.bed.BaseBed;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.TriageLevel;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for the {@code beds} table.
 * <p>
 * Provides CRUD operations and specialised queries for {@link BaseBed}
 * entities, including an <strong>optimistic-locking</strong>
 * {@link #bookBed(long, long, String, TriageLevel, BedStatus) bookBed} method
 * that protects against concurrent double-booking by conditioning the UPDATE
 * on the bed's expected current status.
 * </p>
 *
 * <h3>Table schema</h3>
 * <pre>{@code
 * CREATE TABLE beds (
 *     bed_id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
 *     ward_name         VARCHAR(255) NOT NULL,
 *     hospital_name     VARCHAR(255) NOT NULL,
 *     status            VARCHAR(50)  NOT NULL DEFAULT 'VACANT',
 *     patient_id        BIGINT       NULL,
 *     patient_name      VARCHAR(255) NULL,
 *     patient_triage    VARCHAR(50)  NULL
 * );
 * }</pre>
 *
 * @author Group 15
 * @see BaseBed
 * @see DatabaseConnection
 */
public class BedDAO {

    private static final Logger LOGGER = Logger.getLogger(BedDAO.class.getName());

    /** Singleton database connection provider. */
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    /** Thread-safe shared in-memory fallback store when MySQL is offline. */
    private static final ConcurrentHashMap<Long, BaseBed> IN_MEMORY_BED_STORE = new ConcurrentHashMap<>();

    static {
        seedInitialInMemoryBeds();
    }

    private static void seedInitialInMemoryBeds() {
        // Beds 101-106 (City Central Hospital)
        IN_MEMORY_BED_STORE.put(101L, new BaseBed(101L, "Emergency ICU Ward A", "City Central Hospital", BedStatus.VACANT));

        BaseBed bed102 = new BaseBed(102L, "Emergency ICU Ward A", "City Central Hospital", BedStatus.VACANT);
        bed102.assignPatient(5001L, "Robert Chen", TriageLevel.LEVEL_4);
        IN_MEMORY_BED_STORE.put(102L, bed102);

        BaseBed bed103 = new BaseBed(103L, "Emergency ICU Ward A", "City Central Hospital", BedStatus.VACANT);
        bed103.assignPatient(5002L, "Emily Davis", TriageLevel.LEVEL_5);
        IN_MEMORY_BED_STORE.put(103L, bed103);

        IN_MEMORY_BED_STORE.put(104L, new BaseBed(104L, "General Medicine Ward B", "City Central Hospital", BedStatus.REQUIRES_SANITIZATION));
        IN_MEMORY_BED_STORE.put(105L, new BaseBed(105L, "General Medicine Ward B", "City Central Hospital", BedStatus.VACANT));
        IN_MEMORY_BED_STORE.put(106L, new BaseBed(106L, "Infectious Isolation Ward C", "City Central Hospital", BedStatus.VACANT));

        // Beds 201-203 (Memorial Care Hospital)
        IN_MEMORY_BED_STORE.put(201L, new BaseBed(201L, "Cardiology Intensive Care", "Memorial Care Hospital", BedStatus.VACANT));
        BaseBed bed202 = new BaseBed(202L, "Cardiology Intensive Care", "Memorial Care Hospital", BedStatus.VACANT);
        bed202.assignPatient(5003L, "James Wilson", TriageLevel.LEVEL_2);
        IN_MEMORY_BED_STORE.put(202L, bed202);
        IN_MEMORY_BED_STORE.put(203L, new BaseBed(203L, "Cardiology Intensive Care", "Memorial Care Hospital", BedStatus.VACANT));

        // Beds 301-303 (Metro General Hospital)
        IN_MEMORY_BED_STORE.put(301L, new BaseBed(301L, "Trauma ICU", "Metro General Hospital", BedStatus.VACANT));
        IN_MEMORY_BED_STORE.put(302L, new BaseBed(302L, "Trauma ICU", "Metro General Hospital", BedStatus.RESERVED_EN_ROUTE));
        BaseBed bed303 = new BaseBed(303L, "Trauma ICU", "Metro General Hospital", BedStatus.VACANT);
        bed303.assignPatient(5005L, "Sarah Jenkins", TriageLevel.LEVEL_5);
        IN_MEMORY_BED_STORE.put(303L, bed303);
    }

    /**
     * Returns the shared in-memory fallback store of beds.
     *
     * @return in-memory map of bed ID to BaseBed
     */
    public static ConcurrentHashMap<Long, BaseBed> getInMemoryBedStore() {
        return IN_MEMORY_BED_STORE;
    }

    // ──────────────────────────── SQL Constants ────────────────────────────

    private static final String TABLE = "beds";

    private static final String SELECT_ALL_COLUMNS =
            "SELECT bed_id, ward_name, hospital_name, status, " +
            "patient_id, patient_name, patient_triage FROM " + TABLE;

    private static final String SQL_FIND_BY_ID =
            SELECT_ALL_COLUMNS + " WHERE bed_id = ?";

    private static final String SQL_FIND_AVAILABLE =
            SELECT_ALL_COLUMNS + " WHERE hospital_name = ? AND status = 'VACANT'";

    private static final String SQL_FIND_ALL_BY_HOSPITAL =
            SELECT_ALL_COLUMNS + " WHERE hospital_name = ?";

    private static final String SQL_UPDATE_STATUS =
            "UPDATE " + TABLE + " SET status = ? WHERE bed_id = ?";

    private static final String SQL_BOOK_BED =
            "UPDATE " + TABLE +
            " SET status = 'OCCUPIED', patient_id = ?, patient_name = ?, patient_triage = ?" +
            " WHERE bed_id = ? AND status = ?";

    private static final String SQL_DISCHARGE_BED =
            "UPDATE " + TABLE +
            " SET status = 'REQUIRES_SANITIZATION', patient_id = NULL, " +
            "patient_name = NULL, patient_triage = NULL" +
            " WHERE bed_id = ?";

    private static final String SQL_COUNT_AVAILABLE =
            "SELECT COUNT(*) FROM " + TABLE + " WHERE hospital_name = ? AND status = 'VACANT'";

    private static final String SQL_INSERT =
            "INSERT INTO " + TABLE +
            " (ward_name, hospital_name, status, patient_id, patient_name, patient_triage)" +
            " VALUES (?, ?, ?, ?, ?, ?)";

    // ──────────────────────────── Read Operations ─────────────────────────

    /**
     * Finds a single bed by its primary key.
     *
     * @param bedId the unique bed identifier
     * @return an {@link Optional} containing the bed, or empty if not found
     */
    public Optional<BaseBed> findById(long bedId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {

            ps.setLong(1, bedId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BaseBed bed = mapRow(rs);
                    IN_MEMORY_BED_STORE.put(bedId, bed);
                    return Optional.of(bed);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB findById failed for bed #" + bedId + ", using in-memory fallback: " + e.getMessage());
        }
        return Optional.ofNullable(IN_MEMORY_BED_STORE.get(bedId));
    }

    /**
     * Returns all vacant beds at the specified hospital.
     *
     * @param hospitalName the hospital to search
     * @return list of available beds (never {@code null})
     */
    public List<BaseBed> findAvailableBeds(String hospitalName) {
        List<BaseBed> beds = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_AVAILABLE)) {

            ps.setString(1, hospitalName);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BaseBed bed = mapRow(rs);
                    beds.add(bed);
                    IN_MEMORY_BED_STORE.put(bed.getBedId(), bed);
                }
            }
            if (!beds.isEmpty()) {
                return beds;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB findAvailableBeds failed for hospital=" + hospitalName + ", using in-memory fallback: " + e.getMessage());
        }

        for (BaseBed bed : IN_MEMORY_BED_STORE.values()) {
            if ((hospitalName == null || hospitalName.isBlank() || hospitalName.equalsIgnoreCase(bed.getHospitalName()))
                    && bed.getBedStatus() == BedStatus.VACANT) {
                beds.add(bed);
            }
        }
        return beds;
    }

    /**
     * Returns all beds at the specified hospital regardless of status.
     *
     * @param hospitalName the hospital to search
     * @return list of beds (never {@code null})
     */
    public List<BaseBed> findAllByHospital(String hospitalName) {
        List<BaseBed> beds = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL_BY_HOSPITAL)) {

            ps.setString(1, hospitalName);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BaseBed bed = mapRow(rs);
                    beds.add(bed);
                    IN_MEMORY_BED_STORE.put(bed.getBedId(), bed);
                }
            }
            if (!beds.isEmpty()) {
                return beds;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB findAllByHospital failed for hospital=" + hospitalName + ", using in-memory fallback: " + e.getMessage());
        }

        for (BaseBed bed : IN_MEMORY_BED_STORE.values()) {
            if (hospitalName == null || hospitalName.isBlank() || hospitalName.equalsIgnoreCase(bed.getHospitalName())) {
                beds.add(bed);
            }
        }
        return beds;
    }

    /**
     * Returns all beds across all hospitals.
     *
     * @return list of all beds
     */
    public List<BaseBed> findAllBeds() {
        return findAllByHospital(null);
    }

    /**
     * Counts the number of vacant beds at a hospital.
     *
     * @param hospitalName the hospital to check
     * @return count of available beds
     */
    public int countAvailableBeds(String hospitalName) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_COUNT_AVAILABLE)) {

            ps.setString(1, hospitalName);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB countAvailableBeds failed for hospital=" + hospitalName + ", using in-memory fallback: " + e.getMessage());
        }
        return findAvailableBeds(hospitalName).size();
    }

    // ──────────────────────────── Write Operations ────────────────────────

    /**
     * Inserts a new bed record and returns the auto-generated primary key.
     *
     * @param bed the bed to persist
     * @return the generated {@code bed_id}
     * @throws SQLException if the insert fails
     */
    public long insert(BaseBed bed) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, bed.getWardName());
            ps.setString(2, bed.getHospitalName());
            ps.setString(3, bed.getBedStatus().name());
            if (bed.getCurrentPatientId() != null) {
                ps.setLong(4, bed.getCurrentPatientId());
            } else {
                ps.setNull(4, Types.BIGINT);
            }
            ps.setString(5, bed.getCurrentPatientName());
            ps.setString(6, bed.getCurrentTriageLevel() != null ? bed.getCurrentTriageLevel().name() : null);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Inserting bed failed, no rows affected.");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    bed.assignPatient(bed.getCurrentPatientId() != null ? bed.getCurrentPatientId() : 0L,
                            bed.getCurrentPatientName() != null ? bed.getCurrentPatientName() : "",
                            bed.getCurrentTriageLevel() != null ? bed.getCurrentTriageLevel() : TriageLevel.LEVEL_5);
                    IN_MEMORY_BED_STORE.put(id, bed);
                    LOGGER.info("Inserted bed id=" + id + " ward=" + bed.getWardName() +
                                " hospital=" + bed.getHospitalName());
                    return id;
                } else {
                    throw new SQLException("Inserting bed failed, no generated key obtained.");
                }
            }
        }
    }

    /**
     * Updates the status of a bed.
     *
     * @param bedId     the bed to update
     * @param newStatus the new status
     */
    public void updateBedStatus(long bedId, BedStatus newStatus) {
        BaseBed mem = IN_MEMORY_BED_STORE.get(bedId);
        if (mem != null) {
            mem.setBedStatus(newStatus);
        } else {
            IN_MEMORY_BED_STORE.put(bedId, new BaseBed(bedId, "General Ward", "City Central Hospital", newStatus));
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            ps.setString(1, newStatus.name());
            ps.setLong(2, bedId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                LOGGER.fine("Updated bed id=" + bedId + " status=" + newStatus);
            } else {
                LOGGER.warning("No bed found in DB with id=" + bedId + " for status update, updated in-memory");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB updateBedStatus failed for bed id=" + bedId + ", updated in-memory: " + e.getMessage());
        }
    }

    /**
     * Atomically books a bed for a patient using <strong>optimistic locking</strong>.
     * <p>
     * The UPDATE only succeeds when the bed's current status matches
     * {@code expectedCurrentStatus}. If another thread changed the status
     * concurrently (affected rows == 0), a {@link BedAlreadyBookedException}
     * is thrown.
     * </p>
     *
     * @param bedId                 the bed to book
     * @param patientId             the patient's ID
     * @param patientName           the patient's display name
     * @param level                 the patient's triage level
     * @param expectedCurrentStatus the status the bed is expected to be in (typically VACANT)
     * @throws BedAlreadyBookedException if the bed's status has changed since it was read
     */
    public void bookBed(long bedId, long patientId, String patientName,
                        TriageLevel level, BedStatus expectedCurrentStatus) {
        BaseBed mem = IN_MEMORY_BED_STORE.get(bedId);
        if (mem != null) {
            if (mem.getBedStatus() != expectedCurrentStatus) {
                throw new BedAlreadyBookedException(
                        "Bed #" + bedId + " could not be booked — its status is no longer " +
                        expectedCurrentStatus + ". Another user may have booked it concurrently.");
            }
            mem.assignPatient(patientId, patientName, level);
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_BOOK_BED)) {

            ps.setLong(1, patientId);
            ps.setString(2, patientName);
            ps.setString(3, level.name());
            ps.setLong(4, bedId);
            ps.setString(5, expectedCurrentStatus.name());

            int affected = ps.executeUpdate();
            if (affected == 0 && mem == null) {
                throw new BedAlreadyBookedException(
                        "Bed #" + bedId + " could not be booked — its status is no longer " +
                        expectedCurrentStatus + ". Another user may have booked it concurrently.");
            }

            LOGGER.info("Booked bed id=" + bedId + " for patient '" + patientName +
                        "' (id=" + patientId + ", triage=" + level + ")");
        } catch (BedAlreadyBookedException e) {
            throw e;  // re-throw domain exception
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB bookBed failed for bed id=" + bedId + ", booked in-memory: " + e.getMessage());
        }
    }

    /**
     * Discharges a patient from a bed — sets status to REQUIRES_SANITIZATION
     * and clears all patient fields.
     *
     * @param bedId the bed to discharge
     */
    public void dischargeBed(long bedId) {
        BaseBed mem = IN_MEMORY_BED_STORE.get(bedId);
        if (mem != null) {
            mem.dischargePatient();
        } else {
            IN_MEMORY_BED_STORE.put(bedId, new BaseBed(bedId, "General Ward", "City Central Hospital", BedStatus.REQUIRES_SANITIZATION));
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DISCHARGE_BED)) {

            ps.setLong(1, bedId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                LOGGER.info("Discharged bed id=" + bedId);
            } else {
                LOGGER.warning("No bed found in DB with id=" + bedId + " to discharge, discharged in-memory");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB dischargeBed failed for bed id=" + bedId + ", discharged in-memory: " + e.getMessage());
        }
    }

    // ──────────────────────────── Row Mapping ─────────────────────────────

    /**
     * Maps the current row of a {@link ResultSet} to a {@link BaseBed}.
     *
     * @param rs the result set positioned at the row to map
     * @return a fully-hydrated {@link BaseBed} instance
     * @throws SQLException if a column cannot be read
     */
    private BaseBed mapRow(ResultSet rs) throws SQLException {
        long bedId          = rs.getLong("bed_id");
        String wardName     = rs.getString("ward_name");
        String hospitalName = rs.getString("hospital_name");
        BedStatus status    = BedStatus.parseDatabase(rs.getString("status"));

        BaseBed bed = new BaseBed(bedId, wardName, hospitalName, status);

        // Hydrate patient state if present
        long patientId = rs.getLong("patient_id");
        if (!rs.wasNull()) {
            String patientName   = rs.getString("patient_name");
            String triageStr     = rs.getString("patient_triage");
            TriageLevel triage   = triageStr != null ? TriageLevel.parseDatabase(triageStr) : TriageLevel.LEVEL_5;
            bed.assignPatient(patientId, patientName != null ? patientName : "Unknown", triage);
        }

        return bed;
    }
}
