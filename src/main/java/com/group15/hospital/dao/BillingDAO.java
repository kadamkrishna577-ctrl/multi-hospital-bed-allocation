package com.group15.hospital.dao;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.model.billing.BillingLineItem;
import com.group15.hospital.model.billing.BillingRecord;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for managing patient billing records and itemized line charges.
 * <p>
 * Matches database tables {@code billing_records} and {@code billing_line_items}.
 * Supports patient discharge settlements, equipment hourly fee tracking, and room rate
 * calculations (SRS F14 &amp; Use Case U7). Includes thread-safe in-memory fallback stores
 * for offline operations.
 * </p>
 *
 * @author Jatin (Module P3)
 * @see BillingRecord
 * @see BillingLineItem
 * @see DatabaseConnection
 */
public class BillingDAO {

    private static final Logger LOGGER = Logger.getLogger(BillingDAO.class.getName());

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    // ── In-Memory Fallback Stores ──
    private final ConcurrentHashMap<Long, BillingRecord> billStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<BillingLineItem>> lineItemsStore = new ConcurrentHashMap<>();
    private final AtomicLong billIdSequence = new AtomicLong(1000);
    private final AtomicLong lineItemIdSequence = new AtomicLong(5000);

    // ──────────────────────────── SQL Constants ────────────────────────────

    private static final String SQL_INSERT_BILL =
            "INSERT INTO billing_records (patient_id, total_amount, is_paid, generated_at) " +
            "VALUES (?, 0.00, FALSE, ?)";

    private static final String SQL_INSERT_LINE_ITEM =
            "INSERT INTO billing_line_items (bill_id, description, amount, created_at) " +
            "VALUES (?, ?, ?, ?)";

    private static final String SQL_UPDATE_BILL_TOTAL =
            "UPDATE billing_records SET total_amount = total_amount + ? WHERE bill_id = ?";

    private static final String SQL_FIND_ACTIVE_BILL_BY_PATIENT =
            "SELECT bill_id, patient_id, total_amount, is_paid, generated_at, paid_at " +
            "FROM billing_records WHERE patient_id = ? AND is_paid = FALSE ORDER BY generated_at DESC LIMIT 1";

    private static final String SQL_FIND_BILL_BY_ID =
            "SELECT bill_id, patient_id, total_amount, is_paid, generated_at, paid_at " +
            "FROM billing_records WHERE bill_id = ?";

    private static final String SQL_FIND_ALL_BILLS =
            "SELECT bill_id, patient_id, total_amount, is_paid, generated_at, paid_at " +
            "FROM billing_records ORDER BY generated_at DESC";

    private static final String SQL_FETCH_LINE_ITEMS_BY_BILL =
            "SELECT item_id, bill_id, description, amount, created_at " +
            "FROM billing_line_items WHERE bill_id = ? ORDER BY created_at ASC";

    private static final String SQL_MARK_PAID =
            "UPDATE billing_records SET is_paid = TRUE, paid_at = ? WHERE bill_id = ?";

    public BillingDAO() {}

