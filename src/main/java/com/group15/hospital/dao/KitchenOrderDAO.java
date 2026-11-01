package com.group15.hospital.dao;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.model.billing.KitchenMealOrder;
import com.group15.hospital.model.enums.DietType;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for hospital kitchen meal preparation and delivery orders.
 * <p>
 * Matches database table {@code kitchen_meal_orders}. Coordinates patient dietary needs
 * (SRS F11 &amp; Use Case U3) following admissions, bed transfers, or medical regimen adjustments.
 * Includes an in-memory fallback store for offline operations.
 * </p>
 *
 * @author Jatin (Module P3)
 * @see KitchenMealOrder
 * @see DietType
 * @see DatabaseConnection
 */
public class KitchenOrderDAO {

    private static final Logger LOGGER = Logger.getLogger(KitchenOrderDAO.class.getName());

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    // ── In-Memory Fallback Store ──
    private final ConcurrentHashMap<Long, KitchenMealOrder> orderStore = new ConcurrentHashMap<>();
    private final AtomicLong orderIdSequence = new AtomicLong(2000);

    // ──────────────────────────── SQL Constants ────────────────────────────

    private static final String TABLE = "kitchen_meal_orders";

    private static final String SQL_INSERT =
            "INSERT INTO " + TABLE + " (patient_id, diet_type, status, ordered_at) VALUES (?, ?, ?, ?)";

    private static final String SQL_FIND_ACTIVE_BY_PATIENT =
            "SELECT order_id, patient_id, diet_type, status, ordered_at FROM " + TABLE +
            " WHERE patient_id = ? AND status IN ('PENDING', 'PREPARING') ORDER BY ordered_at DESC";

    private static final String SQL_FIND_ALL_ACTIVE =
            "SELECT order_id, patient_id, diet_type, status, ordered_at FROM " + TABLE +
            " WHERE status IN ('PENDING', 'PREPARING') ORDER BY ordered_at ASC";

    private static final String SQL_FIND_ALL =
            "SELECT order_id, patient_id, diet_type, status, ordered_at FROM " + TABLE +
            " ORDER BY ordered_at DESC";

    private static final String SQL_FIND_BY_ID =
            "SELECT order_id, patient_id, diet_type, status, ordered_at FROM " + TABLE +
            " WHERE order_id = ?";

    private static final String SQL_UPDATE_STATUS =
            "UPDATE " + TABLE + " SET status = ? WHERE order_id = ?";

    private static final String SQL_CANCEL_PENDING_FOR_PATIENT =
            "UPDATE " + TABLE + " SET status = 'CANCELLED' WHERE patient_id = ? AND status = 'PENDING'";

    public KitchenOrderDAO() {}

