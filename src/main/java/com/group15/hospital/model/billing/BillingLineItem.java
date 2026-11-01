package com.group15.hospital.model.billing;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain entity representing an itemized entry on a patient's hospital bill.
 * <p>
 * Matches database table {@code billing_line_items}. Tracks equipment charges,
 * hourly base bed rates, auxiliary medication, and service line items.
 * </p>
 *
 * @author Jatin (Module P3)
 */
public class BillingLineItem {

    private long itemId;
    private long billId;
    private String description;
    private double amount;
    private LocalDateTime createdAt;

    /** Default constructor. */
    public BillingLineItem() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new line item before persistence.
     *
     * @param billId      foreign key to parent billing_records
     * @param description itemized service description
     * @param amount      line item charge amount in dollars
     */
    public BillingLineItem(long billId, String description, double amount) {
        this.billId = billId;
        this.description = description;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Full constructor for hydrating from the database.
     *
     * @param itemId      primary key item_id
     * @param billId      foreign key bill_id
     * @param description itemized service description
     * @param amount      dollar charge amount
     * @param createdAt   timestamp when line item was recorded
     */
    public BillingLineItem(long itemId, long billId, String description, double amount, LocalDateTime createdAt) {
        this.itemId = itemId;
        this.billId = billId;
        this.description = description;
        this.amount = amount;
        this.createdAt = (createdAt != null) ? createdAt : LocalDateTime.now();
    }

    // ──────────────────────────── Getters & Setters ────────────────────────────

    public long getItemId() {
        return itemId;
    }

    public void setItemId(long itemId) {
        this.itemId = itemId;
    }

    public long getBillId() {
        return billId;
    }

    public void setBillId(long billId) {
        this.billId = billId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // ──────────────────────────── Standard Overrides ───────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BillingLineItem that = (BillingLineItem) o;
        return itemId == that.itemId &&
                billId == that.billId &&
                Double.compare(that.amount, amount) == 0 &&
                Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, billId, description, amount);
    }

    @Override
    public String toString() {
        return String.format("BillingLineItem{itemId=%d, billId=%d, desc='%s', amount=$%.2f, createdAt=%s}",
                itemId, billId, description, amount, createdAt);
    }
}
