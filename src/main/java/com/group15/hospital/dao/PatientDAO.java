package com.group15.hospital.dao;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.model.enums.DietType;
import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.model.hierarchy.Patient;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

// patient dao class
public class PatientDAO {

    private static final Logger LOGGER = Logger.getLogger(PatientDAO.class.getName());

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    // in-memory store for fallback
    private final ConcurrentHashMap<Long, Patient> inMemoryStore = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1000);

    // sql queries
    private static final String SQL_INSERT =
            "INSERT INTO patients (full_name, age, gender, triage_level, assigned_bed_id, " +
            "diet_type, admission_time, discharge_time, is_discharged) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_SELECT_BY_ID =
            "SELECT patient_id, full_name, age, gender, triage_level, assigned_bed_id, " +
            "diet_type, admission_time, discharge_time, is_discharged " +
            "FROM patients WHERE patient_id = ?";

    private static final String SQL_SELECT_ALL =
            "SELECT patient_id, full_name, age, gender, triage_level, assigned_bed_id, " +
            "diet_type, admission_time, discharge_time, is_discharged " +
            "FROM patients ORDER BY patient_id ASC";

    private static final String SQL_SELECT_BY_TRIAGE =
            "SELECT patient_id, full_name, age, gender, triage_level, assigned_bed_id, " +
            "diet_type, admission_time, discharge_time, is_discharged " +
            "FROM patients WHERE triage_level = ? AND is_discharged = FALSE " +
            "ORDER BY admission_time ASC";

    private static final String SQL_SELECT_ACTIVE_ADMITTED =
            "SELECT patient_id, full_name, age, gender, triage_level, assigned_bed_id, " +
            "diet_type, admission_time, discharge_time, is_discharged " +
            "FROM patients WHERE is_discharged = FALSE AND assigned_bed_id IS NOT NULL";

    private static final String SQL_UPDATE_BED =
            "UPDATE patients SET assigned_bed_id = ? WHERE patient_id = ?";

    private static final String SQL_DISCHARGE =
            "UPDATE patients SET is_discharged = TRUE, discharge_time = ?, assigned_bed_id = NULL " +
            "WHERE patient_id = ?";

    private static final String SQL_DELETE =
            "DELETE FROM patients WHERE patient_id = ?";

    public PatientDAO() {
        // seed sample patients for testing and fallback
        seedSamplePatient(101L, "Ramesh Kumar", 45, "Male", TriageLevel.LEVEL_1, 101L, DietType.IV_FLUIDS);
        seedSamplePatient(102L, "Sunita Sharma", 32, "Female", TriageLevel.LEVEL_2, 102L, DietType.LIQUID);
        seedSamplePatient(103L, "Anand Verma", 60, "Male", TriageLevel.LEVEL_4, null, DietType.REGULAR);
    }

    private void seedSamplePatient(Long id, String name, int age, String gender, TriageLevel triage, Long bedId, DietType diet) {
        Patient p = new Patient(id, name, age, gender, triage, bedId, diet, LocalDateTime.now(), null, false);
        inMemoryStore.put(id, p);
    }

    // saves patient to db or fallback
    public Patient save(Patient patient) {
        if (patient == null) {
            throw new IllegalArgumentException("Patient cannot be null");
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, (patient.getFullName() != null) ? patient.getFullName() : "Unknown Patient");
            ps.setInt(2, patient.getAge());
            ps.setString(3, (patient.getGender() != null) ? patient.getGender() : "Unknown");
            ps.setString(4, (patient.getTriageLevel() != null) ? patient.getTriageLevel().name() : TriageLevel.LEVEL_5.name());

            if (patient.getAssignedBedId() != null) {
                ps.setLong(5, patient.getAssignedBedId());
            } else {
                ps.setNull(5, Types.BIGINT);
            }

            ps.setString(6, (patient.getDietType() != null) ? patient.getDietType().name() : DietType.REGULAR.name());

            Timestamp admitTs = (patient.getAdmissionTime() != null)
                    ? Timestamp.valueOf(patient.getAdmissionTime())
                    : Timestamp.valueOf(LocalDateTime.now());
            ps.setTimestamp(7, admitTs);

            if (patient.getDischargeTime() != null) {
                ps.setTimestamp(8, Timestamp.valueOf(patient.getDischargeTime()));
            } else {
                ps.setNull(8, Types.TIMESTAMP);
            }

            ps.setBoolean(9, patient.isDischarged());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        long generatedId = rs.getLong(1);
                        patient.setPatientId(generatedId);
                    }
                }
            }
            inMemoryStore.put(patient.getPatientId(), patient);
            return patient;

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert patient into DB. Using in-memory fallback: " + e.getMessage());
            long newId = (patient.getPatientId() > 0) ? patient.getPatientId() : idSequence.incrementAndGet();
            patient.setPatientId(newId);
            inMemoryStore.put(newId, patient);
            return patient;
        }
    }

    // finds patient by id
    public Optional<Patient> findById(long patientId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {

            ps.setLong(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Patient patient = mapRowToPatient(rs);
                    inMemoryStore.put(patient.getPatientId(), patient);
                    return Optional.of(patient);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for patientId=" + patientId + ", reading from fallback: " + e.getMessage());
        }

        return Optional.ofNullable(inMemoryStore.get(patientId));
    }

    // gets all registered patients
    public List<Patient> findAll() {
        List<Patient> list = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Patient patient = mapRowToPatient(rs);
                list.add(patient);
                inMemoryStore.put(patient.getPatientId(), patient);
            }
            if (!list.isEmpty()) {
                return list;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findAll patients. Using fallback: " + e.getMessage());
        }

        return new ArrayList<>(inMemoryStore.values());
    }

    // finds active patients by triage level
    public List<Patient> findByTriageLevel(TriageLevel level) {
        if (level == null) {
            return new ArrayList<>();
        }

        List<Patient> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_TRIAGE)) {

            ps.setString(1, level.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Patient p = mapRowToPatient(rs);
                    list.add(p);
                    inMemoryStore.put(p.getPatientId(), p);
                }
            }
            if (!list.isEmpty()) {
                return list;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findByTriageLevel. Using fallback: " + e.getMessage());
        }

        // Fallback filter
        for (Patient p : inMemoryStore.values()) {
            if (!p.isDischarged() && p.getTriageLevel() == level) {
                list.add(p);
            }
        }
        return list;
    }

    // finds all admitted patients occupying beds
    public List<Patient> findAdmittedPatients() {
        List<Patient> list = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ACTIVE_ADMITTED);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Patient p = mapRowToPatient(rs);
                list.add(p);
                inMemoryStore.put(p.getPatientId(), p);
            }
            if (!list.isEmpty()) {
                return list;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findAdmittedPatients. Using fallback: " + e.getMessage());
        }

        for (Patient p : inMemoryStore.values()) {
            if (p.isAdmitted()) {
                list.add(p);
            }
        }
        return list;
    }

    // updates bed assigned to patient
    public boolean updateAssignedBed(long patientId, Long bedId) {
        boolean updatedInDb = false;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_BED)) {

            if (bedId != null) {
                ps.setLong(1, bedId);
            } else {
                ps.setNull(1, Types.BIGINT);
            }
            ps.setLong(2, patientId);

            updatedInDb = (ps.executeUpdate() > 0);

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB update assigned bed failed for patientId=" + patientId + ": " + e.getMessage());
        }

        Patient existing = inMemoryStore.get(patientId);
        if (existing != null) {
            existing.setAssignedBedId(bedId);
            return true;
        }
        return updatedInDb;
    }

    // discharges patient and stamps discharge time
    public boolean dischargePatient(long patientId) {
        boolean updatedInDb = false;
        LocalDateTime now = LocalDateTime.now();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DISCHARGE)) {

            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setLong(2, patientId);

            updatedInDb = (ps.executeUpdate() > 0);

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB discharge failed for patientId=" + patientId + ": " + e.getMessage());
        }

        Patient existing = inMemoryStore.get(patientId);
        if (existing != null) {
            existing.discharge();
            return true;
        }
        return updatedInDb;
    }

    // deletes patient by id
    public boolean delete(long patientId) {
        boolean deletedInDb = false;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {

            ps.setLong(1, patientId);
            deletedInDb = (ps.executeUpdate() > 0);

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB delete failed for patientId=" + patientId + ": " + e.getMessage());
        }

        Patient removed = inMemoryStore.remove(patientId);
        return deletedInDb || (removed != null);
    }

    private Patient mapRowToPatient(ResultSet rs) throws SQLException {
        long id = rs.getLong("patient_id");
        String name = rs.getString("full_name");
        int age = rs.getInt("age");
        String gender = rs.getString("gender");

        TriageLevel triage = TriageLevel.parseDatabase(rs.getString("triage_level"));

        long bedIdVal = rs.getLong("assigned_bed_id");
        Long bedId = rs.wasNull() ? null : bedIdVal;

        DietType diet = DietType.parseDatabase(rs.getString("diet_type"));

        Timestamp admitTs = rs.getTimestamp("admission_time");
        LocalDateTime admitTime = (admitTs != null) ? admitTs.toLocalDateTime() : LocalDateTime.now();

        Timestamp dischargeTs = rs.getTimestamp("discharge_time");
        LocalDateTime dischargeTime = (dischargeTs != null) ? dischargeTs.toLocalDateTime() : null;

        boolean isDischarged = rs.getBoolean("is_discharged");

        return new Patient(id, name, age, gender, triage, bedId, diet, admitTime, dischargeTime, isDischarged);
    }
}