    /**
     * Creates and records a new meal order for a patient.
     *
     * @param patientId the patient identifier
     * @param dietType  prescribed diet requirement
     * @param status    initial status (defaults to PENDING if null)
     * @return created {@link KitchenMealOrder} with generated ID
     */
    public KitchenMealOrder createOrder(long patientId, DietType dietType, String status) {
        long generatedId = 0;
        LocalDateTime now = LocalDateTime.now();
        DietType diet = (dietType != null) ? dietType : DietType.REGULAR;
        String st = (status != null && !status.trim().isEmpty())
                ? status.trim().toUpperCase() : KitchenMealOrder.STATUS_PENDING;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, patientId);
            ps.setString(2, diet.name());
            ps.setString(3, st);
            ps.setTimestamp(4, Timestamp.valueOf(now));

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedId = rs.getLong(1);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert kitchen_meal_order into DB, using in-memory store: " + e.getMessage());
        }

        if (generatedId <= 0) {
            generatedId = orderIdSequence.incrementAndGet();
        }

        KitchenMealOrder order = new KitchenMealOrder(generatedId, patientId, diet, st, now);
        orderStore.put(generatedId, order);
        LOGGER.info(String.format("Created kitchen order orderId=%d for patientId=%d (%s, %s)",
                generatedId, patientId, diet, st));
        return order;
    }

    /**
     * Finds active meal orders (PENDING or PREPARING) for a specific patient.
     *
     * @param patientId the patient ID
     * @return list of active meal orders
     */
    public List<KitchenMealOrder> findActiveOrdersByPatientId(long patientId) {
        List<KitchenMealOrder> list = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ACTIVE_BY_PATIENT)) {

            ps.setLong(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    KitchenMealOrder order = mapRow(rs);
                    list.add(order);
                    orderStore.put(order.getOrderId(), order);
                }
            }

            if (!list.isEmpty()) {
                return list;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findActiveOrdersByPatientId(" + patientId + "): " + e.getMessage());
        }

        // Fallback filter
        for (KitchenMealOrder order : orderStore.values()) {
            if (order.getPatientId() == patientId &&
                    (order.isPending() || order.isPreparing())) {
                list.add(order);
            }
        }
        return list;
    }

    /**
     * Retrieves all active meal orders (PENDING or PREPARING) across the system.
     *
     * @return list of active meal orders ordered by creation time
     */
    public List<KitchenMealOrder> findAllActiveOrders() {
        List<KitchenMealOrder> list = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL_ACTIVE);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                KitchenMealOrder order = mapRow(rs);
                list.add(order);
                orderStore.put(order.getOrderId(), order);
            }

            if (!list.isEmpty()) {
                return list;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findAllActiveOrders: " + e.getMessage());
        }

        // Fallback filter
        for (KitchenMealOrder order : orderStore.values()) {
            if (order.isPending() || order.isPreparing()) {
                list.add(order);
            }
        }
        return list;
    }

    /**
     * Retrieves all meal orders across the system (including delivered and cancelled).
     *
     * @return list of all meal orders ordered by creation time descending
     */
    public List<KitchenMealOrder> findAllOrders() {
        List<KitchenMealOrder> list = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                KitchenMealOrder order = mapRow(rs);
                list.add(order);
                orderStore.put(order.getOrderId(), order);
            }

            if (!list.isEmpty()) {
                return list;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findAllOrders: " + e.getMessage());
        }

        list.addAll(orderStore.values());
        list.sort((o1, o2) -> {
            if (o1.getOrderedAt() == null || o2.getOrderedAt() == null) return 0;
            return o2.getOrderedAt().compareTo(o1.getOrderedAt());
        });
        return list;
    }

    /**
     * Finds an order by its primary key.
     *
     * @param orderId the order ID
     * @return an {@link Optional} containing the order, or empty
     */
    public Optional<KitchenMealOrder> findOrderById(long orderId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {

            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    KitchenMealOrder order = mapRow(rs);
                    orderStore.put(orderId, order);
                    return Optional.of(order);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findOrderById(" + orderId + "): " + e.getMessage());
        }

        return Optional.ofNullable(orderStore.get(orderId));
    }

    /**
     * Updates the status of an existing meal order.
     *
     * @param orderId   the order ID
     * @param newStatus the new status string (e.g. PREPARING, DELIVERED, CANCELLED)
     * @return {@code true} if status was updated
     */
    public boolean updateOrderStatus(long orderId, String newStatus) {
        if (newStatus == null || newStatus.trim().isEmpty()) {
            return false;
        }
        String cleanStatus = newStatus.trim().toUpperCase();
        boolean dbUpdated = false;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            ps.setString(1, cleanStatus);
            ps.setLong(2, orderId);

            dbUpdated = (ps.executeUpdate() > 0);
            if (dbUpdated) {
                LOGGER.info(String.format("Updated orderId=%d to status=%s", orderId, cleanStatus));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to update order status in DB for orderId=" + orderId + ": " + e.getMessage());
        }

        KitchenMealOrder memoryOrder = orderStore.get(orderId);
        if (memoryOrder != null) {
            memoryOrder.setStatus(cleanStatus);
            return true;
        }

        return dbUpdated;
    }

    /**
     * Cancels any pending meal orders for a patient (e.g. upon discharge or diet regimen shift).
     *
     * @param patientId the patient ID
     * @return number of orders cancelled
     */
    public int cancelPendingOrdersForPatient(long patientId) {
        int cancelled = 0;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_CANCEL_PENDING_FOR_PATIENT)) {

            ps.setLong(1, patientId);
            cancelled = ps.executeUpdate();
            if (cancelled > 0) {
                LOGGER.info(String.format("Cancelled %d pending orders for patientId=%d", cancelled, patientId));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to cancel pending orders for patientId=" + patientId + ": " + e.getMessage());
        }

        // Cancel in-memory store orders as well
        int memCancelled = 0;
        for (KitchenMealOrder order : orderStore.values()) {
            if (order.getPatientId() == patientId && order.isPending()) {
                order.setStatus(KitchenMealOrder.STATUS_CANCELLED);
                memCancelled++;
            }
        }

        return Math.max(cancelled, memCancelled);
    }

    // ──────────────────────────── Row Mapper ────────────────────────────

    private KitchenMealOrder mapRow(ResultSet rs) throws SQLException {
        long orderId = rs.getLong("order_id");
        long patientId = rs.getLong("patient_id");
        String dietStr = rs.getString("diet_type");
        DietType diet = (dietStr != null) ? DietType.parseDatabase(dietStr) : DietType.REGULAR;
        String status = rs.getString("status");
        Timestamp ts = rs.getTimestamp("ordered_at");
        LocalDateTime orderedAt = (ts != null) ? ts.toLocalDateTime() : LocalDateTime.now();

        return new KitchenMealOrder(orderId, patientId, diet, status, orderedAt);
    }
}
