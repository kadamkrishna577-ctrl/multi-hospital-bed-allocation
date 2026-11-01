package com.group15.hospital.model.billing;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Domain entity representing an aggregate hospital invoice for a patient.
 * <p>
 * Matches database table {@code billing_records}. Encapsulates total billing amount,
 * settlement status, payment timestamp, and an itemized collection of {@link BillingLineItem}s.
 * </p>
 *
 * @author Jatin (Module P3)
 * @see BillingLineItem
 */
public class BillingRecord {

    private long billId;
    private long patientId;
    private double totalAmount;
    private boolean isPaid;
    private LocalDateTime generatedAt;
    private LocalDateTime paidAt;
    private List<BillingLineItem> lineItems = new ArrayList<>();

    /** Default constructor. */
    public BillingRecord() {
        this.generatedAt = LocalDateTime.now();
        this.isPaid = false;
        this.totalAmount = 0.0;
    }

    /**
     * Constructor for creating a new active bill for an admitted patient.
     *
     * @param patientId ID of the patient receiving care
     */
    public BillingRecord(long patientId) {
        this.patientId = patientId;
        this.totalAmount = 0.0;
        this.isPaid = false;
        this.generatedAt = LocalDateTime.now();
        this.paidAt = null;
    }

    /**
     * Constructor without pre-loaded line items.
     *
     * @param billId      primary key bill_id
     * @param patientId   foreign key patient_id
     * @param totalAmount aggregate bill total
     * @param isPaid      payment settlement flag
     * @param generatedAt invoice creation timestamp
     * @param paidAt      invoice payment timestamp (nullable)
     */
    public BillingRecord(long billId, long patientId, double totalAmount, boolean isPaid,
                         LocalDateTime generatedAt, LocalDateTime paidAt) {
        this(billId, patientId, totalAmount, isPaid, generatedAt, paidAt, new ArrayList<>());
    }

    /**
     * Full constructor with line items list.
     *
     * @param billId      primary key bill_id
     * @param patientId   foreign key patient_id
     * @param totalAmount aggregate bill total
     * @param isPaid      payment settlement flag
     * @param generatedAt invoice creation timestamp
     * @param paidAt      invoice payment timestamp (nullable)
     * @param lineItems   itemized list of charges
     */
    public BillingRecord(long billId, long patientId, double totalAmount, boolean isPaid,
                         LocalDateTime generatedAt, LocalDateTime paidAt, List<BillingLineItem> lineItems) {
        this.billId = billId;
        this.patientId = patientId;
        this.totalAmount = totalAmount;
        this.isPaid = isPaid;
        this.generatedAt = (generatedAt != null) ? generatedAt : LocalDateTime.now();
        this.paidAt = paidAt;
        if (lineItems != null) {
            this.lineItems = new ArrayList<>(lineItems);
        }
    }

    // ──────────────────────────── Business Methods ────────────────────────────

    /**
     * Appends an itemized line item to this bill and recalculates the total.
     *
     * @param item line item to attach
     */
    public void addLineItem(BillingLineItem item) {
        if (item != null) {
            if (item.getBillId() <= 0 && this.billId > 0) {
                item.setBillId(this.billId);
            }
            this.lineItems.add(item);
            recalculateTotal();
        }
    }

    /**
     * Recalculates the cumulative bill total by summing all attached line items.
     *
     * @return updated total amount
     */
    public double recalculateTotal() {
        double sum = 0.0;
        for (BillingLineItem item : lineItems) {
            if (item != null) {
                sum += item.getAmount();
            }
        }
        this.totalAmount = sum;
        return this.totalAmount;
    }

    /**
     * Marks this bill as settled and stamps the payment timestamp.
     */
    public void markPaid() {
        this.isPaid = true;
        this.paidAt = LocalDateTime.now();
    }

    // ──────────────────────────── Getters & Setters ────────────────────────────

    public long getBillId() {
        return billId;
    }

    public void setBillId(long billId) {
        this.billId = billId;
        // propagate billId to child line items if needed
        for (BillingLineItem item : lineItems) {
            if (item != null && item.getBillId() <= 0) {
                item.setBillId(billId);
            }
        }
    }

    public long getPatientId() {
        return patientId;
    }

    public void setPatientId(long patientId) {
        this.patientId = patientId;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public boolean isPaid() {
        return isPaid;
    }

    public void setPaid(boolean paid) {
        isPaid = paid;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public List<BillingLineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<BillingLineItem> lineItems) {
        this.lineItems = (lineItems != null) ? new ArrayList<>(lineItems) : new ArrayList<>();
        recalculateTotal();
    }

    // ──────────────────────────── Standard Overrides ───────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BillingRecord that = (BillingRecord) o;
        return billId == that.billId && patientId == that.patientId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(billId, patientId);
    }

    @Override
    public String toString() {
        return String.format("BillingRecord{billId=%d, patientId=%d, total=$%.2f, isPaid=%b, items=%d}",
                billId, patientId, totalAmount, isPaid, lineItems.size());
    }
}
