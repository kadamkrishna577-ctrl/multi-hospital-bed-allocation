package com.group15.hospital.model.billing;

import com.group15.hospital.model.enums.DietType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain entity representing a dietary meal preparation order dispatched to the hospital kitchen.
 * <p>
 * Matches database table {@code kitchen_meal_orders}. Automatically generated and updated
 * when a patient is admitted, transferred, or has their dietary regimen adjusted (SRS F11 &amp; Use Case U3).
 * </p>
 *
 * @author Jatin (Module P3)
 * @see DietType
 */
public class KitchenMealOrder {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PREPARING = "PREPARING";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private long orderId;
    private long patientId;
    private DietType dietType;
    private String status;
    private LocalDateTime orderedAt;

    /** Default constructor. */
    public KitchenMealOrder() {
        this.status = STATUS_PENDING;
        this.dietType = DietType.REGULAR;
        this.orderedAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new pending meal order.
     *
     * @param patientId ID of the admitted patient
     * @param dietType  prescribed dietary requirement
     */
    public KitchenMealOrder(long patientId, DietType dietType) {
        this(0, patientId, dietType, STATUS_PENDING, LocalDateTime.now());
    }

    /**
     * Constructor specifying patient, diet, and status.
     *
     * @param patientId ID of the patient
     * @param dietType  prescribed dietary requirement
     * @param status    order fulfillment status
     */
    public KitchenMealOrder(long patientId, DietType dietType, String status) {
        this(0, patientId, dietType, status, LocalDateTime.now());
    }

    /**
     * Full constructor for hydrating from the database.
     *
     * @param orderId   primary key order_id
     * @param patientId foreign key patient_id
     * @param dietType  prescribed dietary requirement
     * @param status    order status (PENDING, PREPARING, DELIVERED, CANCELLED)
     * @param orderedAt timestamp when order was placed
     */
    public KitchenMealOrder(long orderId, long patientId, DietType dietType, String status, LocalDateTime orderedAt) {
        this.orderId = orderId;
        this.patientId = patientId;
        this.dietType = (dietType != null) ? dietType : DietType.REGULAR;
        this.status = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : STATUS_PENDING;
        this.orderedAt = (orderedAt != null) ? orderedAt : LocalDateTime.now();
    }

    // ──────────────────────────── Helper Methods ────────────────────────────

    public boolean isPending() {
        return STATUS_PENDING.equalsIgnoreCase(status);
    }

    public boolean isPreparing() {
        return STATUS_PREPARING.equalsIgnoreCase(status);
    }

    public boolean isDelivered() {
        return STATUS_DELIVERED.equalsIgnoreCase(status);
    }

    public boolean isCancelled() {
        return STATUS_CANCELLED.equalsIgnoreCase(status);
    }

    // ──────────────────────────── Getters & Setters ────────────────────────────

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public long getPatientId() {
        return patientId;
    }

    public void setPatientId(long patientId) {
        this.patientId = patientId;
    }

    public DietType getDietType() {
        return dietType;
    }

    public void setDietType(DietType dietType) {
        this.dietType = (dietType != null) ? dietType : DietType.REGULAR;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : STATUS_PENDING;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }

    public void setOrderedAt(LocalDateTime orderedAt) {
        this.orderedAt = orderedAt;
    }

    // ──────────────────────────── Standard Overrides ───────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KitchenMealOrder that = (KitchenMealOrder) o;
        return orderId == that.orderId && patientId == that.patientId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, patientId);
    }

    @Override
    public String toString() {
        return String.format("KitchenMealOrder{orderId=%d, patientId=%d, diet=%s, status='%s', orderedAt=%s}",
                orderId, patientId, dietType, status, orderedAt);
    }
}
