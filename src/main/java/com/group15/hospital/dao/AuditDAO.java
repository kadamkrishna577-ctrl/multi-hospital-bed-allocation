package com.group15.hospital.dao;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.model.hierarchy.AuditLogEntry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

// dao for audit_logs table
public class AuditDAO {

    private static final Logger LOGGER = Logger.getLogger(AuditDAO.class.getName());

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    // in-memory list fallback for offline mode
    private final ConcurrentLinkedQueue<AuditLogEntry> inMemoryLogs = new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicLong memoryIdSequence = new java.util.concurrent.atomic.AtomicLong(100);

    // sql queries
    private static final String SQL_INSERT =
            "INSERT INTO audit_logs (timestamp, actor_username, action_type, entity_id, details) " +
            "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_SELECT_ALL =
            "SELECT log_id, timestamp, actor_username, action_type, entity_id, details " +
            "FROM audit_logs ORDER BY timestamp DESC, log_id DESC";

    private static final String SQL_SELECT_BY_ACTOR =
            "SELECT log_id, timestamp, actor_username, action_type, entity_id, details " +
            "FROM audit_logs WHERE actor_username = ? ORDER BY timestamp DESC";

    private static final String SQL_SELECT_BY_ACTION =
            "SELECT log_id, timestamp, actor_username, action_type, entity_id, details " +
            "FROM audit_logs WHERE action_type = ? ORDER BY timestamp DESC";

    public AuditDAO() {
        // seed initial system boot log
        log(new AuditLogEntry("system", "SYSTEM_STARTUP", "APP", "Audit logging service initialized"));
    }

    // saves an audit log entry to database and fallback queue
    public void log(AuditLogEntry entry) {
        if (entry == null) {
            return;
        }

        long assignedId = entry.getLogId();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            Timestamp ts = (entry.getTimestamp() != null)
                    ? Timestamp.valueOf(entry.getTimestamp())
                    : Timestamp.valueOf(LocalDateTime.now());

            String actor = (entry.getActorUsername() != null && !entry.getActorUsername().trim().isEmpty())
                    ? entry.getActorUsername().trim() : "system";
            String action = (entry.getActionType() != null && !entry.getActionType().trim().isEmpty())
                    ? entry.getActionType().trim() : "SYSTEM_EVENT";
            String entity = (entry.getEntityId() != null) ? entry.getEntityId().trim() : "";
            String details = (entry.getDetails() != null) ? entry.getDetails() : "";

            ps.setTimestamp(1, ts);
            ps.setString(2, actor);
            ps.setString(3, action);
            ps.setString(4, entity);
            ps.setString(5, details);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        assignedId = rs.getLong(1);
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to persist audit log to DB, writing to memory queue: " + e.getMessage());
            if (assignedId <= 0) {
                assignedId = memoryIdSequence.incrementAndGet();
            }
        }

        // Keep in-memory queue populated with guaranteed valid logId
        AuditLogEntry storedEntry = new AuditLogEntry(
                (assignedId > 0 ? assignedId : memoryIdSequence.incrementAndGet()),
                entry.getTimestamp(),
                entry.getActorUsername(),
                entry.getActionType(),
                entry.getEntityId(),
                entry.getDetails()
        );
        inMemoryLogs.add(storedEntry);
    }

    // helper log method with strings
    public void log(String actorUsername, String actionType, String entityId, String details) {
        AuditLogEntry entry = new AuditLogEntry(actorUsername, actionType, entityId, details);
        log(entry);
    }

    // retrieves all audit logs sorted newest first
    public List<AuditLogEntry> findAll() {
        List<AuditLogEntry> list = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapRowToEntry(rs));
            }
            if (!list.isEmpty()) {
                return list;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findAll audit logs. Using fallback: " + e.getMessage());
        }

        // Return from in-memory queue, sorted newest first
        List<AuditLogEntry> fallbackList = new ArrayList<>(inMemoryLogs);
        Collections.sort(fallbackList);
        return fallbackList;
    }

    // finds audit logs for a specific user
    public List<AuditLogEntry> findByActor(String username) {
        if (username == null || username.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<AuditLogEntry> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ACTOR)) {

            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToEntry(rs));
                }
            }
            if (!list.isEmpty()) {
                return list;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findByActor. Using fallback: " + e.getMessage());
        }

        for (AuditLogEntry entry : inMemoryLogs) {
            if (entry.getActorUsername() != null && entry.getActorUsername().equalsIgnoreCase(username.trim())) {
                list.add(entry);
            }
        }
        Collections.sort(list);
        return list;
    }

    // finds audit logs by action category
    public List<AuditLogEntry> findByActionType(String actionType) {
        if (actionType == null || actionType.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<AuditLogEntry> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ACTION)) {

            ps.setString(1, actionType.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToEntry(rs));
                }
            }
            if (!list.isEmpty()) {
                return list;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findByActionType. Using fallback: " + e.getMessage());
        }

        for (AuditLogEntry entry : inMemoryLogs) {
            if (entry.getActionType() != null && entry.getActionType().equalsIgnoreCase(actionType.trim())) {
                list.add(entry);
            }
        }
        Collections.sort(list);
        return list;
    }

    private AuditLogEntry mapRowToEntry(ResultSet rs) throws SQLException {
        long logId = rs.getLong("log_id");
        Timestamp ts = rs.getTimestamp("timestamp");
        LocalDateTime timestamp = (ts != null) ? ts.toLocalDateTime() : LocalDateTime.now();
        String actor = rs.getString("actor_username");
        String action = rs.getString("action_type");
        String entity = rs.getString("entity_id");
        String details = rs.getString("details");

        return new AuditLogEntry(logId, timestamp, actor, action, entity, details);
    }
}
