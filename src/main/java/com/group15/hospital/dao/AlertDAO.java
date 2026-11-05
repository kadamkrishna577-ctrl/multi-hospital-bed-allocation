package com.group15.hospital.dao;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.model.ambulance.SystemAlert;
import com.group15.hospital.model.enums.UserRole;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Data Access Object for persisting system alerts and maintaining offline alert queues
 * for clinical and triage workstations (SRS F13, F15, Use Case U5 & U6).
 * <p>
 * Supports offline triage screens (U6 Exception 3) by persisting alerts until acknowledged,
 * ensuring staff receive all missed notifications upon reconnection. Includes a thread-safe
 * in-memory fallback store when MySQL is offline.
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class AlertDAO {

    private static final Logger LOGGER = Logger.getLogger(AlertDAO.class.getName());

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    // ── In-Memory Fallback & Queue Store ──
    private final ConcurrentHashMap<Long, SystemAlert> alertStore = new ConcurrentHashMap<>();
    private final AtomicLong alertIdSequence = new AtomicLong(5000);

    // ── SQL Queries ──
    private static final String SQL_INSERT_ALERT =
            "INSERT INTO system_alerts (alert_type, target_role, hospital_name, bed_id, message, created_at, is_acknowledged) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_FETCH_UNACKNOWLEDGED =
            "SELECT alert_id, alert_type, target_role, hospital_name, bed_id, message, created_at, is_acknowledged " +
            "FROM system_alerts WHERE hospital_name = ? AND target_role = ? AND is_acknowledged = FALSE " +
            "ORDER BY created_at ASC";

    private static final String SQL_FETCH_RECENT =
            "SELECT alert_id, alert_type, target_role, hospital_name, bed_id, message, created_at, is_acknowledged " +
            "FROM system_alerts WHERE hospital_name = ? " +
            "ORDER BY created_at DESC LIMIT ?";

    private static final String SQL_MARK_ACKNOWLEDGED =
            "UPDATE system_alerts SET is_acknowledged = TRUE WHERE alert_id = ?";

    private static final String SQL_MARK_ALL_ACKNOWLEDGED =
            "UPDATE system_alerts SET is_acknowledged = TRUE WHERE hospital_name = ? AND target_role = ? AND is_acknowledged = FALSE";

    public AlertDAO() {}

    /**
     * Persists an alert to the database and in-memory queue.
     *
     * @param alert the alert payload
     * @return persisted {@link SystemAlert} with generated ID
     */
    public SystemAlert saveAlert(SystemAlert alert) {
        Objects.requireNonNull(alert, "alert must not be null");

        long generatedId = 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT_ALERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, alert.getAlertType());
            ps.setString(2, alert.getTargetRole().name());
            ps.setString(3, alert.getHospitalName());
            if (alert.getBedId() != null) {
                ps.setLong(4, alert.getBedId());
            } else {
                ps.setNull(4, Types.BIGINT);
            }
            ps.setString(5, alert.getMessage());
            ps.setTimestamp(6, Timestamp.valueOf(alert.getTimestamp()));
            ps.setBoolean(7, alert.isAcknowledged());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    generatedId = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL unavailable for saving alert, using in-memory store: " + e.getMessage());
        }

        long finalId = (generatedId > 0) ? generatedId : alertIdSequence.incrementAndGet();
        SystemAlert saved = new SystemAlert(
                finalId,
                alert.getAlertType(),
                alert.getTargetRole(),
                alert.getHospitalName(),
                alert.getBedId(),
                alert.getMessage(),
                alert.getTimestamp(),
                alert.isAcknowledged()
        );

        alertStore.put(finalId, saved);
        LOGGER.info("Queued system alert #" + finalId + " [" + saved.getAlertType() + "] for " + saved.getHospitalName());
        return saved;
    }

    /**
     * Retrieves all pending unacknowledged alerts for an offline or reconnecting workstation (U6 Exception 3).
     *
     * @param hospitalName target hospital
     * @param targetRole   staff role (e.g. NURSE)
     * @return list of pending unacknowledged alerts in chronological order
     */
    public List<SystemAlert> fetchUnacknowledgedAlerts(String hospitalName, UserRole targetRole) {
        List<SystemAlert> list = new ArrayList<>();

        try (Connection conn = db.getConnection()) {
            String sql = (targetRole != null) ? SQL_FETCH_UNACKNOWLEDGED :
                    "SELECT alert_id, alert_type, target_role, hospital_name, bed_id, message, created_at, is_acknowledged " +
                    "FROM system_alerts WHERE hospital_name = ? AND is_acknowledged = FALSE ORDER BY created_at ASC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, hospitalName);
                if (targetRole != null) {
                    ps.setString(2, targetRole.name());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapAlertRow(rs));
                    }
                }
            }
            if (!list.isEmpty()) {
                list.forEach(a -> alertStore.put(a.getAlertId(), a));
                return list;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL unavailable for fetchUnacknowledgedAlerts: " + e.getMessage());
        }

        return alertStore.values().stream()
                .filter(a -> a.getHospitalName().equalsIgnoreCase(hospitalName))
                .filter(a -> targetRole == null || a.getTargetRole() == null || a.getTargetRole() == targetRole)
                .filter(a -> !a.isAcknowledged())
                .sorted(Comparator.comparing(SystemAlert::getTimestamp))
                .collect(Collectors.toList());
    }

    /**
     * Returns the most recent alerts for a hospital dashboard display.
     *
     * @param hospitalName the hospital name
     * @param limit        max records to retrieve
     * @return recent alerts list
     */
    public List<SystemAlert> fetchRecentAlerts(String hospitalName, int limit) {
        int max = (limit > 0) ? limit : 20;
        List<SystemAlert> list = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FETCH_RECENT)) {

            ps.setString(1, hospitalName);
            ps.setInt(2, max);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapAlertRow(rs));
                }
            }
            if (!list.isEmpty()) {
                list.forEach(a -> alertStore.put(a.getAlertId(), a));
                return list;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL unavailable for fetchRecentAlerts: " + e.getMessage());
        }

        return alertStore.values().stream()
                .filter(a -> a.getHospitalName().equalsIgnoreCase(hospitalName))
                .sorted(Comparator.comparing(SystemAlert::getTimestamp).reversed())
                .limit(max)
                .collect(Collectors.toList());
    }

    /**
     * Finds recent alerts for a hospital (default limit 50).
     */
    public List<SystemAlert> findAlertsForHospital(String hospitalName) {
        return fetchRecentAlerts(hospitalName, 50);
    }

    /**
     * Marks a specific alert as acknowledged by a staff member (U6 Notes/Issues).
     *
     * @param alertId unique alert ID
     */
    public void markAlertAcknowledged(long alertId) {
        SystemAlert existing = alertStore.get(alertId);
        if (existing != null) {
            existing.setAcknowledged(true);
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_MARK_ACKNOWLEDGED)) {
            ps.setLong(1, alertId);
            ps.executeUpdate();
            LOGGER.fine("Marked alert #" + alertId + " as acknowledged");
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL unavailable for markAlertAcknowledged: " + e.getMessage());
        }
    }

    /**
     * Marks all unacknowledged alerts for a specific hospital and role as acknowledged.
     *
     * @param hospitalName target hospital
     * @param role         staff role
     */
    public void markAllAcknowledged(String hospitalName, UserRole role) {
        alertStore.values().stream()
                .filter(a -> a.getHospitalName().equalsIgnoreCase(hospitalName))
                .filter(a -> a.getTargetRole() == role)
                .forEach(a -> a.setAcknowledged(true));

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_MARK_ALL_ACKNOWLEDGED)) {
            ps.setString(1, hospitalName);
            ps.setString(2, role.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL unavailable for markAllAcknowledged: " + e.getMessage());
        }
    }

    private SystemAlert mapAlertRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("alert_id");
        String type = rs.getString("alert_type");
        UserRole role = UserRole.fromDatabaseValue(rs.getString("target_role"));
        String hosp = rs.getString("hospital_name");
        long bedId = rs.getLong("bed_id");
        Long bedIdObj = rs.wasNull() ? null : bedId;
        String msg = rs.getString("message");
        LocalDateTime created = rs.getTimestamp("created_at").toLocalDateTime();
        boolean ack = rs.getBoolean("is_acknowledged");

        return new SystemAlert(id, type, role, hosp, bedIdObj, msg, created, ack);
    }
}