    /**
     * Creates a new active billing record for an admitted patient.
     *
     * @param patientId the patient's ID
     * @return the created {@link BillingRecord} with its assigned primary key
     */
    public BillingRecord createBillingRecord(long patientId) {
        long generatedBillId = 0;
        LocalDateTime now = LocalDateTime.now();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT_BILL, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, patientId);
            ps.setTimestamp(2, Timestamp.valueOf(now));

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedBillId = rs.getLong(1);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert billing_record into DB, using in-memory store: " + e.getMessage());
        }

        if (generatedBillId <= 0) {
            generatedBillId = billIdSequence.incrementAndGet();
        }

        BillingRecord record = new BillingRecord(generatedBillId, patientId, 0.00, false, now, null);
        billStore.put(generatedBillId, record);
        lineItemsStore.put(generatedBillId, new ArrayList<>());
        LOGGER.info(String.format("Created billing record billId=%d for patientId=%d", generatedBillId, patientId));
        return record;
    }

    /**
     * Appends an itemized line item to an existing bill and updates the cumulative bill total.
     *
     * @param billId      the target bill ID
     * @param description description of the service or equipment
     * @param amount      monetary charge amount
     * @return the created {@link BillingLineItem}
     */
    public BillingLineItem addLineItem(long billId, String description, double amount) {
        long generatedItemId = 0;
        LocalDateTime now = LocalDateTime.now();
        String desc = (description != null) ? description : "Miscellaneous Charge";

        try (Connection conn = db.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement psItem = conn.prepareStatement(SQL_INSERT_LINE_ITEM, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement psUpdate = conn.prepareStatement(SQL_UPDATE_BILL_TOTAL)) {

                // 1. Insert line item
                psItem.setLong(1, billId);
                psItem.setString(2, desc);
                psItem.setDouble(3, amount);
                psItem.setTimestamp(4, Timestamp.valueOf(now));
                psItem.executeUpdate();

                try (ResultSet rs = psItem.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedItemId = rs.getLong(1);
                    }
                }

                // 2. Increment bill total
                psUpdate.setDouble(1, amount);
                psUpdate.setLong(2, billId);
                psUpdate.executeUpdate();

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to add billing line item to DB, updating in-memory store: " + e.getMessage());
        }

        if (generatedItemId <= 0) {
            generatedItemId = lineItemIdSequence.incrementAndGet();
        }

        BillingLineItem lineItem = new BillingLineItem(generatedItemId, billId, desc, amount, now);

        // Update in-memory store
        List<BillingLineItem> items = lineItemsStore.computeIfAbsent(billId, k -> new ArrayList<>());
        items.add(lineItem);

        BillingRecord record = billStore.get(billId);
        if (record != null) {
            record.addLineItem(lineItem);
        }

        LOGGER.info(String.format("Added line item itemId=%d to billId=%d: '%s' ($%.2f)",
                generatedItemId, billId, desc, amount));
        return lineItem;
    }

    /**
     * Loads the active (unpaid) billing record for a patient, along with all of its line items.
     *
     * @param patientId the patient ID
     * @return an {@link Optional} containing the active {@link BillingRecord}, or empty
     */
    public Optional<BillingRecord> findActiveBillByPatientId(long patientId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ACTIVE_BILL_BY_PATIENT)) {

            ps.setLong(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BillingRecord record = mapRowToBill(rs);
                    record.setLineItems(fetchLineItems(conn, record.getBillId()));
                    billStore.put(record.getBillId(), record);
                    return Optional.of(record);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findActiveBillByPatientId(" + patientId + "), reading fallback: " + e.getMessage());
        }

        // Fallback to in-memory store
        for (BillingRecord record : billStore.values()) {
            if (record.getPatientId() == patientId && !record.isPaid()) {
                List<BillingLineItem> items = lineItemsStore.getOrDefault(record.getBillId(), Collections.emptyList());
                record.setLineItems(new ArrayList<>(items));
                return Optional.of(record);
            }
        }

        return Optional.empty();
    }

    /**
     * Loads a billing record by its primary key.
     *
     * @param billId the bill identifier
     * @return an {@link Optional} containing the record with line items, or empty
     */
    public Optional<BillingRecord> findBillById(long billId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_BILL_BY_ID)) {

            ps.setLong(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BillingRecord record = mapRowToBill(rs);
                    record.setLineItems(fetchLineItems(conn, billId));
                    billStore.put(billId, record);
                    return Optional.of(record);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findBillById(" + billId + "): " + e.getMessage());
        }

        BillingRecord fallback = billStore.get(billId);
        if (fallback != null) {
            List<BillingLineItem> items = lineItemsStore.getOrDefault(billId, Collections.emptyList());
            fallback.setLineItems(new ArrayList<>(items));
            return Optional.of(fallback);
        }

        return Optional.empty();
    }

    /**
     * Retrieves all billing records ordered by generation timestamp descending.
     *
     * @return list of all {@link BillingRecord}s with populated line items
     */
    public List<BillingRecord> findAllBills() {
        List<BillingRecord> bills = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL_BILLS);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                BillingRecord record = mapRowToBill(rs);
                record.setLineItems(fetchLineItems(conn, record.getBillId()));
                bills.add(record);
                billStore.put(record.getBillId(), record);
            }

            if (!bills.isEmpty()) {
                return bills;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findAllBills, using fallback: " + e.getMessage());
        }

        for (BillingRecord rec : billStore.values()) {
            List<BillingLineItem> items = lineItemsStore.getOrDefault(rec.getBillId(), Collections.emptyList());
            rec.setLineItems(new ArrayList<>(items));
            bills.add(rec);
        }
        return bills;
    }

    /**
     * Settles and marks a billing record as paid.
     *
     * @param billId the bill to settle
     * @return {@code true} if updated successfully
     */
    public boolean markBillPaid(long billId) {
        boolean dbUpdated = false;
        LocalDateTime now = LocalDateTime.now();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_MARK_PAID)) {

            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setLong(2, billId);

            dbUpdated = (ps.executeUpdate() > 0);
            if (dbUpdated) {
                LOGGER.info("Marked billId=" + billId + " as PAID");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to mark billId=" + billId + " as paid in DB: " + e.getMessage());
        }

        BillingRecord memoryRecord = billStore.get(billId);
        if (memoryRecord != null) {
            memoryRecord.markPaid();
            return true;
        }

        return dbUpdated;
    }

    // ──────────────────────────── Internal Helpers ────────────────────────────

    private List<BillingLineItem> fetchLineItems(Connection conn, long billId) throws SQLException {
        List<BillingLineItem> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FETCH_LINE_ITEMS_BY_BILL)) {
            ps.setLong(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long itemId = rs.getLong("item_id");
                    String desc = rs.getString("description");
                    double amt = rs.getDouble("amount");
                    Timestamp ts = rs.getTimestamp("created_at");
                    LocalDateTime createdAt = (ts != null) ? ts.toLocalDateTime() : LocalDateTime.now();
                    items.add(new BillingLineItem(itemId, billId, desc, amt, createdAt));
                }
            }
        }
        lineItemsStore.put(billId, items);
        return items;
    }

    private BillingRecord mapRowToBill(ResultSet rs) throws SQLException {
        long billId = rs.getLong("bill_id");
        long patientId = rs.getLong("patient_id");
        double total = rs.getDouble("total_amount");
        boolean isPaid = rs.getBoolean("is_paid");
        Timestamp genTs = rs.getTimestamp("generated_at");
        LocalDateTime genAt = (genTs != null) ? genTs.toLocalDateTime() : LocalDateTime.now();
        Timestamp paidTs = rs.getTimestamp("paid_at");
        LocalDateTime paidAt = (paidTs != null) ? paidTs.toLocalDateTime() : null;

        return new BillingRecord(billId, patientId, total, isPaid, genAt, paidAt);
    }
}
