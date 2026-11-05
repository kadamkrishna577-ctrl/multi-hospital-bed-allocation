package com.group15.hospital.dao;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.model.bed.MedicalMachine;
import com.group15.hospital.model.enums.EquipmentType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for the {@code medical_machines} table.
 * <p>
 * Provides CRUD operations and specialised queries for
 * {@link MedicalMachine} entities. Every method obtains its own
 * {@link Connection} from {@link DatabaseConnection#getInstance()} and
 * uses try-with-resources to guarantee proper resource cleanup.
 * </p>
 *
 * <h3>Table schema</h3>
 * <pre>{@code
 * CREATE TABLE medical_machines (
 *     machine_id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
 *     equipment_type       VARCHAR(50)  NOT NULL,
 *     serial_number        VARCHAR(100) NOT NULL,
 *     hospital_name        VARCHAR(255) NOT NULL,
 *     operating_hours_used DOUBLE       NOT NULL DEFAULT 0,
 *     under_maintenance    BOOLEAN      NOT NULL DEFAULT FALSE
 * );
 * }</pre>
 *
 * @author Group 15
 * @see MedicalMachine
 * @see DatabaseConnection
 */
public class EquipmentDAO {

    private static final Logger LOGGER = Logger.getLogger(EquipmentDAO.class.getName());

    /** Singleton database connection provider. */
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    /** In-memory fallback store for offline resilience when MySQL is unreachable. */
    private static final ConcurrentHashMap<Long, MedicalMachine> IN_MEMORY_STORE = new ConcurrentHashMap<>();

    static {
        seedInitialInMemoryMachines();
    }

    private static void seedInitialInMemoryMachines() {
        // City Central Hospital (machines attached/assigned to beds 101, 102, 103)
        IN_MEMORY_STORE.put(1L, new MedicalMachine(1L, EquipmentType.VENTILATOR, "VENT-CC-101", "City Central Hospital", 120.5, false));
        IN_MEMORY_STORE.put(2L, new MedicalMachine(2L, EquipmentType.VENTILATOR, "VENT-CC-102", "City Central Hospital", 485.0, false));
        IN_MEMORY_STORE.put(3L, new MedicalMachine(3L, EquipmentType.OXYGEN, "OXY-CC-201", "City Central Hospital", 250.0, false));
        IN_MEMORY_STORE.put(4L, new MedicalMachine(4L, EquipmentType.DIALYSIS, "DIA-CC-301", "City Central Hospital", 290.0, false));
        IN_MEMORY_STORE.put(5L, new MedicalMachine(5L, EquipmentType.MONITOR, "MON-CC-401", "City Central Hospital", 800.0, false));

        // Memorial Care Hospital
        IN_MEMORY_STORE.put(6L, new MedicalMachine(6L, EquipmentType.VENTILATOR, "VENT-MC-101", "Memorial Care Hospital", 510.0, true));
        IN_MEMORY_STORE.put(7L, new MedicalMachine(7L, EquipmentType.OXYGEN, "OXY-MC-201", "Memorial Care Hospital", 920.0, false));
        IN_MEMORY_STORE.put(8L, new MedicalMachine(8L, EquipmentType.MONITOR, "MON-MC-401", "Memorial Care Hospital", 1450.0, false));

        // Metro General Hospital
        IN_MEMORY_STORE.put(9L, new MedicalMachine(9L, EquipmentType.VENTILATOR, "VENT-MG-101", "Metro General Hospital", 50.0, false));
        IN_MEMORY_STORE.put(10L, new MedicalMachine(10L, EquipmentType.DIALYSIS, "DIA-MG-301", "Metro General Hospital", 110.0, false));
    }

    // ──────────────────────────── SQL Constants ────────────────────────────

    private static final String TABLE = "medical_machines";

    private static final String SELECT_ALL_COLUMNS =
            "SELECT machine_id, equipment_type, serial_number, hospital_name, " +
            "operating_hours_used, under_maintenance FROM " + TABLE;

    private static final String SQL_FIND_BY_ID =
            SELECT_ALL_COLUMNS + " WHERE machine_id = ?";

    private static final String SQL_FIND_ALL =
            SELECT_ALL_COLUMNS;

    private static final String SQL_FIND_BY_HOSPITAL =
            SELECT_ALL_COLUMNS + " WHERE hospital_name = ?";

    private static final String SQL_FIND_BY_TYPE =
            SELECT_ALL_COLUMNS + " WHERE equipment_type = ?";

    private static final String SQL_INSERT =
            "INSERT INTO " + TABLE +
            " (equipment_type, serial_number, hospital_name, operating_hours_used, under_maintenance)" +
            " VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE_HOURS =
            "UPDATE " + TABLE + " SET operating_hours_used = ? WHERE machine_id = ?";

    private static final String SQL_ADD_HOURS =
            "UPDATE " + TABLE + " SET operating_hours_used = operating_hours_used + ? WHERE machine_id = ?";

    private static final String SQL_FLAG_MAINTENANCE =
            "UPDATE " + TABLE + " SET under_maintenance = TRUE WHERE machine_id = ?";

    private static final String SQL_RESET_HOURS =
            "UPDATE " + TABLE + " SET operating_hours_used = 0, under_maintenance = FALSE WHERE machine_id = ?";

    private static final String SQL_FIND_NEEDING_MAINTENANCE =
            SELECT_ALL_COLUMNS + " WHERE under_maintenance = TRUE";

    private static final String SQL_DELETE =
            "DELETE FROM " + TABLE + " WHERE machine_id = ?";

    // ──────────────────────────── Read Operations ─────────────────────────

    /**
     * Finds a single machine by its primary key.
     *
     * @param machineId the unique machine identifier
     * @return an {@link Optional} containing the machine, or empty if not found
     */
    public Optional<MedicalMachine> findById(long machineId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {

            ps.setLong(1, machineId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB findById failed for machine #" + machineId + ", using in-memory fallback: " + e.getMessage());
        }
        return Optional.ofNullable(IN_MEMORY_STORE.get(machineId));
    }

    /**
     * Returns every machine in the {@code medical_machines} table.
     *
     * @return an unmodifiable-safe list of all machines (never {@code null})
     */
    public List<MedicalMachine> findAll() {
        List<MedicalMachine> machines = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                machines.add(mapRow(rs));
            }
            if (!machines.isEmpty()) {
                return machines;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB findAll failed, using in-memory fallback: " + e.getMessage());
        }
        return new ArrayList<>(IN_MEMORY_STORE.values());
    }

    /**
     * Returns all machines deployed at the specified hospital.
     *
     * @param hospitalName the hospital to filter by (case-sensitive)
     * @return list of matching machines (never {@code null})
     */
    public List<MedicalMachine> findByHospital(String hospitalName) {
        if (hospitalName == null || hospitalName.trim().isEmpty() || hospitalName.equalsIgnoreCase("All") || hospitalName.equalsIgnoreCase("All Hospitals")) {
            return findAll();
        }

        List<MedicalMachine> machines = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_HOSPITAL)) {

            ps.setString(1, hospitalName);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    machines.add(mapRow(rs));
                }
            }
            if (!machines.isEmpty()) {
                return machines;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB findByHospital failed for " + hospitalName + ", using in-memory fallback: " + e.getMessage());
        }
        return IN_MEMORY_STORE.values().stream()
                .filter(m -> m.getHospitalName().equalsIgnoreCase(hospitalName))
                .collect(Collectors.toList());
    }

    /**
     * Returns all machines of the given {@link EquipmentType}.
     *
     * @param type the equipment type to filter by
     * @return list of matching machines (never {@code null})
     */
    public List<MedicalMachine> findByType(EquipmentType type) {
        List<MedicalMachine> machines = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_TYPE)) {

            ps.setString(1, type.name());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    machines.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find machines by type=" + type, e);
        }
        return machines;
    }

    /**
     * Finds all machines that currently require maintenance.
     * <p>
     * A machine needs maintenance when its {@code under_maintenance} flag is
     * {@code true}.
     * </p>
     *
     * @return list of machines needing maintenance (never {@code null})
     */
    public List<MedicalMachine> findMachinesNeedingMaintenance() {
        List<MedicalMachine> machines = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_NEEDING_MAINTENANCE);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                machines.add(mapRow(rs));
            }
            if (!machines.isEmpty()) {
                return machines;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB findMachinesNeedingMaintenance failed, using in-memory fallback: " + e.getMessage());
        }
        return IN_MEMORY_STORE.values().stream()
                .filter(MedicalMachine::isUnderMaintenance)
                .collect(Collectors.toList());
    }

    // ──────────────────────────── Write Operations ────────────────────────

    /**
     * Inserts a new machine record and returns the auto-generated primary key.
     *
     * @param machine the machine to persist (its {@code machineId} is ignored;
     *                the database assigns the key)
     * @return the generated {@code machine_id}
     * @throws SQLException if the insert fails or no key is generated
     */
    public long insert(MedicalMachine machine) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, machine.getEquipmentType().name());
            ps.setString(2, machine.getSerialNumber());
            ps.setString(3, machine.getHospitalName());
            ps.setDouble(4, machine.getOperatingHoursUsed());
            ps.setBoolean(5, machine.isUnderMaintenance());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Inserting machine failed, no rows affected.");
            }

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long generatedId = generatedKeys.getLong(1);
                    LOGGER.info("Inserted machine id=" + generatedId +
                                " type=" + machine.getEquipmentType().getDisplayName() +
                                " serial=" + machine.getSerialNumber());
                    return generatedId;
                } else {
                    throw new SQLException("Inserting machine failed, no generated key obtained.");
                }
            }
        }
    }

    /**
     * Updates the operating hours for a specific machine to an absolute value.
     *
     * @param machineId the machine to update
     * @param newHours  the new total operating hours
     */
    public void updateOperatingHours(long machineId, double newHours) {
        MedicalMachine mem = IN_MEMORY_STORE.get(machineId);
        if (mem != null) {
            try {
                java.lang.reflect.Field field = MedicalMachine.class.getDeclaredField("operatingHoursUsed");
                field.setAccessible(true);
                field.set(mem, newHours);
            } catch (Exception ignored) {}
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_HOURS)) {

            ps.setDouble(1, newHours);
            ps.setLong(2, machineId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                LOGGER.fine("Updated operating hours for machine id=" + machineId + " to " + newHours);
            } else {
                LOGGER.warning("No machine found with id=" + machineId + " for hours update");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update operating hours for machine id=" + machineId, e);
        }
    }

    /**
     * Atomically increments the operating hours for a machine.
     * <p>
     * Executes {@code UPDATE ... SET operating_hours_used = operating_hours_used + ?}
     * so the increment is safe under concurrent access.
     * </p>
     *
     * @param machineId  the machine to update
     * @param hoursToAdd the hours to add (must be positive)
     */
    public void addOperatingHours(long machineId, double hoursToAdd) {
        MedicalMachine mem = IN_MEMORY_STORE.get(machineId);
        if (mem != null) {
            mem.addOperatingHours(hoursToAdd);
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ADD_HOURS)) {

            ps.setDouble(1, hoursToAdd);
            ps.setLong(2, machineId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                LOGGER.fine("Added " + hoursToAdd + " operating hours to machine id=" + machineId);
            } else {
                LOGGER.warning("No machine found with id=" + machineId + " for addOperatingHours");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to add operating hours for machine id=" + machineId, e);
        }
    }

    /**
     * Flags a machine as requiring maintenance.
     *
     * @param machineId the machine to flag
     */
    public void flagForMaintenance(long machineId) {
        MedicalMachine mem = IN_MEMORY_STORE.get(machineId);
        if (mem != null) {
            mem.setUnderMaintenance(true);
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FLAG_MAINTENANCE)) {

            ps.setLong(1, machineId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                LOGGER.info("Flagged machine id=" + machineId + " for maintenance");
            } else {
                LOGGER.warning("No machine found with id=" + machineId + " to flag for maintenance");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to flag machine id=" + machineId + " for maintenance", e);
        }
    }

    /**
     * Resets a machine's operating hours to zero and clears the maintenance flag.
     * <p>
     * Typically called after a repair or scheduled service is completed.
     * </p>
     *
     * @param machineId the machine to reset
     */
    public void resetOperatingHours(long machineId) {
        MedicalMachine mem = IN_MEMORY_STORE.get(machineId);
        if (mem != null) {
            mem.completeMaintenance();
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RESET_HOURS)) {

            ps.setLong(1, machineId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                LOGGER.info("Reset operating hours and cleared maintenance flag for machine id=" + machineId);
            } else {
                LOGGER.warning("No machine found with id=" + machineId + " to reset");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to reset operating hours for machine id=" + machineId, e);
        }
    }

    /**
     * Deletes a machine record from the database.
     *
     * @param machineId the machine to delete
     */
    public void delete(long machineId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {

            ps.setLong(1, machineId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                LOGGER.info("Deleted machine id=" + machineId);
            } else {
                LOGGER.warning("No machine found with id=" + machineId + " to delete");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete machine id=" + machineId, e);
        }
    }

    // ──────────────────────────── Row Mapping ─────────────────────────────

    /**
     * Maps the current row of a {@link ResultSet} to a {@link MedicalMachine}.
     *
     * @param rs the result set positioned at the row to map
     * @return a fully-hydrated {@link MedicalMachine} instance
     * @throws SQLException if a column cannot be read
     */
    private MedicalMachine mapRow(ResultSet rs) throws SQLException {
        long machineId            = rs.getLong("machine_id");
        EquipmentType type        = EquipmentType.parseDatabase(rs.getString("equipment_type"));
        String serialNumber       = rs.getString("serial_number");
        String hospitalName       = rs.getString("hospital_name");
        double operatingHoursUsed = rs.getDouble("operating_hours_used");
        boolean underMaintenance  = rs.getBoolean("under_maintenance");

        return new MedicalMachine(machineId, type, serialNumber, hospitalName,
                                  operatingHoursUsed, underMaintenance);
    }
}
